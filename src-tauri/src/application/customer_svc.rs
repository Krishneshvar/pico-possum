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
               AND (?1 IS NULL OR c.name LIKE ?1 OR c.phone LIKE ?1)
             GROUP BY c.id
             ORDER BY c.name ASC"
        )
        .bind(pattern)
        .fetch_all(pool)
        .await?;

        Ok(customers)
    }

    pub async fn create_customer(
        pool: &SqlitePool,
        payload: CreateCustomerPayload,
    ) -> Result<Customer, AppError> {
        ValidationService::validate_create_customer(&payload)?;
        let id = Uuid::new_v4().to_string();
        sqlx::query(
            "INSERT INTO customers (id, name, phone, address, is_deleted)
             VALUES (?, ?, ?, ?, 0)"
        )
        .bind(&id)
        .bind(&payload.name)
        .bind(&payload.phone)
        .bind(&payload.address)
        .execute(pool)
        .await?;

        let cust = sqlx::query_as::<_, Customer>(
            "SELECT id, name, phone, address, is_deleted, created_at, updated_at
             FROM customers WHERE id = ?"
        )
        .bind(&id)
        .fetch_one(pool)
        .await?;

        Ok(cust)
    }

    pub async fn update_customer(
        pool: &SqlitePool,
        id: &str,
        payload: UpdateCustomerPayload,
    ) -> Result<Customer, AppError> {
        ValidationService::validate_update_customer(&payload)?;
        let rows_affected = sqlx::query(
            "UPDATE customers
             SET name = ?, phone = ?, address = ?, updated_at = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = 0"
        )
        .bind(&payload.name)
        .bind(&payload.phone)
        .bind(&payload.address)
        .bind(id)
        .execute(pool)
        .await?
        .rows_affected();

        if rows_affected == 0 {
            return Err(AppError::NotFound(format!("Customer {id} not found")));
        }

        let cust = sqlx::query_as::<_, Customer>(
            "SELECT id, name, phone, address, is_deleted, created_at, updated_at
             FROM customers WHERE id = ?"
        )
        .bind(id)
        .fetch_one(pool)
        .await?;

        Ok(cust)
    }

    pub async fn delete_customer(pool: &SqlitePool, id: &str) -> Result<(), AppError> {
        let rows_affected = sqlx::query(
            "UPDATE customers SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        )
        .bind(id)
        .execute(pool)
        .await?
        .rows_affected();

        if rows_affected == 0 {
            return Err(AppError::NotFound(format!("Customer {id} not found")));
        }

        Ok(())
    }
}
