package at.fhv.sysarch.lab2.homeautomation.devices;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.environment.WeatherEnvironment;
import at.fhv.sysarch.lab2.homeautomation.shared.Weather;

import java.time.Duration;

public class WeatherSensor extends AbstractBehavior<WeatherSensor.WeatherSensorCommand> {

    // Nachrichten (Commands)
    public interface WeatherSensorCommand {}

    // Anfrage an das Environment (von sich selbst)
    public static final class RequestWeather implements WeatherSensorCommand {}

    // Antwort vom Environment
    public static final class ReceiveWeatherResponse implements WeatherSensorCommand {
        public final Weather weather;

        public ReceiveWeatherResponse(Weather weather) {
            this.weather = weather;
        }
    }

    // Actor-Referenzen
    private final ActorRef<WeatherEnvironment.WeatherEnvironmentCommand> environment;

    // Zustand
    private Weather currentWeather = Weather.SUNNY;

    // Factory-Methode
    public static Behavior<WeatherSensorCommand> create(ActorRef<WeatherEnvironment.WeatherEnvironmentCommand> environment) {
        return Behaviors.setup(context ->
                Behaviors.withTimers(timers ->
                        new WeatherSensor(context, environment, timers)));
    }

    // Konstruktor
    private WeatherSensor(ActorContext<WeatherSensorCommand> context,
                          ActorRef<WeatherEnvironment.WeatherEnvironmentCommand> environment,
                          TimerScheduler<WeatherSensorCommand> timers) {
        super(context);
        this.environment = environment;

        // alle 15 Sekunden Wetter anfragen
        timers.startTimerAtFixedRate(new RequestWeather(), Duration.ofSeconds(15));
        getContext().getLog().info("WeatherSensor started and polling environment");
    }

    @Override
    public Receive<WeatherSensorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(RequestWeather.class, this::onRequestWeather)
                .onMessage(ReceiveWeatherResponse.class, this::onReceiveWeather)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    // Wetter vom Environment anfragen
    private Behavior<WeatherSensorCommand> onRequestWeather(RequestWeather msg) {
        environment.tell(new WeatherEnvironment.WeatherRequest(getContext().getSelf()));
        return this;
    }

    // Antwort verarbeiten
    private Behavior<WeatherSensorCommand> onReceiveWeather(ReceiveWeatherResponse response) {
        this.currentWeather = response.weather;
        getContext().getLog().info("[SENSOR] Current weather: {}", currentWeather);
        return this;
    }

    // Stop-Log
    private Behavior<WeatherSensorCommand> onPostStop() {
        getContext().getLog().info("WeatherSensor stopped");
        return this;
    }
}
