package at.fhv.sysarch.lab2.homeautomation.devices;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class AirCondition extends AbstractBehavior<AirCondition.AirConditionCommand> {
    public interface AirConditionCommand {}

    public static final class PowerAirCondition implements AirConditionCommand {
        final boolean on;

        public PowerAirCondition(boolean on) {
            this.on = on;
        }
    }

    public static final class EnrichedTemperature implements AirConditionCommand {
        final double value;
        final String unit;

        public EnrichedTemperature(double value, String unit) {
            this.value = value;
            this.unit = unit;
        }
    }

    private final String groupId;
    private final String deviceId;
    private boolean isOn = false;
    private final double activationThreshold = 20.0; // Celsius

    public AirCondition(ActorContext<AirConditionCommand> context, String groupId, String deviceId) {
        super(context);
        this.groupId = groupId;
        this.deviceId = deviceId;
        getContext().getLog().info("AirCondition {}-{} started", groupId, deviceId);
    }

    public static Behavior<AirConditionCommand> create(String groupId, String deviceId) {
        return Behaviors.setup(context -> new AirCondition(context, groupId, deviceId));
    }

    @Override
    public Receive<AirConditionCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(EnrichedTemperature.class, this::onTemperatureUpdate)
                .onMessage(PowerAirCondition.class, this::onPowerCommand)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<AirConditionCommand> onTemperatureUpdate(EnrichedTemperature temp) {
        if (!"°C".equals(temp.unit)) {
            getContext().getLog().warn("Received temperature in unsupported unit: {}", temp.unit);
            return this;
        }

        boolean shouldBeOn = temp.value > activationThreshold;

        if (shouldBeOn && !isOn) {
            getContext().getLog().info("Turning AC ON (Temperature: {}°C)", temp.value);
            isOn = true;
        } else if (!shouldBeOn && isOn) {
            getContext().getLog().info("Turning AC OFF (Temperature: {}°C)", temp.value);
            isOn = false;
        }

        return this;
    }

    private Behavior<AirConditionCommand> onPowerCommand(PowerAirCondition cmd) {
        if (cmd.on != isOn) {
            isOn = cmd.on;
            getContext().getLog().info("AC manually turned {}", isOn ? "ON" : "OFF");
        }
        return this;
    }

    private Behavior<AirConditionCommand> onPostStop() {
        getContext().getLog().info("AirCondition actor {}-{} stopped", groupId, deviceId);
        return this;
    }
}