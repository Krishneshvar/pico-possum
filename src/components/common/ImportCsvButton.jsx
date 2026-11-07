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
} from 'lucide-react';
import { tauriApi } from '../../lib/tauri';
import { toast } from '../../stores/toast.store';

export default function ImportCsvButton({
  entity = 'products',
  label,
  onComplete,
  className = '',
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [showErrorsList, setShowErrorsList] = useState(false);
  const fileInputRef = useRef(null);

  const defaultLabel = entity === 'bills' ? 'Import Legacy CSV' : 'Import CSV';
  const buttonLabel = label || defaultLabel;

  const getEntityTitle = () => {
    switch (entity) {
      case 'categories':
        return 'Import Categories from CSV';
      case 'products':
        return 'Import Products from CSV';
      case 'customers':
        return 'Import Customers from CSV';
      case 'bills':
        return 'Import Legacy Bills (Serieswise CSV)';
      default:
        return 'Import CSV';
    }
  };

  const handleButtonClick = () => {
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
      fileInputRef.current.click();
    }
  };

  const handleFileChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setSelectedFile(file);
    setIsOpen(true);
    setIsProcessing(true);
    setResult(null);
    setError(null);
    setShowErrorsList(false);

    try {
      const csvContent = await file.text();
      let res;

      if (entity === 'categories') {
        res = await tauriApi.importCsvCategories({ csvContent, filePath: file.name });
      } else if (entity === 'products') {
        res = await tauriApi.importCsvProducts({ csvContent, filePath: file.name });
      } else if (entity === 'customers') {
        res = await tauriApi.importCsvCustomers({ csvContent, filePath: file.name });
      } else if (entity === 'bills') {
        res = await tauriApi.importCsvBills({
          csvContent,
          filePath: file.name,
          fileName: file.name,
        });
      }

      setResult(res);
      toast.success(`Import complete: ${res.imported} records imported.`);
      if (onComplete) onComplete(res);
    } catch (err) {
      const msg = typeof err === 'string' ? err : err?.message || 'Failed to parse and import CSV file';
      setError(msg);
      toast.error(msg, { title: 'Import failed' });
    } finally {
      setIsProcessing(false);
    }
  };

  const handleClose = () => {
    setIsOpen(false);
    setSelectedFile(null);
    setResult(null);
    setError(null);
    setIsProcessing(false);
  };

  return (
    <>
      <input
        ref={fileInputRef}
        type="file"
        accept=".csv,text/csv"
        className="hidden"
        onChange={handleFileChange}
      />

      <button
        type="button"
        onClick={handleButtonClick}
        className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-border-default bg-surface hover:bg-surface-secondary text-text-secondary hover:text-text-primary transition-colors shadow-2xs cursor-pointer ${className}`}
        title={`Import ${entity} from CSV file`}
      >
        <Upload className="w-3.5 h-3.5 text-text-muted" />
        <span>{buttonLabel}</span>
      </button>

      {/* Progress / Result Modal */}
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs animate-fade-in">
          <div className="relative w-full max-w-md bg-surface border border-border-default rounded-2xl shadow-xl overflow-hidden p-6 text-text-primary animate-scale-in">
            {/* Header */}
            <div className="flex items-center justify-between pb-4 border-b border-border-default">
              <div className="flex items-center gap-3">
                <div className="p-2.5 rounded-xl bg-brand-subtle text-brand border border-brand/20">
                  <FileSpreadsheet className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-bold text-text-primary text-sm">{getEntityTitle()}</h3>
                  <p className="text-xs text-text-muted truncate max-w-[240px]">
                    {selectedFile?.name || 'Processing CSV file...'}
                  </p>
                </div>
              </div>
              {!isProcessing && (
                <button
                  type="button"
                  onClick={handleClose}
                  className="p-1 rounded-lg text-text-muted hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
                >
                  <X className="w-5 h-5" />
                </button>
              )}
            </div>

            {/* Body */}
            <div className="py-5">
              {isProcessing && (
                <div className="flex flex-col items-center justify-center py-6 text-center">
                  <Loader2 className="w-8 h-8 text-brand animate-spin mb-3" />
                  <p className="text-xs font-semibold text-text-primary">Parsing and importing records...</p>
                  <p className="text-[11px] text-text-muted mt-1">
                    Validating headers, updating data, and recording audit movements
                  </p>
                </div>
              )}

              {!isProcessing && error && (
                <div className="flex flex-col items-center text-center py-2">
                  <div className="p-3 rounded-full bg-danger-bg text-danger mb-3">
                    <XCircle className="w-7 h-7" />
                  </div>
                  <h4 className="font-bold text-danger text-sm mb-1">Import Failed</h4>
                  <p className="text-xs text-text-secondary max-w-sm px-3 py-2 bg-surface-secondary rounded-lg border border-border-default text-left overflow-x-auto whitespace-pre-wrap font-mono">
                    {error}
                  </p>
                </div>
              )}

              {!isProcessing && result && (
                <div className="space-y-3">
                  <div className="flex items-center gap-3 p-3.5 rounded-xl bg-success-bg border border-success/30 text-success">
                    <CheckCircle2 className="w-5 h-5 shrink-0" />
                    <div>
                      <div className="font-semibold text-xs">
                        Successfully imported {result.imported} record{result.imported === 1 ? '' : 's'}
                      </div>
                      <div className="text-[11px] opacity-80">
                        Total {result.total_rows} data rows processed
                      </div>
                    </div>
                  </div>

                  {result.skipped > 0 && (
                    <div className="p-3 rounded-xl bg-warning-bg border border-warning/30 text-warning">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <AlertTriangle className="w-4 h-4 shrink-0" />
                          <span className="text-xs font-medium">
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
                            {showErrorsList ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                          </button>
                        )}
                      </div>

                      {showErrorsList && result.errors && result.errors.length > 0 && (
                        <div className="mt-2.5 max-h-36 overflow-y-auto space-y-1 text-[11px] bg-surface p-2 rounded-lg border border-warning/20 font-mono">
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

            {/* Footer */}
            <div className="flex justify-end gap-2 pt-3 border-t border-border-default">
              {!isProcessing && (
                <button
                  type="button"
                  onClick={handleClose}
                  className="px-4 py-1.5 text-xs font-semibold rounded-lg bg-brand hover:bg-brand-hover text-white transition-colors cursor-pointer shadow-xs"
                >
                  Done
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
