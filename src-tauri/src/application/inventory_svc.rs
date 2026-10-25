use sqlx::{Row, SqlitePool};
use uuid::Uuid;

use crate::domain::models::{
    BillSettings, Category, CreateCategoryPayload, CreateCustomerPayload, CreateProductPayload,
    Customer, GeneralSettings, Product, SalesReportSummary, StockAdjustmentPayload,
    StockMovement, TopSellingProduct, UpdateCategoryPayload, UpdateProductPayload,
};
use crate::errors::AppError;

pub struct InventoryService;

impl InventoryService {
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
                    p.status, p.is_deleted,
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
                    p.status, p.is_deleted,
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
        let status = payload.status.unwrap_or_else(|| "active".to_string());
        let cost_price = payload.cost_price_paise.unwrap_or(0);
        let tax_rate = payload.tax_rate.unwrap_or(0.0);

        sqlx::query(
            "INSERT INTO products (id, name, sku, category_id, mrp_paise, cost_price_paise, tax_rate, current_stock, status, is_deleted)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"
        )
        .bind(&id)
        .bind(&payload.name)
        .bind(&payload.sku)
        .bind(&payload.category_id)
        .bind(payload.mrp_paise)
        .bind(cost_price)
        .bind(tax_rate)
        .bind(initial_stock)
        .bind(&status)
        .execute(&mut *tx)
        .await?;

        if initial_stock > 0 {
            let movement_id = Uuid::new_v4().to_string();
            sqlx::query(
                "INSERT INTO stock_movements (id, product_id, quantity_change, movement_type, reference_id, note)
                 VALUES (?, ?, ?, 'RESTOCK', NULL, 'Initial Stock')"
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
        sqlx::query(
            "UPDATE products SET
                name = ?,
                sku = ?,
                category_id = ?,
                mrp_paise = ?,
                cost_price_paise = ?,
                tax_rate = ?,
                status = ?,
                updated_at = CURRENT_TIMESTAMP
             WHERE id = ?"
        )
        .bind(&payload.name)
        .bind(&payload.sku)
        .bind(&payload.category_id)
        .bind(payload.mrp_paise)
        .bind(payload.cost_price_paise)
        .bind(payload.tax_rate)
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
        let mut tx = pool.begin().await?;

        let movement_id = Uuid::new_v4().to_string();
        let movement_type = if payload.quantity_change >= 0 { "RESTOCK" } else { "ADJUSTMENT" };

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

        tx.commit().await?;

        Self::get_product_by_id(pool, &payload.product_id).await
    }

    // ── Categories ───────────────────────────────────────────────────────────

    pub async fn get_categories(pool: &SqlitePool) -> Result<Vec<Category>, AppError> {
        let categories = sqlx::query_as::<_, Category>(
            "SELECT id, name, parent_id, is_deleted, created_at, updated_at
             FROM categories
             WHERE is_deleted = 0
             ORDER BY name ASC"
        )
        .fetch_all(pool)
        .await?;

        Ok(categories)
    }

    pub async fn create_category(
        pool: &SqlitePool,
        payload: CreateCategoryPayload,
    ) -> Result<Category, AppError> {
        let id = Uuid::new_v4().to_string();
        sqlx::query(
            "INSERT INTO categories (id, name, parent_id, is_deleted)
             VALUES (?, ?, ?, 0)"
        )
        .bind(&id)
        .bind(&payload.name)
        .bind(&payload.parent_id)
        .execute(pool)
        .await?;

        let cat = sqlx::query_as::<_, Category>(
            "SELECT id, name, parent_id, is_deleted, created_at, updated_at
             FROM categories WHERE id = ?"
        )
        .bind(&id)
        .fetch_one(pool)
        .await?;

        Ok(cat)
    }

    pub async fn update_category(
        pool: &SqlitePool,
        id: &str,
        payload: UpdateCategoryPayload,
    ) -> Result<Category, AppError> {
        sqlx::query(
            "UPDATE categories SET name = ?, parent_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        )
        .bind(&payload.name)
        .bind(&payload.parent_id)
        .bind(id)
        .execute(pool)
        .await?;

        let cat = sqlx::query_as::<_, Category>(
            "SELECT id, name, parent_id, is_deleted, created_at, updated_at
             FROM categories WHERE id = ?"
        )
        .bind(id)
        .fetch_one(pool)
        .await?;

        Ok(cat)
    }

    pub async fn delete_category(pool: &SqlitePool, id: &str) -> Result<(), AppError> {
        sqlx::query(
            "UPDATE categories SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        )
        .bind(id)
        .execute(pool)
        .await?;

        Ok(())
    }

    // ── Customers ────────────────────────────────────────────────────────────

    pub async fn get_customers(
        pool: &SqlitePool,
        search: Option<String>,
    ) -> Result<Vec<Customer>, AppError> {
        let pattern = search.map(|s| format!("%{}%", s.trim()));

        let customers = sqlx::query_as::<_, Customer>(
            "SELECT id, name, phone, address, is_deleted, created_at, updated_at
             FROM customers
             WHERE is_deleted = 0
               AND (?1 IS NULL OR name LIKE ?1 OR phone LIKE ?1)
             ORDER BY name ASC"
        )
        .bind(pattern)
        .fetch_all(pool)
        .await?;

        Ok(customers)
    }

    pub async fn create_customer(
        pool: &SqlitePool,
        payload: CreateCustomerPayload,
    ) -> Result<Customer, AppError> {
        let id = Uuid::new_v4().to_string();
        sqlx::query(
            "INSERT INTO customers (id, name, phone, address, is_deleted)
             VALUES (?, ?, ?, ?, 0)"
        )
        .bind(&id)
        .bind(&payload.name)
        .bind(&payload.phone)
        .bind(&payload.address)
        .execute(pool)
        .await?;

        let cust = sqlx::query_as::<_, Customer>(
            "SELECT id, name, phone, address, is_deleted, created_at, updated_at
             FROM customers WHERE id = ?"
        )
        .bind(&id)
        .fetch_one(pool)
        .await?;

        Ok(cust)
    }

    // ── Stock Movements ──────────────────────────────────────────────────────

    pub async fn get_stock_movements(
        pool: &SqlitePool,
        product_id: Option<String>,
        limit: i64,
    ) -> Result<Vec<StockMovement>, AppError> {
        let movements = sqlx::query_as::<_, StockMovement>(
            "SELECT sm.id, sm.product_id, p.name as product_name, sm.quantity_change,
                    sm.movement_type, sm.reference_id, sm.note, sm.created_at
             FROM stock_movements sm
             LEFT JOIN products p ON p.id = sm.product_id
             WHERE (?1 IS NULL OR sm.product_id = ?1)
             ORDER BY sm.created_at DESC LIMIT ?2"
        )
        .bind(product_id)
        .bind(limit)
        .fetch_all(pool)
        .await?;

        Ok(movements)
    }

    // ── Reports & Analytics ──────────────────────────────────────────────────

    pub async fn get_sales_report_summary(
        pool: &SqlitePool,
        from_date: Option<String>,
        to_date: Option<String>,
    ) -> Result<SalesReportSummary, AppError> {
        let from = from_date.unwrap_or_else(|| "1970-01-01".to_string());
        let to = to_date.unwrap_or_else(|| "2099-12-31".to_string());

        let row = sqlx::query(
            "SELECT
                COALESCE(SUM(total_paise), 0) as total_revenue,
                COALESCE(SUM(discount_paise), 0) as total_discount,
                COALESCE(SUM(tax_paise), 0) as total_tax,
                COUNT(id) as total_count,
                COALESCE(SUM(CASE WHEN payment_method = 'CASH' THEN total_paise ELSE 0 END), 0) as cash_rev,
                COALESCE(SUM(CASE WHEN payment_method = 'CARD' THEN total_paise ELSE 0 END), 0) as card_rev,
                COALESCE(SUM(CASE WHEN payment_method = 'UPI' THEN total_paise ELSE 0 END), 0) as upi_rev,
                COALESCE(SUM(CASE WHEN payment_method NOT IN ('CASH', 'CARD', 'UPI') THEN total_paise ELSE 0 END), 0) as other_rev
             FROM sales
             WHERE status = 'PAID' AND created_at >= ? AND created_at <= ?"
        )
        .bind(from)
        .bind(to)
        .fetch_one(pool)
        .await?;

        let total_revenue_paise: i64 = row.get("total_revenue");
        let total_discount_paise: i64 = row.get("total_discount");
        let total_tax_paise: i64 = row.get("total_tax");
        let total_sales_count: i64 = row.get("total_count");
        let cash_revenue_paise: i64 = row.get("cash_rev");
        let card_revenue_paise: i64 = row.get("card_rev");
        let upi_revenue_paise: i64 = row.get("upi_rev");
        let other_revenue_paise: i64 = row.get("other_rev");

        Ok(SalesReportSummary {
            total_revenue_paise,
            total_discount_paise,
            total_tax_paise,
            total_sales_count,
            cash_revenue_paise,
            card_revenue_paise,
            upi_revenue_paise,
            other_revenue_paise,
        })
    }

    pub async fn get_top_selling_products(
        pool: &SqlitePool,
        limit: i64,
    ) -> Result<Vec<TopSellingProduct>, AppError> {
        let rows = sqlx::query(
            "SELECT si.product_id, si.product_name,
                    SUM(si.quantity) as total_quantity,
                    SUM(si.total_paise) as total_revenue_paise
             FROM sale_items si
             JOIN sales s ON s.id = si.sale_id
             WHERE s.status = 'PAID'
             GROUP BY si.product_id, si.product_name
             ORDER BY total_quantity DESC LIMIT ?"
        )
        .bind(limit)
        .fetch_all(pool)
        .await?;

        let items = rows.into_iter().map(|r| {
            TopSellingProduct {
                product_id: r.get("product_id"),
                product_name: r.get("product_name"),
                total_quantity: r.get("total_quantity"),
                total_revenue_paise: r.get("total_revenue_paise"),
            }
        }).collect();

        Ok(items)
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    pub async fn get_general_settings(pool: &SqlitePool) -> Result<GeneralSettings, AppError> {
        let row = sqlx::query("SELECT value FROM app_settings WHERE key = 'general_settings'")
            .fetch_optional(pool)
            .await?;

        if let Some(r) = row {
            let val: String = r.get("value");
            let s: GeneralSettings = serde_json::from_str(&val)?;
            Ok(s)
        } else {
            Ok(GeneralSettings {
                store_name: "Pico POS".to_string(),
                phone: "".to_string(),
                address: "".to_string(),
                gstin: "".to_string(),
                currency_symbol: "₹".to_string(),
                currency_code: "INR".to_string(),
                date_format: "DD/MM/YYYY".to_string(),
                time_format: "12h".to_string(),
                default_printer_name: "".to_string(),
            })
        }
    }

    pub async fn update_general_settings(
        pool: &SqlitePool,
        settings: GeneralSettings,
    ) -> Result<(), AppError> {
        let val = serde_json::to_string(&settings)?;
        sqlx::query(
            "INSERT INTO app_settings (key, value, updated_at) VALUES ('general_settings', ?, CURRENT_TIMESTAMP)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = CURRENT_TIMESTAMP"
        )
        .bind(val)
        .execute(pool)
        .await?;

        Ok(())
    }

    pub async fn get_bill_settings(pool: &SqlitePool) -> Result<BillSettings, AppError> {
        let row = sqlx::query("SELECT value FROM app_settings WHERE key = 'bill_settings'")
            .fetch_optional(pool)
            .await?;

        if let Some(r) = row {
            let val: String = r.get("value");
            let s: BillSettings = serde_json::from_str(&val)?;
            Ok(s)
        } else {
            Ok(BillSettings {
                paper_width: "80mm".to_string(),
                store_name: "Pico POS".to_string(),
                store_details: "Retail".to_string(),
                phone: "".to_string(),
                gst: "".to_string(),
                show_tax: false,
                footer_text: "Thank you for shopping!".to_string(),
            })
        }
    }

    pub async fn update_bill_settings(
        pool: &SqlitePool,
        settings: BillSettings,
    ) -> Result<(), AppError> {
        let val = serde_json::to_string(&settings)?;
        sqlx::query(
            "INSERT INTO app_settings (key, value, updated_at) VALUES ('bill_settings', ?, CURRENT_TIMESTAMP)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = CURRENT_TIMESTAMP"
        )
        .bind(val)
        .execute(pool)
        .await?;

        Ok(())
    }
}
