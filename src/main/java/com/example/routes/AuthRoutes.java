package com.example.routes;

import com.example.auth.JwtUtil;
import com.example.dao.UserDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import static spark.Spark.*;

public class AuthRoutes {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void register() {

        post("/api/auth/register", (req, res) -> {
            res.type("application/json");
            JsonNode body = MAPPER.readTree(req.body());
            String username = body.has("username") ? body.get("username").asText() : "";
            String password = body.has("password") ? body.get("password").asText() : "";

            if (username.length() < 3 || username.length() > 50) {
                res.status(400);
                return "{\"error\":\"Username must be 3-50 characters\"}";
            }
            if (password.length() < 6) {
                res.status(400);
                return "{\"error\":\"Password must be at least 6 characters\"}";
            }

            if (UserDao.findByUsername(username) != null) {
                res.status(409);
                return "{\"error\":\"Username already exists\"}";
            }

            // First user gets ADMIN role
            long count = UserDao.countAll();
            String role = count == 0 ? "ADMIN" : "RESOURCE";

            Map<String, Object> user = UserDao.create(username, password, role);
            long userId = (Long) user.get("id");
            String token = JwtUtil.generateToken(userId, username, role);

            user.put("token", token);
            return MAPPER.writeValueAsString(user);
        });

        post("/api/auth/login", (req, res) -> {
            res.type("application/json");
            JsonNode body = MAPPER.readTree(req.body());
            String username = body.has("username") ? body.get("username").asText() : "";
            String password = body.has("password") ? body.get("password").asText() : "";

            Map<String, Object> user = UserDao.authenticate(username, password);
            if (user == null) {
                res.status(401);
                return "{\"error\":\"Invalid credentials or account inactive\"}";
            }

            long userId = (Long) user.get("id");
            String role = (String) user.get("role");
            String token = JwtUtil.generateToken(userId, username, role);

            user.put("token", token);
            return MAPPER.writeValueAsString(user);
        });

        get("/api/auth/me", (req, res) -> {
            res.type("application/json");
            Long userId = req.attribute("userId");
            Map<String, Object> user = UserDao.findById(userId);
            if (user == null) {
                res.status(404);
                return "{\"error\":\"User not found\"}";
            }
            return MAPPER.writeValueAsString(user);
        });
    }
}
