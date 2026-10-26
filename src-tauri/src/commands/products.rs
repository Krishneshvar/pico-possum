use sqlx::SqlitePool;
use tauri::State;

use crate::application::{AuthSessionManager, InventoryService};
use crate::domain::models::{CreateProductPayload, Product, StockAdjustmentPayload, UpdateProductPayload};
use crate::errors::AppError;

async fn require_auth(session_mgr: &AuthSessionManager) -> Result<(), AppError> {
    if !session_mgr.is_authenticated().await {
        return Err(AppError::Unauthorized("Authentication required".to_string()));
    }
    Ok(())
}

#[tauri::command]
pub async fn get_products(
    pool: State<'_, SqlitePool>,
    category_id: Option<String>,
    search: Option<String>,
) -> Result<Vec<Product>, AppError> {
    InventoryService::get_products(&pool, category_id, search).await
}

#[tauri::command]
pub async fn get_product_by_id(
    pool: State<'_, SqlitePool>,
    id: String,
) -> Result<Product, AppError> {
    InventoryService::get_product_by_id(&pool, &id).await
}

#[tauri::command]
pub async fn create_product(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    payload: CreateProductPayload,
) -> Result<Product, AppError> {
    require_auth(&session_mgr).await?;
    InventoryService::create_product(&pool, payload).await
}

#[tauri::command]
pub async fn update_product(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    id: String,
    payload: UpdateProductPayload,
) -> Result<Product, AppError> {
    require_auth(&session_mgr).await?;
    InventoryService::update_product(&pool, &id, payload).await
}

#[tauri::command]
pub async fn delete_product(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    id: String,
) -> Result<(), AppError> {
    require_auth(&session_mgr).await?;
    InventoryService::delete_product(&pool, &id).await
}

#[tauri::command]
pub async fn adjust_stock(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    payload: StockAdjustmentPayload,
) -> Result<Product, AppError> {
    require_auth(&session_mgr).await?;
    InventoryService::adjust_stock(&pool, payload).await
}
