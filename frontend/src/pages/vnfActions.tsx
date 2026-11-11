import { Play, Power, RotateCcw, Rocket, Square, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { api, errorMessage } from '../api/client';
import type { Vnf } from '../api/types';
import { CAN_OPERATE, useAuth } from '../auth/AuthContext';
import { useToast } from '../components/Toast';
import { Button, ConfirmDialog } from '../components/ui';

type Op = 'deploy' | 'start' | 'stop' | 'restart' | 'terminate' | 'delete';

const META: Record<Op, { label: string; icon: typeof Play; danger?: boolean; confirm: (v: Vnf) => string }> = {
  deploy: { label: 'Deploy', icon: Rocket, confirm: (v) => `Instantiate ${v.name} on the ${v.orchestrator === 'OSM' ? 'OSM-managed VIM' : 'NFV lab (Docker)'}? The VNF moves to INSTANTIATING until the infrastructure confirms it is running.` },
  start: { label: 'Start', icon: Play, confirm: (v) => `Start ${v.name}? Traffic will be filtered again once the agent is healthy.` },
  stop: { label: 'Stop', icon: Square, danger: true, confirm: (v) => `Stop ${v.name}? Traffic routed through this firewall will NOT be forwarded while it is stopped.` },
  restart: { label: 'Restart', icon: RotateCcw, confirm: (v) => `Restart ${v.name}? Forwarding is interrupted briefly; the policy is re-applied afterwards.` },
  terminate: { label: 'Terminate', icon: Power, danger: true, confirm: (v) => `Terminate ${v.name}? Its infrastructure resources are released. Rules are kept and can be re-deployed.` },
  delete: { label: 'Delete', icon: Trash2, danger: true, confirm: (v) => `Permanently delete the record of ${v.name} including its rules and history?` },
};

export function availableOps(v: Vnf): Op[] {
  switch (v.state) {
    case 'CREATED': return ['deploy', 'delete'];
    case 'ACTIVE': return ['stop', 'restart', 'terminate'];
    case 'STOPPED': return ['start', 'terminate'];
    case 'FAILED': return v.externalId ? ['deploy', 'start', 'terminate'] : ['deploy', 'delete'];
    case 'TERMINATED': return ['delete'];
    default: return [];
  }
}

export function VnfActions({ vnf, onDone, compact }: { vnf: Vnf; onDone: () => void; compact?: boolean }) {
  const { hasRole } = useAuth();
  const toast = useToast();
  const [pending, setPending] = useState<Op | null>(null);
  const [busy, setBusy] = useState(false);
  if (!hasRole(...CAN_OPERATE)) return null;

  const run = async () => {
    if (!pending) return;
    setBusy(true);
    try {
      if (pending === 'delete') {
        await api.delete(`/vnfs/${vnf.id}`);
        toast.success(`${vnf.name} deleted`);
      } else {
        const r = await api.post<Vnf>(`/vnfs/${vnf.id}/${pending}`);
        toast.info(`${META[pending].label} of ${vnf.name} accepted`, `State: ${r.data.state}. Completion is confirmed by the orchestrator.`);
      }
      onDone();
    } catch (e) {
      toast.error(`${META[pending].label} failed`, errorMessage(e));
    } finally {
      setBusy(false);
      setPending(null);
    }
  };

  return (
    <>
      <div className="flex flex-wrap gap-1.5">
        {availableOps(vnf).map((op) => {
          const M = META[op];
          return (
            <Button key={op} variant={M.danger ? 'ghost' : 'secondary'} className={compact ? 'px-2 py-1 text-xs' : ''}
              icon={<M.icon className={compact ? 'h-3.5 w-3.5' : 'h-4 w-4'} />} onClick={() => setPending(op)}
              title={M.label}>{M.label}</Button>
          );
        })}
      </div>
      <ConfirmDialog open={!!pending} title={pending ? `${META[pending].label} VNF` : ''} message={pending ? META[pending].confirm(vnf) : ''}
        confirmLabel={pending ? META[pending].label : ''} danger={pending ? !!META[pending].danger : false} busy={busy}
        onConfirm={run} onCancel={() => setPending(null)} />
    </>
  );
}
