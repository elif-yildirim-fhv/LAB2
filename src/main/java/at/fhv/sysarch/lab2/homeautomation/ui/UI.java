package at.fhv.sysarch.lab2.homeautomation.ui;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.shared.EnvironmentMode;
import at.fhv.sysarch.lab2.homeautomation.shared.Movie;
import at.fhv.sysarch.lab2.homeautomation.shared.Temperature;
import at.fhv.sysarch.lab2.homeautomation.Fridge.Fridge;
import at.fhv.sysarch.lab2.homeautomation.Fridge.Product;

import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Collectors;

public class UI extends AbstractBehavior<Void> {

    private final ActorRef<TemperatureSensor.TemperatureCommand> tempSensor;
    private final ActorRef<AirCondition.AirConditionCommand> airCondition;
    private final ActorRef<MediaStation.MediaStationCommand> mediaStation;
    private final ActorRef<Blinds.BlindsCommand> blinds;
    private final ActorRef<Fridge.FridgeCommand> fridge;

    // Fix: Adapters als Member
    private final ActorRef<Fridge.ProductsResponse> productPrinter;
    private final ActorRef<Fridge.OrderHistoryResponse> orderHistoryPrinter;
    private final ActorRef<Fridge.OperationResult> operationResultPrinter;

    public static Behavior<Void> create(
            ActorRef<TemperatureSensor.TemperatureCommand> tempSensor,
            ActorRef<AirCondition.AirConditionCommand> airCondition,
            ActorRef<MediaStation.MediaStationCommand> mediaStation,
            ActorRef<Blinds.BlindsCommand> blinds,
            ActorRef<Fridge.FridgeCommand> fridge
    ) {
        return Behaviors.setup(context -> new UI(context, tempSensor, airCondition, mediaStation, blinds, fridge));
    }

    private UI(
            ActorContext<Void> context,
            ActorRef<TemperatureSensor.TemperatureCommand> tempSensor,
            ActorRef<AirCondition.AirConditionCommand> airCondition,
            ActorRef<MediaStation.MediaStationCommand> mediaStation,
            ActorRef<Blinds.BlindsCommand> blinds,
            ActorRef<Fridge.FridgeCommand> fridge
    ) {
        super(context);
        this.tempSensor = tempSensor;
        this.airCondition = airCondition;
        this.mediaStation = mediaStation;
        this.blinds = blinds;
        this.fridge = fridge;

        // FIX: messageAdapters im Actor-Thread erstellen
        this.productPrinter = getContext().messageAdapter(Fridge.ProductsResponse.class, response -> {
            System.out.println("== Products in Fridge ==");
            response.products.forEach((product, quantity) ->
                    System.out.printf("%s: %d pcs (%.2f€ / %.2fg)%n", product.name(), quantity, product.price(), product.weight()));
            return null;
        });

        this.orderHistoryPrinter = getContext().messageAdapter(Fridge.OrderHistoryResponse.class, response -> {
            System.out.println("== Order History ==");
            response.orderHistory.forEach(order -> {
                System.out.printf("Ordered %d x %s on %s, total: €%.2f%n",
                        order.amount(), order.product().name(), order.receipt().timestamp(), order.receipt().totalPrice());
            });
            return null;
        });

        this.operationResultPrinter = getContext().messageAdapter(Fridge.OperationResult.class, result -> {
            System.out.println("[FRIDGE] " + result.message);
            return null;
        });

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

        System.out.println("<<<< Home Automation Console >>>>");
        System.out.println("Commands:");
        System.out.println("  temp <value> -> simulate temperature reading");
        System.out.println("  ac on/off -> turn air condition ON/OFF");
        System.out.println("  media on <movie name> -> start movie on MediaStation");
        System.out.println("  media off -> stop MediaStation");
        System.out.println("  blinds movie <on/off> -> simulate MediaState for blinds");
        System.out.println("  blinds weather <sun/rain> -> simulate weather for blinds");
        System.out.println("  env temp <internal/external> -> switch temperature mode");
        System.out.println("  fridge add <name> <price> <weight> <amount> -> add product");
        System.out.println("  fridge consume <name> <amount>  -> consume product");
        System.out.println("  fridge products -> list all products");
        System.out.println("  fridge history   -> show order history");
        System.out.println("  quit  -> exit UI");

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
                case "env" -> handleEnv(parts);
                case "fridge" -> handleFridge(parts);
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
            tempSensor.tell(new TemperatureSensor.ReceiveTemperature(new Temperature("Celsius", value)));
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

    private void handleEnv(String[] parts) {
        if (parts.length < 3) {
            System.out.println("[CMD] Usage: env temp <internal/external>");
            return;
        }

        if (parts[1].equalsIgnoreCase("temp")) {
            EnvironmentMode mode = parts[2].equalsIgnoreCase("external") ? EnvironmentMode.EXTERNAL : EnvironmentMode.INTERNAL;
            tempSensor.tell(new TemperatureSensor.SetMode(mode));
            System.out.println("[CMD] Temperature mode set to " + mode);
        } else {
            System.out.println("[CMD] Unknown env command");
        }
    }

    private void handleFridge(String[] parts) {
        if (parts.length < 2) {
            System.out.println("[CMD] Usage: fridge <add/consume/products/history>");
            return;
        }
        switch (parts[1]) {
            case "add" -> {
                if (parts.length < 6) {
                    System.out.println("[CMD] Usage: fridge add <name> <price> <weight> <amount>");
                    return;
                }
                String name = parts[2];
                double price = Double.parseDouble(parts[3]);
                double weight = Double.parseDouble(parts[4]);
                int amount = Integer.parseInt(parts[5]);
                Product product = new Product(name, price, weight);
                fridge.tell(new Fridge.AddProductCommand(product, amount, operationResultPrinter));
            }
            case "consume" -> {
                if (parts.length < 4) {
                    System.out.println("[CMD] Usage: fridge consume <name> <amount>");
                    return;
                }
                String name = parts[2];
                int amount = Integer.parseInt(parts[3]);
                fridge.tell(new Fridge.ConsumeProductCommand(name, amount, operationResultPrinter));
            }
            case "products" -> fridge.tell(new Fridge.GetProductsCommand(productPrinter));
            case "history" -> fridge.tell(new Fridge.GetOrderHistoryCommand(orderHistoryPrinter));
            default -> System.out.println("[CMD] Unknown fridge command");
        }
    }
}
