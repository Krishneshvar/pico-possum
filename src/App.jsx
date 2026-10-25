import { useState } from 'react';
import { QueryClientProvider, useQuery } from '@tanstack/react-query';
import { queryClient } from './lib/queryClient';
import Sidebar from './components/layout/Sidebar';
import PosView from './features/commercial/pos/PosView';
import BillsHistoryView from './features/commercial/bills/BillsHistoryView';
import ProductsView from './features/inventory/products/ProductsView';
import CategoriesView from './features/inventory/categories/CategoriesView';
import StockLedgerView from './features/inventory/stock-ledger/StockLedgerView';
import CustomersView from './features/commercial/customers/CustomersView';
import ReportsView from './features/insights/reports/ReportsView';
import SettingsView from './features/settings/SettingsView';
import { tauriApi } from './lib/tauri';

function MainLayout() {
  const [activeRoute, setActiveRoute] = useState('pos');

  // Check if legacy Java database is detected on machine
  const { data: legacyPath } = useQuery({
    queryKey: ['detected_legacy_db'],
    queryFn: () => tauriApi.detectLegacyDatabase(),
  });

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-slate-950 text-slate-100 antialiased">
      {/* Navigation Sidebar */}
      <Sidebar
        activeRoute={activeRoute}
        onNavigate={setActiveRoute}
        legacyDetected={!!legacyPath}
      />

      {/* Main Feature View */}
      <main className="flex-1 flex flex-col h-full overflow-hidden">
        {activeRoute === 'pos' && <PosView />}
        {activeRoute === 'bills' && <BillsHistoryView />}
        {activeRoute === 'products' && <ProductsView />}
        {activeRoute === 'categories' && <CategoriesView />}
        {activeRoute === 'stock-ledger' && <StockLedgerView />}
        {activeRoute === 'customers' && <CustomersView />}
        {activeRoute === 'reports' && <ReportsView />}
        {activeRoute === 'settings' && <SettingsView />}
      </main>
    </div>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <MainLayout />
    </QueryClientProvider>
  );
}
