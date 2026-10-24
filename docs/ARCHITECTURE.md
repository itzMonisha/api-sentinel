# API Sentinel — Technical Architecture & Engineering Guide

## 1. Architectural Philosophy: The Modular Monolith

### Why a Modular Monolith over Microservices?
In modern engineering discussions and technical interviews, jumping straight to microservices for a greenfield or medium-scale system is an anti-pattern. Microservices introduce distributed transactions, network latency, distributed tracing complexity, partial failure states, and deployment overhead.

Instead, **API Sentinel** is built as a **Modular Monolith**:
- **Single Deployable Unit**: Simplified CI/CD, one JAR executable, lower infrastructure cost, local development simplicity.
- **Strict Logical Boundaries**: Modules communicate via well-defined internal Java APIs / domain service interfaces, not ad-hoc circular dependencies.
- **Isolated Relational Schemas**: Tables are logically grouped by bounded context, preventing cross-domain Spaghetti SQL joins.
- **Future-Proof**: If a module (such as the Security Scanner or Health Probe) experiences heavy computational load, it can be decoupled into an independent microservice or worker pool with minimal refactoring.

```mermaid
graph TD
    subgraph "Presentation Layer"
        VITE["React 18 + TypeScript SPA<br/>(Vite, Tailwind/Modern CSS, Recharts)"]
    end

    VITE -->|HTTPS / REST / JWT Bearer| GATEWAY["Spring Boot 3 Web Layer<br/>(Thin Controllers, RFC 7807 Handler)"]

    subgraph "Modular Monolith Domain Core"
        AUTH["auth: Auth & RBAC"]
        PROJ["projects: Projects & API Registry"]
        DISC["discovery: OpenAPI 3.x Parser & Diff Engine"]
        TEST["testing: Test Suite & Chained Test Runner"]
        SEC["security: Safe OWASP Security Scanner"]
        MON["monitoring: Scheduled Health Probes & SLA"]
        INC["incidents: FSM Incident Lifecycle & Alerts"]
        AUD["audit: Immutable Audit Log Stream"]
        DASH["dashboard: Real Backend Metrics Aggregator"]
    end

    GATEWAY --> AUTH
    GATEWAY --> PROJ
    GATEWAY --> DISC
    GATEWAY --> TEST
    GATEWAY --> SEC
    GATEWAY --> MON
    GATEWAY --> INC
    GATEWAY --> AUD
    GATEWAY --> DASH

    subgraph "Cross-Cutting Infrastructure"
        SSRF["net: Zero-Trust SSRF-Safe HTTP Client<br/>(DNS Pinning, Bitmask CIDR, Direct Socket Binding)"]
        ASYNC["concurrency: ThreadPoolTaskExecutor<br/>(Bounded Queues, Rejection Handlers)"]
        FLYWAY["migration: Flyway Versioned Migrations"]
    end

    DISC --> SSRF
    TEST --> SSRF
    SEC --> SSRF
    MON --> SSRF

    TEST --> ASYNC
    SEC --> ASYNC
    MON --> ASYNC

    subgraph "Persistence Layer"
        HIKARI["HikariCP Connection Pool"]
        PG[("PostgreSQL 16+ Database")]
    end

    AUTH --> HIKARI
    PROJ --> HIKARI
    DISC --> HIKARI
    TEST --> HIKARI
    SEC --> HIKARI
    MON --> HIKARI
    INC --> HIKARI
    AUD --> HIKARI
    DASH --> HIKARI
    HIKARI --> PG
    FLYWAY --> PG
```

---

## 2. Module Boundaries & Dependency Rules

To preserve clean architecture, dependencies between modules must be strictly unidirectional:

```mermaid
graph LR
    AUTH["auth"] --> PROJ["projects"]
    PROJ --> DISC["discovery"]
    PROJ --> TEST["testing"]
    PROJ --> SEC["security"]
    PROJ --> MON["monitoring"]
    MON --> INC["incidents"]
    SEC --> INC["incidents"]
    PROJ --> AUD["audit"]
    PROJ --> DASH["dashboard"]
    TEST --> DASH
    SEC --> DASH
    MON --> DASH
```

### Dependency Rules:
1. **Controllers are Thin**: Controllers perform request routing, Bean Validation trigger (`@Valid`), and response mapping. Zero business logic in controllers.
2. **Business Logic in Services**: All domain logic, authorization verification, state machines, and calculations reside inside dedicated service classes.
3. **DTO Separation**: Internal entities (`@Entity`) are never returned directly from controllers. All ingress and egress data pass through strongly typed Record/Class DTOs.
4. **No Circular Module Dependencies**: Modules never import each other bidirectionally. Cross-module communication uses domain events or well-defined service interfaces.

---

## 3. Deep Dive: Zero-Trust Network Defense & SSRF Protection

### The Attack Vector: Server-Side Request Forgery (SSRF) & DNS Rebinding
When an application accepts target URLs from users to perform HTTP requests (e.g., importing OpenAPI specifications, running test cases, or pinging health endpoints), attackers attempt to:
1. Access internal cloud metadata: `http://169.254.169.254/latest/meta-data/` to steal AWS/GCP/Azure IAM credentials.
2. Scan internal networks: `http://10.0.0.1:8080`, `http://192.168.1.1`.
3. Exploit local services: `http://localhost:5432` (PostgreSQL), `http://127.0.0.1:6379` (Redis).
4. Conduct **DNS Rebinding Attacks**: The attacker controls a domain `rebind.attacker.com`. When our server validates the domain, DNS returns a benign public IP (`93.184.216.34`, TTL = 0). When the HTTP client connects a millisecond later, the DNS server returns `127.0.0.1`.

### The API Sentinel Outbound Defense Architecture

```mermaid
sequenceDiagram
    participant User as Engine / Worker
    participant Client as SafeHttpClient
    participant DNS as System DNS
    participant Target as External Target

    User->>Client: execute(Request: "https://api.example.com/v1/health")
    
    rect rgb(240, 248, 255)
        Note over Client: Step 1: Protocol Check
        Client->>Client: Only 'http' and 'https' allowed
    end

    rect rgb(255, 245, 238)
        Note over Client,DNS: Step 2: Pre-connection DNS Resolution
        Client->>DNS: InetAddress.getAllByName("api.example.com")
        DNS-->>Client: [Resolved IPs]
    end

    rect rgb(255, 240, 245)
        Note over Client: Step 3: O(1) Bitmask CIDR Verification
        Client->>Client: Check against Blocked Subnets:<br/>127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16,<br/>169.254.0.0/16, fc00::/7, 169.254.169.254
    end

    alt Any IP falls in blocked subnet
        Client-->>User: REJECT (SSRF Exception: Blocked IP target)
    else All IPs are Valid Public Addresses
        rect rgb(240, 255, 240)
            Note over Client,Target: Step 4: Direct Socket IP Binding (Anti-Rebinding)
            Client->>Target: Connect socket directly to resolved IP
            Note over Client,Target: Preserves 'Host: api.example.com' for TLS SNI
        end

        rect rgb(248, 248, 255)
            Note over Client,Target: Step 5: Quotas & Redirect Inspection
            Client->>Target: Send Request (5s connect timeout, 10s read timeout)
            Target-->>Client: Response Stream (Hard capped at 5MB)
            Note over Client: If 3xx Redirect: Re-evaluate Steps 1-4 on 'Location'
        end

        Client-->>User: Validated Response
    end
```

---

## 4. Concurrency & Threading Architecture

Outbound network operations (test executions, security scans, health checks) are fundamentally **I/O-bound**. If not isolated, slow external APIs could starve the Spring Boot web container (Tomcat) of worker threads.

### Thread Isolation Model
```mermaid
graph TD
    TOMCAT["Tomcat Worker Threads<br/>(Handles inbound client requests)<br/>Pool Size: 200"]
    
    subgraph "Dedicated I/O Worker Pools"
        TEST_POOL["testExecutionExecutor<br/>Core: 4, Max: 16, Queue: 100"]
        SCAN_POOL["securityScanExecutor<br/>Core: 2, Max: 8, Queue: 50"]
        HEALTH_POOL["healthCheckScheduler<br/>Core: 4, Max: 10, Queue: 500"]
    end

    TOMCAT -->|Enqueues Test Run| TEST_POOL
    TOMCAT -->|Enqueues Scan| SCAN_POOL
    SCHED["Spring @Scheduled"] -->|Triggers Probe| HEALTH_POOL
```

### Thread Pool Sizing Principles (Interview Formula)
For I/O-bound tasks where threads spend most time waiting on network responses:
$$\text{Threads} = \text{CPU Cores} \times \left(1 + \frac{\text{Wait Time}}{\text{Service Time}}\right)$$
Because external HTTP pings have high wait-to-service ratios ($\approx 10:1$), bounded worker pools with non-blocking queues prevent system overload while enabling high concurrency.

---

## 5. Deployment Architecture

```mermaid
graph TD
    subgraph "Client Tier"
        BROWSER["Web Browser / Playwright E2E"]
    end

    subgraph "Docker Compose / Production Host"
        subgraph "Frontend Container"
            NGINX["Nginx Web Server / Vite Preview<br/>Port: 3000 / 5173"]
        end

        subgraph "Backend Container"
            APP["API Sentinel Backend JAR<br/>(Java 21 OpenJDK, Spring Boot 3)<br/>Port: 8080"]
        end

        subgraph "Database Container"
            POSTGRES["PostgreSQL 16 Container<br/>Port: 5432<br/>Mounted Volume: pg_data"]
        end
    end

    BROWSER -->|HTTP/HTTPS| NGINX
    NGINX -->|Reverse Proxy /api/*| APP
    APP -->|JDBC / HikariCP Pool| POSTGRES
```

---

## 6. SDE Interview Mentoring & Architectural Trade-offs

When explaining this architecture in SDE and backend engineering interviews, focus on these critical trade-offs:

### 1. Why Bitmask Integer Matching for CIDR Blocks?
- **Naive Approach**: Checking if string starts with `"192.168."` fails on CIDRs that don't align with octets (e.g., `/12` for `172.16.0.0/12` spans `172.16.0.0` through `172.31.255.255`).
- **Bit Manipulation**: 
  $$\text{NetMask} = \sim((1 \ll (32 - \text{prefix})) - 1)$$
  $$\text{Matches} = (\text{IP\_Int} \ \& \ \text{NetMask}) == \text{Subnet\_Int}$$
  This runs in $O(1)$ operations with zero heap allocation.

### 2. Time-of-Check to Time-of-Use (TOCTOU) in Network Security:
- *Question*: *"Why isn't validating the URL hostname before making the request enough?"*
- *Answer*: *"Because of DNS rebinding. An attacker can set a DNS TTL of 0 seconds. During the validation check, it resolves to a public IP. When the HTTP library opens the socket, it resolves DNS a second time and gets 127.0.0.1. We eliminate this TOCTOU window by pre-resolving the IP, validating it, and pinning the socket connection directly to that specific IP address."*

### 3. Asynchronous Execution with Bounded Queues:
- *Question*: *"What happens if a user triggers 1,000 security scans simultaneously?"*
- *Answer*: *"We never spawn unbounded threads. We use a `ThreadPoolTaskExecutor` with a bounded `ArrayBlockingQueue` and `ThreadPoolExecutor.AbortPolicy` (or custom rejection handler) that translates excess traffic into an HTTP `429 Too Many Requests` or `QUEUED` state, protecting the server against Out-Of-Memory (OOM) crashes."*
