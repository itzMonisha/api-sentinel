import {
  ApiResponse,
  SystemHealthData,
  PageResponse,
  Project,
  CreateProjectInput,
  UpdateProjectInput,
  ApiEndpoint,
  CreateEndpointInput,
  UpdateEndpointInput,
  OpenApiPreviewResponse,
  OpenApiImportResponse,
  OpenApiImportRequest,
  SecurityScan,
  SecurityFinding,
  CreateScanInput,
  HealthCheck,
  HealthCheckRecord,
  Incident,
  IncidentStatus,
  AlertNotification,
  ProjectReliabilityDashboard,
  CreateHealthCheckInput,
  UpdateHealthCheckInput,
  ResolveIncidentInput,
  TestSuite,
  CreateTestSuiteInput,
  UpdateTestSuiteInput,
  TestCase,
  CreateTestCaseInput,
  UpdateTestCaseInput,
  TestRun,
  TestResult,
  User,
  AuthResponse
} from '../types';

export class ApiClient {
  private static baseUrl = '';
  private static accessToken: string | null = typeof window !== 'undefined' ? localStorage.getItem('sentinel_access_token') : null;
  private static refreshTokenVal: string | null = typeof window !== 'undefined' ? localStorage.getItem('sentinel_refresh_token') : null;
  private static currentUser: User | null = null;
  private static authListeners: Array<(user: User | null) => void> = [];

  public static onAuthChange(listener: (user: User | null) => void): () => void {
    this.authListeners.push(listener);
    listener(this.currentUser);
    return () => {
      this.authListeners = this.authListeners.filter(l => l !== listener);
    };
  }

  private static notifyAuthChange() {
    this.authListeners.forEach(listener => listener(this.currentUser));
  }

  public static setTokens(accessToken: string | null, refreshToken: string | null = null) {
    this.accessToken = accessToken;
    this.refreshTokenVal = refreshToken;
    if (typeof window !== 'undefined') {
      if (accessToken) {
        localStorage.setItem('sentinel_access_token', accessToken);
      } else {
        localStorage.removeItem('sentinel_access_token');
      }
      if (refreshToken) {
        localStorage.setItem('sentinel_refresh_token', refreshToken);
      } else {
        localStorage.removeItem('sentinel_refresh_token');
      }
    }
  }

  public static setAccessToken(token: string | null) {
    this.setTokens(token, this.refreshTokenVal);
  }

  public static getAccessToken(): string | null {
    return this.accessToken;
  }

  public static getRefreshToken(): string | null {
    return this.refreshTokenVal;
  }

  public static getCachedUser(): User | null {
    return this.currentUser;
  }

  private static async getHeaders(contentType: string | null = 'application/json'): Promise<HeadersInit> {
    const headers: Record<string, string> = {
      'Accept': 'application/json',
    };
    if (contentType) {
      headers['Content-Type'] = contentType;
    }
    if (this.accessToken) {
      headers['Authorization'] = `Bearer ${this.accessToken}`;
    }
    return headers;
  }

  private static async handleResponse<T>(res: Response): Promise<T> {
    if (!res.ok) {
      if (res.status === 401) {
        this.setTokens(null, null);
        this.currentUser = null;
        this.notifyAuthChange();
      }
      const errorJson = await res.json().catch(() => null);
      const detail = errorJson?.error?.detail || errorJson?.error?.title || `Request failed with status ${res.status}`;
      const fieldErrors = errorJson?.error?.fieldErrors;
      const error: any = new Error(detail);
      error.status = res.status;
      error.fieldErrors = fieldErrors;
      error.code = errorJson?.error?.errorCode;
      throw error;
    }
    const json: ApiResponse<T> = await res.json();
    return json.data;
  }

  // Health
  public static async getHealth(): Promise<ApiResponse<SystemHealthData>> {
    const res = await fetch(`${this.baseUrl}/api/v1/health?_t=${Date.now()}`, {
      headers: { 'Accept': 'application/json' },
    });
    if (!res.ok) {
      const errorJson = await res.json().catch(() => null);
      throw new Error(errorJson?.error?.detail || `Health probe failed with status ${res.status}`);
    }
    return res.json();
  }

  // Auth helper / Auto-login for demo UI
  public static async ensureAuthenticated(): Promise<string> {
    if (this.accessToken) {
      return this.accessToken;
    }
    // Attempt demo user login or registration
    try {
      const loginRes = await fetch(`${this.baseUrl}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify({ email: 'developer@apisentinel.dev', password: 'Password123!@#' }),
      });
      if (loginRes.ok) {
        const body = await loginRes.json();
        this.setTokens(body.data.accessToken, body.data.refreshToken);
        this.currentUser = body.data.user;
        this.notifyAuthChange();
        return this.accessToken!;
      }
    } catch {
      // ignore
    }

    // Register developer demo account
    try {
      const regRes = await fetch(`${this.baseUrl}/api/v1/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify({
          email: 'developer@apisentinel.dev',
          password: 'Password123!@#',
          fullName: 'API Sentinel Lead'
        }),
      });
      if (regRes.ok) {
        const body = await regRes.json();
        if (body.data?.accessToken) {
          this.setTokens(body.data.accessToken, body.data.refreshToken);
        }
        return this.accessToken || '';
      }
    } catch {
      // ignore
    }
    return '';
  }

  // Projects CRUD
  public static async listProjects(search?: string, page = 0, size = 20): Promise<PageResponse<Project>> {
    await this.ensureAuthenticated();
    const params = new URLSearchParams();
    if (search) params.append('search', search);
    params.append('page', page.toString());
    params.append('size', size.toString());
    params.append('sort', 'createdAt,desc');

    const res = await fetch(`${this.baseUrl}/api/v1/projects?${params.toString()}`, {
      headers: await this.getHeaders(),
    });
    return this.handleResponse<PageResponse<Project>>(res);
  }

  public static async getProject(projectId: string): Promise<Project> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}`, {
      headers: await this.getHeaders(),
    });
    return this.handleResponse<Project>(res);
  }

  public static async createProject(data: CreateProjectInput): Promise<Project> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects`, {
      method: 'POST',
      headers: await this.getHeaders(),
      body: JSON.stringify(data),
    });
    return this.handleResponse<Project>(res);
  }

  public static async updateProject(projectId: string, data: UpdateProjectInput): Promise<Project> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}`, {
      method: 'PUT',
      headers: await this.getHeaders(),
      body: JSON.stringify(data),
    });
    return this.handleResponse<Project>(res);
  }

  public static async deleteProject(projectId: string): Promise<void> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}`, {
      method: 'DELETE',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<void>(res);
  }

  // Endpoints CRUD
  public static async listEndpoints(
    projectId: string,
    method?: string,
    search?: string,
    page = 0,
    size = 50
  ): Promise<PageResponse<ApiEndpoint>> {
    await this.ensureAuthenticated();
    const params = new URLSearchParams();
    if (method && method !== 'ALL') params.append('method', method);
    if (search) params.append('search', search);
    params.append('page', page.toString());
    params.append('size', size.toString());
    params.append('sort', 'createdAt,desc');

    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/endpoints?${params.toString()}`, {
      headers: await this.getHeaders(),
    });
    return this.handleResponse<PageResponse<ApiEndpoint>>(res);
  }

  public static async getEndpoint(projectId: string, endpointId: string): Promise<ApiEndpoint> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/endpoints/${endpointId}`, {
      headers: await this.getHeaders(),
    });
    return this.handleResponse<ApiEndpoint>(res);
  }

  public static async createEndpoint(projectId: string, data: CreateEndpointInput): Promise<ApiEndpoint> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/endpoints`, {
      method: 'POST',
      headers: await this.getHeaders(),
      body: JSON.stringify(data),
    });
    return this.handleResponse<ApiEndpoint>(res);
  }

  public static async updateEndpoint(projectId: string, endpointId: string, data: UpdateEndpointInput): Promise<ApiEndpoint> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/endpoints/${endpointId}`, {
      method: 'PUT',
      headers: await this.getHeaders(),
      body: JSON.stringify(data),
    });
    return this.handleResponse<ApiEndpoint>(res);
  }

  public static async deleteEndpoint(projectId: string, endpointId: string): Promise<void> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/endpoints/${endpointId}`, {
      method: 'DELETE',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<void>(res);
  }

  // OpenAPI Ingestion
  public static async previewOpenApi(projectId: string, rawSpec: string): Promise<OpenApiPreviewResponse> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/openapi/preview`, {
      method: 'POST',
      headers: await this.getHeaders(),
      body: JSON.stringify({ rawSpec }),
    });
    return this.handleResponse<OpenApiPreviewResponse>(res);
  }

  public static async previewOpenApiFile(projectId: string, file: File): Promise<OpenApiPreviewResponse> {
    await this.ensureAuthenticated();
    const formData = new FormData();
    formData.append('file', file);

    const headers = await this.getHeaders(null);
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/openapi/preview/file`, {
      method: 'POST',
      headers,
      body: formData,
    });
    return this.handleResponse<OpenApiPreviewResponse>(res);
  }

  public static async importOpenApi(projectId: string, request: OpenApiImportRequest): Promise<OpenApiImportResponse> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/openapi/import`, {
      method: 'POST',
      headers: await this.getHeaders(),
      body: JSON.stringify(request),
    });
    return this.handleResponse<OpenApiImportResponse>(res);
  }

  public static async importOpenApiFile(
    projectId: string,
    file: File,
    overwriteExisting = false,
    selectedKeys?: string[]
  ): Promise<OpenApiImportResponse> {
    await this.ensureAuthenticated();
    const formData = new FormData();
    formData.append('file', file);
    formData.append('overwriteExisting', overwriteExisting.toString());
    if (selectedKeys && selectedKeys.length > 0) {
      selectedKeys.forEach(k => formData.append('selectedKeys', k));
    }

    const headers = await this.getHeaders(null);
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/openapi/import/file`, {
      method: 'POST',
      headers,
      body: formData,
    });
    return this.handleResponse<OpenApiImportResponse>(res);
  }

  // Security Scanner
  public static async launchScan(input: CreateScanInput): Promise<SecurityScan> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/scans`, {
      method: 'POST',
      headers: await this.getHeaders(),
      body: JSON.stringify(input),
    });
    return this.handleResponse<SecurityScan>(res);
  }

  public static async launchProjectScan(projectId: string, input?: CreateScanInput): Promise<SecurityScan> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/scans`, {
      method: 'POST',
      headers: await this.getHeaders(),
      body: JSON.stringify(input || {}),
    });
    return this.handleResponse<SecurityScan>(res);
  }

  public static async getScan(id: string): Promise<SecurityScan> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/scans/${id}`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<SecurityScan>(res);
  }

  public static async getScanFindings(id: string): Promise<SecurityFinding[]> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/scans/${id}/findings`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<SecurityFinding[]>(res);
  }

  public static async getProjectScans(projectId: string): Promise<SecurityScan[]> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/scans`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<SecurityScan[]>(res);
  }

  public static async getEndpointSecurityFindings(endpointId: string): Promise<SecurityFinding[]> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/endpoints/${endpointId}/security-findings`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<SecurityFinding[]>(res);
  }

  // ==========================================
  // Phase 06: Reliability Monitoring & Incidents
  // ==========================================

  public static async createHealthCheck(projectId: string, input: CreateHealthCheckInput): Promise<HealthCheck> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/health-checks`, {
      method: 'POST',
      headers: await this.getHeaders(),
      body: JSON.stringify(input),
    });
    return this.handleResponse<HealthCheck>(res);
  }

  public static async getProjectHealthChecks(projectId: string): Promise<HealthCheck[]> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/health-checks`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<HealthCheck[]>(res);
  }

  public static async getHealthCheck(checkId: string): Promise<HealthCheck> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/health-checks/${checkId}`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<HealthCheck>(res);
  }

  public static async updateHealthCheck(checkId: string, input: UpdateHealthCheckInput): Promise<HealthCheck> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/health-checks/${checkId}`, {
      method: 'PUT',
      headers: await this.getHeaders(),
      body: JSON.stringify(input),
    });
    return this.handleResponse<HealthCheck>(res);
  }

  public static async deleteHealthCheck(checkId: string): Promise<void> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/health-checks/${checkId}`, {
      method: 'DELETE',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<void>(res);
  }

  public static async triggerHealthCheck(checkId: string): Promise<HealthCheckRecord> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/health-checks/${checkId}/trigger`, {
      method: 'POST',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<HealthCheckRecord>(res);
  }

  public static async getHealthCheckRecords(checkId: string, limit = 50): Promise<HealthCheckRecord[]> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/health-checks/${checkId}/history?limit=${limit}`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<HealthCheckRecord[]>(res);
  }

  public static async getProjectIncidents(projectId: string, status?: IncidentStatus): Promise<Incident[]> {
    await this.ensureAuthenticated();
    const query = status ? `?status=${encodeURIComponent(status)}` : '';
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/incidents${query}`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<Incident[]>(res);
  }

  public static async getIncident(incidentId: string): Promise<Incident> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/incidents/${incidentId}`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<Incident>(res);
  }

  public static async acknowledgeIncident(incidentId: string): Promise<Incident> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/incidents/${incidentId}/acknowledge`, {
      method: 'POST',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<Incident>(res);
  }

  public static async resolveIncident(incidentId: string, input?: ResolveIncidentInput): Promise<Incident> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/incidents/${incidentId}/resolve`, {
      method: 'POST',
      headers: await this.getHeaders(),
      body: JSON.stringify(input || {}),
    });
    return this.handleResponse<Incident>(res);
  }

  public static async getProjectAlerts(projectId: string): Promise<AlertNotification[]> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/alerts`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<AlertNotification[]>(res);
  }

  public static async markAlertRead(alertId: string): Promise<void> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/alerts/${alertId}/read`, {
      method: 'POST',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<void>(res);
  }

  public static async getProjectReliabilityDashboard(projectId: string): Promise<ProjectReliabilityDashboard> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/dashboard/summary`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<ProjectReliabilityDashboard>(res);
  }

  // ==========================================
  // Test Suites & Execution Engine
  // ==========================================

  public static async listTestSuites(projectId: string): Promise<TestSuite[]> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/test-suites`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<TestSuite[]>(res);
  }

  public static async getTestSuite(id: string): Promise<TestSuite> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/test-suites/${id}`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<TestSuite>(res);
  }

  public static async createTestSuite(projectId: string, input: CreateTestSuiteInput): Promise<TestSuite> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/projects/${projectId}/test-suites`, {
      method: 'POST',
      headers: await this.getHeaders(),
      body: JSON.stringify(input),
    });
    return this.handleResponse<TestSuite>(res);
  }

  public static async updateTestSuite(id: string, input: UpdateTestSuiteInput): Promise<TestSuite> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/test-suites/${id}`, {
      method: 'PUT',
      headers: await this.getHeaders(),
      body: JSON.stringify(input),
    });
    return this.handleResponse<TestSuite>(res);
  }

  public static async deleteTestSuite(id: string): Promise<void> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/test-suites/${id}`, {
      method: 'DELETE',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<void>(res);
  }

  public static async createTestCase(suiteId: string, input: CreateTestCaseInput): Promise<TestCase> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/test-suites/${suiteId}/cases`, {
      method: 'POST',
      headers: await this.getHeaders(),
      body: JSON.stringify(input),
    });
    return this.handleResponse<TestCase>(res);
  }

  public static async getTestCases(suiteId: string): Promise<TestCase[]> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/test-suites/${suiteId}/cases`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<TestCase[]>(res);
  }

  public static async getTestCase(caseId: string): Promise<TestCase> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/test-cases/${caseId}`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<TestCase>(res);
  }

  public static async updateTestCase(caseId: string, input: UpdateTestCaseInput): Promise<TestCase> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/test-cases/${caseId}`, {
      method: 'PUT',
      headers: await this.getHeaders(),
      body: JSON.stringify(input),
    });
    return this.handleResponse<TestCase>(res);
  }

  public static async deleteTestCase(caseId: string): Promise<void> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/test-cases/${caseId}`, {
      method: 'DELETE',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<void>(res);
  }

  public static async triggerTestRun(suiteId: string): Promise<TestRun> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/test-suites/${suiteId}/runs`, {
      method: 'POST',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<TestRun>(res);
  }

  public static async getTestRunsBySuite(suiteId: string): Promise<TestRun[]> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/test-suites/${suiteId}/runs`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<TestRun[]>(res);
  }

  public static async getTestRun(runId: string): Promise<TestRun> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/test-runs/${runId}`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<TestRun>(res);
  }

  public static async getTestRunResults(runId: string): Promise<TestResult[]> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/test-runs/${runId}/results`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<TestResult[]>(res);
  }

  public static async getEndpointTestRuns(endpointId: string): Promise<TestRun[]> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/endpoints/${endpointId}/test-runs`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    return this.handleResponse<TestRun[]>(res);
  }

  // ==========================================
  // Real Identity & Profile Endpoints
  // ==========================================

  public static async getCurrentUser(): Promise<User> {
    await this.ensureAuthenticated();
    const res = await fetch(`${this.baseUrl}/api/v1/auth/me`, {
      method: 'GET',
      headers: await this.getHeaders(),
    });
    const user = await this.handleResponse<User>(res);
    this.currentUser = user;
    this.notifyAuthChange();
    return user;
  }

  public static async login(email: string, password: string): Promise<AuthResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify({ email, password }),
    });
    const auth = await this.handleResponse<AuthResponse>(res);
    this.setTokens(auth.accessToken, auth.refreshToken);
    this.currentUser = auth.user;
    this.notifyAuthChange();
    return auth;
  }

  public static async register(email: string, password: string, fullName: string): Promise<User> {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify({ email, password, fullName }),
    });
    return this.handleResponse<User>(res);
  }

  public static async refreshToken(): Promise<AuthResponse> {
    if (!this.refreshTokenVal) {
      throw new Error('No refresh token available');
    }
    const res = await fetch(`${this.baseUrl}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify({ refreshToken: this.refreshTokenVal }),
    });
    const auth = await this.handleResponse<AuthResponse>(res);
    this.setTokens(auth.accessToken, auth.refreshToken);
    this.currentUser = auth.user;
    this.notifyAuthChange();
    return auth;
  }

  public static async logout(): Promise<void> {
    try {
      if (this.accessToken) {
        await fetch(`${this.baseUrl}/api/v1/auth/logout`, {
          method: 'POST',
          headers: await this.getHeaders(),
          body: JSON.stringify({ refreshToken: this.refreshTokenVal }),
        });
      }
    } finally {
      this.setTokens(null, null);
      this.currentUser = null;
      this.notifyAuthChange();
    }
  }
}

