package com.possum.infrastructure.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import com.possum.infrastructure.filesystem.AppPaths;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class LoggingConfig {

    private static final String LOG_PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [user:%X{userId}/%X{username}] - %msg%n";
    private static final String MAX_FILE_SIZE = "10MB";
    private static final String TOTAL_SIZE_CAP = "512MB";
    private static final int MAX_HISTORY_DAYS = 30;

    private static boolean configured;

    private LoggingConfig() {
    }

    public static synchronized void configure(AppPaths appPaths) {
        if (configured) {
            return;
        }

        Path logsDir = appPaths.getLogsDir();

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        RollingFileAppender<ILoggingEvent> applicationAppender =
                createRollingAppender(context, logsDir.resolve("application.log"), "application");

        RollingFileAppender<ILoggingEvent> errorAppender =
                createRollingAppender(context, logsDir.resolve("error.log"), "error");

        ThresholdFilter thresholdFilter = new ThresholdFilter();
        thresholdFilter.setLevel("ERROR");
        thresholdFilter.start();
        errorAppender.addFilter(thresholdFilter);

        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
        rootLogger.addAppender(applicationAppender);
        rootLogger.addAppender(errorAppender);

        configured = true;
    }

    private static RollingFileAppender<ILoggingEvent> createRollingAppender(
            LoggerContext context,
            Path activeFile,
            String archivePrefix
    ) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(LOG_PATTERN);
        encoder.start();

        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
        appender.setContext(context);
        appender.setFile(activeFile.toString());
        appender.setEncoder(encoder);

        SizeAndTimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new SizeAndTimeBasedRollingPolicy<>();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(appender);
        rollingPolicy.setFileNamePattern(activeFile.getParent().resolve(archivePrefix + ".%d{yyyy-MM-dd}.%i.log.gz").toString());
        rollingPolicy.setMaxHistory(MAX_HISTORY_DAYS);
        rollingPolicy.setMaxFileSize(FileSize.valueOf(MAX_FILE_SIZE));
        rollingPolicy.setTotalSizeCap(FileSize.valueOf(TOTAL_SIZE_CAP));
        rollingPolicy.start();

        appender.setRollingPolicy(rollingPolicy);
        appender.start();
        return appender;
    }

    public static org.slf4j.Logger getLogger() {
        return LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    }
}
