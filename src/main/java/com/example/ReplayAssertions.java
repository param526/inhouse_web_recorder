package com.example;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.function.Supplier;

public final class ReplayAssertions {

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    private ReplayAssertions() {
        // utility class
    }

    /* =========================
     *  BASIC ELEMENT ASSERTIONS
     * ========================= */

    public static WebElement assertPresent(By locator, WebDriver driver) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                    .until(ExpectedConditions.presenceOfElementLocated(locator));
        } catch (TimeoutException e) {
            throw new AssertionError("Element NOT found: " + locator, e);
        }
    }

    public static WebElement assertVisible(By locator, WebDriver driver) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                    .until(ExpectedConditions.visibilityOfElementLocated(locator));
        } catch (TimeoutException e) {
            throw new AssertionError("Element NOT visible: " + locator, e);
        }
    }

    public static WebElement assertClickable(By locator, WebDriver driver) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                    .until(ExpectedConditions.elementToBeClickable(locator));
        } catch (TimeoutException e) {
            throw new AssertionError("Element NOT clickable: " + locator, e);
        }
    }

    /* =========================
     *  PAGE-LEVEL ASSERTIONS
     * ========================= */

    public static void assertUrlContains(WebDriver driver, String expectedPart) {
        if (expectedPart == null || expectedPart.isEmpty()) {
            return;
        }
        try {
            new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                    .until(ExpectedConditions.urlContains(expectedPart));
        } catch (TimeoutException e) {
            throw new AssertionError("URL does not contain: " + expectedPart
                    + " | Actual: " + driver.getCurrentUrl(), e);
        }
    }

    public static void assertTitleContains(WebDriver driver, String expectedPart) {
        if (expectedPart == null || expectedPart.isEmpty()) {
            return;
        }
        try {
            new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                    .until(ExpectedConditions.titleContains(expectedPart));
        } catch (TimeoutException e) {
            throw new AssertionError("Title does not contain: " + expectedPart
                    + " | Actual: " + driver.getTitle(), e);
        }
    }

    public static void assertPageContains(WebDriver driver, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        boolean present = driver.getPageSource().contains(text);
        if (!present) {
            throw new AssertionError("Expected text not found on page: " + text);
        }
    }

    /* =========================
     *  HOVER / INTERACTION HELPERS
     * ========================= */

    public static WebElement hoverAndClick(By locator, WebDriver driver) {
        WebElement el = assertPresent(locator, driver);
        new Actions(driver).moveToElement(el).perform();

        try {
            new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                    .until(ExpectedConditions.elementToBeClickable(el));
        } catch (TimeoutException e) {
            throw new AssertionError("Element not clickable even after hover: " + locator, e);
        }

        el.click();
        return el;
    }

    /* =========================
     *  PAGE READY / ANTI-FLAKY
     * ========================= */

    public static void waitForPageReady(WebDriver driver) {
        new WebDriverWait(driver, Duration.ofSeconds(30))
                .until(d -> "complete".equals(
                        ((JavascriptExecutor) d).executeScript("return document.readyState")
                ));
    }

    public static <T> T retry(int times, Supplier<T> action) {
        RuntimeException last = null;
        for (int i = 0; i < times; i++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                last = e;
                if (i == times - 1) throw e;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
        }
        if (last != null) {
            throw last;
        }
        return null;
    }

    /* =========================
     *  SMART ASSERTIONS FROM RECORDED EVENT
     * ========================= */

    public static void assertFromEventTitle(WebDriver driver, RecordedEvent event) {
        if (event == null) return;
        String title = event.getTitle();
        if (title != null && !title.isBlank()) {
            assertTitleContains(driver, title);
        }
    }

    public static void assertFromEventUrl(WebDriver driver, RecordedEvent event) {
        if (event == null) return;
        String url = event.getUrl();
        if (url != null && !url.isBlank()) {
            // contain-match is usually safer than exact, especially with redirects
            assertUrlContains(driver, url);
        }
    }
}
