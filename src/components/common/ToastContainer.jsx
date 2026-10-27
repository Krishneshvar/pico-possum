import { createPortal } from 'react-dom';
import {
  CheckCircle2,
  AlertTriangle,
  Info,
  XCircle,
  X,
} from 'lucide-react';
import { useToastStore } from '../../stores/toast.store';

const TOAST_STYLES = {
  success: {
    icon: CheckCircle2,
    panel: 'border-emerald-500/40 bg-emerald-500/10',
    iconColor: 'text-emerald-400',
    titleColor: 'text-emerald-200',
    bar: 'bg-emerald-400',
  },
  error: {
    icon: XCircle,
    panel: 'border-rose-500/40 bg-rose-500/10',
    iconColor: 'text-rose-400',
    titleColor: 'text-rose-200',
    bar: 'bg-rose-400',
  },
  warning: {
    icon: AlertTriangle,
    panel: 'border-amber-500/40 bg-amber-500/10',
    iconColor: 'text-amber-400',
    titleColor: 'text-amber-200',
    bar: 'bg-amber-400',
  },
  info: {
    icon: Info,
    panel: 'border-sky-500/40 bg-sky-500/10',
    iconColor: 'text-sky-400',
    titleColor: 'text-sky-200',
    bar: 'bg-sky-400',
  },
};

const TYPE_LABELS = {
  success: 'Success',
  error: 'Error',
  warning: 'Warning',
  info: 'Info',
};

export function Toast({ toast: item, onDismiss }) {
  const style = TOAST_STYLES[item.type] || TOAST_STYLES.info;
  const Icon = style.icon;
  const isUrgent = item.type === 'error' || item.type === 'warning';

  return (
    <div
      role={isUrgent ? 'alert' : 'status'}
      aria-live={isUrgent ? 'assertive' : 'polite'}
      className={`pointer-events-auto relative w-full overflow-hidden rounded-xl border ${style.panel} bg-slate-900/95 shadow-2xl shadow-black/50 backdrop-blur-xl animate-toast-in`}
    >
      <div className="flex items-start gap-3 p-3.5 pr-9">
        <Icon className={`w-5 h-5 mt-0.5 shrink-0 ${style.iconColor}`} />
        <div className="min-w-0 flex-1">
          <p className={`text-xs font-bold ${style.titleColor}`}>
            {item.title || TYPE_LABELS[item.type] || 'Notice'}
          </p>
          {item.message && (
            <p className="mt-0.5 text-xs leading-relaxed text-slate-300 break-words">
              {item.message}
            </p>
          )}
        </div>
      </div>

      <button
        type="button"
        onClick={() => onDismiss(item.id)}
        aria-label="Dismiss notification"
        className="absolute right-2 top-2 p-1 rounded-md text-slate-500 hover:text-slate-200 hover:bg-slate-800/80 transition-colors cursor-pointer"
      >
        <X className="w-3.5 h-3.5" />
      </button>

      {item.duration > 0 && (
        <span
          className={`toast-progress absolute bottom-0 left-0 h-0.5 w-full ${style.bar}`}
          style={{ animationDuration: `${item.duration}ms` }}
        />
      )}
    </div>
  );
}

export default function ToastContainer() {
  const toasts = useToastStore((state) => state.toasts);
  const dismiss = useToastStore((state) => state.dismiss);

  if (typeof document === 'undefined' || toasts.length === 0) return null;

  return createPortal(
    <div
      className="fixed top-3 right-3 z-[9999] flex w-[360px] max-w-[calc(100vw-1.5rem)] flex-col gap-2 pointer-events-none select-none"
      aria-label="Notifications"
    >
      {toasts.map((item) => (
        <Toast key={item.id} toast={item} onDismiss={dismiss} />
      ))}
    </div>,
    document.body,
  );
}
