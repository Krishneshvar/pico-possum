use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default, rename_all = "camelCase")]
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
    #[serde(alias = "auto_numeric_sku")]
    pub auto_numeric_sku: bool,
    #[serde(alias = "allow_negative_stock")]
    pub allow_negative_stock: bool,
}

impl Default for GeneralSettings {
    fn default() -> Self {
        Self {
            store_name: "Pico POS".to_string(),
            phone: "".to_string(),
            address: "".to_string(),
            gstin: "".to_string(),
            currency_symbol: "$".to_string(),
            currency_code: "USD".to_string(),
            date_format: "DD/MM/YYYY".to_string(),
            time_format: "12h".to_string(),
            default_printer_name: "".to_string(),
            auto_numeric_sku: true,
            allow_negative_stock: false,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default, rename_all = "camelCase")]
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
    pub sections: Option<Vec<serde_json::Value>>,
}

impl Default for BillSettings {
    fn default() -> Self {
        Self {
            paper_width: "80mm".to_string(),
            store_name: "Pico POS".to_string(),
            store_details: "Retail".to_string(),
            phone: "".to_string(),
            gst: "".to_string(),
            show_tax: false,
            footer_text: "Thank you for shopping!".to_string(),
            sections: None,
        }
    }
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
    #[serde(alias = "total_revenue_cents")]
    pub total_revenue_cents: i64,
    #[serde(alias = "total_discount_cents")]
    pub total_discount_cents: i64,
    #[serde(alias = "total_tax_cents")]
    pub total_tax_cents: i64,
    #[serde(alias = "total_sales_count")]
    pub total_sales_count: i64,
    #[serde(alias = "cash_revenue_cents")]
    pub cash_revenue_cents: i64,
    #[serde(alias = "card_revenue_cents")]
    pub card_revenue_cents: i64,
    #[serde(alias = "upi_revenue_cents")]
    pub upi_revenue_cents: i64,
    #[serde(alias = "gift_card_revenue_cents")]
    pub gift_card_revenue_cents: i64,
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
    #[serde(alias = "total_revenue_cents")]
    pub total_revenue_cents: i64,
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
    #[serde(alias = "total_revenue_cents")]
    pub total_revenue_cents: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct MultiYearSalesPoint {
    pub year: String,
    pub month: String,
    #[serde(alias = "total_revenue_cents")]
    pub total_revenue_cents: i64,
    #[serde(alias = "total_transactions")]
    pub total_transactions: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct SalesBreakdownItem {
    pub period: String,
    #[serde(alias = "total_transactions")]
    pub total_transactions: i64,
    #[serde(alias = "total_sales_cents")]
    pub total_sales_cents: i64,
    #[serde(alias = "total_discount_cents")]
    pub total_discount_cents: i64,
    #[serde(alias = "cash_cents")]
    pub cash_cents: i64,
    #[serde(alias = "upi_cents")]
    pub upi_cents: i64,
    #[serde(alias = "card_cents")]
    pub card_cents: i64,
    #[serde(alias = "gift_card_cents")]
    pub gift_card_cents: i64,
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
    #[serde(alias = "unit_price_cents")]
    pub unit_price_cents: i64,
    #[serde(alias = "total_cents")]
    pub total_cents: i64,
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
