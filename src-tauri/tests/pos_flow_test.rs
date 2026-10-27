use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};
use std::str::FromStr;
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

#[tokio::test]
async fn test_bill_edit_lifecycle() {
    let temp_dir = tempfile::tempdir().expect("Failed to create tempdir");
    let db_file = temp_dir.path().join("test-bill-edit.db");

    let options = SqliteConnectOptions::from_str(&format!("sqlite://{}", db_file.to_string_lossy()))
        .unwrap()
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

    // Verify stock movement audit for B contains a SALE movement
    let b_movements = InventoryService::get_stock_movements(&pool, Some(prod_b.id.clone()), None, None, 10).await.unwrap();
    let b_sale = b_movements.iter().find(|m| m.movement_type == "SALE").expect("B should have a SALE movement");
    assert_eq!(b_sale.quantity_change, -2);

    // Verify stock movement for A contains a RETURN for 2 units
    let a_movements = InventoryService::get_stock_movements(&pool, Some(prod_a.id.clone()), None, None, 10).await.unwrap();
    let a_return = a_movements.iter().find(|m| m.movement_type == "RETURN").expect("A should have a RETURN movement");
    assert_eq!(a_return.quantity_change, 2);
}

#[tokio::test]
async fn test_sales_analytics_with_filters_and_categories() {
    let temp_dir = tempfile::tempdir().expect("Failed to create tempdir");
    let target_db_file = temp_dir.path().join("test-analytics.db");

    let options = SqliteConnectOptions::from_str(&format!("sqlite://{}", target_db_file.to_string_lossy()))
        .unwrap()
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

    // Create 2 categories: Snacks, Beverages
    let cat_snacks = InventoryService::create_category(
        &pool,
        CreateCategoryPayload { name: "Snacks".into(), parent_id: None }
    ).await.unwrap();

    let cat_bev = InventoryService::create_category(
        &pool,
        CreateCategoryPayload { name: "Beverages".into(), parent_id: None }
    ).await.unwrap();

    // Create 2 products
    let chips = InventoryService::create_product(
        &pool,
        CreateProductPayload {
            name: "Potato Chips".into(),
            sku: Some("CHP-001".into()),
            category_id: Some(cat_snacks.id.clone()),
            cost_price_paise: Some(1500),
            mrp_paise: 2000,
            tax_rate: Some(5.0),
            initial_stock: Some(100),
            min_stock_alert: Some(10),
            status: Some("active".into()),
        }
    ).await.unwrap();

    let soda = InventoryService::create_product(
        &pool,
        CreateProductPayload {
            name: "Lemon Soda".into(),
            sku: Some("SDA-002".into()),
            category_id: Some(cat_bev.id.clone()),
            cost_price_paise: Some(2500),
            mrp_paise: 4000,
            tax_rate: Some(12.0),
            initial_stock: Some(50),
            min_stock_alert: Some(5),
            status: Some("active".into()),
        }
    ).await.unwrap();

    // Sale 1: CASH - 2x Chips (4000)
    SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None,
            customer_name: Some("Walk-in 1".into()),
            customer_phone: None,
            customer_address: None,
            discount_paise: Some(0),
            payment_method: "CASH".into(),
            paid_paise: 4000,
            notes: None,
            items: vec![
                CreateSaleItemPayload {
                    product_id: chips.id.clone(),
                    quantity: 2,
                    unit_price_paise: 2000,
                    discount_paise: Some(0),
                }
            ],
        }
    ).await.expect("Sale 1 failed");

    // Sale 2: UPI - 1x Chips (2000) + 2x Soda (4000 each = 8000) = 10000 - 500 discount = 9500
    SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None,
            customer_name: Some("Walk-in 2".into()),
            customer_phone: None,
            customer_address: None,
            discount_paise: Some(500),
            payment_method: "UPI".into(),
            paid_paise: 9500,
            notes: None,
            items: vec![
                CreateSaleItemPayload {
                    product_id: chips.id.clone(),
                    quantity: 1,
                    unit_price_paise: 2000,
                    discount_paise: Some(0),
                },
                CreateSaleItemPayload {
                    product_id: soda.id.clone(),
                    quantity: 2,
                    unit_price_paise: 4000,
                    discount_paise: Some(0),
                }
            ],
        }
    ).await.expect("Sale 2 failed");

    // 1. Overall Summary (ALL)
    let summary_all = InventoryService::get_sales_report_summary(&pool, None, None, None).await.unwrap();
    assert_eq!(summary_all.total_sales_count, 2);
    assert_eq!(summary_all.total_revenue_paise, 13500); // 4000 + 9500
    assert_eq!(summary_all.cash_revenue_paise, 4000);
    assert_eq!(summary_all.upi_revenue_paise, 9500);

    // 2. Summary Filtered by CASH
    let summary_cash = InventoryService::get_sales_report_summary(&pool, None, None, Some("CASH".into())).await.unwrap();
    assert_eq!(summary_cash.total_sales_count, 1);
    assert_eq!(summary_cash.total_revenue_paise, 4000);

    // 3. Summary Filtered by UPI
    let summary_upi = InventoryService::get_sales_report_summary(&pool, None, None, Some("UPI".into())).await.unwrap();
    assert_eq!(summary_upi.total_sales_count, 1);
    assert_eq!(summary_upi.total_revenue_paise, 9500);

    // 4. Top Selling Categories (ALL)
    let cats = InventoryService::get_top_selling_categories(&pool, None, None, None, 10).await.unwrap();
    assert_eq!(cats.len(), 2);
    // Beverage total revenue: 8000; Snacks total revenue: 4000 + 2000 = 6000
    assert_eq!(cats[0].category_name, "Beverages");
    assert_eq!(cats[0].total_revenue_paise, 8000);
    assert_eq!(cats[0].total_quantity, 2);
    assert_eq!(cats[1].category_name, "Snacks");
    assert_eq!(cats[1].total_revenue_paise, 6000);
    assert_eq!(cats[1].total_quantity, 3); // 2 from Sale 1 + 1 from Sale 2

    // 5. Top Selling Categories filtered by CASH (only Sale 1 has cash, which had Chips/Snacks)
    let cats_cash = InventoryService::get_top_selling_categories(&pool, None, None, Some("CASH".into()), 10).await.unwrap();
    assert_eq!(cats_cash.len(), 1);
    assert_eq!(cats_cash[0].category_name, "Snacks");
    assert_eq!(cats_cash[0].total_quantity, 2);
    assert_eq!(cats_cash[0].total_revenue_paise, 4000);

    // 6. Top Selling Products
    let prods = InventoryService::get_top_selling_products(&pool, None, None, None, 10).await.unwrap();
    assert_eq!(prods.len(), 2);
    // Chips total quantity = 3; Soda = 2
    assert_eq!(prods[0].product_name, "Potato Chips");
    assert_eq!(prods[0].total_quantity, 3);
    assert_eq!(prods[0].sku, chips.sku);

    // 7. Multi-Year Sales
    let multi_year = InventoryService::get_multi_year_sales(&pool, 3, None).await.unwrap();
    assert!(!multi_year.is_empty(), "Multi-year query should return points for current year");
}
