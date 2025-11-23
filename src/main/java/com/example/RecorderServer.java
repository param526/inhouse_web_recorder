package com.example;

public class RecorderServer {

    public static class Status {
        private boolean installed;
        private int count;
        private String last_raw_gherkin;

        public Status(boolean installed, int count, String last_raw_gherkin) {
            this.installed = installed;
            this.count = count;
            this.last_raw_gherkin = last_raw_gherkin;
        }

        public boolean isInstalled() { return installed; }
        public int getCount() { return count; }
        public String getLast_raw_gherkin() { return last_raw_gherkin; }
    }

}

