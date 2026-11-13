use std::time::{Duration, Instant};

use serde::Serialize;
use wreq::redirect::Policy;
use wreq_util::Emulation;

use crate::error::{Error, Result};
use crate::{bail};

pub const MIRRORS: [&str; 3] = ["buzzheavier.com", "bzzhr.co", "fuckingfast.net"];
const AD_HOSTS: [&str; 1] = ["fafda.to"];

#[derive(Debug, Clone, Serialize)]
pub struct Resolved {
    pub url: String,
    pub size: u64,
    pub name: String,
    pub mirror: String,
}

/// Cliente com fingerprint de Chrome (o Cloudflare bloqueia clients "crus").
pub fn build_client(follow_redirects: bool) -> Result<wreq::Client> {
    let policy = if follow_redirects {
        Policy::limited(8)
    } else {
        Policy::none()
    };
    Ok(wreq::Client::builder()
        .emulation(Emulation::Chrome146)
        .redirect(policy)
        .connect_timeout(Duration::from_secs(12))
        .read_timeout(Duration::from_secs(45))
        .build()?)
}

fn file_id(page_url: &str) -> Result<String> {
    let path = page_url
        .split(['?', '#'])
        .next()
        .unwrap_or(page_url)
        .trim_end_matches('/');
    let id = path.rsplit('/').next().unwrap_or("");
    if id.is_empty() || id.len() > 64 || !id.chars().all(|c| c.is_ascii_alphanumeric()) {
        bail!("id inválido na URL: {page_url}");
    }
    Ok(id.to_string())
}

/// Extrai o token de `download?t=...` do HTML da página.
pub fn parse_token(html: &str) -> Option<String> {
    let start = html.find("download?t=")? + "download?t=".len();
    let rest = &html[start..];
    let end = rest
        .find(|c: char| c == '"' || c == '\'' || c == '&' || c == '\\' || c == '#')
        .unwrap_or(rest.len());
    let token = &rest[..end];
    if token.len() < 16 { None } else { Some(token.to_string()) }
}

pub fn is_valid_direct(url: &str) -> bool {
    let Ok(u) = url.parse::<wreq::Uri>() else { return false };
    let host = u.host().unwrap_or("");
    u.scheme_str() == Some("https")
        && !AD_HOSTS.contains(&host)
        && u.path().starts_with("/d/")
        && u.path().len() > 3
}

async fn mirror_latency(client: &wreq::Client, host: &str) -> Option<(String, u128)> {
    let t0 = Instant::now();
    let resp = tokio::time::timeout(
        Duration::from_secs(5),
        client
            .get(format!("https://{host}/api/locations"))
            .header("Accept", "application/json")
            .send(),
    )
    .await
    .ok()?
    .ok()?;
    if !resp.status().is_success() {
        return None;
    }
    Some((host.to_string(), t0.elapsed().as_millis()))
}

pub async fn pick_mirror(client: &wreq::Client) -> String {
    let probes = MIRRORS.map(|h| mirror_latency(client, h));
    let results = futures_util::future::join_all(probes).await;
    results
        .into_iter()
        .flatten()
        .min_by_key(|(_, ms)| *ms)
        .map(|(h, _)| h)
        .unwrap_or_else(|| MIRRORS[0].to_string())
}

async fn fetch_token(client: &wreq::Client, host: &str, id: &str) -> Result<String> {
    let resp = client
        .get(format!("https://{host}/{id}"))
        .header("Accept-Encoding", "identity")
        .send()
        .await?;
    if !resp.status().is_success() {
        bail!("página do arquivo retornou HTTP {}", resp.status());
    }
    let body = resp.bytes().await?;
    let html = String::from_utf8_lossy(&body);
    parse_token(&html).ok_or_else(|| Error::new("token de download não encontrado na página"))
}

fn header_str(resp: &wreq::Response, name: &str) -> Option<String> {
    resp.headers()
        .get(name)
        .and_then(|v| v.to_str().ok())
        .map(str::to_string)
}

async fn request_redirect(
    client: &wreq::Client,
    host: &str,
    id: &str,
    token: &str,
    alt: bool,
) -> Result<String> {
    let mut url = format!("https://{host}/{id}/download?t={token}");
    if alt {
        url.push_str("&alt=true");
    }
    let resp = client
        .get(url)
        .header("hx-current-url", format!("https://{host}/{id}"))
        .header("hx-request", "true")
        .header("referer", format!("https://{host}/{id}"))
        .header("Accept-Encoding", "identity")
        .send()
        .await?;
    let status = resp.status();
    let redirect = header_str(&resp, "hx-redirect")
        .or_else(|| header_str(&resp, "location"))
        .ok_or_else(|| Error::new(format!("HTTP {status} sem hx-redirect")))?;
    if !is_valid_direct(&redirect) {
        bail!("redirect inválido (anúncio ou página): {redirect}");
    }
    Ok(redirect)
}

fn parse_content_disposition(cd: &str) -> Option<String> {
    if let Some(pos) = cd.find("filename*=UTF-8''") {
        let rest = &cd[pos + "filename*=UTF-8''".len()..];
        let end = rest.find(';').unwrap_or(rest.len());
        return Some(percent_decode(rest[..end].trim()));
    }
    if let Some(pos) = cd.find("filename=") {
        let rest = cd[pos + "filename=".len()..].trim();
        let rest = rest.trim_matches('"');
        let end = rest.find(';').unwrap_or(rest.len());
        return Some(rest[..end].trim_matches('"').to_string());
    }
    None
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

async fn probe(client: &wreq::Client, direct: &str) -> Result<(u64, String)> {
    let fallback_name = direct
        .split('?')
        .next()
        .unwrap_or(direct)
        .trim_end_matches('/')
        .rsplit('/')
        .next()
        .unwrap_or("download")
        .to_string();

    // HEAD é barato; alguns CDNs recusam, então há fallback com Range 0-0.
    if let Ok(resp) = client
        .head(direct)
        .header("Accept-Encoding", "identity")
        .send()
        .await
    {
        if resp.status().is_success() {
            if let Some(size) = parse_size(resp.headers()) {
                let name = header_str(&resp, "content-disposition")
                    .as_deref()
                    .and_then(parse_content_disposition)
                    .unwrap_or(fallback_name);
                return Ok((size, name));
            }
        }
    }

    let resp = client
        .get(direct)
        .header("Range", "bytes=0-0")
        .header("Accept-Encoding", "identity")
        .send()
        .await?;
    let status = resp.status();
    if status != wreq::StatusCode::PARTIAL_CONTENT && !status.is_success() {
        bail!("probe Range retornou HTTP {status}");
    }
    let size = parse_size(resp.headers())
        .ok_or_else(|| Error::new("servidor não informou Content-Length/Content-Range"))?;
    let name = header_str(&resp, "content-disposition")
        .as_deref()
        .and_then(parse_content_disposition)
        .unwrap_or(fallback_name);
    Ok((size, name))
}

/// Resolve a URL de uma página `https://buzzheavier.com/<id>` até o arquivo direto.
pub async fn resolve(
    client: &wreq::Client,
    page_url: &str,
    forced_mirror: Option<&str>,
) -> Result<Resolved> {
    let id = file_id(page_url)?;
    let direct_client = build_client(false)?;

    // Se já é uma URL direta /d/, só mede.
    if page_url.contains("/d/") && is_valid_direct(page_url) {
        let (size, name) = probe(client, page_url).await?;
        return Ok(Resolved {
            url: page_url.to_string(),
            size,
            name,
            mirror: "direto".into(),
        });
    }

    let mirror = match forced_mirror {
        Some(m) if MIRRORS.contains(&m) => m.to_string(),
        _ => pick_mirror(client).await,
    };

    let token = fetch_token(&direct_client, &mirror, &id).await?;

    let mut last_err = Error::new("falha ao resolver");
    for alt in [false, true] {
        for attempt in 0..3 {
            if attempt > 0 {
                tokio::time::sleep(Duration::from_millis(400 * attempt as u64)).await;
            }
            match request_redirect(&direct_client, &mirror, &id, &token, alt).await {
                Ok(url) => {
                    let (size, name) = probe(client, &url).await?;
                    return Ok(Resolved { url, size, name, mirror });
                }
                Err(e) => last_err = e,
            }
        }
    }
    Err(last_err)
}
