use std::collections::HashSet;
use std::fs::{File, OpenOptions};
use std::os::unix::fs::FileExt;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use futures_util::StreamExt;
use serde::{Deserialize, Serialize};

use crate::error::{Error, Result};
use crate::bail;

pub const CANCELLED: &str = "__cancelled__";
const FIRST_BYTE_TIMEOUT: Duration = Duration::from_secs(45);
const IO_TIMEOUT: Duration = Duration::from_secs(12);
const SAVE_EVERY: Duration = Duration::from_secs(2);

#[derive(Clone, Copy, Debug, Default, Serialize)]
pub struct Progress {
    pub done: u64,
    pub total: u64,
    pub speed_bps: u64,
    pub eta_secs: u64,
}

#[derive(Debug)]
pub enum DlEvent {
    Progress(Progress),
    Done,
    Cancelled,
    Error(String),
}

pub type EventFn = Arc<dyn Fn(DlEvent) + Send + Sync>;

#[derive(Clone, Debug)]
pub struct Job {
    pub url: String,
    pub dest: PathBuf,
    pub total: u64,
    pub threads: usize,
    pub retries: u32,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
struct SegState {
    start: u64,
    end: u64,
    done: u64,
}

impl SegState {
    fn len(&self) -> u64 {
        self.end.saturating_sub(self.start) + 1
    }
    fn complete(&self) -> bool {
        self.done >= self.len()
    }
}

#[derive(Serialize, Deserialize)]
struct State {
    version: u32,
    url: String,
    total: u64,
    segments: Vec<SegState>,
}

fn sidecar(dest: &Path) -> PathBuf {
    let mut s = dest.as_os_str().to_owned();
    s.push(".part.json");
    PathBuf::from(s)
}

fn part_path(dest: &Path) -> PathBuf {
    let mut s = dest.as_os_str().to_owned();
    s.push(".part");
    PathBuf::from(s)
}

fn plan(total: u64, threads: usize) -> Vec<SegState> {
    let threads = threads.clamp(1, 8) as u64;
    let base = total / threads;
    let extra = total % threads;
    let mut out = Vec::with_capacity(threads as usize);
    let mut start = 0u64;
    for i in 0..threads {
        let n = base + if i < extra { 1 } else { 0 };
        if n == 0 {
            continue;
        }
        out.push(SegState { start, end: start + n - 1, done: 0 });
        start += n;
    }
    out
}

fn load_state(dest: &Path, total: u64) -> Option<Vec<SegState>> {
    let data = std::fs::read(sidecar(dest)).ok()?;
    let st: State = serde_json::from_slice(&data).ok()?;
    if st.version != 1 || st.total != total || st.segments.is_empty() {
        return None;
    }
    let mut expected = 0u64;
    for s in &st.segments {
        if s.start != expected || s.end < s.start || s.done > s.len() {
            return None;
        }
        expected = s.end + 1;
    }
    if expected != total {
        return None;
    }
    Some(st.segments)
}

fn save_state(dest: &Path, url: &str, total: u64, segs: &Mutex<Vec<SegState>>) {
    let segments = match segs.lock() {
        Ok(g) => g.clone(),
        Err(_) => return,
    };
    let st = State { version: 1, url: url.to_string(), total, segments };
    let Ok(json) = serde_json::to_vec(&st) else { return };
    let path = sidecar(dest);
    let tmp = path.with_extension("json.tmp");
    if std::fs::write(&tmp, json).is_ok() {
        let _ = std::fs::rename(&tmp, path);
    }
}

async fn supports_range(client: &wreq::Client, url: &str) -> bool {
    match tokio::time::timeout(
        Duration::from_secs(30),
        client
            .get(url)
            .header("Range", "bytes=0-0")
            .header("Accept-Encoding", "identity")
            .send(),
    )
    .await
    {
        Ok(Ok(r)) => r.status() == wreq::StatusCode::PARTIAL_CONTENT,
        _ => false,
    }
}

fn write_all_at(file: &File, mut buf: &[u8], mut offset: u64) -> std::io::Result<()> {
    while !buf.is_empty() {
        let n = file.write_at(buf, offset)?;
        if n == 0 {
            return Err(std::io::Error::new(std::io::ErrorKind::WriteZero, "write_at = 0"));
        }
        offset += n as u64;
        buf = &buf[n..];
    }
    Ok(())
}

/// Executa o download. Mantém `.part` e o sidecar em caso de erro/cancelamento.
pub async fn run(client: wreq::Client, job: Job, events: EventFn, cancel: Arc<AtomicBool>) -> Result<()> {
    if job.total == 0 {
        bail!("tamanho total desconhecido");
    }
    if let Some(parent) = job.dest.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let part = part_path(&job.dest);
    let ranged = supports_range(&client, &job.url).await;

    if !ranged {
        return download_single(client, job, events, cancel).await;
    }

    let resumed = load_state(&job.dest, job.total).filter(|_| part.exists());
    let segments = match resumed {
        Some(s) => s,
        None => {
            let s = plan(job.total, job.threads);
            let f = OpenOptions::new().read(true).write(true).create(true).truncate(true).open(&part)?;
            f.set_len(job.total)?;
            drop(f);
            let _ = std::fs::remove_file(sidecar(&job.dest));
            s
        }
    };

    let file = Arc::new(OpenOptions::new().read(true).write(true).open(&part)?);
    let segs = Arc::new(Mutex::new(segments));
    let initial: u64 = segs.lock().unwrap().iter().map(|s| s.done).sum();
    let done = Arc::new(AtomicU64::new(initial));
    let stop_flags = Arc::new(AtomicBool::new(false));

    // Reporter a 5 Hz (200 ms) + persistência do estado a cada 2 s.
    let reporter = {
        let events = events.clone();
        let segs = segs.clone();
        let done = done.clone();
        let stop = stop_flags.clone();
        let total = job.total;
        let dest = job.dest.clone();
        let url = job.url.clone();
        tokio::spawn(async move {
            let mut tick = tokio::time::interval(Duration::from_millis(200));
            let mut save_tick = tokio::time::interval(SAVE_EVERY);
            let t0 = Instant::now();
            let mut last_d = initial;
            let mut last_t = t0;
            let mut speed = 0f64;
            loop {
                tokio::select! {
                    _ = tick.tick() => {
                        let d = done.load(Ordering::Relaxed);
                        let now = Instant::now();
                        let dt = now.duration_since(last_t).as_secs_f64();
                        if dt >= 0.2 {
                            let inst = (d.saturating_sub(last_d)) as f64 / dt;
                            speed = if speed == 0.0 { inst } else { 0.8 * speed + 0.2 * inst };
                            last_d = d;
                            last_t = now;
                        }
                        let eta = if speed > 1.0 { ((total.saturating_sub(d)) as f64 / speed) as u64 } else { 0 };
                        events(DlEvent::Progress(Progress { done: d, total, speed_bps: speed as u64, eta_secs: eta }));
                    }
                    _ = save_tick.tick() => {
                        save_state(&dest, &url, total, &segs);
                    }
                    _ = tokio::time::sleep(Duration::from_millis(100)) => {
                        if stop.load(Ordering::Relaxed) { break; }
                    }
                }
                if stop.load(Ordering::Relaxed) {
                    break;
                }
            }
        })
    };

    let mut set = tokio::task::JoinSet::new();
    {
        let list = segs.lock().unwrap().clone();
        let mut seen = HashSet::new();
        for seg in list {
            if seg.complete() {
                continue;
            }
            if !seen.insert((seg.start, seg.end)) {
                continue;
            }
            let client = client.clone();
            let url = job.url.clone();
            let file = file.clone();
            let segs = segs.clone();
            let done = done.clone();
            let cancel = cancel.clone();
            let retries = job.retries;
            set.spawn(async move { segment(client, url, file, segs, done, cancel, retries, seg).await });
        }
    }

    let mut first_err: Option<Error> = None;
    while let Some(res) = set.join_next().await {
        match res {
            Ok(Ok(())) => {}
            Ok(Err(e)) => {
                if first_err.is_none() {
                    first_err = Some(e);
                }
                cancel.store(true, Ordering::SeqCst);
            }
            Err(e) => {
                if first_err.is_none() {
                    first_err = Some(Error::new(format!("task: {e}")));
                }
                cancel.store(true, Ordering::SeqCst);
            }
        }
    }
    stop_flags.store(true, Ordering::Relaxed);
    let _ = tokio::time::timeout(Duration::from_secs(2), reporter).await;

    save_state(&job.dest, &job.url, job.total, &segs);

    if let Some(e) = first_err {
        return Err(e);
    }
    if cancel.load(Ordering::SeqCst) {
        return Err(Error::new(CANCELLED));
    }

    let finished: u64 = segs.lock().unwrap().iter().map(|s| s.done).sum();
    if finished != job.total {
        bail!("download incompleto: {finished}/{}", job.total);
    }
    let file = Arc::try_unwrap(file).map_err(|_| Error::new("arquivo ainda em uso"))?;
    file.sync_all()?;
    drop(file);
    std::fs::rename(&part, &job.dest)?;
    let _ = std::fs::remove_file(sidecar(&job.dest));
    events(DlEvent::Done);
    Ok(())
}

#[allow(clippy::too_many_arguments)]
async fn segment(
    client: wreq::Client,
    url: String,
    file: Arc<File>,
    segs: Arc<Mutex<Vec<SegState>>>,
    done: Arc<AtomicU64>,
    cancel: Arc<AtomicBool>,
    retries: u32,
    mut seg: SegState,
) -> Result<()> {
    let mut failures = 0u32;
    loop {
        if cancel.load(Ordering::SeqCst) {
            return Err(Error::new(CANCELLED));
        }
        let pos = seg.start + seg.done;
        if pos > seg.end {
            return Ok(());
        }
        let resp = tokio::time::timeout(
            FIRST_BYTE_TIMEOUT,
            client
                .get(&url)
                .header("Range", format!("bytes={pos}-{}", seg.end))
                .header("Accept-Encoding", "identity")
                .send(),
        )
        .await
        .map_err(|_| Error::new("timeout aguardando o primeiro byte"))?
        .map_err(|e| Error::new(format!("http: {e}")))?;

        if resp.status() != wreq::StatusCode::PARTIAL_CONTENT {
            failures += 1;
            if failures > retries {
                bail!("Range retornou HTTP {} (tentativas esgotadas)", resp.status());
            }
            tokio::time::sleep(backoff(failures)).await;
            continue;
        }

        let mut stream = resp.bytes_stream();
        let mut first = true;
        let mut progressed = false;
        // Offset relativo a `pos` nesta requisição (não confundir com seg.done,
        // que já está embutido em `pos`).
        let mut cursor = 0u64;
        loop {
            if cancel.load(Ordering::SeqCst) {
                return Err(Error::new(CANCELLED));
            }
            let wait = if first { FIRST_BYTE_TIMEOUT } else { IO_TIMEOUT };
            match tokio::time::timeout(wait, stream.next()).await {
                Err(_) => break,
                Ok(None) => break,
                Ok(Some(Err(_))) => break,
                Ok(Some(Ok(chunk))) => {
                    first = false;
                    let remaining = seg.len() - seg.done;
                    let take = (chunk.len() as u64).min(remaining) as usize;
                    if take == 0 {
                        break;
                    }
                    if let Err(e) = write_all_at(&file, &chunk[..take], pos + cursor) {
                        bail!("escrita: {e}");
                    }
                    cursor += take as u64;
                    seg.done += take as u64;
                    done.fetch_add(take as u64, Ordering::Relaxed);
                    progressed = true;
                    if let Ok(mut g) = segs.lock() {
                        if let Some(item) = g
                            .iter_mut()
                            .find(|s| s.start == seg.start && s.end == seg.end)
                        {
                            item.done = seg.done;
                        }
                    }
                    if seg.complete() {
                        return Ok(());
                    }
                }
            }
        }
        failures = if progressed { 1 } else { failures + 1 };
        if failures > retries {
            bail!("segmento {}..{} falhou após {retries} tentativas", seg.start, seg.end);
        }
        tokio::time::sleep(backoff(failures)).await;
    }
}

fn backoff(attempt: u32) -> Duration {
    Duration::from_secs(1u64 << attempt.min(5))
}

/// Fallback quando o servidor não suporta Range: um único stream, sem retomada.
async fn download_single(
    client: wreq::Client,
    job: Job,
    events: EventFn,
    cancel: Arc<AtomicBool>,
) -> Result<()> {
    let part = part_path(&job.dest);
    let _ = std::fs::remove_file(sidecar(&job.dest));
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(true)
        .open(&part)?;
    let resp = client
        .get(&job.url)
        .header("Accept-Encoding", "identity")
        .send()
        .await?;
    if !resp.status().is_success() {
        bail!("download retornou HTTP {}", resp.status());
    }
    let mut stream = resp.bytes_stream();
    let mut done = 0u64;
    let t0 = Instant::now();
    let mut last_emit = Instant::now();
    let mut last_d = 0u64;
    let mut speed = 0f64;
    loop {
        if cancel.load(Ordering::SeqCst) {
            return Err(Error::new(CANCELLED));
        }
        match tokio::time::timeout(IO_TIMEOUT, stream.next()).await {
            Err(_) => bail!("timeout de I/O"),
            Ok(None) => break,
            Ok(Some(Err(e))) => bail!("stream: {e}"),
            Ok(Some(Ok(chunk))) => {
                write_all_at(&file, &chunk, done)?;
                done += chunk.len() as u64;
                if last_emit.elapsed() >= Duration::from_millis(200) {
                    let dt = last_emit.elapsed().as_secs_f64().max(0.001);
                    let inst = (done - last_d) as f64 / dt;
                    speed = if speed == 0.0 { inst } else { 0.8 * speed + 0.2 * inst };
                    last_d = done;
                    last_emit = Instant::now();
                    let eta = if speed > 1.0 { ((job.total.saturating_sub(done)) as f64 / speed) as u64 } else { 0 };
                    events(DlEvent::Progress(Progress { done, total: job.total, speed_bps: speed as u64, eta_secs: eta }));
                }
                if done > job.total {
                    bail!("servidor enviou mais bytes que o esperado");
                }
            }
        }
    }
    if done != job.total {
        bail!("download incompleto: {done}/{}", job.total);
    }
    file.sync_all()?;
    drop(file);
    std::fs::rename(&part, &job.dest)?;
    let _ = t0;
    events(DlEvent::Done);
    Ok(())
}
