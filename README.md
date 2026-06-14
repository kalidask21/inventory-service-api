# Inventory Management API

A RESTful service that tracks product stock by SKU.

---

## Table of Contents

1. [Service Description & Functional Requirements](#1-service-description--functional-requirements)
2. [Tech Stack](#2-tech-stack)
3. [Prerequisites](#3-prerequisites)
4. [Running Locally](#4-running-locally)
5. [API Security](#5-api-security)
6. [Observability & Logging](#6-observability--logging)

---

## 1. Service Description & Functional Requirements

The Inventory Management API tracks product stock keyed by SKU. It exposes four core operations:

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/inventory` | List all inventory items |
| `GET` | `/inventory/{skuId}` | Get current stock for a specific SKU |
| `POST` | `/inventory/{skuId}` | Add stock (additive upsert — creates if absent, adds to existing) |
| `POST` | `/inventory/{skuId}/purchase` | Purchase stock (atomic decrement with sufficiency check) |

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
- Live Swagger UI at `/swagger-ui.html` with OAuth2 authorization support.

**FR-7 — Authentication & authorization**
- All `/inventory` endpoints require a valid JWT bearer token.
- `GET` endpoints require scope `inventory.read`; `POST` endpoints require `inventory.write`.
- Missing token → `401`; valid token with wrong scope → `403`.

**FR-8 — Seed data**
- On normal startup, 100 dummy SKUs are seeded into the in-memory database.
- Seeding is disabled under the `test` profile so integration tests start from a clean DB.

---

## 2. Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.x |
| Web | Spring Web (Spring MVC) |
| Persistence | Spring Data JPA + H2 (in-memory) |
| Validation | Jakarta Bean Validation (`@Valid`, `@Min`, `@NotNull`) |
| Security | Spring Security — OAuth2 Resource Server (JWT validation) + Spring Authorization Server (client-credentials token issuance), RS256 / JWK rotation |
| API Docs | springdoc-openapi 2.x (Swagger UI) |
| Health | Spring Boot Actuator |
| Build | Maven 3.9+ (bundled `./mvnw` wrapper) |
| Container | Docker (multi-stage) + Docker Compose |

---

## 3. Prerequisites

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

No external database or message broker is required — the app uses an embedded H2 in-memory store.

---

## 4. Running Locally

### Option A — Maven (fastest)

```bash
# Build, test, and package
./mvnw clean package

# Run only tests
./mvnw test

# Start the application (http://localhost:8080)
./mvnw spring-boot:run
```

On startup, 100 dummy SKUs are seeded automatically. The application is ready when you see:

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
| `http://localhost:8080/swagger-ui.html` | Interactive API docs (Swagger UI) |
| `http://localhost:8080/actuator/health` | Health check endpoint |
| `http://localhost:8080/h2-console` | H2 database console (dev only) |
| `http://localhost:8080/oauth2/token` | Token issuance endpoint |
| `http://localhost:8080/oauth2/jwks` | Public JWK set |

---

### Quick curl examples

**Get a token** (replace `<client_id>` and `<client_secret>` with your configured credentials):
```bash
TOKEN=$(curl -s -u <client_id>:<client_secret> \
  -d 'grant_type=client_credentials&scope=inventory.read inventory.write' \
  http://localhost:8080/oauth2/token | jq -r .access_token)
```

**List all inventory:**
```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/inventory
```

**Get a specific SKU:**
```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/inventory/SKU-001
```

**Add stock:**
```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"quantity": 50}' \
  http://localhost:8080/inventory/SKU-001
```

**Purchase stock:**
```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"quantity": 5}' \
  http://localhost:8080/inventory/SKU-001/purchase
```

---

## 5. API Security

### Overview

The service implements **OAuth2 client-credentials** security with **JWT validation**. Both the Authorization Server (token issuance) and Resource Server (token validation) are co-hosted in the same application.

| Component | Description |
|-----------|-------------|
| Grant type | `client_credentials` (service-to-service) |
| Token format | RS256-signed JWT |
| Token TTL | 10 minutes |
| Key rotation | Every 30 minutes with a 60-minute overlap window |

### Endpoint access rules

| Endpoint | Required scope | No token | Wrong scope |
|----------|---------------|----------|-------------|
| `GET /inventory` | `inventory.read` | 401 | 403 |
| `GET /inventory/{skuId}` | `inventory.read` | 401 | 403 |
| `POST /inventory/{skuId}` | `inventory.write` | 401 | 403 |
| `POST /inventory/{skuId}/purchase` | `inventory.write` | 401 | 403 |

### Permit-listed endpoints (no token required)

- `/actuator/health`
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/oauth2/token`
- `/oauth2/jwks`

### JWK key rotation

Signing keys rotate every 30 minutes. Retired public keys remain published in the JWKS for a 60-minute overlap window (greater than the 10-minute token TTL), ensuring in-flight tokens remain valid across rotations. Keys are in-memory and reset on restart.

---

### Testing via Swagger UI

1. Open `http://localhost:8080/swagger-ui.html`
2. Click the **Authorize** button (lock icon)
3. Enter your `client_id`, `client_secret`, and select scopes (`inventory.read`, `inventory.write`)
4. Click **Authorize** — Swagger fetches a bearer token automatically
5. Use **Try it out** on any endpoint — the token is attached to every request

### Testing via curl

```bash
# Step 1: Get a token
curl -s -u <client_id>:<client_secret> \
  -d 'grant_type=client_credentials&scope=inventory.read inventory.write' \
  http://localhost:8080/oauth2/token
# Response: { "access_token": "...", "token_type": "Bearer", "expires_in": 600 }

# Step 2: Use the token
curl -H "Authorization: Bearer <access_token>" http://localhost:8080/inventory
```

---

## 6. Observability & Logging

### Health endpoint

Spring Boot Actuator exposes a health check at `/actuator/health`:

```bash
curl http://localhost:8080/actuator/health
# { "status": "UP" }
```

Used by Docker Compose (`healthcheck`) and container orchestrators for liveness/readiness probes.

Exposed Actuator endpoints: `health`, `info`.

### Logging

The application uses Spring Boot's default structured logging (SLF4J + Logback):

- **Startup events:** application context initialization, datasource setup, seed data load
- **Error events:** validation failures, domain exceptions (SKU not found, insufficient inventory), malformed requests
- **Security events:** authentication/authorization failures are logged by Spring Security

Log output goes to stdout (console).

### Viewing logs

**Local (Maven):** logs print directly to the terminal.

**Docker Compose:**
```bash
docker compose logs -f api
```

### H2 Console (dev only)

The H2 in-memory database console is available at `http://localhost:8080/h2-console` during local development.

> The H2 console is disabled outside the local/dev profile and must not be exposed in production.
