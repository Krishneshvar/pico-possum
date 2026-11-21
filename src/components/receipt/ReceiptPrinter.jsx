import { formatPaise, formatDate, formatTime } from '../../lib/formatters';
import { strings } from '../../lib/strings';

export default function ReceiptPrinter({ sale, items, billSettings, generalSettings }) {
  if (!sale) return null;

  const is58mm = (billSettings?.paperWidth ?? billSettings?.paper_width) === '58mm';
  const widthClass = is58mm ? 'w-[58mm] text-[11px]' : 'w-[80mm] text-[12px]';

  // Sections config or default order
  const sections = billSettings?.sections || [
    { id: 'storeHeader', visible: true, options: {} },
    { id: 'billMetadata', visible: true, options: {} },
    { id: 'itemsTable', visible: true, options: {} },
    { id: 'totals', visible: true, options: {} },
    { id: 'footer', visible: true, options: {} },
  ];

  const getAlignClass = (align) => {
    if (align === 'left') return 'text-left';
    if (align === 'right') return 'text-right';
    return 'text-center';
  };

  const getFontSizeClass = (size) => {
    if (size === 'large') return 'text-[13px]';
    if (size === 'small') return 'text-[10px]';
    return 'text-[11px]';
  };

  const renderSection = (section) => {
    if (!section.visible) return null;
    const opts = section.options || {};
    const alignClass = getAlignClass(opts.alignment);
    const fontClass = getFontSizeClass(opts.fontSize);

    const invoiceNumber = sale.invoiceNumber ?? sale.invoice_number;
    const createdAt = sale.createdAt ?? sale.created_at;
    const customerName = sale.customerName ?? sale.customer_name;
    const billerName = sale.billerName ?? sale.biller_name;
    const subtotalPaise = sale.subtotalPaise ?? sale.subtotal_paise ?? 0;
    const discountPaise = sale.discountPaise ?? sale.discount_paise ?? 0;
    const taxPaise = sale.taxPaise ?? sale.tax_paise ?? 0;
    const totalPaise = sale.totalPaise ?? sale.total_paise ?? 0;
    const paidPaise = sale.paidPaise ?? sale.paid_paise ?? totalPaise;
    const paymentMethod = sale.paymentMethod ?? sale.payment_method;

    switch (section.id) {
      case 'storeHeader': {
        const storeName = opts.storeName || billSettings?.storeName || generalSettings?.storeName || strings.receipt.defaults.storeName;
        const storeDetails = opts.storeDetails || billSettings?.storeDetails || generalSettings?.address || '';
        const phone = opts.phone || billSettings?.phone || generalSettings?.phone || '';
        const gstin = opts.gst || billSettings?.gst || generalSettings?.gstin || '';

        return (
          <div key="storeHeader" className={`pb-2 border-b-2 border-black ${alignClass} ${fontClass}`}>
            <h2 className="text-base font-black uppercase tracking-tight">{storeName}</h2>
            {storeDetails && <p className="whitespace-pre-line text-[11px]">{storeDetails}</p>}
            {phone && <p className="font-bold text-[11px]">{strings.receipt.labels.phoneLine(phone)}</p>}
            {gstin && <p className="font-bold text-[11px]">{strings.receipt.labels.gstinLine(gstin)}</p>}
          </div>
        );
      }

      case 'billMetadata': {
        return (
          <div key="billMetadata" className={`py-2 border-b border-black space-y-0.5 ${alignClass} ${fontClass}`}>
            <div className="flex justify-between">
              <span><strong className="font-bold">{strings.receipt.labels.billNo}:</strong> {invoiceNumber}</span>
              <span><strong className="font-bold">{strings.common.labels.date}:</strong> {formatDate(createdAt)}</span>
            </div>
            <div className="flex justify-between">
              <span><strong className="font-bold">{strings.receipt.labels.cust}:</strong> {customerName || strings.receipt.defaults.cashCustomer}</span>
              <span><strong className="font-bold">{strings.common.labels.time}:</strong> {formatTime(createdAt)}</span>
            </div>
            {billerName && (
              <div><strong className="font-bold">{strings.receipt.labels.cashier}:</strong> {billerName}</div>
            )}
          </div>
        );
      }

      case 'itemsTable': {
        const showTax = opts.showTax ?? billSettings?.showTax ?? billSettings?.show_tax ?? false;
        return (
          <table key="itemsTable" className={`w-full my-2 border-collapse ${fontClass}`}>
            <thead>
              <tr className="border-b-2 border-black font-bold">
                <th className="text-left py-1">{strings.receipt.columns.item}</th>
                <th className="text-center py-1 w-10">{strings.receipt.columns.qty}</th>
                <th className="text-right py-1 w-14">{strings.receipt.columns.rate}</th>
                {showTax && <th className="text-right py-1 w-12">{strings.receipt.columns.tax}</th>}
                <th className="text-right py-1 w-16">{strings.receipt.columns.amount}</th>
              </tr>
            </thead>
            <tbody>
              {(items || []).map((it, idx) => {
                const unitPrice = it.unitPricePaise ?? it.unit_price_paise ?? 0;
                const itemDiscount = it.discountPaise ?? it.discount_paise ?? 0;
                const productName = it.productName ?? it.product_name ?? it.name;
                const taxRate = it.taxRate ?? it.tax_rate ?? 0;
                const lineTotal = (unitPrice * it.quantity) - itemDiscount;
                return (
                  <tr key={idx} className="border-b border-dotted border-gray-400">
                    <td className="py-1 pr-1 break-words whitespace-normal leading-tight">{productName}</td>
                    <td className="text-center py-1">{it.quantity}</td>
                    <td className="text-right py-1">{(unitPrice / 100).toFixed(2)}</td>
                    {showTax && <td className="text-right py-1 font-mono">{strings.receipt.columns.taxRate(taxRate)}</td>}
                    <td className="text-right py-1">{(lineTotal / 100).toFixed(2)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        );
      }

      case 'totals': {
        const showTax = opts.showTax ?? billSettings?.showTax ?? billSettings?.show_tax ?? false;
        return (
          <div key="totals" className={`pt-1 border-t-2 border-black space-y-1 ${alignClass} ${fontClass}`}>
            <div className="flex justify-between">
              <span>{strings.common.labels.subtotal}:</span>
              <span>{formatPaise(subtotalPaise)}</span>
            </div>

            {discountPaise > 0 && (
              <div className="flex justify-between">
                <span>{strings.common.labels.discount}:</span>
                <span>-{formatPaise(discountPaise)}</span>
              </div>
            )}

            {showTax && taxPaise > 0 && (
              <div className="flex justify-between">
                <span>{strings.receipt.labels.taxAmount}:</span>
                <span>{formatPaise(taxPaise)}</span>
              </div>
            )}

            <div className="flex justify-between font-black text-sm pt-1 border-t border-black">
              <span>{strings.common.labels.total}:</span>
              <span>{formatPaise(totalPaise)}</span>
            </div>

            {paymentMethod && (
              <div className="flex justify-between text-[11px] pt-0.5">
                <span>{strings.receipt.labels.paidVia(paymentMethod.replace(/_/g, ' '))}</span>
                <span>{formatPaise(paidPaise)}</span>
              </div>
            )}

            {paidPaise > totalPaise && (
              <div className="flex justify-between text-[11px]">
                <span>{strings.receipt.labels.change}:</span>
                <span>{formatPaise(paidPaise - totalPaise)}</span>
              </div>
            )}

            {paidPaise < totalPaise && (
              <div className="flex justify-between text-[11px] font-bold">
                <span>{strings.receipt.labels.balanceDue}:</span>
                <span>{formatPaise(totalPaise - paidPaise)}</span>
              </div>
            )}
          </div>
        );
      }

      case 'footer': {
        const text = opts.text || billSettings?.footerText || strings.receipt.defaults.footerText;
        return (
          <div key="footer" className={`mt-4 pt-2 border-t border-black whitespace-pre-line ${alignClass} ${fontClass}`}>
            {text}
          </div>
        );
      }

      default:
        return null;
    }
  };

  const isCancelled = sale.status === 'CANCELLED';
  const isAmended = !!sale.notes && sale.notes.includes('[Amended:');

  return (
    <div id="thermal-receipt" className={`${widthClass} bg-white text-black p-3 font-mono mx-auto leading-tight shadow-md rounded-sm`}>
      {isCancelled && (
        <div className="border-2 border-black border-dashed py-1.5 mb-2 text-center font-black text-sm uppercase tracking-wider">
          {strings.receipt.banners.cancelled}
        </div>
      )}
      {!isCancelled && isAmended && (
        <div className="border border-black py-1 mb-2 text-center font-bold text-xs uppercase tracking-wide">
          {strings.receipt.banners.amended}
        </div>
      )}
      {sections.map(renderSection)}
    </div>
  );
}
