import { create } from 'zustand';

const MAX_TOASTS = 5;

const TYPE_DURATIONS = {
  success: 3500,
  info: 4000,
  warning: 5000,
  error: 6000,
};

const timers = new Map();
let _nextId = 1;

function normalizeMessage(message) {
  if (message === null || message === undefined) return '';
  if (typeof message === 'string') return message;
  if (message instanceof Error) return message.message;
  return String(message);
}

/**
 * Toast store – global queue of transient notifications.
 *
 * Any component can trigger a toast without prop drilling:
 *   import { toast } from '../../stores/toast.store';
 *   toast.success('Product saved');
 *   toast.error(err);
 *
 * <ToastContainer /> renders the queue once at the app root.
 */
export const useToastStore = create((set, get) => ({
  toasts: [],

  show({ type = 'info', title = null, message = '', duration } = {}) {
    const id = _nextId++;
    const resolvedDuration = duration ?? TYPE_DURATIONS[type] ?? 4000;

    const entry = {
      id,
      type,
      title: title || null,
      message: normalizeMessage(message),
      duration: resolvedDuration,
    };

    set((state) => {
      const next = [...state.toasts, entry];
      // Drop the oldest toasts when the queue is full, cleaning up their timers.
      while (next.length > MAX_TOASTS) {
        const evicted = next.shift();
        const timer = timers.get(evicted.id);
        if (timer) {
          clearTimeout(timer);
          timers.delete(evicted.id);
        }
      }
      return { toasts: next };
    });

    if (resolvedDuration > 0) {
      const timer = setTimeout(() => get().dismiss(id), resolvedDuration);
      timers.set(id, timer);
    }

    return id;
  },

  dismiss(id) {
    const timer = timers.get(id);
    if (timer) {
      clearTimeout(timer);
      timers.delete(id);
    }
    set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) }));
  },

  clear() {
    for (const timer of timers.values()) clearTimeout(timer);
    timers.clear();
    set({ toasts: [] });
  },
}));

const show = (type) => (message, options = {}) =>
  useToastStore.getState().show({ ...options, type, message });

/** Imperative helper – callable from components, hooks and plain modules. */
export const toast = {
  success: show('success'),
  error: show('error'),
  warning: show('warning'),
  info: show('info'),
  show: (options) => useToastStore.getState().show(options),
  dismiss: (id) => useToastStore.getState().dismiss(id),
  clear: () => useToastStore.getState().clear(),
};
