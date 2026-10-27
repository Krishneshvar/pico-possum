use sqlx::SqlitePool;
use tauri::State;

use crate::application::InventoryService;
use crate::domain::models::{
    AuditLog, MultiYearSalesPoint, ProductFlowItem, SalesBreakdownItem, SalesReportSummary,
    StockMovement, TopSellingCategory, TopSellingProduct,
};
use crate::errors::AppError;

#[tauri::command]
pub async fn get_sales_report_summary(
    pool: State<'_, SqlitePool>,
    from_date: Option<String>,
    to_date: Option<String>,
    payment_method: Option<String>,
) -> Result<SalesReportSummary, AppError> {
    InventoryService::get_sales_report_summary(&pool, from_date, to_date, payment_method).await
}

#[tauri::command]
pub async fn get_sales_breakdown(
    pool: State<'_, SqlitePool>,
    period_type: String,
    from_date: Option<String>,
    to_date: Option<String>,
) -> Result<Vec<SalesBreakdownItem>, AppError> {
    InventoryService::get_sales_breakdown(&pool, &period_type, from_date, to_date).await
}

#[tauri::command]
pub async fn get_product_flow(
    pool: State<'_, SqlitePool>,
    product_id: Option<String>,
    from_date: Option<String>,
    to_date: Option<String>,
    limit: Option<i64>,
) -> Result<Vec<ProductFlowItem>, AppError> {
    let lim = limit.unwrap_or(200);
    InventoryService::get_product_flow(&pool, product_id, from_date, to_date, lim).await
}

#[tauri::command]
pub async fn get_top_selling_products(
    pool: State<'_, SqlitePool>,
    from_date: Option<String>,
    to_date: Option<String>,
    payment_method: Option<String>,
    limit: Option<i64>,
) -> Result<Vec<TopSellingProduct>, AppError> {
    let lim = limit.unwrap_or(10);
    InventoryService::get_top_selling_products(&pool, from_date, to_date, payment_method, lim).await
}

#[tauri::command]
pub async fn get_top_selling_categories(
    pool: State<'_, SqlitePool>,
    from_date: Option<String>,
    to_date: Option<String>,
    payment_method: Option<String>,
    limit: Option<i64>,
) -> Result<Vec<TopSellingCategory>, AppError> {
    let lim = limit.unwrap_or(10);
    InventoryService::get_top_selling_categories(&pool, from_date, to_date, payment_method, lim).await
}

#[tauri::command]
pub async fn get_multi_year_sales(
    pool: State<'_, SqlitePool>,
    years_back: Option<i64>,
    payment_method: Option<String>,
) -> Result<Vec<MultiYearSalesPoint>, AppError> {
    let yb = years_back.unwrap_or(3);
    InventoryService::get_multi_year_sales(&pool, yb, payment_method).await
}

#[tauri::command]
pub async fn get_stock_movements(
    pool: State<'_, SqlitePool>,
    product_id: Option<String>,
    from_date: Option<String>,
    to_date: Option<String>,
    limit: Option<i64>,
) -> Result<Vec<StockMovement>, AppError> {
    let lim = limit.unwrap_or(200);
    InventoryService::get_stock_movements(&pool, product_id, from_date, to_date, lim).await
}

#[tauri::command]
pub async fn get_audit_logs(
    pool: State<'_, SqlitePool>,
    limit: Option<i64>,
) -> Result<Vec<AuditLog>, AppError> {
    let lim = limit.unwrap_or(200);
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
