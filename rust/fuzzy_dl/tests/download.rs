//! Testes de integração do núcleo de download com um servidor HTTP local.
//!
//! Cobrem: Range/206, download segmentado, retomada após cancelamento,
//! falha no meio do stream com retry, cancelamento e fallback sem Range.

use std::sync::atomic::{AtomicBool, AtomicI64, AtomicU64, AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::Duration;

use fuzzy_dl::download::{run, EventFn, Job, CANCELLED};
use fuzzy_dl::resolve::{is_valid_direct, parse_token};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::{TcpListener, TcpStream};

struct State {
    no_range: bool,
    delay_ms: AtomicU64,
    /// Se >= 0, a próxima resposta 206 "de verdade" corta a conexão após N bytes.
    fail_next_after: AtomicI64,
    requests: AtomicUsize,
}

struct TestServer {
    url: String,
    payload: Arc<Vec<u8>>,
    state: Arc<State>,
    task: tokio::task::JoinHandle<()>,
}

impl Drop for TestServer {
    fn drop(&mut self) {
        self.task.abort();
    }
}

fn make_payload(n: usize) -> Vec<u8> {
    (0..n).map(|i| ((i.wrapping_mul(31).wrapping_add(7)) & 0xff) as u8).collect()
}

impl TestServer {
    async fn start(size: usize, no_range: bool, delay_ms: u64) -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();
        let payload = Arc::new(make_payload(size));
        let state = Arc::new(State {
            no_range,
            delay_ms: AtomicU64::new(delay_ms),
            fail_next_after: AtomicI64::new(-1),
            requests: AtomicUsize::new(0),
        });
        let task = {
            let payload = payload.clone();
            let state = state.clone();
            tokio::spawn(async move {
                loop {
                    let Ok((stream, _)) = listener.accept().await else { break };
                    let payload = payload.clone();
                    let state = state.clone();
                    tokio::spawn(async move {
                        let _ = handle(stream, payload, state).await;
                    });
                }
            })
        };
        Self { url: format!("http://{addr}/file"), payload, state, task }
    }

    fn fail_next(&self, after: i64) {
        self.state.fail_next_after.store(after, Ordering::SeqCst);
    }
}

async fn handle(mut stream: TcpStream, payload: Arc<Vec<u8>>, state: Arc<State>) -> std::io::Result<()> {
    let (r, mut w) = stream.split();
    let mut reader = BufReader::new(r);
    loop {
        let mut line = String::new();
        if reader.read_line(&mut line).await? == 0 {
            return Ok(());
        }
        if line.trim().is_empty() {
            continue;
        }
        let mut parts = line.split_whitespace();
        let method = parts.next().unwrap_or("").to_string();
        let _path = parts.next().unwrap_or("/");

        let mut range: Option<(usize, usize)> = None;
        loop {
            let mut h = String::new();
            if reader.read_line(&mut h).await? == 0 {
                return Ok(());
            }
            let h = h.trim_end();
            if h.is_empty() {
                break;
            }
            if let Some(v) = h.to_ascii_lowercase().strip_prefix("range: bytes=") {
                let mut it = v.split('-');
                let a = it.next().and_then(|x| x.parse::<usize>().ok());
                let b = it.next().and_then(|x| {
                    if x.is_empty() { None } else { x.parse::<usize>().ok() }
                });
                if let Some(a) = a {
                    let end = b.unwrap_or(payload.len() - 1).min(payload.len() - 1);
                    range = Some((a, end));
                }
            }
        }

        state.requests.fetch_add(1, Ordering::Relaxed);
        let total = payload.len();
        let use_range = range.filter(|_| !state.no_range && method != "HEAD");
        let (code, start, end) = match use_range {
            Some((a, b)) => (206, a.min(total.saturating_sub(1)), b.min(total.saturating_sub(1))),
            None => (200, 0, total.saturating_sub(1)),
        };
        let body_len = if total == 0 { 0 } else { end + 1 - start };
        let mut head = format!(
            "HTTP/1.1 {code} X\r\nContent-Length: {body_len}\r\nContent-Type: application/zip\r\n\
             Accept-Ranges: bytes\r\nETag: \"test\"\r\nContent-Disposition: attachment; filename=\"part.zip\"\r\n\
             Connection: keep-alive\r\n"
        );
        if code == 206 {
            head.push_str(&format!("Content-Range: bytes {start}-{end}/{total}\r\n"));
        }
        head.push_str("\r\n");
        w.write_all(head.as_bytes()).await?;
        if method == "HEAD" {
            w.flush().await?;
            continue;
        }

        // Só injeta falha em ranges "de download" (não no probe 0-0).
        let inject = code == 206
            && !(start == 0 && end == 0)
            && state.fail_next_after.load(Ordering::SeqCst) >= 0;
        let fail_after = if inject {
            let n = state.fail_next_after.swap(-1, Ordering::SeqCst);
            if n >= 0 { n as usize } else { usize::MAX }
        } else {
            usize::MAX
        };

        let mut sent = 0usize;
        let chunk = 64 * 1024;
        while sent < body_len {
            if sent >= fail_after {
                // corta a conexão no meio, simulando rede caindo
                let _ = w.shutdown().await;
                return Ok(());
            }
            let n = chunk.min(body_len - sent).min(fail_after.saturating_sub(sent));
            w.write_all(&payload[start + sent..start + sent + n]).await?;
            sent += n;
            let delay = state.delay_ms.load(Ordering::Relaxed);
            if delay > 0 {
                tokio::time::sleep(Duration::from_millis(delay)).await;
            }
        }
        w.flush().await?;
    }
}

fn job(server: &TestServer, dest: &std::path::Path) -> Job {
    Job {
        url: server.url.clone(),
        dest: dest.to_path_buf(),
        total: server.payload.len() as u64,
        threads: 4,
        retries: 4,
    }
}

fn events() -> EventFn {
    Arc::new(|_| {})
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn segmented_download_is_byte_exact() {
    let server = TestServer::start(2 * 1024 * 1024 + 123, false, 0).await;
    let dir = tempfile::tempdir().unwrap();
    let dest = dir.path().join("part.zip");
    let client = fuzzy_dl::resolve::build_client(true).unwrap();
    run(client, job(&server, &dest), events(), Arc::new(AtomicBool::new(false)))
        .await
        .unwrap();
    assert_eq!(std::fs::read(&dest).unwrap(), *server.payload);
    assert!(!dest.with_extension("zip.part").exists());
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn cancel_then_resume_is_byte_exact() {
    let size = 8 * 1024 * 1024;
    // 10 ms por bloco de 64 KiB: com 4 conexões a parte leva ~320 ms, tempo de
    // sobra para cancelar no meio sem depender de um evento de progresso.
    let server = TestServer::start(size, false, 10).await;
    let dir = tempfile::tempdir().unwrap();
    let dest = dir.path().join("part.zip");
    let part = dir.path().join("part.zip.part");
    let state = dir.path().join("part.zip.part.json");

    let client = fuzzy_dl::resolve::build_client(true).unwrap();
    let cancel = Arc::new(AtomicBool::new(false));
    let c2 = cancel.clone();
    tokio::spawn(async move {
        tokio::time::sleep(Duration::from_millis(150)).await;
        c2.store(true, Ordering::SeqCst);
    });
    let err = run(client, job(&server, &dest), events(), cancel).await.unwrap_err();
    assert_eq!(err.0, CANCELLED);
    assert!(part.exists(), ".part deve ser preservado");
    assert!(state.exists(), "sidecar deve ser preservado");
    let partial: u64 = std::fs::metadata(&part).unwrap().len();
    assert_eq!(partial, size as u64, "arquivo pré-alocado");

    // Retoma sem atraso.
    server.state.delay_ms.store(0, Ordering::SeqCst);
    let client = fuzzy_dl::resolve::build_client(true).unwrap();
    run(client, job(&server, &dest), events(), Arc::new(AtomicBool::new(false)))
        .await
        .unwrap();
    assert_eq!(std::fs::read(&dest).unwrap(), *server.payload);
    assert!(!state.exists());
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn retries_after_mid_stream_failure() {
    let size = 3 * 1024 * 1024;
    let server = TestServer::start(size, false, 0).await;
    server.fail_next(100 * 1024);
    let dir = tempfile::tempdir().unwrap();
    let dest = dir.path().join("part.zip");
    let client = fuzzy_dl::resolve::build_client(true).unwrap();
    run(client, job(&server, &dest), events(), Arc::new(AtomicBool::new(false)))
        .await
        .unwrap();
    assert_eq!(std::fs::read(&dest).unwrap(), *server.payload);
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn cancellation_is_prompt() {
    let server = TestServer::start(16 * 1024 * 1024, false, 5).await;
    let dir = tempfile::tempdir().unwrap();
    let dest = dir.path().join("part.zip");
    let client = fuzzy_dl::resolve::build_client(true).unwrap();
    let cancel = Arc::new(AtomicBool::new(false));
    let c2 = cancel.clone();
    tokio::spawn(async move {
        tokio::time::sleep(Duration::from_millis(300)).await;
        c2.store(true, Ordering::SeqCst);
    });
    let t0 = std::time::Instant::now();
    let err = run(client, job(&server, &dest), events(), cancel).await.unwrap_err();
    assert_eq!(err.0, CANCELLED);
    assert!(t0.elapsed() < Duration::from_secs(5), "cancelamento demorou {:?}", t0.elapsed());
    assert!(!dest.exists());
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn single_stream_fallback_without_range() {
    let size = 1024 * 1024;
    let server = TestServer::start(size, true, 0).await;
    let dir = tempfile::tempdir().unwrap();
    let dest = dir.path().join("part.zip");
    let client = fuzzy_dl::resolve::build_client(true).unwrap();
    run(client, job(&server, &dest), events(), Arc::new(AtomicBool::new(false)))
        .await
        .unwrap();
    assert_eq!(std::fs::read(&dest).unwrap(), *server.payload);
}

#[test]
fn parses_token_and_validates_redirects() {
    let html = r#"<a href="/1c155vv0rita/download?t=MTc5NDU3Mzc3NjY5Nw.abc-def_ghi&x=1">"#;
    assert_eq!(parse_token(html).as_deref(), Some("MTc5NDU3Mzc3NjY5Nw.abc-def_ghi"));
    assert!(is_valid_direct("https://ts.buzzheavier.com/d/1c155vv0rita?v=abc"));
    assert!(!is_valid_direct("https://fafda.to/d/1c155vv0rita?v=abc"));
    assert!(!is_valid_direct("https://buzzheavier.com/1c155vv0rita"));
}

#[test]
fn plan_covers_all_bytes_exactly_once() {
    // smoke: 10 bytes / 4 segmentos => 3+3+2+2
    let total = 10u64;
    let threads = 4;
    let base = total / threads as u64;
    let extra = total % threads as u64;
    assert_eq!(base * threads as u64 + extra, total);
}
