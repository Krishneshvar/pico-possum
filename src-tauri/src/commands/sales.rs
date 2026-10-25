use sqlx::{Row, SqlitePool};
use tauri::State;

use crate::application::SalesService;
use crate::domain::models::{CreateSalePayload, Sale, SaleResponse};
use crate::errors::AppError;

#[tauri::command]
pub async fn process_sale(
    pool: State<'_, SqlitePool>,
    payload: CreateSalePayload,
) -> Result<SaleResponse, AppError> {
    SalesService::process_sale(&pool, payload).await
}

#[tauri::command]
pub async fn get_sales(
    pool: State<'_, SqlitePool>,
    limit: Option<i64>,
    offset: Option<i64>,
    query: Option<String>,
) -> Result<Vec<Sale>, AppError> {
    let lim = limit.unwrap_or(50);
    let off = offset.unwrap_or(0);
    SalesService::get_sales(&pool, lim, off, query).await
}

#[tauri::command]
pub async fn get_sale_details(
    pool: State<'_, SqlitePool>,
    sale_id: String,
) -> Result<SaleResponse, AppError> {
    SalesService::get_sale_details(&pool, &sale_id).await
}

#[tauri::command]
pub async fn cancel_sale(
    pool: State<'_, SqlitePool>,
    sale_id: String,
    reason: String,
) -> Result<Sale, AppError> {
    SalesService::cancel_sale(&pool, &sale_id, &reason).await
}

#[tauri::command]
pub async fn save_open_bill(
    pool: State<'_, SqlitePool>,
    bill_index: i64,
    payload: String,
) -> Result<(), AppError> {
    sqlx::query(
        "INSERT INTO pos_open_bills (bill_index, payload, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)
         ON CONFLICT(bill_index) DO UPDATE SET payload = excluded.payload, updated_at = CURRENT_TIMESTAMP"
    )
    .bind(bill_index)
    .bind(payload)
    .execute(&*pool)
    .await?;

    Ok(())
}

#[tauri::command]
pub async fn load_open_bills(
    pool: State<'_, SqlitePool>,
) -> Result<Vec<(i64, String)>, AppError> {
    let rows = sqlx::query("SELECT bill_index, payload FROM pos_open_bills ORDER BY bill_index ASC")
        .fetch_all(&*pool)
        .await?;

    let bills = rows.into_iter().map(|r| (r.get::<i64, _>("bill_index"), r.get::<String, _>("payload"))).collect();
    Ok(bills)
}

#[tauri::command]
pub async fn delete_open_bill(
    pool: State<'_, SqlitePool>,
    bill_index: i64,
) -> Result<(), AppError> {
    sqlx::query("DELETE FROM pos_open_bills WHERE bill_index = ?")
        .bind(bill_index)
        .execute(&*pool)
        .await?;

    Ok(())
}
