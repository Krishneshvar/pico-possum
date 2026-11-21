import { Receipt, ArrowUp, ArrowDown, RefreshCw, Check } from 'lucide-react';
import ReceiptPrinter from '../../../components/receipt/ReceiptPrinter';
import { strings } from '../../../lib/strings';

const sampleSale = {
  invoiceNumber: strings.settings.sampleData.invoiceNumber,
  createdAt: new Date().toISOString(),
  paymentMethod: strings.settings.sampleData.paymentMethod,
  subtotalPaise: 35000,
  discountPaise: 3500,
  taxPaise: 1750,
  totalPaise: 33250,
};

const sampleItems = [
  { id: 1, productName: strings.settings.sampleData.sampleItem1, quantity: 2, unitPricePaise: 10000, taxRate: 5, totalPaise: 20000 },
  { id: 2, productName: strings.settings.sampleData.sampleItem2, quantity: 1, unitPricePaise: 15000, taxRate: 5, totalPaise: 15000 },
];

export default function ReceiptTab({ bill, setBill, general, onSave, isSaving }) {
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

                      {section.id === 'itemsTable' && (
                        <div className="col-span-2">
                          <label className="flex items-center gap-2 cursor-pointer pt-1">
                            <input
                              type="checkbox"
                              checked={opts.showTax ?? false}
                              onChange={(e) =>
                                updateSectionOption(section.id, 'showTax', e.target.checked)
                              }
                              className="w-3.5 h-3.5 rounded border-border-strong text-brand focus:ring-brand cursor-pointer"
                            />
                            <span className="text-[11px] text-content-secondary">
                              {strings.settings.receipt.printTaxColumn}
                            </span>
                          </label>
                        </div>
                      )}

                      {section.id === 'footer' && (
                        <div className="col-span-2">
                          <label className="text-[11px] text-content-secondary block mb-1">
                            {strings.settings.receipt.footerText}
                          </label>
                          <input
                            type="text"
                            value={opts.text || ''}
                            onChange={(e) =>
                              updateSectionOption(section.id, 'text', e.target.value)
                            }
                            placeholder={strings.settings.receipt.footerPlaceholder}
                            className="w-full bg-surface border border-border-strong rounded p-2 text-content text-xs"
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
            <span className="text-[11px] text-content-muted">{strings.settings.receipt.sampleTransaction}</span>
          </div>

          <div className="bg-neutral-100 dark:bg-neutral-900 p-4 rounded-lg flex justify-center border border-border overflow-x-auto">
            <div className="bg-white text-black font-mono p-4 shadow-md border border-neutral-300">
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
