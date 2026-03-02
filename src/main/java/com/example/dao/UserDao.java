package com.example.dao;

import com.example.db.DatabaseManager;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserDao {

    public static Map<String, Object> create(String username, String password, String role) throws SQLException {
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, role);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            long id = rs.getLong(1);
            return findById(id);
        }
    }

    public static Map<String, Object> authenticate(String username, String password) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ? AND deleted_at IS NULL";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String status = rs.getString("status");
                if (!"ACTIVE".equals(status)) {
                    return null;
                }
                String hash = rs.getString("password_hash");
                if (BCrypt.checkpw(password, hash)) {
                    return rowToMap(rs);
                }
            }
        }
        return null;
    }

    public static Map<String, Object> findById(long id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rowToMap(rs);
            }
        }
        return null;
    }

    public static Map<String, Object> findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ? AND deleted_at IS NULL";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rowToMap(rs);
            }
        }
        return null;
    }

    public static List<Map<String, Object>> findAll(boolean includeDeleted) throws SQLException {
        String sql = includeDeleted
                ? "SELECT * FROM users ORDER BY created_at DESC"
                : "SELECT * FROM users WHERE deleted_at IS NULL ORDER BY created_at DESC";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(rowToMap(rs));
            }
        }
        return list;
    }

    public static long countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE deleted_at IS NULL";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    public static boolean updateRole(long id, String role) throws SQLException {
        String sql = "UPDATE users SET role = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean updateStatus(long id, String status) throws SQLException {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean changePassword(long id, String newPassword) throws SQLException {
        String hash = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean verifyPassword(long id, String password) throws SQLException {
        String sql = "SELECT password_hash FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return BCrypt.checkpw(password, rs.getString("password_hash"));
            }
        }
        return false;
    }

    public static boolean softDelete(long id) throws SQLException {
        String sql = "UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean restore(long id) throws SQLException {
        String sql = "UPDATE users SET deleted_at = NULL WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private static Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        map.put("id", rs.getLong("id"));
        map.put("username", rs.getString("username"));
        map.put("role", rs.getString("role"));
        map.put("status", rs.getString("status"));
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        map.put("deleted_at", deletedAt != null ? deletedAt.toString() : null);
        map.put("created_at", rs.getTimestamp("created_at").toString());
        map.put("updated_at", rs.getTimestamp("updated_at").toString());
        return map;
    }
}
