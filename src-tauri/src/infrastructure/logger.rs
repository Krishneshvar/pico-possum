/// Structured observability logger for Pico Possum backend.
///
/// Log levels:
/// - `DEBUG`: Active ONLY during development runtime (`debug_assertions`).
///            Completely eliminated by the compiler in release builds.
/// - `INFO`:  High-level application lifecycle events (audits, migrations, sales).
/// - `WARN`:  Recoverable errors, warnings, and fallback scenarios.
/// - `ERROR`: Critical failures requiring administrative attention.

pub struct AppLogger;

impl AppLogger {
    #[inline]
    pub fn debug(target: &str, msg: impl std::fmt::Display) {
        #[cfg(debug_assertions)]
        eprintln!("[DEBUG] [{}] {}", target, msg);
        #[cfg(not(debug_assertions))]
        let _ = (target, msg);
    }

    #[inline]
    pub fn info(target: &str, msg: impl std::fmt::Display) {
        eprintln!("[INFO]  [{}] {}", target, msg);
    }

    #[inline]
    pub fn warn(target: &str, msg: impl std::fmt::Display) {
        eprintln!("[WARN]  [{}] {}", target, msg);
    }

    #[inline]
    pub fn error(target: &str, msg: impl std::fmt::Display) {
        eprintln!("[ERROR] [{}] {}", target, msg);
    }
}

#[macro_export]
macro_rules! log_debug {
    ($target:expr, $($arg:tt)*) => {
        #[cfg(debug_assertions)]
        eprintln!("[DEBUG] [{}] {}", $target, format_args!($($arg)*));
    };
}

#[macro_export]
macro_rules! log_info {
    ($target:expr, $($arg:tt)*) => {
        eprintln!("[INFO]  [{}] {}", $target, format_args!($($arg)*));
    };
}

#[macro_export]
macro_rules! log_warn {
    ($target:expr, $($arg:tt)*) => {
        eprintln!("[WARN]  [{}] {}", $target, format_args!($($arg)*));
    };
}

#[macro_export]
macro_rules! log_error {
    ($target:expr, $($arg:tt)*) => {
        eprintln!("[ERROR] [{}] {}", $target, format_args!($($arg)*));
    };
}
