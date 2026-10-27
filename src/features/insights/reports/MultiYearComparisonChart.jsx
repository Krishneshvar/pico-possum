import { useState, useMemo } from 'react';
import { Calendar, Layers, Eye, EyeOff } from 'lucide-react';
import { formatPaise } from '../../../lib/formatters';

const MONTH_LABELS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const SERIES_COLORS = [
  { stroke: '#10b981', fill: 'rgba(16, 185, 129, 0.15)', name: 'Emerald' },
  { stroke: '#06b6d4', fill: 'rgba(6, 182, 212, 0.15)', name: 'Cyan' },
  { stroke: '#8b5cf6', fill: 'rgba(139, 92, 246, 0.15)', name: 'Violet' },
  { stroke: '#f59e0b', fill: 'rgba(245, 158, 11, 0.15)', name: 'Amber' },
  { stroke: '#ec4899', fill: 'rgba(236, 72, 153, 0.15)', name: 'Pink' },
  { stroke: '#3b82f6', fill: 'rgba(59, 130, 246, 0.15)', name: 'Blue' },
];

export default function MultiYearComparisonChart({
  data = [],
  yearsBack = 3,
  onYearsBackChange,
  isLoading = false,
  paymentMethod = 'ALL',
}) {
  const [hoveredPoint, setHoveredPoint] = useState(null); // { year, monthIdx, value, transactions, x, y }
  const [hoveredMonthIdx, setHoveredMonthIdx] = useState(null);
  const [hiddenYears, setHiddenYears] = useState(new Set());

  // Extract all distinct years present in the dataset or past N years
  const { years, yearDataMap, maxRevenue } = useMemo(() => {
    const map = {};
    const yearSet = new Set();
    const currentYear = new Date().getFullYear();

    // Ensure past N years exist
    for (let i = 0; i < yearsBack; i++) {
      yearSet.add(String(currentYear - i));
    }

    data.forEach((item) => {
      if (item.year) {
        yearSet.add(String(item.year));
        if (!map[item.year]) {
          map[item.year] = {};
        }
        const m = parseInt(item.month, 10);
        map[item.year][m] = {
          revenue: item.total_revenue_paise || 0,
          transactions: item.total_transactions || 0,
        };
      }
    });

    const sortedYears = Array.from(yearSet).sort((a, b) => b.localeCompare(a)); // Descending order (newest first)

    let maxVal = 0;
    sortedYears.forEach((yr) => {
      for (let m = 1; m <= 12; m++) {
        const val = map[yr]?.[m]?.revenue || 0;
        if (val > maxVal) maxVal = val;
      }
    });

    return {
      years: sortedYears,
      yearDataMap: map,
      maxRevenue: maxVal > 0 ? maxVal : 100000, // Default baseline ₹1,000 if empty
    };
  }, [data, yearsBack]);

  const toggleYearVisibility = (yr) => {
    setHiddenYears((prev) => {
      const next = new Set(prev);
      if (next.has(yr)) {
        next.delete(yr);
      } else {
        // Prevent hiding all years
        if (next.size < years.length - 1) {
          next.add(yr);
        }
      }
      return next;
    });
  };

  // Dimensions for responsive SVG
  const width = 800;
  const height = 300;
  const padding = { top: 25, right: 35, bottom: 40, left: 75 };
  const chartWidth = width - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;

  // Nice Y-axis ceiling & steps
  const yTicksCount = 4;
  const yCeil = Math.ceil(maxRevenue / 100000) * 100000 || 100000;
  const yTicks = Array.from({ length: yTicksCount + 1 }, (_, i) => Math.round((yCeil / yTicksCount) * i));

  const getX = (monthIdx) => padding.left + (monthIdx / 11) * chartWidth;
  const getY = (val) => padding.top + chartHeight - (val / yCeil) * chartHeight;

  return (
    <div className="glass-card p-6 rounded-2xl border border-slate-800 space-y-4">
      {/* Chart Header & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg bg-emerald-500/10 text-emerald-400">
              <Calendar className="w-4 h-4" />
            </span>
            <h3 className="text-sm font-bold text-slate-100">Multi-Year Performance Comparison</h3>
          </div>
          <p className="text-xs text-slate-400">
            Compare monthly sales revenue trends across multiple years
            {paymentMethod !== 'ALL' && (
              <span className="ml-1 text-emerald-400 font-semibold font-mono">
                ({paymentMethod})
              </span>
            )}
          </p>
        </div>

        {/* Controls: Years back selector */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-1.5 bg-slate-900 border border-slate-800 rounded-xl px-2.5 py-1 text-xs text-slate-300">
            <span className="text-slate-500 font-medium">Compare:</span>
            <select
              value={yearsBack}
              onChange={(e) => onYearsBackChange(Number(e.target.value))}
              className="bg-transparent text-emerald-400 font-semibold focus:outline-none cursor-pointer"
            >
              <option value={2} className="bg-slate-900 text-slate-200">Last 2 Years</option>
              <option value={3} className="bg-slate-900 text-slate-200">Last 3 Years</option>
              <option value={5} className="bg-slate-900 text-slate-200">Last 5 Years</option>
            </select>
          </div>
        </div>
      </div>

      {/* Series Toggle Chips / Legend */}
      <div className="flex items-center flex-wrap gap-2 pt-1 border-t border-slate-800/60">
        <span className="text-[11px] text-slate-500 font-medium flex items-center gap-1 mr-1">
          <Layers className="w-3.5 h-3.5" /> Series:
        </span>
        {years.map((yr, idx) => {
          const color = SERIES_COLORS[idx % SERIES_COLORS.length];
          const isHidden = hiddenYears.has(yr);
          return (
            <button
              key={yr}
              type="button"
              onClick={() => toggleYearVisibility(yr)}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-mono font-semibold transition-all border ${
                isHidden
                  ? 'bg-slate-900/40 text-slate-500 border-slate-800/50 opacity-60'
                  : 'bg-slate-900 text-slate-200 border-slate-700 hover:border-slate-600 shadow-sm'
              }`}
              title={isHidden ? `Show ${yr} series` : `Hide ${yr} series`}
            >
              <span
                className="w-2.5 h-2.5 rounded-full"
                style={{ backgroundColor: isHidden ? '#475569' : color.stroke }}
              />
              <span>{yr}</span>
              {isHidden ? <EyeOff className="w-3 h-3 text-slate-500 ml-0.5" /> : <Eye className="w-3 h-3 text-slate-400 ml-0.5" />}
            </button>
          );
        })}
      </div>

      {/* Interactive SVG Chart Area */}
      <div className="relative w-full aspect-[8/3] min-h-[260px] bg-slate-950/40 rounded-xl border border-slate-800/80 p-2 overflow-hidden flex items-center justify-center">
        {isLoading ? (
          <div className="text-xs text-slate-500 font-medium animate-pulse">
            Loading multi-year sales data...
          </div>
        ) : (
          <svg
            viewBox={`0 0 ${width} ${height}`}
            className="w-full h-full select-none overflow-visible"
            onMouseLeave={() => {
              setHoveredPoint(null);
              setHoveredMonthIdx(null);
            }}
          >
            <defs>
              {years.map((yr, idx) => {
                const color = SERIES_COLORS[idx % SERIES_COLORS.length];
                return (
                  <linearGradient key={`grad-${yr}`} id={`grad-${yr}`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={color.stroke} stopOpacity="0.3" />
                    <stop offset="100%" stopColor={color.stroke} stopOpacity="0.0" />
                  </linearGradient>
                );
              })}
            </defs>

            {/* Horizontal Grid Lines & Y Axis Labels */}
            {yTicks.map((tickVal, i) => {
              const y = getY(tickVal);
              return (
                <g key={`y-tick-${i}`}>
                  <line
                    x1={padding.left}
                    y1={y}
                    x2={width - padding.right}
                    y2={y}
                    stroke="#1e293b"
                    strokeDasharray={i === 0 ? 'none' : '4,4'}
                    strokeWidth="1"
                  />
                  <text
                    x={padding.left - 10}
                    y={y + 3.5}
                    textAnchor="end"
                    className="fill-slate-500 text-[10px] font-mono"
                  >
                    {formatPaise(tickVal)}
                  </text>
                </g>
              );
            })}

            {/* Hovered Month Vertical Guideline */}
            {hoveredMonthIdx !== null && (
              <line
                x1={getX(hoveredMonthIdx)}
                y1={padding.top}
                x2={getX(hoveredMonthIdx)}
                y2={height - padding.bottom}
                stroke="#38bdf8"
                strokeWidth="1.5"
                strokeDasharray="3,3"
                opacity="0.8"
              />
            )}

            {/* X Axis Labels */}
            {MONTH_LABELS.map((name, idx) => {
              const x = getX(idx);
              const isSelected = hoveredMonthIdx === idx;
              return (
                <text
                  key={`month-${name}`}
                  x={x}
                  y={height - padding.bottom + 20}
                  textAnchor="middle"
                  className={`text-[11px] font-mono transition-colors ${
                    isSelected ? 'fill-emerald-400 font-bold' : 'fill-slate-400'
                  }`}
                >
                  {name}
                </text>
              );
            })}

            {/* Lines and Areas for Each Year */}
            {years
              .filter((yr) => !hiddenYears.has(yr))
              .map((yr) => {
                const yearOriginalIdx = years.indexOf(yr);
                const color = SERIES_COLORS[yearOriginalIdx % SERIES_COLORS.length];

                // Build points array for the 12 months
                const points = MONTH_LABELS.map((_, mIdx) => {
                  const m = mIdx + 1;
                  const item = yearDataMap[yr]?.[m];
                  const rev = item?.revenue || 0;
                  return {
                    monthIdx: mIdx,
                    monthName: MONTH_LABELS[mIdx],
                    value: rev,
                    transactions: item?.transactions || 0,
                    x: getX(mIdx),
                    y: getY(rev),
                  };
                });

                // Path points
                const pathD = points.reduce((acc, p, idx) => {
                  return `${acc} ${idx === 0 ? 'M' : 'L'} ${p.x.toFixed(1)},${p.y.toFixed(1)}`;
                }, '');

                // Area path for gradient fill
                const areaD = `${pathD} L ${points[points.length - 1].x.toFixed(1)},${(height - padding.bottom).toFixed(1)} L ${points[0].x.toFixed(1)},${(height - padding.bottom).toFixed(1)} Z`;

                return (
                  <g key={`series-${yr}`} className="transition-opacity duration-300">
                    {/* Area fill */}
                    <path d={areaD} fill={`url(#grad-${yr})`} opacity="0.8" />

                    {/* Main Line */}
                    <path
                      d={pathD}
                      fill="none"
                      stroke={color.stroke}
                      strokeWidth="2.5"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      className="hover:stroke-width-3 transition-all"
                    />

                    {/* Data Points */}
                    {points.map((p) => {
                      const isHovered =
                        hoveredPoint?.year === yr && hoveredPoint?.monthIdx === p.monthIdx;
                      return (
                        <g key={`pt-${yr}-${p.monthIdx}`}>
                          {/* Invisible larger hit target */}
                          <circle
                            cx={p.x}
                            cy={p.y}
                            r="12"
                            fill="transparent"
                            className="cursor-pointer"
                            onMouseEnter={() => {
                              setHoveredPoint({
                                year: yr,
                                monthName: p.monthName,
                                monthIdx: p.monthIdx,
                                value: p.value,
                                transactions: p.transactions,
                                color: color.stroke,
                                x: p.x,
                                y: p.y,
                              });
                              setHoveredMonthIdx(p.monthIdx);
                            }}
                          />
                          {/* Visible point circle */}
                          <circle
                            cx={p.x}
                            cy={p.y}
                            r={isHovered ? '6' : '3.5'}
                            fill={isHovered ? '#ffffff' : color.stroke}
                            stroke={color.stroke}
                            strokeWidth="2"
                            className="transition-all duration-150 pointer-events-none"
                          />
                        </g>
                      );
                    })}
                  </g>
                );
              })}
          </svg>
        )}

        {/* Floating Tooltip */}
        {hoveredPoint && (
          <div
            className="absolute z-20 pointer-events-none transform -translate-x-1/2 -translate-y-full mb-3 bg-slate-900/95 backdrop-blur-md border border-slate-700/80 rounded-xl px-3.5 py-2 shadow-2xl space-y-1"
            style={{
              left: `${(hoveredPoint.x / width) * 100}%`,
              top: `${(hoveredPoint.y / height) * 100}%`,
            }}
          >
            <div className="flex items-center justify-between gap-3 text-xs border-b border-slate-800 pb-1">
              <span className="font-bold text-slate-200">
                {hoveredPoint.monthName} {hoveredPoint.year}
              </span>
              <span
                className="w-2.5 h-2.5 rounded-full"
                style={{ backgroundColor: hoveredPoint.color }}
              />
            </div>
            <div className="text-sm font-black font-mono" style={{ color: hoveredPoint.color }}>
              {formatPaise(hoveredPoint.value)}
            </div>
            <div className="text-[10px] text-slate-400 font-mono">
              {hoveredPoint.transactions} {hoveredPoint.transactions === 1 ? 'sale' : 'sales'} recorded
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
