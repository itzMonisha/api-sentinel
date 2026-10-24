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

## Phase 04: Zero-Trust Network Defense & SSRF Protection Engine (COMPLETED)
- [x] Implement `CidrBlocklist` with bitwise IPv4/IPv6 mask matching algorithms.
- [x] Implement DNS resolution validator (`TargetValidator`) (checks all returned IPs before TCP handshake).
- [x] Implement `SafeHttpClient` with zero-trust network boundary and SSRF protection.
- [x] Enforce request/response quotas (5s connect timeout, 10s read timeout, 5MB response size limit, 2MB request body cap).
- [x] Enforce redirect inspection (disabled by default, re-validated per redirect hop up to 3 hops).
- [x] Implement development mode toggle (`api-sentinel.security.allow-local-targets=true/false`).
- [x] Write comprehensive unit tests for CIDR matching (loopback, private, link-local, cloud metadata, public IPs).
- [x] Write integration tests simulating network defense, redirect traps, and offline failures.

---

## Phase 06: Test Suites, Assertions & Execution Engine (COMPLETED)
- [x] Implement Flyway migration `V4__init_test_engine.sql` (`test_suites`, `test_cases`, `test_runs`, `test_results`).
- [x] Implement `TestSuite`, `TestCase`, `TestRun`, and `TestResult` entities and JPA repositories.
- [x] Implement Assertion Engine supporting 8 core validation checks:
  - `STATUS_CODE_EQUALS` (HTTP status equals expected status)
  - `RESPONSE_TIME_BELOW` (Response time below configured threshold)
  - `HEADER_EXISTS` (Required response header presence)
  - `CONTENT_TYPE_MATCHES` (Content-Type header matches expectation)
  - `JSON_FIELD_EXISTS` (JSON response contains expected field / path)
  - `JSON_FIELD_EQUALS` (JSON field equals expected value)
  - `OPENAPI_SCHEMA_MATCH` (Response conforms to OpenAPI response schema)
  - Error and failure checks (Connection failure, timeout, unexpected server errors)
- [x] Implement `TestSuiteController`, `TestCaseController`, and `TestRunController`:
  - `POST /api/v1/test-suites`
  - CRUD endpoints for test suites and cases (`/api/v1/test-suites/{id}`, `/api/v1/test-cases/{id}`, etc.)
  - `POST /api/v1/test-suites/{id}/runs`
  - `GET /api/v1/test-runs/{id}`
  - `GET /api/v1/test-runs/{id}/results`
  - `GET /api/v1/endpoints/{id}/test-runs`
- [x] Enforce project and user concurrency quotas (`MAX_CONCURRENT_RUNS = 5`).
- [x] Redact sensitive authorization headers, cookies, and secrets.
- [x] Build comprehensive end-to-end integration test (`TestExecutionIntegrationTest`) with real local HTTP server returning 200, 404, delayed latency, and invalid JSON, demonstrating real persisted results and failure detection.

---

## Phase 07: Safe Security Analysis Engine (COMPLETED)
- [x] Implement Flyway migration `V5__init_security_scanner.sql` (`security_scans` and `security_findings`).
- [x] Implement `SecurityScan` and `SecurityFinding` entities and JPA repositories.
- [x] Implement modular `ScannerCheck` engine with 7 read-only checks:
  1. `SecurityHeadersCheck` (`Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options`, `Server`/`X-Powered-By` banner leakage)
  2. `InsecureTransportCheck` (Detects cleartext `http://` transport schemes)
  3. `UnauthenticatedAccessCheck` (Publicly accessible endpoints expected to require auth)
  4. `AuthenticationConsistencyCheck` (Auth configuration inconsistencies and 500 error leak on malformed token probe)
  5. `OpenApiSecurityRequirementCheck` (OpenAPI mutating operations documented without security requirements)
  6. `SensitiveDataExposureCheck` (Conservative heuristics for stack traces, SQL syntax errors, database leakages, and private keys)
  7. `AuthorizationConsistencyCheck` (Broken Function Level Auth verification using owner-supplied low-privilege test account fixture)
- [x] Implement `Sanitizer` utility: automatic redaction of JWTs, AWS credentials, RSA/EC private keys, emails, passwords, and file paths.
- [x] Enforce safety controls:
  - Read-only probes (GET, HEAD, OPTIONS); never execute exploit payloads, brute force, or DoS.
  - Zero-Trust network validation and allowlist enforcement via `SafeHttpClient` and `TargetValidator`.
  - Distinguish `CONFIRMED` observations from `POTENTIAL_RISK`.
- [x] Implement REST endpoints (`SecurityScanController`):
  - `POST /api/v1/scans` and `POST /api/v1/projects/{projectId}/scans`
  - `GET /api/v1/scans/{id}`
  - `GET /api/v1/scans/{id}/findings`
  - `GET /api/v1/projects/{projectId}/scans`
  - `GET /api/v1/endpoints/{id}/security-findings`
- [x] Unit and Integration Tests:
  - `ScannerRulesTest`: 11 unit tests covering positive findings and false-positive resistance across all 7 checks.
  - `SecurityScannerIntegrationTest`: End-to-end integration test with controlled local vulnerable `HttpServer` fixture verifying real database persistence, evidence redaction, and multi-tenant security boundaries.
- [x] Frontend UI: Full-featured `SecurityScansPage.tsx` with scan launch modal (with optional test account fixture), KPI summary cards, scan history list, and granular findings cards with redacted evidence and remediation guidance.

---

## Phase 08 & Phase 09: API Reliability Monitoring, Incidents & Alerting (COMPLETED)
- [x] Implement Flyway migration `V6__init_reliability_monitoring.sql` (`health_checks`, `health_check_records`, `incidents`, `alert_notifications`).
- [x] Implement `HealthCheck`, `HealthCheckRecord`, `Incident`, and `AlertNotification` entities and JPA repositories.
- [x] Implement scheduled health probe runner (`HealthCheckScheduler`) with bounded concurrency worker pool (`Executors.newFixedThreadPool`) and graceful shutdown.
- [x] Implement zero-trust network safety enforcement: all health probes pass outbound URL security policies (`SafeHttpClient`, `TargetValidator`, `CidrBlocklist`), per-user timeouts, and request budgets.
- [x] Implement metric windows & formulas: latency measurements ($p50$, $p95$, $p99$, average), success and failure counts, failure-rate calculation, and insufficient data detection (requires >= 3 persisted records before claiming uptime SLA).
- [x] Implement incident engine with threshold crossing:
  - Incident creation when consecutive failure threshold is crossed.
  - Incident deduplication to prevent duplicate alerts during ongoing outages.
  - Automatic recovery and resolution tracking when consecutive success threshold is reached.
  - Manual operator acknowledge and resolve workflows with root cause logging.
- [x] Implement In-App Alert Center and alert delivery interface (`AlertNotificationService`, `DefaultAlertNotificationService`) with safe development dispatching and optional email hook.
- [x] Implement REST controller endpoints (`ReliabilityMonitoringController`):
  - `POST /api/v1/projects/{projectId}/health-checks`
  - `GET /api/v1/projects/{projectId}/health-checks`
  - `GET /api/v1/health-checks/{id}`
  - `PUT /api/v1/health-checks/{id}`
  - `DELETE /api/v1/health-checks/{id}`
  - `POST /api/v1/health-checks/{id}/trigger`
  - `GET /api/v1/health-checks/{id}/records`
  - `GET /api/v1/projects/{projectId}/incidents`
  - `GET /api/v1/incidents/{id}`
  - `POST /api/v1/incidents/{id}/acknowledge`
  - `POST /api/v1/incidents/{id}/resolve`
  - `GET /api/v1/projects/{projectId}/alerts`
  - `POST /api/v1/alerts/{id}/read`
  - `GET /api/v1/projects/{projectId}/dashboard/summary`
- [x] Comprehensive unit and integration tests:
  - `ReliabilityMonitoringUnitTest`: 6 tests verifying metric math, zero denominators, insufficient data detection, threshold crossing, deduplication, and recovery.
  - `ReliabilityMonitoringIntegrationTest`: 4 integration tests verifying dynamic failing local test endpoints creating incidents, deduplication, auto-recovery on healthy probe, manual acknowledge/resolve, bounded scheduler execution, and cross-project authorization.
- [x] Frontend UI: Full-featured `IncidentsPage.tsx` with KPI overview cards, status/severity filters, incident state actions, health check scheduler configuration modal, probe trigger with immediate feedback, execution history drawer, and in-app alert center feed.

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
