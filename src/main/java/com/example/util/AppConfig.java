package com.example.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Centralized configuration loader.
 * Reads defaults from application.properties, then overrides with environment variables.
 *
 * Environment variable mapping: property key "db.host" maps to env var "DB_HOST"
 * (dots replaced with underscores, uppercased).
 */
public class AppConfig {

    private static final Properties props = new Properties();
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;
        try (InputStream in = AppConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
                System.out.println("[CONFIG] Loaded application.properties");
            } else {
                System.out.println("[CONFIG] application.properties not found on classpath, using env vars only");
            }
        } catch (IOException e) {
            System.err.println("[CONFIG] Error reading application.properties: " + e.getMessage());
        }
        loaded = true;
    }

    /**
     * Get a config value. Resolution order:
     * 1. Environment variable (key with dots replaced by underscores, uppercased)
     * 2. Value from application.properties
     * 3. Provided default
     */
    public static String get(String key, String defaultValue) {
        if (!loaded) load();

        String envKey = key.replace('.', '_').toUpperCase();
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isEmpty()) {
            return envVal;
        }

        return props.getProperty(key, defaultValue);
    }

    public static String get(String key) {
        return get(key, null);
    }

    public static int getInt(String key, int defaultValue) {
        String val = get(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue) {
        String val = get(key);
        if (val == null) return defaultValue;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
