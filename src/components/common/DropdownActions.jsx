import { useState, useRef, useEffect, useLayoutEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { MoreHorizontal } from 'lucide-react';

const VIEWPORT_PADDING = 8;
const MENU_GAP = 6;

/**
 * DropdownActions button adhering to solid surface tokens.
 */
export default function DropdownActions({ items = [], align = 'right' }) {
  const [open, setOpen] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const [coords, setCoords] = useState({ top: 0, left: 0 });
  const wrapperRef = useRef(null);
  const buttonRef = useRef(null);
  const menuRef = useRef(null);

  const actionableIndices = items
    .map((item, idx) => (item !== 'divider' && !item.disabled ? idx : null))
    .filter((idx) => idx !== null);

  useEffect(() => {
    if (open) {
      setHighlightedIndex(actionableIndices.length > 0 ? actionableIndices[0] : -1);
    } else {
      setHighlightedIndex(-1);
    }
  }, [open]);

  const updatePosition = useCallback(() => {
    const btn = buttonRef.current;
    if (!btn) return;

    const rect = btn.getBoundingClientRect();
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
      if (e.key === 'Escape') {
        setOpen(false);
        buttonRef.current?.focus();
        return;
      }

      if (e.key === 'ArrowDown') {
        e.preventDefault();
        const curPos = actionableIndices.indexOf(highlightedIndex);
        const nextPos = curPos < actionableIndices.length - 1 ? curPos + 1 : 0;
        setHighlightedIndex(actionableIndices[nextPos]);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        const curPos = actionableIndices.indexOf(highlightedIndex);
        const prevPos = curPos > 0 ? curPos - 1 : actionableIndices.length - 1;
        setHighlightedIndex(actionableIndices[prevPos]);
      } else if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        if (highlightedIndex >= 0 && items[highlightedIndex] && items[highlightedIndex] !== 'divider') {
          const it = items[highlightedIndex];
          if (!it.disabled) {
            setOpen(false);
            it.onClick?.();
            buttonRef.current?.focus();
          }
        }
      }
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
          className={`p-1.5 rounded-lg transition-colors border cursor-pointer ${
            open
              ? 'bg-surface-secondary text-text-primary border-border-strong'
              : 'hover:bg-surface-secondary text-text-muted hover:text-text-primary border-transparent'
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
            className="z-[9999] min-w-[160px] bg-surface border border-border-default rounded-xl shadow-lg py-1 overflow-hidden animate-scale-in"
            onClick={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
          >
            {items.map((item, i) =>
              item === 'divider' ? (
                <div key={i} className="my-1 border-t border-border-default" />
              ) : (
                <button
                  key={i}
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    setOpen(false);
                    item.onClick?.();
                  }}
                  onMouseEnter={() => setHighlightedIndex(i)}
                  disabled={item.disabled}
                  className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs font-medium transition-colors text-left disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer ${
                    highlightedIndex === i
                      ? item.danger
                        ? 'bg-danger-bg text-danger font-semibold'
                        : 'bg-surface-secondary text-brand font-semibold'
                      : item.danger
                      ? 'text-danger hover:bg-danger-bg'
                      : 'text-text-secondary hover:bg-surface-secondary hover:text-text-primary'
                  }`}
                >
                  {item.icon && (
                    <span
                      className={`shrink-0 w-3.5 h-3.5 flex items-center justify-center ${
                        item.danger ? 'text-danger' : 'text-text-muted'
                      }`}
                    >
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
