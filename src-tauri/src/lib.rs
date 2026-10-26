pub mod application;
pub mod commands;
pub mod domain;
pub mod errors;
pub mod infrastructure;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let pool = tauri::async_runtime::block_on(async {
        infrastructure::database::init_pool()
            .await
            .expect("Failed to initialize SQLite database pool")
    });

    let session_mgr = application::AuthSessionManager::new();

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(pool)
        .manage(session_mgr)
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
        .run(tauri::generate_context!())
        .expect("error while running tauri application");

}
