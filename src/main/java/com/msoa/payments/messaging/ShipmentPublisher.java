package com.msoa.payments.messaging;

import com.msoa.payments.model.Payment;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Publishes a "ShipmentRequested" message to RabbitMQ once a payment succeeded.
 *
 * Contract (shared with the Shipping service):
 *  - broker      : localhost:5672 (guest / guest)
 *  - exchange    : "" (default exchange)
 *  - routing key : shipping_requests (= queue name)
 *  - queue       : durable=true, exclusive=false, autoDelete=false
 *  - message     : persistent (deliveryMode = 2), application/json
 */
@ApplicationScoped
public class ShipmentPublisher {

    private static final Logger LOG = Logger.getLogger(ShipmentPublisher.class.getName());

    public static final String QUEUE = "shipping_requests";
    public static final String MESSAGE_TYPE = "ShipmentRequested";

    @Inject
    @ConfigProperty(name = "rabbitmq.host", defaultValue = "localhost")
    private String host;

    @Inject
    @ConfigProperty(name = "rabbitmq.port", defaultValue = "5672")
    private int port;

    @Inject
    @ConfigProperty(name = "rabbitmq.username", defaultValue = "guest")
    private String username;

    @Inject
    @ConfigProperty(name = "rabbitmq.password", defaultValue = "guest")
    private String password;

    private Connection connection;

    /** Lazy connection: Payments still starts if RabbitMQ is down. */
    private synchronized Connection getConnection() throws Exception {
        if (connection == null || !connection.isOpen()) {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host);
            factory.setPort(port);
            factory.setUsername(username);
            factory.setPassword(password);
            factory.setConnectionTimeout(3000);
            factory.setAutomaticRecoveryEnabled(true);
            connection = factory.newConnection("payments-service");
        }
        return connection;
    }

    /**
     * @return true if the broker confirmed the message, false otherwise.
     *         Never throws: a broker problem must not break a payment that already succeeded.
     */
    public boolean publishShipmentRequested(Payment payment) {
        if (payment == null || !"SUCCESS".equals(payment.getStatus())) {
            return false;
        }

        String messageId = "msg-" + UUID.randomUUID().toString().substring(0, 8);
        String createdAt = payment.getCreatedAt() != null ? payment.getCreatedAt() : Instant.now().toString();

        JsonObject json = Json.createObjectBuilder()
                .add("messageId", messageId)
                .add("type", MESSAGE_TYPE)
                .add("orderId", payment.getOrderId())
                .add("paymentId", payment.getId())
                .add("amount", payment.getAmount())
                .add("method", payment.getMethod())
                .add("status", payment.getStatus())
                .add("createdAt", createdAt)
                .build();

        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);

        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .contentEncoding("UTF-8")
                .deliveryMode(2)                 // persistent
                .messageId(messageId)
                .type(MESSAGE_TYPE)
                .build();

        // A Channel is not thread-safe: use a short-lived one per publish.
        try (Channel channel = getConnection().createChannel()) {
            channel.queueDeclare(QUEUE, true, false, false, null);   // durable, non-exclusive, no auto-delete
            channel.confirmSelect();                                 // publisher confirms
            channel.basicPublish("", QUEUE, props, body);            // default exchange, routing key = queue
            channel.waitForConfirmsOrDie(5000);
            LOG.info("[Payments][RabbitMQ] Published " + MESSAGE_TYPE + " for order "
                    + payment.getOrderId() + " (" + messageId + ")");
            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[Payments][RabbitMQ] Failed to publish " + MESSAGE_TYPE
                    + " for order " + payment.getOrderId(), e);
            return false;
        }
    }

    @PreDestroy
    void close() {
        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception ignored) {
            // shutting down
        }
    }
}