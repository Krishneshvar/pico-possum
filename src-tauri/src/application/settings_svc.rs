use sqlx::{Row, SqlitePool};

use crate::domain::models::{BillSettings, GeneralSettings};
use crate::errors::AppError;

pub struct SettingsService;

impl SettingsService {
    pub async fn get_general_settings(pool: &SqlitePool) -> Result<GeneralSettings, AppError> {
        let row = sqlx::query("SELECT value FROM app_settings WHERE key = 'general_settings'")
            .fetch_optional(pool)
            .await?;

        if let Some(r) = row {
            let val: String = r.get("value");
            let s: GeneralSettings = serde_json::from_str(&val)?;
            Ok(s)
        } else {
            Ok(GeneralSettings {
                store_name: "Pico POS".to_string(),
                phone: "".to_string(),
                address: "".to_string(),
                gstin: "".to_string(),
                currency_symbol: "₹".to_string(),
                currency_code: "INR".to_string(),
                date_format: "DD/MM/YYYY".to_string(),
                time_format: "12h".to_string(),
                default_printer_name: "".to_string(),
                auto_numeric_sku: true,
                allow_negative_stock: false,
            })
        }
    }

    pub async fn update_general_settings(
        pool: &SqlitePool,
        settings: GeneralSettings,
    ) -> Result<(), AppError> {
        let val = serde_json::to_string(&settings)?;
        sqlx::query(
            "INSERT INTO app_settings (key, value, updated_at) VALUES ('general_settings', ?, CURRENT_TIMESTAMP)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = CURRENT_TIMESTAMP"
        )
        .bind(val)
        .execute(pool)
        .await?;

        Ok(())
    }

    pub async fn get_bill_settings(pool: &SqlitePool) -> Result<BillSettings, AppError> {
        let row = sqlx::query("SELECT value FROM app_settings WHERE key = 'bill_settings'")
            .fetch_optional(pool)
            .await?;

        if let Some(r) = row {
            let val: String = r.get("value");
            let s: BillSettings = serde_json::from_str(&val)?;
            Ok(s)
        } else {
            Ok(BillSettings {
                paper_width: "80mm".to_string(),
                store_name: "Pico POS".to_string(),
                store_details: "Retail".to_string(),
                phone: "".to_string(),
                gst: "".to_string(),
                show_tax: false,
                footer_text: "Thank you for shopping!".to_string(),
                sections: None,
            })
        }
    }

    pub async fn update_bill_settings(
        pool: &SqlitePool,
        settings: BillSettings,
    ) -> Result<(), AppError> {
        let val = serde_json::to_string(&settings)?;
        sqlx::query(
            "INSERT INTO app_settings (key, value, updated_at) VALUES ('bill_settings', ?, CURRENT_TIMESTAMP)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = CURRENT_TIMESTAMP"
        )
        .bind(val)
        .execute(pool)
        .await?;

        Ok(())
    }
}
