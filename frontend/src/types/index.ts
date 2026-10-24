export type NavigationTab = 
  | 'dashboard'
  | 'projects'
  | 'endpoints'
  | 'test-runs'
  | 'security-scans'
  | 'incidents'
  | 'settings';

export interface SystemHealthData {
  status: 'UP' | 'DOWN' | 'DEGRADED';
  application: string;
  version: string;
  environment: string;
  timestamp: string;
  uptimeSeconds: number;
  components: {
    database?: {
      status: string;
      databaseProductName?: string;
      databaseProductVersion?: string;
      error?: string;
    };
    [key: string]: unknown;
  };
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  timestamp: string;
}

export interface ApiErrorDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  errorCode: string;
  instance: string;
  fieldErrors?: Array<{ field: string; message: string }>;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export type ProjectRole = 'OWNER' | 'EDITOR' | 'VIEWER';

export interface Project {
  id: string;
  name: string;
  slug: string;
  description?: string;
  createdBy: string;
  userRole: ProjectRole;
  endpointCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProjectInput {
  name: string;
  slug?: string;
  description?: string;
}

export interface UpdateProjectInput {
  name: string;
  description?: string;
}

export type AuthType = 'NONE' | 'BEARER' | 'BASIC' | 'API_KEY' | 'CUSTOM_HEADER';

export interface AuthConfig {
  type: AuthType;
  keyName?: string;
  headerName?: string;
  paramLocation?: string;
  tokenOrPassword?: string;
  maskedValue?: string;
}

export interface Parameter {
  name: string;
  in: 'query' | 'path' | 'header' | 'cookie';
  required: boolean;
  type: string;
  description?: string;
  example?: string;
}

export interface TestHistorySummary {
  testRunId: string;
  testCaseName: string;
  status: string;
  httpStatusCode: number;
  responseTimeMs: number;
  executedAt: string;
}

export interface ApiEndpoint {
  id: string;
  projectId: string;
  name: string;
  url: string;
  httpMethod: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'HEAD' | 'OPTIONS';
  description?: string;
  operationId?: string;
  authConfig?: AuthConfig;
  parameters?: Parameter[];
  headers?: Record<string, string>;
  requestSchema?: string;
  responseSchema?: string;
  deprecated: boolean;
  testHistory?: TestHistorySummary[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateEndpointInput {
  name: string;
  url: string;
  httpMethod: string;
  description?: string;
  operationId?: string;
  authConfig?: AuthConfig;
  parameters?: Parameter[];
  headers?: Record<string, string>;
  requestSchema?: string;
  responseSchema?: string;
}

export interface UpdateEndpointInput extends CreateEndpointInput {
  deprecated?: boolean;
}

export interface OpenApiEndpointPreview {
  key: string;
  name: string;
  path: string;
  httpMethod: string;
  summary?: string;
  description?: string;
  operationId?: string;
  tags?: string[];
  alreadyExists: boolean;
  parameters?: Parameter[];
  requestSchema?: string;
  responseSchema?: string;
  deprecated?: boolean;
}

export interface OpenApiPreviewResponse {
  title: string;
  version: string;
  description?: string;
  serverUrls: string[];
  totalEndpoints: number;
  existingEndpointsCount: number;
  newEndpointsCount: number;
  endpoints: OpenApiEndpointPreview[];
  validationErrors?: string[];
  validationWarnings?: string[];
}

export interface OpenApiImportRequest {
  rawSpec?: string;
  selectedKeys?: string[];
  overwriteExisting?: boolean;
  baseUrlOverride?: string;
}

export interface OpenApiImportResponse {
  totalProcessed: number;
  importedCount: number;
  skippedCount: number;
  overwrittenCount: number;
  importedEndpoints: ApiEndpoint[];
}

export type ScanStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
export type FindingSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
export type FindingConfidence = 'CONFIRMED' | 'POTENTIAL_RISK';

export interface SecurityScan {
  id: string;
  projectId: string;
  projectName: string;
  status: ScanStatus;
  totalChecks: number;
  criticalCount: number;
  highCount: number;
  mediumCount: number;
  lowCount: number;
  infoCount: number;
  startedAt?: string;
  finishedAt?: string;
  createdAt: string;
}

export interface SecurityFinding {
  id: string;
  scanId: string;
  endpointId?: string;
  endpointName?: string;
  endpointUrl?: string;
  httpMethod?: string;
  ruleId: string;
  title: string;
  severity: FindingSeverity;
  confidence: FindingConfidence;
  owaspCategory?: string;
  description: string;
  evidence?: string;
  remediation: string;
  createdAt: string;
}

export interface CreateScanInput {
  projectId?: string;
  testFixtureAuth?: Record<string, string>;
}

// ==========================================
// Phase 06: Reliability Monitoring & Incidents
// ==========================================

export type HealthCheckStatus = 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'UNKNOWN';
export type IncidentSeverity = 'CRITICAL' | 'MAJOR' | 'MINOR';
export type IncidentStatus = 'TRIGGERED' | 'ACKNOWLEDGED' | 'RESOLVED';
export type AlertChannel = 'IN_APP' | 'EMAIL' | 'WEBHOOK';

export interface HealthMetrics {
  status: HealthCheckStatus;
  totalChecks: number;
  successCount: number;
  failureCount: number;
  failureRatePercentage: number;
  uptimePercentage: number | null;
  p50LatencyMs: number | null;
  p95LatencyMs: number | null;
  p99LatencyMs: number | null;
  averageLatencyMs: number | null;
  hasSufficientData: boolean;
  metricsExplanation: string;
}

export interface HealthCheck {
  id: string;
  projectId: string;
  endpointId: string;
  endpointName: string;
  endpointUrl: string;
  httpMethod: string;
  name: string;
  intervalSeconds: number;
  timeoutMs: number;
  expectedStatus: number;
  failureThreshold: number;
  recoveryThreshold: number;
  active: boolean;
  lastStatus: HealthCheckStatus;
  consecutiveFailures: number;
  consecutiveSuccesses: number;
  lastCheckedAt?: string;
  nextCheckAt?: string;
  metrics?: HealthMetrics;
}

export interface HealthCheckRecord {
  id: string;
  healthCheckId: string;
  endpointId: string;
  statusCode: number;
  responseTimeMs: number;
  success: boolean;
  errorMessage?: string;
  executedAt: string;
}

export interface Incident {
  id: string;
  projectId: string;
  projectName: string;
  healthCheckId: string;
  healthCheckName: string;
  endpointId: string;
  endpointName: string;
  endpointUrl: string;
  httpMethod: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  title: string;
  description: string;
  consecutiveFailures: number;
  rootCause?: string;
  startedAt: string;
  acknowledgedAt?: string;
  resolvedAt?: string;
  acknowledgedByUsername?: string;
  resolvedByUsername?: string;
}

export interface AlertNotification {
  id: string;
  projectId: string;
  projectName: string;
  incidentId?: string;
  channel: AlertChannel;
  severity: IncidentSeverity;
  title: string;
  message: string;
  sentAt: string;
  read: boolean;
}

export interface ProjectReliabilityDashboard {
  projectId: string;
  projectName: string;
  totalMonitoredEndpoints: number;
  healthyEndpoints: number;
  degradedEndpoints: number;
  downEndpoints: number;
  overallUptimePercentage: number | null;
  activeIncidentsCount: number;
  totalIncidents: number;
  unreadAlertsCount: number;
}

export interface CreateHealthCheckInput {
  endpointId: string;
  name: string;
  intervalSeconds?: number;
  timeoutMs?: number;
  expectedStatus?: number;
  failureThreshold?: number;
  recoveryThreshold?: number;
}

export interface UpdateHealthCheckInput {
  name?: string;
  intervalSeconds?: number;
  timeoutMs?: number;
  expectedStatus?: number;
  failureThreshold?: number;
  recoveryThreshold?: number;
  active?: boolean;
}

export interface ResolveIncidentInput {
  rootCause?: string;
}


