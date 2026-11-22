import { formatCurrency, formatDate, formatTime } from '../../lib/formatters';
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

  const fontStyle = { fontFamily: '"Times New Roman", Times, serif' };

  const renderSection = (section) => {
    if (!section.visible) return null;
    const opts = section.options || {};
    const alignClass = getAlignClass(opts.alignment);
    const fontClass = getFontSizeClass(opts.fontSize);

    const invoiceNumber = sale.invoiceNumber ?? sale.invoice_number;
    const createdAt = sale.createdAt ?? sale.created_at;
    const customerName = sale.customerName ?? sale.customer_name;
    const billerName = sale.billerName ?? sale.biller_name;
    const discountCents = sale.discountCents ?? sale.discount_cents ?? 0;
    const taxCents = sale.taxCents ?? sale.tax_cents ?? 0;
    const totalCents = sale.totalCents ?? sale.total_cents ?? 0;
    const paidCents = sale.paidCents ?? sale.paid_cents ?? totalCents;

    switch (section.id) {
      case 'storeHeader': {
        const isPreview = sale.invoiceNumber === strings.settings.sampleData.invoiceNumber;

        const optStoreName = opts.storeName?.trim();
        const optStoreDetails = opts.storeDetails?.trim();
        const optPhone = opts.phone?.trim();
        const optGst = opts.gst?.trim();

        const billStoreName = (billSettings?.storeName ?? billSettings?.store_name)?.trim();
        const billStoreDetails = (billSettings?.storeDetails ?? billSettings?.store_details)?.trim();
        const billPhone = billSettings?.phone?.trim();
        const billGst = billSettings?.gst?.trim();

        const genName = (generalSettings?.storeName ?? generalSettings?.store_name)?.trim();
        const genAddress = generalSettings?.address?.trim();
        const genPhone = generalSettings?.phone?.trim();
        const genGstin = generalSettings?.gstin?.trim();

        const storeName =
          optStoreName ||
          billStoreName ||
          genName ||
          (isPreview ? strings.settings.sampleData.storeName : strings.receipt.defaults.storeName);

        const storeDetails =
          optStoreDetails ||
          billStoreDetails ||
          genAddress ||
          (isPreview ? (strings.settings.sampleData.address || '#12 Market Street, Downtown') : '');

        const phone =
          optPhone ||
          billPhone ||
          genPhone ||
          (isPreview ? (strings.settings.sampleData.phone || '+91 98765 43210') : '');

        const gstin =
          optGst ||
          billGst ||
          genGstin ||
          (isPreview ? (strings.settings.sampleData.gstin || '29ABCDE1234F1Z5') : '');

        return (
          <div key="storeHeader" className={`pb-2 border-b-2 border-black ${alignClass} ${fontClass}`} style={fontStyle}>
            <h2 className="text-base font-black uppercase tracking-tight">{storeName}</h2>
            {storeDetails && <p className="whitespace-pre-line text-[11px]">{storeDetails}</p>}
            {phone && <p className="font-bold text-[11px]">{strings.receipt.labels.phoneLine(phone)}</p>}
            {gstin && <p className="font-bold text-[11px]">{strings.receipt.labels.gstinLine(gstin)}</p>}
          </div>
        );
      }

      case 'billMetadata': {
        const showCashier = opts.showCashier ?? true;
        const showCustomer = opts.showCustomer ?? true;
        return (
          <div key="billMetadata" className={`py-2 border-b border-black space-y-0.5 ${alignClass} ${fontClass}`} style={fontStyle}>
            <div className="flex justify-between">
              <span><strong className="font-bold">{strings.receipt.labels.billNo}:</strong> {invoiceNumber}</span>
              <span><strong className="font-bold">{strings.common.labels.date}:</strong> {formatDate(createdAt)}</span>
            </div>
            <div className="flex justify-between">
              {showCustomer ? (
                <span><strong className="font-bold">{strings.receipt.labels.cust}:</strong> {customerName || strings.receipt.defaults.cashCustomer}</span>
              ) : <span />}
              <span><strong className="font-bold">{strings.common.labels.time}:</strong> {formatTime(createdAt)}</span>
            </div>
            {showCashier && billerName && (
              <div><strong className="font-bold">{strings.receipt.labels.cashier}:</strong> {billerName}</div>
            )}
          </div>
        );
      }

      case 'itemsTable': {
        const showTax = opts.showTax ?? billSettings?.showTax ?? billSettings?.show_tax ?? false;
        return (
          <table key="itemsTable" className={`w-full my-2 border-collapse ${fontClass}`} style={fontStyle}>
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
                const unitPrice = it.unitPriceCents ?? it.unit_price_cents ?? 0;
                const itemDiscount = it.discountCents ?? it.discount_cents ?? 0;
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
          <div key="totals" className={`pt-1 border-t-2 border-black space-y-1 ${alignClass} ${fontClass}`} style={fontStyle}>
            {discountCents > 0 && (
              <div className="flex justify-between">
                <span>{strings.common.labels.discount}:</span>
                <span>-{formatCurrency(discountCents)}</span>
              </div>
            )}

            {showTax && taxCents > 0 && (
              <div className="flex justify-between">
                <span>{strings.receipt.labels.taxAmount}:</span>
                <span>{formatCurrency(taxCents)}</span>
              </div>
            )}

            <div className="flex justify-between font-black text-sm pt-1 border-t border-black">
              <span>{strings.common.labels.total}:</span>
              <span>{formatCurrency(totalCents)}</span>
            </div>
          </div>
        );
      }

      case 'footer': {
        const text = opts.text || billSettings?.footerText || strings.receipt.defaults.footerText;
        return (
          <div key="footer" className={`mt-4 pt-2 border-t border-black whitespace-pre-line ${alignClass} ${fontClass}`} style={fontStyle}>
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
    <div
      id="thermal-receipt"
      className={`${widthClass} bg-white text-black p-3 mx-auto leading-tight shadow-md rounded-sm`}
      style={fontStyle}
    >
      {isCancelled && (
        <div className="border-2 border-black border-dashed py-1.5 mb-2 text-center font-black text-sm uppercase tracking-wider" style={fontStyle}>
          {strings.receipt.banners.cancelled}
        </div>
      )}
      {!isCancelled && isAmended && (
        <div className="border border-black py-1 mb-2 text-center font-bold text-xs uppercase tracking-wide" style={fontStyle}>
          {strings.receipt.banners.amended}
        </div>
      )}
      {sections.map(renderSection)}
    </div>
  );
}
