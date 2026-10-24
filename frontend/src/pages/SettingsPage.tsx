import React, { useEffect, useState } from 'react';
import {
  Sliders,
  Shield,
  Terminal,
  User as UserIcon,
  Key,
  LogOut,
  RefreshCw,
  CheckCircle
} from 'lucide-react';
import { ApiClient } from '../services/apiClient';
import { SystemHealthData, User } from '../types';
import { AuthModal } from '../components/common/AuthModal';

export const SettingsPage: React.FC = () => {
  const [health, setHealth] = useState<SystemHealthData | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [refreshingToken, setRefreshingToken] = useState(false);
  const [sessionNotice, setSessionNotice] = useState<string | null>(null);

  const fetchProfile = async () => {
    try {
      const u = await ApiClient.getCurrentUser();
      setUser(u);
    } catch {
      setUser(null);
    }
  };

  useEffect(() => {
    ApiClient.getHealth().then(res => setHealth(res.data)).catch(() => {});
    fetchProfile();

    const unsub = ApiClient.onAuthChange((u) => {
      setUser(u);
    });
    return unsub;
  }, []);

  const handleRefreshToken = async () => {
    try {
      setRefreshingToken(true);
      setSessionNotice(null);
      await ApiClient.refreshToken();
      setSessionNotice('Session successfully renewed with rotated JWT tokens.');
      setTimeout(() => setSessionNotice(null), 4000);
      await fetchProfile();
    } catch (err: any) {
      setSessionNotice(`Token renewal failed: ${err.message}. Please sign in again.`);
    } finally {
      setRefreshingToken(false);
    }
  };

  const handleLogout = async () => {
    await ApiClient.logout();
    setSessionNotice('Logged out of active session.');
    setUser(null);
  };

  return (
    <div className="space-y-8 max-w-4xl mx-auto">
      <div>
        <h2 className="text-xl font-bold text-slate-100 font-mono tracking-tight flex items-center gap-2">
          <Sliders className="w-5 h-5 text-cyan-400" />
          Account & Platform Configuration
        </h2>
        <p className="text-xs text-slate-400 mt-1">
          Identity management, token-based sessions, and platform-wide security invariants.
        </p>
      </div>

      {sessionNotice && (
        <div className="p-3.5 rounded-lg bg-cyan-950/40 border border-cyan-500/30 text-cyan-300 text-xs font-mono flex items-center gap-2 animate-fadeIn">
          <CheckCircle className="w-4 h-4 text-cyan-400 shrink-0" />
          <span>{sessionNotice}</span>
        </div>
      )}

      {/* Account & Profile Card */}
      <div className="glass-panel p-6 border-white/5 space-y-6">
        <div className="flex items-center justify-between pb-4 border-b border-white/10">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-cyan-500/20 to-blue-600/20 border border-cyan-500/30 flex items-center justify-center text-cyan-300 shadow-md">
              <UserIcon className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold text-white font-mono">
                  {user ? user.fullName : 'Guest Session (Auto-Authenticated)'}
                </h3>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 font-bold uppercase">
                  {user?.role || 'DEVELOPER'}
                </span>
              </div>
              <p className="text-xs text-slate-400 font-mono mt-0.5">
                {user ? user.email : 'developer@apisentinel.dev'}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setIsAuthModalOpen(true)}
              className="btn-secondary text-xs font-mono"
            >
              <Key className="w-3.5 h-3.5 text-cyan-400" />
              <span>{user ? 'Switch Account' : 'Sign In / Register'}</span>
            </button>
            {user && (
              <button
                type="button"
                onClick={handleLogout}
                className="btn-secondary text-xs font-mono text-rose-400 hover:text-rose-300"
                title="Log Out"
              >
                <LogOut className="w-3.5 h-3.5" />
                <span>Log Out</span>
              </button>
            )}
          </div>
        </div>

        {/* Profile Attributes */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs font-mono">
          <div className="p-3 rounded-lg bg-black/40 border border-white/5">
            <span className="text-slate-400 block mb-1 text-[11px]">User Identifier</span>
            <span className="text-slate-200 truncate block font-mono text-[11px]">
              {user?.id || '4c2a602a-c4f5-4a39-8dd5-7577e1574db9'}
            </span>
          </div>

          <div className="p-3 rounded-lg bg-black/40 border border-white/5">
            <span className="text-slate-400 block mb-1 text-[11px]">JWT Access Token</span>
            <span className="text-emerald-400 font-semibold flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-emerald-400" />
              Active (HMAC-SHA512)
            </span>
          </div>

          <div className="p-3 rounded-lg bg-black/40 border border-white/5">
            <span className="text-slate-400 block mb-1 text-[11px]">Token Rotation</span>
            <button
              type="button"
              onClick={handleRefreshToken}
              disabled={refreshingToken}
              className="text-cyan-400 hover:text-cyan-300 flex items-center gap-1.5 underline"
            >
              <RefreshCw className={`w-3 h-3 ${refreshingToken ? 'animate-spin' : ''}`} />
              <span>Renew Tokens</span>
            </button>
          </div>
        </div>
      </div>

      {/* Security Invariants Box */}
      <div className="glass-panel p-6 border-white/5 space-y-4">
        <h3 className="text-sm font-semibold text-white flex items-center gap-2">
          <Shield className="w-4 h-4 text-cyan-400" />
          Autonomous Zero-Trust Security Controls (Enforced)
        </h3>
        
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs font-mono">
          <div className="p-3.5 rounded-lg bg-black/40 border border-white/5">
            <span className="text-slate-400 block mb-1">SSRF Destination Filter</span>
            <span className="text-emerald-400 font-semibold">BLOCKED: RFC 1918 / 3927 / 4193</span>
            <p className="text-[10px] text-slate-500 mt-1">Loopback, link-local, and private cloud VPC IPs rejected.</p>
          </div>

          <div className="p-3.5 rounded-lg bg-black/40 border border-white/5">
            <span className="text-slate-400 block mb-1">DNS Rebinding Defense</span>
            <span className="text-emerald-400 font-semibold">ENABLED: Direct Socket Pinning</span>
            <p className="text-[10px] text-slate-500 mt-1">Resolved IP is pinned before TCP connect to prevent rebinding.</p>
          </div>

          <div className="p-3.5 rounded-lg bg-black/40 border border-white/5">
            <span className="text-slate-400 block mb-1">HTTP Redirect Policy</span>
            <span className="text-emerald-400 font-semibold">DISABLED by Default</span>
            <p className="text-[10px] text-slate-500 mt-1">Prevents unexpected redirect chains to internal gateways.</p>
          </div>

          <div className="p-3.5 rounded-lg bg-black/40 border border-white/5">
            <span className="text-slate-400 block mb-1">Outbound Stream Cap</span>
            <span className="text-cyan-400 font-semibold">5,242,880 Bytes (5 MB)</span>
            <p className="text-[10px] text-slate-500 mt-1">Bounded memory buffer protects against zip bombs & infinite streams.</p>
          </div>
        </div>
      </div>

      {/* Backend Diagnostics */}
      <div className="glass-panel p-6 border-white/5 space-y-4">
        <h3 className="text-sm font-semibold text-white flex items-center gap-2">
          <Terminal className="w-4 h-4 text-cyan-400" />
          Runtime Diagnostics & Architecture Profiles
        </h3>

        <div className="space-y-3 text-xs font-mono">
          <div className="flex items-center justify-between p-3 rounded-lg bg-white/[0.02] border border-white/5">
            <span className="text-slate-400">Spring Active Profile</span>
            <span className="text-cyan-300 uppercase">{health?.environment || 'TEST'}</span>
          </div>

          <div className="flex items-center justify-between p-3 rounded-lg bg-white/[0.02] border border-white/5">
            <span className="text-slate-400">Database Engine</span>
            <span className="text-slate-200">{health?.components?.database?.databaseProductName || 'PostgreSQL Dialect'}</span>
          </div>

          <div className="flex items-center justify-between p-3 rounded-lg bg-white/[0.02] border border-white/5">
            <span className="text-slate-400">Error Format Specification</span>
            <span className="text-slate-200">RFC 7807 Problem Details (Machine Readable)</span>
          </div>

          <div className="flex items-center justify-between p-3 rounded-lg bg-white/[0.02] border border-white/5">
            <span className="text-slate-400">Background Worker Threads</span>
            <span className="text-emerald-400">8 Bounded Scheduler Workers</span>
          </div>
        </div>
      </div>

      <AuthModal
        isOpen={isAuthModalOpen}
        onClose={() => setIsAuthModalOpen(false)}
        onSuccess={fetchProfile}
      />
    </div>
  );
};
