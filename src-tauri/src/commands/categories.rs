use sqlx::SqlitePool;
use tauri::State;

use crate::application::InventoryService;
use crate::domain::models::{Category, CreateCategoryPayload, UpdateCategoryPayload};
use crate::errors::AppError;

#[tauri::command]
pub async fn get_categories(pool: State<'_, SqlitePool>) -> Result<Vec<Category>, AppError> {
    InventoryService::get_categories(&pool).await
}

#[tauri::command]
pub async fn create_category(
    pool: State<'_, SqlitePool>,
    payload: CreateCategoryPayload,
) -> Result<Category, AppError> {
    InventoryService::create_category(&pool, payload).await
}

#[tauri::command]
pub async fn update_category(
    pool: State<'_, SqlitePool>,
    id: String,
    payload: UpdateCategoryPayload,
) -> Result<Category, AppError> {
    InventoryService::update_category(&pool, &id, payload).await
}

#[tauri::command]
pub async fn delete_category(
    pool: State<'_, SqlitePool>,
    id: String,
) -> Result<(), AppError> {
    InventoryService::delete_category(&pool, &id).await
}
