import {
  Bell, CheckCheck, ChevronsLeft, ChevronsRight, KeyRound, LogOut, Menu, RefreshCw, Search, Server, Settings, X,
} from 'lucide-react';
import { Suspense, useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { api, errorMessage } from '../api/client';
import type { Alert, AlertStats, Connection, Page, Role } from '../api/types';
import { CAN_OPERATE, CAN_SECURITY, useAuth } from '../auth/AuthContext';
import { useApi } from '../hooks/useApi';
import { ago } from '../utils/format';
import { Wordmark, LogoMark } from './Brand';
import { CommandPalette } from './CommandPalette';
import { AmbientBackground } from './effects';
import { ErrorBoundary } from './ErrorBoundary';
import { NAV } from './nav';
import { useToast } from './Toast';
import { Badge, Spinner, StatusBadge, cx } from './ui';

export { NAV } from './nav';

const roleLabel: Record<Role, string> = {
  ADMIN: 'Administrator', NETWORK_OPERATOR: 'Network Operator', SECURITY_ANALYST: 'Security Analyst',
};

function readCollapsed() {
  try { return localStorage.getItem('nexus.sidebar') === 'collapsed'; } catch { return false; }
}

/** Dropdown anchored to a trigger; closes on outside click or Escape. */
function Popover({ open, onClose, children, className }: { open: boolean; onClose: () => void; children: ReactNode; className?: string }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const click = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && onClose();
    const key = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    setTimeout(() => document.addEventListener('mousedown', click), 0);
    document.addEventListener('keydown', key);
    return () => { document.removeEventListener('mousedown', click); document.removeEventListener('keydown', key); };
  }, [open, onClose]);
  if (!open) return null;
  return <div ref={ref} className={cx('glass-strong absolute right-0 top-full z-40 mt-2 rounded-xl shadow-2xl animate-[fade-up_0.18s_ease-out]', className)}>{children}</div>;
}

function SidebarNav({ collapsed, onNavigate, activeAlerts }: { collapsed: boolean; onNavigate?: () => void; activeAlerts: number }) {
  const { hasRole } = useAuth();
  return (
    <nav className="flex-1 overflow-y-auto px-2.5 py-3" aria-label="Main">
      {NAV.map((s) => {
        const items = s.items.filter((i) => !i.roles || hasRole(...i.roles));
        if (!items.length) return null;
        return (
          <div key={s.section} className="mb-4">
            {!collapsed && <div className="px-2.5 pb-1.5 text-[10px] font-semibold tracking-[0.16em] text-slate-500 uppercase">{s.section}</div>}
            {items.map((i) => (
              <NavLink key={i.to} to={i.to} onClick={onNavigate} title={collapsed ? i.label : undefined}
                className={({ isActive }) => cx(
                  'group relative mb-0.5 flex items-center gap-2.5 rounded-xl px-2.5 py-2 text-sm transition-all',
                  collapsed && 'justify-center',
                  isActive ? 'bg-gradient-to-r from-[#6c8cff]/20 to-[#a78bfa]/5 text-white ring-1 ring-[#6c8cff]/30 shadow-[0_0_20px_-6px_rgb(108_140_255/0.6)]'
                    : 'text-slate-400 hover:bg-white/[0.05] hover:text-slate-100',
                )}>
                {({ isActive }) => (
                  <>
                    {isActive && <span className="absolute top-2 bottom-2 left-0 w-0.5 rounded-full bg-gradient-to-b from-[#8ba3ff] to-[#38d9ff]" />}
                    <i.icon className={cx('h-4 w-4 shrink-0', isActive ? 'text-accent-300' : 'text-slate-500 group-hover:text-slate-300')} />
                    {!collapsed && <span className="flex-1 truncate">{i.label}</span>}
                    {i.to === '/alerts' && activeAlerts > 0 && (
                      <span className={cx('rounded-md bg-rose-500/20 px-1.5 text-[10px] font-semibold text-rose-200', collapsed && 'absolute top-0.5 right-0.5')}>{activeAlerts}</span>
                    )}
                  </>
                )}
              </NavLink>
            ))}
          </div>
        );
      })}
    </nav>
  );
}

export default function Layout() {
  const { user, logout, hasRole } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [menu, setMenu] = useState<'none' | 'notif' | 'infra' | 'profile'>('none');
  const closeMenu = useCallback(() => setMenu('none'), []);

  const alertStats = useApi(() => api.get<AlertStats>('/alerts/stats').then((r) => r.data), [], 30000);
  const openAlerts = useApi(() => api.get<Page<Alert>>('/alerts', { params: { status: 'OPEN', size: 6 } }).then((r) => r.data), [], 30000);
  const infra = useApi(() => api.get<Connection[]>('/integrations').then((r) => r.data), [], 30000);
  const activeAlerts = (alertStats.data?.open ?? 0) + (alertStats.data?.acknowledged ?? 0);
  const conns = (infra.data ?? []).filter((c) => c.status !== 'DISABLED');
  const up = conns.filter((c) => c.status === 'UP').length;
  const infraTone = !conns.length ? 'bg-slate-500' : up === conns.length ? 'bg-emerald-400' : up === 0 ? 'bg-rose-400' : 'bg-amber-400';

  useEffect(() => {
    try { localStorage.setItem('nexus.sidebar', collapsed ? 'collapsed' : 'expanded'); } catch { /* storage unavailable */ }
  }, [collapsed]);

  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setSearchOpen(true); }
    };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, []);

  useEffect(() => { setMobileOpen(false); setMenu('none'); }, [location.pathname]);

  const acknowledge = async (a: Alert) => {
    try {
      await api.patch(`/alerts/${a.id}`, { status: 'ACKNOWLEDGED', assignedTo: user?.username });
      toast.success(`${a.alertId} acknowledged`);
      void openAlerts.reload(true);
      void alertStats.reload(true);
    } catch (e) { toast.error('Acknowledge failed', errorMessage(e)); }
  };

  const checkInfra = async () => {
    try { await api.post('/integrations/check'); void infra.reload(true); toast.success('Integration health refreshed'); }
    catch (e) { toast.error('Health check failed', errorMessage(e)); }
  };

  const signOut = async () => {
    await logout();
    navigate('/login');
  };

  const sidebar = (mobile: boolean) => (
    <>
      <div className={cx('flex h-16 items-center border-b border-white/[0.07] px-4', collapsed && !mobile ? 'justify-center px-2' : 'justify-between')}>
        <Link to="/" title="NEXUS NFV home">{collapsed && !mobile ? <LogoMark /> : <Wordmark />}</Link>
        {mobile && <button onClick={() => setMobileOpen(false)} className="rounded-lg p-1.5 text-slate-400 hover:bg-white/10" aria-label="Close navigation"><X className="h-4 w-4" /></button>}
      </div>
      <SidebarNav collapsed={collapsed && !mobile} onNavigate={mobile ? () => setMobileOpen(false) : undefined} activeAlerts={activeAlerts} />
      {!mobile && (
        <button onClick={() => setCollapsed((c) => !c)} className="m-2.5 flex items-center justify-center gap-2 rounded-xl border border-white/[0.07] py-2 text-xs text-slate-400 hover:bg-white/[0.05] hover:text-slate-200"
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}>
          {collapsed ? <ChevronsRight className="h-4 w-4" /> : <><ChevronsLeft className="h-4 w-4" /> Collapse</>}
        </button>
      )}
    </>
  );

  return (
    <div className="relative flex h-full">
      <AmbientBackground intensity="subtle" />

      {/* desktop sidebar */}
      <aside className={cx('relative z-20 hidden shrink-0 flex-col border-r border-white/[0.07] bg-[#0b1222]/70 backdrop-blur-xl transition-[width] duration-300 lg:flex', collapsed ? 'w-[76px]' : 'w-64')}>
        {sidebar(false)}
      </aside>

      {/* mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <div className="absolute inset-0 bg-[#030612]/70 backdrop-blur-sm" onClick={() => setMobileOpen(false)} />
          <aside className="glass-strong relative flex h-full w-72 max-w-[85vw] flex-col animate-[fade-up_0.2s_ease-out]">{sidebar(true)}</aside>
        </div>
      )}

      <div className="relative z-10 flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-16 shrink-0 items-center gap-2 border-b border-white/[0.07] bg-[#080d1a]/60 px-3 backdrop-blur-xl sm:gap-3 sm:px-6">
          <button onClick={() => setMobileOpen(true)} className="rounded-lg p-2 text-slate-300 hover:bg-white/10 lg:hidden" aria-label="Open navigation"><Menu className="h-5 w-5" /></button>
          <div className="lg:hidden"><LogoMark className="h-7 w-7" /></div>

          <button onClick={() => setSearchOpen(true)}
            className="flex min-w-0 flex-1 items-center gap-2 rounded-xl border border-white/10 bg-white/[0.04] px-3 py-2 text-sm text-slate-500 transition-colors hover:border-white/20 hover:text-slate-300 sm:max-w-md"
            aria-label="Open global search">
            <Search className="h-4 w-4 shrink-0" />
            <span className="truncate">Search pages, VNFs, IPs…</span>
            <kbd className="ml-auto hidden rounded border border-white/15 px-1.5 text-[10px] text-slate-500 sm:inline">Ctrl K</kbd>
          </button>

          <div className="ml-auto flex items-center gap-1 sm:gap-2">
            {/* infrastructure status */}
            <div className="relative">
              <button onClick={() => setMenu(menu === 'infra' ? 'none' : 'infra')} className="flex items-center gap-2 rounded-xl px-2.5 py-2 text-xs text-slate-300 hover:bg-white/[0.07]"
                aria-label="Infrastructure status" aria-expanded={menu === 'infra'}>
                <span className="relative flex h-2.5 w-2.5"><span className={cx('absolute inline-flex h-full w-full animate-ping rounded-full opacity-50', infraTone)} /><span className={cx('relative inline-flex h-2.5 w-2.5 rounded-full', infraTone)} /></span>
                <span className="hidden md:inline">{infra.data ? `${up}/${conns.length} integrations up` : 'Checking…'}</span>
                <Server className="h-4 w-4 md:hidden" />
              </button>
              <Popover open={menu === 'infra'} onClose={closeMenu} className="w-80">
                <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
                  <span className="text-sm font-semibold text-fg">Infrastructure</span>
                  {hasRole(...CAN_OPERATE) && <button onClick={checkInfra} className="flex items-center gap-1 text-xs text-accent-300 hover:text-accent-200"><RefreshCw className="h-3 w-3" /> Check now</button>}
                </div>
                <ul className="max-h-80 overflow-y-auto p-2">
                  {(infra.data ?? []).map((c) => (
                    <li key={c.type + c.name} className="rounded-lg px-2 py-2 hover:bg-white/[0.04]">
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-sm text-slate-200">{c.type} <span className="text-slate-500">· {c.name}</span></span>
                        <span className="flex gap-1">{c.mode === 'DEMO' && <Badge tone="violet">DEMO</Badge>}<StatusBadge value={c.status} /></span>
                      </div>
                      <div className="truncate text-[11px] text-slate-500" title={c.lastError ?? c.details}>{c.lastError ?? c.details ?? c.endpoint ?? '—'}</div>
                    </li>
                  ))}
                  {!infra.data?.length && <li className="px-2 py-4 text-center text-sm text-slate-500">No health data yet</li>}
                </ul>
                <Link to="/settings" className="block border-t border-white/10 px-4 py-2.5 text-xs text-accent-300 hover:bg-white/[0.04]">Integration settings →</Link>
              </Popover>
            </div>

            {/* notifications */}
            <div className="relative">
              <button onClick={() => setMenu(menu === 'notif' ? 'none' : 'notif')} className="relative rounded-xl p-2 text-slate-300 hover:bg-white/[0.07]"
                aria-label={`${activeAlerts} active alerts`} aria-expanded={menu === 'notif'}>
                <Bell className="h-5 w-5" />
                {activeAlerts > 0 && <span className="absolute top-1 right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-500 px-1 text-[9px] font-bold text-white">{activeAlerts > 99 ? '99+' : activeAlerts}</span>}
              </button>
              <Popover open={menu === 'notif'} onClose={closeMenu} className="w-[22rem] max-w-[calc(100vw-1.5rem)]">
                <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
                  <span className="text-sm font-semibold text-fg">Open security alerts</span>
                  <span className="text-xs text-slate-500">{alertStats.data?.open ?? 0} open</span>
                </div>
                <ul className="max-h-96 overflow-y-auto p-2">
                  {!openAlerts.data?.content.length && <li className="px-2 py-6 text-center text-sm text-slate-500">No open alerts</li>}
                  {openAlerts.data?.content.map((a) => (
                    <li key={a.id} className="flex items-start gap-2 rounded-lg px-2 py-2 hover:bg-white/[0.04]">
                      <StatusBadge value={a.severity} />
                      <button className="min-w-0 flex-1 text-left" onClick={() => { closeMenu(); navigate(`/alerts?sourceIp=${a.sourceIp ?? ''}`); }}>
                        <div className="truncate text-sm text-slate-200">{a.eventType}</div>
                        <div className="truncate text-[11px] text-slate-500"><span className="mono">{a.sourceIp}</span> · {ago(a.lastSeen)}</div>
                      </button>
                      {hasRole(...CAN_SECURITY) && (
                        <button onClick={() => acknowledge(a)} className="rounded-md p-1 text-slate-400 hover:bg-white/10 hover:text-accent-200" title="Acknowledge" aria-label={`Acknowledge ${a.alertId}`}>
                          <CheckCheck className="h-4 w-4" />
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
                <Link to="/alerts" onClick={closeMenu} className="block border-t border-white/10 px-4 py-2.5 text-xs text-accent-300 hover:bg-white/[0.04]">View all alerts →</Link>
              </Popover>
            </div>

            {/* profile */}
            <div className="relative">
              <button onClick={() => setMenu(menu === 'profile' ? 'none' : 'profile')} className="flex items-center gap-2.5 rounded-xl py-1 pr-2 pl-1 hover:bg-white/[0.07]"
                aria-label="Profile menu" aria-expanded={menu === 'profile'}>
                <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-[#6c8cff] to-[#a78bfa] text-xs font-bold text-white">
                  {(user?.fullName ?? user?.username ?? '?').split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase()}
                </span>
                <span className="hidden text-left sm:block">
                  <span className="block text-sm leading-tight font-medium text-slate-100">{user?.fullName}</span>
                  <span className="block text-[11px] leading-tight text-slate-500">{user && roleLabel[user.role]}</span>
                </span>
              </button>
              <Popover open={menu === 'profile'} onClose={closeMenu} className="w-60 p-1.5">
                <div className="border-b border-white/10 px-3 py-2.5">
                  <div className="text-sm font-medium text-fg">{user?.username}</div>
                  <div className="truncate text-xs text-slate-500">{user?.email}</div>
                </div>
                <Link to="/settings" onClick={closeMenu} className="mt-1 flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-slate-300 hover:bg-white/[0.06]"><Settings className="h-4 w-4" /> Settings</Link>
                <Link to="/settings#password" onClick={closeMenu} className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-slate-300 hover:bg-white/[0.06]"><KeyRound className="h-4 w-4" /> Change password</Link>
                <button onClick={signOut} className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-rose-200 hover:bg-rose-500/10"><LogOut className="h-4 w-4" /> Sign out</button>
              </Popover>
            </div>
          </div>
        </header>

        <main className="min-w-0 flex-1 overflow-x-hidden overflow-y-auto px-3 py-5 sm:px-6 lg:px-8">
          <div key={location.pathname} className="page-enter mx-auto max-w-[1600px]">
            <ErrorBoundary resetKey={location.pathname}>
              <Suspense fallback={<Spinner />}><Outlet /></Suspense>
            </ErrorBoundary>
          </div>
        </main>
      </div>
      <CommandPalette open={searchOpen} onClose={() => setSearchOpen(false)} />
    </div>
  );
}
