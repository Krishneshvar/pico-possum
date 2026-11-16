import { useState, useRef, useEffect } from 'react';
import { Download, FileText, FileSpreadsheet, ChevronDown } from 'lucide-react';
import { strings } from '../../lib/strings';

export default function ExportButton({
  onExportCsv,
  onExportXlsx,
  disabled = false,
  className = '',
  label = strings.common.exportButton.export,
}) {
  const [isOpen, setIsOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    if (!isOpen) return;
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    };
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') setIsOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  return (
    <div ref={menuRef} className={`relative ${className}`}>
      <button
        type="button"
        disabled={disabled}
        onClick={() => setIsOpen((prev) => !prev)}
        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-border-default bg-surface hover:bg-surface-secondary text-text-secondary hover:text-text-primary transition-colors shadow-2xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
        title={strings.common.exportButton.tooltip}
        aria-haspopup="true"
        aria-expanded={isOpen}
      >
        <Download className="w-3.5 h-3.5 text-text-muted" />
        <span>{label}</span>
        <ChevronDown
          className={`w-3 h-3 text-text-muted transition-transform ${isOpen ? 'rotate-180' : ''}`}
        />
      </button>

      {isOpen && (
        <div className="absolute right-0 top-full mt-1.5 z-50 bg-surface border border-border-default rounded-xl shadow-xl p-1.5 min-w-[175px] space-y-0.5 text-text-primary animate-scale-in">
          {onExportCsv && (
            <button
              type="button"
              onClick={() => {
                setIsOpen(false);
                onExportCsv();
              }}
              className="w-full flex items-center gap-2 px-2.5 py-2 rounded-lg text-xs font-medium text-text-secondary hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
            >
              <FileText className="w-4 h-4 text-blue-500" />
              <span>{strings.common.exportButton.exportCsv}</span>
            </button>
          )}

          {onExportXlsx && (
            <button
              type="button"
              onClick={() => {
                setIsOpen(false);
                onExportXlsx();
              }}
              className="w-full flex items-center gap-2 px-2.5 py-2 rounded-lg text-xs font-medium text-text-secondary hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
            >
              <FileSpreadsheet className="w-4 h-4 text-emerald-500" />
              <span>{strings.common.exportButton.exportXlsx}</span>
            </button>
          )}
        </div>
      )}
    </div>
  );
}

