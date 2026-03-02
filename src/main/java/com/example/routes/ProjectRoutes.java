package com.example.routes;

import com.example.auth.AuthFilter;
import com.example.dao.ModuleDao;
import com.example.dao.ProjectDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static spark.Spark.*;

public class ProjectRoutes {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void register() {

        get("/api/projects", (req, res) -> {
            res.type("application/json");
            List<Map<String, Object>> projects;
            if (AuthFilter.isAdmin(req)) {
                projects = ProjectDao.findAll();
            } else {
                projects = ProjectDao.findByUserId(AuthFilter.getUserId(req));
            }
            // Attach recording count and modules
            for (Map<String, Object> p : projects) {
                long pid = (Long) p.get("id");
                p.put("recording_count", ProjectDao.getRecordingCount(pid));
                p.put("modules", ModuleDao.findByProjectId(pid));
                p.put("access_users", ProjectDao.getAccessUsers(pid));
            }
            return MAPPER.writeValueAsString(projects);
        });

        post("/api/projects", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }
            JsonNode body = MAPPER.readTree(req.body());
            String name = body.get("name").asText();
            String desc = body.has("description") ? body.get("description").asText() : null;
            Map<String, Object> project = ProjectDao.create(name, desc);
            return MAPPER.writeValueAsString(project);
        });

        put("/api/projects/:id", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }
            long id = Long.parseLong(req.params(":id"));
            JsonNode body = MAPPER.readTree(req.body());
            ProjectDao.update(id, body.get("name").asText(),
                    body.has("description") ? body.get("description").asText() : null);
            return MAPPER.writeValueAsString(ProjectDao.findById(id));
        });

        delete("/api/projects/:id", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }
            long id = Long.parseLong(req.params(":id"));
            ProjectDao.delete(id);
            return "{\"message\":\"Project deleted\"}";
        });

        // Modules
        get("/api/projects/:id/modules", (req, res) -> {
            res.type("application/json");
            long projectId = Long.parseLong(req.params(":id"));
            return MAPPER.writeValueAsString(ModuleDao.findByProjectId(projectId));
        });

        post("/api/projects/:id/modules", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }
            long projectId = Long.parseLong(req.params(":id"));
            JsonNode body = MAPPER.readTree(req.body());
            Map<String, Object> module = ModuleDao.create(projectId, body.get("name").asText(),
                    body.has("description") ? body.get("description").asText() : null);
            return MAPPER.writeValueAsString(module);
        });

        put("/api/modules/:id", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }
            long id = Long.parseLong(req.params(":id"));
            JsonNode body = MAPPER.readTree(req.body());
            ModuleDao.update(id, body.get("name").asText(),
                    body.has("description") ? body.get("description").asText() : null);
            return MAPPER.writeValueAsString(ModuleDao.findById(id));
        });

        delete("/api/modules/:id", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }
            long id = Long.parseLong(req.params(":id"));
            ModuleDao.delete(id);
            return "{\"message\":\"Module deleted\"}";
        });

        // Access management
        post("/api/projects/:id/access", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }
            long projectId = Long.parseLong(req.params(":id"));
            JsonNode body = MAPPER.readTree(req.body());
            long userId = body.get("user_id").asLong();
            ProjectDao.grantAccess(userId, projectId);
            return "{\"message\":\"Access granted\"}";
        });

        delete("/api/projects/:projectId/access/:userId", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }
            long projectId = Long.parseLong(req.params(":projectId"));
            long userId = Long.parseLong(req.params(":userId"));
            ProjectDao.revokeAccess(userId, projectId);
            return "{\"message\":\"Access revoked\"}";
        });
    }
}
