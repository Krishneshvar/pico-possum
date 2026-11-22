use sqlx::sqlite::SqlitePoolOptions;
use sqlx::SqlitePool;

use pico_possum_lib::application::{CategoryService, InventoryService};
use pico_possum_lib::domain::models::{
    Category, CreateCategoryPayload, CreateProductPayload, UpdateCategoryPayload,
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

async fn make_cat(pool: &SqlitePool, name: &str, parent: Option<&str>) -> Result<Category, AppError> {
    CategoryService::create_category(
        pool,
        CreateCategoryPayload {
            name: name.to_string(),
            parent_id: parent.map(|s| s.to_string()),
        },
    )
    .await
}

#[tokio::test]
async fn test_category_lifecycle_creation_and_validation() {
    let pool = setup_db().await;

    // 1. Create root category
    let root = make_cat(&pool, "Electronics", None).await.unwrap();
    assert_eq!(root.name, "Electronics");
    assert!(root.parent_id.is_none());
    assert!(root.parent_name.is_none());

    // Audit log check
    let audit_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM audit_logs WHERE entity_id = ? AND event_type = 'CATEGORY_CREATED'",
    )
    .bind(&root.id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(audit_count, 1);

    // 2. Create subcategory
    let sub = make_cat(&pool, "Laptops", Some(&root.id)).await.unwrap();
    assert_eq!(sub.name, "Laptops");
    assert_eq!(sub.parent_id.as_deref(), Some(root.id.as_str()));
    assert_eq!(sub.parent_name.as_deref(), Some("Electronics"));

    // 3. Validation: empty name
    let empty_err = make_cat(&pool, "   ", None).await;
    assert!(matches!(empty_err, Err(AppError::Validation(_))));

    // 4. Validation: name too long
    let long_name = "A".repeat(101);
    let long_err = make_cat(&pool, &long_name, None).await;
    assert!(matches!(long_err, Err(AppError::Validation(_))));

    // 5. Validation: non-existent parent
    let bad_parent = make_cat(&pool, "Smartphones", Some("non-existent-uuid")).await;
    assert!(matches!(bad_parent, Err(AppError::Validation(_))));
}

#[tokio::test]
async fn test_category_lifecycle_uniqueness_and_restoration() {
    let pool = setup_db().await;

    let cat1 = make_cat(&pool, "Beverages", None).await.unwrap();

    // Duplicate active category name is rejected
    let dup_err = make_cat(&pool, "beverages", None).await;
    assert!(matches!(dup_err, Err(AppError::Validation(_))));

    // Soft delete category
    CategoryService::delete_category(&pool, &cat1.id).await.unwrap();

    // Recreating category with the same name revives it rather than throwing SQLite UNIQUE constraint violation
    let revived = make_cat(&pool, "Beverages", None).await.unwrap();
    assert_eq!(revived.id, cat1.id);
    assert!(!revived.is_deleted);

    // Active categories list contains exactly 1 Beverages
    let all = CategoryService::get_categories(&pool).await.unwrap();
    assert_eq!(all.len(), 1);
    assert_eq!(all[0].id, cat1.id);
}

#[tokio::test]
async fn test_category_lifecycle_updating_and_cycle_prevention() {
    let pool = setup_db().await;

    // Create hierarchy: A -> B -> C
    let cat_a = make_cat(&pool, "Hardware", None).await.unwrap();
    let cat_b = make_cat(&pool, "Computers", Some(&cat_a.id)).await.unwrap();
    let cat_c = make_cat(&pool, "Workstations", Some(&cat_b.id)).await.unwrap();

    // 1. Valid update
    let updated_b = CategoryService::update_category(
        &pool,
        &cat_b.id,
        UpdateCategoryPayload {
            name: "Personal Computers".to_string(),
            parent_id: Some(cat_a.id.clone()),
        },
    )
    .await
    .unwrap();
    assert_eq!(updated_b.name, "Personal Computers");

    // Audit log check
    let audit_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM audit_logs WHERE entity_id = ? AND event_type = 'CATEGORY_UPDATED'",
    )
    .bind(&cat_b.id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(audit_count, 1);

    // 2. Self-parenting rejected
    let self_parent_err = CategoryService::update_category(
        &pool,
        &cat_b.id,
        UpdateCategoryPayload {
            name: "Personal Computers".to_string(),
            parent_id: Some(cat_b.id.clone()),
        },
    )
    .await;
    assert!(matches!(self_parent_err, Err(AppError::Validation(_))));

    // 3. Circular hierarchy: Attempting to set A's parent to C (which is a descendant of A)
    let cycle_err = CategoryService::update_category(
        &pool,
        &cat_a.id,
        UpdateCategoryPayload {
            name: "Hardware".to_string(),
            parent_id: Some(cat_c.id.clone()),
        },
    )
    .await;
    assert!(matches!(cycle_err, Err(AppError::Validation(_))));

    // 4. Duplicate name on update against another active category
    let dup_name_err = CategoryService::update_category(
        &pool,
        &cat_a.id,
        UpdateCategoryPayload {
            name: "Personal Computers".to_string(), // already used by cat_b
            parent_id: None,
        },
    )
    .await;
    assert!(matches!(dup_name_err, Err(AppError::Validation(_))));
}

#[tokio::test]
async fn test_category_lifecycle_product_association_and_detach_on_delete() {
    let pool = setup_db().await;

    let parent_cat = make_cat(&pool, "Snacks", None).await.unwrap();
    let sub_cat = make_cat(&pool, "Chips", Some(&parent_cat.id)).await.unwrap();

    // Create products assigned to parent and subcategory
    let prod1 = InventoryService::create_product(
        &pool,
        CreateProductPayload {
            name: "Potato Chips 50g".to_string(),
            sku: None,
            category_id: Some(sub_cat.id.clone()),
            mrp_cents: 3000,
            cost_price_cents: Some(1500),
            tax_rate: Some(12.0),
            initial_stock: Some(20),
            min_stock_alert: Some(5),
            status: Some("active".to_string()),
        },
    )
    .await
    .unwrap();

    let prod2 = InventoryService::create_product(
        &pool,
        CreateProductPayload {
            name: "Mixed Nuts 100g".to_string(),
            sku: None,
            category_id: Some(parent_cat.id.clone()),
            mrp_cents: 12000,
            cost_price_cents: Some(7000),
            tax_rate: Some(5.0),
            initial_stock: Some(10),
            min_stock_alert: Some(2),
            status: Some("active".to_string()),
        },
    )
    .await
    .unwrap();

    assert_eq!(prod1.category_name.as_deref(), Some("Chips"));
    assert_eq!(prod2.category_name.as_deref(), Some("Snacks"));

    // Filter products by subcategory
    let chips_prods = InventoryService::get_products(&pool, Some(sub_cat.id.clone()), None).await.unwrap();
    assert_eq!(chips_prods.len(), 1);
    assert_eq!(chips_prods[0].id, prod1.id);

    // Delete parent_cat ("Snacks"):
    // 1. sub_cat ("Chips") should have parent_id detached to NULL
    // 2. prod2 ("Mixed Nuts") should have category_id detached to NULL
    CategoryService::delete_category(&pool, &parent_cat.id).await.unwrap();

    let sub_after = CategoryService::get_category_by_id(&pool, &sub_cat.id).await.unwrap();
    assert!(sub_after.parent_id.is_none());
    assert!(sub_after.parent_name.is_none());

    let prod2_after = InventoryService::get_product_by_id(&pool, &prod2.id).await.unwrap();
    assert!(prod2_after.category_id.is_none());
    assert!(prod2_after.category_name.is_none());

    // Invariants on deleted category:
    // get_category_by_id returns NotFound
    let get_del_err = CategoryService::get_category_by_id(&pool, &parent_cat.id).await;
    assert!(matches!(get_del_err, Err(AppError::NotFound(_))));

    // Cannot update deleted category
    let update_del_err = CategoryService::update_category(
        &pool,
        &parent_cat.id,
        UpdateCategoryPayload {
            name: "Updated Snacks".to_string(),
            parent_id: None,
        },
    )
    .await;
    assert!(matches!(update_del_err, Err(AppError::NotFound(_))));

    // Cannot re-delete already deleted category
    let re_del_err = CategoryService::delete_category(&pool, &parent_cat.id).await;
    assert!(matches!(re_del_err, Err(AppError::NotFound(_))));

    // Audit log check
    let del_audit: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM audit_logs WHERE entity_id = ? AND event_type = 'CATEGORY_DELETED'",
    )
    .bind(&parent_cat.id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(del_audit, 1);
}
