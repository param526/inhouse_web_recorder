package com.example.dao;

import com.example.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleDao {

    public static Map<String, Object> create(long projectId, String name, String description) throws SQLException {
        String sql = "INSERT INTO modules (project_id, name, description) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, projectId);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return findById(rs.getLong(1));
        }
    }

    public static Map<String, Object> findById(long id) throws SQLException {
        String sql = "SELECT * FROM modules WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rowToMap(rs);
        }
        return null;
    }

    public static List<Map<String, Object>> findByProjectId(long projectId) throws SQLException {
        String sql = "SELECT * FROM modules WHERE project_id = ? ORDER BY name ASC";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rowToMap(rs));
        }
        return list;
    }

    public static boolean update(long id, String name, String description) throws SQLException {
        String sql = "UPDATE modules SET name = ?, description = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setLong(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean delete(long id) throws SQLException {
        String sql = "DELETE FROM modules WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private static Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        map.put("id", rs.getLong("id"));
        map.put("project_id", rs.getLong("project_id"));
        map.put("name", rs.getString("name"));
        map.put("description", rs.getString("description"));
        map.put("created_at", rs.getTimestamp("created_at").toString());
        map.put("updated_at", rs.getTimestamp("updated_at").toString());
        return map;
    }
}
