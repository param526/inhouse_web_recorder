package com.example.routes;

import com.example.auth.AuthFilter;
import com.example.dao.SettingsDao;
import com.example.dao.UserDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.*;

public class SettingsRoutes {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void register() {

        get("/api/settings/sidebar", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }

            // Return all resource users with their sidebar settings
            List<Map<String, Object>> users = UserDao.findAll(false);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> user : users) {
                if ("RESOURCE".equals(user.get("role"))) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", user.get("id"));
                    entry.put("username", user.get("username"));
                    entry.put("sidebar", SettingsDao.getSidebarVisibility((Long) user.get("id")));
                    result.add(entry);
                }
            }
            return MAPPER.writeValueAsString(result);
        });

        put("/api/settings/sidebar/:userId", (req, res) -> {
            res.type("application/json");
            if (!AuthFilter.isAdmin(req)) {
                res.status(403);
                return "{\"error\":\"Admin access required\"}";
            }
            long userId = Long.parseLong(req.params(":userId"));
            JsonNode body = MAPPER.readTree(req.body());
            JsonNode settingsNode = body.get("settings");

            List<Map<String, Object>> settings = new ArrayList<>();
            if (settingsNode != null && settingsNode.isArray()) {
                for (JsonNode s : settingsNode) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("page_name", s.get("page_name").asText());
                    m.put("visible", s.get("visible").asBoolean());
                    settings.add(m);
                }
            }

            SettingsDao.saveSidebarSettings(userId, settings);
            return "{\"message\":\"Settings saved\"}";
        });
    }
}
