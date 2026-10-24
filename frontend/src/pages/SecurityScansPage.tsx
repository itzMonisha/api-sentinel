import React from 'react';
import { ShieldAlert, Shield } from 'lucide-react';
import { EmptyState } from '../components/common/EmptyState';

export const SecurityScansPage: React.FC = () => {
  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-slate-100 font-mono tracking-tight">
            Security Scans & Vulnerabilities
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Safe, non-destructive vulnerability assessments against the OWASP API Security Top 10.
          </p>
        </div>
        <button
          type="button"
          disabled
          className="btn-primary opacity-60 cursor-not-allowed text-xs font-mono"
          title="Security scanner will be implemented in Phase 07"
        >
          <Shield className="w-3.5 h-3.5" />
          <span>Launch Scan</span>
        </button>
      </div>

      <EmptyState
        icon={ShieldAlert}
        title="No Security Scans Run"
        description="The safe scanner will audit transport security, headers (HSTS, CSP, X-Frame-Options), overly permissive CORS, stack trace leakages, unauthenticated sensitive paths, and missing rate limits. Scan reports with reproduction commands will appear here once Phase 07 is deployed."
        phaseNote="Phase 07 Feature: Safe OWASP API Security Scanner"
      />
    </div>
  );
};
