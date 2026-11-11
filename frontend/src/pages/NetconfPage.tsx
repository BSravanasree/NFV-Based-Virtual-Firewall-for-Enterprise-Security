import { CheckCircle2, FileDown, History, Plug, Plus, RotateCcw, Send, Trash2, Upload } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { api, errorMessage } from '../api/client';
import type { ConfigVersion, NetconfDevice, OperationResult, SyncReport, Vnf } from '../api/types';
import { ADMIN_ONLY, CAN_OPERATE, useAuth } from '../auth/AuthContext';
import { useToast } from '../components/Toast';
import { Banner, Button, Card, ConfirmDialog, EmptyState, ErrorState, Field, Modal, PageHeader, Spinner, StatusBadge } from '../components/ui';
import { useApi } from '../hooks/useApi';
import { ago, dateTime } from '../utils/format';

export default function NetconfPage() {
  const { hasRole } = useAuth();
  const canOperate = hasRole(...CAN_OPERATE);
  const devices = useApi(() => api.get<NetconfDevice[]>('/netconf/devices').then((r) => r.data), [], 15000);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [adding, setAdding] = useState(false);
  const selected = devices.data?.find((d) => d.id === selectedId) ?? devices.data?.[0];

  return (
    <div>
      <PageHeader title="NETCONF Configuration" subtitle="YANG-modelled firewall configuration (module nexus-firewall, urn:nexus:nfv:firewall) over NETCONF/SSH"
        actions={canOperate && <Button variant="primary" icon={<Plus className="h-4 w-4" />} onClick={() => setAdding(true)}>Register device</Button>} />
      <Banner tone="info">Firewall VNFs deployed in the lab run Netopeer2/sysrepo and are registered automatically with a per-instance credential. Pushed configuration is committed to the device's datastore and enforced as nftables by the in-VNF agent.</Banner>
      {devices.loading && !devices.data ? <Spinner /> : devices.error ? <ErrorState message={devices.error} onRetry={() => devices.reload()} /> : !devices.data?.length ? (
        <Card><EmptyState title="No NETCONF devices" hint="Deploy a firewall VNF (auto-registered) or register an external NETCONF server." /></Card>
      ) : (
        <div className="grid gap-4 xl:grid-cols-[320px_1fr]">
          <Card title="Devices" bodyClass="p-2 space-y-1">
            {devices.data.map((d) => (
              <button key={d.id} onClick={() => setSelectedId(d.id)} className={`w-full rounded-md px-3 py-2 text-left ${selected?.id === d.id ? 'bg-accent-600/15 ring-1 ring-accent-500/40' : 'hover:bg-ink-800'}`}>
                <div className="flex items-center justify-between"><span className="font-medium text-slate-100">{d.name}</span><StatusBadge value={d.status} /></div>
                <div className="mono text-[11px] text-slate-500">{d.username}@{d.host}:{d.port}{d.vnfId ? ` · VNF ${d.vnfId}` : ''}</div>
              </button>
            ))}
          </Card>
          {selected && <DevicePanel key={selected.id} device={selected} onChanged={() => devices.reload(true)} canOperate={canOperate} />}
        </div>
      )}
      <AddDeviceModal open={adding} onClose={() => setAdding(false)} onDone={() => devices.reload(true)} />
    </div>
  );
}

function DevicePanel({ device, onChanged, canOperate }: { device: NetconfDevice; onChanged: () => void; canOperate: boolean }) {
  const toast = useToast();
  const { hasRole } = useAuth();
  const versions = useApi(() => api.get<ConfigVersion[]>(`/netconf/devices/${device.id}/versions`).then((r) => r.data), [device.id]);
  const [sync, setSync] = useState<SyncReport | null>(null);
  const [xml, setXml] = useState<{ title: string; body: string } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [rollback, setRollback] = useState<ConfigVersion | null>(null);
  const [del, setDel] = useState(false);

  const op = async (key: string, fn: () => Promise<void>) => {
    setBusy(key);
    try { await fn(); } catch (e) { toast.error('NETCONF operation failed', errorMessage(e)); } finally { setBusy(null); onChanged(); }
  };
  const result = (r: OperationResult, what: string) => {
    if (r.success) toast.success(what, r.message); else toast.error(`${what} failed`, r.message);
    void versions.reload(true);
  };

  return (
    <div className="space-y-4">
      <Card title={device.name} actions={<StatusBadge value={device.status} />}>
        {device.lastError && device.status !== 'CONNECTED' && <div className="mb-3"><ErrorState message={device.lastError} /></div>}
        <div className="mb-3 grid grid-cols-2 gap-x-6 gap-y-1 text-sm md:grid-cols-4">
          <div><span className="label">Endpoint</span><span className="mono">{device.host}:{device.port}</span></div>
          <div><span className="label">User</span><span className="mono">{device.username}</span></div>
          <div><span className="label">Last session</span>{ago(device.lastConnectedAt)}</div>
          <div><span className="label">Host key (pinned)</span><span className="mono text-[11px] break-all">{device.hostKeyFingerprint ?? 'not pinned yet'}</span></div>
        </div>
        <div className="flex flex-wrap gap-2">
          {canOperate && <Button icon={<Plug className="h-4 w-4" />} loading={busy === 'test'} onClick={() => op('test', async () => { await api.post(`/netconf/devices/${device.id}/test`); toast.success('NETCONF session established', 'Capabilities exchanged via <hello>.'); })}>Test connection</Button>}
          <Button icon={<FileDown className="h-4 w-4" />} loading={busy === 'running'} onClick={() => op('running', async () => { const r = await api.get<{ configXml: string; ruleCount: number }>(`/netconf/devices/${device.id}/running`); setXml({ title: `Running datastore (${r.data.ruleCount} rules)`, body: r.data.configXml || '<!-- empty -->' }); })}>Get running config</Button>
          {device.vnfId && <Button loading={busy === 'desired'} onClick={() => op('desired', async () => { const r = await api.get<{ configXml: string }>(`/netconf/devices/${device.id}/desired`); setXml({ title: 'Desired configuration (from platform policy)', body: r.data.configXml }); })}>View desired config</Button>}
          <Button icon={<CheckCircle2 className="h-4 w-4" />} loading={busy === 'sync'} onClick={() => op('sync', async () => setSync((await api.get<SyncReport>(`/netconf/devices/${device.id}/sync`)).data))}>Check sync</Button>
          {canOperate && device.vnfId && <Button loading={busy === 'validate'} onClick={() => op('validate', async () => result((await api.post<OperationResult>(`/netconf/devices/${device.id}/validate`)).data, 'Validation'))}>Validate</Button>}
          {canOperate && device.vnfId && <Button variant="primary" icon={<Send className="h-4 w-4" />} loading={busy === 'push'} onClick={() => op('push', async () => result((await api.post<OperationResult>(`/netconf/devices/${device.id}/push`)).data, 'Configuration pushed'))}>Push policy</Button>}
          {canOperate && <Button icon={<Upload className="h-4 w-4" />} loading={busy === 'snap'} onClick={() => op('snap', async () => { await api.post(`/netconf/devices/${device.id}/snapshot`); toast.success('Snapshot stored'); void versions.reload(true); })}>Snapshot</Button>}
          {hasRole(...ADMIN_ONLY) && device.hostKeyFingerprint && <Button variant="ghost" onClick={() => op('rk', async () => { await api.post(`/netconf/devices/${device.id}/reset-host-key`); toast.info('Pinned host key cleared', 'The next connection will pin the presented key.'); })}>Reset host key</Button>}
          {canOperate && <Button variant="ghost" className="text-rose-400" icon={<Trash2 className="h-4 w-4" />} onClick={() => setDel(true)}>Remove</Button>}
        </div>
        {sync && (
          <div className="mt-3 rounded-md border border-ink-600 bg-ink-900 p-3 text-sm">
            <div className="flex items-center gap-2"><StatusBadge value={sync.status} /><span className="text-slate-300">{sync.detail}</span></div>
            <div className="mono mt-1 text-[11px] text-slate-500">running {sync.runningChecksum?.slice(0, 16) ?? '—'} · desired {sync.desiredChecksum?.slice(0, 16) ?? '—'} · last applied v{sync.lastAppliedVersion ?? '—'}</div>
          </div>
        )}
        {device.capabilities.length > 0 && (
          <details className="mt-3 text-xs text-slate-400"><summary className="cursor-pointer">{device.capabilities.length} server capabilities</summary>
            <ul className="mono mt-2 max-h-48 space-y-0.5 overflow-y-auto">{device.capabilities.map((c) => <li key={c} className="break-all">{c}</li>)}</ul></details>
        )}
      </Card>

      <Card title={<span className="flex items-center gap-2"><History className="h-4 w-4" /> Configuration history</span>} bodyClass="p-0">
        {versions.error ? <div className="p-3"><ErrorState message={versions.error} /></div> : !versions.data?.length ? <EmptyState title="No configuration versions yet" /> : (
          <table className="w-full">
            <thead><tr><th className="th">Version</th><th className="th">Operation</th><th className="th">Datastore</th><th className="th">Rules</th><th className="th">Checksum</th><th className="th">Status</th><th className="th">By / when</th><th className="th" /></tr></thead>
            <tbody>{versions.data.map((v) => (
              <tr key={v.id}>
                <td className="td mono">v{v.versionNo}</td><td className="td text-xs">{v.operation}</td><td className="td text-xs">{v.datastore}</td>
                <td className="td text-xs">{v.ruleCount}</td><td className="td mono text-[11px]">{v.checksum.slice(0, 12)}</td>
                <td className="td"><StatusBadge value={v.status} />{v.message && <div className="max-w-xs truncate text-[11px] text-slate-500" title={v.message}>{v.message}</div>}</td>
                <td className="td text-xs">{v.createdBy}<div className="text-slate-500">{dateTime(v.createdAt)}</div></td>
                <td className="td"><div className="flex gap-1">
                  <Button variant="ghost" className="px-2 text-xs" onClick={async () => { try { const r = await api.get<{ configXml: string }>(`/netconf/devices/${device.id}/versions/${v.id}`); setXml({ title: `Version ${v.versionNo}`, body: r.data.configXml }); } catch (e) { toast.error('Cannot load version', errorMessage(e)); } }}>View</Button>
                  {canOperate && v.status !== 'FAILED' && <Button variant="ghost" className="px-2 text-xs" icon={<RotateCcw className="h-3 w-3" />} onClick={() => setRollback(v)}>Rollback</Button>}
                </div></td>
              </tr>))}
            </tbody>
          </table>
        )}
      </Card>

      <Modal open={!!xml} title={xml?.title ?? ''} wide onClose={() => setXml(null)}>
        {xml && <XmlView xml={xml.body} />}
      </Modal>
      <ConfirmDialog open={!!rollback} title="Roll back configuration" confirmLabel="Roll back" busy={busy === 'rb'}
        message={<>Re-apply configuration version <b>v{rollback?.versionNo}</b> to {device.name}? The device commits it and the VNF enforces it immediately. The platform's desired policy is not changed, so the sync check will report drift until both agree.</>}
        onConfirm={() => op('rb', async () => { result((await api.post<OperationResult>(`/netconf/devices/${device.id}/versions/${rollback!.id}/rollback`)).data, `Rollback to v${rollback!.versionNo}`); setRollback(null); })}
        onCancel={() => setRollback(null)} />
      <ConfirmDialog open={del} title="Remove NETCONF device" danger confirmLabel="Remove" busy={busy === 'del'}
        message={<>Remove <b>{device.name}</b> and its configuration history from the platform? The device itself is not changed.</>}
        onConfirm={() => op('del', async () => { await api.delete(`/netconf/devices/${device.id}`); toast.success('Device removed'); setDel(false); })}
        onCancel={() => setDel(false)} />
    </div>
  );
}

/** Syntax-highlighted, indented XML (display only). */
export function XmlView({ xml }: { xml: string }) {
  const lines = prettyXml(xml).split('\n');
  return (
    <div>
      <pre className="mono max-h-[60vh] w-full overflow-auto rounded-xl border border-white/10 bg-[#060a14]/80 p-4 text-xs leading-relaxed">
        {lines.map((line, i) => (
          <div key={i} className="flex">
            <span className="mr-4 w-7 shrink-0 text-right text-slate-600 select-none">{i + 1}</span>
            <span>{line.split(/(<\/?[\w:.-]+|\/?>|[\w:.-]+="[^"]*")/g).map((tok, j) => {
              if (/^<\/?[\w:.-]+$/.test(tok) || /^\/?>$/.test(tok)) return <span key={j} className="text-[#8ba3ff]">{tok}</span>;
              if (/^[\w:.-]+="[^"]*"$/.test(tok)) {
                const k = tok.indexOf('=');
                return <span key={j}><span className="text-[#a78bfa]">{tok.slice(0, k)}</span>=<span className="text-emerald-300">{tok.slice(k + 1)}</span></span>;
              }
              return <span key={j} className="text-slate-200">{tok}</span>;
            })}</span>
          </div>
        ))}
      </pre>
    </div>
  );
}

/** Indents XML for display; leaf elements (<a>text</a>) stay on one line. Display only – never parsed. */
export function prettyXml(xml: string): string {
  const tokens = xml.replace(/>\s+</g, '><').match(/<[^>]+>|[^<]+/g) ?? [];
  const lines: string[] = [];
  let depth = 0;
  for (let i = 0; i < tokens.length; i++) {
    const t = tokens[i];
    const isClose = t.startsWith('</');
    const isOpen = t.startsWith('<') && !isClose && !t.endsWith('/>') && !t.startsWith('<?') && !t.startsWith('<!');
    // leaf: <tag>text</tag>
    if (isOpen && tokens[i + 1] && !tokens[i + 1].startsWith('<') && tokens[i + 2]?.startsWith('</')) {
      lines.push('  '.repeat(depth) + t + tokens[i + 1].trim() + tokens[i + 2]);
      i += 2;
      continue;
    }
    if (isClose) depth = Math.max(depth - 1, 0);
    if (t.trim()) lines.push('  '.repeat(depth) + t.trim());
    if (isOpen) depth++;
  }
  return lines.join('\n');
}

function AddDeviceModal({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: () => void }) {
  const toast = useToast();
  const vnfs = useApi(() => (open ? api.get<Vnf[]>('/vnfs').then((r) => r.data) : Promise.resolve([] as Vnf[])), [open]);
  const [f, setF] = useState({ name: '', host: '', port: '830', username: '', password: '', vnfId: '' });
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await api.post('/netconf/devices', { name: f.name, host: f.host, port: Number(f.port), username: f.username, password: f.password, vnfId: f.vnfId ? Number(f.vnfId) : null });
      toast.success('NETCONF device registered', 'Credential stored encrypted (AES-GCM).');
      setF({ name: '', host: '', port: '830', username: '', password: '', vnfId: '' });
      onDone();
      onClose();
    } catch (err) { toast.error('Registration failed', errorMessage(err)); } finally { setBusy(false); }
  };
  return (
    <Modal open={open} title="Register NETCONF device" onClose={onClose} footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" type="submit" form="nc-form" loading={busy}>Register</Button></>}>
      <form id="nc-form" onSubmit={submit} className="grid grid-cols-2 gap-3">
        <Field label="Name"><input required className="input" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
        <Field label="Linked VNF (optional)"><select className="input" value={f.vnfId} onChange={(e) => setF({ ...f, vnfId: e.target.value })}><option value="">None</option>{vnfs.data?.map((v) => <option key={v.id} value={v.id}>{v.name}</option>)}</select></Field>
        <Field label="Host"><input required className="input mono" value={f.host} onChange={(e) => setF({ ...f, host: e.target.value.trim() })} /></Field>
        <Field label="Port"><input required className="input" type="number" value={f.port} onChange={(e) => setF({ ...f, port: e.target.value })} /></Field>
        <Field label="Username"><input required className="input" value={f.username} onChange={(e) => setF({ ...f, username: e.target.value.trim() })} /></Field>
        <Field label="Password" hint="Never displayed again"><input required className="input" type="password" autoComplete="new-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} /></Field>
      </form>
    </Modal>
  );
}
