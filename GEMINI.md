# GEMINI Five-Minute Guide: KCD Tax Project

This document provides a comprehensive overview for AI assistants to understand and interact with the KCD Tax project.

## 1. Project Overview

This is a multi-module Spring Boot application written in Kotlin for calculating Value Added Tax (VAT). It consists of two main components: a REST API server for managing businesses and tax data, and a separate data collector that processes sales and purchase information from an Excel file.

### Core Functionality

- **Data Collection**: An API endpoint to trigger data collection for a specific business.
- **Status Tracking**: An endpoint to check the status of a data collection job (`NOT_REQUESTED`, `COLLECTING`, `COLLECTED`).
- **Business & Permission Management**: APIs for CRUD operations on business entities and managing user permissions (Admin/Manager roles).
- **VAT Calculation**: An API to retrieve the calculated VAT based on collected sales and purchase data, with access control based on user roles.

### Architecture

The project follows a clean, decoupled 4-layer multi-module architecture:

- `tax/` (Root)
    - `common/`: A pure Kotlin module containing core domain enums and custom exceptions. It has no framework dependencies.
    - `infrastructure/`: Handles data persistence and external interactions. It includes JPA entities, Spring Data repositories, and utilities like an Excel parser.
    - `api-server/`: The main public-facing REST API server. It handles user requests, authentication, and business logic.
    - `collector/`: A background worker application that polls the database for new collection tasks, parses the data file, and updates the database.

### Tech Stack

- **Language**: Kotlin
- **Framework**: Spring Boot
- **Data**: Spring Data JPA with Hibernate
- **Database**: H2 (File-based, shared between `api-server` and `collector`)
- **Build**: Gradle (Kotlin DSL)
- **API Docs**: SpringDoc (OpenAPI)

---

## 2. API Endpoints

### Collection APIs
- `POST /api/v1/collections`: Request data collection for a business.
- `GET /api/v1/collections/{businessNumber}/status`: Check the collection status.

### Business Place Management (ADMIN Only)
- `POST /api/v1/business-places`: Create a new business place.
- `GET /api/v1/business-places`: Get a list of all business places.
- `GET /api/v1/business-places/{businessNumber}`: Get details for a specific business place.
- `PUT /api/v1/business-places/{businessNumber}`: Update a business place's information.

### Permission Management (ADMIN Only)
- `POST /api/v1/business-places/{businessNumber}/admins`: Grant a manager permission to a business place.
- `GET /api/v1/business-places/{businessNumber}/admins`: List all managers with permission for a business place.
- `DELETE /api/v1/business-places/{businessNumber}/admins/{adminId}`: Revoke a manager's permission.

### VAT Calculation API
- `GET /api/v1/vat`: Calculate and retrieve VAT.
    - **ADMIN**: Can query all businesses or a specific one.
    - **MANAGER**: Can only query businesses they have been granted access to.

---

## 3. Key Commands

### Build

Build all modules and run tests.
```bash
./gradlew clean build
```

### Run Application

**Important**: Both the `api-server` and `collector` must be running for the system to be fully operational.

**1. Run API Server (Port 8080)**:
In one terminal, run:
```bash
./gradlew :api-server:bootRun
```

**2. Run Collector (Port 8081)**:
In a separate terminal, run:
```bash
./gradlew :collector:bootRun
```

### Run Tests

Execute all unit and integration tests across all modules.
```bash
./gradlew test
```

---

## 4. Development Conventions

### Authentication & Authorization

- **Method**: A simple, prototype-level header-based authentication is used. **This is not secure for production.**
- **Headers**:
    - `X-Admin-Id`: The ID of the administrator/manager.
    - `X-Admin-Role`: The role (`ADMIN` or `MANAGER`).
- **Roles**:
    - `ADMIN`: Can access all data and manage permissions.
    - `MANAGER`: Can only access data for businesses they have been granted permission to.

### Asynchronous Data Flow (DB Polling)

1.  A `POST /api/v1/collections` request is sent to the `api-server`.
2.  The server validates the request and ensures the business is not already being collected. The status in the DB remains `NOT_REQUESTED`.
3.  The `collector` application polls the database every 10 seconds for records with `NOT_REQUESTED` status.
4.  Upon finding a task, the `collector` updates the status to `COLLECTING`, spends ~5 minutes parsing the `sample.xlsx` file, and persists the transaction data.
5.  Finally, the `collector` updates the status to `COLLECTED`.

### VAT Calculation Logic

The VAT is calculated using the formula `(Total Sales - Total Purchases) * (1 / 11)`. The result is rounded to the nearest 10 won (e.g., `454,545.45...` -> `454,545` -> `454,550`). This logic is implemented in `infrastructure/src/main/kotlin/com/kcd/tax/infrastructure/util/VatCalculator.kt`.

### Database Access

- **H2 Console**: http://localhost:8080/h2-console
- **JDBC URL**: `jdbc:h2:file:~/tax-data/taxdb;AUTO_SERVER=TRUE`
- **Username**: `sa`
- **Password**: (blank)

The `AUTO_SERVER=TRUE` option is critical as it allows both the `api-server` and `collector` processes to access the same database file simultaneously. The database file is stored in the user's home directory (`~/tax-data`).

### Key Design Decisions

- **Multi-Module Architecture**: The project is split into four modules (`common`, `infrastructure`, `api-server`, `collector`) to enforce separation of concerns, improve testability, and allow for independent scaling and deployment.
- **Database Polling**: Chosen for its simplicity and to avoid external message queue dependencies (like Kafka/RabbitMQ) for this prototype. The 10-second polling delay is considered acceptable given the 5-minute collection simulation.
- **Natural Primary Key**: The `business_place` table uses the 10-digit business number as its primary key. This makes queries more intuitive and reinforces the domain model, at the minor cost of using a `VARCHAR` PK.
- **Entity State Logic**: State transitions (e.g., for `CollectionStatus`) are handled by methods within the JPA entity itself (e.g., `businessPlace.startCollection()`). This encapsulates business rules and prevents invalid state changes.