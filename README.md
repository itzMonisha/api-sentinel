# API Sentinel 🛡️

> **Production-Grade API Security, Reliability, and Observability Platform**  
> Built with Java 21, Spring Boot 3.x, PostgreSQL, Flyway, and React + TypeScript + Vite.

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3+-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21_LTS-orange.svg?logo=openjdk)](https://adoptium.net)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16+-blue.svg?logo=postgresql)](https://www.postgresql.org/)
[![React](https://img.shields.io/badge/React-18_TypeScript-61DAFB.svg?logo=react)](https://react.dev/)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

---

## 📌 Overview

**API Sentinel** is an enterprise-caliber API governance, security, and observability platform. Modern backend teams manage hundreds of distributed REST APIs without centralized visibility into schema drift, security vulnerabilities, or silent SLA degradation. API Sentinel solves this by providing:

1. **API Registry & Workspaces**: Multi-tenant isolation with strict Role-Based Access Control (RBAC).
2. **OpenAPI 3.0 / 3.1 Ingestion**: Automated endpoint discovery and schema drift diff engine.
3. **Automated Integration Testing**: Chained request execution using Topological Sort (DAG) with variable extraction and JSONPath assertions.
4. **Zero-Trust Security Scanner**: Non-destructive vulnerability assessment against the OWASP API Security Top 10 (HSTS, CSP, CORS, info leakage, auth bypass, rate limiting).
5. **Zero-Trust SSRF Defense Engine**: Enterprise-grade outbound HTTP client featuring $O(1)$ bitwise CIDR blocklist matching, pre-connection DNS validation, direct socket IP pinning, and redirect inspection to prevent Server-Side Request Forgery and DNS Rebinding attacks.
6. **Continuous SLA & Health Monitoring**: Rolling uptime tracking and latency percentiles ($p50$, $p95$, $p99$).
7. **Incident Management & Audit Trails**: Finite State Machine (FSM) incident lifecycle with automated recovery and webhook alerting.
8. **Real-Data Dashboard**: 100% backend-computed statistics — strictly zero fabricated or mocked metrics.

---

## 🏗️ Architecture & Technical Highlights

```
+-----------------------------------------------------------------------------------+
|                            React 18 + TypeScript Frontend                         |
|             (Vite, Modern CSS Design System, Recharts, Lucide Icons)              |
+------------------------------------------+----------------------------------------+
                                           |  REST API / JWT Bearer
                                           v
+-----------------------------------------------------------------------------------+
|                                API Sentinel Core                                  |
|                      (Spring Boot 3.x Modular Monolith)                           |
|                                                                                   |
|  [Auth & RBAC]         [Projects & Registry]     [OpenAPI Discovery]              |
|  [Test Runner Engine]  [Safe Security Scanner]   [Health SLA Monitor]             |
|  [Incident Lifecycle]  [Audit Stream]            [Real-Time Analytics]            |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  |           Zero-Trust SSRF Defense & Outbound HTTP Engine                    |  |
|  | - RFC 1918 / 3927 / 4193 CIDR Bitmask Filter ($O(1)$)                       |  |
|  | - Pre-Connect DNS Resolution & Socket Pinning (Anti-DNS Rebinding)          |  |
|  | - Per-Hop Redirect Validation & Stream Quotas (5MB max)                     |  |
|  +-----------------------------------------------------------------------------+  |
+------------------------------------------+----------------------------------------+
                                           |  HikariCP / Flyway Migrations
                                           v
+-----------------------------------------------------------------------------------+
|                         PostgreSQL 16+ Relational Database                        |
+-----------------------------------------------------------------------------------+
```

---

## 📚 Engineering Documentation

Detailed engineering specifications are available in the [`docs/`](./docs) directory:

- 📋 [**Product Requirements Document (PRD)**](./docs/REQUIREMENTS.md): User personas, functional requirements, and security invariants.
- 🏛️ [**Technical Architecture & Interview Guide**](./docs/ARCHITECTURE.md): Design decisions, SSRF defense engine, algorithms, data structures, and SDE interview questions.
- 🗄️ [**Database Design & Schema Specification**](./docs/DATABASE_DESIGN.md): Relational schemas, indexing strategies, audit fields, and Flyway migration roadmap.
- 🔌 [**REST API Contract Specification**](./docs/API_CONTRACT.md): Standard request/response envelopes, RFC 7807 problem details, and endpoint catalogs.
- 🚀 [**Production Deployment & Operations Guide**](./docs/DEPLOYMENT.md): 12-factor configuration, Docker container specs, cloud hosting guides (Render, Railway, Fly.io, AWS ECS+RDS), zero-trust egress review, Flyway startup validation, backup runbooks, and verified test results.
- ✅ [**Implementation Master Checklist**](./docs/CHECKLIST.md): Phased roadmap from Phase 00 through Phase 12.

---

## 🛠️ Technology Stack

| Component | Technology | Version / Specification |
|---|---|---|
| **Runtime & Language** | Java (OpenJDK) | 21 LTS |
| **Framework** | Spring Boot | 3.3.x / 3.4.x |
| **Security** | Spring Security & JJWT | Stateless JWT + Refresh Token Rotation, BCrypt |
| **Database** | PostgreSQL | 16+ with Flyway versioned migrations |
| **Outbound HTTP** | Apache HttpClient 5 / Java 21 HTTP | Custom Zero-Trust SSRF & DNS Pinning Engine |
| **OpenAPI Parser** | Swagger Parser | OpenAPI 3.0.x and 3.1.x support |
| **Frontend** | React, TypeScript, Vite | Modern responsive design, zero plain Tailwind, Recharts |
| **Testing** | JUnit 5, Mockito, Testcontainers | Unit, Integration, and End-to-End |
| **Containerization**| Docker & Docker Compose | Multi-container development & production setups |

---

## 🚀 Getting Started (Phase 01 Completed)

### Prerequisites
- **Java 21 LTS** (Installed in `C:\Users\monis\.jdks\jdk-21.0.12.1+1` or system PATH)
- **Node.js 20+** & **npm 10+** (Detected: Node v24, npm 11)
- **Docker Compose** or local PostgreSQL 16 (Optional: application defaults to test/local profiles)

---

### 1. Start the PostgreSQL Database (Docker)
```bash
docker compose up -d
```
*Note: If Docker is not running, the application can boot using the `-Dspring.profiles.active=test` profile with embedded in-memory database.*

---

### 2. Start the Spring Boot Backend (Port 8080)
```bash
# Set JAVA_HOME if needed
$env:JAVA_HOME="C:\Users\monis\.jdks\jdk-21.0.12.1+1"

# Run tests
cd backend
.\mvnw.cmd test "-Dspring.profiles.active=test"

# Run application
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=test"
```
The API is available at:
- Health probe: `http://localhost:8080/api/v1/health`
- Actuator: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

---

### 3. Start the React Frontend (Port 5173)
```bash
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173` in your browser. The application shell will connect to the live backend telemetry.

---

### 4. Build Production Artifacts
```bash
# Backend JAR package
cd backend
.\mvnw.cmd clean package -DskipTests

# Frontend static build
cd frontend
npm run build
```

---

### 5. Production Docker Compose Deployment
```bash
# Start all production containers (PostgreSQL 16, Spring Boot Backend, Nginx SPA Frontend)
docker compose -f docker-compose.prod.yml up -d --build
```
For complete cloud hosting instructions (Render, Railway, Fly.io, AWS ECS+RDS), Let's Encrypt TLS setup via Caddy, backup/restore runbooks, and zero-trust egress review, consult [**`docs/DEPLOYMENT.md`**](./docs/DEPLOYMENT.md).

