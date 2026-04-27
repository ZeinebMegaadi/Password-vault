-- ============================================================
--  VAULT — MySQL Workbench schema
--  Run this once to bootstrap the database, then let the
--  Java server handle incremental schema init automatically.
-- ============================================================

CREATE DATABASE IF NOT EXISTS vault_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE vault_db;

-- ── Users ─────────────────────────────────────────────────────────────────
-- auth_hash  : PBKDF2-HMAC-SHA256 (310 000 iterations), stored as
--              "iterations:saltHex:hashHex"
-- vault_salt : 16-byte random salt used to derive the AES-256 vault key
--              on every login — the key itself is NEVER stored.

CREATE TABLE IF NOT EXISTS users (
    id          INT          AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL,
    auth_hash   VARCHAR(512) NOT NULL,
    vault_salt  VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login  TIMESTAMP    NULL,
    UNIQUE KEY uq_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Secrets ───────────────────────────────────────────────────────────────
-- encrypted_value : AES-256-GCM ciphertext produced by CryptoUtils.encrypt().
--                   Format: Base64( IV[12] || ciphertext+authTag )
--                   The vault key used is derived fresh each login session
--                   and held only in server RAM — it never touches the DB.

CREATE TABLE IF NOT EXISTS secrets (
    id               INT          AUTO_INCREMENT PRIMARY KEY,
    user_id          INT          NOT NULL,
    name             VARCHAR(128) NOT NULL,
    category         VARCHAR(32)  NOT NULL DEFAULT 'PASSWORD',
    encrypted_value  TEXT         NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_secret (user_id, name),
    CONSTRAINT fk_secrets_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Audit log ─────────────────────────────────────────────────────────────
-- Immutable append-only log of security events per user.
-- The application never deletes rows from this table.

CREATE TABLE IF NOT EXISTS audit_log (
    id          INT          AUTO_INCREMENT PRIMARY KEY,
    user_id     INT          NOT NULL,
    action      VARCHAR(64)  NOT NULL,   -- e.g. LOGIN, ADD_SECRET, DELETE_SECRET
    details     VARCHAR(512),
    ip_address  VARCHAR(45),             -- supports IPv6
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Views (read-only helpers for MySQL Workbench) ─────────────────────────

CREATE OR REPLACE VIEW v_user_summary AS
SELECT
    u.id,
    u.username,
    u.created_at,
    u.last_login,
    COUNT(DISTINCT s.id) AS secret_count,
    COUNT(DISTINCT a.id) AS audit_events
FROM users u
LEFT JOIN secrets   s ON s.user_id = u.id
LEFT JOIN audit_log a ON a.user_id = u.id
GROUP BY u.id, u.username, u.created_at, u.last_login;

CREATE OR REPLACE VIEW v_recent_events AS
SELECT
    a.created_at   AS ts,
    u.username,
    a.action,
    a.details,
    a.ip_address
FROM audit_log a
JOIN users u ON u.id = a.user_id
ORDER BY a.created_at DESC;

-- ── Optional: seed a test admin (remove in production) ───────────────────
-- The Java server handles password hashing; you cannot insert a usable
-- password hash by hand unless you run PasswordHasher.hash() first.
-- Leave this section commented out — register via the GUI instead.
--
-- INSERT IGNORE INTO users (username, auth_hash, vault_salt)
-- VALUES ('admin', 'SEE_JAVA_PasswordHasher', 'SEE_JAVA_CryptoUtils');
