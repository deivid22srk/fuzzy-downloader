# Decisões de arquitetura

Registro curto do porquê de cada escolha relevante (v2, loja de ports).

## 1. Catálogo fora do APK (port-db)

O banco de dados vive em [`deivid22srk/port-db`](https://github.com/deivid22srk/port-db)
(`data/games.json`). O app busca, guarda cache em `filesDir` e tem uma cópia embutida
em `assets/portdb.json` para abrir offline. A URL base é configurável em Ajustes, então
trocar de banco não exige novo APK.

## 2. Nada específico de um único jogo

A versão anterior baixava um pacote fixo (9 ZIPs do Buzzheavier) e extraía em
`Android/data` via Shizuku. Isso foi removido: não há partes fixas, alvo fixo, AIDL nem
UserService. O motor de download é genérico (qualquer URL), e a ideia de “partes” virou
“arquivos de um item”, dentro da tela de detalhes.

## 3. Ports que exigem dados originais

O catálogo só lista ports/jogos de código aberto/freeware e aponta para os releases
oficiais. Ports que precisam dos dados do jogo original são marcados nos requisitos; o
app não distribui nem baixa dados protegidos.

## 4. Link direto x página

O `links.download` pode ser um APK direto, uma página de releases ou um espelho. O app
decide: extensões de arquivo (`.apk`, `.zip`, ...) entram na fila; o resto abre no
navegador. Assim não é preciso criar regras por site.

## 5. Fila genérica + Rust

`DownloadQueue` é sequencial, com `QUEUED/PROBING/DOWNLOADING/PAUSED/VERIFYING/READY/FAILED`,
persistência em `SharedPreferences` e pausa preservando o `.part` (o Rust retoma por
sidecar). O tamanho/nome vêm de um probe `HEAD`/`Range` genérico no Rust.

## 6. Instalação pelo Android

`ApkInstaller` usa `FileProvider` + `ACTION_VIEW` e pede “fontes desconhecidas” quando
necessário. Sem root/Shizuku.

## 7. Imagens com Coil (SVG/WebP)

As capas do port-db são SVG e WebP. O `ImageLoader` do app registra `SvgDecoder` e usa
cache de memória/disco. Enquanto carrega (ou se falhar), um fallback discreto
(gradiente determinístico + monograma pequeno) preenche o espaço — nunca uma letra
gigante.

## 8. Vidro com critério

Vidro apenas em barra inferior, topo, bottom sheets, chips e painel de download; cards
de conteúdo usam superfície sólida com borda sutil. Isso resolve o problema de “vidro
sobre vidro” e melhora o contraste. O anel de pontos continua sendo a assinatura.

## 9. Navegação sem duplicidade

Quatro destinos na barra inferior: **Loja**, **Biblioteca**, **Downloads** e **Ajustes**.
Não existe mais a aba “Loja | Biblioteca” no topo. Busca e atualização (puxar para
atualizar) ficam dentro da Loja.

## 10. Sem Material3

Tudo em Compose Foundation: `BasicText`, `BasicTextField`, `Canvas`, `clickable`,
`AnimatedVisibility` e um pull-to-refresh próprio via `NestedScrollConnection`.
Tipografia Manrope variável (OFL) com algarismos tabulares.
