package com.example.routes;

import com.example.auth.AuthFilter;
import com.example.dao.RecordingDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static spark.Spark.*;

public class RecorderRoutes {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static WebDriver currentDriver;
    private static final List<Object> ALL_RECORDED_ACTIONS = new CopyOnWriteArrayList<>();
    private static volatile String currentSessionUrl = null;

    public static void register() {

        // Start recording session
        post("/api/recorder/start", (req, res) -> {
            res.type("application/json");
            JsonNode body = MAPPER.readTree(req.body());
            String url = body.has("url") ? body.get("url").asText() : "";

            if (url.isEmpty()) {
                res.status(400);
                return "{\"error\":\"URL is required\"}";
            }

            // Normalize URL
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            // Close any existing session
            closeCurrentDriver();
            ALL_RECORDED_ACTIONS.clear();

            try {
                openWithSeleniumAndRecorder(url);
                currentSessionUrl = url;
                return "{\"message\":\"Recording started\",\"url\":\"" + url + "\"}";
            } catch (Exception e) {
                res.status(500);
                return "{\"error\":\"Failed to open browser: " + e.getMessage().replace("\"", "'") + "\"}";
            }
        });

        // Stop and save recording
        post("/api/recorder/stop", (req, res) -> {
            res.type("application/json");

            // Transfer final actions
            if (currentDriver != null) {
                transferActionsFromBrowser(currentDriver);
            }

            String actionsJson = MAPPER.writeValueAsString(ALL_RECORDED_ACTIONS);
            int stepCount = ALL_RECORDED_ACTIONS.size();

            // Save to database if requested
            JsonNode body = req.body() != null && !req.body().isEmpty() ? MAPPER.readTree(req.body()) : null;
            if (body != null && body.has("name")) {
                String name = body.get("name").asText();
                String filename = body.has("filename") ? body.get("filename").asText() : name;
                Long projectId = body.has("project_id") && !body.get("project_id").isNull()
                        ? body.get("project_id").asLong() : null;
                Long moduleId = body.has("module_id") && !body.get("module_id").isNull()
                        ? body.get("module_id").asLong() : null;

                Map<String, Object> recording = RecordingDao.create(
                        name, filename, actionsJson, projectId, moduleId,
                        stepCount, actionsJson.length(), AuthFilter.getUserId(req));

                closeCurrentDriver();
                recording.put("steps_json", actionsJson);
                return MAPPER.writeValueAsString(recording);
            }

            closeCurrentDriver();
            return "{\"message\":\"Recording stopped\",\"steps\":" + actionsJson + ",\"step_count\":" + stepCount + "}";
        });

        // Abort recording (discard)
        post("/api/recorder/abort", (req, res) -> {
            res.type("application/json");
            closeCurrentDriver();
            ALL_RECORDED_ACTIONS.clear();
            return "{\"message\":\"Recording aborted\"}";
        });

        // Get recording status
        get("/api/recorder/status", (req, res) -> {
            res.type("application/json");
            if (currentDriver == null) {
                return "{\"active\":false,\"count\":0}";
            }
            try {
                JavascriptExecutor js = (JavascriptExecutor) currentDriver;
                Object eventsObj = js.executeScript("return (window.__recordedEvents || []);");
                int browserCount = 0;
                if (eventsObj instanceof List<?>) {
                    browserCount = ((List<?>) eventsObj).size();
                }
                int totalCount = ALL_RECORDED_ACTIONS.size() + browserCount;

                String lastAction = "";
                Object installed = js.executeScript("return !!window.__recordingInstalled;");
                String currentUrl = "";
                try { currentUrl = currentDriver.getCurrentUrl(); } catch (Exception ignored) {}

                return "{\"active\":true,\"installed\":" + installed
                        + ",\"count\":" + totalCount
                        + ",\"browser_count\":" + browserCount
                        + ",\"stored_count\":" + ALL_RECORDED_ACTIONS.size()
                        + ",\"url\":\"" + currentUrl.replace("\"", "'") + "\"}";
            } catch (WebDriverException e) {
                return "{\"active\":false,\"count\":" + ALL_RECORDED_ACTIONS.size() + "}";
            }
        });

        // Get live recorded steps
        get("/api/recorder/steps", (req, res) -> {
            res.type("application/json");
            if (currentDriver != null) {
                transferActionsFromBrowser(currentDriver);
            }
            return MAPPER.writeValueAsString(ALL_RECORDED_ACTIONS);
        });

        // Legacy endpoints for backwards compatibility
        post("/open", (req, res) -> {
            String rawUrl = req.queryParams("url");
            closeCurrentDriver();
            ALL_RECORDED_ACTIONS.clear();
            if (rawUrl == null || rawUrl.isBlank()) {
                res.status(400);
                return "{\"error\":\"URL is required\"}";
            }
            String finalUrl = rawUrl.trim();
            if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                finalUrl = "https://" + finalUrl;
            }
            openWithSeleniumAndRecorder(finalUrl);
            res.redirect("/");
            return null;
        });

        get("/quit", (req, res) -> {
            boolean closed = closeCurrentDriver();
            res.type("application/json");
            return closed ? "{\"message\":\"Closed\"}" : "{\"error\":\"No session\"}";
        });

        get("/recorder-status", (req, res) -> {
            res.type("application/json");
            if (currentDriver == null) {
                return "{\"installed\":false,\"count\":" + ALL_RECORDED_ACTIONS.size() + "}";
            }
            try {
                JavascriptExecutor js = (JavascriptExecutor) currentDriver;
                Object installed = js.executeScript("return !!window.__recordingInstalled;");
                Object eventsObj = js.executeScript("return (window.__recordedEvents || []);");
                int count = 0;
                if (eventsObj instanceof List<?>) count = ((List<?>) eventsObj).size();
                int total = ALL_RECORDED_ACTIONS.size() + count;
                return "{\"installed\":" + installed + ",\"count\":" + total + "}";
            } catch (Exception e) {
                return "{\"installed\":false,\"count\":" + ALL_RECORDED_ACTIONS.size() + "}";
            }
        });

        get("/recorded-actions", (req, res) -> {
            res.type("application/json");
            if (currentDriver != null) transferActionsFromBrowser(currentDriver);
            return MAPPER.writeValueAsString(ALL_RECORDED_ACTIONS);
        });

        get("/save-recorded-actions", (req, res) -> {
            if (currentDriver != null) transferActionsFromBrowser(currentDriver);
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ALL_RECORDED_ACTIONS);
            String fileName = req.queryParams("fileName");
            if (fileName == null || fileName.isBlank()) fileName = "recording.json";
            if (!fileName.endsWith(".json")) fileName += ".json";
            java.nio.file.Path dir = Paths.get("recordings");
            Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve(fileName);
            Files.writeString(file, json, StandardCharsets.UTF_8);
            res.type("text/plain");
            return file.toAbsolutePath().toString();
        });
    }

    private static void openWithSeleniumAndRecorder(String url) throws InterruptedException {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(90));
        driver.get(url);
        Thread.sleep(3000);
        currentDriver = driver;
        initialInjectionLoop(driver);
        startNavigationMonitor(driver);
    }

    private static void initialInjectionLoop(WebDriver driver) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        for (int i = 0; i < 10; i++) {
            try {
                js.executeScript(getRecorderScript());
                Object result = js.executeScript("return !!window.__recordingInstalled;");
                if (result instanceof Boolean && (Boolean) result) return;
                Thread.sleep(5000);
            } catch (Exception e) {
                Thread.sleep(5000);
            }
        }
    }

    private static void startNavigationMonitor(WebDriver driver) {
        new Thread(() -> {
            String lastUrl = "";
            try { lastUrl = driver.getCurrentUrl(); } catch (Exception ignored) {}
            while (true) {
                try {
                    transferActionsFromBrowser(driver);
                    String currentUrl = driver.getCurrentUrl();
                    if (!currentUrl.equals(lastUrl)) {
                        Thread.sleep(1000);
                        reInjectRecorder(driver);
                        lastUrl = currentUrl;
                    }
                    Thread.sleep(500);
                } catch (WebDriverException e) {
                    break;
                } catch (Exception e) {
                    break;
                }
            }
        }).start();
    }

    private static void reInjectRecorder(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        for (int i = 0; i < 3; i++) {
            try {
                js.executeScript(getRecorderScript());
                Object result = js.executeScript("return !!window.__recordingInstalled;");
                if (result instanceof Boolean && (Boolean) result) return;
                Thread.sleep(500);
            } catch (Exception e) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
    }

    private static void transferActionsFromBrowser(WebDriver driver) {
        if (driver == null) return;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            @SuppressWarnings("unchecked")
            List<Object> currentActions = (List<Object>) js.executeScript("return window.__recordedEvents || [];");
            if (currentActions != null && !currentActions.isEmpty()) {
                ALL_RECORDED_ACTIONS.addAll(currentActions);
                // Clear browser events AND localStorage to prevent re-loading on next page
                js.executeScript("window.__recordedEvents = []; try { localStorage.removeItem('__recordedEvents'); } catch(e) {}");
                // Sort all actions by timestamp and deduplicate
                sortAndDeduplicateActions();
            }
        } catch (WebDriverException ignored) {
        } catch (Exception e) {
            System.err.println("Error transferring actions: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void sortAndDeduplicateActions() {
        try {
            // Sort by timestamp
            ALL_RECORDED_ACTIONS.sort((a, b) -> {
                long tsA = getTimestamp(a);
                long tsB = getTimestamp(b);
                return Long.compare(tsA, tsB);
            });

            // Deduplicate: remove consecutive events with same signature
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            List<Object> deduped = new java.util.ArrayList<>();
            for (Object action : ALL_RECORDED_ACTIONS) {
                String sig = getActionSignature(action);
                if (seen.add(sig)) {
                    deduped.add(action);
                }
            }
            ALL_RECORDED_ACTIONS.clear();
            ALL_RECORDED_ACTIONS.addAll(deduped);
        } catch (Exception e) {
            System.err.println("Error sorting actions: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static long getTimestamp(Object action) {
        if (action instanceof Map) {
            Object ts = ((Map<String, Object>) action).get("timestamp");
            if (ts instanceof Number) return ((Number) ts).longValue();
        }
        return 0L;
    }

    @SuppressWarnings("unchecked")
    private static String getActionSignature(Object action) {
        if (action instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) action;
            String type = String.valueOf(m.getOrDefault("type", ""));
            String act = String.valueOf(m.getOrDefault("action", ""));
            String gherkin = String.valueOf(m.getOrDefault("raw_gherkin", ""));
            long ts = getTimestamp(action);
            return type + "|" + act + "|" + gherkin + "|" + ts;
        }
        return action.toString();
    }

    private static boolean closeCurrentDriver() {
        if (currentDriver != null) {
            try { transferActionsFromBrowser(currentDriver); } catch (Exception ignored) {}
            try { currentDriver.quit(); } catch (Exception ignored) {}
            currentDriver = null;
            currentSessionUrl = null;
            return true;
        }
        return false;
    }

    private static String getRecorderScript() {
        try {
            return new String(Files.readAllBytes(Paths.get("src/main/resources/recorder.js")), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not read recorder.js: " + e.getMessage());
            return null;
        }
    }
}
