# Phase 11: Docker Compose, Multi-Container Orchestration, and Local Networking

Phase 11 delivers a single-command local deployment for the entire E-Commerce microservices platform using **Docker Compose**. It brings together all 6 Spring Boot microservices and 4 backing infrastructure containers onto a shared, isolated bridge network with automatic internal DNS service discovery, health-check dependencies, and persistent data volumes.

---

## Architecture and Topology

```
                                  HOST / POSTMAN / BROWSER
                                             │
                                             ▼ (Port 8080)
┌────────────────────────────────────────────┴────────────────────────────────────────────┐
│ Docker Network: ecommerce-network                                                       │
│                                                                                         │
│                                  ┌───────────────────┐                                  │
│                                  │    api-gateway    │ (:8080)                          │
│                                  └─────────┬─────────┘                                  │
│                                            │                                            │
│          ┌────────────────┬────────────────┼────────────────┬────────────────┐          │
│          ▼                ▼                ▼                ▼                ▼          │
│   ┌────────────┐   ┌────────────┐   ┌────────────┐   ┌────────────┐   ┌────────────┐    │
│   │user-service│   │product-svc │   │cart-service│   │order-service   │notification│    │
│   │  (:8081)   │   │  (:8082)   │   │  (:8083)   │   │  (:8084)   │   │  (:8085)   │    │
│   └──────┬─────┘   └──────┬─────┘   └──────┬─────┘   └──────┬─────┘   └──────┬─────┘    │
│          │                │                │                │                │          │
│          ▼                ▼                ▼                ▼                ▼          │
│   ┌────────────┐   ┌────────────┐   ┌────────────┐   ┌────────────┐   ┌────────────┐    │
│   │  postgres  │   │  mongodb   │   │   redis    │   │  postgres  │   │  mongodb   │    │
│   │  (userdb)  │   │(productdb) │   │ (cart:uid) │   │ (orderdb)  │   │(notif_db)  │    │
│   └────────────┘   └────────────┘   └────────────┘   └────────────┘   └────────────┘    │
│                                                             │                │          │
│                                                             ▼ (order-events) ▼          │
│                                                      ┌──────────────────────────────┐   │
│                                                      │      kafka (KRaft 9092)      │   │
│                                                      └──────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Service Matrix

| Service | Container Name | Base / Build | Host Port | Internal Port | Dependencies (`condition`) | Healthcheck |
|---|---|---|---|---|---|---|
| `postgres` | `ecommerce-postgres` | `postgres:16-alpine` | `5432` | `5432` | None | `pg_isready -U $$POSTGRES_USER -d postgres` |
| `redis` | `ecommerce-redis` | `redis:7.4-alpine` | `6379` | `6379` | None | `redis-cli ping` |
| `mongodb` | `ecommerce-mongodb` | `mongo:8.0` | `27017` | `27017` | None | `mongosh --eval "db.adminCommand('ping').ok"` |
| `kafka` | `ecommerce-kafka` | `apache/kafka:3.8.0` | `9092` | `9092` / `29092` | None | `kafka-cluster.sh cluster-id` |
| `user-service` | `user-service` | `./user-service/Dockerfile` | `8081` | `8081` | `postgres` (`service_healthy`) | `wget --spider http://localhost:8081/actuator/health` |
| `product-service` | `product-service` | `./product-service/Dockerfile` | `8082` | `8082` | `mongodb` (`service_healthy`) | `wget --spider http://localhost:8082/actuator/health` |
| `cart-service` | `cart-service` | `./cart-service/Dockerfile` | `8083` | `8083` | `redis` (`service_healthy`) | `wget --spider http://localhost:8083/actuator/health` |
| `order-service` | `order-service` | `./order-service/Dockerfile` | `8084` | `8084` | `postgres`, `kafka` (`service_healthy`) | `wget --spider http://localhost:8084/actuator/health` |
| `notification-service` | `notification-service` | `./notification-service/Dockerfile` | `8085` | `8085` | `mongodb`, `kafka` (`service_healthy`) | `wget --spider http://localhost:8085/actuator/health` |
| `api-gateway` | `api-gateway` | `./api-gateway/Dockerfile` | `8080` | `8080` | All microservices (`service_started`) | `wget --spider http://localhost:8080/actuator/health` |

---

## Service Discovery and Networking

All containers join a custom bridge network: `ecommerce-network`.
Docker provides embedded DNS resolution:
- Microservices connect to PostgreSQL at `postgres:5432`
- Microservices connect to Redis at `redis:6379`
- Microservices connect to MongoDB at `mongodb:27017`
- Microservices connect to Kafka at `kafka:29092` (internal listener)
- API Gateway routes downstream requests to `http://user-service:8081`, `http://product-service:8082`, etc.

No Eureka or Consul agent is necessary for the local container environment.

---

## Data Durability & Named Volumes

All stateful containers store data on dedicated named volumes so database records and message topics survive container restarts:
- `postgres_data` $\rightarrow$ `/var/lib/postgresql/data`
- `redis_data` $\rightarrow$ `/data` (with `--appendonly yes`)
- `mongo_data` $\rightarrow$ `/data/db`
- `kafka_data` $\rightarrow$ `/tmp/kraft-combined-logs`

---

## Multi-Stage Dockerfile Strategy

Each Spring Boot microservice uses a multi-stage Docker build:
1. **Builder Stage:** Uses `maven:3.9-eclipse-temurin-25` to compile and package an executable JAR.
2. **Runtime Stage:** Uses lightweight `eclipse-temurin:25-jre-alpine` (~100 MB), copies only the packaged JAR, and executes it with non-privileged defaults.

---

## How to Run (PowerShell)

### 1. Initialize Local Environment
```powershell
Set-Location D:\DOWNLOADS_in_D\ecommerce-microservices\ecommerce-microservices
Copy-Item .env.example .env
```

### 2. Start Backing Datastores Only (Optional / Development)
```powershell
docker compose up -d postgres redis mongodb kafka
```

### 3. Build and Start the Entire Platform
```powershell
docker compose up -d --build
```

### 4. Check Container Status & Health
```powershell
docker compose ps
```
All containers should display status `Up` or `Up (healthy)`.

### 5. Inspect Logs
```powershell
# View logs across all services
docker compose logs -f

# View logs for a single service
docker compose logs -f api-gateway
docker compose logs -f order-service
```

### 6. Graceful Shutdown
```powershell
# Stop and remove containers and network, preserving volumes
docker compose down

# Stop and wipe database volumes (clean slate)
docker compose down -v
```
> [!WARNING]
> Running `docker compose down -v` permanently deletes PostgreSQL databases, MongoDB collections, Redis cart entries, and Kafka event logs.

---

## Troubleshooting Guide

| Problem | Cause | Diagnostic Command | Fix |
|---|---|---|---|
| `port is already allocated` | Another local process (e.g. local PostgreSQL or Redis) occupies port 5432, 6379, 8080, etc. | `Get-NetTCPConnection -LocalPort 8080` | Stop conflicting local service or change host port in `.env`. |
| `service_healthy` wait timeout | Backing store slow to start or healthcheck command failing. | `docker compose logs postgres` | Verify `init-multiple-dbs.sh` permissions and increase `start_period` if necessary. |
| Gateway returns `503 Service Unavailable` | Downstream microservice is still booting or crashed. | `docker compose ps` | Check downstream container logs: `docker compose logs user-service`. |
| Kafka connection refused | Internal vs external listener mismatch. | `docker compose logs kafka` | Ensure microservices use `kafka:29092` inside Docker, while host tools use `localhost:9092`. |
