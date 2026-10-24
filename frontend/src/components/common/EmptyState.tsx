import React from 'react';
import { LucideIcon } from 'lucide-react';

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description: string;
  phaseNote?: string;
  actionText?: string;
  onAction?: () => void;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  icon: Icon,
  title,
  description,
  phaseNote,
  actionText,
  onAction,
}) => {
  return (
    <div className="glass-panel p-10 flex flex-col items-center justify-center text-center max-w-xl mx-auto my-6 border border-white/5">
      <div className="w-14 h-14 rounded-2xl bg-cyan-950/40 border border-cyan-500/20 flex items-center justify-center mb-5 text-cyan-400 shadow-lg shadow-cyan-950/50">
        <Icon className="w-7 h-7" />
      </div>
      <h3 className="text-lg font-semibold text-slate-100 mb-2">{title}</h3>
      <p className="text-sm text-slate-400 mb-5 leading-relaxed">{description}</p>
      
      {phaseNote && (
        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-md bg-white/[0.03] border border-white/5 text-xs text-cyan-300 font-mono mb-6">
          <span className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse" />
          {phaseNote}
        </div>
      )}

      {actionText && onAction && (
        <button
          type="button"
          onClick={onAction}
          className="btn-primary text-xs tracking-wide uppercase font-mono"
        >
          {actionText}
        </button>
      )}
    </div>
  );
};
