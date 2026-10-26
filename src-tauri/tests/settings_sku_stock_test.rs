use sqlx::sqlite::SqlitePoolOptions;
use sqlx::SqlitePool;

use pico_possum_lib::application::{InventoryService, SalesService};
use pico_possum_lib::domain::models::{
    CreateProductPayload, CreateSaleItemPayload, CreateSalePayload, GeneralSettings,
    UpdateProductPayload,
};
use pico_possum_lib::errors::AppError;
use pico_possum_lib::infrastructure::database::schema::INIT_SCHEMA;

async fn setup_db() -> SqlitePool {
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

fn general_settings(auto_numeric_sku: bool, allow_negative_stock: bool) -> GeneralSettings {
    GeneralSettings {
        store_name: "Test Store".to_string(),
        phone: "".to_string(),
        address: "".to_string(),
        gstin: "".to_string(),
        currency_symbol: "₹".to_string(),
        currency_code: "INR".to_string(),
        date_format: "DD/MM/YYYY".to_string(),
        time_format: "12h".to_string(),
        default_printer_name: "".to_string(),
        auto_numeric_sku,
        allow_negative_stock,
    }
}

fn product_payload(name: &str, sku: Option<&str>, stock: i64) -> CreateProductPayload {
    CreateProductPayload {
        name: name.to_string(),
        sku: sku.map(|s| s.to_string()),
        category_id: None,
        mrp_paise: 1000,
        cost_price_paise: Some(500),
        tax_rate: Some(0.0),
        initial_stock: Some(stock),
        min_stock_alert: Some(5),
        status: Some("active".to_string()),
    }
}

fn sale_payload(product_id: &str, quantity: i64) -> CreateSalePayload {
    let total = 1000 * quantity;
    CreateSalePayload {
        customer_id: None,
        customer_name: Some("Walk-in".to_string()),
        customer_phone: None,
        customer_address: None,
        items: vec![CreateSaleItemPayload {
            product_id: product_id.to_string(),
            quantity,
            unit_price_paise: 1000,
            discount_paise: Some(0),
        }],
        discount_paise: Some(0),
        payment_method: "CASH".to_string(),
        paid_paise: total,
        notes: None,
    }
}

#[tokio::test]
async fn test_default_settings_enable_auto_sku_and_disable_negative_stock() {
    let pool = setup_db().await;

    // No settings row exists yet -> defaults apply.
    let settings = InventoryService::get_general_settings(&pool)
        .await
        .expect("Settings should load");
    assert!(settings.auto_numeric_sku);
    assert!(!settings.allow_negative_stock);

    // Legacy persisted settings JSON without the new fields must keep defaults.
    sqlx::query(
        "INSERT INTO app_settings (key, value) VALUES ('general_settings', ?)",
    )
    .bind(r#"{"store_name":"Legacy","phone":"","address":"","gstin":"","currency_symbol":"₹","currency_code":"INR","date_format":"DD/MM/YYYY","time_format":"12h","default_printer_name":""}"#)
    .execute(&pool)
    .await
    .unwrap();

    let legacy = InventoryService::get_general_settings(&pool)
        .await
        .expect("Legacy settings should load");
    assert!(legacy.auto_numeric_sku, "Missing field should default to enabled");
    assert!(!legacy.allow_negative_stock);
}

#[tokio::test]
async fn test_auto_numeric_sku_generation_and_import_continuation() {
    let pool = setup_db().await;

    // Auto SKU is enabled by default, so supplied SKUs are ignored.
    let first = InventoryService::create_product(&pool, product_payload("First", Some("MANUAL-1"), 0))
        .await
        .unwrap();
    let second = InventoryService::create_product(&pool, product_payload("Second", None, 0))
        .await
        .unwrap();

    assert_eq!(first.sku.as_deref(), Some("1"));
    assert_eq!(second.sku.as_deref(), Some("2"));
    assert_eq!(
        InventoryService::get_next_numeric_sku(&pool).await.unwrap(),
        "3"
    );

    // Simulate imported legacy data containing a much higher numeric SKU and
    // a non-numeric SKU. Only the numeric one should advance the sequence.
    sqlx::query(
        "INSERT INTO products (id, name, sku, mrp_paise, current_stock, min_stock_alert, status, is_deleted)
         VALUES ('imported-1', 'Imported Numeric', '250', 1000, 10, 5, 'active', 0),
                ('imported-2', 'Imported Legacy SKU', 'SKU-84', 1000, 10, 5, 'active', 0)",
    )
    .execute(&pool)
    .await
    .unwrap();

    assert_eq!(
        InventoryService::get_next_numeric_sku(&pool).await.unwrap(),
        "251"
    );

    let continued = InventoryService::create_product(&pool, product_payload("Continued", None, 0))
        .await
        .unwrap();
    assert_eq!(continued.sku.as_deref(), Some("251"));

    // Disabling automatic SKUs restores fully manual SKU entry.
    InventoryService::update_general_settings(&pool, general_settings(false, false))
        .await
        .unwrap();

    let manual = InventoryService::create_product(&pool, product_payload("Manual", Some("ABC-1"), 0))
        .await
        .unwrap();
    assert_eq!(manual.sku.as_deref(), Some("ABC-1"));

    // Manual mode allows editing the SKU.
    let updated = InventoryService::update_product(
        &pool,
        &manual.id,
        UpdateProductPayload {
            name: manual.name.clone(),
            sku: Some("ABC-2".to_string()),
            category_id: None,
            mrp_paise: manual.mrp_paise,
            cost_price_paise: manual.cost_price_paise,
            tax_rate: manual.tax_rate,
            min_stock_alert: Some(manual.min_stock_alert),
            status: manual.status.clone(),
        },
    )
    .await
    .unwrap();
    assert_eq!(updated.sku.as_deref(), Some("ABC-2"));

    // Re-enabling auto SKUs locks the SKU on edit (payload changes are ignored).
    InventoryService::update_general_settings(&pool, general_settings(true, false))
        .await
        .unwrap();

    let locked = InventoryService::update_product(
        &pool,
        &manual.id,
        UpdateProductPayload {
            name: manual.name.clone(),
            sku: Some("SHOULD-NOT-APPLY".to_string()),
            category_id: None,
            mrp_paise: manual.mrp_paise,
            cost_price_paise: manual.cost_price_paise,
            tax_rate: manual.tax_rate,
            min_stock_alert: Some(manual.min_stock_alert),
            status: manual.status.clone(),
        },
    )
    .await
    .unwrap();
    assert_eq!(locked.sku.as_deref(), Some("ABC-2"));
}

#[tokio::test]
async fn test_stock_restrictions_and_allow_negative_stock() {
    let pool = setup_db().await;

    let product = InventoryService::create_product(&pool, product_payload("Stocked", None, 2))
        .await
        .unwrap();

    // Default: selling more than available is blocked.
    let err = SalesService::process_sale(&pool, sale_payload(&product.id, 3))
        .await
        .expect_err("Sale beyond stock should fail");
    assert!(matches!(
        err,
        AppError::InsufficientStock {
            available: 2,
            requested: 3,
            ..
        }
    ));

    let after_failure = InventoryService::get_product_by_id(&pool, &product.id)
        .await
        .unwrap();
    assert_eq!(after_failure.current_stock, 2);

    // Selling exactly the available quantity succeeds.
    SalesService::process_sale(&pool, sale_payload(&product.id, 2))
        .await
        .expect("Sale within stock should succeed");

    let sold_out = InventoryService::get_product_by_id(&pool, &product.id)
        .await
        .unwrap();
    assert_eq!(sold_out.current_stock, 0);

    let err = SalesService::process_sale(&pool, sale_payload(&product.id, 1))
        .await
        .expect_err("Sale with zero stock should fail");
    assert!(matches!(err, AppError::InsufficientStock { available: 0, requested: 1, .. }));

    // Enabling negative stock removes the restriction.
    InventoryService::update_general_settings(&pool, general_settings(true, true))
        .await
        .unwrap();

    SalesService::process_sale(&pool, sale_payload(&product.id, 5))
        .await
        .expect("Sale should succeed when negative stock is allowed");

    let negative = InventoryService::get_product_by_id(&pool, &product.id)
        .await
        .unwrap();
    assert_eq!(negative.current_stock, -5);
}
