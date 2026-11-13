package com.qualcomm.robotcore.hardware;

/** ServoControllerEx is an optional servo controller interface supported by some hardware. */
public interface ServoControllerEx extends ServoController {

    void setServoPwmRange(int servo, PwmControl.PwmRange range);

    PwmControl.PwmRange getServoPwmRange(int servo);

    void setServoPwmEnable(int servo);

    void setServoPwmDisable(int servo);

    boolean isServoPwmEnabled(int servo);
}
