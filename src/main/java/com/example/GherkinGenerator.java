package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class GherkinGenerator {

    public static void generateFeatureFile(String featureName,
                                           String scenarioName,
                                           String jsonPath) {

        // --- 1) Resolve and validate JSON file ---
        File jsonFile = new File(jsonPath);
        if (!jsonFile.exists()) {
            System.err.println("JSON file for Gherkin generation not found at: "
                    + jsonFile.getAbsolutePath());
            return; // do NOT create feature file in this case
        }

        // --- 2) Prepare feature file path ---
        String safeFeatureName =
                (featureName == null || featureName.trim().isEmpty())
                        ? "Recording"
                        : featureName.trim();

        // convert spaces to underscores for filename
        String outputFileName = safeFeatureName.replaceAll("\\s+", "_") + ".feature";

        Path featuresDir = Paths.get("src", "main", "resources", "features");
        try {
            Files.createDirectories(featuresDir);
        } catch (IOException e) {
            System.err.println("Could not create features directory: " + featuresDir.toAbsolutePath());
            e.printStackTrace();
            return;
        }

        Path featurePath = featuresDir.resolve(outputFileName);
        File featureFile = featurePath.toFile();
        boolean fileExistsAndHasContent = featureFile.exists() && featureFile.length() > 0;

        // --- 3) Read RecordedEvent list from JSON ---
        ObjectMapper mapper = new ObjectMapper();
        CollectionType listType = mapper.getTypeFactory()
                .constructCollectionType(List.class, RecordedEvent.class);

        List<RecordedEvent> events;
        try {
            events = mapper.readValue(jsonFile, listType);
        } catch (IOException e) {
            System.err.println("Error reading JSON for Gherkin from: "
                    + jsonFile.getAbsolutePath());
            e.printStackTrace();
            return;
        }

        if (events == null || events.isEmpty()) {
            System.err.println("⚠ JSON has no events. No steps will be written. File: "
                    + jsonFile.getAbsolutePath());
            return;
        }

        // --- 4) Write Feature + Scenario + Steps ---
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(featureFile, true), StandardCharsets.UTF_8)) {

            // Feature header (only once per file)
            if (!fileExistsAndHasContent) {
                writer.write("Feature: " + safeFeatureName + "\n\n");
            } else {
                writer.write("\n");
            }

            // Scenario header
            String safeScenario =
                    (scenarioName == null || scenarioName.trim().isEmpty())
                            ? "Generated Scenario"
                            : scenarioName.trim();
            writer.write("Scenario: " + safeScenario + "\n");

            boolean firstNavigationSeen = false;
            String previousActionType = null;

            for (RecordedEvent event : events) {
                if (event == null) continue;

                String rawGherkin = event.getRaw_gherkin();
                if (rawGherkin == null || rawGherkin.isEmpty()) {
                    continue;
                }

                String actionType = event.getAction();
                if (actionType == null) {
                    actionType = "other";
                }

                String keyword;
                String stepText = rawGherkin;

                boolean isNavigation = "navigate".equalsIgnoreCase(actionType);

                // ===== Navigation -> Given/Then rules =====
                if (isNavigation) {
                    if (!firstNavigationSeen) {
                        keyword = "Given";
                        if (rawGherkin.startsWith("I navigate to")) {
                            stepText = rawGherkin.replaceFirst("I navigate to", "I visit");
                        }
                        firstNavigationSeen = true;
                    } else {
                        keyword = "Then";
                        if (rawGherkin.startsWith("I navigate to")) {
                            stepText = rawGherkin.replaceFirst("I navigate to", "I am on");
                        }
                    }
                    writer.write(keyword + " " + stepText + "\n");
                    previousActionType = actionType;
                    continue;
                }

                // ===== Non-navigation: When / And logic =====
                String baseKeyword = "When";
                if (previousActionType != null &&
                        previousActionType.equalsIgnoreCase(actionType)) {
                    keyword = "And";
                } else {
                    keyword = baseKeyword;
                }

                writer.write(keyword + " " + stepText + "\n");
                previousActionType = actionType;
            }

            writer.flush();
            System.out.println("Feature file generated from JSON:\n  "
                    + jsonFile.getAbsolutePath()
                    + "\n→ " + featurePath.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("Error generating feature file from: "
                    + jsonFile.getAbsolutePath());
            e.printStackTrace();
        }
    }
}