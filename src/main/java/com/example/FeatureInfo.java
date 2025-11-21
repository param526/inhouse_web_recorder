package com.example;

public class FeatureInfo {
    private String feature;
    private String scenario;

    // Default constructor is required by Jackson
    public FeatureInfo() {
    }

    // Getters and Setters for 'feature'
    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    // Getters and Setters for 'scenario'
    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }
}
