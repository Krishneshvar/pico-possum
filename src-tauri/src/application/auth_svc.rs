use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::{Mutex, RwLock};
use chrono::{DateTime, Utc};
use sqlx::{Row, SqlitePool};
use uuid::Uuid;

use crate::domain::models::{
    AuthStateResponse, AuthUser, LoginRequest, RegisterAdminRequest, UpdatePasswordRequest,
};
use crate::errors::AppError;

pub struct SessionData {
    pub user: AuthUser,
    pub token: String,
    pub authenticated_at: DateTime<Utc>,
}

#[derive(Clone)]
pub struct AuthSessionManager {
    current_session: Arc<RwLock<Option<SessionData>>>,
    failed_attempts: Arc<Mutex<HashMap<String, (u32, DateTime<Utc>)>>>,
}

impl Default for AuthSessionManager {
    fn default() -> Self {
        Self::new()
    }
}

impl AuthSessionManager {
    pub fn new() -> Self {
        Self {
            current_session: Arc::new(RwLock::new(None)),
            failed_attempts: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    pub async fn is_authenticated(&self) -> bool {
        self.current_session.read().await.is_some()
    }

    pub async fn get_current_user(&self) -> Option<AuthUser> {
        self.current_session.read().await.as_ref().map(|s| s.user.clone())
    }

    pub async fn get_session_token(&self) -> Option<String> {
        self.current_session.read().await.as_ref().map(|s| s.token.clone())
    }

    pub async fn set_session(&self, user: AuthUser) -> String {
        let token = Uuid::new_v4().to_string();
        let mut session = self.current_session.write().await;
        *session = Some(SessionData {
            user,
            token: token.clone(),
            authenticated_at: Utc::now(),
        });
        token
    }

    pub async fn clear_session(&self) {
        let mut session = self.current_session.write().await;
        *session = None;
    }

    pub async fn check_rate_limit(&self, username: &str) -> Result<(), AppError> {
        let mut attempts = self.failed_attempts.lock().await;
        let now = Utc::now();
        if let Some((count, last_attempt)) = attempts.get(username) {
            if *count >= 5 {
                let diff_secs = (now - *last_attempt).num_seconds();
                if diff_secs < 30 {
                    return Err(AppError::Forbidden(format!(
                        "Too many failed login attempts. Please wait {} seconds.",
                        30 - diff_secs
                    )));
                } else {
                    attempts.remove(username);
                }
            }
        }
        Ok(())
    }

    pub async fn record_failed_attempt(&self, username: &str) {
        let mut attempts = self.failed_attempts.lock().await;
        let now = Utc::now();
        let entry = attempts.entry(username.to_string()).or_insert((0, now));
        entry.0 += 1;
        entry.1 = now;
    }

    pub async fn clear_failed_attempts(&self, username: &str) {
        let mut attempts = self.failed_attempts.lock().await;
        attempts.remove(username);
    }
}

pub struct AuthService;

impl AuthService {
    pub async fn count_users(pool: &SqlitePool) -> Result<i64, AppError> {
        let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM users WHERE is_active = 1")
            .fetch_one(pool)
            .await?;
        Ok(count)
    }

    pub async fn get_first_username(pool: &SqlitePool) -> Result<Option<String>, AppError> {
        let username: Option<String> = sqlx::query_scalar("SELECT username FROM users WHERE is_active = 1 LIMIT 1")
            .fetch_optional(pool)
            .await?;
        Ok(username)
    }

    pub async fn get_auth_state(
        pool: &SqlitePool,
        session_mgr: &AuthSessionManager,
    ) -> Result<AuthStateResponse, AppError> {
        let count = Self::count_users(pool).await?;
        let requires_setup = count == 0;

        let user = session_mgr.get_current_user().await;
        let is_authenticated = user.is_some();
        let session_token = session_mgr.get_session_token().await;

        Ok(AuthStateResponse {
            requires_setup,
            is_authenticated,
            user,
            session_token,
        })
    }

    pub async fn register_admin(
        pool: &SqlitePool,
        session_mgr: &AuthSessionManager,
        req: RegisterAdminRequest,
    ) -> Result<AuthStateResponse, AppError> {
        let trimmed_name = req.name.trim();
        let trimmed_user = req.username.trim();
        let trimmed_pass = req.password.trim();

        if trimmed_name.is_empty() {
            return Err(AppError::Validation("Name is required".to_string()));
        }
        if trimmed_user.is_empty() {
            return Err(AppError::Validation("Username is required".to_string()));
        }
        if trimmed_pass.len() < 4 {
            return Err(AppError::Validation("Password must be at least 4 characters".to_string()));
        }

        // Critical Security Check: ONLY allow registration if 0 users exist!
        let mut tx = pool.begin().await?;
        let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM users")
            .fetch_one(&mut *tx)
            .await?;

        if count > 0 {
            return Err(AppError::Forbidden(
                "System setup has already been completed. Registration is closed.".to_string(),
            ));
        }

        // Hash password with BCrypt (10 rounds work factor, same as Java app)
        let password_hash = bcrypt::hash(trimmed_pass, 10)
            .map_err(|e| AppError::Validation(format!("Failed to hash password: {}", e)))?;

        let user_id = Uuid::new_v4().to_string();

        sqlx::query(
            "INSERT INTO users (id, username, password_hash, name, is_active)
             VALUES (?, ?, ?, ?, 1)"
        )
        .bind(&user_id)
        .bind(trimmed_user)
        .bind(&password_hash)
        .bind(trimmed_name)
        .execute(&mut *tx)
        .await?;

        // Audit log setup
        let audit_id = Uuid::new_v4().to_string();
        sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description)
             VALUES (?, 'SYSTEM_SETUP', 'USER', ?, ?)"
        )
        .bind(&audit_id)
        .bind(&user_id)
        .bind(format!("Administrator account created for '{}' ({})", trimmed_name, trimmed_user))
        .execute(&mut *tx)
        .await?;

        tx.commit().await?;

        let auth_user = AuthUser {
            id: user_id,
            name: trimmed_name.to_string(),
            username: trimmed_user.to_string(),
        };

        let token = session_mgr.set_session(auth_user.clone()).await;

        Ok(AuthStateResponse {
            requires_setup: false,
            is_authenticated: true,
            user: Some(auth_user),
            session_token: Some(token),
        })
    }

    pub async fn login(
        pool: &SqlitePool,
        session_mgr: &AuthSessionManager,
        req: LoginRequest,
    ) -> Result<AuthStateResponse, AppError> {
        let trimmed_user = req.username.trim();
        let trimmed_pass = req.password.trim();

        if trimmed_user.is_empty() || trimmed_pass.is_empty() {
            return Err(AppError::Validation("Username and password are required".to_string()));
        }

        // Brute force protection check
        session_mgr.check_rate_limit(trimmed_user).await?;

        let user_row = sqlx::query(
            "SELECT id, username, password_hash, name, is_active FROM users WHERE username = ? LIMIT 1"
        )
        .bind(trimmed_user)
        .fetch_optional(pool)
        .await?;

        let (user_id, username, password_hash, name, is_active) = match user_row {
            Some(row) => {
                let id: String = row.get("id");
                let uname: String = row.get("username");
                let phash: String = row.get("password_hash");
                let n: String = row.get("name");
                let active: bool = row.get("is_active");
                (id, uname, phash, n, active)
            }
            None => {
                session_mgr.record_failed_attempt(trimmed_user).await;
                let _ = sqlx::query(
                    "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description)
                     VALUES (?, 'FAILED_LOGIN', 'USER', ?, ?)"
                )
                .bind(Uuid::new_v4().to_string())
                .bind(trimmed_user)
                .bind(format!("Failed login attempt: username '{}' not found", trimmed_user))
                .execute(pool)
                .await;

                return Err(AppError::Unauthorized("Invalid username or password".to_string()));
            }
        };

        if !is_active {
            return Err(AppError::Forbidden("This account is inactive. Please contact your system administrator.".to_string()));
        }

        // Constant-time BCrypt verification
        let valid = bcrypt::verify(trimmed_pass, &password_hash).unwrap_or(false);

        if !valid {
            session_mgr.record_failed_attempt(trimmed_user).await;
            let _ = sqlx::query(
                "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description)
                 VALUES (?, 'FAILED_LOGIN', 'USER', ?, ?)"
            )
            .bind(Uuid::new_v4().to_string())
            .bind(&user_id)
            .bind(format!("Failed login attempt: incorrect password for '{}'", trimmed_user))
            .execute(pool)
            .await;

            return Err(AppError::Unauthorized("Invalid username or password".to_string()));
        }

        // Login succeeded: clear failed attempts
        session_mgr.clear_failed_attempts(trimmed_user).await;

        let auth_user = AuthUser {
            id: user_id.clone(),
            name,
            username,
        };

        let token = session_mgr.set_session(auth_user.clone()).await;

        // Audit log login
        let _ = sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description)
             VALUES (?, 'USER_LOGIN', 'USER', ?, ?)"
        )
        .bind(Uuid::new_v4().to_string())
        .bind(&user_id)
        .bind(format!("User '{}' logged in successfully", trimmed_user))
        .execute(pool)
        .await;

        Ok(AuthStateResponse {
            requires_setup: false,
            is_authenticated: true,
            user: Some(auth_user),
            session_token: Some(token),
        })
    }

    pub async fn logout(
        pool: &SqlitePool,
        session_mgr: &AuthSessionManager,
    ) -> Result<(), AppError> {
        if let Some(user) = session_mgr.get_current_user().await {
            let _ = sqlx::query(
                "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description)
                 VALUES (?, 'USER_LOGOUT', 'USER', ?, ?)"
            )
            .bind(Uuid::new_v4().to_string())
            .bind(&user.id)
            .bind(format!("User '{}' logged out", user.username))
            .execute(pool)
            .await;
        }

        session_mgr.clear_session().await;
        Ok(())
    }

    pub async fn update_password(
        pool: &SqlitePool,
        session_mgr: &AuthSessionManager,
        req: UpdatePasswordRequest,
    ) -> Result<String, AppError> {
        // Enforce active session
        let current_user = session_mgr.get_current_user().await
            .ok_or_else(|| AppError::Unauthorized("You must be logged in to update password".to_string()))?;

        let cur_pass = req.current_password.trim();
        let new_pass = req.new_password.trim();

        if cur_pass.is_empty() || new_pass.is_empty() {
            return Err(AppError::Validation("Current and new passwords are required".to_string()));
        }
        if new_pass.len() < 4 {
            return Err(AppError::Validation("New password must be at least 4 characters long".to_string()));
        }

        // Fetch current user from DB
        let user_row = sqlx::query("SELECT password_hash FROM users WHERE id = ?")
            .bind(&current_user.id)
            .fetch_one(pool)
            .await?;

        let current_hash: String = user_row.get("password_hash");

        // Verify current password
        let valid = bcrypt::verify(cur_pass, &current_hash).unwrap_or(false);
        if !valid {
            return Err(AppError::Unauthorized("Current password does not match".to_string()));
        }

        // Hash new password
        let new_hash = bcrypt::hash(new_pass, 10)
            .map_err(|e| AppError::Validation(format!("Failed to hash new password: {}", e)))?;

        sqlx::query("UPDATE users SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
            .bind(&new_hash)
            .bind(&current_user.id)
            .execute(pool)
            .await?;

        // Audit log password update
        let _ = sqlx::query(
            "INSERT INTO audit_logs (id, event_type, entity_type, entity_id, description)
             VALUES (?, 'PASSWORD_CHANGED', 'USER', ?, ?)"
        )
        .bind(Uuid::new_v4().to_string())
        .bind(&current_user.id)
        .bind(format!("Password successfully changed for user '{}'", current_user.username))
        .execute(pool)
        .await;

        Ok("Password updated successfully".to_string())
    }
}
