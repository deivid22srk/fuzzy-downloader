# Fuzzy Downloader

Loja de **ports e jogos da comunidade** para Android, com biblioteca, fila de
downloads (pausa, retomada e verificação) e detalhes de cada port.

O catálogo não vive no APK: o app lê o banco público
[`deivid22srk/port-db`](https://github.com/deivid22srk/port-db) (`data/games.json`),
com cache local e uma cópia embutida como reserva offline.

<p align="center">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?style=flat-square" />
  <img alt="Rust" src="https://img.shields.io/badge/Rust-edition%202024-000000?style=flat-square" />
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-3DDC84?style=flat-square" />
  <img alt="ABI" src="https://img.shields.io/badge/ABI-arm64--v8a-0070D1?style=flat-square" />
</p>

## Funcionalidades

- **Loja** com destaque (banner real), busca, filtros por categoria, prateleira de
  novidades e lista compacta com ação por item.
- **Detalhes** com descrição, capturas, requisitos, arquivos, controles, instruções,
  licença, créditos e links oficiais — e o botão de ação fixo no rodapé.
- **Biblioteca** com o que foi baixado/instalado, ordenação e atalho para instalar/abrir.
- **Downloads** com o anel pontilhado, velocidade, restante, tamanho e controles de
  pausar/retomar/cancelar.
- **Ajustes** com fonte de dados configurável, cache, conexões, Wi-Fi, tema e sobre.

O download usa o motor em **Rust** (Range, retomada via `.part`, retry) e roda em um
serviço em primeiro plano. A instalação usa o instalador do Android (`FileProvider` +
`REQUEST_INSTALL_PACKAGES`), com o fluxo de “fontes desconhecidas”.

## Requisitos

- Android 8.0+ (`minSdk 26`), `arm64-v8a`.
- Não usa root nem Shizuku: baixa para a pasta do próprio app e abre o instalador.

## Sobre o catálogo

- Fonte padrão: `https://raw.githubusercontent.com/deivid22srk/port-db/main/`
  (configurável em Ajustes).
- O banco **não hospeda APKs**: cada registro tem links oficiais (`links.download`,
  `releases`, `github`, `project`, `tutorial`). Quando o link é um arquivo direto
  (`.apk`/`.zip`...), o app baixa e instala; quando é uma página, abre no navegador.
- As imagens (capa, banner, capturas) são servidas pelo próprio port-db e carregadas
  com Coil (SVG e WebP).
- Ports que exigem os dados do jogo original são marcados nos requisitos; nenhum dado
  protegido é distribuído.

## Estrutura do repositório

```
app/                         App Android (Kotlin + Compose Foundation)
  src/main/assets/portdb.json  Reserva offline do catálogo
  src/main/kotlin/com/fuzzydl/app/
    core/                      Ponte JNI (NativeDownloader)
    data/                      Ajustes, log, conectividade
    download/                  Fila genérica, serviço e instalador de APK
    store/                     Modelos e repositório do port-db
    ui/                        Tema, componentes e telas
rust/fuzzy_dl/               cdylib JNI + CLI desktop
  src/client.rs              Cliente HTTP e probe genérico
  src/download.rs            Range, resume, retry, cancelamento
  src/jni_api.rs             Ponte JNI (probe/start/cancel)
.github/workflows/build.yml  CI: testes Rust, cargo-ndk, R8 e release
```

## Build

### GitHub Actions (recomendado)

O workflow `.github/workflows/build.yml` roda a cada push/PR e em tags `v*`:

1. `cargo test --all-targets` no Rust;
2. `cargo ndk -t arm64-v8a -o app/src/main/jniLibs build --release`;
3. copia `libc++_shared.so` do NDK (dependência da BoringSSL);
4. `./gradlew testDebugUnitTest assembleRelease` (R8 full mode);
5. em tags `v*`, anexa o APK ao Release.

Assinatura: configure `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e
`KEY_PASSWORD`. Sem eles o release usa a chave de debug.

### Local

Pré-requisitos: JDK 17, Android SDK com `platforms;android-37.0` e
`ndk;28.2.13676358`, Rust com o target `aarch64-linux-android` e `cargo-ndk` 4.1.2.

```bash
cd rust/fuzzy_dl
cargo test --all-targets
cargo ndk -t arm64-v8a -o ../../app/src/main/jniLibs build --release
cp "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so \
   ../../app/src/main/jniLibs/arm64-v8a/
cd ../..
./gradlew testDebugUnitTest assembleRelease
```

O CLI desktop também roda sem Android:

```bash
cd rust/fuzzy_dl
cargo run --bin fuzzy-dl -- probe https://example.com/arquivo.apk
cargo run --bin fuzzy-dl -- download https://example.com/arquivo.apk -o /tmp/arquivo.apk
```

## Aviso

Projeto pessoal, sem vínculo com os autores dos ports. O catálogo apenas aponta para
os releases oficiais; os direitos de marcas e imagens pertencem aos seus titulares.
Use somente conteúdo que você tem direito de acessar.

## Licença

MIT — veja [LICENSE](LICENSE). A fonte Manrope é distribuída sob a SIL Open Font
License 1.1 (`app/src/main/assets/fonts/OFL-Manrope.txt`).
