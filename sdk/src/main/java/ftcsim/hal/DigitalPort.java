package ftcsim.hal;

import com.qualcomm.robotcore.hardware.DigitalChannel;

/** A digital port on a hub. Inputs are driven by physics (e.g. a touch sensor's switch). */
public final class DigitalPort {
    public final String name;
    public final SimHub hub;
    public final int channel;

    public volatile DigitalChannel.Mode mode = DigitalChannel.Mode.INPUT;
    /** Electrical level of the pin. REV touch sensors pull the line low when pressed. */
    public volatile boolean inputLevel = true;
    public volatile boolean outputLevel = false;

    public DigitalPort(String name, SimHub hub, int channel) {
        this.name = name;
        this.hub = hub;
        this.channel = channel;
    }

    public boolean level() {
        return mode == DigitalChannel.Mode.OUTPUT ? outputLevel : inputLevel;
    }
}
