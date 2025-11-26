package com.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocatorCandidate {
    public String type;   // id, dataTest, name, aria, css, xpathText, etc.
    public String value;
    public int score;

    // Getters & Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
}

