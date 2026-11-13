use std::fmt;

#[derive(Debug)]
pub struct Error(pub String);

impl Error {
    pub fn new(msg: impl Into<String>) -> Self {
        Self(msg.into())
    }
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl std::error::Error for Error {}

impl From<std::io::Error> for Error {
    fn from(e: std::io::Error) -> Self {
        Self(format!("io: {e}"))
    }
}

impl From<wreq::Error> for Error {
    fn from(e: wreq::Error) -> Self {
        Self(format!("http: {e}"))
    }
}

impl From<serde_json::Error> for Error {
    fn from(e: serde_json::Error) -> Self {
        Self(format!("json: {e}"))
    }
}

pub type Result<T> = std::result::Result<T, Error>;

#[macro_export]
macro_rules! bail {
    ($($arg:tt)*) => {
        return Err($crate::Error::new(format!($($arg)*)))
    };
}
