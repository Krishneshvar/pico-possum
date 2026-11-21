import { formatCurrency, formatDate } from '../../../lib/formatters';
import { strings } from '../../../lib/strings';

export const defaultColKeys = [
  'period',
  'totalTransactions',
  'cashCents',
  'upiCents',
  'cardCents',
  'giftCardCents',
  'totalDiscountCents',
  'totalSalesCents',
  'netSales',
];

export function getSalesReportColumns(reportType) {
  return [
    {
      key: 'period',
      label: strings.salesReports.columns.periodLabel(reportType),
      className: 'font-mono text-text-primary font-semibold text-xs whitespace-nowrap',
      minWidth: 120,
      exportValue: (r) => (reportType === 'yearly' ? r.period : formatDate(r.period)),
      render: (r) => (reportType === 'yearly' ? r.period : formatDate(r.period)),
    },
    {
      key: 'totalTransactions',
      label: strings.salesReports.columns.billsCount,
      headerClassName: 'text-center',
      className: 'text-center text-xs font-mono',
      minWidth: 90,
      exportValue: (r) => r.totalTransactions ?? r.total_transactions,
      render: (r) => r.totalTransactions ?? r.total_transactions,
      isNumeric: true,
      totalKey: 'transactions',
    },
    {
      key: 'cashCents',
      label: strings.salesReports.columns.cashSettled,
      headerClassName: 'text-right',
      className: 'text-right financial-value text-text-secondary text-xs',
      minWidth: 110,
      exportValue: (r) => (((r.cashCents ?? r.cash_cents ?? 0)) / 100).toFixed(2),
      render: (r) => formatCurrency(r.cashCents ?? r.cash_cents),
      isNumeric: true,
      totalKey: 'cash',
    },
    {
      key: 'upiCents',
      label: strings.salesReports.columns.upiQr,
      headerClassName: 'text-right',
      className: 'text-right financial-value text-text-secondary text-xs',
      minWidth: 110,
      exportValue: (r) => (((r.upiCents ?? r.upi_cents ?? 0)) / 100).toFixed(2),
      render: (r) => formatCurrency(r.upiCents ?? r.upi_cents),
      isNumeric: true,
      totalKey: 'upi',
    },
    {
      key: 'cardCents',
      label: strings.salesReports.columns.cardTerminal,
      headerClassName: 'text-right',
      className: 'text-right financial-value text-text-secondary text-xs',
      minWidth: 110,
      exportValue: (r) => (((r.cardCents ?? r.card_cents ?? 0)) / 100).toFixed(2),
      render: (r) => formatCurrency(r.cardCents ?? r.card_cents),
      isNumeric: true,
      totalKey: 'card',
    },
    {
      key: 'giftCardCents',
      label: strings.salesReports.columns.giftCard,
      headerClassName: 'text-right',
      className: 'text-right financial-value text-text-secondary text-xs',
      minWidth: 100,
      exportValue: (r) => (((r.giftCardCents ?? r.gift_card_cents ?? 0)) / 100).toFixed(2),
      render: (r) => formatCurrency(r.giftCardCents ?? r.gift_card_cents),
      isNumeric: true,
      totalKey: 'giftCard',
    },
    {
      key: 'totalDiscountCents',
      label: strings.salesReports.columns.discounts,
      headerClassName: 'text-right',
      className: 'text-right financial-value text-amber-600 text-xs',
      minWidth: 100,
      exportValue: (r) => (((r.totalDiscountCents ?? r.total_discount_cents ?? 0)) / 100).toFixed(2),
      render: (r) => {
        const disc = r.totalDiscountCents ?? r.total_discount_cents ?? 0;
        return disc > 0 ? `-${formatCurrency(disc)}` : strings.common.states.emptyValue;
      },
      isNumeric: true,
      totalKey: 'discount',
    },
    {
      key: 'totalSalesCents',
      label: strings.salesReports.columns.grossSales,
      headerClassName: 'text-right',
      className: 'text-right financial-value text-text-primary font-medium text-xs',
      minWidth: 120,
      exportValue: (r) => (((r.totalSalesCents ?? r.total_sales_cents ?? 0)) / 100).toFixed(2),
      render: (r) => formatCurrency(r.totalSalesCents ?? r.total_sales_cents),
      isNumeric: true,
      totalKey: 'gross',
    },
    {
      key: 'netSales',
      label: strings.salesReports.columns.netSales,
      headerClassName: 'text-right',
      className: 'text-right financial-value font-bold text-text-primary text-xs',
      minWidth: 120,
      exportValue: (r) => {
        const gross = r.totalSalesCents ?? r.total_sales_cents ?? 0;
        const disc = r.totalDiscountCents ?? r.total_discount_cents ?? 0;
        return ((gross - disc) / 100).toFixed(2);
      },
      render: (r) => {
        const gross = r.totalSalesCents ?? r.total_sales_cents ?? 0;
        const disc = r.totalDiscountCents ?? r.total_discount_cents ?? 0;
        return formatCurrency(Math.max(0, gross - disc));
      },
      sortValue: (r) => {
        const gross = r.totalSalesCents ?? r.total_sales_cents ?? 0;
        const disc = r.totalDiscountCents ?? r.total_discount_cents ?? 0;
        return Math.max(0, gross - disc);
      },
      isNumeric: true,
      totalKey: 'net',
    },
  ];
}
