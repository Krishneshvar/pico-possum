use sqlx::SqlitePool;
use tauri::State;

use crate::application::InventoryService;
use crate::domain::models::{BillSettings, GeneralSettings};
use crate::errors::AppError;

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
