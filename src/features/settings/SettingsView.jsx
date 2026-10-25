import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Settings, Store, Printer, Database, CheckCircle2, AlertCircle, ArrowRight, RefreshCw, FileText } from 'lucide-react';
import { tauriApi } from '../../lib/tauri';
import ReceiptPrinter from '../../components/receipt/ReceiptPrinter';

export default function SettingsView() {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState('general'); // 'general' | 'bill' | 'migration'
  const [migrationPath, setMigrationPath] = useState('');
  const [migrationResult, setMigrationResult] = useState(null);

  // Form states
  const [general, setGeneral] = useState({
    store_name: '',
    phone: '',
    address: '',
    gstin: '',
    currency_symbol: '₹',
    currency_code: 'INR',
    date_format: 'DD/MM/YYYY',
    time_format: '12h',
    default_printer_name: '',
  });

  const [bill, setBill] = useState({
    paper_width: '80mm',
    store_name: '',
    store_details: '',
    phone: '',
    gst: '',
    show_tax: false,
    footer_text: 'Thank you for shopping with us!',
  });

  // Queries
  const { data: generalData } = useQuery({
    queryKey: ['general_settings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });

  const { data: billData } = useQuery({
    queryKey: ['bill_settings'],
    queryFn: () => tauriApi.getBillSettings(),
  });

  const { data: detectedLegacyDb } = useQuery({
    queryKey: ['detected_legacy_db'],
    queryFn: () => tauriApi.detectLegacyDatabase(),
  });

  useEffect(() => {
    if (generalData) setGeneral(generalData);
  }, [generalData]);

  useEffect(() => {
    if (billData) setBill(billData);
  }, [billData]);

  useEffect(() => {
    if (detectedLegacyDb && !migrationPath) {
      setMigrationPath(detectedLegacyDb);
    }
  }, [detectedLegacyDb]);

  // Mutations
  const updateGeneralMutation = useMutation({
    mutationFn: (data) => tauriApi.updateGeneralSettings(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['general_settings'] });
      alert('General settings saved!');
    },
  });

  const updateBillMutation = useMutation({
    mutationFn: (data) => tauriApi.updateBillSettings(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bill_settings'] });
      alert('Receipt settings saved!');
    },
  });

  const migrationMutation = useMutation({
    mutationFn: (path) => tauriApi.importLegacyDatabase(path || null),
    onSuccess: (summary) => {
      setMigrationResult(summary);
      queryClient.invalidateQueries();
    },
    onError: (err) => alert(`Migration failed: ${err}`),
  });

  const sampleSale = {
    invoice_number: 'S26CH0000001',
    created_at: new Date().toISOString(),
    customer_name: 'Demo Customer',
    biller_name: 'Cashier',
    subtotal_paise: 25000,
    discount_paise: 2000,
    tax_paise: 1250,
    total_paise: 23000,
    paid_paise: 25000,
    payment_method: 'CASH',
  };

  const sampleItems = [
    { product_name: 'Sample Product 1', quantity: 2, unit_price_paise: 10000, discount_paise: 0 },
    { product_name: 'Sample Item 2', quantity: 1, unit_price_paise: 5000, discount_paise: 2000 },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-16 px-6 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <Settings className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-slate-100">Application Settings</h2>
            <p className="text-xs text-slate-400">Configure store info, bill layout, and legacy database migration</p>
          </div>
        </div>

        {/* Tab Switcher */}
        <div className="flex bg-slate-900 border border-slate-800 rounded-xl p-1 text-xs">
          <button
            onClick={() => setActiveTab('general')}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-lg font-semibold transition-all ${
              activeTab === 'general' ? 'bg-emerald-500 text-slate-950 shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            <Store className="w-3.5 h-3.5" />
            <span>Store Profile</span>
          </button>
          <button
            onClick={() => setActiveTab('bill')}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-lg font-semibold transition-all ${
              activeTab === 'bill' ? 'bg-emerald-500 text-slate-950 shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            <Printer className="w-3.5 h-3.5" />
            <span>Receipt Design</span>
          </button>
          <button
            onClick={() => setActiveTab('migration')}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-lg font-semibold transition-all relative ${
              activeTab === 'migration' ? 'bg-emerald-500 text-slate-950 shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            <Database className="w-3.5 h-3.5" />
            <span>Data Migration</span>
            {detectedLegacyDb && (
              <span className="w-2 h-2 rounded-full bg-amber-400 absolute top-1 right-1" />
            )}
          </button>
        </div>
      </div>

      {/* Content Area */}
      <div className="flex-1 overflow-y-auto p-6 max-w-4xl">
        {/* ── Tab 1: General Settings ─────────────────────────────────── */}
        {activeTab === 'general' && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              updateGeneralMutation.mutate(general);
            }}
            className="space-y-6"
          >
            <div className="glass-card p-6 rounded-2xl border border-slate-800 space-y-4">
              <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
                <Store className="w-4 h-4 text-emerald-400" /> Store Information
              </h3>

              <div className="grid grid-cols-2 gap-4 text-xs">
                <div>
                  <label className="text-slate-400 block mb-1">Store Name</label>
                  <input
                    type="text"
                    value={general.store_name}
                    onChange={(e) => setGeneral({ ...general, store_name: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2.5 text-slate-200 focus:border-emerald-500 focus:outline-none"
                  />
                </div>
                <div>
                  <label className="text-slate-400 block mb-1">Phone Number</label>
                  <input
                    type="text"
                    value={general.phone}
                    onChange={(e) => setGeneral({ ...general, phone: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2.5 text-slate-200 focus:border-emerald-500 focus:outline-none"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4 text-xs">
                <div>
                  <label className="text-slate-400 block mb-1">Store Address / Details</label>
                  <input
                    type="text"
                    value={general.address}
                    onChange={(e) => setGeneral({ ...general, address: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2.5 text-slate-200 focus:border-emerald-500 focus:outline-none"
                  />
                </div>
                <div>
                  <label className="text-slate-400 block mb-1">GSTIN / Tax ID</label>
                  <input
                    type="text"
                    value={general.gstin}
                    onChange={(e) => setGeneral({ ...general, gstin: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2.5 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono"
                  />
                </div>
              </div>
            </div>

            <div className="glass-card p-6 rounded-2xl border border-slate-800 space-y-4">
              <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
                <FileText className="w-4 h-4 text-emerald-400" /> Currency & Regional Formatting
              </h3>

              <div className="grid grid-cols-2 gap-4 text-xs">
                <div>
                  <label className="text-slate-400 block mb-1">Currency Symbol</label>
                  <input
                    type="text"
                    value={general.currency_symbol}
                    onChange={(e) => setGeneral({ ...general, currency_symbol: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2.5 text-slate-200 focus:border-emerald-500 focus:outline-none font-bold"
                  />
                </div>
                <div>
                  <label className="text-slate-400 block mb-1">Date Format</label>
                  <select
                    value={general.date_format}
                    onChange={(e) => setGeneral({ ...general, date_format: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2.5 text-slate-200 focus:border-emerald-500 focus:outline-none"
                  >
                    <option value="DD/MM/YYYY">DD/MM/YYYY (Standard)</option>
                    <option value="MM/DD/YYYY">MM/DD/YYYY</option>
                    <option value="YYYY-MM-DD">YYYY-MM-DD</option>
                  </select>
                </div>
              </div>
            </div>

            <button
              type="submit"
              disabled={updateGeneralMutation.isPending}
              className="px-6 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs shadow-lg shadow-emerald-500/20 transition-all"
            >
              Save Store Profile
            </button>
          </form>
        )}

        {/* ── Tab 2: Bill & Receipt Settings ─────────────────────────── */}
        {activeTab === 'bill' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 items-start">
            <form
              onSubmit={(e) => {
                e.preventDefault();
                updateBillMutation.mutate(bill);
              }}
              className="space-y-4 glass-card p-6 rounded-2xl border border-slate-800 text-xs"
            >
              <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2 mb-2">
                <Printer className="w-4 h-4 text-emerald-400" /> Thermal Printer Layout
              </h3>

              <div>
                <label className="text-slate-400 block mb-1">Paper Width</label>
                <div className="grid grid-cols-2 gap-2">
                  {['80mm', '58mm'].map((w) => (
                    <button
                      key={w}
                      type="button"
                      onClick={() => setBill({ ...bill, paper_width: w })}
                      className={`p-2 rounded-lg font-bold border transition-all ${
                        bill.paper_width === w
                          ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/50'
                          : 'bg-slate-950 text-slate-400 border-slate-800'
                      }`}
                    >
                      {w} Paper
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="text-slate-400 block mb-1">Receipt Header Title</label>
                <input
                  type="text"
                  value={bill.store_name}
                  onChange={(e) => setBill({ ...bill, store_name: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2.5 text-slate-200 focus:border-emerald-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="text-slate-400 block mb-1">Store Address / Details</label>
                <textarea
                  rows="2"
                  value={bill.store_details}
                  onChange={(e) => setBill({ ...bill, store_details: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-slate-400 block mb-1">Phone</label>
                  <input
                    type="text"
                    value={bill.phone}
                    onChange={(e) => setBill({ ...bill, phone: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                  />
                </div>
                <div>
                  <label className="text-slate-400 block mb-1">GSTIN</label>
                  <input
                    type="text"
                    value={bill.gst}
                    onChange={(e) => setBill({ ...bill, gst: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono"
                  />
                </div>
              </div>

              <div>
                <label className="text-slate-400 block mb-1">Footer Message</label>
                <input
                  type="text"
                  value={bill.footer_text}
                  onChange={(e) => setBill({ ...bill, footer_text: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                />
              </div>

              <button
                type="submit"
                disabled={updateBillMutation.isPending}
                className="w-full py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs shadow-lg shadow-emerald-500/20 transition-all mt-2"
              >
                Save Receipt Design
              </button>
            </form>

            {/* Live Preview Column */}
            <div className="space-y-2">
              <span className="text-xs font-semibold text-slate-400 block text-center">Live Preview</span>
              <div className="p-4 bg-slate-900 border border-slate-800 rounded-2xl flex justify-center shadow-inner">
                <ReceiptPrinter
                  sale={sampleSale}
                  items={sampleItems}
                  billSettings={bill}
                  generalSettings={general}
                />
              </div>
            </div>
          </div>
        )}

        {/* ── Tab 3: Legacy Data Migration ───────────────────────────── */}
        {activeTab === 'migration' && (
          <div className="space-y-6">
            <div className="glass-card p-6 rounded-2xl border border-slate-800 space-y-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
                  <Database className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-100">Java PicoPOSSUM Data Migration</h3>
                  <p className="text-xs text-slate-400">
                    Seamlessly import all historical products, categories, stock, sales, and customers from the old app
                  </p>
                </div>
              </div>

              {detectedLegacyDb && (
                <div className="bg-emerald-500/10 border border-emerald-500/30 rounded-xl p-4 text-xs text-emerald-300 flex items-start gap-3">
                  <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                  <div>
                    <strong className="font-bold block text-emerald-200">Existing Shop Database Detected!</strong>
                    <p className="mt-0.5 font-mono text-[11px] text-emerald-400/90 break-all">{detectedLegacyDb}</p>
                  </div>
                </div>
              )}

              <div className="space-y-2 text-xs">
                <label className="text-slate-400 block">Legacy SQLite Database Path</label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={migrationPath}
                    onChange={(e) => setMigrationPath(e.target.value)}
                    placeholder="/path/to/pico-possum.db"
                    className="flex-1 bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-xs text-slate-200 focus:border-emerald-500 focus:outline-none font-mono"
                  />
                  <button
                    onClick={() => migrationMutation.mutate(migrationPath)}
                    disabled={migrationMutation.isPending || !migrationPath}
                    className="px-5 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-lg shadow-emerald-500/20 disabled:opacity-50 transition-all whitespace-nowrap"
                  >
                    {migrationMutation.isPending ? (
                      <>
                        <RefreshCw className="w-4 h-4 animate-spin" />
                        <span>Importing...</span>
                      </>
                    ) : (
                      <>
                        <ArrowRight className="w-4 h-4" />
                        <span>Start Migration</span>
                      </>
                    )}
                  </button>
                </div>
              </div>

              {/* Migration Result Report */}
              {migrationResult && (
                <div className="mt-4 p-5 rounded-2xl bg-emerald-500/10 border border-emerald-500/30 space-y-3 animate-in fade-in">
                  <div className="flex items-center gap-2 text-emerald-400 font-bold text-sm">
                    <CheckCircle2 className="w-5 h-5" />
                    <span>Migration Completed Successfully!</span>
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-xs">
                    <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
                      <span className="text-slate-400 block">Categories</span>
                      <strong className="text-base text-slate-100 font-mono">
                        {migrationResult.categories_imported}
                      </strong>
                    </div>
                    <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
                      <span className="text-slate-400 block">Products</span>
                      <strong className="text-base text-slate-100 font-mono">
                        {migrationResult.products_imported}
                      </strong>
                    </div>
                    <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
                      <span className="text-slate-400 block">Customers</span>
                      <strong className="text-base text-slate-100 font-mono">
                        {migrationResult.customers_imported}
                      </strong>
                    </div>
                    <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
                      <span className="text-slate-400 block">Sales Invoices</span>
                      <strong className="text-base text-slate-100 font-mono">
                        {migrationResult.sales_imported}
                      </strong>
                    </div>
                    <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
                      <span className="text-slate-400 block">Sale Line Items</span>
                      <strong className="text-base text-slate-100 font-mono">
                        {migrationResult.sale_items_imported}
                      </strong>
                    </div>
                    <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
                      <span className="text-slate-400 block">Stock Movements</span>
                      <strong className="text-base text-slate-100 font-mono">
                        {migrationResult.stock_movements_imported}
                      </strong>
                    </div>
                  </div>
                  <p className="text-[11px] text-emerald-300">
                    All financial values were accurately converted to Paise, IDs were mapped to UUIDs, and stock ledger entries have been restored.
                  </p>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
