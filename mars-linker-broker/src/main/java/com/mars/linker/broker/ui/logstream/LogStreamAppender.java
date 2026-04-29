package com.mars.linker.broker.ui.logstream;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class LogStreamAppender extends AppenderBase<ILoggingEvent> {

    private static LogStreamAppender instance;

    public LogStreamAppender() {
        instance = this;
    }

    public static LogStreamAppender getInstance() {
        return instance;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) return;
        String level = event.getLevel().toString();
        String logger = event.getLoggerName();
        String message = event.getFormattedMessage();
        String thread = event.getThreadName();
        LogStreamWsHandler.pushLog(level, logger, message, thread);
    }
}
