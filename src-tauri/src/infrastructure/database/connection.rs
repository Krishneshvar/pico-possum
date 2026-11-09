use sqlx::sqlite::{SqliteConnectOptions, SqliteJournalMode, SqlitePool, SqlitePoolOptions, SqliteSynchronous};
use std::str::FromStr;
use crate::errors::AppError;
use crate::infrastructure::paths::AppPaths;
use super::schema::INIT_SCHEMA;

pub async fn init_pool() -> Result<SqlitePool, AppError> {
    let db_path = AppPaths::get_database_path();
    let db_path_str = db_path.to_string_lossy();

    let options = SqliteConnectOptions::from_str(&format!("sqlite://{}", db_path_str))?
        .create_if_missing(true)
        .journal_mode(SqliteJournalMode::Wal)
        .synchronous(SqliteSynchronous::Normal)
        .foreign_keys(true)
        .busy_timeout(std::time::Duration::from_secs(5));

    let pool = SqlitePoolOptions::new()
        .max_connections(10)
        .connect_with(options)
        .await?;

    // Execute schema migrations
    sqlx::raw_sql(INIT_SCHEMA).execute(&pool).await?;

    // Migration helper: add min_stock_alert column to products if missing
    let _ = sqlx::query("ALTER TABLE products ADD COLUMN min_stock_alert INTEGER NOT NULL DEFAULT 5")
        .execute(&pool)
        .await;

    // Migration helper: ensure sales table check constraint supports 'LEGACY' status
    let sales_sql: Option<String> = sqlx::query_scalar("SELECT sql FROM sqlite_master WHERE type='table' AND name='sales'")
        .fetch_optional(&pool)
        .await
        .ok()
        .flatten();

    if let Some(sql) = sales_sql {
        if !sql.contains("'LEGACY'") {
            let _ = sqlx::raw_sql("
                PRAGMA foreign_keys=OFF;
                CREATE TABLE IF NOT EXISTS sales_upgrade_tmp (
                    id TEXT PRIMARY KEY,
                    invoice_number TEXT NOT NULL UNIQUE,
                    customer_id TEXT REFERENCES customers(id) ON DELETE SET NULL,
                    customer_name TEXT,
                    customer_phone TEXT,
                    biller_name TEXT,
                    subtotal_paise INTEGER NOT NULL CHECK(subtotal_paise >= 0),
                    discount_paise INTEGER NOT NULL DEFAULT 0 CHECK(discount_paise >= 0),
                    tax_paise INTEGER NOT NULL DEFAULT 0 CHECK(tax_paise >= 0),
                    total_paise INTEGER NOT NULL CHECK(total_paise >= 0),
                    paid_paise INTEGER NOT NULL DEFAULT 0 CHECK(paid_paise >= 0),
                    payment_method TEXT NOT NULL,
                    status TEXT NOT NULL CHECK(status IN ('PAID', 'PARTIALLY_PAID', 'CANCELLED', 'REFUNDED', 'LEGACY')) DEFAULT 'PAID',
                    fulfillment_status TEXT NOT NULL DEFAULT 'FULFILLED',
                    notes TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                );
                INSERT OR IGNORE INTO sales_upgrade_tmp SELECT * FROM sales;
                DROP TABLE sales;
                ALTER TABLE sales_upgrade_tmp RENAME TO sales;
                CREATE INDEX IF NOT EXISTS idx_sales_invoice ON sales(invoice_number);
                CREATE INDEX IF NOT EXISTS idx_sales_date ON sales(created_at);
                PRAGMA foreign_keys=ON;
            ").execute(&pool).await;
        }
    }

    // Migration helper: ensure all past imported legacy records have 'LEGACY' status
    let _ = sqlx::query(
        "UPDATE sales SET status = 'LEGACY' WHERE status = 'PAID' AND (biller_name = 'Legacy Import' OR notes LIKE '%Imported legacy bill%')"
    )
    .execute(&pool)
    .await;

    // Seed default settings if missing
    seed_defaults(&pool).await?;

    Ok(pool)
}

async fn seed_defaults(pool: &SqlitePool) -> Result<(), AppError> {
    let default_general = serde_json::json!({
        "store_name": "Pico POS Store",
        "phone": "",
        "address": "",
        "gstin": "",
        "currency_symbol": "₹",
        "currency_code": "INR",
        "date_format": "DD/MM/YYYY",
        "time_format": "12h",
        "default_printer_name": "",
        "auto_numeric_sku": true,
        "allow_negative_stock": false
    });

    let default_bill = serde_json::json!({
        "paper_width": "80mm",
        "store_name": "Pico POS Store",
        "store_details": "Retail Shop",
        "phone": "",
        "gst": "",
        "show_tax": false,
        "footer_text": "Thank you for shopping with us!"
    });

    sqlx::query(
        "INSERT OR IGNORE INTO app_settings (key, value) VALUES ('general_settings', ?), ('bill_settings', ?)"
    )
    .bind(default_general.to_string())
    .bind(default_bill.to_string())
    .execute(pool)
    .await?;

    Ok(())
}
