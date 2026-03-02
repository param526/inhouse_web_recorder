package com.example;

import static spark.Spark.*;

import com.example.auth.AuthFilter;
import com.example.auth.JwtUtil;
import com.example.db.DatabaseManager;
import com.example.routes.*;
import com.example.util.AppConfig;
import com.example.util.EncryptionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UrlOpenerServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {

        // Load configuration
        AppConfig.load();

        // Initialize infrastructure
        EncryptionUtil.init();
        JwtUtil.init();
        DatabaseManager.init();

        // Configure Spark
        int serverPort = AppConfig.getInt("server.port", 4567);
        port(serverPort);
        staticFiles.location("/public");

        // CORS for SPA
        before((req, res) -> {
            res.header("Access-Control-Allow-Origin", "*");
            res.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });
        options("/*", (req, res) -> {
            res.status(200);
            return "OK";
        });

        // Auth filter for /api/* routes (except public ones)
        before("/api/*", new AuthFilter());

        // Root redirect to SPA
        get("/", (req, res) -> {
            res.redirect("/app.html");
            return null;
        });

        // Health check
        get("/api/health", (req, res) -> {
            res.type("application/json");
            return "{\"status\":\"ok\",\"version\":\"2.0.0\"}";
        });

        // Register all route modules
        AuthRoutes.register();
        UserRoutes.register();
        ProjectRoutes.register();
        RecordingRoutes.register();
        RecorderRoutes.register();
        ReplayRoutes.register();
        DashboardRoutes.register();
        ApiTestRoutes.register();
        SettingsRoutes.register();

        // Legacy endpoints
        get("/replay-status", (req, res) -> {
            res.type("application/json");
            return MAPPER.writeValueAsString(ReplayStatusHolder.get());
        });

        post("/replay-stop", (req, res) -> {
            RawSeleniumReplayer.stopCurrentReplay();
            res.status(200);
            return "Replay stopped";
        });

        get("/view-replay-report", (req, res) -> {
            String workingDir = System.getProperty("user.dir");
            String reportPath = workingDir + File.separator + "replay-recordings"
                    + File.separator + "raw_selenium_report.html";
            File reportFile = new File(reportPath);
            if (!reportFile.exists()) {
                res.status(404);
                return "Report not found. Run a replay first.";
            }
            res.type("text/html; charset=UTF-8");
            return Files.readString(reportFile.toPath(), StandardCharsets.UTF_8);
        });

        get("/screenshots/:fileName", (req, res) -> {
            String fileName = req.params(":fileName");
            Path imgPath = Path.of(System.getProperty("user.dir"),
                    "replay-recordings", "screenshots", fileName);
            if (!Files.exists(imgPath)) {
                res.status(404);
                return "Screenshot not found";
            }
            res.type("image/png");
            byte[] bytes = Files.readAllBytes(imgPath);
            res.raw().getOutputStream().write(bytes);
            res.raw().getOutputStream().flush();
            return res.raw();
        });

        // Gherkin generation (legacy)
        post("/generate-test", (req, res) -> {
            res.type("application/json");
            FeatureInfo info = MAPPER.readValue(req.body(), FeatureInfo.class);
            String featureName = info.getFeature();
            String scenarioName = info.getScenario();
            if (featureName == null || featureName.trim().isEmpty()
                    || scenarioName == null || scenarioName.trim().isEmpty()) {
                res.status(400);
                return "{\"error\":\"Both Feature and Scenario names are required.\"}";
            }
            return "{\"status\":\"Success\",\"message\":\"Feature file generated\"}";
        });

        // Recordings list (legacy)
        get("/recordings-list", (req, res) -> {
            res.type("application/json");
            Path dir = Paths.get("recordings");
            Files.createDirectories(dir);
            java.util.List<java.util.Map<String, String>> payload = new java.util.ArrayList<>();
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                        .forEach(p -> {
                            java.util.Map<String, String> row = new java.util.HashMap<>();
                            row.put("name", p.getFileName().toString().replace(".json", ""));
                            row.put("fileName", p.getFileName().toString());
                            payload.add(row);
                        });
            }
            return MAPPER.writeValueAsString(payload);
        });

        awaitInitialization();
        System.out.println("==============================================");
        System.out.println("  Selenium Recorder & Replayer v2.0.0");
        System.out.println("  Running on http://localhost:" + serverPort);
        System.out.println("==============================================");

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            DatabaseManager.shutdown();
        }));
    }

    public static String getRecorderScriptFromFile() {
        try {
            return new String(Files.readAllBytes(
                    Paths.get("src/main/resources/recorder.js")), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            System.err.println("Could not read recorder.js: " + e.getMessage());
            return null;
        }
    }
}
