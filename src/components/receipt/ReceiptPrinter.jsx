import { formatPaise, formatDate, formatTime } from '../../lib/formatters';

export default function ReceiptPrinter({ sale, items, billSettings, generalSettings }) {
  if (!sale) return null;

  const is58mm = billSettings?.paper_width === '58mm';
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

    switch (section.id) {
      case 'storeHeader': {
        const storeName = opts.storeName || billSettings?.store_name || generalSettings?.store_name || 'STORE NAME';
        const storeDetails = opts.storeDetails || billSettings?.store_details || generalSettings?.address || '';
        const phone = opts.phone || billSettings?.phone || generalSettings?.phone || '';
        const gstin = opts.gst || billSettings?.gst || generalSettings?.gstin || '';

        return (
          <div key="storeHeader" className={`pb-2 border-b-2 border-black ${alignClass} ${fontClass}`}>
            <h2 className="text-base font-black uppercase tracking-tight">{storeName}</h2>
            {storeDetails && <p className="whitespace-pre-line text-[11px]">{storeDetails}</p>}
            {phone && <p className="font-bold text-[11px]">Ph: {phone}</p>}
            {gstin && <p className="font-bold text-[11px]">GSTIN: {gstin}</p>}
          </div>
        );
      }

      case 'billMetadata': {
        return (
          <div key="billMetadata" className={`py-2 border-b border-black space-y-0.5 ${alignClass} ${fontClass}`}>
            <div className="flex justify-between">
              <span><strong className="font-bold">Bill No:</strong> {sale.invoice_number}</span>
              <span><strong className="font-bold">Date:</strong> {formatDate(sale.created_at)}</span>
            </div>
            <div className="flex justify-between">
              <span><strong className="font-bold">Cust:</strong> {sale.customer_name || 'Cash Customer'}</span>
              <span><strong className="font-bold">Time:</strong> {formatTime(sale.created_at)}</span>
            </div>
            {sale.biller_name && (
              <div><strong className="font-bold">Cashier:</strong> {sale.biller_name}</div>
            )}
          </div>
        );
      }

      case 'itemsTable': {
        const showTax = opts.showTax ?? billSettings?.show_tax ?? false;
        return (
          <table key="itemsTable" className={`w-full my-2 border-collapse ${fontClass}`}>
            <thead>
              <tr className="border-b-2 border-black font-bold">
                <th className="text-left py-1">Item</th>
                <th className="text-center py-1 w-10">Qty</th>
                <th className="text-right py-1 w-14">Rate</th>
                {showTax && <th className="text-right py-1 w-12">Tax%</th>}
                <th className="text-right py-1 w-16">Amt</th>
              </tr>
            </thead>
            <tbody>
              {(items || []).map((it, idx) => {
                const lineTotal = (it.unit_price_paise * it.quantity) - (it.discount_paise || 0);
                return (
                  <tr key={idx} className="border-b border-dotted border-gray-400">
                    <td className="py-1 pr-1 break-words whitespace-normal leading-tight">{it.product_name}</td>
                    <td className="text-center py-1">{it.quantity}</td>
                    <td className="text-right py-1">{(it.unit_price_paise / 100).toFixed(2)}</td>
                    {showTax && <td className="text-right py-1 font-mono">{it.tax_rate ?? 0}%</td>}
                    <td className="text-right py-1">{(lineTotal / 100).toFixed(2)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        );
      }

      case 'totals': {
        return (
          <div key="totals" className={`pt-1 border-t-2 border-black space-y-1 ${alignClass} ${fontClass}`}>
            <div className="flex justify-between">
              <span>Subtotal:</span>
              <span>{formatPaise(sale.subtotal_paise)}</span>
            </div>

            {sale.discount_paise > 0 && (
              <div className="flex justify-between">
                <span>Discount:</span>
                <span>-{formatPaise(sale.discount_paise)}</span>
              </div>
            )}

            {(opts.showTax ?? billSettings?.show_tax ?? false) && sale.tax_paise > 0 && (
              <div className="flex justify-between">
                <span>Tax Amount:</span>
                <span>{formatPaise(sale.tax_paise)}</span>
              </div>
            )}

            <div className="flex justify-between font-black text-sm pt-1 border-t border-black">
              <span>Total:</span>
              <span>{formatPaise(sale.total_paise)}</span>
            </div>
          </div>
        );
      }

      case 'footer': {
        const text = opts.text || billSettings?.footer_text || 'Thank you for your visit!';
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

  return (
    <div id="thermal-receipt" className={`${widthClass} bg-white text-black p-3 font-mono mx-auto leading-tight shadow-md rounded-sm`}>
      {sections.map(renderSection)}
    </div>
  );
}
