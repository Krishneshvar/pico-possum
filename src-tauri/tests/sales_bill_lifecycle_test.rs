use sqlx::sqlite::SqlitePoolOptions;
use sqlx::SqlitePool;

use pico_possum_lib::application::{CustomerService, InventoryService, ReportsService, SalesService};
use pico_possum_lib::domain::models::{
    CreateCustomerPayload, CreateProductPayload, CreateSaleItemPayload, CreateSalePayload,
    Product, UpdateSaleItemPayload, UpdateSalePayload,
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

async fn make_prod(
    pool: &SqlitePool,
    name: &str,
    sku: &str,
    stock: i64,
    mrp_cents: i64,
    cost_cents: i64,
) -> Product {
    InventoryService::create_product(
        pool,
        CreateProductPayload {
            name: name.to_string(),
            sku: Some(sku.to_string()),
            category_id: None,
            cost_price_cents: Some(cost_cents),
            mrp_cents,
            tax_rate: Some(0.0),
            initial_stock: Some(stock),
            min_stock_alert: Some(2),
            status: Some("active".to_string()),
        },
    )
    .await
    .unwrap()
}

#[tokio::test]
async fn test_sales_bill_lifecycle_creation_and_validation() {
    let pool = setup_db().await;
    let p1 = make_prod(&pool, "Premium Tea", "TEA-01", 10, 5000, 3000).await;

    // 1. Empty items validation failure
    let err_empty = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None, customer_name: None, customer_phone: None, customer_address: None,
            items: vec![], discount_cents: None, payment_method: "CASH".to_string(), paid_cents: 5000, notes: None,
        },
    )
    .await
    .unwrap_err();
    assert!(matches!(err_empty, AppError::Validation(_)));

    // 2. Zero or negative quantity validation failure
    let err_qty = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None, customer_name: None, customer_phone: None, customer_address: None,
            items: vec![CreateSaleItemPayload {
                product_id: p1.id.clone(), quantity: 0, unit_price_cents: 5000, discount_cents: None,
            }],
            discount_cents: None, payment_method: "CASH".to_string(), paid_cents: 5000, notes: None,
        },
    )
    .await
    .unwrap_err();
    assert!(matches!(err_qty, AppError::Validation(_)));

    // 3. Selling above MRP validation failure
    let err_mrp = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None, customer_name: None, customer_phone: None, customer_address: None,
            items: vec![CreateSaleItemPayload {
                product_id: p1.id.clone(), quantity: 1, unit_price_cents: 6000, discount_cents: None,
            }],
            discount_cents: None, payment_method: "CASH".to_string(), paid_cents: 6000, notes: None,
        },
    )
    .await
    .unwrap_err();
    assert!(matches!(err_mrp, AppError::Validation(_)));

    // 4. Insufficient stock failure (when negative stock is disabled)
    let err_stock = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None, customer_name: None, customer_phone: None, customer_address: None,
            items: vec![CreateSaleItemPayload {
                product_id: p1.id.clone(), quantity: 15, unit_price_cents: 5000, discount_cents: None,
            }],
            discount_cents: None, payment_method: "CASH".to_string(), paid_cents: 75000, notes: None,
        },
    )
    .await
    .unwrap_err();
    assert!(matches!(err_stock, AppError::InsufficientStock { .. }));

    // 5. Successful sale creation
    let sale_res = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None,
            customer_name: Some("Rohit Sharma".to_string()),
            customer_phone: Some("9876543210".to_string()),
            customer_address: Some("Wankhede".to_string()),
            items: vec![CreateSaleItemPayload {
                product_id: p1.id.clone(),
                quantity: 3,
                unit_price_cents: 5000,
                discount_cents: Some(500), // 15000 - 500 = 14500
            }],
            discount_cents: Some(500), // total 14000
            payment_method: "CASH".to_string(),
            paid_cents: 15000,
            notes: Some("Express counter".to_string()),
        },
    )
    .await
    .unwrap();

    assert_eq!(sale_res.sale.subtotal_cents, 14500);
    assert_eq!(sale_res.sale.total_cents, 14000);
    assert_eq!(sale_res.sale.paid_cents, 15000);
    assert_eq!(sale_res.change_cents, 1000);
    assert_eq!(sale_res.sale.status, "PAID");
    assert!(sale_res.sale.invoice_number.contains("CH"));

    // Check stock reduction
    let updated_p1 = InventoryService::get_product_by_id(&pool, &p1.id).await.unwrap();
    assert_eq!(updated_p1.current_stock, 7); // 10 - 3

    // Check audit log
    let audit_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM audit_logs WHERE entity_id = ? AND event_type = 'SALE_CREATED'",
    )
    .bind(&sale_res.sale.id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(audit_count, 1);
}

#[tokio::test]
async fn test_sales_bill_sequential_invoices_and_multi_payments() {
    let pool = setup_db().await;
    let p = make_prod(&pool, "Coffee Beans", "CB-01", 100, 2000, 1000).await;

    // 1. CASH Bill 1
    let sale_ch1 = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None,
            customer_name: None,
            customer_phone: None,
            customer_address: None,
            items: vec![CreateSaleItemPayload {
                product_id: p.id.clone(),
                quantity: 1,
                unit_price_cents: 2000,
                discount_cents: None,
            }],
            discount_cents: None,
            payment_method: "CASH".to_string(),
            paid_cents: 2000,
            notes: None,
        },
    )
    .await
    .unwrap();

    // 2. CASH Bill 2 (consecutive sequence)
    let sale_ch2 = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None,
            customer_name: None,
            customer_phone: None,
            customer_address: None,
            items: vec![CreateSaleItemPayload {
                product_id: p.id.clone(),
                quantity: 1,
                unit_price_cents: 2000,
                discount_cents: None,
            }],
            discount_cents: None,
            payment_method: "CASH".to_string(),
            paid_cents: 2000,
            notes: None,
        },
    )
    .await
    .unwrap();

    // 3. UPI Bill 1 (independent sequence)
    let sale_up1 = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None,
            customer_name: None,
            customer_phone: None,
            customer_address: None,
            items: vec![CreateSaleItemPayload {
                product_id: p.id.clone(),
                quantity: 2,
                unit_price_cents: 2000,
                discount_cents: None,
            }],
            discount_cents: None,
            payment_method: "UPI".to_string(),
            paid_cents: 4000,
            notes: None,
        },
    )
    .await
    .unwrap();

    assert!(sale_ch1.sale.invoice_number.ends_with("0000001"));
    assert!(sale_ch2.sale.invoice_number.ends_with("0000002"));
    assert!(sale_up1.sale.invoice_number.contains("UP"));
    assert!(sale_up1.sale.invoice_number.ends_with("0000001"));
}

#[tokio::test]
async fn test_sales_bill_customer_revival_and_metrics() {
    let pool = setup_db().await;
    let p = make_prod(&pool, "Almonds", "ALM-01", 50, 8000, 5000).await;

    // 1. Create and then soft-delete a customer
    let c = CustomerService::create_customer(
        &pool,
        CreateCustomerPayload {
            name: "Sunil Gavaskar".to_string(),
            phone: Some("9811223344".to_string()),
            address: Some("Marine Drive".to_string()),
        },
    )
    .await
    .unwrap();
    CustomerService::delete_customer(&pool, &c.id).await.unwrap();

    // 2. Process sale with the soft-deleted customer's phone -> automatic revival!
    let sale_res = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None,
            customer_name: Some("Sunil Gavaskar".to_string()),
            customer_phone: Some("9811223344".to_string()),
            customer_address: Some("Marine Drive, Mumbai".to_string()),
            items: vec![CreateSaleItemPayload {
                product_id: p.id.clone(),
                quantity: 2,
                unit_price_cents: 8000,
                discount_cents: None,
            }],
            discount_cents: None,
            payment_method: "CARD".to_string(),
            paid_cents: 16000,
            notes: None,
        },
    )
    .await
    .unwrap();

    assert_eq!(sale_res.sale.customer_id.as_deref(), Some(c.id.as_str()));

    // Verify customer is revived and metrics updated
    let revived = CustomerService::get_customer_by_id(&pool, &c.id).await.unwrap();
    assert_eq!(revived.total_sales, 1);
    assert_eq!(revived.total_spent_cents, 16000);
    assert!(revived.last_sale_date.is_some());
}

#[tokio::test]
async fn test_sales_bill_amendment_lifecycle_and_product_flow() {
    let pool = setup_db().await;
    let p1 = make_prod(&pool, "Cashew Nuts", "CSH-01", 20, 10000, 6000).await;
    let p2 = make_prod(&pool, "Raisins", "RSN-01", 30, 4000, 2500).await;

    // 1. Create sale: 2x Cashews (stock 20 -> 18) and 3x Raisins (stock 30 -> 27)
    let initial_sale = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None,
            customer_name: Some("Rahul Dravid".to_string()),
            customer_phone: Some("9844001122".to_string()),
            customer_address: None,
            items: vec![
                CreateSaleItemPayload {
                    product_id: p1.id.clone(),
                    quantity: 2,
                    unit_price_cents: 10000,
                    discount_cents: None,
                },
                CreateSaleItemPayload {
                    product_id: p2.id.clone(),
                    quantity: 3,
                    unit_price_cents: 4000,
                    discount_cents: None,
                },
            ],
            discount_cents: None,
            payment_method: "CASH".to_string(),
            paid_cents: 32000, // 20000 + 12000 = 32000
            notes: None,
        },
    )
    .await
    .unwrap();

    assert_eq!(initial_sale.sale.total_cents, 32000);
    assert_eq!(initial_sale.sale.payment_method, "CASH");
    assert_eq!(InventoryService::get_product_by_id(&pool, &p1.id).await.unwrap().current_stock, 18);
    assert_eq!(InventoryService::get_product_by_id(&pool, &p2.id).await.unwrap().current_stock, 27);

    // Initial revenue report: 32000 in cash, 0 in UPI
    let summary1 = ReportsService::get_sales_report_summary(&pool, None, None, None).await.unwrap();
    assert_eq!(summary1.cash_revenue_cents, 32000);
    assert_eq!(summary1.upi_revenue_cents, 0);

    // 2. Amend bill:
    // - Increase p1 quantity from 2 to 4 (requires 2 more stock -> stock 18 -> 16)
    // - Completely remove p2 (returns 3 stock to inventory -> stock 27 -> 30)
    // - Change payment method from CASH to UPI
    let amended_res = SalesService::update_sale(
        &pool,
        UpdateSalePayload {
            sale_id: initial_sale.sale.id.clone(),
            customer_id: initial_sale.sale.customer_id.clone(),
            customer_name: initial_sale.sale.customer_name.clone(),
            customer_phone: initial_sale.sale.customer_phone.clone(),
            customer_address: None,
            items: vec![UpdateSaleItemPayload {
                product_id: p1.id.clone(),
                quantity: 4,
                unit_price_cents: 10000,
                discount_cents: None,
            }],
            discount_cents: None,
            payment_method: "UPI".to_string(),
            paid_cents: 40000,
            notes: Some("Customer exchanged items".to_string()),
        },
    )
    .await
    .unwrap();

    assert_eq!(amended_res.sale.total_cents, 40000);
    assert_eq!(amended_res.sale.payment_method, "UPI");
    assert_eq!(amended_res.sale.invoice_number, initial_sale.sale.invoice_number); // Invoice number is immutable!
    assert_eq!(amended_res.items.len(), 1);
    assert_eq!(amended_res.items[0].product_id, p1.id);
    assert_eq!(amended_res.items[0].quantity, 4);

    // Post-amendment revenue report: Cash shifted to UPI!
    let summary2 = ReportsService::get_sales_report_summary(&pool, None, None, None).await.unwrap();
    assert_eq!(summary2.cash_revenue_cents, 0);
    assert_eq!(summary2.upi_revenue_cents, 40000);

    // Verify stock returned / adjusted accurately
    assert_eq!(InventoryService::get_product_by_id(&pool, &p1.id).await.unwrap().current_stock, 16);
    assert_eq!(InventoryService::get_product_by_id(&pool, &p2.id).await.unwrap().current_stock, 30);

    // Check stock ledger movements for p2 has RETURN
    let p2_movements = InventoryService::get_stock_movements(&pool, Some(p2.id.clone()), None, None, 10).await.unwrap();
    assert!(p2_movements.iter().any(|m| m.movement_type == "RETURN" && m.quantity_change == 3));

    // Check Product Flow: p2 must NOT appear in product flow for this bill anymore!
    let p2_flow = ReportsService::get_product_flow(&pool, Some(p2.id.clone()), None, None, 10).await.unwrap();
    assert_eq!(p2_flow.len(), 0);

    // p1 must appear in product flow with quantity -4
    let p1_flow = ReportsService::get_product_flow(&pool, Some(p1.id.clone()), None, None, 10).await.unwrap();
    assert_eq!(p1_flow.len(), 1);
    assert_eq!(p1_flow[0].quantity, -4);

    // Audit log check: verify payment method change was captured in description
    let audit_desc: String = sqlx::query_scalar(
        "SELECT description FROM audit_logs WHERE entity_id = ? AND event_type = 'SALE_EDITED' ORDER BY created_at DESC LIMIT 1",
    )
    .bind(&initial_sale.sale.id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert!(audit_desc.contains("Payment changed: CASH -> UPI"));
}

#[tokio::test]
async fn test_sales_bill_cancellation_and_invariants() {
    let pool = setup_db().await;
    let p = make_prod(&pool, "Dark Chocolate", "DC-01", 15, 3500, 2000).await;

    let sale_res = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None,
            customer_name: Some("Virender Sehwag".to_string()),
            customer_phone: Some("9811009988".to_string()),
            customer_address: None,
            items: vec![CreateSaleItemPayload {
                product_id: p.id.clone(),
                quantity: 5,
                unit_price_cents: 3500,
                discount_cents: None,
            }],
            discount_cents: None,
            payment_method: "CASH".to_string(),
            paid_cents: 17500,
            notes: None,
        },
    )
    .await
    .unwrap();

    assert_eq!(InventoryService::get_product_by_id(&pool, &p.id).await.unwrap().current_stock, 10);

    // 1. Cancel the bill
    let cancelled = SalesService::cancel_sale(&pool, &sale_res.sale.id, "Customer returned damaged box").await.unwrap();
    assert_eq!(cancelled.status, "CANCELLED");
    assert_eq!(cancelled.fulfillment_status, "CANCELLED");
    assert!(cancelled.notes.as_deref().unwrap().contains("Customer returned damaged box"));

    // Verify stock fully restored
    assert_eq!(InventoryService::get_product_by_id(&pool, &p.id).await.unwrap().current_stock, 15);

    // 2. Prevent double cancellation
    let err_double = SalesService::cancel_sale(&pool, &sale_res.sale.id, "Second cancel attempt").await.unwrap_err();
    assert!(matches!(err_double, AppError::Validation(_)));

    // 3. Prevent editing cancelled bill
    let err_edit = SalesService::update_sale(
        &pool,
        UpdateSalePayload {
            sale_id: sale_res.sale.id.clone(),
            customer_id: None,
            customer_name: None,
            customer_phone: None,
            customer_address: None,
            items: vec![UpdateSaleItemPayload {
                product_id: p.id.clone(),
                quantity: 1,
                unit_price_cents: 3500,
                discount_cents: None,
            }],
            discount_cents: None,
            payment_method: "CASH".to_string(),
            paid_cents: 3500,
            notes: None,
        },
    )
    .await
    .unwrap_err();
    assert!(matches!(err_edit, AppError::Validation(_)));

    // 4. Product flow excludes cancelled bills
    let flow = ReportsService::get_product_flow(&pool, Some(p.id.clone()), None, None, 10).await.unwrap();
    assert_eq!(flow.len(), 0);

    // 5. Query and historical preservation: bill details still return original line items
    let details = SalesService::get_sale_details(&pool, &sale_res.sale.id).await.unwrap();
    assert_eq!(details.sale.status, "CANCELLED");
    assert_eq!(details.items.len(), 1);
    assert_eq!(details.items[0].product_id, p.id);
    assert_eq!(details.items[0].quantity, 5);
}
