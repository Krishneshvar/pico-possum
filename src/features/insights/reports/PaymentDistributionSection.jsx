import { Banknote, QrCode, CreditCard, Gift } from 'lucide-react';
import { formatPaise } from '../../../lib/formatters';
import { strings } from '../../../lib/strings';

export default function PaymentDistributionSection({
  totalRev = 0,
  cashRev = 0,
  upiRev = 0,
  cardRev = 0,
  giftCardRev = 0,
  cashPct = 0,
  upiPct = 0,
  cardPct = 0,
  giftCardPct = 0,
}) {
  return (
    <div className="p-5 rounded-xl bg-surface border border-border space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-sm font-semibold text-content">{strings.reports.payments.title}</h2>
          <p className="text-xs text-content-secondary">{strings.reports.payments.subtitle}</p>
        </div>
        <span className="text-xs font-semibold text-content financial-value">
          {strings.reports.payments.total(formatPaise(totalRev))}
        </span>
      </div>

      {/* Visual Stacked Distribution Bar */}
      {totalRev > 0 && (
        <div className="space-y-2">
          <div className="w-full h-3 bg-surface-secondary rounded-full overflow-hidden flex border border-border">
            {cashPct > 0 && (
              <div
                style={{ width: `${cashPct}%`, backgroundColor: '#2563EB' }}
                className="h-full transition-all"
                title={strings.reports.payments.barTooltip(strings.reports.payments.cash, formatPaise(cashRev), cashPct.toFixed(1))}
              />
            )}
            {upiPct > 0 && (
              <div
                style={{ width: `${upiPct}%`, backgroundColor: '#0F766E' }}
                className="h-full transition-all"
                title={strings.reports.payments.barTooltip(strings.reports.payments.upiQr, formatPaise(upiRev), upiPct.toFixed(1))}
              />
            )}
            {cardPct > 0 && (
              <div
                style={{ width: `${cardPct}%`, backgroundColor: '#7C3AED' }}
                className="h-full transition-all"
                title={strings.reports.payments.barTooltip(strings.reports.payments.card, formatPaise(cardRev), cardPct.toFixed(1))}
              />
            )}
            {giftCardPct > 0 && (
              <div
                style={{ width: `${giftCardPct}%`, backgroundColor: '#D97706' }}
                className="h-full transition-all"
                title={strings.reports.payments.barTooltip(strings.reports.payments.giftCard, formatPaise(giftCardRev), giftCardPct.toFixed(1))}
              />
            )}
          </div>

          {/* Legend with BOTH absolute amounts and % */}
          <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5 text-xs text-content-secondary pt-1">
            <span className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: '#2563EB' }} />
              <span className="font-medium text-content">{strings.reports.payments.cash}:</span>
              <span className="financial-value">{formatPaise(cashRev)}</span>
              <span className="text-content-muted">({cashPct.toFixed(1)}%)</span>
            </span>
            <span className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: '#0F766E' }} />
              <span className="font-medium text-content">{strings.reports.payments.upiQr}:</span>
              <span className="financial-value">{formatPaise(upiRev)}</span>
              <span className="text-content-muted">({upiPct.toFixed(1)}%)</span>
            </span>
            <span className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: '#7C3AED' }} />
              <span className="font-medium text-content">{strings.reports.payments.card}:</span>
              <span className="financial-value">{formatPaise(cardRev)}</span>
              <span className="text-content-muted">({cardPct.toFixed(1)}%)</span>
            </span>
            {giftCardPct > 0 && (
              <span className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: '#D97706' }} />
                <span className="font-medium text-content">{strings.reports.payments.giftCard}:</span>
                <span className="financial-value">{formatPaise(giftCardRev)}</span>
                <span className="text-content-muted">({giftCardPct.toFixed(1)}%)</span>
              </span>
            )}
          </div>
        </div>
      )}

      {/* Individual Method Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 pt-2">
        <div className="bg-surface-secondary p-3.5 rounded-lg border border-border space-y-1">
          <div className="flex items-center gap-2 text-xs font-medium text-content-secondary">
            <Banknote className="w-4 h-4 text-brand" /> Cash
          </div>
          <div className="text-lg font-semibold text-content financial-value">
            {formatPaise(cashRev)}
          </div>
          <div className="text-[11px] text-content-muted">
            {cashPct.toFixed(1)}% of total revenue
          </div>
        </div>

        <div className="bg-surface-secondary p-3.5 rounded-lg border border-border space-y-1">
          <div className="flex items-center gap-2 text-xs font-medium text-content-secondary">
            <QrCode className="w-4 h-4 text-teal-600" /> UPI / QR
          </div>
          <div className="text-lg font-semibold text-content financial-value">
            {formatPaise(upiRev)}
          </div>
          <div className="text-[11px] text-content-muted">
            {upiPct.toFixed(1)}% of total revenue
          </div>
        </div>

        <div className="bg-surface-secondary p-3.5 rounded-lg border border-border space-y-1">
          <div className="flex items-center gap-2 text-xs font-medium text-content-secondary">
            <CreditCard className="w-4 h-4 text-purple-600" /> Card
          </div>
          <div className="text-lg font-semibold text-content financial-value">
            {formatPaise(cardRev)}
          </div>
          <div className="text-[11px] text-content-muted">
            {cardPct.toFixed(1)}% of total revenue
          </div>
        </div>

        <div className="bg-surface-secondary p-3.5 rounded-lg border border-border space-y-1">
          <div className="flex items-center gap-2 text-xs font-medium text-content-secondary">
            <Gift className="w-4 h-4 text-amber-600" /> Gift Card
          </div>
          <div className="text-lg font-semibold text-content financial-value">
            {formatPaise(giftCardRev)}
          </div>
          <div className="text-[11px] text-content-muted">
            {giftCardPct.toFixed(1)}% of total revenue
          </div>
        </div>
      </div>
    </div>
  );
}
