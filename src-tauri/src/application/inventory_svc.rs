use sqlx::{Row, SqlitePool};
use uuid::Uuid;

use crate::application::{CategoryService, CustomerService, ReportsService, SettingsService};
use crate::domain::models::{
    BillSettings, Category, CreateCategoryPayload, CreateCustomerPayload, CreateProductPayload,
    Customer, GeneralSettings, MultiYearSalesPoint, Product, ProductFlowItem, SalesBreakdownItem,
    SalesReportSummary, StockAdjustmentPayload, StockMovement, TopSellingCategory, TopSellingProduct,
    UpdateCategoryPayload, UpdateCustomerPayload, UpdateProductPayload,
};
use crate::errors::AppError;

pub struct InventoryService;

const NEXT_SKU_SQL: &str =
    "SELECT COALESCE(MAX(CAST(TRIM(sku) AS INTEGER)), 0)
     FROM products
     WHERE sku IS NOT NULL
       AND TRIM(sku) <> ''
       AND length(TRIM(sku)) <= 18
       AND TRIM(sku) NOT GLOB '*[^0-9]*'";

impl InventoryService {
    /// Whether new products should automatically receive the next numerical SKU.
    async fn auto_numeric_sku_enabled(pool: &SqlitePool) -> Result<bool, AppError> {
        Ok(Self::get_general_settings(pool).await?.auto_numeric_sku)
    }

    /// Read the auto-SKU setting from within an existing transaction.
    async fn auto_numeric_sku_enabled_tx(
        tx: &mut sqlx::Transaction<'_, sqlx::Sqlite>,
    ) -> Result<bool, AppError> {
        let row = sqlx::query("SELECT value FROM app_settings WHERE key = 'general_settings'")
            .fetch_optional(&mut **tx)
            .await?;

        if let Some(r) = row {
            let val: String = r.get("value");
            if let Ok(settings) = serde_json::from_str::<GeneralSettings>(&val) {
                return Ok(settings.auto_numeric_sku);
            }
        }

        // Field missing / settings not persisted yet -> default enabled.
        Ok(true)
    }

    async fn next_numeric_sku_tx(
        tx: &mut sqlx::Transaction<'_, sqlx::Sqlite>,
    ) -> Result<String, AppError> {
        let max_sku: i64 = sqlx::query_scalar(NEXT_SKU_SQL).fetch_one(&mut **tx).await?;
        Ok((max_sku + 1).to_string())
    }

    /// Returns the next unused numerical SKU based on the highest numeric SKU
    /// already present (including imported and soft-deleted products).
    pub async fn get_next_numeric_sku(pool: &SqlitePool) -> Result<String, AppError> {
        let max_sku: i64 = sqlx::query_scalar(NEXT_SKU_SQL).fetch_one(pool).await?;
        Ok((max_sku + 1).to_string())
    }

    // ── Products ─────────────────────────────────────────────────────────────

    pub async fn get_products(
        pool: &SqlitePool,
        category_id: Option<String>,
        search: Option<String>,
    ) -> Result<Vec<Product>, AppError> {
        let pattern = search.map(|s| format!("%{}%", s.trim()));

        let products = sqlx::query_as::<_, Product>(
            "SELECT p.id, p.name, p.sku, p.category_id, c.name as category_name,
                    p.mrp_paise, p.cost_price_paise, CAST(p.tax_rate AS REAL) as tax_rate, p.current_stock,
                    p.min_stock_alert, p.status, p.is_deleted,
                    p.created_at, p.updated_at
             FROM products p
             LEFT JOIN categories c ON c.id = p.category_id
             WHERE p.is_deleted = 0
               AND (?1 IS NULL OR p.category_id = ?1)
               AND (?2 IS NULL OR p.name LIKE ?2 OR p.sku LIKE ?2)
             ORDER BY p.name ASC"
        )
        .bind(category_id)
        .bind(pattern)
        .fetch_all(pool)
        .await?;

        Ok(products)
    }

    pub async fn get_product_by_id(
        pool: &SqlitePool,
        id: &str,
    ) -> Result<Product, AppError> {
        let product = sqlx::query_as::<_, Product>(
            "SELECT p.id, p.name, p.sku, p.category_id, c.name as category_name,
                    p.mrp_paise, p.cost_price_paise, CAST(p.tax_rate AS REAL) as tax_rate, p.current_stock,
                    p.min_stock_alert, p.status, p.is_deleted,
                    p.created_at, p.updated_at
             FROM products p
             LEFT JOIN categories c ON c.id = p.category_id
             WHERE p.id = ?"
        )
        .bind(id)
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Product {} not found", id)))?;

        Ok(product)
    }

    pub async fn create_product(
        pool: &SqlitePool,
        payload: CreateProductPayload,
    ) -> Result<Product, AppError> {
        let mut tx = pool.begin().await?;

        let id = Uuid::new_v4().to_string();
        let initial_stock = payload.initial_stock.unwrap_or(0);
        let min_stock_alert = payload.min_stock_alert.unwrap_or(5);
        let status = payload.status.unwrap_or_else(|| "active".to_string());
        let cost_price = payload.cost_price_paise.unwrap_or(0);
        let tax_rate = payload.tax_rate.unwrap_or(0.0);

        // When automatic numerical SKUs are enabled the SKU is always generated
        // server-side from the latest available number. This also keeps the
        // sequence correct after legacy/CSV imports.
        let sku = if Self::auto_numeric_sku_enabled_tx(&mut tx).await? {
            Some(Self::next_numeric_sku_tx(&mut tx).await?)
        } else {
            payload.sku.clone()
        };

        sqlx::query(
            "INSERT INTO products (id, name, sku, category_id, mrp_paise, cost_price_paise, tax_rate, current_stock, min_stock_alert, status, is_deleted)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"
        )
        .bind(&id)
        .bind(&payload.name)
        .bind(&sku)
        .bind(&payload.category_id)
        .bind(payload.mrp_paise)
        .bind(cost_price)
        .bind(tax_rate)
        .bind(initial_stock)
        .bind(min_stock_alert)
        .bind(&status)
        .execute(&mut *tx)
        .await?;

        if initial_stock > 0 {
            let movement_id = Uuid::new_v4().to_string();
            sqlx::query(
                "INSERT INTO stock_movements (id, product_id, quantity_change, movement_type, reference_id, note)
                 VALUES (?, ?, ?, 'PURCHASE', NULL, 'Initial Stock')"
            )
            .bind(&movement_id)
            .bind(&id)
            .bind(initial_stock)
            .execute(&mut *tx)
            .await?;
        }

        // Audit log
        let audit_id = Uuid::new_v4().to_string();
        let desc = format!("Created product '{}'", payload.name);
        sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description)
             VALUES (?, 'PRODUCT_CREATED', 'PRODUCT', ?, ?)"
        )
        .bind(&audit_id)
        .bind(&id)
        .bind(&desc)
        .execute(&mut *tx)
        .await?;

        tx.commit().await?;

        Self::get_product_by_id(pool, &id).await
    }

    pub async fn update_product(
        pool: &SqlitePool,
        id: &str,
        payload: UpdateProductPayload,
    ) -> Result<Product, AppError> {
        let min_stock_alert = payload.min_stock_alert.unwrap_or(5);

        // SKU is not editable while automatic numerical SKUs are enabled; keep
        // the product's existing SKU (including legacy non-numeric SKUs).
        let sku = if Self::auto_numeric_sku_enabled(pool).await? {
            sqlx::query_scalar::<_, Option<String>>("SELECT sku FROM products WHERE id = ?")
                .bind(id)
                .fetch_optional(pool)
                .await?
                .flatten()
        } else {
            payload.sku.clone()
        };

        sqlx::query(
            "UPDATE products SET
                name = ?,
                sku = ?,
                category_id = ?,
                mrp_paise = ?,
                cost_price_paise = ?,
                tax_rate = ?,
                min_stock_alert = ?,
                status = ?,
                updated_at = CURRENT_TIMESTAMP
             WHERE id = ?"
        )
        .bind(&payload.name)
        .bind(&sku)
        .bind(&payload.category_id)
        .bind(payload.mrp_paise)
        .bind(payload.cost_price_paise)
        .bind(payload.tax_rate)
        .bind(min_stock_alert)
        .bind(&payload.status)
        .bind(id)
        .execute(pool)
        .await?;

        Self::get_product_by_id(pool, id).await
    }

    pub async fn delete_product(
        pool: &SqlitePool,
        id: &str,
    ) -> Result<(), AppError> {
        sqlx::query(
            "UPDATE products SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        )
        .bind(id)
        .execute(pool)
        .await?;

        let audit_id = Uuid::new_v4().to_string();
        sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description)
             VALUES (?, 'PRODUCT_SOFT_DELETED', 'PRODUCT', ?, 'Soft deleted product')"
        )
        .bind(&audit_id)
        .bind(id)
        .execute(pool)
        .await?;

        Ok(())
    }

    pub async fn adjust_stock(
        pool: &SqlitePool,
        payload: StockAdjustmentPayload,
    ) -> Result<Product, AppError> {
        if payload.quantity_change == 0 {
            return Err(AppError::Validation("Quantity change cannot be zero".to_string()));
        }

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
                // If legacy RESTOCK is provided with positive qty, map to PURCHASE; if negative, map to MANUAL_STOCK_CORRECTION
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

        let mut tx = pool.begin().await?;

        let allow_negative_stock = Self::get_general_settings(pool)
            .await?
            .allow_negative_stock;

        if !allow_negative_stock && payload.quantity_change < 0 {
            let current: i64 = sqlx::query_scalar("SELECT current_stock FROM products WHERE id = ?")
                .bind(&payload.product_id)
                .fetch_optional(&mut *tx)
                .await?
                .ok_or_else(|| AppError::NotFound(format!("Product {} not found", payload.product_id)))?;

            if current + payload.quantity_change < 0 {
                return Err(AppError::Validation(format!(
                    "Cannot deduct {} units: current stock is {} and negative stock is disabled",
                    -payload.quantity_change, current
                )));
            }
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
            "UPDATE products SET current_stock = current_stock + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        )
        .bind(payload.quantity_change)
        .bind(&payload.product_id)
        .execute(&mut *tx)
        .await?;

        // Audit log
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

        tx.commit().await?;

        Self::get_product_by_id(pool, &payload.product_id).await
    }

    // ── Delegated Facades (Maintains Full Backwards Compatibility) ────────────

    #[inline]
    pub async fn get_categories(pool: &SqlitePool) -> Result<Vec<Category>, AppError> {
        CategoryService::get_categories(pool).await
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
