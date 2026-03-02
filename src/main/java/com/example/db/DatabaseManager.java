package com.example.db;

import com.example.util.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static HikariDataSource dataSource;

    public static void init() {
        String host = AppConfig.get("db.host", "localhost");
        String port = AppConfig.get("db.port", "3306");
        String dbName = AppConfig.get("db.name", "recorder_db");
        String user = AppConfig.get("db.user", "root");
        String pass = AppConfig.get("db.password", "");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + dbName
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(AppConfig.getInt("db.maxPoolSize", 10));
        config.setMinimumIdle(AppConfig.getInt("db.minIdle", 2));
        config.setConnectionTimeout(AppConfig.getLong("db.connectionTimeout", 30000));
        config.setIdleTimeout(AppConfig.getLong("db.idleTimeout", 600000));
        config.setMaxLifetime(AppConfig.getLong("db.maxLifetime", 1800000));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
        System.out.println("[DB] Connected to MySQL: " + host + ":" + port + "/" + dbName);

        createTables();
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private static void createTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "username VARCHAR(50) UNIQUE NOT NULL, "
                    + "password_hash VARCHAR(255) NOT NULL, "
                    + "role VARCHAR(20) NOT NULL DEFAULT 'RESOURCE', "
                    + "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', "
                    + "deleted_at TIMESTAMP NULL, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS projects ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(100) NOT NULL, "
                    + "description TEXT, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS modules ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "project_id BIGINT NOT NULL, "
                    + "name VARCHAR(100) NOT NULL, "
                    + "description TEXT, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS user_project_access ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "user_id BIGINT NOT NULL, "
                    + "project_id BIGINT NOT NULL, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "UNIQUE KEY uk_user_project (user_id, project_id), "
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE, "
                    + "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS recordings ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(255) NOT NULL, "
                    + "filename VARCHAR(255), "
                    + "description TEXT, "
                    + "project_id BIGINT, "
                    + "module_id BIGINT, "
                    + "steps_json LONGBLOB NOT NULL, "
                    + "gherkin_text LONGTEXT, "
                    + "step_count INT DEFAULT 0, "
                    + "file_size BIGINT DEFAULT 0, "
                    + "created_by BIGINT, "
                    + "deleted_at TIMESTAMP NULL, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL, "
                    + "FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE SET NULL, "
                    + "FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS runs ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "recording_id BIGINT NOT NULL, "
                    + "status VARCHAR(20) NOT NULL DEFAULT 'RUNNING', "
                    + "mode VARCHAR(20) DEFAULT 'LOCAL', "
                    + "started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "finished_at TIMESTAMP NULL, "
                    + "duration_ms BIGINT DEFAULT 0, "
                    + "error_message TEXT, "
                    + "report_path VARCHAR(500), "
                    + "video_path VARCHAR(500), "
                    + "created_by BIGINT, "
                    + "FOREIGN KEY (recording_id) REFERENCES recordings(id) ON DELETE CASCADE, "
                    + "FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS run_steps ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "run_id BIGINT NOT NULL, "
                    + "step_index INT NOT NULL, "
                    + "action VARCHAR(50), "
                    + "title VARCHAR(500), "
                    + "locator TEXT, "
                    + "value_text TEXT, "
                    + "status VARCHAR(20) NOT NULL, "
                    + "duration_ms BIGINT DEFAULT 0, "
                    + "error_message TEXT, "
                    + "screenshot_path VARCHAR(500), "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (run_id) REFERENCES runs(id) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS api_tests ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(255) NOT NULL, "
                    + "description TEXT, "
                    + "project_id BIGINT, "
                    + "module_id BIGINT, "
                    + "base_url VARCHAR(500), "
                    + "test_data LONGTEXT NOT NULL, "
                    + "deleted_at TIMESTAMP NULL, "
                    + "created_by BIGINT, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL, "
                    + "FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE SET NULL, "
                    + "FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS api_test_runs ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "api_test_id BIGINT NOT NULL, "
                    + "status VARCHAR(20) NOT NULL, "
                    + "response_data LONGTEXT, "
                    + "duration_ms BIGINT DEFAULT 0, "
                    + "created_by BIGINT, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (api_test_id) REFERENCES api_tests(id) ON DELETE CASCADE, "
                    + "FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS api_suites ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(255) NOT NULL, "
                    + "description TEXT, "
                    + "project_id BIGINT, "
                    + "test_ids LONGTEXT, "
                    + "deleted_at TIMESTAMP NULL, "
                    + "created_by BIGINT, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL, "
                    + "FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS api_suite_runs ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "suite_id BIGINT NOT NULL, "
                    + "status VARCHAR(20) NOT NULL, "
                    + "results_data LONGTEXT, "
                    + "duration_ms BIGINT DEFAULT 0, "
                    + "created_by BIGINT, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (suite_id) REFERENCES api_suites(id) ON DELETE CASCADE, "
                    + "FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS environments ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(100) NOT NULL, "
                    + "base_url VARCHAR(500), "
                    + "project_id BIGINT, "
                    + "is_default BOOLEAN DEFAULT FALSE, "
                    + "created_by BIGINT, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL, "
                    + "FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS environment_variables ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "environment_id BIGINT NOT NULL, "
                    + "var_key VARCHAR(100) NOT NULL, "
                    + "var_value TEXT NOT NULL, "
                    + "FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS sidebar_settings ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "user_id BIGINT NOT NULL, "
                    + "page_name VARCHAR(50) NOT NULL, "
                    + "visible BOOLEAN DEFAULT TRUE, "
                    + "UNIQUE KEY uk_user_page (user_id, page_name), "
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            System.out.println("[DB] All tables created/verified successfully.");

        } catch (SQLException e) {
            System.err.println("[DB] Error creating tables: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Database initialization failed", e);
        }
    }

}
