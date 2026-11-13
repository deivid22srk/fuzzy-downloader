//! Ponte JNI usada pelo app Android.
//!
//! Classe Java/Kotlin: `com.fuzzydl.app.core.NativeDownloader`
//! Interface de callback: `com.fuzzydl.app.core.NativeCallback`
//!
//! Eventos: 0 = progresso, 1 = concluído, 2 = erro, 3 = cancelado.

use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, AtomicI64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

use jni::objects::{GlobalRef, JClass, JObject, JString, JValue};
use jni::sys::{jint, jlong, jstring};
use jni::JNIEnv;

use crate::client::build_client;
use crate::download::{run, DlEvent, EventFn, Job, CANCELLED};

static REGISTRY: OnceLock<Mutex<HashMap<i64, Arc<AtomicBool>>>> = OnceLock::new();
static NEXT_HANDLE: AtomicI64 = AtomicI64::new(1);

fn registry() -> &'static Mutex<HashMap<i64, Arc<AtomicBool>>> {
    REGISTRY.get_or_init(|| Mutex::new(HashMap::new()))
}

fn json_err(msg: &str) -> String {
    serde_json::json!({ "ok": false, "error": msg }).to_string()
}

fn jstring_of(env: &mut JNIEnv, s: &str) -> jstring {
    match env.new_string(s) {
        Ok(v) => v.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Descobre tamanho e nome de um arquivo qualquer (HEAD + fallback Range).
/// Retorna JSON `{ok,url,size,name}` ou `{ok:false,error}`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_fuzzydl_app_core_NativeDownloader_probe(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
) -> jstring {
    let url: String = match env.get_string(&url) {
        Ok(s) => s.into(),
        Err(_) => return jstring_of(&mut env, &json_err("URL inválida")),
    };
    let rt = match tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
    {
        Ok(r) => r,
        Err(e) => return jstring_of(&mut env, &json_err(&format!("runtime: {e}"))),
    };
    let out = rt.block_on(async {
        let client = build_client(true)?;
        let r = crate::client::probe(&client, &url).await?;
        Ok::<String, crate::Error>(serde_json::to_string(&serde_json::json!({
            "ok": true,
            "url": r.url,
            "size": r.size,
            "name": r.name,
        }))?)
    });
    match out {
        Ok(json) => jstring_of(&mut env, &json),
        Err(e) => jstring_of(&mut env, &json_err(&e.0)),
    }
}

fn emit(
    env: &mut JNIEnv,
    cb: &GlobalRef,
    code: i32,
    done: u64,
    total: u64,
    speed: u64,
    eta: u64,
    msg: Option<&str>,
) {
    let jmsg: JObject = match msg {
        Some(m) => match env.new_string(m) {
            Ok(s) => s.into(),
            Err(_) => JObject::null(),
        },
        None => JObject::null(),
    };
    let _ = env.call_method(
        cb,
        "onEvent",
        "(IJJJJLjava/lang/String;)V",
        &[
            JValue::Int(code as jint),
            JValue::Long(done as jlong),
            JValue::Long(total as jlong),
            JValue::Long(speed as jlong),
            JValue::Long(eta as jlong),
            JValue::Object(&jmsg),
        ],
    );
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_fuzzydl_app_core_NativeDownloader_start(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
    dest: JString,
    total: jlong,
    threads: jint,
    callback: JObject,
) -> jlong {
    let url: String = match env.get_string(&url) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let dest: String = match env.get_string(&dest) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let cb = match env.new_global_ref(callback) {
        Ok(v) => v,
        Err(_) => return 0,
    };
    let vm = match env.get_java_vm() {
        Ok(v) => v,
        Err(_) => return 0,
    };

    let handle = NEXT_HANDLE.fetch_add(1, Ordering::SeqCst);
    let cancel = Arc::new(AtomicBool::new(false));
    registry().lock().unwrap().insert(handle, cancel.clone());

    std::thread::spawn(move || {
        let (tx, rx) = std::sync::mpsc::channel::<DlEvent>();
        let cb_thread = cb.clone();
        let dispatcher = std::thread::spawn(move || {
            let mut env = match vm.attach_current_thread() {
                Ok(e) => e,
                Err(_) => return,
            };
            while let Ok(ev) = rx.recv() {
                match ev {
                    DlEvent::Progress(p) => emit(&mut env, &cb_thread, 0, p.done, p.total, p.speed_bps, p.eta_secs, None),
                    DlEvent::Done => emit(&mut env, &cb_thread, 1, 0, 0, 0, 0, None),
                    DlEvent::Cancelled => emit(&mut env, &cb_thread, 3, 0, 0, 0, 0, None),
                    DlEvent::Error(msg) => emit(&mut env, &cb_thread, 2, 0, 0, 0, 0, Some(&msg)),
                }
            }
        });

        let rt = match tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
        {
            Ok(r) => r,
            Err(e) => {
                let _ = tx.send(DlEvent::Error(format!("runtime: {e}")));
                drop(tx);
                let _ = dispatcher.join();
                registry().lock().unwrap().remove(&handle);
                return;
            }
        };

        let tx2 = tx.clone();
        let events: EventFn = Arc::new(move |ev| {
            let _ = tx.send(ev);
        });
        let job = Job {
            url,
            dest: dest.into(),
            total: total.max(0) as u64,
            threads: threads.max(1) as usize,
            retries: 4,
        };
        let client = match build_client(true) {
            Ok(c) => c,
            Err(e) => {
                let _ = tx2.send(DlEvent::Error(e.0));
                drop(tx2);
                let _ = dispatcher.join();
                registry().lock().unwrap().remove(&handle);
                return;
            }
        };
        match rt.block_on(run(client, job, events, cancel)) {
            Ok(()) => {}
            Err(e) if e.0 == CANCELLED => {
                let _ = tx2.send(DlEvent::Cancelled);
            }
            Err(e) => {
                let _ = tx2.send(DlEvent::Error(e.0));
            }
        }
        drop(tx2);
        let _ = dispatcher.join();
        registry().lock().unwrap().remove(&handle);
    });

    handle
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_fuzzydl_app_core_NativeDownloader_cancel(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(flag) = registry().lock().unwrap().get(&(handle as i64)) {
        flag.store(true, Ordering::SeqCst);
    }
}
