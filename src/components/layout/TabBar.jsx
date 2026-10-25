import { useRef, useEffect } from 'react';
import { X } from 'lucide-react';
import { useWorkspaceStore } from '../../stores/workspaceStore';

/**
 * Browser-style tab strip.
 * - Shows open tabs, highlights active
 * - Close button per tab (Ctrl+W)
 * - Horizontally scrollable when tabs overflow
 * - Shows placeholder when no tabs
 */
export default function TabBar() {
  const { tabs, activeTabId, setActiveTab, closeTab } = useWorkspaceStore();
  const scrollRef = useRef(null);

  // Scroll active tab into view
  useEffect(() => {
    if (!scrollRef.current) return;
    const activeEl = scrollRef.current.querySelector('[data-active="true"]');
    if (activeEl) activeEl.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
  }, [activeTabId]);

  // Global Ctrl+W to close active tab
  useEffect(() => {
    const handler = (e) => {
      if (e.ctrlKey && e.key === 'w') {
        e.preventDefault();
        if (activeTabId) closeTab(activeTabId);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [activeTabId, closeTab]);

  return (
    <div className="h-9 bg-slate-950 border-b border-slate-800/80 flex items-end overflow-hidden flex-shrink-0">
      {tabs.length === 0 ? (
        <div className="flex-1 flex items-center px-4 pb-1.5">
          <span className="text-[11px] text-slate-600 italic">
            No tabs open — use the menu above to open a view
          </span>
        </div>
      ) : (
        <div
          ref={scrollRef}
          className="flex items-end gap-0.5 px-2 overflow-x-auto flex-1"
          style={{ scrollbarWidth: 'none' }}
        >
          {tabs.map((tab) => {
            const isActive = tab.id === activeTabId;
            return (
              <div
                key={tab.id}
                data-active={isActive}
                onClick={() => setActiveTab(tab.id)}
                className={`
                  group flex items-center gap-2 px-3 py-1.5 rounded-t-lg
                  text-xs font-medium cursor-pointer select-none
                  max-w-48 min-w-24 flex-shrink-0
                  border border-b-0 transition-all
                  ${isActive
                    ? 'bg-slate-900 border-slate-800/80 text-slate-100 shadow-sm'
                    : 'bg-slate-950 border-transparent text-slate-500 hover:text-slate-300 hover:bg-slate-900/50'
                  }
                `}
              >
                {/* Active indicator dot */}
                {isActive && (
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 flex-shrink-0" />
                )}

                {/* Tab title */}
                <span className="truncate flex-1">{tab.title}</span>

                {/* Close button */}
                {tab.closeable && (
                  <button
                    onClick={(e) => { e.stopPropagation(); closeTab(tab.id); }}
                    title="Close tab (Ctrl+W)"
                    className={`
                      flex-shrink-0 w-4 h-4 rounded flex items-center justify-center
                      transition-all
                      ${isActive
                        ? 'text-slate-400 hover:text-white hover:bg-slate-700'
                        : 'opacity-0 group-hover:opacity-100 text-slate-500 hover:text-white hover:bg-slate-800'
                      }
                    `}
                  >
                    <X className="w-2.5 h-2.5" />
                  </button>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Tab count badge */}
      {tabs.length > 0 && (
        <div className="px-2 pb-2 flex-shrink-0">
          <span className="text-[10px] text-slate-600">{tabs.length}/10</span>
        </div>
      )}
    </div>
  );
}
