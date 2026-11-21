import { formatPaise, formatDate } from '../../../lib/formatters';
import { strings } from '../../../lib/strings';

export const defaultColKeys = [
  'period',
  'totalTransactions',
  'cashPaise',
  'upiPaise',
  'cardPaise',
  'giftCardPaise',
  'totalDiscountPaise',
  'totalSalesPaise',
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
      key: 'cashPaise',
      label: strings.salesReports.columns.cashSettled,
      headerClassName: 'text-right',
      className: 'text-right financial-value text-text-secondary text-xs',
      minWidth: 110,
      exportValue: (r) => (((r.cashPaise ?? r.cash_paise ?? 0)) / 100).toFixed(2),
      render: (r) => formatPaise(r.cashPaise ?? r.cash_paise),
      isNumeric: true,
      totalKey: 'cash',
    },
    {
      key: 'upiPaise',
      label: strings.salesReports.columns.upiQr,
      headerClassName: 'text-right',
      className: 'text-right financial-value text-text-secondary text-xs',
      minWidth: 110,
      exportValue: (r) => (((r.upiPaise ?? r.upi_paise ?? 0)) / 100).toFixed(2),
      render: (r) => formatPaise(r.upiPaise ?? r.upi_paise),
      isNumeric: true,
      totalKey: 'upi',
    },
    {
      key: 'cardPaise',
      label: strings.salesReports.columns.cardTerminal,
      headerClassName: 'text-right',
      className: 'text-right financial-value text-text-secondary text-xs',
      minWidth: 110,
      exportValue: (r) => (((r.cardPaise ?? r.card_paise ?? 0)) / 100).toFixed(2),
      render: (r) => formatPaise(r.cardPaise ?? r.card_paise),
      isNumeric: true,
      totalKey: 'card',
    },
    {
      key: 'giftCardPaise',
      label: strings.salesReports.columns.giftCard,
      headerClassName: 'text-right',
      className: 'text-right financial-value text-text-secondary text-xs',
      minWidth: 100,
      exportValue: (r) => (((r.giftCardPaise ?? r.gift_card_paise ?? 0)) / 100).toFixed(2),
      render: (r) => formatPaise(r.giftCardPaise ?? r.gift_card_paise),
      isNumeric: true,
      totalKey: 'giftCard',
    },
    {
      key: 'totalDiscountPaise',
      label: strings.salesReports.columns.discounts,
      headerClassName: 'text-right',
      className: 'text-right financial-value text-amber-600 text-xs',
      minWidth: 100,
      exportValue: (r) => (((r.totalDiscountPaise ?? r.total_discount_paise ?? 0)) / 100).toFixed(2),
      render: (r) => {
        const disc = r.totalDiscountPaise ?? r.total_discount_paise ?? 0;
        return disc > 0 ? `-${formatPaise(disc)}` : strings.common.states.emptyValue;
      },
      isNumeric: true,
      totalKey: 'discount',
    },
    {
      key: 'totalSalesPaise',
      label: strings.salesReports.columns.grossSales,
      headerClassName: 'text-right',
      className: 'text-right financial-value text-text-primary font-medium text-xs',
      minWidth: 120,
      exportValue: (r) => (((r.totalSalesPaise ?? r.total_sales_paise ?? 0)) / 100).toFixed(2),
      render: (r) => formatPaise(r.totalSalesPaise ?? r.total_sales_paise),
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
        const gross = r.totalSalesPaise ?? r.total_sales_paise ?? 0;
        const disc = r.totalDiscountPaise ?? r.total_discount_paise ?? 0;
        return ((gross - disc) / 100).toFixed(2);
      },
      render: (r) => {
        const gross = r.totalSalesPaise ?? r.total_sales_paise ?? 0;
        const disc = r.totalDiscountPaise ?? r.total_discount_paise ?? 0;
        return formatPaise(Math.max(0, gross - disc));
      },
      sortValue: (r) => {
        const gross = r.totalSalesPaise ?? r.total_sales_paise ?? 0;
        const disc = r.totalDiscountPaise ?? r.total_discount_paise ?? 0;
        return Math.max(0, gross - disc);
      },
      isNumeric: true,
      totalKey: 'net',
    },
  ];
}
