package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.util.List;

public class GherkinGenerator {

    private static final String INPUT_JSON_PATH = "D:\\url-opener-selenium\\recordings\\action_logs.json";

    public static void generateFeatureFile(String featureName, String scenarioName) {

        // 1. Define the output file path based on the feature name
        // The file name should be based on the Feature name for consistency
        String outputFileName = featureName.replaceAll("\\s+", "_") + ".feature";
        String outputFilePath = "D:\\url-opener-selenium\\src\\main\\resources\\features\\" + outputFileName;

        File featureFile = new File(outputFilePath);
        File jsonFile = new File(INPUT_JSON_PATH);

        // Determine if the file already exists to decide whether to write the Feature header.
        boolean fileExistsAndHasContent = featureFile.exists() && featureFile.length() > 0;

        ObjectMapper mapper = new ObjectMapper();

        try (
                // KEY CHANGE: Set 'true' in FileOutputStream for APPEND mode.
                OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(featureFile, true), "UTF-8"
                )
        ) {

            // 1. Read the JSON array into a List of RecordedEvent POJOs
            CollectionType listType = mapper.getTypeFactory()
                    .constructCollectionType(List.class, RecordedEvent.class);
            List<RecordedEvent> events = mapper.readValue(jsonFile, listType);

            // 2. CONDITIONAL FEATURE HEADER
            if (!fileExistsAndHasContent) {
                // If the file is new or empty, write the Feature header first.
                writer.write("Feature: " + featureName + "\n\n");
            } else {
                // If content already exists, add a newline separator before the next scenario.
                writer.write("\n");
            }

            // 3. Write the Scenario header
            writer.write("Scenario: " + scenarioName + "\n");
            String previousKeyword = "";

            for (int i = 0; i < events.size(); i++) {
                RecordedEvent event = events.get(i);
                String rawGherkin = event.getRaw_gherkin();

                if (rawGherkin == null || rawGherkin.isEmpty()) continue;

                String currentKeyword;
                String actionType = event.getAction();

                // --- Keyword Determination (Same as before) ---
                switch (actionType) {

                    case "navigate":
                        currentKeyword = "Given ";
                        break;

                    case "sendKeys":
                    case "click":
                        if (i == events.size() - 1) {
                            currentKeyword = "Then ";
                        } else if (i == 0 && !"navigate".equals(events.get(0).getAction())) {
                            currentKeyword = "Given ";
                        } else {
                            currentKeyword = "When ";
                        }
                        break;

                    default:
                        currentKeyword = (i == 0) ? "Given " : "When ";
                }
                // --- End Keyword Determination ---


                // NEW LOGIC: Substitute Keyword with "And"
                String keywordToWrite;

                // Check if the current keyword matches the previous keyword (ignoring "Then")
                // "Then" is rarely followed by "And", so we prioritize the distinction.
                if (!currentKeyword.equals("Then ") && currentKeyword.equals(previousKeyword)) {
                    keywordToWrite = "And ";
                } else {
                    keywordToWrite = currentKeyword;
                }

                // Write the step using the substituted keyword
                writer.write(keywordToWrite + rawGherkin + "\n");

                // Update the previousKeyword for the next iteration
                // We only track Given, When, or Then. We track the determined keyword, not "And".
                previousKeyword = currentKeyword;
            }

            writer.flush();

            System.out.println("🎉 Successfully appended new scenario to feature file: " + outputFilePath);

        } catch (IOException e) {
            System.err.println("Error generating feature file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}