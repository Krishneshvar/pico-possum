use sqlx::sqlite::SqlitePoolOptions;
use sqlx::SqlitePool;

use pico_possum_lib::application::{InventoryService, ReportsService, SalesService, SettingsService};
use pico_possum_lib::domain::models::{
    CreateCategoryPayload, CreateProductPayload, CreateSaleItemPayload, CreateSalePayload,
    GeneralSettings, Product, StockAdjustmentPayload, UpdateProductPayload, UpdateSaleItemPayload,
    UpdateSalePayload,
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

fn create_settings(auto_numeric_sku: bool, allow_negative_stock: bool) -> GeneralSettings {
    GeneralSettings {
        store_name: "Lifecycle Test Store".to_string(),
        phone: "1234567890".to_string(),
        address: "123 Main St".to_string(),
        gstin: "".to_string(),
        currency_symbol: "₹".to_string(),
        currency_code: "INR".to_string(),
        currency_format: "en-IN".to_string(),
        date_format: "DD/MM/YYYY".to_string(),
        time_format: "12h".to_string(),
        default_printer_name: "".to_string(),
        auto_numeric_sku,
        allow_negative_stock,
    }
}

async fn make_prod(pool: &SqlitePool, name: &str, sku: Option<&str>, cat: Option<String>, mrp: i64, stock: i64) -> Result<Product, AppError> {
    InventoryService::create_product(
        pool,
        CreateProductPayload {
            name: name.to_string(),
            sku: sku.map(|s| s.to_string()),
            category_id: cat,
            mrp_cents: mrp,
            cost_price_cents: Some(mrp / 2),
            tax_rate: Some(5.0),
            initial_stock: Some(stock),
            min_stock_alert: Some(2),
            status: Some("active".to_string()),
        },
    ).await
}

#[tokio::test]
async fn test_product_lifecycle_creation_and_auto_sku() {
    let pool = setup_db().await;
    SettingsService::update_general_settings(&pool, create_settings(true, false)).await.unwrap();

    let p1 = make_prod(&pool, "Organic Apples 1kg", None, None, 25000, 15).await.unwrap();
    assert_eq!(p1.sku.as_deref(), Some("1"));
    assert_eq!(p1.current_stock, 15);

    let mov: (i64, String) = sqlx::query_as(
        "SELECT quantity_change, movement_type FROM stock_movements WHERE product_id = ?",
    )
    .bind(&p1.id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(mov.0, 15);
    assert_eq!(mov.1, "PURCHASE");

    let p2 = make_prod(&pool, "Organic Bananas 1kg", None, None, 8000, 0).await.unwrap();
    assert_eq!(p2.sku.as_deref(), Some("2"));

    let empty_name = make_prod(&pool, "   ", None, None, 1000, 0).await;
    assert!(matches!(empty_name, Err(AppError::Validation(_))));

    let neg_price = make_prod(&pool, "Bad Item", None, None, -500, 0).await;
    assert!(matches!(neg_price, Err(AppError::Validation(_))));
}

#[tokio::test]
async fn test_product_lifecycle_manual_sku_editing_and_audit() {
    let pool = setup_db().await;
    SettingsService::update_general_settings(&pool, create_settings(false, false)).await.unwrap();

    let p = make_prod(&pool, "Vintage Camera", Some("CAM-900"), None, 500000, 3).await.unwrap();
    assert_eq!(p.sku.as_deref(), Some("CAM-900"));

    let updated = InventoryService::update_product(
        &pool,
        &p.id,
        UpdateProductPayload {
            name: "Vintage Camera Pro 35mm".to_string(),
            sku: Some("CAM-900-PRO".to_string()),
            category_id: None,
            mrp_cents: 550000,
            cost_price_cents: 380000,
            tax_rate: 18.0,
            min_stock_alert: Some(2),
            status: "active".to_string(),
        },
    ).await.unwrap();

    assert_eq!(updated.name, "Vintage Camera Pro 35mm");
    assert_eq!(updated.sku.as_deref(), Some("CAM-900-PRO"));
    assert_eq!(updated.current_stock, 3);

    let count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM audit_logs WHERE entity_id = ? AND event_type = 'PRODUCT_UPDATED'",
    )
    .bind(&p.id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(count, 1);
}

#[tokio::test]
async fn test_product_lifecycle_search_filter_and_categories() {
    let pool = setup_db().await;
    SettingsService::update_general_settings(&pool, create_settings(false, false)).await.unwrap();

    let cat = InventoryService::create_category(
        &pool,
        CreateCategoryPayload { name: "Beverages".to_string(), parent_id: None },
    ).await.unwrap();

    let prod = make_prod(&pool, "Cold Brew Coffee", Some("BREW-01"), Some(cat.id.clone()), 15000, 10).await.unwrap();

    let res1 = InventoryService::get_products(&pool, None, Some("Cold Brew".to_string())).await.unwrap();
    assert_eq!(res1.len(), 1);
    assert_eq!(res1[0].id, prod.id);

    let res2 = InventoryService::get_products(&pool, None, Some("BREW-01".to_string())).await.unwrap();
    assert_eq!(res2.len(), 1);
    assert_eq!(res2[0].id, prod.id);

    let res3 = InventoryService::get_products(&pool, None, Some("Beverages".to_string())).await.unwrap();
    assert_eq!(res3.len(), 1);
    assert_eq!(res3[0].id, prod.id);

    let res4 = InventoryService::get_products(&pool, Some(cat.id.clone()), None).await.unwrap();
    assert_eq!(res4.len(), 1);
    assert_eq!(res4[0].id, prod.id);
}

#[tokio::test]
async fn test_product_lifecycle_stock_adjustments_and_restrictions() {
    let pool = setup_db().await;
    SettingsService::update_general_settings(&pool, create_settings(true, false)).await.unwrap();

    let p = make_prod(&pool, "Green Tea Box", None, None, 20000, 10).await.unwrap();

    let p_add = InventoryService::adjust_stock(
        &pool,
        StockAdjustmentPayload {
            product_id: p.id.clone(),
            quantity_change: 15,
            reason: "PURCHASE".to_string(),
            note: Some("Vendor batch 404".to_string()),
        },
    ).await.unwrap();
    assert_eq!(p_add.current_stock, 25);

    let p_deduct = InventoryService::adjust_stock(
        &pool,
        StockAdjustmentPayload {
            product_id: p.id.clone(),
            quantity_change: -5,
            reason: "MANUAL_STOCK_CORRECTION".to_string(),
            note: Some("Damaged goods removed".to_string()),
        },
    ).await.unwrap();
    assert_eq!(p_deduct.current_stock, 20);

    let over_err = InventoryService::adjust_stock(
        &pool,
        StockAdjustmentPayload {
            product_id: p.id.clone(),
            quantity_change: -25,
            reason: "MANUAL_STOCK_CORRECTION".to_string(),
            note: None,
        },
    ).await;
    assert!(matches!(over_err, Err(AppError::Validation(_))));

    SettingsService::update_general_settings(&pool, create_settings(true, true)).await.unwrap();

    let allowed_neg = InventoryService::adjust_stock(
        &pool,
        StockAdjustmentPayload {
            product_id: p.id.clone(),
            quantity_change: -25,
            reason: "MANUAL_STOCK_CORRECTION".to_string(),
            note: Some("Allowed negative adjustment".to_string()),
        },
    ).await.unwrap();
    assert_eq!(allowed_neg.current_stock, -5);
}

#[tokio::test]
async fn test_product_lifecycle_billing_amendment_and_deletion() {
    let pool = setup_db().await;
    SettingsService::update_general_settings(&pool, create_settings(true, false)).await.unwrap();

    let p = make_prod(&pool, "Stainless Steel Bottle", None, None, 100000, 50).await.unwrap();

    let sale_resp = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None,
            customer_name: Some("Rahul Sharma".to_string()),
            customer_phone: Some("9876543210".to_string()),
            customer_address: None,
            items: vec![CreateSaleItemPayload {
                product_id: p.id.clone(),
                quantity: 10,
                unit_price_cents: 100000,
                discount_cents: Some(0),
            }],
            discount_cents: Some(0),
            payment_method: "CASH".to_string(),
            paid_cents: 1000000,
            notes: None,
        },
    ).await.unwrap();

    let p_after_sale = InventoryService::get_product_by_id(&pool, &p.id).await.unwrap();
    assert_eq!(p_after_sale.current_stock, 40);

    let flow = ReportsService::get_product_flow(&pool, Some(p.id.clone()), None, None, 50).await.unwrap();
    assert_eq!(flow.len(), 1);
    assert_eq!(flow[0].quantity, -10);
    assert_eq!(flow[0].invoice_number, Some(sale_resp.sale.invoice_number.clone()));

    SalesService::update_sale(
        &pool,
        UpdateSalePayload {
            sale_id: sale_resp.sale.id.clone(),
            customer_id: None,
            customer_name: Some("Rahul Sharma".to_string()),
            customer_phone: Some("9876543210".to_string()),
            customer_address: None,
            items: vec![UpdateSaleItemPayload {
                product_id: p.id.clone(),
                quantity: 6,
                unit_price_cents: 100000,
                discount_cents: Some(0),
            }],
            discount_cents: Some(0),
            payment_method: "CASH".to_string(),
            paid_cents: 600000,
            notes: Some("Returned 4 bottles".to_string()),
        },
    ).await.unwrap();

    let p_after_amend = InventoryService::get_product_by_id(&pool, &p.id).await.unwrap();
    assert_eq!(p_after_amend.current_stock, 44);

    InventoryService::delete_product(&pool, &p.id).await.unwrap();

    let p_deleted: (i64, i64) = sqlx::query_as(
        "SELECT current_stock, is_deleted FROM products WHERE id = ?",
    )
    .bind(&p.id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(p_deleted.0, 0);
    assert_eq!(p_deleted.1, 1);

    let del_mov: (i64, String) = sqlx::query_as(
        "SELECT quantity_change, movement_type FROM stock_movements WHERE product_id = ? AND movement_type = 'PRODUCT_DELETED'",
    )
    .bind(&p.id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(del_mov.0, -44);
    assert_eq!(del_mov.1, "PRODUCT_DELETED");

    let adjust_del_err = InventoryService::adjust_stock(
        &pool,
        StockAdjustmentPayload {
            product_id: p.id.clone(),
            quantity_change: 10,
            reason: "PURCHASE".to_string(),
            note: None,
        },
    ).await;
    assert!(matches!(adjust_del_err, Err(AppError::Validation(_))));

    let update_del_err = InventoryService::update_product(
        &pool,
        &p.id,
        UpdateProductPayload {
            name: "Deleted Bottle".to_string(),
            sku: None,
            category_id: None,
            mrp_cents: 100000,
            cost_price_cents: 60000,
            tax_rate: 18.0,
            min_stock_alert: Some(5),
            status: "active".to_string(),
        },
    ).await;
    assert!(matches!(update_del_err, Err(AppError::NotFound(_))));

    let re_del_err = InventoryService::delete_product(&pool, &p.id).await;
    assert!(matches!(re_del_err, Err(AppError::NotFound(_))));

    let bill_del_err = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None,
            customer_name: None,
            customer_phone: None,
            customer_address: None,
            items: vec![CreateSaleItemPayload {
                product_id: p.id.clone(),
                quantity: 1,
                unit_price_cents: 100000,
                discount_cents: None,
            }],
            discount_cents: None,
            payment_method: "CASH".to_string(),
            paid_cents: 100000,
            notes: None,
        },
    ).await;
    assert!(matches!(bill_del_err, Err(AppError::NotFound(_))));

    let flow_after = ReportsService::get_product_flow(&pool, Some(p.id.clone()), None, None, 50).await.unwrap();
    assert_eq!(flow_after.len(), 1);
    assert_eq!(flow_after[0].quantity, -6);
}
