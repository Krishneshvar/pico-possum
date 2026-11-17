use std::str::FromStr;
use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};
use pico_possum_lib::domain::models::{
    CreateCategoryPayload, CreateProductPayload,
    CreateSaleItemPayload, CreateSalePayload,
};
use pico_possum_lib::application::{InventoryService, SalesService};

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
