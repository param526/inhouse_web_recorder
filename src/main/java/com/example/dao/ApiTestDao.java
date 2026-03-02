package com.example.dao;

import com.example.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiTestDao {

    public static Map<String, Object> create(String name, String description, Long projectId,
                                              Long moduleId, String baseUrl, String testData,
                                              Long createdBy) throws SQLException {
        String sql = "INSERT INTO api_tests (name, description, project_id, module_id, base_url, test_data, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setObject(3, projectId);
            ps.setObject(4, moduleId);
            ps.setString(5, baseUrl);
            ps.setString(6, testData);
            ps.setObject(7, createdBy);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return findById(rs.getLong(1));
        }
    }

    public static Map<String, Object> findById(long id) throws SQLException {
        String sql = "SELECT * FROM api_tests WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rowToMap(rs);
        }
        return null;
    }

    public static List<Map<String, Object>> findAll(boolean includeDeleted, Long projectId) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM api_tests WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (!includeDeleted) {
            sql.append(" AND deleted_at IS NULL");
        }
        if (projectId != null) {
            sql.append(" AND project_id = ?");
            params.add(projectId);
        }
        sql.append(" ORDER BY created_at DESC");

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rowToMap(rs));
        }
        return list;
    }

    public static List<Map<String, Object>> findByProjectIds(List<Long> projectIds, boolean includeDeleted) throws SQLException {
        if (projectIds.isEmpty()) return new ArrayList<>();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < projectIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        String sql = "SELECT * FROM api_tests WHERE project_id IN (" + placeholders + ")";
        if (!includeDeleted) sql += " AND deleted_at IS NULL";
        sql += " ORDER BY created_at DESC";

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < projectIds.size(); i++) ps.setLong(i + 1, projectIds.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rowToMap(rs));
        }
        return list;
    }

    public static boolean update(long id, String name, String description, Long projectId,
                                  Long moduleId, String baseUrl, String testData) throws SQLException {
        String sql = "UPDATE api_tests SET name=?, description=?, project_id=?, module_id=?, base_url=?, test_data=? WHERE id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setObject(3, projectId);
            ps.setObject(4, moduleId);
            ps.setString(5, baseUrl);
            ps.setString(6, testData);
            ps.setLong(7, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean softDelete(long id) throws SQLException {
        String sql = "UPDATE api_tests SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean restore(long id) throws SQLException {
        String sql = "UPDATE api_tests SET deleted_at = NULL WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // --- API Test Runs ---
    public static long createRun(long apiTestId, String status, String responseData,
                                  long durationMs, Long createdBy) throws SQLException {
        String sql = "INSERT INTO api_test_runs (api_test_id, status, response_data, duration_ms, created_by) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, apiTestId);
            ps.setString(2, status);
            ps.setString(3, responseData);
            ps.setLong(4, durationMs);
            ps.setObject(5, createdBy);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getLong(1);
        }
    }

    public static List<Map<String, Object>> getTestRuns(long apiTestId, int limit) throws SQLException {
        String sql = "SELECT * FROM api_test_runs WHERE api_test_id = ? ORDER BY created_at DESC LIMIT ?";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, apiTestId);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("api_test_id", rs.getLong("api_test_id"));
                m.put("status", rs.getString("status"));
                m.put("response_data", rs.getString("response_data"));
                m.put("duration_ms", rs.getLong("duration_ms"));
                m.put("created_at", rs.getTimestamp("created_at").toString());
                list.add(m);
            }
        }
        return list;
    }

    // --- Suites ---
    public static Map<String, Object> createSuite(String name, String description, Long projectId,
                                                    String testIds, Long createdBy) throws SQLException {
        String sql = "INSERT INTO api_suites (name, description, project_id, test_ids, created_by) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setObject(3, projectId);
            ps.setString(4, testIds);
            ps.setObject(5, createdBy);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return findSuiteById(rs.getLong(1));
        }
    }

    public static Map<String, Object> findSuiteById(long id) throws SQLException {
        String sql = "SELECT * FROM api_suites WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return suiteRowToMap(rs);
        }
        return null;
    }

    public static List<Map<String, Object>> findAllSuites(boolean includeDeleted, Long projectId) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM api_suites WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (!includeDeleted) sql.append(" AND deleted_at IS NULL");
        if (projectId != null) { sql.append(" AND project_id = ?"); params.add(projectId); }
        sql.append(" ORDER BY created_at DESC");

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(suiteRowToMap(rs));
        }
        return list;
    }

    public static boolean updateSuite(long id, String name, String description, Long projectId, String testIds) throws SQLException {
        String sql = "UPDATE api_suites SET name=?, description=?, project_id=?, test_ids=? WHERE id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setObject(3, projectId);
            ps.setString(4, testIds);
            ps.setLong(5, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean deleteSuite(long id) throws SQLException {
        String sql = "UPDATE api_suites SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static long createSuiteRun(long suiteId, String status, String resultsData,
                                        long durationMs, Long createdBy) throws SQLException {
        String sql = "INSERT INTO api_suite_runs (suite_id, status, results_data, duration_ms, created_by) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, suiteId);
            ps.setString(2, status);
            ps.setString(3, resultsData);
            ps.setLong(4, durationMs);
            ps.setObject(5, createdBy);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getLong(1);
        }
    }

    public static List<Map<String, Object>> getSuiteRuns(long suiteId, int limit) throws SQLException {
        String sql = "SELECT * FROM api_suite_runs WHERE suite_id = ? ORDER BY created_at DESC LIMIT ?";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, suiteId);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("suite_id", rs.getLong("suite_id"));
                m.put("status", rs.getString("status"));
                m.put("results_data", rs.getString("results_data"));
                m.put("duration_ms", rs.getLong("duration_ms"));
                m.put("created_at", rs.getTimestamp("created_at").toString());
                list.add(m);
            }
        }
        return list;
    }

    // --- Environments ---
    public static Map<String, Object> createEnvironment(String name, String baseUrl, Long projectId,
                                                          boolean isDefault, Long createdBy) throws SQLException {
        String sql = "INSERT INTO environments (name, base_url, project_id, is_default, created_by) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, baseUrl);
            ps.setObject(3, projectId);
            ps.setBoolean(4, isDefault);
            ps.setObject(5, createdBy);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return findEnvironmentById(rs.getLong(1));
        }
    }

    public static Map<String, Object> findEnvironmentById(long id) throws SQLException {
        String sql = "SELECT * FROM environments WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> m = envRowToMap(rs);
                m.put("variables", getEnvironmentVariables(id));
                return m;
            }
        }
        return null;
    }

    public static List<Map<String, Object>> findAllEnvironments(Long projectId) throws SQLException {
        String sql = projectId != null
                ? "SELECT * FROM environments WHERE project_id = ? ORDER BY name"
                : "SELECT * FROM environments ORDER BY name";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (projectId != null) ps.setLong(1, projectId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = envRowToMap(rs);
                m.put("variables", getEnvironmentVariables(rs.getLong("id")));
                list.add(m);
            }
        }
        return list;
    }

    public static boolean updateEnvironment(long id, String name, String baseUrl, Long projectId, boolean isDefault) throws SQLException {
        String sql = "UPDATE environments SET name=?, base_url=?, project_id=?, is_default=? WHERE id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, baseUrl);
            ps.setObject(3, projectId);
            ps.setBoolean(4, isDefault);
            ps.setLong(5, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean deleteEnvironment(long id) throws SQLException {
        String sql = "DELETE FROM environments WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static void setEnvironmentVariables(long environmentId, List<Map<String, String>> variables) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            PreparedStatement del = conn.prepareStatement("DELETE FROM environment_variables WHERE environment_id = ?");
            del.setLong(1, environmentId);
            del.executeUpdate();

            if (variables != null && !variables.isEmpty()) {
                PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO environment_variables (environment_id, var_key, var_value) VALUES (?, ?, ?)");
                for (Map<String, String> v : variables) {
                    ins.setLong(1, environmentId);
                    ins.setString(2, v.get("key"));
                    ins.setString(3, v.get("value"));
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        }
    }

    private static List<Map<String, String>> getEnvironmentVariables(long environmentId) throws SQLException {
        String sql = "SELECT var_key, var_value FROM environment_variables WHERE environment_id = ?";
        List<Map<String, String>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, environmentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> m = new HashMap<>();
                m.put("key", rs.getString("var_key"));
                m.put("value", rs.getString("var_value"));
                list.add(m);
            }
        }
        return list;
    }

    private static Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> m = new HashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("name", rs.getString("name"));
        m.put("description", rs.getString("description"));
        long pid = rs.getLong("project_id");
        m.put("project_id", rs.wasNull() ? null : pid);
        long mid = rs.getLong("module_id");
        m.put("module_id", rs.wasNull() ? null : mid);
        m.put("base_url", rs.getString("base_url"));
        m.put("test_data", rs.getString("test_data"));
        Timestamp del = rs.getTimestamp("deleted_at");
        m.put("deleted_at", del != null ? del.toString() : null);
        m.put("created_at", rs.getTimestamp("created_at").toString());
        m.put("updated_at", rs.getTimestamp("updated_at").toString());
        return m;
    }

    private static Map<String, Object> suiteRowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> m = new HashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("name", rs.getString("name"));
        m.put("description", rs.getString("description"));
        long pid = rs.getLong("project_id");
        m.put("project_id", rs.wasNull() ? null : pid);
        m.put("test_ids", rs.getString("test_ids"));
        Timestamp del = rs.getTimestamp("deleted_at");
        m.put("deleted_at", del != null ? del.toString() : null);
        m.put("created_at", rs.getTimestamp("created_at").toString());
        m.put("updated_at", rs.getTimestamp("updated_at").toString());
        return m;
    }

    private static Map<String, Object> envRowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> m = new HashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("name", rs.getString("name"));
        m.put("base_url", rs.getString("base_url"));
        long pid = rs.getLong("project_id");
        m.put("project_id", rs.wasNull() ? null : pid);
        m.put("is_default", rs.getBoolean("is_default"));
        m.put("created_at", rs.getTimestamp("created_at").toString());
        m.put("updated_at", rs.getTimestamp("updated_at").toString());
        return m;
    }
}
