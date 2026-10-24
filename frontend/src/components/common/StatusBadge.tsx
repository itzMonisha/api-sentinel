import React from 'react';

interface StatusBadgeProps {
  status: string;
  size?: 'sm' | 'md';
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, size = 'md' }) => {
  const normalized = status.toUpperCase();
  
  let badgeClass = 'badge-neutral';
  let dotClass = 'bg-slate-400';

  if (normalized === 'UP' || normalized === 'PASSED' || normalized === 'RESOLVED') {
    badgeClass = 'badge-up';
    dotClass = 'bg-emerald-400';
  } else if (normalized === 'DOWN' || normalized === 'FAILED' || normalized === 'CRITICAL') {
    badgeClass = 'badge-down';
    dotClass = 'bg-rose-400';
  } else if (normalized === 'DEGRADED' || normalized === 'WARNING' || normalized === 'TRIGGERED') {
    badgeClass = 'badge-degraded';
    dotClass = 'bg-amber-400';
  }

  const paddingClass = size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-3 py-1 text-xs';

  return (
    <span className={`badge ${badgeClass} ${paddingClass} inline-flex items-center gap-1.5 font-mono`}>
      <span className={`w-1.5 h-1.5 rounded-full ${dotClass} animate-pulse-slow`} />
      {normalized}
    </span>
  );
};
