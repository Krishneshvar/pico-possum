import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Lock, RefreshCw, Check } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { strings } from '../../../lib/strings';
import { validatePasswordChange } from '../../../lib/validation';

export default function SecurityTab() {
  const [passwordState, setPasswordState] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });
  const [errors, setErrors] = useState({});

  const updatePasswordMutation = useMutation({
    mutationFn: ({ currentPassword, newPassword }) =>
      tauriApi.updatePassword({ currentPassword, newPassword }),
    onSuccess: (msg) => {
      setPasswordState({ currentPassword: '', newPassword: '', confirmPassword: '' });
      setErrors({});
      toast.success(msg || strings.settings.security.updated);
    },
    onError: (err) => {
      const msg = typeof err === 'string' ? err : err?.message || strings.settings.security.updateFailed;
      toast.error(msg.replace(/^Unauthorized:\s*|^Validation error:\s*|^Forbidden:\s*/, ''), {
        title: strings.settings.toasts.passwordUpdateFailedTitle,
      });
    },
  });

  const handleChange = (field, value) => {
    setPasswordState((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: undefined }));
    }
  };

  const handlePasswordSubmit = (e) => {
    e.preventDefault();
    const { currentPassword, newPassword, confirmPassword } = passwordState;
    const validation = validatePasswordChange(currentPassword, newPassword, confirmPassword);

    if (!validation.isValid) {
      setErrors(validation.errors);
      const firstError = Object.values(validation.errors)[0];
      if (firstError) toast.warning(firstError);
      return;
    }

    updatePasswordMutation.mutate({ currentPassword, newPassword });
  };

  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <h2 className="text-base font-semibold text-content">{strings.settings.security.title}</h2>
        <p className="text-xs text-content-secondary">
          {strings.settings.security.subtitle}
        </p>
      </div>

      <div className="p-6 rounded-xl bg-surface border border-border space-y-5">
        <div className="flex items-center gap-2.5 pb-3 border-b border-border">
          <div className="w-8 h-8 rounded-lg bg-brand-subtle text-brand flex items-center justify-center border border-brand/20">
            <Lock className="w-4 h-4" />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-content">{strings.settings.security.updateTitle}</h3>
            <p className="text-[11px] text-content-secondary">
              {strings.settings.security.updateHint}
            </p>
          </div>
        </div>

        <form onSubmit={handlePasswordSubmit} className="space-y-4 text-xs">
          <div>
            <label className="text-content font-medium block mb-1">
              {strings.settings.security.currentPassword}
            </label>
            <input
              type="password"
              value={passwordState.currentPassword}
              onChange={(e) => handleChange('currentPassword', e.target.value)}
              placeholder={strings.settings.security.currentPasswordPlaceholder}
              className={`w-full bg-surface-secondary border rounded-lg p-2.5 text-content focus:outline-none ${
                errors.currentPassword ? 'border-danger focus:border-danger' : 'border-border-strong focus:border-brand'
              }`}
            />
            {errors.currentPassword && <p className="text-[11px] text-danger mt-1">{errors.currentPassword}</p>}
          </div>

          <div>
            <label className="text-content font-medium block mb-1">
              {strings.settings.security.newPassword}
            </label>
            <input
              type="password"
              value={passwordState.newPassword}
              onChange={(e) => handleChange('newPassword', e.target.value)}
              placeholder={strings.settings.security.newPasswordPlaceholder}
              className={`w-full bg-surface-secondary border rounded-lg p-2.5 text-content focus:outline-none ${
                errors.newPassword ? 'border-danger focus:border-danger' : 'border-border-strong focus:border-brand'
              }`}
            />
            {errors.newPassword && <p className="text-[11px] text-danger mt-1">{errors.newPassword}</p>}
          </div>

          <div>
            <label className="text-content font-medium block mb-1">
              {strings.settings.security.confirmPassword}
            </label>
            <input
              type="password"
              value={passwordState.confirmPassword}
              onChange={(e) => handleChange('confirmPassword', e.target.value)}
              placeholder={strings.settings.security.confirmPasswordPlaceholder}
              className={`w-full bg-surface-secondary border rounded-lg p-2.5 text-content focus:outline-none ${
                errors.confirmPassword ? 'border-danger focus:border-danger' : 'border-border-strong focus:border-brand'
              }`}
            />
            {errors.confirmPassword && <p className="text-[11px] text-danger mt-1">{errors.confirmPassword}</p>}
          </div>

          <div className="pt-2 flex justify-end">
            <button
              type="submit"
              disabled={updatePasswordMutation.isPending}
              className="px-5 py-2.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-medium text-xs flex items-center gap-2 transition-colors cursor-pointer disabled:opacity-50"
            >
              {updatePasswordMutation.isPending ? (
                <RefreshCw className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <Check className="w-3.5 h-3.5" />
              )}
              <span>{strings.settings.security.updatePassword}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
