import React, { useEffect, useState } from 'react';
import { Sliders, Shield, Terminal } from 'lucide-react';
import { ApiClient } from '../services/apiClient';
import { SystemHealthData } from '../types';

export const SettingsPage: React.FC = () => {
  const [health, setHealth] = useState<SystemHealthData | null>(null);

  useEffect(() => {
    ApiClient.getHealth().then(res => setHealth(res.data)).catch(() => {});
  }, []);

  return (
    <div className="space-y-8 max-w-4xl mx-auto">
      <div>
        <h2 className="text-xl font-bold text-slate-100 font-mono tracking-tight flex items-center gap-2">
          <Sliders className="w-5 h-5 text-cyan-400" />
          System Settings & Platform Invariants
        </h2>
        <p className="text-xs text-slate-400 mt-1">
          Active environment parameters, security invariants, and connection profiles.
        </p>
      </div>

      {/* Security Invariants Box */}
      <div className="glass-panel p-6 border-white/5 space-y-4">
        <h3 className="text-sm font-semibold text-white flex items-center gap-2">
          <Shield className="w-4 h-4 text-cyan-400" />
          Zero-Trust Security Controls (Active)
        </h3>
        
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs font-mono">
          <div className="p-3.5 rounded-lg bg-black/40 border border-white/5">
            <span className="text-slate-400 block mb-1">SSRF Destination Filter</span>
            <span className="text-emerald-400 font-semibold">BLOCKED: RFC 1918 / 3927 / 4193</span>
          </div>

          <div className="p-3.5 rounded-lg bg-black/40 border border-white/5">
            <span className="text-slate-400 block mb-1">DNS Rebinding Defense</span>
            <span className="text-emerald-400 font-semibold">ENABLED: Direct Socket Pinning</span>
          </div>

          <div className="p-3.5 rounded-lg bg-black/40 border border-white/5">
            <span className="text-slate-400 block mb-1">HTTP Redirect Policy</span>
            <span className="text-emerald-400 font-semibold">DISABLED by Default</span>
          </div>

          <div className="p-3.5 rounded-lg bg-black/40 border border-white/5">
            <span className="text-slate-400 block mb-1">Outbound Stream Cap</span>
            <span className="text-cyan-400 font-semibold">5,242,880 Bytes (5 MB)</span>
          </div>
        </div>
      </div>

      {/* Backend Diagnostics */}
      <div className="glass-panel p-6 border-white/5 space-y-4">
        <h3 className="text-sm font-semibold text-white flex items-center gap-2">
          <Terminal className="w-4 h-4 text-cyan-400" />
          Core Diagnostics & Profiles
        </h3>

        <div className="space-y-3 text-xs font-mono">
          <div className="flex items-center justify-between p-3 rounded-lg bg-white/[0.02] border border-white/5">
            <span className="text-slate-400">Spring Profile</span>
            <span className="text-cyan-300 uppercase">{health?.environment || 'LOCAL'}</span>
          </div>

          <div className="flex items-center justify-between p-3 rounded-lg bg-white/[0.02] border border-white/5">
            <span className="text-slate-400">Timezone Policy</span>
            <span className="text-slate-200">Strict UTC (JVM Default)</span>
          </div>

          <div className="flex items-center justify-between p-3 rounded-lg bg-white/[0.02] border border-white/5">
            <span className="text-slate-400">Database Engine</span>
            <span className="text-slate-200">{health?.components?.database?.databaseProductName || 'PostgreSQL 16'}</span>
          </div>

          <div className="flex items-center justify-between p-3 rounded-lg bg-white/[0.02] border border-white/5">
            <span className="text-slate-400">Error Specification</span>
            <span className="text-slate-200">RFC 7807 Problem Details</span>
          </div>
        </div>
      </div>
    </div>
  );
};
