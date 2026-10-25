use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};
use std::str::FromStr;
use pico_possum_lib::domain::{
    CreateCategoryInput, CreateCustomerInput, CreateProductInput, ProcessSaleInput, SaleItemInput,
    StockAdjustmentInput,
};

#[tokio::test]
async fn test_full_pos_lifecycle() {
    let temp_dir = tempfile::tempdir().expect("Failed to create tempdir");
    let target_db_file = temp_dir.path().join("test-pos-lifecycle.db");

    let options = SqliteConnectOptions::from_str(&format!("sqlite://{}", target_db_file.to_string_lossy()))
        .unwrap()
        .create_if_missing(true)
        .foreign_keys(true);

    let pool = SqlitePoolOptions::new()
        .connect_with(options)
        .await
        .expect("Failed to connect to db");

    // Initialize Schema and default settings
    sqlx::raw_sql(pico_possum_lib::infrastructure::database::schema::INIT_SCHEMA)
        .execute(&pool)
        .await
        .expect("Failed to run schema");

    pico_possum_lib::infrastructure::database::connection::seed_default_settings(&pool)
        .await
        .expect("Failed to seed default settings");

    // 1. Create a Category
    let cat = pico_possum_lib::application::InventoryService::create_category(
        &pool,
        CreateCategoryInput {
            name: "Confectionery".to_string(),
            description: Some("Chocolates and Sweets".to_string()),
        },
    )
    .await
    .expect("Failed to create category");

    assert_eq!(cat.name, "Confectionery");

    // 2. Create a Product
    let prod = pico_possum_lib::application::InventoryService::create_product(
        &pool,
        CreateProductInput {
            name: "Dairy Milk Silk".to_string(),
            barcode: Some("8901234567890".to_string()),
            sku: Some("DMS-100".to_string()),
            category_id: Some(cat.id.clone()),
            cost_price_paise: 8000, // ₹80.00
            mrp_paise: 10000,        // ₹100.00
            selling_price_paise: 9500, // ₹95.00
            current_stock: 50,
            min_stock_alert: 5,
            tax_rate: 18.0,
            unit: "PCS".to_string(),
        },
    )
    .await
    .expect("Failed to create product");

    assert_eq!(prod.current_stock, 50);
    assert_eq!(prod.selling_price_paise, 9500);

    // Verify stock ledger has initial count
    let movements = pico_possum_lib::application::InventoryService::get_stock_movements(&pool, Some(prod.id.clone()), 10)
        .await
        .expect("Failed to get stock movements");
    assert_eq!(movements.len(), 1);
    assert_eq!(movements[0].quantity_change, 50);
    assert_eq!(movements[0].movement_type, "INITIAL");

    // 3. Create a Customer
    let cust = pico_possum_lib::application::InventoryService::create_customer(
        &pool,
        CreateCustomerInput {
            name: "John Retail".to_string(),
            phone: Some("9876543210".to_string()),
            email: Some("john@example.com".to_string()),
            address: Some("Shop 4, Market Complex".to_string()),
            notes: None,
        },
    )
    .await
    .expect("Failed to create customer");

    // 4. Process Sale (Cart: 2x Dairy Milk Silk @ 9500 paise = 19000 paise, discount 1000 paise => 18000 paise)
    let sale = pico_possum_lib::application::SalesService::process_sale(
        &pool,
        ProcessSaleInput {
            customer_id: Some(cust.id.clone()),
            items: vec![SaleItemInput {
                product_id: prod.id.clone(),
                product_name: prod.name.clone(),
                barcode: prod.barcode.clone(),
                quantity: 2,
                unit_price_paise: 9500,
                total_price_paise: 19000,
                cost_price_paise: 8000,
            }],
            subtotal_paise: 19000,
            discount_paise: 1000,
            tax_paise: 0,
            round_off_paise: 0,
            total_paise: 18000,
            payment_method: "CASH".to_string(),
            amount_tendered_paise: 20000,
            change_returned_paise: 2000,
            notes: Some("Test POS sale".to_string()),
        },
    )
    .await
    .expect("Failed to process sale");

    assert!(sale.invoice_number.starts_with("S"), "Invoice should start with S prefix");
    assert_eq!(sale.total_paise, 18000);
    assert_eq!(sale.status, "COMPLETED");

    // 5. Verify product stock was decremented from 50 to 48
    let updated_prod = pico_possum_lib::application::InventoryService::get_product(&pool, &prod.id)
        .await
        .expect("Failed to fetch product");
    assert_eq!(updated_prod.current_stock, 48);

    // 6. Verify stock ledger recorded -2 SALE movement
    let movements = pico_possum_lib::application::InventoryService::get_stock_movements(&pool, Some(prod.id.clone()), 10)
        .await
        .expect("Failed to get stock movements");
    assert_eq!(movements.len(), 2);
    assert_eq!(movements[0].quantity_change, -2);
    assert_eq!(movements[0].movement_type, "SALE");

    // 7. Verify Dashboard metrics
    let metrics = pico_possum_lib::application::InventoryService::get_dashboard_metrics(&pool)
        .await
        .expect("Failed to get metrics");
    assert_eq!(metrics.today_bills_count, 1);
    assert_eq!(metrics.today_sales_paise, 18000);

    // 8. Test Sale Cancellation / Return with automatic stock replenishment
    let cancelled_sale = pico_possum_lib::application::SalesService::cancel_sale(
        &pool,
        &sale.id,
        "Customer returned unopened items",
    )
    .await
    .expect("Failed to cancel sale");

    assert_eq!(cancelled_sale.status, "CANCELLED");

    // Verify stock is restored to 50
    let restored_prod = pico_possum_lib::application::InventoryService::get_product(&pool, &prod.id)
        .await
        .expect("Failed to fetch product");
    assert_eq!(restored_prod.current_stock, 50);

    // Verify stock ledger has +2 RETURN movement
    let movements = pico_possum_lib::application::InventoryService::get_stock_movements(&pool, Some(prod.id.clone()), 10)
        .await
        .expect("Failed to get stock movements");
    assert_eq!(movements.len(), 3);
    assert_eq!(movements[0].quantity_change, 2);
    assert_eq!(movements[0].movement_type, "RETURN");

    // 9. Manual stock adjustment
    let adjusted_prod = pico_possum_lib::application::InventoryService::adjust_stock(
        &pool,
        StockAdjustmentInput {
            product_id: prod.id.clone(),
            quantity_change: 10,
            movement_type: "PURCHASE".to_string(),
            note: Some("Received shipment".to_string()),
        },
    )
    .await
    .expect("Failed to adjust stock");
    assert_eq!(adjusted_prod.current_stock, 60);

    // 10. Audit logs validation
    let audit_logs = pico_possum_lib::application::InventoryService::get_audit_logs(&pool, 10)
        .await
        .expect("Failed to get audit logs");
    assert!(audit_logs.len() >= 3);
    assert!(audit_logs.iter().any(|l| l.event_type == "SALE_CREATED"));
    assert!(audit_logs.iter().any(|l| l.event_type == "SALE_CANCELLED"));
}
