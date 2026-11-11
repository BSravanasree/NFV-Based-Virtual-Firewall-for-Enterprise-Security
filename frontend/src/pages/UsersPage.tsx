import { KeyRound, Lock, Plus, Trash2, UserCog, XCircle } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { api, errorMessage } from '../api/client';
import type { Role, SessionDto, UserDto } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../components/Toast';
import { Badge, Button, Card, ConfirmDialog, ErrorState, Field, Modal, PageHeader, Spinner, StatusBadge } from '../components/ui';
import { useApi } from '../hooks/useApi';
import { ago, dateTime } from '../utils/format';

const ROLES: { value: Role; label: string; desc: string }[] = [
  { value: 'ADMIN', label: 'Administrator', desc: 'Everything, including users and settings' },
  { value: 'NETWORK_OPERATOR', label: 'Network Operator', desc: 'VNF lifecycle, firewall rules, SDN policy, NETCONF' },
  { value: 'SECURITY_ANALYST', label: 'Security Analyst', desc: 'Read infrastructure, triage alerts, audit & reports' },
];

export function passwordProblems(pw: string, username = ''): string[] {
  const p: string[] = [];
  if (pw.length < 10) p.push('at least 10 characters');
  if (!/[A-Z]/.test(pw)) p.push('an upper-case letter');
  if (!/[a-z]/.test(pw)) p.push('a lower-case letter');
  if (!/\d/.test(pw)) p.push('a digit');
  if (!/[^A-Za-z0-9]/.test(pw)) p.push('a symbol');
  if (username && pw.toLowerCase().includes(username.toLowerCase())) p.push('must not contain the username');
  return p;
}

export default function UsersPage() {
  const { user: me } = useAuth();
  const toast = useToast();
  const users = useApi(() => api.get<UserDto[]>('/users').then((r) => r.data), []);
  const sessions = useApi(() => api.get<SessionDto[]>('/auth/sessions').then((r) => r.data), [], 30000);
  const [editing, setEditing] = useState<UserDto | 'new' | null>(null);
  const [deleting, setDeleting] = useState<UserDto | null>(null);
  const [busy, setBusy] = useState(false);

  const patch = async (u: UserDto, body: Record<string, unknown>, msg: string) => {
    try { await api.patch(`/users/${u.id}`, body); toast.success(msg); void users.reload(true); void sessions.reload(true); }
    catch (e) { toast.error('Update failed', errorMessage(e)); }
  };
  const remove = async () => {
    if (!deleting) return;
    setBusy(true);
    try { await api.delete(`/users/${deleting.id}`); toast.success(`User ${deleting.username} deleted`); setDeleting(null); void users.reload(true); }
    catch (e) { toast.error('Delete failed', errorMessage(e)); } finally { setBusy(false); }
  };
  const revoke = async (s: SessionDto) => {
    try { await api.delete(`/auth/sessions/${s.id}`); toast.success(`Session of ${s.username} revoked`); void sessions.reload(true); }
    catch (e) { toast.error('Revoke failed', errorMessage(e)); }
  };

  return (
    <div>
      <PageHeader title="User Management" subtitle="Accounts, role-based access control and active sessions"
        actions={<Button variant="primary" icon={<Plus className="h-4 w-4" />} onClick={() => setEditing('new')}>Add user</Button>} />
      <div className="mb-4 grid gap-3 md:grid-cols-3">
        {ROLES.map((r) => <div key={r.value} className="panel p-3"><div className="text-sm font-semibold text-slate-100">{r.label}</div><div className="text-xs text-slate-400">{r.desc}</div></div>)}
      </div>
      <Card title="Users" bodyClass="p-0" className="mb-4">
        {users.loading && !users.data ? <Spinner /> : users.error ? <div className="p-3"><ErrorState message={users.error} /></div> : (
          <table className="w-full">
            <thead><tr><th className="th">User</th><th className="th">Role</th><th className="th">Status</th><th className="th">Last login</th><th className="th">Created</th><th className="th">Actions</th></tr></thead>
            <tbody>{users.data?.map((u) => (
              <tr key={u.id} className="hover:bg-ink-800/40">
                <td className="td"><div className="font-medium text-slate-100">{u.fullName} {u.id === me?.id && <Badge tone="blue">you</Badge>}</div><div className="text-xs text-slate-500">{u.username} · {u.email}</div></td>
                <td className="td text-sm">{ROLES.find((r) => r.value === u.role)?.label}</td>
                <td className="td"><StatusBadge value={u.enabled ? 'ACTIVE' : 'STOPPED'} /> {u.locked && <Badge tone="red"><Lock className="h-3 w-3" /> locked</Badge>}</td>
                <td className="td text-xs">{ago(u.lastLoginAt)}</td>
                <td className="td text-xs">{dateTime(u.createdAt)}</td>
                <td className="td"><div className="flex flex-wrap gap-1">
                  <Button variant="ghost" className="px-2 text-xs" icon={<UserCog className="h-3.5 w-3.5" />} onClick={() => setEditing(u)}>Edit</Button>
                  {u.locked && <Button variant="ghost" className="px-2 text-xs" onClick={() => patch(u, { unlock: true }, `${u.username} unlocked`)}>Unlock</Button>}
                  {u.id !== me?.id && <Button variant="ghost" className="px-2 text-xs" onClick={() => patch(u, { enabled: !u.enabled }, `${u.username} ${u.enabled ? 'disabled' : 'enabled'}`)}>{u.enabled ? 'Disable' : 'Enable'}</Button>}
                  {u.id !== me?.id && <Button variant="ghost" className="px-2 text-xs text-rose-400" icon={<Trash2 className="h-3.5 w-3.5" />} onClick={() => setDeleting(u)}>Delete</Button>}
                </div></td>
              </tr>))}
            </tbody>
          </table>
        )}
      </Card>
      <Card title="Active sessions" bodyClass="p-0">
        {sessions.error ? <div className="p-3"><ErrorState message={sessions.error} /></div> : (
          <table className="w-full">
            <thead><tr><th className="th">User</th><th className="th">Issued</th><th className="th">Expires</th><th className="th">Client</th><th className="th" /></tr></thead>
            <tbody>{sessions.data?.map((s) => (
              <tr key={s.id}><td className="td">{s.username}</td><td className="td text-xs">{dateTime(s.issuedAt)}</td><td className="td text-xs">{dateTime(s.expiresAt)}</td>
                <td className="td text-xs"><span className="mono">{s.ipAddress}</span><div className="max-w-sm truncate text-slate-500" title={s.userAgent}>{s.userAgent}</div></td>
                <td className="td"><Button variant="ghost" className="px-2 text-xs text-rose-400" icon={<XCircle className="h-3.5 w-3.5" />} onClick={() => revoke(s)}>Revoke</Button></td></tr>))}
            </tbody>
          </table>
        )}
      </Card>
      {editing && <UserModal user={editing === 'new' ? null : editing} isSelf={editing !== 'new' && editing.id === me?.id} onClose={() => setEditing(null)} onDone={() => { void users.reload(true); void sessions.reload(true); }} />}
      <ConfirmDialog open={!!deleting} danger busy={busy} title="Delete user" confirmLabel="Delete"
        message={<>Delete <b>{deleting?.username}</b>? Audit records are retained.</>} onConfirm={remove} onCancel={() => setDeleting(null)} />
    </div>
  );
}

function UserModal({ user, isSelf, onClose, onDone }: { user: UserDto | null; isSelf: boolean; onClose: () => void; onDone: () => void }) {
  const toast = useToast();
  const [f, setF] = useState({ username: user?.username ?? '', fullName: user?.fullName ?? '', email: user?.email ?? '', role: user?.role ?? 'SECURITY_ANALYST' as Role, password: '' });
  const [busy, setBusy] = useState(false);
  const pwIssues = f.password ? passwordProblems(f.password, f.username) : [];

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if ((!user || f.password) && pwIssues.length) { toast.error('Password policy', `Password needs ${pwIssues.join(', ')}`); return; }
    if (!user && !f.password) { toast.error('Password required'); return; }
    setBusy(true);
    try {
      if (user) {
        await api.patch(`/users/${user.id}`, { fullName: f.fullName, email: f.email, role: isSelf ? undefined : f.role, password: f.password || undefined });
        toast.success(`User ${user.username} updated`, f.password ? 'Password reset; existing sessions revoked.' : undefined);
      } else {
        await api.post('/users', f);
        toast.success(`User ${f.username} created`);
      }
      onDone();
      onClose();
    } catch (err) { toast.error('Save failed', errorMessage(err)); } finally { setBusy(false); }
  };

  return (
    <Modal open title={user ? `Edit ${user.username}` : 'Add user'} onClose={onClose} footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" type="submit" form="user-form" loading={busy}>{user ? 'Save' : 'Create'}</Button></>}>
      <form id="user-form" onSubmit={submit} className="grid grid-cols-2 gap-3">
        <Field label="Username"><input required disabled={!!user} className="input" value={f.username} onChange={(e) => setF({ ...f, username: e.target.value.trim() })} pattern="[A-Za-z0-9._-]{3,64}" /></Field>
        <Field label="Role" hint={isSelf ? 'You cannot change your own role' : undefined}>
          <select className="input" disabled={isSelf} value={f.role} onChange={(e) => setF({ ...f, role: e.target.value as Role })}>{ROLES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}</select>
        </Field>
        <Field label="Full name"><input required className="input" value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} /></Field>
        <Field label="Email"><input required type="email" className="input" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></Field>
        <div className="col-span-2">
          <Field label={user ? 'Reset password (optional)' : 'Initial password'} error={f.password && pwIssues.length ? `Needs ${pwIssues.join(', ')}` : undefined}
            hint="≥10 chars, upper, lower, digit, symbol">
            <div className="relative"><KeyRound className="pointer-events-none absolute top-2.5 left-2.5 h-4 w-4 text-slate-500" />
              <input type="password" autoComplete="new-password" className="input pl-8" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} /></div>
          </Field>
        </div>
      </form>
    </Modal>
  );
}
