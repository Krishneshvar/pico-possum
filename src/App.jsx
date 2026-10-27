import { QueryClientProvider, useQuery } from '@tanstack/react-query';
import { queryClient } from './lib/queryClient';
import NavBar from './components/layout/NavBar';
import TabBar from './components/layout/TabBar';
import ToastContainer from './components/common/ToastContainer';
import { ConfirmDialogHost } from './components/common/ConfirmDialog';
import { useWorkspaceStore } from './stores/workspaceStore';
import { tauriApi } from './lib/tauri';
import { applyGeneralSettings } from './lib/formatters';

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
  const { tabs, activeTabId } = useWorkspaceStore();
  const activeTab = tabs.find((t) => t.id === activeTabId);

  if (!activeTab) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center bg-slate-950 text-slate-600 select-none gap-4">
        <img
          src="/src/assets/icon-shell.png"
          alt="PicoPOSSUM"
          className="w-24 h-24 rounded-2xl opacity-20"
        />
        <div className="text-center">
          <p className="text-lg font-bold text-slate-700">Nothing open yet</p>
          <p className="text-sm mt-1">Use the menu bar above to open a view</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-hidden relative">
      {/* Render ALL tabs but only show the active one – keeps state alive */}
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

import { RefreshCw } from 'lucide-react';
import AuthScreen from './features/auth/AuthScreen';

function MainLayout({ authUser }) {
  // Load locale/currency preferences once and keep the shared formatters in sync.
  const { data: generalSettings } = useQuery({
    queryKey: ['general_settings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });

  if (generalSettings) applyGeneralSettings(generalSettings);

  return (
    <div className="flex flex-col h-screen w-screen overflow-hidden bg-slate-950 text-slate-100 antialiased">
      {/* Top navigation menu bar */}
      <NavBar authUser={authUser} />

      {/* Browser-style tab strip */}
      <TabBar />

      {/* Content area */}
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
      <div className="flex h-screen w-screen flex-col items-center justify-center bg-slate-950 text-slate-400 gap-3 select-none">
        <div className="w-12 h-12 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-center">
          <RefreshCw className="w-6 h-6 animate-spin text-emerald-400" />
        </div>
        <span className="text-xs font-medium text-slate-500">Initializing Pico POS...</span>
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
