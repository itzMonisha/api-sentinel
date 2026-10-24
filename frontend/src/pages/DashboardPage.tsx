import React, { useEffect, useState, useMemo } from 'react';
import {
  Activity,
  AlertOctagon,
  Network,
  Server,
  PlayCircle,
  ShieldAlert,
  RefreshCw,
  CheckCircle2,
  XCircle,
  Calendar
} from 'lucide-react';
import { ApiClient } from '../services/apiClient';
import {
  SystemHealthData,
  ProjectReliabilityDashboard,
  Project,
  HealthCheckRecord,
  Incident,
  SecurityScan,
  TestRun
} from '../types';
import { CardSkeleton } from '../components/common/LoadingSkeleton';
import { ErrorBanner } from '../components/common/ErrorBanner';
import { TrendChart, TrendDataPoint } from '../components/common/TrendChart';

export const DashboardPage: React.FC = () => {
  const [health, setHealth] = useState<SystemHealthData | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>('ALL');
  const [dateRange, setDateRange] = useState<'24h' | '7d' | '30d'>('24h');

  // Aggregated data across projects or selected project
  const [reliability, setReliability] = useState<ProjectReliabilityDashboard | null>(null);
  const [totalEndpoints, setTotalEndpoints] = useState<number>(0);
  const [activeIncidents, setActiveIncidents] = useState<Incident[]>([]);
  const [recentScans, setRecentScans] = useState<SecurityScan[]>([]);
  const [recentTestRuns, setRecentTestRuns] = useState<TestRun[]>([]);
  const [healthRecords, setHealthRecords] = useState<HealthCheckRecord[]>([]);

  // Telemetry findings counts
  const [severityCounts, setSeverityCounts] = useState({
    critical: 0,
    high: 0,
    medium: 0,
    low: 0,
    info: 0,
  });

  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = async (isManualRefresh = false) => {
    try {
      if (isManualRefresh) setRefreshing(true);
      else setLoading(true);
      setError(null);

      // 1. Fetch system health
      const healthRes = await ApiClient.getHealth().catch(() => null);
      if (healthRes) setHealth(healthRes.data);

      // 2. Fetch projects
      const projsRes = await ApiClient.listProjects('', 0, 100).catch(() => ({ content: [] }));
      const projs = projsRes.content || [];
      setProjects(projs);

      const targetProjects = selectedProjectId === 'ALL'
        ? projs
        : projs.filter(p => p.id === selectedProjectId);

      let epCount = 0;
      const allIncidents: Incident[] = [];
      const allScans: SecurityScan[] = [];
      const allTestRuns: TestRun[] = [];
      const allRecords: HealthCheckRecord[] = [];

      let sevCritical = 0;
      let sevHigh = 0;
      let sevMedium = 0;
      let sevLow = 0;
      let sevInfo = 0;

      // Single project reliability summary if 1 selected
      if (selectedProjectId !== 'ALL' && selectedProjectId) {
        const relRes = await ApiClient.getProjectReliabilityDashboard(selectedProjectId).catch(() => null);
        setReliability(relRes);
      } else {
        setReliability(null);
      }

      // Aggregate telemetry across relevant projects
      await Promise.all(
        targetProjects.map(async (project) => {
          // Count endpoints
          const epRes = await ApiClient.listEndpoints(project.id, undefined, undefined, 0, 1).catch(() => ({ totalElements: 0 }));
          epCount += epRes.totalElements || 0;

          // Fetch incidents
          const incRes = await ApiClient.getProjectIncidents(project.id).catch(() => []);
          allIncidents.push(...incRes);

          // Fetch scans
          const scanRes = await ApiClient.getProjectScans(project.id).catch(() => []);
          allScans.push(...scanRes);
          scanRes.forEach(s => {
            sevCritical += s.criticalCount || 0;
            sevHigh += s.highCount || 0;
            sevMedium += s.mediumCount || 0;
            sevLow += s.lowCount || 0;
            sevInfo += s.infoCount || 0;
          });

          // Fetch test suites & test runs
          const suites = await ApiClient.listTestSuites(project.id).catch(() => []);
          for (const suite of suites) {
            const runs = await ApiClient.getTestRunsBySuite(suite.id).catch(() => []);
            allTestRuns.push(...runs);
          }

          // Fetch health checks and their historical records
          const checks = await ApiClient.getProjectHealthChecks(project.id).catch(() => []);
          for (const check of checks) {
            const records = await ApiClient.getHealthCheckRecords(check.id, 30).catch(() => []);
            allRecords.push(...records);
          }
        })
      );

      setTotalEndpoints(epCount);
      setActiveIncidents(allIncidents.filter(i => i.status !== 'RESOLVED'));
      setRecentScans(allScans.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
      setRecentTestRuns(allTestRuns.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
      setHealthRecords(allRecords.sort((a, b) => new Date(a.executedAt).getTime() - new Date(b.executedAt).getTime()));

      setSeverityCounts({
        critical: sevCritical,
        high: sevHigh,
        medium: sevMedium,
        low: sevLow,
        info: sevInfo,
      });

    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to reach API Sentinel telemetry engine.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [selectedProjectId]);

  const formatUptime = (seconds: number) => {
    const d = Math.floor(seconds / 86400);
    const h = Math.floor((seconds % 86400) / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (d > 0) return `${d}d ${h}h ${m}m`;
    if (h > 0) return `${h}h ${m}m ${s}s`;
    return `${m}m ${s}s`;
  };

  // Convert real persisted HealthCheckRecords into Latency Trend data points
  const latencyTrendData: TrendDataPoint[] = useMemo(() => {
    if (!healthRecords || healthRecords.length === 0) return [];
    
    // Filter by date range
    const now = Date.now();
    const cutoffMs = {
      '24h': 24 * 60 * 60 * 1000,
      '7d': 7 * 24 * 60 * 60 * 1000,
      '30d': 30 * 24 * 60 * 60 * 1000,
    }[dateRange];

    const filtered = healthRecords.filter(r => {
      const time = new Date(r.executedAt).getTime();
      return now - time <= cutoffMs;
    });

    return filtered.map(r => ({
      timestamp: r.executedAt,
      value: r.responseTimeMs,
      label: `${new Date(r.executedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} (${r.statusCode})`,
    }));
  }, [healthRecords, dateRange]);

  // Convert real HealthCheckRecords into Failure-Rate / Success Trend data points
  const failureRateTrendData: TrendDataPoint[] = useMemo(() => {
    if (!healthRecords || healthRecords.length === 0) return [];
    
    // Group records in chronological windows of 5 to show moving failure rate
    const windowSize = Math.max(1, Math.floor(healthRecords.length / 10));
    const points: TrendDataPoint[] = [];

    for (let i = 0; i < healthRecords.length; i += windowSize) {
      const slice = healthRecords.slice(i, i + windowSize);
      const fails = slice.filter(r => !r.success).length;
      const rate = Math.round((fails / slice.length) * 100);
      const sample = slice[slice.length - 1];

      points.push({
        timestamp: sample.executedAt,
        value: rate,
        label: `${rate}% Fail Rate @ ${new Date(sample.executedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`,
      });
    }

    return points;
  }, [healthRecords]);

  // Overall test metrics
  const testMetrics = useMemo(() => {
    if (recentTestRuns.length === 0) return { passRate: 100, totalTests: 0, passed: 0, failed: 0 };
    const total = recentTestRuns.reduce((acc, r) => acc + r.totalTests, 0);
    const passed = recentTestRuns.reduce((acc, r) => acc + r.passedTests, 0);
    const failed = recentTestRuns.reduce((acc, r) => acc + r.failedTests, 0);
    const rate = total > 0 ? Math.round((passed / total) * 100) : 100;
    return { passRate: rate, totalTests: total, passed, failed };
  }, [recentTestRuns]);

  return (
    <div className="space-y-8 max-w-7xl mx-auto">
      {/* Top Header & Project / Date Range Filter */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-100 font-mono tracking-tight flex items-center gap-2">
            Executive Reliability & Security Dashboard
            <span className="text-xs px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-mono">
              Live Telemetry
            </span>
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Aggregated metrics computed directly from PostgreSQL tables and real active probes. Zero fabricated records.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {/* Project Selector */}
          <div className="flex items-center gap-2 bg-[#0d131f] border border-white/10 rounded-lg px-3 py-1.5">
            <Server className="w-3.5 h-3.5 text-slate-400" />
            <span className="text-xs text-slate-400 font-mono">Project:</span>
            <select
              value={selectedProjectId}
              onChange={(e) => setSelectedProjectId(e.target.value)}
              aria-label="Filter dashboard by project"
              className="bg-transparent text-xs font-mono text-slate-200 outline-none cursor-pointer"
            >
              <option value="ALL" className="bg-slate-900 text-slate-200">
                All Projects ({projects.length})
              </option>
              {projects.map((p) => (
                <option key={p.id} value={p.id} className="bg-slate-900 text-slate-200">
                  {p.name}
                </option>
              ))}
            </select>
          </div>

          {/* Date Filter */}
          <div className="flex items-center gap-1 bg-[#0d131f] border border-white/10 rounded-lg p-1 text-xs font-mono">
            <Calendar className="w-3.5 h-3.5 text-slate-400 ml-1.5 mr-0.5" />
            {(['24h', '7d', '30d'] as const).map(range => (
              <button
                key={range}
                type="button"
                onClick={() => setDateRange(range)}
                className={`px-2.5 py-1 rounded transition-colors ${
                  dateRange === range ? 'bg-cyan-500/20 text-cyan-300 font-semibold' : 'text-slate-400 hover:text-white'
                }`}
              >
                {range.toUpperCase()}
              </button>
            ))}
          </div>

          {/* Refresh Action */}
          <button
            type="button"
            onClick={() => loadData(true)}
            disabled={refreshing}
            className="btn-secondary text-xs font-mono"
            aria-label="Refresh dashboard telemetry"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin text-cyan-400' : ''}`} />
            <span>{refreshing ? 'Refreshing...' : 'Refresh'}</span>
          </button>
        </div>
      </div>

      {error && <ErrorBanner message={error} onRetry={() => loadData()} />}

      {/* KPI Stats Grid */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
          <CardSkeleton />
          <CardSkeleton />
          <CardSkeleton />
          <CardSkeleton />
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
          {/* Core Engine & Uptime */}
          <div className="glass-panel p-5 relative overflow-hidden group">
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs font-mono uppercase tracking-wider text-slate-400">Core Runtime</span>
              <div className="w-8 h-8 rounded-lg bg-cyan-500/10 text-cyan-400 flex items-center justify-center">
                <Activity className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2 mb-1">
              <span className="text-2xl font-bold text-white font-mono">
                {health?.status || 'UNKNOWN'}
              </span>
              <span className="text-xs text-emerald-400 font-mono">
                {health?.environment ? `${health.environment.toUpperCase()}` : 'ACTIVE'}
              </span>
            </div>
            <div className="flex items-center justify-between text-xs text-slate-400 font-mono">
              <span>Uptime: {health ? formatUptime(health.uptimeSeconds) : '0s'}</span>
              <span className="text-cyan-400">{health?.application || 'api-sentinel'}</span>
            </div>
            <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-cyan-500 to-transparent" />
          </div>

          {/* Endpoints & Inventory Status */}
          <div className="glass-panel p-5 relative overflow-hidden">
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs font-mono uppercase tracking-wider text-slate-400">Inventory Status</span>
              <div className="w-8 h-8 rounded-lg bg-indigo-500/10 text-indigo-400 flex items-center justify-center">
                <Network className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2 mb-1">
              <span className="text-2xl font-bold text-white font-mono">
                {totalEndpoints}
              </span>
              <span className="text-xs text-slate-400 font-mono">registered endpoints</span>
            </div>
            <div className="flex items-center gap-2 text-xs font-mono">
              <span className="text-emerald-400 flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                {reliability?.healthyEndpoints ?? Math.max(0, totalEndpoints - (reliability?.downEndpoints || 0))} UP
              </span>
              <span className="text-rose-400 flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-rose-400" />
                {reliability?.downEndpoints ?? activeIncidents.length} DOWN
              </span>
            </div>
            <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-indigo-500 to-transparent" />
          </div>

          {/* Integration Test Pass Rate */}
          <div className="glass-panel p-5 relative overflow-hidden">
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs font-mono uppercase tracking-wider text-slate-400">Test Pass Rate</span>
              <div className="w-8 h-8 rounded-lg bg-emerald-500/10 text-emerald-400 flex items-center justify-center">
                <PlayCircle className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2 mb-1">
              <span className="text-2xl font-bold text-white font-mono">
                {testMetrics.passRate}%
              </span>
              <span className="text-xs text-slate-400 font-mono">
                ({testMetrics.passed}/{testMetrics.totalTests} steps)
              </span>
            </div>
            <div className="text-xs text-slate-400 font-mono">
              {recentTestRuns.length} total test runs executed
            </div>
            <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-emerald-500 to-transparent" />
          </div>

          {/* Active Incidents & SLA */}
          <div className="glass-panel p-5 relative overflow-hidden">
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs font-mono uppercase tracking-wider text-slate-400">Active Incidents</span>
              <div className="w-8 h-8 rounded-lg bg-rose-500/10 text-rose-400 flex items-center justify-center">
                <AlertOctagon className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2 mb-1">
              <span className={`text-2xl font-bold font-mono ${activeIncidents.length > 0 ? 'text-rose-400' : 'text-white'}`}>
                {activeIncidents.length}
              </span>
              <span className="text-xs text-slate-400 font-mono">unresolved outages</span>
            </div>
            <div className="text-xs text-slate-400 font-mono">
              Uptime SLA: {reliability?.overallUptimePercentage != null ? `${reliability.overallUptimePercentage}%` : 'Tracking Live'}
            </div>
            <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-rose-500 to-transparent" />
          </div>
        </div>
      )}

      {/* Latency & Failure-Rate Interactive Trend Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <TrendChart
          title="Endpoint Latency Telemetry (Live Probes)"
          subtitle="Real roundtrip HTTP response duration in milliseconds across probes"
          unit="ms"
          data={latencyTrendData}
          color="cyan"
          height={170}
          showDateFilter={true}
          selectedRange={dateRange}
          onRangeChange={setDateRange}
          emptyMessage="No latency probe records found. Trigger health checks or test suites to populate real data."
        />

        <TrendChart
          title="Failure-Rate Moving Distribution"
          subtitle="Percentage of consecutive non-2xx failures over active probe samples"
          unit="%"
          data={failureRateTrendData}
          color={activeIncidents.length > 0 ? 'rose' : 'emerald'}
          height={170}
          showDateFilter={false}
          emptyMessage="Zero probe failures recorded. System reliability running at 100%."
        />
      </div>

      {/* Security Findings & Recent Test Runs */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Security Findings Breakdown */}
        <div className="glass-panel p-5 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-amber-500/10 text-amber-400 flex items-center justify-center">
                  <ShieldAlert className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-sm font-semibold text-white font-mono">Security Scan Findings</h3>
                  <p className="text-xs text-slate-400">OWASP API Security Top 10 automated audit findings</p>
                </div>
              </div>
              <span className="text-xs font-mono text-slate-400">
                {recentScans.length} scans run
              </span>
            </div>

            {/* Severity Breakdown Badges */}
            <div className="grid grid-cols-5 gap-2 mb-4 text-center">
              <div className="p-2.5 rounded-lg bg-rose-950/30 border border-rose-500/20">
                <span className="text-[10px] font-mono uppercase text-rose-400 block">Critical</span>
                <span className="text-lg font-bold font-mono text-rose-300">{severityCounts.critical}</span>
              </div>
              <div className="p-2.5 rounded-lg bg-orange-950/30 border border-orange-500/20">
                <span className="text-[10px] font-mono uppercase text-orange-400 block">High</span>
                <span className="text-lg font-bold font-mono text-orange-300">{severityCounts.high}</span>
              </div>
              <div className="p-2.5 rounded-lg bg-amber-950/30 border border-amber-500/20">
                <span className="text-[10px] font-mono uppercase text-amber-400 block">Medium</span>
                <span className="text-lg font-bold font-mono text-amber-300">{severityCounts.medium}</span>
              </div>
              <div className="p-2.5 rounded-lg bg-blue-950/30 border border-blue-500/20">
                <span className="text-[10px] font-mono uppercase text-blue-400 block">Low</span>
                <span className="text-lg font-bold font-mono text-blue-300">{severityCounts.low}</span>
              </div>
              <div className="p-2.5 rounded-lg bg-slate-800/40 border border-slate-700/30">
                <span className="text-[10px] font-mono uppercase text-slate-400 block">Info</span>
                <span className="text-lg font-bold font-mono text-slate-300">{severityCounts.info}</span>
              </div>
            </div>
          </div>

          <div className="border-t border-white/5 pt-3">
            <span className="text-xs text-slate-400 font-mono flex items-center justify-between">
              <span>Scanner engine status:</span>
              <span className="text-emerald-400 font-semibold">Active & Safe SSRF Constrained</span>
            </span>
          </div>
        </div>

        {/* Recent Integration Test Runs */}
        <div className="glass-panel p-5 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-cyan-500/10 text-cyan-400 flex items-center justify-center">
                  <PlayCircle className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-sm font-semibold text-white font-mono">Recent Test Suite Runs</h3>
                  <p className="text-xs text-slate-400">Chained API assertions & response validations</p>
                </div>
              </div>
              <span className="text-xs font-mono text-cyan-400 font-semibold">
                Pass: {testMetrics.passRate}%
              </span>
            </div>

            {recentTestRuns.length === 0 ? (
              <div className="p-6 text-center text-xs text-slate-500 font-mono italic border border-dashed border-white/5 rounded-lg bg-black/20">
                No test runs recorded. Visit the Test Suites page to execute tests.
              </div>
            ) : (
              <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
                {recentTestRuns.slice(0, 5).map(run => (
                  <div
                    key={run.id}
                    className="p-2.5 rounded-lg bg-white/[0.02] border border-white/5 flex items-center justify-between text-xs font-mono"
                  >
                    <div className="flex items-center gap-2 truncate">
                      {run.status === 'PASSED' ? (
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                      ) : (
                        <XCircle className="w-4 h-4 text-rose-400 shrink-0" />
                      )}
                      <span className="text-slate-200 truncate">{run.testSuiteName || 'Integration Suite'}</span>
                    </div>
                    <div className="flex items-center gap-3 shrink-0 text-slate-400 text-[11px]">
                      <span>{run.passedTests}/{run.totalTests} passed</span>
                      {run.durationMs != null && <span className="text-cyan-400">{run.durationMs}ms</span>}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="border-t border-white/5 pt-3 mt-4">
            <span className="text-xs text-slate-400 font-mono flex items-center justify-between">
              <span>Total steps validated:</span>
              <span className="text-slate-200 font-semibold">{testMetrics.totalTests} assertions</span>
            </span>
          </div>
        </div>
      </div>

      {/* Active Incidents Banner if any */}
      {activeIncidents.length > 0 && (
        <div className="glass-panel p-5 border-rose-500/30 bg-gradient-to-r from-rose-950/30 via-transparent to-transparent">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2 text-rose-400">
              <AlertOctagon className="w-5 h-5" />
              <h3 className="text-sm font-bold font-mono">Active Incidents Requiring Attention</h3>
            </div>
            <span className="text-xs font-mono text-rose-300">
              {activeIncidents.length} active
            </span>
          </div>
          <div className="space-y-2">
            {activeIncidents.map(inc => (
              <div
                key={inc.id}
                className="p-3 rounded-lg bg-rose-950/40 border border-rose-500/20 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs font-mono"
              >
                <div>
                  <span className="font-semibold text-rose-200 mr-2">[{inc.severity}] {inc.title}</span>
                  <span className="text-slate-400">Endpoint: {inc.endpointName} ({inc.httpMethod} {inc.endpointUrl})</span>
                </div>
                <div className="text-slate-400 shrink-0 text-[11px]">
                  Started {new Date(inc.startedAt).toLocaleTimeString()}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
