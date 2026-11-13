package com.qualcomm.robotcore.eventloop.opmode;

/** Holder for OpMode-manager types that robot code may reference. */
public class OpModeManagerImpl {
    public static final String DEFAULT_OP_MODE_NAME = "$Stop$Robot$";

    private OpModeManagerImpl() {
    }

    /** Thrown by {@link OpMode#terminateOpModeNow()} to unwind the OpMode's thread. */
    public static class ForceStopException extends RuntimeException {
        public ForceStopException() {
            super("OpMode terminated");
        }
    }
}
