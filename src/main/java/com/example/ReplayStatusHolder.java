// ReplayStatusHolder.java
package com.example;

public class ReplayStatusHolder {
    private static final ReplayStatus STATUS = new ReplayStatus();

    public static synchronized ReplayStatus get() {
        return STATUS;
    }

    public static synchronized void init(int totalSteps) {
        STATUS.running = true;
        STATUS.totalSteps = totalSteps;
        STATUS.currentIndex = 0;
        STATUS.currentAction = "";
        STATUS.currentTitle = "";
        STATUS.currentGherkin = "";
        STATUS.currentSelenium = "";
        STATUS.lastStepSuccess = true;
        STATUS.lastError = null;
    }

    public static synchronized void beforeStep(
            int index,
            RecordedEvent ev,
            String title
    ) {
        STATUS.currentIndex = index + 1; // 1-based for UI

        if (ev == null) {
            STATUS.currentAction = "";
            STATUS.currentTitle = title == null ? "" : title;
            STATUS.currentGherkin = "";
            STATUS.currentSelenium = "";
            STATUS.lastError = null;
            return;
        }

        STATUS.currentAction = ev.getAction() == null ? "" : ev.getAction();
        STATUS.currentTitle = title == null ? "" : title;
        STATUS.currentGherkin = ev.getRaw_gherkin() == null ? "" : ev.getRaw_gherkin();
        STATUS.currentSelenium = ev.getRaw_selenium() == null ? "" : ev.getRaw_selenium();
        STATUS.lastError = null;
        // lastStepSuccess remains whatever previous step was
    }

    public static synchronized void stepSuccess() {
        STATUS.lastStepSuccess = true;
        STATUS.lastError = null;
    }

    public static synchronized void stepFailure(Throwable t) {
        STATUS.lastStepSuccess = false;
        STATUS.lastError = buildErrorMessage(t);
    }

    public static synchronized void done() {
        STATUS.running = false;
    }

    private static String buildErrorMessage(Throwable t) {
        if (t == null) {
            return "Unknown replay error";
        }

        String message = trimToNull(t.getMessage());
        if (message != null) {
            return message;
        }

        Throwable cause = t.getCause();
        while (cause != null) {
            String causeMessage = trimToNull(cause.getMessage());
            if (causeMessage != null) {
                return causeMessage;
            }
            if (cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }

        return t.getClass().getSimpleName();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
