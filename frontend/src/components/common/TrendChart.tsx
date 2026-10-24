import React, { useState } from 'react';

export interface TrendDataPoint {
  timestamp: string; // ISO date string or formatted label
  value: number; // e.g. latencyMs or failureRate%
  secondaryValue?: number;
  label?: string;
}

interface TrendChartProps {
  title: string;
  subtitle?: string;
  unit?: string;
  data: TrendDataPoint[];
  color?: 'cyan' | 'emerald' | 'rose' | 'amber';
  height?: number;
  showDateFilter?: boolean;
  selectedRange?: '24h' | '7d' | '30d';
  onRangeChange?: (range: '24h' | '7d' | '30d') => void;
  emptyMessage?: string;
}

export const TrendChart: React.FC<TrendChartProps> = ({
  title,
  subtitle,
  unit = 'ms',
  data,
  color = 'cyan',
  height = 160,
  showDateFilter = true,
  selectedRange = '24h',
  onRangeChange,
  emptyMessage = 'No historical telemetry recorded for selected window.',
}) => {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  const colorStyles = {
    cyan: {
      stroke: '#06b6d4',
      gradientStart: 'rgba(6, 182, 212, 0.35)',
      gradientEnd: 'rgba(6, 182, 212, 0.0)',
      point: '#22d3ee',
      text: 'text-cyan-400',
    },
    emerald: {
      stroke: '#10b981',
      gradientStart: 'rgba(16, 185, 129, 0.35)',
      gradientEnd: 'rgba(16, 185, 129, 0.0)',
      point: '#34d399',
      text: 'text-emerald-400',
    },
    rose: {
      stroke: '#f43f5e',
      gradientStart: 'rgba(244, 63, 94, 0.35)',
      gradientEnd: 'rgba(244, 63, 94, 0.0)',
      point: '#fb7185',
      text: 'text-rose-400',
    },
    amber: {
      stroke: '#f59e0b',
      gradientStart: 'rgba(245, 158, 11, 0.35)',
      gradientEnd: 'rgba(245, 158, 11, 0.0)',
      point: '#fbbf24',
      text: 'text-amber-400',
    },
  }[color];

  if (!data || data.length === 0) {
    return (
      <div className="glass-panel p-5">
        <div className="flex items-center justify-between mb-3">
          <div>
            <h3 className="text-sm font-semibold text-white font-mono">{title}</h3>
            {subtitle && <p className="text-xs text-slate-400 mt-0.5">{subtitle}</p>}
          </div>
          {showDateFilter && onRangeChange && (
            <div className="flex items-center gap-1 bg-black/40 border border-white/5 rounded-lg p-0.5 text-[11px] font-mono">
              {(['24h', '7d', '30d'] as const).map(r => (
                <button
                  key={r}
                  type="button"
                  onClick={() => onRangeChange(r)}
                  className={`px-2 py-0.5 rounded ${
                    selectedRange === r ? 'bg-cyan-500/20 text-cyan-300 font-semibold' : 'text-slate-400 hover:text-white'
                  }`}
                >
                  {r.toUpperCase()}
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="h-36 flex items-center justify-center text-xs text-slate-500 font-mono italic border border-dashed border-white/5 rounded-lg bg-black/20">
          {emptyMessage}
        </div>
      </div>
    );
  }

  const values = data.map(d => d.value);
  const minVal = Math.min(...values);
  const maxVal = Math.max(...values);
  const avgVal = Math.round(values.reduce((a, b) => a + b, 0) / values.length);
  
  // Sorted for P95 calculation
  const sorted = [...values].sort((a, b) => a - b);
  const p95Val = sorted[Math.floor(sorted.length * 0.95)] || maxVal;

  const yRange = maxVal === minVal ? (maxVal === 0 ? 10 : maxVal * 0.5) : (maxVal - minVal);
  const svgPadding = 20;
  const svgWidth = 600;
  const svgHeight = height;

  const getX = (idx: number) => {
    if (data.length <= 1) return svgWidth / 2;
    return svgPadding + (idx / (data.length - 1)) * (svgWidth - svgPadding * 2);
  };

  const getY = (val: number) => {
    if (yRange === 0) return svgHeight / 2;
    const normalized = (val - minVal) / yRange;
    return (svgHeight - svgPadding) - normalized * (svgHeight - svgPadding * 2);
  };

  const points = data.map((d, i) => `${getX(i)},${getY(d.value)}`).join(' ');
  const areaPoints = `${getX(0)},${svgHeight} ${points} ${getX(data.length - 1)},${svgHeight}`;

  const hoveredData = hoveredIndex !== null ? data[hoveredIndex] : null;

  return (
    <div className="glass-panel p-5 relative">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-semibold text-white font-mono">{title}</h3>
            <span className={`text-[10px] font-mono px-1.5 py-0.2 rounded bg-white/5 border border-white/10 ${colorStyles.text}`}>
              Avg: {avgVal} {unit} | P95: {p95Val} {unit}
            </span>
          </div>
          {subtitle && <p className="text-xs text-slate-400 mt-0.5">{subtitle}</p>}
        </div>

        {showDateFilter && onRangeChange && (
          <div className="flex items-center gap-1 bg-black/40 border border-white/5 rounded-lg p-0.5 text-[11px] font-mono self-start sm:self-auto">
            {(['24h', '7d', '30d'] as const).map(r => (
              <button
                key={r}
                type="button"
                onClick={() => onRangeChange(r)}
                className={`px-2.5 py-1 rounded transition-colors ${
                  selectedRange === r ? 'bg-cyan-500/20 text-cyan-300 font-semibold' : 'text-slate-400 hover:text-white'
                }`}
              >
                {r.toUpperCase()}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* SVG Canvas */}
      <div className="relative w-full overflow-hidden select-none">
        <svg
          viewBox={`0 0 ${svgWidth} ${svgHeight}`}
          className="w-full overflow-visible"
          style={{ height: `${height}px` }}
        >
          <defs>
            <linearGradient id={`grad-${color}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={colorStyles.gradientStart} />
              <stop offset="100%" stopColor={colorStyles.gradientEnd} />
            </linearGradient>
          </defs>

          {/* Grid lines */}
          <line
            x1={svgPadding}
            y1={getY(maxVal)}
            x2={svgWidth - svgPadding}
            y2={getY(maxVal)}
            stroke="rgba(255,255,255,0.06)"
            strokeDasharray="3 3"
          />
          <line
            x1={svgPadding}
            y1={getY(minVal)}
            x2={svgWidth - svgPadding}
            y2={getY(minVal)}
            stroke="rgba(255,255,255,0.06)"
            strokeDasharray="3 3"
          />

          {/* Area fill */}
          <polygon points={areaPoints} fill={`url(#grad-${color})`} />

          {/* Line stroke */}
          <polyline
            points={points}
            fill="none"
            stroke={colorStyles.stroke}
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />

          {/* Data Points */}
          {data.map((d, i) => {
            const cx = getX(i);
            const cy = getY(d.value);
            const isHovered = hoveredIndex === i;
            return (
              <g key={i}>
                <circle
                  cx={cx}
                  cy={cy}
                  r={isHovered ? 5 : 2.5}
                  fill={colorStyles.point}
                  className="transition-all cursor-pointer"
                  onMouseEnter={() => setHoveredIndex(i)}
                  onMouseLeave={() => setHoveredIndex(null)}
                />
              </g>
            );
          })}
        </svg>

        {/* Floating Tooltip */}
        {hoveredData && (
          <div className="absolute top-2 right-2 bg-slate-900/90 border border-white/10 rounded px-2.5 py-1 text-[11px] font-mono text-slate-200 pointer-events-none shadow-xl">
            <div className="text-white font-bold">
              {hoveredData.value} {unit}
            </div>
            <div className="text-slate-400 text-[10px]">
              {hoveredData.label || hoveredData.timestamp}
            </div>
          </div>
        )}
      </div>

      {/* Axis Labels */}
      <div className="flex justify-between text-[10px] font-mono text-slate-500 mt-2 px-1">
        <span>{data[0]?.label || data[0]?.timestamp.substring(11, 16) || 'Start'}</span>
        <span>Min: {minVal}{unit} / Max: {maxVal}{unit}</span>
        <span>{data[data.length - 1]?.label || data[data.length - 1]?.timestamp.substring(11, 16) || 'Latest'}</span>
      </div>
    </div>
  );
};
