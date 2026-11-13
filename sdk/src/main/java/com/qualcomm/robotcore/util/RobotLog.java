package com.qualcomm.robotcore.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;

import ftcsim.hal.SimLog;

/**
 * Allows consistent logging across all RobotCore packages. In the simulator, log lines go to the
 * console and to the "Robot log" panel of the browser Driver Station.
 */
public class RobotLog {
    public static final String OPMODE_START_TAG = "******************** START - OPMODE ********************";
    public static final String OPMODE_STOP_TAG = "******************** STOP - OPMODE ********************";
    public static final String TAG = "RobotCore";

    private static volatile String globalErrorMessage = "";
    private static volatile String globalWarningMessage = "";

    private RobotLog() {
    }

    private static String fmt(String format, Object... args) {
        if (args == null || args.length == 0) return format;
        try {
            return String.format(Locale.US, format, args);
        } catch (RuntimeException e) {
            return format;
        }
    }

    private static String trace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static void log(char level, String tag, String message) {
        SimLog.log(level, tag, message);
    }

    public static void a(String format, Object... args) { log('A', TAG, fmt(format, args)); }
    public static void a(String message) { log('A', TAG, message); }
    public static void aa(String tag, String format, Object... args) { log('A', tag, fmt(format, args)); }
    public static void aa(String tag, String message) { log('A', tag, message); }
    public static void aa(String tag, Throwable t, String format, Object... args) { log('A', tag, fmt(format, args) + "\n" + trace(t)); }
    public static void aa(String tag, Throwable t, String message) { log('A', tag, message + "\n" + trace(t)); }

    public static void v(String format, Object... args) { log('V', TAG, fmt(format, args)); }
    public static void v(String message) { log('V', TAG, message); }
    public static void vv(String tag, String format, Object... args) { log('V', tag, fmt(format, args)); }
    public static void vv(String tag, String message) { log('V', tag, message); }
    public static void vv(String tag, Throwable t, String format, Object... args) { log('V', tag, fmt(format, args) + "\n" + trace(t)); }
    public static void vv(String tag, Throwable t, String message) { log('V', tag, message + "\n" + trace(t)); }

    public static void d(String format, Object... args) { log('D', TAG, fmt(format, args)); }
    public static void d(String message) { log('D', TAG, message); }
    public static void dd(String tag, String format, Object... args) { log('D', tag, fmt(format, args)); }
    public static void dd(String tag, String message) { log('D', tag, message); }
    public static void dd(String tag, Throwable t, String format, Object... args) { log('D', tag, fmt(format, args) + "\n" + trace(t)); }
    public static void dd(String tag, Throwable t, String message) { log('D', tag, message + "\n" + trace(t)); }

    public static void i(String format, Object... args) { log('I', TAG, fmt(format, args)); }
    public static void i(String message) { log('I', TAG, message); }
    public static void ii(String tag, String format, Object... args) { log('I', tag, fmt(format, args)); }
    public static void ii(String tag, String message) { log('I', tag, message); }
    public static void ii(String tag, Throwable t, String format, Object... args) { log('I', tag, fmt(format, args) + "\n" + trace(t)); }
    public static void ii(String tag, Throwable t, String message) { log('I', tag, message + "\n" + trace(t)); }

    public static void w(String format, Object... args) { log('W', TAG, fmt(format, args)); }
    public static void w(String message) { log('W', TAG, message); }
    public static void ww(String tag, String format, Object... args) { log('W', tag, fmt(format, args)); }
    public static void ww(String tag, String message) { log('W', tag, message); }
    public static void ww(String tag, Throwable t, String format, Object... args) { log('W', tag, fmt(format, args) + "\n" + trace(t)); }
    public static void ww(String tag, Throwable t, String message) { log('W', tag, message + "\n" + trace(t)); }

    public static void e(String format, Object... args) { log('E', TAG, fmt(format, args)); }
    public static void e(String message) { log('E', TAG, message); }
    public static void ee(String tag, String format, Object... args) { log('E', tag, fmt(format, args)); }
    public static void ee(String tag, String message) { log('E', tag, message); }
    public static void ee(String tag, Throwable t, String format, Object... args) { log('E', tag, fmt(format, args) + "\n" + trace(t)); }
    public static void ee(String tag, Throwable t, String message) { log('E', tag, message + "\n" + trace(t)); }

    public static void logStacktrace(Throwable e) {
        log('E', TAG, trace(e));
    }

    public static void logExceptionHeader(Exception e, String format, Object... args) {
        log('E', TAG, fmt(format, args) + ": " + e);
    }

    public static boolean setGlobalErrorMsg(String message) {
        if (globalErrorMessage.isEmpty()) {
            globalErrorMessage = message;
            SimLog.globalError(message);
            return true;
        }
        return false;
    }

    public static void setGlobalErrorMsg(String format, Object... args) {
        setGlobalErrorMsg(fmt(format, args));
    }

    public static void addGlobalWarningMessage(String message) {
        globalWarningMessage = globalWarningMessage.isEmpty() ? message : globalWarningMessage + "; " + message;
        SimLog.globalWarning(globalWarningMessage);
    }

    public static void addGlobalWarningMessage(String format, Object... args) {
        addGlobalWarningMessage(fmt(format, args));
    }

    public static String getGlobalErrorMsg() {
        return globalErrorMessage;
    }

    public static boolean hasGlobalErrorMsg() {
        return !globalErrorMessage.isEmpty();
    }

    public static boolean hasGlobalWarningMsg() {
        return !globalWarningMessage.isEmpty();
    }

    public static void clearGlobalErrorMsg() {
        globalErrorMessage = "";
        SimLog.globalError("");
    }

    public static void clearGlobalWarningMsg() {
        globalWarningMessage = "";
        SimLog.globalWarning("");
    }
}
