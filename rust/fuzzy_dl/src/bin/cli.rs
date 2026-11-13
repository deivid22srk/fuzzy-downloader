use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use fuzzy_dl::client::{build_client, probe};
use fuzzy_dl::download::{run, DlEvent, EventFn, Job, Progress, CANCELLED};

fn usage() -> ! {
    eprintln!("uso:\n  fuzzy-dl probe <url>\n  fuzzy-dl download <url> -o <arquivo> [--threads N]\n");
    std::process::exit(2)
}

fn arg_value(args: &[String], name: &str) -> Option<String> {
    args.iter().position(|a| a == name).and_then(|i| args.get(i + 1)).cloned()
}

fn fmt_bytes(n: u64) -> String {
    if n >= 1 << 30 {
        format!("{:.2} GiB", n as f64 / (1u64 << 30) as f64)
    } else {
        format!("{:.1} MiB", n as f64 / (1u64 << 20) as f64)
    }
}

fn fmt_eta(secs: u64) -> String {
    format!("{:02}:{:02}:{:02}", secs / 3600, secs % 3600 / 60, secs % 60)
}

fn print_progress(p: &Progress) {
    let pct = if p.total > 0 { p.done as f64 * 100.0 / p.total as f64 } else { 0.0 };
    eprint!(
        "\r{:6.2}%  {} / {}  {}/s  ETA {}   ",
        pct,
        fmt_bytes(p.done),
        fmt_bytes(p.total),
        fmt_bytes(p.speed_bps),
        fmt_eta(p.eta_secs)
    );
}

#[tokio::main(flavor = "multi_thread", worker_threads = 2)]
async fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    if args.len() < 2 {
        usage();
    }
    let cmd = &args[0];
    let url = &args[1];
    let client = build_client(true).expect("cliente");

    match cmd.as_str() {
        "probe" => {
            let r = probe(&client, url).await.unwrap_or_else(|e| {
                eprintln!("erro: {e}");
                std::process::exit(1)
            });
            println!("{}", serde_json::to_string_pretty(&r).unwrap());
        }
        "download" => {
            let out = arg_value(&args, "-o").unwrap_or_else(|| usage());
            let threads: usize = arg_value(&args, "--threads").and_then(|v| v.parse().ok()).unwrap_or(4);
            let r = probe(&client, url).await.unwrap_or_else(|e| {
                eprintln!("erro ao sondar: {e}");
                std::process::exit(1)
            });
            eprintln!("{} ({} bytes)\n{} -> {}", r.name, r.size, url, out);
            let cancel = Arc::new(AtomicBool::new(false));
            let c2 = cancel.clone();
            tokio::spawn(async move {
                if tokio::signal::ctrl_c().await.is_ok() {
                    c2.store(true, Ordering::SeqCst);
                    eprintln!("\ncancelando (estado salvo)...");
                }
            });
            let events: EventFn = Arc::new(|ev| match ev {
                DlEvent::Progress(p) => print_progress(&p),
                DlEvent::Done => eprintln!("\nconcluído"),
                DlEvent::Cancelled => eprintln!("\ncancelado"),
                DlEvent::Error(m) => eprintln!("\nerro: {m}"),
            });
            let job = Job {
                url: r.url,
                dest: PathBuf::from(out),
                total: r.size,
                threads,
                retries: 4,
            };
            let t0 = std::time::Instant::now();
            match run(client, job, events, cancel).await {
                Ok(()) => {
                    let dt = t0.elapsed().as_secs_f64();
                    eprintln!("ok em {:.1}s ({}/s)", dt, fmt_bytes((r.size as f64 / dt) as u64));
                }
                Err(e) if e.0 == CANCELLED => {
                    eprintln!("cancelado; rode de novo para retomar.");
                    std::process::exit(130);
                }
                Err(e) => {
                    eprintln!("erro: {e}");
                    std::process::exit(1);
                }
            }
        }
        _ => usage(),
    }
}
