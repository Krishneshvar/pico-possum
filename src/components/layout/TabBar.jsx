import { useRef, useEffect, useState } from 'react';
import { X, MoreVertical, Circle } from 'lucide-react';
import { useWorkspaceStore } from '../../stores/workspaceStore';
import { confirmDialog } from '../../stores/confirm.store';
import { strings } from '../../lib/strings';

/**
 * Browser-style workspace tab strip.
 * - Active tab highlighted with brand blue accent and elevated surface
 * - Context menu: Close, Close Others, Close All
 * - Unsaved changes indicator
 * - Pointer-based drag & drop reordering
 */
export default function TabBar() {
  const {
    tabs,
    activeTabId,
    setActiveTab,
    closeTab,
    closeOtherTabs,
    closeAllTabs,
    moveTab,
  } = useWorkspaceStore();

  const scrollRef = useRef(null);
  const dragRef = useRef(null);
  const suppressClickRef = useRef(false);
  const [draggingId, setDraggingId] = useState(null);
  const [contextMenu, setContextMenu] = useState(null); // { x, y, tabId }
  const menuRef = useRef(null);

  const handleAttemptCloseTab = async (tabId) => {
    const tab = tabs.find((t) => t.id === tabId);
    if (!tab) return;
    if (tab.isDirty) {
      const { confirmed } = await confirmDialog({
        title: strings.layout.tabs.unsavedTitle,
        message: strings.layout.tabs.unsavedMessage(tab.title),
        confirmLabel: strings.layout.tabs.discardAndClose,
        cancelLabel: strings.layout.tabs.keepTabOpen,
        tone: 'warning',
      });
      if (!confirmed) return;
    }
    closeTab(tabId);
  };

  // Scroll active tab into view safely
  useEffect(() => {
    if (!scrollRef.current) return;
    try {
      const activeEl = scrollRef.current.querySelector('[data-active="true"]');
      if (activeEl?.scrollIntoView) {
        activeEl.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
      }
    } catch {
      // Ignore scroll errors on detached or partially rendered elements
    }
  }, [activeTabId]);

  // Global Ctrl+W / Cmd+W to close active tab
  useEffect(() => {
    const handler = (e) => {
      if ((e.ctrlKey || e.metaKey) && !e.shiftKey && (e.key === 'w' || e.key === 'W')) {
        e.preventDefault();
        if (activeTabId) handleAttemptCloseTab(activeTabId);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [activeTabId, closeTab]);

  // Close context menu on click outside
  useEffect(() => {
    if (!contextMenu) return;
    const handler = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) setContextMenu(null);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [contextMenu]);

  // Pointer-based drag & drop reordering
  useEffect(() => {
    const handleMove = (e) => {
      const drag = dragRef.current;
      if (!drag || e.pointerId !== drag.pointerId) return;

      if (!drag.dragging) {
        const dx = Math.abs(e.clientX - drag.startX);
        const dy = Math.abs(e.clientY - drag.startY);
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

      const bounds = container.getBoundingClientRect();
      if (e.clientX < bounds.left + 28) container.scrollLeft -= 14;
      else if (e.clientX > bounds.right - 28) container.scrollLeft += 14;

      const tabEls = Array.from(container.querySelectorAll('[data-tab-id]'));
      const fromIndex = tabEls.findIndex((el) => el.dataset.tabId === String(drag.id));
      if (fromIndex === -1) return;

      let toIndex = tabEls.length;
      for (let i = 0; i < tabEls.length; i++) {
        const rect = tabEls[i].getBoundingClientRect();
        if (e.clientX < rect.left + rect.width / 2) {
          toIndex = i;
          break;
        }
      }

      if (toIndex !== fromIndex && toIndex !== fromIndex + 1) {
        const target = toIndex > fromIndex ? toIndex - 1 : toIndex;
        moveTab(drag.id, target);
      }
    };

    const handleUp = (e) => {
      const drag = dragRef.current;
      if (!drag || e.pointerId !== drag.pointerId) return;
      dragRef.current = null;
      setDraggingId(null);
      setTimeout(() => {
        suppressClickRef.current = false;
      }, 50);
    };

    window.addEventListener('pointermove', handleMove);
    window.addEventListener('pointerup', handleUp);
    window.addEventListener('pointercancel', handleUp);
    return () => {
      window.removeEventListener('pointermove', handleMove);
      window.removeEventListener('pointerup', handleUp);
      window.removeEventListener('pointercancel', handleUp);
    };
  }, [moveTab]);

  const handlePointerDown = (e, tab) => {
    if (e.button !== 0) return;
    dragRef.current = {
      id: tab.id,
      startX: e.clientX,
      startY: e.clientY,
      pointerId: e.pointerId,
      dragging: false,
    };
  };

  const handleTabClick = (tabId) => {
    if (suppressClickRef.current) return;
    setActiveTab(tabId);
  };


  const handleContextMenu = (e, tabId) => {
    e.preventDefault();
    setContextMenu({
      x: e.clientX,
      y: e.clientY,
      tabId,
    });
  };

  return (
    <div className="h-9.5 bg-surface-secondary border-b border-border flex items-end overflow-hidden shrink-0 select-none relative">
      {tabs.length === 0 ? (
        <div className="flex-1 flex items-center justify-between px-4 pb-1.5">
          <span className="text-xs text-content-muted italic">
            {strings.layout.tabs.noTabs}
          </span>
        </div>
      ) : (
        <div
          ref={scrollRef}
          className="flex items-end gap-1 px-2 overflow-x-auto flex-1 h-full pt-1"
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
                onContextMenu={(e) => handleContextMenu(e, tab.id)}
                style={{ touchAction: 'pan-y' }}
                className={`
                  group flex items-center gap-2 px-3.5 py-1.5 rounded-t-lg
                  text-xs font-medium cursor-pointer select-none
                  max-w-56 min-w-32 shrink-0 relative
                  border border-b-0 transition-colors
                  ${
                    isActive
                      ? 'bg-surface border-border text-content shadow-xs font-semibold'
                      : 'bg-surface-secondary/60 border-transparent text-content-secondary hover:text-content hover:bg-surface/80'
                  }
                  ${
                    isDragging
                      ? 'opacity-50 cursor-grabbing ring-1 ring-brand'
                      : 'active:cursor-grabbing'
                  }
                `}
              >
                {/* Dirty indicator if unsaved changes */}
                {tab.isDirty && (
                  <span className="w-1.5 h-1.5 rounded-full bg-warning shrink-0" title={strings.layout.tabs.unsavedIndicator} />
                )}

                {/* Tab title */}
                <span className="truncate flex-1">{tab.title}</span>

                {/* Close button */}
                {tab.closeable && (
                  <button
                    type="button"
                    onPointerDown={(e) => e.stopPropagation()}
                    onClick={(e) => {
                      e.stopPropagation();
                      handleAttemptCloseTab(tab.id);
                    }}
                    title={strings.layout.tabs.closeTabTitle}
                    className={`
                      p-0.5 rounded-md text-content-muted hover:text-content hover:bg-surface-tertiary transition-colors cursor-pointer
                      ${isActive ? 'opacity-70 hover:opacity-100' : 'opacity-0 group-hover:opacity-100'}
                    `}
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Tabs counter */}
      {tabs.length > 0 && (
        <div className="flex items-center gap-1.5 px-3 pb-2 shrink-0">
          <span className="text-[11px] font-mono text-content-muted">{tabs.length}/10</span>
        </div>
      )}

      {/* Right Click Context Menu */}
      {contextMenu && (
        <div
          ref={menuRef}
          style={{ top: contextMenu.y, left: contextMenu.x }}
          className="fixed z-50 min-w-40 bg-surface border border-border rounded-xl shadow-xl p-1 animate-scale-in text-xs"
        >
          <button
            type="button"
            onClick={() => {
              handleAttemptCloseTab(contextMenu.tabId);
              setContextMenu(null);
            }}
            className="w-full text-left px-3 py-1.5 rounded-lg text-content hover:bg-surface-secondary transition-colors cursor-pointer"
          >
            {strings.layout.tabs.closeTab}
          </button>
          <button
            type="button"
            onClick={() => {
              closeOtherTabs(contextMenu.tabId);
              setContextMenu(null);
            }}
            className="w-full text-left px-3 py-1.5 rounded-lg text-content hover:bg-surface-secondary transition-colors cursor-pointer"
          >
            {strings.layout.tabs.closeOthers}
          </button>
          <button
            type="button"
            onClick={() => {
              closeAllTabs();
              setContextMenu(null);
            }}
            className="w-full text-left px-3 py-1.5 rounded-lg text-content hover:bg-surface-secondary transition-colors cursor-pointer"
          >
            {strings.layout.tabs.closeAll}
          </button>
        </div>
      )}
    </div>
  );
}
