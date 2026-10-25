import {
  ShoppingCart,
  Package,
  FolderTree,
  History,
  Users,
  BarChart3,
  Settings,
  Database,
  ArrowRightLeft
} from 'lucide-react';

export default function Sidebar({ activeRoute, onNavigate, legacyDetected }) {
  const navItems = [
    { id: 'pos', label: 'Point of Sale', icon: ShoppingCart, badge: 'POS' },
    { id: 'bills', label: 'Bill History', icon: History },
    { id: 'products', label: 'Products', icon: Package },
    { id: 'categories', label: 'Categories', icon: FolderTree },
    { id: 'stock-ledger', label: 'Stock Activity', icon: ArrowRightLeft },
    { id: 'customers', label: 'Customers', icon: Users },
    { id: 'reports', label: 'Analytics & Reports', icon: BarChart3 },
    { id: 'settings', label: 'Settings', icon: Settings, alert: legacyDetected },
  ];

  return (
    <aside className="w-64 bg-slate-900/90 border-r border-slate-800/80 flex flex-col justify-between select-none">
      <div>
        {/* Brand Header */}
        <div className="h-16 flex items-center gap-3 px-5 border-b border-slate-800/80 bg-slate-900/50">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-emerald-600 to-teal-400 flex items-center justify-center shadow-lg shadow-emerald-500/20 font-bold text-white text-lg">
            P
          </div>
          <div>
            <h1 className="font-bold text-slate-100 text-base leading-tight tracking-tight">PicoPOSSUM</h1>
            <p className="text-xs text-emerald-400/90 font-medium">Retail Terminal</p>
          </div>
        </div>

        {/* Navigation Items */}
        <nav className="p-3 space-y-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeRoute === item.id;
            return (
              <button
                key={item.id}
                onClick={() => onNavigate(item.id)}
                className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-lg text-sm font-medium transition-all duration-150 ${
                  isActive
                    ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 shadow-sm'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
                }`}
              >
                <div className="flex items-center gap-3">
                  <Icon className={`w-4 h-4 ${isActive ? 'text-emerald-400' : 'text-slate-400'}`} />
                  <span>{item.label}</span>
                </div>
                {item.badge && (
                  <span className="text-[10px] uppercase font-bold tracking-wider px-1.5 py-0.5 rounded bg-emerald-500/20 text-emerald-300">
                    {item.badge}
                  </span>
                )}
                {item.alert && (
                  <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" title="Legacy DB detected!" />
                )}
              </button>
            );
          })}
        </nav>
      </div>

      {/* Footer shortcut help */}
      <div className="p-4 border-t border-slate-800/80 bg-slate-950/40 text-xs text-slate-400 space-y-1.5">
        <div className="flex justify-between items-center text-[11px]">
          <span>Search Products</span>
          <kbd className="px-1.5 py-0.5 rounded bg-slate-800 border border-slate-700 text-slate-300 font-mono text-[10px]">Ctrl+K</kbd>
        </div>
        <div className="flex justify-between items-center text-[11px]">
          <span>Complete Checkout</span>
          <kbd className="px-1.5 py-0.5 rounded bg-slate-800 border border-slate-700 text-slate-300 font-mono text-[10px]">F12</kbd>
        </div>
        <div className="flex justify-between items-center text-[11px]">
          <span>Switch Bills</span>
          <kbd className="px-1.5 py-0.5 rounded bg-slate-800 border border-slate-700 text-slate-300 font-mono text-[10px]">Ctrl+1-9</kbd>
        </div>
      </div>
    </aside>
  );
}
