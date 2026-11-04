package io.kaoto.forage.core.util.config;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

/**
 * Captures every log event, at every level, while open: lets tests check that secrets are never logged.
 */
public final class LogCapture implements AutoCloseable {

    private final List<String> lines = new CopyOnWriteArrayList<>();
    private final AbstractAppender appender;
    private final Level previousLevel;

    private LogCapture() {
        appender = new AbstractAppender("forage-log-capture", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                StringBuilder line = new StringBuilder(event.getMessage().getFormattedMessage());
                for (Throwable t = event.getThrown(); t != null; t = t.getCause()) {
                    line.append(" | ").append(t);
                }
                lines.add(line.toString());
            }
        };
        appender.start();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        LoggerConfig root = context.getConfiguration().getRootLogger();
        previousLevel = root.getLevel();
        root.addAppender(appender, Level.ALL, null);
        root.setLevel(Level.ALL);
        context.updateLoggers();
    }

    public static LogCapture start() {
        return new LogCapture();
    }

    public List<String> lines() {
        return List.copyOf(lines);
    }

    @Override
    public void close() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        LoggerConfig root = configuration.getRootLogger();
        root.removeAppender(appender.getName());
        root.setLevel(previousLevel);
        context.updateLoggers();
        appender.stop();
    }
}
