import React, { useState, useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Lock,
  User,
  AtSign,
  Eye,
  EyeOff,
  ShieldCheck,
  Sparkles,
  AlertCircle,
  CheckCircle2,
  ArrowRight,
  RefreshCw,
} from 'lucide-react';
import { tauriApi } from '../../lib/tauri';

export default function AuthScreen({ authState, onAuthenticated }) {
  const queryClient = useQueryClient();
  const requiresSetup = authState?.requires_setup ?? false;

  // Form states
  const [name, setName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

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
  }, [adminUsername]);

  // Login mutation
  const loginMutation = useMutation({
    mutationFn: (credentials) => tauriApi.login(credentials),
    onSuccess: (res) => {
      setErrorMsg('');
      queryClient.setQueryData(['auth_state'], res);
      if (onAuthenticated) onAuthenticated(res);
    },
    onError: (err) => {
      const msg = typeof err === 'string' ? err : err?.message || 'Invalid username or password';
      setErrorMsg(msg.replace(/^Unauthorized:\s*|^Validation error:\s*|^Forbidden:\s*/, ''));
    },
  });

  // Register mutation
  const registerMutation = useMutation({
    mutationFn: (payload) => tauriApi.registerAdmin(payload),
    onSuccess: (res) => {
      setErrorMsg('');
      queryClient.setQueryData(['auth_state'], res);
      if (onAuthenticated) onAuthenticated(res);
    },
    onError: (err) => {
      const msg = typeof err === 'string' ? err : err?.message || 'Failed to complete registration';
      setErrorMsg(msg.replace(/^Validation error:\s*|^Forbidden:\s*/, ''));
    },
  });

  const handleSubmit = (e) => {
    e.preventDefault();
    setErrorMsg('');

    if (requiresSetup) {
      if (!name.trim()) {
        setErrorMsg('Please enter your full name');
        return;
      }
      if (!username.trim()) {
        setErrorMsg('Please choose an administrator username');
        return;
      }
      if (!password) {
        setErrorMsg('Please enter a secure password');
        return;
      }
      if (password.length < 4) {
        setErrorMsg('Password must be at least 4 characters long');
        return;
      }
      if (password !== confirmPassword) {
        setErrorMsg('Passwords do not match');
        return;
      }

      registerMutation.mutate({
        name: name.trim(),
        username: username.trim(),
        password,
      });
    } else {
      if (!username.trim() || !password) {
        setErrorMsg('Please enter both username and password');
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950 p-4 select-none">
      {/* Background ambient decorative glow */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-1/4 left-1/3 w-80 h-80 bg-sky-500/10 rounded-full blur-3xl pointer-events-none" />

      {/* Auth Card Container */}
      <div className="w-full max-w-md bg-slate-900/90 border border-slate-800 rounded-3xl p-8 shadow-2xl backdrop-blur-xl relative z-10 transition-all animate-fade-in">
        {/* Brand Header */}
        <div className="flex flex-col items-center text-center mb-7">
          <div className="w-16 h-16 rounded-2xl bg-gradient-to-tr from-emerald-500/20 to-sky-500/20 border border-emerald-500/30 p-2.5 mb-4 shadow-lg shadow-emerald-500/10 flex items-center justify-center">
            <img
              src="/src/assets/icon-shell.png"
              alt="Pico POS"
              className="w-full h-full object-contain"
            />
          </div>

          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-slate-800/80 border border-slate-700/60 text-[11px] font-semibold text-emerald-400 mb-2">
            {requiresSetup ? (
              <>
                <Sparkles className="w-3.5 h-3.5" />
                <span>First-Time Setup</span>
              </>
            ) : (
              <>
                <ShieldCheck className="w-3.5 h-3.5" />
                <span>Protected Terminal</span>
              </>
            )}
          </div>

          <h1 className="text-xl font-extrabold text-slate-100 tracking-tight">
            {requiresSetup ? 'System Administrator Setup' : 'Sign In to Pico POS'}
          </h1>
          <p className="text-xs text-slate-400 mt-1 max-w-xs">
            {requiresSetup
              ? "Create your master administrator account to secure your store's database and data."
              : 'Enter your credentials to unlock terminal sales and operations.'}
          </p>
        </div>

        {/* Error Notification */}
        {errorMsg && (
          <div className="mb-5 p-3.5 rounded-xl bg-rose-500/15 border border-rose-500/30 text-rose-300 text-xs flex items-center gap-2.5 animate-shake">
            <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
            <span className="flex-1 font-medium">{errorMsg}</span>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          {requiresSetup && (
            <div>
              <label className="block text-slate-300 text-xs font-semibold mb-1.5">
                Administrator Name
              </label>
              <div className="relative">
                <User className="w-4 h-4 text-slate-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. John Doe / Store Manager"
                  disabled={isPending}
                  required
                  autoFocus
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-4 py-2.5 text-xs text-slate-100 placeholder-slate-600 focus:outline-none focus:border-emerald-500 transition-colors"
                />
              </div>
            </div>
          )}

          <div>
            <label className="block text-slate-300 text-xs font-semibold mb-1.5">
              Username
            </label>
            <div className="relative">
              <AtSign className="w-4 h-4 text-slate-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="admin"
                disabled={isPending}
                required
                autoFocus={!requiresSetup}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-4 py-2.5 text-xs text-slate-100 placeholder-slate-600 focus:outline-none focus:border-emerald-500 transition-colors"
              />
            </div>
          </div>

          <div>
            <label className="block text-slate-300 text-xs font-semibold mb-1.5">
              Password
            </label>
            <div className="relative">
              <Lock className="w-4 h-4 text-slate-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                disabled={isPending}
                required
                className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-10 py-2.5 text-xs text-slate-100 placeholder-slate-600 focus:outline-none focus:border-emerald-500 transition-colors"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors cursor-pointer"
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>

            {requiresSetup && password && (
              <div className="mt-2 space-y-1">
                <div className="flex gap-1 h-1 w-full bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className={`h-full transition-all duration-300 ${
                      strength <= 1
                        ? 'w-1/4 bg-rose-500'
                        : strength === 2
                        ? 'w-2/4 bg-amber-500'
                        : strength === 3
                        ? 'w-3/4 bg-sky-500'
                        : 'w-full bg-emerald-500'
                    }`}
                  />
                </div>
                <div className="flex justify-between text-[10px] text-slate-400">
                  <span>Password strength</span>
                  <span className="font-semibold text-slate-300">
                    {strength <= 1
                      ? 'Weak'
                      : strength === 2
                      ? 'Moderate'
                      : strength === 3
                      ? 'Good'
                      : 'Strong'}
                  </span>
                </div>
              </div>
            )}
          </div>

          {requiresSetup && (
            <div>
              <label className="block text-slate-300 text-xs font-semibold mb-1.5">
                Confirm Password
              </label>
              <div className="relative">
                <Lock className="w-4 h-4 text-slate-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="••••••••"
                  disabled={isPending}
                  required
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-10 py-2.5 text-xs text-slate-100 placeholder-slate-600 focus:outline-none focus:border-emerald-500 transition-colors"
                />
                {confirmPassword && password === confirmPassword && (
                  <CheckCircle2 className="w-4 h-4 text-emerald-400 absolute right-3.5 top-1/2 -translate-y-1/2" />
                )}
              </div>
            </div>
          )}

          <button
            type="submit"
            disabled={isPending}
            className="w-full mt-2 py-3 rounded-xl bg-emerald-500 hover:bg-emerald-400 active:bg-emerald-600 text-slate-950 font-bold text-xs flex items-center justify-center gap-2 shadow-lg shadow-emerald-500/20 disabled:opacity-50 transition-all cursor-pointer"
          >
            {isPending ? (
              <>
                <RefreshCw className="w-4 h-4 animate-spin" />
                <span>{requiresSetup ? 'Setting up account...' : 'Authenticating...'}</span>
              </>
            ) : (
              <>
                <span>{requiresSetup ? 'Create Account & Open Terminal' : 'Sign In'}</span>
                <ArrowRight className="w-4 h-4" />
              </>
            )}
          </button>
        </form>


      </div>
    </div>
  );
}
