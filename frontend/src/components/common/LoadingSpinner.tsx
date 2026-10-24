import React from 'react';
import { Loader2 } from 'lucide-react';

interface LoadingSpinnerProps {
  message?: string;
}

export const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({ message = 'Connecting to API Sentinel core...' }) => {
  return (
    <div className="flex flex-col items-center justify-center p-12 text-center">
      <div className="relative mb-4">
        <Loader2 className="w-10 h-10 text-cyan-400 animate-spin" />
        <div className="absolute inset-0 rounded-full blur-md bg-cyan-400/20" />
      </div>
      <p className="text-sm text-slate-400 font-mono tracking-wide">{message}</p>
    </div>
  );
};
