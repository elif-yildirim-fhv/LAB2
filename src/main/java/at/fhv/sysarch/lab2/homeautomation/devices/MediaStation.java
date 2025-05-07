package at.fhv.sysarch.lab2.homeautomation.devices;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class MediaStation extends AbstractBehavior<MediaStation.MediaCommand> {
	public interface MediaCommand {}

	public static final class PlayMovie implements MediaCommand {
		final String title;
		final ActorRef<OperationResult> replyTo;

		public PlayMovie(String title, ActorRef<OperationResult> replyTo) {
			this.title = title;
			this.replyTo = replyTo;
		}
	}

	public static final class StopMovie implements MediaCommand {
		final ActorRef<OperationResult> replyTo;

		public StopMovie(ActorRef<OperationResult> replyTo) {
			this.replyTo = replyTo;
		}
	}

	public static final class OperationResult {
		final boolean success;
		final String message;

		public OperationResult(boolean success, String message) {
			this.success = success;
			this.message = message;
		}
	}

	private final String groupId;
	private final String deviceId;
	private String currentMovie = null;

	public MediaStation(ActorContext<MediaCommand> context, String groupId, String deviceId) {
		super(context);
		this.groupId = groupId;
		this.deviceId = deviceId;
		getContext().getLog().info("MediaStation {}-{} started", groupId, deviceId);
	}

	public static Behavior<MediaCommand> create(String groupId, String deviceId) {
		return Behaviors.setup(context -> new MediaStation(context, groupId, deviceId));
	}

	@Override
	public Receive<MediaCommand> createReceive() {
		return newReceiveBuilder()
				.onMessage(PlayMovie.class, this::onPlayMovie)
				.onMessage(StopMovie.class, this::onStopMovie)
				.onSignal(PostStop.class, signal -> onPostStop())
				.build();
	}

	private Behavior<MediaCommand> onPlayMovie(PlayMovie cmd) {
		if (currentMovie != null) {
			cmd.replyTo.tell(new OperationResult(false,
					"Cannot play " + cmd.title + ". " + currentMovie + " is already playing"));
		} else {
			currentMovie = cmd.title;
			getContext().getLog().info("Now playing: {}", cmd.title);
			cmd.replyTo.tell(new OperationResult(true, "Now playing: " + cmd.title));
		}
		return this;
	}

	private Behavior<MediaCommand> onStopMovie(StopMovie cmd) {
		if (currentMovie == null) {
			cmd.replyTo.tell(new OperationResult(false, "No movie is currently playing"));
		} else {
			getContext().getLog().info("Stopping movie: {}", currentMovie);
			cmd.replyTo.tell(new OperationResult(true, "Stopped: " + currentMovie));
			currentMovie = null;
		}
		return this;
	}

	private Behavior<MediaCommand> onPostStop() {
		getContext().getLog().info("MediaStation actor {}-{} stopped", groupId, deviceId);
		return this;
	}
}