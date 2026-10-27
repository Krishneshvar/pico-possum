import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { AlertTriangle, CheckCircle2, Trash2 } from 'lucide-react';
import { useConfirmStore } from '../../stores/confirm.store';

const TONE_STYLES = {
  danger: {
    icon: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
    button: 'bg-rose-500 text-white hover:bg-rose-400 shadow-rose-500/20',
  },
  warning: {
    icon: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    button: 'bg-amber-500 text-slate-950 hover:bg-amber-400 shadow-amber-500/20',
  },
  primary: {
    icon: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    button: 'bg-emerald-500 text-slate-950 hover:bg-emerald-400 shadow-emerald-500/20',
  },
};

const TONE_ICONS = {
  danger: Trash2,
  warning: AlertTriangle,
  primary: CheckCircle2,
};

/**
 * The app-wide confirmation dialog.
 *
 * Render it through <ConfirmDialogHost /> (imperative `confirmDialog()` calls)
 * or use it directly when a view already owns the open/closed state.
 */
export default function ConfirmDialog({
  title = 'Are you sure?',
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

  // Put focus where it is most useful: the input when present, otherwise the
  // safe option for destructive dialogs and the primary action for the rest.
  useEffect(() => {
    if (inputRef.current) {
      inputRef.current.focus();
      inputRef.current.select?.();
    } else if (tone === 'danger') {
      cancelRef.current?.focus();
    } else {
      confirmRef.current?.focus();
    }
    // Only run when the dialog mounts.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (cannotConfirm) return;
    onConfirm?.(input ? inputValue.trim() : undefined);
  };

  const dialog = (
    <div
      className="fixed inset-0 z-[10000] flex items-center justify-center bg-slate-950/80 p-4 backdrop-blur-sm animate-fade-in"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) handleCancel();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="w-full max-w-sm space-y-4 rounded-2xl border border-slate-800 bg-slate-900 p-5 shadow-2xl animate-scale-in"
      >
        <div className="flex items-start gap-3">
          <div
            className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border ${styles.icon}`}
          >
            <Icon className="h-4 w-4" />
          </div>
          <div className="min-w-0 flex-1 pt-0.5">
            <h3 id={titleId} className="text-sm font-bold text-slate-100">
              {title}
            </h3>
            {message && (
              <div className="mt-1 text-xs leading-relaxed text-slate-400">
                {message}
              </div>
            )}
            {detailItems.length > 0 && (
              <ul className="mt-2.5 space-y-1.5 text-[11px] leading-relaxed text-slate-400">
                {detailItems.map((detail, index) => (
                  <li key={index} className="flex gap-2">
                    <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-slate-600" />
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
                  className="mb-1 block text-xs text-slate-400"
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
                className="w-full rounded-lg border border-slate-800 bg-slate-950 p-2 text-xs text-slate-200 focus:border-rose-500 focus:outline-none"
              />
            </div>
          )}

          <div className="flex justify-end gap-2 border-t border-slate-800 pt-3">
            <button
              ref={cancelRef}
              type="button"
              onClick={handleCancel}
              disabled={isPending}
              className="rounded-lg px-3 py-1.5 text-xs font-semibold text-slate-400 transition-colors hover:bg-slate-800 hover:text-slate-200 disabled:opacity-60"
            >
              {cancelLabel}
            </button>
            <button
              ref={confirmRef}
              type="submit"
              disabled={cannotConfirm}
              className={`rounded-lg px-4 py-1.5 text-xs font-bold shadow-md transition-all disabled:cursor-not-allowed disabled:opacity-60 ${styles.button}`}
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

/** Renders the active imperative `confirmDialog()` request at the app root. */
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
