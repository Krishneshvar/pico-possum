use pico_possum_lib::application::{AuthService, AuthSessionManager};
use pico_possum_lib::domain::models::{LoginRequest, RegisterAdminRequest, UpdatePasswordRequest};
use pico_possum_lib::infrastructure::database::schema::INIT_SCHEMA;
use sqlx::sqlite::SqlitePoolOptions;

#[tokio::test]
async fn test_auth_full_lifecycle() {
    let pool = SqlitePoolOptions::new()
        .max_connections(1)
        .connect("sqlite::memory:")
        .await
        .expect("Failed to connect to memory SQLite");

    sqlx::raw_sql(INIT_SCHEMA)
        .execute(&pool)
        .await
        .expect("Failed to initialize schema");

    let session_mgr = AuthSessionManager::new();

    // 1. Initial State: Count is 0, requires_setup is true, unauthenticated
    let initial_state = AuthService::get_auth_state(&pool, &session_mgr)
        .await
        .expect("Failed to get auth state");
    assert!(initial_state.requires_setup);
    assert!(!initial_state.is_authenticated);
    assert!(initial_state.user.is_none());

    // 2. Register first administrator
    let reg_req = RegisterAdminRequest {
        name: "Admin Boss".to_string(),
        username: "admin".to_string(),
        password: "secretpassword123".to_string(),
    };
    let reg_result = AuthService::register_admin(&pool, &session_mgr, reg_req)
        .await
        .expect("Admin registration should succeed");

    assert!(!reg_result.requires_setup);
    assert!(reg_result.is_authenticated);
    assert_eq!(reg_result.user.as_ref().unwrap().username, "admin");
    assert!(reg_result.session_token.is_some());

    // 3. Security Lockdown: Attempting second registration MUST FAIL!
    let hacker_req = RegisterAdminRequest {
        name: "Hacker Evil".to_string(),
        username: "hacker".to_string(),
        password: "evilpassword".to_string(),
    };
    let hacker_result = AuthService::register_admin(&pool, &session_mgr, hacker_req).await;
    assert!(hacker_result.is_err(), "Subsequent registration attempt must be blocked");

    // 4. Logout
    AuthService::logout(&pool, &session_mgr)
        .await
        .expect("Logout should succeed");
    assert!(!session_mgr.is_authenticated().await);

    // 5. Login with invalid password fails
    let bad_login_req = LoginRequest {
        username: "admin".to_string(),
        password: "wrongpassword".to_string(),
    };
    let bad_login = AuthService::login(&pool, &session_mgr, bad_login_req).await;
    assert!(bad_login.is_err());
    assert!(!session_mgr.is_authenticated().await);

    // 6. Login with correct password succeeds
    let good_login_req = LoginRequest {
        username: "admin".to_string(),
        password: "secretpassword123".to_string(),
    };
    let good_login = AuthService::login(&pool, &session_mgr, good_login_req)
        .await
        .expect("Login with valid password should succeed");
    assert!(good_login.is_authenticated);
    assert!(session_mgr.is_authenticated().await);

    // 7. Update password
    let update_req = UpdatePasswordRequest {
        current_password: "secretpassword123".to_string(),
        new_password: "newsuperpassword456".to_string(),
    };
    let update_msg = AuthService::update_password(&pool, &session_mgr, update_req)
        .await
        .expect("Password update should succeed");
    assert!(update_msg.contains("successfully"));

    // 8. Old password no longer works
    AuthService::logout(&pool, &session_mgr).await.unwrap();
    let old_pass_login = AuthService::login(
        &pool,
        &session_mgr,
        LoginRequest {
            username: "admin".to_string(),
            password: "secretpassword123".to_string(),
        },
    )
    .await;
    assert!(old_pass_login.is_err(), "Old password should be rejected");

    // 9. New password works
    let new_pass_login = AuthService::login(
        &pool,
        &session_mgr,
        LoginRequest {
            username: "admin".to_string(),
            password: "newsuperpassword456".to_string(),
        },
    )
    .await
    .expect("New password should work");
    assert!(new_pass_login.is_authenticated);
}
