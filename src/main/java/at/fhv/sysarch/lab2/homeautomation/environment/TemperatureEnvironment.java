package at.fhv.sysarch.lab2.homeautomation.environment;


import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.devices.TemperatureSensor;
import at.fhv.sysarch.lab2.homeautomation.shared.Temperature;


import java.time.Duration;
import java.util.Random;

public class TemperatureEnvironment extends AbstractBehavior<TemperatureEnvironment.TemperatureEnvironmentCommand> {

    // Nachrichten an den Actor
    public interface TemperatureEnvironmentCommand {}

    public static final class TemperatureAuto implements TemperatureEnvironmentCommand {}

    public static final class TemperatureManual implements TemperatureEnvironmentCommand {
        public final Temperature temperature;
        public TemperatureManual(Temperature temperature) {
            this.temperature = temperature;
        }
    }

    public static final class TemperatureUpdate implements TemperatureEnvironmentCommand {
        public final Temperature currentTemp;
        public TemperatureUpdate(Temperature currentTemp) {
            this.currentTemp = currentTemp;
        }
    }

    public final class ReceiveTemperatureRequest implements TemperatureEnvironmentCommand {
    public final ActorRef<TemperatureSensor.TemperatureCommand> sensor;
    public ReceiveTemperatureRequest(ActorRef<TemperatureSensor.TemperatureCommand> sensor) {
        this.sensor = sensor;
        }
    }

    // Zustände
    private Temperature currentTemperature;
    private boolean isAuto = true;
    private final Random random = new Random();
    private final TimerScheduler<TemperatureEnvironmentCommand> timer;

    // Factory-Methode
    public static Behavior<TemperatureEnvironmentCommand> create(Temperature initTemp) {
        return Behaviors.setup(context ->
                Behaviors.withTimers(timer ->
                        new TemperatureEnvironment(context, initTemp, timer)));
    }

    private TemperatureEnvironment(ActorContext<TemperatureEnvironmentCommand> context, Temperature initTemp, TimerScheduler<TemperatureEnvironmentCommand> timer) {
        super(context);
        this.currentTemperature = initTemp;
        this.timer = timer;

        getContext().getLog().info("[ENVIRONMENT] Starting temperature simulation...");
        timer.startTimerAtFixedRate(new TemperatureUpdate(initTemp), Duration.ofSeconds(10));
    }

    @Override
    public Receive<TemperatureEnvironmentCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(TemperatureUpdate.class, this::onUpdate)
                .onMessage(ReceiveTemperatureRequest.class, this::onRequest)
                .onMessage(TemperatureManual.class, this::onManual)
                .onMessage(TemperatureAuto.class, this::onAuto)
                .build();
    }

    private Behavior<TemperatureEnvironmentCommand> onUpdate(TemperatureUpdate msg) {
        if (isAuto) {
            double delta = (random.nextInt(60) - 30) / 10.0;  // -3.0 bis +3.0
            currentTemperature = new Temperature(currentTemperature.unit(), currentTemperature.value() + delta);
            getContext().getLog().info("[ENVIRONMENT] New temperature: " + currentTemperature);
        }
        return this;
    }

    private Behavior<TemperatureEnvironmentCommand> onRequest(ReceiveTemperatureRequest msg) {
        msg.sensor.tell(new TemperatureSensor.ReadTemperature(currentTemperature.value()));
        return this;
    }

    private Behavior<TemperatureEnvironmentCommand> onManual(TemperatureManual msg) {
        isAuto = false;
        currentTemperature = msg.temperature;
        getContext().getLog().info("[ENVIRONMENT] Set manual temperature: " + currentTemperature);
        return this;
    }

    private Behavior<TemperatureEnvironmentCommand> onAuto(TemperatureAuto msg) {
        isAuto = true;
        getContext().getLog().info("[ENVIRONMENT] Switched to automatic temperature mode");
        return this;
    }
}
