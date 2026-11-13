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
public @interface Autonomous {
    String name() default "";

    String group() default "";

    /** The name of a TeleOp OpMode to select on the Driver Station after this OpMode ends. */
    String preselectTeleOp() default "";
}
