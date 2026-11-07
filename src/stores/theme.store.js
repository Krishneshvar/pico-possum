import { create } from 'zustand';

const THEME_STORAGE_KEY = 'pico-possum:theme';

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

export const useThemeStore = create((set, get) => ({
  theme: getInitialTheme(),

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
}));

// Apply theme immediately to prevent FOUC
if (typeof document !== 'undefined') {
  const initial = getInitialTheme();
  document.documentElement.setAttribute('data-theme', initial);
  if (initial === 'dark') {
    document.documentElement.classList.add('dark');
  } else {
    document.documentElement.classList.remove('dark');
  }
}

