import React, { useEffect, useState, useMemo } from 'react';
import { 
  ShieldAlert, 
  ShieldCheck, 
  Shield, 
  AlertTriangle, 
  CheckCircle, 
  Info, 
  Play, 
  RefreshCw, 
  Filter, 
  Search, 
  Lock, 
  Eye, 
  Clock, 
  Terminal, 
  Copy, 
  Check, 
  X,
  Layers,
  Sparkles
} from 'lucide-react';
import { ApiClient } from '../services/apiClient';
import { 
  Project, 
  SecurityScan, 
  SecurityFinding, 
  FindingSeverity 
} from '../types';
import { EmptyState } from '../components/common/EmptyState';

export const SecurityScansPage: React.FC = () => {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>('');
  const [scans, setScans] = useState<SecurityScan[]>([]);
  const [selectedScanId, setSelectedScanId] = useState<string | null>(null);
  const [findings, setFindings] = useState<SecurityFinding[]>([]);
  
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [loadingScans, setLoadingScans] = useState(false);
  const [loadingFindings, setLoadingFindings] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Filter state
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedSeverity, setSelectedSeverity] = useState<string>('ALL');
  const [selectedConfidence, setSelectedConfidence] = useState<string>('ALL');

  // Launch modal state
  const [isLaunchModalOpen, setIsLaunchModalOpen] = useState(false);
  const [fixtureHeaderName, setFixtureHeaderName] = useState('Authorization');
  const [fixtureHeaderValue, setFixtureHeaderValue] = useState('');
  const [useFixture, setUseFixture] = useState(false);

  // Copied evidence state
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // Load projects on mount
  useEffect(() => {
    loadProjects();
  }, []);

  const loadProjects = async () => {
    setLoadingProjects(true);
    setError(null);
    try {
      const res = await ApiClient.listProjects('', 0, 100);
      const projs = res.content || [];
      setProjects(projs);
      if (projs.length > 0 && !selectedProjectId) {
        setSelectedProjectId(projs[0].id);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to load projects');
    } finally {
      setLoadingProjects(false);
    }
  };

  // Load scans whenever selected project changes
  useEffect(() => {
    if (selectedProjectId) {
      loadProjectScans(selectedProjectId);
    } else {
      setScans([]);
      setSelectedScanId(null);
      setFindings([]);
    }
  }, [selectedProjectId]);

  const loadProjectScans = async (projectId: string) => {
    setLoadingScans(true);
    setError(null);
    try {
      const scanList = await ApiClient.getProjectScans(projectId);
      setScans(scanList);
      if (scanList.length > 0) {
        setSelectedScanId(scanList[0].id);
      } else {
        setSelectedScanId(null);
        setFindings([]);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to load security scans');
    } finally {
      setLoadingScans(false);
    }
  };

  // Load findings whenever selected scan changes
  useEffect(() => {
    if (selectedScanId) {
      loadScanFindings(selectedScanId);
    } else {
      setFindings([]);
    }
  }, [selectedScanId]);

  const loadScanFindings = async (scanId: string) => {
    setLoadingFindings(true);
    try {
      const findingList = await ApiClient.getScanFindings(scanId);
      setFindings(findingList);
    } catch (err: any) {
      console.error('Failed to load scan findings:', err);
    } finally {
      setLoadingFindings(false);
    }
  };

  const handleLaunchScan = async () => {
    if (!selectedProjectId) return;
    setScanning(true);
    setError(null);

    const testFixtureAuth = useFixture && fixtureHeaderName && fixtureHeaderValue
      ? { [fixtureHeaderName]: fixtureHeaderValue }
      : undefined;

    try {
      const newScan = await ApiClient.launchProjectScan(selectedProjectId, {
        projectId: selectedProjectId,
        testFixtureAuth
      });
      setIsLaunchModalOpen(false);
      // Reload scans and select newly completed scan
      await loadProjectScans(selectedProjectId);
      setSelectedScanId(newScan.id);
    } catch (err: any) {
      setError(err.message || 'Security scan execution failed');
    } finally {
      setScanning(false);
    }
  };

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  // Filtered findings
  const filteredFindings = useMemo(() => {
    return findings.filter(f => {
      // Severity filter
      if (selectedSeverity !== 'ALL' && f.severity !== selectedSeverity) {
        return false;
      }
      // Confidence filter
      if (selectedConfidence !== 'ALL' && f.confidence !== selectedConfidence) {
        return false;
      }
      // Search query
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase();
        const matchesTitle = f.title.toLowerCase().includes(query);
        const matchesRule = f.ruleId.toLowerCase().includes(query);
        const matchesUrl = f.endpointUrl?.toLowerCase().includes(query);
        const matchesDesc = f.description.toLowerCase().includes(query);
        const matchesCategory = f.owaspCategory?.toLowerCase().includes(query);
        return matchesTitle || matchesRule || matchesUrl || matchesDesc || matchesCategory;
      }
      return true;
    });
  }, [findings, selectedSeverity, selectedConfidence, searchQuery]);

  // Selected scan object
  const activeScan = useMemo(() => {
    return scans.find(s => s.id === selectedScanId);
  }, [scans, selectedScanId]);

  // Aggregate stats across all scans for this project
  const stats = useMemo(() => {
    if (!activeScan) {
      return { totalChecks: 0, critical: 0, high: 0, medium: 0, low: 0, info: 0 };
    }
    return {
      totalChecks: activeScan.totalChecks,
      critical: activeScan.criticalCount,
      high: activeScan.highCount,
      medium: activeScan.mediumCount,
      low: activeScan.lowCount,
      info: activeScan.infoCount
    };
  }, [activeScan]);

  const getSeverityBadgeClass = (severity: FindingSeverity) => {
    switch (severity) {
      case 'CRITICAL':
        return 'bg-red-500/20 text-red-400 border border-red-500/30';
      case 'HIGH':
        return 'bg-rose-500/20 text-rose-300 border border-rose-500/30';
      case 'MEDIUM':
        return 'bg-amber-500/20 text-amber-300 border border-amber-500/30';
      case 'LOW':
        return 'bg-blue-500/20 text-blue-300 border border-blue-500/30';
      case 'INFO':
      default:
        return 'bg-slate-700/40 text-slate-300 border border-slate-600/30';
    }
  };

  const getMethodBadgeClass = (method?: string) => {
    switch (method?.toUpperCase()) {
      case 'GET': return 'badge-get';
      case 'POST': return 'badge-post';
      case 'PUT': return 'badge-put';
      case 'DELETE': return 'badge-delete';
      default: return 'badge-neutral';
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* Header & Controls */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-5">
        <div>
          <div className="flex items-center gap-2">
            <Shield className="w-6 h-6 text-cyan-400" />
            <h2 className="text-xl font-bold text-slate-100 font-mono tracking-tight">
              Safe Security Analysis Engine
            </h2>
          </div>
          <p className="text-xs text-slate-400 mt-1 max-w-3xl">
            Read-only, zero-trust automated vulnerability audits against OWASP API Security Top 10 with conservative heuristics and PII redaction.
          </p>
        </div>

        <div className="flex items-center gap-3">
          {/* Project Selector */}
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-400 font-mono">Project:</span>
            <select
              value={selectedProjectId}
              onChange={(e) => setSelectedProjectId(e.target.value)}
              disabled={loadingProjects || scanning}
              className="bg-slate-900 border border-slate-700 text-slate-200 text-xs rounded-lg px-3 py-1.5 focus:border-cyan-500 outline-none font-mono"
            >
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({p.endpointCount} eps)
                </option>
              ))}
            </select>
          </div>

          <button
            type="button"
            onClick={() => loadProjectScans(selectedProjectId)}
            disabled={loadingScans || scanning || !selectedProjectId}
            className="btn-secondary text-xs font-mono py-1.5 px-3 flex items-center gap-1.5"
            title="Refresh Scan Results"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loadingScans ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>

          <button
            type="button"
            onClick={() => setIsLaunchModalOpen(true)}
            disabled={scanning || !selectedProjectId}
            className="btn-primary text-xs font-mono py-1.5 px-4 flex items-center gap-1.5"
          >
            <Play className={`w-3.5 h-3.5 fill-current ${scanning ? 'animate-pulse' : ''}`} />
            <span>{scanning ? 'Auditing...' : 'Launch Safe Scan'}</span>
          </button>
        </div>
      </div>

      {/* Safety Policy Badges */}
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-emerald-950/40 border border-emerald-800/40 text-emerald-400 font-mono">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
          <span>Zero-Trust SSRF Blocklist Enforced</span>
        </div>
        <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-cyan-950/40 border border-cyan-800/40 text-cyan-400 font-mono">
          <Lock className="w-3.5 h-3.5 text-cyan-400" />
          <span>Read-Only Probes (GET/HEAD/OPTIONS)</span>
        </div>
        <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-purple-950/40 border border-purple-800/40 text-purple-400 font-mono">
          <Eye className="w-3.5 h-3.5 text-purple-400" />
          <span>Automatic Secrets & PII Redaction</span>
        </div>
        <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-slate-800/60 border border-slate-700/50 text-slate-300 font-mono">
          <Layers className="w-3.5 h-3.5 text-slate-400" />
          <span>7 Modular Security Checks</span>
        </div>
      </div>

      {/* Error Banner */}
      {error && (
        <div className="p-3 bg-red-950/50 border border-red-800/60 rounded-lg flex items-center justify-between text-xs text-red-200">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-red-400 flex-shrink-0" />
            <span>{error}</span>
          </div>
          <button 
            type="button" 
            onClick={() => setError(null)} 
            className="text-red-400 hover:text-red-200"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* KPI Stats Row */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        <div className="glass-panel p-3 rounded-lg border border-slate-800">
          <span className="text-[10px] font-mono uppercase tracking-wider text-slate-400">Total Scans</span>
          <div className="text-lg font-bold text-slate-100 font-mono mt-0.5">{scans.length}</div>
        </div>
        <div className="glass-panel p-3 rounded-lg border border-red-900/30 bg-red-950/10">
          <span className="text-[10px] font-mono uppercase tracking-wider text-red-400 flex items-center gap-1">
            <ShieldAlert className="w-3 h-3" /> Critical
          </span>
          <div className="text-lg font-bold text-red-300 font-mono mt-0.5">{stats.critical}</div>
        </div>
        <div className="glass-panel p-3 rounded-lg border border-rose-900/30 bg-rose-950/10">
          <span className="text-[10px] font-mono uppercase tracking-wider text-rose-400 flex items-center gap-1">
            <AlertTriangle className="w-3 h-3" /> High
          </span>
          <div className="text-lg font-bold text-rose-300 font-mono mt-0.5">{stats.high}</div>
        </div>
        <div className="glass-panel p-3 rounded-lg border border-amber-900/30 bg-amber-950/10">
          <span className="text-[10px] font-mono uppercase tracking-wider text-amber-400 flex items-center gap-1">
            <AlertTriangle className="w-3 h-3" /> Medium
          </span>
          <div className="text-lg font-bold text-amber-300 font-mono mt-0.5">{stats.medium}</div>
        </div>
        <div className="glass-panel p-3 rounded-lg border border-blue-900/30 bg-blue-950/10">
          <span className="text-[10px] font-mono uppercase tracking-wider text-blue-400 flex items-center gap-1">
            <Info className="w-3 h-3" /> Low / Info
          </span>
          <div className="text-lg font-bold text-blue-300 font-mono mt-0.5">{stats.low + stats.info}</div>
        </div>
        <div className="glass-panel p-3 rounded-lg border border-cyan-900/30 bg-cyan-950/10">
          <span className="text-[10px] font-mono uppercase tracking-wider text-cyan-400 flex items-center gap-1">
            <CheckCircle className="w-3 h-3" /> Checks Run
          </span>
          <div className="text-lg font-bold text-cyan-300 font-mono mt-0.5">{stats.totalChecks}</div>
        </div>
      </div>

      {/* Main Workspace Layout */}
      {scans.length === 0 && !loadingScans ? (
        <EmptyState
          icon={Shield}
          title="No Security Scans Run Yet"
          description="Execute safe, non-destructive vulnerability assessments against your registered API endpoints to verify HTTP security headers, transport encryption, authentication boundaries, and information leakages."
          actionText="Launch First Safe Scan"
          onAction={() => setIsLaunchModalOpen(true)}
        />
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Left Column: Scan History List */}
          <div className="lg:col-span-4 space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-mono font-semibold uppercase tracking-wider text-slate-300 flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5 text-cyan-400" />
                Scan History ({scans.length})
              </h3>
            </div>

            <div className="space-y-2 max-h-[700px] overflow-y-auto pr-1">
              {scans.map((scan) => {
                const isSelected = scan.id === selectedScanId;
                const totalIssues = scan.criticalCount + scan.highCount + scan.mediumCount + scan.lowCount + scan.infoCount;
                return (
                  <div
                    key={scan.id}
                    onClick={() => setSelectedScanId(scan.id)}
                    className={`p-3.5 rounded-lg border cursor-pointer transition-all ${
                      isSelected
                        ? 'bg-slate-800/80 border-cyan-500/60 shadow-lg shadow-cyan-950/20'
                        : 'bg-slate-900/60 border-slate-800 hover:border-slate-700 hover:bg-slate-800/40'
                    }`}
                  >
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-mono text-slate-200 font-medium">
                        Scan #{scan.id.substring(0, 8)}
                      </span>
                      <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-semibold ${
                        scan.status === 'COMPLETED' 
                          ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' 
                          : 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                      }`}>
                        {scan.status}
                      </span>
                    </div>

                    <div className="text-[11px] text-slate-400 mt-1 font-mono">
                      {new Date(scan.createdAt).toLocaleString()}
                    </div>

                    <div className="flex items-center justify-between mt-3 pt-2.5 border-t border-slate-800/60 text-xs">
                      <span className="text-[11px] text-slate-400 font-mono">
                        {scan.totalChecks} checks run
                      </span>
                      <div className="flex items-center gap-1.5 font-mono text-[10px]">
                        {scan.criticalCount > 0 && (
                          <span className="px-1.5 py-0.5 rounded bg-red-500/20 text-red-400 font-bold">
                            {scan.criticalCount}C
                          </span>
                        )}
                        {scan.highCount > 0 && (
                          <span className="px-1.5 py-0.5 rounded bg-rose-500/20 text-rose-400 font-bold">
                            {scan.highCount}H
                          </span>
                        )}
                        {scan.mediumCount > 0 && (
                          <span className="px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-400 font-bold">
                            {scan.mediumCount}M
                          </span>
                        )}
                        {scan.lowCount > 0 && (
                          <span className="px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-400">
                            {scan.lowCount}L
                          </span>
                        )}
                        {totalIssues === 0 && (
                          <span className="text-emerald-400 flex items-center gap-1">
                            <CheckCircle className="w-3 h-3" /> Clean
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Right Column: Active Scan Details & Findings */}
          <div className="lg:col-span-8 space-y-4">
            {activeScan ? (
              <>
                {/* Active Scan Metadata Banner */}
                <div className="glass-panel p-4 rounded-xl border border-slate-800 flex flex-col md:flex-row md:items-center justify-between gap-3">
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="text-sm font-bold text-slate-100 font-mono">
                        Scan Findings: {activeScan.projectName}
                      </h3>
                      <span className="text-xs text-slate-400 font-mono">
                        ({activeScan.id})
                      </span>
                    </div>
                    <div className="flex items-center gap-3 text-xs text-slate-400 mt-1 font-mono">
                      <span>Started: {new Date(activeScan.startedAt || activeScan.createdAt).toLocaleTimeString()}</span>
                      {activeScan.finishedAt && (
                        <span>Finished: {new Date(activeScan.finishedAt).toLocaleTimeString()}</span>
                      )}
                      <span>Total Checks: {activeScan.totalChecks}</span>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <span className="text-xs font-mono text-slate-400">Total Findings:</span>
                    <span className="text-sm font-bold font-mono text-cyan-400">
                      {findings.length}
                    </span>
                  </div>
                </div>

                {/* Filter and Search Bar */}
                <div className="glass-panel p-3 rounded-lg border border-slate-800 flex flex-wrap items-center gap-3">
                  {/* Search */}
                  <div className="relative flex-1 min-w-[200px]">
                    <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
                    <input
                      type="text"
                      placeholder="Filter by title, rule ID, path, or OWASP..."
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      className="w-full bg-slate-900 border border-slate-700 rounded-md pl-8 pr-3 py-1 text-xs text-slate-200 placeholder-slate-500 font-mono outline-none focus:border-cyan-500"
                    />
                  </div>

                  {/* Severity Filter */}
                  <div className="flex items-center gap-1">
                    {['ALL', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((sev) => (
                      <button
                        key={sev}
                        type="button"
                        onClick={() => setSelectedSeverity(sev)}
                        className={`px-2.5 py-1 text-[10px] font-mono rounded font-semibold transition-all ${
                          selectedSeverity === sev
                            ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/40'
                            : 'bg-slate-900 text-slate-400 hover:text-slate-200 border border-slate-800'
                        }`}
                      >
                        {sev}
                      </button>
                    ))}
                  </div>

                  {/* Confidence Filter */}
                  <select
                    value={selectedConfidence}
                    onChange={(e) => setSelectedConfidence(e.target.value)}
                    className="bg-slate-900 border border-slate-700 text-slate-300 text-[11px] rounded px-2.5 py-1 font-mono outline-none focus:border-cyan-500"
                  >
                    <option value="ALL">All Confidence</option>
                    <option value="CONFIRMED">Confirmed Findings</option>
                    <option value="POTENTIAL_RISK">Potential Risks</option>
                  </select>
                </div>

                {/* Findings List */}
                {loadingFindings ? (
                  <div className="p-12 text-center text-slate-400 font-mono text-xs flex flex-col items-center justify-center gap-2">
                    <RefreshCw className="w-5 h-5 animate-spin text-cyan-400" />
                    <span>Loading vulnerability analysis...</span>
                  </div>
                ) : filteredFindings.length === 0 ? (
                  <div className="p-8 text-center glass-panel rounded-xl border border-slate-800 space-y-2">
                    {findings.length === 0 ? (
                      <>
                        <ShieldCheck className="w-10 h-10 text-emerald-400 mx-auto" />
                        <h4 className="text-sm font-semibold text-slate-200 font-mono">
                          Zero Vulnerabilities Discovered
                        </h4>
                        <p className="text-xs text-slate-400 max-w-md mx-auto">
                          All evaluated endpoints complied with security header configurations, transport layer encryption, and authorization controls.
                        </p>
                      </>
                    ) : (
                      <>
                        <Filter className="w-8 h-8 text-slate-500 mx-auto" />
                        <h4 className="text-sm font-semibold text-slate-300 font-mono">
                          No Findings Match Filter
                        </h4>
                        <p className="text-xs text-slate-400">
                          Try adjusting your search query, severity, or confidence filters.
                        </p>
                      </>
                    )}
                  </div>
                ) : (
                  <div className="space-y-3">
                    {filteredFindings.map((finding) => (
                      <div
                        key={finding.id}
                        className="glass-panel p-4 rounded-xl border border-slate-800 hover:border-slate-700 transition-all space-y-3"
                      >
                        {/* Finding Header */}
                        <div className="flex flex-wrap items-start justify-between gap-2">
                          <div className="space-y-1">
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold uppercase tracking-wider ${getSeverityBadgeClass(finding.severity)}`}>
                                {finding.severity}
                              </span>

                              <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-semibold ${
                                finding.confidence === 'CONFIRMED'
                                  ? 'bg-emerald-950/60 text-emerald-400 border border-emerald-800/40'
                                  : 'bg-amber-950/60 text-amber-400 border border-amber-800/40'
                              }`}>
                                {finding.confidence === 'CONFIRMED' ? 'CONFIRMED VULNERABILITY' : 'POTENTIAL RISK'}
                              </span>

                              {finding.owaspCategory && (
                                <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-purple-950/40 text-purple-300 border border-purple-800/40">
                                  {finding.owaspCategory}
                                </span>
                              )}

                              <span className="text-[10px] text-slate-500 font-mono">
                                Rule: {finding.ruleId}
                              </span>
                            </div>

                            <h4 className="text-sm font-bold text-slate-100 font-mono">
                              {finding.title}
                            </h4>
                          </div>

                          <span className="text-[10px] text-slate-500 font-mono">
                            {new Date(finding.createdAt).toLocaleTimeString()}
                          </span>
                        </div>

                        {/* Affected Endpoint */}
                        {finding.endpointUrl && (
                          <div className="flex items-center gap-2 p-2 rounded bg-slate-900/90 border border-slate-800 text-xs font-mono">
                            <span className={`px-1.5 py-0.5 rounded text-[10px] ${getMethodBadgeClass(finding.httpMethod)}`}>
                              {finding.httpMethod || 'GET'}
                            </span>
                            <span className="text-slate-300 truncate">
                              {finding.endpointUrl}
                            </span>
                            {finding.endpointName && (
                              <span className="text-slate-500 text-[11px] ml-auto flex-shrink-0">
                                ({finding.endpointName})
                              </span>
                            )}
                          </div>
                        )}

                        {/* Description */}
                        <p className="text-xs text-slate-300 leading-relaxed">
                          {finding.description}
                        </p>

                        {/* Redacted Evidence */}
                        {finding.evidence && (
                          <div className="space-y-1">
                            <div className="flex items-center justify-between text-[11px] text-slate-400 font-mono">
                              <span className="flex items-center gap-1.5">
                                <Terminal className="w-3.5 h-3.5 text-cyan-400" />
                                Redacted Evidence & Observations
                              </span>
                              <button
                                type="button"
                                onClick={() => copyToClipboard(finding.evidence!, finding.id)}
                                className="text-xs text-slate-400 hover:text-slate-200 flex items-center gap-1 font-mono transition-colors"
                              >
                                {copiedId === finding.id ? (
                                  <>
                                    <Check className="w-3 h-3 text-emerald-400" />
                                    <span className="text-emerald-400 text-[10px]">Copied</span>
                                  </>
                                ) : (
                                  <>
                                    <Copy className="w-3 h-3" />
                                    <span className="text-[10px]">Copy</span>
                                  </>
                                )}
                              </button>
                            </div>
                            <pre className="p-3 bg-slate-950 border border-slate-800/80 rounded-lg text-xs font-mono text-cyan-300/90 overflow-x-auto whitespace-pre-wrap leading-relaxed max-h-48">
                              {finding.evidence}
                            </pre>
                          </div>
                        )}

                        {/* Remediation */}
                        <div className="p-3 rounded-lg bg-emerald-950/20 border border-emerald-900/30 space-y-1">
                          <span className="text-[11px] font-mono font-bold uppercase tracking-wider text-emerald-400 flex items-center gap-1.5">
                            <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
                            Recommended Remediation
                          </span>
                          <p className="text-xs text-emerald-200/90 leading-relaxed">
                            {finding.remediation}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </>
            ) : (
              <div className="p-8 text-center glass-panel rounded-xl border border-slate-800 text-xs text-slate-400 font-mono">
                Select a scan from the history list to view findings.
              </div>
            )}
          </div>
        </div>
      )}

      {/* Launch Safe Scan Modal */}
      {isLaunchModalOpen && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="glass-panel p-6 rounded-2xl border border-slate-700 max-w-xl w-full shadow-2xl space-y-5 bg-slate-900">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div className="flex items-center gap-2">
                <Shield className="w-5 h-5 text-cyan-400" />
                <h3 className="text-base font-bold text-slate-100 font-mono">
                  Launch Safe Security Scan
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setIsLaunchModalOpen(false)}
                className="text-slate-400 hover:text-slate-200"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-4 text-xs text-slate-300">
              <p>
                The security engine will execute read-only probes against all registered endpoints in the selected project.
              </p>

              {/* 7 Modular Checks List */}
              <div className="p-3.5 rounded-lg bg-slate-950/80 border border-slate-800 space-y-2">
                <span className="font-mono font-semibold text-slate-200 text-xs uppercase tracking-wider flex items-center gap-1.5">
                  <Sparkles className="w-3.5 h-3.5 text-cyan-400" />
                  Included Analysis Modules:
                </span>
                <ul className="space-y-1.5 text-[11px] text-slate-400 font-mono list-disc list-inside">
                  <li><strong className="text-slate-300">HTTP Security Headers:</strong> Audits HSTS, X-Content-Type-Options, X-Frame-Options, Server banners.</li>
                  <li><strong className="text-slate-300">Insecure Transport:</strong> Verifies cleartext HTTP transport configuration.</li>
                  <li><strong className="text-slate-300">Unauthenticated Access:</strong> Flags public accessibility on sensitive expected-auth endpoints.</li>
                  <li><strong className="text-slate-300">Auth Configuration Inconsistency:</strong> Checks auth schema integrity and unhandled 500 crashes.</li>
                  <li><strong className="text-slate-300">OpenAPI Spec Discrepancy:</strong> Flags undocumented security requirements on mutating endpoints.</li>
                  <li><strong className="text-slate-300">Information Disclosure:</strong> Conservative heuristics for stack traces, SQL errors, and secret leaks.</li>
                  <li><strong className="text-slate-300">Broken Function Level Auth (BFLA):</strong> Validates low-privilege test account isolation on admin routes.</li>
                </ul>
              </div>

              {/* Safe Test Account Fixture (Optional) */}
              <div className="p-3.5 rounded-lg bg-slate-950/50 border border-slate-800/80 space-y-3">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={useFixture}
                    onChange={(e) => setUseFixture(e.target.checked)}
                    className="rounded bg-slate-900 border-slate-700 text-cyan-500 focus:ring-0"
                  />
                  <span className="font-mono font-semibold text-slate-200 text-xs">
                    Supply Safe Test Account Fixture (For BFLA check)
                  </span>
                </label>

                {useFixture && (
                  <div className="space-y-2.5 pt-1 pl-5">
                    <p className="text-[11px] text-slate-400">
                      Provide a low-privilege test account credential. The scanner will safely verify that elevated administrative routes deny access to this account.
                    </p>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      <div>
                        <span className="text-[10px] font-mono text-slate-400 block mb-1">Header Name</span>
                        <input
                          type="text"
                          value={fixtureHeaderName}
                          onChange={(e) => setFixtureHeaderName(e.target.value)}
                          placeholder="Authorization"
                          className="w-full bg-slate-900 border border-slate-700 rounded px-2.5 py-1 text-xs font-mono text-slate-200 outline-none focus:border-cyan-500"
                        />
                      </div>
                      <div>
                        <span className="text-[10px] font-mono text-slate-400 block mb-1">Header Value / Token</span>
                        <input
                          type="text"
                          value={fixtureHeaderValue}
                          onChange={(e) => setFixtureHeaderValue(e.target.value)}
                          placeholder="Bearer guest_test_token"
                          className="w-full bg-slate-900 border border-slate-700 rounded px-2.5 py-1 text-xs font-mono text-slate-200 outline-none focus:border-cyan-500"
                        />
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>

            <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-800">
              <button
                type="button"
                onClick={() => setIsLaunchModalOpen(false)}
                className="btn-secondary text-xs font-mono py-1.5 px-4"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleLaunchScan}
                disabled={scanning}
                className="btn-primary text-xs font-mono py-1.5 px-5 flex items-center gap-1.5"
              >
                <Play className="w-3.5 h-3.5 fill-current" />
                <span>{scanning ? 'Running Scan...' : 'Start Safe Scan'}</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
