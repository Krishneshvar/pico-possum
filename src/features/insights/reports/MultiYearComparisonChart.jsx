import { useState, useMemo } from 'react';
import { Calendar, Layers, Eye, EyeOff } from 'lucide-react';
import { formatPaise } from '../../../lib/formatters';

const MONTH_LABELS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** Recommended Categorical Chart Palette */
const SERIES_PALETTE = [
  { stroke: '#2563EB', fill: 'rgba(37, 99, 235, 0.12)', name: 'Blue', dash: 'none', shape: 'circle' },
  { stroke: '#0F766E', fill: 'rgba(15, 118, 110, 0.12)', name: 'Teal', dash: 'none', shape: 'square' },
  { stroke: '#7C3AED', fill: 'rgba(124, 58, 237, 0.12)', name: 'Purple', dash: 'none', shape: 'triangle' },
  { stroke: '#C2410C', fill: 'rgba(194, 65, 12, 0.12)', name: 'Orange', dash: '4,4', shape: 'circle' },
  { stroke: '#BE185D', fill: 'rgba(190, 24, 93, 0.12)', name: 'Pink', dash: '6,3', shape: 'square' },
  { stroke: '#475569', fill: 'rgba(71, 85, 105, 0.12)', name: 'Slate', dash: '2,2', shape: 'circle' },
];

export default function MultiYearComparisonChart({
  data = [],
  yearsBack = 3,
  onYearsBackChange,
  isLoading = false,
  paymentMethod = 'ALL',
}) {
  const [hoveredPoint, setHoveredPoint] = useState(null);
  const [hoveredMonthIdx, setHoveredMonthIdx] = useState(null);
  const [hiddenYears, setHiddenYears] = useState(new Set());

  const { years, yearDataMap, maxRevenue } = useMemo(() => {
    const map = {};
    const yearSet = new Set();
    const currentYear = new Date().getFullYear();

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

    const sortedYears = Array.from(yearSet).sort((a, b) => b.localeCompare(a));

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
      maxRevenue: maxVal > 0 ? maxVal : 100000,
    };
  }, [data, yearsBack]);

  const toggleYearVisibility = (yr) => {
    setHiddenYears((prev) => {
      const next = new Set(prev);
      if (next.has(yr)) {
        next.delete(yr);
      } else {
        if (next.size < years.length - 1) {
          next.add(yr);
        }
      }
      return next;
    });
  };

  const width = 800;
  const height = 300;
  const padding = { top: 25, right: 35, bottom: 40, left: 75 };
  const chartWidth = width - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;

  const yTicksCount = 4;
  const yCeil = Math.ceil(maxRevenue / 100000) * 100000 || 100000;
  const yTicks = Array.from({ length: yTicksCount + 1 }, (_, i) => Math.round((yCeil / yTicksCount) * i));

  const getX = (monthIdx) => padding.left + (monthIdx / 11) * chartWidth;
  const getY = (val) => padding.top + chartHeight - (val / yCeil) * chartHeight;

  return (
    <div className="solid-card p-5 rounded-2xl border border-border-default bg-surface space-y-4">
      {/* Chart Header & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg bg-brand-subtle text-brand">
              <Calendar className="w-4 h-4" />
            </span>
            <h3 className="text-sm font-bold text-text-primary">Multi-Year Revenue Comparison</h3>
          </div>
          <p className="text-xs text-text-muted">
            Monthly revenue performance trends across comparison years
            {paymentMethod !== 'ALL' && (
              <span className="ml-1 text-brand font-semibold font-mono">({paymentMethod})</span>
            )}
          </p>
        </div>

        {/* Years back selector */}
        <div className="flex items-center gap-2 text-xs">
          <span className="text-text-muted">Comparison Range:</span>
          <div className="flex bg-surface-secondary border border-border-default rounded-lg p-0.5">
            {[2, 3, 4, 5].map((y) => (
              <button
                key={y}
                type="button"
                onClick={() => onYearsBackChange && onYearsBackChange(y)}
                className={`px-2.5 py-1 rounded text-xs font-semibold transition-colors cursor-pointer ${
                  yearsBack === y
                    ? 'bg-brand text-white shadow-2xs'
                    : 'text-text-secondary hover:text-text-primary'
                }`}
              >
                {y} Years
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* SVG Chart */}
      <div className="relative w-full aspect-[8/3] max-h-[340px] select-none">
        {isLoading && (
          <div className="absolute inset-0 bg-surface/80 flex items-center justify-center z-20 backdrop-blur-xs">
            <div className="flex items-center gap-2 text-xs text-text-muted">
              <div className="w-4 h-4 border-2 border-brand border-t-transparent rounded-full animate-spin" />
              <span>Updating comparison data…</span>
            </div>
          </div>
        )}

        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="w-full h-full overflow-visible"
          onMouseLeave={() => {
            setHoveredPoint(null);
            setHoveredMonthIdx(null);
          }}
        >
          {/* Y-axis grid lines & labels */}
          {yTicks.map((tickVal, i) => {
            const y = getY(tickVal);
            return (
              <g key={i}>
                <line
                  x1={padding.left}
                  y1={y}
                  x2={width - padding.right}
                  y2={y}
                  stroke="var(--color-border-default)"
                  strokeDasharray="4 4"
                  strokeWidth="1"
                />
                <text
                  x={padding.left - 10}
                  y={y + 3.5}
                  textAnchor="end"
                  className="fill-text-muted text-[10px] font-mono"
                >
                  {formatPaise(tickVal)}
                </text>
              </g>
            );
          })}

          {/* X-axis Month Labels */}
          {MONTH_LABELS.map((m, i) => {
            const x = getX(i);
            const isHovered = hoveredMonthIdx === i;
            return (
              <g key={m}>
                {isHovered && (
                  <line
                    x1={x}
                    y1={padding.top}
                    x2={x}
                    y2={padding.top + chartHeight}
                    stroke="var(--color-brand)"
                    strokeWidth="1.5"
                    strokeDasharray="2 2"
                    opacity="0.6"
                  />
                )}
                <text
                  x={x}
                  y={height - padding.bottom + 20}
                  textAnchor="middle"
                  className={`text-[11px] font-medium transition-colors ${
                    isHovered ? 'fill-brand font-bold' : 'fill-text-muted'
                  }`}
                >
                  {m}
                </text>
              </g>
            );
          })}

          {/* Lines & Data for each year in categorical series */}
          {years.map((year, yearIdx) => {
            if (hiddenYears.has(year)) return null;
            const color = SERIES_PALETTE[yearIdx % SERIES_PALETTE.length];

            const points = [];
            for (let m = 1; m <= 12; m++) {
              const val = yearDataMap[year]?.[m]?.revenue || 0;
              const txns = yearDataMap[year]?.[m]?.transactions || 0;
              points.push({
                monthIdx: m - 1,
                monthName: MONTH_LABELS[m - 1],
                value: val,
                transactions: txns,
                x: getX(m - 1),
                y: getY(val),
              });
            }

            const pathD = points.reduce((acc, pt, i) => {
              return i === 0 ? `M ${pt.x} ${pt.y}` : `${acc} L ${pt.x} ${pt.y}`;
            }, '');

            return (
              <g key={year}>
                <path
                  d={pathD}
                  fill="none"
                  stroke={color.stroke}
                  strokeWidth="2.5"
                  strokeDasharray={color.dash}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />

                {points.map((pt) => {
                  const isHovered =
                    hoveredPoint?.year === year && hoveredPoint?.monthIdx === pt.monthIdx;
                  return (
                    <circle
                      key={pt.monthIdx}
                      cx={pt.x}
                      cy={pt.y}
                      r={isHovered ? 6 : 3.5}
                      fill={isHovered ? 'white' : color.stroke}
                      stroke={color.stroke}
                      strokeWidth={isHovered ? 3 : 1.5}
                      className="cursor-pointer transition-all"
                      onMouseEnter={() => {
                        setHoveredPoint({
                          year,
                          monthName: pt.monthName,
                          monthIdx: pt.monthIdx,
                          value: pt.value,
                          transactions: pt.transactions,
                          x: pt.x,
                          y: pt.y,
                          color: color.stroke,
                        });
                        setHoveredMonthIdx(pt.monthIdx);
                      }}
                    />
                  );
                })}
              </g>
            );
          })}
        </svg>

        {/* Hover Tooltip */}
        {hoveredPoint && (
          <div
            style={{
              position: 'absolute',
              left: `${(hoveredPoint.x / width) * 100}%`,
              top: `${(hoveredPoint.y / height) * 100}%`,
              transform: 'translate(-50%, -125%)',
            }}
            className="z-30 pointer-events-none bg-surface border border-border-default rounded-xl shadow-lg p-2.5 text-xs whitespace-nowrap animate-scale-in"
          >
            <div className="flex items-center gap-2 mb-1 border-b border-border-default pb-1">
              <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: hoveredPoint.color }} />
              <span className="font-bold text-text-primary">
                {hoveredPoint.year} · {hoveredPoint.monthName}
              </span>
            </div>
            <div className="space-y-0.5">
              <div className="flex justify-between gap-4">
                <span className="text-text-muted">Revenue:</span>
                <span className="font-bold financial-value text-text-primary">
                  {formatPaise(hoveredPoint.value)}
                </span>
              </div>
              <div className="flex justify-between gap-4">
                <span className="text-text-muted">Transactions:</span>
                <span className="font-mono text-text-secondary">{hoveredPoint.transactions}</span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Categorical Series Legend */}
      <div className="flex items-center justify-center gap-4 flex-wrap pt-2 border-t border-border-default text-xs">
        {years.map((year, idx) => {
          const color = SERIES_PALETTE[idx % SERIES_PALETTE.length];
          const isHidden = hiddenYears.has(year);

          return (
            <button
              key={year}
              type="button"
              onClick={() => toggleYearVisibility(year)}
              className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border transition-colors cursor-pointer ${
                isHidden
                  ? 'border-dashed border-border-default opacity-40 bg-surface'
                  : 'border-border-default bg-surface hover:bg-surface-secondary shadow-2xs'
              }`}
            >
              <span className="w-3 h-3 rounded-full" style={{ backgroundColor: color.stroke }} />
              <span className="font-semibold text-text-primary">{year}</span>
              {isHidden ? (
                <EyeOff className="w-3 h-3 text-text-muted" />
              ) : (
                <Eye className="w-3 h-3 text-text-muted" />
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
