use pico_possum_lib::application::ValidationService;
use pico_possum_lib::domain::models::{
    CreateCategoryPayload, CreateCustomerPayload, CreateProductPayload, CreateSaleItemPayload,
    CreateSalePayload, StockAdjustmentPayload, UpdateCategoryPayload,
    UpdateProductPayload,
};

#[test]
fn test_product_validation() {
    // Valid create payload
    let valid_prod = CreateProductPayload {
        name: "Test Item".to_string(),
        sku: Some("SKU-123".to_string()),
        category_id: None,
        mrp_cents: 1500,
        cost_price_cents: Some(1000),
        tax_rate: Some(18.0),
        initial_stock: Some(10),
        min_stock_alert: Some(2),
        status: Some("active".to_string()),
    };
    assert!(ValidationService::validate_create_product(&valid_prod).is_ok());

    // Empty name
    let mut invalid = valid_prod.clone();
    invalid.name = "   ".to_string();
    assert!(ValidationService::validate_create_product(&invalid).is_err());

    // Negative MRP
    let mut invalid = valid_prod.clone();
    invalid.mrp_cents = -10;
    assert!(ValidationService::validate_create_product(&invalid).is_err());

    // Negative cost price
    let mut invalid = valid_prod.clone();
    invalid.cost_price_cents = Some(-50);
    assert!(ValidationService::validate_create_product(&invalid).is_err());

    // Invalid tax rate (> 100 or < 0)
    let mut invalid = valid_prod.clone();
    invalid.tax_rate = Some(150.0);
    assert!(ValidationService::validate_create_product(&invalid).is_err());
    invalid.tax_rate = Some(-5.0);
    assert!(ValidationService::validate_create_product(&invalid).is_err());

    // Invalid status
    let mut invalid = valid_prod.clone();
    invalid.status = Some("unknown_status".to_string());
    assert!(ValidationService::validate_create_product(&invalid).is_err());

    // Update product validation
    let valid_update = UpdateProductPayload {
        name: "Updated Item".to_string(),
        sku: Some("SKU-123".to_string()),
        category_id: None,
        mrp_cents: 2000,
        cost_price_cents: 1200,
        tax_rate: 5.0,
        min_stock_alert: Some(5),
        status: "active".to_string(),
    };
    assert!(ValidationService::validate_update_product(&valid_update).is_ok());

    let mut invalid_update = valid_update.clone();
    invalid_update.cost_price_cents = -100;
    assert!(ValidationService::validate_update_product(&invalid_update).is_err());
}

#[test]
fn test_category_validation() {
    let valid_cat = CreateCategoryPayload {
        name: "Snacks".to_string(),
        parent_id: None,
    };
    assert!(ValidationService::validate_create_category(&valid_cat).is_ok());

    let invalid_cat = CreateCategoryPayload {
        name: "".to_string(),
        parent_id: None,
    };
    assert!(ValidationService::validate_create_category(&invalid_cat).is_err());

    // Self-parenting check on update
    let self_parent = UpdateCategoryPayload {
        name: "Snacks".to_string(),
        parent_id: Some("cat-123".to_string()),
    };
    assert!(ValidationService::validate_update_category("cat-123", &self_parent).is_err());

    let diff_parent = UpdateCategoryPayload {
        name: "Snacks".to_string(),
        parent_id: Some("cat-999".to_string()),
    };
    assert!(ValidationService::validate_update_category("cat-123", &diff_parent).is_ok());
}

#[test]
fn test_customer_validation() {
    let valid_cust = CreateCustomerPayload {
        name: "John Doe".to_string(),
        phone: Some("+91 9876543210".to_string()),
        address: Some("123 Main St".to_string()),
    };
    assert!(ValidationService::validate_create_customer(&valid_cust).is_ok());

    let empty_name = CreateCustomerPayload {
        name: "   ".to_string(),
        phone: None,
        address: None,
    };
    assert!(ValidationService::validate_create_customer(&empty_name).is_err());

    let invalid_phone = CreateCustomerPayload {
        name: "Jane Doe".to_string(),
        phone: Some("phone#123;DROP TABLE".to_string()),
        address: None,
    };
    assert!(ValidationService::validate_create_customer(&invalid_phone).is_err());
}

#[test]
fn test_sales_and_price_validation() {
    // Unit price <= MRP
    assert!(ValidationService::validate_item_unit_price("Test", 1000, 1000).is_ok());
    assert!(ValidationService::validate_item_unit_price("Test", 900, 1000).is_ok());
    assert!(ValidationService::validate_item_unit_price("Test", 1001, 1000).is_err());

    // Empty items
    let empty_sale = CreateSalePayload {
        customer_id: None,
        customer_name: None,
        customer_phone: None,
        customer_address: None,
        items: vec![],
        discount_cents: None,
        payment_method: "CASH".to_string(),
        paid_cents: 1000,
        notes: None,
    };
    assert!(ValidationService::validate_create_sale(&empty_sale).is_err());

    // Invalid payment method
    let bad_pm = CreateSalePayload {
        customer_id: None,
        customer_name: None,
        customer_phone: None,
        customer_address: None,
        items: vec![CreateSaleItemPayload {
            product_id: "prod-1".to_string(),
            quantity: 1,
            unit_price_cents: 500,
            discount_cents: None,
        }],
        discount_cents: None,
        payment_method: "BITCOIN".to_string(),
        paid_cents: 500,
        notes: None,
    };
    assert!(ValidationService::validate_create_sale(&bad_pm).is_err());

    // Valid sale
    let mut good_sale = bad_pm.clone();
    good_sale.payment_method = "UPI".to_string();
    assert!(ValidationService::validate_create_sale(&good_sale).is_ok());
}

#[test]
fn test_stock_adjustment_and_password_validation() {
    let adj = StockAdjustmentPayload {
        product_id: "prod-1".to_string(),
        quantity_change: 0,
        reason: "DAMAGE".to_string(),
        note: None,
    };
    assert!(ValidationService::validate_stock_adjustment(&adj).is_err());

    let mut valid_adj = adj.clone();
    valid_adj.quantity_change = -5;
    assert!(ValidationService::validate_stock_adjustment(&valid_adj).is_ok());

    // Password validation
    assert!(ValidationService::validate_password_change("", "1234").is_err());
    assert!(ValidationService::validate_password_change("old", "12").is_err());
    assert!(ValidationService::validate_password_change("old", "pass123").is_ok());
}
