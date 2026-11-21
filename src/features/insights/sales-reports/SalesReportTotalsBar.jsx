import { formatCurrency } from '../../../lib/formatters';
import { strings } from '../../../lib/strings';

export default function SalesReportTotalsBar({ visibleColumns, totals }) {
  return (
    <div className="border-t border-border-default bg-surface px-5 py-2.5 flex items-center justify-between flex-wrap gap-x-6 gap-y-2 text-xs shrink-0 shadow-xs z-20">
      <div className="flex items-center gap-2">
        <span className="font-bold text-text-primary uppercase tracking-wide">
          {strings.salesReports.summary.totals}
        </span>
      </div>

      <div className="flex items-center gap-4 flex-wrap">
        {visibleColumns.map((col) => {
          if (!col.totalKey) return null;
          let displayVal = '';

          if (col.totalKey === 'transactions') {
            displayVal = strings.salesReports.summary.transactions(totals.transactions);
          } else if (col.totalKey === 'cash') {
            displayVal = formatCurrency(totals.cash);
          } else if (col.totalKey === 'upi') {
            displayVal = formatCurrency(totals.upi);
          } else if (col.totalKey === 'card') {
            displayVal = formatCurrency(totals.card);
          } else if (col.totalKey === 'giftCard') {
            displayVal = formatCurrency(totals.giftCard);
          } else if (col.totalKey === 'discount') {
            displayVal = formatCurrency(totals.discount);
          } else if (col.totalKey === 'gross') {
            displayVal = formatCurrency(totals.gross);
          } else if (col.totalKey === 'net') {
            displayVal = formatCurrency(totals.net);
          }

          return (
            <div
              key={col.key}
              className="flex items-center gap-1.5 bg-surface-secondary px-2 py-1 rounded-md border border-border-default"
            >
              <span className="text-[10px] text-text-muted uppercase tracking-wider">{col.label}:</span>
              <span className="font-semibold text-text-primary financial-value">{displayVal}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
