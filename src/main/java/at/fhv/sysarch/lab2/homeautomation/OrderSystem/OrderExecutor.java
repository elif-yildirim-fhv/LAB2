package at.fhv.sysarch.lab2.homeautomation.order;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.grpc.GrpcClientSettings;
import at.fhv.sysarch.lab2.homeautomation.grpc.OrderReply;
import at.fhv.sysarch.lab2.homeautomation.grpc.OrderRequest;
import at.fhv.sysarch.lab2.homeautomation.grpc.OrderServiceClient;
import org.slf4j.Logger;

import java.util.concurrent.CompletionStage;

public class OrderExecutor extends AbstractBehavior<String> {

    public static Behavior<String> create() {
        return Behaviors.setup(context -> {
            GrpcClientSettings settings = GrpcClientSettings
                    .connectToServiceAt("localhost", 8080, context.getSystem())
                    .withTls(false);
            OrderServiceClient client = OrderServiceClient.create(settings, context.getSystem());
            return new OrderExecutor(context, client);
        });
    }

    private final OrderServiceClient client;

    private OrderExecutor(ActorContext<String> context, OrderServiceClient client) {
        super(context);
        this.client = client;
    }

    @Override
    public Receive<String> createReceive() {
        return newReceiveBuilder()
                .onMessage(String.class, this::orderReceived)
                .build();
    }

    private Behavior<String> orderReceived(String s) {
        // TODO: call gRPC client to process order
        getContext().getLog().info("Order received {}", s);
        CompletionStage<OrderReply> request = this.client.order(OrderRequest.newBuilder().setProduct(s).build());

        Logger logger = getContext().getLog();
        request.thenAccept(reply ->
                logger.info("Order processed {}", reply.getSuccessful()));

        return Behaviors.same();
    }
}
