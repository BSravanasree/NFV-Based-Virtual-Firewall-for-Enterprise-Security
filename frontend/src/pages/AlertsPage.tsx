import { Download, GitCommitVertical, List, Radar, RefreshCw } from 'lucide-react';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, download, errorMessage } from '../api/client';
import type { Alert, AlertStats, AlertStatus, Page, Severity } from '../api/types';
import { CAN_SECURITY, useAuth } from '../auth/AuthContext';
import { useToast } from '../components/Toast';
import { Banner, Button, Card, EmptyState, ErrorState, Field, Modal, PageHeader, Pager, Segmented, Spinner, StatCard, StatusBadge } from '../components/ui';
import { useApi } from '../hooks/useApi';
import { ago, dateTime } from '../utils/format';

const SEV_DOT: Record<Severity, string> = { CRITICAL: 'bg-rose-400', HIGH: 'bg-orange-400', MEDIUM: 'bg-amber-300', LOW: 'bg-cyan-300' };

const DETECTORS = ['PORT_SCAN', 'REPEATED_BLOCKED', 'HIGH_FREQUENCY', 'UNAUTHORIZED_ACCESS', 'UNEXPECTED_COMMUNICATION'];

export default function AlertsPage() {
  const { hasRole } = useAuth();
  const canTriage = hasRole(...CAN_SECURITY);
  const toast = useToast();
  const [params] = useSearchParams();
  const [status, setStatus] = useState<string>(params.get('sourceIp') ? '' : 'OPEN');
  const [severity, setSeverity] = useState('');
  const [detector, setDetector] = useState('');
  const [sourceIp, setSourceIp] = useState(params.get('sourceIp') ?? '');
  const [page, setPage] = useState(0);
  const stats = useApi(() => api.get<AlertStats>('/alerts/stats').then((r) => r.data), [], 15000);
  const list = useApi(() => api.get<Page<Alert>>('/alerts', { params: { status: status || undefined, severity: severity || undefined, detector: detector || undefined, sourceIp: sourceIp || undefined, page, size: 20 } }).then((r) => r.data), [status, severity, detector, sourceIp, page], 15000);
  const [selected, setSelected] = useState<Alert | null>(null);
  const [view, setView] = useState<'table' | 'timeline'>('table');
  const [detecting, setDetecting] = useState(false);

  const detectNow = async () => {
    setDetecting(true);
    try {
      const r = await api.post<{ alertsCreatedOrUpdated: number }>('/alerts/detect');
      toast.success('Detection cycle completed', `${r.data.alertsCreatedOrUpdated} alert(s) created or updated`);
      void list.reload(true); void stats.reload(true);
    } catch (e) { toast.error('Detection failed', errorMessage(e)); } finally { setDetecting(false); }
  };

  const s = stats.data;
  return (
    <div>
      <PageHeader title="Security Alerts" subtitle="Transparent rule-based detection over firewall connection events (thresholds in System Settings)"
        actions={<>
          <Button icon={<RefreshCw className="h-4 w-4" />} onClick={() => { void list.reload(); void stats.reload(); }}>Refresh</Button>
          <Button icon={<Download className="h-4 w-4" />} onClick={() => download('/alerts/export', { status: status || undefined, severity: severity || undefined }, 'security-alerts.csv').catch((e) => toast.error('Export failed', errorMessage(e)))}>Export CSV</Button>
          {canTriage && <Button variant="primary" icon={<Radar className="h-4 w-4" />} loading={detecting} onClick={detectNow}>Run detection now</Button>}
        </>} />
      <Banner tone="info">Detectors: port scanning (distinct ports per source), repeated blocked traffic, unusual connection frequency, unauthorized access to sensitive ports (22, 23, 445, 1433, 3306, 3389, 5432, 5900) and unexpected communication hitting the default-deny policy. This is a threshold-based engine, not an AI/ML IDS.</Banner>

      {s && (
        <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-6">
          <StatCard label="Open" value={s.open} tone={s.open ? 'warn' : 'good'} />
          <StatCard label="Acknowledged" value={s.acknowledged} />
          {(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as Severity[]).map((sv) => (
            <StatCard key={sv} label={`${sv.toLowerCase()} (active)`} value={s.activeBySeverity[sv]} tone={sv === 'CRITICAL' || sv === 'HIGH' ? (s.activeBySeverity[sv] ? 'bad' : 'default') : 'default'} />
          ))}
        </div>
      )}

      <Card bodyClass="p-0">
        <div className="flex flex-wrap gap-2 border-b border-ink-700 p-3">
          <select className="input w-40" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} aria-label="Status">
            <option value="">All statuses</option>{['OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'FALSE_POSITIVE'].map((x) => <option key={x}>{x}</option>)}
          </select>
          <select className="input w-36" value={severity} onChange={(e) => { setSeverity(e.target.value); setPage(0); }} aria-label="Severity">
            <option value="">All severities</option>{['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((x) => <option key={x}>{x}</option>)}
          </select>
          <select className="input w-56" value={detector} onChange={(e) => { setDetector(e.target.value); setPage(0); }} aria-label="Detector">
            <option value="">All detectors</option>{DETECTORS.map((x) => <option key={x}>{x}</option>)}
          </select>
          <input className="input mono w-44" placeholder="Source IP" value={sourceIp} onChange={(e) => { setSourceIp(e.target.value.trim()); setPage(0); }} />
          <div className="ml-auto"><Segmented label="Alert view" value={view} onChange={setView}
            options={[{ value: 'table', label: <><List className="h-3.5 w-3.5" /> Table</> }, { value: 'timeline', label: <><GitCommitVertical className="h-3.5 w-3.5" /> Timeline</> }]} /></div>
        </div>
        {list.loading && !list.data ? <Spinner /> : list.error ? <div className="p-3"><ErrorState message={list.error} /></div> : !list.data?.content.length ? <EmptyState title="No alerts match the filter" /> : view === 'timeline' ? (
          <>
            <ol className="relative m-4 space-y-4 border-l border-white/10 pl-6">
              {list.data.content.map((a) => (
                <li key={a.id} className="relative">
                  <span className={`absolute top-3 -left-[31px] h-3 w-3 rounded-full border-2 border-[#10182a] ${SEV_DOT[a.severity]}`} />
                  <button onClick={() => setSelected(a)} className="glass glass-hover w-full p-3 text-left">
                    <div className="flex flex-wrap items-center gap-2">
                      <StatusBadge value={a.severity} /><span className="font-medium text-slate-100">{a.eventType}</span>
                      <StatusBadge value={a.status} /><span className="ml-auto text-xs text-slate-500">{dateTime(a.lastSeen)}</span>
                    </div>
                    <div className="mt-1 text-xs text-slate-400"><span className="mono">{a.alertId}</span> · <span className="mono">{a.sourceIp ?? '—'} → {a.destinationIp ?? '—'}</span> · {a.evidenceCount} events</div>
                    <div className="mt-1 line-clamp-2 text-sm text-slate-300">{a.description}</div>
                  </button>
                </li>
              ))}
            </ol>
            <Pager page={list.data.page} totalPages={list.data.totalPages} total={list.data.totalElements} onPage={setPage} />
          </>
        ) : (
          <>
            <div className="overflow-x-auto"><table className="w-full min-w-[900px]">
              <thead><tr><th className="th">Alert</th><th className="th">Severity</th><th className="th">Event</th><th className="th">Source → Destination</th><th className="th">Evidence</th><th className="th">Last seen</th><th className="th">Status</th><th className="th">Assignee</th></tr></thead>
              <tbody>{list.data.content.map((a) => (
                <tr key={a.id} className="cursor-pointer hover:bg-ink-800/50" onClick={() => setSelected(a)}>
                  <td className="td mono text-xs">{a.alertId}</td>
                  <td className="td"><StatusBadge value={a.severity} /></td>
                  <td className="td"><div className="text-slate-100">{a.eventType}</div><div className="max-w-md truncate text-[11px] text-slate-500" title={a.description}>{a.description}</div></td>
                  <td className="td mono text-xs">{a.sourceIp ?? '—'} → {a.destinationIp ?? '—'}</td>
                  <td className="td tabular-nums">{a.evidenceCount}</td>
                  <td className="td text-xs">{ago(a.lastSeen)}</td>
                  <td className="td"><StatusBadge value={a.status} /></td>
                  <td className="td text-xs">{a.assignedTo ?? '—'}</td>
                </tr>))}
              </tbody>
            </table></div>
            <Pager page={list.data.page} totalPages={list.data.totalPages} total={list.data.totalElements} onPage={setPage} />
          </>
        )}
      </Card>
      {selected && <AlertModal alert={selected} canTriage={canTriage} onClose={() => setSelected(null)} onDone={() => { void list.reload(true); void stats.reload(true); }} />}
    </div>
  );
}

function AlertModal({ alert, canTriage, onClose, onDone }: { alert: Alert; canTriage: boolean; onClose: () => void; onDone: () => void }) {
  const toast = useToast();
  const { user } = useAuth();
  const [note, setNote] = useState(alert.resolutionNote ?? '');
  const [assignee, setAssignee] = useState(alert.assignedTo ?? '');
  const [busy, setBusy] = useState<AlertStatus | null>(null);

  const update = async (status: AlertStatus) => {
    if ((status === 'RESOLVED' || status === 'FALSE_POSITIVE') && !note.trim()) {
      toast.error('Resolution note required', 'Document the investigation outcome before closing the alert.');
      return;
    }
    setBusy(status);
    try {
      await api.patch(`/alerts/${alert.id}`, { status, assignedTo: assignee || (status === 'ACKNOWLEDGED' ? user?.username : undefined), note: note || undefined });
      toast.success(`${alert.alertId} → ${status}`);
      onDone();
      onClose();
    } catch (e) { toast.error('Update failed', errorMessage(e)); } finally { setBusy(null); }
  };

  return (
    <Modal open title={`${alert.alertId} · ${alert.eventType}`} onClose={onClose} wide footer={canTriage ? <>
      {alert.status === 'OPEN' && <Button loading={busy === 'ACKNOWLEDGED'} onClick={() => update('ACKNOWLEDGED')}>Acknowledge</Button>}
      {alert.status !== 'FALSE_POSITIVE' && <Button loading={busy === 'FALSE_POSITIVE'} onClick={() => update('FALSE_POSITIVE')}>False positive</Button>}
      {alert.status !== 'RESOLVED' && <Button variant="primary" loading={busy === 'RESOLVED'} onClick={() => update('RESOLVED')}>Resolve</Button>}
      {(alert.status === 'RESOLVED' || alert.status === 'FALSE_POSITIVE') && <Button loading={busy === 'OPEN'} onClick={() => update('OPEN')}>Re-open</Button>}
    </> : <Button onClick={onClose}>Close</Button>}>
      {!canTriage && <Banner tone="info">Your role can view alerts; triage requires ADMIN or SECURITY_ANALYST.</Banner>}
      <div className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
        <div><span className="label">Severity</span><StatusBadge value={alert.severity} /></div>
        <div><span className="label">Status</span><StatusBadge value={alert.status} /></div>
        <div><span className="label">Detector</span><span className="mono">{alert.detector}</span></div>
        <div><span className="label">Evidence (events in window)</span>{alert.evidenceCount}</div>
        <div><span className="label">Source IP</span><span className="mono">{alert.sourceIp ?? '—'}</span></div>
        <div><span className="label">Destination IP (sample)</span><span className="mono">{alert.destinationIp ?? '—'}</span></div>
        <div><span className="label">First seen</span>{dateTime(alert.firstSeen)}</div>
        <div><span className="label">Last seen</span>{dateTime(alert.lastSeen)}</div>
        <div className="col-span-2"><span className="label">Description</span><p className="text-slate-300">{alert.description}</p></div>
        {canTriage && <>
          <div><Field label="Assignee"><input className="input" value={assignee} onChange={(e) => setAssignee(e.target.value)} placeholder={user?.username} /></Field></div>
          <div className="col-span-2"><Field label="Investigation / resolution note"><textarea className="input min-h-20" value={note} maxLength={1000} onChange={(e) => setNote(e.target.value)} /></Field></div>
        </>}
        {!canTriage && alert.resolutionNote && <div className="col-span-2"><span className="label">Resolution note</span>{alert.resolutionNote}</div>}
      </div>
    </Modal>
  );
}
