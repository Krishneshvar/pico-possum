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
   * Open a tab for the given viewId (or custom tabKey), or focus it if already open.
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
      // Focus the last tab if max limit reached
      if (tabs.length > 0) set({ activeTabId: tabs[tabs.length - 1].id });
      return;
    }

    const id = _nextId++;
    const newTab = { id, title, viewId, tabKey: key, props, closeable: true };
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
