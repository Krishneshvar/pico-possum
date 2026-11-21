import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Database, RefreshCw, Layers, Package, Users, FileText } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import ImportCsvButton from '../../../components/common/ImportCsvButton';
import ErrorDetailModal from '../../../components/common/ErrorDetailModal';
import { formatHumanError } from '../../../lib/formatHumanError';
import { strings } from '../../../lib/strings';

export default function MigrationTab() {
  const queryClient = useQueryClient();
  const [migrationResult, setMigrationResult] = useState(null);
  const [migrationError, setMigrationError] = useState(null);

  const { data: detectedLegacyDb } = useQuery({
    queryKey: ['detectedLegacyDb'],
    queryFn: () => tauriApi.detectLegacyDatabase(),
  });

  const migrationMutation = useMutation({
    mutationFn: (path) => tauriApi.importLegacyDatabase(path || null),
    onSuccess: (res) => {
      setMigrationResult(res);
      setMigrationError(null);
      queryClient.invalidateQueries();
      toast.success(strings.settings.toasts.migrationComplete);
    },
    onError: (err) => {
      const parsed = formatHumanError(err, 'migration');
      setMigrationError(err);
      toast.error(parsed.message, {
        title: parsed.title,
      });
    },
  });

  return (
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
              ✓ Successfully imported {migrationResult.productsImported ?? migrationResult.products_imported} products, {migrationResult.categoriesImported ?? migrationResult.categories_imported} categories, {migrationResult.customersImported ?? migrationResult.customers_imported} customers, and {migrationResult.salesImported ?? migrationResult.sales_imported} sales invoices.
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

      {/* Migration Error Detail Modal Overlay */}
      <ErrorDetailModal
        isOpen={Boolean(migrationError)}
        onClose={() => setMigrationError(null)}
        error={migrationError}
        context="migration"
      />
    </div>
  );
}
