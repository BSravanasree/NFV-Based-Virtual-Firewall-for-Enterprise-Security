import { RefreshCw } from 'lucide-react';
import { useState } from 'react';
import { api } from '../api/client';
import type { ControllerStatus, Device, Host, Link, Port } from '../api/types';
import { Badge, Banner, Button, Card, EmptyState, ErrorState, PageHeader, SourceBadge, Spinner, StatCard, StatusBadge } from '../components/ui';
import { useApi } from '../hooks/useApi';
import { IntegrationPanel } from '../components/IntegrationPanel';
import { bytes, num } from '../utils/format';

export function OnosModeBanner({ status }: { status?: ControllerStatus | null }) {
  if (!status) return null;
  if (status.mode === 'DEMO') {
    return <Banner tone="demo"><b>DEMO MODE</b> – ONOS data below is a labelled fixture (no controller connected). Flow operations only change the in-memory fixture. Configure NEXUS_ONOS_MODE=real with ONOS_URL to manage a live controller.</Banner>;
  }
  if (!status.reachable) return <Banner tone="warn"><b>ONOS unreachable</b> at {status.endpoint}: {status.error}</Banner>;
  return null;
}

export default function OnosPage() {
  const status = useApi(() => api.get<ControllerStatus>('/sdn/status').then((r) => r.data), [], 15000);
  const devices = useApi(() => api.get<Device[]>('/sdn/devices').then((r) => r.data), [], 15000);
  const hosts = useApi(() => api.get<Host[]>('/sdn/hosts').then((r) => r.data), [], 15000);
  const links = useApi(() => api.get<Link[]>('/sdn/links').then((r) => r.data), [], 15000);
  const [device, setDevice] = useState<string | null>(null);
  const ports = useApi(() => (device ? api.get<Port[]>(`/sdn/devices/${encodeURIComponent(device)}/ports`).then((r) => r.data) : Promise.resolve([] as Port[])), [device]);
  const s = status.data;

  const reloadAll = () => { void status.reload(); void devices.reload(); void hosts.reload(); void links.reload(); };

  return (
    <div>
      <PageHeader title="ONOS Controller" subtitle="SDN controller connectivity and network inventory (ONOS northbound REST API)"
        actions={<Button icon={<RefreshCw className="h-4 w-4" />} onClick={reloadAll}>Refresh</Button>} />
      <OnosModeBanner status={s} />
      <IntegrationPanel type="ONOS" title="ONOS controller" />
      {status.loading && !s ? <Spinner /> : status.error && !s ? <ErrorState message={status.error} /> : s && (
        <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-6">
          <StatCard label="Controller" value={<StatusBadge value={s.reachable ? 'UP' : 'DOWN'} />} sub={s.endpoint} />
          <StatCard label="Version" value={<span className="text-base">{s.version ?? '—'}</span>} sub={s.reachable ? `${s.latencyMs} ms` : undefined} />
          <StatCard label="Devices" value={s.devices} />
          <StatCard label="Hosts" value={s.hosts} />
          <StatCard label="Links" value={s.links} />
          <StatCard label="Flow rules" value={s.flows} />
        </div>
      )}
      {s?.activeApps.length ? <div className="mb-4 flex flex-wrap gap-1.5">{s.activeApps.map((a) => <Badge key={a} tone="blue">{a}</Badge>)}</div> : null}

      <div className="grid gap-4 xl:grid-cols-2">
        <Card title="Network devices" bodyClass="p-0" actions={<SourceBadge source={s?.dataSource} />}>
          {devices.error ? <div className="p-3"><ErrorState message={devices.error} /></div> : !devices.data?.length ? <EmptyState title="No devices" /> : (
            <table className="w-full">
              <thead><tr><th className="th">Device</th><th className="th">Status</th><th className="th">Vendor / SW</th><th className="th">Protocol</th><th className="th">Channel</th></tr></thead>
              <tbody>{devices.data.map((d) => (
                <tr key={d.id} onClick={() => setDevice(d.id)} className={`cursor-pointer hover:bg-ink-800/50 ${device === d.id ? 'bg-accent-600/10' : ''}`}>
                  <td className="td"><div className="mono text-xs text-slate-100">{d.id}</div><div className="text-[11px] text-slate-500">{d.name}</div></td>
                  <td className="td"><StatusBadge value={d.available ? 'UP' : 'DOWN'} /></td>
                  <td className="td text-xs">{d.manufacturer}<div className="text-slate-500">{d.hwVersion} {d.swVersion}</div></td>
                  <td className="td text-xs">{d.protocol}</td>
                  <td className="td mono text-xs">{d.channelId}</td>
                </tr>))}
              </tbody>
            </table>
          )}
        </Card>
        <Card title={device ? `Ports of ${device}` : 'Device ports'} bodyClass="p-0">
          {!device ? <EmptyState title="Select a device to view its ports and statistics" /> : ports.loading ? <Spinner /> : ports.error ? <div className="p-3"><ErrorState message={ports.error} /></div> : (
            <table className="w-full">
              <thead><tr><th className="th">Port</th><th className="th">State</th><th className="th">Speed</th><th className="th">RX pkts / bytes</th><th className="th">TX pkts / bytes</th><th className="th">Drops</th></tr></thead>
              <tbody>{ports.data?.map((p) => (
                <tr key={p.port}>
                  <td className="td"><span className="mono">{p.port}</span> <span className="text-xs text-slate-500">{p.name}</span></td>
                  <td className="td"><StatusBadge value={p.enabled ? 'UP' : 'DOWN'} /></td>
                  <td className="td text-xs">{p.speedMbps ? `${p.speedMbps} Mb/s` : '—'}</td>
                  <td className="td text-xs tabular-nums">{num(p.packetsReceived)} / {bytes(p.bytesReceived)}</td>
                  <td className="td text-xs tabular-nums">{num(p.packetsSent)} / {bytes(p.bytesSent)}</td>
                  <td className="td text-xs tabular-nums">{p.rxDropped == null ? '—' : `${p.rxDropped} / ${p.txDropped}`}</td>
                </tr>))}
              </tbody>
            </table>
          )}
        </Card>
        <Card title="Hosts" bodyClass="p-0">
          {hosts.error ? <div className="p-3"><ErrorState message={hosts.error} /></div> : !hosts.data?.length ? <EmptyState title="No hosts discovered" /> : (
            <table className="w-full">
              <thead><tr><th className="th">Host</th><th className="th">IP</th><th className="th">MAC</th><th className="th">Location</th></tr></thead>
              <tbody>{hosts.data.map((h) => (
                <tr key={h.id}><td className="td text-sm">{h.name ?? '—'}</td><td className="td mono text-xs">{h.ipAddresses.join(', ')}</td>
                  <td className="td mono text-xs">{h.mac}</td><td className="td mono text-xs">{h.deviceId}/{h.port}</td></tr>))}
              </tbody>
            </table>
          )}
        </Card>
        <Card title="Links" bodyClass="p-0">
          {links.error ? <div className="p-3"><ErrorState message={links.error} /></div> : !links.data?.length ? <EmptyState title="No inter-switch links" /> : (
            <table className="w-full">
              <thead><tr><th className="th">Source</th><th className="th">Destination</th><th className="th">Type</th><th className="th">State</th></tr></thead>
              <tbody>{links.data.map((l, i) => (
                <tr key={i}><td className="td mono text-xs">{l.srcDevice}/{l.srcPort}</td><td className="td mono text-xs">{l.dstDevice}/{l.dstPort}</td>
                  <td className="td text-xs">{l.type}</td><td className="td"><StatusBadge value={l.state === 'ACTIVE' ? 'UP' : l.state} /></td></tr>))}
              </tbody>
            </table>
          )}
        </Card>
      </div>
    </div>
  );
}
