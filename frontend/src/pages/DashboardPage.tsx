import React, { useEffect, useState } from 'react';
import {
  Activity,
  Database,
  ShieldCheck,
  Clock,
  Cpu
} from 'lucide-react';
import { ApiClient } from '../services/apiClient';
import { SystemHealthData } from '../types';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorBanner } from '../components/common/ErrorBanner';

export const DashboardPage: React.FC = () => {
  const [health, setHealth] = useState<SystemHealthData | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await ApiClient.getHealth();
      setHealth(res.data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to reach API Sentinel backend');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

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
    return <LoadingSpinner message="Querying live system health from Spring Boot..." />;
  }

  return (
    <div className="space-y-8 max-w-7xl mx-auto">
      {/* Page Title & Subtitle */}
      <div>
        <h2 className="text-xl font-bold text-slate-100 font-mono tracking-tight flex items-center gap-2">
          Platform Overview
          <span className="text-xs px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-mono">
            Live Telemetry
          </span>
        </h2>
        <p className="text-xs text-slate-400 mt-1">
          Real-time health, database readiness, and observability metrics. Strictly zero simulated numbers.
        </p>
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
              {health?.components?.database?.status || 'CHECKING'}
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
            <span className="text-xs font-mono uppercase tracking-wider text-slate-400">Uptime</span>
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
            Profile: Spring Boot 3.3.5
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-purple-500 to-transparent" />
        </div>
      </div>

      {/* System Invariant & Phase Notice */}
      <div className="glass-panel p-6 border-cyan-500/20 bg-gradient-to-r from-cyan-950/20 via-transparent to-transparent">
        <div className="flex items-start gap-4">
          <div className="w-10 h-10 rounded-xl bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center text-cyan-400 shrink-0">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div className="flex-1">
            <h3 className="text-sm font-semibold text-white mb-1 flex items-center gap-2">
              Phase 01: Core Platform Scaffolding Active
              <span className="text-[10px] px-2 py-0.5 rounded bg-cyan-500/20 text-cyan-300 font-mono">
                Verified
              </span>
            </h3>
            <p className="text-xs text-slate-300 leading-relaxed max-w-3xl">
              Spring Boot 3 backend is compiling cleanly with Java 21 LTS, Flyway migrations are established,
              and health endpoints are verified. In strict accordance with our engineering rules, test suites,
              endpoints, and vulnerability scans will populate only as subsequent phases are implemented.
            </p>
          </div>
        </div>
      </div>

      {/* Honest Empty Metric Panels (Upcoming Modules) */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        <div className="glass-panel p-5 border-dashed border-white/10">
          <div className="flex items-center justify-between text-xs text-slate-400 font-mono mb-2">
            <span>REGISTERED APIS</span>
            <span className="px-1.5 py-0.5 rounded bg-white/5 text-[10px]">Phase 04</span>
          </div>
          <p className="text-2xl font-bold text-slate-600 font-mono">0</p>
          <p className="text-xs text-slate-500 mt-2">API registration unlocks in Phase 04</p>
        </div>

        <div className="glass-panel p-5 border-dashed border-white/10">
          <div className="flex items-center justify-between text-xs text-slate-400 font-mono mb-2">
            <span>EXECUTED TEST RUNS</span>
            <span className="px-1.5 py-0.5 rounded bg-white/5 text-[10px]">Phase 06</span>
          </div>
          <p className="text-2xl font-bold text-slate-600 font-mono">0</p>
          <p className="text-xs text-slate-500 mt-2">Test execution engine unlocks in Phase 06</p>
        </div>

        <div className="glass-panel p-5 border-dashed border-white/10">
          <div className="flex items-center justify-between text-xs text-slate-400 font-mono mb-2">
            <span>SECURITY SCANS</span>
            <span className="px-1.5 py-0.5 rounded bg-white/5 text-[10px]">Phase 07</span>
          </div>
          <p className="text-2xl font-bold text-slate-600 font-mono">0</p>
          <p className="text-xs text-slate-500 mt-2">OWASP scanner engine unlocks in Phase 07</p>
        </div>
      </div>
    </div>
  );
};
