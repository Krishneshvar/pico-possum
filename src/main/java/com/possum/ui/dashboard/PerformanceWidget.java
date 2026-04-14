package com.possum.ui.dashboard;

import com.possum.infrastructure.monitoring.PerformanceMonitor;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

import java.util.Map;

public class PerformanceWidget extends VBox {
    
    @FXML private Label taxCalcLabel;
    @FXML private Label auditLogLabel;
    @FXML private Label dbQueryLabel;
    @FXML private Label cacheHitLabel;
    
    private final PerformanceMonitor performanceMonitor;
    private Timeline refreshTimeline;

    public PerformanceWidget(PerformanceMonitor performanceMonitor) {
        this.performanceMonitor = performanceMonitor;
    }

    @FXML
    public void initialize() {
        startAutoRefresh();
    }

    private void startAutoRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> refresh()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    public void refresh() {
        var stats = performanceMonitor.getAllStats();
        
        updateMetric(taxCalcLabel, "Tax Calculations", stats.get("tax_calculation"));
        updateMetric(auditLogLabel, "Audit Logs", stats.get("audit_log"));
        updateMetric(dbQueryLabel, "DB Queries", stats.get("db_query"));
        
        var cacheStats = stats.get("cache_hit");
        if (cacheStats != null && cacheStats.count() > 0) {
            double hitRate = cacheStats.successRate();
            cacheHitLabel.setText(String.format("Cache Hit Rate: %.1f%%", hitRate));
        } else {
            cacheHitLabel.setText("Cache Hit Rate: N/A");
        }
    }

    private void updateMetric(Label label, String name, PerformanceMonitor.OperationStats stats) {
        if (stats == null || stats.count() == 0) {
            label.setText(name + ": N/A");
            return;
        }
        
        double avgMs = stats.avgDurationMillis();
        long count = stats.count();
        
        label.setText(String.format("%s: %.2fms avg (%d ops)", name, avgMs, count));
    }

    public void stop() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
    }
}
