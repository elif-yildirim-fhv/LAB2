package at.fhv.sysarch.lab2.homeautomation.ui;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.shared.Movie;

import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Collectors;

public class UI extends AbstractBehavior<Void> {

    private final ActorRef<TemperatureSensor.TemperatureCommand> tempSensor;
    private final ActorRef<AirCondition.AirConditionCommand> airCondition;
    private final ActorRef<MediaStation.MediaStationCommand> mediaStation;
    private final ActorRef<Blinds.BlindsCommand> blinds;

    public static Behavior<Void> create(
            ActorRef<TemperatureSensor.TemperatureCommand> tempSensor,
            ActorRef<AirCondition.AirConditionCommand> airCondition,
            ActorRef<MediaStation.MediaStationCommand> mediaStation,
            ActorRef<Blinds.BlindsCommand> blinds
    ) {
        return Behaviors.setup(context -> new UI(context, tempSensor, airCondition, mediaStation, blinds));
    }

    private UI(
            ActorContext<Void> context,
            ActorRef<TemperatureSensor.TemperatureCommand> tempSensor,
            ActorRef<AirCondition.AirConditionCommand> airCondition,
            ActorRef<MediaStation.MediaStationCommand> mediaStation,
            ActorRef<Blinds.BlindsCommand> blinds
    ) {
        super(context);
        this.tempSensor = tempSensor;
        this.airCondition = airCondition;
        this.mediaStation = mediaStation;
        this.blinds = blinds;
        new Thread(this::runCommandLine).start();
        getContext().getLog().info("[UI] Started");
    }

    @Override
    public Receive<Void> createReceive() {
        return newReceiveBuilder()
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private UI onPostStop() {
        getContext().getLog().info("[UI] Stopped");
        return this;
    }

    private void runCommandLine() {
        Scanner scanner = new Scanner(System.in);
        String input;

        System.out.println("==== Home Automation Console ====");
        System.out.println("Commands:");
        System.out.println("  temp <value>              -> simulate temperature reading");
        System.out.println("  ac on/off                 -> turn air condition ON/OFF");
        System.out.println("  media on <movie name>     -> start movie on MediaStation");
        System.out.println("  media off                 -> stop MediaStation");
        System.out.println("  blinds movie <on/off>     -> simulate MediaState for blinds");
        System.out.println("  blinds weather <sun/rain> -> simulate weather for blinds");
        System.out.println("  quit                      -> exit UI");
        System.out.println("=================================");

        while (scanner.hasNextLine()) {
            input = scanner.nextLine().trim();
            String[] parts = input.split(" ");

            if (parts.length == 0) continue;
            String command = parts[0];

            switch (command) {
                case "temp" -> handleTemp(parts);
                case "ac" -> handleAC(parts);
                case "media" -> handleMedia(parts);
                case "blinds" -> handleBlinds(parts);
                case "quit" -> {
                    System.out.println("[CMD] Shutting down UI...");
                    return;
                }
                default -> System.out.println("[CMD] Unknown command");
            }
        }
    }

    private void handleTemp(String[] parts) {
        if (parts.length < 2) {
            System.out.println("[CMD] Usage: temp <value>");
            return;
        }
        try {
            double value = Double.parseDouble(parts[1]);
            tempSensor.tell(new TemperatureSensor.ReadTemperature(value));
            System.out.println("[CMD] Simulated temperature: " + value);
        } catch (NumberFormatException e) {
            System.out.println("[ERROR] Invalid temperature value");
        }
    }

    private void handleAC(String[] parts) {
        if (parts.length < 2) {
            System.out.println("[CMD] Usage: ac on/off");
            return;
        }
        boolean powerOn = parts[1].equalsIgnoreCase("on");
        airCondition.tell(new AirCondition.PowerAirCondition(powerOn));
        System.out.println("[CMD] AirCondition manually turned " + (powerOn ? "ON" : "OFF"));
    }

    private void handleMedia(String[] parts) {
        if (parts.length < 2) {
            System.out.println("[CMD] Usage: media on <movie name> | media off");
            return;
        }

        if (parts[1].equalsIgnoreCase("on") && parts.length > 2) {
            String movieName = Arrays.stream(parts).skip(2).collect(Collectors.joining(" "));
            mediaStation.tell(new MediaStation.TurnMediaStationOnCommand(Movie.withTitle(movieName)));
            System.out.println("[CMD] Playing movie: " + movieName);
        } else if (parts[1].equalsIgnoreCase("off")) {
            mediaStation.tell(new MediaStation.TurnMediaStationOffCommand());
            System.out.println("[CMD] Stopping MediaStation");
        } else {
            System.out.println("[CMD] Invalid media command");
        }
    }

    private void handleBlinds(String[] parts) {
        if (parts.length < 3) {
            System.out.println("[CMD] Usage: blinds movie <on/off> | blinds weather <sun/rain>");
            return;
        }

        if (parts[1].equalsIgnoreCase("movie")) {
            boolean isPlaying = parts[2].equalsIgnoreCase("on");
            blinds.tell(new Blinds.MediaStationStatusChangedCommand(isPlaying));
            System.out.println("[CMD] Simulated media " + (isPlaying ? "ON" : "OFF"));
        } else if (parts[1].equalsIgnoreCase("weather")) {
            boolean isSunny = parts[2].equalsIgnoreCase("sun");
            blinds.tell(new Blinds.WeatherChangedCommand(isSunny));
            System.out.println("[CMD] Simulated weather: " + (isSunny ? "SUNNY" : "RAINY"));
        } else {
            System.out.println("[CMD] Unknown blinds command");
        }
    }
}
