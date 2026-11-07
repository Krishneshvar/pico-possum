import { create } from 'zustand';
import { toast } from './toast.store';

let _nextId = 1;

/**
 * Workspace tab store.
 *
 * Each tab:
 *   { id, title, viewId, tabKey, props, closeable, isDirty }
 *
 * Up to 10 tabs at once.
 */
export const useWorkspaceStore = create((set, get) => ({
  tabs: [],
  activeTabId: null,

  /**
   * Open a tab for the given viewId (or custom tabKey), or focus it if already open.
   * Warns the operator if the maximum limit of 10 tabs is reached.
   */
  openOrFocus(title, viewId, props = {}, tabKey = null) {
    const key = tabKey || viewId;
    const existing = get().tabs.find((t) => (t.tabKey || t.viewId) === key);
    if (existing) {
      set({
        activeTabId: existing.id,
        tabs: get().tabs.map((t) => (t.id === existing.id ? { ...t, title, props: { ...t.props, ...props } } : t)),
      });
      return;
    }

    const { tabs } = get();
    if (tabs.length >= 10) {
      toast.warning(
        'Maximum limit of 10 open tabs reached. Please close a tab before opening another workspace.',
        { title: 'Tab Limit Reached' }
      );
      return;
    }

    const id = _nextId++;
    const newTab = { id, title, viewId, tabKey: key, props, closeable: true, isDirty: false };
    set((s) => ({ tabs: [...s.tabs, newTab], activeTabId: id }));
  },

  /**
   * Close a tab by id.
   */
  closeTab(id) {
    const { tabs, activeTabId } = get();
    const idx = tabs.findIndex((t) => t.id === id);
    if (idx === -1) return;

    const newTabs = tabs.filter((t) => t.id !== id);
    let newActive = activeTabId;

    if (activeTabId === id) {
      if (newTabs.length > 0) {
        const nextIdx = Math.min(idx, newTabs.length - 1);
        newActive = newTabs[nextIdx].id;
      } else {
        newActive = null;
      }
    }

    set({
      tabs: newTabs,
      activeTabId: newActive,
    });
  },

  /**
   * Close all tabs other than the specified tab id.
   */
  closeOtherTabs(id) {
    const { tabs } = get();
    const target = tabs.find((t) => t.id === id);
    if (!target) return;
    set({
      tabs: [target],
      activeTabId: target.id,
    });
  },

  /**
   * Close all tabs.
   */
  closeAllTabs() {
    const { tabs } = get();
    if (tabs.length === 0) return;
    set({
      tabs: [],
      activeTabId: null,
    });
  },

  setTabDirty(id, isDirty = true) {
    set({
      tabs: get().tabs.map((t) => (t.id === id ? { ...t, isDirty } : t)),
    });
  },

  setActiveTab(id) {
    set({ activeTabId: id });
  },

  /**
   * Move a tab to a new index (drag-and-drop reordering).
   */
  moveTab(id, toIndex) {
    const { tabs } = get();
    const fromIndex = tabs.findIndex((t) => t.id === id);
    if (fromIndex === -1) return;

    const target = Math.max(0, Math.min(toIndex, tabs.length - 1));
    if (fromIndex === target) return;

    const next = [...tabs];
    const [moved] = next.splice(fromIndex, 1);
    next.splice(target, 0, moved);
    set({ tabs: next });
  },

  cycleTab(step) {
    const { tabs, activeTabId } = get();
    if (tabs.length === 0) return;
    const idx = tabs.findIndex((t) => t.id === activeTabId);
    const next = ((idx + step) % tabs.length + tabs.length) % tabs.length;
    set({ activeTabId: tabs[next].id });
  },
}));
