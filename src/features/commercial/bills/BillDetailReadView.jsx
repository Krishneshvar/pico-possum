import {
  AlertTriangle,
  Receipt,
  User,
  CreditCard,
  Calendar,
  Printer,
} from 'lucide-react';
import { formatPaise, formatDateTime } from '../../../lib/formatters';
import { nextSortState, sortRows } from '../../../lib/sort';
import SortableTh from '../../../components/common/SortableTh';
import { strings } from '../../../lib/strings';

const b = strings.bills;
const c = strings.common;

export default function BillDetailReadView({
  sale,
  items = [],
  lineSort,
  onLineSortChange,
  onReprint,
}) {
  const sortedItems = sortRows(items, lineSort, (row, key) => row[key]);
  const isCancelled = sale.status === 'CANCELLED';

  return (
    <div className="flex-1 flex overflow-hidden">
      {/* Left Column: Line Items Table */}
      <div className="flex-1 flex flex-col p-6 overflow-y-auto space-y-4 min-w-0 bg-canvas">
        {isCancelled && (
          <div className="p-4 rounded-xl bg-danger-bg border border-danger/30 flex items-start gap-3 shrink-0">
            <AlertTriangle className="w-5 h-5 text-danger shrink-0 mt-0.5" />
            <div className="text-xs">
              <h4 className="font-bold text-danger">{b.detail.cancelledBanner.title}</h4>
              <p className="text-text-secondary mt-0.5">
                {b.detail.cancelledBanner.message}
              </p>
            </div>
          </div>
        )}

        {/* Line Items Table Card */}
        <div className="rounded-xl border border-border-default overflow-hidden bg-surface shadow-xs flex-1 flex flex-col">
          <div className="px-4 py-3 border-b border-border-default bg-surface-secondary flex items-center justify-between shrink-0">
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold uppercase tracking-wider text-text-primary">
                {b.detail.items.heading}
              </span>
              <span className="text-[11px] font-mono px-2 py-0.5 rounded-full bg-surface border border-border-default text-text-muted">
                {b.detail.items.lineCount(items.length)}
              </span>
            </div>
            <span className="text-xs text-text-muted font-mono">
              {b.detail.items.totalUnits(items.reduce((acc, it) => acc + (it.quantity || 0), 0))}
            </span>
          </div>

          <div className="flex-1 overflow-x-auto overflow-y-auto">
            <table className="w-full text-left border-collapse text-xs select-text">
              <thead className="bg-table-header-bg text-[11px] font-semibold text-table-header-text uppercase tracking-wider sticky top-0 border-b border-table-header-border z-10 shadow-2xs">
                <tr>
                  <th className="py-2.5 px-4 w-12 text-center">{b.columns.index}</th>
                  <SortableTh
                    sortKey="product_name"
                    sort={lineSort}
                    onSort={(key) => onLineSortChange((p) => nextSortState(p, key))}
                    className="py-2.5 px-4"
                  >
                    {b.columns.productName}
                  </SortableTh>
                  <SortableTh
                    sortKey="unit_price_paise"
                    sort={lineSort}
                    onSort={(key) => onLineSortChange((p) => nextSortState(p, key))}
                    align="right"
                    className="py-2.5 px-4"
                  >
                    {b.columns.unitRate}
                  </SortableTh>
                  <SortableTh
                    sortKey="quantity"
                    sort={lineSort}
                    onSort={(key) => onLineSortChange((p) => nextSortState(p, key))}
                    align="center"
                    className="py-2.5 px-4"
                  >
                    {b.columns.quantity}
                  </SortableTh>
                  <SortableTh
                    sortKey="discount_paise"
                    sort={lineSort}
                    onSort={(key) => onLineSortChange((p) => nextSortState(p, key))}
                    align="right"
                    className="py-2.5 px-4"
                  >
                    {b.columns.discount}
                  </SortableTh>
                  <SortableTh
                    sortKey="tax_rate"
                    sort={lineSort}
                    onSort={(key) => onLineSortChange((p) => nextSortState(p, key))}
                    align="right"
                    className="py-2.5 px-4"
                  >
                    {b.columns.taxPercent}
                  </SortableTh>
                  <SortableTh
                    sortKey="total_paise"
                    sort={lineSort}
                    onSort={(key) => onLineSortChange((p) => nextSortState(p, key))}
                    align="right"
                    className="py-2.5 px-4"
                  >
                    {b.columns.total}
                  </SortableTh>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-default">
                {sortedItems.map((it, idx) => (
                  <tr key={it.id || idx} className="hover:bg-surface-secondary/60 transition-colors">
                    <td className="py-3 px-4 text-text-muted font-mono text-center">{idx + 1}</td>
                    <td className="py-3 px-4">
                      <div className="font-semibold text-text-primary">{it.product_name}</div>
                      {it.product_sku && (
                        <div className="text-[10px] text-text-muted font-mono mt-0.5">{it.product_sku}</div>
                      )}
                    </td>
                    <td className="py-3 px-4 text-right financial-value text-text-secondary">
                      {formatPaise(it.unit_price_paise)}
                    </td>
                    <td className="py-3 px-4 text-center font-bold text-text-primary financial-value">
                      {it.quantity}
                    </td>
                    <td className="py-3 px-4 text-right financial-value text-amber-600">
                      {it.discount_paise > 0 ? `-${formatPaise(it.discount_paise)}` : '—'}
                    </td>
                    <td className="py-3 px-4 text-right font-mono text-text-muted">
                      {it.tax_rate != null ? `${it.tax_rate}%` : '0%'}
                    </td>
                    <td className="py-3 px-4 text-right font-bold text-text-primary financial-value">
                      {formatPaise(it.total_paise)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Right Column: Metadata & Summary Sidebar */}
      <div className="w-80 lg:w-96 border-l border-border-default bg-surface flex flex-col overflow-y-auto p-5 space-y-4 shrink-0">
        {/* Customer Details Card */}
        <div className="p-3.5 rounded-xl bg-surface-secondary border border-border-default space-y-2">
          <div className="flex items-center gap-2 text-xs font-semibold text-text-primary">
            <User className="w-4 h-4 text-brand" />
            <span className="uppercase tracking-wider text-[11px]">{b.detail.customer.heading}</span>
          </div>
          <div className="pt-2 border-t border-border-default space-y-1">
            <div className="text-sm font-bold text-text-primary">
              {sale.customer_name || b.labels.walkInCustomer}
            </div>
            {sale.customer_phone ? (
              <div className="text-xs text-text-muted font-mono">{sale.customer_phone}</div>
            ) : (
              <div className="text-xs text-text-muted italic">{b.detail.customer.noPhone}</div>
            )}
            {sale.customer_address && (
              <div className="text-xs text-text-secondary mt-1">{sale.customer_address}</div>
            )}
          </div>
        </div>

        {/* Payment & Transaction Card */}
        <div className="p-3.5 rounded-xl bg-surface-secondary border border-border-default space-y-2">
          <div className="flex items-center gap-2 text-xs font-semibold text-text-primary">
            <CreditCard className="w-4 h-4 text-brand" />
            <span className="uppercase tracking-wider text-[11px]">{b.detail.payment.heading}</span>
          </div>
          <div className="pt-2 border-t border-border-default space-y-2 text-xs">
            <div className="flex items-center justify-between">
              <span className="text-text-muted">{c.labels.paymentMethod}</span>
              <span className="text-xs font-bold px-2 py-0.5 rounded bg-surface border border-border-default text-text-primary uppercase tracking-wide">
                {sale.payment_method ? sale.payment_method.replace(/_/g, ' ') : 'CASH'}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-text-muted">{b.detail.payment.cashier}</span>
              <span className="font-medium text-text-primary">
                {sale.biller_name || b.detail.payment.operatorDefault}
              </span>
            </div>
            <div className="flex items-center justify-between pt-1 border-t border-border-default">
              <span className="text-text-muted flex items-center gap-1.5">
                <Calendar className="w-3.5 h-3.5" /> {c.labels.dateTime}
              </span>
              <span className="font-mono text-text-primary">
                {formatDateTime(sale.created_at)}
              </span>
            </div>
          </div>
        </div>

        {/* Financial Summary Card */}
        <div className="p-3.5 rounded-xl bg-surface-secondary border border-border-default space-y-3 flex-1 flex flex-col justify-between">
          <div className="space-y-2.5">
            <div className="flex items-center gap-2 text-xs font-semibold text-text-primary">
              <Receipt className="w-4 h-4 text-brand" />
              <span className="uppercase tracking-wider text-[11px]">{b.detail.summary.heading}</span>
            </div>

            <div className="space-y-1.5 pt-2 border-t border-border-default text-xs text-text-secondary">
              <div className="flex justify-between">
                <span className="text-text-muted">{c.labels.subtotal}</span>
                <span className="financial-value font-medium text-text-primary">
                  {formatPaise(sale.subtotal_paise)}
                </span>
              </div>

              {sale.discount_paise > 0 && (
                <div className="flex justify-between text-amber-600">
                  <span>{c.labels.discount}</span>
                  <span className="financial-value font-medium">
                    -{formatPaise(sale.discount_paise)}
                  </span>
                </div>
              )}

              {sale.tax_paise > 0 && (
                <div className="flex justify-between text-text-muted">
                  <span>{b.detail.summary.gst}</span>
                  <span className="financial-value font-medium text-text-primary">
                    {formatPaise(sale.tax_paise)}
                  </span>
                </div>
              )}

              <div className="pt-2 border-t border-border-default flex justify-between items-baseline">
                <span className="text-sm font-bold text-text-primary uppercase tracking-wide">
                  {b.detail.summary.grandTotal}
                </span>
                <span className="text-xl font-bold financial-value text-text-primary">
                  {formatPaise(sale.total_paise)}
                </span>
              </div>
            </div>

            {/* Paid & Settlement Details */}
            <div className="bg-surface rounded-lg p-2.5 border border-border-default space-y-1 text-xs">
              <div className="flex justify-between text-text-muted">
                <span>{b.detail.summary.paidAmount(sale.payment_method ? sale.payment_method.replace(/_/g, ' ') : 'CASH')}</span>
                <span className="financial-value font-semibold text-text-primary">
                  {formatPaise(sale.paid_paise)}
                </span>
              </div>
              {sale.paid_paise > sale.total_paise ? (
                <div className="flex justify-between text-success font-semibold">
                  <span>{b.detail.summary.changeReturned}</span>
                  <span className="financial-value">{formatPaise(sale.paid_paise - sale.total_paise)}</span>
                </div>
              ) : sale.paid_paise < sale.total_paise ? (
                <div className="flex justify-between text-danger font-semibold">
                  <span>{b.detail.summary.balanceDue}</span>
                  <span className="financial-value">{formatPaise(sale.total_paise - sale.paid_paise)}</span>
                </div>
              ) : (
                <div className="flex justify-between text-text-muted text-[11px]">
                  <span>{b.detail.summary.balanceStatus}</span>
                  <span>{b.detail.summary.settled}</span>
                </div>
              )}
            </div>

            {/* Notes & Audit Reason */}
            {sale.notes && (
              <div className="pt-2 border-t border-border-default text-xs">
                <span className="text-[10px] uppercase font-bold text-text-muted block mb-0.5">
                  {b.detail.summary.notesLabel}
                </span>
                <p className="bg-surface p-2 rounded-lg border border-border-default text-text-primary leading-relaxed">
                  {sale.notes}
                </p>
              </div>
            )}
          </div>

          {/* Sidebar Action Button */}
          <div className="pt-3 border-t border-border-default">
            <button
              type="button"
              onClick={onReprint}
              className="w-full py-2 rounded-xl bg-surface hover:bg-surface-secondary text-text-primary font-semibold text-xs flex items-center justify-center gap-2 border border-border-default transition-colors cursor-pointer shadow-2xs"
            >
              <Printer className="w-3.5 h-3.5 text-text-muted" />
              <span>{b.actions.reprintReceipt}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
