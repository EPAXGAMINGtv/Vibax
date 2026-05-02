package de.epax.logging;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

    public enum Level {
        INFO, WARN, ERROR, DEBUG
    }

    private static final String LOG_DIR = "logs";
    private static PrintWriter writer;

    private static final PrintStream originalOut = System.out;
    private static final PrintStream originalErr = System.err;

    static {
        try {
            new File(LOG_DIR).mkdirs();

            String fileName = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".log";
            String fullPath = LOG_DIR + "/" + fileName;

            writer = new PrintWriter(new FileWriter(fullPath, true), true);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.setOut(new PrintStream(new LoggerOutputStream(Level.INFO), true));
        System.setErr(new PrintStream(new LoggerOutputStream(Level.ERROR), true));

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            log(Level.ERROR, "Uncaught Exception in thread " + thread.getName());
            throwable.printStackTrace(writer);
            throwable.printStackTrace(originalErr);
        });
    }

    private static String getTime() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    private static String getColor(Level level) {
        return switch (level) {
            case INFO -> "\u001B[32m";
            case WARN -> "\u001B[33m";
            case ERROR -> "\u001B[31m";
            case DEBUG -> "\u001B[36m";
        };
    }

    private static final String RESET = "\u001B[0m";

    public static void log(Level level, String message) {
        String time = getTime();
        String prefix = "[" + time + "] [Server thread/" + level + "]: ";
        String formatted = prefix + message;

        String colored = getColor(level) + formatted + RESET;

        originalOut.println(colored);
        writer.println(formatted);
    }

    public static void info(String msg) {
        log(Level.INFO, msg);
    }

    public static void warn(String msg) {
        log(Level.WARN, msg);
    }

    public static void error(String msg) {
        log(Level.ERROR, msg);
    }

    public static void debug(String msg) {
        log(Level.DEBUG, msg);
    }

    static class LoggerOutputStream extends OutputStream {
        private final Level level;
        private final StringBuilder buffer = new StringBuilder();

        public LoggerOutputStream(Level level) {
            this.level = level;
        }

        @Override
        public void write(int b) {
            if (b == '\n') {
                if (!buffer.isEmpty()) {
                    String msg = buffer.toString();
                    buffer.setLength(0);
                    Logger.log(level, msg + " [STDOUT]");
                }
            } else {
                buffer.append((char) b);
            }
        }
    }
}