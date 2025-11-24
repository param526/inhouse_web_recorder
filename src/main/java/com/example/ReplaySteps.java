package com.example;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.Scenario;
import org.junit.Assert;
import com.example.RawSeleniumReplayer;

import java.io.File;

public class ReplaySteps {

    private Scenario scenario;

    @Before
    public void before(Scenario scenario) {
        this.scenario = scenario;
    }

    @Given("I replay raw selenium actions from {string}")
    public void iReplayRawSeleniumActionsFrom(String jsonPath) throws Exception {

        // Create the folder "replay-recordings" inside project root
        String projectPath = System.getProperty("user.dir");
        String replayDirPath = projectPath + File.separator + "replay-recordings";

        File replayDir = new File(replayDirPath);
        if (!replayDir.exists()) {
            replayDir.mkdirs();  // creates folder if missing
        }

        // Report file location inside the new folder
        String reportPath = replayDirPath + File.separator + "raw-selenium-replay-report.html";

        boolean ok = RawSeleniumReplayer.replayFromJson(jsonPath, reportPath, scenario);

        Assert.assertTrue(
                "Raw Selenium replay failed. Check attached HTML report in /replay-recordings folder.",
                ok
        );
    }
}