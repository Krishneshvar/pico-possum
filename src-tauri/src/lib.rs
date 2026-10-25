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

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(pool)
        .invoke_handler(tauri::generate_handler![
            // Products
            commands::products::get_products,
            commands::products::get_product_by_id,
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
            commands::sales::get_sales,
            commands::sales::get_sale_details,
            commands::sales::cancel_sale,
            commands::sales::save_open_bill,
            commands::sales::load_open_bills,
            commands::sales::delete_open_bill,
            // Customers
            commands::customers::get_customers,
            commands::customers::create_customer,
            // Reports & Insights
            commands::reports::get_sales_report_summary,
            commands::reports::get_top_selling_products,
            commands::reports::get_stock_movements,
            commands::reports::get_audit_logs,
            // Settings
            commands::settings::get_general_settings,
            commands::settings::update_general_settings,
            commands::settings::get_bill_settings,
            commands::settings::update_bill_settings,
            // Legacy Migration
            commands::migration::detect_legacy_database,
            commands::migration::import_legacy_database,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
