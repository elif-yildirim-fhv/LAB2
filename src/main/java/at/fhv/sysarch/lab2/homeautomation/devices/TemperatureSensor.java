package at.fhv.sysarch.lab2.homeautomation.devices;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class TemperatureSensor extends AbstractBehavior<TemperatureSensor.TemperatureCommand> {
    public interface TemperatureCommand {}

    public static final class ReadTemperature implements TemperatureCommand {
        final ActorRef<TemperatureResponse> replyTo;

        public ReadTemperature(ActorRef<TemperatureResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class TemperatureResponse {
        final double value;
        final String unit;

        public TemperatureResponse(double value, String unit) {
            this.value = value;
            this.unit = unit;
        }
    }

    private final String groupId;
    private final String deviceId;
    private double currentTemperature = 20.0;

    public TemperatureSensor(ActorContext<TemperatureCommand> context, String groupId, String deviceId) {
        super(context);
        this.groupId = groupId;
        this.deviceId = deviceId;
        getContext().getLog().info("TemperatureSensor {}-{} started", groupId, deviceId);
    }

    public static Behavior<TemperatureCommand> create(String groupId, String deviceId) {
        return Behaviors.setup(context -> new TemperatureSensor(context, groupId, deviceId));
    }

    public void updateTemperature(double newTemp) {
        this.currentTemperature = newTemp;
    }

    @Override
    public Receive<TemperatureCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(ReadTemperature.class, this::onReadTemperature)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<TemperatureCommand> onReadTemperature(ReadTemperature cmd) {
        cmd.replyTo.tell(new TemperatureResponse(currentTemperature, "°C"));
        getContext().getLog().debug("Temperature reading: {}°C", currentTemperature);
        return this;
    }

    private Behavior<TemperatureCommand> onPostStop() {
        getContext().getLog().info("TemperatureSensor actor {}-{} stopped", groupId, deviceId);
        return this;
    }
}