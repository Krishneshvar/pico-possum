import { useState, useEffect, useRef, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Settings,
  Sliders,
  Receipt,
  Printer,
  ShieldCheck,
  Database,
  RefreshCw,
  FolderOpen,
  ArrowUp,
  ArrowDown,
  Lock,
  HardDrive,
  Info,
  Check,
  FileSpreadsheet,
  Upload,
  Package,
} from 'lucide-react';
import { tauriApi } from '../../lib/tauri';
import { toast } from '../../stores/toast.store';
import { applyGeneralSettings, formatDateTime } from '../../lib/formatters';
import { nextSortState, sortRows } from '../../lib/sort';
import SortableTh from '../../components/common/SortableTh';
import ReceiptPrinter from '../../components/receipt/ReceiptPrinter';

export default function SettingsView() {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState('general'); // 'general' | 'bill' | 'hardware' | 'backup' | 'migration'
  const [migrationPath, setMigrationPath] = useState('');
  const [migrationResult, setMigrationResult] = useState(null);
  const restoreFileInputRef = useRef(null);

  // ── General Settings State ──────────────────────────────────────────────────
  const [general, setGeneral] = useState({
    store_name: '',
    phone: '',
    address: '',
    gstin: '',
    currency_symbol: '₹',
    currency_code: 'INR',
    date_format: 'DD-MM-YYYY',
    time_format: '12 hour format',
    default_printer_name: '',
    auto_numeric_sku: true,
    allow_negative_stock: false,
  });

  // ── Password UI State ───────────────────────────────────────────────────────
  const [passwordState, setPasswordState] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  // ── Bill Structure State ───────────────────────────────────────────────────
  const defaultSections = [
    {
      id: 'storeHeader',
      name: 'Store Header',
      visible: true,
      options: {
        alignment: 'center',
        fontSize: 'medium',
        storeName: 'Pico POS Store',
        storeDetails: '123 Main St, Tech City',
        phone: '9876543210',
        gst: '22AAAAA0000A1Z5',
      },
    },
    {
      id: 'billMetadata',
      name: 'Bill Metadata',
      visible: true,
      options: {
        alignment: 'left',
        fontSize: 'small',
      },
    },
    {
      id: 'itemsTable',
      name: 'Items Table',
      visible: true,
      options: {
        fontSize: 'medium',
        showTax: false,
      },
    },
    {
      id: 'totals',
      name: 'Totals & Payments',
      visible: true,
      options: {
        alignment: 'right',
        fontSize: 'medium',
      },
    },
    {
      id: 'footer',
      name: 'Footer Message',
      visible: true,
      options: {
        alignment: 'center',
        fontSize: 'small',
        text: 'Thank you for your visit!',
      },
    },
  ];

  const [bill, setBill] = useState({
    paper_width: '80mm',
    store_name: 'Pico POS Store',
    store_details: '123 Main St, Tech City',
    phone: '9876543210',
    gst: '22AAAAA0000A1Z5',
    show_tax: false,
    footer_text: 'Thank you for your visit!',
    sections: defaultSections,
  });

  // ── Feedback Toasts ─────────────────────────────────────────────────────────
  const showNotification = (message, type = 'success') => {
    const notify = toast[type] || toast.info;
    notify(message);
  };

  // ── Queries ─────────────────────────────────────────────────────────────────
  const { data: generalData } = useQuery({
    queryKey: ['general_settings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });

  const { data: billData } = useQuery({
    queryKey: ['bill_settings'],
    queryFn: () => tauriApi.getBillSettings(),
  });

  const { data: printers = [], isLoading: scanningPrinters, refetch: refreshPrinters } = useQuery({
    queryKey: ['system_printers'],
    queryFn: () => tauriApi.listPrinters(),
    enabled: activeTab === 'hardware',
  });

  const { data: backupStatus, refetch: refreshBackupStatus } = useQuery({
    queryKey: ['backup_status'],
    queryFn: () => tauriApi.getBackupStatus(),
    enabled: activeTab === 'backup',
  });

  const { data: backupsList = [], refetch: refreshBackupsList } = useQuery({
    queryKey: ['backups_list'],
    queryFn: () => tauriApi.listBackups(),
    enabled: activeTab === 'backup',
  });

  const [backupSort, setBackupSort] = useState({ key: null, dir: null });
  const sortedBackups = useMemo(
    () => sortRows(backupsList, backupSort, (row, key) => row[key]),
    [backupsList, backupSort]
  );

  const { data: detectedLegacyDb } = useQuery({
    queryKey: ['detected_legacy_db'],
    queryFn: () => tauriApi.detectLegacyDatabase(),
  });

  useEffect(() => {
    if (generalData) {
      setGeneral((prev) => ({
        ...prev,
        ...generalData,
        date_format: generalData.date_format?.replace(/\//g, '-') || 'DD-MM-YYYY',
        time_format: generalData.time_format?.includes('24') ? '24 hour format' : '12 hour format',
        auto_numeric_sku: generalData.auto_numeric_sku ?? true,
        allow_negative_stock: generalData.allow_negative_stock ?? false,
      }));
    }
  }, [generalData]);

  useEffect(() => {
    if (billData) {
      setBill((prev) => ({
        ...prev,
        ...billData,
        sections: billData.sections && billData.sections.length > 0 ? billData.sections : prev.sections,
      }));
    }
  }, [billData]);

  useEffect(() => {
    if (detectedLegacyDb && !migrationPath) {
      setMigrationPath(detectedLegacyDb);
    }
  }, [detectedLegacyDb]);

  // ── Mutations ───────────────────────────────────────────────────────────────
  const updateGeneralMutation = useMutation({
    mutationFn: (data) => tauriApi.updateGeneralSettings(data),
    onSuccess: (_data, variables) => {
      // Apply locale/currency formatting immediately across the app.
      applyGeneralSettings(variables);
      queryClient.invalidateQueries({ queryKey: ['general_settings'] });
      queryClient.invalidateQueries({ queryKey: ['next_sku'] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      showNotification('General settings saved successfully');
    },
    onError: (err) => showNotification(`Failed to save settings: ${err}`, 'error'),
  });

  const updateBillMutation = useMutation({
    mutationFn: (data) => tauriApi.updateBillSettings(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bill_settings'] });
      showNotification('Bill structure settings saved successfully');
    },
    onError: (err) => showNotification(`Failed to save bill settings: ${err}`, 'error'),
  });

  const testPrintMutation = useMutation({
    mutationFn: (printerName) => tauriApi.testPrint(printerName),
    onSuccess: () => {
      showNotification(`Test print sent successfully to ${general.default_printer_name || 'default printer'}`);
    },
    onError: (err) => {
      // Fallback to window.print if direct thermal print is not configured
      window.print();
      showNotification('Opened system print dialog for test receipt', 'info');
    },
  });

  const createBackupMutation = useMutation({
    mutationFn: () => tauriApi.createManualBackup(),
    onSuccess: (info) => {
      refreshBackupStatus();
      refreshBackupsList();
      showNotification(`Backup created successfully: ${info.filename}`);
    },
    onError: (err) => showNotification(`Failed to create backup: ${err}`, 'error'),
  });

  const restoreBackupMutation = useMutation({
    mutationFn: async (arg) => {
      if (typeof arg === 'object' && arg.bytes) {
        return tauriApi.restoreBackupFile(arg.filename, arg.bytes);
      }
      return tauriApi.restoreBackup(arg);
    },
    onSuccess: (msg) => {
      refreshBackupStatus();
      refreshBackupsList();
      queryClient.invalidateQueries();
      showNotification(msg, 'success');
    },
    onError: (err) => showNotification(`Restore failed: ${err}`, 'error'),
  });

  const migrationMutation = useMutation({
    mutationFn: (path) => tauriApi.importLegacyDatabase(path || null),
    onSuccess: (summary) => {
      setMigrationResult(summary);
      queryClient.invalidateQueries();
      showNotification('Legacy database migrated successfully');
    },
    onError: (err) => showNotification(`Migration failed: ${err}`, 'error'),
  });

  // ── Section Reordering & Options Handlers ───────────────────────────────────
  const moveSection = (index, delta) => {
    const newIndex = index + delta;
    if (newIndex < 0 || newIndex >= bill.sections.length) return;
    const updated = [...bill.sections];
    const [moved] = updated.splice(index, 1);
    updated.splice(newIndex, 0, moved);
    setBill({ ...bill, sections: updated });
  };

  const toggleSectionVisible = (id) => {
    const updated = bill.sections.map((sec) =>
      sec.id === id ? { ...sec, visible: !sec.visible } : sec
    );
    setBill({ ...bill, sections: updated });
  };

  const updateSectionOption = (sectionId, optionKey, value) => {
    const updated = bill.sections.map((sec) => {
      if (sec.id === sectionId) {
        return {
          ...sec,
          options: {
            ...(sec.options || {}),
            [optionKey]: value,
          },
        };
      }
      return sec;
    });

    // Also sync flat properties for backward compatibility
    let extra = {};
    if (sectionId === 'storeHeader') {
      if (optionKey === 'storeName') extra.store_name = value;
      if (optionKey === 'storeDetails') extra.store_details = value;
      if (optionKey === 'phone') extra.phone = value;
      if (optionKey === 'gst') extra.gst = value;
    }
    if (sectionId === 'itemsTable' && optionKey === 'showTax') {
      extra.show_tax = value;
    }
    if (sectionId === 'footer' && optionKey === 'text') {
      extra.footer_text = value;
    }

    setBill({ ...bill, ...extra, sections: updated });
  };

  // ── Password Form Submit & Mutation ────────────────────────────────────────
  const updatePasswordMutation = useMutation({
    mutationFn: ({ currentPassword, newPassword }) =>
      tauriApi.updatePassword({ currentPassword, newPassword }),
    onSuccess: (msg) => {
      setPasswordState({ currentPassword: '', newPassword: '', confirmPassword: '' });
      showNotification(msg || 'Password updated successfully!');
    },
    onError: (err) => {
      const msg = typeof err === 'string' ? err : err?.message || 'Failed to update password';
      showNotification(
        msg.replace(/^Unauthorized:\s*|^Validation error:\s*|^Forbidden:\s*/, ''),
        'error'
      );
    },
  });

  const handlePasswordSubmit = (e) => {
    e.preventDefault();
    if (!passwordState.currentPassword || !passwordState.newPassword || !passwordState.confirmPassword) {
      showNotification('Please fill in all password fields.', 'warning');
      return;
    }
    if (passwordState.newPassword.length < 4) {
      showNotification('New password must be at least 4 characters long.', 'warning');
      return;
    }
    if (passwordState.newPassword !== passwordState.confirmPassword) {
      showNotification('New passwords do not match.', 'warning');
      return;
    }

    updatePasswordMutation.mutate({
      currentPassword: passwordState.currentPassword,
      newPassword: passwordState.newPassword,
    });
  };

  // ── Sample data for Live Preview ───────────────────────────────────────────
  const sampleSale = {
    invoice_number: 'S26CH0000001',
    created_at: new Date().toISOString(),
    customer_name: 'Walk-in Customer',
    biller_name: 'Cashier',
    subtotal_paise: 25000,
    discount_paise: 2000,
    tax_paise: 1250,
    total_paise: 24250,
    paid_paise: 25000,
    payment_method: 'CASH',
  };

  const sampleItems = [
    { product_name: 'Cadbury Silk 150g', quantity: 2, unit_price_paise: 10000, discount_paise: 0, tax_rate: 5.0 },
    { product_name: 'Greeting Card Premium', quantity: 1, unit_price_paise: 5000, discount_paise: 2000, tax_rate: 18.0 },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-16 px-6 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20 shadow-sm">
            <Settings className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-slate-100">System Settings</h2>
            <p className="text-xs text-slate-400">
              Configure localization, bill layout, hardware printers, and database backups
            </p>
          </div>
        </div>

        {/* Tab Switcher */}
        <div className="flex bg-slate-900 border border-slate-800 rounded-xl p-1 text-xs">
          <button
            onClick={() => setActiveTab('general')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg font-semibold transition-all cursor-pointer ${
              activeTab === 'general' ? 'bg-emerald-500 text-slate-950 shadow-sm' : 'text-slate-400 hover:text-white'
            }`}
          >
            <Sliders className="w-3.5 h-3.5" />
            <span>General</span>
          </button>
          <button
            onClick={() => setActiveTab('bill')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg font-semibold transition-all cursor-pointer ${
              activeTab === 'bill' ? 'bg-emerald-500 text-slate-950 shadow-sm' : 'text-slate-400 hover:text-white'
            }`}
          >
            <Receipt className="w-3.5 h-3.5" />
            <span>Bill Structure</span>
          </button>
          <button
            onClick={() => setActiveTab('hardware')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg font-semibold transition-all cursor-pointer ${
              activeTab === 'hardware' ? 'bg-emerald-500 text-slate-950 shadow-sm' : 'text-slate-400 hover:text-white'
            }`}
          >
            <Printer className="w-3.5 h-3.5" />
            <span>Hardware & Printing</span>
          </button>
          <button
            onClick={() => setActiveTab('backup')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg font-semibold transition-all cursor-pointer relative ${
              activeTab === 'backup' ? 'bg-emerald-500 text-slate-950 shadow-sm' : 'text-slate-400 hover:text-white'
            }`}
          >
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Backup & Restore</span>
          </button>
        </div>
      </div>

      {/* Main Tab Content */}
      <div className="flex-1 overflow-y-auto p-6">
        {/* ══════════════════════════════════════════════════════════════════════
            TAB 1: GENERAL SETTINGS (Localization + Security / Password UI)
           ══════════════════════════════════════════════════════════════════════ */}
        {activeTab === 'general' && (
          <div className="max-w-4xl space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Localization Card */}
              <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-5 shadow-sm">
                <div className="flex items-center gap-2.5 pb-3 border-b border-slate-800">
                  <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                    <Sliders className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-slate-100">Localization</h3>
                    <p className="text-[11px] text-slate-400">Currencies, dates, and timestamp formats</p>
                  </div>
                </div>

                <div className="space-y-4 text-xs">
                  <div>
                    <label className="text-slate-300 font-medium block mb-1.5">Currency Symbol</label>
                    <input
                      type="text"
                      value={general.currency_symbol}
                      onChange={(e) => setGeneral({ ...general, currency_symbol: e.target.value })}
                      placeholder="e.g. ₹, $, €"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-slate-100 focus:border-emerald-500 focus:outline-none font-bold"
                    />
                    <span className="text-[11px] text-slate-500 mt-1 block">Displayed on POS bills and financial reports</span>
                  </div>

                  <div>
                    <label className="text-slate-300 font-medium block mb-1.5">Date Format</label>
                    <select
                      value={general.date_format}
                      onChange={(e) => setGeneral({ ...general, date_format: e.target.value })}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-slate-200 focus:border-emerald-500 focus:outline-none cursor-pointer"
                    >
                      <option value="DD-MM-YYYY">DD-MM-YYYY (e.g. 26-10-2026)</option>
                      <option value="MM-DD-YYYY">MM-DD-YYYY (e.g. 10-26-2026)</option>
                      <option value="YYYY-MM-DD">YYYY-MM-DD (e.g. 2026-10-26)</option>
                      <option value="Month Date, Year">Month Date, Year (e.g. Oct 26, 2026)</option>
                      <option value="Date Month, Year">Date Month, Year (e.g. 26 Oct, 2026)</option>
                    </select>
                  </div>

                  <div>
                    <label className="text-slate-300 font-medium block mb-1.5">Time Format</label>
                    <select
                      value={general.time_format}
                      onChange={(e) => setGeneral({ ...general, time_format: e.target.value })}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-slate-200 focus:border-emerald-500 focus:outline-none cursor-pointer"
                    >
                      <option value="12 hour format">12 hour format (e.g. 02:45 PM)</option>
                      <option value="24 hour format">24 hour format (e.g. 14:45)</option>
                    </select>
                  </div>
                </div>
              </div>

              {/* Security & Access Card (Password UI) */}
              <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-5 shadow-sm">
                <div className="flex items-center gap-2.5 pb-3 border-b border-slate-800">
                  <div className="p-2 rounded-lg bg-amber-500/10 text-amber-400 border border-amber-500/20">
                    <Lock className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-slate-100">Security & Access</h3>
                    <p className="text-[11px] text-slate-400">Admin password and access credentials</p>
                  </div>
                </div>

                <form onSubmit={handlePasswordSubmit} className="space-y-3.5 text-xs">
                  <div>
                    <label className="text-slate-300 font-medium block mb-1">Current Password</label>
                    <input
                      type="password"
                      value={passwordState.currentPassword}
                      onChange={(e) => setPasswordState({ ...passwordState, currentPassword: e.target.value })}
                      placeholder="Enter current password"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-slate-200 focus:border-amber-500 focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="text-slate-300 font-medium block mb-1">New Password</label>
                    <input
                      type="password"
                      value={passwordState.newPassword}
                      onChange={(e) => setPasswordState({ ...passwordState, newPassword: e.target.value })}
                      placeholder="Enter new password"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-slate-200 focus:border-amber-500 focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="text-slate-300 font-medium block mb-1">Confirm New Password</label>
                    <input
                      type="password"
                      value={passwordState.confirmPassword}
                      onChange={(e) => setPasswordState({ ...passwordState, confirmPassword: e.target.value })}
                      placeholder="Confirm new password"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-slate-200 focus:border-amber-500 focus:outline-none"
                    />
                  </div>

                  <div className="pt-2 flex justify-end">
                    <button
                      type="submit"
                      disabled={updatePasswordMutation.isPending}
                      className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 font-bold text-xs flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
                    >
                      {updatePasswordMutation.isPending ? (
                        <RefreshCw className="w-3.5 h-3.5 animate-spin text-emerald-400" />
                      ) : (
                        <Check className="w-3.5 h-3.5 text-emerald-400" />
                      )}
                      <span>Update Password</span>
                    </button>
                  </div>
                </form>
              </div>
            </div>

            {/* Store Information Card */}
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4 shadow-sm">
              <div className="flex items-center gap-2.5 pb-3 border-b border-slate-800">
                <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  <Settings className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-slate-100">Store Information</h3>
                  <p className="text-[11px] text-slate-400">Master business profile data used across the system</p>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                <div>
                  <label className="text-slate-300 font-medium block mb-1">Store Name</label>
                  <input
                    type="text"
                    value={general.store_name}
                    onChange={(e) => setGeneral({ ...general, store_name: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-slate-200 focus:border-emerald-500 focus:outline-none"
                  />
                </div>
                <div>
                  <label className="text-slate-300 font-medium block mb-1">Phone Number</label>
                  <input
                    type="text"
                    value={general.phone}
                    onChange={(e) => setGeneral({ ...general, phone: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono"
                  />
                </div>
                <div>
                  <label className="text-slate-300 font-medium block mb-1">Address / Location</label>
                  <input
                    type="text"
                    value={general.address}
                    onChange={(e) => setGeneral({ ...general, address: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-slate-200 focus:border-emerald-500 focus:outline-none"
                  />
                </div>
                <div>
                  <label className="text-slate-300 font-medium block mb-1">GSTIN / Tax Registration</label>
                  <input
                    type="text"
                    value={general.gstin}
                    onChange={(e) => setGeneral({ ...general, gstin: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-slate-200 focus:border-emerald-500 focus:outline-none font-mono"
                  />
                </div>
              </div>
            </div>

            {/* Inventory & Stock Behavior Card */}
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4 shadow-sm">
              <div className="flex items-center gap-2.5 pb-3 border-b border-slate-800">
                <div className="p-2 rounded-lg bg-sky-500/10 text-sky-400 border border-sky-500/20">
                  <Package className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-slate-100">Inventory & Stock Behavior</h3>
                  <p className="text-[11px] text-slate-400">SKU generation and stock guard rails</p>
                </div>
              </div>

              <div className="space-y-3 text-xs">
                <label
                  className={`flex items-start gap-3 p-3.5 rounded-xl border cursor-pointer transition-colors ${
                    general.auto_numeric_sku
                      ? 'bg-emerald-500/10 border-emerald-500/30'
                      : 'bg-slate-950/60 border-slate-800 hover:border-slate-700'
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={general.auto_numeric_sku}
                    onChange={(e) => setGeneral({ ...general, auto_numeric_sku: e.target.checked })}
                    className="w-4 h-4 mt-0.5 rounded border-slate-700 bg-slate-900 text-emerald-500 focus:ring-emerald-500 focus:ring-offset-slate-900 cursor-pointer"
                  />
                  <div className="space-y-1">
                    <span className="font-bold text-slate-200 block">Automatic Numerical SKU</span>
                    <span className="text-[11px] text-slate-400 block">
                      New products receive the next available numerical SKU automatically. The SKU field is
                      read-only. After importing legacy data, numbering continues from the highest existing SKU.
                    </span>
                  </div>
                </label>

                <label
                  className={`flex items-start gap-3 p-3.5 rounded-xl border cursor-pointer transition-colors ${
                    general.allow_negative_stock
                      ? 'bg-amber-500/10 border-amber-500/30'
                      : 'bg-slate-950/60 border-slate-800 hover:border-slate-700'
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={general.allow_negative_stock}
                    onChange={(e) => setGeneral({ ...general, allow_negative_stock: e.target.checked })}
                    className="w-4 h-4 mt-0.5 rounded border-slate-700 bg-slate-900 text-amber-500 focus:ring-amber-500 focus:ring-offset-slate-900 cursor-pointer"
                  />
                  <div className="space-y-1">
                    <span className="font-bold text-slate-200 block">
                      Allow Negative Stock (Ignore Stock Alerts)
                    </span>
                    <span className="text-[11px] text-slate-400 block">
                      Disables stock alerts, warnings and billing restrictions. Bills can be completed even
                      when stock reaches zero or below. Leave unchecked to enforce stock limits during billing.
                    </span>
                  </div>
                </label>
              </div>
            </div>

            {/* Save General Settings Button */}
            <div className="flex items-center gap-3 pt-2">
              <button
                type="button"
                onClick={() => updateGeneralMutation.mutate(general)}
                disabled={updateGeneralMutation.isPending}
                className="px-6 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-lg shadow-emerald-500/20 transition-all cursor-pointer"
              >
                {updateGeneralMutation.isPending ? (
                  <RefreshCw className="w-4 h-4 animate-spin" />
                ) : (
                  <Check className="w-4 h-4" />
                )}
                <span>Save General Settings</span>
              </button>
              <span className="text-xs text-slate-400">Changes will take effect immediately</span>
            </div>
          </div>
        )}

        {/* ══════════════════════════════════════════════════════════════════════
            TAB 2: BILL STRUCTURE (Layout, Configurable Sections, Live Preview)
           ══════════════════════════════════════════════════════════════════════ */}
        {activeTab === 'bill' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Left Column: Settings & Sections Editor (7 cols) */}
            <div className="lg:col-span-7 space-y-6">
              {/* Paper Format Card */}
              <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4 shadow-sm">
                <div>
                  <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
                    <Receipt className="w-4 h-4 text-emerald-400" /> Format Settings
                  </h3>
                  <p className="text-[11px] text-slate-400 mt-0.5">Configure thermal paper roll width and density</p>
                </div>

                <div className="text-xs">
                  <label className="text-slate-300 font-medium block mb-2">Paper Width</label>
                  <div className="grid grid-cols-2 gap-3 max-w-sm">
                    {['80mm', '58mm'].map((w) => (
                      <button
                        key={w}
                        type="button"
                        onClick={() => setBill({ ...bill, paper_width: w })}
                        className={`p-3 rounded-xl font-bold border transition-all flex items-center justify-center gap-2 cursor-pointer ${
                          bill.paper_width === w
                            ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/50 shadow-sm'
                            : 'bg-slate-950 text-slate-400 border-slate-800 hover:text-slate-200'
                        }`}
                      >
                        <Receipt className="w-4 h-4" />
                        <span>{w === '80mm' ? '3 inch (80 mm)' : '2 inch (58 mm)'}</span>
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              {/* Receipt Sections Card */}
              <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4 shadow-sm">
                <div>
                  <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
                    <FileSpreadsheet className="w-4 h-4 text-emerald-400" /> Receipt Sections
                  </h3>
                  <p className="text-[11px] text-slate-400 mt-0.5">
                    Toggle visibility and reorder sections to customize your receipt layout. Use arrows to change positions.
                  </p>
                </div>

                <div className="space-y-3">
                  {bill.sections.map((section, idx) => {
                    const isFirst = idx === 0;
                    const isLast = idx === bill.sections.length - 1;
                    const opts = section.options || {};

                    return (
                      <div
                        key={section.id}
                        className={`p-4 rounded-xl border transition-all ${
                          section.visible
                            ? 'bg-slate-950/70 border-slate-800'
                            : 'bg-slate-950/30 border-slate-850 opacity-60'
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <label className="flex items-center gap-3 cursor-pointer">
                            <input
                              type="checkbox"
                              checked={section.visible}
                              onChange={() => toggleSectionVisible(section.id)}
                              className="w-4 h-4 rounded border-slate-700 bg-slate-900 text-emerald-500 focus:ring-emerald-500 focus:ring-offset-slate-900 cursor-pointer"
                            />
                            <span className="text-xs font-bold text-slate-200">{section.name || section.id}</span>
                          </label>

                          <div className="flex items-center gap-1">
                            <button
                              type="button"
                              onClick={() => moveSection(idx, -1)}
                              disabled={isFirst}
                              className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 disabled:opacity-20 cursor-pointer"
                              title="Move section up"
                            >
                              <ArrowUp className="w-3.5 h-3.5" />
                            </button>
                            <button
                              type="button"
                              onClick={() => moveSection(idx, 1)}
                              disabled={isLast}
                              className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 disabled:opacity-20 cursor-pointer"
                              title="Move section down"
                            >
                              <ArrowDown className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </div>

                        {/* Section Options if visible */}
                        {section.visible && (
                          <div className="mt-3 pt-3 border-t border-slate-800/60 pl-6 space-y-3 text-xs">
                            {/* Alignment & Font Size options */}
                            <div className="flex flex-wrap gap-4 items-center">
                              {opts.alignment !== undefined && (
                                <div className="flex items-center gap-2">
                                  <span className="text-slate-400 text-[11px]">Alignment:</span>
                                  <select
                                    value={opts.alignment}
                                    onChange={(e) => updateSectionOption(section.id, 'alignment', e.target.value)}
                                    className="bg-slate-900 border border-slate-800 rounded-lg px-2 py-1 text-slate-200 text-xs focus:outline-none"
                                  >
                                    <option value="left">Left</option>
                                    <option value="center">Center</option>
                                    <option value="right">Right</option>
                                  </select>
                                </div>
                              )}

                              {opts.fontSize !== undefined && (
                                <div className="flex items-center gap-2">
                                  <span className="text-slate-400 text-[11px]">Font Size:</span>
                                  <select
                                    value={opts.fontSize}
                                    onChange={(e) => updateSectionOption(section.id, 'fontSize', e.target.value)}
                                    className="bg-slate-900 border border-slate-800 rounded-lg px-2 py-1 text-slate-200 text-xs focus:outline-none"
                                  >
                                    <option value="small">Small</option>
                                    <option value="medium">Medium</option>
                                    <option value="large">Large</option>
                                  </select>
                                </div>
                              )}
                            </div>

                            {/* Section-specific detailed fields */}
                            {section.id === 'storeHeader' && (
                              <div className="grid grid-cols-2 gap-2.5 pt-1">
                                <div>
                                  <span className="text-slate-400 text-[11px] block mb-0.5">Header Name:</span>
                                  <input
                                    type="text"
                                    value={opts.storeName ?? bill.store_name}
                                    onChange={(e) => updateSectionOption('storeHeader', 'storeName', e.target.value)}
                                    className="w-full bg-slate-900 border border-slate-800 rounded-lg px-2.5 py-1.5 text-slate-200 text-xs"
                                  />
                                </div>
                                <div>
                                  <span className="text-slate-400 text-[11px] block mb-0.5">Phone:</span>
                                  <input
                                    type="text"
                                    value={opts.phone ?? bill.phone}
                                    onChange={(e) => updateSectionOption('storeHeader', 'phone', e.target.value)}
                                    className="w-full bg-slate-900 border border-slate-800 rounded-lg px-2.5 py-1.5 text-slate-200 text-xs font-mono"
                                  />
                                </div>
                                <div className="col-span-2">
                                  <span className="text-slate-400 text-[11px] block mb-0.5">Address / Details:</span>
                                  <textarea
                                    rows="2"
                                    value={opts.storeDetails ?? bill.store_details}
                                    onChange={(e) => updateSectionOption('storeHeader', 'storeDetails', e.target.value)}
                                    className="w-full bg-slate-900 border border-slate-800 rounded-lg px-2.5 py-1.5 text-slate-200 text-xs"
                                  />
                                </div>
                              </div>
                            )}

                            {section.id === 'itemsTable' && (
                              <div className="pt-1">
                                <label className="flex items-center gap-2 cursor-pointer text-slate-300">
                                  <input
                                    type="checkbox"
                                    checked={opts.showTax ?? bill.show_tax}
                                    onChange={(e) => updateSectionOption('itemsTable', 'showTax', e.target.checked)}
                                    className="w-3.5 h-3.5 rounded border-slate-700 bg-slate-900 text-emerald-500 cursor-pointer"
                                  />
                                  <span className="text-[11px]">Show Tax% column on receipt items</span>
                                </label>
                              </div>
                            )}

                            {section.id === 'footer' && (
                              <div className="pt-1">
                                <span className="text-slate-400 text-[11px] block mb-0.5">Footer Message:</span>
                                <textarea
                                  rows="3"
                                  value={opts.text ?? bill.footer_text}
                                  onChange={(e) => updateSectionOption('footer', 'text', e.target.value)}
                                  className="w-full bg-slate-900 border border-slate-800 rounded-lg px-2.5 py-1.5 text-slate-200 text-xs focus:border-emerald-500 focus:outline-none resize-y"
                                  placeholder="e.g. Thank you for your visit!&#10;Terms & conditions apply"
                                />
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Save Bill Settings Action */}
              <div className="flex items-center gap-3 pt-1">
                <button
                  type="button"
                  onClick={() => updateBillMutation.mutate(bill)}
                  disabled={updateBillMutation.isPending}
                  className="px-6 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-lg shadow-emerald-500/20 transition-all cursor-pointer"
                >
                  {updateBillMutation.isPending ? (
                    <RefreshCw className="w-4 h-4 animate-spin" />
                  ) : (
                    <Check className="w-4 h-4" />
                  )}
                  <span>Save Bill Settings</span>
                </button>
                <span className="text-xs text-slate-400">Layout changes update live print output immediately</span>
              </div>
            </div>

            {/* Right Column: Live Thermal Receipt Preview (5 cols) */}
            <div className="lg:col-span-5 sticky top-6">
              <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4 shadow-sm">
                <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                  <div>
                    <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
                      <Printer className="w-4 h-4 text-emerald-400" /> Live Receipt Preview
                    </h3>
                    <p className="text-[11px] text-slate-400">Simulating {bill.paper_width} thermal printer tape</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => testPrintMutation.mutate(general.default_printer_name || null)}
                    className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold border border-slate-700 transition-colors flex items-center gap-1.5 cursor-pointer"
                  >
                    <Printer className="w-3.5 h-3.5 text-emerald-400" />
                    <span>Print Test</span>
                  </button>
                </div>

                <div className="p-4 bg-slate-950 rounded-xl border border-slate-800 flex justify-center overflow-x-auto">
                  <ReceiptPrinter
                    sale={sampleSale}
                    items={sampleItems}
                    billSettings={bill}
                    generalSettings={general}
                  />
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ══════════════════════════════════════════════════════════════════════
            TAB 3: HARDWARE & PRINTING (Printer Scanning, Default Selection, Tips)
           ══════════════════════════════════════════════════════════════════════ */}
        {activeTab === 'hardware' && (
          <div className="max-w-4xl space-y-6">
            {/* Receipt Printer Card */}
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-5 shadow-sm">
              <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                <div className="flex items-center gap-3">
                  <div className="p-2.5 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                    <Printer className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-slate-100">Receipt Printer</h3>
                    <p className="text-xs text-slate-400">Configure default printer for POS receipts and invoices</p>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={async () => {
                    try {
                      await refreshPrinters();
                      toast.success('Printer list refreshed');
                    } catch (err) {
                      toast.error(`Could not refresh printers: ${err}`, {
                        title: 'Printer scan failed',
                      });
                    }
                  }}
                  disabled={scanningPrinters}
                  className="px-3.5 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold border border-slate-700 transition-colors flex items-center gap-1.5 cursor-pointer"
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${scanningPrinters ? 'animate-spin text-emerald-400' : ''}`} />
                  <span>Refresh Printers</span>
                </button>
              </div>

              <div className="space-y-4 text-xs max-w-lg">
                <div>
                  <label className="text-slate-300 font-medium block mb-1.5">Default Printer</label>
                  <select
                    value={general.default_printer_name}
                    onChange={(e) => {
                      const updated = { ...general, default_printer_name: e.target.value };
                      setGeneral(updated);
                      updateGeneralMutation.mutate(updated);
                    }}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-slate-200 focus:border-emerald-500 focus:outline-none cursor-pointer"
                  >
                    <option value="">System Default Printer</option>
                    {printers.map((p) => (
                      <option key={p} value={p}>
                        {p}
                      </option>
                    ))}
                  </select>
                  <span className="text-[11px] text-slate-500 mt-1 block">
                    This printer will be selected for all direct print operations and bill printing
                  </span>
                </div>

                <div className="pt-2 flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => testPrintMutation.mutate(general.default_printer_name || null)}
                    disabled={testPrintMutation.isPending}
                    className="px-5 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-lg shadow-emerald-500/20 transition-all cursor-pointer"
                  >
                    {testPrintMutation.isPending ? (
                      <RefreshCw className="w-4 h-4 animate-spin" />
                    ) : (
                      <Printer className="w-4 h-4" />
                    )}
                    <span>Send Test Print</span>
                  </button>
                  <span className="text-xs text-slate-400">Verify printer connection and output quality</span>
                </div>
              </div>
            </div>

            {/* Hardware Information Card */}
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4 shadow-sm">
              <div className="flex items-center gap-2.5 pb-3 border-b border-slate-800">
                <div className="p-2 rounded-lg bg-sky-500/10 text-sky-400 border border-sky-500/20">
                  <Info className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-slate-100">Hardware Information</h3>
                  <p className="text-[11px] text-slate-400">Tips for connecting and configuring hardware devices</p>
                </div>
              </div>

              <div className="space-y-3 text-xs text-slate-300">
                <div className="flex items-start gap-3">
                  <div className="p-1 rounded bg-emerald-500/10 text-emerald-400 mt-0.5">
                    <Check className="w-3.5 h-3.5" />
                  </div>
                  <div>
                    <strong className="text-slate-200 block">Ensure printer is powered on and connected:</strong>
                    <span className="text-slate-400">Check USB or network cable and paper roll feed.</span>
                  </div>
                </div>

                <div className="flex items-start gap-3">
                  <div className="p-1 rounded bg-emerald-500/10 text-emerald-400 mt-0.5">
                    <Check className="w-3.5 h-3.5" />
                  </div>
                  <div>
                    <strong className="text-slate-200 block">Install manufacturer thermal printer drivers:</strong>
                    <span className="text-slate-400">
                      Standard ESC/POS thermal printers (TVS, Epson, Xprinter, Citizen) require drivers with zero margin
                      paper configurations for seamless cuts.
                    </span>
                  </div>
                </div>

                <div className="flex items-start gap-3">
                  <div className="p-1 rounded bg-emerald-500/10 text-emerald-400 mt-0.5">
                    <Check className="w-3.5 h-3.5" />
                  </div>
                  <div>
                    <strong className="text-slate-200 block">Use the Refresh button if newly plugged in:</strong>
                    <span className="text-slate-400">
                      If you connect a printer while the application is open, click Refresh to detect it immediately.
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ══════════════════════════════════════════════════════════════════════
            TAB 4: BACKUP & RESTORE (Daily Backups, Manual Backup, Folder, Restore)
           ══════════════════════════════════════════════════════════════════════ */}
        {activeTab === 'backup' && (
          <div className="max-w-4xl space-y-6">
            <input
              ref={restoreFileInputRef}
              type="file"
              accept=".db"
              className="hidden"
              onChange={async (e) => {
                const file = e.target.files?.[0];
                if (file) {
                  if (
                    confirm(
                      `Are you sure you want to restore from "${file.name}"?\n\n• Supports both native backups and Java app backups.\n• Database format is auto-detected and converted seamlessly.\n• An automated safety pre-restore backup will be created first.`
                    )
                  ) {
                    if (file.path) {
                      restoreBackupMutation.mutate(file.path);
                    } else {
                      try {
                        const buffer = await file.arrayBuffer();
                        const bytes = Array.from(new Uint8Array(buffer));
                        restoreBackupMutation.mutate({ filename: file.name, bytes });
                      } catch (err) {
                        showNotification('Failed to read file: ' + err.message, 'error');
                      }
                    }
                  }
                }
              }}
            />

            {/* Previous Java POS Database Auto-Detected Banner */}
            {detectedLegacyDb && (
              <div className="p-5 rounded-2xl bg-amber-500/10 border border-amber-500/30 space-y-3 shadow-sm">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <div className="p-2.5 rounded-xl bg-amber-500/20 text-amber-300">
                      <Database className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="text-sm font-bold text-amber-200">Previous Java POS Database Detected</h3>
                      <p className="text-xs text-amber-300/80">
                        Found local installation: <code className="font-mono text-[11px] bg-slate-950/60 px-1.5 py-0.5 rounded text-amber-100">{detectedLegacyDb}</code>
                      </p>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      if (confirm('Import all categories, products, customers, and sales history from your previous Java POS installation?')) {
                        migrationMutation.mutate(detectedLegacyDb);
                      }
                    }}
                    disabled={migrationMutation.isPending}
                    className="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-lg shadow-amber-500/20 transition-all shrink-0 cursor-pointer"
                  >
                    {migrationMutation.isPending ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Database className="w-3.5 h-3.5" />}
                    <span>Import Previous POS Data</span>
                  </button>
                </div>
                {migrationResult && (
                  <div className="pt-2 text-xs text-amber-200/90 font-medium border-t border-amber-500/20">
                    ✓ Successfully imported {migrationResult.products_imported} products, {migrationResult.categories_imported} categories, {migrationResult.customers_imported} customers, and {migrationResult.sales_imported} sales invoices.
                  </div>
                )}
              </div>
            )}

            {/* Automatic Daily Backups Card */}
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4 shadow-sm">
              <div className="flex items-center gap-3 pb-3 border-b border-slate-800">
                <div className="p-2.5 rounded-xl bg-sky-500/10 text-sky-400 border border-sky-500/20">
                  <ShieldCheck className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-slate-100">Automatic Daily Backups</h3>
                  <p className="text-xs text-slate-400">
                    A mandatory backup is created once every day while the app is running, and every time the app is closed
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                <div className="p-4 rounded-xl bg-slate-950 border border-slate-850">
                  <span className="text-slate-400 text-[11px] font-medium block mb-1">Backup Location</span>
                  <div className="font-mono text-slate-200 break-all select-all text-xs">
                    {backupStatus?.backup_location || 'Loading location...'}
                  </div>
                </div>

                <div className="p-4 rounded-xl bg-slate-950 border border-slate-850">
                  <span className="text-slate-400 text-[11px] font-medium block mb-1">Latest Backup</span>
                  <div className="font-mono text-slate-200 text-xs">
                    {backupStatus?.latest_backup ? (
                      <span className="text-emerald-400 font-semibold">
                        {backupStatus.latest_backup.filename} ({Math.round(backupStatus.latest_backup.size_bytes / 1024)} KB)
                      </span>
                    ) : (
                      <span className="text-slate-500">No backups created yet</span>
                    )}
                  </div>
                  {backupStatus?.latest_backup && (
                    <span className="text-[11px] text-slate-500 block mt-0.5">
                      {backupStatus.latest_backup.created_at
                        ? formatDateTime(backupStatus.latest_backup.created_at)
                        : backupStatus.latest_backup.created_at}
                    </span>
                  )}
                </div>
              </div>
            </div>

            {/* Manual Backup & Actions Card */}
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4 shadow-sm">
              <div className="flex items-center gap-3 pb-3 border-b border-slate-800">
                <div className="p-2.5 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  <HardDrive className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-slate-100">Manual Backup & Restore</h3>
                  <p className="text-xs text-slate-400">
                    Create an on-demand snapshot or restore from any backup file (native or Java app)
                  </p>
                </div>
              </div>

              <div className="flex flex-wrap gap-3 pt-1">
                <button
                  type="button"
                  onClick={() => createBackupMutation.mutate()}
                  disabled={createBackupMutation.isPending}
                  className="px-4 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-lg shadow-emerald-500/20 transition-all cursor-pointer"
                >
                  {createBackupMutation.isPending ? (
                    <RefreshCw className="w-4 h-4 animate-spin" />
                  ) : (
                    <HardDrive className="w-4 h-4" />
                  )}
                  <span>Create Backup Now</span>
                </button>

                <button
                  type="button"
                  onClick={async () => {
                    try {
                      await tauriApi.openBackupFolder();
                      toast.success('Opened backup folder');
                    } catch (err) {
                      toast.error(`Could not open backup folder: ${err}`, {
                        title: 'Folder unavailable',
                      });
                    }
                  }}
                  className="px-4 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-semibold text-xs border border-slate-700 transition-colors flex items-center gap-2 cursor-pointer"
                >
                  <FolderOpen className="w-4 h-4 text-emerald-400" />
                  <span>Open Backup Folder</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    if (restoreFileInputRef.current) {
                      restoreFileInputRef.current.value = '';
                      restoreFileInputRef.current.click();
                    }
                  }}
                  className="px-4 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-semibold text-xs border border-slate-700 transition-colors flex items-center gap-2 cursor-pointer"
                >
                  <Upload className="w-4 h-4 text-sky-400" />
                  <span>Restore From Backup File</span>
                </button>
              </div>
            </div>

            {/* Backups List Table */}
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 space-y-4 shadow-sm">
              <div className="flex items-center justify-between pb-2 border-b border-slate-800">
                <h4 className="text-xs font-bold text-slate-200 uppercase tracking-wider">
                  Available Backups ({backupsList.length})
                </h4>
                <button
                  type="button"
                  onClick={async () => {
                    try {
                      await Promise.all([refreshBackupStatus(), refreshBackupsList()]);
                      toast.info('Backup list refreshed');
                    } catch (err) {
                      toast.error(`Could not refresh backups: ${err}`, {
                        title: 'Refresh failed',
                      });
                    }
                  }}
                  className="text-xs text-slate-400 hover:text-white flex items-center gap-1 cursor-pointer"
                >
                  <RefreshCw className="w-3 h-3" />
                  <span>Refresh List</span>
                </button>
              </div>

              {backupsList.length === 0 ? (
                <div className="text-center py-8 text-xs text-slate-500">
                  No backup files found in directory. Click "Create Backup Now" to create your first snapshot.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead>
                      <tr className="border-b border-slate-800 text-slate-400">
                        <SortableTh sortKey="filename" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} className="pb-2 font-semibold">Filename</SortableTh>
                        <SortableTh sortKey="backup_type" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} className="pb-2 font-semibold">Type</SortableTh>
                        <SortableTh sortKey="created_at" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} className="pb-2 font-semibold">Date &amp; Time</SortableTh>
                        <SortableTh sortKey="size_bytes" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} align="right" className="pb-2 font-semibold">Size</SortableTh>
                        <th className="pb-2 font-semibold text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60 font-mono">
                      {sortedBackups.map((bk) => {
                        const isAuto = bk.backup_type === 'auto';
                        const isPre = bk.backup_type === 'pre-restore';
                        const isExit = bk.backup_type === 'exit';
                        const badgeCls = isAuto
                          ? 'bg-sky-500/15 text-sky-300 border-sky-500/30'
                          : isPre
                          ? 'bg-amber-500/15 text-amber-300 border-amber-500/30'
                          : isExit
                          ? 'bg-violet-500/15 text-violet-300 border-violet-500/30'
                          : 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30';

                        return (
                          <tr key={bk.filename} className="hover:bg-slate-800/30 transition-colors">
                            <td className="py-2.5 text-slate-200 font-semibold truncate max-w-xs">{bk.filename}</td>
                            <td className="py-2.5">
                              <span className={`px-2 py-0.5 rounded-full text-[10px] font-sans font-bold border ${badgeCls}`}>
                                {bk.backup_type}
                              </span>
                            </td>
                            <td className="py-2.5 text-slate-400 text-[11px]">{formatDateTime(bk.created_at)}</td>
                            <td className="py-2.5 text-right text-slate-300">{Math.round(bk.size_bytes / 1024)} KB</td>
                            <td className="py-2.5 text-right font-sans">
                              <button
                                type="button"
                                onClick={() => {
                                  if (confirm(`Restore database from '${bk.filename}'? A safety pre-restore backup will be created.`)) {
                                    restoreBackupMutation.mutate(bk.filename);
                                  }
                                }}
                                disabled={restoreBackupMutation.isPending}
                                className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-[11px] font-semibold border border-slate-700 transition-colors cursor-pointer"
                              >
                                Restore
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
