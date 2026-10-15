# E-commerce Microservices Platform

A scalable, containerized e-commerce backend application built using **Java, Spring Boot, and Microservices Architecture**. The project demonstrates how independent services communicate through a centralized API Gateway to support essential e-commerce operations, including user management, product catalog, shopping cart, order processing, and notifications.

## 🚀 Features

- **API Gateway:** Centralized entry point for routing client requests to backend services.
- **User Service:** User registration, login, and authentication.
- **Product Service:** Product catalog management and product retrieval.
- **Cart Service:** Add products to the shopping cart and retrieve cart details.
- **Order Service:** Create and retrieve customer orders.
- **Notification Service:** Dedicated service for handling application notifications.
- **Security:** JWT-based authentication and role-based authorization.
- **Database Integration:** PostgreSQL and MongoDB for data persistence.
- **Caching:** Redis integration for caching support.
- **Message Broker:** Apache Kafka for asynchronous communication between services.
- **Containerization:** Docker and Docker Compose for running the application and its infrastructure.

## 🛠️ Tech Stack

| Category | Technologies |
|---|---|
| Language | Java |
| Backend Framework | Spring Boot |
| Security | Spring Security, JWT |
| Architecture | Microservices, REST APIs |
| Databases | PostgreSQL, MongoDB |
| Caching | Redis |
| Messaging | Apache Kafka |
| Containerization | Docker, Docker Compose |
| API Testing | Postman |
| Build Tool | Maven |

## 🏗️ Architecture

The application follows a microservices architecture in which each service handles a specific business responsibility.

```text
             Client / Postman
                    |
                    v
               API Gateway
                    |
          +---------+---------+
          |         |         |
          v         v         v
      User       Product     Cart
     Service     Service    Service
          |         |         |
          +---------+---------+
                    |
                    v
               Order Service
                    |
                    v
            Notification Service

   Supporting Infrastructure:
   PostgreSQL | MongoDB | Redis | Kafka
```

*Note: This is a conceptual overview. Actual communication paths and database assignments depend on the implementation.*

## ⚙️ Prerequisites

Install the following before running the project:

- Java Development Kit (JDK)
- Docker Desktop
- Docker Compose
- Postman (optional, for API testing)

## ▶️ Getting Started

### 1. Clone the repository

```bash
git clone <YOUR_REPOSITORY_URL>
cd ecommerce-microservices
```

Replace `<YOUR_REPOSITORY_URL>` with your GitHub repository URL.

### 2. Start the application

Ensure Docker Desktop is running, then execute:

```bash
docker compose up -d --build
```

### 3. Verify running containers

```bash
docker compose ps
```

### 4. Check service health

The project exposes application services on local ports `8080`–`8085` in the current configuration.

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:8082/actuator/health
Invoke-RestMethod http://localhost:8083/actuator/health
Invoke-RestMethod http://localhost:8084/actuator/health
Invoke-RestMethod http://localhost:8085/actuator/health
```

These commands assume the services are configured with Spring Boot Actuator health endpoints and the listed port mappings.

## 🧪 API Testing

Use Postman to test the main application workflows:

1. Register a new user and log in.
2. Retrieve products from the product catalog.
3. Add a product to the shopping cart.
4. Retrieve the cart details.
5. Create an order and retrieve its details.
6. Test authentication and authorization with protected endpoints.

**Example product endpoint:**

```http
GET http://localhost:8080/api/products
```

Protected endpoints may require a valid JWT access token in the request's `Authorization` header.

## 📁 Project Structure

```text
ecommerce-microservices/
├── api-gateway/
├── user-service/
├── product-service/
├── cart-service/
├── order-service/
├── notification-service/
├── docs/
├── init-scripts/
├── scripts/
├── docker-compose.yml
└── README.md
```

*The structure above is representative; filenames and configuration files may vary in your repository.*

## 🎯 Learning Outcomes

- Understanding microservices architecture and service separation.
- Building RESTful APIs using Java and Spring Boot.
- Implementing authentication and role-based authorization.
- Integrating relational and NoSQL databases.
- Exploring caching and event-driven communication.
- Containerizing applications with Docker Compose.
- Testing APIs and validating end-to-end e-commerce workflows.

## 🔮 Future Enhancements

- Integrate a dedicated payment service.
- Implement order status tracking and inventory management.
- Add automated unit and integration tests.
- Introduce centralized logging, monitoring, and distributed tracing.
- Set up CI/CD pipelines for automated builds and deployment.

## 👨‍💻 Author

**Vikash Kumar Mistry**

Java Backend Development | Microservices | AI/ML

---

⭐ If you find this project useful, consider starring the repository.
