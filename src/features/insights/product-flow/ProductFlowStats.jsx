import { ShoppingBag, Receipt } from 'lucide-react';
import { formatCurrency } from '../../../lib/formatters';
import { strings } from '../../../lib/strings';

export default function ProductFlowStats({ selectedProduct, summaryMetrics }) {
  return (
    <div className="px-4 py-2.5 border-b border-border bg-surface-secondary/40 flex-shrink-0">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <h2 className="text-sm font-semibold text-content">
              {selectedProduct?.name}
            </h2>
            {selectedProduct?.sku && (
              <span className="text-[11px] font-mono px-1.5 py-0.5 rounded bg-surface border border-border text-content-secondary">
                {strings.productFlow.productFlow.skuBadge(selectedProduct.sku)}
              </span>
            )}
            {(selectedProduct?.isDeleted ?? selectedProduct?.is_deleted) ? (
              <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-danger/10 text-danger border border-danger/20">
                Deleted
              </span>
            ) : selectedProduct?.status === 'inactive' ? (
              <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-500 border border-amber-500/20">
                Inactive
              </span>
            ) : selectedProduct?.status === 'discontinued' ? (
              <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-slate-500/10 text-slate-400 border border-slate-500/20">
                Discontinued
              </span>
            ) : (
              <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-success/10 text-success border border-success/20">
                Active
              </span>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-surface border border-border">
            <ShoppingBag className="w-3.5 h-3.5 text-brand" />
            <span className="text-xs text-content-secondary">
              {strings.productFlow.productFlow.totalSold}:
            </span>
            <span className="text-xs font-bold text-content financial-value">
              {strings.productFlow.productFlow.units(summaryMetrics.unitsSold)}
            </span>
          </div>

          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-surface border border-border">
            <Receipt className="w-3.5 h-3.5 text-success" />
            <span className="text-xs text-content-secondary">
              {strings.productFlow.productFlow.totalRevenue}:
            </span>
            <span className="text-xs font-bold text-content financial-value">
              {formatCurrency(summaryMetrics.totalRevenue)}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
