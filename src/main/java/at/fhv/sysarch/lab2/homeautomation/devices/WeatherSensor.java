package at.fhv.sysarch.lab2.homeautomation.devices;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class WeatherSensor extends AbstractBehavior<WeatherSensor.WeatherCommand> {
	public interface WeatherCommand {}

	public static final class ReadWeather implements WeatherCommand {
		final ActorRef<WeatherResponse> replyTo;

		public ReadWeather(ActorRef<WeatherResponse> replyTo) {
			this.replyTo = replyTo;
		}
	}

	public static final class WeatherResponse {
		final String condition;
		final boolean isSunny;

		public WeatherResponse(String condition, boolean isSunny) {
			this.condition = condition;
			this.isSunny = isSunny;
		}
	}

	private final String groupId;
	private final String deviceId;
	private String currentCondition = "sunny";
	private boolean isSunny = true;

	public WeatherSensor(ActorContext<WeatherCommand> context, String groupId, String deviceId) {
		super(context);
		this.groupId = groupId;
		this.deviceId = deviceId;
		getContext().getLog().info("WeatherSensor {}-{} started", groupId, deviceId);
	}

	public static Behavior<WeatherCommand> create(String groupId, String deviceId) {
		return Behaviors.setup(context -> new WeatherSensor(context, groupId, deviceId));
	}

	public void updateWeather(String condition, boolean isSunny) {
		this.currentCondition = condition;
		this.isSunny = isSunny;
	}

	@Override
	public Receive<WeatherCommand> createReceive() {
		return newReceiveBuilder()
				.onMessage(ReadWeather.class, this::onReadWeather)
				.onSignal(PostStop.class, signal -> onPostStop())
				.build();
	}

	private Behavior<WeatherCommand> onReadWeather(ReadWeather cmd) {
		cmd.replyTo.tell(new WeatherResponse(currentCondition, isSunny));
		getContext().getLog().debug("Weather reading: {}, sunny: {}", currentCondition, isSunny);
		return this;
	}

	private Behavior<WeatherCommand> onPostStop() {
		getContext().getLog().info("WeatherSensor actor {}-{} stopped", groupId, deviceId);
		return this;
	}
}