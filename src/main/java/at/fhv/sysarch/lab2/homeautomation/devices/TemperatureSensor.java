package at.fhv.sysarch.lab2.homeautomation.devices;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.environment.TemperatureEnvironment;
import at.fhv.sysarch.lab2.homeautomation.shared.Temperature;

import java.time.Duration;

public class TemperatureSensor extends AbstractBehavior<TemperatureSensor.TemperatureCommand> {

    public interface TemperatureCommand {}

    public static final class DoRequestTemperature implements TemperatureCommand {}
    public static final class ReceiveTemperature implements TemperatureCommand {
        public final Temperature temperature;

        public ReceiveTemperature(Temperature temperature) {
            this.temperature = temperature;
        }
    }

    public static Behavior<TemperatureCommand> create(
            ActorRef<TemperatureEnvironment.TemperatureEnvironmentCommand> environment,
            ActorRef<AirCondition.AirConditionCommand> airCondition) {
        return Behaviors.setup(context ->
                Behaviors.withTimers(timers ->
                        new TemperatureSensor(context, environment, airCondition, timers)));
    }

    private final ActorRef<TemperatureEnvironment.TemperatureEnvironmentCommand> environment;
    private final ActorRef<AirCondition.AirConditionCommand> airCondition;

    private TemperatureSensor(ActorContext<TemperatureCommand> context,
                              ActorRef<TemperatureEnvironment.TemperatureEnvironmentCommand> environment,
                              ActorRef<AirCondition.AirConditionCommand> airCondition,
                              TimerScheduler<TemperatureCommand> timers) {
        super(context);
        this.environment = environment;
        this.airCondition = airCondition;
        timers.startTimerAtFixedRate(new DoRequestTemperature(), Duration.ofSeconds(5));
        getContext().getLog().info("TemperatureSensor started and polling environment");
    }

    @Override
    public Receive<TemperatureCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(DoRequestTemperature.class, this::onRequestTemperature)
                .onMessage(ReceiveTemperature.class, this::onReceiveTemperature)
                .build();
    }

    private Behavior<TemperatureCommand> onRequestTemperature(DoRequestTemperature msg) {
        environment.tell(new TemperatureEnvironment.ReceiveTemperatureRequest(getContext().getSelf()));
        return this;
    }

    private Behavior<TemperatureCommand> onReceiveTemperature(ReceiveTemperature msg) {
        getContext().getLog().info("[SENSOR] Measured temperature: {}", msg.temperature);
        airCondition.tell(new AirCondition.ReceiveTemperature(msg.temperature));
        return this;
    }
}
