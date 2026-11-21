import { formatPaise } from '../../../lib/formatters';

export function getProductColumns({ allowNegativeStock }) {
  return [
    {
      key: 'name',
      label: 'Product',
      className: 'font-semibold text-text-primary text-xs',
      render: (r) => {
        const catName = r.categoryName ?? r.category_name;
        return (
          <div>
            <span>{r.name}</span>
            {catName && (
              <div className="text-[10px] text-text-muted">{catName}</div>
            )}
          </div>
        );
      },
    },
    {
      key: 'sku',
      label: 'SKU / Barcode',
      className: 'font-mono text-text-muted text-xs',
      render: (r) => r.sku || '—',
    },
    {
      key: 'costPricePaise',
      label: 'Cost Price',
      headerClassName: 'text-right',
      className: 'text-right financial-value text-text-muted text-xs',
      render: (r) => formatPaise(r.costPricePaise ?? r.cost_price_paise),
    },
    {
      key: 'mrpPaise',
      label: 'MRP',
      headerClassName: 'text-right',
      className: 'text-right financial-value font-bold text-text-primary text-xs',
      render: (r) => formatPaise(r.mrpPaise ?? r.mrp_paise),
    },
    {
      key: 'taxRate',
      label: 'GST %',
      headerClassName: 'text-center',
      className: 'text-center font-mono text-text-muted text-xs',
      render: (r) => `${r.taxRate ?? r.tax_rate}%`,
    },
    {
      key: 'currentStock',
      label: 'Stock Level',
      headerClassName: 'text-center',
      className: 'text-center text-xs',
      render: (r) => {
        const s = r.currentStock ?? r.current_stock ?? 0;
        const alertThreshold = r.minStockAlert ?? r.min_stock_alert ?? 5;

        if (allowNegativeStock) {
          return (
            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-bold font-mono bg-surface-secondary text-text-secondary border border-border-default">
              {s}
            </span>
          );
        }

        const isOut = s <= 0;
        const isLow = s <= alertThreshold && !isOut;
        const cls = isOut
          ? 'bg-danger-bg text-danger border-danger/30'
          : isLow
          ? 'bg-warning-bg text-warning border-warning/30'
          : 'bg-success-bg text-success border-success/30';
        return (
          <span
            title={isLow ? `Low stock alert (≤ ${alertThreshold})` : isOut ? 'Out of stock' : `Stock: ${s}`}
            className={`inline-flex items-center justify-center px-2.5 py-0.5 rounded-full text-[11px] font-bold font-mono border ${cls}`}
          >
            {s}
          </span>
        );
      },
    },
    {
      key: 'status',
      label: 'Status',
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => {
        const status = r.status || 'active';
        const styles = {
          active: 'bg-success-bg text-success border-success/30',
          inactive: 'bg-surface-secondary text-text-muted border-border-default',
          discontinued: 'bg-danger-bg text-danger border-danger/30',
        };
        return (
          <span
            className={`inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-semibold uppercase tracking-wider border ${
              styles[status] || styles.active
            }`}
          >
            {status}
          </span>
        );
      },
    },
  ];
}
