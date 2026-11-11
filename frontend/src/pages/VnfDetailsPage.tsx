import { ArrowLeft, Scaling, Settings2, Shield } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, errorMessage } from '../api/client';
import type { LifecycleEvent, MetricSample, Vnf } from '../api/types';
import { CAN_OPERATE, useAuth } from '../auth/AuthContext';
import { COLORS } from '../components/charts';
import { useToast } from '../components/Toast';
import { Banner, Button, Card, EmptyState, ErrorState, Field, Modal, PageHeader, SourceBadge, Spinner, StatusBadge } from '../components/ui';
import { useApi } from '../hooks/useApi';
import { bps, bytes, dateTime, pct, timeShort } from '../utils/format';
import { VnfActions } from './vnfActions';

const LIFECYCLE = ['CREATED', 'INSTANTIATING', 'ACTIVE', 'STOPPING', 'STOPPED', 'TERMINATING', 'TERMINATED'];

export default function VnfDetailsPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const vnf = useApi(() => api.get<Vnf>(`/vnfs/${id}`).then((r) => r.data), [id], 4000);
  const events = useApi(() => api.get<LifecycleEvent[]>(`/vnfs/${id}/events`).then((r) => r.data), [id], 6000);
  const metrics = useApi(() => api.get<MetricSample[]>(`/vnfs/${id}/metrics`, { params: { minutes: 60 } }).then((r) => r.data), [id], 15000);
  const [configuring, setConfiguring] = useState(false);
  const [scaling, setScaling] = useState(false);

  if (vnf.loading && !vnf.data) return <Spinner />;
  if (vnf.error && !vnf.data) return <ErrorState message={vnf.error} onRetry={() => vnf.reload()} />;
  const v = vnf.data!;
  const reloadAll = () => { void vnf.reload(true); void events.reload(true); };

  return (
    <div>
      <button onClick={() => navigate('/vnfs')} className="mb-2 flex items-center gap-1 text-xs text-slate-400 hover:text-slate-200"><ArrowLeft className="h-3.5 w-3.5" /> VNF Management</button>
      <PageHeader title={v.name} subtitle={<span className="mono">{v.vnfId} · {v.vnfType} · orchestrator {v.orchestrator}</span>}
        actions={<>
          <Link to={`/firewall?vnf=${v.id}`}><Button icon={<Shield className="h-4 w-4" />}>Firewall policy</Button></Link>
          {hasRole(...CAN_OPERATE) && v.state !== 'TERMINATED' && <Button icon={<Settings2 className="h-4 w-4" />} onClick={() => setConfiguring(true)}>Configure</Button>}
          {hasRole(...CAN_OPERATE) && v.state === 'ACTIVE' && <Button icon={<Scaling className="h-4 w-4" />} onClick={() => setScaling(true)}>Scale</Button>}
          <VnfActions vnf={v} onDone={() => { if (v.state === 'CREATED' || v.state === 'TERMINATED') reloadAll(); else reloadAll(); }} />
        </>} />

      {v.state === 'FAILED' && v.lastError && <Banner tone="warn"><b>Last operation failed:</b> {v.lastError}</Banner>}
      {v.state === 'ACTIVE' && v.syncStatus !== 'IN_SYNC' && (
        <Banner tone="warn">Firewall policy is <b>{v.syncStatus}</b> (desired revision {v.rulesRevision}, applied {v.appliedRevision}). {v.lastError}</Banner>
      )}

      <Card title="Lifecycle" className="mb-4">
        <div className="flex flex-wrap items-center gap-1">
          {LIFECYCLE.map((s, i) => {
            const reached = s === v.state;
            return (
              <div key={s} className="flex items-center gap-1">
                <span className={`rounded px-2 py-1 text-[11px] font-semibold ${reached ? 'bg-accent-600/30 text-accent-200 ring-1 ring-accent-500' : 'bg-ink-750 text-slate-500'}`}>{s}</span>
                {i < LIFECYCLE.length - 1 && <span className="text-slate-600">→</span>}
              </div>
            );
          })}
          {(v.state === 'FAILED' || v.state === 'STARTING') && <span className="ml-3"><StatusBadge value={v.state} /></span>}
        </div>
      </Card>

      <div className="mb-4 grid gap-4 lg:grid-cols-3">
        <Card title="Details" bodyClass="text-sm">
          <dl className="grid grid-cols-[130px_1fr] gap-y-1.5">
            <dt className="text-slate-500">State</dt><dd><StatusBadge value={v.state} /></dd>
            <dt className="text-slate-500">Health</dt><dd><StatusBadge value={v.health} /></dd>
            <dt className="text-slate-500">External ID</dt><dd className="mono truncate text-xs" title={v.externalId}>{v.externalId ?? '—'}</dd>
            <dt className="text-slate-500">Management IP</dt><dd className="mono">{v.mgmtIp ?? '—'}</dd>
            <dt className="text-slate-500">LAN IP</dt><dd className="mono">{v.lanIp ?? '—'}</dd>
            <dt className="text-slate-500">DMZ IP</dt><dd className="mono">{v.wanIp ?? '—'}</dd>
            <dt className="text-slate-500">Resources</dt><dd>{v.cpuLimit} vCPU · {v.memoryLimitMb} MiB</dd>
            <dt className="text-slate-500">Default policy</dt><dd><StatusBadge value={v.defaultAction === 'DROP' ? 'DENY' : 'ALLOW'} /> {v.defaultAction}</dd>
            <dt className="text-slate-500">Policy sync</dt><dd><StatusBadge value={v.syncStatus} /> <span className="mono text-xs text-slate-500">r{v.appliedRevision}/{v.rulesRevision}</span></dd>
            <dt className="text-slate-500">Created</dt><dd>{dateTime(v.createdAt)} by {v.createdBy}</dd>
            <dt className="text-slate-500">Description</dt><dd className="text-slate-300">{v.description ?? '—'}</dd>
          </dl>
        </Card>
        <Card title="Current utilisation" actions={<SourceBadge source={v.metricsSource} />} bodyClass="text-sm">
          {v.metricsUpdatedAt ? (
            <dl className="grid grid-cols-[130px_1fr] gap-y-1.5">
              <dt className="text-slate-500">CPU</dt><dd className="tabular-nums">{pct(v.cpuPercent)}</dd>
              <dt className="text-slate-500">Memory</dt><dd className="tabular-nums">{pct(v.memoryPercent)} ({v.memoryUsageMb ?? '—'} MiB)</dd>
              <dt className="text-slate-500">Throughput</dt><dd className="tabular-nums">{bps(v.throughputBps)}</dd>
              <dt className="text-slate-500">RX / TX total</dt><dd className="tabular-nums">{bytes(v.rxBytes)} / {bytes(v.txBytes)}</dd>
              <dt className="text-slate-500">Measured</dt><dd>{dateTime(v.metricsUpdatedAt)}</dd>
            </dl>
          ) : <EmptyState title="No measurement yet" hint={v.orchestrator === 'OSM' ? 'OSM VIM metrics are exposed via OSM MON/Prometheus; traffic counters come from the in-VNF agent.' : 'Metrics are sampled from the Docker Engine while the VNF is ACTIVE.'} />}
        </Card>
        <Card title="Lifecycle history" bodyClass="max-h-72 space-y-2 overflow-y-auto">
          {events.data?.map((e) => (
            <div key={e.id} className="border-l-2 border-ink-600 pl-2 text-sm">
              <div className="flex items-center gap-1.5"><StatusBadge value={e.outcome} /><span className="text-slate-200">{e.operation}</span>
                <span className="text-xs text-slate-500">{e.fromState ?? '∅'} → {e.toState}</span></div>
              <div className="text-[11px] text-slate-500">{dateTime(e.occurredAt)} · {e.actor}</div>
              {e.message && <div className="text-xs break-words text-slate-400">{e.message}</div>}
            </div>
          ))}
        </Card>
      </div>

      <Card title="Resource metrics (last 60 minutes)" actions={<SourceBadge source={metrics.data?.[0]?.dataSource} />}>
        {metrics.data?.length ? (
          <ResponsiveContainer width="100%" height={240}>
            <LineChart data={metrics.data} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
              <CartesianGrid stroke={COLORS.grid} vertical={false} />
              <XAxis dataKey="sampledAt" tickFormatter={timeShort} stroke={COLORS.axis} fontSize={11} minTickGap={30} />
              <YAxis yAxisId="p" stroke={COLORS.axis} fontSize={11} unit="%" width={45} />
              <YAxis yAxisId="t" orientation="right" stroke={COLORS.axis} fontSize={11} width={70} tickFormatter={(x) => bps(x as number)} />
              <Tooltip contentStyle={{ background: '#0f1729', border: '1px solid #2b3a5c', fontSize: 12 }} labelFormatter={(l) => new Date(l as string).toLocaleTimeString()} />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              <Line yAxisId="p" dataKey="cpuPercent" name="CPU %" stroke={COLORS.cpu} dot={false} strokeWidth={2} />
              <Line yAxisId="p" dataKey="memoryPercent" name="Memory %" stroke={COLORS.mem} dot={false} strokeWidth={2} />
              <Line yAxisId="t" dataKey="throughputBps" name="Throughput" stroke={COLORS.allowed} dot={false} strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        ) : <EmptyState title="No samples in the last hour" />}
      </Card>

      <ConfigureModal open={configuring} vnf={v} onClose={() => setConfiguring(false)} onDone={reloadAll} />
      <ScaleModal open={scaling} vnf={v} onClose={() => setScaling(false)} onDone={reloadAll} />
    </div>
  );
}

function ConfigureModal({ open, vnf, onClose, onDone }: { open: boolean; vnf: Vnf; onClose: () => void; onDone: () => void }) {
  const toast = useToast();
  const [description, setDescription] = useState(vnf.description ?? '');
  const [defaultAction, setDefaultAction] = useState(vnf.defaultAction);
  const [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    try {
      await api.patch(`/vnfs/${vnf.id}`, { description, defaultAction });
      toast.success('Configuration saved', defaultAction !== vnf.defaultAction ? 'Default policy change is being pushed to the VNF.' : undefined);
      onDone();
      onClose();
    } catch (e) {
      toast.error('Configuration failed', errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal open={open} title={`Configure ${vnf.name}`} onClose={onClose} footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" loading={busy} onClick={save}>Save</Button></>}>
      <div className="space-y-3">
        <Field label="Description"><input className="input" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} /></Field>
        <Field label="Default policy" hint="Applied to packets matching no rule">
          <select className="input" value={defaultAction} onChange={(e) => setDefaultAction(e.target.value as 'DROP' | 'ACCEPT')}>
            <option value="DROP">DROP (default-deny)</option><option value="ACCEPT">ACCEPT</option>
          </select>
        </Field>
      </div>
    </Modal>
  );
}

function ScaleModal({ open, vnf, onClose, onDone }: { open: boolean; vnf: Vnf; onClose: () => void; onDone: () => void }) {
  const toast = useToast();
  const [direction, setDirection] = useState<'VERTICAL' | 'SCALE_OUT' | 'SCALE_IN'>('VERTICAL');
  const [cpu, setCpu] = useState(String(vnf.cpuLimit));
  const [mem, setMem] = useState(String(vnf.memoryLimitMb));
  const [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    try {
      await api.post(`/vnfs/${vnf.id}/scale`, { direction, cpuLimit: Number(cpu), memoryLimitMb: Number(mem) });
      toast.success('Scaling applied', 'Confirmed by the orchestrator.');
      onDone();
      onClose();
    } catch (e) {
      toast.error('Scaling failed', errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal open={open} title={`Scale ${vnf.name}`} onClose={onClose} footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" loading={busy} onClick={save}>Apply</Button></>}>
      <div className="space-y-3">
        <Field label="Scaling type" hint={vnf.orchestrator === 'DOCKER' ? 'The Docker lab supports vertical scaling; horizontal scaling requires OSM scaling groups.' : 'OSM: vertical (flavor change) or horizontal (VNFD scaling-group)'}>
          <select className="input" value={direction} onChange={(e) => setDirection(e.target.value as typeof direction)}>
            <option value="VERTICAL">Vertical – change vCPU / memory</option>
            <option value="SCALE_OUT">Scale out (+1 instance)</option>
            <option value="SCALE_IN">Scale in (−1 instance)</option>
          </select>
        </Field>
        {direction === 'VERTICAL' && (
          <div className="grid grid-cols-2 gap-3">
            <Field label="vCPU"><input className="input" type="number" step="0.25" min="0.25" max="4" value={cpu} onChange={(e) => setCpu(e.target.value)} /></Field>
            <Field label="Memory (MiB)"><input className="input" type="number" step="64" min="128" max="4096" value={mem} onChange={(e) => setMem(e.target.value)} /></Field>
          </div>
        )}
      </div>
    </Modal>
  );
}
