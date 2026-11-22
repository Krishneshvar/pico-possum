import { exportToCsv, exportToXlsx, escapeCsv } from '../../../lib/exportUtils';
import { formatDate, formatTime } from '../../../lib/formatters';
import { strings } from '../../../lib/strings';
import { toast } from '../../../stores/toast.store';

export async function exportBillsToCsv(dataToExport, currencySymbol) {
  if (!dataToExport || !dataToExport.length) {
    toast.warning(strings.bills.toasts.noBillsToExport);
    return;
  }

  const headers = [
    strings.common.labels.invoiceNumber,
    strings.common.labels.date,
    strings.common.labels.time,
    strings.bills.export.headers.customerName,
    strings.bills.export.headers.customerPhone,
    strings.common.labels.paymentMethod,
    strings.bills.export.headers.subtotal(currencySymbol),
    strings.bills.export.headers.discount(currencySymbol),
    strings.bills.export.headers.tax(currencySymbol),
    strings.bills.export.headers.netTotal(currencySymbol),
    strings.common.labels.status,
  ];

  const rows = dataToExport.map((s) => {
    const invNo = s.invoiceNumber ?? s.invoice_number ?? '';
    const createdAt = s.createdAt ?? s.created_at;
    const custName = s.customerName ?? s.customer_name ?? strings.bills.labels.walkInCustomer;
    const custPhone = s.customerPhone ?? s.customer_phone ?? '';
    const pm = s.paymentMethod ?? s.payment_method ?? '';
    const subtotal = s.subtotalCents ?? s.subtotal_cents ?? 0;
    const discount = s.discountCents ?? s.discount_cents ?? 0;
    const tax = s.taxCents ?? s.tax_cents ?? 0;
    const total = s.totalCents ?? s.total_cents ?? 0;
    return [
      escapeCsv(invNo),
      escapeCsv(formatDate(createdAt)),
      escapeCsv(formatTime(createdAt)),
      escapeCsv(custName),
      escapeCsv(custPhone),
      escapeCsv(pm),
      escapeCsv((subtotal / 100).toFixed(2)),
      escapeCsv((discount / 100).toFixed(2)),
      escapeCsv((tax / 100).toFixed(2)),
      escapeCsv((total / 100).toFixed(2)),
      escapeCsv(s.status || ''),
    ];
  });

  const dateStr = new Date().toISOString().split('T')[0];
  await exportToCsv({
    filename: `bill-history-${dateStr}.csv`,
    headers,
    rows,
    count: dataToExport.length,
    entityName: strings.bills.export.entityName,
  });
}

export async function exportBillsToXlsx(dataToExport, currencySymbol) {
  if (!dataToExport || !dataToExport.length) {
    toast.warning(strings.bills.toasts.noBillsToExport);
    return;
  }

  const headers = [
    strings.common.labels.invoiceNumber,
    strings.common.labels.date,
    strings.common.labels.time,
    strings.bills.export.headers.customerName,
    strings.bills.export.headers.customerPhone,
    strings.common.labels.paymentMethod,
    strings.bills.export.headers.subtotal(currencySymbol),
    strings.bills.export.headers.discount(currencySymbol),
    strings.bills.export.headers.tax(currencySymbol),
    strings.bills.export.headers.netTotal(currencySymbol),
    strings.common.labels.status,
  ];

  const rows = dataToExport.map((s) => {
    const invNo = s.invoiceNumber ?? s.invoice_number ?? '';
    const createdAt = s.createdAt ?? s.created_at;
    const custName = s.customerName ?? s.customer_name ?? strings.bills.labels.walkInCustomer;
    const custPhone = s.customerPhone ?? s.customer_phone ?? '';
    const pm = s.paymentMethod ?? s.payment_method ?? '';
    const subtotal = s.subtotalCents ?? s.subtotal_cents ?? 0;
    const discount = s.discountCents ?? s.discount_cents ?? 0;
    const tax = s.taxCents ?? s.tax_cents ?? 0;
    const total = s.totalCents ?? s.total_cents ?? 0;
    return [
      invNo,
      formatDate(createdAt),
      formatTime(createdAt),
      custName,
      custPhone,
      pm,
      parseFloat((subtotal / 100).toFixed(2)),
      parseFloat((discount / 100).toFixed(2)),
      parseFloat((tax / 100).toFixed(2)),
      parseFloat((total / 100).toFixed(2)),
      s.status || '',
    ];
  });

  const dateStr = new Date().toISOString().split('T')[0];
  await exportToXlsx({
    filename: `bill-history-${dateStr}.xlsx`,
    sheetName: strings.bills.export.sheetName,
    headers,
    rows,
    count: dataToExport.length,
    entityName: strings.bills.export.entityName,
  });
}
