package com.possum;


public final class AppLauncher {
    public static void main(String[] args) {
        // Fix scaling issue on Windows where JavaFX might pick up a high DPI and make the UI too large.
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            if (System.getProperty("glass.win.uiScale") == null) {
                // Defaulting to 1.0 prevents the "huge UI" issue on high DPI Windows displays.
                System.setProperty("glass.win.uiScale", "1.0");
            }
        }
        javafx.application.Application.launch(MainApp.class, args);
    }

    public static class MainApp extends javafx.application.Application {
        private AppBootstrap appBootstrap;

        @Override
        public void start(javafx.stage.Stage primaryStage) {
            appBootstrap = new AppBootstrap();
            appBootstrap.start(primaryStage);
        }

        @Override
        public void stop() {
            if (appBootstrap != null) {
                appBootstrap.shutdown();
            }
        }
    }
}
