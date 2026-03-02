package com.example.dao;

import com.example.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsDao {

    public static List<Map<String, Object>> getSidebarSettings(long userId) throws SQLException {
        String sql = "SELECT page_name, visible FROM sidebar_settings WHERE user_id = ? ORDER BY page_name";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("page_name", rs.getString("page_name"));
                m.put("visible", rs.getBoolean("visible"));
                list.add(m);
            }
        }
        return list;
    }

    public static void saveSidebarSettings(long userId, List<Map<String, Object>> settings) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            PreparedStatement del = conn.prepareStatement("DELETE FROM sidebar_settings WHERE user_id = ?");
            del.setLong(1, userId);
            del.executeUpdate();

            if (settings != null && !settings.isEmpty()) {
                PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO sidebar_settings (user_id, page_name, visible) VALUES (?, ?, ?)");
                for (Map<String, Object> s : settings) {
                    ins.setLong(1, userId);
                    ins.setString(2, (String) s.get("page_name"));
                    ins.setBoolean(3, Boolean.TRUE.equals(s.get("visible")));
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        }
    }

    public static Map<String, Boolean> getSidebarVisibility(long userId) throws SQLException {
        Map<String, Boolean> visibility = new HashMap<>();
        String[] defaultPages = {"dashboard", "reports", "projects", "recordings", "recorder", "replayer", "api-testing", "users"};
        for (String p : defaultPages) visibility.put(p, true);

        List<Map<String, Object>> settings = getSidebarSettings(userId);
        for (Map<String, Object> s : settings) {
            visibility.put((String) s.get("page_name"), (Boolean) s.get("visible"));
        }
        return visibility;
    }
}
