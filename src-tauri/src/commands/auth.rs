use sqlx::SqlitePool;
use tauri::State;

use crate::application::{AuthService, AuthSessionManager};
use crate::domain::models::{
    AuthStateResponse, LoginRequest, RegisterAdminRequest, UpdatePasswordRequest,
};
use crate::errors::AppError;

#[tauri::command]
pub async fn get_auth_state(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
) -> Result<AuthStateResponse, AppError> {
    AuthService::get_auth_state(&pool, &session_mgr).await
}

#[tauri::command]
pub async fn get_admin_username(
    pool: State<'_, SqlitePool>,
) -> Result<Option<String>, AppError> {
    AuthService::get_first_username(&pool).await
}

#[tauri::command]
pub async fn register_admin(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    req: RegisterAdminRequest,
) -> Result<AuthStateResponse, AppError> {
    AuthService::register_admin(&pool, &session_mgr, req).await
}

#[tauri::command]
pub async fn login(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    req: LoginRequest,
) -> Result<AuthStateResponse, AppError> {
    AuthService::login(&pool, &session_mgr, req).await
}

#[tauri::command]
pub async fn logout(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
) -> Result<(), AppError> {
    AuthService::logout(&pool, &session_mgr).await
}

#[tauri::command]
pub async fn update_password(
    pool: State<'_, SqlitePool>,
    session_mgr: State<'_, AuthSessionManager>,
    req: UpdatePasswordRequest,
) -> Result<String, AppError> {
    AuthService::update_password(&pool, &session_mgr, req).await
}
