package com.example;

public class FeatureInfo {
    private String feature;
    private String scenario;
    private String recording; // <-- NEW

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getRecording() {
        return recording;
    }

    public void setRecording(String recording) {
        this.recording = recording;
    }
}
