package com.qualcomm.robotcore.exception;

/** RobotCore exception. */
public class RobotCoreException extends Exception {
    public RobotCoreException(String message) {
        super(message);
    }

    public RobotCoreException(String format, Object... args) {
        super(String.format(format, args));
    }

    public static RobotCoreException createChained(Exception e, String format, Object... args) {
        RobotCoreException result = new RobotCoreException(format, args);
        result.initCause(e);
        return result;
    }
}
