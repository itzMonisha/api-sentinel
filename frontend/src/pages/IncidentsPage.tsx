import React from 'react';
import { AlertOctagon, CheckCircle2 } from 'lucide-react';
import { EmptyState } from '../components/common/EmptyState';

export const IncidentsPage: React.FC = () => {
  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-slate-100 font-mono tracking-tight">
            Operational Incidents & SLA Tracking
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Automated health probe alerting, FSM state machine lifecycle, and webhook dispatching.
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs font-mono text-emerald-400 bg-emerald-950/40 border border-emerald-500/20 px-3 py-1.5 rounded-lg">
          <CheckCircle2 className="w-3.5 h-3.5" />
          <span>All Monitored Systems Operational</span>
        </div>
      </div>

      <EmptyState
        icon={AlertOctagon}
        title="Zero Active Incidents"
        description="Incidents are triggered automatically when health probe failures exceed threshold (3 consecutive failures) or when critical security findings are discovered on production APIs. An incident lifecycle (TRIGGERED -> ACKNOWLEDGED -> RESOLVED) will govern alerts in Phase 09."
        phaseNote="Phase 09 Feature: Incident State Machine & Webhooks"
      />
    </div>
  );
};
