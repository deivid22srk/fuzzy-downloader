# Decisões de arquitetura

Registro curto do porquê de cada escolha relevante. Ordem cronológica.

## 1. Resolver o Buzzheavier no Rust, não no Kotlin

O Cloudflare devolve **403** para clientes sem fingerprint de navegador
(`requests` puro foi reproduzido). A fase 1 provou que `curl_cffi` com
`impersonate="chrome"` funciona. No app, a mesma engine é obtida com **`wreq`
0.16 + BoringSSL** (`Emulation::Chrome146`), que fala TLS/HTTP como o Chrome.

Fluxo confirmado (detalhes em `docs/findings.md`):

```
GET /<id>                                  → HTML com download?t=<token>
GET /<id>/download?t=<token>  hx-request   → 204 + hx-redirect
GET https://ts.buzzheavier.com/d/<id>?v=…  → 206 + Content-Range
```

`alt=true` redireciona para `fafda.to` (anúncio) e é explicitamente rejeitado.

## 2. Rust para o transporte, Kotlin para o produto

- O Rust concentra resolução, Range, retomada, retry e cancelamento; o Kotlin
  orquestra o pipeline e a UI. A ponte é JNI com um `handle` inteiro (permite
  cancelar sem travar a thread da UI) e eventos 0..3.
- O mesmo crate expõe um **CLI desktop** (`fuzzy-dl probe|download`), usado nos
  testes e em diagnóstico sem aparelho.
- `cdylib` + `cargo-ndk`, ABI única `arm64-v8a` (reduz tamanho e é a ABI de todos
  os aparelhos relevantes).

## 3. Staging em `getExternalFilesDir("staging")`

`Android/data/<outro app>` não é gravável pelo app comum. Duas alternativas:

- gravar o ZIP em `Android/data/com.fuzzydl.app/files/staging` (Plano A,
  escolhido) e extrair via Shizuku;
- gravar direto no destino (exigiria Shizuku para o download inteiro).

O Plano A mantém o download no processo do app (mais simples de retomar e
observar), e o Shizuku só é usado para ler o ZIP e escrever no destino. O shell
consegue ler `Android/data` de outros apps via UserService.

## 4. Shizuku UserService, não `su`

`su` não existe em aparelhos sem root. O UserService roda com UID de shell
(2000), tem acesso a `Android/data` e é iniciado pelo próprio Shizuku. AIDL:

- `IFileService.extractZip(zip, dest, cb)` — síncrono no processo do serviço,
  com `IExtractCallback` `oneway` para progresso;
- `freeBytes`, `exists`, `mkdirs`, `delete` para checagens pontuais.

`ZipExtractor` é Kotlin puro (`java.io` + `java.util.zip`) justamente para rodar
nos dois lados: no UserService e nos testes JVM.

## 5. Um ZIP por vez, apagar só depois de conferir

O pico de armazenamento seria ~128 GiB se o app baixasse tudo antes de extrair.
O pipeline baixa uma parte, valida tamanho/assinatura/diretório central, extrai,
compara **contagem de arquivos e soma de bytes** com o diretório central e só
então apaga ZIP + sidecar. Qualquer divergência mantém o ZIP e marca `FAILED`.

Se o processo morrer entre o download e a extração, o ZIP completo que ficou em
staging é reaproveitado (tamanho + assinatura + diretório central) em vez de ser
rebaixado. `DONE` é persistido **antes** de apagar ZIP + sidecar: um kill nessa
janela deixa apenas um ZIP órfão, que a limpeza global remove, e não um novo
download de vários GiB.

## 6. Retomada com `.part` + sidecar

O arquivo `.part` é pré-alocado no tamanho total (`setLength`) e escrito com
`pwrite` em offsets absolutos. O sidecar JSON guarda `start/end/done` por
segmento, além de URL e total. Na retomada, só os trechos pendentes são
requisitados. Um `fsync`/save a cada 2 s limita a perda a segundos.

O cancelamento explícito apaga `.part` e sidecar; a pausa os preserva.

## 7. Sem Material3

A UI é Compose **Foundation** pura: `BasicText`, `Canvas`, `Box`, `clickable`,
`AnimatedVisibility`. Isso evita a dependência e dá controle total ao visual
"vitrine de vidro sobre um céu noturno", inspirado na linguagem de loja de jogos
do app PlayStation sem copiar marcas: fundo `#070B16`, brilhos radiais azul e
violeta, cards translúcidos com borda em gradiente, capas procedurais e uma
barra inferior flutuante. A tipografia é Manrope (variável, OFL), com algarismos
tabulares; o anel de 60 pontos desenhado no Canvas continua sendo a assinatura.

O progresso do anel é lido **dentro da fase de desenho** (`DotRing(progress = {
... })`), então a 10 Hz não recompoem a árvore inteira. O "vidro" usa
transparência + gradientes (funciona em qualquer API); o desfoque real fica
reservado ao fundo ambiente, que já é um gradiente suave.

## 8. Kotlin 2.4.21 + AGP 9.5.1 com Kotlin embutido

O AGP 9 compila Kotlin sem o plugin `kotlin-android`, mas carrega o KGP 2.2.10.
O plugin do Compose precisa casar com o compilador, então o `build.gradle.kts`
raiz adiciona `kotlin-gradle-plugin:2.4.21` ao classpath do buildscript. A tabela
de compatibilidade do AGP indica suporte a Kotlin 2.4 (AGP 8.5.2+).

## 9. Persistência e dependências mínimas

- `parts.json` em assets é a única fonte de IDs/URLs.
- Estado em `filesDir/state.json` via `org.json` (sem kotlinx.serialization).
- Nada de Hilt, Retrofit, Coil, Gson, Room ou WorkManager: o serviço em primeiro
  plano + `StateFlow` + `SharedPreferences` cobrem o caso.
- `minSdk 26`: abaixo disso não há `Android/data` por app nem Shizuku moderno.

## 10. Assinatura e CI

O APK é compilado **somente no GitHub Actions** (decisão do projeto). O keystore
é criado fora do repositório e exposto ao workflow por secrets em base64. Sem
secrets, o build usa a chave de debug para permitir PRs de terceiros. R8 full
mode com keeps explícitos para JNI, AIDL e Shizuku; baseline profile commitado.

## 11. Rede e energia

- `DownloadService` é `dataSync`, com notificação a 1 Hz, WakeLock parcial e
  WifiLock high-perf. O download em si notifica a ~10 Hz; a UI e a notificação
  consomem via `StateFlow`/`sample`.
- "Somente Wi-Fi" é verificado antes de cada parte e durante a espera; o modo
  simulação (só debug) extrai em `filesDir/simulation/game` sem Shizuku.
