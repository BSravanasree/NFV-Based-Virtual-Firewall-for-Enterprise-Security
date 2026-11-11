import { Download, FileText, Search, X } from 'lucide-react';
import { useState } from 'react';
import { api, download, errorMessage } from '../api/client';
import type { AuditLog, Page } from '../api/types';
import { useToast } from '../components/Toast';
import { Button, Card, EmptyState, ErrorState, PageHeader, Pager, Spinner, StatusBadge } from '../components/ui';
import { useApi } from '../hooks/useApi';
import { dateTime } from '../utils/format';

interface F { from: string; to: string; username: string; action: string; category: string; outcome: string; search: string }
const EMPTY: F = { from: '', to: '', username: '', action: '', category: '', outcome: '', search: '' };

export default function AuditPage() {
  const toast = useToast();
  const [draft, setDraft] = useState<F>(EMPTY);
  const [filters, setFilters] = useState<F>(EMPTY);
  const [page, setPage] = useState(0);
  const [reportHours, setReportHours] = useState(24);
  const [busy, setBusy] = useState<string | null>(null);
  const actions = useApi(() => api.get<string[]>('/audit-logs/actions').then((r) => r.data), []);
  const params = () => {
    const p: Record<string, string> = {};
    Object.entries(filters).forEach(([k, v]) => { if (v) p[k] = k === 'from' || k === 'to' ? new Date(v).toISOString() : v; });
    return p;
  };
  const list = useApi(() => api.get<Page<AuditLog>>('/audit-logs', { params: { ...params(), page, size: 50 } }).then((r) => r.data), [filters, page]);

  const exp = async (kind: 'csv' | 'pdf') => {
    setBusy(kind);
    try {
      if (kind === 'csv') await download('/audit-logs/export', params(), 'audit-log.csv');
      else await download('/reports/security.pdf', { hours: reportHours }, 'nexus-security-report.pdf');
      toast.success(kind === 'csv' ? 'Audit CSV downloaded' : 'Security report generated');
    } catch (e) { toast.error('Export failed', errorMessage(e)); } finally { setBusy(null); }
  };

  return (
    <div>
      <PageHeader title="Audit Logs" subtitle="Tamper-evident trail of authentication and administrative actions"
        actions={<>
          <Button icon={<Download className="h-4 w-4" />} loading={busy === 'csv'} onClick={() => exp('csv')}>Export CSV</Button>
          <select className="input w-36" value={reportHours} onChange={(e) => setReportHours(Number(e.target.value))} aria-label="Report window">
            <option value={24}>Report: 24 h</option><option value={72}>Report: 3 days</option><option value={168}>Report: 7 days</option>
          </select>
          <Button variant="primary" icon={<FileText className="h-4 w-4" />} loading={busy === 'pdf'} onClick={() => exp('pdf')}>PDF security report</Button>
        </>} />
      <Card bodyClass="p-0">
        <form className="grid grid-cols-2 gap-2 border-b border-ink-700 p-3 md:grid-cols-4 xl:grid-cols-9" onSubmit={(e) => { e.preventDefault(); setPage(0); setFilters(draft); }}>
          <input className="input" type="datetime-local" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} aria-label="From" />
          <input className="input" type="datetime-local" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} aria-label="To" />
          <input className="input" placeholder="Username" value={draft.username} onChange={(e) => setDraft({ ...draft, username: e.target.value.trim() })} />
          <select className="input" value={draft.action} onChange={(e) => setDraft({ ...draft, action: e.target.value })} aria-label="Action">
            <option value="">All actions</option>{actions.data?.map((a) => <option key={a}>{a}</option>)}
          </select>
          <select className="input" value={draft.category} onChange={(e) => setDraft({ ...draft, category: e.target.value })} aria-label="Category">
            <option value="">All categories</option>{['AUTH', 'USER', 'VNF', 'FIREWALL', 'SDN', 'NETCONF', 'ALERT', 'SETTINGS', 'REPORT'].map((c) => <option key={c}>{c}</option>)}
          </select>
          <select className="input" value={draft.outcome} onChange={(e) => setDraft({ ...draft, outcome: e.target.value })} aria-label="Outcome">
            <option value="">Any outcome</option><option>SUCCESS</option><option>FAILURE</option>
          </select>
          <input className="input" placeholder="Search details…" value={draft.search} onChange={(e) => setDraft({ ...draft, search: e.target.value })} />
          <Button type="submit" variant="primary" icon={<Search className="h-4 w-4" />}>Search</Button>
          <Button type="button" icon={<X className="h-4 w-4" />} onClick={() => { setDraft(EMPTY); setFilters(EMPTY); setPage(0); }}>Clear</Button>
        </form>
        {list.loading && !list.data ? <Spinner /> : list.error ? <div className="p-3"><ErrorState message={list.error} /></div> : !list.data?.content.length ? <EmptyState title="No audit records match" /> : (
          <>
            <div className="overflow-x-auto"><table className="w-full">
              <thead><tr><th className="th">Time</th><th className="th">User</th><th className="th">Action</th><th className="th">Category</th><th className="th">Target</th><th className="th">Outcome</th><th className="th">Details</th><th className="th">Client IP</th></tr></thead>
              <tbody>{list.data.content.map((a) => (
                <tr key={a.id} className="align-top hover:bg-ink-800/40">
                  <td className="td text-xs whitespace-nowrap">{dateTime(a.occurredAt)}</td>
                  <td className="td text-sm">{a.username}</td>
                  <td className="td mono text-xs">{a.action}</td>
                  <td className="td text-xs">{a.category}</td>
                  <td className="td mono text-xs">{a.targetType}{a.targetId ? ` #${a.targetId}` : ''}</td>
                  <td className="td"><StatusBadge value={a.outcome} /></td>
                  <td className="td max-w-lg text-xs break-words text-slate-400">{a.details}</td>
                  <td className="td mono text-xs">{a.ipAddress}</td>
                </tr>))}
              </tbody>
            </table></div>
            <Pager page={list.data.page} totalPages={list.data.totalPages} total={list.data.totalElements} onPage={setPage} />
          </>
        )}
      </Card>
    </div>
  );
}
