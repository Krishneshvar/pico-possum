import { tauriApi } from '../../lib/tauri';
import { formatCurrency, formatDate, formatTime } from '../../lib/formatters';
import { strings } from '../../lib/strings';

/**
 * Builds the structured JSON receipt payload for native thermal printer rendering.
 * Conforms to:
 * - Full paper width (80mm / 58mm)
 * - Times New Roman font
 * - Store header (Name, Address, Phone, GSTIN)
 * - Items table with only Item Name (NO SKU!), Qty, Rate, Amount
 * - NO Subtotal
 * - Discount ONLY if discountCents > 0
 * - NO Paid via
 */
export function buildReceiptPayload({ sale, items = [], billSettings, generalSettings }) {
  if (!sale) return null;

  const is58mm = (billSettings?.paperWidth ?? billSettings?.paper_width) === '58mm';
  const isPreview = sale?.invoiceNumber === strings.settings.sampleData.invoiceNumber;

  const headerSection = billSettings?.sections?.find((s) => s.id === 'storeHeader');
  const showStoreHeader = headerSection?.visible ?? true;
  const headerOpts = headerSection?.options || {};

  const storeName = showStoreHeader
    ? headerOpts.storeName?.trim() ||
      (billSettings?.storeName ?? billSettings?.store_name)?.trim() ||
      (generalSettings?.storeName ?? generalSettings?.store_name)?.trim() ||
      (isPreview ? strings.settings.sampleData.storeName : strings.receipt.defaults.storeName)
    : null;

  const storeDetails = showStoreHeader
    ? headerOpts.storeDetails?.trim() ||
      (billSettings?.storeDetails ?? billSettings?.store_details)?.trim() ||
      generalSettings?.address?.trim() ||
      (isPreview ? (strings.settings.sampleData.address || '#12 Market Street, Downtown') : '')
    : null;

  const phone = showStoreHeader
    ? headerOpts.phone?.trim() ||
      billSettings?.phone?.trim() ||
      generalSettings?.phone?.trim() ||
      (isPreview ? (strings.settings.sampleData.phone || '+91 98765 43210') : '')
    : null;

  const gstin = showStoreHeader
    ? headerOpts.gst?.trim() ||
      billSettings?.gst?.trim() ||
      generalSettings?.gstin?.trim() ||
      (isPreview ? (strings.settings.sampleData.gstin || '29ABCDE1234F1Z5') : '')
    : null;

  const metaSection = billSettings?.sections?.find((s) => s.id === 'billMetadata');
  const showMetadata = metaSection?.visible ?? true;
  const metaOpts = metaSection?.options || {};

  const invoiceNumber = showMetadata ? (sale.invoiceNumber ?? sale.invoice_number ?? '—') : null;
  const createdAt = sale.createdAt ?? sale.created_at;
  const customerName = sale.customerName ?? sale.customer_name;
  const billerName = sale.billerName ?? sale.biller_name;

  const showCustomer = showMetadata && (metaOpts.showCustomer ?? true);
  const showCashier = showMetadata && (metaOpts.showCashier ?? true);
  const showTax = billSettings?.showTax ?? billSettings?.show_tax ?? false;

  const formattedItems = (items || []).map((it) => {
    const name = it.productName ?? it.product_name ?? it.name ?? 'Item';
    const quantity = it.quantity ?? 1;
    const unitPriceCents = it.unitPriceCents ?? it.unit_price_cents ?? 0;
    const itemDiscount = it.discountCents ?? it.discount_cents ?? 0;
    const totalCents = (unitPriceCents * quantity) - itemDiscount;
    const taxRate = it.taxRate ?? it.tax_rate ?? 0;
    return {
      name,
      quantity,
      unitPriceCents,
      totalCents,
      taxRate,
      rateStr: formatCurrency(unitPriceCents, '').trim(),
      amtStr: formatCurrency(totalCents, '').trim(),
    };
  });

  const discountCents = sale.discountCents ?? sale.discount_cents ?? 0;
  const taxCents = sale.taxCents ?? sale.tax_cents ?? 0;
  const totalCents = sale.totalCents ?? sale.total_cents ?? 0;

  const footerSection = billSettings?.sections?.find((s) => s.id === 'footer');
  const showFooter = footerSection?.visible ?? true;
  const footerOpts = footerSection?.options || {};
  const footerText = showFooter
    ? footerOpts.text || billSettings?.footerText || strings.receipt.defaults.footerText
    : null;

  const isCancelled = sale.status === 'CANCELLED';
  const isAmended = !!sale.notes && sale.notes.includes('[Amended:');
  const plainText = buildReceiptPlainText({ sale, items, billSettings, generalSettings });

  return {
    paperWidth: is58mm ? '58mm' : '80mm',
    storeName,
    storeDetails,
    phone,
    gstin,
    invoiceNumber,
    date: showMetadata ? formatDate(createdAt) : null,
    time: showMetadata ? formatTime(createdAt) : null,
    customerName: showCustomer ? (customerName || strings.receipt.defaults.cashCustomer) : null,
    billerName: showCashier && billerName ? billerName : null,
    showTax,
    items: formattedItems,
    discountCents,
    taxCents,
    totalCents,
    totalStr: formatCurrency(totalCents, '').trim(),
    discountStr: discountCents > 0 ? formatCurrency(discountCents, '').trim() : null,
    taxStr: taxCents > 0 ? formatCurrency(taxCents, '').trim() : null,
    currencySymbol: generalSettings?.currencySymbol ?? generalSettings?.currency_symbol ?? '$',
    footerText,
    isCancelled,
    isAmended,
    plainText,
  };
}

/**
 * Builds the plain-text receipt suitable for Unix `lp` spooling or raw text fallback.
 */
export function buildReceiptPlainText({ sale, items = [], billSettings, generalSettings }) {
  if (!sale) return '';

  const is58mm = (billSettings?.paperWidth ?? billSettings?.paper_width) === '58mm';
  const width = is58mm ? 32 : 42;
  const divider = '-'.repeat(width);

  const center = (text) => {
    if (!text) return '';
    const trimmed = text.trim();
    if (trimmed.length >= width) return trimmed;
    const pad = Math.floor((width - trimmed.length) / 2);
    return ' '.repeat(pad) + trimmed;
  };

  const justify = (left, right) => {
    const l = String(left || '');
    const r = String(right || '');
    const spaces = Math.max(1, width - l.length - r.length);
    return l + ' '.repeat(spaces) + r;
  };

  const lines = [];

  // Store Header
  const headerSection = billSettings?.sections?.find((s) => s.id === 'storeHeader');
  const showStoreHeader = headerSection?.visible ?? true;
  const headerOpts = headerSection?.options || {};

  const isPreview = sale?.invoiceNumber === strings.settings.sampleData.invoiceNumber;
  const storeName = showStoreHeader
    ? headerOpts.storeName?.trim() ||
      (billSettings?.storeName ?? billSettings?.store_name)?.trim() ||
      (generalSettings?.storeName ?? generalSettings?.store_name)?.trim() ||
      (isPreview ? strings.settings.sampleData.storeName : strings.receipt.defaults.storeName)
    : null;

  const storeDetails = showStoreHeader
    ? headerOpts.storeDetails?.trim() ||
      (billSettings?.storeDetails ?? billSettings?.store_details)?.trim() ||
      generalSettings?.address?.trim() ||
      (isPreview ? (strings.settings.sampleData.address || '#12 Market Street, Downtown') : '')
    : null;

  const phone = showStoreHeader
    ? headerOpts.phone?.trim() ||
      billSettings?.phone?.trim() ||
      generalSettings?.phone?.trim() ||
      (isPreview ? (strings.settings.sampleData.phone || '+91 98765 43210') : '')
    : null;

  const gstin = showStoreHeader
    ? headerOpts.gst?.trim() ||
      billSettings?.gst?.trim() ||
      generalSettings?.gstin?.trim() ||
      (isPreview ? (strings.settings.sampleData.gstin || '29ABCDE1234F1Z5') : '')
    : null;

  if (storeName) lines.push(center(storeName.toUpperCase()));
  if (storeDetails) {
    storeDetails.split('\n').forEach((d) => {
      if (d.trim()) lines.push(center(d.trim()));
    });
  }
  if (phone) lines.push(center(strings.receipt.labels.phoneLine(phone)));
  if (gstin) lines.push(center(strings.receipt.labels.gstinLine(gstin)));

  if (showStoreHeader && (storeName || storeDetails || phone || gstin)) {
    lines.push(divider);
  }

  // Bill Metadata
  const metaSection = billSettings?.sections?.find((s) => s.id === 'billMetadata');
  const showMetadata = metaSection?.visible ?? true;
  const metaOpts = metaSection?.options || {};

  if (showMetadata) {
    const invoiceNumber = sale.invoiceNumber ?? sale.invoice_number ?? '—';
    const createdAt = sale.createdAt ?? sale.created_at;
    const customerName = sale.customerName ?? sale.customer_name;
    const billerName = sale.billerName ?? sale.biller_name;

    lines.push(justify(`${strings.receipt.labels.billNo}: ${invoiceNumber}`, `${formatDate(createdAt)}`));
    const showCustomer = metaOpts.showCustomer ?? true;
    if (showCustomer) {
      const custDisplayName = customerName || strings.receipt.defaults.cashCustomer;
      lines.push(justify(`${strings.receipt.labels.cust}: ${custDisplayName}`, `${formatTime(createdAt)}`));
    } else {
      lines.push(justify('', `${formatTime(createdAt)}`));
    }
    const showCashier = metaOpts.showCashier ?? true;
    if (showCashier && billerName) {
      lines.push(`${strings.receipt.labels.cashier}: ${billerName}`);
    }
    lines.push(divider);
  }

  // Items Table (ONLY Item Name, NO SKU)
  const showTax = billSettings?.showTax ?? billSettings?.show_tax ?? false;
  if (is58mm) {
    lines.push(justify('Item/Qty x Rate', 'Amt'));
  } else {
    lines.push(justify('Item                 Qty    Rate', 'Amount'));
  }
  lines.push(divider);

  for (const it of items) {
    const productName = it.productName ?? it.product_name ?? it.name ?? 'Item';
    const qty = it.quantity ?? 1;
    const unitPrice = it.unitPriceCents ?? it.unit_price_cents ?? 0;
    const itemDiscount = it.discountCents ?? it.discount_cents ?? 0;
    const lineTotal = (unitPrice * qty) - itemDiscount;

    const rateStr = (unitPrice / 100).toFixed(2);
    const amtStr = (lineTotal / 100).toFixed(2);

    if (is58mm) {
      lines.push(productName);
      lines.push(justify(`  ${qty} x ${rateStr}`, amtStr));
    } else {
      const namePart = productName.length > 18 ? productName.slice(0, 18) + '..' : productName.padEnd(20);
      const qtyPart = String(qty).padStart(4);
      const ratePart = rateStr.padStart(8);
      const amtPart = amtStr.padStart(9);
      lines.push(`${namePart} ${qtyPart} ${ratePart} ${amtPart}`);
    }
  }

  lines.push(divider);

  // Totals (NO Subtotal, Discount ONLY if > 0, NO Paid via)
  const discountCents = sale.discountCents ?? sale.discount_cents ?? 0;
  const taxCents = sale.taxCents ?? sale.tax_cents ?? 0;
  const totalCents = sale.totalCents ?? sale.total_cents ?? 0;
  const paidCents = sale.paidCents ?? sale.paid_cents ?? totalCents;

  if (discountCents > 0) {
    lines.push(justify(strings.common.labels.discount + ':', `-${formatCurrency(discountCents)}`));
  }

  if (showTax && taxCents > 0) {
    lines.push(justify(strings.receipt.labels.taxAmount + ':', formatCurrency(taxCents)));
  }

  lines.push(justify(strings.common.labels.total.toUpperCase() + ':', formatCurrency(totalCents)));

  lines.push(divider);

  // Footer
  const footerSection = billSettings?.sections?.find((s) => s.id === 'footer');
  const showFooter = footerSection?.visible ?? true;
  const footerOpts = footerSection?.options || {};
  const footerText = showFooter
    ? footerOpts.text || billSettings?.footerText || strings.receipt.defaults.footerText
    : null;

  if (footerText) {
    footerText.split('\n').forEach((f) => {
      if (f.trim()) lines.push(center(f.trim()));
    });
  }

  lines.push('\n\n\n');
  return lines.join('\n');
}

/**
 * Executes direct printing to the thermal printer via Tauri backend without OS dialog.
 * Passes structured JSON for Windows GDI TrueType zero-margin rendering,
 * and falls back to window.print() if native printing is unavailable or fails.
 */
export async function executeDirectPrint({ sale, items = [], billSettings, generalSettings, fallback = true }) {
  const payload = buildReceiptPayload({ sale, items, billSettings, generalSettings });
  const payloadJson = payload ? JSON.stringify(payload) : buildReceiptPlainText({ sale, items, billSettings, generalSettings });
  const targetPrinter = generalSettings?.defaultPrinterName || generalSettings?.default_printer_name || null;

  try {
    if (window.__TAURI_INTERNALS__ || window.__TAURI__) {
      await tauriApi.printReceipt(payloadJson, targetPrinter);
      return { success: true, method: 'direct' };
    }
  } catch (err) {
    console.warn('[Print] Direct printing failed, using fallback:', err);
  }

  if (fallback) {
    setTimeout(() => {
      window.print();
    }, 100);
    return { success: true, method: 'fallback' };
  }

  return { success: false };
}
