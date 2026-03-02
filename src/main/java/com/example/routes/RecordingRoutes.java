package com.example.routes;

import com.example.auth.AuthFilter;
import com.example.dao.ProjectDao;
import com.example.dao.RecordingDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static spark.Spark.*;

public class RecordingRoutes {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void register() {

        get("/api/recordings", (req, res) -> {
            res.type("application/json");
            boolean includeDeleted = "true".equals(req.queryParams("includeDeleted"));
            String projectIdStr = req.queryParams("project_id");
            String moduleIdStr = req.queryParams("module_id");
            Long projectId = projectIdStr != null && !projectIdStr.isEmpty() ? Long.parseLong(projectIdStr) : null;
            Long moduleId = moduleIdStr != null && !moduleIdStr.isEmpty() ? Long.parseLong(moduleIdStr) : null;

            List<Map<String, Object>> recordings;
            if (AuthFilter.isAdmin(req)) {
                recordings = RecordingDao.findAll(includeDeleted, projectId, moduleId);
            } else {
                List<Map<String, Object>> projects = ProjectDao.findByUserId(AuthFilter.getUserId(req));
                List<Long> projectIds = projects.stream()
                        .map(p -> (Long) p.get("id"))
                        .collect(Collectors.toList());
                if (projectId != null) {
                    if (!projectIds.contains(projectId)) {
                        res.status(403);
                        return "{\"error\":\"Access denied to this project\"}";
                    }
                    recordings = RecordingDao.findAll(includeDeleted, projectId, moduleId);
                } else {
                    recordings = RecordingDao.findByProjectIds(projectIds, includeDeleted);
                }
            }
            return MAPPER.writeValueAsString(recordings);
        });

        get("/api/recordings/:id", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            Map<String, Object> recording = RecordingDao.findById(id);
            if (recording == null) {
                res.status(404);
                return "{\"error\":\"Recording not found\"}";
            }
            return MAPPER.writeValueAsString(recording);
        });

        get("/api/recordings/:id/steps", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            String stepsJson = RecordingDao.getStepsJson(id);
            if (stepsJson == null) {
                res.status(404);
                return "{\"error\":\"Recording not found\"}";
            }
            return stepsJson;
        });

        post("/api/recordings", (req, res) -> {
            res.type("application/json");
            JsonNode body = MAPPER.readTree(req.body());
            String name = body.get("name").asText();
            String filename = body.has("filename") ? body.get("filename").asText() : name;
            String stepsJson = body.has("steps") ? MAPPER.writeValueAsString(body.get("steps")) : "[]";
            Long projectId = body.has("project_id") && !body.get("project_id").isNull()
                    ? body.get("project_id").asLong() : null;
            Long moduleId = body.has("module_id") && !body.get("module_id").isNull()
                    ? body.get("module_id").asLong() : null;

            JsonNode stepsNode = body.get("steps");
            int stepCount = stepsNode != null && stepsNode.isArray() ? stepsNode.size() : 0;
            long fileSize = stepsJson.length();

            Map<String, Object> recording = RecordingDao.create(
                    name, filename, stepsJson, projectId, moduleId,
                    stepCount, fileSize, AuthFilter.getUserId(req));
            return MAPPER.writeValueAsString(recording);
        });

        put("/api/recordings/:id/assign", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            JsonNode body = MAPPER.readTree(req.body());
            Long projectId = body.has("project_id") && !body.get("project_id").isNull()
                    ? body.get("project_id").asLong() : null;
            Long moduleId = body.has("module_id") && !body.get("module_id").isNull()
                    ? body.get("module_id").asLong() : null;
            RecordingDao.updateAssignment(id, projectId, moduleId);
            return MAPPER.writeValueAsString(RecordingDao.findById(id));
        });

        put("/api/recordings/:id", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            JsonNode body = MAPPER.readTree(req.body());
            String name = body.has("name") ? body.get("name").asText() : null;
            String desc = body.has("description") ? body.get("description").asText() : null;
            if (name != null) {
                RecordingDao.updateName(id, name, desc);
            }
            if (body.has("steps")) {
                String stepsJson = MAPPER.writeValueAsString(body.get("steps"));
                int stepCount = body.get("steps").isArray() ? body.get("steps").size() : 0;
                RecordingDao.updateSteps(id, stepsJson, stepCount);
            }
            return MAPPER.writeValueAsString(RecordingDao.findById(id));
        });

        put("/api/recordings/:id/gherkin", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            JsonNode body = MAPPER.readTree(req.body());
            String gherkinText = body.get("gherkin_text").asText();
            RecordingDao.updateGherkin(id, gherkinText);
            return "{\"message\":\"Gherkin saved\"}";
        });

        delete("/api/recordings/:id", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            RecordingDao.softDelete(id);
            return "{\"message\":\"Recording deleted\"}";
        });

        put("/api/recordings/:id/restore", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            RecordingDao.restore(id);
            return MAPPER.writeValueAsString(RecordingDao.findById(id));
        });
    }
}
