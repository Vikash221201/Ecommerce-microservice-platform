# E-Commerce Platform Architecture (Phase 1)

## What we are building

This is a container-ready e-commerce backend made of independently deployable Spring Boot services. Each service owns its data and exposes a focused API. The client enters through the Gateway; services do not share database tables.

The existing `user-service` scaffold is retained as a supporting identity service. Although it was not in the requested six business services, JWT login and BCrypt-backed user records need a clear owner. It runs on port `8086`; the six requested services retain their specified ports.

## Services and ownership

| Service | Port | Responsibility | Owned store |
| --- | ---: | --- | --- |
| gateway-api | 8080 | Single public entry point, routing, JWT validation, cross-cutting security | none |
| user-service | 8081 | Registration/login, BCrypt password hashes, JWT creation | PostgreSQL: `userdb` / `users` |
| product-service | 8082 | Product catalogue and inventory quantity | MongoDB: `productdb` / `products` |
| cart-service | 8083 | Per-user active shopping carts | Redis: `cart:{userId}` |
| order-service | 8084 | Order lifecycle and immutable order-line snapshots | PostgreSQL: `orderdb` / `orders`, `order_items` |
| notification-service | 8085 | Event-driven notification audit and delivery simulation | MongoDB: `notificationdb` / `notifications` |
| payment-service | 8086 | Simulated payments and refunds; never payment-card data | PostgreSQL: `payment_db` / `payments` |

### Why these databases

- PostgreSQL provides transactions, constraints, decimal amounts, relations, and indexes needed for products, orders, payments, and users. Each service gets a separate database, so data changes remain private.
- Redis fits carts because they are short-lived, per-user aggregates with fast reads and writes. A Redis hash or JSON value at `cart:{userId}` avoids unnecessary relational joins.
- MongoDB fits notifications because notification payloads can evolve and are append-oriented documents rather than highly relational business records.

## Communication

Synchronous REST calls are used only when a caller needs an immediate answer:

```text
Gateway -> Product: catalogue CRUD/read
Cart -> Product: verify a product exists and has sufficient stock
Order -> Cart: obtain the current cart, then clear it after durable order creation
Order -> Product: revalidate product/pricing/availability before creating an order
```

Kafka is the single asynchronous broker. It prevents Order, Payment, and Notification from blocking each other:

```text
order.created (Order -> Kafka -> Payment, Notification)
payment.completed / payment.failed (Payment -> Kafka -> Order, Notification)
order.status.changed (Order -> Kafka -> Notification)
```

Events will use versioned JSON records with an `eventId`, `eventType`, `occurredAt`, `orderId`, `userId`, and event-specific data. Consumers are idempotent: repeated Kafka delivery must not create a second payment or notification.

Kafka is selected over RabbitMQ because the platform benefits from durable, replayable domain-event streams: a later analytics or email consumer can read existing order/payment events without changing the producer.

## Request and event flow

```text
Client + Bearer JWT
        |
        v
Gateway (:8080) -- validates JWT, forwards identity headers --> service
        |
        +--> Product/Cart/Order/Payment/Notification APIs

Order creation:
Order -> Cart (read) -> Product (revalidate) -> order_db (PENDING)
      -> Kafka order.created -> Payment -> payment_db
      -> Kafka payment.completed|payment.failed -> Order (updates status)
      -> Kafka events -> Notification -> MongoDB + safe application log
```

The order stores item name, unit price, and quantity as an order-line snapshot. Product data may later change, but a past order must remain historically correct. Inventory decrement/reservation is intentionally coordinated by the Product Service in a later phase; no other service writes the product database.

## Authentication and authorization

1. A client registers or logs in through `/api/auth/**`, routed to User Service.
2. User Service compares the submitted password against its BCrypt hash and returns a signed, expiring JWT with user ID and roles.
3. The client supplies `Authorization: Bearer <token>` to Gateway.
4. Gateway validates the signature and expiry. Each downstream service also validates the JWT; this is defense in depth if a service is accessed internally.
5. Services derive the user identity from the token, never from a user ID supplied in a path alone. `ROLE_ADMIN` manages products; `ROLE_USER` manages only that user’s cart, orders, and payments.

No password, JWT secret, card number, or CVV is logged or stored. The payment flow accepts a safe payment-method label and simulates a provider result.

## Docker topology and environment

The later Compose stack will run these containers on one private `ecommerce-network`:

```text
gateway-api, product-service, cart-service, order-service,
payment-service, notification-service, user-service,
postgres, redis, mongodb, kafka
```

Docker Compose supplies service discovery through its internal DNS: the hostname `product-service` resolves to that container, so `http://product-service:8081` works without Eureka. Host port mappings are for the developer only; in production only the load balancer/Gateway is public. PostgreSQL, MongoDB, Redis, and Kafka use named volumes (Redis persistence will be explicitly enabled).

`.env.example` lists all configuration keys. Copy it to `.env`, use non-default secrets, and do not commit that file. Docker Compose reads `.env`; local IDE runs should override container hostnames with `localhost`.

## Planned schema and constraints

- `products`: UUID primary key; category and name indexes; non-negative quantity; positive price; creation/update timestamps.
- `orders`: UUID primary key; `user_id` and `created_at` indexes; status/payment-status constrained by enums. `order_items` has a foreign key to `orders` and stores product ID plus price snapshot.
- `payments`: UUID primary key; unique `order_id` for one active payment; indexed user ID; unique transaction reference.
- `users`: UUID primary key; unique, normalized email; BCrypt password hash only; roles.
- `notifications`: MongoDB document keyed by UUID; indexes on `userId`, `orderId`, and `createdAt`.
- `cart:{userId}`: Redis key with an expiry and a structured set of product items.

## Development roadmap

1. **Architecture (current):** define boundaries, configuration contract, database ownership, and event contracts.
2. **Product Service:** product DTOs, validation, PostgreSQL CRUD, errors, pagination, tests.
3. **PostgreSQL hardening:** migrations, indexes, health checks, repository integration tests.
4. **Cart Service:** Redis cart API and Product REST validation.
5. **Order Service:** transactional order/order-item persistence and synchronous validation.
6. **Payment Service:** safe simulated payment/refund API and persistence.
7. **Notification Service:** MongoDB audit records and logging simulation.
8. **Kafka:** publish/consume versioned events with idempotency.
9. **Gateway:** routes, timeout/error behavior, and public API boundary.
10. **Security:** User Service, JWT, role and ownership checks throughout.
11. **Docker Compose:** Dockerfiles, health checks, volumes, and local networking.
12. **Testing:** service/unit/integration tests and end-to-end test workflow.
13. **OpenAPI:** Swagger docs and example error/auth responses.
14. **AWS readiness:** ECR/ECS/RDS/ElastiCache/MSK deployment guidance and production profiles.

## Phase 1 verification

- Java 21 is installed: verified locally (`21.0.12`).
- Project scaffolds exist for all seven services.
- No executable service was changed in this phase; therefore no database or broker is required yet.
- Maven Wrapper invocation from PowerShell currently fails before Maven starts. This will be repaired/validated before Phase 2 builds begin.
