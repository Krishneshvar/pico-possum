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

    const rawInvoiceNumber = sale.invoiceNumber ?? sale.invoice_number;
    const invoiceMatch = String(rawInvoiceNumber || '').match(/(\d+)$/);
    const invoiceNumber = invoiceMatch ? invoiceMatch[1] : rawInvoiceNumber;
    const createdAt = sale.createdAt ?? sale.created_at;
    const customerName = sale.customerName ?? sale.customer_name;
    const billerName = sale.billerName ?? sale.biller_name;
    const subtotalCents = sale.subtotalCents ?? sale.subtotal_cents;
    const billDiscountCents = sale.discountCents ?? sale.discount_cents ?? 0;
    const itemsGrossSubtotalCents = (items || []).reduce(
      (sum, it) => sum + ((it.unitPriceCents ?? it.unit_price_cents ?? 0) * (it.quantity ?? 1)),
      0
    );
    const itemsDiscountCents = (items || []).reduce(
      (sum, it) => sum + (it.discountCents ?? it.discount_cents ?? 0),
      0
    );
    const totalDiscountCents = itemsDiscountCents + billDiscountCents;
    const taxCents = sale.taxCents ?? sale.tax_cents ?? 0;
    const totalCents = sale.totalCents ?? sale.total_cents ?? 0;
    const effectiveSubtotalCents = (items && items.length > 0)
      ? itemsGrossSubtotalCents
      : (subtotalCents ?? (totalCents + billDiscountCents));

    switch (section.id) {
      case 'storeHeader': {
        const isPreview = sale.invoiceNumber === strings.settings.sampleData.invoiceNumber;

        const optStoreName = opts.storeName?.trim();
        const optStoreDetails = opts.storeDetails?.trim();
        const optPhone = opts.phone?.trim();
        const optGst = opts.gst?.trim();
        const logoData = opts.logoData || billSettings?.logoData || billSettings?.logo_data;

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

        const headerSize = opts.fontSize || 'medium';
        const titleSizeClass =
          headerSize === 'large'
            ? 'text-[28px] leading-tight'
            : headerSize === 'small'
            ? 'text-xl leading-tight'
            : 'text-2xl leading-tight';
        const logoSizeClass =
          headerSize === 'large'
            ? 'max-h-16 max-w-[112px]'
            : headerSize === 'small'
            ? 'max-h-11 max-w-[72px]'
            : 'max-h-14 max-w-[96px]';

        return (
          <div key="storeHeader" className={`pb-2 border-b-[2.5px] border-black ${alignClass} ${fontClass}`} style={fontStyle}>
            <div className="flex items-center justify-center gap-2 mb-1">
              {logoData && (
                <img
                  src={logoData}
                  alt="Store Logo"
                  className={`${logoSizeClass} object-contain shrink-0`}
                />
              )}
              <h2 className={`${titleSizeClass} font-bold tracking-tight`}>{storeName}</h2>
            </div>
            {storeDetails && <p className="whitespace-pre-line text-[11px] leading-tight">{storeDetails}</p>}
            {phone && (
              <p className="text-[11px]">
                <strong className="font-bold">Phone: </strong>
                <span>{phone}</span>
              </p>
            )}
            {gstin && (
              <p className="text-[11px]">
                <strong className="font-bold">GSTIN: </strong>
                <span>{gstin}</span>
              </p>
            )}
          </div>
        );
      }

      case 'billMetadata': {
        const showCashier = opts.showCashier ?? true;
        const showCustomer = opts.showCustomer ?? true;
        const hasRealCustomer = customerName && customerName.trim() !== '' && customerName.trim().toLowerCase() !== 'walk-in customer';

        return (
          <div key="billMetadata" className={`py-2 border-b-[2.5px] border-black space-y-0.5 ${alignClass} ${fontClass}`} style={fontStyle}>
            <div className="grid grid-cols-[54%_46%] items-start">
              <div className="text-left min-w-0 pr-1"><strong className="font-bold">Bill No: </strong><span>{invoiceNumber}</span></div>
              <div className="text-left"><strong className="font-bold">Date: </strong><span>{formatDate(createdAt)}</span></div>
            </div>
            <div className="grid grid-cols-[54%_46%] items-start">
              {showCustomer ? (
                <div className="text-left min-w-0 break-words pr-1">
                  <strong className="font-bold">Cust: </strong>
                  <span>{hasRealCustomer ? customerName : ''}</span>
                </div>
              ) : <div />}
              <div className="text-left"><strong className="font-bold">Time: </strong><span>{formatTime(createdAt)}</span></div>
            </div>
            {showCashier && billerName && (
              <div className="text-left"><strong className="font-bold">User: </strong><span>{billerName}</span></div>
            )}
          </div>
        );
      }

      case 'itemsTable': {
        const showTax = opts.showTax ?? billSettings?.showTax ?? billSettings?.show_tax ?? false;
        return (
          <table key="itemsTable" className={`w-full my-2 border-collapse ${fontClass}`} style={fontStyle}>
            <thead>
              <tr className="border-b-[2.5px] border-black font-bold">
                <th className="text-left py-1">Item</th>
                <th className="text-center py-1 w-10">Qty</th>
                <th className="text-right py-1 w-14">Rate</th>
                {showTax && <th className="text-right py-1 w-12">Tax%</th>}
                <th className="text-right py-1 w-16">Amt</th>
              </tr>
            </thead>
            <tbody>
              {(items || []).map((it, idx) => {
                const unitPrice = it.unitPriceCents ?? it.unit_price_cents ?? 0;
                const productName = it.productName ?? it.product_name ?? it.name;
                const taxRate = it.taxRate ?? it.tax_rate ?? 0;
                const lineTotal = unitPrice * it.quantity;
                return (
                  <tr key={idx} className="border-b border-dotted border-gray-400">
                    <td className="py-1 pr-1 break-words whitespace-normal leading-tight">{productName}</td>
                    <td className="text-center py-1">{it.quantity}</td>
                    <td className="text-right py-1">{(unitPrice / 100).toFixed(2)}</td>
                    {showTax && <td className="text-right py-1 font-mono">{taxRate}%</td>}
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
        const hasDiscount = totalDiscountCents > 0;
        const hasTax = showTax && taxCents > 0;
        const hasPreTotalRows = hasDiscount || hasTax;

        return (
          <div key="totals" className={`pt-1 border-t-[2.5px] border-black space-y-1 ${alignClass} ${fontClass}`} style={fontStyle}>
            {hasDiscount && (
              <>
                <div className="flex justify-between">
                  <span><strong className="font-bold">Subtotal: </strong></span>
                  <span>{formatCurrency(effectiveSubtotalCents)}</span>
                </div>
                <div className="flex justify-between">
                  <span><strong className="font-bold">Discount: </strong></span>
                  <span>-{formatCurrency(totalDiscountCents)}</span>
                </div>
              </>
            )}

            {hasTax && (
              <div className="flex justify-between">
                <span><strong className="font-bold">Tax Amount: </strong></span>
                <span>{formatCurrency(taxCents)}</span>
              </div>
            )}

            <div className={`flex justify-between font-black text-sm pt-1 ${hasPreTotalRows ? 'border-t-[2.5px] border-black' : ''}`}>
              <span>Total:</span>
              <span>{formatCurrency(totalCents)}</span>
            </div>
          </div>
        );
      }

      case 'footer': {
        const text = opts.text || billSettings?.footerText || strings.receipt.defaults.footerText;
        return (
          <div key="footer" className={`mt-4 pt-2 border-t-[2.5px] border-black whitespace-pre-line ${alignClass} ${fontClass}`} style={fontStyle}>
            {text}
          </div>
        );
      }

      default:
        return null;
    }
  };

  const isCancelled = sale.status === 'CANCELLED';

  return (
    <div
      id="thermal-receipt"
      className={`${widthClass} bg-white text-black pt-1.5 px-3 pb-3 mx-auto leading-tight shadow-md rounded-sm`}
      style={fontStyle}
    >
      {isCancelled && (
        <div className="border-2 border-black border-dashed py-1.5 mb-2 text-center font-black text-sm uppercase tracking-wider" style={fontStyle}>
          {strings.receipt.banners.cancelled}
        </div>
      )}
      {sections.map(renderSection)}
    </div>
  );
}
