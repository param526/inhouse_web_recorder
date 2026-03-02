package com.example.routes;

import com.example.auth.AuthFilter;
import com.example.dao.ApiTestDao;
import com.example.dao.ProjectDao;
import com.example.service.ApiTestExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static spark.Spark.*;

public class ApiTestRoutes {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void register() {

        // --- API Tests CRUD ---
        get("/api/api-tests", (req, res) -> {
            res.type("application/json");
            boolean includeDeleted = "true".equals(req.queryParams("includeDeleted"));
            String projectIdStr = req.queryParams("project_id");
            Long projectId = projectIdStr != null && !projectIdStr.isEmpty() ? Long.parseLong(projectIdStr) : null;

            List<Map<String, Object>> tests;
            if (AuthFilter.isAdmin(req)) {
                tests = ApiTestDao.findAll(includeDeleted, projectId);
            } else {
                var projects = ProjectDao.findByUserId(AuthFilter.getUserId(req));
                var projectIds = projects.stream().map(p -> (Long) p.get("id")).collect(Collectors.toList());
                tests = ApiTestDao.findByProjectIds(projectIds, includeDeleted);
            }
            return MAPPER.writeValueAsString(tests);
        });

        get("/api/api-tests/:id", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            Map<String, Object> test = ApiTestDao.findById(id);
            if (test == null) { res.status(404); return "{\"error\":\"API test not found\"}"; }
            return MAPPER.writeValueAsString(test);
        });

        post("/api/api-tests", (req, res) -> {
            res.type("application/json");
            JsonNode body = MAPPER.readTree(req.body());
            Map<String, Object> test = ApiTestDao.create(
                    body.get("name").asText(),
                    body.has("description") ? body.get("description").asText() : null,
                    body.has("project_id") && !body.get("project_id").isNull() ? body.get("project_id").asLong() : null,
                    body.has("module_id") && !body.get("module_id").isNull() ? body.get("module_id").asLong() : null,
                    body.has("base_url") ? body.get("base_url").asText() : null,
                    body.has("test_data") ? MAPPER.writeValueAsString(body.get("test_data")) : "{}",
                    AuthFilter.getUserId(req));
            return MAPPER.writeValueAsString(test);
        });

        put("/api/api-tests/:id", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            JsonNode body = MAPPER.readTree(req.body());
            ApiTestDao.update(id,
                    body.get("name").asText(),
                    body.has("description") ? body.get("description").asText() : null,
                    body.has("project_id") && !body.get("project_id").isNull() ? body.get("project_id").asLong() : null,
                    body.has("module_id") && !body.get("module_id").isNull() ? body.get("module_id").asLong() : null,
                    body.has("base_url") ? body.get("base_url").asText() : null,
                    body.has("test_data") ? MAPPER.writeValueAsString(body.get("test_data")) : "{}");
            return MAPPER.writeValueAsString(ApiTestDao.findById(id));
        });

        delete("/api/api-tests/:id", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            ApiTestDao.softDelete(id);
            return "{\"message\":\"API test deleted\"}";
        });

        put("/api/api-tests/:id/restore", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            ApiTestDao.restore(id);
            return MAPPER.writeValueAsString(ApiTestDao.findById(id));
        });

        // --- Run API Test ---
        post("/api/api-tests/:id/run", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            Map<String, Object> test = ApiTestDao.findById(id);
            if (test == null) { res.status(404); return "{\"error\":\"API test not found\"}"; }

            String envVarsJson = null;
            if (req.body() != null && !req.body().isEmpty()) {
                JsonNode body = MAPPER.readTree(req.body());
                if (body.has("environment")) {
                    envVarsJson = MAPPER.writeValueAsString(body.get("environment"));
                }
            }

            ObjectNode result = ApiTestExecutor.executeTest((String) test.get("test_data"), envVarsJson);
            long durationMs = result.has("duration_ms") ? result.get("duration_ms").asLong() : 0;
            String status = result.get("status").asText();

            long runId = ApiTestDao.createRun(id, status, MAPPER.writeValueAsString(result),
                    durationMs, AuthFilter.getUserId(req));
            result.put("run_id", runId);
            return MAPPER.writeValueAsString(result);
        });

        get("/api/api-tests/:id/runs", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            int limit = 20;
            try { limit = Integer.parseInt(req.queryParams("limit")); } catch (Exception ignored) {}
            return MAPPER.writeValueAsString(ApiTestDao.getTestRuns(id, limit));
        });

        // --- Suites ---
        get("/api/api-suites", (req, res) -> {
            res.type("application/json");
            boolean includeDeleted = "true".equals(req.queryParams("includeDeleted"));
            String projectIdStr = req.queryParams("project_id");
            Long projectId = projectIdStr != null && !projectIdStr.isEmpty() ? Long.parseLong(projectIdStr) : null;
            return MAPPER.writeValueAsString(ApiTestDao.findAllSuites(includeDeleted, projectId));
        });

        post("/api/api-suites", (req, res) -> {
            res.type("application/json");
            JsonNode body = MAPPER.readTree(req.body());
            Map<String, Object> suite = ApiTestDao.createSuite(
                    body.get("name").asText(),
                    body.has("description") ? body.get("description").asText() : null,
                    body.has("project_id") && !body.get("project_id").isNull() ? body.get("project_id").asLong() : null,
                    body.has("test_ids") ? MAPPER.writeValueAsString(body.get("test_ids")) : "[]",
                    AuthFilter.getUserId(req));
            return MAPPER.writeValueAsString(suite);
        });

        put("/api/api-suites/:id", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            JsonNode body = MAPPER.readTree(req.body());
            ApiTestDao.updateSuite(id,
                    body.get("name").asText(),
                    body.has("description") ? body.get("description").asText() : null,
                    body.has("project_id") && !body.get("project_id").isNull() ? body.get("project_id").asLong() : null,
                    body.has("test_ids") ? MAPPER.writeValueAsString(body.get("test_ids")) : "[]");
            return MAPPER.writeValueAsString(ApiTestDao.findSuiteById(id));
        });

        delete("/api/api-suites/:id", (req, res) -> {
            res.type("application/json");
            ApiTestDao.deleteSuite(Long.parseLong(req.params(":id")));
            return "{\"message\":\"Suite deleted\"}";
        });

        // Run suite
        post("/api/api-suites/:id/run", (req, res) -> {
            res.type("application/json");
            long suiteId = Long.parseLong(req.params(":id"));
            Map<String, Object> suite = ApiTestDao.findSuiteById(suiteId);
            if (suite == null) { res.status(404); return "{\"error\":\"Suite not found\"}"; }

            String envVarsJson = null;
            if (req.body() != null && !req.body().isEmpty()) {
                JsonNode body = MAPPER.readTree(req.body());
                if (body.has("environment")) envVarsJson = MAPPER.writeValueAsString(body.get("environment"));
            }

            JsonNode testIdsNode = MAPPER.readTree((String) suite.get("test_ids"));
            List<ObjectNode> results = new ArrayList<>();
            boolean allPassed = true;
            long totalDuration = 0;

            for (JsonNode testIdNode : testIdsNode) {
                long testId = testIdNode.asLong();
                Map<String, Object> test = ApiTestDao.findById(testId);
                if (test == null) continue;

                ObjectNode result = ApiTestExecutor.executeTest((String) test.get("test_data"), envVarsJson);
                result.put("test_id", testId);
                result.put("test_name", (String) test.get("name"));
                results.add(result);

                if (!"PASSED".equals(result.get("status").asText())) allPassed = false;
                totalDuration += result.has("duration_ms") ? result.get("duration_ms").asLong() : 0;
            }

            String overallStatus = allPassed ? "PASSED" : "FAILED";
            long suiteRunId = ApiTestDao.createSuiteRun(suiteId, overallStatus,
                    MAPPER.writeValueAsString(results), totalDuration, AuthFilter.getUserId(req));

            var response = MAPPER.createObjectNode();
            response.put("suite_run_id", suiteRunId);
            response.put("status", overallStatus);
            response.put("duration_ms", totalDuration);
            response.set("results", MAPPER.valueToTree(results));
            return MAPPER.writeValueAsString(response);
        });

        get("/api/api-suites/:id/runs", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            int limit = 20;
            try { limit = Integer.parseInt(req.queryParams("limit")); } catch (Exception ignored) {}
            return MAPPER.writeValueAsString(ApiTestDao.getSuiteRuns(id, limit));
        });

        // --- Environments ---
        get("/api/environments", (req, res) -> {
            res.type("application/json");
            String projectIdStr = req.queryParams("project_id");
            Long projectId = projectIdStr != null && !projectIdStr.isEmpty() ? Long.parseLong(projectIdStr) : null;
            return MAPPER.writeValueAsString(ApiTestDao.findAllEnvironments(projectId));
        });

        post("/api/environments", (req, res) -> {
            res.type("application/json");
            JsonNode body = MAPPER.readTree(req.body());
            Map<String, Object> env = ApiTestDao.createEnvironment(
                    body.get("name").asText(),
                    body.has("base_url") ? body.get("base_url").asText() : null,
                    body.has("project_id") && !body.get("project_id").isNull() ? body.get("project_id").asLong() : null,
                    body.has("is_default") && body.get("is_default").asBoolean(),
                    AuthFilter.getUserId(req));

            if (body.has("variables")) {
                List<Map<String, String>> vars = new ArrayList<>();
                for (JsonNode v : body.get("variables")) {
                    Map<String, String> m = new java.util.HashMap<>();
                    m.put("key", v.get("key").asText());
                    m.put("value", v.get("value").asText());
                    vars.add(m);
                }
                ApiTestDao.setEnvironmentVariables((Long) env.get("id"), vars);
            }
            return MAPPER.writeValueAsString(ApiTestDao.findEnvironmentById((Long) env.get("id")));
        });

        put("/api/environments/:id", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            JsonNode body = MAPPER.readTree(req.body());
            ApiTestDao.updateEnvironment(id,
                    body.get("name").asText(),
                    body.has("base_url") ? body.get("base_url").asText() : null,
                    body.has("project_id") && !body.get("project_id").isNull() ? body.get("project_id").asLong() : null,
                    body.has("is_default") && body.get("is_default").asBoolean());

            if (body.has("variables")) {
                List<Map<String, String>> vars = new ArrayList<>();
                for (JsonNode v : body.get("variables")) {
                    Map<String, String> m = new java.util.HashMap<>();
                    m.put("key", v.get("key").asText());
                    m.put("value", v.get("value").asText());
                    vars.add(m);
                }
                ApiTestDao.setEnvironmentVariables(id, vars);
            }
            return MAPPER.writeValueAsString(ApiTestDao.findEnvironmentById(id));
        });

        delete("/api/environments/:id", (req, res) -> {
            res.type("application/json");
            ApiTestDao.deleteEnvironment(Long.parseLong(req.params(":id")));
            return "{\"message\":\"Environment deleted\"}";
        });
    }
}
