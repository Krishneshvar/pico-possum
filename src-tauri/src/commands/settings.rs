use sqlx::SqlitePool;
use tauri::State;

use crate::application::{BackupService, InventoryService, PrinterService};
use crate::domain::models::{BackupInfo, BackupStatus, BillSettings, GeneralSettings};
use crate::errors::AppError;

// ── General & Bill Settings ──────────────────────────────────────────────────

#[tauri::command]
pub async fn get_general_settings(pool: State<'_, SqlitePool>) -> Result<GeneralSettings, AppError> {
    InventoryService::get_general_settings(&pool).await
}

#[tauri::command]
pub async fn update_general_settings(
    pool: State<'_, SqlitePool>,
    settings: GeneralSettings,
) -> Result<(), AppError> {
    InventoryService::update_general_settings(&pool, settings).await
}

#[tauri::command]
pub async fn get_bill_settings(pool: State<'_, SqlitePool>) -> Result<BillSettings, AppError> {
    InventoryService::get_bill_settings(&pool).await
}

#[tauri::command]
pub async fn update_bill_settings(
    pool: State<'_, SqlitePool>,
    settings: BillSettings,
) -> Result<(), AppError> {
    InventoryService::update_bill_settings(&pool, settings).await
}

// ── Hardware & Printing ──────────────────────────────────────────────────────

#[tauri::command]
pub async fn list_printers() -> Result<Vec<String>, AppError> {
    Ok(PrinterService::list_printers())
}

#[tauri::command]
pub async fn test_print(printer_name: Option<String>) -> Result<(), AppError> {
    PrinterService::test_print(printer_name)
}

// ── Backup & Restore ─────────────────────────────────────────────────────────

#[tauri::command]
pub async fn get_backup_status() -> Result<BackupStatus, AppError> {
    BackupService::get_backup_status().await
}

#[tauri::command]
pub async fn list_backups() -> Result<Vec<BackupInfo>, AppError> {
    BackupService::list_backups().await
}

#[tauri::command]
pub async fn create_manual_backup(pool: State<'_, SqlitePool>) -> Result<BackupInfo, AppError> {
    BackupService::create_manual_backup(&pool).await
}

#[tauri::command]
pub async fn restore_backup(
    pool: State<'_, SqlitePool>,
    backup_path: String,
) -> Result<String, AppError> {
    BackupService::restore_from_backup(&pool, &backup_path).await
}

#[tauri::command]
pub async fn restore_backup_file(
    pool: State<'_, SqlitePool>,
    filename: String,
    bytes: Vec<u8>,
) -> Result<String, AppError> {
    let backups_dir = crate::infrastructure::paths::AppPaths::get_backups_dir();
    let temp_name = if filename.ends_with(".db") {
        filename
    } else {
        format!("{}.db", filename)
    };
    let target_path = backups_dir.join(&temp_name);
    std::fs::write(&target_path, &bytes)?;
    BackupService::restore_from_backup(&pool, &target_path.to_string_lossy()).await
}

#[tauri::command]
pub async fn open_backup_folder() -> Result<(), AppError> {
    BackupService::open_backup_folder()
}
