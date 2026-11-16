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
import { getCurrencySymbol } from '../../lib/formatters';
import Select from '../../components/common/Select';
import { confirmDialog } from '../../stores/confirm.store';
import { useThemeStore } from '../../stores/theme.store';
import { applyGeneralSettings, formatDateTime } from '../../lib/formatters';
import { nextSortState, sortRows } from '../../lib/sort';
import SortableTh from '../../components/common/SortableTh';
import ReceiptPrinter from '../../components/receipt/ReceiptPrinter';
import ImportCsvButton from '../../components/common/ImportCsvButton';
import { strings } from '../../lib/strings';

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
    currency_symbol: getCurrencySymbol() || '',
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
      name: strings.settings.receipt.sectionNames.storeHeader,
      visible: true,
      options: {
        alignment: 'center',
        fontSize: 'medium',
        storeName: strings.settings.sampleData.storeName,
        storeDetails: strings.settings.sampleData.address,
        phone: strings.settings.sampleData.phone,
        gst: strings.settings.sampleData.gstin,
      },
    },
    {
      id: 'billMetadata',
      name: strings.settings.receipt.sectionNames.billMetadata,
      visible: true,
      options: {
        alignment: 'left',
        fontSize: 'small',
      },
    },
    {
      id: 'itemsTable',
      name: strings.settings.receipt.sectionNames.itemsTable,
      visible: true,
      options: {
        fontSize: 'medium',
        showTax: false,
      },
    },
    {
      id: 'totals',
      name: strings.settings.receipt.sectionNames.totals,
      visible: true,
      options: {
        alignment: 'right',
        fontSize: 'medium',
      },
    },
    {
      id: 'footer',
      name: strings.settings.receipt.sectionNames.footer,
      visible: true,
      options: {
        alignment: 'center',
        fontSize: 'small',
        text: strings.settings.sampleData.footer,
      },
    },
  ];

  const [bill, setBill] = useState({
    paper_width: '80mm',
    store_name: strings.settings.sampleData.storeName,
    store_details: strings.settings.sampleData.address,
    phone: strings.settings.sampleData.phone,
    gst: strings.settings.sampleData.gstin,
    show_tax: false,
    footer_text: strings.settings.sampleData.footer,
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
      showNotification(strings.settings.toasts.settingsSaved);
    },
    onError: (err) => showNotification(strings.settings.toasts.saveSettingsFailed(err), 'error'),
  });

  const updateBillMutation = useMutation({
    mutationFn: (data) => tauriApi.updateBillSettings(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bill_settings'] });
      showNotification(strings.settings.toasts.receiptSaved);
    },
    onError: (err) => showNotification(strings.settings.toasts.saveReceiptFailed(err), 'error'),
  });

  const testPrintMutation = useMutation({
    mutationFn: (printerName) => tauriApi.testPrint(printerName),
    onSuccess: () => {
      showNotification(
        strings.settings.toasts.testPrintSent(
          general.default_printer_name || strings.settings.toasts.defaultPrinter
        )
      );
    },
    onError: () => {
      window.print();
      showNotification(strings.settings.toasts.systemPrintDialog, 'info');
    },
  });

  const createBackupMutation = useMutation({
    mutationFn: () => tauriApi.createManualBackup(),
    onSuccess: (info) => {
      refreshBackupStatus();
      refreshBackupsList();
      showNotification(strings.settings.toasts.backupCreated(info.filename));
    },
    onError: (err) => showNotification(strings.settings.toasts.createBackupFailed(err), 'error'),
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
    onError: (err) => showNotification(strings.settings.toasts.restoreFailed(err), 'error'),
  });

  const migrationMutation = useMutation({
    mutationFn: (path) => tauriApi.importLegacyDatabase(path || null),
    onSuccess: (summary) => {
      setMigrationResult(summary);
      queryClient.invalidateQueries();
      showNotification(strings.settings.toasts.migrationComplete);
    },
    onError: (err) => showNotification(strings.settings.toasts.migrationFailed(err), 'error'),
  });

  const updatePasswordMutation = useMutation({
    mutationFn: ({ currentPassword, newPassword }) =>
      tauriApi.updatePassword({ currentPassword, newPassword }),
    onSuccess: (msg) => {
      setPasswordState({ currentPassword: '', newPassword: '', confirmPassword: '' });
      showNotification(msg || strings.settings.security.updated);
    },
    onError: (err) => {
      const msg =
        typeof err === 'string' ? err : err?.message || strings.settings.security.updateFailed;
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
      showNotification(strings.settings.security.fillAllFields, 'warning');
      return;
    }
    if (passwordState.newPassword.length < 4) {
      showNotification(strings.settings.security.passwordTooShort, 'warning');
      return;
    }
    if (passwordState.newPassword !== passwordState.confirmPassword) {
      showNotification(strings.settings.security.passwordsDoNotMatch, 'warning');
      return;
    }

    updatePasswordMutation.mutate({
      currentPassword: passwordState.currentPassword,
      newPassword: passwordState.newPassword,
    });
  };

  // Sample data for Live Receipt Preview
  const sampleSale = {
    invoice_number: strings.settings.sampleData.invoiceNumber,
    created_at: new Date().toISOString(),
    customer_name: strings.settings.sampleData.customerName,
    biller_name: strings.settings.sampleData.billerName,
    subtotal_paise: 25000,
    discount_paise: 2000,
    tax_paise: 1250,
    total_paise: 24250,
    paid_paise: 25000,
    payment_method: strings.settings.sampleData.paymentMethod,
  };

  const sampleItems = [
    { product_name: strings.settings.sampleData.productCadbury, quantity: 2, unit_price_paise: 10000, discount_paise: 0, tax_rate: 5.0 },
    { product_name: strings.settings.sampleData.productGreetingCard, quantity: 1, unit_price_paise: 5000, discount_paise: 2000, tax_rate: 18.0 },
  ];

  // Navigation Items
  const navItems = [
    { id: 'general', label: strings.settings.nav.general.label, icon: Sliders, description: strings.settings.nav.general.description },
    { id: 'store_tax', label: strings.settings.nav.storeTax.label, icon: Store, description: strings.settings.nav.storeTax.description },
    { id: 'receipt', label: strings.settings.nav.receipt.label, icon: Receipt, description: strings.settings.nav.receipt.description },
    { id: 'printing', label: strings.settings.nav.printing.label, icon: Printer, description: strings.settings.nav.printing.description },
    { id: 'backup', label: strings.settings.nav.backup.label, icon: ShieldCheck, description: strings.settings.nav.backup.description },
    { id: 'migration', label: strings.settings.nav.migration.label, icon: Database, description: strings.settings.nav.migration.description },
    { id: 'security', label: strings.settings.nav.security.label, icon: Lock, description: strings.settings.nav.security.description },
    { id: 'appearance', label: strings.settings.nav.appearance.label, icon: Palette, description: strings.settings.nav.appearance.description },
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
            <h1 className="text-sm font-semibold text-content">
              {strings.settings.header.title}
            </h1>
            <p className="text-xs text-content-secondary">
              {strings.settings.header.subtitle}
            </p>
          </div>
        </div>
      </div>

      {/* Main Settings Body: Left Vertical Navigation + Right Content Panel */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left Vertical Navigation Sidebar (240px) */}
        <aside className="w-60 border-r border-border bg-surface flex flex-col p-3 space-y-1 overflow-y-auto shrink-0">
          <div className="px-3 py-2 text-[11px] font-semibold text-content-muted uppercase tracking-wider">
            {strings.settings.header.navHeading}
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
                <h2 className="text-base font-semibold text-content">{strings.settings.general.title}</h2>
                <p className="text-xs text-content-secondary">
                  {strings.settings.general.subtitle}
                </p>
              </div>

              {/* Store Identity */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <h3 className="text-sm font-semibold text-content">
                  {strings.settings.general.storeProfile}
                </h3>
                <div className="space-y-4 text-xs">
                  <div>
                    <label className="text-content font-medium block mb-1">
                      {strings.settings.general.storeName}
                    </label>
                    <input
                      type="text"
                      value={general.store_name}
                      onChange={(e) => setGeneral({ ...general, store_name: e.target.value })}
                      placeholder={strings.settings.general.storeNamePlaceholder}
                      className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none"
                    />
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label className="text-content font-medium block mb-1">
                        {strings.settings.general.phone}
                      </label>
                      <input
                        type="text"
                        value={general.phone}
                        onChange={(e) => setGeneral({ ...general, phone: e.target.value })}
                        placeholder={strings.settings.general.phonePlaceholder}
                        className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none font-mono"
                      />
                    </div>
                    <div>
                      <label className="text-content font-medium block mb-1">
                        {strings.settings.general.address}
                      </label>
                      <input
                        type="text"
                        value={general.address}
                        onChange={(e) => setGeneral({ ...general, address: e.target.value })}
                        placeholder={strings.settings.general.addressPlaceholder}
                        className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none"
                      />
                    </div>
                  </div>
                </div>
              </div>

              {/* Date & Time Formats */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <h3 className="text-sm font-semibold text-content">
                  {strings.settings.general.dateTimeFormats}
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                  <div>
                    <label className="text-content font-medium block mb-1.5">
                      {strings.settings.general.dateDisplayFormat}
                    </label>
                    <Select
                      value={general.date_format}
                      onChange={(val) => setGeneral({ ...general, date_format: val })}
                      options={[
                        { value: 'DD-MM-YYYY', label: strings.settings.general.dateFormats.dayFirst },
                        { value: 'MM-DD-YYYY', label: strings.settings.general.dateFormats.monthFirst },
                        { value: 'YYYY-MM-DD', label: strings.settings.general.dateFormats.iso },
                        { value: 'Month Date, Year', label: strings.settings.general.dateFormats.monthNameFirst },
                        { value: 'Date Month, Year', label: strings.settings.general.dateFormats.dayBeforeMonthName },
                      ]}
                    />
                  </div>

                  <div>
                    <label className="text-content font-medium block mb-1.5">
                      {strings.settings.general.timeDisplayFormat}
                    </label>
                    <Select
                      value={general.time_format}
                      onChange={(val) => setGeneral({ ...general, time_format: val })}
                      options={[
                        { value: '12 hour format', label: strings.settings.general.timeFormats.twelveHour },
                        { value: '24 hour format', label: strings.settings.general.timeFormats.twentyFourHour },
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
                  <span>{strings.settings.general.save}</span>
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
                <h2 className="text-base font-semibold text-content">{strings.settings.storeTax.title}</h2>
                <p className="text-xs text-content-secondary">
                  {strings.settings.storeTax.subtitle}
                </p>
              </div>

              {/* Currency & Tax Registration */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <h3 className="text-sm font-semibold text-content">
                  {strings.settings.storeTax.currencyGstin}
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                  <div>
                    <label className="text-content font-medium block mb-1">
                      {strings.settings.storeTax.currencySymbol}
                    </label>
                    <input
                      type="text"
                      value={general.currency_symbol}
                      onChange={(e) => setGeneral({ ...general, currency_symbol: e.target.value })}
                      placeholder={strings.settings.storeTax.currencySymbolPlaceholder}
                      className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none font-medium"
                    />
                    <span className="text-[11px] text-content-muted mt-1 block">
                      {strings.settings.storeTax.currencySymbolHint}
                    </span>
                  </div>

                  <div>
                    <label className="text-content font-medium block mb-1">
                      {strings.settings.storeTax.gstin}
                    </label>
                    <input
                      type="text"
                      value={general.gstin}
                      onChange={(e) => setGeneral({ ...general, gstin: e.target.value })}
                      placeholder={strings.settings.storeTax.gstinPlaceholder}
                      className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none font-mono"
                    />
                    <span className="text-[11px] text-content-muted mt-1 block">
                      {strings.settings.storeTax.gstinHint}
                    </span>
                  </div>
                </div>
              </div>

              {/* Inventory Guard Rails */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <h3 className="text-sm font-semibold text-content">
                  {strings.settings.storeTax.inventoryBehavior}
                </h3>
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
                      <span className="font-semibold text-content block">
                        {strings.settings.storeTax.autoSkuTitle}
                      </span>
                      <span className="text-content-secondary block">
                        {strings.settings.storeTax.autoSkuDescription}
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
                        {strings.settings.storeTax.negativeStockTitle}
                      </span>
                      <span className="text-content-secondary block">
                        {strings.settings.storeTax.negativeStockDescription}
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
                  <span>{strings.settings.storeTax.save}</span>
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
                  <h2 className="text-base font-semibold text-content">{strings.settings.receipt.title}</h2>
                  <p className="text-xs text-content-secondary">
                    {strings.settings.receipt.subtitle}
                  </p>
                </div>

                {/* Paper Roll Width */}
                <div className="p-6 rounded-xl bg-surface border border-border space-y-3">
                  <h3 className="text-xs font-semibold text-content uppercase tracking-wider">
                    {strings.settings.receipt.paperRollWidth}
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
                        <span>
                          {w === '80mm'
                            ? strings.settings.receipt.paperWidths.wide
                            : strings.settings.receipt.paperWidths.narrow}
                        </span>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Section Reordering & Options */}
                <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                  <div>
                    <h3 className="text-xs font-semibold text-content uppercase tracking-wider">
                      {strings.settings.receipt.sectionsHeading}
                    </h3>
                    <p className="text-xs text-content-secondary mt-1">
                      {strings.settings.receipt.sectionsHint}
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
                                title={strings.settings.receipt.moveUp}
                              >
                                <ArrowUp className="w-3.5 h-3.5" />
                              </button>
                              <button
                                type="button"
                                disabled={isLast}
                                onClick={() => moveSection(idx, 1)}
                                className="p-1 rounded hover:bg-surface border border-transparent hover:border-border text-content-secondary hover:text-content disabled:opacity-30 disabled:pointer-events-none"
                                title={strings.settings.receipt.moveDown}
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
                                    {strings.settings.receipt.alignment}
                                  </label>
                                  <select
                                    value={opts.alignment}
                                    onChange={(e) =>
                                      updateSectionOption(section.id, 'alignment', e.target.value)
                                    }
                                    className="w-full bg-surface border border-border-strong rounded p-1.5 text-content"
                                  >
                                    <option value="left">{strings.settings.receipt.alignmentOptions.left}</option>
                                    <option value="center">{strings.settings.receipt.alignmentOptions.center}</option>
                                    <option value="right">{strings.settings.receipt.alignmentOptions.right}</option>
                                  </select>
                                </div>
                              )}

                              {opts.fontSize !== undefined && (
                                <div>
                                  <label className="text-[11px] text-content-secondary block mb-1">
                                    {strings.settings.receipt.fontSize}
                                  </label>
                                  <select
                                    value={opts.fontSize}
                                    onChange={(e) =>
                                      updateSectionOption(section.id, 'fontSize', e.target.value)
                                    }
                                    className="w-full bg-surface border border-border-strong rounded p-1.5 text-content"
                                  >
                                    <option value="small">{strings.settings.receipt.fontSizeOptions.small}</option>
                                    <option value="medium">{strings.settings.receipt.fontSizeOptions.medium}</option>
                                    <option value="large">{strings.settings.receipt.fontSizeOptions.large}</option>
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
                                      {strings.settings.receipt.printTaxColumn}
                                    </span>
                                  </label>
                                </div>
                              )}

                              {section.id === 'footer' && (
                                <div className="col-span-2">
                                  <label className="text-[11px] text-content-secondary block mb-1">
                                    {strings.settings.receipt.footerText}
                                  </label>
                                  <input
                                    type="text"
                                    value={opts.text || ''}
                                    onChange={(e) =>
                                      updateSectionOption(section.id, 'text', e.target.value)
                                    }
                                    placeholder={strings.settings.receipt.footerPlaceholder}
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
                    <span>{strings.settings.receipt.save}</span>
                  </button>
                </div>
              </div>

              {/* Right Column: Thermal Paper Preview (5 cols) */}
              <div className="xl:col-span-5 sticky top-6">
                <div className="p-4 rounded-xl bg-surface border border-border space-y-3">
                  <div className="flex items-center justify-between">
                    <h3 className="text-xs font-semibold text-content uppercase tracking-wider">
                      {strings.settings.receipt.previewTitle(bill.paper_width)}
                    </h3>
                    <span className="text-[11px] text-content-muted">{strings.settings.receipt.sampleTransaction}</span>
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
                <h2 className="text-base font-semibold text-content">{strings.settings.printing.title}</h2>
                <p className="text-xs text-content-secondary">
                  {strings.settings.printing.subtitle}
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
                      {printers.length > 0
                        ? strings.settings.printing.printersDetected(printers.length)
                        : strings.settings.printing.noPrintersDetected}
                    </div>
                    <p className="text-[11px] text-content-secondary">
                      {general.default_printer_name
                        ? strings.settings.printing.activeDefault(general.default_printer_name)
                        : strings.settings.printing.usingOsDefault}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`w-2.5 h-2.5 rounded-full ${printers.length > 0 ? 'bg-emerald-500' : 'bg-amber-500'}`} />
                  <span className="text-xs text-content-secondary font-medium">
                    {printers.length > 0
                      ? strings.settings.printing.hardwareReady
                      : strings.settings.printing.checkCable}
                  </span>
                </div>
              </div>

              {/* Hardware Selection */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <div className="flex items-center justify-between pb-3 border-b border-border">
                  <h3 className="text-sm font-semibold text-content">{strings.settings.printing.connectedPrinters}</h3>
                  <button
                    type="button"
                    onClick={() => refreshPrinters()}
                    disabled={scanningPrinters}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface-secondary border border-border text-content-secondary hover:text-content text-xs font-medium transition-colors"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 text-brand ${scanningPrinters ? 'animate-spin' : ''}`} />
                    <span>{strings.settings.printing.scanPrinters}</span>
                  </button>
                </div>

                <div className="space-y-4 text-xs">
                  <div>
                    <label className="text-content font-medium block mb-1.5">{strings.settings.printing.defaultPrinter}</label>
                    <Select
                      value={general.default_printer_name}
                      onChange={(val) => setGeneral({ ...general, default_printer_name: val })}
                      placeholder={strings.settings.printing.osDefaultOption}
                      options={[
                        { value: '', label: strings.settings.printing.osDefaultOption },
                        ...printers.map((p) => ({
                          value: p.name,
                          label: strings.settings.printing.printerOption(p.name, p.is_default),
                        })),
                      ]}
                    />
                    <span className="text-[11px] text-content-muted mt-1 block">
                      {strings.settings.printing.printerHint}
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
                      <span>{strings.settings.printing.saveDefaultPrinter}</span>
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
                      <span>{strings.settings.printing.sendTestPrint}</span>
                    </button>
                    <span className="text-xs text-content-secondary">
                      {strings.settings.printing.testPrintHint}
                    </span>
                  </div>
                </div>
              </div>

              {/* Hardware Tips */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-3 text-xs text-content-secondary">
                <h3 className="text-sm font-semibold text-content flex items-center gap-2">
                  <Info className="w-4 h-4 text-brand" /> {strings.settings.printing.guidelinesTitle}
                </h3>
                <ul className="space-y-2 list-disc pl-4 leading-relaxed">
                  {strings.settings.printing.guidelines.map((g, i) => (
                    <li key={i}>{g}</li>
                  ))}
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
                    title: strings.settings.backup.confirmFileRestore.title,
                    message: strings.settings.backup.confirmFileRestore.message(file.name),
                    details: strings.settings.backup.confirmFileRestore.details,
                    confirmLabel: strings.settings.backup.confirmFileRestore.confirmLabel,
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
                      showNotification(strings.settings.backup.readFileFailed(err.message), 'error');
                    }
                  }
                }}
              />

              <div>
                <h2 className="text-base font-semibold text-content">{strings.settings.backup.title}</h2>
                <p className="text-xs text-content-secondary">
                  {strings.settings.backup.subtitle}
                </p>
              </div>

              {/* Status & Manual Action */}
              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-border">
                  <div>
                    <h3 className="text-sm font-semibold text-content">{strings.settings.backup.maintenance}</h3>
                    <p className="text-xs text-content-secondary">
                      {strings.settings.backup.maintenanceHint}
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
                      <span>{strings.settings.backup.createBackup}</span>
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
                      title={strings.settings.backup.openFolderTitle}
                    >
                      <FolderOpen className="w-3.5 h-3.5 text-content-muted" />
                      <span>{strings.settings.backup.openFolder}</span>
                    </button>
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                  <div className="p-3.5 rounded-lg bg-surface-secondary border border-border">
                    <span className="text-[11px] text-content-muted font-medium block mb-1">
                      {strings.settings.backup.storagePath}
                    </span>
                    <div className="font-mono text-content text-xs break-all select-all">
                      {backupStatus?.backup_location || strings.settings.backup.loadingLocation}
                    </div>
                  </div>

                  <div className="p-3.5 rounded-lg bg-surface-secondary border border-border">
                    <span className="text-[11px] text-content-muted font-medium block mb-1">
                      {strings.settings.backup.latestSnapshot}
                    </span>
                    <div className="text-content font-medium text-xs">
                      {backupStatus?.latest_backup ? (
                        <span className="text-success font-semibold">
                          {backupStatus.latest_backup.filename} (
                          {Math.round(backupStatus.latest_backup.size_bytes / 1024)} KB)
                        </span>
                      ) : (
                        <span className="text-content-muted">{strings.settings.backup.noSnapshots}</span>
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
                    {strings.settings.backup.availableBackups(backupsList.length)}
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
                    <span>{strings.settings.backup.uploadExternal}</span>
                  </button>
                </div>

                {backupsList.length === 0 ? (
                  <div className="text-center py-8 text-xs text-content-muted">
                    {strings.settings.backup.emptyState}
                  </div>
                ) : (
                  <div className="overflow-x-auto rounded-xl border border-border bg-surface shadow-2xs">
                    <table className="w-full text-left text-xs border-collapse">
                      <thead className="bg-table-header-bg text-table-header-text font-semibold border-b border-table-header-border">
                        <tr className="h-10 text-[11px] uppercase tracking-wider">
                          <SortableTh sortKey="filename" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} className="py-2.5 px-3.5 pl-4">{strings.settings.backup.table.filename}</SortableTh>
                          <SortableTh sortKey="backup_type" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} className="py-2.5 px-3.5">{strings.settings.backup.table.type}</SortableTh>
                          <SortableTh sortKey="created_at" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} className="py-2.5 px-3.5">{strings.settings.backup.table.date}</SortableTh>
                          <SortableTh sortKey="size_bytes" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} align="right" className="py-2.5 px-3.5">{strings.settings.backup.table.size}</SortableTh>
                          <th className="py-2.5 pl-3.5 pr-4 font-semibold text-right">{strings.settings.backup.table.action}</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border">
                        {sortedBackups.map((bk) => (
                          <tr key={bk.filename} className="hover:bg-surface-secondary transition-colors">
                            <td className="py-2.5 px-3.5 pl-4 font-medium text-content truncate max-w-xs">{bk.filename}</td>
                            <td className="py-2.5 px-3.5">
                              <span className="px-2 py-0.5 rounded text-[11px] font-medium bg-surface-secondary text-content-secondary border border-border">
                                {bk.backup_type}
                              </span>
                            </td>
                            <td className="py-2.5 px-3.5 text-content-secondary text-xs">{formatDateTime(bk.created_at)}</td>
                            <td className="py-2.5 px-3.5 text-right text-content financial-value">{Math.round(bk.size_bytes / 1024)} KB</td>
                            <td className="py-2.5 pl-3.5 pr-4 text-right">
                              <button
                                type="button"
                                onClick={async () => {
                                  const { confirmed } = await confirmDialog({
                                    title: strings.settings.backup.confirmBackupRestore.title,
                                    message: strings.settings.backup.confirmBackupRestore.message(bk.filename),
                                    details: strings.settings.backup.confirmBackupRestore.details,
                                    confirmLabel: strings.settings.backup.confirmBackupRestore.confirmLabel,
                                    tone: 'warning',
                                  });
                                  if (confirmed) {
                                    restoreBackupMutation.mutate(bk.filename);
                                  }
                                }}
                                disabled={restoreBackupMutation.isPending}
                                className="px-3 py-1 rounded-lg bg-surface border border-border hover:bg-surface-secondary text-content text-xs font-medium transition-colors"
                              >
                                {strings.settings.backup.restore}
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
                <h2 className="text-base font-semibold text-content">{strings.settings.migration.title}</h2>
                <p className="text-xs text-content-secondary">
                  {strings.settings.migration.subtitle}
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
                        <h3 className="text-sm font-semibold text-content">{strings.settings.migration.legacyDetectedTitle}</h3>
                        <p className="text-xs text-content-secondary">
                          {strings.settings.migration.legacyFound} <code className="font-mono text-content font-medium">{detectedLegacyDb}</code>
                        </p>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={async () => {
                        const { confirmed } = await confirmDialog({
                          title: strings.settings.migration.confirmImport.title,
                          message: strings.settings.migration.confirmImport.message,
                          confirmLabel: strings.settings.migration.confirmImport.confirmLabel,
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
                      <span>{strings.settings.migration.importPrevious}</span>
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
                <h3 className="text-sm font-semibold text-content">{strings.settings.migration.csvHeading}</h3>
                <p className="text-xs text-content-secondary">
                  {strings.settings.migration.csvDescription}
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
                      entity="categories"
                      label="Import Categories"
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
                      entity="products"
                      label="Import Products"
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
                      entity="customers"
                      label="Import Customers"
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
                      entity="bills"
                      label="Import Bills"
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
                <h2 className="text-base font-semibold text-content">{strings.settings.security.title}</h2>
                <p className="text-xs text-content-secondary">
                  {strings.settings.security.subtitle}
                </p>
              </div>

              <div className="p-6 rounded-xl bg-surface border border-border space-y-5">
                <div className="flex items-center gap-2.5 pb-3 border-b border-border">
                  <div className="w-8 h-8 rounded-lg bg-brand-subtle text-brand flex items-center justify-center border border-brand/20">
                    <Lock className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="text-sm font-semibold text-content">{strings.settings.security.updateTitle}</h3>
                    <p className="text-[11px] text-content-secondary">
                      {strings.settings.security.updateHint}
                    </p>
                  </div>
                </div>

                <form onSubmit={handlePasswordSubmit} className="space-y-4 text-xs">
                  <div>
                    <label className="text-content font-medium block mb-1">
                      {strings.settings.security.currentPassword}
                    </label>
                    <input
                      type="password"
                      value={passwordState.currentPassword}
                      onChange={(e) =>
                        setPasswordState({ ...passwordState, currentPassword: e.target.value })
                      }
                      placeholder={strings.settings.security.currentPasswordPlaceholder}
                      className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="text-content font-medium block mb-1">
                      {strings.settings.security.newPassword}
                    </label>
                    <input
                      type="password"
                      value={passwordState.newPassword}
                      onChange={(e) =>
                        setPasswordState({ ...passwordState, newPassword: e.target.value })
                      }
                      placeholder={strings.settings.security.newPasswordPlaceholder}
                      className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2.5 text-content focus:border-brand focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="text-content font-medium block mb-1">
                      {strings.settings.security.confirmPassword}
                    </label>
                    <input
                      type="password"
                      value={passwordState.confirmPassword}
                      onChange={(e) =>
                        setPasswordState({ ...passwordState, confirmPassword: e.target.value })
                      }
                      placeholder={strings.settings.security.confirmPasswordPlaceholder}
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
                      <span>{strings.settings.security.updatePassword}</span>
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
                <h2 className="text-base font-semibold text-content">{strings.settings.appearance.title}</h2>
                <p className="text-xs text-content-secondary">
                  {strings.settings.appearance.subtitle}
                </p>
              </div>

              <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
                <h3 className="text-sm font-semibold text-content">{strings.settings.appearance.colorTheme}</h3>
                <p className="text-xs text-content-secondary">
                  {strings.settings.appearance.colorThemeHint}
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
                        <span>{strings.settings.appearance.lightTitle}</span>
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
                      {strings.settings.appearance.lightDescription}
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
                        <span>{strings.settings.appearance.darkTitle}</span>
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
                      {strings.settings.appearance.darkDescription}
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
