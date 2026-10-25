import { formatPaise, formatDate, formatTime } from '../../lib/formatters';

export default function ReceiptPrinter({ sale, items, billSettings, generalSettings }) {
  if (!sale) return null;

  const is58mm = billSettings?.paper_width === '58mm';
  const widthClass = is58mm ? 'w-[58mm] text-[11px]' : 'w-[80mm] text-[12px]';
  const storeName = billSettings?.store_name || generalSettings?.store_name || 'STORE NAME';
  const phone = billSettings?.phone || generalSettings?.phone || '';
  const gstin = billSettings?.gst || generalSettings?.gstin || '';
  const storeDetails = billSettings?.store_details || generalSettings?.address || '';
  const footerText = billSettings?.footer_text || 'Thank you for your visit!';

  return (
    <div id="thermal-receipt" className={`${widthClass} bg-white text-black p-2 font-mono mx-auto leading-tight`}>
      {/* Store Header */}
      <div className="text-center pb-2 border-b-2 border-black">
        <h2 className="text-lg font-black uppercase tracking-tight">{storeName}</h2>
        {storeDetails && <p className="text-[11px] whitespace-pre-line">{storeDetails}</p>}
        {phone && <p className="text-[11px] font-bold">Ph: {phone}</p>}
        {gstin && <p className="text-[11px] font-bold">GSTIN: {gstin}</p>}
      </div>

      {/* Bill Meta */}
      <div className="py-2 border-b border-black text-[11px] space-y-0.5">
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

      {/* Line Items Table */}
      <table className="w-full my-2 border-collapse text-[11px]">
        <thead>
          <tr className="border-b-2 border-black font-bold">
            <th className="text-left py-1">Item</th>
            <th className="text-center py-1 w-10">Qty</th>
            <th className="text-right py-1 w-14">Rate</th>
            <th className="text-right py-1 w-16">Amt</th>
          </tr>
        </thead>
        <tbody>
          {(items || []).map((it, idx) => {
            const lineTotal = (it.unit_price_paise * it.quantity) - (it.discount_paise || 0);
            return (
              <tr key={idx} className="border-b border-dotted border-gray-400">
                <td className="py-1 pr-1 truncate max-w-[120px]">{it.product_name}</td>
                <td className="text-center py-1">{it.quantity}</td>
                <td className="text-right py-1">{(it.unit_price_paise / 100).toFixed(2)}</td>
                <td className="text-right py-1">{(lineTotal / 100).toFixed(2)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>

      {/* Totals Section */}
      <div className="pt-1 border-t-2 border-black space-y-1 text-[12px]">
        <div className="flex justify-between">
          <span>Subtotal:</span>
          <span>{formatPaise(sale.subtotal_paise)}</span>
        </div>

        {sale.discount_paise > 0 && (
          <div className="flex justify-between text-black">
            <span>Discount:</span>
            <span>-{formatPaise(sale.discount_paise)}</span>
          </div>
        )}

        {billSettings?.show_tax && sale.tax_paise > 0 && (
          <div className="flex justify-between">
            <span>Tax Amount:</span>
            <span>{formatPaise(sale.tax_paise)}</span>
          </div>
        )}

        <div className="flex justify-between font-black text-sm pt-1 border-t border-black">
          <span>Total:</span>
          <span>{formatPaise(sale.total_paise)}</span>
        </div>

        <div className="flex justify-between text-[11px] pt-1 border-t border-dotted border-gray-400">
          <span>Paid ({sale.payment_method}):</span>
          <span>{formatPaise(sale.paid_paise)}</span>
        </div>

        {sale.paid_paise > sale.total_paise && (
          <div className="flex justify-between text-[11px] font-bold">
            <span>Change Returned:</span>
            <span>{formatPaise(sale.paid_paise - sale.total_paise)}</span>
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="mt-4 pt-2 border-t border-black text-center text-[11px] whitespace-pre-line">
        {footerText}
      </div>
    </div>
  );
}
