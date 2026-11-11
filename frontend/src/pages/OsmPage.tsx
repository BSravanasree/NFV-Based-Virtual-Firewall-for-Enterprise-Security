import { RefreshCw } from 'lucide-react';
import type { ReactNode } from 'react';
import { api } from '../api/client';
import { Banner, Button, Card, EmptyState, ErrorState, PageHeader, Spinner, StatCard, StatusBadge } from '../components/ui';
import { useApi } from '../hooks/useApi';
import { IntegrationPanel } from '../components/IntegrationPanel';

interface OsmStatus {
  configured: boolean; activeOrchestratorMode: string; endpoint?: string; project: string; vimAccount?: string;
  nsdId: string; reachable: boolean; latencyMs: number; detail?: string;
}
type Row = Record<string, string | number>;

function Table({ title, path, enabled, columns }: { title: string; path: string; enabled: boolean; columns: { key: string; label: string; render?: (r: Row) => ReactNode }[] }) {
  const q = useApi(() => (enabled ? api.get<Row[]>(path).then((r) => r.data) : Promise.resolve([] as Row[])), [enabled], enabled ? 30000 : undefined);
  return (
    <Card title={title} bodyClass="p-0">
      {!enabled ? <EmptyState title="OSM not reachable" /> : q.loading && !q.data ? <Spinner /> : q.error ? <div className="p-3"><ErrorState message={q.error} onRetry={() => q.reload()} /></div>
        : !q.data?.length ? <EmptyState title="Nothing found" /> : (
          <div className="overflow-x-auto"><table className="w-full">
            <thead><tr>{columns.map((c) => <th key={c.key} className="th">{c.label}</th>)}</tr></thead>
            <tbody>{q.data.map((r, i) => <tr key={i}>{columns.map((c) => <td key={c.key} className="td text-xs">{c.render ? c.render(r) : String(r[c.key] ?? '—')}</td>)}</tr>)}</tbody>
          </table></div>
        )}
    </Card>
  );
}

const ts = (r: Row, k: string) => (r[k] ? new Date(Number(r[k]) * 1000).toLocaleString() : '—');

export default function OsmPage() {
  const st = useApi(() => api.get<OsmStatus>('/osm/status').then((r) => r.data), [], 30000);
  const s = st.data;
  const ok = !!s?.reachable;
  return (
    <div>
      <PageHeader title="OSM Orchestration" subtitle="Open Source MANO northbound interface: VIMs, VNF packages, NS descriptors and NS instances"
        actions={<Button icon={<RefreshCw className="h-4 w-4" />} onClick={() => st.reload()}>Refresh</Button>} />
      <IntegrationPanel type="OSM" title="OSM NBI" />
      {st.loading && !s ? <Spinner /> : st.error ? <ErrorState message={st.error} /> : s && <>
        {!s.configured && (
          <Banner tone="info">
            <b>OSM is not configured.</b> Set <span className="mono">OSM_NBI_URL</span>, <span className="mono">OSM_PASSWORD</span> and <span className="mono">OSM_VIM_ACCOUNT</span>, onboard the packages in <span className="mono">osm/</span>,
            and set <span className="mono">NEXUS_ORCHESTRATOR_MODE=osm</span> to deploy VNFs through OSM. The platform currently uses the <b>{s.activeOrchestratorMode}</b> orchestrator.
            No OSM data is simulated.
          </Banner>
        )}
        {s.configured && !s.reachable && <Banner tone="warn"><b>OSM NBI unreachable:</b> {s.detail}</Banner>}
        <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-5">
          <StatCard label="NBI status" value={<StatusBadge value={!s.configured ? 'DISABLED' : s.reachable ? 'UP' : 'DOWN'} />} sub={s.endpoint || 'not set'} />
          <StatCard label="Version" value={<span className="text-base">{ok ? s.detail : '—'}</span>} sub={ok ? `${s.latencyMs} ms` : undefined} />
          <StatCard label="Project" value={<span className="text-base">{s.project}</span>} />
          <StatCard label="VIM account" value={<span className="text-base">{s.vimAccount || '—'}</span>} />
          <StatCard label="Active orchestrator" value={<span className="text-base">{s.activeOrchestratorMode.toUpperCase()}</span>} sub={`NSD ${s.nsdId}`} />
        </div>
        <div className="grid gap-4 xl:grid-cols-2">
          <Table title="VIM accounts" path="/osm/vim-accounts" enabled={ok} columns={[
            { key: 'name', label: 'Name' }, { key: 'type', label: 'Type' }, { key: 'url', label: 'URL' },
            { key: 'state', label: 'State', render: (r) => <StatusBadge value={String(r.state)} /> }]} />
          <Table title="VNF packages" path="/osm/vnf-packages" enabled={ok} columns={[
            { key: 'name', label: 'Name' }, { key: 'version', label: 'Version' }, { key: 'provider', label: 'Provider' },
            { key: 'onboardingState', label: 'Onboarding' }]} />
          <Table title="NS descriptors" path="/osm/ns-descriptors" enabled={ok} columns={[
            { key: 'name', label: 'Name' }, { key: 'version', label: 'Version' }, { key: 'onboardingState', label: 'Onboarding' }]} />
          <Table title="NS instances" path="/osm/ns-instances" enabled={ok} columns={[
            { key: 'name', label: 'Name' }, { key: 'nsState', label: 'NS state', render: (r) => <StatusBadge value={String(r.nsState)} /> },
            { key: 'operationalStatus', label: 'Operational' }, { key: 'created', label: 'Created', render: (r) => ts(r, 'created') }]} />
          <div className="xl:col-span-2">
            <Table title="LCM operations" path="/osm/operations" enabled={ok} columns={[
              { key: 'operation', label: 'Operation' }, { key: 'state', label: 'State', render: (r) => <StatusBadge value={String(r.state)} /> },
              { key: 'nsInstanceId', label: 'NS instance' }, { key: 'startTime', label: 'Started', render: (r) => ts(r, 'startTime') },
              { key: 'detail', label: 'Detail' }]} />
          </div>
        </div>
      </>}
    </div>
  );
}
