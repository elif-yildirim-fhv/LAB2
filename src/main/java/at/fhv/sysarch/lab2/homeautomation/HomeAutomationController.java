package at.fhv.sysarch.lab2.homeautomation;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.environment.TemperatureEnvironment;
import at.fhv.sysarch.lab2.homeautomation.environment.WeatherEnvironment;
import at.fhv.sysarch.lab2.homeautomation.fridge.Fridge;
import at.fhv.sysarch.lab2.homeautomation.shared.Temperature;
import at.fhv.sysarch.lab2.homeautomation.shared.Weather;
import at.fhv.sysarch.lab2.homeautomation.ui.UI;

public class HomeAutomationController extends AbstractBehavior<Void> {

    public static Behavior<Void> create() {
        return Behaviors.setup(HomeAutomationController::new);
    }

    private HomeAutomationController(ActorContext<Void> context) {
        super(context);

        // Environments
        ActorRef<WeatherEnvironment.WeatherEnvironmentCommand> weatherEnv =
                context.spawn(WeatherEnvironment.create(Weather.SUNNY), "WeatherEnvironment");
        ActorRef<TemperatureEnvironment.TemperatureEnvironmentCommand> tempEnv =
                context.spawn(TemperatureEnvironment.create(new Temperature("Celsius", 21.0)), "TemperatureEnvironment");

        // Devices
        ActorRef<Blinds.BlindsCommand> blinds = context.spawn(Blinds.create(), "Blinds");
        ActorRef<MediaStation.MediaStationCommand> mediaStation = context.spawn(MediaStation.create(blinds), "MediaStation");
        ActorRef<AirCondition.AirConditionCommand> airCondition = context.spawn(AirCondition.create(), "AirCondition");
        ActorRef<TemperatureSensor.TemperatureCommand> tempSensor = context.spawn(TemperatureSensor.create(tempEnv, airCondition), "TemperatureSensor");
        ActorRef<WeatherSensor.WeatherSensorCommand> weatherSensor = context.spawn(WeatherSensor.create(weatherEnv, blinds), "WeatherSensor");


        // Fridge
        ActorRef<Fridge.FridgeCommand> fridge = context.spawn(Fridge.create(), "Fridge");

        // UI ohne Fridge,
        // ToDo Implementierung
        ActorRef<Void> ui = context.spawn(UI.create(tempSensor, airCondition, mediaStation, blinds), "UI");
        context.getLog().info("HomeAutomation Application started");
    }

    @Override
    public Receive<Void> createReceive() {
        return newReceiveBuilder()
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private HomeAutomationController onPostStop() {
        getContext().getLog().info("HomeAutomation Application stopped");
        return this;
    }
}
