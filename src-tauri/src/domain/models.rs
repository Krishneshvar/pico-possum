use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Category {
    pub id: String,
    pub name: String,
    pub parent_id: Option<String>,
    pub parent_name: Option<String>,
    pub is_deleted: bool,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateCategoryPayload {
    pub name: String,
    pub parent_id: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateCategoryPayload {
    pub name: String,
    pub parent_id: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Product {
    pub id: String,
    pub name: String,
    pub sku: Option<String>,
    pub category_id: Option<String>,
    pub category_name: Option<String>,
    pub mrp_paise: i64,
    pub cost_price_paise: i64,
    pub tax_rate: f64,
    pub current_stock: i64,
    pub min_stock_alert: i64,
    pub status: String,
    pub is_deleted: bool,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateProductPayload {
    pub name: String,
    pub sku: Option<String>,
    pub category_id: Option<String>,
    pub mrp_paise: i64,
    pub cost_price_paise: Option<i64>,
    pub tax_rate: Option<f64>,
    pub initial_stock: Option<i64>,
    pub min_stock_alert: Option<i64>,
    pub status: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateProductPayload {
    pub name: String,
    pub sku: Option<String>,
    pub category_id: Option<String>,
    pub mrp_paise: i64,
    pub cost_price_paise: i64,
    pub tax_rate: f64,
    pub min_stock_alert: Option<i64>,
    pub status: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Customer {
    pub id: String,
    pub name: String,
    pub phone: Option<String>,
    pub address: Option<String>,
    pub is_deleted: bool,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateCustomerPayload {
    pub name: String,
    pub phone: Option<String>,
    pub address: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Sale {
    pub id: String,
    pub invoice_number: String,
    pub customer_id: Option<String>,
    pub customer_name: Option<String>,
    pub customer_phone: Option<String>,
    pub biller_name: Option<String>,
    pub subtotal_paise: i64,
    pub discount_paise: i64,
    pub tax_paise: i64,
    pub total_paise: i64,
    pub paid_paise: i64,
    pub payment_method: String,
    pub status: String,
    pub fulfillment_status: String,
    pub notes: Option<String>,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SaleItem {
    pub id: String,
    pub sale_id: String,
    pub product_id: String,
    pub product_name: String,
    pub product_sku: Option<String>,
    pub quantity: i64,
    pub unit_price_paise: i64,
    pub cost_price_paise: i64,
    pub discount_paise: i64,
    pub tax_rate: f64,
    pub tax_paise: i64,
    pub total_paise: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateSaleItemPayload {
    pub product_id: String,
    pub quantity: i64,
    pub unit_price_paise: i64,
    pub discount_paise: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateSalePayload {
    pub customer_id: Option<String>,
    pub customer_name: Option<String>,
    pub customer_phone: Option<String>,
    pub customer_address: Option<String>,
    pub items: Vec<CreateSaleItemPayload>,
    pub discount_paise: Option<i64>,
    pub payment_method: String,
    pub paid_paise: i64,
    pub notes: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SaleResponse {
    pub sale: Sale,
    pub items: Vec<SaleItem>,
    pub change_paise: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct StockMovement {
    pub id: String,
    pub product_id: String,
    pub product_name: Option<String>,
    pub quantity_change: i64,
    pub movement_type: String,
    pub reference_id: Option<String>,
    pub note: Option<String>,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StockAdjustmentPayload {
    pub product_id: String,
    pub quantity_change: i64,
    pub reason: String,
    pub note: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct AuditLog {
    pub id: String,
    pub event_type: String,
    pub entity_type: String,
    pub entity_id: String,
    pub description: String,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GeneralSettings {
    pub store_name: String,
    pub phone: String,
    pub address: String,
    pub gstin: String,
    pub currency_symbol: String,
    pub currency_code: String,
    pub date_format: String,
    pub time_format: String,
    pub default_printer_name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BillSettings {
    pub paper_width: String,
    pub store_name: String,
    pub store_details: String,
    pub phone: String,
    pub gst: String,
    pub show_tax: bool,
    pub footer_text: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LegacyMigrationSummary {
    pub source_path: String,
    pub categories_imported: usize,
    pub products_imported: usize,
    pub customers_imported: usize,
    pub sales_imported: usize,
    pub sale_items_imported: usize,
    pub stock_movements_imported: usize,
    pub settings_imported: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SalesReportSummary {
    pub total_revenue_paise: i64,
    pub total_discount_paise: i64,
    pub total_tax_paise: i64,
    pub total_sales_count: i64,
    pub cash_revenue_paise: i64,
    pub card_revenue_paise: i64,
    pub upi_revenue_paise: i64,
    pub other_revenue_paise: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TopSellingProduct {
    pub product_id: String,
    pub product_name: String,
    pub total_quantity: i64,
    pub total_revenue_paise: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SalesBreakdownItem {
    pub period: String,
    pub total_transactions: i64,
    pub total_sales_paise: i64,
    pub total_discount_paise: i64,
    pub cash_paise: i64,
    pub upi_paise: i64,
    pub card_paise: i64,
    pub other_paise: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct ProductFlowItem {
    pub id: String,
    pub product_id: String,
    pub product_name: String,
    pub product_sku: Option<String>,
    pub invoice_number: Option<String>,
    pub customer_name: Option<String>,
    pub payment_method: Option<String>,
    pub event_type: String,
    pub quantity: i64,
    pub unit_price_paise: i64,
    pub total_paise: i64,
    pub created_at: String,
}

