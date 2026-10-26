import { useState, useRef, useEffect } from 'react';
import { Package, ShoppingCart, BarChart3, Settings, ChevronDown, LogOut, User } from 'lucide-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useWorkspaceStore } from '../../stores/workspaceStore';
import { tauriApi } from '../../lib/tauri';
import iconShell from '../../assets/icon-shell.png';

/** Navigation structure – mirrors Java NavBarBuilder */
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
    label: 'Commercial',
    icon: ShoppingCart,
    items: [
      { label: 'Point of Sale', viewId: 'pos' },
      { label: 'Bill History',  viewId: 'bills' },
      { label: 'Customers',     viewId: 'customers' },
    ],
  },
  {
    label: 'Insights',
    icon: BarChart3,
    items: [
      { label: 'Sales Reports',   viewId: 'sales-reports' },
      { label: 'Sales Analytics', viewId: 'reports' },
      { label: 'Product Flow',    viewId: 'product-flow' },
      { label: 'Audit Log',       viewId: 'audit-log' },
    ],
  },
];

const NAV_DIRECT = [
  { label: 'Settings', icon: Settings, viewId: 'settings' },
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
        onClick={() => open ? onClose() : onOpen()}
        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
          open
            ? 'bg-slate-700 text-white'
            : 'text-slate-300 hover:text-white hover:bg-slate-800'
        }`}
      >
        <Icon className="w-3.5 h-3.5" />
        <span>{menu.label}</span>
        <ChevronDown className={`w-3 h-3 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-1 z-50 min-w-44 bg-slate-800 border border-slate-700/80 rounded-xl shadow-2xl shadow-black/40 py-1 overflow-hidden">
          {menu.items.map((item) => (
            <button
              key={item.viewId}
              onClick={() => { openOrFocus(item.label, item.viewId); onClose(); }}
              className="w-full text-left px-4 py-2 text-xs text-slate-300 hover:text-white hover:bg-slate-700/70 transition-colors"
            >
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export default function NavBar({ legacyDetected, authUser }) {
  const [openMenu, setOpenMenu] = useState(null);
  const { openOrFocus } = useWorkspaceStore();
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
    },
  });

  return (
    <header className="h-11 bg-slate-900 border-b border-slate-800/80 flex items-center px-3 gap-2 select-none flex-shrink-0">
      {/* Brand */}
      <div className="flex items-center gap-2 mr-3">
        <img src={iconShell} alt="PicoPOSSUM" className="w-7 h-7 rounded-lg" />
        <span className="font-bold text-slate-100 text-sm tracking-tight">PicoPOSSUM</span>
        <span className="text-[10px] text-emerald-400/80 font-medium">Retail Terminal</span>
      </div>

      {/* Separator */}
      <div className="w-px h-5 bg-slate-800 mx-1" />

      {/* Dropdown menus */}
      {NAV_MENUS.map((menu) => (
        <DropdownMenu
          key={menu.label}
          menu={menu}
          open={openMenu === menu.label}
          onOpen={() => setOpenMenu(menu.label)}
          onClose={() => setOpenMenu(null)}
        />
      ))}

      {/* Direct nav buttons */}
      {NAV_DIRECT.map((item) => {
        const Icon = item.icon;
        return (
          <button
            key={item.viewId}
            onClick={() => openOrFocus(item.label, item.viewId)}
            className="relative flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-300 hover:text-white hover:bg-slate-800 transition-all cursor-pointer"
          >
            <Icon className="w-3.5 h-3.5" />
            <span>{item.label}</span>
            {legacyDetected && item.viewId === 'settings' && (
              <span className="absolute top-1 right-1 w-1.5 h-1.5 rounded-full bg-amber-400" />
            )}
          </button>
        );
      })}

      {/* Spacer to push user profile to right */}
      <div className="flex-1" />

      {/* Authenticated user badge & Lock / Sign Out */}
      {authUser && (
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-slate-950/70 border border-slate-800 text-[11px] text-slate-300">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
            <User className="w-3 h-3 text-slate-400" />
            <span className="font-semibold text-slate-200">{authUser.name || authUser.username}</span>
            <span className="text-slate-500 font-mono text-[10px]">(@{authUser.username})</span>
          </div>

          <button
            type="button"
            onClick={() => {
              if (confirm('Lock terminal and sign out?')) {
                logoutMutation.mutate();
              }
            }}
            title="Lock Terminal & Sign Out"
            className="flex items-center gap-1 px-2.5 py-1 rounded-lg text-slate-400 hover:text-rose-300 hover:bg-rose-500/10 text-xs transition-colors cursor-pointer border border-transparent hover:border-rose-500/20"
          >
            <LogOut className="w-3.5 h-3.5" />
            <span className="text-[11px] font-medium">Lock</span>
          </button>
        </div>
      )}
    </header>
  );
}
