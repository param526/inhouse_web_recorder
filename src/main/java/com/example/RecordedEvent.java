package com.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RecordedEvent {
    public String action;
    public String type;
    public String title;
    public String url;
    public String raw_gherkin;
    public String raw_selenium;
    public long timestamp;

    // Getters and Setters for raw_gherkin
    public String getRaw_gherkin() { return raw_gherkin; }
    public void setRaw_gherkin(String raw_gherkin) { this.raw_gherkin = raw_gherkin; }

    // Getter and Setter for action
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public long getTimestamp() {
        return timestamp;
    }
}
