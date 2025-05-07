package at.fhv.sysarch.lab2.homeautomation.devices;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.shared.BlindsPosition;

public class Blinds extends AbstractBehavior<Blinds.BlindsCommand> {

    // --- Nachrichten ---
    public interface BlindsCommand {}

    public static final class MediaStationStatusChangedCommand implements BlindsCommand {
        public final boolean isMoviePlaying;
        public MediaStationStatusChangedCommand(boolean isMoviePlaying) {
            this.isMoviePlaying = isMoviePlaying;
        }
    }

    public static final class WeatherChangedCommand implements BlindsCommand {
        public final boolean isSunny;
        public WeatherChangedCommand(boolean isSunny) {
            this.isSunny = isSunny;
        }
    }

    // --- Zustand ---
    private boolean isMoviePlaying = false;
    private boolean isSunny = true;
    private BlindsPosition blindsPosition = BlindsPosition.UP;

    // --- Factory ---
    public static Behavior<BlindsCommand> create() {
        return Behaviors.setup(Blinds::new);
    }

    private Blinds(ActorContext<BlindsCommand> context) {
        super(context);
        context.getLog().info("[DEVICE] Blinds started");
    }

    @Override
    public Receive<BlindsCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(MediaStationStatusChangedCommand.class, this::onMediaChanged)
                .onMessage(WeatherChangedCommand.class, this::onWeatherChanged)
                .build();
    }

    private Behavior<BlindsCommand> onMediaChanged(MediaStationStatusChangedCommand msg) {
        isMoviePlaying = msg.isMoviePlaying;
        updateBlinds();
        return this;
    }

    private Behavior<BlindsCommand> onWeatherChanged(WeatherChangedCommand msg) {
        isSunny = msg.isSunny;
        updateBlinds();
        return this;
    }

    private void updateBlinds() {
        BlindsPosition newPosition = (isSunny || isMoviePlaying) ? BlindsPosition.DOWN : BlindsPosition.UP;
        if (newPosition != blindsPosition) {
            blindsPosition = newPosition;
            getContext().getLog().info("[DEVICE] Blinds moved to {}", blindsPosition);
        }
    }
}
