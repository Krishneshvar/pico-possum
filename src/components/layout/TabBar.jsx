import { useRef, useEffect, useState } from 'react';
import { X } from 'lucide-react';
import { useWorkspaceStore } from '../../stores/workspaceStore';

/**
 * Browser-style tab strip.
 * - Shows open tabs, highlights active
 * - Close button per tab (Ctrl+W)
 * - Drag to reorder tabs (pointer based, works with mouse & touch)
 * - Horizontally scrollable when tabs overflow
 * - Shows placeholder when no tabs
 */
export default function TabBar() {
  const { tabs, activeTabId, setActiveTab, closeTab, moveTab } = useWorkspaceStore();
  const scrollRef = useRef(null);
  const dragRef = useRef(null);
  const suppressClickRef = useRef(false);
  const [draggingId, setDraggingId] = useState(null);

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

  // Pointer-based drag & drop reordering
  useEffect(() => {
    const handleMove = (e) => {
      const drag = dragRef.current;
      if (!drag || e.pointerId !== drag.pointerId) return;

      if (!drag.dragging) {
        const dx = Math.abs(e.clientX - drag.startX);
        const dy = Math.abs(e.clientY - drag.startY);
        // Ignore tiny jitters; cancel when the gesture is mostly vertical
        if (dx < 4 && dy < 4) return;
        if (dy > dx) {
          dragRef.current = null;
          return;
        }
        drag.dragging = true;
        suppressClickRef.current = true;
        setDraggingId(drag.id);
      }

      e.preventDefault();

      const container = scrollRef.current;
      if (!container) return;

      // Auto-scroll when dragging near the strip edges
      const bounds = container.getBoundingClientRect();
      if (e.clientX < bounds.left + 28) container.scrollLeft -= 14;
      else if (e.clientX > bounds.right - 28) container.scrollLeft += 14;

      const tabEls = Array.from(container.querySelectorAll('[data-tab-id]'));
      const fromIndex = tabEls.findIndex((el) => el.dataset.tabId === String(drag.id));
      if (fromIndex === -1) return;

      // Insert before the first tab whose midpoint is past the pointer
      let toIndex = tabEls.length;
      for (let i = 0; i < tabEls.length; i++) {
        const rect = tabEls[i].getBoundingClientRect();
        if (e.clientX < rect.left + rect.width / 2) {
          toIndex = i;
          break;
        }
      }
      if (toIndex > fromIndex) toIndex -= 1;

      const { tabs: currentTabs, moveTab: move } = useWorkspaceStore.getState();
      if (currentTabs.findIndex((t) => t.id === drag.id) !== toIndex) move(drag.id, toIndex);
    };

    const handleUp = (e) => {
      const drag = dragRef.current;
      if (!drag || e.pointerId !== drag.pointerId) return;
      dragRef.current = null;
      setDraggingId(null);
      // Swallow the click that follows a drag, then re-enable activation
      if (suppressClickRef.current) {
        setTimeout(() => { suppressClickRef.current = false; }, 0);
      }
    };

    window.addEventListener('pointermove', handleMove);
    window.addEventListener('pointerup', handleUp);
    window.addEventListener('pointercancel', handleUp);
    return () => {
      window.removeEventListener('pointermove', handleMove);
      window.removeEventListener('pointerup', handleUp);
      window.removeEventListener('pointercancel', handleUp);
    };
  }, []);

  const handlePointerDown = (e, tab) => {
    if (e.button !== 0) return;
    if (e.target.closest('button')) return;
    suppressClickRef.current = false;
    dragRef.current = {
      id: tab.id,
      pointerId: e.pointerId,
      startX: e.clientX,
      startY: e.clientY,
      dragging: false,
    };
  };

  const handleTabClick = (id) => {
    if (suppressClickRef.current) return;
    setActiveTab(id);
  };

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
            const isDragging = tab.id === draggingId;
            return (
              <div
                key={tab.id}
                data-tab-id={tab.id}
                data-active={isActive}
                onPointerDown={(e) => handlePointerDown(e, tab)}
                onClick={() => handleTabClick(tab.id)}
                style={{ touchAction: 'pan-y' }}
                className={`
                  group flex items-center gap-2 px-3 py-1.5 rounded-t-lg
                  text-xs font-medium cursor-pointer select-none
                  max-w-48 min-w-24 flex-shrink-0
                  border border-b-0 transition-all
                  ${isActive
                    ? 'bg-slate-900 border-slate-800/80 text-slate-100 shadow-sm'
                    : 'bg-slate-950 border-transparent text-slate-500 hover:text-slate-300 hover:bg-slate-900/50'
                  }
                  ${isDragging
                    ? 'opacity-50 cursor-grabbing ring-1 ring-emerald-400/50'
                    : 'active:cursor-grabbing'
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
                    onPointerDown={(e) => e.stopPropagation()}
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
