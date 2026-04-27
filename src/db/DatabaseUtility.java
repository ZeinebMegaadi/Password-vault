package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import security.EnvLoader;

public class DatabaseUtility {

    public static Connection getConnection() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(
                EnvLoader.get("DB_URL"),
                EnvLoader.get("DB_USER"),
                EnvLoader.get("DB_PASS"));
    }

    // Legacy overload kept for backwards compatibility
    public static Connection getConnectionDB(String url, String user, String pass)
            throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(url, user, pass);
    }

    /**
     * Creates all required tables if they do not already exist.
     * Called once on server start-up so there is no manual SQL setup step.
     */
    public static void initSchema() throws Exception {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    id          INT AUTO_INCREMENT PRIMARY KEY,
                    username    VARCHAR(64)  UNIQUE NOT NULL,
                    auth_hash   VARCHAR(512) NOT NULL,
                    vault_salt  VARCHAR(64)  NOT NULL,
                    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_login  TIMESTAMP NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS secrets (
                    id               INT AUTO_INCREMENT PRIMARY KEY,
                    user_id          INT          NOT NULL,
                    name             VARCHAR(128) NOT NULL,
                    category         VARCHAR(32)  NOT NULL DEFAULT 'PASSWORD',
                    encrypted_value  TEXT         NOT NULL,
                    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_user_secret (user_id, name),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS audit_log (
                    id          INT AUTO_INCREMENT PRIMARY KEY,
                    user_id     INT          NOT NULL,
                    action      VARCHAR(64)  NOT NULL,
                    details     VARCHAR(512),
                    ip_address  VARCHAR(45),
                    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        }
    }
}
