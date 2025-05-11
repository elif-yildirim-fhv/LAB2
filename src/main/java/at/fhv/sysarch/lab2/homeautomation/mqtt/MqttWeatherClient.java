package at.fhv.sysarch.lab2.homeautomation.mqtt;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.devices.TemperatureSensor;
import at.fhv.sysarch.lab2.homeautomation.devices.WeatherSensor;
import at.fhv.sysarch.lab2.homeautomation.shared.Weather;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttWeatherClient extends AbstractBehavior<MqttWeatherClient.MqttCommand> {

	// --- Nachrichten ---
	public interface MqttCommand {
	}

	public static final class StartListening implements MqttCommand {
	}

	public static final class StopListening implements MqttCommand {
	}

	private static final class MqttMessageReceived implements MqttCommand {
		final String topic;
		final String message;

		MqttMessageReceived(String topic, String message) {
			this.topic = topic;
			this.message = message;
		}
	}

	// MQTT-Konfiguration
	private static final String BROKER_URL = "tcp://10.0.40.161:1883";
	private static final String CLIENT_ID = "home-automation-client-" + System.currentTimeMillis();
	private static final String TOPIC_TEMPERATURE = "weather/temperature";
	private static final String TOPIC_WEATHER = "weather/condition";

	private final MqttClient mqttClient;
	private final ActorRef<TemperatureSensor.TemperatureCommand> tempSensor;
	private final ActorRef<WeatherSensor.WeatherSensorCommand> weatherSensor;

	private boolean isListening = false;

	// Factory-Methode
	public static Behavior<MqttCommand> create(
			ActorRef<TemperatureSensor.TemperatureCommand> tempSensor,
			ActorRef<WeatherSensor.WeatherSensorCommand> weatherSensor) {
		return Behaviors.setup(context -> new MqttWeatherClient(context, tempSensor, weatherSensor));
	}

	private MqttWeatherClient(ActorContext<MqttCommand> context,
							  ActorRef<TemperatureSensor.TemperatureCommand> tempSensor,
							  ActorRef<WeatherSensor.WeatherSensorCommand> weatherSensor) {

		super(context);
		this.tempSensor = tempSensor;
		this.weatherSensor = weatherSensor;

		MqttClient client = null;
		try {
			client = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence());
			MqttConnectOptions options = new MqttConnectOptions();
			options.setCleanSession(true);
			options.setConnectionTimeout(10);
			client.connect(options);

			// Callback für eingehende Nachrichten
			client.setCallback(new MqttCallback() {
				@Override
				public void connectionLost(Throwable cause) {
					getContext().getLog().error("MQTT connection lost", cause);
				}

				@Override
				public void messageArrived(String topic, MqttMessage message) {
					String payload = new String(message.getPayload());
					getContext().getSelf().tell(new MqttMessageReceived(topic, payload));
				}

				@Override
				public void deliveryComplete(IMqttDeliveryToken token) {
					// Ignorieren, da wir nur empfangen
				}
			});

			getContext().getLog().info("MQTT client connected to {}", BROKER_URL);

		} catch (MqttException e) {
			getContext().getLog().error("Failed to initialize MQTT client", e);
		}

		this.mqttClient = client;
	}

	@Override
	public Receive<MqttCommand> createReceive() {
		return newReceiveBuilder()
				.onMessage(StartListening.class, this::onStart)
				.onMessage(StopListening.class, this::onStop)
				.onMessage(MqttMessageReceived.class, this::onMessageReceived)
				.onSignal(PostStop.class, signal -> onPostStop())
				.build();
	}

	private Behavior<MqttCommand> onStart(StartListening msg) {
		if (mqttClient != null && !isListening) {
			try {
				mqttClient.subscribe(TOPIC_TEMPERATURE);
				mqttClient.subscribe(TOPIC_WEATHER);
				isListening = true;
				getContext().getLog().info("Started listening to MQTT topics");
			} catch (MqttException e) {
				getContext().getLog().error("Failed to subscribe to MQTT topics", e);
			}
		}
		return this;
	}

	private Behavior<MqttCommand> onStop(StopListening msg) {
		if (mqttClient != null && isListening) {
			try {
				mqttClient.unsubscribe(TOPIC_TEMPERATURE);
				mqttClient.unsubscribe(TOPIC_WEATHER);
				isListening = false;
				getContext().getLog().info("Stopped listening to MQTT topics");
			} catch (MqttException e) {
				getContext().getLog().error("Failed to unsubscribe from MQTT topics", e);
			}
		}
		return this;
	}

	private Behavior<MqttCommand> onMessageReceived(MqttMessageReceived msg) {
		try {
			if (msg.topic.equals(TOPIC_TEMPERATURE)) {
				double temperature = Double.parseDouble(msg.message);
				tempSensor.tell(new TemperatureSensor.ReadTemperature(temperature));
			} else if (msg.topic.equals(TOPIC_WEATHER)) {
				Weather weather = msg.message.equalsIgnoreCase("sunny") ? Weather.SUNNY : Weather.RAINY;
				weatherSensor.tell(new WeatherSensor.ReceiveWeatherResponse(weather));
			}
		} catch (NumberFormatException e) {
			getContext().getLog().error("Failed to parse temperature value: {}", msg.message);
		}
		return this;
	}

	private Behavior<MqttCommand> onPostStop() {
		if (mqttClient != null) {
			try {
				if (isListening) {
					mqttClient.unsubscribe(TOPIC_TEMPERATURE);
					mqttClient.unsubscribe(TOPIC_WEATHER);
				}
				mqttClient.disconnect();
				getContext().getLog().info("MQTT client disconnected");
			} catch (MqttException e) {
				getContext().getLog().error("Error while disconnecting MQTT client", e);
			}
		}
		return this;
	}
}