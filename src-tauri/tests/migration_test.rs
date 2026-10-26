use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};
use std::str::FromStr;

#[tokio::test]
async fn test_migration_against_legacy_db() {
    let legacy_db_path = dirs::data_dir()
        .expect("Must have data dir")
        .join("pico-possum")
        .join("database")
        .join("pico-possum.db");

    if !legacy_db_path.exists() {
        println!("Legacy DB not found at {:?}, skipping test", legacy_db_path);
        return;
    }

    // Create an in-memory or temp SQLite database for the target
    let temp_dir = tempfile::tempdir().expect("Failed to create tempdir");
    let target_db_file = temp_dir.path().join("test-possum.db");

    let options = SqliteConnectOptions::from_str(&format!("sqlite://{}", target_db_file.to_string_lossy()))
        .unwrap()
        .create_if_missing(true)
        .foreign_keys(true);

    let target_pool = SqlitePoolOptions::new()
        .connect_with(options)
        .await
        .expect("Failed to connect to target db");

    // Run schema
    sqlx::raw_sql(pico_possum_lib::infrastructure::database::schema::INIT_SCHEMA)
        .execute(&target_pool)
        .await
        .expect("Failed to run schema");

    // Run migration
    let summary = pico_possum_lib::application::MigrationService::migrate_from_legacy(
        &target_pool,
        &legacy_db_path.to_string_lossy(),
    )
    .await
    .expect("Migration failed");

    println!("Migration Summary: {:?}", summary);
    assert!(summary.categories_imported >= 1, "Categories count mismatch");
    assert!(summary.products_imported >= 1, "Products count mismatch");
    assert!(summary.customers_imported >= 1, "Customers count mismatch");
    assert!(summary.sales_imported >= 1, "Sales count mismatch");
    assert!(summary.sale_items_imported >= 1, "Sale items count mismatch");
    assert!(summary.stock_movements_imported >= 1, "Stock movements count mismatch");

    // Verify a sample product has integer paise
    let prod = pico_possum_lib::application::InventoryService::get_products(&target_pool, None, None)
        .await
        .expect("Failed to get products");

    println!("Imported {} products.", prod.len());
    for p in prod.iter().take(3) {
        assert!(p.mrp_paise >= 0);
    }

    let sales = pico_possum_lib::application::SalesService::get_sales(&target_pool, 10, 0, None)
        .await
        .expect("Failed to get sales");
    println!("Imported {} sales.", sales.len());
    for s in sales.iter().take(3) {
        println!("Sale: {} | Customer: {:?} | Total (paise): {} (₹{:.2}) | Status: {}",
            s.invoice_number, s.customer_name, s.total_paise, s.total_paise as f64 / 100.0, s.status);
    }
}
