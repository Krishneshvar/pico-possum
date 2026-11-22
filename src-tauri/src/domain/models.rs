use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct Category {
    pub id: String,
    pub name: String,
    #[serde(alias = "parent_id")]
    pub parent_id: Option<String>,
    #[serde(alias = "parent_name")]
    pub parent_name: Option<String>,
    #[serde(alias = "is_deleted")]
    pub is_deleted: bool,
    #[serde(alias = "created_at")]
    pub created_at: String,
    #[serde(alias = "updated_at")]
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateCategoryPayload {
    pub name: String,
    #[serde(alias = "parent_id")]
    pub parent_id: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateCategoryPayload {
    pub name: String,
    #[serde(alias = "parent_id")]
    pub parent_id: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct Product {
    pub id: String,
    pub name: String,
    pub sku: Option<String>,
    #[serde(alias = "category_id")]
    pub category_id: Option<String>,
    #[serde(alias = "category_name")]
    pub category_name: Option<String>,
    #[serde(alias = "mrp_cents", alias = "priceCents")]
    pub mrp_cents: i64,
    #[serde(alias = "cost_price_cents")]
    pub cost_price_cents: i64,
    #[serde(alias = "tax_rate")]
    pub tax_rate: f64,
    #[serde(alias = "current_stock")]
    pub current_stock: i64,
    #[serde(alias = "min_stock_alert")]
    pub min_stock_alert: i64,
    pub status: String,
    #[serde(alias = "is_deleted")]
    pub is_deleted: bool,
    #[serde(alias = "created_at")]
    pub created_at: String,
    #[serde(alias = "updated_at")]
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateProductPayload {
    pub name: String,
    pub sku: Option<String>,
    #[serde(alias = "category_id")]
    pub category_id: Option<String>,
    #[serde(alias = "mrp_cents", alias = "priceCents")]
    pub mrp_cents: i64,
    #[serde(alias = "cost_price_cents")]
    pub cost_price_cents: Option<i64>,
    #[serde(alias = "tax_rate")]
    pub tax_rate: Option<f64>,
    #[serde(alias = "initial_stock")]
    pub initial_stock: Option<i64>,
    #[serde(alias = "min_stock_alert")]
    pub min_stock_alert: Option<i64>,
    pub status: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateProductPayload {
    pub name: String,
    pub sku: Option<String>,
    #[serde(alias = "category_id")]
    pub category_id: Option<String>,
    #[serde(alias = "mrp_cents", alias = "priceCents")]
    pub mrp_cents: i64,
    #[serde(alias = "cost_price_cents")]
    pub cost_price_cents: i64,
    #[serde(alias = "tax_rate")]
    pub tax_rate: f64,
    #[serde(alias = "min_stock_alert")]
    pub min_stock_alert: Option<i64>,
    pub status: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct Customer {
    pub id: String,
    pub name: String,
    pub phone: Option<String>,
    pub address: Option<String>,
    #[serde(alias = "is_deleted")]
    pub is_deleted: bool,
    #[serde(alias = "created_at")]
    pub created_at: String,
    #[serde(alias = "updated_at")]
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateCustomerPayload {
    pub name: String,
    pub phone: Option<String>,
    pub address: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateCustomerPayload {
    pub name: String,
    pub phone: Option<String>,
    pub address: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct Sale {
    pub id: String,
    #[serde(alias = "invoice_number")]
    pub invoice_number: String,
    #[serde(alias = "customer_id")]
    pub customer_id: Option<String>,
    #[serde(alias = "customer_name")]
    pub customer_name: Option<String>,
    #[serde(alias = "customer_phone")]
    pub customer_phone: Option<String>,
    #[serde(alias = "biller_name")]
    pub biller_name: Option<String>,
    #[serde(alias = "subtotal_cents")]
    pub subtotal_cents: i64,
    #[serde(alias = "discount_cents")]
    pub discount_cents: i64,
    #[serde(alias = "tax_cents")]
    pub tax_cents: i64,
    #[serde(alias = "total_cents")]
    pub total_cents: i64,
    #[serde(alias = "paid_cents")]
    pub paid_cents: i64,
    #[serde(alias = "payment_method")]
    pub payment_method: String,
    pub status: String,
    #[serde(alias = "fulfillment_status")]
    pub fulfillment_status: String,
    pub notes: Option<String>,
    #[serde(alias = "created_at")]
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct SaleItem {
    pub id: String,
    #[serde(alias = "sale_id")]
    pub sale_id: String,
    #[serde(alias = "product_id")]
    pub product_id: String,
    #[serde(alias = "product_name")]
    pub product_name: String,
    #[serde(alias = "product_sku")]
    pub product_sku: Option<String>,
    pub quantity: i64,
    #[serde(alias = "unit_price_cents")]
    pub unit_price_cents: i64,
    #[serde(alias = "cost_price_cents")]
    pub cost_price_cents: i64,
    #[serde(alias = "discount_cents")]
    pub discount_cents: i64,
    #[serde(alias = "tax_rate")]
    pub tax_rate: f64,
    #[serde(alias = "tax_cents")]
    pub tax_cents: i64,
    #[serde(alias = "total_cents")]
    pub total_cents: i64,
    #[serde(default, alias = "item_order")]
    pub item_order: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateSaleItemPayload {
    #[serde(alias = "product_id")]
    pub product_id: String,
    pub quantity: i64,
    #[serde(alias = "unit_price_cents")]
    pub unit_price_cents: i64,
    #[serde(alias = "discount_cents")]
    pub discount_cents: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateSalePayload {
    #[serde(alias = "customer_id")]
    pub customer_id: Option<String>,
    #[serde(alias = "customer_name")]
    pub customer_name: Option<String>,
    #[serde(alias = "customer_phone")]
    pub customer_phone: Option<String>,
    #[serde(alias = "customer_address")]
    pub customer_address: Option<String>,
    pub items: Vec<CreateSaleItemPayload>,
    #[serde(alias = "discount_cents")]
    pub discount_cents: Option<i64>,
    #[serde(alias = "payment_method")]
    pub payment_method: String,
    #[serde(alias = "paid_cents")]
    pub paid_cents: i64,
    pub notes: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateSaleItemPayload {
    #[serde(alias = "product_id")]
    pub product_id: String,
    pub quantity: i64,
    #[serde(alias = "unit_price_cents")]
    pub unit_price_cents: i64,
    #[serde(alias = "discount_cents")]
    pub discount_cents: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateSalePayload {
    #[serde(alias = "sale_id")]
    pub sale_id: String,
    #[serde(alias = "customer_id")]
    pub customer_id: Option<String>,
    #[serde(alias = "customer_name")]
    pub customer_name: Option<String>,
    #[serde(alias = "customer_phone")]
    pub customer_phone: Option<String>,
    #[serde(alias = "customer_address")]
    pub customer_address: Option<String>,
    pub items: Vec<UpdateSaleItemPayload>,
    #[serde(alias = "discount_cents")]
    pub discount_cents: Option<i64>,
    #[serde(alias = "payment_method")]
    pub payment_method: String,
    #[serde(alias = "paid_cents")]
    pub paid_cents: i64,
    pub notes: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SaleResponse {
    pub sale: Sale,
    pub items: Vec<SaleItem>,
    #[serde(alias = "change_cents")]
    pub change_cents: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct StockMovement {
    pub id: String,
    #[serde(alias = "product_id")]
    pub product_id: String,
    #[serde(alias = "product_name")]
    pub product_name: Option<String>,
    #[serde(alias = "product_sku")]
    pub product_sku: Option<String>,
    #[serde(alias = "quantity_change")]
    pub quantity_change: i64,
    #[serde(alias = "movement_type")]
    pub movement_type: String,
    #[serde(alias = "reference_id")]
    pub reference_id: Option<String>,
    pub note: Option<String>,
    #[serde(alias = "created_at")]
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StockAdjustmentPayload {
    #[serde(alias = "product_id")]
    pub product_id: String,
    #[serde(alias = "quantity_change")]
    pub quantity_change: i64,
    pub reason: String,
    pub note: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct AuditLog {
    pub id: String,
    #[serde(alias = "event_type")]
    pub event_type: String,
    #[serde(alias = "entity_type")]
    pub entity_type: String,
    #[serde(alias = "entity_id")]
    pub entity_id: String,
    pub description: String,
    #[serde(alias = "created_at")]
    pub created_at: String,
}

pub use crate::domain::app_models::*;




