use std::collections::HashMap;
use std::path::Path;
use std::str::FromStr;
use sqlx::sqlite::{SqliteConnectOptions, SqlitePool, SqlitePoolOptions, SqliteRow};
use sqlx::Row;
use uuid::Uuid;

use crate::domain::models::LegacyMigrationSummary;
use crate::errors::AppError;
use crate::infrastructure::paths::AppPaths;

pub struct MigrationService;

impl MigrationService {
    pub fn detect_legacy_path() -> Option<String> {
        AppPaths::detect_legacy_db_path().map(|p| p.to_string_lossy().to_string())
    }

    pub async fn migrate_from_legacy(
        target_pool: &SqlitePool,
        legacy_path_str: &str,
    ) -> Result<LegacyMigrationSummary, AppError> {
        let legacy_path = Path::new(legacy_path_str);
        if !legacy_path.exists() {
            return Err(AppError::Migration(format!(
                "Legacy database not found at {}",
                legacy_path_str
            )));
        }

        // Open read-only connection to legacy database
        let legacy_options = SqliteConnectOptions::from_str(&format!("sqlite://{}", legacy_path.to_string_lossy()))?
            .read_only(true);
        let legacy_pool = SqlitePoolOptions::new()
            .max_connections(1)
            .connect_with(legacy_options)
            .await?;

        let mut tx = target_pool.begin().await?;

        // 1. Categories
        let mut category_map: HashMap<i64, String> = HashMap::new();
        let cat_rows: Vec<SqliteRow> = sqlx::query("SELECT id, name, parent_id, deleted_at, created_at, updated_at FROM categories")
            .fetch_all(&legacy_pool)
            .await
            .unwrap_or_default();

        let mut categories_imported = 0;
        for row in &cat_rows {
            let old_id: i64 = row.get("id");
            let name: String = row.get("name");
            let deleted_at: Option<String> = row.get("deleted_at");
            let created_at: Option<String> = row.get("created_at");
            let updated_at: Option<String> = row.get("updated_at");

            let new_uuid = Uuid::new_v4().to_string();
            category_map.insert(old_id, new_uuid.clone());

            let is_deleted = deleted_at.is_some();
            let c_at = created_at.unwrap_or_else(|| chrono::Utc::now().to_rfc3339());
            let u_at = updated_at.unwrap_or_else(|| c_at.clone());

            sqlx::query(
                "INSERT INTO categories (id, name, parent_id, is_deleted, created_at, updated_at)
                 VALUES (?, ?, NULL, ?, ?, ?)
                 ON CONFLICT(name) DO UPDATE SET updated_at = excluded.updated_at"
            )
            .bind(&new_uuid)
            .bind(&name)
            .bind(is_deleted)
            .bind(&c_at)
            .bind(&u_at)
            .execute(&mut *tx)
            .await?;

            categories_imported += 1;
        }

        // Second pass for category parent_id
        for row in &cat_rows {
            let old_id: i64 = row.get("id");
            let parent_id_opt: Option<i64> = row.get("parent_id");
            if let Some(parent_old_id) = parent_id_opt {
                if let (Some(cat_uuid), Some(parent_uuid)) = (category_map.get(&old_id), category_map.get(&parent_old_id)) {
                    sqlx::query("UPDATE categories SET parent_id = ? WHERE id = ?")
                        .bind(parent_uuid)
                        .bind(cat_uuid)
                        .execute(&mut *tx)
                        .await?;
                }
            }
        }

        // 2. Customers
        let mut customer_map: HashMap<i64, String> = HashMap::new();
        let cust_rows: Vec<SqliteRow> = sqlx::query("SELECT id, name, phone, address, deleted_at, created_at, updated_at FROM customers")
            .fetch_all(&legacy_pool)
            .await
            .unwrap_or_default();

        let mut customers_imported = 0;
        for row in &cust_rows {
            let old_id: i64 = row.get("id");
            let name: String = row.get("name");
            let phone: Option<String> = row.get("phone");
            let address: Option<String> = row.get("address");
            let deleted_at: Option<String> = row.get("deleted_at");
            let created_at: Option<String> = row.get("created_at");
            let updated_at: Option<String> = row.get("updated_at");

            let new_uuid = Uuid::new_v4().to_string();
            customer_map.insert(old_id, new_uuid.clone());

            let is_deleted = deleted_at.is_some();
            let c_at = created_at.unwrap_or_else(|| chrono::Utc::now().to_rfc3339());
            let u_at = updated_at.unwrap_or_else(|| c_at.clone());

            sqlx::query(
                "INSERT INTO customers (id, name, phone, address, is_deleted, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?)"
            )
            .bind(&new_uuid)
            .bind(&name)
            .bind(&phone)
            .bind(&address)
            .bind(is_deleted)
            .bind(&c_at)
            .bind(&u_at)
            .execute(&mut *tx)
            .await?;

            customers_imported += 1;
        }

        // 3. Products
        let mut product_map: HashMap<i64, String> = HashMap::new();
        let prod_rows: Vec<SqliteRow> = sqlx::query(
            "SELECT p.id, p.name, p.category_id, p.tax_rate, p.status, p.sku, p.mrp, p.cost_price, p.deleted_at, p.created_at, p.updated_at,
                    COALESCE(c.current_stock, 0) as cached_stock
             FROM products p
             LEFT JOIN product_stock_cache c ON c.product_id = p.id"
        )
        .fetch_all(&legacy_pool)
        .await
        .unwrap_or_default();

        let mut products_imported = 0;
        for row in &prod_rows {
            let old_id: i64 = row.get("id");
            let name: String = row.get("name");
            let sku: Option<String> = row.get("sku");
            let old_cat_id: Option<i64> = row.get("category_id");
            let tax_rate: f64 = row.try_get("tax_rate").unwrap_or(0.0);
            let status: String = row.try_get("status").unwrap_or_else(|_| "active".to_string());
            let mrp_num: f64 = row.try_get("mrp").unwrap_or(0.0);
            let cost_num: f64 = row.try_get("cost_price").unwrap_or(0.0);
            let cached_stock: i64 = row.try_get("cached_stock").unwrap_or(0);
            let deleted_at: Option<String> = row.get("deleted_at");
            let created_at: Option<String> = row.get("created_at");
            let updated_at: Option<String> = row.get("updated_at");

            let new_uuid = Uuid::new_v4().to_string();
            product_map.insert(old_id, new_uuid.clone());

            let mrp_paise = (mrp_num * 100.0).round() as i64;
            let cost_price_paise = (cost_num * 100.0).round() as i64;
            let target_cat_id = old_cat_id.and_then(|cid| category_map.get(&cid).cloned());
            let is_deleted = deleted_at.is_some();
            let c_at = created_at.unwrap_or_else(|| chrono::Utc::now().to_rfc3339());
            let u_at = updated_at.unwrap_or_else(|| c_at.clone());

            sqlx::query(
                "INSERT INTO products (id, name, sku, category_id, mrp_paise, cost_price_paise, tax_rate, current_stock, status, is_deleted, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 ON CONFLICT(sku) DO UPDATE SET
                    name = excluded.name,
                    mrp_paise = excluded.mrp_paise,
                    current_stock = excluded.current_stock,
                    updated_at = excluded.updated_at"
            )
            .bind(&new_uuid)
            .bind(&name)
            .bind(&sku)
            .bind(&target_cat_id)
            .bind(mrp_paise)
            .bind(cost_price_paise)
            .bind(tax_rate)
            .bind(cached_stock)
            .bind(&status)
            .bind(is_deleted)
            .bind(&c_at)
            .bind(&u_at)
            .execute(&mut *tx)
            .await?;

            products_imported += 1;
        }

        // 4. Sales & Sale Items
        let sale_rows: Vec<SqliteRow> = sqlx::query(
            "SELECT id, invoice_number, sale_date, biller_name, total_amount, paid_amount, discount, tax_amount, status, fulfillment_status, customer_id, customer_name, customer_phone, payment_method_name
             FROM sales"
        )
        .fetch_all(&legacy_pool)
        .await
        .unwrap_or_default();

        let mut sales_imported = 0;
        let mut sale_map: HashMap<i64, String> = HashMap::new();

        for row in &sale_rows {
            let old_id: i64 = row.get("id");
            let invoice_number: String = row.get("invoice_number");
            let sale_date: Option<String> = row.get("sale_date");
            let biller_name: Option<String> = row.get("biller_name");
            let total_f64: f64 = row.try_get("total_amount").unwrap_or(0.0);
            let paid_f64: f64 = row.try_get("paid_amount").unwrap_or(0.0);
            let discount_f64: f64 = row.try_get("discount").unwrap_or(0.0);
            let tax_f64: f64 = row.try_get("tax_amount").unwrap_or(0.0);
            let status: String = row.try_get("status").unwrap_or_else(|_| "PAID".to_string()).to_uppercase();
            let fulfillment_status: String = row.try_get("fulfillment_status").unwrap_or_else(|_| "FULFILLED".to_string()).to_uppercase();
            let old_cust_id: Option<i64> = row.get("customer_id");
            let customer_name: Option<String> = row.get("customer_name");
            let customer_phone: Option<String> = row.get("customer_phone");
            let raw_pm: Option<String> = row.get("payment_method_name");

            let new_sale_uuid = Uuid::new_v4().to_string();
            sale_map.insert(old_id, new_sale_uuid.clone());

            let target_cust_id = old_cust_id.and_then(|cid| customer_map.get(&cid).cloned());
            let total_paise = (total_f64 * 100.0).round() as i64;
            let paid_paise = (paid_f64 * 100.0).round() as i64;
            let discount_paise = (discount_f64 * 100.0).round() as i64;
            let tax_paise = (tax_f64 * 100.0).round() as i64;
            let subtotal_paise = total_paise + discount_paise;

            let payment_method = match raw_pm.as_deref().unwrap_or("Cash").to_uppercase().as_str() {
                "CARD" => "CARD",
                "UPI" => "UPI",
                "GIFT CARD" | "GIFT_CARD" => "GIFT_CARD",
                _ => "CASH",
            };

            let s_date = sale_date.unwrap_or_else(|| chrono::Utc::now().to_rfc3339());

            sqlx::query(
                "INSERT INTO sales (id, invoice_number, customer_id, customer_name, customer_phone, biller_name, subtotal_paise, discount_paise, tax_paise, total_paise, paid_paise, payment_method, status, fulfillment_status, notes, created_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
                 ON CONFLICT(invoice_number) DO NOTHING"
            )
            .bind(&new_sale_uuid)
            .bind(&invoice_number)
            .bind(&target_cust_id)
            .bind(&customer_name)
            .bind(&customer_phone)
            .bind(&biller_name)
            .bind(subtotal_paise)
            .bind(discount_paise)
            .bind(tax_paise)
            .bind(total_paise)
            .bind(paid_paise)
            .bind(payment_method)
            .bind(&status)
            .bind(&fulfillment_status)
            .bind(&s_date)
            .execute(&mut *tx)
            .await?;

            sales_imported += 1;
        }

        // Sale items
        let item_rows: Vec<SqliteRow> = sqlx::query(
            "SELECT si.id, si.sale_id, si.product_id, si.quantity, si.price_per_unit, si.cost_per_unit, si.discount_amount, si.tax_rate, si.tax_amount,
                    p.name as product_name, p.sku as product_sku
             FROM sale_items si
             LEFT JOIN products p ON p.id = si.product_id"
        )
        .fetch_all(&legacy_pool)
        .await
        .unwrap_or_default();

        let mut sale_items_imported = 0;
        for row in &item_rows {
            let old_sale_id: i64 = row.get("sale_id");
            let old_prod_id: i64 = row.get("product_id");

            if let (Some(sale_uuid), Some(prod_uuid)) = (sale_map.get(&old_sale_id), product_map.get(&old_prod_id)) {
                let quantity: i64 = row.try_get("quantity").unwrap_or(1);
                let price_num: f64 = row.try_get("price_per_unit").unwrap_or(0.0);
                let cost_num: f64 = row.try_get("cost_per_unit").unwrap_or(0.0);
                let disc_num: f64 = row.try_get("discount_amount").unwrap_or(0.0);
                let tax_rate: f64 = row.try_get("tax_rate").unwrap_or(0.0);
                let tax_num: f64 = row.try_get("tax_amount").unwrap_or(0.0);
                let p_name: String = row.try_get("product_name").unwrap_or_else(|_| "Item".to_string());
                let p_sku: Option<String> = row.try_get("product_sku").ok();

                let unit_price_paise = (price_num * 100.0).round() as i64;
                let cost_price_paise = (cost_num * 100.0).round() as i64;
                let discount_paise = (disc_num * 100.0).round() as i64;
                let tax_paise = (tax_num * 100.0).round() as i64;
                let total_paise = (unit_price_paise * quantity) - discount_paise;

                let item_uuid = Uuid::new_v4().to_string();

                sqlx::query(
                    "INSERT INTO sale_items (id, sale_id, product_id, product_name, product_sku, quantity, unit_price_paise, cost_price_paise, discount_paise, tax_rate, tax_paise, total_paise)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )
                .bind(&item_uuid)
                .bind(sale_uuid)
                .bind(prod_uuid)
                .bind(&p_name)
                .bind(&p_sku)
                .bind(quantity)
                .bind(unit_price_paise)
                .bind(cost_price_paise)
                .bind(discount_paise)
                .bind(tax_rate)
                .bind(tax_paise)
                .bind(total_paise)
                .execute(&mut *tx)
                .await?;

                sale_items_imported += 1;
            }
        }

        // 5. Stock movements
        let sm_rows: Vec<SqliteRow> = sqlx::query(
            "SELECT id, product_id, quantity_change, reference_id, notes, created_at FROM stock_movements"
        )
        .fetch_all(&legacy_pool)
        .await
        .unwrap_or_default();

        let mut stock_movements_imported = 0;
        for row in &sm_rows {
            let old_prod_id: i64 = row.get("product_id");
            if let Some(prod_uuid) = product_map.get(&old_prod_id) {
                let qty_change: i64 = row.get("quantity_change");
                let old_ref_id: Option<i64> = row.get("reference_id");
                let notes: Option<String> = row.get("notes");
                let created_at: Option<String> = row.get("created_at");

                let movement_uuid = Uuid::new_v4().to_string();
                let ref_uuid = old_ref_id.and_then(|rid| sale_map.get(&rid).cloned());
                let movement_type = if ref_uuid.is_some() || qty_change < 0 {
                    "SALE"
                } else {
                    "RESTOCK"
                };

                let c_at = created_at.unwrap_or_else(|| chrono::Utc::now().to_rfc3339());

                sqlx::query(
                    "INSERT INTO stock_movements (id, product_id, quantity_change, movement_type, reference_id, note, created_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?)"
                )
                .bind(&movement_uuid)
                .bind(prod_uuid)
                .bind(qty_change)
                .bind(movement_type)
                .bind(&ref_uuid)
                .bind(&notes)
                .bind(&c_at)
                .execute(&mut *tx)
                .await?;

                stock_movements_imported += 1;
            }
        }

        // 6. Settings import if available
        let mut settings_imported = false;
        if let Some(settings_dir) = AppPaths::detect_legacy_settings_dir() {
            let gen_file = settings_dir.join("general-settings.json");
            if gen_file.exists() {
                if let Ok(content) = std::fs::read_to_string(&gen_file) {
                    let _ = sqlx::query("INSERT OR REPLACE INTO app_settings (key, value, updated_at) VALUES ('general_settings', ?, CURRENT_TIMESTAMP)")
                        .bind(content)
                        .execute(&mut *tx)
                        .await;
                    settings_imported = true;
                }
            }
            let bill_file = settings_dir.join("bill-settings.json");
            if bill_file.exists() {
                if let Ok(content) = std::fs::read_to_string(&bill_file) {
                    let _ = sqlx::query("INSERT OR REPLACE INTO app_settings (key, value, updated_at) VALUES ('bill_settings', ?, CURRENT_TIMESTAMP)")
                        .bind(content)
                        .execute(&mut *tx)
                        .await;
                    settings_imported = true;
                }
            }
        }

        // 7. Audit log the migration
        let audit_uuid = Uuid::new_v4().to_string();
        sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
             VALUES (?, 'LEGACY_MIGRATION', 'SYSTEM', 'DATABASE', ?, CURRENT_TIMESTAMP)"
        )
        .bind(&audit_uuid)
        .bind(format!(
            "Imported {} categories, {} products, {} customers, {} sales from {}",
            categories_imported, products_imported, customers_imported, sales_imported, legacy_path_str
        ))
        .execute(&mut *tx)
        .await?;

        tx.commit().await?;

        Ok(LegacyMigrationSummary {
            source_path: legacy_path_str.to_string(),
            categories_imported,
            products_imported,
            customers_imported,
            sales_imported,
            sale_items_imported,
            stock_movements_imported,
            settings_imported,
        })
    }
}
