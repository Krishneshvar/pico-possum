import { useState, useRef } from 'react';
import {
  Upload,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Loader2,
  X,
  ChevronDown,
  ChevronUp,
  FileSpreadsheet,
  FileText,
  ShieldAlert,
} from 'lucide-react';
import * as XLSX from 'xlsx';
import { tauriApi } from '../../lib/tauri';
import { toast } from '../../stores/toast.store';
import { getCurrencySymbol } from '../../lib/formatters';
import { strings } from '../../lib/strings';

const getEntitySchemas = (sym) => strings.common.importSchemas(sym);

export default function ImportCsvButton({
  entity = 'products',
  label = strings.common.actions.import,
  onComplete,
  onImport,
  onSuccess,
  className = '',
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [showErrorsList, setShowErrorsList] = useState(false);

  const fileInputCsvRef = useRef(null);
  const fileInputXlsxRef = useRef(null);

  // Resolve schema configuration
  const sym = getCurrencySymbol();
  const schemas = getEntitySchemas(sym);
  const resolvedKey =
    entity in schemas
      ? entity
      : label?.toLowerCase().includes('categor')
      ? 'categories'
      : label?.toLowerCase().includes('customer')
      ? 'customers'
      : label?.toLowerCase().includes('bill')
      ? 'bills'
      : 'products';

  const schema = schemas[resolvedKey] || schemas.products;

  const handleOpenModal = () => {
    setSelectedFile(null);
    setResult(null);
    setError(null);
    setShowErrorsList(false);
    setIsProcessing(false);
    setIsOpen(true);
  };

  const handleClose = () => {
    setIsOpen(false);
    setSelectedFile(null);
    setResult(null);
    setError(null);
    setIsProcessing(false);
  };

  const processFile = async (file) => {
    if (!file) return;

    const fileName = file.name.toLowerCase();
    const isCsv = fileName.endsWith('.csv');
    const isXlsx = fileName.endsWith('.xlsx') || fileName.endsWith('.xls');

    if (!isCsv && !isXlsx) {
      setError(strings.common.importCsv.unsupportedFileType);
      return;
    }

    setSelectedFile(file);
    setIsProcessing(true);
    setResult(null);
    setError(null);
    setShowErrorsList(false);

    try {
      let csvContent = '';
      if (isXlsx) {
        const arrayBuffer = await file.arrayBuffer();
        const workbook = XLSX.read(arrayBuffer, {
          type: 'array',
          cellDates: true,
          dateNF: 'yyyy-mm-dd',
        });
        if (!workbook.SheetNames || workbook.SheetNames.length === 0) {
          throw new Error(strings.common.importCsv.noSheets);
        }
        const firstSheetName = workbook.SheetNames[0];
        const worksheet = workbook.Sheets[firstSheetName];
        csvContent = XLSX.utils.sheet_to_csv(worksheet);
        if (!csvContent || !csvContent.trim()) {
          throw new Error(strings.common.importCsv.emptySheet(firstSheetName));
        }
      } else {
        csvContent = await file.text();
      }

      let res;
      if (onImport) {
        res = await onImport({
          csvContent,
          filePath: file.name,
          fileName: file.name,
        });
      } else if (resolvedKey === 'categories') {
        res = await tauriApi.importCsvCategories({ csvContent, filePath: file.name });
      } else if (resolvedKey === 'products') {
        res = await tauriApi.importCsvProducts({ csvContent, filePath: file.name });
      } else if (resolvedKey === 'customers') {
        res = await tauriApi.importCsvCustomers({ csvContent, filePath: file.name });
      } else if (resolvedKey === 'bills') {
        res = await tauriApi.importCsvBills({
          csvContent,
          filePath: file.name,
          fileName: file.name,
        });
      }

      setResult(res);
      toast.success(strings.common.importCsv.importComplete(res?.imported ?? 0));
      if (onComplete) onComplete(res);
      if (onSuccess) onSuccess(res);
    } catch (err) {
      const msg = typeof err === 'string' ? err : err?.message || strings.common.importCsv.parseFailed;
      setError(msg);
      toast.error(msg, { title: strings.common.importCsv.importFailedTitle });
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <>
      {/* Hidden file inputs */}
      <input
        ref={fileInputCsvRef}
        type="file"
        accept=".csv,text/csv"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) processFile(file);
        }}
      />
      <input
        ref={fileInputXlsxRef}
        type="file"
        accept=".xlsx,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) processFile(file);
        }}
      />

      {/* Trigger Button */}
      <button
        type="button"
        onClick={handleOpenModal}
        className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-border-default bg-surface hover:bg-surface-secondary text-text-secondary hover:text-text-primary transition-colors shadow-2xs cursor-pointer ${className}`}
        title={strings.common.importCsv.triggerTooltip(resolvedKey)}
      >
        <Upload className="w-3.5 h-3.5 text-text-muted" />
        <span>{label}</span>
      </button>

      {/* Import Modal */}
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-fade-in">
          <div className="relative w-full max-w-xl max-h-[90vh] bg-surface border border-border-default rounded-2xl shadow-2xl overflow-hidden flex flex-col text-text-primary animate-scale-in">
            {/* Modal Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-border-default shrink-0 bg-surface">
              <div className="flex items-center gap-3">
                <div className="p-2.5 rounded-xl bg-brand-subtle text-brand border border-brand/20">
                  <FileSpreadsheet className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-bold text-text-primary text-base">{schema.title}</h3>
                  <p className="text-xs text-text-muted">
                    {strings.common.importCsv.modalSubtitle(schema.singular)}
                  </p>
                </div>
              </div>
              {!isProcessing && (
                <button
                  type="button"
                  onClick={handleClose}
                  className="p-1.5 rounded-lg text-text-muted hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
                  title={strings.common.importCsv.closeModal}
                >
                  <X className="w-5 h-5" />
                </button>
              )}
            </div>

            {/* Modal Scrollable Content */}
            <div className="flex-1 overflow-y-auto p-6 space-y-4">
              {/* Top: Sleek Import Action Buttons */}
              {!isProcessing && !result && (
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => {
                      if (fileInputCsvRef.current) {
                        fileInputCsvRef.current.value = '';
                        fileInputCsvRef.current.click();
                      }
                    }}
                    className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl border border-border-default bg-surface hover:bg-surface-secondary text-text-primary text-xs font-semibold shadow-2xs hover:border-brand/40 transition-colors cursor-pointer group"
                  >
                    <FileText className="w-4 h-4 text-blue-500 group-hover:scale-110 transition-transform" />
                    <span>{strings.common.importCsv.importFromCsv}</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      if (fileInputXlsxRef.current) {
                        fileInputXlsxRef.current.value = '';
                        fileInputXlsxRef.current.click();
                      }
                    }}
                    className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl border border-border-default bg-surface hover:bg-surface-secondary text-text-primary text-xs font-semibold shadow-2xs hover:border-emerald-500/40 transition-colors cursor-pointer group"
                  >
                    <FileSpreadsheet className="w-4 h-4 text-emerald-500 group-hover:scale-110 transition-transform" />
                    <span>{strings.common.importCsv.importFromExcel}</span>
                  </button>
                </div>
              )}

              {/* Warning / Strict Schema Notice */}
              <div className="flex items-start gap-3 p-3.5 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-800 dark:text-amber-200">
                <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5 text-amber-500" />
                <div className="text-xs leading-relaxed space-y-1">
                  {schema.compatibility && (
                    <div className="pb-1 border-b border-amber-500/20">
                      <span className="font-semibold">{strings.common.importCsv.formatSupport}</span>
                      <span>{schema.compatibility}</span>
                    </div>
                  )}
                  <div>
                    <span className="font-semibold block mb-0.5">{strings.common.importCsv.strictSchemaTitle}</span>
                    {strings.common.importCsv.strictSchemaBody}
                  </div>
                </div>
              </div>

              {/* Supported Fields (Required and Optional) */}
              <div className="space-y-2.5">
                <div className="flex items-center justify-between">
                  <h4 className="text-xs font-bold text-text-primary uppercase tracking-wider">
                    {strings.common.importCsv.supportedFields}
                  </h4>
                  <span className="text-[11px] text-text-muted">
                    {strings.common.importCsv.headersCaseInsensitive}
                  </span>
                </div>

                <div className="border border-border-default rounded-xl overflow-hidden divide-y divide-border-default bg-surface text-xs">
                  {/* Required Fields */}
                  {schema.required?.map((col) => (
                    <div key={col.name} className="p-2.5 flex items-center justify-between gap-3 bg-surface hover:bg-surface-secondary/40 transition-colors">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-text-primary font-mono">{col.name}</span>
                          <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-danger-bg text-danger border border-danger/30">
                            {strings.common.importCsv.required}
                          </span>
                        </div>
                        <div className="text-[11px] text-text-muted mt-0.5">
                          {col.desc} {col.aliases && <span className="text-text-secondary font-mono text-[10px] ml-1">{strings.common.importCsv.aliases(col.aliases)}</span>}
                        </div>
                      </div>
                    </div>
                  ))}

                  {/* Optional Fields */}
                  {schema.optional?.map((col) => (
                    <div key={col.name} className="p-2.5 flex items-center justify-between gap-3 bg-surface hover:bg-surface-secondary/40 transition-colors">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-text-secondary font-mono">{col.name}</span>
                          <span className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-surface-secondary text-text-muted border border-border-default">
                            {strings.common.importCsv.optional}
                          </span>
                        </div>
                        <div className="text-[11px] text-text-muted mt-0.5">
                          {col.desc} {col.aliases && <span className="text-text-secondary font-mono text-[10px] ml-1">{strings.common.importCsv.aliases(col.aliases)}</span>}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Processing Spinner State */}
              {isProcessing && (
                <div className="flex flex-col items-center justify-center py-8 text-center bg-surface-secondary/30 rounded-xl border border-border-default">
                  <Loader2 className="w-8 h-8 text-brand animate-spin mb-3" />
                  <p className="text-xs font-bold text-text-primary">{strings.common.importCsv.processingFile(selectedFile?.name)}</p>
                  <p className="text-[11px] text-text-muted mt-1 max-w-sm">
                    {strings.common.importCsv.processingDetail}
                  </p>
                </div>
              )}

              {/* Error State */}
              {!isProcessing && error && (
                <div className="p-4 rounded-xl bg-danger-bg border border-danger/30 text-danger space-y-2">
                  <div className="flex items-center gap-2">
                    <XCircle className="w-5 h-5 shrink-0" />
                    <span className="font-bold text-xs">{strings.common.importCsv.importFailed}</span>
                  </div>
                  <p className="text-xs text-text-secondary whitespace-pre-wrap font-mono bg-surface p-2.5 rounded-lg border border-border-default">
                    {error}
                  </p>
                  <div className="pt-1 flex justify-end">
                    <button
                      type="button"
                      onClick={() => {
                        setError(null);
                        setSelectedFile(null);
                      }}
                      className="text-xs font-semibold hover:underline cursor-pointer"
                    >
                      {strings.common.importCsv.tryAnotherFile}
                    </button>
                  </div>
                </div>
              )}

              {/* Success Result State */}
              {!isProcessing && result && (
                <div className="space-y-3">
                  <div className="flex items-center gap-3 p-4 rounded-xl bg-success-bg border border-success/30 text-success">
                    <CheckCircle2 className="w-6 h-6 shrink-0" />
                    <div>
                      <div className="font-bold text-sm">
                        {strings.common.importCsv.successTitle(result.imported, schema.singular)}
                      </div>
                      <div className="text-[11px] opacity-80 mt-0.5">
                        {strings.common.importCsv.totalRows(result.total_rows)}<span className="font-mono">{selectedFile?.name}</span>
                      </div>
                    </div>
                  </div>

                  {result.skipped > 0 && (
                    <div className="p-3.5 rounded-xl bg-warning-bg border border-warning/30 text-warning">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <AlertTriangle className="w-4 h-4 shrink-0" />
                          <span className="text-xs font-semibold">
                            {strings.common.importCsv.skippedRows(result.skipped)}
                          </span>
                        </div>
                        {result.errors && result.errors.length > 0 && (
                          <button
                            type="button"
                            onClick={() => setShowErrorsList(!showErrorsList)}
                            className="inline-flex items-center gap-1 text-xs hover:underline cursor-pointer"
                          >
                            <span>{showErrorsList ? strings.common.actions.hideDetails : strings.common.actions.viewDetails}</span>
                            {showErrorsList ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                          </button>
                        )}
                      </div>

                      {showErrorsList && result.errors && result.errors.length > 0 && (
                        <div className="mt-2.5 max-h-40 overflow-y-auto space-y-1 text-[11px] bg-surface p-2.5 rounded-lg border border-warning/20 font-mono text-text-secondary">
                          {result.errors.map((errStr, idx) => (
                            <div key={idx} className="truncate">
                              {errStr}
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Modal Footer */}
            <div className="flex items-center justify-between px-6 py-3.5 border-t border-border-default shrink-0 bg-surface">
              {result && !isProcessing ? (
                <button
                  type="button"
                  onClick={() => {
                    setResult(null);
                    setSelectedFile(null);
                    setError(null);
                  }}
                  className="px-3 py-1.5 text-xs font-medium rounded-lg border border-border-default bg-surface hover:bg-surface-secondary text-text-secondary cursor-pointer transition-colors"
                >
                  {strings.common.importCsv.importAnotherFile}
                </button>
              ) : (
                <div />
              )}

              <div className="flex items-center gap-2">
                {!isProcessing && (
                  <button
                    type="button"
                    onClick={handleClose}
                    className="px-4 py-1.5 text-xs font-semibold rounded-lg bg-brand hover:bg-brand-hover text-white transition-colors cursor-pointer shadow-xs"
                  >
                    {result ? strings.common.importCsv.done : strings.common.actions.cancel}
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
