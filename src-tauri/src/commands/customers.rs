use sqlx::SqlitePool;
use tauri::State;

use crate::application::InventoryService;
use crate::domain::models::{CreateCustomerPayload, Customer, UpdateCustomerPayload};
use crate::errors::AppError;

#[tauri::command]
pub async fn get_customers(
    pool: State<'_, SqlitePool>,
    search: Option<String>,
) -> Result<Vec<Customer>, AppError> {
    InventoryService::get_customers(&pool, search).await
}

#[tauri::command]
pub async fn create_customer(
    pool: State<'_, SqlitePool>,
    payload: CreateCustomerPayload,
) -> Result<Customer, AppError> {
    InventoryService::create_customer(&pool, payload).await
}

#[tauri::command]
pub async fn update_customer(
    pool: State<'_, SqlitePool>,
    id: String,
    payload: UpdateCustomerPayload,
) -> Result<Customer, AppError> {
    InventoryService::update_customer(&pool, &id, payload).await
}

#[tauri::command]
pub async fn delete_customer(
    pool: State<'_, SqlitePool>,
    id: String,
) -> Result<(), AppError> {
    InventoryService::delete_customer(&pool, &id).await
}
