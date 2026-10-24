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
