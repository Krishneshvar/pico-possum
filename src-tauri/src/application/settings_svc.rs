use sqlx::{Row, SqlitePool};

use crate::domain::models::{BillSettings, GeneralSettings};
use crate::errors::AppError;
use crate::infrastructure::logger::AppLogger;

pub struct SettingsService;

impl SettingsService {
    pub async fn get_general_settings(pool: &SqlitePool) -> Result<GeneralSettings, AppError> {
        let row = sqlx::query("SELECT value FROM app_settings WHERE key = 'general_settings'")
            .fetch_optional(pool)
            .await?;

        if let Some(r) = row {
            let val: String = r.get("value");
            match serde_json::from_str::<GeneralSettings>(&val) {
                Ok(s) => Ok(s),
                Err(err) => {
                    AppLogger::warn("SettingsService", format!("Failed to parse general_settings JSON: {}. Using default settings.", err));
                    Ok(GeneralSettings::default())
                }
            }
        } else {
            Ok(GeneralSettings::default())
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
            match serde_json::from_str::<BillSettings>(&val) {
                Ok(mut s) => {
                    if (s.store_name.is_empty() || s.phone.is_empty()) && s.sections.is_some() {
                        if let Some(ref sec_vec) = s.sections {
                            for sec in sec_vec {
                                if let Some(opts) = sec.get("options") {
                                    if s.store_name.is_empty() {
                                        if let Some(sn) = opts.get("storeName").and_then(|v| v.as_str()) {
                                            s.store_name = sn.to_string();
                                        }
                                    }
                                    if s.phone.is_empty() {
                                        if let Some(ph) = opts.get("phone").and_then(|v| v.as_str()) {
                                            s.phone = ph.to_string();
                                        }
                                    }
                                    if s.store_details.is_empty() {
                                        if let Some(sd) = opts.get("storeDetails").and_then(|v| v.as_str()) {
                                            s.store_details = sd.to_string();
                                        }
                                    }
                                    if s.gst.is_empty() {
                                        if let Some(gst) = opts.get("gst").and_then(|v| v.as_str()) {
                                            s.gst = gst.to_string();
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Ok(s)
                }
                Err(err) => {
                    AppLogger::warn("SettingsService", format!("Failed to parse bill_settings JSON: {}. Using default settings.", err));
                    Ok(BillSettings::default())
                }
            }
        } else {
            Ok(BillSettings::default())
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
