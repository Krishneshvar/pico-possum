import { useEffect, useRef } from 'react';
import { CheckCircle2, Printer } from 'lucide-react';
import { strings } from '../../../lib/strings';
import { formatCurrency } from '../../../lib/formatters';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';

export default function SaleCompleteModal({
  isOpen,
  onClose,
  completedSale,
  billSettings,
  generalSettings,
  onPrintReceipt,
}) {
  const newSaleBtnRef = useRef(null);
  const printReceiptBtnRef = useRef(null);

  useEffect(() => {
    if (isOpen) {
      const timer = setTimeout(() => {
        newSaleBtnRef.current?.focus();
      }, 80);
      return () => clearTimeout(timer);
    }
  }, [isOpen]);

  if (!isOpen || !completedSale) return null;

  const invNo = completedSale.sale?.invoiceNumber ?? completedSale.sale?.invoice_number ?? '—';
  const changeCents = completedSale.changeCents ?? completedSale.change_cents ?? 0;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fade-in">
      <div className="bg-surface border border-border-default rounded-2xl max-w-sm w-full p-6 shadow-xl space-y-4 animate-scale-in">
        <div className="text-center space-y-2">
          <div className="w-12 h-12 rounded-full bg-success-bg text-success flex items-center justify-center mx-auto">
            <CheckCircle2 className="w-7 h-7" />
          </div>
          <h3 className="text-base font-bold text-text-primary">{strings.pos.view.saleCompletedTitle}</h3>
          <p className="text-xs text-text-muted">
            {strings.pos.view.invoiceNumber}{' '}
            <strong className="text-text-primary font-mono">{invNo}</strong>
          </p>
        </div>

        {/* Change banner */}
        {changeCents > 0 && (
          <div className="bg-surface-secondary p-3.5 rounded-xl border border-border-default text-center">
            <div className="text-[11px] text-text-muted uppercase font-bold tracking-wider">
              {strings.pos.view.changeToReturn}
            </div>
            <div className="text-2xl font-black text-text-primary financial-value mt-0.5">
              {formatCurrency(changeCents)}
            </div>
          </div>
        )}

        {/* Hidden printable receipt container */}
        <div className="print-only-container">
          <ReceiptPrinter
            sale={completedSale.sale}
            items={completedSale.items}
            billSettings={billSettings}
            generalSettings={generalSettings}
          />
        </div>

        {/* Action buttons */}
        <div className="grid grid-cols-2 gap-2.5 pt-2">
          <button
            ref={newSaleBtnRef}
            type="button"
            onClick={onClose}
            className="py-2 px-3 rounded-xl bg-surface hover:bg-surface-secondary text-text-primary text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors border border-border-default cursor-pointer focus:outline-none focus:ring-2 focus:ring-brand focus:border-brand"
          >
            <span>{strings.pos.view.newSale}</span>
          </button>
          <button
            ref={printReceiptBtnRef}
            type="button"
            onClick={onPrintReceipt}
            className="py-2 px-3 rounded-xl bg-brand hover:bg-brand-hover text-white text-xs font-bold flex items-center justify-center gap-1.5 transition-colors shadow-2xs cursor-pointer focus:outline-none focus:ring-2 focus:ring-brand focus:ring-offset-2"
          >
            <Printer className="w-4 h-4" />
            <span>{strings.pos.view.printReceipt}</span>
          </button>
        </div>
      </div>
    </div>
  );
}
