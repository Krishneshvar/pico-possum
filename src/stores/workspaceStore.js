import { create } from 'zustand';

let _nextId = 1;

/**
 * Workspace tab store – mirrors the Java WorkspaceDesktop/WorkspaceManager logic.
 *
 * Each tab:
 *   { id, title, viewId, closeable }
 *
 * viewId maps to a component in the registry (App.jsx).
 * Up to 10 tabs at once (matching Java limit).
 */
export const useWorkspaceStore = create((set, get) => ({
  tabs: [],
  activeTabId: null,

  /**
   * Open a tab for the given viewId, or focus it if already open.
   */
  openOrFocus(title, viewId, props = {}) {
    const existing = get().tabs.find((t) => t.viewId === viewId);
    if (existing) {
      set({ activeTabId: existing.id });
      return;
    }

    const { tabs } = get();
    if (tabs.length >= 10) {
      // Callers can watch this; for now just silently focus last
      return;
    }

    const id = _nextId++;
    const newTab = { id, title, viewId, props, closeable: true };
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
      // focus neighbour
      if (newTabs.length > 0) {
        const nextIdx = Math.min(idx, newTabs.length - 1);
        newActive = newTabs[nextIdx].id;
      } else {
        newActive = null;
      }
    }

    set({ tabs: newTabs, activeTabId: newActive });
  },

  setActiveTab(id) {
    set({ activeTabId: id });
  },

  cycleTab(step) {
    const { tabs, activeTabId } = get();
    if (tabs.length === 0) return;
    const idx = tabs.findIndex((t) => t.id === activeTabId);
    const next = ((idx + step) % tabs.length + tabs.length) % tabs.length;
    set({ activeTabId: tabs[next].id });
  },
}));
