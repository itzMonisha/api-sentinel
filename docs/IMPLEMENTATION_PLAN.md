# API Sentinel — Phased Implementation Plan & Complexity Review

## 1. Executive Implementation Roadmap

This implementation plan is architected specifically for **one student engineer targeting top SDE internships**. It prioritizes deep engineering quality (zero-trust network security, clean modular monolith architecture, real-data observability, automated test coverage) over superficial surface area.

```mermaid
gantt
    title API Sentinel — Phased Engineering Roadmap
    dateFormat  X
    axisFormat  Day %d

    section Foundation
    Phase 00: Architecture & Threat Model (Current) :done, p0, 0, 1
    Phase 01: Scaffolding, Spring Boot 3 & Docker   :active, p1, 1, 2
    Phase 02: Zero-Trust Outbound Engine (Anti-SSRF):p2, 2, 4

    section Core Domain
    Phase 03: Auth, RBAC & Stateless JWT            :p3, 4, 6
    Phase 04: Projects & API Registry               :p4, 6, 8
    Phase 05: OpenAPI 3.x Import & Discovery        :p5, 8, 10

    section Engine & Reliability
    Phase 06: Test Suites & Chained Execution Engine:p6, 10, 13
    Phase 07: Safe Security Scanner (OWASP API Top) :p7, 13, 16
    Phase 08: Health Monitoring & Real SLA Probes   :p8, 16, 18
    Phase 09: Incidents (FSM), Audit & Dashboard    :p9, 18, 21

    section Frontend & Verification
    Phase 10: Modern React + TypeScript + Vite UI   :p10, 21, 25
    Phase 11: End-to-End Testing (Playwright) & CI  :p11, 25, 27
```

---

## 2. Complexity Review: Keeping the MVP Achievable for One Student

Before writing code, senior engineers conduct a **Complexity Review** to prune unnecessary overhead while preserving production integrity:

| Architectural Choice | Decision | Why This Keeps the Project Achievable for One Student |
|---|---|---|
| **Architecture Pattern** | **Modular Monolith** over Microservices | Eliminates distributed transaction rollbacks (Saga), API gateway routing bugs, separate deployment pipelines, and multi-repo friction. Everything runs locally in one command. |
| **Identity Provider** | **Native JWT + BCrypt** over External Keycloak/Auth0 | Eliminates running an external identity server in Docker (saving 1GB+ RAM), simplifies CI/CD testing, and allows full interview discussion of password hashing, token claims, and refresh rotation. |
| **Async Execution** | **Spring `ThreadPoolTaskExecutor`** over Kafka / RabbitMQ | Handles asynchronous execution with bounded in-memory queues and rejection policies. Eliminates running and configuring an external message broker. |
| **Frontend Stack** | **Vite + React 18 + TypeScript + Modern CSS Tokens** | Fast HMR (hot module replacement), zero build configuration headaches, and complete UI aesthetic control without fighting Tailwind class conflicts. |
| **Database Migrations** | **Flyway** with clean SQL migrations | Version-controlled, reproducible schema changes that run automatically on application startup. |

---

## 3. Phase-by-Phase Execution Plan & Acceptance Criteria

---

### Phase 00: Planning, Architecture & Repository Inspection (CURRENT)
- **Objective**: Establish complete engineering requirements, architectural design, database schemas, threat models, testing strategy, and implementation roadmap.
- **Dependencies**: None.
- **Changed Files**:
  - `docs/REQUIREMENTS.md`
  - `docs/ARCHITECTURE.md`
  - `docs/DATABASE_DESIGN.md`
  - `docs/API_CONTRACT.md`
  - `docs/SECURITY_THREAT_MODEL.md`
  - `docs/IMPLEMENTATION_PLAN.md`
  - `docs/TESTING_STRATEGY.md`
  - `README.md`
- **Risks**: Over-engineering or creating unrealistic requirements that stall implementation.
- **Mitigation**: Strictly prune non-essential features into "Future Enhancements" (see Section 2).
- **Acceptance Criteria**:
  1. All 7 architecture documents created and verified.
  2. Entity model covers all 12 core domains.
  3. SSRF zero-trust policy defined with explicit CIDR blocklists and anti-DNS rebinding algorithms.
  4. User reviews and gives approval before proceeding to Phase 01.

---

### Phase 01: Project Scaffolding & Infrastructure
- **Objective**: Establish the Maven project structure, Maven Wrapper (`mvnw`), Spring Boot 3.3.x baseline, dependency management, Docker Compose for PostgreSQL 16, and base application properties.
- **Dependencies**: Phase 00.
- **Technical Deliverables**:
  - `backend/pom.xml`: Spring Boot Starter Web, Security, Validation, Data JPA, PostgreSQL Driver, Flyway, JJWT, Swagger Parser, Testcontainers, JUnit 5.
  - `mvnw` & `.mvn/wrapper/`: Committed Maven wrapper.
  - `docker-compose.yml`: PostgreSQL 16 container definition with health check and volume persistence.
  - `backend/src/main/resources/application.yml`: Externalized environment variables.
  - Global RFC 7807 Exception Handler (`@RestControllerAdvice`).
- **Risks**: Dependency version conflicts between Spring Boot 3.3, JJWT 0.12, and Flyway.
- **Mitigation**: Pin exact compatible dependency versions tested against Spring Boot 3.3.
- **Acceptance Criteria**:
  1. `./mvnw clean compile` succeeds with zero errors.
  2. Spring Boot application starts cleanly and exposes actuator/health.
  3. `docker-compose up -d` starts PostgreSQL 16 on port 5432.
  4. Baseline unit test executes and passes.

---

### Phase 02: Zero-Trust Outbound Engine (Anti-SSRF & DNS Pinning)
- **Objective**: Build the core security foundation used by all scanning, testing, and health-probing components.
- **Dependencies**: Phase 01.
- **Technical Deliverables**:
  - `CidrBlocklist.java`: $O(1)$ bitwise IPv4/IPv6 mask matcher blocking RFC 1918, RFC 3927, RFC 4193, loopback, and cloud metadata (`169.254.169.254`).
  - `PreResolutionDnsResolver.java`: Queries all IP addresses for a host before connection.
  - `SafeHttpClientFactory.java`: HTTP client that pins the TCP socket to the validated IP, preserving HTTP Host header and TLS SNI.
  - Quota enforcement: 5s connect timeout, 10s read timeout, 5MB response size limit.
  - Redirect guard: Disabled by default; re-validates each hop through SSRF pipeline if enabled.
  - `api-sentinel.security.allow-local-targets=false`: Config flag for safe development toggle.
- **Risks**: TLS certificate hostname validation failure when connecting to an IP address directly.
- **Mitigation**: Configure the HTTP client SSL socket factory with `SNIHostName` set to the original domain name, ensuring valid TLS verification while preventing DNS rebinding.
- **Acceptance Criteria**:
  1. Unit tests pass verifying blocked CIDRs: `127.0.0.1`, `10.0.0.1`, `172.16.0.1`, `192.168.1.1`, `169.254.169.254`, `::1`.
  2. Unit tests pass verifying public IPs are allowed (`93.184.216.34`, `8.8.8.8`).
  3. Tests verify redirect traps to private IPs are intercepted and blocked.
  4. Tests verify streams > 5MB are terminated with `RESPONSE_SIZE_EXCEEDED`.

---

### Phase 03: Authentication, RBAC & Stateless JWT
- **Objective**: Implement user registration, login, JWT token generation, refresh token rotation, and Spring Security configuration.
- **Dependencies**: Phase 01, Phase 02.
- **Technical Deliverables**:
  - Flyway migration `V1__init_users_and_auth.sql`: `users`, `refresh_tokens`.
  - `User.java`, `Role.java`, `RefreshToken.java`, `UserRepository`, `RefreshTokenRepository`.
  - `JwtTokenProvider.java`: Generates signed JWTs with expiration and subject claims.
  - `JwtAuthFilter.java`: Validates Bearer token on inbound requests and sets `SecurityContext`.
  - `SecurityConfig.java`: Stateless session, BCrypt encoder (cost 12), route permit rules.
  - `AuthController.java`: `/api/v1/auth/register`, `/login`, `/refresh`, `/me`, `/logout`.
- **Risks**: Storing plain refresh tokens or suffering token replay attacks.
- **Mitigation**: Store SHA-256 hashed refresh tokens. Revoke immediately on rotation.
- **Acceptance Criteria**:
  1. Successful registration returns 201 with sanitized User DTO (no password hash).
  2. Login with valid credentials returns 200 with access token and refresh token.
  3. Login with invalid password returns 401 Unauthorized (RFC 7807 error format).
  4. Protected endpoint returns 401 without Bearer token and 200 with valid token.
  5. Refresh endpoint rotates the token and invalidates the previous refresh token.

---

### Phase 04: Projects & API Registry Module
- **Objective**: Implement multi-tenant workspaces, project membership RBAC, and API service registration.
- **Dependencies**: Phase 03.
- **Technical Deliverables**:
  - Flyway migration `V2__init_projects_and_services.sql`: `projects`, `project_members`, `api_services`.
  - `Project.java`, `ProjectMember.java`, `ApiService.java` and repositories.
  - `ProjectService.java` with automatic slugification and owner assignment.
  - `ProjectSecurity.java` `@PreAuthorize` evaluator for `OWNER`, `EDITOR`, `VIEWER`.
  - `ProjectController.java` and `ApiServiceController.java`.
- **Risks**: Broken Object-Level Authorization (BOLA) allowing User A to query User B's project.
- **Mitigation**: All repository queries enforce user membership join.
- **Acceptance Criteria**:
  1. User can create a project; creator is automatically assigned `OWNER`.
  2. Non-member receives 403 Forbidden when attempting to access project APIs.
  3. `VIEWER` receives 403 when attempting to register an API service.
  4. `EDITOR` can register an API service; target URL is validated against SSRF rules.

---

### Phase 05: OpenAPI 3.x Import & Endpoint Discovery
- **Objective**: Ingest OpenAPI specifications (JSON/YAML) via file upload and URL fetch, catalog endpoints, and detect schema drift.
- **Dependencies**: Phase 02, Phase 04.
- **Technical Deliverables**:
  - Flyway migration `V3__init_api_endpoints.sql`: `api_endpoints`, `openapi_specs`.
  - `OpenApiParserService.java`: Safe parsing via `swagger-parser` with entity expansion disabled.
  - `EndpointDiscoveryService.java`: Extracts paths, methods, schemas, parameters into `ApiEndpoint` entities.
  - `DiffEngine.java`: Compares existing endpoints against new spec to tag added, modified, and deprecated endpoints.
  - `OpenApiController.java`: Upload and URL fetch endpoints.
- **Risks**: Parsing malicious OpenAPI files with circular references causing StackOverflowError.
- **Mitigation**: Enforce maximum recursion depth of 10 and limit file size to 5MB.
- **Acceptance Criteria**:
  1. Uploading a valid OpenAPI 3.0/3.1 JSON/YAML file creates `ApiEndpoint` records in database.
  2. Importing via URL uses `SafeHttpClient` and rejects private/loopback URLs with 400.
  3. Re-importing an updated spec identifies new and deprecated endpoints accurately.

---

### Phase 06: Test Suites & Chained Execution Engine
- **Objective**: Build test suite creation, assertion engine, variable extraction, and asynchronous execution runner.
- **Dependencies**: Phase 02, Phase 05.
- **Technical Deliverables**:
  - Flyway migration `V4__init_test_engine.sql`: `test_suites`, `test_cases`, `test_runs`, `test_results`.
  - `AssertionEngine.java`: Evaluates `STATUS_CODE`, `RESPONSE_TIME_MS`, `JSON_PATH`, `HEADER`.
  - `VariableExtractor.java`: Extracts JSONPath values (`$.data.token`) into runtime execution context.
  - `TestRunnerService.java`: Asynchronous execution via dedicated thread pool, recording each `TestResult`.
  - `TestSuiteController.java` and `TestRunController.java`.
- **Risks**: Test step execution blocking main application threads or hanging indefinitely on slow APIs.
- **Mitigation**: Runs in isolated `ThreadPoolTaskExecutor`; each step enforces 10s read timeout.
- **Acceptance Criteria**:
  1. Create a test suite with 2 chained test cases (e.g., Step 1 logs in $\rightarrow$ Step 2 uses extracted token).
  2. Trigger test run asynchronously; status transitions `QUEUED` $\rightarrow$ `RUNNING` $\rightarrow$ `PASSED`/`FAILED`.
  3. `TestResult` records exact status code, response time (ms), and assertion evaluations.
  4. Sensitive headers (`Authorization`, `Cookie`) are masked in stored dumps.

---

### Phase 07: Safe Security Scanner
- **Objective**: Implement automated, non-destructive security vulnerability scanning against registered APIs.
- **Dependencies**: Phase 02, Phase 05.
- **Technical Deliverables**:
  - Flyway migration `V5__init_security_scanner.sql`: `security_scans`, `security_findings`.
  - `SecurityRule.java` interface with 6 standard safe rules:
    1. `MissingHstsRule`: Verifies `Strict-Transport-Security`.
    2. `MissingCspRule`: Verifies `Content-Security-Policy`.
    3. `PermissiveCorsRule`: Detects `Access-Control-Allow-Origin: *` with credentials.
    4. `StackTraceLeakageRule`: Detects Spring Boot whitelabel or database exception dumps.
    5. `UnauthenticatedAccessRule`: Verifies protected endpoints return 401/403.
    6. `RateLimitPresenceRule`: Probes for `RateLimit-*` or `Retry-After` headers.
  - `ScannerService.java`: Non-destructive (GET/HEAD only), rate-limited execution.
  - `ScanController.java`.
- **Risks**: Scanning third-party infrastructure without authorization or modifying live database state.
- **Mitigation**: Strict allowlist verification, read-only HTTP methods, and rate limits.
- **Acceptance Criteria**:
  1. Triggering a scan runs all active rules against discovered endpoints.
  2. Discovered vulnerabilities create `SecurityFinding` records tagged with CVSS severity and remediation.
  3. Scanner strictly uses `GET`, `HEAD`, or `OPTIONS` methods.

---

### Phase 08: Health Monitoring & Real SLA Probes
- **Objective**: Implement periodic health checks, latency tracking, and rolling SLA percentiles.
- **Dependencies**: Phase 02, Phase 04.
- **Technical Deliverables**:
  - Flyway migration `V6__init_health_monitoring.sql`: `health_checks`, `health_check_records`.
  - `HealthProbeScheduler.java`: `@Scheduled` runner executing active probes at configured intervals.
  - `SlaCalculatorService.java`: Calculates 24h, 7d, 30d uptime percentages and $p50$, $p95$, $p99$ latencies.
  - `HealthCheckController.java` with on-demand ping and historical chart data.
- **Risks**: Overfilling `health_check_records` table with high-frequency pings.
- **Mitigation**: Uses `BIGSERIAL` PK, indexed `(health_check_id, checked_at DESC)`, and clean partition retention design.
- **Acceptance Criteria**:
  1. Scheduled probe pings target URL and inserts a `HealthCheckRecord`.
  2. Consecutive failures update `health_checks.consecutive_failures` and status (`UP` $\rightarrow$ `DOWN`).
  3. Historical query returns calculated $p50, p95, p99$ response times computed from database rows.

---

### Phase 09: Incidents, Audit Logging & Real Backend Dashboard
- **Objective**: Implement automated incident lifecycle (FSM), immutable audit log stream, and 100% backend-calculated executive metrics.
- **Dependencies**: Phase 06, Phase 07, Phase 08.
- **Technical Deliverables**:
  - Flyway migration `V7__init_incidents_and_audit.sql`: `incidents`, `audit_logs`.
  - `IncidentService.java`: Automatic creation on 3 consecutive probe failures or CRITICAL scan finding; FSM state transitions (`TRIGGERED` $\rightarrow$ `ACKNOWLEDGED` $\rightarrow$ `RESOLVED`).
  - `AuditAspect.java`: Spring AOP `@Auditable` capturing actor, action, timestamp, and entity diffs.
  - `DashboardService.java`: Direct SQL aggregate queries for executive metrics.
  - `IncidentController.java`, `AuditController.java`, `DashboardController.java`.
- **Risks**: Inconsistent dashboard numbers or mock data creeping into endpoints.
- **Mitigation**: Zero-fake-data policy verified by automated test assertions comparing SQL counts to API responses.
- **Acceptance Criteria**:
  1. 3 probe failures trigger an `Incident` in `TRIGGERED` status.
  2. Editor can acknowledge and resolve incident; transitions are verified by FSM.
  3. Actions emit immutable `AuditLog` rows.
  4. Dashboard summary endpoint returns live calculated counts matching database tables.

---

### Phase 10: Modern React + TypeScript + Vite Frontend Implementation
- **Objective**: Build the user interface with modern typography, glassmorphism, responsive navigation, and complete UI states.
- **Dependencies**: Phase 03 through Phase 09.
- **Technical Deliverables**:
  - React 18 + TypeScript + Vite project initialized with clean CSS design tokens.
  - Auth flows: Login, Register, Protected Route guard, JWT refresh interceptor.
  - Views: Project Registry, OpenAPI Ingestion, Endpoint Catalog, Test Suite Builder, Test Run Results, Security Scanner Findings, Health Monitor Charts, Incident Tracker, Audit Stream, Executive Dashboard.
  - Interactive charts via Recharts (Uptime trends, latency percentiles, severity breakdown).
  - Complete state coverage: Loading skeletons, empty states with actions, error banners, success toasts.
- **Risks**: UI showing placeholder or disconnected pages.
- **Mitigation**: Every view connects directly to live backend REST endpoints.
- **Acceptance Criteria**:
  1. End-to-end user journey functions in browser: Register $\rightarrow$ Create Project $\rightarrow$ Import Spec $\rightarrow$ Run Tests $\rightarrow$ View Dashboard.
  2. Empty and error states render gracefully without console errors.

---

### Phase 11: End-to-End Testing (Playwright), GitHub Actions & Documentation
- **Objective**: Automate end-to-end browser tests, set up GitHub Actions CI workflow, and finalize documentation.
- **Dependencies**: Phase 10.
- **Technical Deliverables**:
  - Playwright test suite (`e2e/`) testing user authentication, project creation, and test execution.
  - `.github/workflows/ci.yml`: Automated build, test, and lint check.
  - Final project documentation updates.
- **Acceptance Criteria**:
  1. Playwright tests execute and pass headlessly.
  2. GitHub Actions workflow compiles and passes all unit and integration tests.
  3. README updated with complete setup instructions and interview talking points.
