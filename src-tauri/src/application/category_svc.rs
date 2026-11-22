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

    pub async fn get_category_by_id(pool: &SqlitePool, id: &str) -> Result<Category, AppError> {
        let cat = sqlx::query_as::<_, Category>(
            "SELECT c.id, c.name, c.parent_id, p.name as parent_name, c.is_deleted, c.created_at, c.updated_at
             FROM categories c
             LEFT JOIN categories p ON p.id = c.parent_id
             WHERE c.id = ? AND c.is_deleted = 0"
        )
        .bind(id)
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Category {} not found", id)))?;

        Ok(cat)
    }

    async fn check_circular_parent_tx(
        tx: &mut sqlx::Transaction<'_, sqlx::Sqlite>,
        category_id: &str,
        new_parent_id: &str,
    ) -> Result<(), AppError> {
        let mut curr = Some(new_parent_id.to_string());
        let mut visited = std::collections::HashSet::new();

        while let Some(pid) = curr {
            if pid == category_id {
                return Err(AppError::Validation(
                    "Circular category reference detected: a category cannot be assigned to one of its own subcategories".to_string(),
                ));
            }
            if !visited.insert(pid.clone()) {
                break;
            }
            let parent_of_pid: Option<Option<String>> = sqlx::query_scalar(
                "SELECT parent_id FROM categories WHERE id = ? AND is_deleted = 0"
            )
            .bind(&pid)
            .fetch_optional(&mut **tx)
            .await?;

            curr = parent_of_pid.flatten();
        }

        Ok(())
    }

    pub async fn create_category(
        pool: &SqlitePool,
        payload: CreateCategoryPayload,
    ) -> Result<Category, AppError> {
        ValidationService::validate_create_category(&payload)?;
        let clean_name = payload.name.trim();
        let clean_parent = payload.parent_id.as_deref().map(str::trim).filter(|s| !s.is_empty());

        let mut tx = pool.begin().await?;

        // 1. Validate parent if provided
        if let Some(pid) = clean_parent {
            let parent_exists: bool = sqlx::query_scalar(
                "SELECT EXISTS(SELECT 1 FROM categories WHERE id = ? AND is_deleted = 0)"
            )
            .bind(pid)
            .fetch_one(&mut *tx)
            .await
            .unwrap_or(false);

            if !parent_exists {
                return Err(AppError::Validation(
                    "Parent category not found or has been deleted".to_string(),
                ));
            }
        }

        // 2. Check for existing category with same name (case-insensitive)
        let existing: Option<(String, i64)> = sqlx::query_as(
            "SELECT id, is_deleted FROM categories WHERE LOWER(name) = LOWER(?)"
        )
        .bind(clean_name)
        .fetch_optional(&mut *tx)
        .await?;

        if let Some((existing_id, is_deleted)) = existing {
            if is_deleted == 0 {
                return Err(AppError::Validation(format!(
                    "A category named '{}' already exists",
                    clean_name
                )));
            } else {
                // Revive previously soft-deleted category
                sqlx::query(
                    "UPDATE categories SET name = ?, parent_id = ?, is_deleted = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
                )
                .bind(clean_name)
                .bind(clean_parent)
                .bind(&existing_id)
                .execute(&mut *tx)
                .await?;

                let audit_id = Uuid::new_v4().to_string();
                let _ = sqlx::query(
                    "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description, created_at)
                     VALUES (?, 'CATEGORY_CREATED', 'CATEGORY', ?, ?, CURRENT_TIMESTAMP)"
                )
                .bind(&audit_id)
                .bind(&existing_id)
                .bind(format!("Restored category '{}'", clean_name))
                .execute(&mut *tx)
                .await;

                tx.commit().await?;
                return Self::get_category_by_id(pool, &existing_id).await;
            }
        }

        let id = Uuid::new_v4().to_string();
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

        Self::get_category_by_id(pool, &id).await
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

        // 1. Confirm existence
        let exists: bool = sqlx::query_scalar(
            "SELECT EXISTS(SELECT 1 FROM categories WHERE id = ? AND is_deleted = 0)"
        )
        .bind(id)
        .fetch_one(&mut *tx)
        .await
        .unwrap_or(false);

        if !exists {
            return Err(AppError::NotFound(format!("Category {} not found", id)));
        }

        // 2. Duplicate name check against other active categories
        let dup_exists: bool = sqlx::query_scalar(
            "SELECT EXISTS(SELECT 1 FROM categories WHERE LOWER(name) = LOWER(?) AND id <> ? AND is_deleted = 0)"
        )
        .bind(clean_name)
        .bind(id)
        .fetch_one(&mut *tx)
        .await
        .unwrap_or(false);

        if dup_exists {
            return Err(AppError::Validation(format!(
                "A category named '{}' already exists",
                clean_name
            )));
        }

        // 3. Parent validation and circular hierarchy check
        if let Some(pid) = clean_parent {
            if pid == id {
                return Err(AppError::Validation("A category cannot be its own parent".to_string()));
            }

            let parent_exists: bool = sqlx::query_scalar(
                "SELECT EXISTS(SELECT 1 FROM categories WHERE id = ? AND is_deleted = 0)"
            )
            .bind(pid)
            .fetch_one(&mut *tx)
            .await
            .unwrap_or(false);

            if !parent_exists {
                return Err(AppError::Validation(
                    "Parent category not found or has been deleted".to_string(),
                ));
            }

            Self::check_circular_parent_tx(&mut tx, id, pid).await?;
        }

        sqlx::query(
            "UPDATE categories SET name = ?, parent_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = 0"
        )
        .bind(clean_name)
        .bind(clean_parent)
        .bind(id)
        .execute(&mut *tx)
        .await?;

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

        Self::get_category_by_id(pool, id).await
    }

    pub async fn delete_category(pool: &SqlitePool, id: &str) -> Result<(), AppError> {
        let mut tx = pool.begin().await?;

        let cat_row: Option<(String, i64)> = sqlx::query_as(
            "SELECT name, is_deleted FROM categories WHERE id = ?"
        )
        .bind(id)
        .fetch_optional(&mut *tx)
        .await?;

        let (name, is_deleted) = match cat_row {
            Some(r) => r,
            None => return Err(AppError::NotFound(format!("Category {} not found", id))),
        };

        if is_deleted != 0 {
            return Err(AppError::NotFound(format!("Category {} is already deleted", id)));
        }

        // Detach subcategories so they don't point to a deleted parent
        sqlx::query("UPDATE categories SET parent_id = NULL, updated_at = CURRENT_TIMESTAMP WHERE parent_id = ?")
            .bind(id)
            .execute(&mut *tx)
            .await?;

        // Detach products referencing this category so active products are not linked to deleted categories
        sqlx::query("UPDATE products SET category_id = NULL, updated_at = CURRENT_TIMESTAMP WHERE category_id = ?")
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
