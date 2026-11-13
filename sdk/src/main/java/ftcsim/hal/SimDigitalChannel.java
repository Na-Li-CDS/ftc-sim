package ftcsim.hal;

import com.qualcomm.robotcore.hardware.DigitalChannel;
import com.qualcomm.robotcore.hardware.DigitalChannelController;

/** A digital channel on a simulated hub. */
public final class SimDigitalChannel implements DigitalChannel {
    private final DigitalPort port;
    private final HalSession session;

    public SimDigitalChannel(DigitalPort port, HalSession session) {
        this.port = port;
        this.session = session;
    }

    @Override
    public Mode getMode() {
        return port.mode;
    }

    @Override
    public void setMode(Mode mode) {
        if (!session.isActive()) return;
        Latency.command();
        port.mode = mode;
    }

    @Override
    @Deprecated
    public void setMode(DigitalChannelController.Mode mode) {
        setMode(mode.migrate());
    }

    @Override
    public boolean getState() {
        return port.hub.readDigital(port.channel);
    }

    @Override
    public void setState(boolean state) {
        if (!session.isActive()) return;
        if (port.mode == Mode.OUTPUT) {
            Latency.command();
            port.outputLevel = state;
        }
    }

    @Override
    public Manufacturer getManufacturer() {
        return Manufacturer.Lynx;
    }

    @Override
    public String getDeviceName() {
        return "Digital Device";
    }

    @Override
    public String getConnectionInfo() {
        return port.hub.connectionInfo() + "; digital channel " + port.channel;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void resetDeviceConfigurationForOpMode() {
    }

    @Override
    public void close() {
    }
}
