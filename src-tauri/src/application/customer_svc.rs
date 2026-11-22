use sqlx::SqlitePool;
use uuid::Uuid;

use crate::application::ValidationService;
use crate::domain::models::{CreateCustomerPayload, Customer, UpdateCustomerPayload};
use crate::errors::AppError;

pub struct CustomerService;

impl CustomerService {
    pub async fn get_customers(
        pool: &SqlitePool,
        search: Option<String>,
    ) -> Result<Vec<Customer>, AppError> {
        let pattern = search.map(|s| format!("%{}%", s.trim()));

        let customers = sqlx::query_as::<_, Customer>(
            "SELECT c.id, c.name, c.phone, c.address, c.is_deleted, c.created_at, c.updated_at,
                    COUNT(DISTINCT CASE WHEN s.status != 'CANCELLED' THEN s.id ELSE NULL END) as total_sales,
                    COALESCE(SUM(CASE WHEN s.status != 'CANCELLED' THEN s.total_cents ELSE 0 END), 0) as total_spent_cents,
                    MAX(CASE WHEN s.status != 'CANCELLED' THEN s.created_at ELSE NULL END) as last_sale_date
             FROM customers c
             LEFT JOIN sales s ON (s.customer_id = c.id OR (c.phone IS NOT NULL AND c.phone != '' AND s.customer_phone = c.phone))
             WHERE c.is_deleted = 0
               AND (?1 IS NULL OR c.name LIKE ?1 OR c.phone LIKE ?1 OR c.address LIKE ?1)
             GROUP BY c.id
             ORDER BY c.name ASC"
        )
        .bind(pattern)
        .fetch_all(pool)
        .await?;

        Ok(customers)
    }

    pub async fn get_customer_by_id(pool: &SqlitePool, id: &str) -> Result<Customer, AppError> {
        let cust = sqlx::query_as::<_, Customer>(
            "SELECT c.id, c.name, c.phone, c.address, c.is_deleted, c.created_at, c.updated_at,
                    COUNT(DISTINCT CASE WHEN s.status != 'CANCELLED' THEN s.id ELSE NULL END) as total_sales,
                    COALESCE(SUM(CASE WHEN s.status != 'CANCELLED' THEN s.total_cents ELSE 0 END), 0) as total_spent_cents,
                    MAX(CASE WHEN s.status != 'CANCELLED' THEN s.created_at ELSE NULL END) as last_sale_date
             FROM customers c
             LEFT JOIN sales s ON (s.customer_id = c.id OR (c.phone IS NOT NULL AND c.phone != '' AND s.customer_phone = c.phone))
             WHERE c.id = ? AND c.is_deleted = 0
             GROUP BY c.id"
        )
        .bind(id)
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Customer {} not found", id)))?;

        Ok(cust)
    }

    pub async fn create_customer(
        pool: &SqlitePool,
        payload: CreateCustomerPayload,
    ) -> Result<Customer, AppError> {
        ValidationService::validate_create_customer(&payload)?;
        let clean_name = payload.name.trim();
        let clean_phone = payload.phone.as_deref().map(str::trim).filter(|s| !s.is_empty());
        let clean_address = payload.address.as_deref().map(str::trim).filter(|s| !s.is_empty());

        let mut tx = pool.begin().await?;

        // 1. Phone-based uniqueness and revival check
        if let Some(phone) = clean_phone {
            let existing: Option<(String, String, i64)> = sqlx::query_as(
                "SELECT id, name, is_deleted FROM customers WHERE phone = ?"
            )
            .bind(phone)
            .fetch_optional(&mut *tx)
            .await?;

            if let Some((existing_id, existing_name, is_deleted)) = existing {
                if is_deleted == 0 {
                    return Err(AppError::Validation(format!(
                        "A customer with phone number '{}' already exists ('{}')",
                        phone, existing_name
                    )));
                } else {
                    // Revive soft-deleted customer
                    sqlx::query(
                        "UPDATE customers SET name = ?, phone = ?, address = ?, is_deleted = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
                    )
                    .bind(clean_name)
                    .bind(Some(phone))
                    .bind(clean_address)
                    .bind(&existing_id)
                    .execute(&mut *tx)
                    .await?;

                    let audit_id = Uuid::new_v4().to_string();
                    let _ = sqlx::query(
                        "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
                         VALUES (?, 'CUSTOMER_CREATED', 'CUSTOMER', ?, ?, CURRENT_TIMESTAMP)"
                    )
                    .bind(&audit_id)
                    .bind(&existing_id)
                    .bind(format!("Restored customer '{}'", clean_name))
                    .execute(&mut *tx)
                    .await;

                    tx.commit().await?;
                    return Self::get_customer_by_id(pool, &existing_id).await;
                }
            }
        } else {
            // Check for duplicate name if phone is not provided
            let existing: Option<(String, i64)> = sqlx::query_as(
                "SELECT id, is_deleted FROM customers WHERE LOWER(name) = LOWER(?) AND (phone IS NULL OR phone = '')"
            )
            .bind(clean_name)
            .fetch_optional(&mut *tx)
            .await?;

            if let Some((existing_id, is_deleted)) = existing {
                if is_deleted == 0 {
                    return Err(AppError::Validation(format!(
                        "A customer named '{}' with no phone number already exists",
                        clean_name
                    )));
                } else {
                    // Revive soft-deleted customer
                    sqlx::query(
                        "UPDATE customers SET name = ?, phone = NULL, address = ?, is_deleted = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
                    )
                    .bind(clean_name)
                    .bind(clean_address)
                    .bind(&existing_id)
                    .execute(&mut *tx)
                    .await?;

                    let audit_id = Uuid::new_v4().to_string();
                    let _ = sqlx::query(
                        "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
                         VALUES (?, 'CUSTOMER_CREATED', 'CUSTOMER', ?, ?, CURRENT_TIMESTAMP)"
                    )
                    .bind(&audit_id)
                    .bind(&existing_id)
                    .bind(format!("Restored customer '{}'", clean_name))
                    .execute(&mut *tx)
                    .await;

                    tx.commit().await?;
                    return Self::get_customer_by_id(pool, &existing_id).await;
                }
            }
        }

        let id = Uuid::new_v4().to_string();
        sqlx::query(
            "INSERT INTO customers (id, name, phone, address, is_deleted)
             VALUES (?, ?, ?, ?, 0)"
        )
        .bind(&id)
        .bind(clean_name)
        .bind(clean_phone)
        .bind(clean_address)
        .execute(&mut *tx)
        .await?;

        let audit_id = Uuid::new_v4().to_string();
        let _ = sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
             VALUES (?, 'CUSTOMER_CREATED', 'CUSTOMER', ?, ?, CURRENT_TIMESTAMP)"
        )
        .bind(&audit_id)
        .bind(&id)
        .bind(format!("Created customer '{}'", clean_name))
        .execute(&mut *tx)
        .await;

        tx.commit().await?;

        Self::get_customer_by_id(pool, &id).await
    }

    pub async fn update_customer(
        pool: &SqlitePool,
        id: &str,
        payload: UpdateCustomerPayload,
    ) -> Result<Customer, AppError> {
        ValidationService::validate_update_customer(&payload)?;
        let clean_name = payload.name.trim();
        let clean_phone = payload.phone.as_deref().map(str::trim).filter(|s| !s.is_empty());
        let clean_address = payload.address.as_deref().map(str::trim).filter(|s| !s.is_empty());

        let mut tx = pool.begin().await?;

        // 1. Confirm customer exists and is active
        let exists: bool = sqlx::query_scalar(
            "SELECT EXISTS(SELECT 1 FROM customers WHERE id = ? AND is_deleted = 0)"
        )
        .bind(id)
        .fetch_one(&mut *tx)
        .await
        .unwrap_or(false);

        if !exists {
            return Err(AppError::NotFound(format!("Customer {} not found", id)));
        }

        // 2. Phone conflict check against other active customers
        if let Some(phone) = clean_phone {
            let conflict: Option<String> = sqlx::query_scalar(
                "SELECT name FROM customers WHERE phone = ? AND id <> ? AND is_deleted = 0"
            )
            .bind(phone)
            .bind(id)
            .fetch_optional(&mut *tx)
            .await?;

            if let Some(other_name) = conflict {
                return Err(AppError::Validation(format!(
                    "A customer with phone number '{}' already exists ('{}')",
                    phone, other_name
                )));
            }
        }

        sqlx::query(
            "UPDATE customers
             SET name = ?, phone = ?, address = ?, updated_at = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = 0"
        )
        .bind(clean_name)
        .bind(clean_phone)
        .bind(clean_address)
        .bind(id)
        .execute(&mut *tx)
        .await?;

        let audit_id = Uuid::new_v4().to_string();
        let _ = sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
             VALUES (?, 'CUSTOMER_UPDATED', 'CUSTOMER', ?, ?, CURRENT_TIMESTAMP)"
        )
        .bind(&audit_id)
        .bind(id)
        .bind(format!("Updated customer '{}'", clean_name))
        .execute(&mut *tx)
        .await;

        tx.commit().await?;

        Self::get_customer_by_id(pool, id).await
    }

    pub async fn delete_customer(pool: &SqlitePool, id: &str) -> Result<(), AppError> {
        let mut tx = pool.begin().await?;

        let cust_row: Option<(String, i64)> = sqlx::query_as(
            "SELECT name, is_deleted FROM customers WHERE id = ?"
        )
        .bind(id)
        .fetch_optional(&mut *tx)
        .await?;

        let (name, is_deleted) = match cust_row {
            Some(r) => r,
            None => return Err(AppError::NotFound(format!("Customer {} not found", id))),
        };

        if is_deleted != 0 {
            return Err(AppError::NotFound(format!("Customer {} is already deleted", id)));
        }

        sqlx::query(
            "UPDATE customers SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        )
        .bind(id)
        .execute(&mut *tx)
        .await?;

        let audit_id = Uuid::new_v4().to_string();
        let _ = sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
             VALUES (?, 'CUSTOMER_DELETED', 'CUSTOMER', ?, ?, CURRENT_TIMESTAMP)"
        )
        .bind(&audit_id)
        .bind(id)
        .bind(format!("Soft deleted customer '{}'", name))
        .execute(&mut *tx)
        .await;

        tx.commit().await?;

        Ok(())
    }
}
