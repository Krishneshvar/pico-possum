import { create } from 'zustand';

let _nextId = 1;
let _resolver = null;

function settle(result) {
  const resolve = _resolver;
  _resolver = null;
  if (resolve) resolve(result);
}

/**
 * Global confirmation dialog store.
 *
 * Any component can request confirmation without prop drilling:
 *   import { confirmDialog } from '../../stores/confirm.store';
 *   const { confirmed } = await confirmDialog({ title: 'Delete product?' });
 *   if (confirmed) deleteProduct();
 *
 * <ConfirmDialogHost /> renders the active request once at the app root and
 * resolves the returned promise when the user confirms or cancels.
 */
export const useConfirmStore = create((set) => ({
  request: null,

  open(options = {}) {
    // Only one confirmation can be visible at a time – dismiss any previous one.
    settle({ confirmed: false, value: null });

    return new Promise((resolve) => {
      _resolver = resolve;
      set({
        request: {
          id: _nextId++,
          title: 'Are you sure?',
          message: '',
          details: [],
          confirmLabel: 'Confirm',
          cancelLabel: 'Cancel',
          tone: 'danger',
          icon: null,
          input: null,
          ...options,
        },
      });
    });
  },

  close(result = { confirmed: false, value: null }) {
    set({ request: null });
    settle(result);
  },
}));

/**
 * Ask the user to confirm an action.
 *
 * @param {object}  options
 * @param {string}  [options.title]         Dialog heading.
 * @param {string}  [options.message]       Supporting copy.
 * @param {string[]}[options.details]       Optional bullet points.
 * @param {string}  [options.confirmLabel]  Confirm button text.
 * @param {string}  [options.cancelLabel]   Cancel button text.
 * @param {'danger'|'warning'|'primary'} [options.tone]
 * @param {React.ComponentType} [options.icon] Optional lucide icon override.
 * @param {object}  [options.input]         Optional text input, e.g.
 *                                          { label, defaultValue, placeholder, required }.
 * @returns {Promise<{ confirmed: boolean, value: string | null }>}
 */
export function confirmDialog(options) {
  return useConfirmStore.getState().open(options);
}
