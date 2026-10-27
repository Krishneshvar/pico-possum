import { useState, useRef, useEffect, useLayoutEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { MoreHorizontal } from 'lucide-react';

const VIEWPORT_PADDING = 8;
const MENU_GAP = 6;

/**
 * A single "⋯" button that reveals a dropdown menu of actions.
 *
 * The menu is rendered in a portal with fixed positioning so it is never
 * clipped or painted underneath adjacent table rows / sticky action cells.
 *
 * Usage:
 *   <DropdownActions items={[
 *     { label: 'Edit', icon: <Edit2 />, onClick: () => ... },
 *     { label: 'Delete', icon: <Trash2 />, onClick: () => ..., danger: true },
 *   ]} />
 */
export default function DropdownActions({ items = [], align = 'right' }) {
  const [open, setOpen] = useState(false);
  const [coords, setCoords] = useState({ top: 0, left: 0 });
  const wrapperRef = useRef(null);
  const buttonRef = useRef(null);
  const menuRef = useRef(null);

  // Position the portaled menu next to the trigger, flipping above and
  // clamping horizontally when there is not enough viewport space.
  const updatePosition = useCallback(() => {
    const btn = buttonRef.current;
    if (!btn) return;

    const rect = btn.getBoundingClientRect();

    // Close the menu if its trigger has been scrolled out of view.
    if (rect.bottom < 0 || rect.top > window.innerHeight) {
      setOpen(false);
      return;
    }

    const menu = menuRef.current;
    const menuWidth = menu?.offsetWidth ?? 168;
    const menuHeight = menu?.offsetHeight ?? 0;

    let top = rect.bottom + MENU_GAP;
    if (menuHeight > 0 && top + menuHeight > window.innerHeight - VIEWPORT_PADDING) {
      const above = rect.top - MENU_GAP - menuHeight;
      top =
        above >= VIEWPORT_PADDING
          ? above
          : Math.max(VIEWPORT_PADDING, window.innerHeight - menuHeight - VIEWPORT_PADDING);
    }

    let left = align === 'right' ? rect.right - menuWidth : rect.left;
    left = Math.min(
      Math.max(VIEWPORT_PADDING, left),
      Math.max(VIEWPORT_PADDING, window.innerWidth - menuWidth - VIEWPORT_PADDING)
    );

    setCoords({ top, left });
  }, [align]);

  // Measure the menu and place it before the first paint.
  useLayoutEffect(() => {
    if (!open) return;
    updatePosition();
    const raf = requestAnimationFrame(updatePosition);
    return () => cancelAnimationFrame(raf);
  }, [open, updatePosition, items.length]);

  useEffect(() => {
    if (!open) return;

    const handlePointerDown = (e) => {
      if (wrapperRef.current?.contains(e.target)) return;
      if (menuRef.current?.contains(e.target)) return;
      setOpen(false);
    };
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') setOpen(false);
    };

    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);

    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
    };
  }, [open, updatePosition]);

  return (
    <>
      <div
        ref={wrapperRef}
        className="relative inline-block"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          ref={buttonRef}
          type="button"
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
      </div>

      {open &&
        createPortal(
          <div
            ref={menuRef}
            role="menu"
            style={{ position: 'fixed', top: `${coords.top}px`, left: `${coords.left}px` }}
            className="z-[9999] min-w-[160px] bg-slate-900 border border-slate-700 rounded-xl shadow-2xl shadow-slate-950/60 py-1 overflow-hidden"
            onClick={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
          >
            {items.map((item, i) =>
              item === 'divider' ? (
                <div key={i} className="my-1 border-t border-slate-800" />
              ) : (
                <button
                  key={i}
                  type="button"
                  role="menuitem"
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
          </div>,
          document.body
        )}
    </>
  );
}
