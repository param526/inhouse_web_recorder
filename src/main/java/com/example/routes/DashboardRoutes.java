package com.example.routes;

import com.example.auth.AuthFilter;
import com.example.dao.ProjectDao;
import com.example.dao.RecordingDao;
import com.example.dao.RunDao;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static spark.Spark.*;

public class DashboardRoutes {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void register() {

        get("/api/dashboard", (req, res) -> {
            res.type("application/json");
            String projectIdStr = req.queryParams("project_id");
            Long projectId = projectIdStr != null && !projectIdStr.isEmpty() ? Long.parseLong(projectIdStr) : null;

            List<Long> accessibleProjectIds = null;
            if (!AuthFilter.isAdmin(req)) {
                List<Map<String, Object>> userProjects = ProjectDao.findByUserId(AuthFilter.getUserId(req));
                accessibleProjectIds = userProjects.stream()
                        .map(p -> (Long) p.get("id"))
                        .collect(Collectors.toList());
            }

            // Stats
            Map<String, Object> stats = RunDao.getStats(projectId, accessibleProjectIds);

            // Recording count
            int recordingCount;
            if (projectId != null) {
                recordingCount = RecordingDao.countAll(projectId);
            } else if (accessibleProjectIds != null) {
                int total = 0;
                for (Long pid : accessibleProjectIds) {
                    total += RecordingDao.countAll(pid);
                }
                recordingCount = total;
            } else {
                recordingCount = RecordingDao.countAll(null);
            }
            stats.put("recordings", recordingCount);

            // Pass rate
            int totalRuns = (Integer) stats.get("total_runs");
            int passed = (Integer) stats.get("passed");
            double passRate = totalRuns > 0 ? (passed * 100.0 / totalRuns) : 0;
            stats.put("pass_rate", Math.round(passRate * 10) / 10.0);

            // Failure rate
            int failed = (Integer) stats.get("failed");
            double failureRate = totalRuns > 0 ? (failed * 100.0 / totalRuns) : 0;
            stats.put("failure_rate", Math.round(failureRate * 10) / 10.0);

            // Projects overview
            List<Map<String, Object>> projects;
            if (AuthFilter.isAdmin(req)) {
                projects = ProjectDao.findAll();
            } else {
                projects = ProjectDao.findByUserId(AuthFilter.getUserId(req));
            }
            List<Map<String, Object>> projectsOverview = new ArrayList<>();
            for (Map<String, Object> p : projects) {
                Map<String, Object> overview = new HashMap<>();
                overview.put("id", p.get("id"));
                overview.put("name", p.get("name"));
                overview.put("recording_count", ProjectDao.getRecordingCount((Long) p.get("id")));
                projectsOverview.add(overview);
            }
            stats.put("projects_overview", projectsOverview);

            // Recent activity
            List<Map<String, Object>> recentRuns = RunDao.getRecentRuns(10, projectId, accessibleProjectIds);
            stats.put("recent_activity", recentRuns);

            return MAPPER.writeValueAsString(stats);
        });
    }
}
