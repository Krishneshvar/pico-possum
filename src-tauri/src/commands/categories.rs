use sqlx::SqlitePool;
use tauri::State;

use crate::application::{AuthSessionManager, InventoryService};
use crate::domain::models::{Category, CreateCategoryPayload, UpdateCategoryPayload};
use crate::errors::AppError;

async fn require_auth(session_mgr: &AuthSessionManager) -> Result<(), AppError> {
    if !session_mgr.is_authenticated().await {
        return Err(AppError::Unauthorized("Authentication required".to_string()));
    }
    Ok(())
}

#[tauri::command]
pub async fn get_categories(pool: State<'_, SqlitePool>) -> Result<Vec<Category>, AppError> {
    InventoryService::get_categories(&pool).await
}

#[tauri::command]
pub async fn create_category(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    payload: CreateCategoryPayload,
) -> Result<Category, AppError> {
    require_auth(&session_mgr).await?;
    InventoryService::create_category(&pool, payload).await
}

#[tauri::command]
pub async fn update_category(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    id: String,
    payload: UpdateCategoryPayload,
) -> Result<Category, AppError> {
    require_auth(&session_mgr).await?;
    InventoryService::update_category(&pool, &id, payload).await
}

#[tauri::command]
pub async fn delete_category(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    id: String,
) -> Result<(), AppError> {
    require_auth(&session_mgr).await?;
    InventoryService::delete_category(&pool, &id).await
}
