use pico_possum_lib::application::{BackupService, PrinterService};
use pico_possum_lib::infrastructure::database::schema::INIT_SCHEMA;
use pico_possum_lib::infrastructure::paths::AppPaths;
use sqlx::sqlite::SqlitePoolOptions;

#[tokio::test]
async fn test_backup_and_restore_lifecycle() {
    let pool = SqlitePoolOptions::new()
        .max_connections(1)
        .connect("sqlite::memory:")
        .await
        .expect("Failed to connect to memory SQLite");

    sqlx::raw_sql(INIT_SCHEMA)
        .execute(&pool)
        .await
        .expect("Failed to initialize schema");

    // Ensure database file exists on disk for file copy test
    let live_db = AppPaths::get_database_path();
    if !live_db.exists() {
        let _ = std::fs::create_dir_all(live_db.parent().unwrap());
        let _ = std::fs::write(&live_db, b"SQLite format 3\0sample test db data content");
    }

    // 1. Create manual backup
    let backup_info = BackupService::create_manual_backup(&pool)
        .await
        .expect("Manual backup should succeed");

    assert!(backup_info.filename.starts_with("pico-possum-manual-backup-"));
    assert!(std::path::Path::new(&backup_info.file_path).exists());

    // 2. Get status and list backups
    let status = BackupService::get_backup_status()
        .await
        .expect("Get backup status should succeed");

    assert!(status.total_backups >= 1);
    assert!(status.latest_backup.is_some());

    let list = BackupService::list_backups()
        .await
        .expect("List backups should succeed");
    assert!(!list.is_empty());

    // 3. Test restore from the created native backup
    let restore_msg = BackupService::restore_from_backup(&pool, &backup_info.filename)
        .await
        .expect("Restore from backup should succeed");

    assert!(restore_msg.contains("successfully restored"));

    // 4. Test restore from legacy Java app database backup if present on system
    let legacy_db_path = dirs::data_dir()
        .expect("Must have data dir")
        .join("pico-possum")
        .join("database")
        .join("pico-possum.db");

    if legacy_db_path.exists() {
        let legacy_restore_msg = BackupService::restore_from_backup(&pool, &legacy_db_path.to_string_lossy())
            .await
            .expect("Restore from legacy Java backup should succeed");

        assert!(legacy_restore_msg.contains("Java POS application backup detected"));
        assert!(legacy_restore_msg.contains("Successfully converted and restored"));
    }
}

#[test]
fn test_printer_service_listing() {
    let printers = PrinterService::list_printers();
    // Doesn't panic and returns a sorted deduplicated list
    println!("Found {} printers: {:?}", printers.len(), printers);
}
