import { useState, useRef, useEffect, useCallback } from 'react';
import { ChevronDown, Check } from 'lucide-react';

/**
 * Standard Modern Select Component
 * Replaces dull gray native <select> elements across the app with consistent,
 * surface-styled dropdowns featuring checkmarks, high-contrast typography,
 * and full keyboard arrow-key navigation.
 */
export default function Select({
  value,
  onChange,
  options = [],
  placeholder = 'Select option…',
  disabled = false,
  className = '',
  buttonClassName = '',
  align = 'left',
}) {
  const [open, setOpen] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const ref = useRef(null);
  const triggerRef = useRef(null);
  const listRef = useRef(null);
  const optionRefs = useRef([]);

  // Normalize options to { value, label }
  const normalizedOptions = options.map((opt) =>
    typeof opt === 'object' && opt !== null
      ? { value: String(opt.value ?? ''), label: String(opt.label ?? opt.value ?? '') }
      : { value: String(opt), label: String(opt) }
  );

  const selectedIndex = normalizedOptions.findIndex(
    (o) => String(o.value) === String(value ?? '')
  );

  const selectedOption = selectedIndex >= 0 ? normalizedOptions[selectedIndex] : null;
  const displayLabel = selectedOption ? selectedOption.label : placeholder;

  // Sync highlightedIndex when opening
  useEffect(() => {
    if (open) {
      setHighlightedIndex(selectedIndex >= 0 ? selectedIndex : 0);
    } else {
      setHighlightedIndex(-1);
    }
  }, [open, selectedIndex]);

  // Scroll highlighted item into view
  useEffect(() => {
    if (open && highlightedIndex >= 0 && optionRefs.current[highlightedIndex]) {
      optionRefs.current[highlightedIndex]?.scrollIntoView({
        block: 'nearest',
        behavior: 'smooth',
      });
    }
  }, [highlightedIndex, open]);

  // Outside click listener
  useEffect(() => {
    if (!open) return;
    const handleClick = (e) => {
      if (ref.current && !ref.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClick);
    return () => {
      document.removeEventListener('mousedown', handleClick);
    };
  }, [open]);

  // Keyboard navigation handler
  const handleKeyDown = (e) => {
    if (disabled) return;

    if (!open) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp' || e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        setOpen(true);
      }
      return;
    }

    // Dropdown is open
    switch (e.key) {
      case 'ArrowDown': {
        e.preventDefault();
        setHighlightedIndex((prev) =>
          prev < normalizedOptions.length - 1 ? prev + 1 : 0
        );
        break;
      }
      case 'ArrowUp': {
        e.preventDefault();
        setHighlightedIndex((prev) =>
          prev > 0 ? prev - 1 : normalizedOptions.length - 1
        );
        break;
      }
      case 'Home': {
        e.preventDefault();
        setHighlightedIndex(0);
        break;
      }
      case 'End': {
        e.preventDefault();
        setHighlightedIndex(normalizedOptions.length - 1);
        break;
      }
      case 'Enter':
      case ' ': {
        e.preventDefault();
        if (highlightedIndex >= 0 && highlightedIndex < normalizedOptions.length) {
          onChange(normalizedOptions[highlightedIndex].value);
          setOpen(false);
          triggerRef.current?.focus();
        }
        break;
      }
      case 'Escape':
      case 'Tab': {
        setOpen(false);
        triggerRef.current?.focus();
        break;
      }
      default:
        break;
    }
  };

  return (
    <div
      ref={ref}
      onKeyDown={handleKeyDown}
      className={`relative ${className}`}
    >
      <button
        ref={triggerRef}
        type="button"
        disabled={disabled}
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="listbox"
        aria-expanded={open}
        className={`w-full flex items-center justify-between gap-2 px-3 py-2 text-xs bg-surface border border-border-default rounded-lg text-text-primary hover:border-border-strong focus:outline-none focus:ring-1 focus:ring-brand focus:border-brand transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed ${buttonClassName}`}
      >
        <span
          className={`truncate ${
            !selectedOption && placeholder ? 'text-text-muted' : 'text-text-primary font-medium'
          }`}
        >
          {displayLabel}
        </span>
        <ChevronDown
          className={`w-3.5 h-3.5 text-text-muted shrink-0 transition-transform ${
            open ? 'rotate-180' : ''
          }`}
        />
      </button>

      {open && (
        <div
          ref={listRef}
          role="listbox"
          tabIndex={-1}
          className={`absolute top-full mt-1.5 z-50 bg-surface border border-border-default rounded-xl shadow-xl py-1 max-h-60 overflow-y-auto animate-scale-in min-w-full ${
            align === 'right' ? 'right-0' : 'left-0'
          }`}
        >
          {normalizedOptions.map((opt, idx) => {
            const isSelected = String(opt.value) === String(value ?? '');
            const isHighlighted = highlightedIndex === idx;

            return (
              <button
                key={opt.value}
                ref={(el) => (optionRefs.current[idx] = el)}
                type="button"
                role="option"
                aria-selected={isSelected}
                onClick={() => {
                  onChange(opt.value);
                  setOpen(false);
                  triggerRef.current?.focus();
                }}
                onMouseEnter={() => setHighlightedIndex(idx)}
                className={`w-full flex items-center justify-between gap-3 px-3 py-2 text-xs text-left transition-colors cursor-pointer ${
                  isSelected
                    ? isHighlighted
                      ? 'bg-brand/15 text-brand font-semibold'
                      : 'bg-brand-subtle text-brand font-semibold'
                    : isHighlighted
                    ? 'bg-surface-secondary text-text-primary font-medium'
                    : 'text-text-primary hover:bg-surface-secondary'
                }`}
              >
                <span className="truncate">{opt.label}</span>
                {isSelected && <Check className="w-3.5 h-3.5 text-brand shrink-0" />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
