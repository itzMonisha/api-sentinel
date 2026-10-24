import React from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface ErrorBannerProps {
  title?: string;
  message: string;
  onRetry?: () => void;
}

export const ErrorBanner: React.FC<ErrorBannerProps> = ({
  title = 'Connection Error',
  message,
  onRetry,
}) => {
  return (
    <div className="glass-panel p-5 border-rose-500/30 bg-rose-950/20 my-4 flex items-start gap-4">
      <div className="p-2 rounded-lg bg-rose-500/20 text-rose-400 shrink-0">
        <AlertTriangle className="w-5 h-5" />
      </div>
      <div className="flex-1 min-w-0">
        <h4 className="text-sm font-semibold text-rose-200 mb-1">{title}</h4>
        <p className="text-xs text-rose-300/80 font-mono leading-relaxed break-words">{message}</p>
      </div>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="btn-secondary text-xs px-3 py-1.5 shrink-0 hover:bg-rose-500/20 hover:border-rose-500/40"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          Retry
        </button>
      )}
    </div>
  );
};
