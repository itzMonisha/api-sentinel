import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

interface PaginationProps {
  currentPage: number;
  totalPages: number;
  totalElements?: number;
  pageSize?: number;
  onPageChange: (newPage: number) => void;
  className?: string;
}

export const Pagination: React.FC<PaginationProps> = ({
  currentPage,
  totalPages,
  totalElements,
  pageSize = 10,
  onPageChange,
  className = '',
}) => {
  if (totalPages <= 1 && (totalElements === undefined || totalElements <= pageSize)) {
    return null;
  }

  const startItem = currentPage * pageSize + 1;
  const endItem = totalElements !== undefined ? Math.min((currentPage + 1) * pageSize, totalElements) : (currentPage + 1) * pageSize;

  return (
    <div className={`flex flex-col sm:flex-row items-center justify-between gap-3 text-xs font-mono text-slate-400 py-3 ${className}`}>
      <div>
        {totalElements !== undefined ? (
          <span>
            Showing <strong className="text-slate-200">{startItem}</strong> -{' '}
            <strong className="text-slate-200">{endItem}</strong> of{' '}
            <strong className="text-slate-200">{totalElements}</strong> items
          </span>
        ) : (
          <span>
            Page <strong className="text-slate-200">{currentPage + 1}</strong> of{' '}
            <strong className="text-slate-200">{totalPages}</strong>
          </span>
        )}
      </div>

      <div className="flex items-center gap-1.5">
        <button
          type="button"
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage <= 0}
          aria-label="Previous page"
          className="p-1.5 rounded-lg bg-white/5 border border-white/10 text-slate-300 hover:text-white hover:bg-white/10 disabled:opacity-30 disabled:cursor-not-allowed transition-all"
        >
          <ChevronLeft className="w-4 h-4" />
        </button>

        <span className="px-2 py-1 text-slate-300">
          {currentPage + 1} / {Math.max(1, totalPages)}
        </span>

        <button
          type="button"
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage >= totalPages - 1}
          aria-label="Next page"
          className="p-1.5 rounded-lg bg-white/5 border border-white/10 text-slate-300 hover:text-white hover:bg-white/10 disabled:opacity-30 disabled:cursor-not-allowed transition-all"
        >
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
};
