use sqlx::SqlitePool;
use tauri::State;

use crate::application::InventoryService;
use crate::domain::models::{CreateCustomerPayload, Customer, Sale, UpdateCustomerPayload};
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

#[tauri::command]
pub async fn get_customer_sales(
    pool: State<'_, SqlitePool>,
    customer_id: String,
) -> Result<Vec<Sale>, AppError> {
    let sales = sqlx::query_as::<_, Sale>(
        "SELECT id, invoice_number, customer_id, customer_name, customer_phone, biller_name,
                subtotal_paise, discount_paise, tax_paise, total_paise, paid_paise,
                payment_method, status, fulfillment_status, notes, created_at
         FROM sales
         WHERE customer_id = ?
         ORDER BY created_at DESC"
    )
    .bind(&customer_id)
    .fetch_all(&*pool)
    .await?;

    Ok(sales)
}
