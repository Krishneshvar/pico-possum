import { ShoppingBag, Receipt } from 'lucide-react';
import { formatCurrency } from '../../../lib/formatters';
import { strings } from '../../../lib/strings';

export default function ProductFlowStats({ selectedProduct, summaryMetrics }) {
  return (
    <div className="flex items-center gap-2 flex-wrap shrink-0">
      {/* 1. Status */}
      {(selectedProduct?.isDeleted ?? selectedProduct?.is_deleted) ? (
        <span className="text-[11px] font-semibold px-2 py-1 rounded-md bg-danger/10 text-danger border border-danger/20">
          Deleted
        </span>
      ) : selectedProduct?.status === 'inactive' ? (
        <span className="text-[11px] font-semibold px-2 py-1 rounded-md bg-amber-500/10 text-amber-500 border border-amber-500/20">
          Inactive
        </span>
      ) : selectedProduct?.status === 'discontinued' ? (
        <span className="text-[11px] font-semibold px-2 py-1 rounded-md bg-slate-500/10 text-slate-400 border border-slate-500/20">
          Discontinued
        </span>
      ) : (
        <span className="text-[11px] font-semibold px-2 py-1 rounded-md bg-success/10 text-success border border-success/20">
          Active
        </span>
      )}

      {/* 2. SKU */}
      {selectedProduct?.sku && (
        <span className="text-[11px] font-mono px-2 py-1 rounded-md bg-surface border border-border text-content-secondary">
          {strings.productFlow.productFlow.skuBadge(selectedProduct.sku)}
        </span>
      )}

      {/* 3. Total Sold */}
      <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-surface border border-border">
        <ShoppingBag className="w-3.5 h-3.5 text-brand" />
        <span className="text-xs text-content-secondary">
          {strings.productFlow.productFlow.totalSold}:
        </span>
        <span className="text-xs font-bold text-content financial-value">
          {strings.productFlow.productFlow.units(summaryMetrics.unitsSold)}
        </span>
      </div>

      {/* 4. Total Revenue */}
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
  );
}
