import { RefreshCw } from 'lucide-react';
import { strings } from '../../lib/strings';

export default function RefreshButton({
  onClick,
  isLoading = false,
  disabled = false,
  className = '',
  label = strings.common.actions.refresh,
  title = strings.common.actions.refresh,
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled || isLoading}
      className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-border-default bg-surface hover:bg-surface-secondary text-text-secondary hover:text-text-primary transition-colors shadow-2xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed ${className}`}
      title={title}
    >
      <RefreshCw className={`w-3.5 h-3.5 text-brand ${isLoading ? 'animate-spin' : ''}`} />
      <span>{label}</span>
    </button>
  );
}
