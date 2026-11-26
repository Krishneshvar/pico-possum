import { Sun, Moon, Check, Monitor, RotateCcw } from 'lucide-react';
import {
  useThemeStore,
  MIN_UI_SCALE,
  MAX_UI_SCALE,
  DEFAULT_UI_SCALE,
  UI_SCALE_STEP,
} from '../../../stores/theme.store';
import { strings } from '../../../lib/strings';

const SCALE_PRESETS = [0.75, 0.9, 1.0, 1.15, 1.3, 1.5];

export default function AppearanceTab() {
  const { theme, setTheme, uiScale, setUiScale, resetUiScale } = useThemeStore();
  const scalePercent = Math.round(uiScale * 100);
  const isDefaultScale = Math.abs(uiScale - DEFAULT_UI_SCALE) < 0.001;

  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <h2 className="text-base font-semibold text-content">{strings.settings.appearance.title}</h2>
        <p className="text-xs text-content-secondary">
          {strings.settings.appearance.subtitle}
        </p>
      </div>

      <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
        <h3 className="text-sm font-semibold text-content">{strings.settings.appearance.colorTheme}</h3>
        <p className="text-xs text-content-secondary">
          {strings.settings.appearance.colorThemeHint}
        </p>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
          {/* Light Operational Card */}
          <div
            onClick={() => setTheme('light')}
            className={`p-4 rounded-xl border-2 cursor-pointer transition-all space-y-3 ${
              theme === 'light'
                ? 'border-brand bg-brand-subtle'
                : 'border-border bg-surface-secondary hover:border-border-strong'
            }`}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 font-medium text-xs text-content">
                <Sun className="w-4 h-4 text-warning" />
                <span>{strings.settings.appearance.lightTitle}</span>
              </div>
              {theme === 'light' && (
                <div className="w-5 h-5 rounded-full bg-brand text-white flex items-center justify-center">
                  <Check className="w-3 h-3" />
                </div>
              )}
            </div>
            {/* Visual Color Palette Preview */}
            <div className="flex gap-1.5 h-6 rounded overflow-hidden border border-border">
              <div className="flex-1 bg-[#F4F6F9]" title="#F4F6F9 App Canvas" />
              <div className="flex-1 bg-[#FFFFFF]" title="#FFFFFF Primary Surface" />
              <div className="flex-1 bg-[#1D4ED8]" title="#1D4ED8 Brand Blue" />
              <div className="flex-1 bg-[#17212F]" title="#17212F Content" />
            </div>
            <p className="text-[11px] text-content-secondary">
              {strings.settings.appearance.lightDescription}
            </p>
          </div>

          {/* Deep Navy Dark Card */}
          <div
            onClick={() => setTheme('dark')}
            className={`p-4 rounded-xl border-2 cursor-pointer transition-all space-y-3 ${
              theme === 'dark'
                ? 'border-brand bg-brand-subtle'
                : 'border-border bg-surface-secondary hover:border-border-strong'
            }`}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 font-medium text-xs text-content">
                <Moon className="w-4 h-4 text-brand" />
                <span>{strings.settings.appearance.darkTitle}</span>
              </div>
              {theme === 'dark' && (
                <div className="w-5 h-5 rounded-full bg-brand text-white flex items-center justify-center">
                  <Check className="w-3 h-3" />
                </div>
              )}
            </div>
            {/* Visual Color Palette Preview */}
            <div className="flex gap-1.5 h-6 rounded overflow-hidden border border-border">
              <div className="flex-1 bg-[#0B1220]" title="#0B1220 Deep Navy Canvas" />
              <div className="flex-1 bg-[#111C2E]" title="#111C2E Primary Surface" />
              <div className="flex-1 bg-[#2457C5]" title="#2457C5 Brand Action" />
              <div className="flex-1 bg-[#E5EDF7]" title="#E5EDF7 Content" />
            </div>
            <p className="text-[11px] text-content-secondary">
              {strings.settings.appearance.darkDescription}
            </p>
          </div>
        </div>
      </div>

      {/* Interface Scale Slider */}
      <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <Monitor className="w-4 h-4 text-brand" />
              <h3 className="text-sm font-semibold text-content">
                {strings.settings.appearance.uiScale}
              </h3>
              <span className="px-2 py-0.5 rounded-full text-[11px] font-semibold bg-brand-subtle text-brand border border-brand/20 tabular-nums">
                {scalePercent}%
              </span>
            </div>
            <p className="text-xs text-content-secondary">
              {strings.settings.appearance.uiScaleHint}
            </p>
          </div>

          <button
            type="button"
            onClick={resetUiScale}
            disabled={isDefaultScale}
            className="px-2.5 py-1.5 rounded-lg text-xs font-medium border border-border bg-surface-secondary text-content-secondary hover:text-content hover:border-border-strong disabled:opacity-40 disabled:pointer-events-none flex items-center gap-1.5 shrink-0 transition-colors cursor-pointer"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>{strings.settings.appearance.uiScaleReset}</span>
          </button>
        </div>

        <div className="pt-2 space-y-3">
          <div className="flex items-center gap-3">
            <span className="text-[11px] font-medium text-content-muted tabular-nums w-9">
              {Math.round(MIN_UI_SCALE * 100)}%
            </span>
            <input
              type="range"
              aria-label={strings.settings.appearance.uiScale}
              min={MIN_UI_SCALE}
              max={MAX_UI_SCALE}
              step={UI_SCALE_STEP}
              value={uiScale}
              onChange={(e) => setUiScale(parseFloat(e.target.value))}
              className="flex-1 h-2 bg-surface-secondary rounded-lg appearance-none cursor-pointer accent-brand border border-border"
            />
            <span className="text-[11px] font-medium text-content-muted tabular-nums w-10 text-right">
              {Math.round(MAX_UI_SCALE * 100)}%
            </span>
          </div>

          {/* Quick-select presets */}
          <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
            <div className="flex flex-wrap items-center gap-1.5">
              {SCALE_PRESETS.map((preset) => {
                const pct = Math.round(preset * 100);
                const isActive = Math.abs(uiScale - preset) < 0.001;
                const isDef = Math.abs(preset - DEFAULT_UI_SCALE) < 0.001;
                return (
                  <button
                    key={preset}
                    type="button"
                    onClick={() => setUiScale(preset)}
                    className={`px-2.5 py-1 rounded-md text-[11px] font-medium border transition-colors cursor-pointer tabular-nums ${
                      isActive
                        ? 'bg-brand text-white border-brand shadow-xs'
                        : 'bg-surface-secondary text-content-secondary border-border hover:text-content hover:border-border-strong'
                    }`}
                  >
                    {pct}%{isDef ? ` (${strings.settings.appearance.uiScaleDefault})` : ''}
                  </button>
                );
              })}
            </div>
            <span className="text-[11px] text-content-muted">
              {strings.settings.appearance.uiScaleDeviceNote}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
