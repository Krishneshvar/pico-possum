import {
  ShoppingBag,
  Receipt,
  TrendingUp,
  Tag,
  Package,
} from 'lucide-react';
import { formatPaise } from '../../../lib/formatters';
import { strings } from '../../../lib/strings';

export default function ProductFlowStats({ selectedProduct, summaryMetrics }) {
  return (
    <div className="p-4 rounded-xl bg-surface border border-border">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <h2 className="text-base font-semibold text-content">
              {selectedProduct?.name}
            </h2>
            {selectedProduct?.sku && (
              <span className="text-xs font-mono px-2 py-0.5 rounded bg-surface-secondary border border-border text-content-secondary">
                {strings.productFlow.productFlow.skuBadge(selectedProduct.sku)}
              </span>
            )}
          </div>
          <p className="text-xs text-content-secondary">
            {strings.productFlow.productFlow.summarySubtitle}
          </p>
        </div>

        <div className="flex items-center gap-2.5 flex-wrap">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface-secondary border border-border">
            <ShoppingBag className="w-3.5 h-3.5 text-brand" />
            <span className="text-xs text-content-secondary">
              {strings.productFlow.productFlow.totalSold}
            </span>
            <span className="text-xs font-bold text-content financial-value">
              {strings.productFlow.productFlow.units(summaryMetrics.unitsSold)}
            </span>
          </div>

          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface-secondary border border-border">
            <Receipt className="w-3.5 h-3.5 text-success" />
            <span className="text-xs text-content-secondary">
              {strings.productFlow.productFlow.totalRevenue}
            </span>
            <span className="text-xs font-bold text-content financial-value">
              {formatPaise(summaryMetrics.totalRevenue)}
            </span>
          </div>

          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface-secondary border border-border">
            <TrendingUp className="w-3.5 h-3.5 text-teal-600" />
            <span className="text-xs text-content-secondary">
              {strings.productFlow.productFlow.avgSoldPrice}
            </span>
            <span className="text-xs font-bold text-content financial-value">
              {formatPaise(summaryMetrics.avgPrice)}
            </span>
          </div>

          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface-secondary border border-border">
            <Tag className="w-3.5 h-3.5 text-content-muted" />
            <span className="text-xs text-content-secondary">
              {strings.productFlow.productFlow.currentMrp}
            </span>
            <span className="text-xs font-bold text-content financial-value">
              {(selectedProduct?.mrpPaise ?? selectedProduct?.mrp_paise) != null
                ? formatPaise(selectedProduct.mrpPaise ?? selectedProduct.mrp_paise)
                : strings.common.states.emptyValue}
            </span>
          </div>

          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface-secondary border border-border">
            <Package className="w-3.5 h-3.5 text-content-muted" />
            <span className="text-xs text-content-secondary">
              {strings.productFlow.productFlow.inStock}
            </span>
            <span className="text-xs font-bold text-content financial-value">
              {strings.productFlow.productFlow.units((selectedProduct?.currentStock ?? selectedProduct?.current_stock) ?? 0)}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
