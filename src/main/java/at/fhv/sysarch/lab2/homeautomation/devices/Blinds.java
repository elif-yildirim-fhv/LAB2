package at.fhv.sysarch.lab2.homeautomation.devices;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class Blinds extends AbstractBehavior<Blinds.BlindsCommand> {
	public interface BlindsCommand {}

	public static final class ControlBlinds implements BlindsCommand {
		final boolean isSunny;
		final boolean isMoviePlaying;

		public ControlBlinds(boolean isSunny, boolean isMoviePlaying) {
			this.isSunny = isSunny;
			this.isMoviePlaying = isMoviePlaying;
		}
	}

	private final String groupId;
	private final String deviceId;
	private boolean isClosed = false;

	public Blinds(ActorContext<BlindsCommand> context, String groupId, String deviceId) {
		super(context);
		this.groupId = groupId;
		this.deviceId = deviceId;
		getContext().getLog().info("Blinds {}-{} started", groupId, deviceId);
	}

	public static Behavior<BlindsCommand> create(String groupId, String deviceId) {
		return Behaviors.setup(context -> new Blinds(context, groupId, deviceId));
	}

	@Override
	public Receive<BlindsCommand> createReceive() {
		return newReceiveBuilder()
				.onMessage(ControlBlinds.class, this::onControlCommand)
				.onSignal(PostStop.class, signal -> onPostStop())
				.build();
	}

	private Behavior<BlindsCommand> onControlCommand(ControlBlinds cmd) {
		boolean shouldClose = cmd.isSunny || cmd.isMoviePlaying;

		if (shouldClose && !isClosed) {
			getContext().getLog().info("Closing blinds (Sunny: {}, Movie: {})",
					cmd.isSunny, cmd.isMoviePlaying);
			isClosed = true;
		} else if (!shouldClose && isClosed) {
			getContext().getLog().info("Opening blinds (Sunny: {}, Movie: {})",
					cmd.isSunny, cmd.isMoviePlaying);
			isClosed = false;
		}

		return this;
	}

	private Behavior<BlindsCommand> onPostStop() {
		getContext().getLog().info("Blinds actor {}-{} stopped", groupId, deviceId);
		return this;
	}
}