# Inventory Management API

A RESTful service that tracks product stock by SKU, secured with OAuth2 client-credentials.

**Live demo:** [https://inventory-api-4rrmxognkq-uc.a.run.app/swagger-ui/index.html](https://inventory-api-4rrmxognkq-uc.a.run.app/swagger-ui/index.html)

---

## Table of Contents

1. [High-Level System Design](#1-high-level-system-design)
2. [Service Description & Functional Requirements](#2-service-description--functional-requirements)
3. [Tech Stack](#3-tech-stack)
4. [Prerequisites](#4-prerequisites)
5. [Running Locally](#5-running-locally)
6. [API Security](#6-api-security)
7. [Observability & Logging](#7-observability--logging)

---

## 1. High-Level System Design

### Overview

The Inventory Management API is a self-contained Spring Boot microservice backed by an embedded H2 in-memory database. It is secured with **OAuth2 client-credentials** (Spring Authorization Server + Resource Server, RS256 JWT with rotating JWKs). No external dependencies are required.

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Client (curl / Swagger UI / upstream service)     │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ HTTP
           ┌───────────────▼──────────────────┐
           │        Spring Boot Application    │
           │            (port 8080)            │
           │                                   │
           │  ┌────────────────────────────┐   │
           │  │   RequestMetricsFilter     │   │
           │  │   (OncePerRequestFilter)   │   │
           │  │   Logs: METHOD /path →     │   │
           │  │   status=N duration=Nms    │   │
           │  └────────────┬───────────────┘   │
           │               │                   │
           │  ┌────────────▼───────────────┐   │
           │  │  SecurityFilterChain @1    │   │
           │  │  (OAuth2 Authorization     │   │
           │  │   Server — issues JWTs)    │   │
           │  │  POST /oauth2/token        │   │
           │  └────────────┬───────────────┘   │
           │               │                   │
           │  ┌────────────▼───────────────┐   │
           │  │  SecurityFilterChain @2    │   │
           │  │  (OAuth2 Resource Server   │   │
           │  │   — validates JWT, checks  │   │
           │  │   SCOPE_inventory.read /   │   │
           │  │   SCOPE_inventory.write)   │   │
           │  └────────────┬───────────────┘   │
           │               │                   │
           │  ┌────────────▼───────────────┐   │
           │  │     InventoryController    │   │
           │  │  GET  /api/inventory       │   │
           │  │  GET  /api/inventory/{id}  │   │
           │  │  POST /api/inventory/{id}  │   │
           │  │  POST /api/inventory/{id}  │   │
           │  │       /purchase            │   │
           │  └────────────┬───────────────┘   │
           │               │                   │
           │  ┌────────────▼───────────────┐   │
           │  │     InventoryService       │   │
           │  │  - get()                   │   │
           │  │  - listAll()               │   │
           │  │  - addStock()  [upsert]    │   │
           │  │  - purchase()  [atomic]    │   │
           │  └────────────┬───────────────┘   │
           │               │                   │
           │  ┌────────────▼───────────────┐   │
           │  │   InventoryRepository      │   │
           │  │   (Spring Data JPA)        │   │
           │  │   decrementIfSufficient()  │   │
           │  │   [single conditional SQL  │   │
           │  │    UPDATE — no oversell]   │   │
           │  └────────────┬───────────────┘   │
           │               │                   │
           │  ┌────────────▼───────────────┐   │
           │  │        H2 In-Memory DB     │   │
           │  │        (inventory table)   │   │
           │  │   skuId VARCHAR (PK)       │   │
           │  │   quantity INT             │   │
           │  └────────────────────────────┘   │
           │                                   │
           │  ┌────────────────────────────┐   │
           │  │  GlobalExceptionHandler    │   │
           │  │  (@RestControllerAdvice)   │   │
           │  │  All errors → text/plain   │   │
           │  └────────────────────────────┘   │
           │                                   │
           │  ┌────────────────────────────┐   │
           │  │  Logback (SLF4J)           │   │
           │  │  Console + Rolling File    │   │
           │  │  logs/inventory-api.log    │   │
           │  └────────────────────────────┘   │
           └───────────────────────────────────┘
```

### Key Design Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Auth | OAuth2 client-credentials (RS256 JWT) | Stateless, scope-based; embedded auth server removes external dependency |
| JWK rotation | `JwkRotationService` — 30-min rotation, 3-key overlap window | Overlap window (> 10 min token TTL) allows in-flight tokens to remain valid across rotations |
| Purchase atomicity | Single conditional `UPDATE ... WHERE quantity >= :qty` | Prevents oversell under concurrent load without application-level locking |
| Additive upsert | `findById` → add or create | `POST /api/inventory/{skuId}` never overwrites — accumulates stock |
| Error format | `text/plain` strings | Per API contract; avoids Spring's default JSON error wrapper |
| Log file | `logs/inventory-api.log` (rolling, daily, gzip) | Persisted log separate from console for production audit and debugging |
| Seed data initialization | `schema.sql` + `data.sql` via Spring SQL init | Scripts run before Hibernate (`ddl-auto: none`); 100 SKUs loaded on every startup |

### Component Map

```
com.nuuly.inventory
├── InventoryApiApplication.java      # @SpringBootApplication @EnableScheduling
├── api/
│   ├── InventoryController.java      # 4 REST endpoints at /api/inventory, delegates to service
│   └── dto/
│       ├── InventoryQuantityRequest  # @NotNull @Min(1) Integer quantity
│       └── InventoryItemResponse     # record + static from(InventoryItem)
├── domain/
│   ├── InventoryItem.java            # @Entity: skuId (PK), quantity
│   ├── SkuNotFoundException.java     # → HTTP 404 text/plain
│   └── InsufficientInventoryException.java  # → HTTP 400 "Insufficient inventory"
├── service/
│   └── InventoryService.java         # Business logic, @Transactional, SLF4J logging
├── repository/
│   └── InventoryRepository.java      # JpaRepository + @Modifying atomic UPDATE
└── config/
    ├── SecurityConfig.java             # Auth server chain @Order(1) + Resource server chain @Order(2)
    ├── AuthorizationServerConfig.java  # Registers inventory-client, JWK source, JWT decoder
    ├── JwkRotationService.java         # @Service — RSA-2048 key generation and 30-min rotation
    ├── RequestMetricsFilter.java       # HTTP request/response metrics logger
    ├── OpenApiConfig.java              # Swagger UI with OAuth2 client-credentials scheme
    └── GlobalExceptionHandler.java     # text/plain error mapping, SLF4J logging
```

### Data Model

```
Table: inventory
┌────────────────┬─────────┬────────────────────────────────┐
│ Column         │ Type    │ Notes                          │
├────────────────┼─────────┼────────────────────────────────┤
│ sku_id         │ VARCHAR │ Primary key (SKU string)       │
│ quantity       │ INTEGER │ Current stock (≥ 0, enforced)  │
└────────────────┴─────────┴────────────────────────────────┘
```

> **H2 caveat:** Data is in-memory and lost on restart. For production, replace the H2 datasource with Cloud SQL (PostgreSQL/MySQL) — no application code changes required, only `application.yml` datasource config.

---

## 2. Service Description & Functional Requirements

The Inventory Management API tracks product stock keyed by SKU. It exposes four core operations:

| Method | Endpoint | Required scope | Description |
|--------|----------|---------------|-------------|
| `GET` | `/api/inventory` | `inventory.read` | List all inventory items |
| `GET` | `/api/inventory/{skuId}` | `inventory.read` | Get current stock for a specific SKU |
| `POST` | `/api/inventory/{skuId}` | `inventory.write` | Add stock (additive upsert — creates if absent, adds to existing) |
| `POST` | `/api/inventory/{skuId}/purchase` | `inventory.write` | Purchase stock (atomic decrement with sufficiency check) |

### Functional Requirements

**FR-1 — Get inventory for a SKU**
- Returns `200` with `{ "skuId": "...", "quantity": N }` when the SKU exists.
- Returns `404` (plain text) when the SKU does not exist.

**FR-2 — Add stock (additive upsert)**
- Creates the SKU with the given quantity if it does not exist (`200`).
- Adds to the existing quantity if the SKU already exists (`200`).
- Rejects missing, non-integer, or `< 1` quantities with `400` (plain text).

**FR-3 — Purchase stock**
- Decrements stock and returns the remaining quantity (`200`) when stock is sufficient.
- Returns `400` with body `Insufficient inventory` when stock `< requested quantity`; stock is unchanged.
- Returns `404` when the SKU does not exist.
- Stock is guaranteed never to go negative (atomic operation).

**FR-4 — List all inventory**
- Returns `200` with an array of all items, or `[]` when empty (never `404` or `null`).

**FR-5 — Error format**
- All error responses (`400`, `404`) use `Content-Type: text/plain` with a plain string body — not JSON.

**FR-6 — API documentation**
- Live Swagger UI at `/swagger-ui.html`. Click **Authorize**, enter client credentials, select scopes, then call endpoints directly from the browser.

**FR-7 — Authentication & authorization**
- All `/api/inventory/**` endpoints require a valid JWT bearer token.
- Missing token → `401 Unauthorized`. Valid token with wrong scope → `403 Forbidden`.
- Token is issued by the embedded auth server at `POST /oauth2/token` (client-credentials grant).
- Public endpoints (no token required): `/swagger-ui/**`, `/v3/api-docs/**`, `/oauth2/token`, `/oauth2/jwks`, `/actuator/health`, `/h2-console/**`.

**FR-8 — Seed data**
- On startup, 100 dummy SKUs are loaded via `src/main/resources/data.sql` (Spring SQL init).
- The table schema is created by `src/main/resources/schema.sql`; Hibernate `ddl-auto` is set to `none`.
- Integration tests call `repo.deleteAll()` before each test case, so seed data does not affect test results.

---

## 3. Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.x |
| Web | Spring Web (Spring MVC) |
| Persistence | Spring Data JPA + H2 (in-memory) |
| Validation | Jakarta Bean Validation (`@Valid`, `@Min`, `@NotNull`) |
| Security | Spring Security — OAuth2 Authorization Server + Resource Server, RS256 JWT |
| Logging | SLF4J + Logback — console + rolling file (`logs/inventory-api.log`) |
| API Docs | springdoc-openapi 2.x (Swagger UI with OAuth2 Authorize flow) |
| Health | Spring Boot Actuator |
| Build | Maven 3.9+ (bundled `./mvnw` wrapper) |
| Container | Docker (multi-stage) + Docker Compose |

---

## 4. Prerequisites

### Local (Maven)

| Requirement | Version |
|-------------|---------|
| JDK | 21+ |
| Maven | Provided by `./mvnw` (no install needed) |

### Docker / Docker Compose

| Requirement | Notes |
|-------------|-------|
| Docker Engine | 24+ |
| Docker Compose | v2+ (`docker compose`) |

No external database or message broker is required — the app uses an embedded H2 in-memory store and an embedded OAuth2 authorization server.

---

## 5. Running Locally

### Option A — Maven (fastest)

```bash
# Build, test, and package
./mvnw clean package

# Run only tests
./mvnw test

# Start the application (http://localhost:8080)
./mvnw spring-boot:run
```

On startup, the schema is created from `schema.sql` and 100 dummy SKUs are loaded from `data.sql` automatically. The application is ready when you see:

```
Started InventoryApiApplication in X.XXX seconds
```

### Option B — Docker

```bash
# Build the image
docker build -t inventory-api:local .

# Run the container
docker run -p 8080:8080 inventory-api:local
```

### Option C — Docker Compose (recommended)

```bash
docker compose up --build
```

The Compose file defines one service (`api`) on port `8080` with an Actuator healthcheck. The container is marked healthy once `/actuator/health` returns `200`.

---

### Useful URLs (after startup)

| URL | Description |
|-----|-------------|
| [https://inventory-api-4rrmxognkq-uc.a.run.app/swagger-ui/index.html](https://inventory-api-4rrmxognkq-uc.a.run.app/swagger-ui/index.html) | **Live** — Swagger UI on Cloud Run |
| `http://localhost:8080/swagger-ui.html` | Local — Interactive API docs — click **Authorize** to log in |
| `http://localhost:8080/oauth2/token` | Local — Token endpoint (POST, Basic auth) |
| `http://localhost:8080/actuator/health` | Local — Health check endpoint |
| `http://localhost:8080/h2-console` | Local — H2 database console (dev only) |

---

### Quick curl examples

All `/api/inventory` endpoints require a JWT bearer token. Fetch one first:

**Step 1 — Get a token:**
```bash
TOKEN=$(curl -s \
  -u inventory-client:inventory-secret \
  -d 'grant_type=client_credentials&scope=inventory.read inventory.write' \
  http://localhost:8080/oauth2/token \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
```

**Step 2 — Use the token in API calls:**

List all inventory:
```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/inventory
```

Get a specific SKU:
```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/inventory/CW-0001-BM-02
```

Add stock (creates SKU if absent, otherwise adds to existing):
```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"quantity": 50}' \
  http://localhost:8080/api/inventory/MY-SKU-001
```

Purchase stock:
```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"quantity": 5}' \
  http://localhost:8080/api/inventory/MY-SKU-001/purchase
```

---

## 6. API Security

The API uses **OAuth2 client-credentials** flow. The embedded Spring Authorization Server issues RS256 JWTs; the Resource Server validates them on every request.

### Client credentials

| Field | Value |
|-------|-------|
| Client ID | `inventory-client` |
| Client Secret | `inventory-secret` |
| Grant type | `client_credentials` |
| Token TTL | 10 minutes |
| Auth methods | `CLIENT_SECRET_BASIC` (recommended), `CLIENT_SECRET_POST` |

### Scopes

| Scope | Grants access to |
|-------|-----------------|
| `inventory.read` | `GET /api/inventory`, `GET /api/inventory/{skuId}` |
| `inventory.write` | `POST /api/inventory/{skuId}`, `POST /api/inventory/{skuId}/purchase` |

Request both scopes in a single token for full access:
```bash
-d 'grant_type=client_credentials&scope=inventory.read inventory.write'
```

### Authorization rules

| Endpoint | Required scope | No token | Wrong scope |
|----------|---------------|----------|-------------|
| `GET /api/inventory` | `inventory.read` | 401 | 403 |
| `GET /api/inventory/{skuId}` | `inventory.read` | 401 | 403 |
| `POST /api/inventory/{skuId}` | `inventory.write` | 401 | 403 |
| `POST /api/inventory/{skuId}/purchase` | `inventory.write` | 401 | 403 |

### Public endpoints (no token required)

| Path | Purpose |
|------|---------|
| `POST /oauth2/token` | Token issuance |
| `GET /oauth2/jwks` | JWK set (for external JWT verification) |
| `/swagger-ui/**`, `/v3/api-docs/**` | API documentation |
| `/actuator/health` | Health check |
| `/h2-console/**` | H2 dev console |

### Using Swagger UI with OAuth2

1. Open `http://localhost:8080/swagger-ui.html`
2. Click the **Authorize** button (lock icon, top right)
3. Enter **Client ID**: `inventory-client` and **Client Secret**: `inventory-secret`
4. Select scopes: `inventory.read` and `inventory.write`
5. Click **Authorize** → **Close**
6. All subsequent "Try it out" calls will include a valid bearer token automatically

### JWK rotation

`JwkRotationService` generates an RSA-2048 key pair on startup and rotates every **30 minutes**, retaining up to 3 keys. The overlap window (> 10-minute token TTL) ensures in-flight tokens remain valid across key rotations. The JWK set is published at `/oauth2/jwks`.

### Disabling OAuth2 (development mode)

To run without authentication:

1. **`AuthorizationServerConfig.java`** — comment out `@Configuration` and all `@Bean` annotations.
2. **`JwkRotationService.java`** — comment out `@Service`.
3. **`SecurityConfig.java`** — comment out `authorizationServerFilterChain` and `resourceServerFilterChain`; add a permit-all chain:
   ```java
   @Bean
   public SecurityFilterChain openFilterChain(HttpSecurity http) throws Exception {
       http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
           .csrf(csrf -> csrf.disable());
       return http.build();
   }
   ```
4. **`OpenApiConfig.java`** — remove the `SecurityScheme` and `SecurityRequirement` from `inventoryOpenApi()`.

---

## 7. Observability & Logging

### Health endpoint

Spring Boot Actuator exposes a health check at `/actuator/health`:

```bash
curl http://localhost:8080/actuator/health
# { "status": "UP" }
```

Used by Docker Compose (`healthcheck`) and container orchestrators for liveness/readiness probes.

Exposed Actuator endpoints: `health`, `info`.

---

### Logging architecture

The application uses **SLF4J + Logback** configured via `src/main/resources/logback-spring.xml`. Logs are written to two destinations simultaneously:

| Destination | Format | Notes |
|-------------|--------|-------|
| Console (stdout) | Colored, short timestamp | Human-readable during development |
| File (`logs/inventory-api.log`) | Full timestamp, structured | Persisted; rolling by day and size |

**Log file location:** `logs/inventory-api.log` (relative to the working directory — created automatically on first startup).

**Rolling policy:**
- Rotates daily and when a single file exceeds **100 MB**
- Compressed archives: `logs/inventory-api.log.YYYY-MM-DD.N.gz`
- Retains **30 days** of history, capped at **3 GB** total

**Log levels:**

| Logger | Level | What it captures |
|--------|-------|-----------------|
| `com.nuuly.inventory` | `DEBUG` | All application code — service ops, business errors |
| `org.hibernate.SQL` | `DEBUG` | Every SQL statement (written to file only) |
| `org.springframework.web` | `INFO` | Spring MVC request mapping events |
| Everything else | `INFO` | Spring Boot internals, Hikari, etc. |

---

### Log entry types

**HTTP metrics** — logged by `RequestMetricsFilter` after every request:

```
INFO  [METRICS] GET  /api/inventory              → status=200 duration=63ms
INFO  [METRICS] POST /api/inventory/TEST-SKU-01  → status=200 duration=69ms
WARN  [METRICS] GET  /api/inventory/MISSING       → status=404 duration=5ms
WARN  [METRICS] POST /api/inventory/widget/purchase → status=400 duration=3ms
```

**Business events** — logged by `InventoryService`:

```
DEBUG GET sku=CW-0001-BM-02
DEBUG LIST all → 100 skus
INFO  ADD_STOCK CREATED sku=TEST-SKU-01 qty=+50 total=50
INFO  ADD_STOCK UPDATED sku=TEST-SKU-01 qty=+10 total=60
INFO  PURCHASE SUCCESS sku=TEST-SKU-01 qty=5 remaining=55
WARN  PURCHASE FAILED sku=TEST-SKU-01 qty=9999 reason=INSUFFICIENT_STOCK
WARN  PURCHASE FAILED sku=GHOST qty=1 reason=SKU_NOT_FOUND
```

**Error events** — logged by `GlobalExceptionHandler`:

```
WARN  404 NOT_FOUND: SKU not found: MISSING-SKU
WARN  400 INSUFFICIENT_INVENTORY
WARN  400 VALIDATION_ERROR: quantity must be at least 1
WARN  400 MALFORMED_REQUEST: JSON parse error: ...
```

---

### Viewing the log file

**Live tail:**
```bash
tail -f logs/inventory-api.log
```

**Filter only HTTP metrics:**
```bash
grep '\[METRICS\]' logs/inventory-api.log
```

**Filter only errors and warnings:**
```bash
grep -E '^.{24} (WARN |ERROR)' logs/inventory-api.log
```

**Filter by SKU:**
```bash
grep 'sku=CW-0001-BM-02' logs/inventory-api.log
```

**Docker Compose:**
```bash
docker compose logs -f api
```

---

### H2 Console (dev only)

The H2 in-memory database console is available at `http://localhost:8080/h2-console` during local development.

**Connection settings:**

| Field | Value |
|-------|-------|
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:mem:inventory` |
| User Name | `sa` |
| Password | *(leave blank)* |

After connecting, expand the left-panel tree: **`PUBLIC`** → **`Tables`** → **`INVENTORY`**. You can also run:

```sql
SELECT * FROM inventory LIMIT 10;
```

> The H2 console is disabled outside the local/dev profile and must not be exposed in production.
