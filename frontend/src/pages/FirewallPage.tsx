import { ArrowDown, ArrowUp, Code2, Pencil, Plus, Send, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, errorMessage } from '../api/client';
import type { FirewallRule, Page, Policy, RuntimeRuleset, SyncResult, TrafficEvent, Vnf } from '../api/types';
import { CAN_OPERATE, useAuth } from '../auth/AuthContext';
import { useToast } from '../components/Toast';
import { Banner, Button, Card, ConfirmDialog, Drawer, EmptyState, ErrorState, Modal, PageHeader, SourceBadge, Spinner, StatusBadge } from '../components/ui';
import { useApi } from '../hooks/useApi';
import { RuleForm } from './RuleEditorPage';
import { ago, bytes, dateTime, num } from '../utils/format';

export function reportSync(toast: ReturnType<typeof useToast>, sync: SyncResult, what: string) {
  if (sync.applied) toast.success(`${what} – enforced on VNF`, sync.message);
  else if (!sync.attempted) toast.info(`${what} – saved (not yet enforced)`, sync.message);
  else toast.error(`${what} – saved but NOT enforced`, sync.message);
}

export default function FirewallPage() {
  const [params, setParams] = useSearchParams();
  const vnfs = useApi(() => api.get<Vnf[]>('/vnfs').then((r) => r.data.filter((v) => v.vnfType === 'FIREWALL' && v.state !== 'TERMINATED')), []);
  const selected = params.get('vnf');

  useEffect(() => {
    if (!selected && vnfs.data?.length) {
      const active = vnfs.data.find((v) => v.state === 'ACTIVE') ?? vnfs.data[0];
      setParams({ vnf: String(active.id) }, { replace: true });
    }
  }, [selected, vnfs.data, setParams]);

  return (
    <div>
      <PageHeader title="Firewall Policy" subtitle="nftables rules enforced inside the selected firewall VNF"
        actions={vnfs.data?.length ? (
          <select className="input w-64" value={selected ?? ''} onChange={(e) => setParams({ vnf: e.target.value })} aria-label="Firewall VNF">
            {vnfs.data.map((v) => <option key={v.id} value={v.id}>{v.name} ({v.state})</option>)}
          </select>
        ) : null} />
      {vnfs.loading && !vnfs.data ? <Spinner /> : vnfs.error ? <ErrorState message={vnfs.error} /> :
        !vnfs.data?.length ? <Card><EmptyState title="No firewall VNFs" hint="Create a firewall VNF first." action={<Link to="/vnfs"><Button variant="primary">Go to VNF Management</Button></Link>} /></Card>
          : selected ? <PolicyView vnfId={Number(selected)} /> : null}
    </div>
  );
}

function PolicyView({ vnfId }: { vnfId: number }) {
  const { hasRole } = useAuth();
  const canEdit = hasRole(...CAN_OPERATE);
  const toast = useToast();
  const policy = useApi(() => api.get<Policy>(`/firewall/vnfs/${vnfId}/policy`).then((r) => r.data), [vnfId], 10000);
  const [toDelete, setToDelete] = useState<FirewallRule | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [runtime, setRuntime] = useState<RuntimeRuleset | null>(null);
  const [editing, setEditing] = useState<{ ruleId?: number } | null>(null);
  const [saving, setSaving] = useState(false);

  if (policy.loading && !policy.data) return <Spinner />;
  if (policy.error && !policy.data) return <ErrorState message={policy.error} onRetry={() => policy.reload()} />;
  const p = policy.data!;
  const rules = p.rules;

  const toggle = async (r: FirewallRule) => {
    setBusy(`t${r.id}`);
    try {
      const res = await api.patch<{ sync: SyncResult }>(`/firewall/rules/${r.id}/enabled`, { enabled: !r.enabled });
      reportSync(toast, res.data.sync, `Rule "${r.name}" ${r.enabled ? 'disabled' : 'enabled'}`);
      await policy.reload(true);
    } catch (e) { toast.error('Update failed', errorMessage(e)); } finally { setBusy(null); }
  };

  const move = async (idx: number, dir: -1 | 1) => {
    const order = rules.map((r) => r.id);
    const j = idx + dir;
    [order[idx], order[j]] = [order[j], order[idx]];
    setBusy('reorder');
    try {
      await api.post(`/firewall/vnfs/${vnfId}/rules/reorder`, { ruleIds: order });
      toast.success('Rule order updated', 'Priorities renumbered (10, 20, 30, …) and policy re-applied.');
      await policy.reload(true);
    } catch (e) { toast.error('Reorder failed', errorMessage(e)); } finally { setBusy(null); }
  };

  const remove = async () => {
    if (!toDelete) return;
    setBusy('delete');
    try {
      const res = await api.delete<SyncResult>(`/firewall/rules/${toDelete.id}`);
      reportSync(toast, res.data, `Rule "${toDelete.name}" deleted`);
      setToDelete(null);
      await policy.reload(true);
    } catch (e) { toast.error('Delete failed', errorMessage(e)); } finally { setBusy(null); }
  };

  const apply = async () => {
    setBusy('apply');
    try {
      const res = await api.post<SyncResult>(`/firewall/vnfs/${vnfId}/apply`);
      reportSync(toast, res.data, 'Policy re-applied');
      await policy.reload(true);
    } catch (e) { toast.error('Apply failed', errorMessage(e)); } finally { setBusy(null); }
  };

  const showRuntime = async () => {
    setBusy('runtime');
    try { setRuntime((await api.get<RuntimeRuleset>(`/firewall/vnfs/${vnfId}/runtime`)).data); }
    catch (e) { toast.error('Cannot read runtime ruleset', errorMessage(e)); } finally { setBusy(null); }
  };

  return (
    <>
      {p.vnfState !== 'ACTIVE' && <Banner tone="info">VNF is <b>{p.vnfState}</b>. Rule changes are saved and will be enforced automatically when the VNF becomes ACTIVE.</Banner>}
      {p.vnfState === 'ACTIVE' && p.syncStatus !== 'IN_SYNC' && <Banner tone="warn">Policy status <b>{p.syncStatus}</b>: desired revision {p.rulesRevision}, enforced revision {p.appliedRevision}. Use “Apply now” to retry.</Banner>}

      <div className="mb-4 grid gap-3 md:grid-cols-4">
        <div className="panel p-3"><div className="label">Default action</div><StatusBadge value={p.defaultAction === 'DROP' ? 'DENY' : 'ALLOW'} /> <span className="text-sm text-slate-300">{p.defaultAction} unmatched traffic</span></div>
        <div className="panel p-3"><div className="label">Enforcement</div><StatusBadge value={p.syncStatus} /> <span className="mono text-xs text-slate-400">rev {p.appliedRevision} / {p.rulesRevision}</span></div>
        <div className="panel p-3"><div className="label">Last applied</div><span className="text-sm text-slate-300">{p.rulesAppliedAt ? `${dateTime(p.rulesAppliedAt)} (${ago(p.rulesAppliedAt)})` : 'never'}</span></div>
        <div className="panel p-3"><div className="label">Rules</div><span className="text-sm text-slate-300">{rules.filter((r) => r.enabled).length} enabled of {rules.length}</span></div>
      </div>

      <Card title="Rules (evaluated top to bottom, first match wins)" bodyClass="p-0" actions={<>
        <Button icon={<Code2 className="h-4 w-4" />} loading={busy === 'runtime'} disabled={p.vnfState !== 'ACTIVE'} onClick={showRuntime}>View nftables</Button>
        {canEdit && <Button icon={<Send className="h-4 w-4" />} loading={busy === 'apply'} disabled={p.vnfState !== 'ACTIVE'} onClick={apply}>Apply now</Button>}
        {canEdit && <Button variant="primary" icon={<Plus className="h-4 w-4" />} onClick={() => setEditing({})}>Add rule</Button>}
      </>}>
        {rules.length === 0 ? <EmptyState title="No rules – all traffic receives the default action" hint={p.defaultAction === 'DROP' ? 'Everything is currently blocked (default-deny). Add ALLOW rules for permitted services.' : undefined} /> : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead><tr>
                <th className="th w-16">Prio</th><th className="th">Name</th><th className="th">Action</th><th className="th">Proto</th>
                <th className="th">Source</th><th className="th">Destination</th><th className="th">Hits</th><th className="th">Log</th><th className="th">Status</th>
                {canEdit && <th className="th">Actions</th>}
              </tr></thead>
              <tbody>
                {rules.map((r, i) => (
                  <tr key={r.id} className={r.enabled ? 'hover:bg-ink-800/40' : 'opacity-50 hover:bg-ink-800/40'}>
                    <td className="td mono">{r.priority}</td>
                    <td className="td"><div className="font-medium text-slate-100">{r.name}</div>{r.description && <div className="text-[11px] text-slate-500">{r.description}</div>}</td>
                    <td className="td"><StatusBadge value={r.action} /></td>
                    <td className="td">{r.protocol}</td>
                    <td className="td mono text-xs">{r.sourceCidr ?? 'any'}{r.sourcePort ? `:${r.sourcePort}` : ''}</td>
                    <td className="td mono text-xs">{r.destinationCidr ?? 'any'}{r.destinationPort ? `:${r.destinationPort}` : ''}</td>
                    <td className="td text-xs tabular-nums" title={r.countersUpdatedAt ? `updated ${ago(r.countersUpdatedAt)}` : 'no counter data yet'}>{num(r.hitPackets)} pkts<div className="text-slate-500">{bytes(r.hitBytes)}</div></td>
                    <td className="td text-xs">{r.logEnabled ? 'yes' : 'no'}</td>
                    <td className="td">
                      {canEdit ? (
                        <button onClick={() => toggle(r)} disabled={busy === `t${r.id}`} role="switch" aria-checked={r.enabled} aria-label={`Toggle ${r.name}`}
                          className={`relative h-5 w-9 rounded-full transition-colors ${r.enabled ? 'bg-emerald-500/70' : 'bg-ink-600'}`}>
                          <span className={`absolute top-0.5 h-4 w-4 rounded-full bg-white transition-all ${r.enabled ? 'left-4.5' : 'left-0.5'}`} />
                        </button>
                      ) : <StatusBadge value={r.enabled ? 'ACTIVE' : 'STOPPED'} />}
                    </td>
                    {canEdit && (
                      <td className="td">
                        <div className="flex gap-1">
                          <Button variant="ghost" className="px-1.5" disabled={i === 0 || !!busy} onClick={() => move(i, -1)} aria-label="Move up"><ArrowUp className="h-3.5 w-3.5" /></Button>
                          <Button variant="ghost" className="px-1.5" disabled={i === rules.length - 1 || !!busy} onClick={() => move(i, 1)} aria-label="Move down"><ArrowDown className="h-3.5 w-3.5" /></Button>
                          <Button variant="ghost" className="px-1.5" onClick={() => setEditing({ ruleId: r.id })} aria-label="Edit"><Pencil className="h-3.5 w-3.5" /></Button>
                          <Button variant="ghost" className="px-1.5 text-rose-400" onClick={() => setToDelete(r)} aria-label="Delete"><Trash2 className="h-3.5 w-3.5" /></Button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
                <tr className="bg-ink-900/40">
                  <td className="td mono text-slate-500">∞</td><td className="td text-slate-400 italic">default policy</td>
                  <td className="td"><StatusBadge value={p.defaultAction === 'DROP' ? 'DENY' : 'ALLOW'} /></td>
                  <td className="td text-slate-500" colSpan={canEdit ? 7 : 6}>all traffic not matched above (established/related connections are always allowed)</td>
                </tr>
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="mt-4 grid gap-4 xl:grid-cols-2">
        <RecentEvents vnfId={vnfId} action="BLOCKED" />
        <RecentEvents vnfId={vnfId} action="ALLOWED" />
      </div>

      <ConfirmDialog open={!!toDelete} title="Delete firewall rule" danger busy={busy === 'delete'} confirmLabel="Delete"
        message={<>Delete rule <b>{toDelete?.name}</b>? The change is applied to the VNF immediately.</>}
        onConfirm={remove} onCancel={() => setToDelete(null)} />
      <Drawer open={!!editing} title={editing?.ruleId ? 'Edit firewall rule' : 'Add firewall rule'}
        subtitle="Saved as desired state and pushed to the VNF immediately" onClose={() => setEditing(null)}
        footer={<>
          <Button onClick={() => setEditing(null)}>Cancel</Button>
          <Button type="submit" form="rule-form-drawer" variant="primary" loading={saving}>{editing?.ruleId ? 'Save changes' : 'Create rule'}</Button>
        </>}>
        {editing && <RuleForm key={editing.ruleId ?? 'new'} vnfId={vnfId} ruleId={editing.ruleId} formId="rule-form-drawer" onBusy={setSaving}
          onSaved={() => { setEditing(null); void policy.reload(true); }} />}
      </Drawer>
      <Modal open={!!runtime} title="Ruleset loaded in the VNF (nft list table inet nexus_fw)" wide onClose={() => setRuntime(null)}>
        {runtime && <>
          <div className="mb-2 text-xs text-slate-400">Revision {runtime.revision} · source {runtime.source} · default {runtime.defaultAction}</div>
          <pre className="mono max-h-[60vh] overflow-auto rounded bg-ink-950 p-3 text-xs leading-relaxed text-emerald-200">{runtime.rendered}</pre>
        </>}
      </Modal>
    </>
  );
}

function RecentEvents({ vnfId, action }: { vnfId: number; action: 'BLOCKED' | 'ALLOWED' }) {
  const ev = useApi(() => api.get<Page<TrafficEvent>>('/traffic/events', { params: { vnfId, action, size: 12 } }).then((r) => r.data), [vnfId, action], 10000);
  return (
    <Card title={action === 'BLOCKED' ? 'Recently blocked traffic' : 'Recently allowed connections'} bodyClass="p-0"
      actions={<Link className="text-xs text-accent-400 hover:underline" to={`/monitoring?action=${action}&vnfId=${vnfId}`}>Search →</Link>}>
      {ev.error ? <div className="p-3"><ErrorState message={ev.error} /></div> : !ev.data?.content.length ? <EmptyState title="No logged events" hint="Events come from NFLOG (header metadata only), rate-limited inside the VNF." /> : (
        <table className="w-full">
          <thead><tr><th className="th">Time</th><th className="th">Proto</th><th className="th">Source</th><th className="th">Destination</th><th className="th">Rule</th><th className="th">Src</th></tr></thead>
          <tbody>{ev.data.content.map((e) => (
            <tr key={e.id}><td className="td text-xs">{ago(e.occurredAt)}</td><td className="td text-xs">{e.protocol}</td>
              <td className="td mono text-xs">{e.srcIp}{e.srcPort ? `:${e.srcPort}` : ''}</td>
              <td className="td mono text-xs">{e.dstIp}{e.dstPort ? `:${e.dstPort}` : ''}</td>
              <td className="td text-xs">{e.ruleId ?? 'default'}</td><td className="td"><SourceBadge source={e.dataSource} /></td></tr>
          ))}</tbody>
        </table>
      )}
    </Card>
  );
}
