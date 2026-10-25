use sqlx::SqlitePool;
use tauri::State;

use crate::application::InventoryService;
use crate::domain::models::{CreateProductPayload, Product, StockAdjustmentPayload, UpdateProductPayload};
use crate::errors::AppError;

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
    payload: CreateProductPayload,
) -> Result<Product, AppError> {
    InventoryService::create_product(&pool, payload).await
}

#[tauri::command]
pub async fn update_product(
    pool: State<'_, SqlitePool>,
    id: String,
    payload: UpdateProductPayload,
) -> Result<Product, AppError> {
    InventoryService::update_product(&pool, &id, payload).await
}

#[tauri::command]
pub async fn delete_product(
    pool: State<'_, SqlitePool>,
    id: String,
) -> Result<(), AppError> {
    InventoryService::delete_product(&pool, &id).await
}

#[tauri::command]
pub async fn adjust_stock(
    pool: State<'_, SqlitePool>,
    payload: StockAdjustmentPayload,
) -> Result<Product, AppError> {
    InventoryService::adjust_stock(&pool, payload).await
}
