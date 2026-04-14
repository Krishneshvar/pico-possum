CREATE TABLE IF NOT EXISTS drafts (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL, -- e.g. "sale", "purchase_order"
    payload TEXT NOT NULL, -- JSON string
    user_id INTEGER NOT NULL,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(user_id) REFERENCES users(id)
);

CREATE INDEX idx_drafts_type_user ON drafts(type, user_id);
