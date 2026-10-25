use pico_possum_lib::application::CsvImportService;
use pico_possum_lib::infrastructure::database::schema::INIT_SCHEMA;
use sqlx::sqlite::SqlitePoolOptions;
use sqlx::Row;

async fn setup_test_db() -> sqlx::SqlitePool {
    let pool = SqlitePoolOptions::new()
        .max_connections(1)
        .connect("sqlite::memory:")
        .await
        .expect("Failed to connect to memory SQLite");

    sqlx::raw_sql(INIT_SCHEMA)
        .execute(&pool)
        .await
        .expect("Failed to initialize schema");

    pool
}

#[tokio::test]
async fn test_import_categories_csv() {
    let pool = setup_test_db().await;

    let csv = "Division Code,Division Name\n\
               2,GIFT ITEMS\n\
               11,Confectionery\n\
               0,Invalid\n\
               ,Empty\n";

    let res = CsvImportService::import_categories(&pool, csv)
        .await
        .expect("import categories should succeed");

    assert_eq!(res.imported, 2);
    assert_eq!(res.skipped, 2);

    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM categories")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(count, 2);
}

#[tokio::test]
async fn test_import_products_csv_with_categories_and_stock() {
    let pool = setup_test_db().await;

    let csv = "Product Code,Product Name,MRP,Division Code,Division Name,Minimum Stock Level,Avg Item Cost,Opening Stock,Tax Rate\n\
               SKU-84,Chocolates,300,11,Confectionery,10,150,50,18.0\n\
               SKU-99,Teddy Bear,500,2,GIFT ITEMS,5,250,15,12.0\n\
               NoCode,No Of Products,100,1,Misc,5,50,0,0\n";

    let res = CsvImportService::import_products(&pool, csv)
        .await
        .expect("import products should succeed");

    assert_eq!(res.imported, 2);
    assert_eq!(res.skipped, 1);

    // Verify products created
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM products")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(count, 2);

    // Verify categories auto-created
    let cat_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM categories")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(cat_count, 2);

    // Verify stock movements created for initial stock
    let sm_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM stock_movements WHERE movement_type = 'RESTOCK'")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(sm_count, 2);

    // Verify price in paise
    let row = sqlx::query("SELECT mrp_paise, cost_price_paise, current_stock FROM products WHERE sku = 'SKU-84'")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(row.get::<i64, _>("mrp_paise"), 30000);
    assert_eq!(row.get::<i64, _>("cost_price_paise"), 15000);
    assert_eq!(row.get::<i64, _>("current_stock"), 50);
}

#[tokio::test]
async fn test_import_customers_csv() {
    let pool = setup_test_db().await;

    let csv = "Customer Code,Customer Name,Contact Number,Customer Address,City,Pin Code\n\
               13,Sindhu,9876543210,123 Lane,Chennai,600001\n\
               14,Rajesh,9876543211,456 Cross,,600002\n";

    let res = CsvImportService::import_customers(&pool, csv)
        .await
        .expect("import customers should succeed");

    assert_eq!(res.imported, 2);

    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM customers")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(count, 2);

    let row = sqlx::query("SELECT name, phone, address FROM customers WHERE phone = '9876543210'")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(row.get::<String, _>("name"), "Sindhu");
    assert_eq!(row.get::<String, _>("address"), "123 Lane, Chennai, 600001");
}

#[tokio::test]
async fn test_import_legacy_bills_csv() {
    let pool = setup_test_db().await;

    let csv = "Serieswise Sales Report From 01/01/25 To 31/12/25,,,,,\n\
               Bill Date,Bill Number,Customer Code,Customer Name,Net amount,Bill Time\n\
               1/2/2025,C01003247,13,Sindhu,220.00,10:38 AM\n\
               1/2/2025,K01000205,21,Naturals,109.00,8.45 PM\n\
               1/2/2025,X01001470,0,,55.00,11:33 AM\n\
               bad-date,C01000000,1,Broken,10.00,1:00 PM\n\
               1/2/2025,Grand Total,,Grand Total,384.00,11:59 PM\n";

    let res = CsvImportService::import_bills(&pool, csv, Some("legacy.csv"))
        .await
        .expect("import bills should succeed");

    assert_eq!(res.imported, 3);
    assert_eq!(res.skipped, 2); // 1 bad date, 1 Grand Total row

    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sales")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(count, 3);

    // Verify Cash bill
    let cash = sqlx::query("SELECT invoice_number, total_paise, payment_method, customer_name, biller_name FROM sales WHERE invoice_number = 'C01003247'")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(cash.get::<i64, _>("total_paise"), 22000);
    assert_eq!(cash.get::<String, _>("payment_method"), "Cash");
    assert_eq!(cash.get::<String, _>("customer_name"), "Sindhu");
    assert_eq!(cash.get::<String, _>("biller_name"), "Legacy Import");

    // Verify Card bill
    let card = sqlx::query("SELECT invoice_number, total_paise, payment_method FROM sales WHERE invoice_number = 'K01000205'")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(card.get::<i64, _>("total_paise"), 10900);
    assert_eq!(card.get::<String, _>("payment_method"), "Card");

    // Verify Gift Card bill (Walk-in Customer fallback)
    let gift = sqlx::query("SELECT invoice_number, customer_name, payment_method FROM sales WHERE invoice_number = 'X01001470'")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(gift.get::<String, _>("customer_name"), "Walk-in Customer");
    assert_eq!(gift.get::<String, _>("payment_method"), "Gift Card");
}
