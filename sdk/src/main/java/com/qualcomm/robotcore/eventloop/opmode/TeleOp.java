package com.qualcomm.robotcore.eventloop.opmode;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Provides an easy and non-centralized way of determining the OpMode list shown on the Driver Station. */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TeleOp {
    /** The name to be used on the driver station display. If empty, the name of the OpMode class is used. */
    String name() default "";

    /** OpModes with the same non-empty group name are grouped together on the Driver Station. */
    String group() default "";
}
