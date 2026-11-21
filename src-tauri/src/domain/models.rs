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
    #[serde(alias = "mrp_paise")]
    pub mrp_paise: i64,
    #[serde(alias = "cost_price_paise")]
    pub cost_price_paise: i64,
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
    #[serde(alias = "mrp_paise")]
    pub mrp_paise: i64,
    #[serde(alias = "cost_price_paise")]
    pub cost_price_paise: Option<i64>,
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
    #[serde(alias = "mrp_paise")]
    pub mrp_paise: i64,
    #[serde(alias = "cost_price_paise")]
    pub cost_price_paise: i64,
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
    #[serde(alias = "subtotal_paise")]
    pub subtotal_paise: i64,
    #[serde(alias = "discount_paise")]
    pub discount_paise: i64,
    #[serde(alias = "tax_paise")]
    pub tax_paise: i64,
    #[serde(alias = "total_paise")]
    pub total_paise: i64,
    #[serde(alias = "paid_paise")]
    pub paid_paise: i64,
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
    #[serde(alias = "unit_price_paise")]
    pub unit_price_paise: i64,
    #[serde(alias = "cost_price_paise")]
    pub cost_price_paise: i64,
    #[serde(alias = "discount_paise")]
    pub discount_paise: i64,
    #[serde(alias = "tax_rate")]
    pub tax_rate: f64,
    #[serde(alias = "tax_paise")]
    pub tax_paise: i64,
    #[serde(alias = "total_paise")]
    pub total_paise: i64,
    #[serde(default, alias = "item_order")]
    pub item_order: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateSaleItemPayload {
    #[serde(alias = "product_id")]
    pub product_id: String,
    pub quantity: i64,
    #[serde(alias = "unit_price_paise")]
    pub unit_price_paise: i64,
    #[serde(alias = "discount_paise")]
    pub discount_paise: Option<i64>,
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
    #[serde(alias = "discount_paise")]
    pub discount_paise: Option<i64>,
    #[serde(alias = "payment_method")]
    pub payment_method: String,
    #[serde(alias = "paid_paise")]
    pub paid_paise: i64,
    pub notes: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateSaleItemPayload {
    #[serde(alias = "product_id")]
    pub product_id: String,
    pub quantity: i64,
    #[serde(alias = "unit_price_paise")]
    pub unit_price_paise: i64,
    #[serde(alias = "discount_paise")]
    pub discount_paise: Option<i64>,
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
    #[serde(alias = "discount_paise")]
    pub discount_paise: Option<i64>,
    #[serde(alias = "payment_method")]
    pub payment_method: String,
    #[serde(alias = "paid_paise")]
    pub paid_paise: i64,
    pub notes: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SaleResponse {
    pub sale: Sale,
    pub items: Vec<SaleItem>,
    #[serde(alias = "change_paise")]
    pub change_paise: i64,
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

fn default_auto_numeric_sku() -> bool {
    true
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GeneralSettings {
    #[serde(alias = "store_name")]
    pub store_name: String,
    pub phone: String,
    pub address: String,
    pub gstin: String,
    #[serde(alias = "currency_symbol")]
    pub currency_symbol: String,
    #[serde(alias = "currency_code")]
    pub currency_code: String,
    #[serde(alias = "date_format")]
    pub date_format: String,
    #[serde(alias = "time_format")]
    pub time_format: String,
    #[serde(alias = "default_printer_name")]
    pub default_printer_name: String,
    /// When enabled, newly created products receive the next available numerical
    /// SKU automatically. Defaults to true for existing installations that do
    /// not have this field persisted yet.
    #[serde(default = "default_auto_numeric_sku", alias = "auto_numeric_sku")]
    pub auto_numeric_sku: bool,
    /// When enabled, stock alerts, warnings and billing restrictions are bypassed
    /// and stock is allowed to go negative.
    #[serde(default, alias = "allow_negative_stock")]
    pub allow_negative_stock: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BillSettings {
    #[serde(alias = "paper_width")]
    pub paper_width: String,
    #[serde(alias = "store_name")]
    pub store_name: String,
    #[serde(alias = "store_details")]
    pub store_details: String,
    pub phone: String,
    pub gst: String,
    #[serde(alias = "show_tax")]
    pub show_tax: bool,
    #[serde(alias = "footer_text")]
    pub footer_text: String,
    #[serde(default)]
    pub sections: Option<Vec<serde_json::Value>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BackupInfo {
    pub filename: String,
    #[serde(alias = "file_path")]
    pub file_path: String,
    #[serde(alias = "size_bytes")]
    pub size_bytes: u64,
    #[serde(alias = "created_at")]
    pub created_at: String,
    #[serde(alias = "backup_type")]
    pub backup_type: String, // "auto" | "manual" | "pre-restore" | "exit"
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BackupStatus {
    #[serde(alias = "backup_location")]
    pub backup_location: String,
    #[serde(alias = "latest_backup")]
    pub latest_backup: Option<BackupInfo>,
    #[serde(alias = "total_backups")]
    pub total_backups: usize,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LegacyMigrationSummary {
    #[serde(alias = "source_path")]
    pub source_path: String,
    #[serde(alias = "categories_imported")]
    pub categories_imported: usize,
    #[serde(alias = "products_imported")]
    pub products_imported: usize,
    #[serde(alias = "customers_imported")]
    pub customers_imported: usize,
    #[serde(alias = "sales_imported")]
    pub sales_imported: usize,
    #[serde(alias = "sale_items_imported")]
    pub sale_items_imported: usize,
    #[serde(alias = "stock_movements_imported")]
    pub stock_movements_imported: usize,
    #[serde(alias = "settings_imported")]
    pub settings_imported: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SalesReportSummary {
    #[serde(alias = "total_revenue_paise")]
    pub total_revenue_paise: i64,
    #[serde(alias = "total_discount_paise")]
    pub total_discount_paise: i64,
    #[serde(alias = "total_tax_paise")]
    pub total_tax_paise: i64,
    #[serde(alias = "total_sales_count")]
    pub total_sales_count: i64,
    #[serde(alias = "cash_revenue_paise")]
    pub cash_revenue_paise: i64,
    #[serde(alias = "card_revenue_paise")]
    pub card_revenue_paise: i64,
    #[serde(alias = "upi_revenue_paise")]
    pub upi_revenue_paise: i64,
    #[serde(alias = "gift_card_revenue_paise")]
    pub gift_card_revenue_paise: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TopSellingProduct {
    #[serde(alias = "product_id")]
    pub product_id: String,
    #[serde(alias = "product_name")]
    pub product_name: String,
    #[serde(default)]
    pub sku: Option<String>,
    #[serde(alias = "total_quantity")]
    pub total_quantity: i64,
    #[serde(alias = "total_revenue_paise")]
    pub total_revenue_paise: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TopSellingCategory {
    #[serde(alias = "category_id")]
    pub category_id: String,
    #[serde(alias = "category_name")]
    pub category_name: String,
    #[serde(alias = "distinct_products_sold")]
    pub distinct_products_sold: i64,
    #[serde(alias = "total_quantity")]
    pub total_quantity: i64,
    #[serde(alias = "total_revenue_paise")]
    pub total_revenue_paise: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct MultiYearSalesPoint {
    pub year: String,
    pub month: String,
    #[serde(alias = "total_revenue_paise")]
    pub total_revenue_paise: i64,
    #[serde(alias = "total_transactions")]
    pub total_transactions: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct SalesBreakdownItem {
    pub period: String,
    #[serde(alias = "total_transactions")]
    pub total_transactions: i64,
    #[serde(alias = "total_sales_paise")]
    pub total_sales_paise: i64,
    #[serde(alias = "total_discount_paise")]
    pub total_discount_paise: i64,
    #[serde(alias = "cash_paise")]
    pub cash_paise: i64,
    #[serde(alias = "upi_paise")]
    pub upi_paise: i64,
    #[serde(alias = "card_paise")]
    pub card_paise: i64,
    #[serde(alias = "gift_card_paise")]
    pub gift_card_paise: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct ProductFlowItem {
    pub id: String,
    #[serde(alias = "product_id")]
    pub product_id: String,
    #[serde(alias = "product_name")]
    pub product_name: String,
    #[serde(alias = "product_sku")]
    pub product_sku: Option<String>,
    #[serde(alias = "invoice_number")]
    pub invoice_number: Option<String>,
    #[serde(alias = "customer_name")]
    pub customer_name: Option<String>,
    #[serde(alias = "payment_method")]
    pub payment_method: Option<String>,
    #[serde(alias = "event_type")]
    pub event_type: String,
    pub quantity: i64,
    #[serde(alias = "unit_price_paise")]
    pub unit_price_paise: i64,
    #[serde(alias = "total_paise")]
    pub total_paise: i64,
    #[serde(alias = "created_at")]
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CsvImportResult {
    #[serde(alias = "total_rows")]
    pub total_rows: usize,
    pub imported: usize,
    pub skipped: usize,
    pub errors: Vec<String>,
}

// ── Identity & Authentication ────────────────────────────────────────────────

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AuthUser {
    pub id: String,
    pub name: String,
    pub username: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AuthStateResponse {
    #[serde(alias = "requires_setup")]
    pub requires_setup: bool,
    #[serde(alias = "is_authenticated")]
    pub is_authenticated: bool,
    pub user: Option<AuthUser>,
    #[serde(alias = "session_token")]
    pub session_token: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RegisterAdminRequest {
    pub name: String,
    pub username: String,
    pub password: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LoginRequest {
    pub username: String,
    pub password: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdatePasswordRequest {
    #[serde(alias = "current_password")]
    pub current_password: String,
    #[serde(alias = "new_password")]
    pub new_password: String,
}



