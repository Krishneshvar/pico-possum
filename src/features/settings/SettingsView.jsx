import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Sliders,
  Store,
  Receipt,
  Printer,
  ShieldCheck,
  Database,
  Lock,
  Palette,
} from 'lucide-react';
import { tauriApi } from '../../lib/tauri';
import { toast } from '../../stores/toast.store';
import { applyGeneralSettings, getCurrencySymbol } from '../../lib/formatters';
import { strings } from '../../lib/strings';

import GeneralTab from './tabs/GeneralTab';
import StoreTaxTab from './tabs/StoreTaxTab';
import ReceiptTab from './tabs/ReceiptTab';
import PrintingTab from './tabs/PrintingTab';
import BackupTab from './tabs/BackupTab';
import MigrationTab from './tabs/MigrationTab';
import SecurityTab from './tabs/SecurityTab';
import AppearanceTab from './tabs/AppearanceTab';

const DEFAULT_SECTIONS = [
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

export default function SettingsView() {
  const queryClient = useQueryClient();

  // Navigation state: 'general' | 'store_tax' | 'receipt' | 'printing' | 'backup' | 'migration' | 'security' | 'appearance'
  const [activeNav, setActiveNav] = useState('general');

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

  // ── Bill Structure State ───────────────────────────────────────────────────
  const [bill, setBill] = useState({
    paper_width: '80mm',
    store_name: strings.settings.sampleData.storeName,
    store_details: strings.settings.sampleData.address,
    phone: strings.settings.sampleData.phone,
    gst: strings.settings.sampleData.gstin,
    show_tax: false,
    footer_text: strings.settings.sampleData.footer,
    sections: DEFAULT_SECTIONS,
  });

  // ── Queries ─────────────────────────────────────────────────────────────────
  const { data: generalData } = useQuery({
    queryKey: ['general_settings'],
    queryFn: () => tauriApi.getGeneralSettings(),
  });

  const { data: billData } = useQuery({
    queryKey: ['bill_settings'],
    queryFn: () => tauriApi.getBillSettings(),
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

  // ── Mutations ───────────────────────────────────────────────────────────────
  const updateGeneralMutation = useMutation({
    mutationFn: (data) => tauriApi.updateGeneralSettings(data),
    onSuccess: (_data, variables) => {
      applyGeneralSettings(variables);
      queryClient.invalidateQueries({ queryKey: ['general_settings'] });
      queryClient.invalidateQueries({ queryKey: ['next_sku'] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      toast.success(strings.settings.toasts.settingsSaved);
    },
    onError: (err) => toast.error(strings.settings.toasts.saveSettingsFailed(err)),
  });

  const updateBillMutation = useMutation({
    mutationFn: (data) => tauriApi.updateBillSettings(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bill_settings'] });
      toast.success(strings.settings.toasts.receiptSaved);
    },
    onError: (err) => toast.error(strings.settings.toasts.saveReceiptFailed(err)),
  });

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
    <div className="flex-1 flex flex-col h-full bg-canvas overflow-hidden">
      {/* Top Banner / Header */}
      <div className="px-6 py-4 border-b border-border bg-surface flex items-center justify-between shrink-0">
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
          {activeNav === 'general' && (
            <GeneralTab
              general={general}
              setGeneral={setGeneral}
              onSave={(data) => updateGeneralMutation.mutate(data)}
              isSaving={updateGeneralMutation.isPending}
            />
          )}

          {activeNav === 'store_tax' && (
            <StoreTaxTab
              general={general}
              setGeneral={setGeneral}
              onSave={(data) => updateGeneralMutation.mutate(data)}
              isSaving={updateGeneralMutation.isPending}
            />
          )}

          {activeNav === 'receipt' && (
            <ReceiptTab
              bill={bill}
              setBill={setBill}
              general={general}
              onSave={(data) => updateBillMutation.mutate(data)}
              isSaving={updateBillMutation.isPending}
            />
          )}

          {activeNav === 'printing' && (
            <PrintingTab
              general={general}
              setGeneral={setGeneral}
              onSave={(data) => updateGeneralMutation.mutate(data)}
              isSaving={updateGeneralMutation.isPending}
            />
          )}

          {activeNav === 'backup' && <BackupTab />}

          {activeNav === 'migration' && <MigrationTab />}

          {activeNav === 'security' && <SecurityTab />}

          {activeNav === 'appearance' && <AppearanceTab />}
        </main>
      </div>
    </div>
  );
}
