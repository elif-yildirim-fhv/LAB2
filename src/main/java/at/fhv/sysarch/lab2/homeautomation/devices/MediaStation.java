package at.fhv.sysarch.lab2.homeautomation.devices;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.shared.Movie;

public class MediaStation extends AbstractBehavior<MediaStation.MediaStationCommand> {

    // --- Commands ---
    public interface MediaStationCommand {}

    public static final class TurnMediaStationOnCommand implements MediaStationCommand {
        public final Movie movie;
        public final ActorRef<OperationResult> replyTo;

        public TurnMediaStationOnCommand(Movie movie, ActorRef<OperationResult> replyTo) {
            this.movie = movie;
            this.replyTo = replyTo;
        }
    }

    public static final class TurnMediaStationOffCommand implements MediaStationCommand {
        public final ActorRef<OperationResult> replyTo;

        public TurnMediaStationOffCommand(ActorRef<OperationResult> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class OperationResult {
        public final boolean success;
        public final String message;

        public OperationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    // --- State ---
    private final ActorRef<Blinds.BlindsCommand> blinds;
    private Movie currentMovie;

    // --- Factory ---
    public static Behavior<MediaStationCommand> create(ActorRef<Blinds.BlindsCommand> blinds) {
        return Behaviors.setup(context -> new MediaStation(context, blinds));
    }

    // --- Constructor ---
    private MediaStation(ActorContext<MediaStationCommand> context, ActorRef<Blinds.BlindsCommand> blinds) {
        super(context);
        this.blinds = blinds;
        getContext().getLog().info("[DEVICE] MediaStation started");
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
            cmd.replyTo.tell(new OperationResult(true, "Now playing: " + currentMovie.title()));
        } else {
            getContext().getLog().info("[DEVICE] Already playing: {}", currentMovie.title());
            cmd.replyTo.tell(new OperationResult(false, "Already playing: " + currentMovie.title()));
        }
        return this;
    }

    private Behavior<MediaStationCommand> onTurnOff(TurnMediaStationOffCommand cmd) {
        if (currentMovie != null) {
            getContext().getLog().info("[DEVICE] Stopping movie: {}", currentMovie.title());
            currentMovie = null;
            blinds.tell(new Blinds.MediaStationStatusChangedCommand(false));
            cmd.replyTo.tell(new OperationResult(true, "Movie stopped"));
        } else {
            cmd.replyTo.tell(new OperationResult(false, "No movie is playing"));
        }
        return this;
    }
}
