# Fase 1 — Achados do Buzzheavier (rede real)

Data: 2026-11-13. Ambiente: Linux aarch64 (PRoot), Python 3.14, `curl_cffi` 0.17.0.

## TL;DR

- O Cloudflare **bloqueia `requests` puro** (`HTTP 403`, `cf-mitigated: challenge`). É obrigatório
  impersonation de navegador. No protótipo usei `curl_cffi` com `impersonate="chrome"`; no Rust
  isso implica **`wreq` (impersonation)** ou um TLS stack que reproduza o fingerprint — `reqwest`
  sozinho tende a ser bloqueado.
- Fluxo de resolução **confirmado**:
  1. `GET https://buzzheavier.com/<id>` → HTML contém `download?t=<token>` (token de ~62 chars).
  2. `GET https://buzzheavier.com/<id>/download?t=<token>` com headers
     `hx-request: true`, `hx-current-url: https://buzzheavier.com/<id>`,
     `referer: https://buzzheavier.com/<id>` → **HTTP 204** + header `hx-redirect`
     com a URL direta `https://ts.buzzheavier.com/d/<id>?v=<token>`.
  3. `GET` na URL direta com `Range` → **HTTP 206**.
  - `alt=true` devolve um redirect para `fafda.to` (anúncio) — **rejeitar**.
- Os 3 espelhos (`buzzheavier.com`, `bzzhr.co`, `fuckingfast.net`) respondem em `/api/locations`.
  Neste ambiente o mais rápido foi `buzzheavier.com` (~590 ms), mas a URL direta sempre aponta
  para `ts.buzzheavier.com`.
- `HEAD` na URL direta devolve `Content-Length`, `Accept-Ranges: bytes`,
  `Content-Type: application/zip` e `Content-Disposition` com o nome. `Range: bytes=0-0` também
  devolve `206` + `Content-Range: bytes 0-0/<total>` (fallback robusto quando HEAD falha).

## Tamanhos reais (HEAD em 2026-11-13)

| Parte | id | bytes | GiB | ZIP |
|---|---|---|---|---|
| 1 | `1c155vv0rita` | 7.458.400.970 | 6,95 | válido |
| 2 | `97obt8us2xra` | 8.353.220.442 | 7,78 | válido |
| 3 | `su33tymhwy49` | 8.295.835.482 | 7,73 | válido |
| 4 | `jubunmg13q0n` | 8.344.330.376 | 7,77 | válido |
| 5 | `4l5mmi7khh98` | 8.121.617.858 | 7,56 | válido |
| 6 | `5c6tvtwrqj9i` | 8.481.100.378 | 7,90 | válido |
| 7 | `vos7ziaxo0n5` | 7.958.663.774 | 7,41 | válido |
| 8 | `xtvmzi9p981t` | 8.291.840.354 | 7,72 | válido |
| 9 | `u7t217kroikb` | 3.552.783.908 | 3,31 | válido |
| **Total** | | **68.857.793.542** | **64,13** | |

> Espaço necessário: ~64,13 GiB no destino `game/` + até ~8 GiB de staging (uma parte por vez).
> Não há compressão: `compressed_bytes == uncompressed_bytes` em todas as partes.

## Estrutura dos ZIPs (diretório central lido remotamente via Range)

- Todos são **ZIP clássico** (sem ZIP64 — nenhuma entrada > 4 GiB isolada, < 65535 entradas),
  `store` (sem deflate). CRC é verificável na leitura.
- **Nenhuma parte tem uma única raiz `game/`.** As raízes são `android/`, `update/` e arquivos
  `*.rpf` na raiz. Portanto: **não remover prefixo**, extrair como está dentro de `game/`.
  Resultado esperado: `game/android/...`, `game/update/android/...`, `game/androidc.rpf`, etc.
- Contagem de entradas por parte: 71, 7, 7, 23, 8, 9, 15, 31, 12.
- Inspeção completa em [`parts-inspection.json`](parts-inspection.json).

## Testes executados

1. **Resolução** das 9 partes: OK (URL direta `ts.buzzheavier.com/d/...`).
2. **Download segmentado**: 32 MiB com 4 conexões, `206` por segmento, escrita posicional
   (`os.pwrite`), ~0,8–1,0 MiB/s neste ambiente.
3. **Retomada**: download de 64 MiB interrompido via SIGINT em 8,37%, estado salvo
   (`done` por segmento `[2277250, 401354, 1621890, 1318292]`), retomado e concluído.
   Seis amostras de 1 MiB (início, meio de cada segmento, fronteiras e fim) foram comparadas
   por SHA-256 contra requisições `Range` independentes: **todas idênticas**.
4. **Range em múltiplos segmentos**: OK, inclusive em offset 5.000.000.000.
5. **Cloudflare**: `requests` puro = 403; `curl_cffi` impersonate chrome = 200.
6. **ZIP**: magic `PK\x03\x04`, central directory íntegro, 5 entradas pequenas com CRC validado
   por leitura real.

## Implicações para o Rust

- É preciso impersonation. O crate **`wreq`** expõe a mesma engine do `curl_cffi` (BoringSSL +
  fingerprint de navegador). Plano: `wreq` com `emulation = Chrome` para resolver e baixar.
  Se `wreq` não compilar/estabilizar, fallback documentado: `reqwest` + headers de navegador
  (provavelmente bloqueado) — por isso `wreq` é a escolha primária.
- Fluxo: token na página → `hx-redirect` → URL `ts.buzzheavier.com/d/<id>?v=...`.
  Guardar a URL direta por parte no estado; ela expira, então re-resolver quando necessário.
- `Range` funciona em qualquer offset; segmentação de 4 conexões é viável.
- Nome do arquivo vem de `Content-Disposition` (`filename*=UTF-8''...`).
- Não há pasta `game/` nos ZIPs → extrair direto em `game/`.

## Como reproduzir

```bash
pip install curl_cffi
python3 prototype/bz_download.py probe 1c155vv0rita
python3 prototype/bz_download.py inspect-zip 1c155vv0rita
python3 prototype/bz_download.py download 1c155vv0rita -o /tmp/p1.zip --limit-bytes 33554432
# interrompa com Ctrl+C e rode de novo para retomar
```
