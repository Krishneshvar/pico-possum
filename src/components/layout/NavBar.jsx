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
  const triggerRef = useRef(null);
  const itemRefs = useRef([]);
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const Icon = menu.icon;

  useEffect(() => {
    if (!open) {
      setHighlightedIndex(-1);
      return;
    }
    setHighlightedIndex(0);
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) onClose();
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open, onClose]);

  const handleKeyDown = (e) => {
    if (!open) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp' || e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        onOpen();
      }
      return;
    }

    switch (e.key) {
      case 'ArrowDown': {
        e.preventDefault();
        setHighlightedIndex((prev) => (prev < menu.items.length - 1 ? prev + 1 : 0));
        break;
      }
      case 'ArrowUp': {
        e.preventDefault();
        setHighlightedIndex((prev) => (prev > 0 ? prev - 1 : menu.items.length - 1));
        break;
      }
      case 'Enter':
      case ' ': {
        e.preventDefault();
        if (highlightedIndex >= 0 && highlightedIndex < menu.items.length) {
          const item = menu.items[highlightedIndex];
          openOrFocus(item.label, item.viewId);
          onClose();
          triggerRef.current?.focus();
        }
        break;
      }
      case 'Escape':
      case 'Tab': {
        onClose();
        triggerRef.current?.focus();
        break;
      }
      default:
        break;
    }
  };

  return (
    <div ref={ref} onKeyDown={handleKeyDown} className="relative">
      <button
        ref={triggerRef}
        type="button"
        onClick={() => (open ? onClose() : onOpen())}
        aria-haspopup="true"
        aria-expanded={open}
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
        <div
          role="menu"
          style={{ color: 'var(--app-text-primary)' }}
          className="absolute top-full left-0 mt-1.5 z-50 min-w-48 bg-surface text-text-primary border border-border-default rounded-xl shadow-xl py-1 overflow-hidden animate-scale-in"
        >
          {menu.items.map((item, idx) => {
            const isHighlighted = highlightedIndex === idx;
            return (
              <button
                key={item.viewId}
                ref={(el) => (itemRefs.current[idx] = el)}
                type="button"
                role="menuitem"
                onClick={() => {
                  openOrFocus(item.label, item.viewId);
                  onClose();
                  triggerRef.current?.focus();
                }}
                onMouseEnter={() => setHighlightedIndex(idx)}
                style={{ color: 'var(--app-text-primary)' }}
                className={`w-full text-left px-3.5 py-2 text-xs transition-colors cursor-pointer flex items-center justify-between font-medium ${
                  isHighlighted ? 'bg-surface-secondary text-brand font-semibold' : 'hover:bg-surface-secondary'
                }`}
              >
                <span style={{ color: 'inherit' }} className="font-medium">{item.label}</span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default function NavBar({ authUser }) {
  const [openMenu, setOpenMenu] = useState(null);
  const [profileOpen, setProfileOpen] = useState(false);
  const [profileIndex, setProfileIndex] = useState(-1);
  const profileRef = useRef(null);
  const profileTriggerRef = useRef(null);
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
    if (!profileOpen) {
      setProfileIndex(-1);
      return;
    }
    setProfileIndex(0);
    const handler = (e) => {
      if (profileRef.current && !profileRef.current.contains(e.target)) setProfileOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [profileOpen]);

  const handleProfileKeyDown = (e) => {
    if (!profileOpen) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp' || e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        setProfileOpen(true);
      }
      return;
    }

    switch (e.key) {
      case 'ArrowDown': {
        e.preventDefault();
        setProfileIndex((prev) => (prev < 2 ? prev + 1 : 0));
        break;
      }
      case 'ArrowUp': {
        e.preventDefault();
        setProfileIndex((prev) => (prev > 0 ? prev - 1 : 2));
        break;
      }
      case 'Enter':
      case ' ': {
        e.preventDefault();
        if (profileIndex === 0) {
          setProfileOpen(false);
          openOrFocus('Settings', 'settings');
          profileTriggerRef.current?.focus();
        } else if (profileIndex === 1) {
          toggleTheme();
        } else if (profileIndex === 2) {
          setProfileOpen(false);
          handleLockTerminal();
        }
        break;
      }
      case 'Escape':
      case 'Tab': {
        setProfileOpen(false);
        profileTriggerRef.current?.focus();
        break;
      }
      default:
        break;
    }
  };

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

      {/* Authenticated User & Profile Dropdown */}
      {authUser && (
        <div ref={profileRef} onKeyDown={handleProfileKeyDown} className="relative">
          <button
            ref={profileTriggerRef}
            type="button"
            onClick={() => setProfileOpen((o) => !o)}
            aria-haspopup="true"
            aria-expanded={profileOpen}
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
            <div
              role="menu"
              style={{ color: 'var(--app-text-primary)' }}
              className="absolute right-0 top-full mt-1.5 w-52 bg-surface text-text-primary border border-border-default rounded-xl shadow-xl p-1.5 z-50 animate-scale-in"
            >
              <div className="px-3 py-2 border-b border-border-default mb-1">
                <div style={{ color: 'var(--app-text-primary)' }} className="text-xs font-bold">{authUser.name || 'Store Operator'}</div>
                <div style={{ color: 'var(--app-text-secondary)' }} className="text-[11px]">Administrator Session</div>
              </div>

              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  setProfileOpen(false);
                  openOrFocus('Settings', 'settings');
                  profileTriggerRef.current?.focus();
                }}
                onMouseEnter={() => setProfileIndex(0)}
                style={{ color: 'var(--app-text-primary)' }}
                className={`w-full flex items-center gap-2 px-3 py-2 text-xs rounded-lg transition-colors cursor-pointer font-medium ${
                  profileIndex === 0 ? 'bg-surface-secondary text-brand font-semibold' : 'hover:bg-surface-secondary'
                }`}
              >
                <Settings className="w-3.5 h-3.5" />
                <span style={{ color: 'inherit' }}>Settings</span>
              </button>

              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  toggleTheme();
                }}
                onMouseEnter={() => setProfileIndex(1)}
                style={{ color: 'var(--app-text-primary)' }}
                className={`w-full flex items-center justify-between px-3 py-2 text-xs rounded-lg transition-colors cursor-pointer font-medium ${
                  profileIndex === 1 ? 'bg-surface-secondary text-brand font-semibold' : 'hover:bg-surface-secondary'
                }`}
              >
                <div className="flex items-center gap-2">
                  {theme === 'dark' ? <Sun className="w-3.5 h-3.5" /> : <Moon className="w-3.5 h-3.5" />}
                  <span style={{ color: 'inherit' }}>Appearance</span>
                </div>
                <span style={{ color: 'var(--app-text-muted)' }} className="text-[10px] font-semibold capitalize">{theme}</span>
              </button>

              <div className="h-px bg-border-default my-1" />

              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  setProfileOpen(false);
                  handleLockTerminal();
                }}
                onMouseEnter={() => setProfileIndex(2)}
                className={`w-full flex items-center gap-2 px-3 py-2 text-xs font-semibold text-danger rounded-lg transition-colors cursor-pointer ${
                  profileIndex === 2 ? 'bg-danger-bg font-bold' : 'hover:bg-danger-bg'
                }`}
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
