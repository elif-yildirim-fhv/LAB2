package at.fhv.sysarch.lab2.homeautomation.devices;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.environment.WeatherEnvironment;
import at.fhv.sysarch.lab2.homeautomation.shared.Weather;

import java.time.Duration;

public class WeatherSensor extends AbstractBehavior<WeatherSensor.WeatherSensorCommand> {

    // --- Nachrichteninterface ---
    public interface WeatherSensorCommand {}

    // Intern vom Actor selbst genutzt (alle 15 Sek.)
    public static final class DoRequestWeather implements WeatherSensorCommand {}

    // Externe Nachricht – für WeatherEnvironment **und** MQTT
    public static final class ReceiveWeather implements WeatherSensorCommand {
        public final Weather weather;

        public ReceiveWeather(Weather weather) {
            this.weather = weather;
        }
    }

    // --- Felder ---
    private final ActorRef<WeatherEnvironment.WeatherEnvironmentCommand> environment;
    private final ActorRef<Blinds.BlindsCommand> blinds;
    private Weather lastWeather = null;

    // --- Factory ---
    public static Behavior<WeatherSensorCommand> create(
            ActorRef<WeatherEnvironment.WeatherEnvironmentCommand> environment,
            ActorRef<Blinds.BlindsCommand> blinds) {
        return Behaviors.setup(context ->
                Behaviors.withTimers(timers ->
                        new WeatherSensor(context, environment, blinds, timers)));
    }

    private WeatherSensor(
            ActorContext<WeatherSensorCommand> context,
            ActorRef<WeatherEnvironment.WeatherEnvironmentCommand> environment,
            ActorRef<Blinds.BlindsCommand> blinds,
            TimerScheduler<WeatherSensorCommand> timers) {
        super(context);
        this.environment = environment;
        this.blinds = blinds;

        timers.startTimerAtFixedRate(new DoRequestWeather(), Duration.ofSeconds(30));
        getContext().getLog().info("WeatherSensor started and polling environment");
    }

    @Override
    public Receive<WeatherSensorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(DoRequestWeather.class, this::onRequestWeather)
                .onMessage(ReceiveWeather.class, this::onReceiveWeather)
                .build();
    }

    private Behavior<WeatherSensorCommand> onRequestWeather(DoRequestWeather msg) {
        environment.tell(new WeatherEnvironment.WeatherRequest(getContext().getSelf()));
        return this;
    }

    private Behavior<WeatherSensorCommand> onReceiveWeather(ReceiveWeather msg) {
        if (msg.weather != lastWeather) {
            boolean isSunny = msg.weather == Weather.SUNNY;
            blinds.tell(new Blinds.WeatherChangedCommand(isSunny));
            lastWeather = msg.weather;
        }

        getContext().getLog().info("[SENSOR] Measured weather: {}", msg.weather);
        return this;
    }
}
