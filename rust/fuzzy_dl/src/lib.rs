//! Núcleo de download do Fuzzy Downloader.
//!
//! - Resolve o link direto do Buzzheavier (token na página + `hx-redirect`).
//! - Baixa com `Range` em N segmentos, retomada via sidecar e cancelamento.
//! - Expõe uma API JNI mínima para o app Android e um CLI para testes no desktop.

pub mod download;
pub mod error;
pub mod resolve;

mod jni_api;

pub use download::{DlEvent, Job, Progress};
pub use error::{Error, Result};
pub use resolve::{resolve, Resolved};
