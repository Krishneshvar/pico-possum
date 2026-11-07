import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { AlertTriangle, CheckCircle2, Trash2, Info } from 'lucide-react';
import { useConfirmStore } from '../../stores/confirm.store';

const TONE_STYLES = {
  danger: {
    iconWrapper: 'bg-danger-bg text-danger border-danger/30',
    button: 'bg-danger text-white hover:bg-danger/90',
  },
  warning: {
    iconWrapper: 'bg-warning-bg text-warning border-warning/30',
    button: 'bg-warning text-white hover:bg-warning/90',
  },
  primary: {
    iconWrapper: 'bg-brand-subtle text-brand border-brand/30',
    button: 'bg-brand text-white hover:bg-brand-hover',
  },
};

const TONE_ICONS = {
  danger: Trash2,
  warning: AlertTriangle,
  primary: CheckCircle2,
};

/**
 * App-wide confirmation dialog adhering to WCAG and design standards:
 * - Safe button focus by default on destructive actions
 * - Explicit action labels
 * - Escape key dismisses
 */
export default function ConfirmDialog({
  title = 'Confirm Action',
  message,
  details = [],
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  tone = 'danger',
  icon,
  input = null,
  isPending = false,
  onConfirm,
  onCancel,
}) {
  const titleId = useId();
  const inputId = useId();
  const inputRef = useRef(null);
  const cancelRef = useRef(null);
  const confirmRef = useRef(null);
  const [inputValue, setInputValue] = useState(input?.defaultValue ?? '');

  const styles = TONE_STYLES[tone] || TONE_STYLES.danger;
  const Icon = icon || TONE_ICONS[tone] || AlertTriangle;
  const detailItems = Array.isArray(details) ? details : [];
  const requiresValue = Boolean(input) && input.required !== false;
  const cannotConfirm = isPending || (requiresValue && !inputValue.trim());

  const handleCancel = useCallback(() => {
    if (isPending) return;
    onCancel?.();
  }, [isPending, onCancel]);

  // Close on Escape.
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') handleCancel();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [handleCancel]);

  // Focus management: For destructive dialogs, default focus is on Cancel!
  useEffect(() => {
    if (inputRef.current) {
      inputRef.current.focus();
      inputRef.current.select?.();
    } else if (tone === 'danger') {
      cancelRef.current?.focus();
    } else {
      confirmRef.current?.focus();
    }
  }, [tone]);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (cannotConfirm) return;
    onConfirm?.(input ? inputValue.trim() : undefined);
  };

  const dialog = (
    <div
      className="fixed inset-0 z-[10000] flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs animate-fade-in"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) handleCancel();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="w-full max-w-md space-y-4 rounded-2xl border border-border-default bg-surface p-5 shadow-xl animate-scale-in"
      >
        <div className="flex items-start gap-3">
          <div
            className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border ${styles.iconWrapper}`}
          >
            <Icon className="h-4 w-4" />
          </div>
          <div className="min-w-0 flex-1 pt-0.5">
            <h3 id={titleId} className="text-sm font-bold text-text-primary">
              {title}
            </h3>
            {message && (
              <div className="mt-1 text-xs leading-relaxed text-text-secondary">
                {message}
              </div>
            )}
            {detailItems.length > 0 && (
              <ul className="mt-2.5 space-y-1.5 text-[11px] leading-relaxed text-text-muted">
                {detailItems.map((detail, index) => (
                  <li key={index} className="flex gap-2">
                    <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-border-strong" />
                    <span>{detail}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {input && (
            <div>
              {input.label && (
                <label
                  htmlFor={inputId}
                  className="mb-1 block text-xs font-medium text-text-secondary"
                >
                  {input.label}
                </label>
              )}
              <input
                ref={inputRef}
                id={inputId}
                type={input.type || 'text'}
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                placeholder={input.placeholder || ''}
                className="w-full rounded-lg border border-border-strong bg-surface-secondary p-2 text-xs text-text-primary focus:border-brand focus:outline-none"
              />
            </div>
          )}

          <div className="flex justify-end gap-2 border-t border-border-default pt-3">
            <button
              ref={cancelRef}
              type="button"
              onClick={handleCancel}
              disabled={isPending}
              className="rounded-lg px-3.5 py-1.5 text-xs font-semibold text-text-secondary transition-colors hover:bg-surface-secondary hover:text-text-primary disabled:opacity-50 cursor-pointer border border-border-default"
            >
              {cancelLabel}
            </button>
            <button
              ref={confirmRef}
              type="submit"
              disabled={cannotConfirm}
              className={`rounded-lg px-4 py-1.5 text-xs font-bold shadow-xs transition-colors disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer ${styles.button}`}
            >
              {isPending ? 'Please wait…' : confirmLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  );

  if (typeof document === 'undefined') return null;
  return createPortal(dialog, document.body);
}

/** Renders active imperative confirm dialog */
export function ConfirmDialogHost() {
  const request = useConfirmStore((state) => state.request);
  const close = useConfirmStore((state) => state.close);

  if (!request) return null;

  return (
    <ConfirmDialog
      key={request.id}
      {...request}
      onCancel={() => close({ confirmed: false, value: null })}
      onConfirm={(value) => close({ confirmed: true, value: value ?? null })}
    />
  );
}
