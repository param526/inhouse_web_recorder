package com.example.dao;

import com.example.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectDao {

    public static Map<String, Object> create(String name, String description) throws SQLException {
        String sql = "INSERT INTO projects (name, description) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return findById(rs.getLong(1));
        }
    }

    public static Map<String, Object> findById(long id) throws SQLException {
        String sql = "SELECT * FROM projects WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rowToMap(rs);
        }
        return null;
    }

    public static List<Map<String, Object>> findAll() throws SQLException {
        String sql = "SELECT * FROM projects ORDER BY name ASC";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rowToMap(rs));
        }
        return list;
    }

    public static List<Map<String, Object>> findByUserId(long userId) throws SQLException {
        String sql = "SELECT p.* FROM projects p "
                + "INNER JOIN user_project_access upa ON p.id = upa.project_id "
                + "WHERE upa.user_id = ? ORDER BY p.name ASC";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rowToMap(rs));
        }
        return list;
    }

    public static boolean update(long id, String name, String description) throws SQLException {
        String sql = "UPDATE projects SET name = ?, description = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setLong(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean delete(long id) throws SQLException {
        String sql = "DELETE FROM projects WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static void grantAccess(long userId, long projectId) throws SQLException {
        String sql = "INSERT IGNORE INTO user_project_access (user_id, project_id) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, projectId);
            ps.executeUpdate();
        }
    }

    public static void revokeAccess(long userId, long projectId) throws SQLException {
        String sql = "DELETE FROM user_project_access WHERE user_id = ? AND project_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, projectId);
            ps.executeUpdate();
        }
    }

    public static List<Map<String, Object>> getAccessUsers(long projectId) throws SQLException {
        String sql = "SELECT u.id, u.username, u.role, u.status FROM users u "
                + "INNER JOIN user_project_access upa ON u.id = upa.user_id "
                + "WHERE upa.project_id = ? AND u.deleted_at IS NULL ORDER BY u.username";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("username", rs.getString("username"));
                m.put("role", rs.getString("role"));
                m.put("status", rs.getString("status"));
                list.add(m);
            }
        }
        return list;
    }

    public static boolean hasAccess(long userId, long projectId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM user_project_access WHERE user_id = ? AND project_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, projectId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1) > 0;
        }
    }

    public static int getRecordingCount(long projectId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM recordings WHERE project_id = ? AND deleted_at IS NULL";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        map.put("id", rs.getLong("id"));
        map.put("name", rs.getString("name"));
        map.put("description", rs.getString("description"));
        map.put("created_at", rs.getTimestamp("created_at").toString());
        map.put("updated_at", rs.getTimestamp("updated_at").toString());
        return map;
    }
}
