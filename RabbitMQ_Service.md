# Payments service: asynchronous communication with Shipping (RabbitMQ)

> Module: Microservices Architecture (MSOA), E-commerce, Group 8
> Service: Payments (port 8085), producer side
> Related service: Shipping (port 8086), consumer side

---

## 1. Purpose

Once a payment succeeds, Shipping must create a shipment for the order. Instead of calling Shipping directly over REST, Payments **publishes a message to RabbitMQ** and Shipping consumes it.

- Payments does not wait for Shipping and answers the client immediately.
- If Shipping is down, messages wait in the queue and are processed when it is back.
- Payments does not need to know Shipping's address or availability.

---

## 2. Flow

1. The frontend sends `POST /api/payments` to Payments with the `orderId` and the payment method.
2. Payments reads the order in Orders and checks it is awaiting payment.
3. Payments updates the order to `PAID` in Orders.
4. Payments publishes a `ShipmentRequested` message to the `shipping_requests` queue.
5. Payments answers `201 Created` to the client.
6. Shipping consumes the message, reads the order in Orders, creates the shipment (`PREPARING`) and acknowledges the message.

The message is published only after Orders accepted the `PAID` update. If that update fails, the payment is marked `FAILED` and nothing is published.

---

## 3. What was added to Payments

| Element | Role |
|---|---|
| `amqp-client` dependency (5.20.0) in `pom.xml` | RabbitMQ Java client |
| `rabbitmq.*` properties in `microprofile-config.properties` | Broker host, port, username, password |
| New class `ShipmentPublisher` | Builds and publishes the message |
| Call in `PaymentResource.createPayment` | Publishes after the `PAID` update, before the `201` response |

Main characteristics of `ShipmentPublisher`:

- Connection opened on first use, so Payments starts even if RabbitMQ is down.
- One short-lived channel per publish (channels are not thread-safe).
- Queue declared at publish time, with the same flags as the consumer.
- Publisher confirms: the broker must confirm it received the message.
- Only payments with status `SUCCESS` are published.
- Never throws: a broker error is logged and does not break a payment that already succeeded.

---

## 4. Message contract (shared with Shipping)

| Setting | Value |
|---|---|
| Broker | `localhost:5672` (`guest` / `guest`) |
| Exchange | `""` (default exchange) |
| Routing key / queue | `shipping_requests` |
| Queue flags | durable, not exclusive, no auto-delete |
| Persistence | Persistent (`deliveryMode = 2`) |
| Format | JSON (`application/json`) |

Message fields:

| Field | Example | Note |
|---|---|---|
| `messageId` | `msg-001` | Generated for each message |
| `type` | `ShipmentRequested` | Event type |
| `orderId` | `5b3f2a1e-...` | Order to ship |
| `paymentId` | `pay-57ec47bd` | Payment reference |
| `amount` | `16998.0` | Taken from the order in Orders, not from the client |
| `method` | `CARD` | Payment method |
| `status` | `SUCCESS` | Payment status |
| `createdAt` | `2026-11-25T14:00:00Z` | ISO 8601 UTC |

The message contains no delivery address: Shipping reads it from Orders.

---

## 5. Behavior by situation

| Situation | Result |
|---|---|
| Payment succeeds and order updated to `PAID` | Message published, `201 Created` |
| Order update to `PAID` fails | Payment `FAILED`, `502`, no message |
| Order not found | `404`, no message |
| Order not awaiting payment | `409`, no message |
| RabbitMQ down at publish time | Error logged, payment still `201`, no shipment created automatically |
| Shipping stopped | Message waits in the queue and is consumed when Shipping starts |
| Message delivered twice | Shipping ignores it (idempotent on `orderId`) |

---

## 6. How to test

1. Start RabbitMQ, Orders (or the test stub on port 8084), Shipping and Payments.
2. Send a payment with `POST /api/payments` (`orderId` and `method`).
3. Check the logs: Payments shows `Published ShipmentRequested`, Shipping shows `Created shipment`.
4. In the RabbitMQ UI (http://localhost:15672), open the queue `shipping_requests` to see Ready, Unacked and Total messages and the publish, deliver and ack rates.
5. To read a message, stop Shipping, make a payment, then use **Get messages** with `Nack message requeue true`.

---

## 7. Known limitations

- If RabbitMQ is down at payment time, the payment succeeds but no message is sent. The Outbox pattern would fix this.
- No retry limit and no dead-letter queue on the Shipping side.
- Credentials are stored in a properties file; use environment variables or a dedicated RabbitMQ user outside local development.
- Delivery is at-least-once, so duplicates are possible and Shipping must stay idempotent.