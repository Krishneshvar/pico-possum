import { useMutation } from '@tanstack/react-query';
import { Receipt, ArrowUp, ArrowDown, RefreshCw, Check, Printer, Upload, X, Image as ImageIcon } from 'lucide-react';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { strings } from '../../../lib/strings';

const sampleSale = {
  invoiceNumber: strings.settings.sampleData.invoiceNumber,
  createdAt: new Date().toISOString(),
  paymentMethod: strings.settings.sampleData.paymentMethod,
  subtotalCents: 35000,
  discountCents: 3500,
  taxCents: 1750,
  totalCents: 33250,
};

const sampleItems = [
  { id: 1, productName: strings.settings.sampleData.sampleItem1, quantity: 2, unitPriceCents: 10000, taxRate: 5, totalCents: 20000 },
  { id: 2, productName: strings.settings.sampleData.sampleItem2, quantity: 1, unitPriceCents: 15000, taxRate: 5, totalCents: 15000 },
];

export default function ReceiptTab({ bill, setBill, general, onSave, isSaving }) {
  const testPrintMutation = useMutation({
    mutationFn: () =>
      tauriApi.testPrint(
        (general?.defaultPrinterName ?? general?.default_printer_name) || null,
        (bill.paperWidth ?? bill.paper_width) || '80mm'
      ),
    onSuccess: () => toast.success(strings.settings.toasts.testPrintSent),
    onError: (err) =>
      toast.error(strings.settings.toasts.testPrintFailed(err.message), {
        title: strings.settings.toasts.testPrintFailedTitle,
      }),
  });

  const moveSection = (index, direction) => {
    const newSections = [...bill.sections];
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= newSections.length) return;
    const temp = newSections[index];
    newSections[index] = newSections[targetIndex];
    newSections[targetIndex] = temp;
    setBill({ ...bill, sections: newSections });
  };

  const toggleSectionVisible = (sectionId) => {
    const newSections = bill.sections.map((s) =>
      s.id === sectionId ? { ...s, visible: !s.visible } : s
    );
    setBill({ ...bill, sections: newSections });
  };

  const updateSectionOption = (sectionId, optionKey, value) => {
    const newSections = bill.sections.map((s) => {
      if (s.id === sectionId) {
        return {
          ...s,
          options: {
            ...s.options,
            [optionKey]: value,
          },
        };
      }
      return s;
    });
    setBill({ ...bill, sections: newSections });
  };

  const handleLogoUpload = (e, sectionId) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      toast.error('Please upload an image file (PNG, JPG, SVG, WebP)');
      return;
    }

    const reader = new FileReader();
    reader.onload = (readerEvent) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement('canvas');
        const maxW = 240;
        const maxH = 120;
        let w = img.width;
        let h = img.height;
        if (w > maxW || h > maxH) {
          const ratio = Math.min(maxW / w, maxH / h);
          w = Math.round(w * ratio);
          h = Math.round(h * ratio);
        }
        canvas.width = w;
        canvas.height = h;
        const ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, w, h);
        const dataUrl = canvas.toDataURL('image/png');

        updateSectionOption(sectionId, 'logoData', dataUrl);
        setBill((prev) => ({ ...prev, logoData: dataUrl }));
      };
      img.src = readerEvent.target.result;
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  const handleRemoveLogo = (sectionId) => {
    updateSectionOption(sectionId, 'logoData', null);
    setBill((prev) => ({ ...prev, logoData: null }));
  };

  return (
    <div className="grid grid-cols-1 xl:grid-cols-12 gap-8 items-start">
      {/* Left Column: Receipt Controls (7 cols) */}
      <div className="xl:col-span-7 space-y-6">
        <div>
          <h2 className="text-base font-semibold text-content">{strings.settings.receipt.title}</h2>
          <p className="text-xs text-content-secondary">
            {strings.settings.receipt.subtitle}
          </p>
        </div>

        {/* Paper Roll Width */}
        <div className="p-6 rounded-xl bg-surface border border-border space-y-3">
          <h3 className="text-xs font-semibold text-content uppercase tracking-wider">
            {strings.settings.receipt.paperRollWidth}
          </h3>
          <div className="grid grid-cols-2 gap-3 max-w-sm">
            {['80mm', '58mm'].map((w) => (
              <button
                key={w}
                type="button"
                onClick={() => setBill({ ...bill, paperWidth: w })}
                className={`p-3 rounded-lg font-medium border text-xs flex items-center justify-center gap-2 transition-colors ${
                  (bill.paperWidth ?? bill.paper_width) === w
                    ? 'bg-brand text-white border-brand shadow-sm'
                    : 'bg-surface-secondary text-content-secondary border-border hover:text-content'
                }`}
              >
                <Receipt className="w-4 h-4" />
                <span>
                  {w === '80mm'
                    ? strings.settings.receipt.paperWidths.wide
                    : strings.settings.receipt.paperWidths.narrow}
                </span>
              </button>
            ))}
          </div>
        </div>

        {/* Section Reordering & Options */}
        <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
          <div>
            <h3 className="text-xs font-semibold text-content uppercase tracking-wider">
              {strings.settings.receipt.sectionsHeading}
            </h3>
            <p className="text-xs text-content-secondary mt-1">
              {strings.settings.receipt.sectionsHint}
            </p>
          </div>

          <div className="space-y-3">
            {bill.sections.map((section, idx) => {
              const isFirst = idx === 0;
              const isLast = idx === bill.sections.length - 1;
              const opts = section.options || {};

              return (
                <div
                  key={section.id}
                  className={`p-3.5 rounded-lg border transition-colors ${
                    section.visible
                      ? 'bg-surface-secondary border-border'
                      : 'bg-surface border-border opacity-60'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <label className="flex items-center gap-2.5 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={section.visible}
                        onChange={() => toggleSectionVisible(section.id)}
                        className="w-4 h-4 rounded border-border-strong text-brand focus:ring-brand cursor-pointer"
                      />
                      <span className="text-xs font-semibold text-content">{section.name}</span>
                    </label>

                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        disabled={isFirst}
                        onClick={() => moveSection(idx, -1)}
                        className="p-1 rounded hover:bg-surface border border-transparent hover:border-border text-content-secondary hover:text-content disabled:opacity-30 disabled:pointer-events-none"
                        title={strings.settings.receipt.moveUp}
                      >
                        <ArrowUp className="w-3.5 h-3.5" />
                      </button>
                      <button
                        type="button"
                        disabled={isLast}
                        onClick={() => moveSection(idx, 1)}
                        className="p-1 rounded hover:bg-surface border border-transparent hover:border-border text-content-secondary hover:text-content disabled:opacity-30 disabled:pointer-events-none"
                        title={strings.settings.receipt.moveDown}
                      >
                        <ArrowDown className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>

                  {/* Contextual Section Options */}
                  {section.visible && (
                    <div className="mt-3 pt-3 border-t border-border grid grid-cols-2 gap-3 text-xs">
                      {opts.alignment !== undefined && (
                        <div>
                          <label className="text-[11px] text-content-secondary block mb-1">
                            {strings.settings.receipt.alignment}
                          </label>
                          <select
                            value={opts.alignment}
                            onChange={(e) =>
                              updateSectionOption(section.id, 'alignment', e.target.value)
                            }
                            className="w-full bg-surface border border-border-strong rounded p-1.5 text-content"
                          >
                            <option value="left">{strings.settings.receipt.alignmentOptions.left}</option>
                            <option value="center">{strings.settings.receipt.alignmentOptions.center}</option>
                            <option value="right">{strings.settings.receipt.alignmentOptions.right}</option>
                          </select>
                        </div>
                      )}

                      {opts.fontSize !== undefined && (
                        <div>
                          <label className="text-[11px] text-content-secondary block mb-1">
                            {strings.settings.receipt.fontSize}
                          </label>
                          <select
                            value={opts.fontSize}
                            onChange={(e) =>
                              updateSectionOption(section.id, 'fontSize', e.target.value)
                            }
                            className="w-full bg-surface border border-border-strong rounded p-1.5 text-content"
                          >
                            <option value="small">{strings.settings.receipt.fontSizeOptions.small}</option>
                            <option value="medium">{strings.settings.receipt.fontSizeOptions.medium}</option>
                            <option value="large">{strings.settings.receipt.fontSizeOptions.large}</option>
                          </select>
                        </div>
                      )}

                      {section.id === 'storeHeader' && (
                        <>
                          <div className="col-span-2">
                            <label className="text-[11px] text-content-secondary block mb-1">
                              Store Logo (Monogram / Emblem)
                            </label>
                            <div className="flex items-center gap-3">
                              {(opts.logoData || bill.logoData) ? (
                                <div className="relative group p-1.5 bg-surface border border-border-strong rounded flex items-center justify-center">
                                  <img
                                    src={opts.logoData || bill.logoData}
                                    alt="Store Logo"
                                    className="h-10 max-w-[100px] object-contain"
                                  />
                                  <button
                                    type="button"
                                    onClick={() => handleRemoveLogo(section.id)}
                                    className="absolute -top-1.5 -right-1.5 p-0.5 rounded-full bg-red-600 text-white hover:bg-red-700 shadow cursor-pointer"
                                    title="Remove Logo"
                                  >
                                    <X className="w-3 h-3" />
                                  </button>
                                </div>
                              ) : (
                                <div className="w-12 h-10 border border-dashed border-border-strong rounded flex items-center justify-center text-content-muted">
                                  <ImageIcon className="w-5 h-5 opacity-40" />
                                </div>
                              )}
                              <div>
                                <label className="px-3 py-1.5 rounded bg-surface hover:bg-surface-secondary border border-border-strong text-content text-xs font-medium inline-flex items-center gap-1.5 cursor-pointer transition-colors shadow-sm">
                                  <Upload className="w-3.5 h-3.5" />
                                  <span>{(opts.logoData || bill.logoData) ? 'Change Logo' : 'Upload Logo'}</span>
                                  <input
                                    type="file"
                                    accept="image/*"
                                    onChange={(e) => handleLogoUpload(e, section.id)}
                                    className="hidden"
                                  />
                                </label>
                                <span className="text-[10px] text-content-muted mt-0.5 block">
                                  PNG or JPG. Displayed next to store title on receipt.
                                </span>
                              </div>
                            </div>
                          </div>

                          <div className="col-span-2 sm:col-span-1">
                            <label className="text-[11px] text-content-secondary block mb-1">
                              {strings.settings.general.storeName}
                            </label>
                            <input
                              type="text"
                              value={opts.storeName ?? ''}
                              onChange={(e) => {
                                updateSectionOption(section.id, 'storeName', e.target.value);
                                setBill((prev) => ({ ...prev, storeName: e.target.value }));
                              }}
                              placeholder={general?.storeName || general?.store_name || strings.receipt.defaults.storeName}
                              className="w-full bg-surface border border-border-strong rounded p-1.5 text-content text-xs"
                            />
                            <span className="text-[10px] text-content-muted mt-0.5 block">
                              Leave empty to use Store Profile
                            </span>
                          </div>

                          <div className="col-span-2 sm:col-span-1">
                            <label className="text-[11px] text-content-secondary block mb-1">
                              {strings.settings.general.phone}
                            </label>
                            <input
                              type="text"
                              value={opts.phone ?? ''}
                              onChange={(e) => {
                                updateSectionOption(section.id, 'phone', e.target.value);
                                setBill((prev) => ({ ...prev, phone: e.target.value }));
                              }}
                              placeholder={general?.phone || 'e.g. +91 9876543210'}
                              className="w-full bg-surface border border-border-strong rounded p-1.5 text-content text-xs font-mono"
                            />
                            <span className="text-[10px] text-content-muted mt-0.5 block">
                              Leave empty to use Store Profile
                            </span>
                          </div>

                          <div className="col-span-2">
                            <label className="text-[11px] text-content-secondary block mb-1">
                              {strings.settings.general.address}
                            </label>
                            <input
                              type="text"
                              value={opts.storeDetails ?? ''}
                              onChange={(e) => {
                                updateSectionOption(section.id, 'storeDetails', e.target.value);
                                setBill((prev) => ({ ...prev, storeDetails: e.target.value }));
                              }}
                              placeholder={general?.address || 'Store address or location'}
                              className="w-full bg-surface border border-border-strong rounded p-1.5 text-content text-xs"
                            />
                            <span className="text-[10px] text-content-muted mt-0.5 block">
                              Leave empty to use Store Profile
                            </span>
                          </div>

                          <div className="col-span-2 sm:col-span-1">
                            <label className="text-[11px] text-content-secondary block mb-1">
                              {strings.settings.storeTax.gstin}
                            </label>
                            <input
                              type="text"
                              value={opts.gst ?? ''}
                              onChange={(e) => {
                                updateSectionOption(section.id, 'gst', e.target.value);
                                setBill((prev) => ({ ...prev, gst: e.target.value }));
                              }}
                              placeholder={general?.gstin || 'Tax registration ID'}
                              className="w-full bg-surface border border-border-strong rounded p-1.5 text-content text-xs font-mono"
                            />
                            <span className="text-[10px] text-content-muted mt-0.5 block">
                              Leave empty to use Store & Tax GSTIN
                            </span>
                          </div>
                        </>
                      )}

                      {section.id === 'billMetadata' && (
                        <div className="col-span-2 flex flex-wrap gap-4 pt-1">
                          <label className="flex items-center gap-2 cursor-pointer">
                            <input
                              type="checkbox"
                              checked={opts.showCustomer ?? true}
                              onChange={(e) =>
                                updateSectionOption(section.id, 'showCustomer', e.target.checked)
                              }
                              className="w-3.5 h-3.5 rounded border-border-strong text-brand focus:ring-brand cursor-pointer"
                            />
                            <span className="text-[11px] text-content-secondary">
                              Print Customer Name
                            </span>
                          </label>

                          <label className="flex items-center gap-2 cursor-pointer">
                            <input
                              type="checkbox"
                              checked={opts.showCashier ?? true}
                              onChange={(e) =>
                                updateSectionOption(section.id, 'showCashier', e.target.checked)
                              }
                              className="w-3.5 h-3.5 rounded border-border-strong text-brand focus:ring-brand cursor-pointer"
                            />
                            <span className="text-[11px] text-content-secondary">
                              Print Cashier Name
                            </span>
                          </label>
                        </div>
                      )}

                      {section.id === 'itemsTable' && (
                        <div className="col-span-2">
                          <label className="flex items-center gap-2 cursor-pointer pt-1">
                            <input
                              type="checkbox"
                              checked={opts.showTax ?? (bill.showTax ?? false)}
                              onChange={(e) => {
                                updateSectionOption(section.id, 'showTax', e.target.checked);
                                setBill((prev) => ({ ...prev, showTax: e.target.checked }));
                              }}
                              className="w-3.5 h-3.5 rounded border-border-strong text-brand focus:ring-brand cursor-pointer"
                            />
                            <span className="text-[11px] text-content-secondary">
                              {strings.settings.receipt.printTaxColumn}
                            </span>
                          </label>
                        </div>
                      )}

                      {section.id === 'totals' && (
                        <div className="col-span-2">
                          <label className="flex items-center gap-2 cursor-pointer pt-1">
                            <input
                              type="checkbox"
                              checked={opts.showTax ?? (bill.showTax ?? false)}
                              onChange={(e) => {
                                updateSectionOption(section.id, 'showTax', e.target.checked);
                                setBill((prev) => ({ ...prev, showTax: e.target.checked }));
                              }}
                              className="w-3.5 h-3.5 rounded border-border-strong text-brand focus:ring-brand cursor-pointer"
                            />
                            <span className="text-[11px] text-content-secondary">
                              Print tax amount breakdown in totals
                            </span>
                          </label>
                        </div>
                      )}

                      {section.id === 'footer' && (
                        <div className="col-span-2">
                          <label className="text-[11px] text-content-secondary block mb-1">
                            {strings.settings.receipt.footerText}
                          </label>
                          <textarea
                            rows={3}
                            value={opts.text !== undefined ? opts.text : (bill.footerText || '')}
                            onChange={(e) => {
                              updateSectionOption(section.id, 'text', e.target.value);
                              setBill((prev) => ({ ...prev, footerText: e.target.value }));
                            }}
                            placeholder={strings.settings.receipt.footerPlaceholder}
                            className="w-full bg-surface border border-border-strong rounded p-2 text-content text-xs resize-y font-sans leading-relaxed focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand"
                          />
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {/* Save CTA */}
        <div className="flex items-center gap-3 pt-2">
          <button
            type="button"
            onClick={() => onSave(bill)}
            disabled={isSaving}
            className="px-5 py-2.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-medium text-xs flex items-center gap-2 transition-colors cursor-pointer"
          >
            {isSaving ? (
              <RefreshCw className="w-4 h-4 animate-spin" />
            ) : (
              <Check className="w-4 h-4" />
            )}
            <span>{strings.settings.receipt.save}</span>
          </button>
        </div>
      </div>

      {/* Right Column: Thermal Paper Preview (5 cols) */}
      <div className="xl:col-span-5 sticky top-6">
        <div className="p-4 rounded-xl bg-surface border border-border space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-semibold text-content uppercase tracking-wider">
              {strings.settings.receipt.previewTitle(bill.paperWidth ?? bill.paper_width)}
            </h3>
            <button
              type="button"
              onClick={() => testPrintMutation.mutate()}
              disabled={testPrintMutation.isPending}
              className="px-2.5 py-1 rounded bg-surface-secondary hover:bg-border text-content text-[11px] font-medium flex items-center gap-1.5 transition-colors cursor-pointer border border-border"
              title="Test Print Thermal Receipt"
            >
              {testPrintMutation.isPending ? (
                <RefreshCw className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <Printer className="w-3.5 h-3.5" />
              )}
              <span>Test Print</span>
            </button>
          </div>

          <div className="bg-neutral-100 dark:bg-neutral-900 p-4 rounded-lg flex justify-center border border-border overflow-x-auto">
            <div className="bg-white text-black p-4 shadow-md border border-neutral-300" style={{ fontFamily: '"Times New Roman", Times, serif' }}>
              <ReceiptPrinter
                sale={sampleSale}
                items={sampleItems}
                billSettings={bill}
                generalSettings={general}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
