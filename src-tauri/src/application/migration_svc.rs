use std::collections::HashMap;
use std::path::Path;
use sqlx::sqlite::{SqliteConnectOptions, SqlitePool, SqlitePoolOptions, SqliteRow};
use sqlx::Row;
use uuid::Uuid;

use crate::domain::models::{BillSettings, GeneralSettings, LegacyMigrationSummary};
use crate::errors::AppError;
use crate::infrastructure::paths::AppPaths;

pub struct MigrationService;

#[inline]
fn get_f64_val(row: &SqliteRow, col: &str) -> f64 {
    row.try_get::<f64, _>(col)
        .or_else(|_| row.try_get::<i64, _>(col).map(|v| v as f64))
        .or_else(|_| row.try_get::<i32, _>(col).map(|v| v as f64))
        .or_else(|_| {
            row.try_get::<String, _>(col)
                .and_then(|s| s.trim().parse::<f64>().map_err(|e| sqlx::Error::Decode(Box::new(e))))
        })
        .unwrap_or(0.0)
}

#[inline]
fn get_i64_val(row: &SqliteRow, col: &str, default: i64) -> i64 {
    row.try_get::<i64, _>(col)
        .or_else(|_| row.try_get::<f64, _>(col).map(|v| v.round() as i64))
        .or_else(|_| row.try_get::<i32, _>(col).map(|v| v as i64))
        .or_else(|_| {
            row.try_get::<String, _>(col)
                .and_then(|s| s.trim().parse::<i64>().map_err(|e| sqlx::Error::Decode(Box::new(e))))
        })
        .unwrap_or(default)
}

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
            return Err(AppError::Migration(format!("Legacy database not found at {}", legacy_path_str)));
        }

        let legacy_options = SqliteConnectOptions::new().filename(legacy_path).read_only(true);
        let legacy_pool = SqlitePoolOptions::new().max_connections(1).connect_with(legacy_options).await?;
        let mut tx = target_pool.begin().await?;

        // 0. Users & Credentials
        let user_rows: Vec<SqliteRow> = sqlx::query("SELECT id, username, password_hash, name FROM users")
            .fetch_all(&legacy_pool).await.unwrap_or_default();
        for row in &user_rows {
            let uname: String = row.get("username");
            let phash: String = row.get("password_hash");
            let display_name = row.get::<Option<String>, _>("name").unwrap_or_else(|| uname.clone());
            let user_uuid = Uuid::new_v4().to_string();

            sqlx::query(
                "INSERT INTO users (id, username, password_hash, name, is_active) VALUES (?, ?, ?, ?, 1)
                 ON CONFLICT(username) DO UPDATE SET password_hash = excluded.password_hash, name = excluded.name"
            )
            .bind(&user_uuid).bind(&uname).bind(&phash).bind(&display_name)
            .execute(&mut *tx).await?;
        }

        // 1. Categories
        let mut category_map: HashMap<i64, String> = HashMap::new();
        let cat_rows: Vec<SqliteRow> = sqlx::query("SELECT id, name, parent_id, deleted_at, created_at, updated_at FROM categories")
            .fetch_all(&legacy_pool).await.unwrap_or_default();

        let mut categories_imported = 0;
        for row in &cat_rows {
            let old_id: i64 = row.get("id");
            let name: String = row.get("name");
            let is_deleted = row.get::<Option<String>, _>("deleted_at").is_some();
            let c_at = row.get::<Option<String>, _>("created_at").unwrap_or_else(|| chrono::Utc::now().to_rfc3339());
            let u_at = row.get::<Option<String>, _>("updated_at").unwrap_or_else(|| c_at.clone());
            let new_uuid = Uuid::new_v4().to_string();
            category_map.insert(old_id, new_uuid.clone());

            sqlx::query(
                "INSERT INTO categories (id, name, parent_id, is_deleted, created_at, updated_at) VALUES (?, ?, NULL, ?, ?, ?)
                 ON CONFLICT(name) DO UPDATE SET updated_at = excluded.updated_at"
            )
            .bind(&new_uuid).bind(&name).bind(is_deleted).bind(&c_at).bind(&u_at)
            .execute(&mut *tx).await?;
            categories_imported += 1;
        }

        // Second pass for category parent_id
        for row in &cat_rows {
            let old_id: i64 = row.get("id");
            if let Some(p_old_id) = row.get::<Option<i64>, _>("parent_id") {
                if let (Some(cat_uuid), Some(p_uuid)) = (category_map.get(&old_id), category_map.get(&p_old_id)) {
                    sqlx::query("UPDATE categories SET parent_id = ? WHERE id = ?")
                        .bind(p_uuid).bind(cat_uuid).execute(&mut *tx).await?;
                }
            }
        }

        // 2. Customers
        let mut customer_map: HashMap<i64, String> = HashMap::new();
        let cust_rows: Vec<SqliteRow> = sqlx::query("SELECT id, name, phone, address, deleted_at, created_at, updated_at FROM customers")
            .fetch_all(&legacy_pool).await.unwrap_or_default();

        let mut customers_imported = 0;
        for row in &cust_rows {
            let old_id: i64 = row.get("id");
            let name: String = row.get("name");
            let phone: Option<String> = row.get("phone");
            let address: Option<String> = row.get("address");
            let is_deleted = row.get::<Option<String>, _>("deleted_at").is_some();
            let c_at = row.get::<Option<String>, _>("created_at").unwrap_or_else(|| chrono::Utc::now().to_rfc3339());
            let u_at = row.get::<Option<String>, _>("updated_at").unwrap_or_else(|| c_at.clone());
            let new_uuid = Uuid::new_v4().to_string();
            customer_map.insert(old_id, new_uuid.clone());

            sqlx::query(
                "INSERT INTO customers (id, name, phone, address, is_deleted, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
            )
            .bind(&new_uuid).bind(&name).bind(&phone).bind(&address).bind(is_deleted).bind(&c_at).bind(&u_at)
            .execute(&mut *tx).await?;
            customers_imported += 1;
        }

        // 3. Products
        let mut product_map: HashMap<i64, String> = HashMap::new();
        let prod_rows: Vec<SqliteRow> = sqlx::query(
            "SELECT p.id, p.name, p.category_id, CAST(p.tax_rate AS REAL) as tax_rate, p.status, p.sku,
                    CAST(p.mrp AS REAL) as mrp, CAST(p.cost_price AS REAL) as cost_price,
                    p.deleted_at, p.created_at, p.updated_at,
                    COALESCE(c.current_stock, 0) as cached_stock
             FROM products p
             LEFT JOIN product_stock_cache c ON c.product_id = p.id"
        )
        .fetch_all(&legacy_pool).await.unwrap_or_default();

        let mut products_imported = 0;
        for row in &prod_rows {
            let old_id: i64 = row.get("id");
            let name: String = row.get("name");
            let sku: Option<String> = row.get("sku");
            let old_cat_id: Option<i64> = row.get("category_id");
            let tax_rate = get_f64_val(row, "tax_rate");
            let status: String = row.try_get("status").unwrap_or_else(|_| "active".to_string());
            let mrp_num = get_f64_val(row, "mrp");
            let cost_num = get_f64_val(row, "cost_price");
            let cached_stock = get_i64_val(row, "cached_stock", 0);
            let is_deleted = row.get::<Option<String>, _>("deleted_at").is_some();
            let c_at = row.get::<Option<String>, _>("created_at").unwrap_or_else(|| chrono::Utc::now().to_rfc3339());
            let u_at = row.get::<Option<String>, _>("updated_at").unwrap_or_else(|| c_at.clone());

            let new_uuid = Uuid::new_v4().to_string();
            product_map.insert(old_id, new_uuid.clone());

            let mrp_cents = (mrp_num * 100.0).round() as i64;
            let cost_price_cents = (cost_num * 100.0).round() as i64;
            let target_cat_id = old_cat_id.and_then(|cid| category_map.get(&cid).cloned());

            sqlx::query(
                "INSERT INTO products (id, name, sku, category_id, mrp_cents, cost_price_cents, tax_rate, current_stock, status, is_deleted, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 ON CONFLICT(sku) DO UPDATE SET
                    name = excluded.name,
                    mrp_cents = excluded.mrp_cents,
                    current_stock = excluded.current_stock,
                    updated_at = excluded.updated_at"
            )
            .bind(&new_uuid).bind(&name).bind(&sku).bind(&target_cat_id).bind(mrp_cents)
            .bind(cost_price_cents).bind(tax_rate).bind(cached_stock).bind(&status).bind(is_deleted).bind(&c_at).bind(&u_at)
            .execute(&mut *tx).await?;
            products_imported += 1;
        }

        // 4. Sales & Sale Items
        let sale_rows: Vec<SqliteRow> = sqlx::query(
            "SELECT id, invoice_number, sale_date, biller_name,
                    CAST(total_amount AS REAL) as total_amount,
                    CAST(paid_amount AS REAL) as paid_amount,
                    CAST(discount AS REAL) as discount,
                    CAST(tax_amount AS REAL) as tax_amount,
                    status, fulfillment_status, customer_id, customer_name, customer_phone, payment_method_name
             FROM sales"
        )
        .fetch_all(&legacy_pool).await.unwrap_or_default();

        let mut sales_imported = 0;
        let mut sale_map: HashMap<i64, String> = HashMap::new();

        for row in &sale_rows {
            let old_id: i64 = row.get("id");
            let invoice_number: String = row.get("invoice_number");
            let sale_date: Option<String> = row.get("sale_date");
            let biller_name: Option<String> = row.get("biller_name");
            let total_f64 = get_f64_val(row, "total_amount");
            let paid_f64 = get_f64_val(row, "paid_amount");
            let discount_f64 = get_f64_val(row, "discount");
            let tax_f64 = get_f64_val(row, "tax_amount");
            let status: String = row.try_get("status").unwrap_or_else(|_| "PAID".to_string()).to_uppercase();
            let fulfillment_status: String = row.try_get("fulfillment_status").unwrap_or_else(|_| "FULFILLED".to_string()).to_uppercase();
            let old_cust_id: Option<i64> = row.get("customer_id");
            let customer_name: Option<String> = row.get("customer_name");
            let customer_phone: Option<String> = row.get("customer_phone");
            let raw_pm: Option<String> = row.get("payment_method_name");

            let new_sale_uuid = Uuid::new_v4().to_string();
            sale_map.insert(old_id, new_sale_uuid.clone());

            let target_cust_id = old_cust_id.and_then(|cid| customer_map.get(&cid).cloned());
            let total_cents = (total_f64 * 100.0).round() as i64;
            let paid_cents = (paid_f64 * 100.0).round() as i64;
            let discount_cents = (discount_f64 * 100.0).round() as i64;
            let tax_cents = (tax_f64 * 100.0).round() as i64;
            let subtotal_cents = total_cents + discount_cents;

            let payment_method = match raw_pm.as_deref().unwrap_or("Cash").to_uppercase().as_str() {
                "CARD" => "CARD",
                "UPI" => "UPI",
                "GIFT CARD" | "GIFT_CARD" => "GIFT_CARD",
                _ => "CASH",
            };
            let s_date = sale_date.unwrap_or_else(|| chrono::Utc::now().to_rfc3339());

            sqlx::query(
                "INSERT INTO sales (id, invoice_number, customer_id, customer_name, customer_phone, biller_name, subtotal_cents, discount_cents, tax_cents, total_cents, paid_cents, payment_method, status, fulfillment_status, notes, created_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
                 ON CONFLICT(invoice_number) DO NOTHING"
            )
            .bind(&new_sale_uuid).bind(&invoice_number).bind(&target_cust_id).bind(&customer_name).bind(&customer_phone)
            .bind(&biller_name).bind(subtotal_cents).bind(discount_cents).bind(tax_cents).bind(total_cents).bind(paid_cents)
            .bind(payment_method).bind(&status).bind(&fulfillment_status).bind(&s_date)
            .execute(&mut *tx).await?;

            sales_imported += 1;
        }

        // 4b. Legacy Historical Sales if table exists
        let has_legacy_sales: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'legacy_sales'"
        )
        .fetch_one(&legacy_pool).await.unwrap_or(0);

        if has_legacy_sales > 0 {
            let leg_rows: Vec<SqliteRow> = sqlx::query(
                "SELECT invoice_number, sale_date, customer_name, CAST(net_amount AS REAL) as net_amount, payment_method_name, source_file FROM legacy_sales"
            )
            .fetch_all(&legacy_pool).await.unwrap_or_default();

            for row in &leg_rows {
                let inv: String = row.get("invoice_number");
                let s_date: Option<String> = row.get("sale_date");
                let c_name: Option<String> = row.get("customer_name");
                let net_num = get_f64_val(row, "net_amount");
                let raw_pm: Option<String> = row.get("payment_method_name");
                let src: Option<String> = row.get("source_file");

                let uid = Uuid::new_v4().to_string();
                let cents = (net_num * 100.0).round() as i64;
                let pm = match raw_pm.as_deref().unwrap_or("Cash").to_uppercase().as_str() {
                    "CARD" => "CARD",
                    "UPI" => "UPI",
                    "GIFT CARD" | "GIFT_CARD" => "GIFT_CARD",
                    _ => "CASH",
                };
                let dt = s_date.unwrap_or_else(|| chrono::Utc::now().to_rfc3339());

                let res = sqlx::query(
                    "INSERT INTO sales (id, invoice_number, customer_id, customer_name, customer_phone, biller_name, subtotal_cents, discount_cents, tax_cents, total_cents, paid_cents, payment_method, status, fulfillment_status, notes, created_at)
                     VALUES (?, ?, NULL, ?, NULL, NULL, ?, 0, 0, ?, ?, ?, 'LEGACY', 'FULFILLED', ?, ?)
                     ON CONFLICT(invoice_number) DO NOTHING"
                )
                .bind(&uid).bind(&inv).bind(&c_name).bind(cents).bind(cents).bind(cents)
                .bind(pm).bind(&src).bind(&dt)
                .execute(&mut *tx).await?;

                if res.rows_affected() > 0 {
                    sales_imported += 1;
                }
            }
        }

        // Sale items
        let item_rows: Vec<SqliteRow> = sqlx::query(
            "SELECT si.id, si.sale_id, si.product_id,
                    CAST(si.quantity AS INTEGER) as quantity,
                    CAST(si.price_per_unit AS REAL) as price_per_unit,
                    CAST(si.cost_per_unit AS REAL) as cost_per_unit,
                    CAST(si.discount_amount AS REAL) as discount_amount,
                    CAST(si.tax_rate AS REAL) as tax_rate,
                    CAST(si.tax_amount AS REAL) as tax_amount,
                    p.name as product_name, p.sku as product_sku
             FROM sale_items si
             LEFT JOIN products p ON p.id = si.product_id
             ORDER BY si.sale_id ASC, si.id ASC"
        )
        .fetch_all(&legacy_pool).await.unwrap_or_default();

        let mut sale_item_order_map: HashMap<String, i64> = HashMap::new();
        let mut sale_items_imported = 0;
        for row in &item_rows {
            let old_sale_id: i64 = row.get("sale_id");
            let old_prod_id: i64 = row.get("product_id");

            if let (Some(sale_uuid), Some(prod_uuid)) = (sale_map.get(&old_sale_id), product_map.get(&old_prod_id)) {
                let quantity = get_i64_val(row, "quantity", 1);
                let price_num = get_f64_val(row, "price_per_unit");
                let cost_num = get_f64_val(row, "cost_per_unit");
                let disc_num = get_f64_val(row, "discount_amount");
                let tax_rate = get_f64_val(row, "tax_rate");
                let tax_num = get_f64_val(row, "tax_amount");
                let p_name: String = row.try_get("product_name").unwrap_or_else(|_| "Item".to_string());
                let p_sku: Option<String> = row.try_get("product_sku").ok();

                let unit_price_cents = (price_num * 100.0).round() as i64;
                let cost_price_cents = (cost_num * 100.0).round() as i64;
                let discount_cents = (disc_num * 100.0).round() as i64;
                let tax_cents = (tax_num * 100.0).round() as i64;
                let total_cents = (unit_price_cents * quantity) - discount_cents;

                let item_uuid = Uuid::new_v4().to_string();
                let order_entry = sale_item_order_map.entry(sale_uuid.clone()).or_insert(0);
                let current_order = *order_entry;
                *order_entry += 1;

                sqlx::query(
                    "INSERT INTO sale_items (id, sale_id, product_id, product_name, product_sku, quantity, unit_price_cents, cost_price_cents, discount_cents, tax_rate, tax_cents, total_cents, item_order)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )
                .bind(&item_uuid).bind(sale_uuid).bind(prod_uuid).bind(&p_name).bind(&p_sku)
                .bind(quantity).bind(unit_price_cents).bind(cost_price_cents).bind(discount_cents)
                .bind(tax_rate).bind(tax_cents).bind(total_cents).bind(current_order)
                .execute(&mut *tx).await?;

                sale_items_imported += 1;
            }
        }

        // 5. Stock movements
        let sm_rows: Vec<SqliteRow> = sqlx::query(
            "SELECT id, product_id, CAST(quantity_change AS INTEGER) as quantity_change, reference_id, notes, created_at FROM stock_movements"
        )
        .fetch_all(&legacy_pool).await.unwrap_or_default();

        let mut stock_movements_imported = 0;
        for row in &sm_rows {
            let old_prod_id: i64 = row.get("product_id");
            if let Some(prod_uuid) = product_map.get(&old_prod_id) {
                let qty_change = get_i64_val(row, "quantity_change", 0);
                let old_ref_id: Option<i64> = row.get("reference_id");
                let notes: Option<String> = row.get("notes");
                let created_at: Option<String> = row.get("created_at");

                let movement_uuid = Uuid::new_v4().to_string();
                let ref_uuid = old_ref_id.and_then(|rid| sale_map.get(&rid).cloned());
                let movement_type = if ref_uuid.is_some() || qty_change < 0 { "SALE" } else { "RESTOCK" };
                let c_at = created_at.unwrap_or_else(|| chrono::Utc::now().to_rfc3339());

                sqlx::query(
                    "INSERT INTO stock_movements (id, product_id, quantity_change, movement_type, reference_id, note, created_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?)"
                )
                .bind(&movement_uuid).bind(prod_uuid).bind(qty_change).bind(movement_type).bind(&ref_uuid).bind(&notes).bind(&c_at)
                .execute(&mut *tx).await?;

                stock_movements_imported += 1;
            }
        }

        // 6. Settings import if available
        let mut settings_imported = false;
        if let Some(settings_dir) = AppPaths::detect_legacy_settings_dir() {
            for (fname, key) in [("general-settings.json", "general_settings"), ("bill-settings.json", "bill_settings")] {
                let file = settings_dir.join(fname);
                if let Ok(content) = std::fs::read_to_string(&file) {
                    let to_store = if key == "general_settings" {
                        serde_json::from_str::<GeneralSettings>(&content)
                            .ok()
                            .and_then(|s| serde_json::to_string(&s).ok())
                            .unwrap_or(content)
                    } else {
                        serde_json::from_str::<BillSettings>(&content)
                            .ok()
                            .and_then(|s| serde_json::to_string(&s).ok())
                            .unwrap_or(content)
                    };
                    let _ = sqlx::query("INSERT OR REPLACE INTO app_settings (key, value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)")
                        .bind(key).bind(to_store).execute(&mut *tx).await;
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
        .execute(&mut *tx).await?;

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
