import { Flame } from 'lucide-react';
import { formatPaise } from '../../../lib/formatters';
import { nextSortState } from '../../../lib/sort';
import SortableTh from '../../../components/common/SortableTh';
import { strings } from '../../../lib/strings';

export default function TopSellingProductsCard({
  topProducts = [],
  sortedTopProducts = [],
  loadingTopProducts = false,
  topProdSort,
  setTopProdSort,
  totalProductUnits = 0,
  totalProductRevenue = 0,
}) {
  return (
    <div className="p-5 rounded-xl bg-surface border border-border space-y-3.5 flex flex-col justify-between">
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Flame className="w-4 h-4 text-warning" />
            <h2 className="text-sm font-semibold text-content">{strings.reports.tables.topProducts}</h2>
          </div>
        </div>

        <div className="border border-border rounded-xl overflow-hidden shadow-2xs">
          <table className="w-full text-left border-collapse text-xs">
            <thead className="bg-table-header-bg text-table-header-text font-semibold border-b border-table-header-border">
              <tr className="h-10">
                <th className="py-2.5 px-3 pl-4 w-10 text-center">#</th>
                <SortableTh
                  sortKey="product_name"
                  sort={topProdSort}
                  onSort={(key) => setTopProdSort((p) => nextSortState(p, key))}
                  className="py-2.5 px-3"
                >
                  Product
                </SortableTh>
                <SortableTh
                  sortKey="total_quantity"
                  sort={topProdSort}
                  onSort={(key) => setTopProdSort((p) => nextSortState(p, key))}
                  align="center"
                  className="py-2.5 px-3"
                >
                  Qty Sold
                </SortableTh>
                <SortableTh
                  sortKey="total_revenue_paise"
                  sort={topProdSort}
                  onSort={(key) => setTopProdSort((p) => nextSortState(p, key))}
                  align="right"
                  className="py-2.5 px-3 pr-4"
                >
                  Total Revenue
                </SortableTh>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loadingTopProducts ? (
                <tr>
                  <td colSpan="4" className="py-8 text-center text-content-muted">
                    Loading products...
                  </td>
                </tr>
              ) : topProducts.length === 0 ? (
                <tr>
                  <td colSpan="4" className="py-8 text-center text-content-muted">
                    No product sales in this period.
                  </td>
                </tr>
              ) : (
                sortedTopProducts.map((p, idx) => (
                  <tr key={p.product_id || idx} className="h-11 hover:bg-surface-secondary transition-colors">
                    <td className="px-3 pl-4 text-center text-content-muted text-xs">
                      {idx + 1}
                    </td>
                    <td className="px-3 text-content">
                      <div className="font-medium">{p.product_name}</div>
                      {p.sku && (
                        <div className="text-[10px] text-content-muted font-mono">
                          SKU: {p.sku}
                        </div>
                      )}
                    </td>
                    <td className="px-3 text-center font-medium text-content financial-value">
                      {p.total_quantity}
                    </td>
                    <td className="px-3 pr-4 text-right font-medium text-content financial-value">
                      {formatPaise(p.total_revenue_paise)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Product Totals Footer */}
      {topProducts.length > 0 && (
        <div className="pt-3 border-t border-border flex items-center justify-between text-xs text-content-secondary bg-surface-secondary px-3.5 py-2 rounded-lg">
          <div>
            <span className="text-content-muted mr-1.5">Total Qty:</span>
            <span className="font-semibold text-content financial-value">{totalProductUnits}</span>
          </div>
          <div>
            <span className="text-content-muted mr-1.5">Total Revenue:</span>
            <span className="font-semibold text-content financial-value">{formatPaise(totalProductRevenue)}</span>
          </div>
        </div>
      )}
    </div>
  );
}
