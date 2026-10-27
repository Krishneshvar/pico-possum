pub mod application;
pub mod commands;
pub mod domain;
pub mod errors;
pub mod infrastructure;

use std::sync::atomic::{AtomicBool, Ordering};

use sqlx::SqlitePool;
use tauri::{Manager, WindowEvent};

use application::BackupService;

/// Guards the shutdown backup so it only runs once, no matter how the app is
/// closed (window close button, app quit, OS session end, ...).
#[derive(Default)]
struct ShutdownState {
    backup_started: AtomicBool,
    backup_finished: AtomicBool,
}

impl ShutdownState {
    /// Returns `true` only for the first caller.
    fn begin(&self) -> bool {
        !self.backup_started.swap(true, Ordering::SeqCst)
    }

    fn finish(&self) {
        self.backup_finished.store(true, Ordering::SeqCst);
    }

    fn is_finished(&self) -> bool {
        self.backup_finished.load(Ordering::SeqCst)
    }
}

/// Takes a backup of the live database. Failures are logged but never block
/// the app from closing.
async fn backup_before_shutdown<R: tauri::Runtime>(app: &tauri::AppHandle<R>) {
    let pool = app.state::<SqlitePool>();
    if let Err(err) = BackupService::create_exit_backup(&pool).await {
        eprintln!("[pico-possum] Failed to create backup before shutdown: {err}");
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let pool = tauri::async_runtime::block_on(async {
        infrastructure::database::init_pool()
            .await
            .expect("Failed to initialize SQLite database pool")
    });

    let session_mgr = application::AuthSessionManager::new();

    let app = tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(pool)
        .manage(session_mgr)
        .manage(ShutdownState::default())
        .invoke_handler(tauri::generate_handler![
            // Authentication & Identity
            commands::auth::get_auth_state,
            commands::auth::get_admin_username,
            commands::auth::register_admin,
            commands::auth::login,
            commands::auth::logout,
            commands::auth::update_password,
            // Products
            commands::products::get_products,
            commands::products::get_product_by_id,
            commands::products::get_next_sku,
            commands::products::create_product,
            commands::products::update_product,
            commands::products::delete_product,
            commands::products::adjust_stock,
            // Categories
            commands::categories::get_categories,
            commands::categories::create_category,
            commands::categories::update_category,
            commands::categories::delete_category,
            // Sales & POS
            commands::sales::process_sale,
            commands::sales::update_sale,
            commands::sales::get_sales,
            commands::sales::get_sale_details,
            commands::sales::cancel_sale,
            commands::sales::save_open_bill,
            commands::sales::load_open_bills,
            commands::sales::delete_open_bill,
            // Customers
            commands::customers::get_customers,
            commands::customers::create_customer,
            commands::customers::update_customer,
            commands::customers::delete_customer,
            commands::customers::get_customer_sales,
            // Reports & Insights
            commands::reports::get_sales_report_summary,
            commands::reports::get_sales_breakdown,
            commands::reports::get_product_flow,
            commands::reports::get_top_selling_products,
            commands::reports::get_stock_movements,
            commands::reports::get_audit_logs,
            // Settings
            commands::settings::get_general_settings,
            commands::settings::update_general_settings,
            commands::settings::get_bill_settings,
            commands::settings::update_bill_settings,
            commands::settings::list_printers,
            commands::settings::test_print,
            commands::settings::get_backup_status,
            commands::settings::list_backups,
            commands::settings::create_manual_backup,
            commands::settings::restore_backup,
            commands::settings::restore_backup_file,
            commands::settings::open_backup_folder,
            // Legacy Migration

            commands::migration::detect_legacy_database,
            commands::migration::import_legacy_database,
            // CSV Imports
            commands::csv_import::import_csv_categories,
            commands::csv_import::import_csv_products,
            commands::csv_import::import_csv_customers,
            commands::csv_import::import_csv_bills,
        ])
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                let state = window.state::<ShutdownState>();

                // A close was already requested and the backup is running.
                if !state.begin() {
                    api.prevent_close();
                    return;
                }

                api.prevent_close();

                let app = window.app_handle().clone();
                let window = window.clone();
                tauri::async_runtime::spawn(async move {
                    backup_before_shutdown(&app).await;
                    app.state::<ShutdownState>().finish();
                    // `destroy` skips the close-requested event, so this
                    // cannot loop back into the handler above.
                    let _ = window.destroy();
                });
            }
        })
        .build(tauri::generate_context!())
        .expect("error while building tauri application");

    app.run(|app_handle, event| {
        // Catches quits that don't go through a window close (Cmd+Q, tray
        // quit, OS shutdown, ...). Window closes are handled above.
        if let tauri::RunEvent::ExitRequested { api, .. } = event {
            let state = app_handle.state::<ShutdownState>();
            if !state.begin() {
                // A backup is already running (started by a window close).
                // Hold the exit until it completes.
                if !state.is_finished() {
                    api.prevent_exit();
                }
                return;
            }

            api.prevent_exit();

            let app_handle = app_handle.clone();
            tauri::async_runtime::spawn(async move {
                backup_before_shutdown(&app_handle).await;
                app_handle.state::<ShutdownState>().finish();
                app_handle.exit(0);
            });
        }
    });
}
