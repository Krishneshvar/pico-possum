import { useState, useRef, useEffect } from 'react';
import {
  Package,
  ShoppingCart,
  BarChart3,
  Settings,
  ChevronDown,
  Lock,
  Sun,
  Moon,
  Database,
  Sliders,
} from 'lucide-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useWorkspaceStore } from '../../stores/workspaceStore';
import { useThemeStore } from '../../stores/theme.store';
import { tauriApi } from '../../lib/tauri';
import { toast } from '../../stores/toast.store';
import { confirmDialog } from '../../stores/confirm.store';
import iconShell from '../../assets/icon-shell.png';

/** Navigation menus: Inventory, Sales (POS, Bills, Customers), Reports, System */
const NAV_MENUS = [
  {
    label: 'Inventory',
    icon: Package,
    items: [
      { label: 'Products',       viewId: 'products' },
      { label: 'Categories',     viewId: 'categories' },
      { label: 'Stock Activity', viewId: 'stock-ledger' },
    ],
  },
  {
    label: 'Sales',
    icon: ShoppingCart,
    items: [
      { label: 'Point of Sale',  viewId: 'pos' },
      { label: 'Bill History',   viewId: 'bills' },
      { label: 'Customers',      viewId: 'customers' },
    ],
  },
  {
    label: 'Reports',
    icon: BarChart3,
    items: [
      { label: 'Sales Reports',         viewId: 'sales-reports' },
      { label: 'Sales Analytics',       viewId: 'reports' },
      { label: 'Product Sales History', viewId: 'product-flow' },
    ],
  },
  {
    label: 'System',
    icon: Sliders,
    items: [
      { label: 'Audit Log', viewId: 'audit-log' },
      { label: 'Settings',  viewId: 'settings' },
    ],
  },
];

function DropdownMenu({ menu, open, onOpen, onClose }) {
  const { openOrFocus } = useWorkspaceStore();
  const ref = useRef(null);
  const Icon = menu.icon;

  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) onClose();
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open, onClose]);

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => (open ? onClose() : onOpen())}
        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
          open
            ? 'bg-slate-800 text-white font-semibold ring-1 ring-slate-700'
            : 'text-slate-300 hover:text-white hover:bg-slate-800/80'
        }`}
      >
        <Icon className="w-3.5 h-3.5 opacity-80" />
        <span>{menu.label}</span>
        <ChevronDown className={`w-3 h-3 opacity-60 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-1.5 z-50 min-w-48 bg-surface text-slate-900 dark:text-slate-100 border border-border-default rounded-xl shadow-xl py-1 overflow-hidden animate-scale-in">
          {menu.items.map((item) => (
            <button
              key={item.viewId}
              type="button"
              onClick={() => {
                openOrFocus(item.label, item.viewId);
                onClose();
              }}
              className="w-full text-left px-3.5 py-2 text-xs text-slate-800 dark:text-slate-200 hover:text-brand dark:hover:text-white hover:bg-surface-secondary transition-colors cursor-pointer flex items-center justify-between font-medium"
            >
              <span>{item.label}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export default function NavBar({ authUser }) {
  const [openMenu, setOpenMenu] = useState(null);
  const [profileOpen, setProfileOpen] = useState(false);
  const profileRef = useRef(null);
  const { openOrFocus } = useWorkspaceStore();
  const { theme, toggleTheme } = useThemeStore();
  const queryClient = useQueryClient();

  const logoutMutation = useMutation({
    mutationFn: () => tauriApi.logout(),
    onSuccess: () => {
      queryClient.setQueryData(['auth_state'], {
        requires_setup: false,
        is_authenticated: false,
        user: null,
        session_token: null,
      });
      queryClient.invalidateQueries({ queryKey: ['auth_state'] });
      toast.info('Terminal locked. Sign in to continue.');
    },
    onError: (err) => toast.error(`Failed to lock terminal: ${err}`, { title: 'Lock failed' }),
  });

  useEffect(() => {
    if (!profileOpen) return;
    const handler = (e) => {
      if (profileRef.current && !profileRef.current.contains(e.target)) setProfileOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [profileOpen]);

  const handleLockTerminal = async () => {
    const { confirmed } = await confirmDialog({
      title: 'Lock Terminal',
      message: 'Lock the point of sale terminal and require password to re-enter?',
      confirmLabel: 'Lock Terminal',
      cancelLabel: 'Cancel',
      tone: 'warning',
      icon: Lock,
    });
    if (confirmed) {
      logoutMutation.mutate();
    }
  };

  return (
    <header className="h-12 bg-navbar-bg text-navbar-text border-b border-navbar-border flex items-center px-4 gap-2 select-none shrink-0 z-30 shadow-xs">
      {/* Brand */}
      <div className="flex items-center gap-2.5 mr-3">
        <img src={iconShell} alt="PicoPOSSUM" className="w-7 h-7 rounded-lg shadow-xs" />
        <span className="font-bold text-white text-sm tracking-tight">PicoPOSSUM</span>
      </div>

      {/* Primary Navigation Menus */}
      <div className="flex items-center gap-1">
        {NAV_MENUS.map((menu) => (
          <DropdownMenu
            key={menu.label}
            menu={menu}
            open={openMenu === menu.label}
            onOpen={() => setOpenMenu(menu.label)}
            onClose={() => setOpenMenu(null)}
          />
        ))}
      </div>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Operational Status Area: DB connection */}
      <div className="hidden md:flex items-center gap-2 text-[11px] mr-2">
        <div
          className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-slate-800/80 border border-slate-700/80 text-slate-300"
          title="Local SQLite Database Connected"
        >
          <span className="w-2 h-2 rounded-full bg-emerald-400" />
          <Database className="w-3 h-3 text-slate-400" />
          <span className="font-medium text-slate-200">DB Connected</span>
        </div>
      </div>

      {/* Theme Toggle (Light / Dark) */}
      <button
        type="button"
        onClick={toggleTheme}
        title={`Switch to ${theme === 'dark' ? 'Light' : 'Dark'} mode`}
        className="p-2 rounded-lg text-slate-300 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer border border-slate-700/80 mr-1"
      >
        {theme === 'dark' ? (
          <Sun className="w-3.5 h-3.5 text-amber-400" />
        ) : (
          <Moon className="w-3.5 h-3.5 text-slate-300" />
        )}
      </button>

      {/* Authenticated User & Profile Dropdown */}
      {authUser && (
        <div ref={profileRef} className="relative">
          <button
            type="button"
            onClick={() => setProfileOpen((o) => !o)}
            className="flex items-center gap-2 pl-2 pr-2.5 py-1 rounded-lg border border-slate-700/80 bg-slate-800/80 hover:bg-slate-700/80 transition-colors cursor-pointer"
          >
            <div className="w-6 h-6 rounded-full bg-brand text-white flex items-center justify-center font-bold text-xs">
              {(authUser.name || authUser.username || 'U')[0].toUpperCase()}
            </div>
            <div className="text-left text-[11px] leading-tight">
              <div className="font-semibold text-slate-200">{authUser.name || authUser.username}</div>
              <div className="text-[10px] text-slate-400">@{authUser.username}</div>
            </div>
            <ChevronDown className="w-3 h-3 text-slate-400 ml-0.5" />
          </button>

          {profileOpen && (
            <div className="absolute right-0 top-full mt-1.5 w-52 bg-surface text-slate-900 dark:text-slate-100 border border-border-default rounded-xl shadow-xl p-1.5 z-50 animate-scale-in">
              <div className="px-3 py-2 border-b border-border-default mb-1">
                <div className="text-xs font-bold text-slate-900 dark:text-white">{authUser.name || 'Store Operator'}</div>
                <div className="text-[11px] text-slate-600 dark:text-slate-400">Administrator Session</div>
              </div>

              <button
                type="button"
                onClick={() => {
                  setProfileOpen(false);
                  openOrFocus('Settings', 'settings');
                }}
                className="w-full flex items-center gap-2 px-3 py-2 text-xs text-slate-800 dark:text-slate-200 hover:text-brand dark:hover:text-white hover:bg-surface-secondary rounded-lg transition-colors cursor-pointer font-medium"
              >
                <Settings className="w-3.5 h-3.5" />
                <span>Settings</span>
              </button>

              <button
                type="button"
                onClick={() => {
                  setProfileOpen(false);
                  toggleTheme();
                }}
                className="w-full flex items-center justify-between px-3 py-2 text-xs text-slate-800 dark:text-slate-200 hover:text-brand dark:hover:text-white hover:bg-surface-secondary rounded-lg transition-colors cursor-pointer font-medium"
              >
                <div className="flex items-center gap-2">
                  {theme === 'dark' ? <Sun className="w-3.5 h-3.5" /> : <Moon className="w-3.5 h-3.5" />}
                  <span>Appearance</span>
                </div>
                <span className="text-[10px] font-semibold text-slate-500 dark:text-slate-400 capitalize">{theme}</span>
              </button>

              <div className="h-px bg-border-default my-1" />

              <button
                type="button"
                onClick={() => {
                  setProfileOpen(false);
                  handleLockTerminal();
                }}
                className="w-full flex items-center gap-2 px-3 py-2 text-xs font-semibold text-danger hover:bg-danger-bg rounded-lg transition-colors cursor-pointer"
              >
                <Lock className="w-3.5 h-3.5" />
                <span>Lock Terminal</span>
              </button>
            </div>
          )}
        </div>
      )}
    </header>
  );
}
