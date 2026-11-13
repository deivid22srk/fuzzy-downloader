#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Protótipo de download do Buzzheavier (Fase 1).

Objetivo: provar, com rede real, o fluxo que depois será portado para Rust:

  1. GET https://buzzheavier.com/<id>            -> extrai token de download
  2. GET .../<id>/download?t=<token> (HX-Request) -> header hx-redirect = URL direta
  3. HEAD / GET Range: bytes=0-0                 -> tamanho, nome, suporte a Range
  4. download segmentado com Range + retomada via sidecar .part.json

Requer `curl_cffi` (impersonation de Chrome) porque o Cloudflare bloqueia
`requests` puro com HTTP 403 + desafio gerenciado.

Uso:
    python3 bz_download.py probe 1c155vv0rita
    python3 bz_download.py inspect-zip 1c155vv0rita
    python3 bz_download.py download 1c155vv0rita -o /tmp/part01.zip --limit-bytes 33554432
    python3 bz_download.py download 1c155vv0rita -o /tmp/part01.zip --threads 4
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import shutil
import signal
import sys
import threading
import time
import zipfile
from dataclasses import dataclass
from email.utils import parsedate_to_datetime
from urllib.parse import urlsplit

from curl_cffi import requests

USER_AGENT = "Buzzheavier Tool"
MIRRORS = ["buzzheavier.com", "bzzhr.co", "fuckingfast.net"]
CHUNK = 64 * 1024
STATE_VERSION = 1
RETRY_LIMIT = 4
FIRST_BYTE_TIMEOUT = 30
IO_TIMEOUT = 12
AD_HOSTS = {"fafda.to"}


# --------------------------------------------------------------------------
# Sessão / resolução
# --------------------------------------------------------------------------
def new_session() -> requests.Session:
    # chrome124+ cobre o fingerprint TLS/HTTP2 que o Cloudflare espera.
    return requests.Session(impersonate="chrome")


def _headers(fid: str, host: str) -> dict:
    return {
        "User-Agent": USER_AGENT,
        "hx-current-url": f"https://{host}/{fid}",
        "hx-request": "true",
        "referer": f"https://{host}/{fid}",
        "Accept-Encoding": "identity",
    }


def measure_mirror(session: requests.Session, host: str) -> float | None:
    """Latência (ms) do endpoint público de espelhos; None se falhar."""
    try:
        t0 = time.perf_counter()
        r = session.get(
            f"https://{host}/api/locations",
            headers={"User-Agent": USER_AGENT, "Accept": "application/json"},
            timeout=(4, 6),
        )
        ms = (time.perf_counter() - t0) * 1000.0
        if r.status_code >= 400:
            return None
        json.loads(r.content.decode("utf-8"))
        return ms
    except Exception:
        return None


def pick_mirror(session: requests.Session) -> str:
    results = []
    for host in MIRRORS:
        ms = measure_mirror(session, host)
        results.append((host, ms))
        print(f"  espelho {host}: {'%.0f ms' % ms if ms is not None else 'falhou'}")
    ok = [(h, ms) for h, ms in results if ms is not None]
    return min(ok, key=lambda x: x[1])[0] if ok else MIRRORS[0]


def fetch_token(session: requests.Session, fid: str, host: str) -> str | None:
    try:
        r = session.get(
            f"https://{host}/{fid}",
            headers={"User-Agent": USER_AGENT, "Accept-Encoding": "identity"},
            timeout=(8, IO_TIMEOUT),
        )
        if r.status_code == 200:
            m = re.search(r'download\?t=([^"\'&\\#]+)', r.text)
            if m:
                return m.group(1)
    except Exception as exc:
        print(f"  token: {type(exc).__name__}: {exc}")
    return None


def _valid_direct(url: str) -> bool:
    parts = urlsplit(url)
    return bool(parts.scheme and parts.netloc
                and parts.netloc not in AD_HOSTS
                and parts.path.startswith("/d/"))


def resolve_direct(session: requests.Session, fid: str, host: str) -> str:
    """Resolve a URL direta /d/... (com token embutido)."""
    token = fetch_token(session, fid, host)
    last = None
    # 1) tentativa normal; 2) fallback alt=true (pode cair em anúncio -> rejeitar)
    for alt in (False, True):
        for attempt in range(1, 4):
            if attempt > 1:
                token = fetch_token(session, fid, host) or token
            qs = []
            if token:
                qs.append("t=" + token)
            if alt:
                qs.append("alt=true")
            url = f"https://{host}/{fid}/download" + ("?" + "&".join(qs) if qs else "")
            try:
                r = session.get(url, headers=_headers(fid, host),
                                allow_redirects=False, timeout=(8, IO_TIMEOUT))
                redirect = r.headers.get("hx-redirect") or r.headers.get("location")
                if r.status_code in (200, 204, 301, 302, 303, 307, 308) and redirect:
                    if _valid_direct(redirect):
                        print(f"  URL direta: {redirect.split('?')[0]}?v=<{len(redirect)} chars>")
                        return redirect
                    last = f"redirect inválido: {redirect[:120]}"
                else:
                    last = f"HTTP {r.status_code} sem redirect"
            except Exception as exc:
                last = f"{type(exc).__name__}: {exc}"
            time.sleep(0.4 * attempt)
    raise RuntimeError(f"não foi possível resolver {fid}: {last}")


# --------------------------------------------------------------------------
# Metadados
# --------------------------------------------------------------------------
@dataclass
class Meta:
    url: str
    size: int
    filename: str
    accepts_ranges: bool


def filename_from_headers(headers, fallback: str) -> str:
    cd = headers.get("content-disposition") or ""
    m = re.search(r"filename\*=UTF-8''([^;]+)", cd)
    if m:
        from urllib.parse import unquote
        return unquote(m.group(1))
    m = re.search(r'filename="?([^";]+)"?', cd)
    return m.group(1) if m else fallback


def probe(session: requests.Session, direct: str) -> Meta:
    """HEAD primeiro; se não vier tamanho, Range 0-0."""
    size = None
    filename = os.path.basename(urlsplit(direct).path.rstrip("/")) or "download"
    accepts = False
    try:
        r = session.head(direct, headers={"User-Agent": USER_AGENT},
                         allow_redirects=True, timeout=(10, IO_TIMEOUT))
        if r.status_code in (200, 206):
            accepts = (r.headers.get("accept-ranges") or "").lower() == "bytes"
            filename = filename_from_headers(r.headers, filename)
            cl = r.headers.get("content-length")
            if cl and cl.isdigit():
                size = int(cl)
    except Exception:
        pass
    if size is None or size == 0:
        r = session.get(direct, headers={"User-Agent": USER_AGENT, "Range": "bytes=0-0"},
                        allow_redirects=True, timeout=(10, FIRST_BYTE_TIMEOUT))
        if r.status_code != 206:
            raise RuntimeError(f"servidor não devolveu 206 no probe: HTTP {r.status_code}")
        cr = r.headers.get("content-range") or ""
        m = re.search(r"/(\d+)$", cr)
        if not m:
            raise RuntimeError(f"Content-Range inesperado: {cr!r}")
        size = int(m.group(1))
        accepts = True
        filename = filename_from_headers(r.headers, filename)
        r.close()
    return Meta(direct, size, filename, accepts)


# --------------------------------------------------------------------------
# Download segmentado com retomada
# --------------------------------------------------------------------------
@dataclass
class Segment:
    start: int
    end: int          # inclusivo
    done: int = 0     # bytes já gravados a partir de start

    @property
    def remaining(self) -> int:
        return self.end - self.start + 1 - self.done


def _save_state(path: str, meta: Meta, segs: list[Segment]) -> None:
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fp:
        json.dump({
            "version": STATE_VERSION,
            "url": meta.url,
            "size": meta.size,
            "filename": meta.filename,
            "segments": [[s.start, s.end, s.done] for s in segs],
        }, fp)
    os.replace(tmp, path)


def _load_state(path: str, meta: Meta) -> list[Segment] | None:
    try:
        with open(path, encoding="utf-8") as fp:
            data = json.load(fp)
        if data.get("version") != STATE_VERSION or data.get("size") != meta.size:
            return None
        return [Segment(int(a), int(b), int(c)) for a, b, c in data["segments"]]
    except Exception:
        return None


def _plan(total: int, threads: int) -> list[Segment]:
    threads = max(1, min(8, threads, total))
    base, extra = divmod(total, threads)
    segs, start = [], 0
    for i in range(threads):
        n = base + (1 if i < extra else 0)
        segs.append(Segment(start, start + n - 1))
        start += n
    return segs


class Progress:
    """Callback a ~5 Hz, com velocidade em média móvel e ETA."""

    def __init__(self, total: int, already: int, tty: bool):
        self.total, self.t0 = total, time.time()
        self.already = already
        self.done = already
        self.tty = tty
        self._speed = 0.0
        self._last_t = self.t0
        self._last_d = already

    def update(self, done: int) -> None:
        self.done = done
        now = time.time()
        dt = now - self._last_t
        if dt >= 0.2:
            inst = (done - self._last_d) / dt
            self._speed = inst if self._speed == 0 else 0.8 * self._speed + 0.2 * inst
            self._last_t, self._last_d = now, done
        pct = done * 100.0 / self.total if self.total else 0.0
        eta = (self.total - done) / self._speed if self._speed > 1 else None
        eta_s = f"{int(eta // 3600):02d}:{int(eta % 3600 // 60):02d}:{int(eta % 60):02d}" if eta else "--:--:--"
        line = (f"{pct:6.2f}%  {done/2**20:8.1f}/{self.total/2**20:.1f} MiB  "
                f"{self._speed/2**20:6.2f} MiB/s  ETA {eta_s}")
        if self.tty:
            sys.stdout.write("\r" + line)
        else:
            sys.stdout.write(line + "\n")
        sys.stdout.flush()


def download(session: requests.Session, meta: Meta, out: str, threads: int,
             limit_bytes: int | None = None, cancel: threading.Event | None = None) -> str:
    """Baixa meta.url para `out` (com .part + sidecar). Retorna o caminho final."""
    cancel = cancel or threading.Event()
    total = meta.size if limit_bytes is None else min(meta.size, limit_bytes)
    part = out + ".part"
    state = out + ".part.json"

    os.makedirs(os.path.dirname(os.path.abspath(out)) or ".", exist_ok=True)
    segs = _load_state(state, meta)
    if segs is None or not os.path.exists(part):
        segs = _plan(total, threads)
        with open(part, "wb") as fp:
            fp.truncate(total)  # pré-aloca (sparse; barato)
        _save_state(state, meta, segs)
        fresh = True
    else:
        # Garante que o plano cobre exatamente `total` (limit pode ter mudado).
        covered = sum(s.end - s.start + 1 for s in segs)
        if covered != total:
            segs = _plan(total, threads)
            with open(part, "wb") as fp:
                fp.truncate(total)
            _save_state(state, meta, segs)
            fresh = True
        else:
            fresh = False
    already = sum(s.done for s in segs)
    print(f"  {'iniciando' if fresh else 'retomando'}: {already/2**20:.1f} MiB de {total/2**20:.1f} MiB, {len(segs)} conexões")

    lock = threading.Lock()
    done_total = [already]
    progress = Progress(total, already, sys.stdout.isatty())
    stop = threading.Event()
    errors: list[BaseException] = []
    save_stop = threading.Event()

    def saver() -> None:
        while not save_stop.wait(2.0):
            with lock:
                _save_state(state, meta, segs)

    def worker(seg: Segment) -> None:
        attempt = 0
        while not stop.is_set():
            try:
                pos = seg.start + seg.done
                if pos > seg.end:
                    return
                headers = {"User-Agent": USER_AGENT, "Range": f"bytes={pos}-{seg.end}",
                           "Accept-Encoding": "identity"}
                r = session.get(meta.url, headers=headers, stream=True,
                                timeout=(10, FIRST_BYTE_TIMEOUT))
                if r.status_code != 206:
                    r.close()
                    raise RuntimeError(f"Range retornou HTTP {r.status_code}")
                with open(part, "r+b", buffering=0) as fp:
                    for chunk in r.iter_content(CHUNK):
                        if stop.is_set():
                            r.close()
                            return
                        if not chunk:
                            continue
                        n = len(chunk)
                        if seg.done + n > seg.end - seg.start + 1:
                            raise RuntimeError("servidor enviou mais bytes que o Range pedido")
                        os.pwrite(fp.fileno(), chunk, seg.start + seg.done)
                        seg.done += n
                        with lock:
                            done_total[0] += n
                            snap = done_total[0]
                        progress.update(snap)
                r.close()
                if seg.done == seg.end - seg.start + 1:
                    return
                raise RuntimeError("conexão terminou antes do fim do segmento")
            except Exception as exc:
                if stop.is_set():
                    return
                attempt += 1
                if attempt > RETRY_LIMIT:
                    with lock:
                        errors.append(exc)
                    stop.set()
                    return
                wait = min(30, 2 ** (attempt - 1))
                print(f"\n  segmento {seg.start}: {type(exc).__name__}: {exc} (retry {attempt}/{RETRY_LIMIT} em {wait}s)")
                stop.wait(wait)
                # resincroniza com o arquivo (outro worker pode ter avançado? não: dono único)
                attempt = attempt if attempt < RETRY_LIMIT else attempt

    saver_thread = threading.Thread(target=saver, daemon=True)
    saver_thread.start()
    from concurrent.futures import ThreadPoolExecutor
    with ThreadPoolExecutor(max_workers=len(segs)) as pool:
        futures = [pool.submit(worker, seg) for seg in segs]
        while any(not f.done() for f in futures):
            if cancel.wait(0.2):
                stop.set()
                break
    save_stop.set()
    saver_thread.join()

    with lock:
        _save_state(state, meta, segs)

    if cancel.is_set():
        raise KeyboardInterrupt
    if errors:
        raise errors[0]
    if sum(s.done for s in segs) != total:
        raise RuntimeError("download incompleto")

    with open(part, "r+b") as fp:
        os.fsync(fp.fileno())
    os.replace(part, out)
    try:
        os.remove(state)
    except FileNotFoundError:
        pass
    print(f"\n  concluído: {out} ({total} bytes)")
    return out


# --------------------------------------------------------------------------
# Leitura remota do ZIP (central directory via Range)
# --------------------------------------------------------------------------
class RangeReader(io.RawIOBase):
    """Arquivo somente-leitura sobre HTTP Range (para o zipfile)."""

    BLOCK = 1 << 20

    def __init__(self, session: requests.Session, url: str, size: int):
        self.session, self.url, self.size = session, url, size
        self.pos = 0
        self._buf = b""
        self._buf_off = 0

    def readable(self):
        return True

    def seekable(self):
        return True

    def tell(self):
        return self.pos

    def seek(self, off, whence=io.SEEK_SET):
        if whence == io.SEEK_SET:
            self.pos = off
        elif whence == io.SEEK_CUR:
            self.pos += off
        elif whence == io.SEEK_END:
            self.pos = self.size + off
        self.pos = max(0, min(self.pos, self.size))
        return self.pos

    def read(self, n=-1):
        if n is None or n < 0:
            n = self.size - self.pos
        n = min(n, self.size - self.pos)
        if n <= 0:
            return b""
        if not (self._buf_off <= self.pos and self.pos + n <= self._buf_off + len(self._buf)):
            start = self.pos
            end = min(self.size - 1, start + max(n, self.BLOCK) - 1)
            r = self.session.get(self.url, headers={
                "User-Agent": USER_AGENT, "Range": f"bytes={start}-{end}",
                "Accept-Encoding": "identity"}, timeout=(10, 60))
            if r.status_code != 206:
                raise IOError(f"Range falhou: HTTP {r.status_code}")
            self._buf, self._buf_off = r.content, start
        off = self.pos - self._buf_off
        data = self._buf[off:off + n]
        self.pos += len(data)
        return data


def inspect_zip(session: requests.Session, meta: Meta, sample: int = 12) -> dict:
    if meta.size < 22:
        raise RuntimeError("arquivo pequeno demais para ser ZIP")
    r = session.get(meta.url, headers={"User-Agent": USER_AGENT, "Range": "bytes=0-3",
                                       "Accept-Encoding": "identity"}, timeout=(10, 30))
    magic = r.content[:4]
    if magic[:2] != b"PK":
        raise RuntimeError(f"não é ZIP: magic={magic!r} (rar/7z/split?)")

    zf = zipfile.ZipFile(RangeReader(session, meta.url, meta.size))
    infos = zf.infolist()
    files = [i for i in infos if not i.is_dir()]
    dirs = [i for i in infos if i.is_dir()]
    uncompressed = sum(i.file_size for i in files)
    compressed = sum(i.compress_size for i in files)
    roots = {i.filename.split("/", 1)[0] for i in infos if i.filename}
    all_prefixed_game = all(i.filename == "game" or i.filename.startswith("game/") for i in infos)
    zip64 = any(i.file_size > 0xFFFFFFFF or i.compress_size > 0xFFFFFFFF for i in infos) or len(infos) > 0xFFFF
    names = [i.filename for i in infos[:sample]]
    # CRC: valida de verdade as primeiras entradas pequenas (<4 MiB) sem baixar tudo.
    crc_checked = 0
    for info in files:
        if info.file_size > 4 * 2**20:
            continue
        with zf.open(info) as fp:
            while fp.read(1 << 20):
                pass
        crc_checked += 1
        if crc_checked >= 5:
            break
    result = {
        "valid": True,
        "entries": len(infos),
        "files": len(files),
        "dirs": len(dirs),
        "uncompressed_bytes": uncompressed,
        "compressed_bytes": compressed,
        "roots": sorted(roots),
        "single_game_root": all_prefixed_game and roots == {"game"},
        "zip64": zip64,
        "sample": names,
        "crc_checked_entries": crc_checked,
    }
    zf.close()
    return result


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------
def _setup_sigint(cancel: threading.Event) -> None:
    def handler(signum, frame):
        print("\n  cancelando (estado salvo)...")
        cancel.set()
    signal.signal(signal.SIGINT, handler)


def main() -> int:
    ap = argparse.ArgumentParser(description="Protótipo Buzzheavier (Fase 1)")
    ap.add_argument("action", choices=["probe", "inspect-zip", "download", "mirror"])
    ap.add_argument("file_id")
    ap.add_argument("-o", "--output", default=None)
    ap.add_argument("--host", default=None, help="força espelho (padrão: mede latência)")
    ap.add_argument("--threads", type=int, default=4)
    ap.add_argument("--limit-bytes", type=int, default=None)
    args = ap.parse_args()

    session = new_session()
    host = args.host
    if not host:
        print("medindo espelhos...")
        host = pick_mirror(session)
        print(f"  selecionado: {host}")
    if args.action == "mirror":
        return 0

    print(f"resolvendo {args.file_id} em {host}...")
    direct = resolve_direct(session, args.file_id, host)
    meta = probe(session, direct)
    print(f"  arquivo: {meta.filename}")
    print(f"  tamanho: {meta.size} bytes ({meta.size/2**30:.2f} GiB)")
    print(f"  ranges: {'sim' if meta.accepts_ranges else 'não'}")

    if args.action == "probe":
        return 0

    if args.action == "inspect-zip":
        info = inspect_zip(session, meta)
        print(json.dumps(info, indent=2, ensure_ascii=False))
        return 0

    out = args.output or meta.filename
    cancel = threading.Event()
    _setup_sigint(cancel)
    t0 = time.time()
    try:
        download(session, meta, out, args.threads, args.limit_bytes, cancel)
    except KeyboardInterrupt:
        print("  interrompido; use o mesmo comando para retomar.")
        return 130
    dt = time.time() - t0
    b = os.path.getsize(out)
    print(f"  {b/2**20:.2f} MiB em {dt:.1f}s ({b/dt/2**20:.2f} MiB/s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
