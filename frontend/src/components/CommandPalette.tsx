import { ArrowRight, Boxes, Search, ShieldAlert, Activity } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { Vnf } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { IPV4_RE } from '../utils/format';
import { NAV } from './nav';
import { cx } from './ui';

interface Item { id: string; label: string; hint: string; icon: typeof Search; to: string }

/** Global search (Ctrl/⌘ + K): pages, VNFs by name/IP, and IP pivots into traffic and alerts. */
export function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const [q, setQ] = useState('');
  const [vnfs, setVnfs] = useState<Vnf[]>([]);
  const [active, setActive] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) return;
    setQ('');
    setActive(0);
    setTimeout(() => inputRef.current?.focus(), 0);
    api.get<Vnf[]>('/vnfs').then((r) => setVnfs(r.data)).catch(() => setVnfs([]));
  }, [open]);

  const items = useMemo<Item[]>(() => {
    const term = q.trim().toLowerCase();
    const pages: Item[] = NAV.flatMap((s) => s.items)
      .filter((i) => !i.roles || hasRole(...i.roles))
      .map((i) => ({ id: `p:${i.to}`, label: i.label, hint: 'Page', icon: i.icon, to: i.to }));
    const vnfItems: Item[] = vnfs.map((v) => ({
      id: `v:${v.id}`, label: v.name, hint: `${v.vnfId} · ${v.state} · ${v.lanIp ?? ''}`, icon: Boxes, to: `/vnfs/${v.id}`,
    }));
    const ipItems: Item[] = IPV4_RE.test(term) ? [
      { id: 'ip:t', label: `Traffic from ${term}`, hint: 'Traffic Analytics', icon: Activity, to: `/monitoring?srcIp=${term}` },
      { id: 'ip:a', label: `Alerts for ${term}`, hint: 'Security Alerts', icon: ShieldAlert, to: `/alerts?sourceIp=${term}` },
    ] : [];
    const all = [...ipItems, ...pages, ...vnfItems];
    if (!term) return all.slice(0, 12);
    return all.filter((i) => i.id.startsWith('ip:') || `${i.label} ${i.hint}`.toLowerCase().includes(term)).slice(0, 12);
  }, [q, vnfs, hasRole]);

  useEffect(() => setActive(0), [q]);

  if (!open) return null;
  const go = (i: Item | undefined) => {
    if (!i) return;
    onClose();
    navigate(i.to);
  };

  return (
    <div className="fixed inset-0 z-[70] flex items-start justify-center bg-[#030612]/70 px-4 pt-[12vh] backdrop-blur-sm" role="dialog" aria-modal="true" aria-label="Global search"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="glass-strong w-full max-w-xl overflow-hidden rounded-2xl shadow-2xl animate-[fade-up_0.2s_ease-out]">
        <div className="flex items-center gap-2 border-b border-white/10 px-4">
          <Search className="h-4 w-4 text-slate-400" />
          <input ref={inputRef} value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search pages, VNFs, or an IP address…"
            className="h-12 flex-1 bg-transparent text-sm text-fg placeholder:text-slate-500 focus:outline-none" aria-label="Search"
            onKeyDown={(e) => {
              if (e.key === 'Escape') onClose();
              if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, items.length - 1)); }
              if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)); }
              if (e.key === 'Enter') go(items[active]);
            }} />
          <kbd className="rounded border border-white/15 px-1.5 text-[10px] text-slate-400">ESC</kbd>
        </div>
        <ul className="max-h-[50vh] overflow-y-auto p-2" role="listbox">
          {items.length === 0 && <li className="px-3 py-6 text-center text-sm text-slate-500">No results</li>}
          {items.map((i, idx) => (
            <li key={i.id} role="option" aria-selected={idx === active}>
              <button onMouseEnter={() => setActive(idx)} onClick={() => go(i)}
                className={cx('flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm', idx === active ? 'bg-[#6c8cff]/15 text-fg' : 'text-slate-300')}>
                <i.icon className="h-4 w-4 shrink-0 text-accent-400" />
                <span className="flex-1 truncate">{i.label}</span>
                <span className="truncate text-xs text-slate-500">{i.hint}</span>
                {idx === active && <ArrowRight className="h-3.5 w-3.5 text-accent-300" />}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
