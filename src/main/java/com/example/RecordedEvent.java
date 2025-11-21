package com.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RecordedEvent {
    private String raw_gherkin;
    private String action; // New field to read the action type

    // Getters and Setters for raw_gherkin
    public String getRaw_gherkin() { return raw_gherkin; }
    public void setRaw_gherkin(String raw_gherkin) { this.raw_gherkin = raw_gherkin; }

    // Getter and Setter for action
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
