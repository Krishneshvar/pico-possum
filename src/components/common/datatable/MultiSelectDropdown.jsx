import { useState, useRef, useEffect, useLayoutEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { ChevronDown, Check, X } from 'lucide-react';
import { strings } from '../../../lib/strings';

const VIEWPORT_PADDING = 8;
const MENU_GAP = 4;

export default function MultiSelectDropdown({ label, options = [], selected = [], onChange }) {
  const [open, setOpen] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const [coords, setCoords] = useState({ top: 0, left: 0, minWidth: 160 });
  const ref = useRef(null);
  const triggerRef = useRef(null);
  const menuRef = useRef(null);
  const optionRefs = useRef([]);

  const updatePosition = useCallback(() => {
    const btn = triggerRef.current;
    if (!btn) return;

    const rect = btn.getBoundingClientRect();
    if (rect.bottom < 0 || rect.top > window.innerHeight) {
      setOpen(false);
      return;
    }

    const menu = menuRef.current;
    const menuHeight = menu?.offsetHeight || 220;
    const menuWidth = Math.max(rect.width, 160);

    let top = rect.bottom + MENU_GAP;
    if (top + menuHeight > window.innerHeight - VIEWPORT_PADDING) {
      const above = rect.top - MENU_GAP - menuHeight;
      top =
        above >= VIEWPORT_PADDING
          ? above
          : Math.max(VIEWPORT_PADDING, window.innerHeight - menuHeight - VIEWPORT_PADDING);
    }

    let left = rect.left;
    if (left + menuWidth > window.innerWidth - VIEWPORT_PADDING) {
      left = Math.max(VIEWPORT_PADDING, window.innerWidth - menuWidth - VIEWPORT_PADDING);
    }

    setCoords({
      top,
      left,
      minWidth: Math.max(rect.width, 160),
    });
  }, []);

  useLayoutEffect(() => {
    if (!open) return;
    updatePosition();
    const raf = requestAnimationFrame(updatePosition);
    return () => cancelAnimationFrame(raf);
  }, [open, updatePosition, options.length]);

  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (ref.current?.contains(e.target)) return;
      if (menuRef.current?.contains(e.target)) return;
      setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);
    return () => {
      document.removeEventListener('mousedown', handler);
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
    };
  }, [open, updatePosition]);

  useEffect(() => {
    if (open) {
      setHighlightedIndex(0);
    } else {
      setHighlightedIndex(-1);
    }
  }, [open]);

  useEffect(() => {
    if (open && highlightedIndex >= 0 && optionRefs.current[highlightedIndex]) {
      optionRefs.current[highlightedIndex]?.scrollIntoView({
        block: 'nearest',
        behavior: 'smooth',
      });
    }
  }, [highlightedIndex, open]);

  const toggle = (value) => {
    const next = selected.includes(value)
      ? selected.filter((v) => v !== value)
      : [...selected, value];
    onChange(next);
  };

  const activeCount = selected.length;
  const totalItems = activeCount > 0 ? options.length + 1 : options.length;

  const handleKeyDown = (e) => {
    if (!open) {
      if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        setOpen(true);
      }
      return;
    }

    switch (e.key) {
      case 'ArrowDown': {
        e.preventDefault();
        setHighlightedIndex((prev) => (prev + 1) % totalItems);
        break;
      }
      case 'ArrowUp': {
        e.preventDefault();
        setHighlightedIndex((prev) => (prev - 1 + totalItems) % totalItems);
        break;
      }
      case 'Home': {
        e.preventDefault();
        setHighlightedIndex(0);
        break;
      }
      case 'End': {
        e.preventDefault();
        setHighlightedIndex(totalItems - 1);
        break;
      }
      case 'Enter':
      case ' ': {
        e.preventDefault();
        if (highlightedIndex === 0 && activeCount > 0) {
          onChange([]);
        } else {
          const optIdx = activeCount > 0 ? highlightedIndex - 1 : highlightedIndex;
          if (options[optIdx]) toggle(options[optIdx].value);
        }
        break;
      }
      case 'Escape': {
        e.preventDefault();
        setOpen(false);
        triggerRef.current?.focus();
        break;
      }
      case 'Tab': {
        setOpen(false);
        break;
      }
      default:
        break;
    }
  };

  return (
    <div className="relative" ref={ref} onKeyDown={handleKeyDown}>
      <button
        ref={triggerRef}
        type="button"
        onClick={() => setOpen((p) => !p)}
        aria-haspopup="listbox"
        aria-expanded={open}
        className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-medium transition-colors cursor-pointer focus:outline-none focus:border-brand ${
          activeCount > 0
            ? 'bg-brand-subtle border-brand text-brand'
            : 'bg-surface border-border-default text-text-secondary hover:text-text-primary hover:border-border-strong'
        }`}
      >
        <span>{label}</span>
        {activeCount > 0 && (
          <span className="w-4 h-4 rounded-full bg-brand text-white text-[10px] font-bold flex items-center justify-center leading-none">
            {activeCount}
          </span>
        )}
        <ChevronDown className={`w-3.5 h-3.5 transition-transform duration-150 ${open ? 'rotate-180' : ''}`} />
      </button>

      {open &&
        createPortal(
          <div
            ref={menuRef}
            role="listbox"
            tabIndex={-1}
            style={{
              position: 'fixed',
              top: `${coords.top}px`,
              left: `${coords.left}px`,
              minWidth: `${coords.minWidth}px`,
            }}
            className="z-[9999] max-h-60 overflow-y-auto bg-surface border border-border-default rounded-xl shadow-xl py-1 text-xs focus:outline-none animate-scale-in"
            onClick={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
          >
            {activeCount > 0 && (
              <button
                ref={(el) => {
                  optionRefs.current[0] = el;
                }}
                type="button"
                role="option"
                aria-selected={false}
                onClick={() => onChange([])}
                onMouseEnter={() => setHighlightedIndex(0)}
                className={`w-full flex items-center gap-2 px-3 py-1.5 text-text-muted transition-colors border-b border-border-default cursor-pointer ${
                  highlightedIndex === 0
                    ? 'bg-danger-bg text-danger'
                    : 'hover:text-danger hover:bg-danger-bg'
                }`}
              >
                <X className="w-3 h-3" />
                <span>{strings.common.table.clearAll}</span>
              </button>
            )}

            {options.map((opt, idx) => {
              const isChecked = selected.includes(opt.value);
              const itemIdx = activeCount > 0 ? idx + 1 : idx;
              const isHighlighted = highlightedIndex === itemIdx;

              return (
                <button
                  key={opt.value}
                  ref={(el) => {
                    optionRefs.current[itemIdx] = el;
                  }}
                  type="button"
                  role="option"
                  aria-selected={isChecked}
                  onClick={() => toggle(opt.value)}
                  onMouseEnter={() => setHighlightedIndex(itemIdx)}
                  className={`w-full flex items-center gap-2 px-3 py-1.5 text-left transition-colors cursor-pointer ${
                    isHighlighted
                      ? isChecked
                        ? 'bg-brand text-white'
                        : 'bg-surface-secondary text-text-primary'
                      : isChecked
                      ? 'bg-brand-subtle text-brand font-medium'
                      : 'text-text-primary hover:bg-surface-secondary'
                  }`}
                >
                  <div
                    className={`w-3.5 h-3.5 rounded border flex items-center justify-center shrink-0 ${
                      isChecked
                        ? isHighlighted
                          ? 'bg-white border-white text-brand'
                          : 'bg-brand border-brand text-white'
                        : 'border-border-strong bg-surface'
                    }`}
                  >
                    {isChecked && <Check className="w-2.5 h-2.5 stroke-[3]" />}
                  </div>
                  <span className="truncate">{opt.label}</span>
                </button>
              );
            })}
          </div>,
          document.body
        )}
    </div>
  );
}
