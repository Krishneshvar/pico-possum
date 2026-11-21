import { useQuery, useMutation } from '@tanstack/react-query';
import { Printer, RefreshCw, Check, Info } from 'lucide-react';
import Select from '../../../components/common/Select';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { strings } from '../../../lib/strings';

export default function PrintingTab({ general, setGeneral, onSave, isSaving }) {
  const { data: printers = [], isLoading: scanningPrinters, refetch: refreshPrinters } = useQuery({
    queryKey: ['systemPrinters'],
    queryFn: () => tauriApi.listPrinters(),
  });

  const testPrintMutation = useMutation({
    mutationFn: (printerName) => tauriApi.testPrint(printerName),
    onSuccess: () => toast.success(strings.settings.toasts.testPrintSent),
    onError: (err) =>
      toast.error(strings.settings.toasts.testPrintFailed(err.message), {
        title: strings.settings.toasts.testPrintFailedTitle,
      }),
  });

  return (
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
              {(general.defaultPrinterName ?? general.default_printer_name)
                ? strings.settings.printing.activeDefault(general.defaultPrinterName ?? general.default_printer_name)
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
              value={general.defaultPrinterName ?? general.default_printer_name ?? ''}
              onChange={(val) => setGeneral({ ...general, defaultPrinterName: val })}
              placeholder={strings.settings.printing.osDefaultOption}
              options={[
                { value: '', label: strings.settings.printing.osDefaultOption },
                ...printers.map((p) => {
                  const name = typeof p === 'string' ? p : p?.name || '';
                  const isDef = typeof p === 'object' ? Boolean(p?.isDefault ?? p?.is_default) : false;
                  return {
                    value: name,
                    label: strings.settings.printing.printerOption(name, isDef),
                  };
                }),
              ]}
            />
            <span className="text-[11px] text-content-muted mt-1 block">
              {strings.settings.printing.printerHint}
            </span>
          </div>

          <div className="pt-2 flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={() => onSave(general)}
              disabled={isSaving}
              className="px-5 py-2.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-medium text-xs flex items-center gap-2 transition-colors cursor-pointer"
            >
              {isSaving ? (
                <RefreshCw className="w-4 h-4 animate-spin" />
              ) : (
                <Check className="w-4 h-4" />
              )}
              <span>{strings.settings.printing.saveDefaultPrinter}</span>
            </button>

            <button
              type="button"
              onClick={() => testPrintMutation.mutate((general.defaultPrinterName ?? general.default_printer_name) || null)}
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
  );
}
