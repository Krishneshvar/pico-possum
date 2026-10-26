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
