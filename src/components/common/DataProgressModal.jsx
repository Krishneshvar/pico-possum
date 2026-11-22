import { useState } from 'react';
import {
  Database,
  FileSpreadsheet,
  FileText,
  HardDrive,
  CheckCircle2,
  XCircle,
  Loader2,
  AlertTriangle,
  ChevronDown,
  ChevronUp,
  Copy,
  Check,
  Lightbulb,
} from 'lucide-react';
import { strings } from '../../lib/strings';

/**
 * @typedef {Object} ProgressStep
 * @property {string} id
 * @property {string} label
 * @property {string} [detail]
 * @property {'pending'|'active'|'completed'|'error'} status
 */

/**
 * Reusable modal for showing real-time status, progress, and summaries
 * during data restore and import operations.
 *
 * @param {Object} props
 * @param {boolean} props.isOpen
 * @param {() => void} [props.onClose]
 * @param {string} props.title - e.g. "Restoring Database" or "Importing Products"
 * @param {'restore'|'import'} [props.operationType='import']
 * @param {string} [props.entityLabel] - e.g. "Products", "Categories", "Database Backup"
 * @param {string} [props.fileName] - Name of file being restored or imported
 * @param {string|number} [props.fileSize] - Optional formatted size
 * @param {number} [props.progressPercent=0] - 0 to 100
 * @param {string} [props.statusMessage] - Active status description
 * @param {ProgressStep[]} [props.steps=[]] - List of progress steps
 * @param {boolean} [props.isProcessing=true]
 * @param {Object|string|null} [props.result=null] - Success result payload
 * @param {Object|string|null} [props.error=null] - Error payload
 */
export default function DataProgressModal({
  isOpen,
  onClose,
  title,
  operationType = 'import',
  entityLabel = '',
  fileName = '',
  fileSize = null,
  progressPercent = 0,
  statusMessage = '',
  steps = [],
  isProcessing = true,
  result = null,
  error = null,
}) {
  const [showTechnicalError, setShowTechnicalError] = useState(false);
  const [copiedError, setCopiedError] = useState(false);

  if (!isOpen) return null;

  const isSuccess = !isProcessing && !error && Boolean(result);
  const isFailed = !isProcessing && Boolean(error);

  const getFileIcon = () => {
    if (operationType === 'restore' || fileName.endsWith('.db')) {
      return <Database className="w-5 h-5 text-brand" />;
    }
    if (fileName.endsWith('.xlsx') || fileName.endsWith('.xls')) {
      return <FileSpreadsheet className="w-5 h-5 text-emerald-500" />;
    }
    return <FileText className="w-5 h-5 text-blue-500" />;
  };

  const handleCopyError = () => {
    const textToCopy = typeof error === 'object' ? error.technical || error.message : String(error);
    navigator.clipboard.writeText(textToCopy);
    setCopiedError(true);
    setTimeout(() => setCopiedError(false), 2000);
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="progress-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in duration-150"
    >
      <div
        className="w-full max-w-lg rounded-2xl border border-border-default bg-surface shadow-2xl overflow-hidden flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150 text-text-primary"
        onClick={(e) => e.stopPropagation()}
      >
        {/* ── Modal Header ── */}
        <div className="px-6 pt-6 pb-4 border-b border-border-default bg-surface flex items-center gap-3.5">
          <div className="w-10 h-10 rounded-xl bg-brand/10 border border-brand/20 flex items-center justify-center shrink-0">
            {operationType === 'restore' ? (
              <HardDrive className="w-5 h-5 text-brand" />
            ) : (
              <Database className="w-5 h-5 text-brand" />
            )}
          </div>
          <div className="flex-1 min-w-0">
            <h3 id="progress-modal-title" className="text-sm font-bold text-text-primary truncate">
              {title}
            </h3>
            <p className="text-xs text-text-secondary truncate mt-0.5">
              {entityLabel ? `${entityLabel} • ` : ''}
              {isProcessing
                ? 'Operation in progress'
                : isSuccess
                ? 'Operation finished successfully'
                : 'Operation encountered an error'}
            </p>
          </div>
          {/* Status Badge */}
          <div>
            {isProcessing && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-brand/10 text-brand border border-brand/30">
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                <span>Running</span>
              </span>
            )}
            {isSuccess && (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/30">
                <CheckCircle2 className="w-3.5 h-3.5" />
                <span>Complete</span>
              </span>
            )}
            {isFailed && (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-danger-bg text-danger border border-danger/30">
                <XCircle className="w-3.5 h-3.5" />
                <span>Failed</span>
              </span>
            )}
          </div>
        </div>

        {/* ── Modal Body (Scrollable) ── */}
        <div className="p-6 space-y-5 overflow-y-auto">
          {/* File & Target Card */}
          {fileName && (
            <div className="p-3.5 rounded-xl border border-border-default bg-surface-secondary/40 flex items-center justify-between gap-3">
              <div className="flex items-center gap-3 min-w-0">
                <div className="w-8 h-8 rounded-lg bg-surface border border-border-default flex items-center justify-center shrink-0 shadow-2xs">
                  {getFileIcon()}
                </div>
                <div className="min-w-0">
                  <div className="text-xs font-semibold text-text-primary truncate font-mono">
                    {fileName}
                  </div>
                  <div className="text-[11px] text-text-muted mt-0.5 truncate">
                    {operationType === 'restore' ? 'Target: SQLite Database (Live)' : `Target: ${entityLabel || 'Database'}`}
                    {fileSize ? ` • ${fileSize}` : ''}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Progress Bar & Status Text */}
          <div className="space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-semibold text-text-primary">
                {statusMessage || (isProcessing ? 'Processing...' : isSuccess ? 'Finished' : 'Stopped')}
              </span>
              <span className="font-mono text-text-muted font-medium">{Math.min(100, Math.max(0, Math.round(progressPercent)))}%</span>
            </div>
            <div className="w-full h-2 rounded-full bg-surface-secondary border border-border-default overflow-hidden">
              <div
                className={`h-full transition-all duration-300 rounded-full ${
                  isFailed
                    ? 'bg-danger'
                    : isSuccess
                    ? 'bg-emerald-500'
                    : 'bg-brand'
                }`}
                style={{ width: `${Math.min(100, Math.max(isProcessing ? 5 : 0, progressPercent))}%` }}
              />
            </div>
          </div>

          {/* Steps Stepper */}
          {steps.length > 0 && (
            <div className="space-y-2">
              <div className="text-[11px] font-bold text-text-muted uppercase tracking-wider">
                Progress Steps
              </div>
              <div className="border border-border-default rounded-xl divide-y divide-border-default bg-surface overflow-hidden">
                {steps.map((step, idx) => {
                  const isStepActive = step.status === 'active';
                  const isStepDone = step.status === 'completed';
                  const isStepError = step.status === 'error';

                  return (
                    <div
                      key={step.id || idx}
                      className={`p-3 flex items-start gap-3 transition-colors ${
                        isStepActive ? 'bg-brand/5' : isStepDone ? 'bg-surface' : 'bg-surface-secondary/20'
                      }`}
                    >
                      <div className="mt-0.5 shrink-0">
                        {isStepDone && (
                          <div className="w-5 h-5 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center">
                            <CheckCircle2 className="w-4 h-4" />
                          </div>
                        )}
                        {isStepActive && (
                          <div className="w-5 h-5 rounded-full bg-brand/10 text-brand flex items-center justify-center">
                            <Loader2 className="w-3.5 h-3.5 animate-spin" />
                          </div>
                        )}
                        {isStepError && (
                          <div className="w-5 h-5 rounded-full bg-danger-bg text-danger flex items-center justify-center">
                            <XCircle className="w-4 h-4" />
                          </div>
                        )}
                        {!isStepDone && !isStepActive && !isStepError && (
                          <div className="w-5 h-5 rounded-full bg-surface-secondary border border-border-default flex items-center justify-center text-[10px] text-text-muted font-medium">
                            {idx + 1}
                          </div>
                        )}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div
                          className={`text-xs font-semibold ${
                            isStepActive
                              ? 'text-brand'
                              : isStepDone
                              ? 'text-text-primary'
                              : isStepError
                              ? 'text-danger'
                              : 'text-text-muted'
                          }`}
                        >
                          {step.label}
                        </div>
                        {step.detail && (
                          <div className="text-[11px] text-text-secondary mt-0.5 leading-relaxed">
                            {step.detail}
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Success Result Summary */}
          {isSuccess && (
            <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-text-primary space-y-2">
              <div className="flex items-center gap-2 text-emerald-600 dark:text-emerald-400 font-bold text-xs">
                <CheckCircle2 className="w-4 h-4" />
                <span>
                  {typeof result === 'string'
                    ? 'Success'
                    : result?.imported != null
                    ? strings.common.importCsv.successTitle(result.imported, entityLabel || 'record')
                    : 'Operation Completed Successfully'}
                </span>
              </div>
              <p className="text-xs text-text-secondary leading-relaxed">
                {typeof result === 'string'
                  ? result
                  : result?.totalRows != null
                  ? `${result.totalRows} total rows processed. ${result.imported} records imported.${
                      result.skipped ? ` (${result.skipped} skipped)` : ''
                    }`
                  : 'All records have been synchronized and written to disk.'}
              </p>
            </div>
          )}

          {/* Error Display */}
          {isFailed && (
            <div className="p-4 rounded-xl bg-danger-bg border border-danger/30 text-text-primary space-y-3">
              <div className="flex items-center gap-2 text-danger font-bold text-xs">
                <XCircle className="w-4 h-4" />
                <span>{typeof error === 'object' && error.title ? error.title : 'Operation Failed'}</span>
              </div>
              <p className="text-xs text-text-primary leading-relaxed bg-surface p-3 rounded-lg border border-border-default">
                {typeof error === 'object' ? error.message : String(error)}
              </p>

              {typeof error === 'object' && error.hint && (
                <div className="flex items-start gap-2.5 p-3 rounded-lg bg-brand-subtle border border-brand/20 text-text-primary text-xs">
                  <Lightbulb className="w-4 h-4 text-brand shrink-0 mt-0.5" />
                  <div>
                    <span className="font-bold text-brand block mb-0.5">How to resolve this:</span>
                    <span className="text-text-secondary leading-relaxed">{error.hint}</span>
                  </div>
                </div>
              )}

              {typeof error === 'object' && error.technical && (
                <div className="border border-border-default rounded-lg overflow-hidden bg-surface">
                  <button
                    type="button"
                    onClick={() => setShowTechnicalError(!showTechnicalError)}
                    className="w-full flex items-center justify-between px-3 py-1.5 text-[11px] text-text-secondary hover:text-text-primary bg-surface-secondary/50 transition-colors cursor-pointer"
                  >
                    <span className="flex items-center gap-1">
                      <AlertTriangle className="w-3 h-3 text-text-muted" />
                      <span>Technical Details</span>
                    </span>
                    {showTechnicalError ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                  </button>
                  {showTechnicalError && (
                    <div className="p-2.5 border-t border-border-default space-y-1.5 font-mono text-[10px] text-text-secondary break-all select-text">
                      <div className="flex justify-end">
                        <button
                          type="button"
                          onClick={handleCopyError}
                          className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-surface border border-border-default hover:bg-surface-secondary text-text-muted hover:text-text-primary cursor-pointer"
                        >
                          {copiedError ? <Check className="w-2.5 h-2.5 text-success" /> : <Copy className="w-2.5 h-2.5" />}
                          <span>{copiedError ? 'Copied' : 'Copy'}</span>
                        </button>
                      </div>
                      <pre className="whitespace-pre-wrap">{error.technical}</pre>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        {/* ── Modal Footer ── */}
        <div className="px-6 py-4 border-t border-border-default bg-surface flex items-center justify-between gap-3">
          <div className="text-[11px] text-text-muted">
            {isProcessing && 'Please do not exit the application.'}
          </div>
          <div>
            {!isProcessing && (
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 rounded-xl bg-brand hover:bg-brand-hover text-white text-xs font-semibold shadow-2xs transition-colors cursor-pointer focus:outline-none focus:ring-2 focus:ring-brand"
              >
                {isSuccess ? strings.common.actions.done : strings.common.actions.close}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
