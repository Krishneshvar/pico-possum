use sqlx::SqlitePool;
use tauri::State;

use crate::application::CsvImportService;
use crate::domain::models::CsvImportResult;
use crate::errors::AppError;

async fn resolve_csv_content(
    csv_content: Option<String>,
    file_path: Option<String>,
) -> Result<String, AppError> {
    if let Some(content) = csv_content {
        if !content.trim().is_empty() {
            return Ok(content);
        }
    }

    if let Some(path) = file_path {
        let content = std::fs::read_to_string(&path)
            .map_err(|e| AppError::Validation(format!("Failed to read file '{}': {}", path, e)))?;
        return Ok(content);
    }

    Err(AppError::Validation("No CSV content or file path provided".to_string()))
}

#[tauri::command]
pub async fn import_csv_categories(
    pool: State<'_, SqlitePool>,
    csv_content: Option<String>,
    file_path: Option<String>,
) -> Result<CsvImportResult, AppError> {
    let content = resolve_csv_content(csv_content, file_path).await?;
    CsvImportService::import_categories(&pool, &content).await
}

#[tauri::command]
pub async fn import_csv_products(
    pool: State<'_, SqlitePool>,
    csv_content: Option<String>,
    file_path: Option<String>,
) -> Result<CsvImportResult, AppError> {
    let content = resolve_csv_content(csv_content, file_path).await?;
    CsvImportService::import_products(&pool, &content).await
}

#[tauri::command]
pub async fn import_csv_customers(
    pool: State<'_, SqlitePool>,
    csv_content: Option<String>,
    file_path: Option<String>,
) -> Result<CsvImportResult, AppError> {
    let content = resolve_csv_content(csv_content, file_path).await?;
    CsvImportService::import_customers(&pool, &content).await
}

#[tauri::command]
pub async fn import_csv_bills(
    pool: State<'_, SqlitePool>,
    csv_content: Option<String>,
    file_path: Option<String>,
    file_name: Option<String>,
) -> Result<CsvImportResult, AppError> {
    let content = resolve_csv_content(csv_content, file_path).await?;
    CsvImportService::import_bills(&pool, &content, file_name.as_deref()).await
}
