package com.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RecordedTarget {
    private String tagName;
    private String text;

    private Map<String, String> attributes;  // id, name, class, etc.

    private List<LocatorCandidate> locators; // array of locator candidates

    // --- Getters & Setters ---
    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }

    public List<LocatorCandidate> getLocators() { return locators; }
    public void setLocators(List<LocatorCandidate> locators) { this.locators = locators; }
}

