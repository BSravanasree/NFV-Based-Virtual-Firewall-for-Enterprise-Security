import { KeyRound, RefreshCw, Save } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { api, errorMessage } from '../api/client';
import type { Connection, Setting } from '../api/types';
import { ADMIN_ONLY, CAN_OPERATE, useAuth } from '../auth/AuthContext';
import { useToast } from '../components/Toast';
import { Badge, Button, Card, ErrorState, Field, PageHeader, Spinner, StatusBadge } from '../components/ui';
import { useApi } from '../hooks/useApi';
import { ago, dateTime } from '../utils/format';
import { passwordProblems } from './UsersPage';

export default function SettingsPage() {
  const { hash } = useLocation();
  useEffect(() => {
    if (hash) setTimeout(() => document.getElementById(hash.slice(1))?.scrollIntoView({ behavior: 'smooth' }), 150);
  }, [hash]);
  const { hasRole, user } = useAuth();
  const isAdmin = hasRole(...ADMIN_ONLY);
  const toast = useToast();
  const settings = useApi(() => api.get<Setting[]>('/settings').then((r) => r.data), []);
  const integrations = useApi(() => api.get<Connection[]>('/integrations').then((r) => r.data), [], 30000);
  const config = useApi(() => api.get<Record<string, string | number | boolean>>('/integrations/config').then((r) => r.data), []);
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);

  const save = async (s: Setting) => {
    setSaving(s.key);
    try {
      await api.put(`/settings/${encodeURIComponent(s.key)}`, { value: edits[s.key] });
      toast.success('Setting saved', `${s.key} = ${edits[s.key]}`);
      setEdits((e) => { const n = { ...e }; delete n[s.key]; return n; });
      void settings.reload(true);
    } catch (e) { toast.error('Setting rejected', errorMessage(e)); } finally { setSaving(null); }
  };

  const check = async () => {
    setChecking(true);
    try { await api.post('/integrations/check'); toast.success('Integration health refreshed'); void integrations.reload(true); }
    catch (e) { toast.error('Health check failed', errorMessage(e)); } finally { setChecking(false); }
  };

  const groups = (settings.data ?? []).reduce<Record<string, Setting[]>>((g, s) => ({ ...g, [s.category]: [...(g[s.category] ?? []), s] }), {});

  return (
    <div>
      <PageHeader title="System Settings" subtitle={isAdmin ? 'Detection thresholds, retention, integrations' : 'Read-only for your role (changes require ADMIN)'} />
      <div className="grid gap-4 xl:grid-cols-2">
        <div className="space-y-4">
          {settings.loading && !settings.data ? <Spinner /> : settings.error ? <ErrorState message={settings.error} /> : Object.entries(groups).map(([cat, list]) => (
            <Card key={cat} title={cat === 'DETECTION' ? 'Threat detection' : cat === 'RETENTION' ? 'Data retention' : cat} bodyClass="divide-y divide-ink-700 p-0">
              {list.map((s) => {
                const val = edits[s.key] ?? s.value;
                const dirty = edits[s.key] !== undefined && edits[s.key] !== s.value;
                return (
                  <div key={s.key} className="flex items-center gap-3 px-4 py-3">
                    <div className="min-w-0 flex-1">
                      <div className="mono text-xs text-slate-300">{s.key}</div>
                      <div className="text-xs text-slate-500">{s.description}</div>
                      {s.updatedBy && <div className="text-[10px] text-slate-600">changed by {s.updatedBy} {ago(s.updatedAt)}</div>}
                    </div>
                    {s.valueType === 'BOOL' ? (
                      <select className="input w-28" disabled={!isAdmin} value={val} onChange={(e) => setEdits({ ...edits, [s.key]: e.target.value })} aria-label={s.key}>
                        <option value="true">enabled</option><option value="false">disabled</option>
                      </select>
                    ) : (
                      <input className="input w-28 text-right" disabled={!isAdmin} type={s.valueType === 'INT' ? 'number' : 'text'} value={val}
                        onChange={(e) => setEdits({ ...edits, [s.key]: e.target.value })} aria-label={s.key} />
                    )}
                    {isAdmin && <Button variant={dirty ? 'primary' : 'ghost'} disabled={!dirty} loading={saving === s.key} onClick={() => save(s)} icon={<Save className="h-3.5 w-3.5" />} aria-label={`Save ${s.key}`} />}
                  </div>
                );
              })}
            </Card>
          ))}
          <ChangePassword username={user?.username ?? ''} />
        </div>
        <div className="space-y-4">
          <Card title="Integrations" actions={hasRole(...CAN_OPERATE) && <Button icon={<RefreshCw className="h-4 w-4" />} loading={checking} onClick={check}>Check now</Button>} bodyClass="p-0">
            {integrations.error ? <div className="p-3"><ErrorState message={integrations.error} /></div> : (
              <table className="w-full">
                <thead><tr><th className="th">System</th><th className="th">Mode</th><th className="th">Status</th><th className="th">Endpoint / detail</th></tr></thead>
                <tbody>{integrations.data?.map((c) => (
                  <tr key={c.type + c.name} className="align-top">
                    <td className="td"><div className="font-medium text-slate-100">{c.type}</div><div className="text-[11px] text-slate-500">{c.name}</div></td>
                    <td className="td">{c.mode === 'DEMO' ? <Badge tone="violet">DEMO</Badge> : <Badge>{c.mode}</Badge>}</td>
                    <td className="td"><StatusBadge value={c.status} />{c.latencyMs != null && <div className="text-[11px] text-slate-500">{c.latencyMs} ms</div>}</td>
                    <td className="td text-xs"><div className="mono break-all text-slate-400">{c.endpoint ?? '—'}</div>
                      <div className={c.lastError ? 'text-rose-400' : 'text-slate-500'}>{c.lastError ?? c.details}</div>
                      <div className="text-[10px] text-slate-600">checked {dateTime(c.lastCheckedAt)}</div></td>
                  </tr>))}
                </tbody>
              </table>
            )}
          </Card>
          <Card title="Runtime configuration" bodyClass="text-sm">
            <p className="mb-2 text-xs text-slate-500">Set through environment variables (see .env.example). Secrets are only reported as configured / missing and are never sent to the browser.</p>
            {config.data && (
              <dl className="grid grid-cols-[220px_1fr] gap-y-1">
                {Object.entries(config.data).map(([k, v]) => (
                  <div key={k} className="contents">
                    <dt className="mono text-xs text-slate-400">{k}</dt>
                    <dd className="text-xs">{typeof v === 'boolean'
                      ? (k.endsWith('Configured') ? <StatusBadge value={v ? 'ACTIVE' : 'FAILED'} /> : String(v))
                      : <span className="mono text-slate-300">{String(v || '—')}</span>}</dd>
                  </div>
                ))}
              </dl>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}

function ChangePassword({ username }: { username: string }) {
  const toast = useToast();
  const { logout } = useAuth();
  const [cur, setCur] = useState('');
  const [next, setNext] = useState('');
  const [busy, setBusy] = useState(false);
  const issues = next ? passwordProblems(next, username) : [];
  const submit = async () => {
    if (issues.length) return;
    setBusy(true);
    try {
      await api.post('/auth/change-password', { currentPassword: cur, newPassword: next });
      toast.success('Password changed', 'All sessions were revoked; please sign in again.');
      await logout().catch(() => undefined);
    } catch (e) { toast.error('Password not changed', errorMessage(e)); } finally { setBusy(false); }
  };
  return (
    <div id="password" className="scroll-mt-24"><Card title={<span className="flex items-center gap-2"><KeyRound className="h-4 w-4" /> Change my password</span>}>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Current password"><input type="password" autoComplete="current-password" className="input" value={cur} onChange={(e) => setCur(e.target.value)} /></Field>
        <Field label="New password" error={issues.length ? `Needs ${issues.join(', ')}` : undefined}><input type="password" autoComplete="new-password" className="input" value={next} onChange={(e) => setNext(e.target.value)} /></Field>
      </div>
      <div className="mt-3 flex justify-end"><Button variant="primary" loading={busy} disabled={!cur || !next || issues.length > 0} onClick={submit}>Change password</Button></div>
    </Card></div>
  );
}
