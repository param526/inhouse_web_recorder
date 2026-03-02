package com.example.dao;

import com.example.db.DatabaseManager;
import com.example.util.EncryptionUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecordingDao {

    public static Map<String, Object> create(String name, String filename, String stepsJson,
                                              Long projectId, Long moduleId, int stepCount,
                                              long fileSize, Long createdBy) throws SQLException {
        byte[] encrypted = EncryptionUtil.encrypt(stepsJson);
        String sql = "INSERT INTO recordings (name, filename, steps_json, project_id, module_id, "
                + "step_count, file_size, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, filename);
            ps.setBytes(3, encrypted);
            ps.setObject(4, projectId);
            ps.setObject(5, moduleId);
            ps.setInt(6, stepCount);
            ps.setLong(7, fileSize);
            ps.setObject(8, createdBy);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return findById(rs.getLong(1));
        }
    }

    public static Map<String, Object> findById(long id) throws SQLException {
        String sql = "SELECT * FROM recordings WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rowToMap(rs, false);
        }
        return null;
    }

    public static Map<String, Object> findByIdWithSteps(long id) throws SQLException {
        String sql = "SELECT * FROM recordings WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rowToMap(rs, true);
        }
        return null;
    }

    public static List<Map<String, Object>> findAll(boolean includeDeleted, Long projectId, Long moduleId) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT id, name, filename, description, project_id, module_id, "
                + "step_count, file_size, gherkin_text, created_by, deleted_at, created_at, updated_at "
                + "FROM recordings WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (!includeDeleted) {
            sql.append(" AND deleted_at IS NULL");
        }
        if (projectId != null) {
            sql.append(" AND project_id = ?");
            params.add(projectId);
        }
        if (moduleId != null) {
            sql.append(" AND module_id = ?");
            params.add(moduleId);
        }
        sql.append(" ORDER BY created_at DESC");

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(rowToMapLight(rs));
            }
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
        String sql = "SELECT id, name, filename, description, project_id, module_id, "
                + "step_count, file_size, gherkin_text, created_by, deleted_at, created_at, updated_at "
                + "FROM recordings WHERE project_id IN (" + placeholders + ")";
        if (!includeDeleted) sql += " AND deleted_at IS NULL";
        sql += " ORDER BY created_at DESC";

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < projectIds.size(); i++) {
                ps.setLong(i + 1, projectIds.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rowToMapLight(rs));
        }
        return list;
    }

    public static String getStepsJson(long id) throws SQLException {
        String sql = "SELECT steps_json FROM recordings WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                byte[] encrypted = rs.getBytes("steps_json");
                return EncryptionUtil.decrypt(encrypted);
            }
        }
        return null;
    }

    public static boolean updateAssignment(long id, Long projectId, Long moduleId) throws SQLException {
        String sql = "UPDATE recordings SET project_id = ?, module_id = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, projectId);
            ps.setObject(2, moduleId);
            ps.setLong(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean updateName(long id, String name, String description) throws SQLException {
        String sql = "UPDATE recordings SET name = ?, description = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setLong(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean updateSteps(long id, String stepsJson, int stepCount) throws SQLException {
        byte[] encrypted = EncryptionUtil.encrypt(stepsJson);
        String sql = "UPDATE recordings SET steps_json = ?, step_count = ?, file_size = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBytes(1, encrypted);
            ps.setInt(2, stepCount);
            ps.setLong(3, stepsJson.length());
            ps.setLong(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean updateGherkin(long id, String gherkinText) throws SQLException {
        String sql = "UPDATE recordings SET gherkin_text = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, gherkinText);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean softDelete(long id) throws SQLException {
        String sql = "UPDATE recordings SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean restore(long id) throws SQLException {
        String sql = "UPDATE recordings SET deleted_at = NULL WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static int countAll(Long projectId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM recordings WHERE deleted_at IS NULL";
        if (projectId != null) sql += " AND project_id = " + projectId;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Map<String, Object> rowToMap(ResultSet rs, boolean includeSteps) throws SQLException {
        Map<String, Object> map = rowToMapLight(rs);
        if (includeSteps) {
            byte[] encrypted = rs.getBytes("steps_json");
            if (encrypted != null) {
                map.put("steps_json", EncryptionUtil.decrypt(encrypted));
            }
        }
        return map;
    }

    private static Map<String, Object> rowToMapLight(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        map.put("id", rs.getLong("id"));
        map.put("name", rs.getString("name"));
        map.put("filename", rs.getString("filename"));
        map.put("description", rs.getString("description"));
        long pid = rs.getLong("project_id");
        map.put("project_id", rs.wasNull() ? null : pid);
        long mid = rs.getLong("module_id");
        map.put("module_id", rs.wasNull() ? null : mid);
        map.put("step_count", rs.getInt("step_count"));
        map.put("file_size", rs.getLong("file_size"));
        map.put("gherkin_text", rs.getString("gherkin_text"));
        long cby = rs.getLong("created_by");
        map.put("created_by", rs.wasNull() ? null : cby);
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        map.put("deleted_at", deletedAt != null ? deletedAt.toString() : null);
        map.put("created_at", rs.getTimestamp("created_at").toString());
        map.put("updated_at", rs.getTimestamp("updated_at").toString());
        return map;
    }
}
