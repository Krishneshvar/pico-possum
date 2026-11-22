use sqlx::SqlitePool;
use uuid::Uuid;

use crate::application::ValidationService;
use crate::domain::models::{Category, CreateCategoryPayload, UpdateCategoryPayload};
use crate::errors::AppError;

pub struct CategoryService;

impl CategoryService {
    pub async fn get_categories(pool: &SqlitePool) -> Result<Vec<Category>, AppError> {
        let categories = sqlx::query_as::<_, Category>(
            "SELECT c.id, c.name, c.parent_id, p.name as parent_name, c.is_deleted, c.created_at, c.updated_at
             FROM categories c
             LEFT JOIN categories p ON p.id = c.parent_id
             WHERE c.is_deleted = 0
             ORDER BY c.name ASC"
        )
        .fetch_all(pool)
        .await?;

        Ok(categories)
    }

    pub async fn create_category(
        pool: &SqlitePool,
        payload: CreateCategoryPayload,
    ) -> Result<Category, AppError> {
        ValidationService::validate_create_category(&payload)?;
        let id = Uuid::new_v4().to_string();
        let clean_name = payload.name.trim();
        let clean_parent = payload.parent_id.as_deref().map(str::trim).filter(|s| !s.is_empty());

        let mut tx = pool.begin().await?;

        sqlx::query(
            "INSERT INTO categories (id, name, parent_id, is_deleted)
             VALUES (?, ?, ?, 0)"
        )
        .bind(&id)
        .bind(clean_name)
        .bind(clean_parent)
        .execute(&mut *tx)
        .await?;

        let audit_id = Uuid::new_v4().to_string();
        let _ = sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
             VALUES (?, 'CATEGORY_CREATED', 'CATEGORY', ?, ?, CURRENT_TIMESTAMP)"
        )
        .bind(&audit_id)
        .bind(&id)
        .bind(format!("Created category '{}'", clean_name))
        .execute(&mut *tx)
        .await;

        tx.commit().await?;

        let cat = sqlx::query_as::<_, Category>(
            "SELECT c.id, c.name, c.parent_id, p.name as parent_name, c.is_deleted, c.created_at, c.updated_at
             FROM categories c
             LEFT JOIN categories p ON p.id = c.parent_id
             WHERE c.id = ?"
        )
        .bind(&id)
        .fetch_one(pool)
        .await?;

        Ok(cat)
    }

    pub async fn update_category(
        pool: &SqlitePool,
        id: &str,
        payload: UpdateCategoryPayload,
    ) -> Result<Category, AppError> {
        ValidationService::validate_update_category(id, &payload)?;
        let clean_name = payload.name.trim();
        let clean_parent = payload.parent_id.as_deref().map(str::trim).filter(|s| !s.is_empty());

        let mut tx = pool.begin().await?;

        let rows_affected = sqlx::query(
            "UPDATE categories SET name = ?, parent_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = 0"
        )
        .bind(clean_name)
        .bind(clean_parent)
        .bind(id)
        .execute(&mut *tx)
        .await?
        .rows_affected();

        if rows_affected == 0 {
            return Err(AppError::NotFound(format!("Category {} not found", id)));
        }

        let audit_id = Uuid::new_v4().to_string();
        let _ = sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
             VALUES (?, 'CATEGORY_UPDATED', 'CATEGORY', ?, ?, CURRENT_TIMESTAMP)"
        )
        .bind(&audit_id)
        .bind(id)
        .bind(format!("Updated category '{}'", clean_name))
        .execute(&mut *tx)
        .await;

        tx.commit().await?;

        let cat = sqlx::query_as::<_, Category>(
            "SELECT c.id, c.name, c.parent_id, p.name as parent_name, c.is_deleted, c.created_at, c.updated_at
             FROM categories c
             LEFT JOIN categories p ON p.id = c.parent_id
             WHERE c.id = ?"
        )
        .bind(id)
        .fetch_one(pool)
        .await?;

        Ok(cat)
    }

    pub async fn delete_category(pool: &SqlitePool, id: &str) -> Result<(), AppError> {
        let mut tx = pool.begin().await?;

        let cat_name: Option<String> = sqlx::query_scalar(
            "SELECT name FROM categories WHERE id = ? AND is_deleted = 0"
        )
        .bind(id)
        .fetch_optional(&mut *tx)
        .await?;

        let name = match cat_name {
            Some(n) => n,
            None => return Err(AppError::NotFound(format!("Category {} not found", id))),
        };

        // Detach subcategories so they don't point to a deleted parent
        sqlx::query("UPDATE categories SET parent_id = NULL WHERE parent_id = ?")
            .bind(id)
            .execute(&mut *tx)
            .await?;

        sqlx::query(
            "UPDATE categories SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        )
        .bind(id)
        .execute(&mut *tx)
        .await?;

        let audit_id = Uuid::new_v4().to_string();
        let _ = sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
             VALUES (?, 'CATEGORY_DELETED', 'CATEGORY', ?, ?, CURRENT_TIMESTAMP)"
        )
        .bind(&audit_id)
        .bind(id)
        .bind(format!("Soft deleted category '{}'", name))
        .execute(&mut *tx)
        .await;

        tx.commit().await?;

        Ok(())
    }
}
