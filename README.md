# E-Commerce Microservices Platform

This repository is being built incrementally as a production-style Java 21/Spring Boot e-commerce platform. Phase 1 establishes service boundaries, data ownership, security flow, and the configuration contract.

Read [the Phase 1 architecture guide](docs/architecture.md) before starting a service. It documents ports, databases, REST/Kafka communication, authentication, schemas, Compose topology, and the development roadmap.

## Current status

Phase 6 (Payment Service) and Phase 12 (Testing) are complete. The standalone Payment Service runs on port 8086 with dedicated `payment_db` storage, simulated payment execution, refunds, order ownership validation, and comprehensive automated test coverage: 96 automated tests passing across 15 test classes with 100% pass rate (unit tests with Mockito, Spring MVC slice tests with `@WebMvcTest`, reactive Gateway integration tests, and Kafka consumer idempotency tests), an end-to-end automated customer journey verification script ([`scripts/e2e-test.ps1`](scripts/e2e-test.ps1)), and a complete Postman collection and environment ([`postman/`](postman/)).

## Local configuration

In PowerShell, create your untracked local environment file:

```powershell
Copy-Item .env.example .env
```

Replace `JWT_SECRET` and `POSTGRES_PASSWORD` in `.env` before running containers. Do not commit `.env`.
