use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::str::FromStr;
use chrono::{DateTime, Local, Utc};
use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};
use sqlx::SqlitePool;

use crate::application::MigrationService;
use crate::domain::models::{BackupInfo, BackupStatus};
use crate::errors::AppError;
use crate::infrastructure::paths::AppPaths;

pub struct BackupService;

impl BackupService {
    pub async fn get_backup_status() -> Result<BackupStatus, AppError> {
        let backups_dir = AppPaths::get_backups_dir();
        let backups = Self::list_backups().await?;

        let latest_backup = backups.first().cloned();
        let total_backups = backups.len();

        Ok(BackupStatus {
            backup_location: backups_dir.to_string_lossy().to_string(),
            latest_backup,
            total_backups,
        })
    }

    pub async fn list_backups() -> Result<Vec<BackupInfo>, AppError> {
        let backups_dir = AppPaths::get_backups_dir();
        if !backups_dir.exists() {
            let _ = fs::create_dir_all(&backups_dir);
            return Ok(Vec::new());
        }

        let mut list = Vec::new();
        let entries = fs::read_dir(&backups_dir)?;

        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_file() {
                if let Some(ext) = path.extension() {
                    if ext == "db" {
                        let filename = path.file_name().unwrap_or_default().to_string_lossy().to_string();
                        let metadata = entry.metadata().ok();
                        let size_bytes = metadata.as_ref().map(|m| m.len()).unwrap_or(0);

                        let created_at = metadata
                            .and_then(|m| m.modified().ok())
                            .map(|st| {
                                let dt: DateTime<Local> = st.into();
                                dt.format("%Y-%m-%d %H:%M:%S").to_string()
                            })
                            .unwrap_or_else(|| "Unknown".to_string());

                        let backup_type = if filename.contains("auto-backup") {
                            "auto".to_string()
                        } else if filename.contains("pre-restore") {
                            "pre-restore".to_string()
                        } else {
                            "manual".to_string()
                        };

                        list.push(BackupInfo {
                            filename,
                            file_path: path.to_string_lossy().to_string(),
                            size_bytes,
                            created_at,
                            backup_type,
                        });
                    }
                }
            }
        }

        // Sort descending by created_at / filename
        list.sort_by(|a, b| b.filename.cmp(&a.filename));

        Ok(list)
    }

    pub async fn create_manual_backup(pool: &SqlitePool) -> Result<BackupInfo, AppError> {
        // 1. Checkpoint WAL to make sure all data is committed to the main db file
        let _ = sqlx::query("PRAGMA wal_checkpoint(TRUNCATE)").execute(pool).await;

        let backups_dir = AppPaths::get_backups_dir();
        let timestamp = Local::now().format("%Y-%m-%d_%H-%M-%S").to_string();
        let backup_filename = format!("pico-possum-manual-backup-{}.db", timestamp);
        let backup_path = backups_dir.join(&backup_filename);

        let live_db_path = AppPaths::get_database_path();
        if !live_db_path.exists() {
            return Err(AppError::Validation("Live database file not found".to_string()));
        }

        fs::copy(&live_db_path, &backup_path)?;

        let size_bytes = fs::metadata(&backup_path).map(|m| m.len()).unwrap_or(0);
        let created_at = Local::now().format("%Y-%m-%d %H:%M:%S").to_string();

        Ok(BackupInfo {
            filename: backup_filename,
            file_path: backup_path.to_string_lossy().to_string(),
            size_bytes,
            created_at,
            backup_type: "manual".to_string(),
        })
    }

    pub async fn run_daily_backup_if_due(pool: &SqlitePool) -> Result<Option<BackupInfo>, AppError> {
        let backups_dir = AppPaths::get_backups_dir();
        let today = Local::now().format("%Y-%m-%d").to_string();
        let backup_filename = format!("pico-possum-auto-backup-{}.db", today);
        let backup_path = backups_dir.join(&backup_filename);

        if backup_path.exists() {
            return Ok(None);
        }

        // Checkpoint WAL
        let _ = sqlx::query("PRAGMA wal_checkpoint(TRUNCATE)").execute(pool).await;

        let live_db_path = AppPaths::get_database_path();
        if !live_db_path.exists() {
            return Ok(None);
        }

        fs::copy(&live_db_path, &backup_path)?;
        let size_bytes = fs::metadata(&backup_path).map(|m| m.len()).unwrap_or(0);
        let created_at = Local::now().format("%Y-%m-%d %H:%M:%S").to_string();

        // Prune auto backups older than 30 days
        Self::prune_old_backups(&backups_dir, 30);

        Ok(Some(BackupInfo {
            filename: backup_filename,
            file_path: backup_path.to_string_lossy().to_string(),
            size_bytes,
            created_at,
            backup_type: "auto".to_string(),
        }))
    }

    pub fn open_backup_folder() -> Result<(), AppError> {
        let backups_dir = AppPaths::get_backups_dir();
        let path_str = backups_dir.to_string_lossy().to_string();

        #[cfg(target_os = "windows")]
        {
            Command::new("explorer").arg(&path_str).spawn()?;
        }

        #[cfg(target_os = "macos")]
        {
            Command::new("open").arg(&path_str).spawn()?;
        }

        #[cfg(not(any(target_os = "windows", target_os = "macos")))]
        {
            // Linux / Unix
            let _ = Command::new("xdg-open").arg(&path_str).spawn()
                .or_else(|_| Command::new("gio").arg("open").arg(&path_str).spawn());
        }

        Ok(())
    }

    pub async fn is_legacy_database(target_path: &Path) -> Result<bool, AppError> {
        let conn_str = format!("sqlite://{}?mode=ro", target_path.to_string_lossy());
        let options = match SqliteConnectOptions::from_str(&conn_str) {
            Ok(opts) => opts.read_only(true),
            Err(_) => return Ok(false),
        };

        let test_pool = match SqlitePoolOptions::new()
            .max_connections(1)
            .connect_with(options)
            .await
        {
            Ok(p) => p,
            Err(_) => return Ok(false),
        };

        // Check if products table has mrp_paise (present in new app, absent in Java app)
        let has_mrp_paise: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM pragma_table_info('products') WHERE name = 'mrp_paise'",
        )
        .fetch_one(&test_pool)
        .await
        .unwrap_or(0);

        let has_legacy_mrp: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM pragma_table_info('products') WHERE name = 'mrp'",
        )
        .fetch_one(&test_pool)
        .await
        .unwrap_or(0);

        // Also check if categories table exists and uses integer id
        let has_categories: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'categories'",
        )
        .fetch_one(&test_pool)
        .await
        .unwrap_or(0);

        let is_legacy = (has_mrp_paise == 0 && has_legacy_mrp > 0)
            || (has_mrp_paise == 0 && has_categories > 0);

        Ok(is_legacy)
    }

    pub async fn restore_from_backup(
        pool: &SqlitePool,
        target_path_or_filename: &str,
    ) -> Result<String, AppError> {
        let backups_dir = AppPaths::get_backups_dir();
        let target_path = if target_path_or_filename.contains('/') || target_path_or_filename.contains('\\') {
            PathBuf::from(target_path_or_filename)
        } else {
            backups_dir.join(target_path_or_filename)
        };

        if !target_path.exists() {
            return Err(AppError::NotFound(format!(
                "Backup file does not exist: {}",
                target_path.display()
            )));
        }

        // Validate SQLite header
        let bytes = fs::read(&target_path)?;
        if bytes.len() < 16 || &bytes[0..15] != b"SQLite format 3" {
            return Err(AppError::Validation(
                "Selected file is not a valid SQLite database backup".to_string(),
            ));
        }

        // Checkpoint WAL on current DB
        let _ = sqlx::query("PRAGMA wal_checkpoint(TRUNCATE)").execute(pool).await;

        let live_db_path = AppPaths::get_database_path();

        // 1. Take safety pre-restore backup
        let timestamp = Local::now().format("%Y-%m-%d_%H-%M-%S").to_string();
        let pre_restore_name = format!("pico-possum-pre-restore-backup-{}.db", timestamp);
        let pre_restore_path = backups_dir.join(&pre_restore_name);
        if live_db_path.exists() {
            let _ = fs::copy(&live_db_path, &pre_restore_path);
        }

        // Check if selected backup is a legacy Java POS database
        if Self::is_legacy_database(&target_path).await? {
            // Clean tables for a fresh restore
            let mut tx = pool.begin().await?;
            let _ = sqlx::query("DELETE FROM audit_logs").execute(&mut *tx).await;
            let _ = sqlx::query("DELETE FROM stock_movements").execute(&mut *tx).await;
            let _ = sqlx::query("DELETE FROM sale_items").execute(&mut *tx).await;
            let _ = sqlx::query("DELETE FROM sales").execute(&mut *tx).await;
            let _ = sqlx::query("DELETE FROM products").execute(&mut *tx).await;
            let _ = sqlx::query("DELETE FROM customers").execute(&mut *tx).await;
            let _ = sqlx::query("DELETE FROM categories").execute(&mut *tx).await;
            let _ = sqlx::query("DELETE FROM users").execute(&mut *tx).await;
            tx.commit().await?;

            // Migrate from legacy DB
            let summary = MigrationService::migrate_from_legacy(pool, &target_path.to_string_lossy()).await?;

            return Ok(format!(
                "Java POS application backup detected! Successfully converted and restored {} categories, {} products, {} customers, and {} sales. Safety pre-restore backup saved at '{}'.",
                summary.categories_imported,
                summary.products_imported,
                summary.customers_imported,
                summary.sales_imported,
                pre_restore_name
            ));
        }

        // 2. Native Tauri backup restore: Remove WAL / SHM files
        let wal_path = PathBuf::from(format!("{}-wal", live_db_path.to_string_lossy()));
        let shm_path = PathBuf::from(format!("{}-shm", live_db_path.to_string_lossy()));
        let _ = fs::remove_file(wal_path);
        let _ = fs::remove_file(shm_path);

        // 3. Overwrite live database with the backup file
        fs::copy(&target_path, &live_db_path)?;

        Ok(format!(
            "Database successfully restored from '{}'. Pre-restore backup saved at '{}'.",
            target_path.file_name().unwrap_or_default().to_string_lossy(),
            pre_restore_path.file_name().unwrap_or_default().to_string_lossy()
        ))
    }

    fn prune_old_backups(dir: &Path, max_days: i64) {
        if let Ok(entries) = fs::read_dir(dir) {
            let threshold = Utc::now() - chrono::Duration::days(max_days);
            for entry in entries.flatten() {
                let path = entry.path();
                if let Some(name) = path.file_name().and_then(|n| n.to_str()) {
                    if name.starts_with("pico-possum-auto-backup-") && name.ends_with(".db") {
                        if let Ok(meta) = entry.metadata() {
                            if let Ok(mod_time) = meta.modified() {
                                let dt: DateTime<Utc> = mod_time.into();
                                if dt < threshold {
                                    let _ = fs::remove_file(path);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
