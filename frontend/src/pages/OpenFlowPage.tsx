import { Plus, RefreshCw, RotateCw, Trash2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { api, errorMessage } from '../api/client';
import type { ControllerStatus, Device, Flow, NetworkPolicy, PolicyAction } from '../api/types';
import { CAN_OPERATE, useAuth } from '../auth/AuthContext';
import { useToast } from '../components/Toast';
import { Badge, Button, Card, ConfirmDialog, EmptyState, ErrorState, Field, Modal, PageHeader, SourceBadge, Spinner, StatusBadge } from '../components/ui';
import { useApi } from '../hooks/useApi';
import { bytes, CIDR_RE, num } from '../utils/format';
import { OnosModeBanner } from './OnosPage';

export default function OpenFlowPage() {
  const { hasRole } = useAuth();
  const canEdit = hasRole(...CAN_OPERATE);
  const toast = useToast();
  const status = useApi(() => api.get<ControllerStatus>('/sdn/status').then((r) => r.data), [], 30000);
  const devices = useApi(() => api.get<Device[]>('/sdn/devices').then((r) => r.data), []);
  const [deviceFilter, setDeviceFilter] = useState('');
  const [onlyNexus, setOnlyNexus] = useState(false);
  const flows = useApi(() => api.get<Flow[]>('/sdn/flows', { params: deviceFilter ? { deviceId: deviceFilter } : {} }).then((r) => r.data), [deviceFilter], 10000);
  const policies = useApi(() => api.get<NetworkPolicy[]>('/sdn/policies').then((r) => r.data), [], 15000);
  const [creating, setCreating] = useState(false);
  const [delFlow, setDelFlow] = useState<Flow | null>(null);
  const [delPolicy, setDelPolicy] = useState<NetworkPolicy | null>(null);
  const [busy, setBusy] = useState(false);

  const removeFlow = async () => {
    if (!delFlow) return;
    setBusy(true);
    try {
      await api.delete(`/sdn/flows/${encodeURIComponent(delFlow.deviceId)}/${delFlow.id}`);
      toast.success('Flow removed', `${delFlow.id} on ${delFlow.deviceId}`);
      setDelFlow(null);
      void flows.reload(true);
    } catch (e) { toast.error('Flow removal failed', errorMessage(e)); } finally { setBusy(false); }
  };
  const removePolicy = async () => {
    if (!delPolicy) return;
    setBusy(true);
    try {
      await api.delete(`/sdn/policies/${delPolicy.id}`);
      toast.success('Policy removed', 'The corresponding flow rule was deleted from the switch.');
      setDelPolicy(null);
      void policies.reload(true); void flows.reload(true);
    } catch (e) { toast.error('Policy removal failed', errorMessage(e)); } finally { setBusy(false); }
  };
  const reinstall = async (p: NetworkPolicy) => {
    try {
      const r = await api.post<NetworkPolicy>(`/sdn/policies/${p.id}/reinstall`);
      if (r.data.status === 'INSTALLED') toast.success('Policy installed', `flow ${r.data.onosFlowId}`);
      else toast.error('Policy still not installed', r.data.lastError);
      void policies.reload(true); void flows.reload(true);
    } catch (e) { toast.error('Reinstall failed', errorMessage(e)); }
  };

  const shown = (flows.data ?? []).filter((f) => !onlyNexus || f.managedByNexus);

  return (
    <div>
      <PageHeader title="OpenFlow Management" subtitle="Open vSwitch flow tables and SDN security policies compiled to OpenFlow 1.3"
        actions={<>
          <Button icon={<RefreshCw className="h-4 w-4" />} onClick={() => { void flows.reload(); void policies.reload(); }}>Refresh</Button>
          {canEdit && <Button variant="primary" icon={<Plus className="h-4 w-4" />} onClick={() => setCreating(true)}>New network policy</Button>}
        </>} />
      <OnosModeBanner status={status.data} />

      <Card title="Network security policies" className="mb-4" bodyClass="p-0">
        {policies.error ? <div className="p-3"><ErrorState message={policies.error} /></div> : !policies.data?.length ? (
          <EmptyState title="No network policies" hint="Policies are translated into OpenFlow flow rules (match on IPv4/protocol/port, action drop / allow / forward / steer to firewall) and installed through ONOS." />
        ) : (
          <table className="w-full">
            <thead><tr><th className="th">Name</th><th className="th">Switch</th><th className="th">Prio</th><th className="th">Match</th><th className="th">Action</th><th className="th">Flow</th><th className="th">Status</th>{canEdit && <th className="th" />}</tr></thead>
            <tbody>{policies.data.map((p) => (
              <tr key={p.id}>
                <td className="td"><div className="font-medium text-slate-100">{p.name}</div><div className="text-[11px] text-slate-500">{p.description}</div></td>
                <td className="td mono text-xs">{p.deviceId}</td>
                <td className="td mono">{p.priority}</td>
                <td className="td mono text-xs">{p.ipProtocol} {p.sourceCidr ?? 'any'} → {p.destinationCidr ?? 'any'}{p.destinationPort ? `:${p.destinationPort}` : ''}</td>
                <td className="td text-xs">{p.action}{p.outputPort ? ` → port ${p.outputPort}` : ''}</td>
                <td className="td mono text-xs">{p.onosFlowId ?? '—'}</td>
                <td className="td"><StatusBadge value={p.status} /> {p.dataSource && <SourceBadge source={p.dataSource} />}
                  {p.lastError && <div className="max-w-xs truncate text-[11px] text-rose-400" title={p.lastError}>{p.lastError}</div>}</td>
                {canEdit && <td className="td"><div className="flex gap-1">
                  {p.status !== 'INSTALLED' && <Button variant="ghost" className="px-1.5" onClick={() => reinstall(p)} aria-label="Reinstall"><RotateCw className="h-3.5 w-3.5" /></Button>}
                  <Button variant="ghost" className="px-1.5 text-rose-400" onClick={() => setDelPolicy(p)} aria-label="Delete policy"><Trash2 className="h-3.5 w-3.5" /></Button>
                </div></td>}
              </tr>))}
            </tbody>
          </table>
        )}
      </Card>

      <Card title="Flow table" bodyClass="p-0" actions={<>
        <label className="flex items-center gap-1.5 text-xs text-slate-400"><input type="checkbox" checked={onlyNexus} onChange={(e) => setOnlyNexus(e.target.checked)} /> NEXUS-managed only</label>
        <select className="input w-56 py-1" value={deviceFilter} onChange={(e) => setDeviceFilter(e.target.value)} aria-label="Switch filter">
          <option value="">All switches</option>
          {devices.data?.map((d) => <option key={d.id} value={d.id}>{d.name ?? d.id}</option>)}
        </select>
      </>}>
        {flows.loading && !flows.data ? <Spinner /> : flows.error ? <div className="p-3"><ErrorState message={flows.error} /></div> : !shown.length ? <EmptyState title="No flow rules" /> : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead><tr><th className="th">Flow ID</th><th className="th">Switch</th><th className="th">App</th><th className="th">Prio</th><th className="th">Match</th><th className="th">Actions</th><th className="th">Packets / Bytes</th><th className="th">State</th>{canEdit && <th className="th" />}</tr></thead>
              <tbody>{shown.map((f) => (
                <tr key={`${f.deviceId}/${f.id}`} className="hover:bg-ink-800/40">
                  <td className="td mono text-xs">{f.id}</td>
                  <td className="td mono text-xs">{f.deviceId}</td>
                  <td className="td text-xs">{f.managedByNexus ? <Badge tone="blue">NEXUS</Badge> : f.appId}</td>
                  <td className="td mono">{f.priority}</td>
                  <td className="td mono text-xs">{f.match}</td>
                  <td className="td mono text-xs">{f.actions}</td>
                  <td className="td text-xs tabular-nums">{num(f.packets)} / {bytes(f.bytes)}</td>
                  <td className="td"><StatusBadge value={f.state === 'ADDED' ? 'INSTALLED' : f.state} /></td>
                  {canEdit && <td className="td"><Button variant="ghost" className="px-1.5 text-rose-400" onClick={() => setDelFlow(f)} aria-label="Delete flow"><Trash2 className="h-3.5 w-3.5" /></Button></td>}
                </tr>))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <PolicyModal open={creating} devices={devices.data ?? []} onClose={() => setCreating(false)} onDone={() => { void policies.reload(true); void flows.reload(true); }} />
      <ConfirmDialog open={!!delFlow} danger busy={busy} title="Delete flow rule" confirmLabel="Delete flow"
        message={<>Remove flow <span className="mono">{delFlow?.id}</span> from <span className="mono">{delFlow?.deviceId}</span>? {delFlow && !delFlow.managedByNexus && <b className="text-amber-300">This flow belongs to {delFlow.appId}; the owning app may re-install it.</b>}</>}
        onConfirm={removeFlow} onCancel={() => setDelFlow(null)} />
      <ConfirmDialog open={!!delPolicy} danger busy={busy} title="Delete network policy" confirmLabel="Delete policy"
        message={<>Delete policy <b>{delPolicy?.name}</b> and remove its flow from the switch?</>} onConfirm={removePolicy} onCancel={() => setDelPolicy(null)} />
    </div>
  );
}

function PolicyModal({ open, devices, onClose, onDone }: { open: boolean; devices: Device[]; onClose: () => void; onDone: () => void }) {
  const toast = useToast();
  const [f, setF] = useState({ name: '', description: '', deviceId: '', priority: '40001', ipProtocol: 'TCP', sourceCidr: '', destinationCidr: '', destinationPort: '', action: 'DROP' as PolicyAction, outputPort: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const set = (k: string, v: string) => setF((x) => ({ ...x, [k]: v }));

  const submit = async (ev: FormEvent) => {
    ev.preventDefault();
    const e: Record<string, string> = {};
    if (!/^[A-Za-z0-9 _.-]{1,64}$/.test(f.name)) e.name = 'Required; letters, digits, space, "_", "." and "-"';
    if (!f.deviceId) e.deviceId = 'Select a switch';
    const prio = Number(f.priority);
    if (!(Number.isInteger(prio) && prio >= 1000 && prio <= 60000)) e.priority = '1000 – 60000';
    if (f.sourceCidr && !CIDR_RE.test(f.sourceCidr)) e.sourceCidr = 'IPv4/CIDR';
    if (f.destinationCidr && !CIDR_RE.test(f.destinationCidr)) e.destinationCidr = 'IPv4/CIDR';
    if (f.destinationPort && (!['TCP', 'UDP'].includes(f.ipProtocol) || !(Number(f.destinationPort) >= 1 && Number(f.destinationPort) <= 65535))) e.destinationPort = 'Port 1-65535 with TCP/UDP';
    if ((f.action === 'FORWARD' || f.action === 'STEER_TO_FIREWALL') && !/^\d{1,5}$/.test(f.outputPort)) e.outputPort = 'Numeric switch port required';
    setErrors(e);
    if (Object.keys(e).length) return;
    setBusy(true);
    try {
      const r = await api.post<NetworkPolicy>('/sdn/policies', {
        name: f.name, description: f.description || null, deviceId: f.deviceId, priority: prio, ipProtocol: f.ipProtocol,
        sourceCidr: f.sourceCidr || null, destinationCidr: f.destinationCidr || null,
        destinationPort: f.destinationPort ? Number(f.destinationPort) : null, action: f.action, outputPort: f.outputPort || null,
      });
      if (r.data.status === 'INSTALLED') toast.success('Policy installed as OpenFlow rule', `flow ${r.data.onosFlowId} on ${r.data.deviceId} [${r.data.dataSource}]`);
      else toast.error('Policy saved but NOT installed', r.data.lastError);
      onDone();
      onClose();
    } catch (err) {
      toast.error('Policy rejected', errorMessage(err));
    } finally { setBusy(false); }
  };

  return (
    <Modal open={open} title="New network security policy" onClose={onClose} wide footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" type="submit" form="policy-form" loading={busy}>Install policy</Button></>}>
      <form id="policy-form" onSubmit={submit} className="grid grid-cols-3 gap-3" noValidate>
        <Field label="Name" error={errors.name}><input className="input" value={f.name} onChange={(e) => set('name', e.target.value)} /></Field>
        <Field label="Switch" error={errors.deviceId}>
          <select className="input" value={f.deviceId} onChange={(e) => set('deviceId', e.target.value)}>
            <option value="">Select…</option>{devices.map((d) => <option key={d.id} value={d.id}>{d.name ? `${d.name} (${d.id})` : d.id}</option>)}
          </select></Field>
        <Field label="Priority" error={errors.priority} hint="Higher wins (ONOS fwd uses 10)"><input className="input" type="number" value={f.priority} onChange={(e) => set('priority', e.target.value)} /></Field>
        <Field label="IP protocol"><select className="input" value={f.ipProtocol} onChange={(e) => set('ipProtocol', e.target.value)}>{['ANY', 'TCP', 'UDP', 'ICMP'].map((p) => <option key={p}>{p}</option>)}</select></Field>
        <Field label="Source CIDR" error={errors.sourceCidr}><input className="input mono" value={f.sourceCidr} onChange={(e) => set('sourceCidr', e.target.value.trim())} placeholder="any" /></Field>
        <Field label="Destination CIDR" error={errors.destinationCidr}><input className="input mono" value={f.destinationCidr} onChange={(e) => set('destinationCidr', e.target.value.trim())} placeholder="any" /></Field>
        <Field label="Destination port" error={errors.destinationPort}><input className="input mono" value={f.destinationPort} onChange={(e) => set('destinationPort', e.target.value.trim())} placeholder="any" /></Field>
        <Field label="Action"><select className="input" value={f.action} onChange={(e) => set('action', e.target.value)}>
          <option value="DROP">DROP</option><option value="ALLOW">ALLOW (normal L2 pipeline)</option>
          <option value="FORWARD">FORWARD to port</option><option value="STEER_TO_FIREWALL">STEER to firewall VNF port</option>
        </select></Field>
        <Field label="Output port" error={errors.outputPort} hint="For FORWARD / STEER"><input className="input mono" value={f.outputPort} onChange={(e) => set('outputPort', e.target.value.trim())} disabled={f.action === 'DROP' || f.action === 'ALLOW'} /></Field>
        <div className="col-span-3"><Field label="Description"><input className="input" value={f.description} onChange={(e) => set('description', e.target.value)} maxLength={255} /></Field></div>
      </form>
    </Modal>
  );
}
