import { exportToCsv, exportToXlsx, escapeCsv } from '../../../lib/exportUtils';
import { toast } from '../../../stores/toast.store';
import { strings } from '../../../lib/strings';

export async function exportProductsCsv({ products, currencySymbol }) {
  if (!products.length) {
    toast.warning(strings.products.export.noProducts);
    return;
  }

  const headers = [
    strings.products.form.productName,
    strings.common.labels.sku,
    strings.common.labels.category,
    strings.products.form.costPrice(currencySymbol),
    strings.products.form.mrp(currencySymbol),
    strings.products.export.headers.gst,
    strings.products.export.headers.currentStock,
    strings.products.export.headers.minStockAlert,
    strings.common.labels.unit,
    strings.common.labels.status,
  ];

  const rows = products.map((p) => [
    escapeCsv(p.name || ''),
    escapeCsv(p.sku || ''),
    escapeCsv((p.categoryName ?? p.category_name) || strings.products.export.uncategorized),
    escapeCsv(((p.costPricePaise ?? p.cost_price_paise ?? 0) / 100).toFixed(2)),
    escapeCsv(((p.mrpPaise ?? p.mrp_paise ?? 0) / 100).toFixed(2)),
    escapeCsv((p.taxRate ?? p.tax_rate) ?? 0),
    escapeCsv((p.currentStock ?? p.current_stock) ?? 0),
    escapeCsv((p.minStockAlert ?? p.min_stock_alert) ?? 5),
    escapeCsv(p.uom || 'Piece'),
    escapeCsv(p.status || 'active'),
  ]);

  const dateStr = new Date().toISOString().split('T')[0];
  await exportToCsv({
    filename: `products_${dateStr}.csv`,
    headers,
    rows,
    count: products.length,
    entityName: strings.products.export.entityName,
  });
}

export async function exportProductsXlsx({ products, currencySymbol }) {
  if (!products.length) {
    toast.warning(strings.products.export.noProducts);
    return;
  }

  const headers = [
    strings.products.form.productName,
    strings.common.labels.sku,
    strings.common.labels.category,
    strings.products.form.costPrice(currencySymbol),
    strings.products.form.mrp(currencySymbol),
    strings.products.export.headers.gst,
    strings.products.export.headers.currentStock,
    strings.products.export.headers.minStockAlert,
    strings.common.labels.unit,
    strings.common.labels.status,
  ];

  const rows = products.map((p) => [
    p.name || '',
    p.sku || '',
    (p.categoryName ?? p.category_name) || strings.products.export.uncategorized,
    ((p.costPricePaise ?? p.cost_price_paise ?? 0) / 100).toFixed(2),
    ((p.mrpPaise ?? p.mrp_paise ?? 0) / 100).toFixed(2),
    (p.taxRate ?? p.tax_rate) ?? 0,
    (p.currentStock ?? p.current_stock) ?? 0,
    (p.minStockAlert ?? p.min_stock_alert) ?? 5,
    p.uom || 'Piece',
    p.status || 'active',
  ]);

  const dateStr = new Date().toISOString().split('T')[0];
  await exportToXlsx({
    filename: `products_${dateStr}.xlsx`,
    headers,
    rows,
    sheetName: strings.products.export.sheetName,
    entityName: strings.products.export.entityName,
  });
}
