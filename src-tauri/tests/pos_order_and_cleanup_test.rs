use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};
use pico_possum_lib::domain::models::{
    CreateProductPayload, CreateSaleItemPayload, CreateSalePayload,
    UpdateSalePayload, UpdateSaleItemPayload,
};
use pico_possum_lib::application::{InventoryService, SalesService};

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
        mrp_cents: 4000,
        cost_price_cents: Some(2000),
        tax_rate: Some(0.0),
        initial_stock: Some(100),
        min_stock_alert: Some(5),
        status: Some("active".to_string()),
    }).await.unwrap();

    let prod_a = InventoryService::create_product(&pool, CreateProductPayload {
        name: "Apple Royal Gala".to_string(),
        sku: Some("SKU-A".to_string()),
        category_id: None,
        mrp_cents: 8000,
        cost_price_cents: Some(4000),
        tax_rate: Some(0.0),
        initial_stock: Some(100),
        min_stock_alert: Some(5),
        status: Some("active".to_string()),
    }).await.unwrap();

    let prod_b = InventoryService::create_product(&pool, CreateProductPayload {
        name: "Banana Cavendish".to_string(),
        sku: Some("SKU-B".to_string()),
        category_id: None,
        mrp_cents: 2500,
        cost_price_cents: Some(1000),
        tax_rate: Some(0.0),
        initial_stock: Some(100),
        min_stock_alert: Some(5),
        status: Some("active".to_string()),
    }).await.unwrap();

    let prod_c = InventoryService::create_product(&pool, CreateProductPayload {
        name: "Carrot Organic".to_string(),
        sku: Some("SKU-C".to_string()),
        category_id: None,
        mrp_cents: 3000,
        cost_price_cents: Some(1500),
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
            unit_price_cents: 4000,
            discount_cents: Some(0),
        },
        CreateSaleItemPayload {
            product_id: prod_a.id.clone(),
            quantity: 2,
            unit_price_cents: 8000,
            discount_cents: Some(0),
        },
        CreateSaleItemPayload {
            product_id: prod_b.id.clone(),
            quantity: 3,
            unit_price_cents: 2500,
            discount_cents: Some(0),
        },
        CreateSaleItemPayload {
            product_id: prod_c.id.clone(),
            quantity: 4,
            unit_price_cents: 3000,
            discount_cents: Some(0),
        },
    ];

    let sale_res = SalesService::process_sale(&pool, CreateSalePayload {
        customer_id: None,
        customer_name: Some("Order Test Customer".to_string()),
        customer_phone: None,
        customer_address: None,
        items: input_items,
        discount_cents: Some(0),
        payment_method: "CASH".to_string(),
        paid_cents: 50000,
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
            unit_price_cents: 3000,
            discount_cents: Some(0),
        },
        UpdateSaleItemPayload {
            product_id: prod_z.id.clone(),
            quantity: 2,
            unit_price_cents: 4000,
            discount_cents: Some(0),
        },
        UpdateSaleItemPayload {
            product_id: prod_a.id.clone(),
            quantity: 1,
            unit_price_cents: 8000,
            discount_cents: Some(0),
        },
    ];

    SalesService::update_sale(&pool, UpdateSalePayload {
        sale_id: sale_res.sale.id.clone(),
        customer_id: None,
        customer_name: Some("Order Test Customer".to_string()),
        customer_phone: None,
        customer_address: None,
        items: amended_items,
        discount_cents: Some(0),
        payment_method: "CASH".to_string(),
        paid_cents: 50000,
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
            cost_price_cents: Some(1000),
            mrp_cents: 2000,
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
