package com.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Scenario;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.ElementClickInterceptedException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 🔴 NEW: import holder for live replay status
import com.example.ReplayStatusHolder;

public class RawSeleniumReplayer {

    // ========== REGEX PATTERNS (fallback mode) ==========

    private static final Pattern ELEMENT_PATTERN = Pattern.compile(
            "driver\\.findElement\\(By\\.(\\w+)\\(\"([^\"]+)\"\\)\\)\\.(\\w+)\\((?:\"([^\"]*)\")?\\);?"
    );

    private static final Pattern NAV_PATTERN = Pattern.compile(
            "driver\\.get\\(\"([^\"]*)\"\\);?"
    );

    // ========== INNER CLASS FOR RESULTS ==========

    private static class StepResult {
        int index;
        String rawScript;
        String rawGherkin;
        boolean success;
        long durationMs;
        String errorMessage;
        String stackTrace;
        String screenshotFileName;
        String status;   // PASSED / FAILED / SKIPPED
    }

    // ========== PUBLIC ENTRYPOINTS ==========

    public static boolean replayFromJson(String jsonPath,
                                         String reportPath) throws Exception {
        return replayFromJson(jsonPath, reportPath, null);
    }

    public static boolean replayFromJson(String jsonPath,
                                         String reportPath,
                                         Scenario scenario) throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        List<StepResult> results = new ArrayList<>();
        boolean allPassed = false;
        WebDriver driver = null;

        File reportFile = new File(reportPath);
        File screenshotsDir = new File(reportFile.getParentFile(), "screenshots");
        if (!screenshotsDir.exists()) {
            screenshotsDir.mkdirs();
        }

        try {
            List<RecordedEvent> events = mapper.readValue(
                    new File(jsonPath),
                    new TypeReference<List<RecordedEvent>>() {}
            );

            // 🔴 NEW: initialize live replay status for the whole run
            ReplayStatusHolder.init(events != null ? events.size() : 0);

            // 🔥 Normalize navigation steps so that only the very first navigation
            // in the JSON keeps driver.get("URL"); all later navigations have raw_selenium = ""
            normalizeNavigationRawSelenium(events);

            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            // 👇 Important flags for headless replay
            options.addArguments(
                    "--headless=new",          // or "--headless" if your Chrome is older
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage"
            );

            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(90));

            int index = 1;
            boolean failureOccurred = false;
            int failStepIndex = -1;

            int eventPos = 0;
            for (; eventPos < events.size(); eventPos++) {
                RecordedEvent ev = events.get(eventPos);
                if (ev == null) {
                    continue;
                }

                String rawSel = ev.getRaw_selenium();
                String displayScript;
                if (rawSel != null && !rawSel.trim().isEmpty()) {
                    displayScript = rawSel.trim();
                } else if (ev.getRaw_gherkin() != null && !ev.getRaw_gherkin().isEmpty()) {
                    displayScript = ev.getRaw_gherkin();
                } else if (ev.getAction() != null) {
                    displayScript = ev.getAction();
                } else {
                    displayScript = "(no raw_selenium / raw_gherkin)";
                }

                System.out.println("Replaying: " + displayScript);

                StepResult step = new StepResult();
                step.index = index++;
                step.rawScript = displayScript;
                step.rawGherkin = ev.getRaw_gherkin();
                long start = System.currentTimeMillis();

                // 🔴 NEW: update live status BEFORE executing this step
                String pageTitle = "";
                try {
                    if (driver != null) {
                        pageTitle = driver.getTitle();
                    }
                } catch (Exception ignore) {
                    pageTitle = "";
                }
                ReplayStatusHolder.beforeStep(eventPos, ev, pageTitle);

                try {
                    String action = ev.getAction() != null ? ev.getAction().toLowerCase() : "";

                    if ("navigate".equals(action)) {
                        replayNavigate(ev, driver);
                    } else if ("click".equals(action)) {
                        replayClick(ev, driver);
                    } else if ("sendkeys".equals(action)) {
                        replaySendKeys(ev, driver);
                    } else if ("hover".equals(action)) {
                        replayHover(ev, driver);
                    } else if (rawSel != null && !rawSel.trim().isEmpty()) {
                        // Fallback to old raw_selenium regex parser
                        String script = rawSel.trim();
                        if (isNavigation(script)) {
                            executeNavigation(script, driver);
                            waitForPageLoad(driver);
                        } else {
                            executeElement(script, driver);
                        }
                    } else {
                        // Unknown action & no raw_selenium – just mark as skipped/pass
                        System.out.println("Skipping unsupported event: " + displayScript);
                    }

                    step.success = true;
                    step.status = "PASSED";
                    step.errorMessage = null;
                    step.stackTrace = null;

                    // 🔴 NEW: mark step success in live status
                    ReplayStatusHolder.stepSuccess();

                } catch (Exception e) {
                    step.success = false;
                    step.status = "FAILED";
                    step.errorMessage = e.getMessage();
                    step.stackTrace = getStackTraceAsString(e);
                    System.out.println("Error executing step: " + displayScript);
                    e.printStackTrace();

                    failureOccurred = true;
                    failStepIndex = step.index;

                    // 🔴 NEW: mark step failure in live status
                    ReplayStatusHolder.stepFailure(e);

                } finally {
                    step.durationMs = System.currentTimeMillis() - start;

                    if (driver != null) {
                        step.screenshotFileName = captureScreenshot(driver, screenshotsDir, step.index);
                        clearHighlights(driver);
                    }

                    results.add(step);
                }

                if (failureOccurred) {
                    System.out.println("⚠ Aborting replay after step " + step.index +
                            " due to failure. Remaining steps will be marked as SKIPPED.");
                    eventPos++;
                    break;
                }

                Thread.sleep(300);
            }

            // Mark remaining steps as SKIPPED
            if (failureOccurred) {
                for (; eventPos < events.size(); eventPos++) {
                    RecordedEvent ev = events.get(eventPos);
                    if (ev == null) {
                        continue;
                    }

                    String rawSel2 = ev.getRaw_selenium();
                    String displayScript2;
                    if (rawSel2 != null && !rawSel2.trim().isEmpty()) {
                        displayScript2 = rawSel2.trim();
                    } else if (ev.getRaw_gherkin() != null && !ev.getRaw_gherkin().isEmpty()) {
                        displayScript2 = ev.getRaw_gherkin();
                    } else if (ev.getAction() != null) {
                        displayScript2 = ev.getAction();
                    } else {
                        displayScript2 = "(no raw_selenium / raw_gherkin)";
                    }

                    StepResult skipped = new StepResult();
                    skipped.index = index++;
                    skipped.rawScript = displayScript2;
                    skipped.rawGherkin = ev.getRaw_gherkin();
                    skipped.success = false;
                    skipped.status = "SKIPPED";
                    skipped.durationMs = 0L;
                    skipped.errorMessage = "Step not executed due to previous failure at step " + failStepIndex;
                    skipped.stackTrace = null;
                    skipped.screenshotFileName = null;

                    results.add(skipped);
                }
            }

            Thread.sleep(1200);

            allPassed = results.stream().noneMatch(r -> "FAILED".equals(r.status));
            System.out.println("Overall Result: " + (allPassed ? "PASS" : "FAIL"));

            return allPassed;

        } finally {
            // 🔴 NEW: mark replay as done for the live UI
            ReplayStatusHolder.done();

            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    System.out.println("Warning quitting driver: " + e.getMessage());
                }
            }

            try {
                generateHtmlReport(results, jsonPath, reportPath, allPassed);
                System.out.println("HTML report written to: " + reportPath);
            } catch (IOException io) {
                System.err.println("Failed to write replay HTML report: " + io.getMessage());
            }

            if (scenario != null) {
                scenario.log("RawSeleniumReplayer finished. Result: " +
                        (allPassed ? "PASS" : "FAIL"));
                scenario.log("JSON: " + jsonPath);
                scenario.log("HTML report: " + reportPath);

                for (StepResult r : results) {
                    scenario.log(
                            String.format("Step %d: %s - %s (%d ms)",
                                    r.index,
                                    r.status,
                                    r.rawScript,
                                    r.durationMs
                            )
                    );
                    if (r.errorMessage != null) {
                        scenario.log("  Note: " + r.errorMessage);
                    }
                }

                try {
                    byte[] bytes = Files.readAllBytes(Paths.get(reportPath));
                    scenario.attach(bytes, "text/html", "Raw Selenium Replay Report");
                } catch (IOException e) {
                    scenario.log("Failed to attach HTML report: " + e.getMessage());
                }
            }
        }
    }

    // ========== NEW: LocatorCandidate → By ==========

    private static By toBy(LocatorCandidate loc) {
        switch (loc.getType()) {
            case "id":
                return By.id(loc.getValue());
            case "name":
                return By.name(loc.getValue());
            case "dataTest":
            case "aria":
            case "css":
            case "titleCss":          // 🔹 NEW
                return By.cssSelector(loc.getValue());
            case "xpathText":
            case "roleText":
            case "labelText":
            case "titleXpath":        // 🔹 NEW
                return By.xpath(loc.getValue());
            default:
                throw new IllegalArgumentException("Unknown locator type: " + loc.getType() +
                        " value=" + loc.getValue());
        }
    }

    // ========== NEW: Action-based replayers using target.locators ==========

    private static void replayNavigate(RecordedEvent ev, WebDriver driver) {
        String url = ev.getUrl();
        if (url == null || url.trim().isEmpty()) {
            // Fallback to raw_selenium if URL missing
            String raw = ev.getRaw_selenium();
            if (raw != null && !raw.trim().isEmpty() && isNavigation(raw.trim())) {
                executeNavigation(raw.trim(), driver);
                return;
            }
            throw new IllegalArgumentException("No URL found for navigate step: " + ev.getRaw_gherkin());
        }
        System.out.println("➡ Navigating to: " + url);
        driver.get(url);
        waitForPageLoad(driver);
    }

    private static void replayClick(RecordedEvent ev, WebDriver driver) {
        RecordedTarget target = ev.getTarget();
        String raw = ev.getRaw_selenium();

        // If no target/locators → fallback to old raw_selenium mode.
        if (target == null || target.getLocators() == null || target.getLocators().isEmpty()) {
            if (raw != null && !raw.trim().isEmpty()) {
                System.out.println("No target.locators – using legacy raw_selenium click: " + raw);
                executeElement(raw.trim(), driver);
                return;
            }
            throw new RuntimeException("No target/locators and no raw_selenium for click step: " + ev.getRaw_gherkin());
        }

        List<LocatorCandidate> locators = new ArrayList<>(target.getLocators());
        locators.sort(Comparator.comparingInt(LocatorCandidate::getScore).reversed());

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Exception lastError = null;

        // Try each locator in order of score
        for (LocatorCandidate loc : locators) {
            By by;
            try {
                by = toBy(loc);
            } catch (Exception ex) {
                lastError = ex;
                continue;
            }

            try {
                WebElement el = wait.until(ExpectedConditions.elementToBeClickable(by));
                highlightElement(driver, el);
                safeClick(driver, el, by, raw != null ? raw : ev.getRaw_gherkin());
                System.out.println("Clicked using locator [" + loc.getType() + "] " + loc.getValue());
                return;
            } catch (TimeoutException | NoSuchElementException | StaleElementReferenceException e) {
                lastError = e;
                System.out.println("Failed locator [" + loc.getType() + "] " + loc.getValue()
                        + " -> " + e.getClass().getSimpleName());
            }
        }

        // Fallback: text-based locator using target.text
        if (target.getText() != null && !target.getText().trim().isEmpty()) {
            try {
                String text = target.getText().trim();
                String xpath = "//*[normalize-space(.)=" + buildXPathLiteral(text) + "]";
                By byText = By.xpath(xpath);
                WebDriverWait waitText = new WebDriverWait(driver, Duration.ofSeconds(15));
                WebElement el = waitText.until(ExpectedConditions.elementToBeClickable(byText));
                highlightElement(driver, el);
                safeClick(driver, el, byText, raw != null ? raw : ev.getRaw_gherkin());
                System.out.println("Clicked using fallback text locator: " + text);
                return;
            } catch (Exception e) {
                lastError = e;
            }
        }

        // Final fallback: legacy raw_selenium
        if (raw != null && !raw.trim().isEmpty()) {
            System.out.println("All target.locators failed, falling back to raw_selenium: " + raw);
            executeElement(raw.trim(), driver);
            return;
        }

        throw new RuntimeException("Failed to click element for step: " + ev.getRaw_gherkin(), lastError);
    }

    private static void replaySendKeys(RecordedEvent ev, WebDriver driver) {
        RecordedTarget target = ev.getTarget();
        String raw = ev.getRaw_selenium();
        String value = ev.getValue();

        if (value == null) {
            System.out.println("No value for sendKeys step (skipping): " + ev.getRaw_gherkin());
            return;
        }

        // If no target/locators → fallback to raw_selenium
        if (target == null || target.getLocators() == null || target.getLocators().isEmpty()) {
            if (raw != null && !raw.trim().isEmpty()) {
                System.out.println("No target.locators – using legacy raw_selenium sendKeys: " + raw);
                executeElement(raw.trim(), driver);
                return;
            }
            throw new RuntimeException("No target/locators and no raw_selenium for sendKeys step: " + ev.getRaw_gherkin());
        }

        List<LocatorCandidate> locators = new ArrayList<>(target.getLocators());
        locators.sort(Comparator.comparingInt(LocatorCandidate::getScore).reversed());

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        Exception lastError = null;

        for (LocatorCandidate loc : locators) {
            By by;
            try {
                by = toBy(loc);
            } catch (Exception ex) {
                lastError = ex;
                continue;
            }

            try {
                WebElement el = wait.until(ExpectedConditions.presenceOfElementLocated(by));
                highlightElement(driver, el);
                try {
                    el.clear();
                } catch (Exception ignore) {
                    // Some inputs (like combo-box) may not support clear()
                }
                el.sendKeys(value);
                System.out.println("sendKeys using locator [" + loc.getType() + "] " + loc.getValue());
                return;
            } catch (TimeoutException | NoSuchElementException | StaleElementReferenceException e) {
                lastError = e;
                System.out.println("Failed sendKeys locator [" + loc.getType() + "] " + loc.getValue()
                        + " -> " + e.getClass().getSimpleName());
            }
        }

        // Final fallback: raw_selenium
        if (raw != null && !raw.trim().isEmpty()) {
            System.out.println("All target.locators failed for sendKeys, falling back to raw_selenium: " + raw);
            executeElement(raw.trim(), driver);
            return;
        }

        throw new RuntimeException("Failed to sendKeys for step: " + ev.getRaw_gherkin(), lastError);
    }

    /**
     * Replays a synthetic "hover" action:
     *  - Uses target.locators (high score first)
     *  - Moves the mouse to the element and pauses briefly
     */
    private static void replayHover(RecordedEvent ev, WebDriver driver) {
        RecordedTarget target = ev.getTarget();
        String raw = ev.getRaw_selenium();

        if (target == null || target.getLocators() == null || target.getLocators().isEmpty()) {
            System.out.println("No target.locators for hover step: " + ev.getRaw_gherkin());
            return;
        }

        List<LocatorCandidate> locators = new ArrayList<>(target.getLocators());
        locators.sort(Comparator.comparingInt(LocatorCandidate::getScore).reversed());

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        Exception lastError = null;

        for (LocatorCandidate loc : locators) {
            By by;
            try {
                by = toBy(loc);
            } catch (Exception ex) {
                lastError = ex;
                continue;
            }

            try {
                WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
                highlightElement(driver, el);

                System.out.println("Hovering using locator [" + loc.getType() + "] " + loc.getValue());

                Actions actions = new Actions(driver);
                actions.moveToElement(el)
                        .pause(Duration.ofMillis(300))
                        .perform();

                return; // hover successful
            } catch (TimeoutException | NoSuchElementException | StaleElementReferenceException e) {
                lastError = e;
                System.out.println("Failed hover locator [" + loc.getType() + "] " + loc.getValue()
                        + " -> " + e.getClass().getSimpleName());
            }
        }

        throw new RuntimeException("Failed to hover for step: " + ev.getRaw_gherkin(), lastError);
    }

    // Literal builder for XPath (handles `'` inside text)
    private static String buildXPathLiteral(String text) {
        if (!text.contains("'")) {
            return "'" + text + "'";
        }
        String[] parts = text.split("'");
        StringBuilder sb = new StringBuilder("concat(");
        for (int i = 0; i < parts.length; i++) {
            sb.append("'").append(parts[i]).append("'");
            if (i < parts.length - 1) {
                sb.append(", \"'\", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    // ========== OLD HELPERS (used in fallback + some special cases) ==========

    private static boolean isNavigation(String raw) {
        return NAV_PATTERN.matcher(raw).matches();
    }

    private static void executeNavigation(String raw, WebDriver driver) {
        Matcher m = NAV_PATTERN.matcher(raw);
        if (!m.matches()) {
            throw new IllegalArgumentException("Could not parse navigation line: " + raw);
        }
        String url = m.group(1);
        System.out.println("➡ Navigating to: " + url);
        driver.get(url);
        waitForPageLoad(driver);
    }

    private static void executeElement(String raw, WebDriver driver) {
        Matcher m = ELEMENT_PATTERN.matcher(raw);

        if (!m.matches()) {
            throw new IllegalArgumentException("Unsupported raw_selenium (not element or nav): " + raw);
        }

        String locatorType = m.group(1);
        String locatorValue = m.group(2);
        String method = m.group(3);
        String arg = m.group(4);

        locatorValue = normalizeLocatorValue(locatorType, locatorValue);

        By by = toBy(locatorType, locatorValue);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        WebElement element;

        if ("click".equals(method) && isFacetControl(locatorType, locatorValue)) {
            System.out.println("Using facet-control shortcut for: " + by + " | Raw: " + raw);
            element = wait.until(ExpectedConditions.presenceOfElementLocated(by));
            highlightElement(driver, element);
            safeClick(driver, element, by, raw);
            return;
        }

        try {
            if ("click".equals(method)) {
                element = wait.until(ExpectedConditions.elementToBeClickable(by));
            } else if ("sendKeys".equals(method)) {
                element = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
            } else if ("clear".equals(method)) {
                element = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
            } else {
                element = wait.until(ExpectedConditions.presenceOfElementLocated(by));
            }

        } catch (TimeoutException e) {
            if ("click".equals(method) && isSignOutLink(by)) {
                try {
                    System.out.println(
                            "Timed out locating Sign Out directly; " +
                                    "hovering user sidenav icon (.sidenav_option-icon .fa-user) and retrying."
                    );

                    WebDriverWait hoverWait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    WebElement userIcon = hoverWait.until(
                            ExpectedConditions.visibilityOfElementLocated(
                                    By.cssSelector(".sidenav_options .sidenav_option .sidenav_option-icon .fa-user")
                            )
                    );

                    new Actions(driver)
                            .moveToElement(userIcon)
                            .pause(Duration.ofMillis(300))
                            .perform();

                    element = hoverWait.until(ExpectedConditions.elementToBeClickable(by));

                } catch (TimeoutException hoverEx) {
                    throw new RuntimeException(
                            "Timed out waiting for Sign Out even after hovering user icon: "
                                    + by + " | Raw: " + raw,
                            hoverEx
                    );
                }

            } else {
                throw new RuntimeException("Timed out waiting for element: " + by + " | Raw: " + raw, e);
            }
        }

        highlightElement(driver, element);

        switch (method) {
            case "click":
                safeClick(driver, element, by, raw);
                break;
            case "sendKeys":
                if (arg != null) {
                    element.sendKeys(arg);
                } else {
                    throw new IllegalArgumentException("sendKeys called without value for: " + raw);
                }
                break;
            case "clear":
                element.clear();
                break;
            default:
                throw new IllegalArgumentException("Unsupported method: " + method + " in " + raw);
        }
    }

    private static By toBy(String type, String value) {
        switch (type) {
            case "id":
                return By.id(value);
            case "name":
                return By.name(value);
            case "xpath":
                return By.xpath(value);
            case "cssSelector":
            case "css":
                return By.cssSelector(value);
            case "linkText":
                return By.linkText(value);
            case "partialLinkText":
                return By.partialLinkText(value);
            case "className":
                return By.className(value);
            case "tagName":
                return By.tagName(value);
            default:
                throw new IllegalArgumentException("Unsupported locator type: " + type);
        }
    }

    private static String getStackTraceAsString(Throwable t) {
        if (t == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(t.toString()).append("\n");
        for (StackTraceElement el : t.getStackTrace()) {
            sb.append("    at ").append(el.toString()).append("\n");
        }
        return sb.toString();
    }

    private static void waitForPageLoad(WebDriver driver) {
        new WebDriverWait(driver, Duration.ofSeconds(30))
                .until(d -> ((JavascriptExecutor) d)
                        .executeScript("return document.readyState")
                        .equals("complete"));
    }

    private static String captureScreenshot(WebDriver driver,
                                            File screenshotsDir,
                                            int stepIndex) {
        if (!(driver instanceof TakesScreenshot)) {
            return null;
        }

        try {
            // ✅ Try to ensure the page has finished loading before we capture
            try {
                waitForPageLoad(driver);
            } catch (Exception e) {
                // Non-fatal – in some SPAs this may throw; we still attempt screenshot
                System.out.println("captureScreenshot: waitForPageLoad skipped/failed: " + e.getMessage());
            }

            // Small extra buffer to let last-moment UI changes settle
            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String fileName = String.format("step_%03d.png", stepIndex);
            File dest = new File(screenshotsDir, fileName);
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return fileName;

        } catch (Exception e) {
            System.err.println("Failed to capture screenshot for step " + stepIndex + ": " + e.getMessage());
            return null;
        }
    }

    private static void highlightElement(WebDriver driver, WebElement element) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                    "arguments[0].scrollIntoView({behavior:'instant',block:'center',inline:'center'});",
                    element
            );
            js.executeScript(
                    "arguments[0].setAttribute('data-replay-highlight','true');" +
                            "arguments[0].style.outline='3px solid #f97316';" +
                            "arguments[0].style.outlineOffset='2px';" +
                            "arguments[0].style.boxShadow='0 0 0 3px rgba(249,115,22,0.75)';",
                    element
            );
        } catch (Exception e) {
            System.out.println("Highlight failed (non-fatal): " + e.getMessage());
        }
    }

    private static void clearHighlights(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                    "document.querySelectorAll('[data-replay-highlight]').forEach(function(el){" +
                            "  el.style.outline='';" +
                            "  el.style.outlineOffset='';" +
                            "  el.style.boxShadow='';" +
                            "  el.removeAttribute('data-replay-highlight');" +
                            "});"
            );
        } catch (Exception e) {
            System.out.println("Clear highlight failed (non-fatal): " + e.getMessage());
        }
    }

    /**
     * Robust click handler with sr-only / overlay fallbacks.
     */
    private static void safeClick(WebDriver driver, WebElement element, By by, String raw) {
        try {
            element.click();
            return;
        } catch (ElementNotInteractableException e) {
            System.out.println("Element click problem (" + e.getClass().getSimpleName() + ") for " + by + " | Raw: " + raw);
        }

        // Special case: screen-reader-only style inputs (e.g., plp-grid-header-filters-button-0)
        try {
            String cls = element.getAttribute("class");
            if (cls != null && cls.contains("sr-only")) {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                Object parentCandidate = js.executeScript(
                        "return arguments[0].closest('button,[role=\"button\"],.fds_selector__control');",
                        element
                );
                if (parentCandidate instanceof WebElement) {
                    WebElement container = (WebElement) parentCandidate;
                    System.out.println("Trying to click closest visible container for sr-only input: " + by);
                    highlightElement(driver, container);
                    try {
                        container.click();
                        return;
                    } catch (ElementNotInteractableException e2) {
                        element = container; // fall through to JS/actions on container
                    }
                }
            }
        } catch (Exception ignore) {
            // Best-effort, fall through.
        }

        // Fallback: JS click
        try {
            System.out.println("Trying JS click for: " + by);
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].click();", element);
            return;
        } catch (Exception jsEx) {
            System.out.println("JS click failed for " + by + " – " + jsEx.getMessage());
        }

        // Fallback: Actions click
        try {
            System.out.println("Trying Actions click for: " + by);
            new Actions(driver)
                    .moveToElement(element)
                    .pause(Duration.ofMillis(200))
                    .click()
                    .perform();
        } catch (Exception actEx) {
            throw new RuntimeException(
                    "Click failed even after JS & Actions for " + by + " | Raw: " + raw,
                    actEx
            );
        }
    }

    private static boolean isSignOutLink(By by) {
        if (by == null) return false;
        String s = by.toString(); // e.g. "By.linkText: Sign Out"
        return s != null
                && s.startsWith("By.linkText:")
                && s.trim().endsWith("Sign Out");
    }

    /**
     * Detects the problematic PLP filter + department facet controls.
     */
    private static boolean isFacetControl(String locatorType, String locatorValue) {
        if (locatorType == null || locatorValue == null) return false;

        // plp grid facet toggle input
        if ("id".equals(locatorType) && locatorValue.startsWith("plp-grid-header-filters-button-")) {
            return true;
        }

        // department facet input
        if ("name".equals(locatorType) && "department-facet".equals(locatorValue)) {
            return true;
        }

        return false;
    }

    private static String normalizeLocatorValue(String locatorType, String locatorValue) {
        if (!"xpath".equals(locatorType) || locatorValue == null) {
            return locatorValue;
        }

        if (!locatorValue.contains("select2-results__option") ||
                !locatorValue.contains("normalize-space()=")) {
            return locatorValue;
        }

        try {
            int nsIdx = locatorValue.indexOf("normalize-space()=");
            if (nsIdx == -1) return locatorValue;

            int firstQuote = locatorValue.indexOf('\'', nsIdx);
            if (firstQuote == -1) return locatorValue;

            int secondQuote = locatorValue.indexOf('\'', firstQuote + 1);
            if (secondQuote == -1) return locatorValue;

            String fullText = locatorValue.substring(firstQuote + 1, secondQuote);

            String token = fullText;
            int newlineIdx = token.indexOf('\n');
            if (newlineIdx > 0) {
                token = token.substring(0, newlineIdx);
            }
            token = token.trim();

            if (token.length() > 60) {
                token = token.substring(0, 60);
            }

            if (token.isEmpty()) {
                return locatorValue;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("contains(normalize-space(.), '")
                    .append(token.replace("'", "\\'"))
                    .append("')");

            String prefix = locatorValue.substring(0, nsIdx);
            String suffix = locatorValue.substring(secondQuote + 1);

            String normalized = prefix + sb.toString() + suffix;

            System.out.println("Normalizing Select2 xpath:");
            System.out.println("  BEFORE: " + locatorValue);
            System.out.println("  AFTER : " + normalized);

            return normalized;
        } catch (Exception ex) {
            System.out.println("Failed to normalize Select2 xpath: " + ex.getMessage());
            return locatorValue;
        }
    }

    /**
     * Normalize navigation steps so that only the very first navigation
     * in the JSON gets a driver.get("URL"); raw_selenium.
     * All later navigation events keep their URL but have empty raw_selenium
     * and cleaner Gherkin ("I am on ... page").
     */
    private static void normalizeNavigationRawSelenium(List<RecordedEvent> events) {
        boolean firstNavSeen = false;

        if (events == null) return;

        for (RecordedEvent ev : events) {
            if (ev == null) continue;

            String action = ev.getAction() != null ? ev.getAction().toLowerCase() : "";
            String type = ev.getType() != null ? ev.getType().toLowerCase() : "";

            boolean isNav = "navigate".equals(action) || "navigation".equals(type);
            if (!isNav) {
                continue;
            }

            String url = ev.getUrl();
            String title = ev.getTitle();
            String pageName = (title != null && !title.trim().isEmpty())
                    ? title.trim()
                    : (url != null ? url : "");

            if (!firstNavSeen && url != null && !url.isEmpty()) {
                firstNavSeen = true;
                String escaped = url.replace("\"", "\\\"");
                ev.setRaw_selenium("driver.get(\"" + escaped + "\");");

                if (ev.getRaw_gherkin() == null || ev.getRaw_gherkin().trim().isEmpty()) {
                    if (pageName.isEmpty() && url != null) {
                        pageName = url;
                    }
                    ev.setRaw_gherkin("I navigate to \"" + pageName + "\" page");
                }
            } else {
                ev.setRaw_selenium("");

                if (!pageName.isEmpty()) {
                    ev.setRaw_gherkin("I am on \"" + pageName + "\" page");
                }
            }
        }
    }

    // ========== HTML REPORT ==========

    private static void generateHtmlReport(List<StepResult> results,
                                           String jsonPath,
                                           String reportPath,
                                           boolean allPassed) throws IOException {

        int total = results.size();
        int passed = (int) results.stream().filter(r -> "PASSED".equals(r.status)).count();
        int failed = (int) results.stream().filter(r -> "FAILED".equals(r.status)).count();
        int skipped = (int) results.stream().filter(r -> "SKIPPED".equals(r.status)).count();
        double passPercent = total == 0 ? 0.0 : (passed * 100.0 / total);

        long totalDuration = results.stream().mapToLong(r -> r.durationMs).sum();

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        try (PrintWriter out = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(reportPath), StandardCharsets.UTF_8)
        )) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\">");
            out.println("  <title>Raw Selenium Replay Report</title>");
            out.println("  <style>");
            out.println("    :root {");
            out.println("      --bg: #0f172a;");
            out.println("      --card-bg: #ffffff;");
            out.println("      --border-subtle: #e2e8f0;");
            out.println("      --text-main: #0f172a;");
            out.println("      --text-muted: #64748b;");
            out.println("      --pass: #16a34a;");
            out.println("      --fail: #dc2626;");
            out.println("      --chip-bg-pass: #dcfce7;");
            out.println("      --chip-bg-fail: #fee2e2;");
            out.println("      --chip-text-pass: #166534;");
            out.println("      --chip-text-fail: #991b1b;");
            out.println("      --row-hover: #f8fafc;");
            out.println("    }");
            out.println("    * { box-sizing: border-box; }");
            out.println("    body {");
            out.println("      margin: 0;");
            out.println("      font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;");
            out.println("      background-color: #f1f5f9;"); // fixed typo here
            out.println("      color: var(--text-main);");
            out.println("    }");
            out.println("    .page {");
            out.println("      max-width: 1200px;");
            out.println("      margin: 0 auto;");
            out.println("      padding: 24px 16px 40px;");
            out.println("    }");
            out.println("    .header {");
            out.println("      background: linear-gradient(135deg, #0f172a, #1e293b);");
            out.println("      color: white;");
            out.println("      border-radius: 16px;");
            out.println("      padding: 20px 24px;");
            out.println("      display: flex;");
            out.println("      align-items: center;");
            out.println("      justify-content: space-between;");
            out.println("      box-shadow: 0 12px 30px rgba(15,23,42,0.35);");
            out.println("      margin-bottom: 24px;");
            out.println("    }");
            out.println("    .header-main {");
            out.println("      display: flex;");
            out.println("      flex-direction: column;");
            out.println("      gap: 4px;");
            out.println("    }");
            out.println("    .header-title {");
            out.println("      font-size: 20px;");
            out.println("      font-weight: 600;");
            out.println("    }");
            out.println("    .header-sub {");
            out.println("      font-size: 13px;");
            out.println("      color: #cbd5f5;");
            out.println("    }");
            out.println("    .status-pill {");
            out.println("      padding: 6px 14px;");
            out.println("      border-radius: 999px;");
            out.println("      font-size: 13px;");
            out.println("      font-weight: 600;");
            out.println("      display: inline-flex;");
            out.println("      align-items: center;");
            out.println("      gap: 6px;");
            out.println("      background-color: rgba(15,23,42,0.8);");
            out.println("      border: 1px solid rgba(148,163,184,0.6);");
            out.println("    }");
            out.println("    .status-pill.pass { border-color: #22c55e; color: #bbf7d0; }");
            out.println("    .status-pill.fail { border-color: #f97373; color: #fecaca; }");
            out.println("    .status-dot {");
            out.println("      width: 8px;");
            out.println("      height: 8px;");
            out.println("      border-radius: 50%;");
            out.println("      background-color: #22c55e;");
            out.println("    }");
            out.println("    .status-pill.fail .status-dot { background-color: #f97316; }");
            out.println("    .summary-grid {");
            out.println("      display: grid;");
            out.println("      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));");
            out.println("      gap: 12px;");
            out.println("      margin-bottom: 24px;");
            out.println("    }");
            out.println("    .summary-card {");
            out.println("      background-color: var(--card-bg);");
            out.println("      border-radius: 12px;");
            out.println("      padding: 12px 14px;");
            out.println("      border: 1px solid var(--border-subtle);");
            out.println("      box-shadow: 0 4px 12px rgba(15,23,42,0.08);");
            out.println("    }");
            out.println("    .summary-label {");
            out.println("      font-size: 11px;");
            out.println("      text-transform: uppercase;");
            out.println("      letter-spacing: 0.06em;");
            out.println("      color: var(--text-muted);");
            out.println("      margin-bottom: 4px;");
            out.println("    }");
            out.println("    .summary-value {");
            out.println("      font-size: 18px;");
            out.println("      font-weight: 600;");
            out.println("    }");
            out.println("    .summary-sub {");
            out.println("      font-size: 11px;");
            out.println("      color: var(--text-muted);");
            out.println("      margin-top: 2px;");
            out.println("      word-break: break-all;");
            out.println("    }");
            out.println("    .table-card {");
            out.println("      background-color: var(--card-bg);");
            out.println("      border-radius: 16px;");
            out.println("      border: 1px solid var(--border-subtle);");
            out.println("      box-shadow: 0 10px 24px rgba(15,23,42,0.1);");
            out.println("      overflow: hidden;");
            out.println("    }");
            out.println("    .table-wrapper {");
            out.println("      max-height: 640px;");
            out.println("      overflow: auto;");
            out.println("    }");
            out.println("    table {");
            out.println("      border-collapse: collapse;");
            out.println("      width: 100%;");
            out.println("      font-size: 13px;");
            out.println("    }");
            out.println("    thead th {");
            out.println("      position: sticky;");
            out.println("      top: 0;");
            out.println("      background-color: #f8fafc;");
            out.println("      border-bottom: 1px solid var(--border-subtle);");
            out.println("      padding: 8px 10px;");
            out.println("      text-align: left;");
            out.println("      font-weight: 600;");
            out.println("      color: var(--text-muted);");
            out.println("      z-index: 1;");
            out.println("    }");
            out.println("    tbody td {");
            out.println("      padding: 8px 10px;");
            out.println("      border-bottom: 1px solid #e5e7eb;");
            out.println("      vertical-align: top;");
            out.println("    }");
            out.println("    tbody tr:nth-child(even) { background-color: #f9fafb; }");
            out.println("    tbody tr:hover { background-color: var(--row-hover); }");
            out.println("    .status-chip {");
            out.println("      display: inline-flex;");
            out.println("      align-items: center;");
            out.println("      justify-content: center;");
            out.println("      padding: 2px 10px;");
            out.println("      border-radius: 999px;");
            out.println("      font-size: 11px;");
            out.println("      font-weight: 600;");
            out.println("    }");
            out.println("    .status-chip.pass {");
            out.println("      background-color: var(--chip-bg-pass);");
            out.println("      color: var(--chip-text-pass);");
            out.println("    }");
            out.println("    .status-chip.fail {");
            out.println("      background-color: var(--chip-bg-fail);");
            out.println("      color: var(--chip-text-fail);");
            out.println("    }");
            out.println("    .status-chip.skipped {");
            out.println("      background-color: rgba(250, 204, 21, 0.18);");
            out.println("      color: #854d0e;");
            out.println("      border: 1px solid rgba(250, 204, 21, 0.55);");
            out.println("    }");
            out.println("    .mono {");
            out.println("      font-family: SFMono-Regular, Menlo, Monaco, Consolas, \"Liberation Mono\", \"Courier New\", monospace;");
            out.println("      font-size: 12px;");
            out.println("    }");
            out.println("    pre {");
            out.println("      white-space: pre-wrap;");
            out.println("      margin: 0;");
            out.println("      word-break: normal;");
            out.println("      overflow-wrap: break-word;");
            out.println("    }");
            out.println("    .selenium-wrap {");
            out.println("      white-space: pre;");
            out.println("      word-break: normal;");
            out.println("      overflow-wrap: normal;");
            out.println("      display: block;");
            out.println("      max-width: 520px;");
            out.println("      overflow-x: auto;");
            out.println("    }");
            out.println("    .stack-toggle {");
            out.println("      font-size: 11px;");
            out.println("      color: #2563eb;");
            out.println("      cursor: pointer;");
            out.println("      text-decoration: underline;");
            out.println("      margin-bottom: 4px;");
            out.println("      display: inline-block;");
            out.println("    }");
            out.println("    .stack-content {");
            out.println("      display: none;");
            out.println("      margin-top: 4px;");
            out.println("      max-height: 220px;");
            out.println("      overflow: auto;");
            out.println("      background-color: #0b1120;");
            out.println("      color: #e5e7eb;");
            out.println("      border-radius: 8px;");
            out.println("      padding: 8px 10px;");
            out.println("      border: 1px solid #1f2937;");
            out.println("    }");
            out.println("    .stack-content pre { font-size: 11px; }");
            out.println("    .small { font-size: 11px; color: var(--text-muted); }");
            out.println("  </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("  <div class=\"page\">");

            out.println("    <div class=\"header\">");
            out.println("      <div class=\"header-main\">");
            out.println("        <div class=\"header-title\">Raw Selenium Replay Report</div>");
            out.printf("        <div class=\"header-sub\">Generated at %s · %d steps · Total duration %d ms</div>%n",
                    timestamp, total, totalDuration);
            out.println("      </div>");
            out.printf("      <div class=\"status-pill %s\">", allPassed ? "pass" : "fail");
            out.println("        <span class=\"status-dot\"></span>");
            out.printf("        <span>%s</span>%n", allPassed ? "ALL STEPS PASSED" : "FAILURES DETECTED");
            out.println("      </div>");
            out.println("    </div>");

            out.println("    <div class=\"summary-grid\">");

            out.println("      <div class=\"summary-card\">");
            out.println("        <div class=\"summary-label\">Total Steps</div>");
            out.println("        <div class=\"summary-value\">" + total + "</div>");
            out.println("      </div>");

            out.println("      <div class=\"summary-card\">");
            out.println("        <div class=\"summary-label\">Passed</div>");
            out.println("        <div class=\"summary-value\" style=\"color: var(--pass);\">" + passed + "</div>");
            out.println("      </div>");

            out.println("      <div class=\"summary-card\">");
            out.println("        <div class=\"summary-label\">Failed</div>");
            out.println("        <div class=\"summary-value\" style=\"color: var(--fail);\">" + failed + "</div>");
            out.println("      </div>");

            out.println("      <div class=\"summary-card\">");
            out.println("        <div class=\"summary-label\">Skipped</div>");
            out.println("        <div class=\"summary-value\" style=\"color: #ca8a04;\">" + skipped + "</div>");
            out.println("      </div>");

            out.println("      <div class=\"summary-card\">");
            out.println("        <div class=\"summary-label\">Pass Rate</div>");
            out.printf("        <div class=\"summary-value\">%.1f%%</div>%n", passPercent);
            out.println("        <div class=\"summary-sub\">JSON: " + escapeHtml(jsonPath) + "</div>");
            out.println("      </div>");

            out.println("    </div>");

            out.println("    <div class=\"table-card\">");
            out.println("      <div class=\"table-wrapper\">");
            out.println("        <table>");
            out.println("          <thead>");
            out.println("            <tr>");
            out.println("              <th style=\"width: 40px;\">#</th>");
            out.println("              <th style=\"width: 90px;\">Status</th>");
            out.println("              <th style=\"width: 80px;\">Duration</th>");
            out.println("              <th>Raw Selenium</th>");
            out.println("              <th>Raw Gherkin</th>");
            out.println("              <th style=\"width: 160px;\">Screenshot</th>");
            out.println("              <th style=\"width: 200px;\">Error Message</th>");
            out.println("              <th style=\"width: 260px;\">Stack Trace</th>");
            out.println("            </tr>");
            out.println("          </thead>");
            out.println("          <tbody>");

            int idx = 0;
            for (StepResult r : results) {
                idx++;
                String rowClass;
                String label;

                if ("PASSED".equals(r.status)) {
                    rowClass = "pass";
                    label = "PASS";
                } else if ("FAILED".equals(r.status)) {
                    rowClass = "fail";
                    label = "FAIL";
                } else {
                    rowClass = "skipped";
                    label = "SKIPPED";
                }

                out.println("            <tr>");
                out.println("              <td>" + r.index + "</td>");
                out.println("              <td>");
                out.printf("                <span class=\"status-chip %s\">%s</span>%n", rowClass, label);
                out.println("              </td>");
                out.println("              <td>" + r.durationMs + " ms</td>");
                out.println("              <td class=\"mono\"><pre class=\"selenium-wrap\">" + escapeHtml(r.rawScript) + "</pre></td>");

                out.println("              <td class=\"mono\">");
                if (r.rawGherkin != null && !r.rawGherkin.isEmpty()) {
                    out.println("                <pre>" + escapeHtml(r.rawGherkin) + "</pre>");
                } else {
                    out.println("                <span class=\"small\">—</span>");
                }
                out.println("              </td>");

                out.println("              <td>");
                if (r.screenshotFileName != null && !r.screenshotFileName.isEmpty()) {
                    String imgSrc = "/screenshots/" + r.screenshotFileName;
                    out.println("                <a href=\"" + imgSrc + "\" target=\"_blank\" " +
                            "style=\"text-decoration:none; color:inherit;\">");
                    out.println("                  <img src=\"" + imgSrc + "\" " +
                            "style=\"max-width: 150px; border-radius: 8px; " +
                            "border: 1px solid #1f2937; display:block;\">");
                    out.println("                  <span class=\"small\">Open full-size</span>");
                    out.println("                </a>");
                } else {
                    out.println("                <span class=\"small\">—</span>");
                }
                out.println("              </td>");

                out.println("              <td>");
                if (r.errorMessage != null && !r.errorMessage.isEmpty()) {
                    out.println("                <div class=\"mono\"><pre>" + escapeHtml(r.errorMessage) + "</pre></div>");
                } else {
                    out.println("                <span class=\"small\">—</span>");
                }
                out.println("              </td>");

                out.println("              <td>");
                if (r.stackTrace != null && !r.stackTrace.isEmpty()) {
                    String stackId = "stack-" + idx;
                    out.printf("                <span class=\"stack-toggle\" data-target=\"%s\">View stack trace</span>%n", stackId);
                    out.printf("                <div id=\"%s\" class=\"stack-content\"><pre>%s</pre></div>%n",
                            stackId, escapeHtml(r.stackTrace));
                } else {
                    out.println("                <span class=\"small\">—</span>");
                }
                out.println("              </td>");

                out.println("            </tr>");
            }

            out.println("          </tbody>");
            out.println("        </table>");
            out.println("      </div>");
            out.println("    </div>");

            out.println("    <p class=\"small\" style=\"margin-top: 12px;\">Generated by RawSeleniumReplayer.</p>");

            out.println("  </div>");

            out.println("  <script>");
            out.println("    document.addEventListener('DOMContentLoaded', function () {");
            out.println("      var toggles = document.querySelectorAll('.stack-toggle');");
            out.println("      toggles.forEach(function (toggle) {");
            out.println("        toggle.addEventListener('click', function () {");
            out.println("          var targetId = this.getAttribute('data-target');");
            out.println("          var el = document.getElementById(targetId);");
            out.println("          if (!el) return;");
            out.println("          var visible = el.style.display === 'block';");
            out.println("          el.style.display = visible ? 'none' : 'block';");
            out.println("          this.textContent = visible ? 'View stack trace' : 'Hide stack trace';");
            out.println("        });");
            out.println("      });");
            out.println("    });");
            out.println("  </script>");

            out.println("</body>");
            out.println("</html>");
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
