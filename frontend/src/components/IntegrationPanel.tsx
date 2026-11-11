import { RefreshCw } from 'lucide-react';
import { useState } from 'react';
import { api, errorMessage } from '../api/client';
import type { Connection } from '../api/types';
import { CAN_OPERATE, useAuth } from '../auth/AuthContext';
import { useApi } from '../hooks/useApi';
import { ago, dateTime } from '../utils/format';
import { useToast } from './Toast';
import { Badge, Button, StatusBadge, cx } from './ui';

const modeTone = { REAL: 'green', LAB: 'blue', DEMO: 'violet', DISABLED: 'gray' } as const;

/**
 * Infrastructure status panel for one integration type (ONOS, OSM, DOCKER, NETCONF): connection state,
 * endpoint, actual-vs-demo mode, last probe and last successful probe – straight from the health service.
 */
export function IntegrationPanel({ type, title }: { type: string; title: string }) {
  const { hasRole } = useAuth();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const conns = useApi(() => api.get<Connection[]>('/integrations').then((r) => r.data.filter((c) => c.type === type)), [type], 30000);

  const check = async () => {
    setBusy(true);
    try {
      await api.post('/integrations/check');
      await conns.reload(true);
      toast.success(`${title} health re-checked`);
    } catch (e) { toast.error('Health check failed', errorMessage(e)); } finally { setBusy(false); }
  };

  return (
    <section className="panel mb-5 overflow-hidden" aria-label={`${title} integration status`}>
      <header className="flex flex-wrap items-center justify-between gap-2 border-b border-ink-700 px-4 py-3">
        <h2 className="text-sm font-semibold text-slate-100">{title} · integration health</h2>
        {hasRole(...CAN_OPERATE) && <Button icon={<RefreshCw className="h-3.5 w-3.5" />} loading={busy} onClick={check}>Check now</Button>}
      </header>
      {!conns.data?.length ? (
        <div className="px-4 py-4 text-sm text-slate-500">{conns.loading ? 'Loading…' : 'No health record yet – the first probe runs within 30 s of startup.'}</div>
      ) : conns.data.map((c) => (
        <div key={c.name} className="grid gap-4 px-4 py-4 sm:grid-cols-2 lg:grid-cols-5">
          <Item label="Connection"><StatusBadge value={c.status} />{c.latencyMs != null && <span className="ml-2 text-xs text-slate-500">{c.latencyMs} ms</span>}</Item>
          <Item label="Mode"><Badge tone={modeTone[c.mode as keyof typeof modeTone] ?? 'gray'}>{c.mode === 'REAL' ? 'ACTUAL' : c.mode}</Badge></Item>
          <Item label="Endpoint"><span className="mono text-xs break-all text-slate-300">{c.endpoint || 'not configured'}</span></Item>
          <Item label="Last check">{c.lastCheckedAt ? <span title={dateTime(c.lastCheckedAt)}>{ago(c.lastCheckedAt)}</span> : '—'}</Item>
          <Item label="Last successful connection">
            <span className={cx(!c.lastSuccessAt && 'text-slate-500')}>{c.lastSuccessAt ? dateTime(c.lastSuccessAt) : 'never'}</span>
          </Item>
          {(c.lastError || c.details) && (
            <div className={cx('text-xs break-words sm:col-span-2 lg:col-span-5', c.lastError ? 'text-rose-300' : 'text-slate-400')}>{c.lastError ?? c.details}</div>
          )}
        </div>
      ))}
    </section>
  );
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="min-w-0"><div className="label">{label}</div><div className="text-sm text-slate-200">{children}</div></div>;
}
