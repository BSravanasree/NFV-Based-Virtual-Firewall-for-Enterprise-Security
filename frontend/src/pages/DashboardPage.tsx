import {
  Activity, Ban, Boxes, CheckCircle2, Gauge, HeartPulse, RefreshCw, Server, ShieldAlert, ShieldCheck,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { useMemo, useState } from 'react';
import { api } from '../api/client';
import type { Alert, AuditLog, DashboardSummary, LifecycleEvent, Page, TimePoint, Vnf } from '../api/types';
import {
  COLORS, DonutChart, InOutChart, ResourceChart, SecurityEventsChart, TrafficChart, type SeverityBucket,
} from '../components/charts';
import { AnimatedNumber } from '../components/effects';
import { Badge, Button, Card, ErrorState, PageHeader, SourceBadge, Spinner, StatCard, StatusBadge, cx } from '../components/ui';
import { useApi } from '../hooks/useApi';
import { ago, bps, pct } from '../utils/format';

const postureStyle: Record<string, string> = {
  SECURE: 'border-emerald-400/30 from-emerald-400/10 text-emerald-200',
  DEGRADED: 'border-amber-400/30 from-amber-400/10 text-amber-100',
  AT_RISK: 'border-orange-400/30 from-orange-400/10 text-orange-100',
  CRITICAL: 'border-rose-400/40 from-rose-500/15 text-rose-100',
  UNPROTECTED: 'border-rose-400/30 from-rose-500/10 text-rose-100',
};

/** Buckets alert detections (by first-seen time) into the dashboard window. */
export function bucketAlerts(alerts: Alert[], minutes: number, now = Date.now()): SeverityBucket[] {
  const bucketMs = (minutes <= 60 ? 5 : minutes <= 360 ? 30 : 120) * 60_000;
  const start = Math.floor((now - minutes * 60_000) / bucketMs) * bucketMs;
  const out: SeverityBucket[] = [];
  for (let t = start; t <= now; t += bucketMs) out.push({ t: new Date(t).toISOString(), CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 });
  for (const a of alerts) {
    const ts = new Date(a.firstSeen).getTime();
    const idx = Math.floor((ts - start) / bucketMs);
    if (idx >= 0 && idx < out.length) out[idx][a.severity] += 1;
  }
  return out;
}

export default function DashboardPage() {
  const [minutes, setMinutes] = useState(60);
  const summary = useApi(() => api.get<DashboardSummary>('/dashboard/summary', { params: { minutes } }).then((r) => r.data), [minutes], 15000);
  const series = useApi(() => api.get<TimePoint[]>('/traffic/timeseries', { params: { minutes, bucketSeconds: minutes <= 60 ? 60 : 300 } }).then((r) => r.data), [minutes], 15000);
  const vnfs = useApi(() => api.get<Vnf[]>('/vnfs').then((r) => r.data), [], 15000);
  const alerts = useApi(() => api.get<Page<Alert>>('/alerts', { params: { from: new Date(Date.now() - minutes * 60_000).toISOString(), size: 200 } }).then((r) => r.data), [minutes], 30000);
  const activity = useApi(() => api.get<{ lifecycle: LifecycleEvent[]; alerts: Alert[]; audit: AuditLog[] }>('/dashboard/activity').then((r) => r.data), [], 20000);

  const eventBuckets = useMemo(() => bucketAlerts(alerts.data?.content ?? [], minutes), [alerts.data, minutes]);
  const resources = useMemo(() => (vnfs.data ?? [])
    .filter((v) => v.state === 'ACTIVE' && v.cpuPercent != null)
    .map((v) => ({ name: v.name, cpu: Number(v.cpuPercent ?? 0), mem: Number(v.memoryPercent ?? 0) })), [vnfs.data]);

  if (summary.loading && !summary.data) return <Spinner />;
  if (summary.error && !summary.data) return <ErrorState message={summary.error} onRetry={() => summary.reload()} />;
  const s = summary.data!;
  const t = s.traffic;
  const onos = s.integrations.find((c) => c.type === 'ONOS');
  const healthy = s.vnfs.byHealth.HEALTHY;
  const reloadAll = () => { void summary.reload(); void series.reload(); void vnfs.reload(); void alerts.reload(); void activity.reload(); };

  return (
    <div>
      <PageHeader
        eyebrow="Security operations center"
        title="Overview"
        subtitle={<>Live posture of your virtual firewalls · refreshed every 15 s · updated {ago(s.generatedAt)}</>}
        actions={<>
          <select className="input w-40" value={minutes} onChange={(e) => setMinutes(Number(e.target.value))} aria-label="Time window">
            <option value={15}>Last 15 minutes</option>
            <option value={60}>Last hour</option>
            <option value={360}>Last 6 hours</option>
            <option value={1440}>Last 24 hours</option>
          </select>
          <Button icon={<RefreshCw className="h-4 w-4" />} onClick={reloadAll}>Refresh</Button>
        </>}
      />

      <div className={cx('mb-5 flex flex-wrap items-center gap-3 rounded-2xl border bg-gradient-to-r to-transparent px-5 py-3.5 backdrop-blur', postureStyle[s.posture.level])}>
        {s.posture.level === 'SECURE' ? <ShieldCheck className="h-5 w-5" /> : <ShieldAlert className="h-5 w-5" />}
        <div className="font-semibold">Security posture: {s.posture.level.replace('_', ' ')}</div>
        <div className="text-sm opacity-90">{s.posture.reasons.join(' · ')}</div>
      </div>

      <div className="mb-5 grid grid-cols-2 gap-3 md:grid-cols-4">
        <StatCard label="Total VNFs" value={<AnimatedNumber value={s.vnfs.total} />} icon={<Boxes className="h-4 w-4" />}
          sub={`${s.vnfs.inactive} inactive · ${s.vnfs.failed} failed`} tone={s.vnfs.failed ? 'bad' : 'default'} />
        <StatCard label="Active VNFs" value={<AnimatedNumber value={s.vnfs.active} />} tone="good" icon={<CheckCircle2 className="h-4 w-4" />}
          sub={`${s.vnfs.transitional} in transition`} />
        <StatCard label="Blocked connections" value={<AnimatedNumber value={t.blockedPackets} />} tone="bad" icon={<Ban className="h-4 w-4" />}
          sub={`packets · ${t.blockedEvents.toLocaleString()} block events logged`} />
        <StatCard label="Allowed connections" value={<AnimatedNumber value={t.allowedPackets} />} tone="good" icon={<Activity className="h-4 w-4" />}
          sub={`packets · ${t.allowedConnectionEvents.toLocaleString()} new connections logged`} />
        <StatCard label="Security alerts" value={<AnimatedNumber value={s.alerts.open + s.alerts.acknowledged} />}
          tone={s.alerts.activeBySeverity.CRITICAL ? 'bad' : s.alerts.open ? 'warn' : 'good'} icon={<ShieldAlert className="h-4 w-4" />}
          sub={`${s.alerts.activeBySeverity.CRITICAL} critical · ${s.alerts.activeBySeverity.HIGH} high`} />
        <StatCard label="Network throughput" value={s.vnfs.measuredVnfs ? <AnimatedNumber value={s.vnfs.throughputBps} format={(n) => bps(n)} /> : '—'}
          tone="accent" icon={<Gauge className="h-4 w-4" />} sub={s.vnfs.measuredVnfs ? `measured on ${s.vnfs.measuredVnfs} VNF(s)` : 'no measured VNFs'} />
        <StatCard label="Firewall health" value={s.vnfs.active ? `${healthy} / ${s.vnfs.active}` : '—'}
          tone={!s.vnfs.active ? 'default' : healthy === s.vnfs.active ? 'good' : 'warn'} icon={<HeartPulse className="h-4 w-4" />}
          sub={s.vnfs.active ? `healthy · CPU ${pct(s.vnfs.avgCpuPercent)} · mem ${pct(s.vnfs.avgMemoryPercent)}` : 'no active firewall'} />
        <StatCard label="SDN controller" value={onos ? <span className="flex items-center gap-2 text-lg"><StatusBadge value={onos.status} />{onos.mode === 'DEMO' && <Badge tone="violet">DEMO</Badge>}</span> : '—'}
          icon={<Server className="h-4 w-4" />} sub={onos?.mode === 'DEMO' ? 'fixture data – no controller connected' : onos?.endpoint ?? 'not checked yet'} />
      </div>

      <div className="mb-5 grid gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2" title="Allowed vs blocked packets" actions={
          <div className="flex gap-1">{t.dataSources.length ? t.dataSources.map((d) => <SourceBadge key={d} source={d} />) : <SourceBadge source={null} />}</div>
        }>
          {series.error ? <ErrorState message={series.error} /> : <TrafficChart points={series.data ?? []} />}
        </Card>
        <Card title="Traffic by protocol" actions={<span className="text-[11px] text-slate-500">nftables counters</span>}>
          <DonutChart height={240} data={[
            { name: 'TCP', value: t.tcpPackets, color: COLORS.tcp },
            { name: 'UDP', value: t.udpPackets, color: COLORS.udp },
            { name: 'ICMP', value: t.icmpPackets, color: COLORS.icmp },
          ]} />
        </Card>
      </div>

      <div className="mb-5 grid gap-4 xl:grid-cols-3">
        <Card title="Incoming vs outgoing traffic" actions={<span className="text-[11px] text-slate-500">bytes per interval</span>}>
          {series.error ? <ErrorState message={series.error} /> : <InOutChart points={series.data ?? []} />}
        </Card>
        <Card title="Security events over time" actions={<Link to="/alerts" className="text-xs text-accent-300 hover:underline">Alerts →</Link>}>
          {alerts.error ? <ErrorState message={alerts.error} /> : <SecurityEventsChart data={eventBuckets} />}
        </Card>
        <Card title="VNF resource utilization" actions={<SourceBadge source={(vnfs.data ?? []).find((v) => v.metricsSource)?.metricsSource} />}>
          {vnfs.error ? <ErrorState message={vnfs.error} /> : <ResourceChart data={resources} />}
        </Card>
      </div>

      <div className="mb-5 grid gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2" title="VNF health" actions={<Link to="/vnfs" className="text-xs text-accent-300 hover:underline">Manage VNFs →</Link>} bodyClass="p-0">
          {vnfs.data?.some((v) => v.state !== 'TERMINATED') ? (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px]">
                <thead><tr><th className="th">VNF</th><th className="th">State</th><th className="th">Health</th><th className="th">Policy</th><th className="th">CPU</th><th className="th">Memory</th><th className="th">Throughput</th><th className="th">Source</th></tr></thead>
                <tbody>
                  {vnfs.data.filter((v) => v.state !== 'TERMINATED').map((v) => (
                    <tr key={v.id} className="hover:bg-white/[0.03]">
                      <td className="td"><Link className="font-medium text-slate-100 hover:text-accent-300" to={`/vnfs/${v.id}`}>{v.name}</Link><div className="mono text-[11px] text-slate-500">{v.vnfId}</div></td>
                      <td className="td"><StatusBadge value={v.state} /></td>
                      <td className="td"><StatusBadge value={v.health} /></td>
                      <td className="td"><StatusBadge value={v.syncStatus} /> <span className="mono text-[11px] text-slate-500">r{v.appliedRevision}/{v.rulesRevision}</span></td>
                      <td className="td tabular-nums">{pct(v.cpuPercent)}</td>
                      <td className="td tabular-nums">{pct(v.memoryPercent)}</td>
                      <td className="td tabular-nums">{bps(v.throughputBps)}</td>
                      <td className="td"><SourceBadge source={v.metricsSource} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : <div className="p-5 text-sm text-slate-400">No VNFs registered yet. <Link to="/vnfs" className="text-accent-300">Create one →</Link></div>}
        </Card>
        <Card title="Integrations" bodyClass="space-y-3">
          {s.integrations.length === 0 && <div className="text-sm text-slate-500">Health checks pending…</div>}
          {s.integrations.map((c) => (
            <div key={c.type + c.name} className="flex items-center justify-between gap-2 text-sm">
              <div className="min-w-0">
                <div className="font-medium text-slate-200">{c.type} <span className="text-slate-500">· {c.name}</span></div>
                <div className="truncate text-[11px] text-slate-500" title={c.lastError ?? c.details}>{c.lastError ?? c.details ?? c.endpoint}</div>
              </div>
              <div className="flex shrink-0 items-center gap-1">
                {c.mode === 'DEMO' && <Badge tone="violet">DEMO</Badge>}
                <StatusBadge value={c.status} />
              </div>
            </div>
          ))}
        </Card>
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <Card title="Security event timeline" actions={<Link to="/alerts" className="text-xs text-accent-300 hover:underline">All alerts →</Link>}>
          <Timeline empty="No alerts raised." items={(activity.data?.alerts ?? []).map((a) => ({
            key: a.id, badge: a.severity, title: <>{a.eventType} · <span className="mono">{a.sourceIp}</span></>, sub: `${ago(a.lastSeen)} · ${a.status}`,
          }))} />
        </Card>
        <Card title="VNF lifecycle activity">
          <Timeline empty="No lifecycle operations yet." items={(activity.data?.lifecycle ?? []).slice(0, 8).map((e) => ({
            key: e.id, badge: e.outcome, title: <>{e.operation} → {e.toState}</>, sub: `${ago(e.occurredAt)} · ${e.actor} · ${e.message ?? ''}`,
          }))} />
        </Card>
        <Card title="Recent administrative activity">
          <Timeline empty="No audit records visible for your role." items={(activity.data?.audit ?? []).map((a) => ({
            key: a.id, badge: a.outcome, title: a.action.replace(/_/g, ' ').toLowerCase(), sub: `${ago(a.occurredAt)} · ${a.username} · ${a.details ?? ''}`,
          }))} />
        </Card>
      </div>
    </div>
  );
}

function Timeline({ items, empty }: { items: { key: number; badge: string; title: React.ReactNode; sub: string }[]; empty: string }) {
  if (!items.length) return <div className="text-sm text-slate-500">{empty}</div>;
  return (
    <ol className="relative space-y-3 border-l border-white/10 pl-4">
      {items.map((i) => (
        <li key={i.key} className="relative">
          <span className="absolute top-1.5 -left-[21px] h-2.5 w-2.5 rounded-full border-2 border-[#10182a] bg-[#6c8cff]" />
          <div className="flex items-center gap-2 text-sm"><StatusBadge value={i.badge} /><span className="min-w-0 truncate text-slate-200">{i.title}</span></div>
          <div className="mt-0.5 truncate text-[11px] text-slate-500">{i.sub}</div>
        </li>
      ))}
    </ol>
  );
}
