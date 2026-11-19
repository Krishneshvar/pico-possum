use crate::domain::models::{
    CreateCategoryPayload, CreateCustomerPayload, CreateProductPayload, CreateSalePayload,
    StockAdjustmentPayload, UpdateCategoryPayload, UpdateCustomerPayload, UpdateProductPayload,
    UpdateSalePayload,
};
use crate::errors::AppError;

pub struct ValidationService;

impl ValidationService {
    pub fn validate_create_customer(payload: &CreateCustomerPayload) -> Result<(), AppError> {
        Self::validate_customer(&payload.name, payload.phone.as_deref(), payload.address.as_deref())
    }

    pub fn validate_update_customer(payload: &UpdateCustomerPayload) -> Result<(), AppError> {
        Self::validate_customer(&payload.name, payload.phone.as_deref(), payload.address.as_deref())
    }
    pub fn validate_create_product(payload: &CreateProductPayload) -> Result<(), AppError> {
        let name = payload.name.trim();
        if name.is_empty() {
            return Err(AppError::Validation("Product name is required".to_string()));
        }
        if name.len() > 200 {
            return Err(AppError::Validation("Product name cannot exceed 200 characters".to_string()));
        }
        if let Some(sku) = &payload.sku {
            if sku.trim().len() > 50 {
                return Err(AppError::Validation("SKU cannot exceed 50 characters".to_string()));
            }
        }
        if payload.mrp_paise < 0 {
            return Err(AppError::Validation("MRP cannot be negative".to_string()));
        }
        if let Some(cp) = payload.cost_price_paise {
            if cp < 0 {
                return Err(AppError::Validation("Cost price cannot be negative".to_string()));
            }
        }
        if let Some(tax) = payload.tax_rate {
            if !(0.0..=100.0).contains(&tax) {
                return Err(AppError::Validation("Tax rate must be between 0% and 100%".to_string()));
            }
        }
        if let Some(stock) = payload.initial_stock {
            if stock < 0 {
                return Err(AppError::Validation("Initial stock cannot be negative".to_string()));
            }
        }
        if let Some(min_alert) = payload.min_stock_alert {
            if min_alert < 0 {
                return Err(AppError::Validation("Minimum stock alert cannot be negative".to_string()));
            }
        }
        if let Some(status) = &payload.status {
            let s = status.trim().to_lowercase();
            if s != "active" && s != "inactive" && s != "discontinued" {
                return Err(AppError::Validation("Status must be active, inactive, or discontinued".to_string()));
            }
        }
        Ok(())
    }

    pub fn validate_update_product(payload: &UpdateProductPayload) -> Result<(), AppError> {
        let name = payload.name.trim();
        if name.is_empty() {
            return Err(AppError::Validation("Product name is required".to_string()));
        }
        if name.len() > 200 {
            return Err(AppError::Validation("Product name cannot exceed 200 characters".to_string()));
        }
        if let Some(sku) = &payload.sku {
            if sku.trim().len() > 50 {
                return Err(AppError::Validation("SKU cannot exceed 50 characters".to_string()));
            }
        }
        if payload.mrp_paise < 0 {
            return Err(AppError::Validation("MRP cannot be negative".to_string()));
        }
        if payload.cost_price_paise < 0 {
            return Err(AppError::Validation("Cost price cannot be negative".to_string()));
        }
        if !(0.0..=100.0).contains(&payload.tax_rate) {
            return Err(AppError::Validation("Tax rate must be between 0% and 100%".to_string()));
        }
        if let Some(min_alert) = payload.min_stock_alert {
            if min_alert < 0 {
                return Err(AppError::Validation("Minimum stock alert cannot be negative".to_string()));
            }
        }
        let s = payload.status.trim().to_lowercase();
        if s != "active" && s != "inactive" && s != "discontinued" {
            return Err(AppError::Validation("Status must be active, inactive, or discontinued".to_string()));
        }
        Ok(())
    }

    pub fn validate_create_category(payload: &CreateCategoryPayload) -> Result<(), AppError> {
        let name = payload.name.trim();
        if name.is_empty() {
            return Err(AppError::Validation("Category name is required".to_string()));
        }
        if name.len() > 100 {
            return Err(AppError::Validation("Category name cannot exceed 100 characters".to_string()));
        }
        Ok(())
    }

    pub fn validate_update_category(id: &str, payload: &UpdateCategoryPayload) -> Result<(), AppError> {
        let name = payload.name.trim();
        if name.is_empty() {
            return Err(AppError::Validation("Category name is required".to_string()));
        }
        if name.len() > 100 {
            return Err(AppError::Validation("Category name cannot exceed 100 characters".to_string()));
        }
        if let Some(pid) = &payload.parent_id {
            if pid.trim() == id.trim() {
                return Err(AppError::Validation("A category cannot be its own parent".to_string()));
            }
        }
        Ok(())
    }

    pub fn validate_customer(name: &str, phone: Option<&str>, address: Option<&str>) -> Result<(), AppError> {
        let n = name.trim();
        if n.is_empty() {
            return Err(AppError::Validation("Customer name is required".to_string()));
        }
        if n.len() > 150 {
            return Err(AppError::Validation("Customer name cannot exceed 150 characters".to_string()));
        }
        if let Some(p) = phone {
            let pt = p.trim();
            if !pt.is_empty() {
                if pt.len() > 30 {
                    return Err(AppError::Validation("Phone number cannot exceed 30 characters".to_string()));
                }
                if !pt.chars().all(|c| c.is_ascii_digit() || c == '+' || c == '-' || c == ' ' || c == '(' || c == ')' || c == '/') {
                    return Err(AppError::Validation("Phone number contains invalid characters".to_string()));
                }
            }
        }
        if let Some(a) = address {
            if a.trim().len() > 500 {
                return Err(AppError::Validation("Address cannot exceed 500 characters".to_string()));
            }
        }
        Ok(())
    }

    pub fn validate_create_sale(payload: &CreateSalePayload) -> Result<(), AppError> {
        if payload.items.is_empty() {
            return Err(AppError::Validation("Cart cannot be empty".to_string()));
        }
        for item in &payload.items {
            if item.product_id.trim().is_empty() {
                return Err(AppError::Validation("Product ID is required for each cart item".to_string()));
            }
            if item.quantity <= 0 {
                return Err(AppError::Validation("Item quantity must be greater than 0".to_string()));
            }
            if item.quantity > 100_000 {
                return Err(AppError::Validation("Item quantity exceeds maximum limit of 100,000".to_string()));
            }
            if item.unit_price_paise < 0 {
                return Err(AppError::Validation("Unit price cannot be negative".to_string()));
            }
            if let Some(disc) = item.discount_paise {
                if disc < 0 {
                    return Err(AppError::Validation("Item discount cannot be negative".to_string()));
                }
            }
        }
        if let Some(disc) = payload.discount_paise {
            if disc < 0 {
                return Err(AppError::Validation("Overall discount cannot be negative".to_string()));
            }
        }
        if payload.paid_paise < 0 {
            return Err(AppError::Validation("Paid amount cannot be negative".to_string()));
        }
        let pm = payload.payment_method.trim().to_uppercase();
        if pm != "CASH" && pm != "UPI" && pm != "CARD" && pm != "GIFT_CARD" {
            return Err(AppError::Validation(format!("Unsupported payment method '{}'", payload.payment_method)));
        }
        Ok(())
    }

    pub fn validate_update_sale(payload: &UpdateSalePayload) -> Result<(), AppError> {
        if payload.items.is_empty() {
            return Err(AppError::Validation("A bill must contain at least one item".to_string()));
        }
        for item in &payload.items {
            if item.product_id.trim().is_empty() {
                return Err(AppError::Validation("Product ID is required for each bill item".to_string()));
            }
            if item.quantity <= 0 {
                return Err(AppError::Validation("Item quantity must be greater than 0".to_string()));
            }
            if item.quantity > 100_000 {
                return Err(AppError::Validation("Item quantity exceeds maximum limit of 100,000".to_string()));
            }
            if item.unit_price_paise < 0 {
                return Err(AppError::Validation("Unit price cannot be negative".to_string()));
            }
            if let Some(disc) = item.discount_paise {
                if disc < 0 {
                    return Err(AppError::Validation("Item discount cannot be negative".to_string()));
                }
            }
        }
        if let Some(disc) = payload.discount_paise {
            if disc < 0 {
                return Err(AppError::Validation("Overall discount cannot be negative".to_string()));
            }
        }
        if payload.paid_paise < 0 {
            return Err(AppError::Validation("Paid amount cannot be negative".to_string()));
        }
        let pm = payload.payment_method.trim().to_uppercase();
        if pm != "CASH" && pm != "UPI" && pm != "CARD" && pm != "GIFT_CARD" {
            return Err(AppError::Validation(format!("Unsupported payment method '{}'", payload.payment_method)));
        }
        Ok(())
    }

    pub fn validate_item_unit_price(product_name: &str, unit_price_paise: i64, mrp_paise: i64) -> Result<(), AppError> {
        if unit_price_paise > mrp_paise {
            return Err(AppError::Validation(format!(
                "Unit price cannot exceed MRP for '{}'",
                product_name
            )));
        }
        Ok(())
    }

    pub fn validate_stock_adjustment(payload: &StockAdjustmentPayload) -> Result<(), AppError> {
        if payload.product_id.trim().is_empty() {
            return Err(AppError::Validation("Product ID is required for stock adjustment".to_string()));
        }
        if payload.quantity_change == 0 {
            return Err(AppError::Validation("Quantity change cannot be zero".to_string()));
        }
        if payload.reason.trim().is_empty() {
            return Err(AppError::Validation("Reason is required for stock adjustment".to_string()));
        }
        if let Some(note) = &payload.note {
            if note.trim().len() > 500 {
                return Err(AppError::Validation("Adjustment note cannot exceed 500 characters".to_string()));
            }
        }
        Ok(())
    }

    pub fn validate_password_change(current_pass: &str, new_pass: &str) -> Result<(), AppError> {
        let cur = current_pass.trim();
        let new_p = new_pass.trim();
        if cur.is_empty() || new_p.is_empty() {
            return Err(AppError::Validation("Current and new passwords are required".to_string()));
        }
        if new_p.len() < 4 {
            return Err(AppError::Validation("New password must be at least 4 characters long".to_string()));
        }
        if new_p.len() > 128 {
            return Err(AppError::Validation("New password cannot exceed 128 characters".to_string()));
        }
        Ok(())
    }
}
