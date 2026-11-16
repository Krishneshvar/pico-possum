use sqlx::SqlitePool;
use uuid::Uuid;

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
        let id = Uuid::new_v4().to_string();
        sqlx::query(
            "INSERT INTO categories (id, name, parent_id, is_deleted)
             VALUES (?, ?, ?, 0)"
        )
        .bind(&id)
        .bind(&payload.name)
        .bind(&payload.parent_id)
        .execute(pool)
        .await?;

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
        sqlx::query(
            "UPDATE categories SET name = ?, parent_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        )
        .bind(&payload.name)
        .bind(&payload.parent_id)
        .bind(id)
        .execute(pool)
        .await?;

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
        sqlx::query(
            "UPDATE categories SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        )
        .bind(id)
        .execute(pool)
        .await?;

        Ok(())
    }
}
