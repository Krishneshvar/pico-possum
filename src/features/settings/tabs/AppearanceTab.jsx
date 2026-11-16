import { Sun, Moon, Check } from 'lucide-react';
import { useThemeStore } from '../../../stores/theme.store';
import { strings } from '../../../lib/strings';

export default function AppearanceTab() {
  const { theme, setTheme } = useThemeStore();

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
    </div>
  );
}
