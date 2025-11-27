// ReplayStatus.java
package com.example;

public class ReplayStatus {
    public boolean running;
    public int totalSteps;
    public int currentIndex;      // 1-based
    public String currentAction;  // "click", "sendKeys", etc.
    public String currentTitle;   // page title
    public String currentGherkin;
    public String currentSelenium;
    public boolean lastStepSuccess;
    public String lastError;
}


