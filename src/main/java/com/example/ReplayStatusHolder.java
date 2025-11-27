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
        STATUS.currentAction = ev.getAction();
        STATUS.currentTitle = title;
        STATUS.currentGherkin = ev.getRaw_gherkin();
        STATUS.currentSelenium = ev.getRaw_selenium();
        STATUS.lastError = null;
        // lastStepSuccess remains whatever previous step was
    }

    public static synchronized void stepSuccess() {
        STATUS.lastStepSuccess = true;
        STATUS.lastError = null;
    }

    public static synchronized void stepFailure(Throwable t) {
        STATUS.lastStepSuccess = false;
        STATUS.lastError = t.getMessage();
    }

    public static synchronized void done() {
        STATUS.running = false;
    }
}
