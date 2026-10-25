use std::path::PathBuf;

pub struct AppPaths;

impl AppPaths {
    pub fn get_app_dir() -> PathBuf {
        let base = dirs::data_dir().unwrap_or_else(|| PathBuf::from("."));
        let dir = base.join("pico-possum-tauri");
        let _ = std::fs::create_dir_all(&dir);
        dir
    }

    pub fn get_database_path() -> PathBuf {
        Self::get_app_dir().join("pico-possum-v2.db")
    }

    pub fn detect_legacy_db_path() -> Option<PathBuf> {
        let base = dirs::data_dir()?;
        let candidate = base.join("pico-possum").join("database").join("pico-possum.db");
        if candidate.exists() {
            Some(candidate)
        } else {
            None
        }
    }

    pub fn detect_legacy_settings_dir() -> Option<PathBuf> {
        let base = dirs::data_dir()?;
        let candidate = base.join("pico-possum").join("settings");
        if candidate.exists() {
            Some(candidate)
        } else {
            None
        }
    }
}
