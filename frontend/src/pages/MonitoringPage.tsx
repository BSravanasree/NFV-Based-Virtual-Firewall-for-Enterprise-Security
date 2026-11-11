import { Download, Search, X } from 'lucide-react';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, download, errorMessage } from '../api/client';
import type { Page, TimePoint, TopTalkers, TrafficEvent, TrafficSummary } from '../api/types';
import { COLORS, DonutChart, HBarChart, ThroughputChart, TrafficChart } from '../components/charts';
import { useToast } from '../components/Toast';
import { Banner, Button, Card, EmptyState, ErrorState, PageHeader, Pager, SourceBadge, Spinner, StatCard, StatusBadge } from '../components/ui';
import { useApi } from '../hooks/useApi';
import { bytes, dateTime, num } from '../utils/format';

interface Filters { action: string; protocol: string; srcIp: string; dstIp: string; port: string; direction: string; from: string; to: string; vnfId: string }

export default function MonitoringPage() {
  const [params] = useSearchParams();
  const toast = useToast();
  const [minutes, setMinutes] = useState(60);
  const summary = useApi(() => api.get<TrafficSummary>('/traffic/summary', { params: { minutes } }).then((r) => r.data), [minutes], 15000);
  const series = useApi(() => api.get<TimePoint[]>('/traffic/timeseries', { params: { minutes, bucketSeconds: minutes <= 60 ? 60 : minutes <= 360 ? 300 : 900 } }).then((r) => r.data), [minutes], 15000);
  const top = useApi(() => api.get<TopTalkers>('/traffic/top', { params: { minutes, limit: 8 } }).then((r) => r.data), [minutes], 20000);

  const initial: Filters = { action: params.get('action') ?? '', protocol: '', srcIp: params.get('srcIp') ?? '', dstIp: '', port: '', direction: '', from: '', to: '', vnfId: params.get('vnfId') ?? '' };
  const [draft, setDraft] = useState<Filters>(initial);
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(0);
  const query = () => {
    const q: Record<string, string> = {};
    Object.entries(filters).forEach(([k, v]) => {
      if (!v) return;
      q[k] = k === 'from' || k === 'to' ? new Date(v).toISOString() : v;
    });
    return q;
  };
  const events = useApi(() => api.get<Page<TrafficEvent>>('/traffic/events', { params: { ...query(), page, size: 25 } }).then((r) => r.data), [filters, page], 15000);

  const s = summary.data;
  const exportCsv = async () => {
    try { await download('/traffic/events/export', query(), 'traffic-events.csv'); toast.success('CSV export downloaded'); }
    catch (e) { toast.error('Export failed', errorMessage(e)); }
  };

  return (
    <div>
      <PageHeader title="Network Traffic Monitoring" subtitle="nftables counters (exact packet/byte totals) and NFLOG connection events (header metadata, no payload inspection)"
        actions={<select className="input w-40" value={minutes} onChange={(e) => setMinutes(Number(e.target.value))} aria-label="Time window">
          <option value={15}>Last 15 min</option><option value={60}>Last hour</option><option value={360}>Last 6 h</option><option value={1440}>Last 24 h</option><option value={10080}>Last 7 days</option>
        </select>} />
      {s && s.samples === 0 && <Banner tone="info">No counter samples in this window. Deploy a firewall VNF and generate traffic in the lab (see docs/DEMO_SCENARIO.md).</Banner>}

      {summary.error ? <ErrorState message={summary.error} /> : !s ? <Spinner /> : (
        <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4 xl:grid-cols-8">
          <StatCard label="Allowed pkts" value={num(s.allowedPackets)} tone="good" sub={bytes(s.allowedBytes)} />
          <StatCard label="Blocked pkts" value={num(s.blockedPackets)} tone="bad" sub={bytes(s.blockedBytes)} />
          <StatCard label="Inbound" value={bytes(s.inboundBytes)} sub="DMZ → LAN" />
          <StatCard label="Outbound" value={bytes(s.outboundBytes)} sub="LAN → DMZ" />
          <StatCard label="TCP pkts" value={num(s.tcpPackets)} />
          <StatCard label="UDP pkts" value={num(s.udpPackets)} />
          <StatCard label="ICMP pkts" value={num(s.icmpPackets)} />
          <StatCard label="Data source" value={<div className="flex gap-1">{s.dataSources.length ? s.dataSources.map((d) => <SourceBadge key={d} source={d} />) : <SourceBadge source={null} />}</div>} sub={`${s.samples} samples`} />
        </div>
      )}

      <div className="mb-4 grid gap-4 xl:grid-cols-2">
        <Card title="Allowed vs blocked packets">{series.error ? <ErrorState message={series.error} /> : <TrafficChart points={series.data ?? []} />}</Card>
        <Card title="Firewall throughput (inbound + outbound)">{series.error ? <ErrorState message={series.error} /> : <ThroughputChart points={series.data ?? []} height={240} />}</Card>
      </div>
      <div className="mb-4 grid gap-4 lg:grid-cols-2 xl:grid-cols-4">
        <Card title="Protocol distribution (events)"><DonutChart data={(top.data?.protocols ?? []).map((p) => ({ name: p.key, value: p.count, color: p.key === 'TCP' ? COLORS.tcp : p.key === 'UDP' ? COLORS.udp : p.key === 'ICMP' ? COLORS.icmp : '#94a3b8' }))} /></Card>
        <Card title="Top blocked sources"><HBarChart data={top.data?.blockedSources ?? []} color={COLORS.blocked} label="Blocked events" /></Card>
        <Card title="Top destinations"><HBarChart data={top.data?.destinations ?? []} color={COLORS.inbound} label="Events" /></Card>
        <Card title="Top destination ports"><HBarChart data={top.data?.destinationPorts ?? []} color={COLORS.udp} label="Events" /></Card>
      </div>

      <Card title="Connection event log" bodyClass="p-0" actions={<Button icon={<Download className="h-4 w-4" />} onClick={exportCsv}>Export CSV</Button>}>
        <form className="grid grid-cols-2 gap-2 border-b border-ink-700 p-3 md:grid-cols-5 xl:grid-cols-10" onSubmit={(e) => { e.preventDefault(); setPage(0); setFilters(draft); }}>
          <select className="input" value={draft.action} onChange={(e) => setDraft({ ...draft, action: e.target.value })} aria-label="Action"><option value="">Any action</option><option>ALLOWED</option><option>BLOCKED</option></select>
          <select className="input" value={draft.protocol} onChange={(e) => setDraft({ ...draft, protocol: e.target.value })} aria-label="Protocol"><option value="">Any protocol</option><option>TCP</option><option>UDP</option><option>ICMP</option></select>
          <select className="input" value={draft.direction} onChange={(e) => setDraft({ ...draft, direction: e.target.value })} aria-label="Direction"><option value="">Any direction</option><option>INBOUND</option><option>OUTBOUND</option></select>
          <input className="input mono" placeholder="Source IP" value={draft.srcIp} onChange={(e) => setDraft({ ...draft, srcIp: e.target.value.trim() })} />
          <input className="input mono" placeholder="Destination IP" value={draft.dstIp} onChange={(e) => setDraft({ ...draft, dstIp: e.target.value.trim() })} />
          <input className="input mono" placeholder="Port" value={draft.port} onChange={(e) => setDraft({ ...draft, port: e.target.value.replace(/\D/g, '') })} />
          <input className="input" type="datetime-local" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} aria-label="From" />
          <input className="input" type="datetime-local" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} aria-label="To" />
          <Button type="submit" variant="primary" icon={<Search className="h-4 w-4" />}>Search</Button>
          <Button type="button" icon={<X className="h-4 w-4" />} onClick={() => { const c = { ...initial, action: '', vnfId: '', srcIp: '' }; setDraft(c); setFilters(c); setPage(0); }}>Clear</Button>
        </form>
        {events.loading && !events.data ? <Spinner /> : events.error ? <div className="p-3"><ErrorState message={events.error} /></div> : !events.data?.content.length ? <EmptyState title="No events match the filter" /> : (
          <>
            <div className="overflow-x-auto"><table className="w-full">
              <thead><tr><th className="th">Time</th><th className="th">Action</th><th className="th">Proto</th><th className="th">Source</th><th className="th">Destination</th><th className="th">Direction</th><th className="th">Rule</th><th className="th">Len</th><th className="th">VNF</th><th className="th">Source</th></tr></thead>
              <tbody>{events.data.content.map((e) => (
                <tr key={e.id} className="hover:bg-ink-800/40">
                  <td className="td text-xs whitespace-nowrap">{dateTime(e.occurredAt)}</td>
                  <td className="td"><StatusBadge value={e.action} /></td>
                  <td className="td text-xs">{e.protocol}</td>
                  <td className="td mono text-xs">{e.srcIp}{e.srcPort != null ? `:${e.srcPort}` : ''}</td>
                  <td className="td mono text-xs">{e.dstIp}{e.dstPort != null ? `:${e.dstPort}` : ''}</td>
                  <td className="td text-xs">{e.direction}</td>
                  <td className="td text-xs">{e.ruleId ?? 'default'}</td>
                  <td className="td text-xs tabular-nums">{e.packetLength ?? '—'}</td>
                  <td className="td text-xs">{e.vnfId}</td>
                  <td className="td"><SourceBadge source={e.dataSource} /></td>
                </tr>))}
              </tbody>
            </table></div>
            <Pager page={events.data.page} totalPages={events.data.totalPages} total={events.data.totalElements} onPage={setPage} />
          </>
        )}
      </Card>
    </div>
  );
}
