use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};
use pico_possum_lib::domain::models::{
    CreateCategoryPayload, CreateCustomerPayload, CreateProductPayload,
    CreateSaleItemPayload, CreateSalePayload, StockAdjustmentPayload,
    UpdateSalePayload, UpdateSaleItemPayload,
};
use pico_possum_lib::application::{InventoryService, SalesService};

#[tokio::test]
async fn test_full_pos_lifecycle() {
    let temp_dir = tempfile::tempdir().expect("Failed to create tempdir");
    let target_db_file = temp_dir.path().join("test-pos-lifecycle.db");

    let options = SqliteConnectOptions::new()
        .filename(&target_db_file)
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
    assert_eq!(movements[0].movement_type, "PURCHASE");

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

    // Verify stock ledger has +2 CANCELLED movement
    let movements = InventoryService::get_stock_movements(&pool, Some(prod.id.clone()), None, None, 10)
        .await
        .expect("Failed to get stock movements");
    assert_eq!(movements.len(), 3);
    let cancel_mv = movements.iter().find(|m| m.movement_type == "CANCELLED").expect("CANCELLED movement should exist");
    assert_eq!(cancel_mv.quantity_change, 2);

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

    // Negative stock adjustment with Purchase should be rejected
    let neg_purchase_err = InventoryService::adjust_stock(
        &pool,
        StockAdjustmentPayload {
            product_id: prod.id.clone(),
            quantity_change: -5,
            reason: "PURCHASE".to_string(),
            note: Some("Invalid negative purchase".to_string()),
        },
    )
    .await;
    assert!(neg_purchase_err.is_err(), "Negative purchase must be rejected");

    // Bill Adjustment cannot be manually selected
    let bill_adj_err = InventoryService::adjust_stock(
        &pool,
        StockAdjustmentPayload {
            product_id: prod.id.clone(),
            quantity_change: 5,
            reason: "BILL_ADJUSTMENT".to_string(),
            note: None,
        },
    )
    .await;
    assert!(bill_adj_err.is_err(), "Bill Adjustment cannot be manually selected");

    // Return cannot be manually selected
    let return_err = InventoryService::adjust_stock(
        &pool,
        StockAdjustmentPayload {
            product_id: prod.id.clone(),
            quantity_change: 5,
            reason: "RETURN".to_string(),
            note: None,
        },
    )
    .await;
    assert!(return_err.is_err(), "Return cannot be manually selected");

    // Cancelled cannot be manually selected
    let cancelled_err = InventoryService::adjust_stock(
        &pool,
        StockAdjustmentPayload {
            product_id: prod.id.clone(),
            quantity_change: 5,
            reason: "CANCELLED".to_string(),
            note: None,
        },
    )
    .await;
    assert!(cancelled_err.is_err(), "Cancelled cannot be manually selected");

    // Manual stock correction with negative qty succeeds
    let manual_adj_prod = InventoryService::adjust_stock(
        &pool,
        StockAdjustmentPayload {
            product_id: prod.id.clone(),
            quantity_change: -5,
            reason: "MANUAL_STOCK_CORRECTION".to_string(),
            note: Some("Inventory recount discrepancy".to_string()),
        },
    )
    .await
    .expect("Manual stock correction must succeed");
    assert_eq!(manual_adj_prod.current_stock, 55);
}

#[tokio::test]
async fn test_bill_edit_lifecycle() {
    let temp_dir = tempfile::tempdir().expect("Failed to create tempdir");
    let db_file = temp_dir.path().join("test-bill-edit.db");

    let options = SqliteConnectOptions::new()
        .filename(&db_file)
        .create_if_missing(true)
        .foreign_keys(true);

    let pool = SqlitePoolOptions::new()
        .connect_with(options)
        .await
        .expect("Failed to connect");

    sqlx::raw_sql(pico_possum_lib::infrastructure::database::schema::INIT_SCHEMA)
        .execute(&pool)
        .await
        .expect("Schema init failed");

    // Setup: category + 2 products
    let cat = InventoryService::create_category(
        &pool,
        CreateCategoryPayload { name: "Edit Test Cat".into(), parent_id: None },
    ).await.unwrap();

    let prod_a = InventoryService::create_product(
        &pool,
        CreateProductPayload {
            name: "Product A".into(), sku: Some("PA-1".into()),
            category_id: Some(cat.id.clone()),
            cost_price_paise: Some(500), mrp_paise: 1000,
            tax_rate: Some(0.0), initial_stock: Some(100),
            min_stock_alert: Some(5), status: Some("active".into()),
        },
    ).await.unwrap();

    let prod_b = InventoryService::create_product(
        &pool,
        CreateProductPayload {
            name: "Product B".into(), sku: Some("PB-1".into()),
            category_id: Some(cat.id.clone()),
            cost_price_paise: Some(800), mrp_paise: 1500,
            tax_rate: Some(0.0), initial_stock: Some(50),
            min_stock_alert: Some(5), status: Some("active".into()),
        },
    ).await.unwrap();

    // Original sale: 3x Product A @ 1000
    let sale_res = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None,
            customer_name: Some("Test Customer".into()),
            customer_phone: None,
            customer_address: None,
            items: vec![CreateSaleItemPayload {
                product_id: prod_a.id.clone(),
                quantity: 3,
                unit_price_paise: 1000,
                discount_paise: Some(0),
            }],
            discount_paise: Some(0),
            payment_method: "CASH".into(),
            paid_paise: 3000,
            notes: None,
        },
    ).await.expect("Original sale failed");

    assert_eq!(sale_res.sale.total_paise, 3000);

    // Verify stock: A went 100 → 97
    let a_after_sale = InventoryService::get_product_by_id(&pool, &prod_a.id).await.unwrap();
    assert_eq!(a_after_sale.current_stock, 97);

    // Edit: change to 1x Product A + 2x Product B
    let updated = SalesService::update_sale(
        &pool,
        UpdateSalePayload {
            sale_id: sale_res.sale.id.clone(),
            customer_id: None,
            customer_name: Some("Updated Customer".into()),
            customer_phone: Some("9999999999".into()),
            customer_address: None,
            items: vec![
                UpdateSaleItemPayload {
                    product_id: prod_a.id.clone(),
                    quantity: 1,
                    unit_price_paise: 1000,
                    discount_paise: Some(0),
                },
                UpdateSaleItemPayload {
                    product_id: prod_b.id.clone(),
                    quantity: 2,
                    unit_price_paise: 1500,
                    discount_paise: Some(0),
                },
            ],
            discount_paise: Some(0),
            payment_method: "UPI".into(),
            paid_paise: 4000,
            notes: Some("Corrected bill".into()),
        },
    ).await.expect("Bill edit failed");

    assert_eq!(updated.sale.total_paise, 4000); // 1*1000 + 2*1500
    assert_eq!(updated.sale.customer_name, Some("Updated Customer".into()));
    assert_eq!(updated.sale.payment_method, "UPI".to_string());
    assert_eq!(updated.items.len(), 2);

    // Stock A: was 97 → reduced from 3 to 1 → restored 2 → 97 + 2 = 99
    let a_after_edit = InventoryService::get_product_by_id(&pool, &prod_a.id).await.unwrap();
    assert_eq!(a_after_edit.current_stock, 99, "Prod A stock should be 99 after reducing from 3 to 1");

    // Stock B: was 50 → 2 deducted → 48
    let b_after_edit = InventoryService::get_product_by_id(&pool, &prod_b.id).await.unwrap();
    assert_eq!(b_after_edit.current_stock, 48, "Prod B stock should be 48 after adding 2");

    // Verify stock movement audit for B contains a BILL_ADJUSTMENT movement
    let b_movements = InventoryService::get_stock_movements(&pool, Some(prod_b.id.clone()), None, None, 10).await.unwrap();
    let b_sale = b_movements.iter().find(|m| m.movement_type == "BILL_ADJUSTMENT").expect("B should have a BILL_ADJUSTMENT movement");
    assert_eq!(b_sale.quantity_change, -2);

    // Verify stock movement for A contains a RETURN for 2 units
    let a_movements = InventoryService::get_stock_movements(&pool, Some(prod_a.id.clone()), None, None, 10).await.unwrap();
    let a_return = a_movements.iter().find(|m| m.movement_type == "RETURN").expect("A should have a RETURN movement");
    assert_eq!(a_return.quantity_change, 2);

    // Verify Product Flow reflects updated items
    let a_flow = InventoryService::get_product_flow(&pool, Some(prod_a.id.clone()), None, None, 10).await.unwrap();
    assert_eq!(a_flow.len(), 1);
    assert_eq!(a_flow[0].quantity, -1); // 1 unit sold (stored as -1 qty change in flow)
    assert_eq!(a_flow[0].unit_price_paise, 1000);
    assert_eq!(a_flow[0].total_paise, 1000);

    let b_flow = InventoryService::get_product_flow(&pool, Some(prod_b.id.clone()), None, None, 10).await.unwrap();
    assert_eq!(b_flow.len(), 1);
    assert_eq!(b_flow[0].quantity, -2); // 2 units sold
    assert_eq!(b_flow[0].unit_price_paise, 1500);
    assert_eq!(b_flow[0].total_paise, 3000);

    // Cancel bill and verify it drops out of product flow
    SalesService::cancel_sale(&pool, &updated.sale.id, "Customer returned").await.unwrap();
    let a_flow_after_cancel = InventoryService::get_product_flow(&pool, Some(prod_a.id.clone()), None, None, 10).await.unwrap();
    assert_eq!(a_flow_after_cancel.len(), 0, "Cancelled sale items should not show in product flow");

    let b_flow_after_cancel = InventoryService::get_product_flow(&pool, Some(prod_b.id.clone()), None, None, 10).await.unwrap();
    assert_eq!(b_flow_after_cancel.len(), 0, "Cancelled sale items should not show in product flow");
}

#[tokio::test]
async fn test_bill_input_item_order_preserved() {
    let temp_dir = tempfile::tempdir().expect("Failed to create tempdir");
    let db_file = temp_dir.path().join("test-item-order.db");

    let options = SqliteConnectOptions::new()
        .filename(&db_file)
        .create_if_missing(true)
        .foreign_keys(true);

    let pool = SqlitePoolOptions::new()
        .connect_with(options)
        .await
        .expect("Failed to connect to db");

    sqlx::raw_sql(pico_possum_lib::infrastructure::database::schema::INIT_SCHEMA)
        .execute(&pool)
        .await
        .expect("Failed to initialize schema");

    // Create 4 products whose natural alphabetical/UUID sort differs from input order
    let prod_z = InventoryService::create_product(&pool, CreateProductPayload {
        name: "Zucchini Squash".to_string(),
        sku: Some("SKU-Z".to_string()),
        category_id: None,
        mrp_paise: 4000,
        cost_price_paise: Some(2000),
        tax_rate: Some(0.0),
        initial_stock: Some(100),
        min_stock_alert: Some(5),
        status: Some("active".to_string()),
    }).await.unwrap();

    let prod_a = InventoryService::create_product(&pool, CreateProductPayload {
        name: "Apple Royal Gala".to_string(),
        sku: Some("SKU-A".to_string()),
        category_id: None,
        mrp_paise: 8000,
        cost_price_paise: Some(4000),
        tax_rate: Some(0.0),
        initial_stock: Some(100),
        min_stock_alert: Some(5),
        status: Some("active".to_string()),
    }).await.unwrap();

    let prod_b = InventoryService::create_product(&pool, CreateProductPayload {
        name: "Banana Cavendish".to_string(),
        sku: Some("SKU-B".to_string()),
        category_id: None,
        mrp_paise: 2500,
        cost_price_paise: Some(1000),
        tax_rate: Some(0.0),
        initial_stock: Some(100),
        min_stock_alert: Some(5),
        status: Some("active".to_string()),
    }).await.unwrap();

    let prod_c = InventoryService::create_product(&pool, CreateProductPayload {
        name: "Carrot Organic".to_string(),
        sku: Some("SKU-C".to_string()),
        category_id: None,
        mrp_paise: 3000,
        cost_price_paise: Some(1500),
        tax_rate: Some(0.0),
        initial_stock: Some(100),
        min_stock_alert: Some(5),
        status: Some("active".to_string()),
    }).await.unwrap();

    // Specific input order chosen by user in bill: Zucchini, Apple, Banana, Carrot
    let input_items = vec![
        CreateSaleItemPayload {
            product_id: prod_z.id.clone(),
            quantity: 1,
            unit_price_paise: 4000,
            discount_paise: Some(0),
        },
        CreateSaleItemPayload {
            product_id: prod_a.id.clone(),
            quantity: 2,
            unit_price_paise: 8000,
            discount_paise: Some(0),
        },
        CreateSaleItemPayload {
            product_id: prod_b.id.clone(),
            quantity: 3,
            unit_price_paise: 2500,
            discount_paise: Some(0),
        },
        CreateSaleItemPayload {
            product_id: prod_c.id.clone(),
            quantity: 4,
            unit_price_paise: 3000,
            discount_paise: Some(0),
        },
    ];

    let sale_res = SalesService::process_sale(&pool, CreateSalePayload {
        customer_id: None,
        customer_name: Some("Order Test Customer".to_string()),
        customer_phone: None,
        customer_address: None,
        items: input_items,
        discount_paise: Some(0),
        payment_method: "CASH".to_string(),
        paid_paise: 50000,
        notes: None,
    }).await.expect("Sale processing should succeed");

    // 1. Check returned sale response preserves the input order
    assert_eq!(sale_res.items.len(), 4);
    assert_eq!(sale_res.items[0].product_name, "Zucchini Squash");
    assert_eq!(sale_res.items[1].product_name, "Apple Royal Gala");
    assert_eq!(sale_res.items[2].product_name, "Banana Cavendish");
    assert_eq!(sale_res.items[3].product_name, "Carrot Organic");

    // 2. Fetch bill details from database (what Bill History and Receipt Print query)
    let details = SalesService::get_sale_details(&pool, &sale_res.sale.id)
        .await
        .expect("Fetch sale details should succeed");

    assert_eq!(details.items.len(), 4);
    assert_eq!(details.items[0].product_name, "Zucchini Squash");
    assert_eq!(details.items[0].item_order, 0);
    assert_eq!(details.items[1].product_name, "Apple Royal Gala");
    assert_eq!(details.items[1].item_order, 1);
    assert_eq!(details.items[2].product_name, "Banana Cavendish");
    assert_eq!(details.items[2].item_order, 2);
    assert_eq!(details.items[3].product_name, "Carrot Organic");
    assert_eq!(details.items[3].item_order, 3);

    // 3. Edit bill: reorder items to Carrot, Zucchini, Apple
    let amended_items = vec![
        UpdateSaleItemPayload {
            product_id: prod_c.id.clone(),
            quantity: 5,
            unit_price_paise: 3000,
            discount_paise: Some(0),
        },
        UpdateSaleItemPayload {
            product_id: prod_z.id.clone(),
            quantity: 2,
            unit_price_paise: 4000,
            discount_paise: Some(0),
        },
        UpdateSaleItemPayload {
            product_id: prod_a.id.clone(),
            quantity: 1,
            unit_price_paise: 8000,
            discount_paise: Some(0),
        },
    ];

    SalesService::update_sale(&pool, UpdateSalePayload {
        sale_id: sale_res.sale.id.clone(),
        customer_id: None,
        customer_name: Some("Order Test Customer".to_string()),
        customer_phone: None,
        customer_address: None,
        items: amended_items,
        discount_paise: Some(0),
        payment_method: "CASH".to_string(),
        paid_paise: 50000,
        notes: None,
    }).await.expect("Sale amendment should succeed");

    let updated_details = SalesService::get_sale_details(&pool, &sale_res.sale.id)
        .await
        .expect("Fetch updated details should succeed");

    assert_eq!(updated_details.items.len(), 3);
    assert_eq!(updated_details.items[0].product_name, "Carrot Organic");
    assert_eq!(updated_details.items[0].item_order, 0);
    assert_eq!(updated_details.items[1].product_name, "Zucchini Squash");
    assert_eq!(updated_details.items[1].item_order, 1);
    assert_eq!(updated_details.items[2].product_name, "Apple Royal Gala");
    assert_eq!(updated_details.items[2].item_order, 2);
}

#[tokio::test]
async fn test_product_deletion_clears_stock_and_records_movement() {
    let temp_dir = tempfile::tempdir().expect("Failed to create tempdir");
    let target_db_file = temp_dir.path().join("test-product-deletion.db");

    let options = SqliteConnectOptions::new()
        .filename(&target_db_file)
        .create_if_missing(true)
        .foreign_keys(true);

    let pool = SqlitePoolOptions::new()
        .connect_with(options)
        .await
        .expect("Failed to connect to db");

    sqlx::raw_sql(pico_possum_lib::infrastructure::database::schema::INIT_SCHEMA)
        .execute(&pool)
        .await
        .expect("Failed to run schema");

    // 1. Create a Product with 5 unit opening stock
    let prod = InventoryService::create_product(
        &pool,
        CreateProductPayload {
            name: "Deletion Stock Test Item".to_string(),
            sku: Some("DEL-005".to_string()),
            category_id: None,
            cost_price_paise: Some(1000),
            mrp_paise: 2000,
            tax_rate: Some(0.0),
            initial_stock: Some(5),
            min_stock_alert: Some(1),
            status: Some("active".to_string()),
        },
    )
    .await
    .expect("Failed to create product");

    assert_eq!(prod.current_stock, 5);

    // Verify initial stock movement is PURCHASE +5
    let movements_before = InventoryService::get_stock_movements(&pool, Some(prod.id.clone()), None, None, 10)
        .await
        .expect("Failed to get stock movements");
    assert_eq!(movements_before.len(), 1);
    assert_eq!(movements_before[0].quantity_change, 5);
    assert_eq!(movements_before[0].movement_type, "PURCHASE");

    // 2. Delete the Product
    InventoryService::delete_product(&pool, &prod.id)
        .await
        .expect("Failed to delete product");

    // 3. Verify stock movements after deletion
    let movements_after = InventoryService::get_stock_movements(&pool, Some(prod.id.clone()), None, None, 10)
        .await
        .expect("Failed to get stock movements after deletion");

    assert_eq!(movements_after.len(), 2);
    // Most recent movement should be PRODUCT_DELETED with -5
    assert_eq!(movements_after[0].movement_type, "PRODUCT_DELETED");
    assert_eq!(movements_after[0].quantity_change, -5);
    assert!(movements_after[0].note.as_ref().unwrap().contains("5 units"));

    // Total net stock change from movements must equal 0
    let total_delta: i64 = movements_after.iter().map(|m| m.quantity_change).sum();
    assert_eq!(total_delta, 0);

    // 4. Verify product record has current_stock = 0 and is_deleted = 1
    let (is_deleted, current_stock): (i64, i64) = sqlx::query_as(
        "SELECT is_deleted, current_stock FROM products WHERE id = ?"
    )
    .bind(&prod.id)
    .fetch_one(&pool)
    .await
    .expect("Product should still exist in soft-deleted state");

    assert_eq!(is_deleted, 1);
    assert_eq!(current_stock, 0);
}

