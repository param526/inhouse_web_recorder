package com.example.routes;

import com.example.auth.AuthFilter;
import com.example.dao.SettingsDao;
import com.example.dao.UserDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static spark.Spark.*;

public class UserRoutes {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void register() {

        get("/api/users", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }
            boolean includeDeleted = "true".equals(req.queryParams("includeDeleted"));
            List<Map<String, Object>> users = UserDao.findAll(includeDeleted);
            return MAPPER.writeValueAsString(users);
        });

        post("/api/users", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }
            JsonNode body = MAPPER.readTree(req.body());
            String username = body.get("username").asText();
            String password = body.get("password").asText();
            String role = body.has("role") ? body.get("role").asText() : "RESOURCE";

            if (username.length() < 3 || password.length() < 6) {
                res.status(400);
                return "{\"error\":\"Username must be 3+ chars, password 6+ chars\"}";
            }
            if (UserDao.findByUsername(username) != null) {
                res.status(409);
                return "{\"error\":\"Username already exists\"}";
            }

            Map<String, Object> user = UserDao.create(username, password, role);
            return MAPPER.writeValueAsString(user);
        });

        put("/api/users/:id/role", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }
            long id = Long.parseLong(req.params(":id"));
            JsonNode body = MAPPER.readTree(req.body());
            String role = body.get("role").asText();
            UserDao.updateRole(id, role);
            return MAPPER.writeValueAsString(UserDao.findById(id));
        });

        put("/api/users/:id/status", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }
            long id = Long.parseLong(req.params(":id"));
            JsonNode body = MAPPER.readTree(req.body());
            String status = body.get("status").asText();
            UserDao.updateStatus(id, status);
            return MAPPER.writeValueAsString(UserDao.findById(id));
        });

        put("/api/users/:id/password", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            Long currentUserId = AuthFilter.getUserId(req);

            JsonNode body = MAPPER.readTree(req.body());
            String newPassword = body.get("new_password").asText();

            if (newPassword.length() < 6) {
                res.status(400);
                return "{\"error\":\"Password must be at least 6 characters\"}";
            }

            // Non-admin can only change own password and must verify current
            if (!AuthFilter.isAdmin(req) || id == currentUserId) {
                if (body.has("current_password")) {
                    String currentPassword = body.get("current_password").asText();
                    if (!UserDao.verifyPassword(id, currentPassword)) {
                        res.status(400);
                        return "{\"error\":\"Current password is incorrect\"}";
                    }
                } else if (!AuthFilter.isAdmin(req)) {
                    res.status(400);
                    return "{\"error\":\"Current password required\"}";
                }
            }

            UserDao.changePassword(id, newPassword);
            return "{\"message\":\"Password changed successfully\"}";
        });

        delete("/api/users/:id", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            Long currentUserId = AuthFilter.getUserId(req);

            // Users can delete themselves, admins can delete anyone
            if (id != currentUserId && !AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }

            UserDao.softDelete(id);
            return "{\"message\":\"User deleted\"}";
        });

        put("/api/users/:id/restore", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }
            long id = Long.parseLong(req.params(":id"));
            UserDao.restore(id);
            return MAPPER.writeValueAsString(UserDao.findById(id));
        });

        // Sidebar settings
        get("/api/users/:id/sidebar", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            return MAPPER.writeValueAsString(SettingsDao.getSidebarVisibility(id));
        });
    }
}
