import { Cpu, LayoutGrid, MemoryStick, Plus, RefreshCw, Rows3, Search } from 'lucide-react';
import { useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, errorMessage } from '../api/client';
import type { Vnf } from '../api/types';
import { CAN_OPERATE, useAuth } from '../auth/AuthContext';
import { useToast } from '../components/Toast';
import { Banner, Button, Card, EmptyState, ErrorState, Field, Modal, PageHeader, Segmented, SourceBadge, Spinner, StatusBadge, cx } from '../components/ui';
import { useApi } from '../hooks/useApi';
import { ago, bps, IPV4_RE, pct } from '../utils/format';
import { VnfActions } from './vnfActions';

export default function VnfListPage() {
  const { hasRole } = useAuth();
  const [query, setQuery] = useState('');
  const [stateFilter, setStateFilter] = useState('');
  const [creating, setCreating] = useState(false);
  const [view, setView] = useState<'cards' | 'table'>(() => {
    try { return (localStorage.getItem('nexus.vnfView') as 'cards' | 'table') || 'cards'; } catch { return 'cards'; }
  });
  const changeView = (v: 'cards' | 'table') => {
    setView(v);
    try { localStorage.setItem('nexus.vnfView', v); } catch { /* storage unavailable */ }
  };
  const list = useApi(() => api.get<Vnf[]>('/vnfs').then((r) => r.data), [], 5000);
  const cfg = useApi(() => api.get<{ orchestratorMode: string }>('/integrations/config').then((r) => r.data), []);
  const orchestratorOff = cfg.data && !['docker', 'osm'].includes(String(cfg.data.orchestratorMode).toLowerCase());

  const rows = useMemo(() => (list.data ?? []).filter((v) =>
    (!stateFilter || v.state === stateFilter) &&
    (!query || `${v.name} ${v.vnfId} ${v.lanIp ?? ''} ${v.wanIp ?? ''} ${v.mgmtIp ?? ''}`.toLowerCase().includes(query.toLowerCase()))),
  [list.data, query, stateFilter]);

  return (
    <div>
      <PageHeader title="VNF Management" subtitle="Lifecycle of virtual firewall network functions"
        actions={<>
          <Button icon={<RefreshCw className="h-4 w-4" />} onClick={() => list.reload()}>Refresh</Button>
          {hasRole(...CAN_OPERATE) && <Button variant="primary" icon={<Plus className="h-4 w-4" />} onClick={() => setCreating(true)}>Create VNF</Button>}
        </>} />

      {orchestratorOff && (
        <Banner tone="warn">
          <b>No NFV infrastructure is attached to this deployment.</b> VNF deployment needs the Docker NFV lab or an OSM
          orchestrator (privileged networking cannot run on this hosting platform). Records can be browsed; lifecycle operations
          will report that no orchestrator is enabled.
        </Banner>
      )}
      <Card bodyClass="p-0">
        <div className="flex flex-wrap items-center gap-2 border-b border-ink-700 p-3">
          <div className="relative w-full sm:w-72">
            <Search className="pointer-events-none absolute top-2.5 left-2.5 h-4 w-4 text-slate-500" />
            <input className="input pl-8" placeholder="Search name, ID or IP…" value={query} onChange={(e) => setQuery(e.target.value)} />
          </div>
          <select className="input w-44" value={stateFilter} onChange={(e) => setStateFilter(e.target.value)} aria-label="Filter by state">
            <option value="">All states</option>
            {['CREATED', 'INSTANTIATING', 'ACTIVE', 'STOPPING', 'STOPPED', 'STARTING', 'TERMINATING', 'TERMINATED', 'FAILED'].map((s) => <option key={s}>{s}</option>)}
          </select>
          <div className="ml-auto"><Segmented label="View" value={view} onChange={changeView}
            options={[{ value: 'cards', label: <><LayoutGrid className="h-3.5 w-3.5" /> Cards</> }, { value: 'table', label: <><Rows3 className="h-3.5 w-3.5" /> Table</> }]} /></div>
        </div>
        {list.loading && !list.data ? <Spinner /> : list.error && !list.data ? <div className="p-4"><ErrorState message={list.error} onRetry={() => list.reload()} /></div> : rows.length === 0 ? (
          <EmptyState title={list.data?.length ? 'No VNFs match the filter' : 'No VNFs registered'}
            hint="Create a firewall VNF, then deploy it to the NFV lab or OSM." />
        ) : view === 'cards' ? (
          <div className="grid gap-4 p-4 sm:grid-cols-2 2xl:grid-cols-3">
            {rows.map((v) => <VnfCard key={v.id} vnf={v} onDone={() => list.reload(true)} />)}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[980px]">
              <thead><tr>
                <th className="th">VNF</th><th className="th">Type</th><th className="th">State</th><th className="th">Health</th>
                <th className="th">IP addresses</th><th className="th">CPU</th><th className="th">Mem</th><th className="th">Throughput</th>
                <th className="th">Created</th><th className="th">Actions</th>
              </tr></thead>
              <tbody>
                {rows.map((v) => (
                  <tr key={v.id} className="align-top hover:bg-ink-800/40">
                    <td className="td">
                      <Link to={`/vnfs/${v.id}`} className="font-medium text-slate-100 hover:text-accent-300">{v.name}</Link>
                      <div className="mono text-[11px] text-slate-500">{v.vnfId} · {v.orchestrator}</div>
                      {v.state === 'FAILED' && v.lastError && <div className="mt-1 max-w-xs truncate text-[11px] text-rose-400" title={v.lastError}>{v.lastError}</div>}
                    </td>
                    <td className="td">{v.vnfType}</td>
                    <td className="td"><StatusBadge value={v.state} /></td>
                    <td className="td"><StatusBadge value={v.health} /></td>
                    <td className="td mono text-xs">
                      <div>LAN {v.lanIp ?? '—'}</div><div>DMZ {v.wanIp ?? '—'}</div><div className="text-slate-500">MGMT {v.mgmtIp ?? '—'}</div>
                    </td>
                    <td className="td tabular-nums">{pct(v.cpuPercent)}</td>
                    <td className="td tabular-nums">{pct(v.memoryPercent)}</td>
                    <td className="td tabular-nums">{bps(v.throughputBps)} {v.metricsSource && <SourceBadge source={v.metricsSource} />}</td>
                    <td className="td text-xs text-slate-400">{ago(v.createdAt)}<div>{v.createdBy}</div></td>
                    <td className="td"><VnfActions vnf={v} onDone={() => list.reload(true)} compact /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      <CreateVnfModal open={creating} onClose={() => setCreating(false)} onCreated={() => list.reload(true)} />
    </div>
  );
}

function Meter({ label, value, icon }: { label: string; value?: number | null; icon: React.ReactNode }) {
  const v = value == null ? null : Math.min(100, Math.max(0, Number(value)));
  return (
    <div>
      <div className="mb-1 flex items-center justify-between text-[11px] text-slate-400"><span className="flex items-center gap-1">{icon}{label}</span><span className="tabular-nums text-slate-200">{pct(value)}</span></div>
      <div className="h-1.5 overflow-hidden rounded-full bg-white/[0.07]">
        <div className={cx('h-full rounded-full bg-gradient-to-r transition-all duration-700', v != null && v > 80 ? 'from-amber-400 to-rose-400' : 'from-[#6c8cff] to-[#38d9ff]')}
          style={{ width: `${v ?? 0}%` }} />
      </div>
    </div>
  );
}

function VnfCard({ vnf: v, onDone }: { vnf: Vnf; onDone: () => void }) {
  return (
    <div className={cx('glass glass-hover flex flex-col gap-4 p-4', v.state === 'ACTIVE' && 'ring-1 ring-emerald-400/15')}>
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <Link to={`/vnfs/${v.id}`} className="block truncate text-base font-semibold text-fg hover:text-accent-200">{v.name}</Link>
          <div className="mono text-[11px] text-slate-500">{v.vnfId} · {v.orchestrator} · {v.vnfType}</div>
        </div>
        <div className="flex flex-col items-end gap-1"><StatusBadge value={v.state} /><StatusBadge value={v.health} /></div>
      </div>
      {v.state === 'FAILED' && v.lastError && <div className="line-clamp-2 rounded-lg bg-rose-500/10 px-2 py-1 text-[11px] text-rose-200" title={v.lastError}>{v.lastError}</div>}
      <div className="grid grid-cols-3 gap-2 text-xs">
        {[['LAN', v.lanIp], ['DMZ', v.wanIp], ['MGMT', v.mgmtIp]].map(([k, ip]) => (
          <div key={k} className="rounded-lg border border-white/[0.07] bg-white/[0.03] px-2 py-1.5">
            <div className="text-[10px] tracking-wider text-slate-500">{k}</div><div className="mono truncate text-slate-200">{ip ?? '—'}</div>
          </div>
        ))}
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Meter label="CPU" value={v.cpuPercent} icon={<Cpu className="h-3 w-3" />} />
        <Meter label="Memory" value={v.memoryPercent} icon={<MemoryStick className="h-3 w-3" />} />
      </div>
      <div className="flex items-center justify-between text-xs text-slate-400">
        <span>Throughput <b className="text-slate-200">{bps(v.throughputBps)}</b></span>
        <span className="flex items-center gap-1.5"><StatusBadge value={v.syncStatus} /><SourceBadge source={v.metricsSource} /></span>
      </div>
      <div className="mt-auto border-t border-white/[0.07] pt-3"><VnfActions vnf={v} onDone={onDone} compact /></div>
    </div>
  );
}

function CreateVnfModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const toast = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', description: '', lanIp: '10.10.0.254', wanIp: '10.20.0.254', cpuLimit: '0.5', memoryLimitMb: '256', defaultAction: 'DROP' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const set = (k: string, v: string) => setForm((f) => ({ ...f, [k]: v }));

  const validate = () => {
    const e: Record<string, string> = {};
    if (!/^[a-z][a-z0-9-]{1,30}[a-z0-9]$/.test(form.name)) e.name = '3-32 chars, lower-case letters, digits and "-", starting with a letter';
    if (form.lanIp && !IPV4_RE.test(form.lanIp)) e.lanIp = 'Invalid IPv4 address';
    if (form.wanIp && !IPV4_RE.test(form.wanIp)) e.wanIp = 'Invalid IPv4 address';
    const cpu = Number(form.cpuLimit);
    if (!(cpu >= 0.25 && cpu <= 4)) e.cpuLimit = '0.25 – 4 vCPU';
    const mem = Number(form.memoryLimitMb);
    if (!(Number.isInteger(mem) && mem >= 128 && mem <= 4096)) e.memoryLimitMb = '128 – 4096 MiB';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const submit = async (ev: FormEvent) => {
    ev.preventDefault();
    if (!validate()) return;
    setBusy(true);
    try {
      const r = await api.post<Vnf>('/vnfs', {
        name: form.name, vnfType: 'FIREWALL', description: form.description || undefined,
        lanIp: form.lanIp || undefined, wanIp: form.wanIp || undefined,
        cpuLimit: Number(form.cpuLimit), memoryLimitMb: Number(form.memoryLimitMb), defaultAction: form.defaultAction,
      });
      toast.success(`VNF ${r.data.name} created`, 'State CREATED – deploy it when ready.');
      onCreated();
      onClose();
      navigate(`/vnfs/${r.data.id}`);
    } catch (e) {
      toast.error('Create failed', errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={open} title="Create firewall VNF" onClose={onClose} footer={<>
      <Button onClick={onClose}>Cancel</Button>
      <Button variant="primary" loading={busy} type="submit" form="create-vnf">Create</Button>
    </>}>
      <form id="create-vnf" onSubmit={submit} className="grid grid-cols-2 gap-3" noValidate>
        <div className="col-span-2"><Field label="Name" error={errors.name} hint="DNS-safe; becomes the container/NS name">
          <input className="input" value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="fw-edge-01" /></Field></div>
        <div className="col-span-2"><Field label="Description"><input className="input" value={form.description} onChange={(e) => set('description', e.target.value)} /></Field></div>
        <Field label="LAN interface IP" error={errors.lanIp} hint="Lab LAN 10.10.0.0/24"><input className="input mono" value={form.lanIp} onChange={(e) => set('lanIp', e.target.value)} /></Field>
        <Field label="DMZ interface IP" error={errors.wanIp} hint="Lab DMZ 10.20.0.0/24"><input className="input mono" value={form.wanIp} onChange={(e) => set('wanIp', e.target.value)} /></Field>
        <Field label="vCPU limit" error={errors.cpuLimit}><input className="input" type="number" step="0.25" value={form.cpuLimit} onChange={(e) => set('cpuLimit', e.target.value)} /></Field>
        <Field label="Memory (MiB)" error={errors.memoryLimitMb}><input className="input" type="number" step="64" value={form.memoryLimitMb} onChange={(e) => set('memoryLimitMb', e.target.value)} /></Field>
        <div className="col-span-2"><Field label="Default policy" hint="DROP (default-deny) is strongly recommended">
          <select className="input" value={form.defaultAction} onChange={(e) => set('defaultAction', e.target.value)}>
            <option value="DROP">DROP – deny anything not explicitly allowed</option>
            <option value="ACCEPT">ACCEPT – allow anything not explicitly denied</option>
          </select></Field></div>
      </form>
    </Modal>
  );
}
