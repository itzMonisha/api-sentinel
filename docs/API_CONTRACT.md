# API Sentinel — REST API Contract Specification

## 1. Global Standards & Envelope Formats

### 1.1 Base URL & Versioning
- Base URL: `/api/v1`
- Content Type: `application/json; charset=UTF-8`
- Timestamps: ISO-8601 UTC formatted strings (`YYYY-MM-DDTHH:mm:ss.sssZ`).

### 1.2 Authentication Header
All protected endpoints require a valid Bearer JWT:
```http
Authorization: Bearer <JWT_ACCESS_TOKEN>
```

### 1.3 Authorization Matrix
- **Public**: Unauthenticated endpoints (Register, Login, Token Refresh).
- **Authenticated (`ROLE_USER`, `ROLE_ADMIN`)**: Basic platform access.
- **Project Roles**:
  - `OWNER`: Full project control, member invitation, project deletion.
  - `EDITOR`: Register APIs, import OpenAPI specs, create test suites, run scans, acknowledge/resolve incidents.
  - `VIEWER`: Read-only access to APIs, test results, vulnerability findings, incidents, and dashboards.

### 1.4 Standard Success Response Format
```json
{
  "success": true,
  "data": { ... },
  "message": "Operation completed successfully",
  "timestamp": "2026-10-24T03:30:00.000Z"
}
```

### 1.5 Standard Paginated Response Format
```json
{
  "success": true,
  "data": {
    "items": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 156,
    "totalPages": 8,
    "hasMore": true
  },
  "timestamp": "2026-10-24T03:30:00.000Z"
}
```

### 1.6 Standard Error Response (RFC 7807 Problem Details)
```json
{
  "success": false,
  "error": {
    "type": "https://api-sentinel.dev/errors/validation-failed",
    "title": "Validation Failed",
    "status": 400,
    "detail": "Field validation failed on one or more attributes",
    "errorCode": "VALIDATION_ERROR",
    "instance": "/api/v1/services",
    "fieldErrors": [
      {
        "field": "baseUrl",
        "message": "Must be a valid HTTP or HTTPS URL"
      }
    ]
  },
  "timestamp": "2026-10-24T03:30:00.000Z"
}
```

---

## 2. API Endpoint Catalog

### 2.1 Authentication Module (`/api/v1/auth`)

| Method | Path | Auth / Role | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | Register new user account |
| `POST` | `/api/v1/auth/login` | Public | Authenticate user & return Access + Refresh token |
| `POST` | `/api/v1/auth/refresh` | Public | Rotate refresh token & return new Access token |
| `GET` | `/api/v1/auth/me` | Bearer | Get authenticated user profile & permissions |
| `POST` | `/api/v1/auth/logout` | Bearer | Revoke active refresh token |

---

### 2.2 Projects & Registry Module (`/api/v1/projects`, `/api/v1/services`)

| Method | Path | Auth / Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/projects` | Bearer | List projects where user is a member (paginated) |
| `POST` | `/api/v1/projects` | Bearer | Create a new project workspace |
| `GET` | `/api/v1/projects/{projectId}` | Project VIEWER+ | Get project details by ID or Slug |
| `PUT` | `/api/v1/projects/{projectId}` | Project OWNER | Update project details |
| `DELETE` | `/api/v1/projects/{projectId}` | Project OWNER | Soft delete project |
| `GET` | `/api/v1/projects/{projectId}/members` | Project VIEWER+ | List project team members |
| `POST` | `/api/v1/projects/{projectId}/members` | Project OWNER | Add member to project |
| `GET` | `/api/v1/projects/{projectId}/services` | Project VIEWER+ | List registered API services under project |
| `POST` | `/api/v1/projects/{projectId}/services` | Project EDITOR+ | Register a new API service |
| `GET` | `/api/v1/services/{serviceId}` | Project VIEWER+ | Get API service details |
| `PUT` | `/api/v1/services/{serviceId}` | Project EDITOR+ | Update API service settings |
| `DELETE` | `/api/v1/services/{serviceId}` | Project OWNER | Soft delete API service |

---

### 2.3 OpenAPI Discovery & ApiEndpoints (`/api/v1/services/{serviceId}`)

| Method | Path | Auth / Role | Description |
|---|---|---|---|
| `POST` | `/api/v1/services/{serviceId}/specs/upload` | Project EDITOR+ | Import spec via multipart file upload (JSON/YAML) |
| `POST` | `/api/v1/services/{serviceId}/specs/fetch` | Project EDITOR+ | Import spec via URL (with SSRF validation) |
| `GET` | `/api/v1/services/{serviceId}/specs/history` | Project VIEWER+ | List specification import history & drift diffs |
| `GET` | `/api/v1/services/{serviceId}/endpoints` | Project VIEWER+ | List discovered `ApiEndpoint` entities (filtered) |
| `GET` | `/api/v1/endpoints/{endpointId}` | Project VIEWER+ | Get full `ApiEndpoint` schemas and metadata |

---

### 2.4 Automated Test Suites, TestRuns & TestResults

| Method | Path | Auth / Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/projects/{projectId}/test-suites` | Project VIEWER+ | List test suites in project |
| `POST` | `/api/v1/projects/{projectId}/test-suites` | Project EDITOR+ | Create a new test suite |
| `GET` | `/api/v1/test-suites/{suiteId}` | Project VIEWER+ | Get test suite details & cases |
| `POST` | `/api/v1/test-suites/{suiteId}/cases` | Project EDITOR+ | Add a `TestCase` with assertions & extractors |
| `PUT` | `/api/v1/test-cases/{caseId}` | Project EDITOR+ | Update a `TestCase` definition |
| `DELETE` | `/api/v1/test-cases/{caseId}` | Project EDITOR+ | Delete a `TestCase` |
| `POST` | `/api/v1/test-suites/{suiteId}/runs` | Project EDITOR+ | Trigger asynchronous `TestRun` execution |
| `GET` | `/api/v1/test-runs/{runId}` | Project VIEWER+ | Get `TestRun` status and summary counts |
| `GET` | `/api/v1/test-runs/{runId}/results` | Project VIEWER+ | Get detailed `TestResult` items for each step |
| `GET` | `/api/v1/test-suites/{suiteId}/runs` | Project VIEWER+ | Get historical `TestRun` list |

---

### 2.5 Security Scans & SecurityFindings

| Method | Path | Auth / Role | Description |
|---|---|---|---|
| `POST` | `/api/v1/services/{serviceId}/scans` | Project EDITOR+ | Launch safe automated `SecurityScan` |
| `GET` | `/api/v1/services/{serviceId}/scans` | Project VIEWER+ | List scan history for an API service |
| `GET` | `/api/v1/scans/{scanId}` | Project VIEWER+ | Get `SecurityScan` progress & severity breakdown |
| `GET` | `/api/v1/scans/{scanId}/findings` | Project VIEWER+ | List `SecurityFinding` items with remediation |

---

### 2.6 HealthChecks & Telemetry

| Method | Path | Auth / Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/services/{serviceId}/health-checks` | Project VIEWER+ | List configured `HealthCheck` probes |
| `POST` | `/api/v1/services/{serviceId}/health-checks` | Project EDITOR+ | Configure new periodic `HealthCheck` probe |
| `GET` | `/api/v1/health-checks/{checkId}/history` | Project VIEWER+ | Get latency records & uptime (24h/7d/30d) |
| `POST` | `/api/v1/health-checks/{checkId}/trigger` | Project EDITOR+ | Execute immediate on-demand health probe |
| `DELETE` | `/api/v1/health-checks/{checkId}` | Project EDITOR+ | Remove health check probe |

---

### 2.7 Incidents & Alert Management

| Method | Path | Auth / Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/projects/{projectId}/incidents` | Project VIEWER+ | List `Incident` records (filter by status) |
| `GET` | `/api/v1/incidents/{incidentId}` | Project VIEWER+ | Get detailed incident report & timeline |
| `POST` | `/api/v1/incidents/{incidentId}/acknowledge`| Project EDITOR+ | Acknowledge incident (transitions status) |
| `POST` | `/api/v1/incidents/{incidentId}/resolve` | Project EDITOR+ | Mark incident resolved with root cause summary |

---

### 2.8 Audit Logs & Dashboard Analytics

| Method | Path | Auth / Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/projects/{projectId}/audit-logs` | Project OWNER | Query immutable `AuditLog` stream |
| `GET` | `/api/v1/projects/{projectId}/dashboard/summary`| Project VIEWER+ | Real aggregated KPIs (Uptime, Incidents, APIs) |
| `GET` | `/api/v1/projects/{projectId}/dashboard/trends` | Project VIEWER+ | Time-series uptime & latency percentiles |
| `GET` | `/api/v1/projects/{projectId}/dashboard/security`| Project VIEWER+ | Vulnerability severity breakdown by OWASP |
| `GET` | `/api/v1/projects/{projectId}/dashboard/testing` | Project VIEWER+ | Test execution pass/fail historical trends |
