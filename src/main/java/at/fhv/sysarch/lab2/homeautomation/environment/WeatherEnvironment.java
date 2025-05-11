package at.fhv.sysarch.lab2.homeautomation.environment;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.devices.WeatherSensor;
import at.fhv.sysarch.lab2.homeautomation.shared.Weather;

import java.time.Duration;

public class WeatherEnvironment extends AbstractBehavior<WeatherEnvironment.WeatherEnvironmentCommand> {

    public interface WeatherEnvironmentCommand {}

    public static final class WeatherUpdate implements WeatherEnvironmentCommand {}

    public static final class WeatherRequest implements WeatherEnvironmentCommand {
        public final ActorRef<WeatherSensor.WeatherSensorCommand> sender;

        public WeatherRequest(ActorRef<WeatherSensor.WeatherSensorCommand> sender) {
            this.sender = sender;
        }
    }

    private Weather currentWeather;

    public static Behavior<WeatherEnvironmentCommand> create(Weather initWeather) {
        return Behaviors.setup(context ->
                Behaviors.withTimers(timer ->
                        new WeatherEnvironment(context, initWeather, timer)));
    }

    private WeatherEnvironment(ActorContext<WeatherEnvironmentCommand> context,
                               Weather initWeather,
                               TimerScheduler<WeatherEnvironmentCommand> scheduler) {
        super(context);
        this.currentWeather = initWeather;
        context.getLog().info("[ENVIRONMENT] Starting weather simulation...");
        scheduler.startTimerAtFixedRate(new WeatherUpdate(), Duration.ofSeconds(30));
    }

    @Override
    public Receive<WeatherEnvironmentCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(WeatherUpdate.class, this::onUpdate)
                .onMessage(WeatherRequest.class, this::onRequest)
                .build();
    }

    private Behavior<WeatherEnvironmentCommand> onUpdate(WeatherUpdate msg) {
        currentWeather = Weather.random();
        getContext().getLog().info("[ENVIRONMENT] New weather: " + currentWeather);
        return this;
    }

    private Behavior<WeatherEnvironmentCommand> onRequest(WeatherRequest msg) {
        msg.sender.tell(new WeatherSensor.ReceiveWeather(currentWeather));
        return this;
    }
}
