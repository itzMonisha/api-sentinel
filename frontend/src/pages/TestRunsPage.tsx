import React from 'react';
import { PlayCircle, Play } from 'lucide-react';
import { EmptyState } from '../components/common/EmptyState';

export const TestRunsPage: React.FC = () => {
  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-slate-100 font-mono tracking-tight">
            Automated Test Runs
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Chained integration test execution with JSONPath variable passing and assertion verification.
          </p>
        </div>
        <button
          type="button"
          disabled
          className="btn-primary opacity-60 cursor-not-allowed text-xs font-mono"
          title="Test execution engine will be implemented in Phase 06"
        >
          <Play className="w-3.5 h-3.5" />
          <span>Execute Suite</span>
        </button>
      </div>

      <EmptyState
        icon={PlayCircle}
        title="No Test Runs Recorded"
        description="Test suites allow grouping API requests into sequential or DAG-chained flows with JSONPath variable extraction and latency thresholds. Historical execution records will appear here once Phase 06 is implemented."
        phaseNote="Phase 06 Feature: DAG Test Execution & JSONPath Assertions"
      />
    </div>
  );
};
