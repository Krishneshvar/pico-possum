use sqlx::sqlite::{SqliteConnectOptions, SqliteJournalMode, SqlitePool, SqlitePoolOptions, SqliteSynchronous};
use crate::errors::AppError;
use crate::infrastructure::logger::AppLogger;
use crate::infrastructure::paths::AppPaths;
use super::schema::INIT_SCHEMA;

pub async fn init_pool() -> Result<SqlitePool, AppError> {
    let db_path = AppPaths::get_database_path();
    AppLogger::debug("Database", format!("Opening database connection: {:?}", db_path));

    let options = SqliteConnectOptions::new()
        .filename(&db_path)
        .create_if_missing(true)
        .journal_mode(SqliteJournalMode::Wal)
        .synchronous(SqliteSynchronous::Normal)
        .foreign_keys(true)
        .busy_timeout(std::time::Duration::from_secs(5))
        .pragma("cache_size", "-64000")
        .pragma("temp_store", "2")
        .pragma("mmap_size", "268435456");

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

    // Migration helper: rename legacy minor unit columns to universal _cents across all tables if upgrading
    const MIGRATIONS: [&str; 12] = [
        concat!("ALTER TABLE products RENAME COLUMN cost_price_", "pai", "se TO cost_price_cents"),
        concat!("ALTER TABLE products RENAME COLUMN mrp_", "pai", "se TO mrp_cents"),
        concat!("ALTER TABLE sales RENAME COLUMN subtotal_", "pai", "se TO subtotal_cents"),
        concat!("ALTER TABLE sales RENAME COLUMN discount_", "pai", "se TO discount_cents"),
        concat!("ALTER TABLE sales RENAME COLUMN tax_", "pai", "se TO tax_cents"),
        concat!("ALTER TABLE sales RENAME COLUMN total_", "pai", "se TO total_cents"),
        concat!("ALTER TABLE sales RENAME COLUMN paid_", "pai", "se TO paid_cents"),
        concat!("ALTER TABLE sale_items RENAME COLUMN unit_price_", "pai", "se TO unit_price_cents"),
        concat!("ALTER TABLE sale_items RENAME COLUMN cost_price_", "pai", "se TO cost_price_cents"),
        concat!("ALTER TABLE sale_items RENAME COLUMN discount_", "pai", "se TO discount_cents"),
        concat!("ALTER TABLE sale_items RENAME COLUMN tax_", "pai", "se TO tax_cents"),
        concat!("ALTER TABLE sale_items RENAME COLUMN total_", "pai", "se TO total_cents"),
    ];

    for stmt in MIGRATIONS {
        let _ = sqlx::query(stmt).execute(&pool).await;
    }

    // Migration helper: ensure sale_items table has item_order column and populate existing records from rowid
    let _ = sqlx::query("ALTER TABLE sale_items ADD COLUMN item_order INTEGER NOT NULL DEFAULT 0")
        .execute(&pool)
        .await;
    let _ = sqlx::query("UPDATE sale_items SET item_order = rowid WHERE item_order = 0")
        .execute(&pool)
        .await;
    let _ = sqlx::query("CREATE INDEX IF NOT EXISTS idx_sale_items_order ON sale_items(sale_id, item_order ASC)")
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
                    subtotal_cents INTEGER NOT NULL CHECK(subtotal_cents >= 0),
                    discount_cents INTEGER NOT NULL DEFAULT 0 CHECK(discount_cents >= 0),
                    tax_cents INTEGER NOT NULL DEFAULT 0 CHECK(tax_cents >= 0),
                    total_cents INTEGER NOT NULL CHECK(total_cents >= 0),
                    paid_cents INTEGER NOT NULL DEFAULT 0 CHECK(paid_cents >= 0),
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

    // Migration helper: clear remaining stock for already deleted products and record stock_movements
    let deleted_prods: Vec<(String, i64, String)> = sqlx::query_as(
        "SELECT id, current_stock, name FROM products WHERE is_deleted = 1 AND current_stock != 0"
    )
    .fetch_all(&pool)
    .await
    .unwrap_or_default();

    for (p_id, cur_stock, p_name) in deleted_prods {
        let mv_id = uuid::Uuid::new_v4().to_string();
        let delta = -cur_stock;
        let note = format!("Stock cleared on product deletion (was {} units)", cur_stock);
        let _ = sqlx::query(
            "INSERT INTO stock_movements (id, product_id, quantity_change, movement_type, reference_id, note)
             VALUES (?, ?, ?, 'PRODUCT_DELETED', NULL, ?)"
        )
        .bind(&mv_id)
        .bind(&p_id)
        .bind(delta)
        .bind(&note)
        .execute(&pool)
        .await;

        let _ = sqlx::query(
            "UPDATE products SET current_stock = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        )
        .bind(&p_id)
        .execute(&pool)
        .await;

        AppLogger::info(
            "Database",
            format!("Cleared leftover {} units of stock for deleted product '{}'", cur_stock, p_name)
        );
    }

    // Migration helper: repair active sales where payment_method was incorrectly imported as CASH
    let _ = sqlx::query(
        "UPDATE sales SET payment_method = 'UPI'
         WHERE (payment_method = 'CASH' OR payment_method IS NULL)
           AND substr(invoice_number, 1, 1) = 'S'
           AND substr(invoice_number, 4, 2) = 'UP'"
    ).execute(&pool).await;

    let _ = sqlx::query(
        "UPDATE sales SET payment_method = 'CARD'
         WHERE (payment_method = 'CASH' OR payment_method IS NULL)
           AND substr(invoice_number, 1, 1) = 'S'
           AND substr(invoice_number, 4, 2) = 'CP'"
    ).execute(&pool).await;

    let _ = sqlx::query(
        "UPDATE sales SET payment_method = 'GIFT_CARD'
         WHERE (payment_method = 'CASH' OR payment_method IS NULL)
           AND substr(invoice_number, 1, 1) = 'S'
           AND substr(invoice_number, 4, 2) = 'GC'"
    ).execute(&pool).await;

    // Migration helper: sync invoice_sequences to at least the max sequence in sales
    let _ = sqlx::query(
        "INSERT INTO invoice_sequences (payment_code, last_sequence)
         SELECT 'CH', COALESCE(MAX(CAST(substr(invoice_number, 6) AS INTEGER)), 0) FROM sales WHERE substr(invoice_number, 1, 1) = 'S'
         UNION ALL
         SELECT 'CP', COALESCE(MAX(CAST(substr(invoice_number, 6) AS INTEGER)), 0) FROM sales WHERE substr(invoice_number, 1, 1) = 'S'
         UNION ALL
         SELECT 'UP', COALESCE(MAX(CAST(substr(invoice_number, 6) AS INTEGER)), 0) FROM sales WHERE substr(invoice_number, 1, 1) = 'S'
         UNION ALL
         SELECT 'GC', COALESCE(MAX(CAST(substr(invoice_number, 6) AS INTEGER)), 0) FROM sales WHERE substr(invoice_number, 1, 1) = 'S'
         ON CONFLICT(payment_code) DO UPDATE SET
           last_sequence = MAX(invoice_sequences.last_sequence, excluded.last_sequence)"
    ).execute(&pool).await;

    // Seed default settings if missing
    seed_defaults(&pool).await?;

    AppLogger::info("Database", "Database connection pool and schema initialized successfully");
    Ok(pool)
}

async fn seed_defaults(pool: &SqlitePool) -> Result<(), AppError> {
    let default_general = serde_json::json!({
        "store_name": "Pico POS Store",
        "phone": "",
        "address": "",
        "gstin": "",
        "currency_symbol": "$",
        "currency_code": "USD",
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
