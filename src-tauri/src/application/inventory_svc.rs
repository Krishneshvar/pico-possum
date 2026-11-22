use sqlx::SqlitePool;
use uuid::Uuid;

use crate::application::{CategoryService, CustomerService, ProductService, ReportsService, SettingsService, ValidationService};
use crate::domain::models::{
    BillSettings, Category, CreateCategoryPayload, CreateCustomerPayload, CreateProductPayload,
    Customer, GeneralSettings, MultiYearSalesPoint, Product, ProductFlowItem, SalesBreakdownItem,
    SalesReportSummary, StockAdjustmentPayload, StockMovement, TopSellingCategory, TopSellingProduct,
    UpdateCategoryPayload, UpdateCustomerPayload, UpdateProductPayload,
};
use crate::errors::AppError;
use crate::infrastructure::AppLogger;

pub struct InventoryService;

impl InventoryService {
    #[inline]
    pub async fn get_next_numeric_sku(pool: &SqlitePool) -> Result<String, AppError> {
        ProductService::get_next_numeric_sku(pool).await
    }

    #[inline]
    pub async fn get_products(
        pool: &SqlitePool,
        category_id: Option<String>,
        search: Option<String>,
    ) -> Result<Vec<Product>, AppError> {
        ProductService::get_products(pool, category_id, search).await
    }

    #[inline]
    pub async fn get_products_with_deleted(
        pool: &SqlitePool,
        category_id: Option<String>,
        search: Option<String>,
        include_deleted: bool,
    ) -> Result<Vec<Product>, AppError> {
        ProductService::get_products_with_deleted(pool, category_id, search, include_deleted).await
    }

    #[inline]
    pub async fn get_product_by_id(
        pool: &SqlitePool,
        id: &str,
    ) -> Result<Product, AppError> {
        ProductService::get_product_by_id(pool, id).await
    }

    #[inline]
    pub async fn create_product(
        pool: &SqlitePool,
        payload: CreateProductPayload,
    ) -> Result<Product, AppError> {
        ProductService::create_product(pool, payload).await
    }

    #[inline]
    pub async fn update_product(
        pool: &SqlitePool,
        id: &str,
        payload: UpdateProductPayload,
    ) -> Result<Product, AppError> {
        ProductService::update_product(pool, id, payload).await
    }

    #[inline]
    pub async fn delete_product(
        pool: &SqlitePool,
        id: &str,
    ) -> Result<(), AppError> {
        ProductService::delete_product(pool, id).await
    }

    pub async fn adjust_stock(
        pool: &SqlitePool,
        payload: StockAdjustmentPayload,
    ) -> Result<Product, AppError> {
        ValidationService::validate_stock_adjustment(&payload)?;

        let reason_upper = payload.reason.trim().to_uppercase();
        let movement_type = match reason_upper.as_str() {
            "PURCHASE" => {
                if payload.quantity_change < 0 {
                    return Err(AppError::Validation(
                        "Cannot select Purchase reason for a negative stock deduction".to_string(),
                    ));
                }
                "PURCHASE"
            }
            "MANUAL_STOCK_CORRECTION" | "MANUAL_ADJUSTMENT" | "ADJUSTMENT" | "RESTOCK" => {
                if reason_upper == "RESTOCK" && payload.quantity_change > 0 {
                    "PURCHASE"
                } else {
                    "MANUAL_STOCK_CORRECTION"
                }
            }
            "BILL_ADJUSTMENT" => {
                return Err(AppError::Validation(
                    "Bill Adjustment reason is automatically assigned by bill amendments and cannot be manually selected".to_string(),
                ));
            }
            "RETURN" => {
                return Err(AppError::Validation(
                    "Return reason is automatically assigned when items are returned and cannot be manually selected".to_string(),
                ));
            }
            "CANCELLED" => {
                return Err(AppError::Validation(
                    "Cancelled reason is automatically assigned when bills are cancelled and cannot be manually selected".to_string(),
                ));
            }
            other => {
                return Err(AppError::Validation(format!(
                    "Invalid stock adjustment reason '{}'. Allowed manual reasons are 'Manual Stock Correction' or 'Purchase'.",
                    other
                )));
            }
        };

        let allow_negative_stock = SettingsService::get_general_settings(pool)
            .await?
            .allow_negative_stock;

        let mut tx = pool.begin().await?;

        let row: Option<(i64, i64)> = sqlx::query_as(
            "SELECT current_stock, is_deleted FROM products WHERE id = ?"
        )
        .bind(&payload.product_id)
        .fetch_optional(&mut *tx)
        .await?;

        let (current, is_deleted) = match row {
            Some(r) => r,
            None => return Err(AppError::NotFound(format!("Product {} not found", payload.product_id))),
        };

        if is_deleted != 0 {
            return Err(AppError::Validation(format!(
                "Cannot adjust stock for deleted product {}",
                payload.product_id
            )));
        }

        if !allow_negative_stock && payload.quantity_change < 0 && current + payload.quantity_change < 0 {
            return Err(AppError::Validation(format!(
                "Cannot deduct {} units: current stock is {} and negative stock is disabled",
                -payload.quantity_change, current
            )));
        }

        let movement_id = Uuid::new_v4().to_string();
        sqlx::query(
            "INSERT INTO stock_movements (id, product_id, quantity_change, movement_type, reference_id, note)
             VALUES (?, ?, ?, ?, NULL, ?)"
        )
        .bind(&movement_id)
        .bind(&payload.product_id)
        .bind(payload.quantity_change)
        .bind(movement_type)
        .bind(&payload.note)
        .execute(&mut *tx)
        .await?;

        sqlx::query(
            "UPDATE products SET current_stock = current_stock + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = 0"
        )
        .bind(payload.quantity_change)
        .bind(&payload.product_id)
        .execute(&mut *tx)
        .await?;

        let audit_id = Uuid::new_v4().to_string();
        let desc = format!(
            "Adjusted stock by {} (Reason: {})",
            payload.quantity_change, movement_type
        );
        sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description)
             VALUES (?, 'STOCK_ADJUSTED', 'PRODUCT', ?, ?)"
        )
        .bind(&audit_id)
        .bind(&payload.product_id)
        .bind(&desc)
        .execute(&mut *tx)
        .await?;

        AppLogger::info("Inventory", format!("Stock adjusted for product {}: delta {} ({})", payload.product_id, payload.quantity_change, movement_type));
        tx.commit().await?;

        ProductService::get_product_by_id(pool, &payload.product_id).await
    }

    // ── Delegated Facades (Maintains Full Backwards Compatibility) ────────────

    #[inline]
    pub async fn get_categories(pool: &SqlitePool) -> Result<Vec<Category>, AppError> {
        CategoryService::get_categories(pool).await
    }

    #[inline]
    pub async fn get_category_by_id(pool: &SqlitePool, id: &str) -> Result<Category, AppError> {
        CategoryService::get_category_by_id(pool, id).await
    }

    #[inline]
    pub async fn create_category(
        pool: &SqlitePool,
        payload: CreateCategoryPayload,
    ) -> Result<Category, AppError> {
        CategoryService::create_category(pool, payload).await
    }

    #[inline]
    pub async fn update_category(
        pool: &SqlitePool,
        id: &str,
        payload: UpdateCategoryPayload,
    ) -> Result<Category, AppError> {
        CategoryService::update_category(pool, id, payload).await
    }

    #[inline]
    pub async fn delete_category(pool: &SqlitePool, id: &str) -> Result<(), AppError> {
        CategoryService::delete_category(pool, id).await
    }

    #[inline]
    pub async fn get_customers(
        pool: &SqlitePool,
        search: Option<String>,
    ) -> Result<Vec<Customer>, AppError> {
        CustomerService::get_customers(pool, search).await
    }

    #[inline]
    pub async fn get_customer_by_id(pool: &SqlitePool, id: &str) -> Result<Customer, AppError> {
        CustomerService::get_customer_by_id(pool, id).await
    }

    #[inline]
    pub async fn create_customer(
        pool: &SqlitePool,
        payload: CreateCustomerPayload,
    ) -> Result<Customer, AppError> {
        CustomerService::create_customer(pool, payload).await
    }

    #[inline]
    pub async fn update_customer(
        pool: &SqlitePool,
        id: &str,
        payload: UpdateCustomerPayload,
    ) -> Result<Customer, AppError> {
        CustomerService::update_customer(pool, id, payload).await
    }

    #[inline]
    pub async fn delete_customer(pool: &SqlitePool, id: &str) -> Result<(), AppError> {
        CustomerService::delete_customer(pool, id).await
    }

    #[inline]
    pub async fn get_stock_movements(
        pool: &SqlitePool,
        product_id: Option<String>,
        from_date: Option<String>,
        to_date: Option<String>,
        limit: i64,
    ) -> Result<Vec<StockMovement>, AppError> {
        ReportsService::get_stock_movements(pool, product_id, from_date, to_date, limit).await
    }

    #[inline]
    pub async fn get_sales_report_summary(
        pool: &SqlitePool,
        from_date: Option<String>,
        to_date: Option<String>,
        payment_method: Option<String>,
    ) -> Result<SalesReportSummary, AppError> {
        ReportsService::get_sales_report_summary(pool, from_date, to_date, payment_method).await
    }

    #[inline]
    pub async fn get_top_selling_products(
        pool: &SqlitePool,
        from_date: Option<String>,
        to_date: Option<String>,
        payment_method: Option<String>,
        limit: i64,
    ) -> Result<Vec<TopSellingProduct>, AppError> {
        ReportsService::get_top_selling_products(pool, from_date, to_date, payment_method, limit).await
    }

    #[inline]
    pub async fn get_top_selling_categories(
        pool: &SqlitePool,
        from_date: Option<String>,
        to_date: Option<String>,
        payment_method: Option<String>,
        limit: i64,
    ) -> Result<Vec<TopSellingCategory>, AppError> {
        ReportsService::get_top_selling_categories(pool, from_date, to_date, payment_method, limit).await
    }

    #[inline]
    pub async fn get_multi_year_sales(
        pool: &SqlitePool,
        years_back: i64,
        payment_method: Option<String>,
    ) -> Result<Vec<MultiYearSalesPoint>, AppError> {
        ReportsService::get_multi_year_sales(pool, years_back, payment_method).await
    }

    #[inline]
    pub async fn get_sales_breakdown(
        pool: &SqlitePool,
        period_type: &str,
        from_date: Option<String>,
        to_date: Option<String>,
    ) -> Result<Vec<SalesBreakdownItem>, AppError> {
        ReportsService::get_sales_breakdown(pool, period_type, from_date, to_date).await
    }

    #[inline]
    pub async fn get_product_flow(
        pool: &SqlitePool,
        product_id: Option<String>,
        from_date: Option<String>,
        to_date: Option<String>,
        limit: i64,
    ) -> Result<Vec<ProductFlowItem>, AppError> {
        ReportsService::get_product_flow(pool, product_id, from_date, to_date, limit).await
    }

    #[inline]
    pub async fn get_general_settings(pool: &SqlitePool) -> Result<GeneralSettings, AppError> {
        SettingsService::get_general_settings(pool).await
    }

    #[inline]
    pub async fn update_general_settings(
        pool: &SqlitePool,
        settings: GeneralSettings,
    ) -> Result<(), AppError> {
        SettingsService::update_general_settings(pool, settings).await
    }

    #[inline]
    pub async fn get_bill_settings(pool: &SqlitePool) -> Result<BillSettings, AppError> {
        SettingsService::get_bill_settings(pool).await
    }

    #[inline]
    pub async fn update_bill_settings(
        pool: &SqlitePool,
        settings: BillSettings,
    ) -> Result<(), AppError> {
        SettingsService::update_bill_settings(pool, settings).await
    }
}
