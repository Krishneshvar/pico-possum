use sqlx::{Row, SqlitePool};
use uuid::Uuid;

use crate::application::SettingsService;
use crate::domain::models::{CreateProductPayload, GeneralSettings, Product, UpdateProductPayload};
use crate::errors::AppError;

pub struct ProductService;

const NEXT_SKU_SQL: &str =
    "SELECT COALESCE(MAX(CAST(TRIM(sku) AS INTEGER)), 0)
     FROM products
     WHERE sku IS NOT NULL
       AND TRIM(sku) <> ''
       AND length(TRIM(sku)) <= 18
       AND TRIM(sku) NOT GLOB '*[^0-9]*'";

impl ProductService {
    pub async fn auto_numeric_sku_enabled(pool: &SqlitePool) -> Result<bool, AppError> {
        Ok(SettingsService::get_general_settings(pool).await?.auto_numeric_sku)
    }

    pub async fn auto_numeric_sku_enabled_tx(
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

        Ok(true)
    }

    pub async fn next_numeric_sku_tx(
        tx: &mut sqlx::Transaction<'_, sqlx::Sqlite>,
    ) -> Result<String, AppError> {
        let max_sku: i64 = sqlx::query_scalar(NEXT_SKU_SQL).fetch_one(&mut **tx).await?;
        Ok((max_sku + 1).to_string())
    }

    pub async fn get_next_numeric_sku(pool: &SqlitePool) -> Result<String, AppError> {
        let max_sku: i64 = sqlx::query_scalar(NEXT_SKU_SQL).fetch_one(pool).await?;
        Ok((max_sku + 1).to_string())
    }

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
             WHERE p.id = ? AND p.is_deleted = 0"
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
            let note = "Initial stock on product creation";
            sqlx::query(
                "INSERT INTO stock_movements (id, product_id, quantity_change, movement_type, reference_id, note)
                 VALUES (?, ?, ?, 'PURCHASE', NULL, ?)"
            )
            .bind(&movement_id)
            .bind(&id)
            .bind(initial_stock)
            .bind(note)
            .execute(&mut *tx)
            .await?;
        }

        let audit_id = Uuid::new_v4().to_string();
        sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description)
             VALUES (?, 'PRODUCT_CREATED', 'PRODUCT', ?, ?)"
        )
        .bind(&audit_id)
        .bind(&id)
        .bind(format!("Created product {}", payload.name))
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
}
