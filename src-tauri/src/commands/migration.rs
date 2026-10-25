use sqlx::SqlitePool;
use tauri::State;

use crate::application::MigrationService;
use crate::domain::models::LegacyMigrationSummary;
use crate::errors::AppError;

#[tauri::command]
pub async fn detect_legacy_database() -> Result<Option<String>, AppError> {
    Ok(MigrationService::detect_legacy_path())
}

#[tauri::command]
pub async fn import_legacy_database(
    pool: State<'_, SqlitePool>,
    file_path: Option<String>,
) -> Result<LegacyMigrationSummary, AppError> {
    let path = file_path
        .or_else(MigrationService::detect_legacy_path)
        .ok_or_else(|| AppError::Migration("No legacy database file specified or detected".to_string()))?;

    MigrationService::migrate_from_legacy(&pool, &path).await
}
