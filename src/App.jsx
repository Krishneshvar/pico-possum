import { QueryClientProvider, useQuery } from '@tanstack/react-query';
import { queryClient } from './lib/queryClient';
import NavBar from './components/layout/NavBar';
import TabBar from './components/layout/TabBar';
import ToastContainer from './components/common/ToastContainer';
import { ConfirmDialogHost } from './components/common/ConfirmDialog';
import { useWorkspaceStore } from './stores/workspaceStore';
import { tauriApi } from './lib/tauri';
import { applyGeneralSettings } from './lib/formatters';
import { RefreshCw, ShoppingCart, Package, BarChart3 } from 'lucide-react';
import AuthScreen from './features/auth/AuthScreen';

// Feature views
import PosView from './features/commercial/pos/PosView';
import BillsHistoryView from './features/commercial/bills/BillsHistoryView';
import ProductsView from './features/inventory/products/ProductsView';
import CategoriesView from './features/inventory/categories/CategoriesView';
import StockLedgerView from './features/inventory/stock-ledger/StockLedgerView';
import CustomersView from './features/commercial/customers/CustomersView';
import ReportsView from './features/insights/reports/ReportsView';
import SalesReportsView from './features/insights/sales-reports/SalesReportsView';
import ProductFlowView from './features/insights/product-flow/ProductFlowView';
import AuditLogView from './features/insights/audit-log/AuditLogView';
import SettingsView from './features/settings/SettingsView';
import BillDetailView from './features/commercial/bills/BillDetailView';

/** Registry: viewId → component */
const VIEW_REGISTRY = {
  pos:            PosView,
  bills:          BillsHistoryView,
  'bill-detail':  BillDetailView,
  products:       ProductsView,
  categories:     CategoriesView,
  'stock-ledger': StockLedgerView,
  customers:      CustomersView,
  'sales-reports': SalesReportsView,
  reports:        ReportsView,
  'product-flow': ProductFlowView,
  'audit-log':    AuditLogView,
  settings:       SettingsView,
};

function WorkspaceContent() {
  const { tabs, activeTabId, openOrFocus } = useWorkspaceStore();
  const activeTab = tabs.find((t) => t.id === activeTabId);

  if (!activeTab) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center bg-canvas text-text-muted select-none gap-6 p-6">
        <div className="w-16 h-16 rounded-2xl bg-surface border border-border-default flex items-center justify-center shadow-xs">
          <img
            src="/src/assets/icon-shell.png"
            alt="PicoPOSSUM"
            className="w-10 h-10 rounded-xl"
          />
        </div>
        <div className="text-center max-w-sm">
          <h2 className="text-base font-bold text-text-primary">No workspace tabs open</h2>
          <p className="text-xs text-text-secondary mt-1 leading-relaxed">
            Open the Point of Sale terminal or select a workspace from the navigation bar above.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => openOrFocus('Point of Sale', 'pos')}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-brand hover:bg-brand-hover text-white text-xs font-semibold shadow-xs transition-colors cursor-pointer"
          >
            <ShoppingCart className="w-4 h-4" />
            <span>Open Point of Sale</span>
          </button>
          <button
            type="button"
            onClick={() => openOrFocus('Products', 'products')}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-surface hover:bg-surface-secondary border border-border-default text-text-primary text-xs font-medium transition-colors cursor-pointer"
          >
            <Package className="w-4 h-4 text-text-muted" />
            <span>Product Catalog</span>
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-hidden relative bg-canvas">
      {/* Render tabs, showing only the active one */}
      {tabs.map((tab) => {
        const ViewComponent = VIEW_REGISTRY[tab.viewId];
        if (!ViewComponent) return null;
        return (
          <div
            key={tab.id}
            className="absolute inset-0"
            style={{ display: tab.id === activeTabId ? 'flex' : 'none', flexDirection: 'column' }}
          >
            <ViewComponent {...(tab.props ?? {})} />
          </div>
        );
      })}
    </div>
  );
}

function MainLayout({ authUser }) {
  // Load locale/currency preferences once and keep shared formatters in sync.
  const { data: generalSettings } = useQuery({
    queryKey: ['general_settings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });

  if (generalSettings) applyGeneralSettings(generalSettings);

  return (
    <div className="flex flex-col h-screen w-screen overflow-hidden bg-canvas text-text-primary antialiased">
      {/* Top navigation menu bar */}
      <NavBar authUser={authUser} />

      {/* Browser-style tab strip */}
      <TabBar />

      {/* Content workspace */}
      <WorkspaceContent />
    </div>
  );
}

function AppRoot() {
  const { data: authState, isLoading } = useQuery({
    queryKey: ['auth_state'],
    queryFn: () => tauriApi.getAuthState(),
  });

  if (isLoading) {
    return (
      <div className="flex h-screen w-screen flex-col items-center justify-center bg-canvas text-text-secondary gap-3 select-none">
        <div className="w-12 h-12 rounded-xl bg-surface border border-border-default flex items-center justify-center shadow-xs">
          <RefreshCw className="w-6 h-6 animate-spin text-brand" />
        </div>
        <span className="text-xs font-medium text-text-muted">Initializing Pico POS...</span>
      </div>
    );
  }

  // If not authenticated or requires initial admin registration, show AuthScreen
  if (!authState?.is_authenticated) {
    return (
      <AuthScreen
        authState={authState}
        onAuthenticated={(res) => {
          queryClient.setQueryData(['auth_state'], res);
        }}
      />
    );
  }

  return <MainLayout authUser={authState.user} />;
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AppRoot />
      <ToastContainer />
      <ConfirmDialogHost />
    </QueryClientProvider>
  );
}
