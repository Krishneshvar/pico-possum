use std::path::Path;
use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};

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

    let temp_dir = tempfile::tempdir().expect("Failed to create tempdir");
    let target_db_file = temp_dir.path().join("test-possum.db");

    let options = SqliteConnectOptions::new()
        .filename(&target_db_file)
        .create_if_missing(true)
        .foreign_keys(true);

    let target_pool = SqlitePoolOptions::new()
        .connect_with(options)
        .await
        .expect("Failed to connect to target db");

    sqlx::raw_sql(pico_possum_lib::infrastructure::database::schema::INIT_SCHEMA)
        .execute(&target_pool)
        .await
        .expect("Failed to run schema");

    let summary = pico_possum_lib::application::MigrationService::migrate_from_legacy(
        &target_pool,
        &legacy_db_path.to_string_lossy(),
    )
    .await
    .expect("Migration failed");

    println!("Migration Summary: {:?}", summary);
    assert!(summary.categories_imported >= 1, "Categories count mismatch");
    assert!(summary.products_imported >= 1, "Products count mismatch");
    let target_cust_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM customers")
        .fetch_one(&target_pool)
        .await
        .unwrap_or(0);
    assert_eq!(summary.customers_imported, target_cust_count as usize, "Customers count mismatch");
    assert!(summary.sales_imported >= 1, "Sales count mismatch");
    assert!(summary.sale_items_imported >= 1, "Sale items count mismatch");
    assert!(summary.stock_movements_imported >= 1, "Stock movements count mismatch");

    let prod = pico_possum_lib::application::InventoryService::get_products(&target_pool, None, None)
        .await
        .expect("Failed to get products");

    println!("Imported {} products.", prod.len());
    for p in prod.iter().take(3) {
        assert!(p.mrp_cents >= 0);
    }
}

#[tokio::test]
async fn test_migration_uploaded_backup_preserves_mrps_and_amounts() {
    let uploaded_path = Path::new("/home/krish/.gemini/antigravity/brain/984d3701-77a1-4896-990a-d56ecf928743/.user_uploaded/media_1795329414119_4214df53.db");
    if !uploaded_path.exists() {
        println!("Uploaded test backup not found at {:?}, skipping test", uploaded_path);
        return;
    }

    let temp_dir = tempfile::tempdir().expect("Failed to create tempdir");
    let target_db_file = temp_dir.path().join("test-possum-uploaded.db");

    let options = SqliteConnectOptions::new()
        .filename(&target_db_file)
        .create_if_missing(true)
        .foreign_keys(true);

    let target_pool = SqlitePoolOptions::new()
        .connect_with(options)
        .await
        .expect("Failed to connect to target db");

    sqlx::raw_sql(pico_possum_lib::infrastructure::database::schema::INIT_SCHEMA)
        .execute(&target_pool)
        .await
        .expect("Failed to run schema");

    let summary = pico_possum_lib::application::MigrationService::migrate_from_legacy(
        &target_pool,
        &uploaded_path.to_string_lossy(),
    )
    .await
    .expect("Migration failed");

    println!("Uploaded DB Migration Summary: {:?}", summary);
    assert_eq!(summary.products_imported, 935);

    // Verify product MRPs in target DB
    let zero_mrp_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM products WHERE mrp_cents = 0")
        .fetch_one(&target_pool)
        .await
        .unwrap_or(0);
    println!("Products with 0 MRP in target: {}", zero_mrp_count);
    assert_eq!(zero_mrp_count, 0, "No products should have 0 MRP in this backup dataset!");

    // Verify sample product MRP
    let vinayagar_mrp: i64 = sqlx::query_scalar(
        "SELECT mrp_cents FROM products WHERE name LIKE '%Vinayagar%Statue%' LIMIT 1"
    )
    .fetch_one(&target_pool)
    .await
    .unwrap_or(0);
    println!("Vinayagar Statue MRP cents: {}", vinayagar_mrp);
    assert_eq!(vinayagar_mrp, 999900, "Vinayagar Statue MRP should be 9999.00 (999900 cents)");

    // Verify sales total amounts (only invoice S26UP0001454 has total_amount = 0 in legacy DB)
    let zero_total_sales: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sales WHERE total_cents = 0")
        .fetch_one(&target_pool)
        .await
        .unwrap_or(0);
    println!("Sales with total_cents = 0: {}", zero_total_sales);
    assert_eq!(zero_total_sales, 1, "Only the 1 legacy sale that had 0 total should be 0");

    // Verify sale items unit prices
    let zero_price_items: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sale_items WHERE unit_price_cents = 0")
        .fetch_one(&target_pool)
        .await
        .unwrap_or(0);
    println!("Sale items with unit_price_cents = 0: {}", zero_price_items);
    assert_eq!(zero_price_items, 0, "Sale items should retain unit prices");

    // Verify sales payment method preservation
    let upi_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sales WHERE status != 'LEGACY' AND payment_method = 'UPI'")
        .fetch_one(&target_pool).await.unwrap_or(0);
    let card_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sales WHERE status != 'LEGACY' AND payment_method = 'CARD'")
        .fetch_one(&target_pool).await.unwrap_or(0);
    let gift_card_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sales WHERE status != 'LEGACY' AND payment_method = 'GIFT_CARD'")
        .fetch_one(&target_pool).await.unwrap_or(0);
    let cash_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sales WHERE status != 'LEGACY' AND payment_method = 'CASH'")
        .fetch_one(&target_pool).await.unwrap_or(0);

    println!("Active sales PM counts: UPI={}, Card={}, GiftCard={}, Cash={}", upi_count, card_count, gift_card_count, cash_count);
    assert!(upi_count >= 1000, "UPI sales should be preserved (found {})", upi_count);
    assert!(card_count >= 50, "Card sales should be preserved (found {})", card_count);
    assert!(gift_card_count >= 600, "Gift Card sales should be preserved (found {})", gift_card_count);
    assert!(cash_count >= 300, "Cash sales should be preserved (found {})", cash_count);
}
