package com.example;

// RawSeleniumReplayer.java
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RawSeleniumReplayer {

    // Matches: driver.findElement(By.id("username")).sendKeys("test");
    //          driver.findElement(By.xpath("//button")).click();
    private static final Pattern ELEMENT_PATTERN = Pattern.compile(
            "driver\\.findElement\\(By\\.(\\w+)\\(\"([^\"]+)\"\\)\\)\\.(\\w+)\\((?:\"([^\"]*)\")?\\);?"
    );

    // Matches: driver.get("https://....");
    private static final Pattern NAV_PATTERN = Pattern.compile(
            "driver\\.get\\(\"([^\"]*)\"\\);?"
    );

    /**
     * Replays recorded actions from JSON file.
     * Launches a fresh browser session, replays, then quits.
     */
    public static void replayFromJson(String jsonPath) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // Important: ignore extra fields like "options", "timestamp", etc.
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        List<RecordedEvent> events = mapper.readValue(
                new File(jsonPath),
                new TypeReference<List<RecordedEvent>>() {}
        );

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(90));
        Thread.sleep(10000);

        try {
            for (RecordedEvent ev : events) {
                if (ev == null || ev.raw_selenium == null || ev.raw_selenium.trim().isEmpty()) {
                    continue;
                }

                String script = ev.raw_selenium.trim();
                System.out.println("Replaying: " + script);

                // First, try navigation (driver.get)
                if (isNavigation(script)) {
                    executeNavigation(script, driver);
                } else {
                    // Otherwise treat as element interaction
                    executeElement(script, driver);
                }

                // Small delay between steps – tweak as you like
                Thread.sleep(300);
            }

            // Pause at the end to see the final state
            Thread.sleep(1200);

        } finally {
            driver.quit();
        }
    }

    private static boolean isNavigation(String raw) {
        return NAV_PATTERN.matcher(raw).matches();
    }

    private static void executeNavigation(String raw, WebDriver driver) {
        Matcher m = NAV_PATTERN.matcher(raw);
        if (!m.matches()) {
            System.out.println("Could not parse navigation line: " + raw);
            return;
        }
        String url = m.group(1);
        System.out.println("➡ Navigating to: " + url);
        driver.get(url);
    }

    private static void executeElement(String raw, WebDriver driver) {
        Matcher m = ELEMENT_PATTERN.matcher(raw);

        if (!m.matches()) {
            System.out.println("Unsupported raw_selenium (not element or nav): " + raw);
            return;
        }

        String locatorType  = m.group(1);  // id, name, xpath, cssSelector, ...
        String locatorValue = m.group(2);  // the actual locator string
        String method       = m.group(3);  // click, sendKeys, clear
        String arg          = m.group(4);  // value for sendKeys (may be null)

        By by = toBy(locatorType, locatorValue);
        WebElement element;

        try {
            element = driver.findElement(by);
        } catch (NoSuchElementException e) {
            System.out.println("Element not found for: " + raw);
            return;
        }

        switch (method) {
            case "click":
                element.click();
                break;

            case "sendKeys":
                if (arg != null) {
                    element.sendKeys(arg);
                } else {
                    System.out.println("⚠ sendKeys called without value → skipping: " + raw);
                }
                break;

            case "clear":
                element.clear();
                break;

            default:
                System.out.println("⚠ Unsupported method: " + method + " in " + raw);
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
}
