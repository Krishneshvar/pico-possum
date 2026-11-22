use sqlx::SqlitePool;
use tauri::State;

use crate::application::{AuthSessionManager, CustomerService};
use crate::domain::models::{CreateCustomerPayload, Customer, Sale, UpdateCustomerPayload};
use crate::errors::AppError;

async fn require_auth(session_mgr: &AuthSessionManager) -> Result<(), AppError> {
    if !session_mgr.is_authenticated().await {
        return Err(AppError::Unauthorized("Authentication required".to_string()));
    }
    Ok(())
}

#[tauri::command]
pub async fn get_customers(
    pool: State<'_, SqlitePool>,
    search: Option<String>,
) -> Result<Vec<Customer>, AppError> {
    CustomerService::get_customers(&pool, search).await
}

#[tauri::command]
pub async fn get_customer_by_id(
    pool: State<'_, SqlitePool>,
    id: String,
) -> Result<Customer, AppError> {
    CustomerService::get_customer_by_id(&pool, &id).await
}

#[tauri::command]
pub async fn create_customer(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    payload: CreateCustomerPayload,
) -> Result<Customer, AppError> {
    require_auth(&session_mgr).await?;
    CustomerService::create_customer(&pool, payload).await
}

#[tauri::command]
pub async fn update_customer(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    id: String,
    payload: UpdateCustomerPayload,
) -> Result<Customer, AppError> {
    require_auth(&session_mgr).await?;
    CustomerService::update_customer(&pool, &id, payload).await
}

#[tauri::command]
pub async fn delete_customer(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    id: String,
) -> Result<(), AppError> {
    require_auth(&session_mgr).await?;
    CustomerService::delete_customer(&pool, &id).await
}

#[tauri::command]
pub async fn get_customer_sales(
    pool: State<'_, SqlitePool>,
    customer_id: String,
) -> Result<Vec<Sale>, AppError> {
    let sales = sqlx::query_as::<_, Sale>(
        "SELECT id, invoice_number, customer_id, customer_name, customer_phone, biller_name,
                subtotal_cents, discount_cents, tax_cents, total_cents, paid_cents,
                payment_method, status, fulfillment_status, notes, created_at
         FROM sales
         WHERE customer_id = ?1
            OR (customer_phone IS NOT NULL AND customer_phone != '' AND customer_phone = (SELECT phone FROM customers WHERE id = ?1 AND phone IS NOT NULL AND phone != ''))
         ORDER BY created_at DESC"
    )
    .bind(&customer_id)
    .fetch_all(&*pool)
    .await?;

    Ok(sales)
}
