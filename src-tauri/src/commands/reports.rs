use sqlx::SqlitePool;
use tauri::State;

use crate::application::InventoryService;
use crate::domain::models::{AuditLog, SalesReportSummary, StockMovement, TopSellingProduct};
use crate::errors::AppError;

#[tauri::command]
pub async fn get_sales_report_summary(
    pool: State<'_, SqlitePool>,
    from_date: Option<String>,
    to_date: Option<String>,
) -> Result<SalesReportSummary, AppError> {
    InventoryService::get_sales_report_summary(&pool, from_date, to_date).await
}

#[tauri::command]
pub async fn get_top_selling_products(
    pool: State<'_, SqlitePool>,
    limit: Option<i64>,
) -> Result<Vec<TopSellingProduct>, AppError> {
    let lim = limit.unwrap_or(10);
    InventoryService::get_top_selling_products(&pool, lim).await
}

#[tauri::command]
pub async fn get_stock_movements(
    pool: State<'_, SqlitePool>,
    product_id: Option<String>,
    limit: Option<i64>,
) -> Result<Vec<StockMovement>, AppError> {
    let lim = limit.unwrap_or(100);
    InventoryService::get_stock_movements(&pool, product_id, lim).await
}

#[tauri::command]
pub async fn get_audit_logs(
    pool: State<'_, SqlitePool>,
    limit: Option<i64>,
) -> Result<Vec<AuditLog>, AppError> {
    let lim = limit.unwrap_or(100);
    let logs = sqlx::query_as::<_, AuditLog>(
        "SELECT id, event_type, entity_type, entity_id, description, created_at
         FROM audit_logs
         ORDER BY created_at DESC LIMIT ?"
    )
    .bind(lim)
    .fetch_all(&*pool)
    .await?;

    Ok(logs)
}
