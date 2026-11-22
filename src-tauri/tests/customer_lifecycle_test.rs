use sqlx::sqlite::SqlitePoolOptions;
use sqlx::SqlitePool;

use pico_possum_lib::application::{CustomerService, InventoryService, SalesService};
use pico_possum_lib::domain::models::{
    CreateCustomerPayload, CreateProductPayload, CreateSaleItemPayload, CreateSalePayload,
    Customer, UpdateCustomerPayload,
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

async fn make_cust(
    pool: &SqlitePool,
    name: &str,
    phone: Option<&str>,
    address: Option<&str>,
) -> Result<Customer, AppError> {
    CustomerService::create_customer(
        pool,
        CreateCustomerPayload {
            name: name.to_string(),
            phone: phone.map(|s| s.to_string()),
            address: address.map(|s| s.to_string()),
        },
    )
    .await
}

#[tokio::test]
async fn test_customer_lifecycle_creation_and_validation() {
    let pool = setup_db().await;

    // 1. Create valid customer
    let c = make_cust(&pool, "Anita Desai", Some("+91 98765 43210"), Some("MG Road, Bengaluru")).await.unwrap();
    assert_eq!(c.name, "Anita Desai");
    assert_eq!(c.phone.as_deref(), Some("+91 98765 43210"));
    assert_eq!(c.address.as_deref(), Some("MG Road, Bengaluru"));
    assert_eq!(c.total_sales, 0);
    assert_eq!(c.total_spent_cents, 0);
    assert!(c.last_sale_date.is_none());

    // Audit log check
    let audit_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM audit_logs WHERE entity_id = ? AND event_type = 'CUSTOMER_CREATED'",
    )
    .bind(&c.id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(audit_count, 1);

    // 2. Validation: empty name
    let empty_name = make_cust(&pool, "   ", Some("9876543210"), None).await;
    assert!(matches!(empty_name, Err(AppError::Validation(_))));

    // 3. Validation: name too long (> 150 chars)
    let long_name = "N".repeat(151);
    let long_name_err = make_cust(&pool, &long_name, None, None).await;
    assert!(matches!(long_name_err, Err(AppError::Validation(_))));

    // 4. Validation: invalid phone characters
    let bad_phone = make_cust(&pool, "Test Cust", Some("98765-ABC"), None).await;
    assert!(matches!(bad_phone, Err(AppError::Validation(_))));

    // 5. Validation: phone too long (> 30 chars)
    let long_phone = "1".repeat(31);
    let long_phone_err = make_cust(&pool, "Test Cust", Some(&long_phone), None).await;
    assert!(matches!(long_phone_err, Err(AppError::Validation(_))));

    // 6. Validation: address too long (> 500 chars)
    let long_addr = "A".repeat(501);
    let long_addr_err = make_cust(&pool, "Test Cust", Some("9876543210"), Some(&long_addr)).await;
    assert!(matches!(long_addr_err, Err(AppError::Validation(_))));
}

#[tokio::test]
async fn test_customer_lifecycle_phone_uniqueness_and_revival() {
    let pool = setup_db().await;

    let c1 = make_cust(&pool, "Vikram Seth", Some("9988776655"), Some("Delhi")).await.unwrap();

    // Duplicate active phone rejected
    let dup_phone_err = make_cust(&pool, "Vikram S", Some("9988776655"), None).await;
    assert!(matches!(dup_phone_err, Err(AppError::Validation(_))));

    // Soft delete customer
    CustomerService::delete_customer(&pool, &c1.id).await.unwrap();

    // Recreating customer with the same phone revives the record
    let revived = make_cust(&pool, "Vikram Seth Updated", Some("9988776655"), Some("New Delhi")).await.unwrap();
    assert_eq!(revived.id, c1.id);
    assert_eq!(revived.name, "Vikram Seth Updated");
    assert_eq!(revived.address.as_deref(), Some("New Delhi"));
    assert!(!revived.is_deleted);

    let all = CustomerService::get_customers(&pool, None).await.unwrap();
    assert_eq!(all.len(), 1);
    assert_eq!(all[0].id, c1.id);
}

#[tokio::test]
async fn test_customer_lifecycle_updating_and_conflict_prevention() {
    let pool = setup_db().await;

    let c1 = make_cust(&pool, "Kiran Rao", Some("9111111111"), Some("Mumbai")).await.unwrap();
    let _c2 = make_cust(&pool, "Aamir Khan", Some("9222222222"), Some("Bandra")).await.unwrap();

    // 1. Valid update
    let updated_c1 = CustomerService::update_customer(
        &pool,
        &c1.id,
        UpdateCustomerPayload {
            name: "Kiran Rao Khan".to_string(),
            phone: Some("9111111111".to_string()),
            address: Some("Colaba, Mumbai".to_string()),
        },
    )
    .await
    .unwrap();
    assert_eq!(updated_c1.name, "Kiran Rao Khan");
    assert_eq!(updated_c1.address.as_deref(), Some("Colaba, Mumbai"));

    // Audit log check
    let audit_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM audit_logs WHERE entity_id = ? AND event_type = 'CUSTOMER_UPDATED'",
    )
    .bind(&c1.id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(audit_count, 1);

    // 2. Phone conflict against another active customer (c2's phone)
    let conflict_err = CustomerService::update_customer(
        &pool,
        &c1.id,
        UpdateCustomerPayload {
            name: "Kiran Rao".to_string(),
            phone: Some("9222222222".to_string()),
            address: None,
        },
    )
    .await;
    assert!(matches!(conflict_err, Err(AppError::Validation(_))));

    // 3. Updating non-existent customer fails
    let not_found_err = CustomerService::update_customer(
        &pool,
        "non-existent-id",
        UpdateCustomerPayload {
            name: "Ghost".to_string(),
            phone: None,
            address: None,
        },
    )
    .await;
    assert!(matches!(not_found_err, Err(AppError::NotFound(_))));
}

#[tokio::test]
async fn test_customer_lifecycle_pos_checkout_and_sales_metrics() {
    let pool = setup_db().await;

    let cust = make_cust(&pool, "Priya Sharma", Some("9845012345"), Some("Indiranagar")).await.unwrap();

    let prod = InventoryService::create_product(
        &pool,
        CreateProductPayload {
            name: "Coffee Beans 500g".to_string(),
            sku: None,
            category_id: None,
            mrp_cents: 50000,
            cost_price_cents: Some(30000),
            tax_rate: Some(5.0),
            initial_stock: Some(100),
            min_stock_alert: Some(5),
            status: Some("active".to_string()),
        },
    )
    .await
    .unwrap();

    // 1. Process sale linked to cust.id
    SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: Some(cust.id.clone()),
            customer_name: Some(cust.name.clone()),
            customer_phone: cust.phone.clone(),
            customer_address: cust.address.clone(),
            items: vec![CreateSaleItemPayload {
                product_id: prod.id.clone(),
                quantity: 2,
                unit_price_cents: 50000,
                discount_cents: Some(0),
            }],
            discount_cents: Some(0),
            payment_method: "CASH".to_string(),
            paid_cents: 105000,
            notes: None,
        },
    )
    .await
    .unwrap();

    // 2. Process second sale resolved by phone without passing customer_id
    SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: None,
            customer_name: Some("Priya S".to_string()),
            customer_phone: Some("9845012345".to_string()), // matches cust.phone
            customer_address: None,
            items: vec![CreateSaleItemPayload {
                product_id: prod.id.clone(),
                quantity: 1,
                unit_price_cents: 50000,
                discount_cents: Some(0),
            }],
            discount_cents: Some(0),
            payment_method: "UPI".to_string(),
            paid_cents: 52500,
            notes: None,
        },
    )
    .await
    .unwrap();

    // Check customer sales metrics
    let cust_with_metrics = CustomerService::get_customer_by_id(&pool, &cust.id).await.unwrap();
    assert_eq!(cust_with_metrics.total_sales, 2);
    assert_eq!(cust_with_metrics.total_spent_cents, 150000); // 100000 + 50000
    assert!(cust_with_metrics.last_sale_date.is_some());

    // Check search functionality
    let search_by_name = CustomerService::get_customers(&pool, Some("Priya".to_string())).await.unwrap();
    assert_eq!(search_by_name.len(), 1);
    assert_eq!(search_by_name[0].id, cust.id);

    let search_by_phone = CustomerService::get_customers(&pool, Some("98450".to_string())).await.unwrap();
    assert_eq!(search_by_phone.len(), 1);
    assert_eq!(search_by_phone[0].id, cust.id);
}

#[tokio::test]
async fn test_customer_lifecycle_deletion_and_history_invariants() {
    let pool = setup_db().await;

    let cust = make_cust(&pool, "Arjun Rampal", Some("9822001122"), Some("Juhu, Mumbai")).await.unwrap();

    let prod = InventoryService::create_product(
        &pool,
        CreateProductPayload {
            name: "Protein Shake".to_string(),
            sku: None,
            category_id: None,
            mrp_cents: 20000,
            cost_price_cents: Some(12000),
            tax_rate: Some(0.0),
            initial_stock: Some(50),
            min_stock_alert: Some(5),
            status: Some("active".to_string()),
        },
    )
    .await
    .unwrap();

    let sale = SalesService::process_sale(
        &pool,
        CreateSalePayload {
            customer_id: Some(cust.id.clone()),
            customer_name: Some(cust.name.clone()),
            customer_phone: cust.phone.clone(),
            customer_address: cust.address.clone(),
            items: vec![CreateSaleItemPayload {
                product_id: prod.id.clone(),
                quantity: 1,
                unit_price_cents: 20000,
                discount_cents: None,
            }],
            discount_cents: None,
            payment_method: "CARD".to_string(),
            paid_cents: 20000,
            notes: None,
        },
    )
    .await
    .unwrap();

    // Delete customer
    CustomerService::delete_customer(&pool, &cust.id).await.unwrap();

    // 1. Invariants on deleted customer:
    // get_customers excludes deleted
    let active_list = CustomerService::get_customers(&pool, None).await.unwrap();
    assert!(active_list.iter().all(|c| c.id != cust.id));

    // get_customer_by_id returns NotFound
    let get_del_err = CustomerService::get_customer_by_id(&pool, &cust.id).await;
    assert!(matches!(get_del_err, Err(AppError::NotFound(_))));

    // Cannot update deleted customer
    let update_del_err = CustomerService::update_customer(
        &pool,
        &cust.id,
        UpdateCustomerPayload {
            name: "Arjun Updated".to_string(),
            phone: None,
            address: None,
        },
    )
    .await;
    assert!(matches!(update_del_err, Err(AppError::NotFound(_))));

    // Cannot re-delete already deleted customer
    let re_del_err = CustomerService::delete_customer(&pool, &cust.id).await;
    assert!(matches!(re_del_err, Err(AppError::NotFound(_))));

    // Audit log check
    let del_audit: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM audit_logs WHERE entity_id = ? AND event_type = 'CUSTOMER_DELETED'",
    )
    .bind(&cust.id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(del_audit, 1);

    // 2. Historical sales preservation:
    // The sale still contains customer snapshot data intact!
    let past_sale = SalesService::get_sale_details(&pool, &sale.sale.id).await.unwrap();
    assert_eq!(past_sale.sale.customer_name.as_deref(), Some("Arjun Rampal"));
    assert_eq!(past_sale.sale.customer_phone.as_deref(), Some("9822001122"));
    assert_eq!(past_sale.sale.customer_id.as_deref(), Some(cust.id.as_str()));
}
