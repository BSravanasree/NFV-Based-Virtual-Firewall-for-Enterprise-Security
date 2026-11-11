import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import type { TimePoint } from '../api/types';
import { bps, timeShort } from '../utils/format';
import { EmptyState } from './ui';

/** Chart palette = design tokens (index.css). */
export const COLORS = {
  allowed: '#34d399', blocked: '#fb7185', tcp: '#6c8cff', udp: '#a78bfa', icmp: '#38d9ff',
  inbound: '#38d9ff', outbound: '#a78bfa', cpu: '#6c8cff', mem: '#a78bfa', warn: '#fbbf24',
  grid: 'rgba(255,255,255,0.06)', axis: '#64748b',
};

export const tooltipStyle = {
  contentStyle: { background: 'rgba(16,24,42,0.92)', border: '1px solid rgba(255,255,255,0.12)', borderRadius: 10, fontSize: 12, backdropFilter: 'blur(12px)' },
  labelStyle: { color: '#cbd5e1' },
};

export function TrafficChart({ points, height = 240 }: { points: TimePoint[]; height?: number }) {
  if (!points.length) {
    return <EmptyState title="No traffic samples in this window" hint="Samples appear once a firewall VNF is ACTIVE and traffic flows through it." />;
  }
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={points} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
        <defs>
          <linearGradient id="gA" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={COLORS.allowed} stopOpacity={0.35} />
            <stop offset="100%" stopColor={COLORS.allowed} stopOpacity={0} />
          </linearGradient>
          <linearGradient id="gB" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={COLORS.blocked} stopOpacity={0.35} />
            <stop offset="100%" stopColor={COLORS.blocked} stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid stroke={COLORS.grid} vertical={false} />
        <XAxis dataKey="t" tickFormatter={timeShort} stroke={COLORS.axis} fontSize={11} minTickGap={30} />
        <YAxis stroke={COLORS.axis} fontSize={11} width={50} />
        <Tooltip {...tooltipStyle} labelFormatter={(l) => new Date(l as string).toLocaleTimeString()} />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        <Area type="monotone" dataKey="allowedPackets" name="Allowed packets" stroke={COLORS.allowed} fill="url(#gA)" strokeWidth={2} />
        <Area type="monotone" dataKey="blockedPackets" name="Blocked packets" stroke={COLORS.blocked} fill="url(#gB)" strokeWidth={2} />
      </AreaChart>
    </ResponsiveContainer>
  );
}

export function ThroughputChart({ points, height = 220 }: { points: TimePoint[]; height?: number }) {
  if (!points.length) return <EmptyState title="No throughput samples" />;
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={points} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
        <CartesianGrid stroke={COLORS.grid} vertical={false} />
        <XAxis dataKey="t" tickFormatter={timeShort} stroke={COLORS.axis} fontSize={11} minTickGap={30} />
        <YAxis stroke={COLORS.axis} fontSize={11} width={60} tickFormatter={(v) => bps(v as number)} />
        <Tooltip {...tooltipStyle} formatter={(v) => bps(v as number)} labelFormatter={(l) => new Date(l as string).toLocaleTimeString()} />
        <Line type="monotone" dataKey="throughputBps" name="Firewall throughput" stroke={COLORS.inbound} dot={false} strokeWidth={2} />
      </LineChart>
    </ResponsiveContainer>
  );
}

export function DonutChart({ data, height = 200 }: { data: { name: string; value: number; color: string }[]; height?: number }) {
  const total = data.reduce((s, d) => s + d.value, 0);
  if (!total) return <EmptyState title="No data in this window" />;
  return (
    <ResponsiveContainer width="100%" height={height}>
      <PieChart>
        <Pie data={data} dataKey="value" nameKey="name" innerRadius="55%" outerRadius="80%" paddingAngle={2} stroke="none">
          {data.map((d) => <Cell key={d.name} fill={d.color} />)}
        </Pie>
        <Tooltip {...tooltipStyle} />
        <Legend wrapperStyle={{ fontSize: 12 }} />
      </PieChart>
    </ResponsiveContainer>
  );
}

export function HBarChart({ data, color = COLORS.tcp, height = 220, label = 'Count' }: {
  data: { key: string; count: number }[]; color?: string; height?: number; label?: string;
}) {
  if (!data.length) return <EmptyState title="No data in this window" />;
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout="vertical" margin={{ top: 0, right: 10, left: 10, bottom: 0 }}>
        <CartesianGrid stroke={COLORS.grid} horizontal={false} />
        <XAxis type="number" stroke={COLORS.axis} fontSize={11} allowDecimals={false} />
        <YAxis type="category" dataKey="key" stroke={COLORS.axis} fontSize={11} width={110} />
        <Tooltip {...tooltipStyle} />
        <Bar dataKey="count" name={label} fill={color} radius={[0, 3, 3, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}

const bytesTick = (v: number) => (v >= 1048576 ? `${(v / 1048576).toFixed(1)}M` : v >= 1024 ? `${(v / 1024).toFixed(0)}K` : String(v));

/** Inbound (DMZ → LAN) vs outbound (LAN → DMZ) bytes per bucket, from nftables direction counters. */
export function InOutChart({ points, height = 240 }: { points: TimePoint[]; height?: number }) {
  if (!points.length) return <EmptyState title="No direction counters in this window" />;
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={points} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
        <defs>
          <linearGradient id="gIn" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor={COLORS.inbound} stopOpacity={0.4} /><stop offset="100%" stopColor={COLORS.inbound} stopOpacity={0} /></linearGradient>
          <linearGradient id="gOut" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor={COLORS.outbound} stopOpacity={0.4} /><stop offset="100%" stopColor={COLORS.outbound} stopOpacity={0} /></linearGradient>
        </defs>
        <CartesianGrid stroke={COLORS.grid} vertical={false} />
        <XAxis dataKey="t" tickFormatter={timeShort} stroke={COLORS.axis} fontSize={11} minTickGap={30} />
        <YAxis stroke={COLORS.axis} fontSize={11} width={50} tickFormatter={(v) => bytesTick(v as number)} />
        <Tooltip {...tooltipStyle} formatter={(v) => `${bytesTick(v as number)}B`} labelFormatter={(l) => new Date(l as string).toLocaleTimeString()} />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        <Area type="monotone" dataKey="inboundBytes" name="Inbound (DMZ → LAN)" stroke={COLORS.inbound} fill="url(#gIn)" strokeWidth={2} />
        <Area type="monotone" dataKey="outboundBytes" name="Outbound (LAN → DMZ)" stroke={COLORS.outbound} fill="url(#gOut)" strokeWidth={2} />
      </AreaChart>
    </ResponsiveContainer>
  );
}

export interface SeverityBucket { t: string; CRITICAL: number; HIGH: number; MEDIUM: number; LOW: number }

/** Detections per time bucket stacked by severity. */
export function SecurityEventsChart({ data, height = 240 }: { data: SeverityBucket[]; height?: number }) {
  if (!data.some((d) => d.CRITICAL + d.HIGH + d.MEDIUM + d.LOW > 0)) return <EmptyState title="No security events in this window" />;
  const sev: [keyof Omit<SeverityBucket, 't'>, string][] = [['LOW', COLORS.icmp], ['MEDIUM', COLORS.warn], ['HIGH', '#fb923c'], ['CRITICAL', COLORS.blocked]];
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
        <CartesianGrid stroke={COLORS.grid} vertical={false} />
        <XAxis dataKey="t" tickFormatter={timeShort} stroke={COLORS.axis} fontSize={11} minTickGap={30} />
        <YAxis stroke={COLORS.axis} fontSize={11} width={30} allowDecimals={false} />
        <Tooltip {...tooltipStyle} labelFormatter={(l) => new Date(l as string).toLocaleString()} />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        {sev.map(([k, c], i) => <Bar key={k} dataKey={k} stackId="s" fill={c} radius={i === sev.length - 1 ? [3, 3, 0, 0] : undefined} />)}
      </BarChart>
    </ResponsiveContainer>
  );
}

/** CPU and memory utilisation per VNF (latest measured sample). */
export function ResourceChart({ data, height = 240 }: { data: { name: string; cpu: number; mem: number }[]; height?: number }) {
  if (!data.length) return <EmptyState title="No measured VNFs" hint="Utilisation is sampled from the orchestrator while a VNF is ACTIVE." />;
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
        <CartesianGrid stroke={COLORS.grid} vertical={false} />
        <XAxis dataKey="name" stroke={COLORS.axis} fontSize={11} />
        <YAxis stroke={COLORS.axis} fontSize={11} width={40} unit="%" />
        <Tooltip {...tooltipStyle} formatter={(v) => `${Number(v).toFixed(1)}%`} />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        <Bar dataKey="cpu" name="CPU %" fill={COLORS.cpu} radius={[4, 4, 0, 0]} />
        <Bar dataKey="mem" name="Memory %" fill={COLORS.mem} radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}
