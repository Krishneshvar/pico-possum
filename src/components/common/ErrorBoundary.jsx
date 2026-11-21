import React from 'react';
import { AlertCircle, RotateCcw, X, Home } from 'lucide-react';
import { logger } from '../../lib/logger';

export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
      showStack: false,
    };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    logger.error('ErrorBoundary', 'Caught view runtime exception:', error, errorInfo);
    this.setState({ errorInfo });
  }

  componentDidUpdate(prevProps) {
    // Reset error state if the user navigates or switches tabs
    if (this.props.tabId !== prevProps.tabId || this.props.viewId !== prevProps.viewId) {
      if (this.state.hasError) {
        this.setState({ hasError: false, error: null, errorInfo: null, showStack: false });
      }
    }
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null, errorInfo: null, showStack: false });
  };

  render() {
    if (this.state.hasError) {
      const { tabTitle, onCloseTab, isRootLevel } = this.props;
      const { error, errorInfo, showStack } = this.state;
      const errorMessage = error?.message || String(error) || 'An unexpected error occurred';

      if (isRootLevel) {
        return (
          <div className="flex h-screen w-screen flex-col items-center justify-center bg-canvas p-6 text-text-primary select-none">
            <div className="w-full max-w-md rounded-2xl border border-danger/30 bg-surface p-6 shadow-xl space-y-4">
              <div className="flex items-center gap-3 text-danger">
                <div className="p-2.5 rounded-xl bg-danger-bg">
                  <AlertCircle className="w-6 h-6" />
                </div>
                <div>
                  <h2 className="text-base font-bold">Application Error</h2>
                  <p className="text-xs text-text-muted">An error interrupted the terminal interface</p>
                </div>
              </div>

              <div className="p-3 rounded-lg bg-surface-secondary border border-border-default font-mono text-xs text-danger break-words">
                {errorMessage}
              </div>

              {errorInfo?.componentStack && (
                <div className="space-y-1">
                  <button
                    type="button"
                    onClick={() => this.setState((s) => ({ showStack: !s.showStack }))}
                    className="text-[11px] font-semibold text-text-muted hover:text-text-primary transition-colors cursor-pointer"
                  >
                    {showStack ? 'Hide technical details' : 'Show technical details'}
                  </button>
                  {showStack && (
                    <pre className="p-2.5 rounded-lg bg-black/40 text-[10px] font-mono text-text-secondary overflow-auto max-h-40 whitespace-pre-wrap">
                      {errorInfo.componentStack}
                    </pre>
                  )}
                </div>
              )}

              <div className="flex items-center gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => window.location.reload()}
                  className="flex-1 py-2 px-3 rounded-lg bg-brand hover:bg-brand-hover text-white text-xs font-bold flex items-center justify-center gap-1.5 shadow-xs transition-colors cursor-pointer"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  Reload Terminal
                </button>
              </div>
            </div>
          </div>
        );
      }

      // Tab-level fallback
      return (
        <div className="flex-1 flex flex-col items-center justify-center bg-canvas p-6 text-text-primary select-none">
          <div className="w-full max-w-md rounded-2xl border border-danger/30 bg-surface p-6 shadow-md space-y-4">
            <div className="flex items-center gap-3 text-danger">
              <div className="p-2.5 rounded-xl bg-danger-bg">
                <AlertCircle className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm font-bold">
                  {tabTitle ? `Unable to display "${tabTitle}"` : 'Failed to display workspace view'}
                </h3>
                <p className="text-xs text-text-muted">
                  A runtime error occurred inside this workspace tab.
                </p>
              </div>
            </div>

            <div className="p-3 rounded-lg bg-surface-secondary border border-border-default font-mono text-xs text-danger break-words">
              {errorMessage}
            </div>

            {errorInfo?.componentStack && (
              <div className="space-y-1">
                <button
                  type="button"
                  onClick={() => this.setState((s) => ({ showStack: !s.showStack }))}
                  className="text-[11px] font-semibold text-text-muted hover:text-text-primary transition-colors cursor-pointer"
                >
                  {showStack ? 'Hide technical details' : 'Show technical details'}
                </button>
                {showStack && (
                  <pre className="p-2.5 rounded-lg bg-black/40 text-[10px] font-mono text-text-secondary overflow-auto max-h-36 whitespace-pre-wrap">
                    {errorInfo.componentStack}
                  </pre>
                )}
              </div>
            )}

            <div className="flex items-center gap-2 pt-1">
              <button
                type="button"
                onClick={this.handleReset}
                className="flex-1 py-1.5 px-3 rounded-lg bg-brand hover:bg-brand-hover text-white text-xs font-bold flex items-center justify-center gap-1.5 shadow-xs transition-colors cursor-pointer"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                Try Reloading Tab
              </button>
              {onCloseTab && (
                <button
                  type="button"
                  onClick={onCloseTab}
                  className="py-1.5 px-3 rounded-lg bg-surface-secondary hover:bg-surface-tertiary border border-border-default text-text-secondary text-xs font-semibold flex items-center gap-1 transition-colors cursor-pointer"
                >
                  <X className="w-3.5 h-3.5" />
                  Close Tab
                </button>
              )}
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
