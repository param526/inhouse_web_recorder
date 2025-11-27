package com.example;

import static spark.Spark.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.WebDriverException;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UrlOpenerServer {

    private static WebDriver currentDriver;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Persistent list to store ALL actions across page navigations and closures
    private static final List<Object> ALL_RECORDED_ACTIONS = new CopyOnWriteArrayList<>();
    private static final Path RECORDINGS_DIR = Paths.get("recordings");

    public static void main(String[] args) {

        port(4567);
        staticFiles.location("/public");

//        RecorderServer.startServer();

        get("/", (req, res) -> {
            res.redirect("/index.html");
            return null;
        });

        // Define the endpoint the UI will hit when the user clicks 'Generate Test'
        post("/generate-test", (request, response) -> {
            ObjectMapper mapper = new ObjectMapper();
            FeatureInfo info = mapper.readValue(request.body(), FeatureInfo.class);

            String featureName = info.getFeature();
            String scenarioName = info.getScenario();
            String recordingName = info.getRecording(); // logical name from dropdown (e.g. "user_login_flow")

            if (featureName == null || featureName.trim().isEmpty()
                    || scenarioName == null || scenarioName.trim().isEmpty()) {
                response.status(400);
                response.type("application/json");
                return "{\"status\":\"Error\",\"message\":\"Both Feature and Scenario names are required.\"}";
            }

            // 🔥 Resolve JSON path for that recording (same logic you use in /replay)
            String jsonPath = resolveRecordingJsonPath(recordingName);

            GherkinGenerator.generateFeatureFile(featureName.trim(), scenarioName.trim(), jsonPath);

            response.type("application/json");
            return "{\"status\":\"Success\",\"message\":\"Feature file generated for: " + scenarioName + "\"}";
        });


        // Ensure the server is ready to handle requests
        awaitInitialization();
//        System.out.println("Gherkin Generator Endpoint ready at http://localhost:4567/generate-test");

        // Open URL in new Selenium browser session + inject recorder
        post("/open", (req, res) -> {
            String rawUrl = req.queryParams("url");

            // Close any existing driver and clear historical data for a new session
            closeCurrentDriver();
            ALL_RECORDED_ACTIONS.clear();

            if (rawUrl == null || rawUrl.isBlank()) {
                return html("URL is required.", true);
            }

            String finalUrl = normalize(rawUrl);

            try {
                openWithSeleniumAndRecorder(finalUrl);
            } catch (Exception e) {
                e.printStackTrace();
                return html("Failed to open browser: " + e.getMessage(), true);
            }

            res.redirect("/index.html");
            return null;
        });

        // Quit the current Selenium browser session
        get("/quit", (req, res) -> {
            boolean closed = closeCurrentDriver();
            if (closed) {
                return html("Browser session successfully closed.", false);
            }
            res.status(400);
            return html("No active browser session to close.", true);
        });

        // Check recorder status
        get("/recorder-status", (req, res) -> {
            if (currentDriver == null) {
                res.status(400);
                return "No active browser session.";
            }
            try {
                JavascriptExecutor js = (JavascriptExecutor) currentDriver;

                Object installed = js.executeScript("return !!window.__recordingInstalled;");

                Object eventsObj = js.executeScript("return (window.__recordedEvents || []);");
                int currentCount = 0;
                String lastRawFromBrowser = null;

                if (eventsObj instanceof java.util.List<?>) {
                    java.util.List<?> browserEvents = (java.util.List<?>) eventsObj;
                    currentCount = browserEvents.size();

                    if (!browserEvents.isEmpty()) {
                        Object lastEvent = browserEvents.get(browserEvents.size() - 1);
                        if (lastEvent instanceof java.util.Map<?, ?>) {
                            Object rg = ((java.util.Map<?, ?>) lastEvent).get("raw_gherkin");
                            if (rg != null) {
                                lastRawFromBrowser = rg.toString();
                            }
                        }
                    }
                }

                int totalCount = ALL_RECORDED_ACTIONS.size() + currentCount;

                // ✅ SAFE last_raw_gherkin extraction
                String lastRawOverall = lastRawFromBrowser;

                if (lastRawOverall == null && !ALL_RECORDED_ACTIONS.isEmpty()) {
                    Object lastStored = ALL_RECORDED_ACTIONS.get(ALL_RECORDED_ACTIONS.size() - 1);

                    if (lastStored instanceof com.example.RecordedEvent) {
                        com.example.RecordedEvent ev = (com.example.RecordedEvent) lastStored;
                        if (ev.getRaw_gherkin() != null) {
                            lastRawOverall = ev.getRaw_gherkin();
                        }
                    } else if (lastStored instanceof java.util.Map<?, ?>) {
                        java.util.Map<?, ?> m = (java.util.Map<?, ?>) lastStored;
                        Object rg = m.get("raw_gherkin");
                        if (rg != null) {
                            lastRawOverall = rg.toString();
                        }
                    } else if (lastStored instanceof String) {
                        lastRawOverall = (String) lastStored;
                    }
                }

                RecorderServer.Status status = new RecorderServer.Status(
                        (installed instanceof Boolean) && (Boolean) installed,
                        totalCount,
                        lastRawOverall
                );

                String json = MAPPER.writeValueAsString(status);
                res.type("application/json");
                return json;

            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return "Error checking recorder status: " + e.getMessage();
            }
        });

        // Read recorded actions (in-memory) - Reads from persistent list
        get("/recorded-actions", (req, res) -> {
            try {
                // Read and transfer any pending actions from the browser before returning the list
                if (currentDriver != null) {
                    transferActionsFromBrowserToHistory(currentDriver);
                }

                String json = MAPPER.writeValueAsString(ALL_RECORDED_ACTIONS);
                res.type("application/json");
                return json;
            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return "Error reading recorded actions: " + e.getMessage();
            }
        });

        // Save to JSON file - Saves the persistent list
        get("/save-recorded-actions", (req, res) -> {
            try {
                // 1) Get requested filename from query param (sent by JS)
                String requestedFileName = req.queryParams("fileName");

                // 2) Sanitize and normalize
                String fileName = sanitizeFileName(requestedFileName);

                // 3) Pull any remaining actions from browser into ALL_RECORDED_ACTIONS
                if (currentDriver != null) {
                    transferActionsFromBrowserToHistory(currentDriver);
                }

                // 4) Serialize ALL_RECORDED_ACTIONS
                String json = MAPPER
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(ALL_RECORDED_ACTIONS);

                // 5) Ensure "recordings" directory exists
                Path dir = Paths.get("recordings");
                Files.createDirectories(dir);

                // 6) Save using EXACTLY the name from the UI
                Path file = dir.resolve(fileName);
                Files.writeString(file, json, StandardCharsets.UTF_8);

                res.type("text/plain");
                // Return the absolute path so UI can display it
                return file.toAbsolutePath().toString();

            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return "Error saving recorded actions: " + e.getMessage();
            }
        });


        post("/replay", (req, res) -> {
            System.out.println("Received /replay request");
            try {
                String workingDir = System.getProperty("user.dir");
                System.out.println("Working directory: " + workingDir);

                String recordingParam = req.queryParams("recording"); // from UI dropdown
                File jsonFile;

                if (recordingParam != null && !recordingParam.isBlank()) {
                    // Use the latest JSON for that recording
                    jsonFile = resolveLatestJsonForRecording(recordingParam.trim());
                } else {
                    // Fallback: original action_logs.json
                    String jsonPath = workingDir
                            + File.separator + "recordings"
                            + File.separator + "action_logs.json";
                    jsonFile = new File(jsonPath);
                }

                if (jsonFile == null || !jsonFile.exists()) {
                    String msg = "JSON file not found for recording: " + recordingParam;
                    System.err.println(msg);
                    res.status(404);
                    res.type("text/plain");
                    return msg;
                }

                System.out.println("Replay JSON path: " + jsonFile.getAbsolutePath());

                String reportDirPath = workingDir + File.separator + "replay-recordings";
                File reportDir = new File(reportDirPath);
                if (!reportDir.exists()) {
                    reportDir.mkdirs();
                }

                String reportPath = reportDirPath + File.separator + "raw_selenium_report.html";

                boolean ok = RawSeleniumReplayer.replayFromJson(
                        jsonFile.getAbsolutePath(),
                        reportPath
                );

                res.type("text/plain");
                if (ok) {
                    res.status(200);
                    return "OK";
                } else {
                    res.status(500);
                    return "Replay had failures. Report is still available at /view-replay-report";
                }

            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                res.type("text/plain");
                return "Replay failed: " + e.toString();
            }
        });

        get("/view-replay-report", (req, res) -> {
            String workingDir = System.getProperty("user.dir");
            Path reportPath = Path.of(
                    workingDir,
                    "replay-recordings",          // or "reports" if that's your folder
                    "raw_selenium_report.html"
            );

            if (!Files.exists(reportPath)) {
                res.status(404);
                res.type("text/plain");
                return "Replay report not found. Run /replay first.";
            }

            res.status(200);
            res.type("text/html");
            return Files.readString(reportPath, StandardCharsets.UTF_8);
        });

        get("/screenshots/:fileName", (req, res) -> {
            String fileName = req.params(":fileName");

            String workingDir = System.getProperty("user.dir");
            Path imgPath = Path.of(
                    workingDir,
                    "replay-recordings",
                    "screenshots",
                    fileName
            );

            if (!Files.exists(imgPath)) {
                res.status(404);
                res.type("text/plain");
                return "Screenshot not found: " + fileName;
            }

            // Basic content type – if you only save PNGs, this is fine
            res.status(200);
            res.type("image/png");

            // Stream bytes
            byte[] bytes = Files.readAllBytes(imgPath);
            res.raw().getOutputStream().write(bytes);
            res.raw().getOutputStream().flush();
            return res.raw();
        });

        get("/recordings-list", (req, res) -> {
            try {
                Files.createDirectories(RECORDINGS_DIR);

                Map<String, File> latestByName = new HashMap<>();

                try (Stream<Path> stream = Files.list(RECORDINGS_DIR)) {
                    stream
                            .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json"))
                            .forEach(p -> {
                                File f = p.toFile();
                                String fileName = f.getName();
                                String recordingName = extractRecordingName(fileName);
                                if (recordingName == null || recordingName.isBlank()) return;

                                File existing = latestByName.get(recordingName);
                                if (existing == null || f.lastModified() > existing.lastModified()) {
                                    latestByName.put(recordingName, f);
                                }
                            });
                }

                List<Map<String, String>> payload = new ArrayList<>();
                for (Map.Entry<String, File> e : latestByName.entrySet()) {
                    Map<String, String> row = new HashMap<>();
                    row.put("name", e.getKey());              // logical recording name
                    row.put("fileName", e.getValue().getName()); // actual JSON filename
                    payload.add(row);
                }

                // sort alphabetically by name if you like
                payload.sort(Comparator.comparing(m -> m.getOrDefault("name", "")));

                res.type("application/json");
                return MAPPER.writeValueAsString(payload);

            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                res.type("text/plain");
                return "Error listing recordings: " + e.getMessage();
            }
        });



        System.out.println("Selenium URL Launcher + Recorder running on http://localhost:4567");

//        RecorderServer.startServer();
    }

    private static String html(String msg, boolean error) {
        String color = error ? "red" : "green";
        return "<html><body><p style='color:" + color + "'>" + msg +
                "</p><a href='/index.html'>Back</a></body></html>";
    }

    // Ensure we don't allow path traversal and always end with .json
    private static String sanitizeFileName(String rawName) {
        String base = (rawName == null || rawName.isBlank())
                ? "recording"
                : rawName.trim();

        // Remove any path characters
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_");

        // Be sure it ends with .json
        if (!base.toLowerCase().endsWith(".json")) {
            base = base + ".json";
        }
        return base;
    }

    /**
     * Extract a logical "recording name" from a filename.
     * - "user_login_flow_20251127_153607.json" -> "user_login_flow"
     * - "plain_name.json" -> "plain_name"
     */
    private static String extractRecordingName(String fileName) {
        if (fileName == null || !fileName.endsWith(".json")) return null;

        String base = fileName.substring(0, fileName.length() - 5); // strip .json
        String[] parts = base.split("_");
        if (parts.length >= 3) {
            String last = parts[parts.length - 1];
            String secondLast = parts[parts.length - 2];
            if (last.matches("\\d{6}") && secondLast.matches("\\d{8}")) {
                // drop last two parts (timestamp)
                return String.join("_", Arrays.copyOf(parts, parts.length - 2));
            }
        }
        return base;
    }

    /**
     * Find the latest JSON file for a recording name (by lastModified).
     */
    private static File resolveLatestJsonForRecording(String recordingName) throws IOException {
        Files.createDirectories(RECORDINGS_DIR);

        try (Stream<Path> stream = Files.list(RECORDINGS_DIR)) {
            return stream
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json"))
                    .map(Path::toFile)
                    .filter(f -> {
                        String baseName = extractRecordingName(f.getName());
                        return recordingName.equals(baseName);
                    })
                    .max(Comparator.comparingLong(File::lastModified))
                    .orElse(null);
        }
    }

    private static String normalize(String input) {
        String url = input.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        return url;
    }

    // Helper to close the current driver instance
    private static boolean closeCurrentDriver() {
        if (currentDriver != null) {
            try {
                // Attempt to transfer current page actions before quitting the driver
                transferActionsFromBrowserToHistory(currentDriver);
            } catch (WebDriverException ignored) {
                // Ignore if the driver is already closed and the transfer failed
            }

            try {
                currentDriver.quit();
                currentDriver = null;
                System.out.println("Closed active WebDriver session.");
                return true;
            } catch (Exception e) {
                // Catch exceptions if quit is called on an already terminated driver
                System.err.println("Error during driver quit: " + e.getMessage());
                currentDriver = null;
                return true;
            }
        }
        return false;
    }

    // Transfers recorded actions from the browser to the persistent Java list
    private static void transferActionsFromBrowserToHistory(WebDriver driver) {
        if (driver == null) return;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Read the current page's recorded events
            @SuppressWarnings("unchecked")
            List<Object> currentActions = (List<Object>) js.executeScript("return window.__recordedEvents || [];");

            // Add them to the persistent list
            if (currentActions != null && !currentActions.isEmpty()) {
                ALL_RECORDED_ACTIONS.addAll(currentActions);
                System.out.println("Transferred " + currentActions.size() + " actions to history. Total actions: " + ALL_RECORDED_ACTIONS.size());

                // Clear the array in the browser memory
                js.executeScript("window.__recordedEvents = [];");
            }
        } catch (WebDriverException e) {
            // Catch WebDriver errors (like 'no such window') during transfer
            // This is expected if the browser is closed right before this call.
            // System.err.println("Error transferring actions (page may be closing): " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error transferring recorded actions: " + e.getMessage());
        }
    }

    // Sets up the driver, performs initial injection, and starts the monitor
    private static void openWithSeleniumAndRecorder(String url) throws InterruptedException {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(90));

        driver.get(url);
        Thread.sleep(3000);

        currentDriver = driver;

        // Initial Injection
        initialInjectionLoop(driver);

        // Start Monitoring Thread for re-injection on navigation and periodic saving
        startNavigationMonitor(driver);
    }

    // Performs the initial injection with a robust loop
    private static void initialInjectionLoop(WebDriver driver) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        boolean installed = false;

        for (int i = 0; i < 10; i++) {
            try {
                js.executeScript(getRecorderScriptFromFile());
                Object result = js.executeScript("return !!window.__recordingInstalled;");
                installed = (result instanceof Boolean) && (Boolean) result;
                if (installed) break;
                Thread.sleep(5000);
            } catch (Exception e) {
                System.out.println("Recorder injection error on attempt " + (i + 1) + ": " + e.getMessage());
                Thread.sleep(5000);
            }
        }

        if (!installed) {
            System.out.println("WARNING: Recorder script could not be installed on initial page load.");
        }
    }

    // Re-injects the recorder script after a page navigation
    private static void reInjectRecorder(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        boolean installed = false;

        for (int i = 0; i < 3; i++) {
            try {
                js.executeScript(getRecorderScriptFromFile());
                Object result = js.executeScript("return !!window.__recordingInstalled;");
                installed = (result instanceof Boolean) && (Boolean) result;
                if (installed) {
                    System.out.println("Recorder successfully re-injected on navigation.");
                    return;
                }
                Thread.sleep(500);
            } catch (Exception e) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
        }
        if (!installed) {
            System.out.println("WARNING: Failed to re-inject recorder after navigation.");
        }
    }

    // Monitors the browser for URL changes and triggers transfer/re-injection
    private static void startNavigationMonitor(WebDriver driver) {
        new Thread(() -> {
            String lastUrl = "";
            try {
                lastUrl = driver.getCurrentUrl();
            } catch (Exception ignored) {
            }

            while (true) {
                try {
                    // 🛑 PERIODIC TRANSFER: Saves current actions every loop iteration
                    transferActionsFromBrowserToHistory(driver);

                    // This command throws an exception if the driver is closed/dead, ending the loop
                    String currentUrl = driver.getCurrentUrl();

                    if (!currentUrl.equals(lastUrl)) {
                        System.out.println("Detected navigation from: " + lastUrl + " to: " + currentUrl);

                        // Transfer already happened above, now just wait and re-inject
                        Thread.sleep(1000);
                        reInjectRecorder(driver);
                        lastUrl = currentUrl;
                    }

                    Thread.sleep(500); // Check every 0.5 seconds
                } catch (WebDriverException e) {
                    // WebDriver closed manually (NoSuchWindowException) or crashed.
                    // Actions were captured periodically before this exception.
                    System.out.println("WebDriver closed manually. Final actions should be saved. Stopping monitor thread.");
                    break;
                } catch (Exception e) {
                    System.err.println("Unexpected error in monitor thread: " + e.getMessage());
                    break;
                }
            }
        }).start();
    }

    public static String getRecorderScriptFromFile() {
        try {
            // Adjust the path based on where you placed recorder.js
            String filePath = "src/main/resources/recorder.js";
            return new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("FATAL: Could not read recorder.js file: " + e.getMessage());
            return null;
        }
    }

    // Returns the most recently modified .json file from recordings/
    private static File getLatestRecordingJsonFile() {
        String workingDir = System.getProperty("user.dir");
        File dir = new File(workingDir, "recordings");
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) {
            return null;
        }

        File latest = files[0];
        for (File f : files) {
            if (f.lastModified() > latest.lastModified()) {
                latest = f;
            }
        }
        return latest;
    }

    private static String resolveRecordingJsonPath(String recordingName) {
        String workingDir = System.getProperty("user.dir");
        File recordingsDir = new File(workingDir, "recordings");

        File[] files = recordingsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) {
            throw new RuntimeException("No JSON recordings found in " + recordingsDir.getAbsolutePath());
        }

        if (recordingName != null && !recordingName.isBlank()) {
            String prefix = recordingName + "_";
            String exact = recordingName + ".json";

            for (File f : files) {
                String name = f.getName();
                if (name.equalsIgnoreCase(exact) || name.startsWith(prefix)) {
                    return f.getAbsolutePath();
                }
            }
            throw new RuntimeException("No JSON file found for recording name: " + recordingName);
        }

        // fallback: latest file
        File latest = files[0];
        for (File f : files) {
            if (f.lastModified() > latest.lastModified()) {
                latest = f;
            }
        }
        return latest.getAbsolutePath();
    }


    private static File resolveLatestRecordingJson(String recordingName) {
        try {
            Path dir = Paths.get("recordings");
            if (!Files.exists(dir)) {
                return null;
            }

            try (Stream<Path> stream = Files.list(dir)) {
                return stream
                        .filter(p -> Files.isRegularFile(p))
                        .filter(p -> {
                            String fn = p.getFileName().toString();
                            if (!fn.endsWith(".json")) return false;
                            if (recordingName == null || recordingName.isBlank()) {
                                // no specific recording: accept any json
                                return true;
                            }
                            String prefix = recordingName + "_";
                            return fn.startsWith(prefix);
                        })
                        .max(Comparator.comparingLong(p -> {
                            try {
                                return Files.getLastModifiedTime(p).toMillis();
                            } catch (IOException e) {
                                return 0L;
                            }
                        }))
                        .map(Path::toFile)
                        .orElse(null);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static File resolveJsonForReplay(String fileParam) {
        Path dir = Paths.get("recordings");
        if (!Files.exists(dir)) {
            return null;
        }

        try {
            // No param → try default action_logs.json, else latest *.json
            if (fileParam == null || fileParam.isBlank()) {
                Path defaultFile = dir.resolve("action_logs.json");
                if (Files.exists(defaultFile)) {
                    return defaultFile.toFile();
                }

                // Fallback: latest any .json
                try (Stream<Path> stream = Files.list(dir)) {
                    return stream
                            .filter(p -> Files.isRegularFile(p))
                            .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                            .max(Comparator.comparingLong(p -> {
                                try {
                                    return Files.getLastModifiedTime(p).toMillis();
                                } catch (IOException e) {
                                    return 0L;
                                }
                            }))
                            .map(Path::toFile)
                            .orElse(null);
                }
            }

            String candidateName = fileParam;
            if (!candidateName.toLowerCase().endsWith(".json")) {
                candidateName = candidateName + ".json";
            }

            Path candidate = dir.resolve(candidateName);
            if (Files.exists(candidate)) {
                return candidate.toFile();
            }

            // If base name without timestamp → find latest matching prefix
            String base = fileParam;
            String prefix = base + "_";

            try (Stream<Path> stream = Files.list(dir)) {
                return stream
                        .filter(p -> Files.isRegularFile(p))
                        .filter(p -> {
                            String fn = p.getFileName().toString();
                            return fn.startsWith(prefix) && fn.toLowerCase().endsWith(".json");
                        })
                        .max(Comparator.comparingLong(p -> {
                            try {
                                return Files.getLastModifiedTime(p).toMillis();
                            } catch (IOException e) {
                                return 0L;
                            }
                        }))
                        .map(Path::toFile)
                        .orElse(null);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}