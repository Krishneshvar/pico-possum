import { useState, useRef, useEffect } from 'react';
import {
  Upload,
  X,
  FileSpreadsheet,
  FileText,
  ShieldAlert,
} from 'lucide-react';
import { tauriApi } from '../../lib/tauri';
import { toast } from '../../stores/toast.store';
import { getCurrencySymbol } from '../../lib/formatters';
import { formatHumanError } from '../../lib/formatHumanError';
import { strings } from '../../lib/strings';
import DataProgressModal from './DataProgressModal';

const getEntitySchemas = (sym) => strings.common.importSchemas(sym);

const createImportSteps = (activeStep = 'read', errorDetail = null) => [
  {
    id: 'read',
    label: 'Read and parse file content',
    detail: 'Reading bytes and extracting spreadsheet rows',
    status: activeStep === 'read' ? 'active' : 'completed',
  },
  {
    id: 'validate',
    label: 'Validate schema headers & field constraints',
    detail: 'Checking columns against required schema definition',
    status:
      activeStep === 'validate'
        ? 'active'
        : ['insert', 'complete'].includes(activeStep)
        ? 'completed'
        : 'pending',
  },
  {
    id: 'insert',
    label: 'Save records to database',
    detail: errorDetail || 'Executing SQLite batch transaction and inventory movements',
    status:
      activeStep === 'insert'
        ? 'active'
        : activeStep === 'complete'
        ? 'completed'
        : activeStep === 'error'
        ? 'error'
        : 'pending',
  },
  {
    id: 'finalize',
    label: 'Finalize & sync catalog',
    detail: 'Refreshing application cache and query stores',
    status: activeStep === 'complete' ? 'completed' : 'pending',
  },
];

export default function ImportCsvButton({
  entity = 'products',
  label = strings.common.actions.import,
  onComplete,
  onImport,
  onSuccess,
  className = '',
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [progressModal, setProgressModal] = useState({
    isOpen: false,
    fileName: '',
    fileSize: null,
    progressPercent: 0,
    statusMessage: '',
    steps: [],
    isProcessing: false,
    result: null,
    error: null,
  });

  const fileInputCsvRef = useRef(null);
  const fileInputXlsxRef = useRef(null);
  const firstImportBtnRef = useRef(null);

  useEffect(() => {
    if (isOpen) {
      const timer = setTimeout(() => {
        firstImportBtnRef.current?.focus();
      }, 50);
      return () => clearTimeout(timer);
    }
  }, [isOpen]);

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
    setIsOpen(true);
  };

  const handleClose = () => {
    setIsOpen(false);
  };

  const processFile = async (file) => {
    if (!file) return;

    const fileName = file.name.toLowerCase();
    const isCsv = fileName.endsWith('.csv');
    const isXlsx = fileName.endsWith('.xlsx') || fileName.endsWith('.xls');

    if (!isCsv && !isXlsx) {
      toast.error(strings.common.importCsv.unsupportedFileType);
      return;
    }

    setIsOpen(false);
    setProgressModal({
      isOpen: true,
      fileName: file.name,
      fileSize: file.size ? `${(file.size / 1024).toFixed(1)} KB` : null,
      progressPercent: 25,
      statusMessage: `Reading and parsing ${file.name}...`,
      steps: createImportSteps('read'),
      isProcessing: true,
      result: null,
      error: null,
    });

    try {
      let csvContent = '';
      if (isXlsx) {
        setProgressModal((prev) => ({
          ...prev,
          progressPercent: 35,
          statusMessage: 'Extracting spreadsheet rows from Excel workbook...',
        }));
        const XLSX = await import('xlsx');
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

      setProgressModal((prev) => ({
        ...prev,
        progressPercent: 55,
        statusMessage: 'Validating column headers and schema rules...',
        steps: createImportSteps('validate'),
      }));

      setProgressModal((prev) => ({
        ...prev,
        progressPercent: 80,
        statusMessage: 'Saving records and stock movements to database...',
        steps: createImportSteps('insert'),
      }));

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

      setProgressModal({
        isOpen: true,
        fileName: file.name,
        fileSize: file.size ? `${(file.size / 1024).toFixed(1)} KB` : null,
        progressPercent: 100,
        statusMessage: `Import complete! ${res?.imported ?? 0} records imported.`,
        steps: createImportSteps('complete'),
        isProcessing: false,
        result: res,
        error: null,
      });

      toast.success(strings.common.importCsv.importComplete(res?.imported ?? 0));
      if (onComplete) onComplete(res);
      if (onSuccess) onSuccess(res);
    } catch (err) {
      const parsed = formatHumanError(err, `import_${resolvedKey}`);
      setProgressModal({
        isOpen: true,
        fileName: file.name,
        fileSize: file.size ? `${(file.size / 1024).toFixed(1)} KB` : null,
        progressPercent: 100,
        statusMessage: 'Import failed',
        steps: [
          { id: 'read', label: 'Read and parse file content', status: 'completed' },
          { id: 'insert', label: 'Save records to database', status: 'error', detail: parsed.message },
        ],
        isProcessing: false,
        result: null,
        error: parsed,
      });
      toast.error(parsed.message, { title: parsed.title });
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
              <div className="flex items-center gap-3">
                <button
                  ref={firstImportBtnRef}
                  type="button"
                  onClick={() => {
                    if (fileInputCsvRef.current) {
                      fileInputCsvRef.current.value = '';
                      fileInputCsvRef.current.click();
                    }
                  }}
                  className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl border border-border-default bg-surface hover:bg-surface-secondary text-text-primary text-xs font-semibold shadow-2xs hover:border-brand/40 transition-colors cursor-pointer group focus:outline-none focus:ring-2 focus:ring-brand"
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
            </div>

            {/* Modal Footer */}
            <div className="flex items-center justify-end px-6 py-3.5 border-t border-border-default shrink-0 bg-surface">
              <button
                type="button"
                onClick={handleClose}
                className="px-4 py-1.5 text-xs font-semibold rounded-lg bg-surface border border-border-default hover:bg-surface-secondary text-text-secondary transition-colors cursor-pointer"
              >
                {strings.common.actions.close}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Real-time Import Progress & Status Modal */}
      <DataProgressModal
        isOpen={progressModal.isOpen}
        onClose={() => setProgressModal((prev) => ({ ...prev, isOpen: false }))}
        title={`Importing ${schema.title || schema.singular || 'Records'}`}
        operationType="import"
        entityLabel={schema.singular || 'Records'}
        fileName={progressModal.fileName}
        fileSize={progressModal.fileSize}
        progressPercent={progressModal.progressPercent}
        statusMessage={progressModal.statusMessage}
        steps={progressModal.steps}
        isProcessing={progressModal.isProcessing}
        result={progressModal.result}
        error={progressModal.error}
      />
    </>
  );
}
