import { useState, useRef, useEffect } from 'react';
import { MoreHorizontal } from 'lucide-react';

/**
 * A single "⋯" button that reveals a dropdown menu of actions.
 *
 * Usage:
 *   <DropdownActions items={[
 *     { label: 'Edit', icon: <Edit2 />, onClick: () => ... },
 *     { label: 'Delete', icon: <Trash2 />, onClick: () => ..., danger: true },
 *   ]} />
 */
export default function DropdownActions({ items = [], align = 'right' }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return;
    const handleClick = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [open]);

  return (
    <div ref={ref} className="relative inline-block" onClick={(e) => e.stopPropagation()}>
      <button
        onClick={() => setOpen((v) => !v)}
        className={`p-1.5 rounded-lg transition-colors text-slate-400 border ${
          open
            ? 'bg-slate-700 text-white border-slate-600'
            : 'hover:bg-slate-800 hover:text-white border-transparent hover:border-slate-700'
        }`}
        title="Actions"
      >
        <MoreHorizontal className="w-4 h-4" />
      </button>

      {open && (
        <div
          className={`absolute z-50 mt-1 min-w-[160px] bg-slate-900 border border-slate-700 rounded-xl shadow-2xl shadow-slate-950/60 py-1 overflow-hidden ${
            align === 'right' ? 'right-0' : 'left-0'
          }`}
        >
          {items.map((item, i) =>
            item === 'divider' ? (
              <div key={i} className="my-1 border-t border-slate-800" />
            ) : (
              <button
                key={i}
                onClick={() => {
                  setOpen(false);
                  item.onClick?.();
                }}
                disabled={item.disabled}
                className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs font-medium transition-colors text-left disabled:opacity-40 disabled:cursor-not-allowed ${
                  item.danger
                    ? 'text-rose-400 hover:bg-rose-500/10 hover:text-rose-300'
                    : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`}
              >
                {item.icon && (
                  <span className={`shrink-0 w-3.5 h-3.5 flex items-center justify-center ${
                    item.danger ? 'text-rose-400' : 'text-slate-400'
                  }`}>
                    {item.icon}
                  </span>
                )}
                {item.label}
              </button>
            )
          )}
        </div>
      )}
    </div>
  );
}
