import { useState } from 'react';
import {
  XCircle,
  AlertTriangle,
  Lightbulb,
  ChevronDown,
  ChevronUp,
  Copy,
  Check,
  X,
  RotateCcw,
  Upload,
} from 'lucide-react';
import { formatHumanError } from '../../lib/formatHumanError';
import { strings } from '../../lib/strings';

export default function ErrorDetailModal({
  isOpen,
  onClose,
  error,
  context = 'general',
  title: customTitle,
  onRetry,
  onChooseAnother,
  retryLabel = strings.common.actions.tryAgain,
  chooseAnotherLabel = strings.common.importCsv.tryAnotherFile,
}) {
  const [showTechnical, setShowTechnical] = useState(false);
  const [copied, setCopied] = useState(false);

  if (!isOpen || !error) return null;

  const parsed = formatHumanError(error, context);
  const displayTitle = customTitle || parsed.title;

  const handleCopyTechnical = () => {
    if (!parsed.technical) return;
    navigator.clipboard.writeText(parsed.technical);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="error-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-fade-in"
    >
      <div className="relative w-full max-w-lg bg-surface border border-border-default rounded-2xl shadow-2xl overflow-hidden flex flex-col text-text-primary animate-scale-in">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-border-default bg-danger-bg/40">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-xl bg-danger text-white shrink-0 shadow-xs">
              <XCircle className="w-5 h-5" />
            </div>
            <div>
              <h3 id="error-modal-title" className="font-bold text-text-primary text-base">
                {displayTitle}
              </h3>
              <p className="text-xs text-text-muted">
                {strings.common.toast.types.error}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-text-muted hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
            title={strings.common.actions.close}
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body */}
        <div className="p-6 space-y-4 max-h-[75vh] overflow-y-auto text-xs">
          {/* Plain Human Readable Explanation */}
          <div className="p-4 rounded-xl bg-surface-secondary border border-border-default text-text-primary text-sm leading-relaxed">
            {parsed.message}
          </div>

          {/* Actionable Hint / Solution */}
          {parsed.hint && (
            <div className="flex items-start gap-3 p-4 rounded-xl bg-brand-subtle border border-brand/20 text-text-primary">
              <Lightbulb className="w-5 h-5 text-brand shrink-0 mt-0.5" />
              <div className="space-y-1">
                <span className="font-bold text-brand block text-xs">
                  How to resolve this:
                </span>
                <p className="text-xs text-text-secondary leading-relaxed">
                  {parsed.hint}
                </p>
              </div>
            </div>
          )}

          {/* Collapsible Technical Details */}
          {parsed.technical && (
            <div className="border border-border-default rounded-xl overflow-hidden">
              <button
                type="button"
                onClick={() => setShowTechnical(!showTechnical)}
                className="w-full flex items-center justify-between px-3.5 py-2.5 bg-surface-secondary text-text-secondary hover:text-text-primary text-xs font-medium transition-colors cursor-pointer"
              >
                <span className="flex items-center gap-1.5">
                  <AlertTriangle className="w-3.5 h-3.5 text-text-muted" />
                  <span>Technical System Details</span>
                </span>
                {showTechnical ? (
                  <ChevronUp className="w-3.5 h-3.5 text-text-muted" />
                ) : (
                  <ChevronDown className="w-3.5 h-3.5 text-text-muted" />
                )}
              </button>

              {showTechnical && (
                <div className="p-3.5 bg-surface border-t border-border-default space-y-2.5">
                  <div className="flex items-center justify-between">
                    <span className="text-[11px] text-text-muted">Exact system message:</span>
                    <button
                      type="button"
                      onClick={handleCopyTechnical}
                      className="inline-flex items-center gap-1 px-2 py-1 rounded-md text-[11px] bg-surface-secondary hover:bg-surface-secondary/80 text-text-secondary border border-border-default transition-colors cursor-pointer"
                    >
                      {copied ? (
                        <>
                          <Check className="w-3 h-3 text-success" />
                          <span className="text-success font-medium">Copied</span>
                        </>
                      ) : (
                        <>
                          <Copy className="w-3 h-3" />
                          <span>Copy details</span>
                        </>
                      )}
                    </button>
                  </div>
                  <pre className="p-2.5 rounded-lg bg-surface-secondary border border-border-default font-mono text-[11px] text-text-secondary overflow-x-auto whitespace-pre-wrap break-all select-text max-h-36">
                    {parsed.technical}
                  </pre>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-6 py-3.5 border-t border-border-default bg-surface">
          {onChooseAnother ? (
            <button
              type="button"
              onClick={() => {
                onClose();
                onChooseAnother();
              }}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg border border-border-default bg-surface hover:bg-surface-secondary text-text-primary transition-colors cursor-pointer"
            >
              <Upload className="w-3.5 h-3.5 text-text-muted" />
              <span>{chooseAnotherLabel}</span>
            </button>
          ) : (
            <div />
          )}

          <div className="flex items-center gap-2">
            {onRetry && (
              <button
                type="button"
                onClick={() => {
                  onClose();
                  onRetry();
                }}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg border border-border-default bg-surface hover:bg-surface-secondary text-text-primary transition-colors cursor-pointer"
              >
                <RotateCcw className="w-3.5 h-3.5 text-text-muted" />
                <span>{retryLabel}</span>
              </button>
            )}

            <button
              type="button"
              onClick={onClose}
              className="px-4 py-1.5 text-xs font-semibold rounded-lg bg-brand hover:bg-brand-hover text-white transition-colors cursor-pointer shadow-xs"
            >
              {strings.common.actions.close}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
