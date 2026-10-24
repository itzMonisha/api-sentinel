import React, { useEffect, useState, useMemo } from 'react';
import {
  AlertOctagon,
  CheckCircle2,
  Clock,
  Activity,
  Bell,
  Check,
  RefreshCw,
  Plus,
  Play,
  History,
  Sliders,
  Search,
  CheckCheck,
  X,
  Server
} from 'lucide-react';
import { ApiClient } from '../services/apiClient';
import {
  Project,
  ApiEndpoint,
  HealthCheck,
  HealthCheckRecord,
  Incident,
  IncidentStatus,
  IncidentSeverity,
  AlertNotification,
  ProjectReliabilityDashboard,
  CreateHealthCheckInput
} from '../types';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorBanner } from '../components/common/ErrorBanner';

export const IncidentsPage: React.FC = () => {
  // Navigation & Project context
  const [activeTab, setActiveTab] = useState<'incidents' | 'checks' | 'alerts'>('incidents');
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>('');
  const [projectEndpoints, setProjectEndpoints] = useState<ApiEndpoint[]>([]);

  // Telemetry & Data states
  const [dashboard, setDashboard] = useState<ProjectReliabilityDashboard | null>(null);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [healthChecks, setHealthChecks] = useState<HealthCheck[]>([]);
  const [alerts, setAlerts] = useState<AlertNotification[]>([]);

  // Loading & Error states
  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [actionLoading, setActionLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Filters
  const [statusFilter, setStatusFilter] = useState<'ALL' | IncidentStatus>('ALL');
  const [severityFilter, setSeverityFilter] = useState<'ALL' | IncidentSeverity>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Modals & Drawers
  const [isCreateModalOpen, setIsCreateModalOpen] = useState<boolean>(false);
  const [isResolveModalOpen, setIsResolveModalOpen] = useState<boolean>(false);
  const [selectedIncidentForResolve, setSelectedIncidentForResolve] = useState<Incident | null>(null);
  const [resolveRootCause, setResolveRootCause] = useState<string>('');

  const [isHistoryDrawerOpen, setIsHistoryDrawerOpen] = useState<boolean>(false);
  const [selectedCheckForHistory, setSelectedCheckForHistory] = useState<HealthCheck | null>(null);
  const [historyRecords, setHistoryRecords] = useState<HealthCheckRecord[]>([]);
  const [loadingHistory, setLoadingHistory] = useState<boolean>(false);

  // Form state for creating health check
  const [formData, setFormData] = useState<CreateHealthCheckInput>({
    endpointId: '',
    name: '',
    intervalSeconds: 10,
    timeoutMs: 3000,
    expectedStatus: 200,
    failureThreshold: 3,
    recoveryThreshold: 2
  });

  // Initial load: Fetch projects
  useEffect(() => {
    loadProjects();
  }, []);

  // When selected project changes, load all project monitoring data
  useEffect(() => {
    if (selectedProjectId) {
      loadProjectData(selectedProjectId);
      loadProjectEndpoints(selectedProjectId);
    }
  }, [selectedProjectId]);

  const loadProjects = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await ApiClient.listProjects('', 0, 100);
      const projs = res.content || [];
      setProjects(projs);
      if (projs.length > 0) {
        setSelectedProjectId(projs[0].id);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load projects');
    } finally {
      setLoading(false);
    }
  };

  const loadProjectEndpoints = async (projectId: string) => {
    try {
      const res = await ApiClient.listEndpoints(projectId, undefined, undefined, 0, 100);
      setProjectEndpoints(res.content || []);
    } catch {
      // Non-fatal if empty
    }
  };

  const loadProjectData = async (projectId: string) => {
    try {
      setRefreshing(true);
      setError(null);

      const [dashRes, incRes, checkRes, alertRes] = await Promise.all([
        ApiClient.getProjectReliabilityDashboard(projectId).catch(() => null),
        ApiClient.getProjectIncidents(projectId).catch(() => []),
        ApiClient.getProjectHealthChecks(projectId).catch(() => []),
        ApiClient.getProjectAlerts(projectId).catch(() => [])
      ]);

      setDashboard(dashRes);
      setIncidents(incRes);
      setHealthChecks(checkRes);
      setAlerts(alertRes);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to fetch monitoring telemetry');
    } finally {
      setRefreshing(false);
    }
  };

  const handleAcknowledge = async (incidentId: string) => {
    try {
      setActionLoading(true);
      await ApiClient.acknowledgeIncident(incidentId);
      setSuccessMessage('Incident acknowledged successfully.');
      setTimeout(() => setSuccessMessage(null), 3000);
      if (selectedProjectId) {
        await loadProjectData(selectedProjectId);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to acknowledge incident');
    } finally {
      setActionLoading(false);
    }
  };

  const handleOpenResolveModal = (incident: Incident) => {
    setSelectedIncidentForResolve(incident);
    setResolveRootCause('');
    setIsResolveModalOpen(true);
  };

  const handleConfirmResolve = async () => {
    if (!selectedIncidentForResolve) return;
    try {
      setActionLoading(true);
      await ApiClient.resolveIncident(selectedIncidentForResolve.id, {
        rootCause: resolveRootCause.trim() || undefined
      });
      setIsResolveModalOpen(false);
      setSelectedIncidentForResolve(null);
      setSuccessMessage('Incident marked as RESOLVED and recovery logged.');
      setTimeout(() => setSuccessMessage(null), 3000);
      if (selectedProjectId) {
        await loadProjectData(selectedProjectId);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to resolve incident');
    } finally {
      setActionLoading(false);
    }
  };

  const handleTriggerCheck = async (checkId: string) => {
    try {
      setActionLoading(true);
      const res = await ApiClient.triggerHealthCheck(checkId);
      const msg = res.success
        ? `Probe succeeded (HTTP ${res.statusCode}, ${res.responseTimeMs}ms)`
        : `Probe failed (HTTP ${res.statusCode || 'ERR'}: ${res.errorMessage || 'Threshold violation'})`;
      setSuccessMessage(msg);
      setTimeout(() => setSuccessMessage(null), 4000);
      if (selectedProjectId) {
        await loadProjectData(selectedProjectId);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to trigger health check');
    } finally {
      setActionLoading(false);
    }
  };

  const handleOpenHistory = async (check: HealthCheck) => {
    setSelectedCheckForHistory(check);
    setIsHistoryDrawerOpen(true);
    setLoadingHistory(true);
    try {
      const records = await ApiClient.getHealthCheckRecords(check.id, 50);
      setHistoryRecords(records);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load probe history');
    } finally {
      setLoadingHistory(false);
    }
  };

  const handleCreateHealthCheck = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProjectId) return;
    if (!formData.endpointId) {
      setError('Please select an endpoint to monitor.');
      return;
    }
    if (!formData.name.trim()) {
      setError('Please provide a descriptive name for this health check.');
      return;
    }

    try {
      setActionLoading(true);
      await ApiClient.createHealthCheck(selectedProjectId, formData);
      setIsCreateModalOpen(false);
      setFormData({
        endpointId: '',
        name: '',
        intervalSeconds: 10,
        timeoutMs: 3000,
        expectedStatus: 200,
        failureThreshold: 3,
        recoveryThreshold: 2
      });
      setSuccessMessage('Health check scheduled successfully.');
      setTimeout(() => setSuccessMessage(null), 3000);
      await loadProjectData(selectedProjectId);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to configure health check');
    } finally {
      setActionLoading(false);
    }
  };

  const handleMarkAlertRead = async (alertId: string) => {
    try {
      await ApiClient.markAlertRead(alertId);
      setAlerts(prev => prev.map(a => a.id === alertId ? { ...a, read: true } : a));
      if (dashboard) {
        setDashboard({ ...dashboard, unreadAlertsCount: Math.max(0, dashboard.unreadAlertsCount - 1) });
      }
    } catch {
      // Ignored
    }
  };

  // Filtered incidents
  const filteredIncidents = useMemo(() => {
    return incidents.filter(inc => {
      if (statusFilter !== 'ALL' && inc.status !== statusFilter) return false;
      if (severityFilter !== 'ALL' && inc.severity !== severityFilter) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchTitle = inc.title.toLowerCase().includes(q);
        const matchUrl = inc.endpointUrl.toLowerCase().includes(q);
        const matchName = inc.endpointName.toLowerCase().includes(q);
        const matchDesc = inc.description.toLowerCase().includes(q);
        if (!matchTitle && !matchUrl && !matchName && !matchDesc) return false;
      }
      return true;
    });
  }, [incidents, statusFilter, severityFilter, searchQuery]);

  if (loading && projects.length === 0) {
    return <LoadingSpinner message="Loading reliability monitoring & incident records..." />;
  }

  const unreadAlertsCount = dashboard?.unreadAlertsCount ?? alerts.filter(a => !a.read).length;

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h2 className="text-xl font-bold text-slate-100 font-mono tracking-tight flex items-center gap-2">
              <AlertOctagon className="w-5 h-5 text-rose-500" />
              API Reliability & Incident Management
            </h2>
            <span className="text-xs px-2 py-0.5 rounded bg-rose-500/10 text-rose-400 border border-rose-500/20 font-mono">
              Phase 06
            </span>
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Automated threshold-based health probes, deduplicated incident lifecycle, and bounded concurrency scheduling.
          </p>
        </div>

        {/* Project Selector & Actions */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 bg-[#0d131f] border border-white/10 rounded-lg px-3 py-1.5">
            <Server className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={selectedProjectId}
              onChange={(e) => setSelectedProjectId(e.target.value)}
              className="bg-transparent text-xs font-mono text-slate-200 outline-none cursor-pointer"
            >
              {projects.map((p) => (
                <option key={p.id} value={p.id} className="bg-slate-900 text-slate-200">
                  {p.name}
                </option>
              ))}
            </select>
          </div>

          <button
            type="button"
            onClick={() => selectedProjectId && loadProjectData(selectedProjectId)}
            disabled={refreshing}
            className="btn-secondary text-xs font-mono flex items-center gap-1.5"
            title="Refresh Monitoring Telemetry"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin text-cyan-400' : ''}`} />
            <span>Refresh</span>
          </button>

          <button
            type="button"
            onClick={() => setIsCreateModalOpen(true)}
            className="btn-primary text-xs font-mono flex items-center gap-1.5"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>New Health Check</span>
          </button>
        </div>
      </div>

      {/* Global Alerts & Notifications */}
      {error && <ErrorBanner message={error} onRetry={() => selectedProjectId && loadProjectData(selectedProjectId)} />}
      {successMessage && (
        <div className="p-3 rounded-lg bg-emerald-950/40 border border-emerald-500/30 text-emerald-300 text-xs font-mono flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{successMessage}</span>
        </div>
      )}

      {/* High-Level Reliability KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Active Incidents */}
        <div className="glass-panel p-4 relative overflow-hidden">
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-mono uppercase tracking-wider text-slate-400">Active Outages</span>
            <div className={`w-7 h-7 rounded-lg flex items-center justify-center ${
              (dashboard?.activeIncidentsCount ?? 0) > 0 ? 'bg-rose-500/15 text-rose-400' : 'bg-emerald-500/10 text-emerald-400'
            }`}>
              <AlertOctagon className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mb-1">
            <span className={`text-2xl font-bold font-mono ${
              (dashboard?.activeIncidentsCount ?? 0) > 0 ? 'text-rose-400' : 'text-slate-100'
            }`}>
              {dashboard?.activeIncidentsCount ?? 0}
            </span>
            {(dashboard?.activeIncidentsCount ?? 0) > 0 && (
              <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono bg-rose-500/20 text-rose-300 animate-pulse">
                Action Required
              </span>
            )}
          </div>
          <span className="text-[11px] text-slate-400 font-mono">
            {dashboard?.totalIncidents ?? 0} total lifetime incidents
          </span>
          <div className={`absolute bottom-0 left-0 right-0 h-0.5 ${
            (dashboard?.activeIncidentsCount ?? 0) > 0 ? 'bg-rose-500' : 'bg-emerald-500'
          }`} />
        </div>

        {/* Monitored Endpoints */}
        <div className="glass-panel p-4 relative overflow-hidden">
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-mono uppercase tracking-wider text-slate-400">Monitored Endpoints</span>
            <div className="w-7 h-7 rounded-lg bg-cyan-500/10 text-cyan-400 flex items-center justify-center">
              <Activity className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mb-1">
            <span className="text-2xl font-bold text-slate-100 font-mono">
              {dashboard?.totalMonitoredEndpoints ?? healthChecks.length}
            </span>
            <span className="text-xs text-slate-400 font-mono">probes</span>
          </div>
          <div className="flex items-center gap-2 text-[11px] font-mono">
            <span className="text-emerald-400">{dashboard?.healthyEndpoints ?? 0} Healthy</span>
            <span className="text-slate-600">|</span>
            <span className="text-rose-400">{dashboard?.downEndpoints ?? 0} Down</span>
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-cyan-500" />
        </div>

        {/* Project Uptime SLA */}
        <div className="glass-panel p-4 relative overflow-hidden">
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-mono uppercase tracking-wider text-slate-400">Average Uptime SLA</span>
            <div className="w-7 h-7 rounded-lg bg-blue-500/10 text-blue-400 flex items-center justify-center">
              <Clock className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mb-1">
            <span className="text-2xl font-bold text-slate-100 font-mono">
              {dashboard?.overallUptimePercentage != null ? `${dashboard.overallUptimePercentage}%` : 'Collecting...'}
            </span>
          </div>
          <span className="text-[11px] text-slate-400 font-mono">
            {dashboard?.overallUptimePercentage != null ? 'Persisted probe metrics' : 'Min 3 probes required'}
          </span>
          <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-blue-500" />
        </div>

        {/* Alert Center Feed */}
        <div className="glass-panel p-4 relative overflow-hidden">
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-mono uppercase tracking-wider text-slate-400">In-App Alerts</span>
            <div className={`w-7 h-7 rounded-lg flex items-center justify-center ${
              unreadAlertsCount > 0 ? 'bg-amber-500/15 text-amber-400' : 'bg-slate-800 text-slate-400'
            }`}>
              <Bell className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mb-1">
            <span className="text-2xl font-bold text-slate-100 font-mono">
              {unreadAlertsCount}
            </span>
            <span className="text-xs text-slate-400 font-mono">unread</span>
          </div>
          <span className="text-[11px] text-slate-400 font-mono">
            {alerts.length} total event notifications
          </span>
          <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-amber-500" />
        </div>
      </div>

      {/* Navigation Sub-Tabs */}
      <div className="flex items-center border-b border-white/5 gap-2">
        <button
          type="button"
          onClick={() => setActiveTab('incidents')}
          className={`px-4 py-2.5 text-xs font-mono font-medium border-b-2 transition-all flex items-center gap-2 ${
            activeTab === 'incidents'
              ? 'border-rose-500 text-rose-400 bg-rose-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <AlertOctagon className="w-3.5 h-3.5" />
          <span>Active & Historical Incidents ({incidents.length})</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('checks')}
          className={`px-4 py-2.5 text-xs font-mono font-medium border-b-2 transition-all flex items-center gap-2 ${
            activeTab === 'checks'
              ? 'border-cyan-500 text-cyan-400 bg-cyan-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Sliders className="w-3.5 h-3.5" />
          <span>Configured Health Checks ({healthChecks.length})</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('alerts')}
          className={`px-4 py-2.5 text-xs font-mono font-medium border-b-2 transition-all flex items-center gap-2 ${
            activeTab === 'alerts'
              ? 'border-amber-500 text-amber-400 bg-amber-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Bell className="w-3.5 h-3.5" />
          <span>In-App Alert Center</span>
          {unreadAlertsCount > 0 && (
            <span className="px-1.5 py-0.2 rounded-full text-[10px] font-mono bg-amber-500/20 text-amber-300 font-bold">
              {unreadAlertsCount}
            </span>
          )}
        </button>
      </div>

      {/* TAB 1: INCIDENTS VIEW */}
      {activeTab === 'incidents' && (
        <div className="space-y-4">
          {/* Filter Bar */}
          <div className="glass-panel p-3 flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2 flex-1 min-w-[240px]">
              <Search className="w-3.5 h-3.5 text-slate-400 ml-1" />
              <input
                type="text"
                placeholder="Search incidents by endpoint, title, or root cause..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="bg-transparent text-xs text-slate-200 placeholder-slate-500 outline-none w-full font-mono"
              />
            </div>

            <div className="flex items-center gap-3">
              {/* Status Filter */}
              <div className="flex items-center gap-1.5 text-xs font-mono">
                <span className="text-slate-400">Status:</span>
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value as any)}
                  className="bg-[#0b101b] border border-white/10 rounded px-2 py-1 text-slate-200 outline-none"
                >
                  <option value="ALL">All Statuses</option>
                  <option value="TRIGGERED">TRIGGERED</option>
                  <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
                  <option value="RESOLVED">RESOLVED</option>
                </select>
              </div>

              {/* Severity Filter */}
              <div className="flex items-center gap-1.5 text-xs font-mono">
                <span className="text-slate-400">Severity:</span>
                <select
                  value={severityFilter}
                  onChange={(e) => setSeverityFilter(e.target.value as any)}
                  className="bg-[#0b101b] border border-white/10 rounded px-2 py-1 text-slate-200 outline-none"
                >
                  <option value="ALL">All Severities</option>
                  <option value="CRITICAL">CRITICAL</option>
                  <option value="MAJOR">MAJOR</option>
                  <option value="MINOR">MINOR</option>
                </select>
              </div>
            </div>
          </div>

          {/* Incidents Table / List */}
          {filteredIncidents.length === 0 ? (
            <div className="glass-panel p-10 text-center space-y-3">
              <div className="w-12 h-12 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center mx-auto">
                <CheckCircle2 className="w-6 h-6" />
              </div>
              <h3 className="text-sm font-semibold text-slate-200 font-mono">Zero Incidents Reported</h3>
              <p className="text-xs text-slate-400 max-w-md mx-auto">
                {incidents.length === 0
                  ? 'All monitored endpoints are operating within defined thresholds. Incidents are automatically triggered when consecutive probe failures cross threshold.'
                  : 'No incidents match the active search and status filters.'}
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {filteredIncidents.map((incident) => {
                const isTriggered = incident.status === 'TRIGGERED';
                const isAcknowledged = incident.status === 'ACKNOWLEDGED';
                const isResolved = incident.status === 'RESOLVED';

                return (
                  <div
                    key={incident.id}
                    className={`glass-panel p-4 border-l-4 transition-all ${
                      isTriggered
                        ? 'border-l-rose-500 bg-rose-950/10'
                        : isAcknowledged
                        ? 'border-l-amber-500 bg-amber-950/10'
                        : 'border-l-emerald-500'
                    }`}
                  >
                    <div className="flex flex-col md:flex-row md:items-start justify-between gap-4">
                      {/* Left Side: Details */}
                      <div className="space-y-2 flex-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          {/* Severity Badge */}
                          <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold uppercase tracking-wider ${
                            incident.severity === 'CRITICAL'
                              ? 'bg-rose-500/20 text-rose-300 border border-rose-500/30'
                              : incident.severity === 'MAJOR'
                              ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                              : 'bg-blue-500/20 text-blue-300 border border-blue-500/30'
                          }`}>
                            {incident.severity}
                          </span>

                          {/* Status Badge */}
                          <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold flex items-center gap-1.5 ${
                            isTriggered
                              ? 'bg-rose-500/20 text-rose-300 border border-rose-500/40 animate-pulse'
                              : isAcknowledged
                              ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40'
                              : 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40'
                          }`}>
                            {isTriggered && <span className="w-1.5 h-1.5 rounded-full bg-rose-400" />}
                            {incident.status}
                          </span>

                          {/* Endpoint HTTP Badge */}
                          <div className="flex items-center gap-1.5 bg-[#0a0f19] border border-white/10 rounded px-2 py-0.5 text-xs font-mono">
                            <span className="text-cyan-400 font-bold">{incident.httpMethod}</span>
                            <span className="text-slate-300 truncate max-w-[280px]">{incident.endpointUrl}</span>
                          </div>

                          <span className="text-xs text-slate-400 font-mono">
                            Check: <strong className="text-slate-200">{incident.healthCheckName}</strong>
                          </span>
                        </div>

                        {/* Title & Description */}
                        <div>
                          <h4 className="text-sm font-semibold text-slate-100 font-mono">
                            {incident.title}
                          </h4>
                          <p className="text-xs text-slate-300 mt-0.5 leading-relaxed font-sans">
                            {incident.description}
                          </p>
                        </div>

                        {/* Root Cause / Resolution notes */}
                        {incident.rootCause && (
                          <div className="p-2.5 rounded bg-black/30 border border-white/5 text-xs font-mono">
                            <span className="text-slate-400 block mb-0.5 font-bold">Root Cause / Recovery Note:</span>
                            <span className="text-emerald-300">{incident.rootCause}</span>
                          </div>
                        )}

                        {/* Metadata Footer */}
                        <div className="flex items-center gap-4 text-[11px] text-slate-400 font-mono pt-1">
                          <span>Started: {new Date(incident.startedAt).toLocaleString()}</span>
                          {incident.acknowledgedAt && (
                            <span>Ack: {new Date(incident.acknowledgedAt).toLocaleTimeString()} ({incident.acknowledgedByUsername || 'Operator'})</span>
                          )}
                          {incident.resolvedAt && (
                            <span className="text-emerald-400">
                              Resolved: {new Date(incident.resolvedAt).toLocaleTimeString()} ({incident.resolvedByUsername || 'Auto-recovered'})
                            </span>
                          )}
                        </div>
                      </div>

                      {/* Right Side: Quick Action Buttons */}
                      <div className="flex items-center gap-2 shrink-0">
                        {isTriggered && (
                          <button
                            type="button"
                            onClick={() => handleAcknowledge(incident.id)}
                            disabled={actionLoading}
                            className="px-3 py-1.5 rounded-lg bg-amber-500/15 border border-amber-500/30 hover:bg-amber-500/25 text-amber-300 text-xs font-mono transition-all flex items-center gap-1.5"
                          >
                            <Check className="w-3.5 h-3.5" />
                            <span>Acknowledge</span>
                          </button>
                        )}

                        {!isResolved && (
                          <button
                            type="button"
                            onClick={() => handleOpenResolveModal(incident)}
                            disabled={actionLoading}
                            className="px-3 py-1.5 rounded-lg bg-emerald-500/15 border border-emerald-500/30 hover:bg-emerald-500/25 text-emerald-300 text-xs font-mono transition-all flex items-center gap-1.5"
                          >
                            <CheckCheck className="w-3.5 h-3.5" />
                            <span>Resolve</span>
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* TAB 2: CONFIGURED HEALTH CHECKS */}
      {activeTab === 'checks' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-xs text-slate-400">
              Probes are dispatched on bounded worker threads with strict per-user quotas, timeouts, and SSRF address validation.
            </p>
            <button
              type="button"
              onClick={() => setIsCreateModalOpen(true)}
              className="btn-primary text-xs font-mono flex items-center gap-1.5"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add Health Check</span>
            </button>
          </div>

          {healthChecks.length === 0 ? (
            <div className="glass-panel p-10 text-center space-y-3">
              <div className="w-12 h-12 rounded-xl bg-cyan-500/10 text-cyan-400 flex items-center justify-center mx-auto">
                <Sliders className="w-6 h-6" />
              </div>
              <h3 className="text-sm font-semibold text-slate-200 font-mono">No Health Checks Configured</h3>
              <p className="text-xs text-slate-400 max-w-md mx-auto">
                Configure health-check schedules to continuously verify uptime, monitor latency percentiles (p50/p95/p99), and auto-trigger incidents.
              </p>
              <button
                type="button"
                onClick={() => setIsCreateModalOpen(true)}
                className="btn-primary text-xs font-mono inline-flex items-center gap-1.5 mt-2"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Configure First Check</span>
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-4">
              {healthChecks.map((check) => {
                const metrics = check.metrics;
                const isHealthy = check.lastStatus === 'HEALTHY';
                const isDegraded = check.lastStatus === 'DEGRADED';
                const isDown = check.lastStatus === 'DOWN';

                return (
                  <div key={check.id} className="glass-panel p-5 space-y-4">
                    <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
                      <div>
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold ${
                            isHealthy
                              ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                              : isDegraded
                              ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                              : isDown
                              ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                              : 'bg-slate-700 text-slate-300'
                          }`}>
                            {check.lastStatus}
                          </span>

                          <span className="text-sm font-bold text-slate-100 font-mono">
                            {check.name}
                          </span>

                          <div className="flex items-center gap-1 bg-[#0a0f19] border border-white/10 rounded px-2 py-0.5 text-xs font-mono">
                            <span className="text-cyan-400 font-bold">{check.httpMethod}</span>
                            <span className="text-slate-300">{check.endpointUrl}</span>
                          </div>
                        </div>

                        <p className="text-xs text-slate-400 mt-1">
                          Monitors endpoint: <strong className="text-slate-300">{check.endpointName}</strong>
                        </p>
                      </div>

                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => handleTriggerCheck(check.id)}
                          disabled={actionLoading}
                          className="btn-secondary text-xs font-mono flex items-center gap-1.5"
                          title="Execute Probe Immediately"
                        >
                          <Play className="w-3.5 h-3.5 text-cyan-400" />
                          <span>Run Probe</span>
                        </button>

                        <button
                          type="button"
                          onClick={() => handleOpenHistory(check)}
                          className="btn-secondary text-xs font-mono flex items-center gap-1.5"
                          title="View Probe History"
                        >
                          <History className="w-3.5 h-3.5 text-slate-400" />
                          <span>History</span>
                        </button>
                      </div>
                    </div>

                    {/* Schedule & Threshold Specs */}
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 p-3 rounded-lg bg-black/20 border border-white/5 text-xs font-mono">
                      <div>
                        <span className="text-slate-500 block text-[10px]">POLL INTERVAL</span>
                        <span className="text-slate-200">{check.intervalSeconds}s</span>
                      </div>
                      <div>
                        <span className="text-slate-500 block text-[10px]">TIMEOUT BUDGET</span>
                        <span className="text-slate-200">{check.timeoutMs}ms</span>
                      </div>
                      <div>
                        <span className="text-slate-500 block text-[10px]">FAILURE THRESHOLD</span>
                        <span className="text-rose-400">{check.failureThreshold} consecutive</span>
                      </div>
                      <div>
                        <span className="text-slate-500 block text-[10px]">RECOVERY THRESHOLD</span>
                        <span className="text-emerald-400">{check.recoveryThreshold} consecutive</span>
                      </div>
                    </div>

                    {/* Live Telemetry Metrics */}
                    <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 text-xs font-mono">
                      <div className="p-2.5 rounded bg-white/[0.02] border border-white/5">
                        <span className="text-slate-500 block text-[10px]">UPTIME SLA</span>
                        <span className="text-slate-200 font-bold">
                          {metrics?.uptimePercentage != null ? `${metrics.uptimePercentage}%` : 'Collecting...'}
                        </span>
                      </div>
                      <div className="p-2.5 rounded bg-white/[0.02] border border-white/5">
                        <span className="text-slate-500 block text-[10px]">AVG LATENCY</span>
                        <span className="text-slate-200">
                          {metrics?.averageLatencyMs != null ? `${metrics.averageLatencyMs}ms` : '—'}
                        </span>
                      </div>
                      <div className="p-2.5 rounded bg-white/[0.02] border border-white/5">
                        <span className="text-slate-500 block text-[10px]">p50 LATENCY</span>
                        <span className="text-slate-200">
                          {metrics?.p50LatencyMs != null ? `${metrics.p50LatencyMs}ms` : '—'}
                        </span>
                      </div>
                      <div className="p-2.5 rounded bg-white/[0.02] border border-white/5">
                        <span className="text-slate-500 block text-[10px]">p95 LATENCY</span>
                        <span className="text-slate-200">
                          {metrics?.p95LatencyMs != null ? `${metrics.p95LatencyMs}ms` : '—'}
                        </span>
                      </div>
                      <div className="p-2.5 rounded bg-white/[0.02] border border-white/5">
                        <span className="text-slate-500 block text-[10px]">SUCCESS RATE</span>
                        <span className="text-emerald-400">
                          {metrics ? `${metrics.successCount}/${metrics.totalChecks}` : '0/0'}
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center justify-between text-[11px] text-slate-500 font-mono">
                      <span>Last checked: {check.lastCheckedAt ? new Date(check.lastCheckedAt).toLocaleTimeString() : 'Never'}</span>
                      <span>Next probe scheduled: {check.nextCheckAt ? new Date(check.nextCheckAt).toLocaleTimeString() : 'Pending'}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* TAB 3: IN-APP ALERT CENTER */}
      {activeTab === 'alerts' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-xs text-slate-400">
              Safe development dispatching with optional SMTP/email delivery hook and in-app alert persistence.
            </p>
            <span className="text-xs font-mono text-cyan-400 bg-cyan-950/40 border border-cyan-500/20 px-2.5 py-1 rounded">
              Channel: IN_APP & LOGGING (Safe Dev Mode)
            </span>
          </div>

          {alerts.length === 0 ? (
            <div className="glass-panel p-10 text-center space-y-3">
              <div className="w-12 h-12 rounded-xl bg-slate-800 text-slate-400 flex items-center justify-center mx-auto">
                <Bell className="w-6 h-6" />
              </div>
              <h3 className="text-sm font-semibold text-slate-200 font-mono">No Alerts in Feed</h3>
              <p className="text-xs text-slate-400 max-w-md mx-auto">
                Alert notifications are emitted whenever an incident is triggered, escalated, or recovered.
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {alerts.map((alert) => (
                <div
                  key={alert.id}
                  className={`glass-panel p-4 transition-all flex items-start justify-between gap-4 ${
                    !alert.read ? 'border-amber-500/40 bg-amber-950/10' : 'opacity-80'
                  }`}
                >
                  <div className="space-y-1.5 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold uppercase ${
                        alert.severity === 'CRITICAL'
                          ? 'bg-rose-500/20 text-rose-300'
                          : alert.severity === 'MAJOR'
                          ? 'bg-amber-500/20 text-amber-300'
                          : 'bg-blue-500/20 text-blue-300'
                      }`}>
                        {alert.severity}
                      </span>

                      <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-white/5 text-slate-300">
                        {alert.channel}
                      </span>

                      {!alert.read && (
                        <span className="w-2 h-2 rounded-full bg-amber-400" title="Unread" />
                      )}

                      <span className="text-xs text-slate-400 font-mono">
                        {new Date(alert.sentAt).toLocaleString()}
                      </span>
                    </div>

                    <h4 className="text-sm font-semibold text-slate-200 font-mono">
                      {alert.title}
                    </h4>
                    <p className="text-xs text-slate-300 leading-relaxed font-sans">
                      {alert.message}
                    </p>
                  </div>

                  {!alert.read && (
                    <button
                      type="button"
                      onClick={() => handleMarkAlertRead(alert.id)}
                      className="px-2.5 py-1 rounded bg-white/5 hover:bg-white/10 text-xs font-mono text-slate-300 transition-colors shrink-0"
                    >
                      Mark Read
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* CREATE HEALTH CHECK MODAL */}
      {isCreateModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="glass-panel w-full max-w-lg p-6 space-y-5 border-white/20 bg-[#0d131f] shadow-2xl">
            <div className="flex items-center justify-between border-b border-white/10 pb-3">
              <h3 className="text-base font-bold text-white font-mono flex items-center gap-2">
                <Sliders className="w-4 h-4 text-cyan-400" />
                Configure Health Check Schedule
              </h3>
              <button
                type="button"
                onClick={() => setIsCreateModalOpen(false)}
                className="text-slate-400 hover:text-white"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleCreateHealthCheck} className="space-y-4">
              <div>
                <label className="block text-xs font-mono text-slate-300 mb-1">
                  Health Check Name
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Auth Service Ping"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  className="w-full bg-[#070b12] border border-white/10 rounded-lg px-3 py-2 text-xs font-mono text-slate-200 outline-none focus:border-cyan-500"
                />
              </div>

              <div>
                <label className="block text-xs font-mono text-slate-300 mb-1">
                  Target Endpoint
                </label>
                <select
                  required
                  value={formData.endpointId}
                  onChange={(e) => setFormData({ ...formData, endpointId: e.target.value })}
                  className="w-full bg-[#070b12] border border-white/10 rounded-lg px-3 py-2 text-xs font-mono text-slate-200 outline-none focus:border-cyan-500"
                >
                  <option value="">Select an endpoint...</option>
                  {projectEndpoints.map((ep) => (
                    <option key={ep.id} value={ep.id}>
                      [{ep.httpMethod}] {ep.name} - {ep.url}
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-mono text-slate-300 mb-1">
                    Poll Interval (seconds)
                  </label>
                  <input
                    type="number"
                    min="5"
                    max="3600"
                    value={formData.intervalSeconds}
                    onChange={(e) => setFormData({ ...formData, intervalSeconds: parseInt(e.target.value) || 10 })}
                    className="w-full bg-[#070b12] border border-white/10 rounded-lg px-3 py-2 text-xs font-mono text-slate-200 outline-none focus:border-cyan-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-mono text-slate-300 mb-1">
                    Timeout Budget (ms)
                  </label>
                  <input
                    type="number"
                    min="500"
                    max="30000"
                    value={formData.timeoutMs}
                    onChange={(e) => setFormData({ ...formData, timeoutMs: parseInt(e.target.value) || 3000 })}
                    className="w-full bg-[#070b12] border border-white/10 rounded-lg px-3 py-2 text-xs font-mono text-slate-200 outline-none focus:border-cyan-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-mono text-slate-300 mb-1">
                    Expected Status
                  </label>
                  <input
                    type="number"
                    value={formData.expectedStatus}
                    onChange={(e) => setFormData({ ...formData, expectedStatus: parseInt(e.target.value) || 200 })}
                    className="w-full bg-[#070b12] border border-white/10 rounded-lg px-3 py-2 text-xs font-mono text-slate-200 outline-none focus:border-cyan-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-mono text-slate-300 mb-1">
                    Failure Threshold
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="20"
                    value={formData.failureThreshold}
                    onChange={(e) => setFormData({ ...formData, failureThreshold: parseInt(e.target.value) || 3 })}
                    className="w-full bg-[#070b12] border border-white/10 rounded-lg px-3 py-2 text-xs font-mono text-slate-200 outline-none focus:border-cyan-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-mono text-slate-300 mb-1">
                    Recovery Threshold
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="20"
                    value={formData.recoveryThreshold}
                    onChange={(e) => setFormData({ ...formData, recoveryThreshold: parseInt(e.target.value) || 2 })}
                    className="w-full bg-[#070b12] border border-white/10 rounded-lg px-3 py-2 text-xs font-mono text-slate-200 outline-none focus:border-cyan-500"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-white/10">
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="btn-secondary text-xs font-mono"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="btn-primary text-xs font-mono"
                >
                  {actionLoading ? 'Scheduling...' : 'Create Health Check'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* RESOLVE INCIDENT MODAL */}
      {isResolveModalOpen && selectedIncidentForResolve && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="glass-panel w-full max-w-md p-6 space-y-4 border-white/20 bg-[#0d131f] shadow-2xl">
            <div className="flex items-center justify-between border-b border-white/10 pb-3">
              <h3 className="text-sm font-bold text-white font-mono flex items-center gap-2">
                <CheckCheck className="w-4 h-4 text-emerald-400" />
                Resolve Incident & Mark Restored
              </h3>
              <button
                type="button"
                onClick={() => setIsResolveModalOpen(false)}
                className="text-slate-400 hover:text-white"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <p className="text-xs text-slate-300">
              Resolving incident for: <strong className="text-white">{selectedIncidentForResolve.endpointName}</strong> ({selectedIncidentForResolve.title})
            </p>

            <div>
              <label className="block text-xs font-mono text-slate-300 mb-1">
                Root Cause & Remediation Notes (Optional)
              </label>
              <textarea
                rows={3}
                placeholder="e.g. Upstream database pool exhaustion resolved; connection leak fixed."
                value={resolveRootCause}
                onChange={(e) => setResolveRootCause(e.target.value)}
                className="w-full bg-[#070b12] border border-white/10 rounded-lg p-2.5 text-xs font-mono text-slate-200 outline-none focus:border-emerald-500"
              />
            </div>

            <div className="flex items-center justify-end gap-3 pt-2 border-t border-white/10">
              <button
                type="button"
                onClick={() => setIsResolveModalOpen(false)}
                className="btn-secondary text-xs font-mono"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmResolve}
                disabled={actionLoading}
                className="px-3.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-mono font-medium transition-colors"
              >
                {actionLoading ? 'Resolving...' : 'Confirm Resolution'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* PROBE EXECUTION HISTORY DRAWER */}
      {isHistoryDrawerOpen && selectedCheckForHistory && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/60 backdrop-blur-sm">
          <div className="w-full max-w-xl h-full bg-[#0a0e17] border-l border-white/10 p-6 flex flex-col space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b border-white/10 pb-3">
              <div>
                <h3 className="text-sm font-bold text-white font-mono flex items-center gap-2">
                  <History className="w-4 h-4 text-cyan-400" />
                  Health Probe History
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  {selectedCheckForHistory.name} ({selectedCheckForHistory.endpointUrl})
                </p>
              </div>
              <button
                type="button"
                onClick={() => setIsHistoryDrawerOpen(false)}
                className="text-slate-400 hover:text-white"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto space-y-2 pr-1">
              {loadingHistory ? (
                <LoadingSpinner message="Fetching persisted probe records..." />
              ) : historyRecords.length === 0 ? (
                <p className="text-xs text-slate-400 text-center py-8">
                  No probe records recorded yet for this health check.
                </p>
              ) : (
                historyRecords.map((rec) => (
                  <div
                    key={rec.id}
                    className={`p-3 rounded-lg border text-xs font-mono flex items-center justify-between ${
                      rec.success
                        ? 'bg-emerald-950/20 border-emerald-500/20 text-emerald-300'
                        : 'bg-rose-950/20 border-rose-500/20 text-rose-300'
                    }`}
                  >
                    <div className="space-y-0.5">
                      <div className="flex items-center gap-2">
                        <span className={`px-1.5 py-0.2 rounded text-[10px] font-bold ${
                          rec.success ? 'bg-emerald-500/20 text-emerald-300' : 'bg-rose-500/20 text-rose-300'
                        }`}>
                          HTTP {rec.statusCode || 'ERR'}
                        </span>
                        <span className="text-slate-200">{rec.responseTimeMs}ms</span>
                      </div>
                      {rec.errorMessage && (
                        <p className="text-[11px] text-rose-400 font-sans mt-0.5">
                          {rec.errorMessage}
                        </p>
                      )}
                    </div>
                    <span className="text-[10px] text-slate-500">
                      {new Date(rec.executedAt).toLocaleTimeString()}
                    </span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
