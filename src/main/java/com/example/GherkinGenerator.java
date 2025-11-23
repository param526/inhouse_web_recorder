package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GherkinGenerator {

    private static final String INPUT_JSON_PATH = "D:\\url-opener-selenium\\recordings\\action_logs.json";

    public static void generateFeatureFile(String featureName, String scenarioName) {

        String outputFileName = featureName.replaceAll("\\s+", "_") + ".feature";
        String outputFilePath = "D:\\url-opener-selenium\\src\\main\\resources\\features\\" + outputFileName;

        File featureFile = new File(outputFilePath);
        File jsonFile = new File(INPUT_JSON_PATH);

        boolean fileExistsAndHasContent = featureFile.exists() && featureFile.length() > 0;

        ObjectMapper mapper = new ObjectMapper();

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(featureFile, true), StandardCharsets.UTF_8)) {

            // 1. Read JSON → List<RecordedEvent>
            CollectionType listType = mapper.getTypeFactory()
                    .constructCollectionType(List.class, RecordedEvent.class);
            List<RecordedEvent> events = mapper.readValue(jsonFile, listType);

            // NOTE: We assume the JSON array is already in chronological order.
            // No sorting by timestamp to avoid getTimestamp() issues.

            // 2. Conditional Feature header
            if (!fileExistsAndHasContent) {
                writer.write("Feature: " + featureName + "\n\n");
            } else {
                writer.write("\n");
            }

            // 3. Scenario header
            writer.write("Scenario: " + scenarioName + "\n");

            boolean firstNavigationSeen = false;
            String previousActionType = null;  // For When/And logic

            for (int i = 0; i < events.size(); i++) {
                RecordedEvent event = events.get(i);

                String rawGherkin = event.getRaw_gherkin();
                if (rawGherkin == null || rawGherkin.isEmpty()) {
                    continue;
                }

                String actionType = event.getAction(); // "navigate", "sendKeys", "click", etc.
                if (actionType == null) {
                    actionType = "other";
                }

                String keyword;
                String stepText = rawGherkin;

                boolean isNavigation = "navigate".equalsIgnoreCase(actionType);
                boolean isSendKeys = "sendKeys".equalsIgnoreCase(actionType);

                // ========== Navigation handling ==========
                if (isNavigation) {

                    // First navigation → Given I visit "" page
                    if (!firstNavigationSeen) {
                        keyword = "Given";

                        // Replace leading 'I navigate to' with 'I visit'
                        if (rawGherkin.startsWith("I navigate to")) {
                            stepText = rawGherkin.replaceFirst("I navigate to", "I visit");
                        } else {
                            // Fallback if pattern changes
                            stepText = rawGherkin;
                        }

                        firstNavigationSeen = true;

                    } else {
                        // Subsequent navigation → Then I am on "" page
                        keyword = "Then";

                        if (rawGherkin.startsWith("I navigate to")) {
                            stepText = rawGherkin.replaceFirst("I navigate to", "I am on");
                        } else {
                            stepText = rawGherkin;
                        }
                    }

                    writer.write(keyword + " " + stepText + "\n");
                    previousActionType = actionType;
                    continue;
                }

                // ========== Non-navigation handling (sendKeys, click, etc.) ==========

                // Base keyword is When
                String baseKeyword = "When";

                // Apply "And" logic:
                // If previous event had the same action type → And
                // Otherwise → When
                if (previousActionType != null &&
                        previousActionType.equalsIgnoreCase(actionType)) {
                    keyword = "And";
                } else {
                    keyword = baseKeyword;
                }

                // For sendKeys: requirement said When for sendKeys, And for consecutive sendKeys.
                // The above logic satisfies that, since sendKeys is identified by actionType.

                writer.write(keyword + " " + stepText + "\n");

                // Track action type for next iteration
                previousActionType = actionType;
            }

            writer.flush();
            System.out.println("🎉 Successfully appended new scenario to feature file: " + outputFilePath);

        } catch (IOException e) {
            System.err.println("Error generating feature file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
