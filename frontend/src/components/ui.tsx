import { AlertTriangle, Inbox, Loader2, RefreshCw, X } from 'lucide-react';
import { useEffect, type ButtonHTMLAttributes, type ReactNode } from 'react';
import type { DataSource } from '../api/types';

export function cx(...c: (string | false | null | undefined)[]) {
  return c.filter(Boolean).join(' ');
}

// ------------------------------------------------------------------ buttons
type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';
const variants: Record<Variant, string> = {
  primary:
    'border-white/10 bg-gradient-to-b from-[#7b98ff] to-[#5a78f0] text-white shadow-[0_8px_24px_-8px_rgb(108_140_255/0.7),inset_0_1px_0_rgb(255_255_255/0.25)] hover:from-[#8ba3ff] hover:to-[#6c8cff]',
  secondary: 'border-white/10 bg-white/[0.06] text-slate-200 hover:bg-white/[0.1] hover:border-white/20',
  danger: 'border-rose-400/30 bg-rose-500/80 text-white hover:bg-rose-500 shadow-[0_8px_24px_-10px_rgb(251_113_133/0.7)]',
  ghost: 'border-transparent bg-transparent text-slate-300 hover:bg-white/[0.07]',
};

export function Button({
  variant = 'secondary', loading, icon, children, className, disabled, type = 'button', ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; loading?: boolean; icon?: ReactNode }) {
  return (
    <button
      type={type}
      {...rest}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cx(
        'inline-flex min-h-8 items-center justify-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm font-medium',
        'transition-all duration-150 select-none active:scale-[0.97]',
        'disabled:cursor-not-allowed disabled:opacity-45 disabled:active:scale-100',
        variants[variant], className,
      )}
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : icon}
      {children}
    </button>
  );
}

// ------------------------------------------------------------------ layout pieces
export function PageHeader({ title, subtitle, actions, eyebrow }: { title: string; subtitle?: ReactNode; actions?: ReactNode; eyebrow?: string }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div className="min-w-0">
        {eyebrow && <div className="mb-1 text-[11px] font-semibold tracking-[0.18em] text-accent-400 uppercase">{eyebrow}</div>}
        <h1 className="text-2xl font-semibold tracking-tight text-fg">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-fg-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Card({ title, actions, children, className, bodyClass }: {
  title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string; bodyClass?: string;
}) {
  return (
    <section className={cx('panel min-w-0', className)}>
      {(title || actions) && (
        <header className="flex flex-wrap items-center justify-between gap-2 border-b border-ink-700 px-4 py-3">
          <h2 className="text-sm font-semibold text-slate-100">{title}</h2>
          <div className="flex flex-wrap items-center gap-2">{actions}</div>
        </header>
      )}
      <div className={cx('p-4', bodyClass)}>{children}</div>
    </section>
  );
}

const toneText = {
  default: 'text-fg', good: 'text-ok', warn: 'text-warn', bad: 'text-danger', accent: 'text-accent-300',
} as const;
const toneGlow = {
  default: 'from-white/10', good: 'from-emerald-400/25', warn: 'from-amber-400/25', bad: 'from-rose-400/25', accent: 'from-[#6c8cff]/30',
} as const;

export function StatCard({ label, value, sub, icon, tone = 'default' }: {
  label: string; value: ReactNode; sub?: ReactNode; icon?: ReactNode; tone?: keyof typeof toneText;
}) {
  return (
    <div className="panel glass-hover relative overflow-hidden p-4">
      <div className={cx('pointer-events-none absolute -top-10 -right-10 h-28 w-28 rounded-full bg-gradient-to-br to-transparent blur-2xl', toneGlow[tone])} />
      <div className="relative flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="text-[11px] font-medium tracking-wider text-fg-muted uppercase">{label}</div>
          <div className={cx('mt-1.5 truncate text-2xl font-semibold tabular-nums', toneText[tone])}>{value}</div>
          {sub && <div className="mt-1 truncate text-xs text-slate-500">{sub}</div>}
        </div>
        {icon && <div className="rounded-xl border border-white/10 bg-white/[0.06] p-2 text-slate-300">{icon}</div>}
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ badges
const badgeTones: Record<string, string> = {
  green: 'bg-emerald-400/10 text-emerald-300 ring-emerald-400/25',
  red: 'bg-rose-400/10 text-rose-300 ring-rose-400/30',
  amber: 'bg-amber-400/10 text-amber-200 ring-amber-400/25',
  blue: 'bg-[#6c8cff]/12 text-accent-300 ring-[#6c8cff]/30',
  cyan: 'bg-cyan-400/10 text-cyan-200 ring-cyan-400/25',
  violet: 'bg-violet-400/12 text-violet-200 ring-violet-400/30',
  orange: 'bg-orange-400/10 text-orange-200 ring-orange-400/25',
  gray: 'bg-white/[0.06] text-slate-300 ring-white/15',
};

export function Badge({ tone = 'gray', children, title }: { tone?: keyof typeof badgeTones; children: ReactNode; title?: string }) {
  return (
    <span title={title} className={cx('inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[11px] font-semibold whitespace-nowrap ring-1 ring-inset', badgeTones[tone])}>
      {children}
    </span>
  );
}

const statusTone: Record<string, keyof typeof badgeTones> = {
  ACTIVE: 'green', HEALTHY: 'green', UP: 'green', IN_SYNC: 'green', INSTALLED: 'green', CONNECTED: 'green', APPLIED: 'green',
  SUCCESS: 'green', SUCCEEDED: 'green', RESOLVED: 'green', ALLOWED: 'green', ALLOW: 'green', SECURE: 'green',
  FAILED: 'red', DOWN: 'red', UNREACHABLE: 'red', BLOCKED: 'red', DENY: 'red', CRITICAL: 'red', AUTH_FAILED: 'red',
  FAILURE: 'red', DRIFT: 'orange', HIGH: 'orange', AT_RISK: 'orange',
  DEGRADED: 'amber', PENDING: 'amber', MEDIUM: 'amber', OPEN: 'amber', STOPPED: 'amber', ACKNOWLEDGED: 'blue',
  INSTANTIATING: 'blue', STARTING: 'blue', STOPPING: 'blue', TERMINATING: 'blue', STARTED: 'blue', RETRIEVED: 'blue',
  LOW: 'cyan', CREATED: 'violet', UNPROTECTED: 'red', DISABLED: 'gray',
};

export function StatusBadge({ value }: { value?: string | null }) {
  if (!value) return <span className="text-slate-500">—</span>;
  const transitional = ['INSTANTIATING', 'STARTING', 'STOPPING', 'TERMINATING'].includes(value);
  const live = ['ACTIVE', 'UP', 'CONNECTED', 'HEALTHY'].includes(value);
  return (
    <Badge tone={statusTone[value] ?? 'gray'}>
      {transitional && <Loader2 className="h-3 w-3 animate-spin" />}
      {live && <span className="h-1.5 w-1.5 animate-pulse-soft rounded-full bg-current" />}
      {value.replace(/_/g, ' ')}
    </Badge>
  );
}

/** Provenance of data. Every infrastructure-derived figure in the UI carries one. */
export function SourceBadge({ source }: { source?: DataSource | string | null }) {
  if (!source) return <Badge tone="gray" title="No measurement available">NO DATA</Badge>;
  const map: Record<string, { tone: keyof typeof badgeTones; title: string }> = {
    LIVE: { tone: 'green', title: 'Measured on production infrastructure' },
    LAB: { tone: 'blue', title: 'Measured in the local NFV lab (real containers, real nftables counters)' },
    DEMO: { tone: 'violet', title: 'Demonstration fixture data – not a live measurement' },
  };
  const m = map[source] ?? { tone: 'gray' as const, title: source };
  return <Badge tone={m.tone} title={m.title}>{source}</Badge>;
}

// ------------------------------------------------------------------ states
export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-12 text-sm text-fg-muted" role="status">
      <Loader2 className="h-4 w-4 animate-spin text-accent-400" /> {label}
    </div>
  );
}

/** Shimmering placeholder blocks used while first data loads. */
export function Skeleton({ className }: { className?: string }) {
  return <div className={cx('animate-pulse-soft rounded-lg bg-white/[0.06]', className)} />;
}

export function EmptyState({ title, hint, action }: { title: string; hint?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-4 py-12 text-center">
      <div className="rounded-2xl border border-white/10 bg-white/[0.04] p-3"><Inbox className="h-6 w-6 text-slate-500" /></div>
      <div className="text-sm font-medium text-slate-200">{title}</div>
      {hint && <div className="max-w-md text-xs text-slate-500">{hint}</div>}
      {action}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex items-start gap-3 rounded-xl border border-rose-400/25 bg-rose-500/[0.07] p-3 text-sm text-rose-100" role="alert">
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-rose-300" />
      <div className="min-w-0 flex-1 break-words">{message}</div>
      {onRetry && (
        <Button variant="ghost" onClick={onRetry} icon={<RefreshCw className="h-3.5 w-3.5" />}>Retry</Button>
      )}
    </div>
  );
}

export function Banner({ tone, children }: { tone: 'demo' | 'warn' | 'info'; children: ReactNode }) {
  const cls = {
    demo: 'border-violet-400/30 bg-violet-500/[0.09] text-violet-100',
    warn: 'border-amber-400/30 bg-amber-500/[0.08] text-amber-100',
    info: 'border-[#6c8cff]/25 bg-[#6c8cff]/[0.07] text-slate-200',
  }[tone];
  return <div className={cx('mb-4 rounded-xl border px-4 py-2.5 text-sm backdrop-blur', cls)}>{children}</div>;
}

// ------------------------------------------------------------------ overlays
function useEscape(open: boolean, onClose: () => void) {
  useEffect(() => {
    if (!open) return;
    const h = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', h);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', h);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);
}

export function Modal({ open, title, onClose, children, footer, wide }: {
  open: boolean; title: string; onClose: () => void; children: ReactNode; footer?: ReactNode; wide?: boolean;
}) {
  useEscape(open, onClose);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-[#030612]/70 p-0 backdrop-blur-sm animate-[fade-up_0.2s_ease-out] sm:items-center sm:p-4"
      role="dialog" aria-modal="true" aria-label={title} onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={cx('glass-strong flex max-h-[92vh] w-full flex-col rounded-t-2xl shadow-2xl animate-[fade-up_0.28s_cubic-bezier(0.22,1,0.36,1)] sm:rounded-2xl',
        wide ? 'sm:max-w-4xl' : 'sm:max-w-lg')}>
        <div className="flex items-center justify-between gap-3 border-b border-white/10 px-5 py-3.5">
          <h3 className="text-sm font-semibold text-fg">{title}</h3>
          <button onClick={onClose} className="rounded-lg p-1.5 text-slate-400 hover:bg-white/10 hover:text-slate-100" aria-label="Close">
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="overflow-y-auto p-5">{children}</div>
        {footer && <div className="flex flex-wrap justify-end gap-2 border-t border-white/10 px-5 py-3">{footer}</div>}
      </div>
    </div>
  );
}

/** Side panel used for editing forms (full screen on mobile). */
export function Drawer({ open, title, subtitle, onClose, children, footer }: {
  open: boolean; title: string; subtitle?: ReactNode; onClose: () => void; children: ReactNode; footer?: ReactNode;
}) {
  useEscape(open, onClose);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-[#030612]/60 backdrop-blur-sm animate-[fade-up_0.2s_ease-out]"
      role="dialog" aria-modal="true" aria-label={title} onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <aside className="glass-strong flex h-full w-full max-w-xl flex-col shadow-2xl sm:rounded-l-2xl animate-[slide-in_0.3s_cubic-bezier(0.22,1,0.36,1)]">
        <style>{'@keyframes slide-in{from{transform:translateX(24px);opacity:0}to{transform:none;opacity:1}}'}</style>
        <div className="flex items-start justify-between gap-3 border-b border-white/10 px-5 py-4">
          <div className="min-w-0">
            <h3 className="text-base font-semibold text-fg">{title}</h3>
            {subtitle && <div className="mt-0.5 text-xs text-fg-muted">{subtitle}</div>}
          </div>
          <button onClick={onClose} className="rounded-lg p-1.5 text-slate-400 hover:bg-white/10 hover:text-slate-100" aria-label="Close">
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-5">{children}</div>
        {footer && <div className="flex flex-wrap justify-end gap-2 border-t border-white/10 px-5 py-3">{footer}</div>}
      </aside>
    </div>
  );
}

export function ConfirmDialog({ open, title, message, confirmLabel = 'Confirm', danger, busy, onConfirm, onCancel }: {
  open: boolean; title: string; message: ReactNode; confirmLabel?: string; danger?: boolean; busy?: boolean;
  onConfirm: () => void; onCancel: () => void;
}) {
  return (
    <Modal open={open} title={title} onClose={onCancel} footer={
      <>
        <Button onClick={onCancel} disabled={busy}>Cancel</Button>
        <Button variant={danger ? 'danger' : 'primary'} loading={busy} onClick={onConfirm}>{confirmLabel}</Button>
      </>
    }>
      <div className="flex gap-3 text-sm text-slate-300">
        {danger && <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-rose-300" />}
        <div>{message}</div>
      </div>
    </Modal>
  );
}

// ------------------------------------------------------------------ form helpers
export function Field({ label, children, hint, error }: { label: string; children: ReactNode; hint?: string; error?: string }) {
  return (
    <label className="block">
      <span className="label">{label}</span>
      {children}
      {error ? <span className="mt-1 block text-xs text-rose-300">{error}</span>
        : hint ? <span className="mt-1 block text-xs text-slate-500">{hint}</span> : null}
    </label>
  );
}

export function Pager({ page, totalPages, total, onPage }: { page: number; totalPages: number; total: number; onPage: (p: number) => void }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 px-3 py-2.5 text-xs text-fg-muted">
      <span>{total.toLocaleString()} record(s)</span>
      <div className="flex items-center gap-2">
        <Button variant="ghost" disabled={page <= 0} onClick={() => onPage(page - 1)}>Previous</Button>
        <span>Page {totalPages === 0 ? 0 : page + 1} / {totalPages}</span>
        <Button variant="ghost" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</Button>
      </div>
    </div>
  );
}

/** Segmented control (e.g. table / cards, list / timeline). */
export function Segmented<T extends string>({ value, options, onChange, label }: {
  value: T; options: { value: T; label: ReactNode }[]; onChange: (v: T) => void; label: string;
}) {
  return (
    <div className="inline-flex rounded-lg border border-white/10 bg-white/[0.04] p-0.5" role="tablist" aria-label={label}>
      {options.map((o) => (
        <button key={o.value} role="tab" aria-selected={value === o.value} onClick={() => onChange(o.value)}
          className={cx('flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors',
            value === o.value ? 'bg-[#6c8cff]/25 text-accent-200 shadow-inner' : 'text-slate-400 hover:text-slate-200')}>
          {o.label}
        </button>
      ))}
    </div>
  );
}
