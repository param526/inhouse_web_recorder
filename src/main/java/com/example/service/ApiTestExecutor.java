package com.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

public class ApiTestExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static ObjectNode executeTest(String testDataJson, String envVarsJson) {
        ObjectNode result = MAPPER.createObjectNode();
        long startTime = System.currentTimeMillis();

        try {
            JsonNode testData = MAPPER.readTree(testDataJson);
            JsonNode envVars = envVarsJson != null ? MAPPER.readTree(envVarsJson) : null;

            String baseUrl = substituteVars(getTextOrNull(testData, "base_url"), envVars);
            if (envVars != null && envVars.has("base_url")) {
                String envBase = envVars.get("base_url").asText();
                if (envBase != null && !envBase.isEmpty()) {
                    baseUrl = envBase;
                }
            }

            JsonNode requests = testData.get("requests");
            if (requests == null || !requests.isArray()) {
                result.put("status", "FAILED");
                result.put("error", "No requests defined in test");
                return result;
            }

            ArrayNode requestResults = MAPPER.createArrayNode();
            boolean allPassed = true;

            for (JsonNode reqDef : requests) {
                ObjectNode reqResult = executeRequest(reqDef, baseUrl, envVars);
                requestResults.add(reqResult);
                if (!"PASSED".equals(reqResult.get("status").asText())) {
                    allPassed = false;
                }
            }

            result.put("status", allPassed ? "PASSED" : "FAILED");
            result.set("requests", requestResults);

        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
        }

        result.put("duration_ms", System.currentTimeMillis() - startTime);
        return result;
    }

    private static ObjectNode executeRequest(JsonNode reqDef, String baseUrl, JsonNode envVars) {
        ObjectNode reqResult = MAPPER.createObjectNode();
        long start = System.currentTimeMillis();

        try {
            String method = reqDef.has("method") ? reqDef.get("method").asText("GET") : "GET";
            String url = substituteVars(getTextOrNull(reqDef, "url"), envVars);

            if (url != null && !url.startsWith("http") && baseUrl != null) {
                if (!baseUrl.endsWith("/") && !url.startsWith("/")) {
                    url = baseUrl + "/" + url;
                } else {
                    url = baseUrl + url;
                }
            }

            // Build query params
            if (reqDef.has("query_params") && reqDef.get("query_params").isArray()) {
                StringBuilder qp = new StringBuilder();
                for (JsonNode p : reqDef.get("query_params")) {
                    if (p.has("enabled") && !p.get("enabled").asBoolean()) continue;
                    String key = substituteVars(p.get("key").asText(), envVars);
                    String val = substituteVars(p.get("value").asText(), envVars);
                    qp.append(qp.length() == 0 ? "?" : "&");
                    qp.append(key).append("=").append(val);
                }
                url += qp.toString();
            }

            int timeout = reqDef.has("timeout") ? reqDef.get("timeout").asInt(30000) : 30000;

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeout));

            // Headers
            if (reqDef.has("headers") && reqDef.get("headers").isArray()) {
                for (JsonNode h : reqDef.get("headers")) {
                    if (h.has("enabled") && !h.get("enabled").asBoolean()) continue;
                    String key = substituteVars(h.get("key").asText(), envVars);
                    String val = substituteVars(h.get("value").asText(), envVars);
                    builder.header(key, val);
                }
            }

            // Authentication
            if (reqDef.has("auth") && reqDef.get("auth").isObject()) {
                JsonNode auth = reqDef.get("auth");
                String authType = auth.has("type") ? auth.get("type").asText() : "none";
                switch (authType) {
                    case "bearer":
                        builder.header("Authorization", "Bearer " + substituteVars(auth.get("token").asText(), envVars));
                        break;
                    case "basic":
                        String cred = substituteVars(auth.get("username").asText(), envVars) + ":" +
                                substituteVars(auth.get("password").asText(), envVars);
                        builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(cred.getBytes()));
                        break;
                    case "apikey":
                        String apiKey = substituteVars(auth.get("key").asText(), envVars);
                        String apiValue = substituteVars(auth.get("value").asText(), envVars);
                        String addTo = auth.has("add_to") ? auth.get("add_to").asText() : "header";
                        if ("query".equals(addTo)) {
                            url += (url.contains("?") ? "&" : "?") + apiKey + "=" + apiValue;
                            builder.uri(URI.create(url));
                        } else {
                            builder.header(apiKey, apiValue);
                        }
                        break;
                }
            }

            // Body
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
            if (reqDef.has("body") && reqDef.get("body").isObject()) {
                JsonNode body = reqDef.get("body");
                String bodyType = body.has("type") ? body.get("type").asText() : "none";
                switch (bodyType) {
                    case "json":
                        String jsonBody = substituteVars(body.get("content").asText(), envVars);
                        bodyPublisher = HttpRequest.BodyPublishers.ofString(jsonBody);
                        builder.header("Content-Type", "application/json");
                        break;
                    case "text":
                        bodyPublisher = HttpRequest.BodyPublishers.ofString(
                                substituteVars(body.get("content").asText(), envVars));
                        builder.header("Content-Type", "text/plain");
                        break;
                    case "form":
                        StringBuilder form = new StringBuilder();
                        if (body.has("fields") && body.get("fields").isArray()) {
                            for (JsonNode f : body.get("fields")) {
                                if (form.length() > 0) form.append("&");
                                form.append(substituteVars(f.get("key").asText(), envVars))
                                        .append("=")
                                        .append(substituteVars(f.get("value").asText(), envVars));
                            }
                        }
                        bodyPublisher = HttpRequest.BodyPublishers.ofString(form.toString());
                        builder.header("Content-Type", "application/x-www-form-urlencoded");
                        break;
                    case "raw":
                        bodyPublisher = HttpRequest.BodyPublishers.ofString(
                                substituteVars(body.get("content").asText(), envVars));
                        if (body.has("content_type")) {
                            builder.header("Content-Type", body.get("content_type").asText());
                        }
                        break;
                }
            }

            builder.method(method.toUpperCase(), bodyPublisher);

            HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            reqResult.put("status_code", response.statusCode());
            reqResult.put("body", response.body());
            reqResult.put("duration_ms", System.currentTimeMillis() - start);

            ObjectNode respHeaders = MAPPER.createObjectNode();
            response.headers().map().forEach((k, v) -> respHeaders.put(k, String.join(", ", v)));
            reqResult.set("headers", respHeaders);

            // Assertions
            boolean allAssertionsPassed = true;
            if (reqDef.has("assertions") && reqDef.get("assertions").isArray()) {
                ArrayNode assertionResults = MAPPER.createArrayNode();
                for (JsonNode assertion : reqDef.get("assertions")) {
                    if (assertion.has("enabled") && !assertion.get("enabled").asBoolean()) continue;
                    ObjectNode ar = evaluateAssertion(assertion, response, envVars);
                    assertionResults.add(ar);
                    if (!"PASSED".equals(ar.get("status").asText())) {
                        allAssertionsPassed = false;
                    }
                }
                reqResult.set("assertions", assertionResults);
            }

            reqResult.put("status", allAssertionsPassed ? "PASSED" : "FAILED");

        } catch (Exception e) {
            reqResult.put("status", "FAILED");
            reqResult.put("error", e.getMessage());
            reqResult.put("duration_ms", System.currentTimeMillis() - start);
        }

        return reqResult;
    }

    private static ObjectNode evaluateAssertion(JsonNode assertion, HttpResponse<String> response, JsonNode envVars) {
        ObjectNode result = MAPPER.createObjectNode();
        String target = assertion.get("target").asText();
        String operator = assertion.has("operator") ? assertion.get("operator").asText() : "equals";
        String expected = substituteVars(assertion.has("expected") ? assertion.get("expected").asText() : "", envVars);

        result.put("target", target);
        result.put("operator", operator);
        result.put("expected", expected);

        try {
            String actual;
            switch (target) {
                case "status_code":
                    actual = String.valueOf(response.statusCode());
                    break;
                case "body":
                    actual = response.body();
                    break;
                case "header":
                    String headerName = assertion.has("header_name") ? assertion.get("header_name").asText() : "";
                    actual = response.headers().firstValue(headerName.toLowerCase()).orElse("");
                    break;
                case "json_path":
                    String path = assertion.has("path") ? assertion.get("path").asText() : "$";
                    actual = evaluateJsonPath(response.body(), path);
                    break;
                default:
                    actual = "";
            }

            result.put("actual", actual);
            boolean passed;
            switch (operator) {
                case "equals":
                    passed = expected.equals(actual);
                    break;
                case "not_equals":
                    passed = !expected.equals(actual);
                    break;
                case "contains":
                    passed = actual != null && actual.contains(expected);
                    break;
                case "not_contains":
                    passed = actual == null || !actual.contains(expected);
                    break;
                case "greater_than":
                    passed = Double.parseDouble(actual) > Double.parseDouble(expected);
                    break;
                case "less_than":
                    passed = Double.parseDouble(actual) < Double.parseDouble(expected);
                    break;
                case "exists":
                    passed = actual != null && !actual.isEmpty();
                    break;
                default:
                    passed = expected.equals(actual);
            }

            result.put("status", passed ? "PASSED" : "FAILED");
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
        }

        return result;
    }

    private static String evaluateJsonPath(String json, String path) {
        try {
            JsonNode root = MAPPER.readTree(json);
            // Simple JSON path: $.field.subfield or $.array[0].field
            String[] parts = path.replace("$.", "").replace("$", "").split("\\.");
            JsonNode current = root;
            for (String part : parts) {
                if (part.isEmpty()) continue;
                if (part.contains("[") && part.contains("]")) {
                    String field = part.substring(0, part.indexOf("["));
                    int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));
                    if (!field.isEmpty()) current = current.get(field);
                    if (current != null && current.isArray()) current = current.get(index);
                } else {
                    current = current.get(part);
                }
                if (current == null) return null;
            }
            return current.isTextual() ? current.asText() : current.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String substituteVars(String input, JsonNode envVars) {
        if (input == null || envVars == null) return input;
        String result = input;
        if (envVars.has("variables") && envVars.get("variables").isArray()) {
            for (JsonNode v : envVars.get("variables")) {
                String key = v.has("key") ? v.get("key").asText() : "";
                String value = v.has("value") ? v.get("value").asText() : "";
                result = result.replace("{{" + key + "}}", value);
            }
        }
        // Also support flat key-value object
        if (envVars.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = envVars.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (entry.getValue().isTextual()) {
                    result = result.replace("{{" + entry.getKey() + "}}", entry.getValue().asText());
                }
            }
        }
        return result;
    }

    private static String getTextOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}
