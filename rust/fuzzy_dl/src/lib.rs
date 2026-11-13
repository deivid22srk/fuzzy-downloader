//! Núcleo de download genérico do Fuzzy.
//!
//! - Sonda qualquer URL do catálogo (tamanho + nome) com HEAD/Range.
//! - Baixa com `Range` em N segmentos, retomada via sidecar e cancelamento.
//! - Expõe uma API JNI mínima para o app Android e um CLI para testes no desktop.

pub mod client;
pub mod download;
pub mod error;

mod jni_api;

pub use client::{build_client, is_direct_file, probe, RemoteFile};
pub use download::{DlEvent, Job, Progress};
pub use error::{Error, Result};
