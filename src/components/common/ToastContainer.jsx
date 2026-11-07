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
    panel: 'bg-[#15803D] text-white border border-[#166534]',
    iconColor: 'text-white',
    titleColor: 'text-white',
    messageColor: 'text-emerald-100',
    closeColor: 'text-white/80 hover:text-white hover:bg-white/10',
    bar: 'bg-emerald-300',
  },
  error: {
    icon: XCircle,
    panel: 'bg-[#DC2626] text-white border border-[#B91C1C]',
    iconColor: 'text-white',
    titleColor: 'text-white',
    messageColor: 'text-red-100',
    closeColor: 'text-white/80 hover:text-white hover:bg-white/10',
    bar: 'bg-red-300',
  },
  warning: {
    icon: AlertTriangle,
    panel: 'bg-[#D97706] text-white border border-[#B45309]',
    iconColor: 'text-white',
    titleColor: 'text-white',
    messageColor: 'text-amber-100',
    closeColor: 'text-white/80 hover:text-white hover:bg-white/10',
    bar: 'bg-amber-300',
  },
  info: {
    icon: Info,
    panel: 'bg-[#1D4ED8] text-white border border-[#1E40AF]',
    iconColor: 'text-white',
    titleColor: 'text-white',
    messageColor: 'text-blue-100',
    closeColor: 'text-white/80 hover:text-white hover:bg-white/10',
    bar: 'bg-blue-300',
  },
};

const TYPE_LABELS = {
  success: 'Success',
  error: 'Error',
  warning: 'Warning',
  info: 'Information',
};

export function Toast({ toast: item, onDismiss }) {
  const style = TOAST_STYLES[item.type] || TOAST_STYLES.info;
  const Icon = style.icon;
  const isUrgent = item.type === 'error' || item.type === 'warning';

  return (
    <div
      role={isUrgent ? 'alert' : 'status'}
      aria-live={isUrgent ? 'assertive' : 'polite'}
      className={`pointer-events-auto relative w-full overflow-hidden rounded-xl ${style.panel} shadow-xl animate-toast-in`}
    >
      <div className="flex items-start gap-3 p-3.5 pr-9">
        <Icon className={`w-4 h-4 mt-0.5 shrink-0 ${style.iconColor}`} />
        <div className="min-w-0 flex-1">
          <p className={`text-xs font-bold ${style.titleColor}`}>
            {item.title || TYPE_LABELS[item.type] || 'Notice'}
          </p>
          {item.message && (
            <p className={`mt-0.5 text-xs leading-relaxed ${style.messageColor} break-words`}>
              {item.message}
            </p>
          )}
        </div>
      </div>

      <button
        type="button"
        onClick={() => onDismiss(item.id)}
        aria-label="Dismiss notification"
        className={`absolute right-2 top-2 p-1 rounded-md ${style.closeColor} transition-colors cursor-pointer`}
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
      aria-label="Notifications"
      className="pointer-events-none fixed top-4 right-4 z-[9999] flex w-full max-w-sm flex-col gap-2 p-2"
    >
      {toasts.map((item) => (
        <Toast key={item.id} toast={item} onDismiss={dismiss} />
      ))}
    </div>,
    document.body
  );
}
