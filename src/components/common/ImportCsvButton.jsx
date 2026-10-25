import React, { useState, useRef } from 'react';
import { Upload, CheckCircle2, AlertTriangle, XCircle, Loader2, X, ChevronDown, ChevronUp, FileSpreadsheet } from 'lucide-react';
import { tauriApi } from '../../lib/tauri';

export default function ImportCsvButton({
  entity = 'products', // 'products' | 'categories' | 'customers' | 'bills'
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
        res = await tauriApi.importCsvCategories({ csvContent });
      } else if (entity === 'products') {
        res = await tauriApi.importCsvProducts({ csvContent });
      } else if (entity === 'customers') {
        res = await tauriApi.importCsvCustomers({ csvContent });
      } else if (entity === 'bills') {
        res = await tauriApi.importCsvBills({
          csvContent,
          fileName: file.name,
        });
      }

      setResult(res);
      if (onComplete) {
        onComplete(res);
      }
    } catch (err) {
      console.error('CSV import error:', err);
      setError(typeof err === 'string' ? err : err?.message || 'Failed to import CSV file');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleClose = () => {
    setIsOpen(false);
    setSelectedFile(null);
    setResult(null);
    setError(null);
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
        className={`inline-flex items-center gap-2 px-3.5 py-2 text-sm font-medium rounded-lg border border-slate-700 bg-slate-800/80 text-slate-200 hover:bg-slate-700 hover:text-white transition-all shadow-sm cursor-pointer ${className}`}
        title={`Import ${entity} from CSV file`}
      >
        <Upload className="w-4 h-4 text-emerald-400" />
        <span>{buttonLabel}</span>
      </button>

      {/* Progress / Result Modal */}
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
          <div className="relative w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl overflow-hidden p-6 text-slate-100 animate-scale-in">
            {/* Header */}
            <div className="flex items-center justify-between pb-4 border-b border-slate-800">
              <div className="flex items-center gap-3">
                <div className="p-2.5 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  <FileSpreadsheet className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-semibold text-slate-100 text-base">{getEntityTitle()}</h3>
                  <p className="text-xs text-slate-400 truncate max-w-[240px]">
                    {selectedFile?.name || 'Processing CSV file...'}
                  </p>
                </div>
              </div>
              {!isProcessing && (
                <button
                  type="button"
                  onClick={handleClose}
                  className="p-1 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              )}
            </div>

            {/* Body */}
            <div className="py-6">
              {isProcessing && (
                <div className="flex flex-col items-center justify-center py-6 text-center">
                  <Loader2 className="w-10 h-10 text-emerald-400 animate-spin mb-4" />
                  <p className="text-sm font-medium text-slate-200">Parsing and importing records...</p>
                  <p className="text-xs text-slate-400 mt-1">
                    Validating headers, updating data, and recording stock audit logs
                  </p>
                </div>
              )}

              {!isProcessing && error && (
                <div className="flex flex-col items-center text-center py-4">
                  <div className="p-3 rounded-full bg-rose-500/10 border border-rose-500/20 text-rose-400 mb-3">
                    <XCircle className="w-8 h-8" />
                  </div>
                  <h4 className="font-semibold text-rose-300 text-sm mb-1">Import Failed</h4>
                  <p className="text-xs text-slate-300 max-w-sm px-2 py-2 bg-slate-950/60 rounded-lg border border-rose-900/40 text-left overflow-x-auto whitespace-pre-wrap">
                    {error}
                  </p>
                </div>
              )}

              {!isProcessing && result && (
                <div className="space-y-4">
                  <div className="flex items-center gap-3 p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-300">
                    <CheckCircle2 className="w-6 h-6 shrink-0 text-emerald-400" />
                    <div>
                      <div className="font-semibold text-sm">
                        Successfully imported {result.imported} record{result.imported === 1 ? '' : 's'}
                      </div>
                      <div className="text-xs text-emerald-400/80">
                        Total {result.total_rows} data rows processed
                      </div>
                    </div>
                  </div>

                  {result.skipped > 0 && (
                    <div className="p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-300">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0" />
                          <span className="text-xs font-medium">
                            {result.skipped} row{result.skipped === 1 ? '' : 's'} skipped
                          </span>
                        </div>
                        {result.errors && result.errors.length > 0 && (
                          <button
                            type="button"
                            onClick={() => setShowErrorsList(!showErrorsList)}
                            className="inline-flex items-center gap-1 text-xs text-amber-400 hover:text-amber-200 transition-colors"
                          >
                            <span>{showErrorsList ? 'Hide details' : 'View details'}</span>
                            {showErrorsList ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                          </button>
                        )}
                      </div>

                      {showErrorsList && result.errors && result.errors.length > 0 && (
                        <div className="mt-3 max-h-40 overflow-y-auto space-y-1 text-[11px] text-slate-300 bg-slate-950/80 p-2.5 rounded-lg border border-amber-500/20 font-mono">
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
            <div className="flex justify-end gap-3 pt-4 border-t border-slate-800">
              {!isProcessing && (
                <button
                  type="button"
                  onClick={handleClose}
                  className="px-4 py-2 text-sm font-medium rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white transition-colors cursor-pointer shadow-sm"
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
