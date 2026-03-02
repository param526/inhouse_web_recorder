package com.example.dao;

import com.example.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RunDao {

    public static long create(long recordingId, String mode, Long createdBy) throws SQLException {
        String sql = "INSERT INTO runs (recording_id, mode, status, created_by) VALUES (?, ?, 'RUNNING', ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, recordingId);
            ps.setString(2, mode);
            ps.setObject(3, createdBy);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getLong(1);
        }
    }

    public static boolean updateStatus(long id, String status, long durationMs, String errorMessage) throws SQLException {
        String sql = "UPDATE runs SET status = ?, finished_at = CURRENT_TIMESTAMP, duration_ms = ?, error_message = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, durationMs);
            ps.setString(3, errorMessage);
            ps.setLong(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean updatePaths(long id, String reportPath, String videoPath) throws SQLException {
        String sql = "UPDATE runs SET report_path = ?, video_path = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reportPath);
            ps.setString(2, videoPath);
            ps.setLong(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static Map<String, Object> findById(long id) throws SQLException {
        String sql = "SELECT r.*, rec.name as recording_name, rec.project_id, rec.module_id "
                + "FROM runs r LEFT JOIN recordings rec ON r.recording_id = rec.id WHERE r.id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rowToMap(rs);
        }
        return null;
    }

    public static List<Map<String, Object>> findByRecordingId(long recordingId, int limit, int offset) throws SQLException {
        String sql = "SELECT r.*, rec.name as recording_name, rec.project_id, rec.module_id "
                + "FROM runs r LEFT JOIN recordings rec ON r.recording_id = rec.id "
                + "WHERE r.recording_id = ? ORDER BY r.started_at DESC LIMIT ? OFFSET ?";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, recordingId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rowToMap(rs));
        }
        return list;
    }

    public static List<Map<String, Object>> findAll(String status, Long projectId, Long moduleId,
                                                      int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT r.*, rec.name as recording_name, rec.project_id, rec.module_id "
                        + "FROM runs r LEFT JOIN recordings rec ON r.recording_id = rec.id WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isEmpty()) {
            sql.append(" AND r.status = ?");
            params.add(status);
        }
        if (projectId != null) {
            sql.append(" AND rec.project_id = ?");
            params.add(projectId);
        }
        if (moduleId != null) {
            sql.append(" AND rec.module_id = ?");
            params.add(moduleId);
        }
        sql.append(" ORDER BY r.started_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rowToMap(rs));
        }
        return list;
    }

    public static List<Map<String, Object>> findByProjectIds(List<Long> projectIds, String status,
                                                               int limit, int offset) throws SQLException {
        if (projectIds.isEmpty()) return new ArrayList<>();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < projectIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        StringBuilder sql = new StringBuilder(
                "SELECT r.*, rec.name as recording_name, rec.project_id, rec.module_id "
                        + "FROM runs r LEFT JOIN recordings rec ON r.recording_id = rec.id "
                        + "WHERE rec.project_id IN (" + placeholders + ")");
        List<Object> params = new ArrayList<>(projectIds);
        if (status != null && !status.isEmpty()) {
            sql.append(" AND r.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY r.started_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rowToMap(rs));
        }
        return list;
    }

    public static Map<String, Object> getStats(Long projectId, List<Long> projectIds) throws SQLException {
        String baseJoin = "FROM runs r LEFT JOIN recordings rec ON r.recording_id = rec.id WHERE 1=1";
        String filter = "";
        if (projectId != null) {
            filter = " AND rec.project_id = " + projectId;
        } else if (projectIds != null && !projectIds.isEmpty()) {
            StringBuilder sb = new StringBuilder(" AND rec.project_id IN (");
            for (int i = 0; i < projectIds.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(projectIds.get(i));
            }
            sb.append(")");
            filter = sb.toString();
        }

        Map<String, Object> stats = new HashMap<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as total, "
                    + "SUM(CASE WHEN r.status='PASSED' THEN 1 ELSE 0 END) as passed, "
                    + "SUM(CASE WHEN r.status='FAILED' THEN 1 ELSE 0 END) as failed, "
                    + "SUM(CASE WHEN r.status='STOPPED' THEN 1 ELSE 0 END) as stopped, "
                    + "SUM(CASE WHEN r.status='RUNNING' THEN 1 ELSE 0 END) as running, "
                    + "AVG(CASE WHEN r.status IN ('PASSED','FAILED') THEN r.duration_ms ELSE NULL END) as avg_duration "
                    + baseJoin + filter);
            rs.next();
            stats.put("total_runs", rs.getInt("total"));
            stats.put("passed", rs.getInt("passed"));
            stats.put("failed", rs.getInt("failed"));
            stats.put("stopped", rs.getInt("stopped"));
            stats.put("running", rs.getInt("running"));
            stats.put("avg_duration", rs.getDouble("avg_duration"));
        }
        return stats;
    }

    public static List<Map<String, Object>> getRecentRuns(int limit, Long projectId, List<Long> projectIds) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT r.*, rec.name as recording_name, rec.project_id, rec.module_id "
                        + "FROM runs r LEFT JOIN recordings rec ON r.recording_id = rec.id WHERE 1=1");
        if (projectId != null) {
            sql.append(" AND rec.project_id = ").append(projectId);
        } else if (projectIds != null && !projectIds.isEmpty()) {
            sql.append(" AND rec.project_id IN (");
            for (int i = 0; i < projectIds.size(); i++) {
                if (i > 0) sql.append(",");
                sql.append(projectIds.get(i));
            }
            sql.append(")");
        }
        sql.append(" ORDER BY r.started_at DESC LIMIT ?");

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rowToMap(rs));
        }
        return list;
    }

    public static void addStep(long runId, int stepIndex, String action, String title,
                                String locator, String value, String status,
                                long durationMs, String errorMessage, String screenshotPath) throws SQLException {
        String sql = "INSERT INTO run_steps (run_id, step_index, action, title, locator, value_text, "
                + "status, duration_ms, error_message, screenshot_path) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, runId);
            ps.setInt(2, stepIndex);
            ps.setString(3, action);
            ps.setString(4, title);
            ps.setString(5, locator);
            ps.setString(6, value);
            ps.setString(7, status);
            ps.setLong(8, durationMs);
            ps.setString(9, errorMessage);
            ps.setString(10, screenshotPath);
            ps.executeUpdate();
        }
    }

    public static List<Map<String, Object>> getRunSteps(long runId) throws SQLException {
        String sql = "SELECT * FROM run_steps WHERE run_id = ? ORDER BY step_index ASC";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, runId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("run_id", rs.getLong("run_id"));
                m.put("step_index", rs.getInt("step_index"));
                m.put("action", rs.getString("action"));
                m.put("title", rs.getString("title"));
                m.put("locator", rs.getString("locator"));
                m.put("value", rs.getString("value_text"));
                m.put("status", rs.getString("status"));
                m.put("duration_ms", rs.getLong("duration_ms"));
                m.put("error_message", rs.getString("error_message"));
                m.put("screenshot_path", rs.getString("screenshot_path"));
                list.add(m);
            }
        }
        return list;
    }

    private static Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        map.put("id", rs.getLong("id"));
        map.put("recording_id", rs.getLong("recording_id"));
        map.put("recording_name", rs.getString("recording_name"));
        long pid = rs.getLong("project_id");
        map.put("project_id", rs.wasNull() ? null : pid);
        long mid = rs.getLong("module_id");
        map.put("module_id", rs.wasNull() ? null : mid);
        map.put("status", rs.getString("status"));
        map.put("mode", rs.getString("mode"));
        map.put("started_at", rs.getTimestamp("started_at").toString());
        Timestamp finishedAt = rs.getTimestamp("finished_at");
        map.put("finished_at", finishedAt != null ? finishedAt.toString() : null);
        map.put("duration_ms", rs.getLong("duration_ms"));
        map.put("error_message", rs.getString("error_message"));
        map.put("report_path", rs.getString("report_path"));
        map.put("video_path", rs.getString("video_path"));
        return map;
    }
}
