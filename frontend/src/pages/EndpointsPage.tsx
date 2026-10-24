import React, { useEffect, useState } from 'react';
import { 
  Network, 
  Plus, 
  UploadCloud, 
  Search, 
  Trash2, 
  Edit3, 
  Eye, 
  FileCode, 
  CheckCircle, 
  AlertCircle, 
  X, 
  FolderKanban,
  Code2,
  FileText
} from 'lucide-react';
import { ApiClient } from '../services/apiClient';
import { 
  Project, 
  ApiEndpoint, 
  CreateEndpointInput, 
  OpenApiPreviewResponse, 
  AuthType 
} from '../types';
import { EmptyState } from '../components/common/EmptyState';

const HTTP_METHODS = ['ALL', 'GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'];

interface EndpointsPageProps {
  initialProjectId?: string;
}

export const EndpointsPage: React.FC<EndpointsPageProps> = ({ initialProjectId }) => {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>(initialProjectId || '');
  const [endpoints, setEndpoints] = useState<ApiEndpoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [selectedMethod, setSelectedMethod] = useState('ALL');
  const [error, setError] = useState<string | null>(null);

  // Endpoint Details Drawer
  const [inspectEndpoint, setInspectEndpoint] = useState<ApiEndpoint | null>(null);

  // Register / Edit Modal
  const [isRegisterOpen, setIsRegisterOpen] = useState(false);
  const [editingEndpoint, setEditingEndpoint] = useState<ApiEndpoint | null>(null);
  const [endpointForm, setEndpointForm] = useState<CreateEndpointInput>({
    name: '',
    url: '',
    httpMethod: 'GET',
    description: '',
    operationId: '',
    authConfig: { type: 'NONE' },
    requestSchema: '',
    responseSchema: '',
  });
  const [endpointLoading, setEndpointLoading] = useState(false);
  const [endpointError, setEndpointError] = useState<string | null>(null);

  // OpenAPI Import Workflow
  const [isOpenApiModalOpen, setIsOpenApiModalOpen] = useState(false);
  const [importMode, setImportMode] = useState<'upload' | 'paste'>('upload');
  const [specFile, setSpecFile] = useState<File | null>(null);
  const [rawSpec, setRawSpec] = useState('');
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewData, setPreviewData] = useState<OpenApiPreviewResponse | null>(null);
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());
  const [overwriteExisting, setOverwriteExisting] = useState(false);
  const [importLoading, setImportLoading] = useState(false);
  const [importSuccessMessage, setImportSuccessMessage] = useState<string | null>(null);

  // Load user projects
  useEffect(() => {
    const fetchProjects = async () => {
      try {
        const res = await ApiClient.listProjects();
        setProjects(res.content);
        if (!selectedProjectId && res.content.length > 0) {
          setSelectedProjectId(res.content[0].id);
        }
      } catch (err: any) {
        setError(err.message || 'Failed to fetch projects');
      }
    };
    fetchProjects();
  }, []);

  // Load endpoints when selected project, method, or search changes
  const loadEndpoints = async () => {
    if (!selectedProjectId) {
      setEndpoints([]);
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      setError(null);
      const res = await ApiClient.listEndpoints(
        selectedProjectId,
        selectedMethod !== 'ALL' ? selectedMethod : undefined,
        search || undefined
      );
      setEndpoints(res.content);
    } catch (err: any) {
      setError(err.message || 'Failed to load endpoints');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadEndpoints();
  }, [selectedProjectId, selectedMethod, search]);

  const handleEndpointSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProjectId) return;
    try {
      setEndpointLoading(true);
      setEndpointError(null);

      if (editingEndpoint) {
        await ApiClient.updateEndpoint(selectedProjectId, editingEndpoint.id, {
          ...endpointForm,
          name: endpointForm.name.trim(),
          url: endpointForm.url.trim(),
        });
      } else {
        await ApiClient.createEndpoint(selectedProjectId, {
          ...endpointForm,
          name: endpointForm.name.trim(),
          url: endpointForm.url.trim(),
        });
      }

      setIsRegisterOpen(false);
      setEditingEndpoint(null);
      await loadEndpoints();
    } catch (err: any) {
      setEndpointError(err.message || 'Operation failed');
    } finally {
      setEndpointLoading(false);
    }
  };

  const handleDeleteEndpoint = async (endpointId: string) => {
    if (!selectedProjectId) return;
    if (!confirm('Are you sure you want to delete this endpoint?')) return;
    try {
      await ApiClient.deleteEndpoint(selectedProjectId, endpointId);
      await loadEndpoints();
    } catch (err: any) {
      setError(err.message || 'Failed to delete endpoint');
    }
  };

  const handleOpenApiPreview = async () => {
    if (!selectedProjectId) return;
    try {
      setPreviewLoading(true);
      setError(null);
      setImportSuccessMessage(null);

      let preview: OpenApiPreviewResponse;
      if (importMode === 'upload') {
        if (!specFile) {
          setError('Please select an OpenAPI JSON or YAML file to upload.');
          setPreviewLoading(false);
          return;
        }
        preview = await ApiClient.previewOpenApiFile(selectedProjectId, specFile);
      } else {
        if (!rawSpec.trim()) {
          setError('Please paste a valid OpenAPI specification document.');
          setPreviewLoading(false);
          return;
        }
        preview = await ApiClient.previewOpenApi(selectedProjectId, rawSpec.trim());
      }

      setPreviewData(preview);
      // Select all endpoints by default
      setSelectedKeys(new Set(preview.endpoints.map((e) => e.key)));
    } catch (err: any) {
      setError(err.message || 'Failed to preview specification');
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleOpenApiImport = async () => {
    if (!selectedProjectId || !previewData) return;
    try {
      setImportLoading(true);
      setError(null);
      const keys = Array.from(selectedKeys);

      let res;
      if (importMode === 'upload' && specFile) {
        res = await ApiClient.importOpenApiFile(selectedProjectId, specFile, overwriteExisting, keys);
      } else {
        res = await ApiClient.importOpenApi(selectedProjectId, {
          rawSpec: rawSpec.trim(),
          selectedKeys: keys,
          overwriteExisting,
        });
      }

      setImportSuccessMessage(
        `Import completed: ${res.importedCount} new endpoints imported, ${res.overwrittenCount} overwritten, ${res.skippedCount} skipped.`
      );
      setPreviewData(null);
      setSpecFile(null);
      setRawSpec('');
      await loadEndpoints();
    } catch (err: any) {
      setError(err.message || 'Import failed');
    } finally {
      setImportLoading(false);
    }
  };

  const getMethodBadgeClass = (method: string) => {
    switch (method.toUpperCase()) {
      case 'GET': return 'badge-get';
      case 'POST': return 'badge-post';
      case 'PUT': return 'badge-put';
      case 'DELETE': return 'badge-delete';
      case 'PATCH': return 'badge-patch';
      default: return 'badge-neutral';
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Header and Project Switcher */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-white/5">
        <div>
          <h2 className="text-xl font-bold text-slate-100 font-mono tracking-tight flex items-center gap-2">
            <Network className="w-5 h-5 text-cyan-400" />
            Endpoints & OpenAPI Catalog
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Register endpoints manually or ingest OpenAPI specifications with automatic schema discovery.
          </p>
        </div>

        {/* Project Selector & Actions */}
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 glass-panel px-3 py-1.5 border-white/10">
            <FolderKanban className="w-3.5 h-3.5 text-cyan-400" />
            <span className="text-xs text-slate-400 font-mono">Workspace:</span>
            <select
              value={selectedProjectId}
              onChange={(e) => setSelectedProjectId(e.target.value)}
              className="bg-transparent text-xs font-mono font-medium text-slate-200 focus:outline-none cursor-pointer"
            >
              {projects.length === 0 ? (
                <option value="">No Projects Available</option>
              ) : (
                projects.map((p) => (
                  <option key={p.id} value={p.id} className="bg-slate-900 text-slate-200">
                    {p.name} ({p.slug})
                  </option>
                ))
              )}
            </select>
          </div>

          <button
            type="button"
            onClick={() => {
              setPreviewData(null);
              setImportSuccessMessage(null);
              setError(null);
              setIsOpenApiModalOpen(true);
            }}
            disabled={!selectedProjectId}
            className="btn-secondary text-xs font-mono disabled:opacity-50"
          >
            <UploadCloud className="w-3.5 h-3.5 text-cyan-400" />
            <span>Import OpenAPI</span>
          </button>

          <button
            type="button"
            onClick={() => {
              setEditingEndpoint(null);
              setEndpointForm({
                name: '',
                url: '',
                httpMethod: 'GET',
                description: '',
                operationId: '',
                authConfig: { type: 'NONE' },
                requestSchema: '',
                responseSchema: '',
              });
              setEndpointError(null);
              setIsRegisterOpen(true);
            }}
            disabled={!selectedProjectId}
            className="btn-primary text-xs font-mono disabled:opacity-50"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add Endpoint</span>
          </button>
        </div>
      </div>

      {/* Global Alerts */}
      {error && (
        <div className="p-4 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs flex items-center gap-3">
          <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
          <span>{error}</span>
        </div>
      )}

      {importSuccessMessage && (
        <div className="p-4 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 text-xs flex items-center gap-3">
          <CheckCircle className="w-4 h-4 shrink-0 text-emerald-400" />
          <span>{importSuccessMessage}</span>
        </div>
      )}

      {/* Method Filters and Search */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        {/* Method Badges */}
        <div className="flex flex-wrap items-center gap-1.5">
          {HTTP_METHODS.map((method) => {
            const active = selectedMethod === method;
            return (
              <button
                key={method}
                type="button"
                onClick={() => setSelectedMethod(method)}
                className={`px-2.5 py-1 rounded text-[11px] font-mono font-semibold transition-all ${
                  active
                    ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 shadow-sm'
                    : 'bg-white/[0.03] text-slate-400 border border-white/5 hover:text-slate-200 hover:bg-white/[0.06]'
                }`}
              >
                {method}
              </button>
            );
          })}
        </div>

        {/* Text Search */}
        <div className="relative">
          <Search className="w-3.5 h-3.5 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Search path or name..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="input-field pl-8 pr-3 py-1 text-xs w-full sm:w-56"
          />
        </div>
      </div>

      {/* Endpoints List */}
      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="glass-panel p-4 animate-pulse flex items-center justify-between">
              <div className="flex items-center gap-3 w-2/3">
                <div className="h-6 w-14 bg-white/5 rounded" />
                <div className="h-4 w-1/2 bg-white/5 rounded" />
              </div>
              <div className="h-6 w-20 bg-white/5 rounded" />
            </div>
          ))}
        </div>
      ) : !selectedProjectId ? (
        <EmptyState
          icon={FolderKanban}
          title="No Project Selected"
          description="Create or select a workspace from the header dropdown to view and manage its endpoints."
        />
      ) : endpoints.length === 0 ? (
        <EmptyState
          icon={Network}
          title={search || selectedMethod !== 'ALL' ? 'No Matching Endpoints' : 'No Endpoints Registered'}
          description={
            search || selectedMethod !== 'ALL'
              ? 'No endpoints matched your filter criteria. Try adjusting the HTTP method or search query.'
              : 'This project currently has no endpoints. You can add one manually or import an entire OpenAPI JSON/YAML specification.'
          }
          actionText="Import OpenAPI Spec"
          onAction={() => setIsOpenApiModalOpen(true)}
        />
      ) : (
        <div className="space-y-2.5">
          {endpoints.map((endpoint) => (
            <div
              key={endpoint.id}
              className="glass-panel px-4 py-3 flex flex-col md:flex-row md:items-center justify-between gap-3 hover:border-cyan-500/30 transition-all group"
            >
              <div className="flex items-start md:items-center gap-3 min-w-0">
                <span className={`badge ${getMethodBadgeClass(endpoint.httpMethod)} text-xs px-2 py-0.5 shrink-0`}>
                  {endpoint.httpMethod}
                </span>

                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs font-semibold text-slate-100 group-hover:text-cyan-300 transition-colors truncate">
                      {endpoint.url}
                    </span>
                    {endpoint.operationId && (
                      <span className="text-[10px] font-mono text-slate-500 bg-white/[0.03] px-1.5 py-0.5 rounded border border-white/5 shrink-0">
                        {endpoint.operationId}
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-slate-400 truncate mt-0.5">
                    {endpoint.name} {endpoint.description ? `— ${endpoint.description}` : ''}
                  </p>
                </div>
              </div>

              <div className="flex items-center justify-between md:justify-end gap-3 shrink-0 pt-2 md:pt-0 border-t md:border-t-0 border-white/5">
                <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-white/[0.03] border border-white/5 text-slate-400">
                  Auth: {endpoint.authConfig?.type || 'NONE'}
                </span>

                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => setInspectEndpoint(endpoint)}
                    className="p-1.5 rounded-md hover:bg-cyan-500/10 text-slate-400 hover:text-cyan-300 transition-colors"
                    title="View Schemas & Details"
                  >
                    <Eye className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setEditingEndpoint(endpoint);
                      setEndpointForm({
                        name: endpoint.name,
                        url: endpoint.url,
                        httpMethod: endpoint.httpMethod,
                        description: endpoint.description || '',
                        operationId: endpoint.operationId || '',
                        authConfig: endpoint.authConfig || { type: 'NONE' },
                        requestSchema: endpoint.requestSchema || '',
                        responseSchema: endpoint.responseSchema || '',
                      });
                      setEndpointError(null);
                      setIsRegisterOpen(true);
                    }}
                    className="p-1.5 rounded-md hover:bg-white/10 text-slate-400 hover:text-slate-200 transition-colors"
                    title="Edit Endpoint"
                  >
                    <Edit3 className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDeleteEndpoint(endpoint.id)}
                    className="p-1.5 rounded-md hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 transition-colors"
                    title="Delete Endpoint"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Endpoint Details Drawer / Modal */}
      {inspectEndpoint && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fadeIn">
          <div className="glass-panel w-full max-w-2xl max-h-[85vh] flex flex-col p-6 border-white/10 shadow-2xl relative">
            <button
              type="button"
              onClick={() => setInspectEndpoint(null)}
              className="absolute top-4 right-4 text-slate-400 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-2 mb-2">
              <span className={`badge ${getMethodBadgeClass(inspectEndpoint.httpMethod)} text-xs px-2.5 py-0.5`}>
                {inspectEndpoint.httpMethod}
              </span>
              <h3 className="text-base font-bold text-slate-100 font-mono">{inspectEndpoint.url}</h3>
            </div>

            <p className="text-xs text-slate-400 mb-4">{inspectEndpoint.name} — {inspectEndpoint.description || 'No description'}</p>

            <div className="overflow-y-auto space-y-4 pr-1">
              {/* Auth Config */}
              <div className="p-3 rounded-lg bg-white/[0.02] border border-white/5">
                <span className="text-[11px] font-mono text-cyan-400 uppercase tracking-wider block mb-1">
                  Authentication Policy
                </span>
                <div className="text-xs font-mono text-slate-300">
                  Type: <span className="text-white font-semibold">{inspectEndpoint.authConfig?.type || 'NONE'}</span>
                  {inspectEndpoint.authConfig?.headerName && (
                    <span className="ml-3 text-slate-400">Header: {inspectEndpoint.authConfig.headerName}</span>
                  )}
                  {inspectEndpoint.authConfig?.maskedValue && (
                    <span className="ml-3 text-slate-400">Value: {inspectEndpoint.authConfig.maskedValue}</span>
                  )}
                </div>
              </div>

              {/* Request Schema */}
              <div>
                <span className="text-[11px] font-mono text-cyan-400 uppercase tracking-wider block mb-1 flex items-center gap-1.5">
                  <Code2 className="w-3.5 h-3.5" />
                  Documented Request Schema
                </span>
                {inspectEndpoint.requestSchema ? (
                  <pre className="p-3 rounded bg-black/50 border border-white/5 text-[11px] font-mono text-cyan-300 overflow-x-auto max-h-40">
                    {inspectEndpoint.requestSchema}
                  </pre>
                ) : (
                  <div className="text-xs text-slate-500 italic p-2 bg-black/20 rounded">No request body schema defined.</div>
                )}
              </div>

              {/* Response Schema */}
              <div>
                <span className="text-[11px] font-mono text-cyan-400 uppercase tracking-wider block mb-1 flex items-center gap-1.5">
                  <FileCode className="w-3.5 h-3.5" />
                  Documented Response Schemas
                </span>
                {inspectEndpoint.responseSchema ? (
                  <pre className="p-3 rounded bg-black/50 border border-white/5 text-[11px] font-mono text-emerald-300 overflow-x-auto max-h-44">
                    {inspectEndpoint.responseSchema}
                  </pre>
                ) : (
                  <div className="text-xs text-slate-500 italic p-2 bg-black/20 rounded">No response schemas documented.</div>
                )}
              </div>

              {/* Test History */}
              <div className="pt-2 border-t border-white/5">
                <span className="text-[11px] font-mono text-slate-400 uppercase tracking-wider block mb-2">
                  Test Execution History
                </span>
                <div className="text-xs text-slate-500 italic p-3 rounded bg-white/[0.02] border border-white/5 text-center">
                  No automated test runs executed for this endpoint yet. (Automated tests unlocked in Phase 04).
                </div>
              </div>
            </div>

            <div className="mt-5 pt-3 border-t border-white/5 flex justify-end">
              <button
                type="button"
                onClick={() => setInspectEndpoint(null)}
                className="btn-secondary text-xs"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Register / Edit Endpoint Modal */}
      {isRegisterOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fadeIn">
          <div className="glass-panel w-full max-w-lg p-6 border-white/10 shadow-2xl relative max-h-[90vh] overflow-y-auto">
            <button
              type="button"
              onClick={() => setIsRegisterOpen(false)}
              className="absolute top-4 right-4 text-slate-400 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-lg font-bold text-slate-100 font-mono mb-1 flex items-center gap-2">
              <Network className="w-5 h-5 text-cyan-400" />
              {editingEndpoint ? 'Edit Endpoint' : 'Register API Endpoint'}
            </h3>
            <p className="text-xs text-slate-400 mb-5">
              Specify HTTP method, path or URL, parameters, and optional authentication.
            </p>

            {endpointError && (
              <div className="mb-4 p-3 rounded bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs">
                {endpointError}
              </div>
            )}

            <form onSubmit={handleEndpointSubmit} className="space-y-4">
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1.5">
                    Method <span className="text-rose-400">*</span>
                  </label>
                  <select
                    value={endpointForm.httpMethod}
                    onChange={(e) => setEndpointForm({ ...endpointForm, httpMethod: e.target.value })}
                    className="input-field font-mono text-xs font-bold"
                  >
                    {['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'].map((m) => (
                      <option key={m} value={m} className="bg-slate-900 text-slate-200">
                        {m}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="col-span-2">
                  <label className="block text-xs font-medium text-slate-300 mb-1.5">
                    URL or Path <span className="text-rose-400">*</span>
                  </label>
                  <input
                    type="text"
                    placeholder="/api/v1/orders or https://..."
                    value={endpointForm.url}
                    onChange={(e) => setEndpointForm({ ...endpointForm, url: e.target.value })}
                    className="input-field font-mono text-xs"
                    required
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Endpoint Name <span className="text-rose-400">*</span>
                </label>
                <input
                  type="text"
                  placeholder="e.g. Fetch Order History"
                  value={endpointForm.name}
                  onChange={(e) => setEndpointForm({ ...endpointForm, name: e.target.value })}
                  className="input-field"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Description
                </label>
                <textarea
                  placeholder="Brief description of what this endpoint does..."
                  value={endpointForm.description || ''}
                  onChange={(e) => setEndpointForm({ ...endpointForm, description: e.target.value })}
                  className="input-field h-20 resize-none"
                />
              </div>

              {/* Auth Config */}
              <div className="p-3 rounded-lg bg-white/[0.02] border border-white/5 space-y-3">
                <label className="block text-xs font-mono text-cyan-400 uppercase tracking-wider">
                  Authentication Configuration
                </label>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-[11px] text-slate-400 mb-1">Auth Type</label>
                    <select
                      value={endpointForm.authConfig?.type || 'NONE'}
                      onChange={(e) =>
                        setEndpointForm({
                          ...endpointForm,
                          authConfig: { ...endpointForm.authConfig, type: e.target.value as AuthType },
                        })
                      }
                      className="input-field text-xs font-mono"
                    >
                      <option value="NONE" className="bg-slate-900">None</option>
                      <option value="BEARER" className="bg-slate-900">Bearer Token</option>
                      <option value="API_KEY" className="bg-slate-900">API Key</option>
                      <option value="BASIC" className="bg-slate-900">Basic Auth</option>
                    </select>
                  </div>

                  {endpointForm.authConfig?.type !== 'NONE' && (
                    <div>
                      <label className="block text-[11px] text-slate-400 mb-1">Token or Secret</label>
                      <input
                        type="password"
                        placeholder="Token (will be masked)"
                        value={endpointForm.authConfig?.tokenOrPassword || ''}
                        onChange={(e) =>
                          setEndpointForm({
                            ...endpointForm,
                            authConfig: { ...endpointForm.authConfig, type: endpointForm.authConfig?.type || 'BEARER', tokenOrPassword: e.target.value },
                          })
                        }
                        className="input-field text-xs font-mono"
                      />
                    </div>
                  )}
                </div>
              </div>

              <div className="flex items-center justify-end gap-3 pt-4 border-t border-white/5">
                <button
                  type="button"
                  onClick={() => setIsRegisterOpen(false)}
                  className="btn-secondary text-xs"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={endpointLoading}
                  className="btn-primary text-xs font-mono"
                >
                  {endpointLoading ? 'Saving...' : editingEndpoint ? 'Save Changes' : 'Register Endpoint'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* OpenAPI Import Workflow Modal */}
      {isOpenApiModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-fadeIn">
          <div className="glass-panel w-full max-w-3xl p-6 border-white/10 shadow-2xl relative max-h-[90vh] flex flex-col">
            <button
              type="button"
              onClick={() => setIsOpenApiModalOpen(false)}
              className="absolute top-4 right-4 text-slate-400 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-lg font-bold text-slate-100 font-mono mb-1 flex items-center gap-2">
              <UploadCloud className="w-5 h-5 text-cyan-400" />
              Import OpenAPI Specification
            </h3>
            <p className="text-xs text-slate-400 mb-4">
              Validate and preview endpoints before importing them into your workspace.
            </p>

            {/* Mode Selector */}
            {!previewData && (
              <div className="flex items-center gap-2 mb-4 p-1 rounded-lg bg-black/40 border border-white/5 w-fit">
                <button
                  type="button"
                  onClick={() => setImportMode('upload')}
                  className={`px-3 py-1.5 rounded text-xs font-mono transition-colors flex items-center gap-1.5 ${
                    importMode === 'upload' ? 'bg-cyan-500/20 text-cyan-300 font-semibold' : 'text-slate-400 hover:text-white'
                  }`}
                >
                  <FileText className="w-3.5 h-3.5" />
                  Upload File
                </button>
                <button
                  type="button"
                  onClick={() => setImportMode('paste')}
                  className={`px-3 py-1.5 rounded text-xs font-mono transition-colors flex items-center gap-1.5 ${
                    importMode === 'paste' ? 'bg-cyan-500/20 text-cyan-300 font-semibold' : 'text-slate-400 hover:text-white'
                  }`}
                >
                  <Code2 className="w-3.5 h-3.5" />
                  Paste Raw Spec
                </button>
              </div>
            )}

            {/* Input Phase */}
            {!previewData ? (
              <div className="space-y-4">
                {importMode === 'upload' ? (
                  <div className="border-2 border-dashed border-white/10 hover:border-cyan-500/40 rounded-xl p-8 text-center transition-colors">
                    <UploadCloud className="w-8 h-8 text-cyan-400 mx-auto mb-3 opacity-80" />
                    <p className="text-xs text-slate-300 mb-1 font-medium">
                      Select an OpenAPI v3.0 or v3.1 specification
                    </p>
                    <p className="text-[11px] text-slate-500 mb-4">
                      Supports .json, .yaml, and .yml files up to 5 MB
                    </p>
                    <input
                      type="file"
                      accept=".json,.yaml,.yml"
                      onChange={(e) => setSpecFile(e.target.files?.[0] || null)}
                      className="text-xs text-slate-400 file:mr-4 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:text-xs file:font-mono file:bg-cyan-500/10 file:text-cyan-300 hover:file:bg-cyan-500/20 cursor-pointer"
                    />
                  </div>
                ) : (
                  <div>
                    <label className="block text-xs font-medium text-slate-300 mb-1.5">
                      Paste OpenAPI JSON or YAML Document
                    </label>
                    <textarea
                      placeholder="openapi: 3.0.3&#10;info:&#10;  title: Sample API&#10;paths:&#10;  /users:&#10;    get: ..."
                      value={rawSpec}
                      onChange={(e) => setRawSpec(e.target.value)}
                      className="input-field font-mono text-xs h-60 resize-none leading-relaxed"
                    />
                  </div>
                )}

                <div className="flex justify-end pt-3">
                  <button
                    type="button"
                    onClick={handleOpenApiPreview}
                    disabled={previewLoading || (importMode === 'upload' ? !specFile : !rawSpec.trim())}
                    className="btn-primary text-xs font-mono disabled:opacity-50"
                  >
                    {previewLoading ? 'Validating & Parsing...' : 'Validate & Preview'}
                  </button>
                </div>
              </div>
            ) : (
              /* Preview & Confirmation Phase */
              <div className="flex-1 overflow-y-auto space-y-4">
                <div className="p-4 rounded-lg bg-cyan-950/30 border border-cyan-500/20 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div>
                    <h4 className="text-sm font-bold text-white font-mono">{previewData.title} (v{previewData.version})</h4>
                    <p className="text-xs text-slate-400 mt-0.5">{previewData.description || 'No description in spec'}</p>
                    {previewData.serverUrls.length > 0 && (
                      <span className="text-[11px] font-mono text-cyan-400 mt-1 block">
                        Base URL: {previewData.serverUrls[0]}
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-3 shrink-0 text-xs font-mono">
                    <span className="text-slate-300">
                      Total: <strong className="text-white">{previewData.totalEndpoints}</strong>
                    </span>
                    <span className="text-emerald-400">
                      New: <strong>{previewData.newEndpointsCount}</strong>
                    </span>
                    {previewData.existingEndpointsCount > 0 && (
                      <span className="text-amber-400">
                        Existing: <strong>{previewData.existingEndpointsCount}</strong>
                      </span>
                    )}
                  </div>
                </div>

                {/* Selection Controls */}
                <div className="flex items-center justify-between text-xs px-1">
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setSelectedKeys(new Set(previewData.endpoints.map((e) => e.key)))}
                      className="text-cyan-400 hover:underline font-mono text-[11px]"
                    >
                      Select All ({previewData.endpoints.length})
                    </button>
                    <span className="text-slate-600">|</span>
                    <button
                      type="button"
                      onClick={() => setSelectedKeys(new Set())}
                      className="text-slate-400 hover:underline font-mono text-[11px]"
                    >
                      Deselect All
                    </button>
                  </div>

                  <label className="flex items-center gap-2 text-slate-300 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={overwriteExisting}
                      onChange={(e) => setOverwriteExisting(e.target.checked)}
                      className="rounded border-white/20 text-cyan-500 focus:ring-cyan-500"
                    />
                    <span className="text-[11px] font-mono">Overwrite existing matching endpoints</span>
                  </label>
                </div>

                {/* Endpoints Table */}
                <div className="glass-panel overflow-hidden border-white/10 max-h-64 overflow-y-auto">
                  <table className="w-full text-left text-xs border-collapse">
                    <thead className="bg-white/[0.04] text-[11px] font-mono text-slate-400 uppercase tracking-wider sticky top-0">
                      <tr>
                        <th className="p-2.5 w-10 text-center">Import</th>
                        <th className="p-2.5">Method</th>
                        <th className="p-2.5">Path</th>
                        <th className="p-2.5">Summary</th>
                        <th className="p-2.5 text-right">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-white/5 font-mono text-xs">
                      {previewData.endpoints.map((ep) => {
                        const checked = selectedKeys.has(ep.key);
                        return (
                          <tr key={ep.key} className="hover:bg-white/[0.02]">
                            <td className="p-2.5 text-center">
                              <input
                                type="checkbox"
                                checked={checked}
                                onChange={(e) => {
                                  const updated = new Set(selectedKeys);
                                  if (e.target.checked) updated.add(ep.key);
                                  else updated.delete(ep.key);
                                  setSelectedKeys(updated);
                                }}
                              />
                            </td>
                            <td className="p-2.5">
                              <span className={`badge ${getMethodBadgeClass(ep.httpMethod)} text-[10px] px-1.5 py-0.5`}>
                                {ep.httpMethod}
                              </span>
                            </td>
                            <td className="p-2.5 text-slate-200 font-semibold">{ep.path}</td>
                            <td className="p-2.5 text-slate-400 truncate max-w-xs">{ep.name}</td>
                            <td className="p-2.5 text-right">
                              {ep.alreadyExists ? (
                                <span className="badge badge-degraded text-[10px] px-2 py-0.5">Existing</span>
                              ) : (
                                <span className="badge badge-up text-[10px] px-2 py-0.5">New</span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>

                <div className="flex items-center justify-between pt-4 border-t border-white/5">
                  <button
                    type="button"
                    onClick={() => setPreviewData(null)}
                    className="btn-secondary text-xs"
                  >
                    Back to Spec
                  </button>
                  <button
                    type="button"
                    onClick={handleOpenApiImport}
                    disabled={importLoading || selectedKeys.size === 0}
                    className="btn-primary text-xs font-mono disabled:opacity-50"
                  >
                    {importLoading
                      ? 'Importing...'
                      : `Import ${selectedKeys.size} Selected Endpoint${selectedKeys.size === 1 ? '' : 's'}`}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
