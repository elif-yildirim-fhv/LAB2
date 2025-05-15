package at.fhv.sysarch.lab2.homeautomation.devices;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.shared.Temperature;

public class AirCondition extends AbstractBehavior<AirCondition.AirConditionCommand> {

    public interface AirConditionCommand {}

    // Temperatur empfangen
    public static final class ReceiveTemperature implements AirConditionCommand {
        public final Temperature temperature;

        public ReceiveTemperature(Temperature temperature) {
            this.temperature = temperature;
        }
    }

    // Optional: externe Steuerung (falls gewünscht)
    public static final class PowerAirCondition implements AirConditionCommand {
        public final boolean powerOn;
        public PowerAirCondition(boolean powerOn) {
            this.powerOn = powerOn;
        }
    }

    private boolean isActive = false;

    public static Behavior<AirConditionCommand> create() {
        return Behaviors.setup(AirCondition::new);
    }

    private AirCondition(ActorContext<AirConditionCommand> context) {
        super(context);
        context.getLog().info("AirCondition started");
    }

    @Override
    public Receive<AirConditionCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(ReceiveTemperature.class, this::onReceiveTemperature)
                .onMessage(PowerAirCondition.class, this::onPowerToggle)
                .build();
    }

    private Behavior<AirConditionCommand> onReceiveTemperature(ReceiveTemperature msg) {
        double value = msg.temperature.value();
        getContext().getLog().info("[DEVICE] Received temperature: {}", value);

        if (value >= 20 && !isActive) {
            isActive = true;
            getContext().getLog().info("[DEVICE] AirCondition activated (>= 20°C)");
        } else if (value < 20 && isActive) {
            isActive = false;
            getContext().getLog().info("[DEVICE] AirCondition deactivated (< 20°C)");
        }

        return this;
    }

    private Behavior<AirConditionCommand> onPowerToggle(PowerAirCondition msg) {
        isActive = msg.powerOn;
        getContext().getLog().info("[DEVICE] AirCondition manually turned {}", isActive ? "ON" : "OFF");
        return this;
    }
}
