import { create } from 'zustand';

const THEME_STORAGE_KEY = 'pico-possum:theme';
const UI_SCALE_STORAGE_KEY = 'pico-possum:ui-scale';

export const MIN_UI_SCALE = 0.75;
export const MAX_UI_SCALE = 1.5;
export const DEFAULT_UI_SCALE = 1.0;
export const UI_SCALE_STEP = 0.05;

export function clampUiScale(value) {
  const num = Number(value);
  if (!Number.isFinite(num)) return DEFAULT_UI_SCALE;
  const clamped = Math.min(MAX_UI_SCALE, Math.max(MIN_UI_SCALE, num));
  return Math.round(clamped * 20) / 20;
}

function applyUiScale(scale) {
  if (typeof document !== 'undefined') {
    document.documentElement.style.fontSize = `${(scale * 16).toFixed(2)}px`;
  }
}

function getInitialTheme() {
  if (typeof window === 'undefined') return 'light';
  try {
    const saved = localStorage.getItem(THEME_STORAGE_KEY);
    if (saved === 'dark' || saved === 'light') return saved;
  } catch {
    // fallback
  }
  return 'light'; // Default recommended theme: Light operational
}

function getInitialUiScale() {
  if (typeof window === 'undefined') return DEFAULT_UI_SCALE;
  try {
    const saved = localStorage.getItem(UI_SCALE_STORAGE_KEY);
    if (saved !== null) return clampUiScale(saved);
  } catch {
    // fallback
  }
  return DEFAULT_UI_SCALE;
}

export const useThemeStore = create((set, get) => ({
  theme: getInitialTheme(),
  uiScale: getInitialUiScale(),

  setTheme: (theme) => {
    const nextTheme = theme === 'dark' ? 'dark' : 'light';
    try {
      localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
    } catch {
      // storage unavailable
    }
    if (typeof document !== 'undefined') {
      document.documentElement.setAttribute('data-theme', nextTheme);
      if (nextTheme === 'dark') {
        document.documentElement.classList.add('dark');
      } else {
        document.documentElement.classList.remove('dark');
      }
    }
    set({ theme: nextTheme });
  },

  toggleTheme: () => {
    const current = get().theme;
    get().setTheme(current === 'dark' ? 'light' : 'dark');
  },

  setUiScale: (scale) => {
    const nextScale = clampUiScale(scale);
    try {
      localStorage.setItem(UI_SCALE_STORAGE_KEY, String(nextScale));
    } catch {
      // storage unavailable
    }
    applyUiScale(nextScale);
    set({ uiScale: nextScale });
  },

  resetUiScale: () => {
    get().setUiScale(DEFAULT_UI_SCALE);
  },
}));

// Apply theme & UI scale immediately to prevent FOUC
if (typeof document !== 'undefined') {
  const initial = getInitialTheme();
  document.documentElement.setAttribute('data-theme', initial);
  if (initial === 'dark') {
    document.documentElement.classList.add('dark');
  } else {
    document.documentElement.classList.remove('dark');
  }
  applyUiScale(getInitialUiScale());
}

