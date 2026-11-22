import { useState, useRef, useEffect, useLayoutEffect, useCallback, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { Search, ChevronDown, Check, X } from 'lucide-react';
import { strings } from '../../lib/strings';

const VIEWPORT_PADDING = 8;
const MENU_GAP = 4;
const DEFAULT_MAX_ITEMS = 100;

/**
 * SearchableSelect Combobox Component
 * Provides a responsive search input with a live-filtered dropdown listbox.
 * Designed for high-cardinality collections (100s-1000s of products/items)
 * with instant keyword filtering, keyboard navigation, and status tags.
 */
export default function SearchableSelect({
  value = '',
  onChange,
  options = [],
  placeholder = strings.common.table.searchRecords,
  disabled = false,
  className = '',
  inputClassName = '',
  maxRendered = DEFAULT_MAX_ITEMS,
}) {
  const [open, setOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const [coords, setCoords] = useState({ top: 0, left: 0, width: 0 });

  const containerRef = useRef(null);
  const inputRef = useRef(null);
  const dropdownRef = useRef(null);
  const optionRefs = useRef([]);

  // Normalize options to consistent structure
  const normalizedOptions = useMemo(() => {
    return options.map((opt) => {
      if (typeof opt === 'object' && opt !== null) {
        return {
          value: String(opt.value ?? ''),
          label: String(opt.label ?? opt.value ?? ''),
          sku: opt.sku ? String(opt.sku) : '',
          sublabel: opt.sublabel ? String(opt.sublabel) : '',
          status: opt.status ? String(opt.status) : '',
        };
      }
      return {
        value: String(opt),
        label: String(opt),
        sku: '',
        sublabel: '',
        status: '',
      };
    });
  }, [options]);

  // Selected option
  const selectedOption = useMemo(() => {
    if (!value) return null;
    return normalizedOptions.find((o) => o.value === String(value)) || null;
  }, [normalizedOptions, value]);

  // Filtered options based on search query
  const filteredOptions = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return normalizedOptions;
    return normalizedOptions.filter((opt) => {
      return (
        opt.label.toLowerCase().includes(q) ||
        (opt.sku && opt.sku.toLowerCase().includes(q)) ||
        (opt.sublabel && opt.sublabel.toLowerCase().includes(q)) ||
        opt.value.toLowerCase().includes(q)
      );
    });
  }, [normalizedOptions, searchQuery]);

  const visibleOptions = useMemo(() => {
    return filteredOptions.slice(0, maxRendered);
  }, [filteredOptions, maxRendered]);

  // Position calculation
  const updatePosition = useCallback(() => {
    const el = inputRef.current;
    if (!el) return;

    const rect = el.getBoundingClientRect();
    if (rect.bottom < 0 || rect.top > window.innerHeight) {
      setOpen(false);
      return;
    }

    const menu = dropdownRef.current;
    const menuHeight = menu?.offsetHeight || 260;
    const menuWidth = Math.max(rect.width, 280);

    let top = rect.bottom + MENU_GAP;
    if (top + menuHeight > window.innerHeight - VIEWPORT_PADDING) {
      const above = rect.top - MENU_GAP - menuHeight;
      top = above >= VIEWPORT_PADDING
        ? above
        : Math.max(VIEWPORT_PADDING, window.innerHeight - menuHeight - VIEWPORT_PADDING);
    }

    let left = rect.left;
    left = Math.min(
      Math.max(VIEWPORT_PADDING, left),
      Math.max(VIEWPORT_PADDING, window.innerWidth - menuWidth - VIEWPORT_PADDING)
    );

    setCoords({
      top,
      left,
      width: Math.max(rect.width, 280),
    });
  }, []);

  useLayoutEffect(() => {
    if (!open) return;
    updatePosition();
    const raf = requestAnimationFrame(updatePosition);
    return () => cancelAnimationFrame(raf);
  }, [open, updatePosition, visibleOptions.length]);

  // Outside click and scroll listener
  useEffect(() => {
    if (!open) return;
    const handleClick = (e) => {
      if (containerRef.current && containerRef.current.contains(e.target)) return;
      if (dropdownRef.current && dropdownRef.current.contains(e.target)) return;
      setOpen(false);
      setSearchQuery('');
    };

    document.addEventListener('mousedown', handleClick);
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);

    return () => {
      document.removeEventListener('mousedown', handleClick);
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
    };
  }, [open, updatePosition]);

  // Scroll highlighted item into view
  useEffect(() => {
    if (open && highlightedIndex >= 0 && optionRefs.current[highlightedIndex]) {
      optionRefs.current[highlightedIndex]?.scrollIntoView({
        block: 'nearest',
        behavior: 'smooth',
      });
    }
  }, [highlightedIndex, open]);

  // Reset highlight index when filtered list changes
  useEffect(() => {
    if (open) {
      setHighlightedIndex(visibleOptions.length > 0 ? 0 : -1);
    } else {
      setHighlightedIndex(-1);
    }
  }, [open, searchQuery, visibleOptions.length]);

  const handleSelectOption = (opt) => {
    onChange?.(opt.value);
    setSearchQuery('');
    setOpen(false);
    inputRef.current?.blur();
  };

  const handleClear = (e) => {
    e.stopPropagation();
    onChange?.('');
    setSearchQuery('');
    setOpen(false);
    inputRef.current?.focus();
  };

  const handleKeyDown = (e) => {
    if (disabled) return;

    switch (e.key) {
      case 'ArrowDown': {
        e.preventDefault();
        if (!open) {
          setOpen(true);
          return;
        }
        if (visibleOptions.length === 0) return;
        setHighlightedIndex((prev) => (prev < visibleOptions.length - 1 ? prev + 1 : 0));
        break;
      }
      case 'ArrowUp': {
        e.preventDefault();
        if (!open) {
          setOpen(true);
          return;
        }
        if (visibleOptions.length === 0) return;
        setHighlightedIndex((prev) => (prev > 0 ? prev - 1 : visibleOptions.length - 1));
        break;
      }
      case 'Enter': {
        e.preventDefault();
        if (open && highlightedIndex >= 0 && highlightedIndex < visibleOptions.length) {
          handleSelectOption(visibleOptions[highlightedIndex]);
        }
        break;
      }
      case 'Escape': {
        e.preventDefault();
        setOpen(false);
        setSearchQuery('');
        break;
      }
      case 'Tab': {
        setOpen(false);
        setSearchQuery('');
        break;
      }
      default:
        break;
    }
  };

  // Determine display text for the input
  const inputValue = open ? searchQuery : (selectedOption ? selectedOption.label : '');

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      {/* Search Input Container */}
      <div className="relative flex items-center">
        <Search className="w-3.5 h-3.5 text-content-muted absolute left-3 pointer-events-none shrink-0" />
        <input
          ref={inputRef}
          type="text"
          role="combobox"
          aria-expanded={open}
          aria-haspopup="listbox"
          aria-autocomplete="list"
          disabled={disabled}
          placeholder={selectedOption ? selectedOption.label : placeholder}
          value={inputValue}
          onChange={(e) => {
            setSearchQuery(e.target.value);
            if (!open) setOpen(true);
          }}
          onFocus={() => {
            setOpen(true);
            setSearchQuery('');
          }}
          onClick={() => {
            if (!open) setOpen(true);
          }}
          onKeyDown={handleKeyDown}
          className={`w-full bg-surface border border-border rounded-lg text-content text-xs pl-8 pr-14 py-2 hover:border-border-strong focus:outline-none focus:ring-1 focus:ring-brand focus:border-brand transition-colors disabled:opacity-50 disabled:cursor-not-allowed placeholder:text-content-muted ${
            selectedOption && !open ? 'font-medium text-content' : ''
          } ${inputClassName}`}
        />

        {/* Action icons on right */}
        <div className="absolute right-2.5 flex items-center gap-1">
          {Boolean(value || searchQuery) && !disabled && (
            <button
              type="button"
              onClick={handleClear}
              className="p-1 rounded text-content-muted hover:text-content hover:bg-surface-secondary cursor-pointer transition-colors"
              title={strings.common.actions.clear}
              tabIndex={-1}
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              if (open) {
                setOpen(false);
                setSearchQuery('');
              } else {
                setOpen(true);
                inputRef.current?.focus();
              }
            }}
            disabled={disabled}
            className="p-1 rounded text-content-muted hover:text-content cursor-pointer transition-colors"
            tabIndex={-1}
          >
            <ChevronDown
              className={`w-3.5 h-3.5 text-content-muted transition-transform duration-200 ${
                open ? 'rotate-180' : ''
              }`}
            />
          </button>
        </div>
      </div>

      {/* Floating Results Listbox */}
      {open &&
        createPortal(
          <div
            ref={dropdownRef}
            role="listbox"
            tabIndex={-1}
            style={{
              position: 'fixed',
              top: `${coords.top}px`,
              left: `${coords.left}px`,
              minWidth: `${coords.width}px`,
              maxWidth: '90vw',
            }}
            className="z-[9999] bg-surface border border-border rounded-xl shadow-xl py-1 max-h-64 overflow-y-auto animate-scale-in"
            onClick={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
          >
            {visibleOptions.length === 0 ? (
              <div className="px-4 py-3 text-center text-xs text-content-muted">
                {searchQuery ? `No matches for "${searchQuery}"` : 'No options available'}
              </div>
            ) : (
              <>
                {visibleOptions.map((opt, idx) => {
                  const isSelected = opt.value === String(value);
                  const isHighlighted = highlightedIndex === idx;

                  return (
                    <button
                      key={opt.value}
                      ref={(el) => (optionRefs.current[idx] = el)}
                      type="button"
                      role="option"
                      aria-selected={isSelected}
                      onClick={() => handleSelectOption(opt)}
                      onMouseEnter={() => setHighlightedIndex(idx)}
                      className={`w-full flex items-center justify-between gap-3 px-3 py-2 text-xs text-left transition-colors cursor-pointer ${
                        isSelected
                          ? isHighlighted
                            ? 'bg-brand/15 text-brand font-semibold'
                            : 'bg-brand-subtle text-brand font-semibold'
                          : isHighlighted
                          ? 'bg-surface-secondary text-content font-medium'
                          : 'text-content hover:bg-surface-secondary'
                      }`}
                    >
                      <div className="flex items-center gap-2 min-w-0">
                        <span className="truncate">{opt.label}</span>
                        {opt.sku && (
                          <span className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-surface-secondary text-content-secondary border border-border shrink-0">
                            SKU: {opt.sku}
                          </span>
                        )}
                        {opt.status && (
                          <span className="text-[10px] px-1.5 py-0.5 rounded border border-danger/30 text-danger/80 shrink-0">
                            [{opt.status}]
                          </span>
                        )}
                      </div>
                      {isSelected && <Check className="w-3.5 h-3.5 text-brand shrink-0 ml-2" />}
                    </button>
                  );
                })}

                {filteredOptions.length > maxRendered && (
                  <div className="px-3 py-1.5 text-[11px] text-content-muted text-center border-t border-border bg-surface-secondary/50">
                    Showing top {maxRendered} of {filteredOptions.length} products (type to filter)
                  </div>
                )}
              </>
            )}
          </div>,
          document.body
        )}
    </div>
  );
}
