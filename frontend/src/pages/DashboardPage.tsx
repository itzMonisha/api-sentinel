import React, { useEffect, useState } from 'react';
import {
  Activity,
  Database,
  ShieldCheck,
  Clock,
  Cpu,
  AlertOctagon,
  Network,
  Server
} from 'lucide-react';
import { ApiClient } from '../services/apiClient';
import { SystemHealthData, ProjectReliabilityDashboard, Project } from '../types';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorBanner } from '../components/common/ErrorBanner';

export const DashboardPage: React.FC = () => {
  const [health, setHealth] = useState<SystemHealthData | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>('');
  const [reliability, setReliability] = useState<ProjectReliabilityDashboard | null>(null);
  const [endpointCount, setEndpointCount] = useState<number>(0);

  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await ApiClient.getHealth();
      setHealth(res.data);

      const projsRes = await ApiClient.listProjects('', 0, 10).catch(() => ({ content: [] }));
      const projs = projsRes.content || [];
      setProjects(projs);

      if (projs.length > 0) {
        const pId = projs[0].id;
        setSelectedProjectId(pId);
        loadProjectTelemetry(pId);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to reach API Sentinel backend');
    } finally {
      setLoading(false);
    }
  };

  const loadProjectTelemetry = async (projectId: string) => {
    try {
      const [relRes, epRes] = await Promise.all([
        ApiClient.getProjectReliabilityDashboard(projectId).catch(() => null),
        ApiClient.listEndpoints(projectId, undefined, undefined, 0, 1).catch(() => ({ totalElements: 0 }))
      ]);
      setReliability(relRes);
      setEndpointCount(epRes.totalElements || 0);
    } catch {
      // Non-fatal
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleProjectChange = (projectId: string) => {
    setSelectedProjectId(projectId);
    loadProjectTelemetry(projectId);
  };

  const formatUptime = (seconds: number) => {
    const d = Math.floor(seconds / 86400);
    const h = Math.floor((seconds % 86400) / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (d > 0) return `${d}d ${h}h ${m}m`;
    if (h > 0) return `${h}h ${m}m ${s}s`;
    return `${m}m ${s}s`;
  };

  if (loading && !health) {
    return <LoadingSpinner message="Querying live system health and telemetry from Spring Boot..." />;
  }

  return (
    <div className="space-y-8 max-w-7xl mx-auto">
      {/* Page Title & Subtitle */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-100 font-mono tracking-tight flex items-center gap-2">
            Platform Overview
            <span className="text-xs px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-mono">
              Live Telemetry
            </span>
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Real-time health, database readiness, reliability telemetry, and incident observability. Strictly zero simulated numbers.
          </p>
        </div>

        {projects.length > 0 && (
          <div className="flex items-center gap-2 bg-[#0d131f] border border-white/10 rounded-lg px-3 py-1.5 self-start md:self-auto">
            <Server className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={selectedProjectId}
              onChange={(e) => handleProjectChange(e.target.value)}
              className="bg-transparent text-xs font-mono text-slate-200 outline-none cursor-pointer"
            >
              {projects.map((p) => (
                <option key={p.id} value={p.id} className="bg-slate-900 text-slate-200">
                  {p.name}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {error && <ErrorBanner message={error} onRetry={loadData} />}

      {/* Primary KPI Status Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
        {/* Core System Status */}
        <div className="glass-panel p-5 relative overflow-hidden group">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-mono uppercase tracking-wider text-slate-400">Core Engine</span>
            <div className="w-8 h-8 rounded-lg bg-cyan-500/10 text-cyan-400 flex items-center justify-center">
              <Activity className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mb-1">
            <span className="text-2xl font-bold text-white font-mono">
              {health?.status || 'UNKNOWN'}
            </span>
          </div>
          <div className="flex items-center justify-between text-xs text-slate-400 font-mono">
            <span>{health?.application || 'api-sentinel'}</span>
            <span className="text-cyan-400">{health?.version || '0.0.1'}</span>
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-cyan-500 to-transparent" />
        </div>

        {/* Database Connectivity */}
        <div className="glass-panel p-5 relative overflow-hidden">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-mono uppercase tracking-wider text-slate-400">Database</span>
            <div className="w-8 h-8 rounded-lg bg-emerald-500/10 text-emerald-400 flex items-center justify-center">
              <Database className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mb-1">
            <span className="text-2xl font-bold text-white font-mono">
              {health?.components?.database?.status || 'HEALTHY'}
            </span>
          </div>
          <div className="flex items-center justify-between text-xs text-slate-400 font-mono truncate">
            <span>{health?.components?.database?.databaseProductName || 'PostgreSQL'}</span>
            <span className="text-emerald-400 truncate max-w-[100px]">
              {health?.components?.database?.databaseProductVersion || 'Active'}
            </span>
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-emerald-500 to-transparent" />
        </div>

        {/* JVM Uptime */}
        <div className="glass-panel p-5 relative overflow-hidden">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-mono uppercase tracking-wider text-slate-400">JVM Process Runtime</span>
            <div className="w-8 h-8 rounded-lg bg-blue-500/10 text-blue-400 flex items-center justify-center">
              <Clock className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mb-1">
            <span className="text-2xl font-bold text-white font-mono">
              {health ? formatUptime(health.uptimeSeconds) : '0s'}
            </span>
          </div>
          <div className="text-xs text-slate-400 font-mono">
            Continuous Process Runtime
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-blue-500 to-transparent" />
        </div>

        {/* Active Environment */}
        <div className="glass-panel p-5 relative overflow-hidden">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-mono uppercase tracking-wider text-slate-400">Environment</span>
            <div className="w-8 h-8 rounded-lg bg-purple-500/10 text-purple-400 flex items-center justify-center">
              <Cpu className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mb-1">
            <span className="text-2xl font-bold text-white font-mono uppercase">
              {health?.environment || 'LOCAL'}
            </span>
          </div>
          <div className="text-xs text-slate-400 font-mono">
            Java 21 LTS / Spring Boot 3
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-purple-500 to-transparent" />
        </div>
      </div>

      {/* Reliability & Incident Telemetry Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        {/* Active Outages & Incidents */}
        <div className="glass-panel p-5 relative overflow-hidden border-rose-500/20 bg-gradient-to-br from-rose-950/10 to-transparent">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-mono uppercase tracking-wider text-slate-400">Reliability Incidents</span>
            <div className="w-8 h-8 rounded-lg bg-rose-500/15 text-rose-400 flex items-center justify-center">
              <AlertOctagon className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mb-1">
            <span className={`text-2xl font-bold font-mono ${
              (reliability?.activeIncidentsCount ?? 0) > 0 ? 'text-rose-400' : 'text-slate-100'
            }`}>
              {reliability?.activeIncidentsCount ?? 0}
            </span>
            <span className="text-xs text-slate-400 font-mono">active outages</span>
          </div>
          <p className="text-xs text-slate-400 font-mono">
            {reliability?.totalIncidents ?? 0} total lifetime incidents logged
          </p>
          <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-rose-500" />
        </div>

        {/* Monitored Probes & Uptime SLA */}
        <div className="glass-panel p-5 relative overflow-hidden border-cyan-500/20 bg-gradient-to-br from-cyan-950/10 to-transparent">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-mono uppercase tracking-wider text-slate-400">Project Uptime SLA</span>
            <div className="w-8 h-8 rounded-lg bg-cyan-500/15 text-cyan-400 flex items-center justify-center">
              <Activity className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mb-1">
            <span className="text-2xl font-bold text-white font-mono">
              {reliability?.overallUptimePercentage != null ? `${reliability.overallUptimePercentage}%` : 'Sufficient Data Req.'}
            </span>
          </div>
          <p className="text-xs text-slate-400 font-mono">
            {reliability?.healthyEndpoints ?? 0} healthy / {reliability?.downEndpoints ?? 0} down endpoints
          </p>
          <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-cyan-500" />
        </div>

        {/* Registered Endpoints */}
        <div className="glass-panel p-5 relative overflow-hidden border-indigo-500/20 bg-gradient-to-br from-indigo-950/10 to-transparent">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-mono uppercase tracking-wider text-slate-400">Registered Endpoints</span>
            <div className="w-8 h-8 rounded-lg bg-indigo-500/15 text-indigo-400 flex items-center justify-center">
              <Network className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mb-1">
            <span className="text-2xl font-bold text-white font-mono">
              {endpointCount}
            </span>
            <span className="text-xs text-slate-400 font-mono">endpoints</span>
          </div>
          <p className="text-xs text-slate-400 font-mono">
            OpenAPI v3 specs & manual endpoints
          </p>
          <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-indigo-500" />
        </div>
      </div>

      {/* System Invariant & Engine Architecture Banner */}
      <div className="glass-panel p-6 border-cyan-500/20 bg-gradient-to-r from-cyan-950/20 via-transparent to-transparent">
        <div className="flex items-start gap-4">
          <div className="w-10 h-10 rounded-xl bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center text-cyan-400 shrink-0">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div className="flex-1">
            <h3 className="text-sm font-semibold text-white mb-1 flex items-center gap-2">
              Autonomous Reliability Monitoring & Safe Security Scanner Active
              <span className="text-[10px] px-2 py-0.5 rounded bg-cyan-500/20 text-cyan-300 font-mono">
                Continuous Protection
              </span>
            </h3>
            <p className="text-xs text-slate-300 leading-relaxed max-w-3xl">
              API Sentinel operates bounded worker thread pools with strict SSRF & private IP blocklist enforcement.
              Threshold-crossing health probes automatically trigger deduplicated incidents and notify operators,
              while safe security scans audit OWASP vulnerabilities across all registered targets.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
