import { User, X, StickyNote, Receipt } from 'lucide-react';
import Select from '../../../../components/common/Select';
import { formatCurrency, unitsToCents } from '../../../../lib/formatters';
import { toast } from '../../../../stores/toast.store';
import { strings } from '../../../../lib/strings';

export default function PosCheckoutSidebar({
  bill,
  totals,
  bills = [],
  activeBillIndex = 0,
  onSwitchBill,
  onClearCart,
  customers = [],
  currencySymbol,
  requiresTender,
  onSetCustomer,
  onRequestAddCustomer,
  customerPhone,
  setCustomerPhone,
  customerAddress,
  setCustomerAddress,
  saveCustomerDetails,
  onSetPaymentMethod,
  onSetFullPayment,
  onSetAmountTendered,
  cashTenderedInput,
  setCashTenderedInput,
  resolvedTenderedRef,
  cashInputFocusedRef,
  emptyProductInputRef,
  onSetDiscount,
  notes = '',
  onNotesChange,
  primaryAction,
}) {
  const PrimaryIcon = primaryAction?.icon;

  return (
    <div className="w-[350px] md:w-[370px] bg-surface border-l border-border-default flex flex-col overflow-hidden shrink-0">
      {/* Panel Header */}
      <div className="px-4 py-2.5 border-b border-border-default bg-surface-secondary/60 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2">
          <Receipt className="w-4 h-4 text-brand" />
          <h3 className="text-xs font-bold uppercase tracking-wider text-text-primary">
            Checkout &amp; Payment
          </h3>
        </div>
        {bills.length > 0 && (
          <span className="text-[11px] font-mono font-semibold px-2 py-0.5 rounded-md bg-surface border border-border-default text-text-secondary">
            Bill #{activeBillIndex + 1}
          </span>
        )}
      </div>

      {/* Unified Scrollable Sections */}
      <div className="flex-1 overflow-y-auto divide-y divide-border-default/70">
        {/* 1. Customer Selector */}
        <div className="px-4 py-2.5 space-y-1.5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-text-primary flex items-center gap-1.5">
              <User className="w-3.5 h-3.5 text-brand" /> Customer
            </span>
            <button
              type="button"
              onClick={onRequestAddCustomer}
              className="text-[11px] font-semibold text-brand hover:underline cursor-pointer"
            >
              {bill.customer ? 'Change' : '+ Add New'}
            </button>
          </div>

          {bill.customer ? (
            <div className="bg-surface-secondary/70 p-2 rounded-lg border border-border-default text-xs space-y-1.5">
              <div className="flex items-center justify-between gap-2">
                <div className="font-semibold text-text-primary min-w-0 truncate">
                  {bill.customer.name}
                </div>
                <button
                  type="button"
                  onClick={() => onSetCustomer(null)}
                  className="text-text-muted hover:text-danger cursor-pointer p-0.5 shrink-0"
                  title={strings.pos.editor.removeCustomer}
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>

              <div className="grid grid-cols-2 gap-1.5">
                <label className="block">
                  <span className="text-[10px] font-semibold uppercase tracking-wider text-text-muted">
                    {strings.common.labels.phone}
                  </span>
                  <input
                    type="tel"
                    value={customerPhone}
                    onChange={(e) => setCustomerPhone(e.target.value)}
                    onBlur={saveCustomerDetails}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') e.currentTarget.blur();
                    }}
                    placeholder={strings.pos.editor.phonePlaceholder}
                    className="mt-0.5 w-full bg-surface border border-border-default rounded-md px-2 py-1 text-[11px] font-mono text-text-primary placeholder:text-text-muted/70 focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-colors"
                  />
                </label>
                <label className="block">
                  <span className="text-[10px] font-semibold uppercase tracking-wider text-text-muted">
                    {strings.common.labels.address}
                  </span>
                  <input
                    type="text"
                    value={customerAddress}
                    onChange={(e) => setCustomerAddress(e.target.value)}
                    onBlur={saveCustomerDetails}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') e.currentTarget.blur();
                    }}
                    placeholder={strings.pos.editor.addressNotesPlaceholder}
                    className="mt-0.5 w-full bg-surface border border-border-default rounded-md px-2 py-1 text-[11px] text-text-primary placeholder:text-text-muted/70 focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-colors"
                  />
                </label>
              </div>
            </div>
          ) : (
            <Select
              value={bill.customer?.id || ''}
              onChange={(val) => {
                const c = customers.find((cust) => String(cust.id) === String(val));
                onSetCustomer(c || null);
              }}
              placeholder={strings.pos.editor.walkInCustomer}
              options={[
                { value: '', label: strings.pos.editor.walkInCustomer },
                ...customers.map((c) => ({
                  value: String(c.id),
                  label: `${c.name}${c.phone ? ` (${c.phone})` : ''}`,
                })),
              ]}
            />
          )}
        </div>

        {/* 2. Payment Method Selector */}
        <div className="px-4 py-2.5 space-y-1.5">
          <label className="text-xs font-semibold text-text-primary block">
            {strings.common.labels.paymentMethod}
          </label>
          <Select
            value={bill.paymentMethod}
            onChange={(val) => onSetPaymentMethod(val)}
            options={[
              { value: 'CASH', label: strings.pos.editor.paymentMethods.cash },
              { value: 'UPI', label: strings.pos.editor.paymentMethods.upi },
              { value: 'CARD', label: strings.pos.editor.paymentMethods.card },
              { value: 'GIFT_CARD', label: strings.pos.editor.paymentMethods.giftCard },
            ]}
            className="w-full"
          />
          {!requiresTender && (
            <div className="pt-1 flex items-center justify-between text-xs text-text-muted">
              <span>{strings.pos.editor.exactAmountPayable}</span>
              <span className="font-semibold text-text-primary financial-value">
                {formatCurrency(totals.totalCents ?? totals.total_cents)}
              </span>
            </div>
          )}
        </div>

        {/* 3. Amount Tendered & Dynamic Settlement Status (Cash & Gift Card) */}
        {requiresTender && (
          <div className="px-4 py-2.5 space-y-1.5 animate-fade-in">
            <div className="flex items-center justify-between text-xs gap-2">
              <label
                htmlFor={`amount-tendered-${bill.index}`}
                className="font-semibold text-text-primary block"
              >
                {strings.pos.editor.amountTendered}
              </label>
              <div className="flex bg-surface-secondary rounded-lg p-0.5 border border-border-default shrink-0">
                <button
                  type="button"
                  onClick={() => onSetFullPayment(true)}
                  className={`px-2.5 py-0.5 rounded text-[11px] font-bold cursor-pointer transition-colors ${
                    bill.isFullPayment !== false
                      ? 'bg-brand text-white shadow-2xs'
                      : 'text-text-secondary hover:text-text-primary'
                  }`}
                >
                  {strings.pos.editor.fullPayment}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    onSetFullPayment(false);
                    if (totals.totalCents > 0 && (bill.amountTenderedCents || 0) >= totals.totalCents) {
                      onSetAmountTendered(0);
                      setCashTenderedInput('');
                    }
                  }}
                  className={`px-2.5 py-0.5 rounded text-[11px] font-bold cursor-pointer transition-colors ${
                    bill.isFullPayment === false
                      ? 'bg-brand text-white shadow-2xs'
                      : 'text-text-secondary hover:text-text-primary'
                  }`}
                >
                  {strings.pos.editor.partialPayment}
                </button>
              </div>
            </div>

            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm font-semibold text-text-muted pointer-events-none">
                {currencySymbol}
              </span>
              <input
                ref={resolvedTenderedRef}
                id={`amount-tendered-${bill.index}`}
                type="text"
                inputMode="decimal"
                placeholder={strings.pos.editor.amountPlaceholder}
                value={cashTenderedInput}
                onFocus={() => {
                  if (cashInputFocusedRef) cashInputFocusedRef.current = true;
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    if (primaryAction?.onClick) {
                      primaryAction.onClick();
                    }
                  } else if (e.key === 'Escape') {
                    e.preventDefault();
                    emptyProductInputRef?.current?.focus();
                  }
                }}
                onChange={(e) => {
                  const rawValue = e.target.value;
                  if (rawValue !== '' && !/^\d*\.?\d*$/.test(rawValue)) return;

                  const inputCents = unitsToCents(rawValue);
                  const isFull = bill.isFullPayment !== false;
                  const total = totals.totalCents ?? totals.total_cents ?? 0;

                  if (!isFull && total > 0 && inputCents >= total) {
                    toast.warning(
                      strings.pos.toasts.partialPaymentLimit(formatCurrency(total)),
                      { title: strings.pos.toasts.partialPaymentLimitTitle }
                    );
                    return;
                  }

                  setCashTenderedInput(rawValue);
                  onSetAmountTendered(inputCents);
                }}
                onBlur={() => {
                  if (cashInputFocusedRef) cashInputFocusedRef.current = false;
                  const cents = bill.amountTenderedCents || 0;
                  setCashTenderedInput(cents > 0 ? (cents / 100).toFixed(2) : '');
                }}
                className="w-full pl-7 pr-3 py-1.5 bg-surface-secondary border border-border-strong rounded-lg text-base financial-value text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand"
              />
            </div>

            {/* Settlement Feedback */}
            {(totals.totalCents ?? totals.total_cents ?? 0) > 0 && (
              <div className="pt-1 space-y-0.5">
                {bill.isFullPayment !== false ? (
                  (bill.amountTenderedCents || 0) >= (totals.totalCents ?? totals.total_cents ?? 0) ? (
                    <div className="flex items-center justify-between text-xs font-semibold text-success">
                      <span>{strings.pos.editor.changeToReturn}</span>
                      <span className="financial-value">
                        {formatCurrency((bill.amountTenderedCents || 0) - (totals.totalCents ?? totals.total_cents ?? 0))}
                      </span>
                    </div>
                  ) : (
                    <div className="space-y-0.5">
                      <div className="flex items-center justify-between text-xs font-medium text-warning">
                        <span>{strings.pos.editor.balanceDue}</span>
                        <span className="financial-value">
                          {formatCurrency((totals.totalCents ?? totals.total_cents ?? 0) - (bill.amountTenderedCents || 0))}
                        </span>
                      </div>
                      <p className="text-[10px] text-text-muted">
                        {strings.pos.editor.tenderAtLeast(formatCurrency(totals.totalCents ?? totals.total_cents ?? 0))}
                      </p>
                    </div>
                  )
                ) : (
                  <div className="space-y-0.5">
                    <div className="flex items-center justify-between text-xs font-medium text-warning">
                      <span>{strings.pos.editor.remainingBalanceDue}</span>
                      <span className="financial-value">
                        {formatCurrency((totals.totalCents ?? totals.total_cents ?? 0) - (bill.amountTenderedCents || 0))}
                      </span>
                    </div>
                    <p className="text-[10px] text-text-muted">
                      {strings.pos.editor.partialPaymentHint(formatCurrency(totals.totalCents ?? totals.total_cents ?? 0))}
                    </p>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* 4. Bill-Level Discount */}
        <div className="px-4 py-2.5 space-y-1.5">
          <div className="flex items-center justify-between text-xs">
            <span className="font-semibold text-text-primary">{strings.pos.editor.billDiscount}</span>
            <div className="flex bg-surface-secondary rounded-lg p-0.5 border border-border-default">
              <button
                type="button"
                onClick={() => onSetDiscount('FIXED', bill.discountValue)}
                className={`px-2 py-0.5 rounded text-[11px] font-bold cursor-pointer transition-colors ${
                  bill.discountType === 'FIXED'
                    ? 'bg-brand text-white shadow-2xs'
                    : 'text-text-secondary hover:text-text-primary'
                }`}
              >
                {strings.pos.editor.flatDiscount(currencySymbol)}
              </button>
              <button
                type="button"
                onClick={() => onSetDiscount('PERCENT', bill.discountValue)}
                className={`px-2 py-0.5 rounded text-[11px] font-bold cursor-pointer transition-colors ${
                  bill.discountType === 'PERCENT'
                    ? 'bg-brand text-white shadow-2xs'
                    : 'text-text-secondary hover:text-text-primary'
                }`}
              >
                %
              </button>
            </div>
          </div>
          <input
            type="number"
            min="0"
            step="0.01"
            placeholder={strings.pos.editor.amountPlaceholder}
            value={bill.discountValue || ''}
            onChange={(e) => onSetDiscount(bill.discountType, e.target.value)}
            className="w-full bg-surface-secondary border border-border-strong rounded-lg px-3 py-1.5 text-xs financial-value text-text-primary focus:outline-none focus:border-brand"
          />
        </div>

        {/* 5. Bill Notes */}
        <div className="px-4 py-2.5 space-y-1.5">
          <label className="text-xs font-semibold text-text-primary flex items-center gap-1.5">
            <StickyNote className="w-3.5 h-3.5 text-brand" /> {strings.common.labels.notes}
          </label>
          <textarea
            rows={2}
            value={notes}
            onChange={(e) => onNotesChange?.(e.target.value)}
            placeholder={strings.pos.editor.notesPlaceholder}
            className="w-full bg-surface-secondary border border-border-strong rounded-lg px-2.5 py-1.5 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand resize-none"
          />
        </div>

        {/* 6. Open Bills Selector */}
        {bills.length > 0 && onSwitchBill && (
          <div className="px-4 py-2.5 space-y-1.5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-text-primary">{strings.pos.editor.bills}</span>
              {onClearCart && (
                <button
                  type="button"
                  onClick={onClearCart}
                  disabled={bill.items.length === 0}
                  className="text-[11px] text-text-secondary hover:text-danger px-2 py-0.5 rounded bg-surface-secondary hover:bg-danger-bg border border-border-default transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  {strings.pos.editor.clearCart}
                </button>
              )}
            </div>
            <div className="grid grid-cols-9 gap-1">
              {bills.map((b, idx) => {
                const isActive = idx === activeBillIndex;
                const hasItems = b.items.length > 0;
                return (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => onSwitchBill(idx)}
                    aria-current={isActive ? 'true' : undefined}
                    className={`h-7 rounded-md text-xs font-bold border transition-colors cursor-pointer ${
                      isActive
                        ? 'bg-brand text-white border-brand shadow-xs'
                        : hasItems
                          ? 'bg-warning-bg text-warning border-warning/40 hover:border-warning/70'
                          : 'bg-surface-secondary text-text-secondary border-border-default hover:text-text-primary hover:border-border-strong'
                    }`}
                  >
                    {idx + 1}
                  </button>
                );
              })}
            </div>
          </div>
        )}
      </div>

      {/* 8. Complete Sale Persistent Button */}
      <div className="px-4 py-3 border-t border-border-default bg-surface shrink-0">
        {primaryAction && (
          <button
            type="button"
            onClick={primaryAction.onClick}
            disabled={primaryAction.disabled}
            className="w-full py-2.5 rounded-xl bg-brand hover:bg-brand-hover text-white font-bold text-sm shadow-xs transition-colors disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2 cursor-pointer"
          >
            {primaryAction.pending ? (
              <span>{primaryAction.pendingLabel || 'Processing...'}</span>
            ) : (
              <>
                {PrimaryIcon && <PrimaryIcon className="w-4 h-4" />}
                <span>{primaryAction.label}</span>
              </>
            )}
          </button>
        )}
      </div>
    </div>
  );
}
