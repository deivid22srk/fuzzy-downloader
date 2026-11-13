//! Cliente HTTP e sondagem genérica de arquivos (sem nada específico de site).
//!
//! O mesmo cliente serve para qualquer item do catálogo: descobre tamanho e
//! nome do arquivo via `HEAD` (com fallback para `Range: bytes=0-0`) e depois
//! alimenta o motor de download segmentado em `download.rs`.

use std::time::Duration;

use wreq::redirect::Policy;

use crate::error::{Error, Result};
use crate::bail;

#[derive(Debug, Clone, serde::Serialize)]
pub struct RemoteFile {
    pub url: String,
    pub size: u64,
    pub name: String,
}

/// Cliente genérico. Segue redirecionamentos quando pedido (GitHub Releases,
/// mirrors) e não faz impersonation: o catálogo só aponta para hosts comuns.
pub fn build_client(follow_redirects: bool) -> Result<wreq::Client> {
    let policy = if follow_redirects {
        Policy::limited(10)
    } else {
        Policy::none()
    };
    Ok(wreq::Client::builder()
        .redirect(policy)
        .connect_timeout(Duration::from_secs(15))
        .read_timeout(Duration::from_secs(60))
        .user_agent("FuzzyDownloader/2.0")
        .build()?)
}

fn header_str(resp: &wreq::Response, name: &str) -> Option<String> {
    resp.headers()
        .get(name)
        .and_then(|v| v.to_str().ok())
        .map(str::to_string)
}

fn percent_decode(s: &str) -> String {
    let bytes = s.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            if let Ok(v) = u8::from_str_radix(&s[i + 1..i + 3], 16) {
                out.push(v);
                i += 3;
                continue;
            }
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

fn parse_content_disposition(cd: &str) -> Option<String> {
    if let Some(pos) = cd.find("filename*=UTF-8''") {
        let rest = &cd[pos + "filename*=UTF-8''".len()..];
        let end = rest.find(';').unwrap_or(rest.len());
        return Some(percent_decode(rest[..end].trim()));
    }
    if let Some(pos) = cd.find("filename=") {
        let rest = cd[pos + "filename=".len()..].trim();
        let end = rest.find(';').unwrap_or(rest.len());
        return Some(rest[..end].trim_matches('"').to_string());
    }
    None
}

fn parse_size(headers: &wreq::header::HeaderMap) -> Option<u64> {
    if let Some(cr) = headers.get("content-range").and_then(|v| v.to_str().ok()) {
        if let Some((_, total)) = cr.rsplit_once('/') {
            if let Ok(n) = total.trim().parse() {
                return Some(n);
            }
        }
    }
    headers
        .get("content-length")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.parse().ok())
}

fn fallback_name(url: &str) -> String {
    url.split('?')
        .next()
        .unwrap_or(url)
        .trim_end_matches('/')
        .rsplit('/')
        .next()
        .unwrap_or("download")
        .to_string()
}

fn name_of(resp: &wreq::Response, url: &str) -> String {
    header_str(resp, "content-disposition")
        .as_deref()
        .and_then(parse_content_disposition)
        .unwrap_or_else(|| fallback_name(url))
}

/// Descobre tamanho e nome de um arquivo.
pub async fn probe(client: &wreq::Client, url: &str) -> Result<RemoteFile> {
    if let Ok(resp) = client
        .head(url)
        .header("Accept-Encoding", "identity")
        .send()
        .await
    {
        if resp.status().is_success() {
            if let Some(size) = parse_size(resp.headers()) {
                return Ok(RemoteFile {
                    url: url.to_string(),
                    size,
                    name: name_of(&resp, url),
                });
            }
        }
    }

    let resp = client
        .get(url)
        .header("Range", "bytes=0-0")
        .header("Accept-Encoding", "identity")
        .send()
        .await?;
    let status = resp.status();
    if status != wreq::StatusCode::PARTIAL_CONTENT && !status.is_success() {
        bail!("servidor respondeu HTTP {status}");
    }
    let size = parse_size(resp.headers())
        .ok_or_else(|| Error::new("servidor não informou Content-Length/Content-Range"))?;
    Ok(RemoteFile {
        url: url.to_string(),
        size,
        name: name_of(&resp, url),
    })
}

/// Heurística simples para decidir se o link é um arquivo baixável direto.
pub fn is_direct_file(url: &str) -> bool {
    let path = url.split('?').next().unwrap_or(url).to_lowercase();
    [".apk", ".zip", ".7z", ".obb", ".tar", ".gz", ".xz"]
        .iter()
        .any(|ext| path.ends_with(ext))
}
