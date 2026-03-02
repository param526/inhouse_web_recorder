package com.example.routes;

import com.example.RawSeleniumReplayer;
import com.example.ReplayStatusHolder;
import com.example.auth.AuthFilter;
import com.example.dao.RecordingDao;
import com.example.dao.RunDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static spark.Spark.*;

public class ReplayRoutes {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ExecutorService REPLAY_EXECUTOR = Executors.newSingleThreadExecutor();
    private static volatile Future<?> currentReplayFuture = null;
    private static volatile long currentRunId = -1;

    public static void register() {

        post("/api/replay", (req, res) -> {
            res.type("application/json");

            synchronized (ReplayRoutes.class) {
                if (currentReplayFuture != null && !currentReplayFuture.isDone()) {
                    res.status(409);
                    return "{\"error\":\"Replay already running\"}";
                }

                JsonNode body = MAPPER.readTree(req.body());
                long recordingId = body.get("recording_id").asLong();
                String mode = body.has("mode") ? body.get("mode").asText() : "LOCAL";

                // Get recording steps
                String stepsJson = RecordingDao.getStepsJson(recordingId);
                if (stepsJson == null) {
                    res.status(404);
                    return "{\"error\":\"Recording not found\"}";
                }

                // Create run record
                Long userId = AuthFilter.getUserId(req);
                long runId = RunDao.create(recordingId, mode, userId);
                currentRunId = runId;

                // Write steps to temp file for replayer
                String workingDir = System.getProperty("user.dir");
                String tempDir = workingDir + File.separator + "replay-recordings";
                Files.createDirectories(Path.of(tempDir));
                String tempJsonPath = tempDir + File.separator + "temp_replay_" + runId + ".json";
                Files.writeString(Path.of(tempJsonPath), stepsJson, StandardCharsets.UTF_8);

                String reportPath = tempDir + File.separator + "report_" + runId + ".html";

                currentReplayFuture = REPLAY_EXECUTOR.submit(() -> {
                    long startTime = System.currentTimeMillis();
                    try {
                        boolean ok = RawSeleniumReplayer.replayFromJson(tempJsonPath, reportPath);
                        long duration = System.currentTimeMillis() - startTime;
                        String status = ok ? "PASSED" : "FAILED";

                        // Get error from last failed step
                        String errorMsg = null;
                        if (!ok) {
                            var replayStatus = ReplayStatusHolder.get();
                            if (replayStatus.lastError != null) {
                                errorMsg = replayStatus.lastError;
                            }
                        }

                        RunDao.updateStatus(runId, status, duration, errorMsg);
                        RunDao.updatePaths(runId, reportPath, null);

                    } catch (InterruptedException ie) {
                        long duration = System.currentTimeMillis() - startTime;
                        try {
                            RunDao.updateStatus(runId, "STOPPED", duration, "Stopped by user");
                        } catch (Exception ignored) {}
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        long duration = System.currentTimeMillis() - startTime;
                        try {
                            RunDao.updateStatus(runId, "FAILED", duration, e.getMessage());
                        } catch (Exception ignored) {}
                    } finally {
                        // Cleanup temp file
                        try { Files.deleteIfExists(Path.of(tempJsonPath)); } catch (Exception ignored) {}
                    }
                });

                return "{\"run_id\":" + runId + ",\"started\":true}";
            }
        });

        post("/api/replay/stop", (req, res) -> {
            res.type("application/json");
            RawSeleniumReplayer.stopCurrentReplay();
            Future<?> f = currentReplayFuture;
            if (f != null && !f.isDone()) {
                f.cancel(true);
            }
            return "{\"message\":\"Replay stopped\"}";
        });

        get("/api/replay/status", (req, res) -> {
            res.type("application/json");
            var status = ReplayStatusHolder.get();
            var map = new java.util.HashMap<String, Object>();
            map.put("running", status.running);
            map.put("totalSteps", status.totalSteps);
            map.put("currentIndex", status.currentIndex);
            map.put("currentAction", status.currentAction);
            map.put("currentTitle", status.currentTitle);
            map.put("currentGherkin", status.currentGherkin);
            map.put("currentSelenium", status.currentSelenium);
            map.put("lastStepSuccess", status.lastStepSuccess);
            map.put("lastError", status.lastError);
            map.put("run_id", currentRunId);
            return MAPPER.writeValueAsString(map);
        });

        // Runs API
        get("/api/runs", (req, res) -> {
            res.type("application/json");
            String status = req.queryParams("status");
            String projectIdStr = req.queryParams("project_id");
            String moduleIdStr = req.queryParams("module_id");
            int limit = parseIntOrDefault(req.queryParams("limit"), 8);
            int offset = parseIntOrDefault(req.queryParams("offset"), 0);

            Long projectId = projectIdStr != null && !projectIdStr.isEmpty() ? Long.parseLong(projectIdStr) : null;
            Long moduleId = moduleIdStr != null && !moduleIdStr.isEmpty() ? Long.parseLong(moduleIdStr) : null;

            List<Map<String, Object>> runs;
            if (AuthFilter.isAdmin(req)) {
                runs = RunDao.findAll(status, projectId, moduleId, limit, offset);
            } else {
                var projects = com.example.dao.ProjectDao.findByUserId(AuthFilter.getUserId(req));
                var projectIds = new java.util.ArrayList<Long>();
                for (var p : projects) projectIds.add((Long) p.get("id"));
                runs = RunDao.findByProjectIds(projectIds, status, limit, offset);
            }
            return MAPPER.writeValueAsString(runs);
        });

        get("/api/runs/:id", (req, res) -> {
            res.type("application/json");
            long id = Long.parseLong(req.params(":id"));
            Map<String, Object> run = RunDao.findById(id);
            if (run == null) {
                res.status(404);
                return "{\"error\":\"Run not found\"}";
            }
            run.put("steps", RunDao.getRunSteps(id));
            return MAPPER.writeValueAsString(run);
        });

        get("/api/recordings/:id/runs", (req, res) -> {
            res.type("application/json");
            long recordingId = Long.parseLong(req.params(":id"));
            int limit = parseIntOrDefault(req.queryParams("limit"), 8);
            int offset = parseIntOrDefault(req.queryParams("offset"), 0);
            return MAPPER.writeValueAsString(RunDao.findByRecordingId(recordingId, limit, offset));
        });

        // Serve report HTML
        get("/api/runs/:id/report", (req, res) -> {
            long id = Long.parseLong(req.params(":id"));
            Map<String, Object> run = RunDao.findById(id);
            if (run == null || run.get("report_path") == null) {
                res.status(404);
                return "Report not found";
            }
            File reportFile = new File((String) run.get("report_path"));
            if (!reportFile.exists()) {
                res.status(404);
                return "Report file not found";
            }
            res.type("text/html; charset=UTF-8");
            return Files.readString(reportFile.toPath(), StandardCharsets.UTF_8);
        });

        // Serve video
        get("/api/runs/:id/video", (req, res) -> {
            long id = Long.parseLong(req.params(":id"));
            Map<String, Object> run = RunDao.findById(id);
            if (run == null || run.get("video_path") == null) {
                res.status(404);
                return "Video not found";
            }
            File videoFile = new File((String) run.get("video_path"));
            if (!videoFile.exists()) {
                res.status(404);
                return "Video file not found";
            }
            res.type("video/webm");
            byte[] bytes = Files.readAllBytes(videoFile.toPath());
            res.raw().getOutputStream().write(bytes);
            res.raw().getOutputStream().flush();
            return res.raw();
        });

        // Serve screenshots
        get("/api/screenshots/*", (req, res) -> {
            String fileName = req.splat()[0];
            Path imgPath = Path.of(System.getProperty("user.dir"), "replay-recordings", "screenshots", fileName);
            if (!Files.exists(imgPath)) {
                res.status(404);
                return "Screenshot not found";
            }
            res.type("image/png");
            byte[] bytes = Files.readAllBytes(imgPath);
            res.raw().getOutputStream().write(bytes);
            res.raw().getOutputStream().flush();
            return res.raw();
        });
    }

    private static int parseIntOrDefault(String s, int defaultVal) {
        if (s == null || s.isEmpty()) return defaultVal;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultVal; }
    }
}
