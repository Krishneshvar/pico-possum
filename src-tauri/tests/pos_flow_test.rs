use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};
use std::str::FromStr;
use pico_possum_lib::domain::models::{
    CreateCategoryPayload, CreateCustomerPayload, CreateProductPayload,
    CreateSaleItemPayload, CreateSalePayload, StockAdjustmentPayload,
};
use pico_possum_lib::application::{InventoryService, SalesService};

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

    // Initialize Schema
    sqlx::raw_sql(pico_possum_lib::infrastructure::database::schema::INIT_SCHEMA)
        .execute(&pool)
        .await
        .expect("Failed to run schema");

    // 1. Create a Category
    let cat = InventoryService::create_category(
        &pool,
        CreateCategoryPayload {
            name: "Confectionery".to_string(),
            parent_id: None,
        },
    )
    .await
    .expect("Failed to create category");

    assert_eq!(cat.name, "Confectionery");

    // 2. Create a Product
    let prod = InventoryService::create_product(
        &pool,
        CreateProductPayload {
            name: "Dairy Milk Silk".to_string(),
            sku: Some("DMS-100".to_string()),
            category_id: Some(cat.id.clone()),
            cost_price_paise: Some(8000), // ₹80.00
            mrp_paise: 9500,              // ₹95.00
            tax_rate: Some(18.0),
            initial_stock: Some(50),
            min_stock_alert: Some(5),
            status: Some("active".to_string()),
        },
    )
    .await
    .expect("Failed to create product");

    assert_eq!(prod.current_stock, 50);
    assert_eq!(prod.mrp_paise, 9500);

    // Verify stock ledger has initial count
    let movements = InventoryService::get_stock_movements(&pool, Some(prod.id.clone()), None, None, 10)
        .await
        .expect("Failed to get stock movements");
    assert_eq!(movements.len(), 1);
    assert_eq!(movements[0].quantity_change, 50);
    assert_eq!(movements[0].movement_type, "RESTOCK");

    // 3. Create a Customer
    let cust = InventoryService::create_customer(
        &pool,
        CreateCustomerPayload {
            name: "John Retail".to_string(),
            phone: Some("9876543210".to_string()),
            address: Some("Shop 4, Market Complex".to_string()),
        },
    )
    .await
    .expect("Failed to create customer");

    // 4. Process Sale (Cart: 2x Dairy Milk Silk @ 9500 paise = 19000 paise, discount 1000 paise => 18000 paise)
    let sale_res = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: Some(cust.id.clone()),
            customer_name: Some(cust.name.clone()),
            customer_phone: cust.phone.clone(),
            customer_address: cust.address.clone(),
            items: vec![CreateSaleItemPayload {
                product_id: prod.id.clone(),
                quantity: 2,
                unit_price_paise: 9500,
                discount_paise: Some(0),
            }],
            discount_paise: Some(1000),
            payment_method: "CASH".to_string(),
            paid_paise: 20000,
            notes: Some("Test POS sale".to_string()),
        },
    )
    .await
    .expect("Failed to process sale");

    assert!(sale_res.sale.invoice_number.starts_with("S"), "Invoice should start with S prefix");
    assert_eq!(sale_res.sale.total_paise, 18000);
    assert_eq!(sale_res.sale.status, "PAID");
    assert_eq!(sale_res.change_paise, 2000);

    // 5. Verify product stock was decremented from 50 to 48
    let updated_prod = InventoryService::get_product_by_id(&pool, &prod.id)
        .await
        .expect("Failed to fetch product");
    assert_eq!(updated_prod.current_stock, 48);

    // 6. Verify stock ledger recorded -2 SALE movement
    let movements = InventoryService::get_stock_movements(&pool, Some(prod.id.clone()), None, None, 10)
        .await
        .expect("Failed to get stock movements");
    assert_eq!(movements.len(), 2);
    let sale_mv = movements.iter().find(|m| m.movement_type == "SALE").expect("SALE movement should exist");
    assert_eq!(sale_mv.quantity_change, -2);

    // 7. Verify Cancel Sale / Return with automatic stock replenishment
    let cancelled_sale = SalesService::cancel_sale(
        &pool,
        &sale_res.sale.id,
        "Customer returned unopened items",
    )
    .await
    .expect("Failed to cancel sale");

    assert_eq!(cancelled_sale.status, "CANCELLED");

    // Verify stock is restored to 50
    let restored_prod = InventoryService::get_product_by_id(&pool, &prod.id)
        .await
        .expect("Failed to fetch product");
    assert_eq!(restored_prod.current_stock, 50);

    // Verify stock ledger has +2 RETURN movement
    let movements = InventoryService::get_stock_movements(&pool, Some(prod.id.clone()), None, None, 10)
        .await
        .expect("Failed to get stock movements");
    assert_eq!(movements.len(), 3);
    let ret_mv = movements.iter().find(|m| m.movement_type == "RETURN").expect("RETURN movement should exist");
    assert_eq!(ret_mv.quantity_change, 2);

    // 8. Manual stock adjustment
    let adjusted_prod = InventoryService::adjust_stock(
        &pool,
        StockAdjustmentPayload {
            product_id: prod.id.clone(),
            quantity_change: 10,
            reason: "PURCHASE".to_string(),
            note: Some("Received shipment".to_string()),
        },
    )
    .await
    .expect("Failed to adjust stock");
    assert_eq!(adjusted_prod.current_stock, 60);
}
