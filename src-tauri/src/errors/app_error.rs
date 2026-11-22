use serde::Serialize;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AppError {
    #[error("{0}")]
    Database(String),

    #[error("{0}")]
    NotFound(String),

    #[error("{0}")]
    Validation(String),

    #[error("Insufficient stock for product '{product}': available {available}, requested {requested}")]
    InsufficientStock {
        product: String,
        available: i64,
        requested: i64,
    },

    #[error("{0}")]
    Migration(String),

    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),

    #[error("Serialization error: {0}")]
    Serialization(#[from] serde_json::Error),

    #[error("{0}")]
    Printer(String),

    #[error("{0}")]
    Unauthorized(String),

    #[error("{0}")]
    Forbidden(String),
}

impl From<sqlx::Error> for AppError {
    fn from(err: sqlx::Error) -> Self {
        let msg = err.to_string();
        if msg.contains("UNIQUE constraint failed: products.sku") {
            AppError::Validation("A product with this SKU already exists. Please choose a unique SKU.".to_string())
        } else if msg.contains("UNIQUE constraint failed: products.name") {
            AppError::Validation("A product with this name already exists. Please choose a unique name.".to_string())
        } else if msg.contains("UNIQUE constraint failed: categories.name") {
            AppError::Validation("A category with this name already exists.".to_string())
        } else if msg.contains("UNIQUE constraint failed: users.username") {
            AppError::Validation("A user with this username already exists.".to_string())
        } else if msg.contains("UNIQUE constraint failed: sales.invoice_number") {
            AppError::Validation("A bill with this invoice number already exists.".to_string())
        } else if msg.contains("FOREIGN KEY constraint failed") {
            AppError::Validation("This item is linked to other transactions and cannot be deleted or modified.".to_string())
        } else if msg.contains("database is locked") || msg.contains("busy") {
            AppError::Database("The database is currently busy. Please try again in a moment.".to_string())
        } else {
            AppError::Database(msg)
        }
    }
}

impl Serialize for AppError {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: serde::Serializer,
    {
        serializer.serialize_str(&self.to_string())
    }
}
