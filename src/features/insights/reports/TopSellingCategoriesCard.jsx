import { FolderTree } from 'lucide-react';
import { formatPaise } from '../../../lib/formatters';
import { nextSortState } from '../../../lib/sort';
import SortableTh from '../../../components/common/SortableTh';
import { strings } from '../../../lib/strings';

export default function TopSellingCategoriesCard({
  topCategories = [],
  sortedTopCategories = [],
  loadingTopCategories = false,
  topCatSort,
  setTopCatSort,
  totalCategoryUnits = 0,
  totalCategoryRevenue = 0,
}) {
  return (
    <div className="p-5 rounded-xl bg-surface border border-border space-y-3.5 flex flex-col justify-between">
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <FolderTree className="w-4 h-4 text-brand" />
            <h2 className="text-sm font-semibold text-content">{strings.reports.tables.topCategories}</h2>
          </div>
        </div>

        <div className="border border-border rounded-xl overflow-hidden shadow-2xs">
          <table className="w-full text-left border-collapse text-xs">
            <thead className="bg-table-header-bg text-table-header-text font-semibold border-b border-table-header-border">
              <tr className="h-10">
                <th className="py-2.5 px-3 pl-4 w-10 text-center">#</th>
                <SortableTh
                  sortKey="categoryName"
                  sort={topCatSort}
                  onSort={(key) => setTopCatSort((p) => nextSortState(p, key))}
                  className="py-2.5 px-3"
                >
                  Category
                </SortableTh>
                <SortableTh
                  sortKey="distinctProductsSold"
                  sort={topCatSort}
                  onSort={(key) => setTopCatSort((p) => nextSortState(p, key))}
                  align="center"
                  className="py-2.5 px-3"
                >
                  Products
                </SortableTh>
                <SortableTh
                  sortKey="totalQuantity"
                  sort={topCatSort}
                  onSort={(key) => setTopCatSort((p) => nextSortState(p, key))}
                  align="center"
                  className="py-2.5 px-3"
                >
                  Sold
                </SortableTh>
                <SortableTh
                  sortKey="totalRevenuePaise"
                  sort={topCatSort}
                  onSort={(key) => setTopCatSort((p) => nextSortState(p, key))}
                  align="right"
                  className="py-2.5 px-3 pr-4"
                >
                  Total Revenue
                </SortableTh>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loadingTopCategories ? (
                <tr>
                  <td colSpan="5" className="py-8 text-center text-content-muted">
                    Loading categories...
                  </td>
                </tr>
              ) : topCategories.length === 0 ? (
                <tr>
                  <td colSpan="5" className="py-8 text-center text-content-muted">
                    No category sales in this period.
                  </td>
                </tr>
              ) : (
                sortedTopCategories.map((c, idx) => (
                  <tr key={c.categoryId ?? c.category_id ?? idx} className="h-11 hover:bg-surface-secondary transition-colors">
                    <td className="px-3 pl-4 text-center text-content-muted text-xs">
                      {idx + 1}
                    </td>
                    <td className="px-3 font-medium text-content">
                      {c.categoryName ?? c.category_name}
                    </td>
                    <td className="px-3 text-center text-content-secondary financial-value">
                      {c.distinctProductsSold ?? c.distinct_products_sold}
                    </td>
                    <td className="px-3 text-center font-medium text-content financial-value">
                      {c.totalQuantity ?? c.total_quantity}
                    </td>
                    <td className="px-3 pr-4 text-right font-medium text-content financial-value">
                      {formatPaise(c.totalRevenuePaise ?? c.total_revenue_paise)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Category Totals Footer */}
      {topCategories.length > 0 && (
        <div className="pt-3 border-t border-border flex items-center justify-between text-xs text-content-secondary bg-surface-secondary px-3.5 py-2 rounded-lg">
          <div>
            <span className="text-content-muted mr-1.5">Total Sold:</span>
            <span className="font-semibold text-content financial-value">{totalCategoryUnits}</span>
          </div>
          <div>
            <span className="text-content-muted mr-1.5">Total Revenue:</span>
            <span className="font-semibold text-content financial-value">{formatPaise(totalCategoryRevenue)}</span>
          </div>
        </div>
      )}
    </div>
  );
}
