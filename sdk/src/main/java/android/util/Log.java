package android.util;

import ftcsim.hal.SimLog;

/** Desktop stand-in for android.util.Log; lines go to the simulator's robot log. */
public final class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    private Log() {
    }

    private static String withThrowable(String msg, Throwable tr) {
        return tr == null ? msg : msg + "\n" + getStackTraceString(tr);
    }

    public static int v(String tag, String msg) { SimLog.log('V', tag, msg); return 0; }
    public static int v(String tag, String msg, Throwable tr) { SimLog.log('V', tag, withThrowable(msg, tr)); return 0; }
    public static int d(String tag, String msg) { SimLog.log('D', tag, msg); return 0; }
    public static int d(String tag, String msg, Throwable tr) { SimLog.log('D', tag, withThrowable(msg, tr)); return 0; }
    public static int i(String tag, String msg) { SimLog.log('I', tag, msg); return 0; }
    public static int i(String tag, String msg, Throwable tr) { SimLog.log('I', tag, withThrowable(msg, tr)); return 0; }
    public static int w(String tag, String msg) { SimLog.log('W', tag, msg); return 0; }
    public static int w(String tag, String msg, Throwable tr) { SimLog.log('W', tag, withThrowable(msg, tr)); return 0; }
    public static int w(String tag, Throwable tr) { SimLog.log('W', tag, getStackTraceString(tr)); return 0; }
    public static int e(String tag, String msg) { SimLog.log('E', tag, msg); return 0; }
    public static int e(String tag, String msg, Throwable tr) { SimLog.log('E', tag, withThrowable(msg, tr)); return 0; }
    public static int wtf(String tag, String msg) { SimLog.log('A', tag, msg); return 0; }

    public static boolean isLoggable(String tag, int level) {
        return true;
    }

    public static String getStackTraceString(Throwable tr) {
        if (tr == null) return "";
        java.io.StringWriter sw = new java.io.StringWriter();
        tr.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
