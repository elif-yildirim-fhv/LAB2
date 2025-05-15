package at.fhv.sysarch.lab2.homeautomation.devices;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.shared.Movie;

public class MediaStation extends AbstractBehavior<MediaStation.MediaStationCommand> {

    public interface MediaStationCommand {}

    public static final class TurnMediaStationOnCommand implements MediaStationCommand {
        public final Movie movie;
        public TurnMediaStationOnCommand(Movie movie) {
            this.movie = movie;
        }
    }

    public static final class TurnMediaStationOffCommand implements MediaStationCommand {}

    private final ActorRef<Blinds.BlindsCommand> blinds;
    private Movie currentMovie;

    public static Behavior<MediaStationCommand> create(ActorRef<Blinds.BlindsCommand> blinds) {
        return Behaviors.setup(context -> new MediaStation(context, blinds));
    }

    private MediaStation(ActorContext<MediaStationCommand> context, ActorRef<Blinds.BlindsCommand> blinds) {
        super(context);
        this.blinds = blinds;
        context.getLog().info("[DEVICE] MediaStation started");
    }

    @Override
    public Receive<MediaStationCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(TurnMediaStationOnCommand.class, this::onTurnOn)
                .onMessage(TurnMediaStationOffCommand.class, this::onTurnOff)
                .build();
    }

    private Behavior<MediaStationCommand> onTurnOn(TurnMediaStationOnCommand cmd) {
        if (currentMovie == null) {
            currentMovie = cmd.movie;
            getContext().getLog().info("[DEVICE] Now playing: {}", currentMovie.title());
            blinds.tell(new Blinds.MediaStationStatusChangedCommand(true));
        } else {
            getContext().getLog().info("[DEVICE] Already playing: {}", currentMovie.title());
        }
        return this;
    }

    private Behavior<MediaStationCommand> onTurnOff(TurnMediaStationOffCommand cmd) {
        if (currentMovie != null) {
            getContext().getLog().info("[DEVICE] Stopping movie: {}", currentMovie.title());
            currentMovie = null;
            blinds.tell(new Blinds.MediaStationStatusChangedCommand(false));
        } else {
            getContext().getLog().info("[DEVICE] No movie is playing");
        }
        return this;
    }
}
