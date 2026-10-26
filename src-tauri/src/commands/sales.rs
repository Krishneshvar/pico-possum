use sqlx::{Row, SqlitePool};
use tauri::State;

use crate::application::{AuthSessionManager, SalesService};
use crate::domain::models::{CreateSalePayload, Sale, SaleResponse, UpdateSalePayload};
use crate::errors::AppError;

/// Guard helper — returns Err(Unauthorized) if no active session.
async fn require_auth(session_mgr: &AuthSessionManager) -> Result<(), AppError> {
    if !session_mgr.is_authenticated().await {
        return Err(AppError::Unauthorized("Authentication required".to_string()));
    }
    Ok(())
}

#[tauri::command]
pub async fn process_sale(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    payload: CreateSalePayload,
) -> Result<SaleResponse, AppError> {
    require_auth(&session_mgr).await?;
    SalesService::process_sale(&pool, payload).await
}

#[tauri::command]
pub async fn update_sale(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    payload: UpdateSalePayload,
) -> Result<SaleResponse, AppError> {
    require_auth(&session_mgr).await?;
    SalesService::update_sale(&pool, payload).await
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
    session_mgr: State<'_, AuthSessionManager>,
    sale_id: String,
    reason: String,
) -> Result<Sale, AppError> {
    require_auth(&session_mgr).await?;
    SalesService::cancel_sale(&pool, &sale_id, &reason).await
}

#[tauri::command]
pub async fn save_open_bill(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    bill_index: i64,
    payload: String,
) -> Result<(), AppError> {
    require_auth(&session_mgr).await?;
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
    session_mgr: State<'_, AuthSessionManager>,
    bill_index: i64,
) -> Result<(), AppError> {
    require_auth(&session_mgr).await?;
    sqlx::query("DELETE FROM pos_open_bills WHERE bill_index = ?")
        .bind(bill_index)
        .execute(&*pool)
        .await?;

    Ok(())
}
