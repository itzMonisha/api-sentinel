import React, { useEffect, useState } from 'react';
import {
  Shield,
  LayoutDashboard,
  FolderKanban,
  Network,
  PlayCircle,
  ShieldAlert,
  AlertOctagon,
  Settings,
  Activity,
  RefreshCw,
  ExternalLink,
  ChevronRight
} from 'lucide-react';
import { NavigationTab, SystemHealthData } from '../../types';
import { ApiClient } from '../../services/apiClient';
import { StatusBadge } from '../common/StatusBadge';

interface ShellProps {
  currentTab: NavigationTab;
  onSelectTab: (tab: NavigationTab) => void;
  children: React.ReactNode;
}

export const Shell: React.FC<ShellProps> = ({
  currentTab,
  onSelectTab,
  children,
}) => {
  const [health, setHealth] = useState<SystemHealthData | null>(null);
  const [healthLoading, setHealthLoading] = useState<boolean>(true);
  const [healthError, setHealthError] = useState<string | null>(null);

  const fetchHealth = async () => {
    try {
      setHealthLoading(true);
      setHealthError(null);
      const res = await ApiClient.getHealth();
      setHealth(res.data);
    } catch (err: unknown) {
      setHealth(null);
      setHealthError(err instanceof Error ? err.message : 'Backend unreachable');
    } finally {
      setHealthLoading(false);
    }
  };

  useEffect(() => {
    fetchHealth();
    const interval = setInterval(fetchHealth, 30000); // 30s probe
    return () => clearInterval(interval);
  }, []);

  const navItems = [
    { id: 'dashboard' as NavigationTab, label: 'Dashboard', icon: LayoutDashboard },
    { id: 'projects' as NavigationTab, label: 'Projects & Registry', icon: FolderKanban },
    { id: 'endpoints' as NavigationTab, label: 'Discovered Endpoints', icon: Network },
    { id: 'test-runs' as NavigationTab, label: 'Test Runs', icon: PlayCircle },
    { id: 'security-scans' as NavigationTab, label: 'Security Scans', icon: ShieldAlert },
    { id: 'incidents' as NavigationTab, label: 'Incidents & SLA', icon: AlertOctagon },
    { id: 'settings' as NavigationTab, label: 'System Settings', icon: Settings },
  ];

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-[#07090e]">
      {/* Sidebar Navigation */}
      <aside className="w-64 border-r border-white/5 bg-[#0a0e17]/80 backdrop-blur-xl flex flex-col shrink-0 select-none">
        {/* Brand Header */}
        <div className="h-16 px-5 flex items-center gap-3 border-b border-white/5">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-cyan-500 to-blue-600 flex items-center justify-center text-white shadow-lg shadow-cyan-500/20">
            <Shield className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <span className="font-bold text-sm tracking-wide text-white">API SENTINEL</span>
              <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-cyan-500/20 text-cyan-300 font-semibold">v0.1</span>
            </div>
            <p className="text-[11px] text-slate-400 font-medium">Security & Reliability</p>
          </div>
        </div>

        {/* Workspace Indicator */}
        <div className="px-4 py-3 border-b border-white/5">
          <div className="px-3 py-2 rounded-lg bg-white/[0.03] border border-white/5 flex items-center justify-between">
            <div className="truncate">
              <span className="text-[10px] font-mono uppercase tracking-wider text-slate-400 block">Workspace</span>
              <span className="text-xs font-semibold text-slate-200">Default Workspace</span>
            </div>
            <span className="w-2 h-2 rounded-full bg-cyan-400/80" />
          </div>
        </div>

        {/* Navigation Links */}
        <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = currentTab === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => onSelectTab(item.id)}
                className={`w-full flex items-center justify-between px-3 py-2.5 rounded-lg text-xs font-medium transition-all ${
                  isActive
                    ? 'bg-cyan-500/15 text-cyan-300 border border-cyan-500/30 shadow-sm shadow-cyan-500/10'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-white/[0.04]'
                }`}
              >
                <div className="flex items-center gap-3">
                  <Icon className={`w-4 h-4 ${isActive ? 'text-cyan-400' : 'text-slate-500'}`} />
                  <span>{item.label}</span>
                </div>
                {isActive && <ChevronRight className="w-3.5 h-3.5 text-cyan-400/80" />}
              </button>
            );
          })}
        </nav>

        {/* Backend Heartbeat Footer */}
        <div className="p-4 border-t border-white/5 bg-[#07090e]/60">
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-1.5 text-xs font-mono text-slate-400">
              <Activity className="w-3.5 h-3.5 text-cyan-400" />
              <span>Core Engine</span>
            </div>
            <button
              type="button"
              onClick={fetchHealth}
              disabled={healthLoading}
              title="Ping Backend Health"
              className="text-slate-500 hover:text-slate-300 transition-colors"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${healthLoading ? 'animate-spin text-cyan-400' : ''}`} />
            </button>
          </div>

          <div className="flex items-center justify-between">
            {health ? (
              <>
                <StatusBadge status={health.status} size="sm" />
                <span className="text-[11px] font-mono text-slate-400">{health.environment}</span>
              </>
            ) : (
              <span className="text-[11px] font-mono text-rose-400 flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
                {healthLoading ? 'Connecting...' : 'Offline'}
              </span>
            )}
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Top Header */}
        <header className="h-16 border-b border-white/5 bg-[#0a0e17]/40 backdrop-blur-md px-8 flex items-center justify-between shrink-0">
          <div className="flex items-center gap-3">
            <h1 className="text-sm font-semibold text-slate-200 capitalize tracking-wide font-mono">
              {currentTab.replace('-', ' ')}
            </h1>
            <span className="text-slate-600">/</span>
            <span className="text-xs text-slate-400 font-mono">Phase 01: Core Platform Scaffolding</span>
          </div>

          <div className="flex items-center gap-3">
            <a
              href="/swagger-ui.html"
              target="_blank"
              rel="noreferrer"
              className="btn-secondary text-xs font-mono"
            >
              <span>Swagger API Docs</span>
              <ExternalLink className="w-3.5 h-3.5 text-slate-400" />
            </a>
            <div className="px-2.5 py-1 rounded bg-cyan-950/40 border border-cyan-500/20 text-cyan-400 text-xs font-mono">
              Zero-Trust Active
            </div>
          </div>
        </header>

        {/* Content Body */}
        <main className="flex-1 overflow-y-auto p-8 bg-[radial-gradient(ellipse_80%_80%_at_50%_-20%,rgba(6,182,212,0.06),rgba(255,255,255,0))]">
          {healthError && (
            <div className="mb-6 p-4 rounded-xl bg-rose-950/30 border border-rose-500/30 text-rose-300 text-xs font-mono flex items-center justify-between">
              <span>Backend connection warning: {healthError}</span>
              <button
                type="button"
                onClick={fetchHealth}
                className="underline hover:text-white"
              >
                Reconnect
              </button>
            </div>
          )}
          {children}
        </main>
      </div>
    </div>
  );
};
