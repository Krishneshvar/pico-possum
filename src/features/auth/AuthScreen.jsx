import { useState, useEffect, useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Lock,
  User,
  AtSign,
  Eye,
  EyeOff,
  ShieldCheck,
  Sparkles,
  CheckCircle2,
  ArrowRight,
  RefreshCw,
  AlertCircle,
} from 'lucide-react';
import { tauriApi } from '../../lib/tauri';
import { toast } from '../../stores/toast.store';
import { strings } from '../../lib/strings';
import iconShell from '../../assets/icon-shell.png';

export default function AuthScreen({ authState, onAuthenticated }) {
  const queryClient = useQueryClient();
  const requiresSetup = authState?.requires_setup ?? false;
  const passwordInputRef = useRef(null);

  // Form states
  const [name, setName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  // Auto focus password
  useEffect(() => {
    const timer = window.setTimeout(() => {
      passwordInputRef.current?.focus();
    }, 50);
    return () => window.clearTimeout(timer);
  }, [requiresSetup]);

  // Fetch admin username if signing in
  const { data: adminUsername } = useQuery({
    queryKey: ['admin_username'],
    queryFn: () => tauriApi.getAdminUsername(),
    enabled: !requiresSetup,
  });

  useEffect(() => {
    if (adminUsername && !username) {
      setUsername(adminUsername);
    }
  }, [adminUsername, username]);

  // Login mutation
  const loginMutation = useMutation({
    mutationFn: (credentials) => tauriApi.login(credentials),
    onSuccess: (res) => {
      toast.success(
        res?.user?.name
          ? strings.auth.toast.welcomeBack(res.user.name)
          : strings.auth.toast.signedIn,
        { title: strings.auth.toast.signedInTitle }
      );
      queryClient.setQueryData(['auth_state'], res);
      if (onAuthenticated) onAuthenticated(res);
    },
    onError: (err) => {
      const msg =
        typeof err === 'string'
          ? err
          : err?.message || strings.auth.errors.invalidCredentials;
      const cleanMsg = msg.replace(/^Unauthorized:\s*|^Validation error:\s*|^Forbidden:\s*/, '');
      setErrorMessage(cleanMsg);
      toast.error(cleanMsg, { title: strings.auth.toast.signInFailedTitle });
    },
  });

  // Register mutation
  const registerMutation = useMutation({
    mutationFn: (payload) => tauriApi.registerAdmin(payload),
    onSuccess: (res) => {
      toast.success(strings.auth.toast.accountCreated, {
        title: strings.auth.toast.setupCompleteTitle,
      });
      queryClient.setQueryData(['auth_state'], res);
      if (onAuthenticated) onAuthenticated(res);
    },
    onError: (err) => {
      const msg =
        typeof err === 'string'
          ? err
          : err?.message || strings.auth.errors.registrationFailed;
      const cleanMsg = msg.replace(/^Validation error:\s*|^Forbidden:\s*/, '');
      setErrorMessage(cleanMsg);
      toast.error(cleanMsg, { title: strings.auth.toast.setupFailedTitle });
    },
  });

  const handleSubmit = (e) => {
    e.preventDefault();
    setErrorMessage('');

    if (requiresSetup) {
      if (!name.trim()) {
        setErrorMessage(strings.auth.errors.fullNameRequired);
        return;
      }
      if (!username.trim()) {
        setErrorMessage(strings.auth.errors.usernameRequired);
        return;
      }
      if (!password) {
        setErrorMessage(strings.auth.errors.passwordRequired);
        return;
      }
      if (password.length < 4) {
        setErrorMessage(strings.auth.errors.passwordTooShort);
        return;
      }
      if (password !== confirmPassword) {
        setErrorMessage(strings.auth.errors.passwordMismatch);
        return;
      }

      registerMutation.mutate({
        name: name.trim(),
        username: username.trim(),
        password,
      });
    } else {
      if (!username.trim() || !password) {
        setErrorMessage(strings.auth.errors.credentialsRequired);
        return;
      }

      loginMutation.mutate({
        username: username.trim(),
        password,
      });
    }
  };

  const isPending = loginMutation.isPending || registerMutation.isPending;

  // Password strength calculation
  const getPasswordStrength = () => {
    if (!password) return 0;
    let score = 0;
    if (password.length >= 4) score += 1;
    if (password.length >= 8) score += 1;
    if (/[A-Z]/.test(password) || /[0-9]/.test(password)) score += 1;
    if (/[^A-Za-z0-9]/.test(password)) score += 1;
    return score;
  };

  const strength = getPasswordStrength();

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-canvas p-4 select-none">
      {/* Auth Card Container - 420px max width, solid surfaces, no glow */}
      <div className="w-full max-w-[420px] bg-surface border border-border-default rounded-2xl p-7 shadow-lg relative z-10 animate-fade-in">
        {/* Brand Header */}
        <div className="flex flex-col items-center text-center mb-6">
          <div className="w-14 h-14 rounded-xl bg-surface-secondary border border-border-default p-2 mb-3 shadow-xs flex items-center justify-center">
            <img
              src={iconShell}
              alt={strings.layout.brand.logoAlt}
              className="w-full h-full object-contain"
            />
          </div>

          <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-brand-subtle text-brand text-[11px] font-semibold mb-2">
            {requiresSetup ? (
              <>
                <Sparkles className="w-3 h-3" />
                <span>{strings.auth.login.badges.setup}</span>
              </>
            ) : (
              <>
                <ShieldCheck className="w-3 h-3" />
                <span>{strings.auth.login.badges.protected}</span>
              </>
            )}
          </div>

          <h1 className="text-xl font-bold text-text-primary tracking-tight">
            {requiresSetup
              ? strings.auth.login.titles.setup
              : strings.auth.login.titles.signIn}
          </h1>
          <p className="text-xs text-text-secondary mt-1 max-w-xs leading-relaxed">
            {requiresSetup
              ? strings.auth.login.subtitles.setup
              : strings.auth.login.subtitles.signIn}
          </p>
        </div>

        {/* Inline Error Message */}
        {errorMessage && (
          <div className="mb-4 p-3 rounded-xl bg-danger-bg border border-danger/30 text-danger text-xs flex items-start gap-2 animate-fade-in">
            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
            <span className="font-medium">{errorMessage}</span>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-3.5">
          {requiresSetup && (
            <div>
              <label className="block text-text-secondary text-xs font-semibold mb-1">
                {strings.auth.fields.administratorName}
              </label>
              <div className="relative">
                <User className="w-4 h-4 text-text-muted absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder={strings.auth.placeholders.administratorName}
                  disabled={isPending}
                  required
                  className="w-full bg-surface-secondary border border-border-strong rounded-xl pl-9 pr-3.5 py-2 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-colors"
                />
              </div>
            </div>
          )}

          <div>
            <label className="block text-text-secondary text-xs font-semibold mb-1">
              {strings.auth.fields.username}
            </label>
            <div className="relative">
              <AtSign className="w-4 h-4 text-text-muted absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder={strings.auth.placeholders.username}
                disabled={isPending}
                required
                className="w-full bg-surface-secondary border border-border-strong rounded-xl pl-9 pr-3.5 py-2 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-colors"
              />
            </div>
          </div>

          <div>
            <label className="block text-text-secondary text-xs font-semibold mb-1">
              {strings.auth.fields.password}
            </label>
            <div className="relative">
              <Lock className="w-4 h-4 text-text-muted absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                ref={passwordInputRef}
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={strings.auth.placeholders.password}
                disabled={isPending}
                required
                className="w-full bg-surface-secondary border border-border-strong rounded-xl pl-9 pr-9 py-2 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-colors"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-text-muted hover:text-text-primary transition-colors cursor-pointer"
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>

            {requiresSetup && password && (
              <div className="mt-2 space-y-1">
                <div className="flex gap-1 h-1 w-full bg-surface-tertiary rounded-full overflow-hidden">
                  <div
                    className={`h-full transition-all duration-300 ${
                      strength <= 1
                        ? 'w-1/4 bg-danger'
                        : strength === 2
                        ? 'w-2/4 bg-warning'
                        : strength === 3
                        ? 'w-3/4 bg-info'
                        : 'w-full bg-success'
                    }`}
                  />
                </div>
                <div className="flex justify-between text-[10px] text-text-muted">
                  <span>{strings.auth.password.strengthLabel}</span>
                  <span className="font-semibold text-text-secondary">
                    {strength <= 1
                      ? strings.auth.password.levels.weak
                      : strength === 2
                      ? strings.auth.password.levels.moderate
                      : strength === 3
                      ? strings.auth.password.levels.good
                      : strings.auth.password.levels.strong}
                  </span>
                </div>
              </div>
            )}
          </div>

          {requiresSetup && (
            <div>
              <label className="block text-text-secondary text-xs font-semibold mb-1">
                {strings.auth.fields.confirmPassword}
              </label>
              <div className="relative">
                <Lock className="w-4 h-4 text-text-muted absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder={strings.auth.placeholders.password}
                  disabled={isPending}
                  required
                  className="w-full bg-surface-secondary border border-border-strong rounded-xl pl-9 pr-9 py-2 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-brand focus:ring-1 focus:ring-brand transition-colors"
                />
                {confirmPassword && password === confirmPassword && (
                  <CheckCircle2 className="w-4 h-4 text-success absolute right-3 top-1/2 -translate-y-1/2" />
                )}
              </div>
            </div>
          )}

          {/* Solid Brand-Blue Action Button */}
          <button
            type="submit"
            disabled={isPending}
            className="w-full mt-1 py-2.5 rounded-xl bg-brand hover:bg-brand-hover text-white font-semibold text-xs flex items-center justify-center gap-2 shadow-xs disabled:opacity-50 transition-colors cursor-pointer"
          >
            {isPending ? (
              <>
                <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                <span>
                  {requiresSetup
                    ? strings.auth.actions.settingUpAccount
                    : strings.auth.actions.authenticating}
                </span>
              </>
            ) : (
              <>
                <span>
                  {requiresSetup
                    ? strings.auth.actions.createAccount
                    : strings.auth.actions.signIn}
                </span>
                <ArrowRight className="w-3.5 h-3.5" />
              </>
            )}
          </button>
        </form>

        {/* Footer with App Version */}
        <div className="mt-6 pt-4 border-t border-border-default flex items-center justify-between text-[11px] text-text-muted">
          <span>{strings.layout.brand.name}</span>
          <span className="font-mono">{strings.auth.footer.version}</span>
        </div>
      </div>
    </div>
  );
}
