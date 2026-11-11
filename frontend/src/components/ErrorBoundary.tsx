import { AlertOctagon, RotateCcw } from 'lucide-react';
import { Component, type ErrorInfo, type ReactNode } from 'react';

interface State { error: Error | null }

/** Catches rendering errors of a page so one broken view never blanks the whole console. */
export class ErrorBoundary extends Component<{ children: ReactNode; resetKey?: string }, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('UI error boundary caught', error, info.componentStack);
  }

  componentDidUpdate(prev: { resetKey?: string }) {
    if (prev.resetKey !== this.props.resetKey && this.state.error) this.setState({ error: null });
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="panel mx-auto mt-10 max-w-lg p-6 text-center" role="alert">
        <AlertOctagon className="mx-auto h-8 w-8 text-rose-300" />
        <h2 className="mt-3 text-base font-semibold text-fg">This view failed to render</h2>
        <p className="mt-1 text-sm break-words text-fg-muted">{this.state.error.message}</p>
        <button onClick={() => this.setState({ error: null })}
          className="mt-4 inline-flex items-center gap-1.5 rounded-lg border border-white/10 bg-white/[0.06] px-3 py-1.5 text-sm text-slate-200 hover:bg-white/10">
          <RotateCcw className="h-4 w-4" /> Try again
        </button>
      </div>
    );
  }
}
