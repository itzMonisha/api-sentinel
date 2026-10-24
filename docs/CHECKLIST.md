# API Sentinel — Implementation Master Checklist

This checklist tracks the end-to-end development of API Sentinel across incremental, testable phases. In accordance with production engineering standards, every phase must satisfy the **Definition of Done** before advancing to the next.

---

## Phase 00: Planning, Architecture & Repository Inspection (CURRENT PHASE)
- [x] Inspect workspace and check system dependencies (Java, Maven, Node, Docker, Git).
- [x] Create Product Requirements Document (`docs/REQUIREMENTS.md`).
- [x] Create Technical Architecture & Engineering Guide (`docs/ARCHITECTURE.md`).
- [x] Create Database Design & Schema Specification (`docs/DATABASE_DESIGN.md`).
- [x] Create REST API Contract Specification (`docs/API_CONTRACT.md`).
- [x] Create Master Implementation Checklist (`docs/CHECKLIST.md`).
- [x] Create Project README (`README.md`).
- [ ] User review and approval of Phase 00 deliverables.

---

## Phase 01: Project Scaffolding & Infrastructure (COMPLETED)
- [x] Initialize Spring Boot 3.x project with Maven wrapper (`mvnw`).
- [x] Configure `pom.xml` with compatible stable dependencies:
  - Spring Boot Starter Web, Spring Boot Starter Security, Spring Boot Starter Validation
  - Spring Boot Starter Data JPA, PostgreSQL Driver, Flyway Core, Flyway PostgreSQL
  - JJWT (JSON Web Token 0.12.x)
  - Swagger Parser / OpenAPI v3 Parser (`swagger-parser:2.1.22+`)
  - Springdoc OpenAPI (Swagger UI for backend documentation)
  - Testcontainers (PostgreSQL), JUnit 5, Mockito
- [x] Configure `application.yml`, `application-local.yml`, and `application-test.yml` with externalized environment variables.
- [x] Set up `docker-compose.yml` for PostgreSQL 16 with health check and volume persistence.
- [x] Set up global exception handling with RFC 7807 Problem Details (`@RestControllerAdvice`).
- [x] Implement health probe endpoint `GET /api/v1/health` and restricted Actuator endpoints.
- [x] Implement React 18 + TypeScript + Vite frontend with dark-mode Shell and 7 navigable pages.
- [x] Verify clean compilation and 100% test pass rate (`./mvnw clean test`).

---

## Phase 02: Secure Authentication, Authorization & RBAC (COMPLETED)
- [x] Implement Flyway migration `V2__init_users_and_auth.sql` (Users and Refresh Tokens with UTC timestamps).
- [x] Implement `User` entity, `Role` enum (`ROLE_USER`, `ROLE_ADMIN`), and `UserRepository` with lowercased email uniqueness.
- [x] Implement `RefreshToken` entity and `RefreshTokenRepository` with SHA-256 hashed storage.
- [x] Configure `SecurityConfig` with stateless session management, BCrypt password encoder (cost 12), and public vs protected route rules.
- [x] Implement `JwtTokenProvider` (HMAC-SHA256 signature, 15m expiration, configurable secrets, claim validation).
- [x] Implement `JwtAuthenticationFilter` and `UserPrincipal` for context injection.
- [x] Implement `AuthController` and `AuthService`:
  - `POST /api/v1/auth/register` (DTO validation, password complexity, duplicate email detection).
  - `POST /api/v1/auth/login` (Authentication, disabled account check, issue access + refresh tokens).
  - `POST /api/v1/auth/refresh` (Token rotation, automatic replay/theft detection revoking all sessions).
  - `GET /api/v1/auth/me` (Context-derived user profile, anti-IDOR, no security fields exposed).
  - `POST /api/v1/auth/logout` (Revokes active refresh token).
- [x] Implement `RateLimiterService` (In-memory sliding-window IP limiter returning HTTP 429).
- [x] Document Browser Token Strategy (In-memory access token + HttpOnly SameSite=Strict cookie) and CSRF implications.
- [x] Write comprehensive unit & integration tests (`AuthControllerTest`) verifying all auth flows, invalid credentials, disabled accounts, expired tokens, replay attacks, and cross-user isolation (19/19 tests passing).

---

## Phase 03: Projects, API Endpoints & OpenAPI Ingestion (COMPLETED)
- [x] Implement Flyway migration `V3__init_projects_and_endpoints.sql` (`projects`, `project_members`, `api_endpoints`).
- [x] Implement `Project`, `ProjectMember`, `ProjectRole` (`OWNER`, `EDITOR`, `VIEWER`), and `ApiEndpoint` entities and repositories.
- [x] Implement project ownership checks and BOLA protection (`ProjectSecurityService`) on every resource operation.
- [x] Implement `ProjectService` and `ProjectController` with slug generation, pagination, and search filtering.
- [x] Implement `ApiEndpointService` and `ApiEndpointController`:
  - Endpoint registration with name, URL, HTTP method, description, auth config.
  - Secret masking for auth credentials (tokens never persisted in plaintext).
  - Validation of URLs and standard uppercase HTTP methods.
  - Pagination, search, and HTTP method filtering.
  - Endpoint details and test execution history drawer.
- [x] Implement `OpenApiParserService` and `OpenApiController`:
  - 5MB file size limit and parser complexity cap (max 500 endpoints).
  - Empty specification and malformed syntax rejection with actionable errors.
  - Path, method, parameter, requestBody, and response schema discovery.
  - Preview endpoint before importing (`/api/v1/projects/{projectId}/openapi/preview`).
  - Selective import and duplicate prevention (`overwriteExisting` flag).
  - Safe parsing without executing outbound operations.
- [x] Implement React frontend interfaces:
  - `ProjectsPage.tsx`: Full CRUD modals, search, stats, role badges, and responsive dark-mode cards.
  - `EndpointsPage.tsx`: Method badge filters, endpoint catalog, manual registration modal, schema inspection drawer, and dual-mode OpenAPI upload/paste workflow with preview table.
  - `App.tsx`: Seamless workspace navigation and active project state synchronization.
- [x] Write comprehensive unit & integration tests (`ProjectAndEndpointIntegrationTest`) verifying cross-user access prevention, invalid URLs/methods, malformed/oversized files, duplicate imports, and empty specs (37/37 tests passing).

---

## Phase 04: Zero-Trust Network Defense & SSRF Protection Engine
- [ ] Implement `CidrBlocklist` with bitwise IPv4/IPv6 mask matching algorithms.
- [ ] Implement DNS resolution validator (checks all returned IPs before TCP handshake).
- [ ] Implement `SafeHttpClientFactory` using Apache HttpClient 5 / JDK HttpClient with direct socket IP pinning.
- [ ] Enforce request/response quotas (5s connect timeout, 10s read timeout, 5MB response size limit).
- [ ] Enforce redirect inspection (disabled by default, re-validated per redirect hop if enabled).
- [ ] Implement development mode toggle (`api-sentinel.security.allow-local-targets=true/false`).
- [ ] Write comprehensive unit tests for CIDR matching (loopback, private, link-local, cloud metadata, public IPs).
- [ ] Write integration tests simulating DNS rebinding defense and redirect traps.

---

## Phase 06: Test Suites, Assertions & Execution Engine
- [ ] Implement Flyway migration `V4__init_test_engine.sql`.
- [ ] Implement `TestSuite`, `TestCase`, `TestExecution`, and `TestStepResult` entities and repositories.
- [ ] Implement Assertion Engine:
  - `STATUS_CODE_EQUALS`
  - `RESPONSE_TIME_LESS_THAN`
  - `JSON_PATH_EQUALS` / `JSON_PATH_EXISTS`
  - `HEADER_EQUALS`
- [ ] Implement Variable Extraction & Expression Evaluator for chained requests.
- [ ] Implement DAG Dependency Resolver (Topological Sort / Kahn's Algorithm).
- [ ] Implement Asynchronous `TestRunner` with bounded `ThreadPoolTaskExecutor`.
- [ ] Implement `TestSuiteController` and `TestExecutionController`.
- [ ] Write unit and integration tests executing real HTTP test flows.

---

## Phase 07: Safe Security Scanner
- [ ] Implement Flyway migration `V5__init_security_scanner.sql`.
- [ ] Implement `SecurityScan` and `Vulnerability` entities and repositories.
- [ ] Implement Scanner Rules Engine (non-destructive, read-only checks):
  1. Missing Security Headers (`HSTS`, `CSP`, `X-Content-Type-Options`, `X-Frame-Options`)
  2. Cleartext Transport / Insecure Scheme
  3. Overly Permissive CORS Configurations
  4. Information Leakage & Stack Trace Fingerprinting
  5. Unauthenticated Access to Sensitive Endpoints
  6. Rate Limit Absence Probe
- [ ] Implement asynchronous `ScannerService` with rate limits per target.
- [ ] Implement `ScanController` and vulnerability reporting endpoints.
- [ ] Write unit tests for all scan rules with mock target responses.

---

## Phase 08: Continuous Health Monitoring
- [ ] Implement Flyway migration `V6__init_health_monitoring.sql`.
- [ ] Implement `HealthCheck` and `HealthCheckRecord` entities and repositories.
- [ ] Implement scheduled health probe runner (`@Scheduled` + thread pool).
- [ ] Implement latency percentiles ($p50$, $p95$, $p99$) and rolling uptime percentage calculation.
- [ ] Implement `HealthCheckController` with on-demand ping and historical analytics.
- [ ] Write tests verifying accurate uptime calculation and probe error handling.

---

## Phase 09: Incidents & Automated Alerting
- [ ] Implement Flyway migration `V7__init_incidents_and_audit.sql`.
- [ ] Implement `Incident` entity and state machine validator (`TRIGGERED`, `ACKNOWLEDGED`, `RESOLVED`, `AUTO_RECOVERED`).
- [ ] Implement automated triggers (consecutive health failures, critical scan vulnerabilities).
- [ ] Implement Webhook Alert Dispatcher with exponential backoff.
- [ ] Implement `IncidentController` (`acknowledge`, `resolve`).
- [ ] Write unit tests for state machine transitions and webhook dispatching.

---

## Phase 10: Audit Logging & Real Backend Dashboard
- [ ] Implement `AuditLog` entity, Spring AOP `@Auditable` aspect, and `AuditService`.
- [ ] Implement `DashboardService` with aggregated SQL queries (no fabricated data!).
- [ ] Implement `DashboardController` (`/api/v1/projects/{projectId}/dashboard/summary`).
- [ ] Write tests ensuring dashboard values match exact database records.

---

## Phase 11: Frontend Application (React + TypeScript + Vite)
- [ ] Initialize Vite React + TypeScript project with modern styling tokens and component system.
- [ ] Implement JWT Auth flow (Login, Register, Token Refresh, Protected Routes).
- [ ] Implement Layout: Sidebar, Navbar, Project Selector, Breadcrumbs, User Profile.
- [ ] Implement Project & API Registry management views.
- [ ] Implement OpenAPI Import view with file dropzone and URL fetcher.
- [ ] Implement Discovered Endpoints Catalog with schema viewer.
- [ ] Implement Test Suite Builder & Test Run Results viewer (with step timings and assertions).
- [ ] Implement Security Scanner dashboard with vulnerability findings and remediation guides.
- [ ] Implement Health Monitoring view with real-time latency and uptime charts.
- [ ] Implement Incident Management & Audit Log views.
- [ ] Implement Executive Metrics Dashboard with interactive charts.
- [ ] Ensure all states are handled: Loading skeletons, empty states, error banners, and success feedback.

---

## Phase 12: CI/CD, End-to-End Testing & Final Polish
- [ ] Write Playwright end-to-end tests for core user journeys (Register -> Create Project -> Register API -> Run Health Check -> View Dashboard).
- [ ] Set up GitHub Actions CI workflow (Build, Test, Lint, Security Scan).
- [ ] Final security audit (SSRF review, CORS, headers, auth check).
- [ ] Update documentation, setup instructions, and portfolio presentation guide.
