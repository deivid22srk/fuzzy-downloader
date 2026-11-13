# Fuzzy Downloader

Baixador Android do pacote de dados do **Fuzzy** (`com.thegwchr.fuzzy`), feito para
baixar os 9 ZIPs do Buzzheavier **um por vez**, validar, extrair em
`/storage/emulated/0/Android/data/com.thegwchr.fuzzy/files/game/` e só então apagar
o ZIP. Interface em Compose Foundation, motor de download em Rust e escrita em
`Android/data` de outro app via **Shizuku UserService**.

<p align="center">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?style=flat-square" />
  <img alt="Rust" src="https://img.shields.io/badge/Rust-edition%202024-000000?style=flat-square" />
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-3DDC84?style=flat-square" />
  <img alt="ABI" src="https://img.shields.io/badge/ABI-arm64--v8a-D71921?style=flat-square" />
</p>

## Como funciona

```
PENDING → RESOLVING → DOWNLOADING → VERIFYING → EXTRACTING → DONE
                                                          ↘ FAILED
```

1. **Resolver** — abre `https://buzzheavier.com/<id>`, extrai o token `download?t=`,
   pede o redirecionamento com `hx-request` e valida a URL direta
   (`ts.buzzheavier.com/d/...`; `fafda.to` é anúncio e é rejeitado).
2. **Baixar** — HTTP Range com 1–8 conexões, arquivo `.part` + sidecar JSON com o
   progresso de cada segmento. Retomada byte-exata depois de pausa, cancelamento ou
   queda de rede. Até 4 retries com backoff por segmento; a URL é re-resolvida se
   expirar. Callback de progresso a ~10 Hz.
3. **Validar** — tamanho exato, assinatura ZIP e diretório central (`java.util.zip`,
   com suporte a ZIP64).
4. **Extrair** — em `game/`, com proteção contra Zip Slip (inclusive `..`, caminhos
   absolutos e `\`), validação de CRC, buffer de 256 KiB e pool de 3 threads para
   arquivos pequenos. O prefixo `game/` só é removido quando **todas** as entradas
   estão sob ele (não é o caso destes 9 ZIPs).
5. **Conferir e apagar** — a contagem de arquivos e o total de bytes extraídos têm de
   bater com o diretório central. Só então o ZIP e o sidecar são removidos.

Tudo é sequencial: existe **uma parte em staging por vez**, o que limita o pico de
armazenamento a ~64 GiB no destino + ~8 GiB temporários.

## Requisitos

- Android 8.0+ (`minSdk 26`), `arm64-v8a`.
- [Shizuku](https://shizuku.rikka.app/) em execução com permissão concedida
  (Android 11+). Em Android 8–10 o app usa o armazenamento legado.
- ~72 GiB livres (64,13 GiB de dados + margem para a maior parte).
- O jogo instalado não é obrigatório: a pasta é criada mesmo assim.

## Instalação

1. Instale o APK de [Releases](https://github.com/deivid22srk/fuzzy-downloader/releases).
2. Abra o app e conclua a tela **Antes de começar** (Shizuku, permissão,
   notificações e espaço livre).
3. Na aba **Baixar**, toque em **Iniciar**. O download continua em primeiro plano,
   com notificação e WakeLock/WifiLock.
4. Acompanhe parte por parte na aba **Partes**. Toque em uma parte para ver
   tamanho, espelho, arquivos e detalhes do erro; ali também é possível refazer.

> **Somente Wi-Fi** vem ligado por padrão. O download pausa e espera uma rede Wi-Fi
> quando a opção está ativa.

## Estrutura do repositório

```
app/                        App Android (Kotlin + Compose Foundation)
  src/main/aidl/            IFileService / IExtractCallback (Shizuku)
  src/main/assets/          parts.json (IDs e destino)
  src/main/kotlin/          UI, motor, Shizuku, extrator
  src/main/res/             Tema, fonte Manrope (OFL), ícones
  src/test/                 Testes JVM do extrator (Zip Slip, CRC, ZIP64)
  src/main/baseline-prof.txt
rust/fuzzy_dl/              cdylib JNI + CLI desktop
  src/resolve.rs            Token, espelhos, probe e cliente wreq
  src/download.rs           Range, resume, retry, cancelamento
  src/jni_api.rs            Ponte JNI (handle + callbacks)
  src/bin/cli.rs            `fuzzy-dl probe|download`
  tests/download.rs         Servidor HTTP local
prototype/                  Fase 1: prova de conceito em Python/curl_cffi
docs/                       Achados da rede e inspeção dos 9 ZIPs
.github/workflows/build.yml CI: testes Rust, cargo-ndk, R8 e release
```

## Build

### GitHub Actions (recomendado)

O workflow `.github/workflows/build.yml` roda a cada push/PR e em tags `v*`:

1. `cargo test --all-targets` no Rust;
2. `cargo ndk -t arm64-v8a -o app/src/main/jniLibs build --release`;
3. copia `libc++_shared.so` do NDK para `jniLibs` (dependência da BoringSSL);
4. `./gradlew testDebugUnitTest assembleRelease` (R8 full mode);
5. publica o APK como artefato e, em tags, anexa ao Release.

Assinatura: configure os secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`
e `KEY_PASSWORD`. Sem eles o release é assinado com a chave de debug (apenas para
teste). O keystore nunca é commitado.

### Local

Pré-requisitos: JDK 17, Android SDK com `platforms;android-37.0` e
`ndk;28.2.13676358`, Rust estável com o target `aarch64-linux-android`,
`cargo-ndk` 4.1.2.

```bash
git clone https://github.com/deivid22srk/fuzzy-downloader
cd fuzzy-downloader

# Rust: biblioteca + testes com servidor local
cd rust/fuzzy_dl
cargo test --all-targets
cargo ndk -t arm64-v8a -o ../../app/src/main/jniLibs build --release
# BoringSSL usa a STL compartilhada; sem este passo o app fecha ao iniciar.
cp "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so \
   ../../app/src/main/jniLibs/arm64-v8a/
cd ../..

# Android
./gradlew testDebugUnitTest assembleRelease
# APK em app/build/outputs/apk/release/
```

O CLI desktop também roda sem Android (útil para diagnosticar a rede):

```bash
cd rust/fuzzy_dl
cargo run --bin fuzzy-dl -- probe https://buzzheavier.com/<id>
cargo run --bin fuzzy-dl -- download https://buzzheavier.com/<id> \
  -o /tmp/parte.zip --threads 4
```

## Decisões e limites

- **wreq + BoringSSL** em vez de `reqwest`: o Cloudflare responde 403 para clientes
  sem fingerprint de navegador. `wreq` reproduz o TLS/HTTP do Chrome.
- **Sem Hilt/Retrofit/Coil/Gson**: `org.json`, `HttpURLConnection` não é usado no
  motor (o Rust cuida da rede) e a UI é Compose Foundation pura — sem Material3.
- **R8 full mode** com regras de keep para JNI, AIDL e Shizuku.
- **Arquivos grandes**: o Rust escreve com `pwrite` em posições calculadas; a
  extração usa `ZipFile` (streaming, 256 KiB) e nunca carrega o ZIP na memória.
- **Falha nunca apaga dados**: se a extração falhar, o ZIP permanece em staging para
  inspeção/retomada; o `.part` só é removido em cancelamento explícito.

Mais detalhes em [DECISIONS.md](DECISIONS.md) e [docs/findings.md](docs/findings.md).

## Testes

- Rust: servidor HTTP local cobre 206/Range, download segmentado byte-exato,
  retomada após cancelamento, falha no meio do stream com retry, cancelamento
  rápido e fallback sem Range.
- Android/JVM: `ZipExtractorTest` cobre arquivos normais, remoção condicional do
  prefixo `game/`, sobrescrita, Zip Slip (simples/aninhado), caminho absoluto,
  separador `\`, CRC corrompido e um ZIP64 real.

## Aviso

Projeto pessoal, sem vínculo com a desenvolvedora do jogo ou com o Buzzheavier.
Baixe apenas conteúdo que você tem direito de acessar. O app não contorna
pagamentos nem DRM; apenas automatiza o download e a cópia dos arquivos para a
pasta do jogo no próprio dispositivo.

## Licença

MIT — veja [LICENSE](LICENSE). A fonte Manrope é distribuída sob a SIL Open Font
License 1.1 (`app/src/main/assets/fonts/OFL-Manrope.txt`).
