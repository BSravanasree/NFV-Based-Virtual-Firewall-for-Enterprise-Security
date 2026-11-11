import { ArrowLeft, ArrowRight, Shield } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, errorMessage } from '../api/client';
import type { FirewallRule, Policy, RuleRequest, SyncResult } from '../api/types';
import { useToast } from '../components/Toast';
import { Button, Card, ErrorState, Field, PageHeader, Spinner, cx } from '../components/ui';
import { CIDR_RE, validPortSpec } from '../utils/format';
import { reportSync } from './FirewallPage';

const EMPTY: RuleRequest = {
  name: '', priority: 100, action: 'ALLOW', protocol: 'TCP', sourceCidr: '', destinationCidr: '', sourcePort: '',
  destinationPort: '', enabled: true, logEnabled: true, description: '',
};

/** Pure validation used by the form and by unit tests. Backend validation remains authoritative. */
export function validateRule(r: RuleRequest, usedPriorities: number[] = []): Record<string, string> {
  const e: Record<string, string> = {};
  if (!r.name.trim()) e.name = 'Required';
  else if (!/^[A-Za-z0-9 _.-]{1,64}$/.test(r.name.trim())) e.name = 'Letters, digits, space, "_", "." and "-" only (max 64)';
  if (!Number.isInteger(r.priority) || r.priority < 1 || r.priority > 65535) e.priority = '1 – 65535';
  else if (usedPriorities.includes(r.priority)) e.priority = 'Priority already used by another rule';
  if (r.sourceCidr && !CIDR_RE.test(r.sourceCidr)) e.sourceCidr = 'IPv4 address or CIDR, e.g. 10.10.0.0/24';
  if (r.destinationCidr && !CIDR_RE.test(r.destinationCidr)) e.destinationCidr = 'IPv4 address or CIDR';
  const portsAllowed = r.protocol === 'TCP' || r.protocol === 'UDP';
  for (const k of ['sourcePort', 'destinationPort'] as const) {
    const v = r[k];
    if (v) {
      if (!portsAllowed) e[k] = 'Ports require TCP or UDP';
      else if (!validPortSpec(v)) e[k] = 'Port 1-65535 or range like 1000-2000';
    }
  }
  return e;
}

export function describeRule(r: RuleRequest): string {
  const src = `${r.sourceCidr || 'any'}${r.sourcePort ? `:${r.sourcePort}` : ''}`;
  const dst = `${r.destinationCidr || 'any'}${r.destinationPort ? `:${r.destinationPort}` : ''}`;
  return `${r.action === 'ALLOW' ? 'Allow' : 'Deny'} ${r.protocol === 'ANY' ? 'all IPv4' : r.protocol} traffic from ${src} to ${dst}`;
}

/**
 * Firewall rule form (create / edit). Rendered inside the Firewall page side panel and on the stand-alone
 * editor route. The submit button lives outside (footer) and targets this form via `formId`.
 */
export function RuleForm({ vnfId, ruleId, formId, onSaved, onBusy }: {
  vnfId: string | number; ruleId?: string | number; formId: string; onSaved: () => void; onBusy?: (b: boolean) => void;
}) {
  const isNew = ruleId == null;
  const toast = useToast();
  const [form, setForm] = useState<RuleRequest>(EMPTY);
  const [policy, setPolicy] = useState<Policy | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const p = (await api.get<Policy>(`/firewall/vnfs/${vnfId}/policy`)).data;
        setPolicy(p);
        if (isNew) {
          const max = p.rules.reduce((m, r) => Math.max(m, r.priority), 0);
          setForm({ ...EMPTY, priority: Math.min(65535, Math.floor(max / 10) * 10 + 10) });
        } else {
          const r = (await api.get<FirewallRule>(`/firewall/rules/${ruleId}`)).data;
          setForm({
            name: r.name, priority: r.priority, action: r.action, protocol: r.protocol, sourceCidr: r.sourceCidr ?? '',
            destinationCidr: r.destinationCidr ?? '', sourcePort: r.sourcePort ?? '', destinationPort: r.destinationPort ?? '',
            enabled: r.enabled, logEnabled: r.logEnabled, description: r.description ?? '',
          });
        }
      } catch (e) {
        setLoadError(errorMessage(e));
      }
    })();
  }, [vnfId, ruleId, isNew]);

  const set = <K extends keyof RuleRequest>(k: K, v: RuleRequest[K]) => setForm((f) => ({ ...f, [k]: v }));

  const submit = async (ev: FormEvent) => {
    ev.preventDefault();
    const used = (policy?.rules ?? []).filter((r) => String(r.id) !== String(ruleId)).map((r) => r.priority);
    const e = validateRule(form, used);
    setErrors(e);
    if (Object.keys(e).length) return;
    const body = {
      ...form, name: form.name.trim(),
      sourceCidr: form.sourceCidr || null, destinationCidr: form.destinationCidr || null,
      sourcePort: form.sourcePort || null, destinationPort: form.destinationPort || null,
      description: form.description || null,
    };
    onBusy?.(true);
    try {
      const res = isNew
        ? await api.post<{ sync: SyncResult }>(`/firewall/vnfs/${vnfId}/rules`, body)
        : await api.put<{ sync: SyncResult }>(`/firewall/rules/${ruleId}`, body);
      reportSync(toast, res.data.sync, isNew ? `Rule "${body.name}" created` : `Rule "${body.name}" updated`);
      onSaved();
    } catch (err) {
      toast.error('Rule not saved', errorMessage(err));
    } finally {
      onBusy?.(false);
    }
  };

  if (loadError) return <ErrorState message={loadError} />;
  if (!policy) return <Spinner />;
  const portsAllowed = form.protocol === 'TCP' || form.protocol === 'UDP';

  return (
    <form id={formId} onSubmit={submit} className="grid grid-cols-1 gap-4 sm:grid-cols-2" noValidate aria-label="Firewall rule">
      <div className="sm:col-span-2 text-xs text-slate-400">VNF <b className="text-slate-200">{policy.vnfName}</b> · default {policy.defaultAction} · {policy.rules.length} existing rule(s)</div>
      <Field label="Rule name" error={errors.name}><input className="input" value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="allow-web-client1" /></Field>
      <Field label="Priority" error={errors.priority} hint="Lower = evaluated first"><input className="input" type="number" value={form.priority} onChange={(e) => set('priority', Number(e.target.value))} /></Field>

      <div className="sm:col-span-2">
        <span className="label">Action</span>
        <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Action">
          {(['ALLOW', 'DENY'] as const).map((a) => (
            <button key={a} type="button" role="radio" aria-checked={form.action === a} onClick={() => set('action', a)}
              className={cx('rounded-xl border px-3 py-2.5 text-left text-sm transition-all',
                form.action === a
                  ? a === 'ALLOW' ? 'border-emerald-400/50 bg-emerald-400/10 text-emerald-200' : 'border-rose-400/50 bg-rose-400/10 text-rose-200'
                  : 'border-white/10 bg-white/[0.03] text-slate-400 hover:border-white/20')}>
              <div className="font-semibold">{a === 'ALLOW' ? 'ALLOW (accept)' : 'DENY (drop)'}</div>
              <div className="text-[11px] opacity-80">{a === 'ALLOW' ? 'permit matching new connections' : 'silently drop matching packets'}</div>
            </button>
          ))}
        </div>
      </div>

      <Field label="Protocol">
        <select className="input" value={form.protocol} onChange={(e) => {
          const proto = e.target.value as RuleRequest['protocol'];
          setForm((f) => ({ ...f, protocol: proto, ...(proto === 'TCP' || proto === 'UDP' ? {} : { sourcePort: '', destinationPort: '' }) }));
        }}>
          <option value="TCP">TCP</option><option value="UDP">UDP</option><option value="ICMP">ICMP</option><option value="ANY">ANY</option>
        </select>
      </Field>
      <div className="hidden sm:block" />
      <Field label="Source IP / CIDR" error={errors.sourceCidr} hint="Empty = any"><input className="input mono" value={form.sourceCidr} onChange={(e) => set('sourceCidr', e.target.value.trim())} placeholder="10.10.0.11/32" /></Field>
      <Field label="Destination IP / CIDR" error={errors.destinationCidr} hint="Empty = any"><input className="input mono" value={form.destinationCidr} onChange={(e) => set('destinationCidr', e.target.value.trim())} placeholder="10.20.0.10/32" /></Field>
      <Field label="Source port" error={errors.sourcePort}><input className="input mono" disabled={!portsAllowed} value={form.sourcePort} onChange={(e) => set('sourcePort', e.target.value.trim())} placeholder="any" /></Field>
      <Field label="Destination port" error={errors.destinationPort}><input className="input mono" disabled={!portsAllowed} value={form.destinationPort} onChange={(e) => set('destinationPort', e.target.value.trim())} placeholder="443 or 8000-8100" /></Field>
      <div className="sm:col-span-2"><Field label="Description"><input className="input" maxLength={255} value={form.description} onChange={(e) => set('description', e.target.value)} /></Field></div>
      <label className="flex items-center gap-2 text-sm text-slate-300"><input type="checkbox" className="accent-[#6c8cff]" checked={form.enabled} onChange={(e) => set('enabled', e.target.checked)} /> Enabled</label>
      <label className="flex items-center gap-2 text-sm text-slate-300"><input type="checkbox" className="accent-[#6c8cff]" checked={form.logEnabled} onChange={(e) => set('logEnabled', e.target.checked)} /> Log matches (NFLOG)</label>
      <div className="sm:col-span-2 rounded-xl border border-[#6c8cff]/25 bg-[#6c8cff]/[0.06] p-3 text-sm text-slate-200">
        <div className="label flex items-center gap-1.5"><Shield className="h-3.5 w-3.5" /> Effect preview</div>
        <div className="flex flex-wrap items-center gap-1.5">
          {describeRule(form)} <ArrowRight className="h-3.5 w-3.5 text-slate-500" />
          <span className={form.action === 'ALLOW' ? 'text-emerald-300' : 'text-rose-300'}>{form.action === 'ALLOW' ? 'accept' : 'drop'}</span>
        </div>
      </div>
    </form>
  );
}

/** Stand-alone editor route (deep-linkable); the Firewall page uses the same form in a side panel. */
export default function RuleEditorPage() {
  const { vnfId, ruleId } = useParams();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const back = () => navigate(`/firewall?vnf=${vnfId}`);
  return (
    <div className="max-w-3xl">
      <button onClick={back} className="mb-2 flex items-center gap-1 text-xs text-slate-400 hover:text-slate-200"><ArrowLeft className="h-3.5 w-3.5" /> Firewall</button>
      <PageHeader title={ruleId ? 'Edit firewall rule' : 'Add firewall rule'} />
      <Card>
        <RuleForm vnfId={vnfId!} ruleId={ruleId} formId="rule-form-page" onSaved={back} onBusy={setBusy} />
        <div className="mt-5 flex justify-end gap-2">
          <Button onClick={back}>Cancel</Button>
          <Button type="submit" form="rule-form-page" variant="primary" loading={busy}>{ruleId ? 'Save changes' : 'Create rule'}</Button>
        </div>
      </Card>
    </div>
  );
}
