import { useState, useEffect, useRef, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
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
  Palette,
  Sun,
  Moon,
  Store,
  DollarSign,
  FileText,
  Users,
  Layers,
} from 'lucide-react';
import { tauriApi } from '../../lib/tauri';
import { toast } from '../../stores/toast.store';
import Select from '../../components/common/Select';
import { confirmDialog } from '../../stores/confirm.store';
import { useThemeStore } from '../../stores/theme.store';
import { applyGeneralSettings, formatDateTime } from '../../lib/formatters';
import { nextSortState, sortRows } from '../../lib/sort';
import SortableTh from '../../components/common/SortableTh';
import ReceiptPrinter from '../../components/receipt/ReceiptPrinter';
import ImportCsvButton from '../../components/common/ImportCsvButton';

export default function SettingsView() {
  const queryClient = useQueryClient();
  const { theme, setTheme } = useThemeStore();

  // Navigation state: 'general' | 'store_tax' | 'receipt' | 'printing' | 'backup' | 'migration' | 'security' | 'appearance'
  const [activeNav, setActiveNav] = useState('general');

  const [migrationPath, setMigrationPath] = useState('');
  const [migrationResult, setMigrationResult] = useState(null);
  const restoreFileInputRef = useRef(null);

  // ── General & Store Settings State ──────────────────────────────────────────
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

  // ── Password Form State ─────────────────────────────────────────────────────
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

  // ── Notifications ───────────────────────────────────────────────────────────
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
    enabled: activeNav === 'printing',
  });

  const { data: backupStatus, refetch: refreshBackupStatus } = useQuery({
    queryKey: ['backup_status'],
    queryFn: () => tauriApi.getBackupStatus(),
    enabled: activeNav === 'backup',
  });

  const { data: backupsList = [], refetch: refreshBackupsList } = useQuery({
    queryKey: ['backups_list'],
    queryFn: () => tauriApi.listBackups(),
    enabled: activeNav === 'backup',
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
      applyGeneralSettings(variables);
      queryClient.invalidateQueries({ queryKey: ['general_settings'] });
      queryClient.invalidateQueries({ queryKey: ['next_sku'] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      showNotification('Settings saved successfully');
    },
    onError: (err) => showNotification(`Failed to save settings: ${err}`, 'error'),
  });

  const updateBillMutation = useMutation({
    mutationFn: (data) => tauriApi.updateBillSettings(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bill_settings'] });
      showNotification('Receipt settings saved successfully');
    },
    onError: (err) => showNotification(`Failed to save receipt settings: ${err}`, 'error'),
  });

  const testPrintMutation = useMutation({
    mutationFn: (printerName) => tauriApi.testPrint(printerName),
    onSuccess: () => {
      showNotification(`Test print sent to ${general.default_printer_name || 'default printer'}`);
    },
    onError: () => {
      window.print();
      showNotification('Opened system print dialog for test receipt', 'info');
    },
  });

  const createBackupMutation = useMutation({
    mutationFn: () => tauriApi.createManualBackup(),
    onSuccess: (info) => {
      refreshBackupStatus();
      refreshBackupsList();
      showNotification(`Backup snapshot created: ${info.filename}`);
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

  // Sample data for Live Receipt Preview
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

  // Navigation Items
  const navItems = [
    { id: 'general', label: 'General', icon: Sliders, description: 'Store identity and date formats' },
    { id: 'store_tax', label: 'Store & Tax', icon: Store, description: 'Currency, GSTIN, and stock rules' },
    { id: 'receipt', label: 'Receipt Designer', icon: Receipt, description: 'Thermal layout and print design' },
    { id: 'printing', label: 'Devices & Printing', icon: Printer, description: 'Printer selection and testing' },
    { id: 'backup', label: 'Backup & Restore', icon: ShieldCheck, description: 'Safety snapshots and data recovery' },
    { id: 'migration', label: 'Imports & Migration', icon: Database, description: 'CSV imports and Java POS conversion' },
    { id: 'security', label: 'Security & Access', icon: Lock, description: 'Master administrator credentials' },
    { id: 'appearance', label: 'Appearance', icon: Palette, description: 'Theme mode and visual display' },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-canvas text-content overflow-hidden">
      {/* Settings Top Header */}
      <div className="h-14 px-6 border-b border-border bg-surface flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-brand-subtle text-brand flex items-center justify-center border border-brand/20">
            <Sliders className="w-4 h-4" />
          </div>
          <div>
            <h1 className="text-sm font-semibold text-content">System Settings</h1>
            <p className="text-xs text-content-secondary">
              Terminal configuration, receipt layout, peripheral hardware, and database maintenance
            </p>
          </div>
        </div>
      </div>

      {/* Main Settings Body: Left Vertical Navigation + Right Content Panel */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left Vertical Navigation Sidebar (240px) */}
        <aside className="w-60 border-r border-border bg-surface flex flex-col p-3 space-y-1 overflow-y-auto shrink-0">
          <div className="px-3 py-2 text-[11px] font-semibold text-content-muted uppercase tracking-wider">
            Configuration
          </div>
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeNav === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setActiveNav(item.id)}
                className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-xs font-medium transition-colors text-left ${
                  isActive
                    ? 'bg-brand text-white shadow-sm'
                    : 'text-content-secondary hover:text-content hover:bg-surface-secondary'
                }`}
              >
                <Icon className={`w-4 h-4 shrink-0 ${isActive ? 'text-white' : 'text-content-muted'}`} />
                <div className="truncate">
                  <div className="leading-tight">{item.label}</div>
                </div>
              </button>
            );
          })}
        </aside>

        {/* Right Active Content Panel */}
        <main className="flex-1 overflow-y-auto p-8">
          {/* ══════════════════════════════════════════════════════════════════════
              NAV 1: GENERAL
             ══════════════════════════════════════════════════════════════════════ */}
          {activeNav === 'general' && (
            <div className="max-w-3xl space-y-6">
              <div>
                <h2 className="text-base font-semibold text-content">General Settings</h2>
                <p className="text-xs text-content-secondary">
                  Configure store identity, contact details, and display formats
                </p>
              </div>

              {/* Store Identity */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <h3 className="text-sm font-semibold text-content">Store Profile</h3>
                <div className="space-y-4 text-xs">
                  <div>
                    <label className="text-content font-medium block mb-1">Store / Business Name</label>
                    <input
                      type="text"
                      value={general.store_name}
                      onChange={(e) => setGeneral({ ...general, store_name: e.target.value })}
                      placeholder="e.g. Acme Retailers"
                      className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none"
                    />
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label className="text-content font-medium block mb-1">Phone Number</label>
                      <input
                        type="text"
                        value={general.phone}
                        onChange={(e) => setGeneral({ ...general, phone: e.target.value })}
                        placeholder="e.g. +91 9876543210"
                        className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none font-mono"
                      />
                    </div>
                    <div>
                      <label className="text-content font-medium block mb-1">Address / Location</label>
                      <input
                        type="text"
                        value={general.address}
                        onChange={(e) => setGeneral({ ...general, address: e.target.value })}
                        placeholder="e.g. Shop #4, Commercial Complex"
                        className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none"
                      />
                    </div>
                  </div>
                </div>
              </div>

              {/* Date & Time Formats */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <h3 className="text-sm font-semibold text-content">Date & Time Formats</h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                  <div>
                    <label className="text-content font-medium block mb-1.5">Date Display Format</label>
                    <Select
                      value={general.date_format}
                      onChange={(val) => setGeneral({ ...general, date_format: val })}
                      options={[
                        { value: 'DD-MM-YYYY', label: 'DD-MM-YYYY (e.g. 26-10-2026)' },
                        { value: 'MM-DD-YYYY', label: 'MM-DD-YYYY (e.g. 10-26-2026)' },
                        { value: 'YYYY-MM-DD', label: 'YYYY-MM-DD (e.g. 2026-10-26)' },
                        { value: 'Month Date, Year', label: 'Month Date, Year (e.g. Oct 26, 2026)' },
                        { value: 'Date Month, Year', label: 'Date Month, Year (e.g. 26 Oct, 2026)' },
                      ]}
                    />
                  </div>

                  <div>
                    <label className="text-content font-medium block mb-1.5">Time Display Format</label>
                    <Select
                      value={general.time_format}
                      onChange={(val) => setGeneral({ ...general, time_format: val })}
                      options={[
                        { value: '12 hour format', label: '12 hour format (e.g. 02:45 PM)' },
                        { value: '24 hour format', label: '24 hour format (e.g. 14:45)' },
                      ]}
                    />
                  </div>
                </div>
              </div>

              {/* Save CTA */}
              <div className="flex items-center gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => updateGeneralMutation.mutate(general)}
                  disabled={updateGeneralMutation.isPending}
                  className="px-5 py-2.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-medium text-xs flex items-center gap-2 transition-colors cursor-pointer"
                >
                  {updateGeneralMutation.isPending ? (
                    <RefreshCw className="w-4 h-4 animate-spin" />
                  ) : (
                    <Check className="w-4 h-4" />
                  )}
                  <span>Save General Settings</span>
                </button>
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════════
              NAV 2: STORE & TAX
             ══════════════════════════════════════════════════════════════════════ */}
          {activeNav === 'store_tax' && (
            <div className="max-w-3xl space-y-6">
              <div>
                <h2 className="text-base font-semibold text-content">Store & Tax Settings</h2>
                <p className="text-xs text-content-secondary">
                  Tax registration, currency configuration, and inventory behavior controls
                </p>
              </div>

              {/* Currency & Tax Registration */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <h3 className="text-sm font-semibold text-content">Currency & GSTIN</h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                  <div>
                    <label className="text-content font-medium block mb-1">Currency Symbol</label>
                    <input
                      type="text"
                      value={general.currency_symbol}
                      onChange={(e) => setGeneral({ ...general, currency_symbol: e.target.value })}
                      placeholder="e.g. ₹, $, €"
                      className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none font-medium"
                    />
                    <span className="text-[11px] text-content-muted mt-1 block">
                      Displayed across POS checkout and financial reports
                    </span>
                  </div>

                  <div>
                    <label className="text-content font-medium block mb-1">GSTIN / Tax ID</label>
                    <input
                      type="text"
                      value={general.gstin}
                      onChange={(e) => setGeneral({ ...general, gstin: e.target.value })}
                      placeholder="e.g. 22AAAAA0000A1Z5"
                      className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none font-mono"
                    />
                    <span className="text-[11px] text-content-muted mt-1 block">
                      Printed on customer receipts and tax invoices
                    </span>
                  </div>
                </div>
              </div>

              {/* Inventory Guard Rails */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <h3 className="text-sm font-semibold text-content">Inventory Behavior</h3>
                <div className="space-y-3 text-xs">
                  <label
                    className={`flex items-start gap-3 p-3.5 rounded-lg border cursor-pointer transition-colors ${
                      general.auto_numeric_sku
                        ? 'bg-brand-subtle border-brand/40'
                        : 'bg-surface-secondary border-border hover:border-border-strong'
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={general.auto_numeric_sku}
                      onChange={(e) => setGeneral({ ...general, auto_numeric_sku: e.target.checked })}
                      className="w-4 h-4 mt-0.5 rounded border-border-strong text-brand focus:ring-brand cursor-pointer"
                    />
                    <div className="space-y-1">
                      <span className="font-semibold text-content block">Automatic Numerical SKU</span>
                      <span className="text-content-secondary block">
                        New products automatically receive the next sequential numerical SKU. After importing legacy data, numbering continues from the highest existing SKU.
                      </span>
                    </div>
                  </label>

                  <label
                    className={`flex items-start gap-3 p-3.5 rounded-lg border cursor-pointer transition-colors ${
                      general.allow_negative_stock
                        ? 'bg-warning/10 border-warning/40'
                        : 'bg-surface-secondary border-border hover:border-border-strong'
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={general.allow_negative_stock}
                      onChange={(e) => setGeneral({ ...general, allow_negative_stock: e.target.checked })}
                      className="w-4 h-4 mt-0.5 rounded border-border-strong text-warning focus:ring-warning cursor-pointer"
                    />
                    <div className="space-y-1">
                      <span className="font-semibold text-content block">
                        Allow Negative Stock (Ignore Stock Limits)
                      </span>
                      <span className="text-content-secondary block">
                        Allows cashiers to bill items even if current stock is zero or negative. Leave unchecked to prevent sales when stock is depleted.
                      </span>
                    </div>
                  </label>
                </div>
              </div>

              {/* Save CTA */}
              <div className="flex items-center gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => updateGeneralMutation.mutate(general)}
                  disabled={updateGeneralMutation.isPending}
                  className="px-5 py-2.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-medium text-xs flex items-center gap-2 transition-colors cursor-pointer"
                >
                  {updateGeneralMutation.isPending ? (
                    <RefreshCw className="w-4 h-4 animate-spin" />
                  ) : (
                    <Check className="w-4 h-4" />
                  )}
                  <span>Save Store &amp; Tax Settings</span>
                </button>
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════════
              NAV 3: RECEIPT DESIGNER (Side-by-side: Settings Left, Preview Right)
             ══════════════════════════════════════════════════════════════════════ */}
          {activeNav === 'receipt' && (
            <div className="grid grid-cols-1 xl:grid-cols-12 gap-8 items-start">
              {/* Left Column: Receipt Controls (7 cols) */}
              <div className="xl:col-span-7 space-y-6">
                <div>
                  <h2 className="text-base font-semibold text-content">Receipt Designer</h2>
                  <p className="text-xs text-content-secondary">
                    Configure thermal paper width, rearrange receipt sections, and preview live
                  </p>
                </div>

                {/* Paper Roll Width */}
                <div className="p-6 rounded-xl bg-surface border border-border space-y-3">
                  <h3 className="text-xs font-semibold text-content uppercase tracking-wider">
                    Paper Roll Width
                  </h3>
                  <div className="grid grid-cols-2 gap-3 max-w-sm">
                    {['80mm', '58mm'].map((w) => (
                      <button
                        key={w}
                        type="button"
                        onClick={() => setBill({ ...bill, paper_width: w })}
                        className={`p-3 rounded-lg font-medium border text-xs flex items-center justify-center gap-2 transition-colors ${
                          bill.paper_width === w
                            ? 'bg-brand text-white border-brand shadow-sm'
                            : 'bg-surface-secondary text-content-secondary border-border hover:text-content'
                        }`}
                      >
                        <Receipt className="w-4 h-4" />
                        <span>{w === '80mm' ? '3 inch (80 mm)' : '2 inch (58 mm)'}</span>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Section Reordering & Options */}
                <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                  <div>
                    <h3 className="text-xs font-semibold text-content uppercase tracking-wider">
                      Receipt Sections
                    </h3>
                    <p className="text-xs text-content-secondary mt-1">
                      Toggle section visibility and adjust position using up/down arrows
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
                          className={`p-3.5 rounded-lg border transition-colors ${
                            section.visible
                              ? 'bg-surface-secondary border-border'
                              : 'bg-surface border-border opacity-60'
                          }`}
                        >
                          <div className="flex items-center justify-between">
                            <label className="flex items-center gap-2.5 cursor-pointer">
                              <input
                                type="checkbox"
                                checked={section.visible}
                                onChange={() => toggleSectionVisible(section.id)}
                                className="w-4 h-4 rounded border-border-strong text-brand focus:ring-brand cursor-pointer"
                              />
                              <span className="text-xs font-semibold text-content">{section.name}</span>
                            </label>

                            <div className="flex items-center gap-1">
                              <button
                                type="button"
                                disabled={isFirst}
                                onClick={() => moveSection(idx, -1)}
                                className="p-1 rounded hover:bg-surface border border-transparent hover:border-border text-content-secondary hover:text-content disabled:opacity-30 disabled:pointer-events-none"
                                title="Move up"
                              >
                                <ArrowUp className="w-3.5 h-3.5" />
                              </button>
                              <button
                                type="button"
                                disabled={isLast}
                                onClick={() => moveSection(idx, 1)}
                                className="p-1 rounded hover:bg-surface border border-transparent hover:border-border text-content-secondary hover:text-content disabled:opacity-30 disabled:pointer-events-none"
                                title="Move down"
                              >
                                <ArrowDown className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          </div>

                          {/* Contextual Section Options */}
                          {section.visible && (
                            <div className="mt-3 pt-3 border-t border-border grid grid-cols-2 gap-3 text-xs">
                              {opts.alignment !== undefined && (
                                <div>
                                  <label className="text-[11px] text-content-secondary block mb-1">
                                    Alignment
                                  </label>
                                  <select
                                    value={opts.alignment}
                                    onChange={(e) =>
                                      updateSectionOption(section.id, 'alignment', e.target.value)
                                    }
                                    className="w-full bg-surface border border-border-strong rounded p-1.5 text-content"
                                  >
                                    <option value="left">Left</option>
                                    <option value="center">Center</option>
                                    <option value="right">Right</option>
                                  </select>
                                </div>
                              )}

                              {opts.fontSize !== undefined && (
                                <div>
                                  <label className="text-[11px] text-content-secondary block mb-1">
                                    Font Size
                                  </label>
                                  <select
                                    value={opts.fontSize}
                                    onChange={(e) =>
                                      updateSectionOption(section.id, 'fontSize', e.target.value)
                                    }
                                    className="w-full bg-surface border border-border-strong rounded p-1.5 text-content"
                                  >
                                    <option value="small">Compact</option>
                                    <option value="medium">Standard</option>
                                    <option value="large">Prominent</option>
                                  </select>
                                </div>
                              )}

                              {section.id === 'itemsTable' && (
                                <div className="col-span-2">
                                  <label className="flex items-center gap-2 cursor-pointer pt-1">
                                    <input
                                      type="checkbox"
                                      checked={opts.showTax ?? false}
                                      onChange={(e) =>
                                        updateSectionOption(section.id, 'showTax', e.target.checked)
                                      }
                                      className="w-3.5 h-3.5 rounded border-border-strong text-brand focus:ring-brand cursor-pointer"
                                    />
                                    <span className="text-[11px] text-content-secondary">
                                      Print tax rate column on receipt
                                    </span>
                                  </label>
                                </div>
                              )}

                              {section.id === 'footer' && (
                                <div className="col-span-2">
                                  <label className="text-[11px] text-content-secondary block mb-1">
                                    Footer Message Text
                                  </label>
                                  <input
                                    type="text"
                                    value={opts.text || ''}
                                    onChange={(e) =>
                                      updateSectionOption(section.id, 'text', e.target.value)
                                    }
                                    placeholder="e.g. Thank you for your business!"
                                    className="w-full bg-surface border border-border-strong rounded p-2 text-content text-xs"
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

                {/* Save CTA */}
                <div className="flex items-center gap-3 pt-2">
                  <button
                    type="button"
                    onClick={() => updateBillMutation.mutate(bill)}
                    disabled={updateBillMutation.isPending}
                    className="px-5 py-2.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-medium text-xs flex items-center gap-2 transition-colors cursor-pointer"
                  >
                    {updateBillMutation.isPending ? (
                      <RefreshCw className="w-4 h-4 animate-spin" />
                    ) : (
                      <Check className="w-4 h-4" />
                    )}
                    <span>Save Receipt Settings</span>
                  </button>
                </div>
              </div>

              {/* Right Column: Thermal Paper Preview (5 cols) */}
              <div className="xl:col-span-5 sticky top-6">
                <div className="p-4 rounded-xl bg-surface border border-border space-y-3">
                  <div className="flex items-center justify-between">
                    <h3 className="text-xs font-semibold text-content uppercase tracking-wider">
                      Thermal Preview ({bill.paper_width})
                    </h3>
                    <span className="text-[11px] text-content-muted">Sample Transaction</span>
                  </div>

                  {/* Simulated White Paper Surface with drop shadow */}
                  <div className="bg-neutral-100 dark:bg-neutral-900 p-4 rounded-lg flex justify-center border border-border overflow-x-auto">
                    <div className="bg-white text-black font-mono p-4 shadow-md border border-neutral-300">
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
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════════
              NAV 4: DEVICES & PRINTING
             ══════════════════════════════════════════════════════════════════════ */}
          {activeNav === 'printing' && (
            <div className="max-w-3xl space-y-6">
              <div>
                <h2 className="text-base font-semibold text-content">Devices &amp; Printing</h2>
                <p className="text-xs text-content-secondary">
                  Detected hardware printers and receipt output routing
                </p>
              </div>

              {/* Status Banner */}
              <div className="p-4 rounded-xl bg-surface border border-border flex items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-lg bg-brand-subtle text-brand flex items-center justify-center border border-brand/20">
                    <Printer className="w-5 h-5" />
                  </div>
                  <div>
                    <div className="text-xs font-semibold text-content">
                      {printers.length > 0 ? `${printers.length} Printer(s) Detected` : 'No Printers Detected'}
                    </div>
                    <p className="text-[11px] text-content-secondary">
                      {general.default_printer_name
                        ? `Active Default: ${general.default_printer_name}`
                        : 'Using OS default print routing'}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`w-2.5 h-2.5 rounded-full ${printers.length > 0 ? 'bg-emerald-500' : 'bg-amber-500'}`} />
                  <span className="text-xs text-content-secondary font-medium">
                    {printers.length > 0 ? 'Hardware Ready' : 'Check USB / Cable'}
                  </span>
                </div>
              </div>

              {/* Hardware Selection */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <div className="flex items-center justify-between pb-3 border-b border-border">
                  <h3 className="text-sm font-semibold text-content">Connected Printers</h3>
                  <button
                    type="button"
                    onClick={() => refreshPrinters()}
                    disabled={scanningPrinters}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface-secondary border border-border text-content-secondary hover:text-content text-xs font-medium transition-colors"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 text-brand ${scanningPrinters ? 'animate-spin' : ''}`} />
                    <span>Scan Printers</span>
                  </button>
                </div>

                <div className="space-y-4 text-xs">
                  <div>
                    <label className="text-content font-medium block mb-1.5">Default Thermal Printer</label>
                    <Select
                      value={general.default_printer_name}
                      onChange={(val) => setGeneral({ ...general, default_printer_name: val })}
                      placeholder="-- Use OS Default Printer --"
                      options={[
                        { value: '', label: '-- Use OS Default Printer --' },
                        ...printers.map((p) => ({
                          value: p.name,
                          label: `${p.name} ${p.is_default ? '(System Default)' : ''}`,
                        })),
                      ]}
                    />
                    <span className="text-[11px] text-content-muted mt-1 block">
                      Receipts printed during POS checkout will be automatically routed to this printer
                    </span>
                  </div>

                  <div className="pt-2 flex flex-wrap items-center gap-3">
                    <button
                      type="button"
                      onClick={() => updateGeneralMutation.mutate(general)}
                      disabled={updateGeneralMutation.isPending}
                      className="px-5 py-2.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-medium text-xs flex items-center gap-2 transition-colors cursor-pointer"
                    >
                      {updateGeneralMutation.isPending ? (
                        <RefreshCw className="w-4 h-4 animate-spin" />
                      ) : (
                        <Check className="w-4 h-4" />
                      )}
                      <span>Save Default Printer</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => testPrintMutation.mutate(general.default_printer_name || null)}
                      disabled={testPrintMutation.isPending}
                      className="px-5 py-2.5 rounded-lg bg-surface-secondary border border-border hover:bg-surface-tertiary text-content font-medium text-xs flex items-center gap-2 transition-colors cursor-pointer"
                    >
                      {testPrintMutation.isPending ? (
                        <RefreshCw className="w-4 h-4 animate-spin text-brand" />
                      ) : (
                        <Printer className="w-4 h-4 text-brand" />
                      )}
                      <span>Send Test Print</span>
                    </button>
                    <span className="text-xs text-content-secondary">
                      Verifies ESC/POS driver connection and line feed
                    </span>
                  </div>
                </div>
              </div>

              {/* Hardware Tips */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-3 text-xs text-content-secondary">
                <h3 className="text-sm font-semibold text-content flex items-center gap-2">
                  <Info className="w-4 h-4 text-brand" /> Thermal Printer Guidelines
                </h3>
                <ul className="space-y-2 list-disc pl-4 leading-relaxed">
                  <li>ESC/POS thermal printers (TVS, Epson, Xprinter, Rongta) connect seamlessly over USB or Network.</li>
                  <li>Ensure zero-margin paper settings are selected in the printer driver properties for smooth continuous cutting.</li>
                  <li>If plugging in a new printer via USB, click "Scan Printers" above to register it immediately.</li>
                </ul>
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════════
              NAV 5: BACKUP & RESTORE
             ══════════════════════════════════════════════════════════════════════ */}
          {activeNav === 'backup' && (
            <div className="max-w-3xl space-y-6">
              <input
                ref={restoreFileInputRef}
                type="file"
                accept=".db"
                className="hidden"
                onChange={async (e) => {
                  const file = e.target.files?.[0];
                  e.target.value = '';
                  if (!file) return;

                  const { confirmed } = await confirmDialog({
                    title: 'Restore Database',
                    message: `Are you sure you want to restore from "${file.name}"?`,
                    details: [
                      'Supports both native PicoPOSSUM backups and Java app database files.',
                      'An automatic safety pre-restore backup will be created first.',
                    ],
                    confirmLabel: 'Restore Database',
                    tone: 'warning',
                  });
                  if (!confirmed) return;

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
                }}
              />

              <div>
                <h2 className="text-base font-semibold text-content">Backup &amp; Data Recovery</h2>
                <p className="text-xs text-content-secondary">
                  Automated snapshots, on-demand archive backups, and safe point-in-time restore
                </p>
              </div>

              {/* Status & Manual Action */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-border">
                  <div>
                    <h3 className="text-sm font-semibold text-content">Database Maintenance</h3>
                    <p className="text-xs text-content-secondary">
                      Mandatory backups occur on daily launch and app close
                    </p>
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => createBackupMutation.mutate()}
                      disabled={createBackupMutation.isPending}
                      className="px-4 py-2 rounded-lg bg-brand hover:bg-brand-hover text-white font-medium text-xs flex items-center gap-1.5 transition-colors cursor-pointer"
                    >
                      {createBackupMutation.isPending ? (
                        <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                      ) : (
                        <HardDrive className="w-3.5 h-3.5" />
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
                          toast.error(`Could not open backup folder: ${err}`);
                        }
                      }}
                      className="px-3 py-2 rounded-lg bg-surface-secondary border border-border text-content-secondary hover:text-content font-medium text-xs flex items-center gap-1.5 transition-colors"
                      title="Open local folder"
                    >
                      <FolderOpen className="w-3.5 h-3.5 text-content-muted" />
                      <span>Open Folder</span>
                    </button>
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                  <div className="p-3.5 rounded-lg bg-surface-secondary border border-border">
                    <span className="text-[11px] text-content-muted font-medium block mb-1">
                      Backup Storage Path
                    </span>
                    <div className="font-mono text-content text-xs break-all select-all">
                      {backupStatus?.backup_location || 'Loading location...'}
                    </div>
                  </div>

                  <div className="p-3.5 rounded-lg bg-surface-secondary border border-border">
                    <span className="text-[11px] text-content-muted font-medium block mb-1">
                      Latest Automated Snapshot
                    </span>
                    <div className="text-content font-medium text-xs">
                      {backupStatus?.latest_backup ? (
                        <span className="text-success font-semibold">
                          {backupStatus.latest_backup.filename} (
                          {Math.round(backupStatus.latest_backup.size_bytes / 1024)} KB)
                        </span>
                      ) : (
                        <span className="text-content-muted">No snapshots created yet</span>
                      )}
                    </div>
                    {backupStatus?.latest_backup?.created_at && (
                      <span className="text-[11px] text-content-muted block mt-0.5">
                        {formatDateTime(backupStatus.latest_backup.created_at)}
                      </span>
                    )}
                  </div>
                </div>
              </div>

              {/* Available Backups Table */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <div className="flex items-center justify-between pb-2 border-b border-border">
                  <h3 className="text-xs font-semibold text-content uppercase tracking-wider">
                    Available Backups ({backupsList.length})
                  </h3>
                  <button
                    type="button"
                    onClick={() => {
                      if (restoreFileInputRef.current) {
                        restoreFileInputRef.current.value = '';
                        restoreFileInputRef.current.click();
                      }
                    }}
                    className="text-xs text-brand hover:underline font-medium flex items-center gap-1"
                  >
                    <Upload className="w-3.5 h-3.5" />
                    <span>Upload External .db File</span>
                  </button>
                </div>

                {backupsList.length === 0 ? (
                  <div className="text-center py-8 text-xs text-content-muted">
                    No backup files found. Click "Create Backup Now" to record a snapshot.
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs">
                      <thead>
                        <tr className="border-b border-border text-content-secondary font-medium">
                          <SortableTh sortKey="filename" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} className="pb-2">Filename</SortableTh>
                          <SortableTh sortKey="backup_type" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} className="pb-2">Type</SortableTh>
                          <SortableTh sortKey="created_at" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} className="pb-2">Date</SortableTh>
                          <SortableTh sortKey="size_bytes" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} align="right" className="pb-2">Size</SortableTh>
                          <th className="pb-2 font-medium text-right">Action</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border">
                        {sortedBackups.map((bk) => (
                          <tr key={bk.filename} className="hover:bg-surface-secondary transition-colors">
                            <td className="py-2.5 font-medium text-content truncate max-w-xs">{bk.filename}</td>
                            <td className="py-2.5">
                              <span className="px-2 py-0.5 rounded text-[11px] font-medium bg-surface-secondary text-content-secondary border border-border">
                                {bk.backup_type}
                              </span>
                            </td>
                            <td className="py-2.5 text-content-secondary text-xs">{formatDateTime(bk.created_at)}</td>
                            <td className="py-2.5 text-right text-content financial-value">{Math.round(bk.size_bytes / 1024)} KB</td>
                            <td className="py-2.5 text-right">
                              <button
                                type="button"
                                onClick={async () => {
                                  const { confirmed } = await confirmDialog({
                                    title: 'Restore Database',
                                    message: `Restore the database from "${bk.filename}"?`,
                                    details: [
                                      'A safety pre-restore backup will be created automatically before current data is replaced.',
                                    ],
                                    confirmLabel: 'Restore',
                                    tone: 'warning',
                                  });
                                  if (confirmed) {
                                    restoreBackupMutation.mutate(bk.filename);
                                  }
                                }}
                                disabled={restoreBackupMutation.isPending}
                                className="px-3 py-1 rounded-lg bg-surface border border-border hover:bg-surface-secondary text-content text-xs font-medium transition-colors"
                              >
                                Restore
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════════
              NAV 6: IMPORTS & MIGRATION
             ══════════════════════════════════════════════════════════════════════ */}
          {activeNav === 'migration' && (
            <div className="max-w-3xl space-y-6">
              <div>
                <h2 className="text-base font-semibold text-content">Imports &amp; Migration</h2>
                <p className="text-xs text-content-secondary">
                  Batch import product, category, customer, and historical invoice data via CSV or legacy databases
                </p>
              </div>

              {/* Legacy Java POS Detection Banner */}
              {detectedLegacyDb && (
                <div className="p-5 rounded-xl bg-brand-subtle border border-brand/30 space-y-3">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div className="flex items-center gap-3">
                      <div className="p-2 rounded-lg bg-brand text-white">
                        <Database className="w-5 h-5" />
                      </div>
                      <div>
                        <h3 className="text-sm font-semibold text-content">Previous Java POS Database Detected</h3>
                        <p className="text-xs text-content-secondary">
                          Found local installation: <code className="font-mono text-content font-medium">{detectedLegacyDb}</code>
                        </p>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={async () => {
                        const { confirmed } = await confirmDialog({
                          title: 'Import Previous POS Data',
                          message:
                            'Import all categories, products, customers, and sales history from your previous Java POS installation?',
                          confirmLabel: 'Import Data',
                          tone: 'warning',
                        });
                        if (confirmed) {
                          migrationMutation.mutate(detectedLegacyDb);
                        }
                      }}
                      disabled={migrationMutation.isPending}
                      className="px-4 py-2 rounded-lg bg-brand hover:bg-brand-hover text-white font-medium text-xs flex items-center gap-2 transition-colors shrink-0"
                    >
                      {migrationMutation.isPending ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Database className="w-3.5 h-3.5" />}
                      <span>Import Previous POS Data</span>
                    </button>
                  </div>
                  {migrationResult && (
                    <div className="pt-2 text-xs text-success font-medium border-t border-brand/20">
                      ✓ Successfully imported {migrationResult.products_imported} products, {migrationResult.categories_imported} categories, {migrationResult.customers_imported} customers, and {migrationResult.sales_imported} sales invoices.
                    </div>
                  )}
                </div>
              )}

              {/* CSV Import Wizards */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <h3 className="text-sm font-semibold text-content">Spreadsheet (CSV) Data Import</h3>
                <p className="text-xs text-content-secondary">
                  Upload standard UTF-8 formatted CSV files to import master records into PicoPOSSUM
                </p>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
                  <div className="p-4 rounded-lg bg-surface-secondary border border-border space-y-2">
                    <div className="flex items-center gap-2 text-content font-medium text-xs">
                      <Layers className="w-4 h-4 text-brand" /> Categories Import
                    </div>
                    <p className="text-[11px] text-content-muted">
                      Columns: name, description
                    </p>
                    <ImportCsvButton
                      label="Import Categories CSV"
                      onImport={({ csvContent, filePath }) =>
                        tauriApi.importCsvCategories({ csvContent, filePath })
                      }
                      onSuccess={() => {
                        queryClient.invalidateQueries({ queryKey: ['categories'] });
                        toast.success('Categories imported successfully');
                      }}
                    />
                  </div>

                  <div className="p-4 rounded-lg bg-surface-secondary border border-border space-y-2">
                    <div className="flex items-center gap-2 text-content font-medium text-xs">
                      <Package className="w-4 h-4 text-brand" /> Products Catalog Import
                    </div>
                    <p className="text-[11px] text-content-muted">
                      Columns: sku, name, category, price, cost, stock
                    </p>
                    <ImportCsvButton
                      label="Import Products CSV"
                      onImport={({ csvContent, filePath }) =>
                        tauriApi.importCsvProducts({ csvContent, filePath })
                      }
                      onSuccess={() => {
                        queryClient.invalidateQueries({ queryKey: ['products'] });
                        toast.success('Products imported successfully');
                      }}
                    />
                  </div>

                  <div className="p-4 rounded-lg bg-surface-secondary border border-border space-y-2">
                    <div className="flex items-center gap-2 text-content font-medium text-xs">
                      <Users className="w-4 h-4 text-brand" /> Customer Directory Import
                    </div>
                    <p className="text-[11px] text-content-muted">
                      Columns: name, phone, email, address
                    </p>
                    <ImportCsvButton
                      label="Import Customers CSV"
                      onImport={({ csvContent, filePath }) =>
                        tauriApi.importCsvCustomers({ csvContent, filePath })
                      }
                      onSuccess={() => {
                        queryClient.invalidateQueries({ queryKey: ['customers'] });
                        toast.success('Customers imported successfully');
                      }}
                    />
                  </div>

                  <div className="p-4 rounded-lg bg-surface-secondary border border-border space-y-2">
                    <div className="flex items-center gap-2 text-content font-medium text-xs">
                      <FileText className="w-4 h-4 text-brand" /> Historical Bills Import
                    </div>
                    <p className="text-[11px] text-content-muted">
                      Legacy bill rows, items, and payments
                    </p>
                    <ImportCsvButton
                      label="Import Bills CSV"
                      onImport={({ csvContent, filePath, fileName }) =>
                        tauriApi.importCsvBills({ csvContent, filePath, fileName })
                      }
                      onSuccess={() => {
                        queryClient.invalidateQueries({ queryKey: ['sales'] });
                        toast.success('Bills imported successfully');
                      }}
                    />
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════════
              NAV 7: SECURITY & ACCESS
             ══════════════════════════════════════════════════════════════════════ */}
          {activeNav === 'security' && (
            <div className="max-w-2xl space-y-6">
              <div>
                <h2 className="text-base font-semibold text-content">Security &amp; Credentials</h2>
                <p className="text-xs text-content-secondary">
                  Manage administrator terminal password and credential safeguards
                </p>
              </div>

              <div className="p-6 rounded-xl bg-surface border border-border space-y-5">
                <div className="flex items-center gap-2.5 pb-3 border-b border-border">
                  <div className="w-8 h-8 rounded-lg bg-brand-subtle text-brand flex items-center justify-center border border-brand/20">
                    <Lock className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="text-sm font-semibold text-content">Update Administrator Password</h3>
                    <p className="text-[11px] text-content-secondary">
                      Used for unlocking the terminal, sensitive returns, and administrative operations
                    </p>
                  </div>
                </div>

                <form onSubmit={handlePasswordSubmit} className="space-y-4 text-xs">
                  <div>
                    <label className="text-content font-medium block mb-1">Current Password</label>
                    <input
                      type="password"
                      value={passwordState.currentPassword}
                      onChange={(e) =>
                        setPasswordState({ ...passwordState, currentPassword: e.target.value })
                      }
                      placeholder="Enter current password"
                      className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="text-content font-medium block mb-1">New Password</label>
                    <input
                      type="password"
                      value={passwordState.newPassword}
                      onChange={(e) =>
                        setPasswordState({ ...passwordState, newPassword: e.target.value })
                      }
                      placeholder="Enter new password (min. 4 characters)"
                      className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="text-content font-medium block mb-1">Confirm New Password</label>
                    <input
                      type="password"
                      value={passwordState.confirmPassword}
                      onChange={(e) =>
                        setPasswordState({ ...passwordState, confirmPassword: e.target.value })
                      }
                      placeholder="Repeat new password"
                      className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none"
                    />
                  </div>

                  <div className="pt-2 flex justify-end">
                    <button
                      type="submit"
                      disabled={updatePasswordMutation.isPending}
                      className="px-5 py-2.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-medium text-xs flex items-center gap-2 transition-colors cursor-pointer disabled:opacity-50"
                    >
                      {updatePasswordMutation.isPending ? (
                        <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                      ) : (
                        <Check className="w-3.5 h-3.5" />
                      )}
                      <span>Update Password</span>
                    </button>
                  </div>
                </form>
              </div>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════════════════
              NAV 8: APPEARANCE & THEME
             ══════════════════════════════════════════════════════════════════════ */}
          {activeNav === 'appearance' && (
            <div className="max-w-2xl space-y-6">
              <div>
                <h2 className="text-base font-semibold text-content">Appearance &amp; Display</h2>
                <p className="text-xs text-content-secondary">
                  Configure visual theme and interface density for retail lighting environments
                </p>
              </div>

              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <h3 className="text-sm font-semibold text-content">Color Theme</h3>
                <p className="text-xs text-content-secondary">
                  Choose between the default high-readability light operational theme or the deep navy dark theme.
                </p>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
                  {/* Light Operational Card */}
                  <div
                    onClick={() => setTheme('light')}
                    className={`p-4 rounded-xl border-2 cursor-pointer transition-all space-y-3 ${
                      theme === 'light'
                        ? 'border-brand bg-brand-subtle'
                        : 'border-border bg-surface-secondary hover:border-border-strong'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2 font-medium text-xs text-content">
                        <Sun className="w-4 h-4 text-warning" />
                        <span>Light Theme (Recommended)</span>
                      </div>
                      {theme === 'light' && (
                        <div className="w-5 h-5 rounded-full bg-brand text-white flex items-center justify-center">
                          <Check className="w-3 h-3" />
                        </div>
                      )}
                    </div>
                    {/* Visual Color Palette Preview */}
                    <div className="flex gap-1.5 h-6 rounded overflow-hidden border border-border">
                      <div className="flex-1 bg-[#F4F6F9]" title="#F4F6F9 App Canvas" />
                      <div className="flex-1 bg-[#FFFFFF]" title="#FFFFFF Primary Surface" />
                      <div className="flex-1 bg-[#1D4ED8]" title="#1D4ED8 Brand Blue" />
                      <div className="flex-1 bg-[#17212F]" title="#17212F Content" />
                    </div>
                    <p className="text-[11px] text-content-secondary">
                      Optimal contrast for bright store lighting, cashier counters, and all display types.
                    </p>
                  </div>

                  {/* Deep Navy Dark Card */}
                  <div
                    onClick={() => setTheme('dark')}
                    className={`p-4 rounded-xl border-2 cursor-pointer transition-all space-y-3 ${
                      theme === 'dark'
                        ? 'border-brand bg-brand-subtle'
                        : 'border-border bg-surface-secondary hover:border-border-strong'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2 font-medium text-xs text-content">
                        <Moon className="w-4 h-4 text-brand" />
                        <span>Deep Navy Dark</span>
                      </div>
                      {theme === 'dark' && (
                        <div className="w-5 h-5 rounded-full bg-brand text-white flex items-center justify-center">
                          <Check className="w-3 h-3" />
                        </div>
                      )}
                    </div>
                    {/* Visual Color Palette Preview */}
                    <div className="flex gap-1.5 h-6 rounded overflow-hidden border border-border">
                      <div className="flex-1 bg-[#0B1220]" title="#0B1220 Deep Navy Canvas" />
                      <div className="flex-1 bg-[#111C2E]" title="#111C2E Primary Surface" />
                      <div className="flex-1 bg-[#2457C5]" title="#2457C5 Brand Action" />
                      <div className="flex-1 bg-[#E5EDF7]" title="#E5EDF7 Content" />
                    </div>
                    <p className="text-[11px] text-content-secondary">
                      Designed for evening shifts and dimmer low-light retail terminals.
                    </p>
                  </div>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
