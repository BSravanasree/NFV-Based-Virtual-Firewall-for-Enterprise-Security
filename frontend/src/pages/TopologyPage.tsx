import { Laptop, Network, RefreshCw, Server, Shield, Router, Cpu, Box } from 'lucide-react';
import { useMemo, useState } from 'react';
import { api } from '../api/client';
import type { TopoEdge, TopoNode, Topology } from '../api/types';
import { Banner, Button, Card, EmptyState, ErrorState, PageHeader, SourceBadge, Spinner, StatusBadge } from '../components/ui';
import { useApi } from '../hooks/useApi';

const LAYER: Record<TopoNode['type'], number> = { CONTROLLER: 0, SEGMENT: 1, SWITCH: 1, FIREWALL: 2, CLIENT: 3, SERVER: 3, HOST: 3 };
const COLOR: Record<TopoNode['type'], string> = {
  CONTROLLER: '#a78bfa', SWITCH: '#38d9ff', SEGMENT: '#38d9ff', FIREWALL: '#6c8cff', CLIENT: '#34d399', SERVER: '#fbbf24', HOST: '#94a3b8',
};
const ICON: Record<TopoNode['type'], typeof Server> = {
  CONTROLLER: Cpu, SWITCH: Router, SEGMENT: Network, FIREWALL: Shield, CLIENT: Laptop, SERVER: Server, HOST: Box,
};
const EDGE_STYLE: Record<TopoEdge['type'], { color: string; dash?: string }> = {
  CONTROL: { color: '#a78bfa', dash: '5 4' }, LINK: { color: '#38d9ff' }, ACCESS: { color: '#6c8cff' }, ATTACHMENT: { color: '#6c8cff' },
};

export interface Positioned extends TopoNode { x: number; y: number }

/** Deterministic layered layout: one row per layer, nodes spread evenly and sorted by label. */
export function layout(nodes: TopoNode[], width: number, rowGap = 130): { nodes: Positioned[]; height: number } {
  const layers = new Map<number, TopoNode[]>();
  nodes.forEach((n) => {
    const l = LAYER[n.type] ?? 3;
    layers.set(l, [...(layers.get(l) ?? []), n]);
  });
  const used = [...layers.keys()].sort((a, b) => a - b);
  const out: Positioned[] = [];
  used.forEach((l, row) => {
    const list = (layers.get(l) ?? []).sort((a, b) => a.label.localeCompare(b.label));
    const step = width / (list.length + 1);
    list.forEach((n, i) => out.push({ ...n, x: step * (i + 1), y: 60 + row * rowGap }));
  });
  return { nodes: out, height: 60 + Math.max(used.length - 1, 0) * rowGap + 80 };
}

export default function TopologyPage() {
  const [view, setView] = useState<'sdn' | 'nfv'>('nfv');
  const topo = useApi(() => api.get<Topology>('/sdn/topology', { params: { view } }).then((r) => r.data), [view], 15000);
  const [selected, setSelected] = useState<string | null>(null);
  const [hover, setHover] = useState<string | null>(null);
  const width = 1000;

  const placed = useMemo(() => layout(topo.data?.nodes ?? [], width), [topo.data]);
  const byId = useMemo(() => new Map(placed.nodes.map((n) => [n.id, n])), [placed]);
  const focus = hover ?? selected;
  const sel = selected ? byId.get(selected) : undefined;
  const connected = (topo.data?.edges ?? []).filter((e) => sel && (e.source === sel.id || e.target === sel.id));

  return (
    <div>
      <PageHeader title="Network Topology" subtitle="Relationships between controller, switches, firewall VNFs, hosts and links"
        actions={<>
          <div className="flex rounded-md border border-ink-600 p-0.5" role="tablist">
            {(['nfv', 'sdn'] as const).map((v) => (
              <button key={v} role="tab" aria-selected={view === v} onClick={() => { setView(v); setSelected(null); }}
                className={`rounded px-3 py-1 text-sm ${view === v ? 'bg-accent-600/30 text-accent-200' : 'text-slate-400 hover:text-slate-200'}`}>
                {v === 'nfv' ? 'NFV lab (service chain)' : 'SDN / OpenFlow (ONOS)'}
              </button>
            ))}
          </div>
          <Button icon={<RefreshCw className="h-4 w-4" />} onClick={() => topo.reload()}>Refresh</Button>
        </>} />

      {topo.data?.dataSource === 'DEMO' && <Banner tone="demo"><b>DEMO MODE</b> – this SDN topology is a fixture mirroring the reference Mininet lab; no ONOS controller is connected. Set NEXUS_ONOS_MODE=real to use a live controller.</Banner>}

      {topo.loading && !topo.data ? <Spinner /> : topo.error ? <ErrorState message={topo.error} onRetry={() => topo.reload()} /> : !topo.data?.available ? (
        <Card><EmptyState title="Topology unavailable" hint={topo.data?.message} /></Card>
      ) : (
        <>
        {view === 'nfv' && <TrafficPath nodes={topo.data.nodes} />}
        <div className="grid gap-4 xl:grid-cols-[1fr_300px]">
          <Card title={<span className="flex items-center gap-2">{view === 'nfv' ? 'NFV lab segments (Docker Engine inventory)' : 'OpenFlow network (ONOS)'} <SourceBadge source={topo.data.dataSource} /></span>} bodyClass="p-2">
            <svg viewBox={`0 0 ${width} ${placed.height}`} className="w-full" role="img" aria-label="Network topology graph">
              <defs>
                <filter id="topo-glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="6" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
                <radialGradient id="node-fill"><stop offset="0" stopColor="#1a2440" /><stop offset="1" stopColor="#0c1324" /></radialGradient>
              </defs>
              {topo.data.edges.map((e) => {
                const a = byId.get(e.source);
                const b = byId.get(e.target);
                if (!a || !b) return null;
                const st = EDGE_STYLE[e.type];
                const active = focus && (e.source === focus || e.target === focus);
                return (
                  <g key={e.id} opacity={focus && !active ? 0.15 : 1}>
                    <line x1={a.x} y1={a.y} x2={b.x} y2={b.y} stroke={st.color} strokeOpacity={0.18} strokeWidth={active ? 7 : 5} />
                    <line x1={a.x} y1={a.y} x2={b.x} y2={b.y} stroke={st.color} strokeWidth={active ? 2.5 : 1.6} strokeDasharray={st.dash}
                      className={e.type !== 'CONTROL' && a.status === 'UP' && b.status === 'UP' ? 'flow-line' : undefined} />
                    {e.label && <text x={(a.x + b.x) / 2} y={(a.y + b.y) / 2 - 4} fill="#64748b" fontSize="10" textAnchor="middle">{e.label}</text>}
                  </g>
                );
              })}
              {placed.nodes.map((n) => {
                const Icon = ICON[n.type];
                const dim = focus && focus !== n.id && !topo.data!.edges.some((e) => (e.source === focus && e.target === n.id) || (e.target === focus && e.source === n.id));
                return (
                  <g key={n.id} transform={`translate(${n.x},${n.y})`} opacity={dim ? 0.3 : 1} className="cursor-pointer"
                    onClick={() => setSelected(n.id === selected ? null : n.id)} onMouseEnter={() => setHover(n.id)} onMouseLeave={() => setHover(null)}
                    role="button" aria-label={`${n.type} ${n.label}`}>
                    {(n.type === 'FIREWALL' || selected === n.id) && <circle r={30} fill={COLOR[n.type]} opacity={0.18} filter="url(#topo-glow)" />}
                    <circle r={selected === n.id ? 25 : 22} fill="url(#node-fill)" stroke={COLOR[n.type]} strokeWidth={selected === n.id ? 3 : 2} />
                    {n.status !== 'UP' && <circle r={5} cx={17} cy={-17} fill="#ef4444" />}
                    <foreignObject x={-10} y={-10} width={20} height={20}><Icon width={20} height={20} color={COLOR[n.type]} /></foreignObject>
                    <text y={38} fill="#e2e8f0" fontSize="12" textAnchor="middle" fontWeight={600}>{n.label.length > 22 ? `${n.label.slice(0, 21)}…` : n.label}</text>
                    <text y={52} fill="#64748b" fontSize="10" textAnchor="middle">{n.type}</text>
                  </g>
                );
              })}
            </svg>
            <div className="flex flex-wrap gap-3 px-2 pb-1 text-[11px] text-slate-400">
              {(Object.keys(COLOR) as TopoNode['type'][]).filter((t) => placed.nodes.some((n) => n.type === t)).map((t) => (
                <span key={t} className="flex items-center gap-1"><span className="h-2.5 w-2.5 rounded-full" style={{ background: COLOR[t] }} />{t.toLowerCase()}</span>
              ))}
              {view === 'sdn' && <span className="flex items-center gap-1"><span className="w-4 border-t border-dashed border-violet-400" />OpenFlow control channel</span>}
              <span className="flex items-center gap-1"><span className="w-4 border-t-2 border-dashed border-[#6c8cff]" />animated = both endpoints up</span>
              <span className="flex items-center gap-1"><span className="h-2.5 w-2.5 rounded-full bg-rose-500" />node not up</span>
            </div>
          </Card>
          <Card title="Details">
            {!sel ? <div className="text-sm text-slate-500">Select a node to see its details and connections.</div> : (
              <div className="space-y-2 text-sm">
                <div className="text-base font-semibold text-slate-100">{sel.label}</div>
                <div className="flex gap-1.5"><StatusBadge value={sel.type} /><StatusBadge value={sel.status} /><SourceBadge source={sel.dataSource} /></div>
                <div className="mono text-xs break-all text-slate-400">{sel.id}</div>
                {sel.detail && <div className="text-xs text-slate-300">{sel.detail}</div>}
                <div className="label pt-2">Connections ({connected.length})</div>
                <ul className="space-y-1">
                  {connected.map((e) => {
                    const other = byId.get(e.source === sel.id ? e.target : e.source);
                    return <li key={e.id} className="text-xs text-slate-300">{e.type.toLowerCase()} → <span className="text-slate-100">{other?.label}</span> {e.label && <span className="mono text-slate-500">({e.label})</span>}</li>;
                  })}
                </ul>
              </div>
            )}
          </Card>
        </div>
        </>
      )}
    </div>
  );
}

/** Direction of protected traffic through the service chain, derived from the live NFV topology. */
function TrafficPath({ nodes }: { nodes: TopoNode[] }) {
  const by = (t: TopoNode['type']) => nodes.filter((n) => n.type === t);
  const segs = by('SEGMENT');
  const stages: { label: string; items: TopoNode[]; color: string }[] = [
    { label: 'Clients', items: by('CLIENT'), color: COLOR.CLIENT },
    { label: 'LAN segment', items: segs.filter((n) => n.label.startsWith('LAN')), color: COLOR.SEGMENT },
    { label: 'Firewall VNF', items: by('FIREWALL'), color: COLOR.FIREWALL },
    { label: 'DMZ segment', items: segs.filter((n) => n.label.startsWith('DMZ')), color: COLOR.SEGMENT },
    { label: 'Protected servers', items: by('SERVER'), color: COLOR.SERVER },
  ];
  return (
    <div className="panel mb-4 overflow-x-auto p-4" aria-label="Traffic direction">
      <div className="mb-2 text-[11px] font-semibold tracking-wider text-slate-400 uppercase">Traffic direction (LAN → DMZ)</div>
      <div className="flex min-w-[640px] items-stretch gap-2">
        {stages.map((st, i) => (
          <div key={st.label} className="flex flex-1 items-center gap-2">
            <div className="flex-1 rounded-xl border border-white/10 bg-white/[0.03] p-2.5">
              <div className="text-[10px] tracking-wider uppercase" style={{ color: st.color }}>{st.label}</div>
              {st.items.length ? st.items.map((n) => (
                <div key={n.id} className="mt-1 flex items-center gap-1.5 truncate text-xs text-slate-200">
                  <span className={n.status === 'UP' ? 'h-1.5 w-1.5 rounded-full bg-emerald-400' : 'h-1.5 w-1.5 rounded-full bg-rose-400'} />{n.label}
                </div>
              )) : <div className="mt-1 text-xs text-slate-500">{i === 2 ? 'none deployed' : 'none'}</div>}
            </div>
            {i < stages.length - 1 && (
              <svg width="28" height="12" viewBox="0 0 28 12" aria-hidden className="shrink-0">
                <line x1="0" y1="6" x2="22" y2="6" stroke="#6c8cff" strokeWidth="2" className="flow-line" />
                <path d="M20 1 L27 6 L20 11" fill="none" stroke="#6c8cff" strokeWidth="2" />
              </svg>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
