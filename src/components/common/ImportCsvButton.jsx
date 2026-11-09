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

const ENTITY_SCHEMAS = {
  products: {
    title: 'Import Products',
    singular: 'product',
    required: [
      { name: 'Product Name', aliases: 'Name', desc: 'Product title / description' },
      { name: 'Product Code / SKU', aliases: 'SKU, Product Code', desc: 'Unique barcode or SKU identifier' },
      { name: 'MRP', aliases: 'Price, MRP/Price', desc: 'Maximum Retail Price / MRP (in ₹)' },
    ],
    optional: [
      { name: 'Category Name', aliases: 'Category, Division Name', desc: 'Category name (auto-created if new)' },
      { name: 'Cost Price', aliases: 'Avg Item Cost, Cost', desc: 'Wholesale purchase / cost price (in ₹)' },
      { name: 'Current Stock', aliases: 'Opening Stock, Stock', desc: 'Initial inventory quantity' },
      { name: 'Tax Rate', aliases: 'Tax%', desc: 'GST percentage rate (e.g. 5, 12, 18)' },
      { name: 'Stock Alert', aliases: 'Minimum Stock Level', desc: 'Low stock warning threshold' },
      { name: 'Division Code', aliases: 'Category Code', desc: 'Numeric division code to map category' },
    ],
  },
  categories: {
    title: 'Import Categories',
    singular: 'category',
    required: [
      { name: 'Category Name', aliases: 'Division Name, Name', desc: 'Unique category or division name' },
    ],
    optional: [
      { name: 'Division Code', aliases: 'Category Code, Code', desc: 'Numeric division identifier' },
    ],
  },
  customers: {
    title: 'Import Customers',
    singular: 'customer',
    required: [
      { name: 'Customer Name', aliases: 'Name', desc: 'Full name of customer or account' },
    ],
    optional: [
      { name: 'Contact Number', aliases: 'Phone, Mobile', desc: 'Primary contact or mobile number' },
      { name: 'Customer Address', aliases: 'Address', desc: 'Billing or street address' },
      { name: 'City', aliases: 'City', desc: 'City or district' },
      { name: 'Pin Code', aliases: 'Postal Code, Pincode', desc: 'Postal PIN code' },
      { name: 'Customer Code', aliases: 'Code, ID', desc: 'Customer account identifier' },
    ],
  },
  bills: {
    title: 'Import Bill History',
    singular: 'bill',
    compatibility: 'Supports both this application\'s own export files (CSV & Excel) and legacy billing data from external POS systems.',
    required: [
      {
        name: 'Invoice # / Bill Number',
        aliases: 'Invoice #, Invoice Number, Bill Number, Bill No, Invoice No',
        desc: 'Unique bill or invoice number (matches this app\'s export "Invoice #" and legacy formats)',
      },
      {
        name: 'Date / Bill Date',
        aliases: 'Date, Bill Date, Sale Date',
        desc: 'Sale date (DD/MM/YYYY, YYYY-MM-DD, or Month Date, Year)',
      },
      {
        name: 'Net Total (Rs) / Net Amount',
        aliases: 'Net Total (Rs), Net Total, Net Amount, Total Amount, Total, Amount',
        desc: 'Final bill total in ₹ (matches this app\'s export "Net Total (Rs)")',
      },
    ],
    optional: [
      {
        name: 'Time / Bill Time',
        aliases: 'Time, Bill Time',
        desc: 'Sale time (12-hour AM/PM or 24-hour HH:MM, defaults to 00:00:00)',
      },
      {
        name: 'Customer Name',
        aliases: 'Customer Name, Customer, Client',
        desc: 'Customer name (defaults to Walk-in Customer)',
      },
      {
        name: 'Customer Phone',
        aliases: 'Customer Phone, Phone, Mobile, Contact Number',
        desc: 'Customer contact phone (auto-linked to customer directory if found)',
      },
      {
        name: 'Payment Method',
        aliases: 'Payment Method, Payment Mode, Mode',
        desc: 'Payment mode (e.g. Cash, UPI, Card; inferred from bill # if omitted)',
      },
      {
        name: 'Subtotal (Rs)',
        aliases: 'Subtotal (Rs), Subtotal, Sub Total',
        desc: 'Pre-tax gross subtotal (defaults to Net Total if omitted)',
      },
      {
        name: 'Discount (Rs)',
        aliases: 'Discount (Rs), Discount, Disc',
        desc: 'Total discount amount in ₹ (defaults to 0.00)',
      },
      {
        name: 'Tax (Rs)',
        aliases: 'Tax (Rs), Tax, GST',
        desc: 'Total tax amount in ₹ (defaults to 0.00)',
      },
      {
        name: 'Status',
        aliases: 'Status, Bill Status',
        desc: 'Sale status (PAID, PARTIALLY_PAID, CANCELLED, REFUNDED; defaults to PAID)',
      },
      {
        name: 'Notes',
        aliases: 'Notes, Note, Remarks',
        desc: 'Optional bill memo or remarks',
      },
    ],
  },
};

export default function ImportCsvButton({
  entity = 'products',
  label = 'Import',
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
  const resolvedKey =
    entity in ENTITY_SCHEMAS
      ? entity
      : label?.toLowerCase().includes('categor')
      ? 'categories'
      : label?.toLowerCase().includes('customer')
      ? 'customers'
      : label?.toLowerCase().includes('bill')
      ? 'bills'
      : 'products';

  const schema = ENTITY_SCHEMAS[resolvedKey] || ENTITY_SCHEMAS.products;

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
      setError('Unsupported file type. Please upload a .csv, .xlsx, or .xls file.');
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
          throw new Error('The uploaded Excel workbook contains no sheets.');
        }
        const firstSheetName = workbook.SheetNames[0];
        const worksheet = workbook.Sheets[firstSheetName];
        csvContent = XLSX.utils.sheet_to_csv(worksheet);
        if (!csvContent || !csvContent.trim()) {
          throw new Error(`Sheet "${firstSheetName}" is empty.`);
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
      toast.success(`Import complete: ${res?.imported ?? 0} records imported.`);
      if (onComplete) onComplete(res);
      if (onSuccess) onSuccess(res);
    } catch (err) {
      const msg = typeof err === 'string' ? err : err?.message || 'Failed to parse and import file';
      setError(msg);
      toast.error(msg, { title: 'Import failed' });
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
        title={`Import ${resolvedKey} from CSV or Excel`}
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
                    Batch import {schema.singular} records from CSV or Excel
                  </p>
                </div>
              </div>
              {!isProcessing && (
                <button
                  type="button"
                  onClick={handleClose}
                  className="p-1.5 rounded-lg text-text-muted hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
                  title="Close modal"
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
                    <span>Import from CSV (.csv)</span>
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
                    <span>Import from Excel (.xlsx)</span>
                  </button>
                </div>
              )}

              {/* Warning / Strict Schema Notice */}
              <div className="flex items-start gap-3 p-3.5 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-800 dark:text-amber-200">
                <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5 text-amber-500" />
                <div className="text-xs leading-relaxed space-y-1">
                  {schema.compatibility && (
                    <div className="pb-1 border-b border-amber-500/20">
                      <span className="font-semibold">Format Support: </span>
                      <span>{schema.compatibility}</span>
                    </div>
                  )}
                  <div>
                    <span className="font-semibold block mb-0.5">Strict Schema Notice</span>
                    Only the supported fields listed below will be taken into the application and stored in the database. Any other columns or extra data present in your file will not be taken in or stored anywhere in the app.
                  </div>
                </div>
              </div>

              {/* Supported Fields (Required and Optional) */}
              <div className="space-y-2.5">
                <div className="flex items-center justify-between">
                  <h4 className="text-xs font-bold text-text-primary uppercase tracking-wider">
                    Supported Fields
                  </h4>
                  <span className="text-[11px] text-text-muted">
                    Headers are case-insensitive
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
                            Required
                          </span>
                        </div>
                        <div className="text-[11px] text-text-muted mt-0.5">
                          {col.desc} {col.aliases && <span className="text-text-secondary font-mono text-[10px] ml-1">(or {col.aliases})</span>}
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
                            Optional
                          </span>
                        </div>
                        <div className="text-[11px] text-text-muted mt-0.5">
                          {col.desc} {col.aliases && <span className="text-text-secondary font-mono text-[10px] ml-1">(or {col.aliases})</span>}
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
                  <p className="text-xs font-bold text-text-primary">Processing {selectedFile?.name}...</p>
                  <p className="text-[11px] text-text-muted mt-1 max-w-sm">
                    Parsing worksheet data, validating schema headers, and saving records to database...
                  </p>
                </div>
              )}

              {/* Error State */}
              {!isProcessing && error && (
                <div className="p-4 rounded-xl bg-danger-bg border border-danger/30 text-danger space-y-2">
                  <div className="flex items-center gap-2">
                    <XCircle className="w-5 h-5 shrink-0" />
                    <span className="font-bold text-xs">Import Failed</span>
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
                      Try another file
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
                        Successfully imported {result.imported} {schema.singular}{result.imported === 1 ? '' : 's'}
                      </div>
                      <div className="text-[11px] opacity-80 mt-0.5">
                        {result.total_rows} total rows processed from <span className="font-mono">{selectedFile?.name}</span>
                      </div>
                    </div>
                  </div>

                  {result.skipped > 0 && (
                    <div className="p-3.5 rounded-xl bg-warning-bg border border-warning/30 text-warning">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <AlertTriangle className="w-4 h-4 shrink-0" />
                          <span className="text-xs font-semibold">
                            {result.skipped} row{result.skipped === 1 ? '' : 's'} skipped
                          </span>
                        </div>
                        {result.errors && result.errors.length > 0 && (
                          <button
                            type="button"
                            onClick={() => setShowErrorsList(!showErrorsList)}
                            className="inline-flex items-center gap-1 text-xs hover:underline cursor-pointer"
                          >
                            <span>{showErrorsList ? 'Hide details' : 'View details'}</span>
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
                  Import Another File
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
                    {result ? 'Done' : 'Cancel'}
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
