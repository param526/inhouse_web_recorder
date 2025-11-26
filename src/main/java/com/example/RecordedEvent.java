package com.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RecordedEvent {

    private String action;       // click, navigate, sendKeys
    private String type;         // click, change, navigation
    private String title;
    private String url;
    private String raw_gherkin;
    private String raw_selenium;
    private String selector;     // button, input, span, etc.
    private String value;        // only for sendKeys
    private long timestamp;

    private Map<String, Object> options; // element_text, primary_name,...

    private RecordedTarget target; // NEW nested target object

    // --- Getters & Setters ---

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getRaw_gherkin() { return raw_gherkin; }
    public void setRaw_gherkin(String raw_gherkin) { this.raw_gherkin = raw_gherkin; }

    public String getRaw_selenium() { return raw_selenium; }
    public void setRaw_selenium(String raw_selenium) { this.raw_selenium = raw_selenium; }

    public String getSelector() { return selector; }
    public void setSelector(String selector) { this.selector = selector; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) { this.options = options; }

    public RecordedTarget getTarget() { return target; }
    public void setTarget(RecordedTarget target) { this.target = target; }
}

