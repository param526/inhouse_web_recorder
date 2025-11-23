package com.example;

import static spark.Spark.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.WebDriverException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class UrlOpenerServer {

    private static WebDriver currentDriver;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Persistent list to store ALL actions across page navigations and closures
    private static final List<Object> ALL_RECORDED_ACTIONS = new CopyOnWriteArrayList<>();

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
            // ObjectMapper is used to convert the JSON string into a Java object
            ObjectMapper mapper = new ObjectMapper();

            //  Step 1: Parse the incoming JSON body
            // The request body will look like: {"feature": "Login Flow", "scenario": "Successful Login"}
            FeatureInfo info = mapper.readValue(request.body(), FeatureInfo.class);

            // Step 2: Validate the required fields
            if (info.getFeature() == null || info.getFeature().trim().isEmpty() ||
                    info.getScenario() == null || info.getScenario().trim().isEmpty()) {

                response.status(400); // Bad Request
                response.type("application/json");
                return "{\"status\": \"Error\", \"message\": \"Both Feature and Scenario names are required.\"}";
            }

            // Step 3: Call the generator with both values
            String featureName = info.getFeature().trim();
            String scenarioName = info.getScenario().trim();

            GherkinGenerator.generateFeatureFile(featureName, scenarioName);

            response.type("application/json");
            return "{\"status\": \"Success\", \"message\": \"Feature file generated for: " + scenarioName + "\"}";
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
                // Read and transfer any pending actions from the browser before saving
                if (currentDriver != null) {
                    transferActionsFromBrowserToHistory(currentDriver);
                }

                String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ALL_RECORDED_ACTIONS);

                Path dir = Paths.get("recordings");
                Files.createDirectories(dir);
                String fileName = "action_logs" + ".json";
                Path file = dir.resolve(fileName);
                Files.writeString(file, json);

                res.type("text/plain");
                return "Recorded actions saved to: " + file.toAbsolutePath();
            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return "Error saving recorded actions: " + e.getMessage();
            }
        });

        System.out.println("Selenium URL Launcher + Recorder running on http://localhost:4567");

//        RecorderServer.startServer();
    }

    // DTO for /recorder-status
//    public class Status {
//        private boolean installed;
//        private int count;
//        private String last_raw_gherkin;  // NEW FIELD
//
//        public Status(boolean installed, int count, String lastRawGherkin) {
//            this.installed = installed;
//            this.count = count;
//            this.last_raw_gherkin = lastRawGherkin;
//        }
//
//        public boolean isInstalled() {
//            return installed;
//        }
//
//        public int getCount() {
//            return count;
//        }
//
//        public String getLast_raw_gherkin() {
//            return last_raw_gherkin;
//        }
//    }

    private static String html(String msg, boolean error) {
        String color = error ? "red" : "green";
        return "<html><body><p style='color:" + color + "'>" + msg +
                "</p><a href='/index.html'>Back</a></body></html>";
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

}