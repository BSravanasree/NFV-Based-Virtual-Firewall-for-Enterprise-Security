import { ArrowRight, Github, Menu, X } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Wordmark } from '../components/Brand';
import { cx } from '../components/ui';

/** Optional public repository link, set at build time (VITE_GITHUB_URL). Hidden when not configured. */
export const GITHUB_URL = (import.meta.env.VITE_GITHUB_URL as string | undefined)?.trim() || '';

const LINKS = [
  { label: 'Home', href: '/#top' },
  { label: 'Platform', href: '/#platform' },
  { label: 'Features', href: '/#features' },
  { label: 'Architecture', href: '/#architecture' },
  { label: 'Security', href: '/#security' },
  { label: 'Documentation', href: '/docs' },
];

function SiteLink({ href, children, className, onClick }: { href: string; children: ReactNode; className?: string; onClick?: () => void }) {
  if (href.startsWith('/#')) {
    return <a href={href.slice(1)} className={className} onClick={onClick}>{children}</a>;
  }
  return <Link to={href} className={className} onClick={onClick}>{children}</Link>;
}

/** Public website navigation (glass bar). On non-landing pages, anchor links route back to the landing page. */
export function SiteNav({ onLanding = false }: { onLanding?: boolean }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const h = () => setScrolled(window.scrollY > 12);
    h();
    window.addEventListener('scroll', h, { passive: true });
    return () => window.removeEventListener('scroll', h);
  }, []);

  const link = (l: (typeof LINKS)[number], cls: string, close?: boolean) => {
    const onClick = close ? () => setOpen(false) : undefined;
    // anchors are in-page on the landing page; elsewhere they route to "/#section" (landing scrolls on mount)
    if (l.href.startsWith('/#') && !onLanding) {
      return <Link key={l.label} to={l.href} className={cls} onClick={onClick}>{l.label}</Link>;
    }
    return <SiteLink key={l.label} href={l.href} className={cls} onClick={onClick}>{l.label}</SiteLink>;
  };

  return (
    <header className={cx('fixed inset-x-0 top-0 z-40 transition-all duration-300', scrolled ? 'py-2' : 'py-4')}>
      <div className={cx('mx-auto flex max-w-7xl items-center gap-4 rounded-2xl px-4 py-2.5 transition-all duration-300 sm:px-5',
        scrolled ? 'mx-3 border border-white/10 bg-[#0b1222]/70 shadow-xl backdrop-blur-xl xl:mx-auto' : 'border border-transparent')}>
        <Link to="/" aria-label="NEXUS NFV home"><Wordmark /></Link>
        <nav className="ml-6 hidden items-center gap-1 lg:flex" aria-label="Website">
          {LINKS.map((l) => link(l, 'rounded-lg px-3 py-1.5 text-sm text-slate-300 transition-colors hover:bg-white/[0.06] hover:text-white'))}
        </nav>
        <div className="ml-auto hidden items-center gap-2 sm:flex">
          {!user && <Link to="/login" className="rounded-lg px-3 py-1.5 text-sm text-slate-200 hover:bg-white/[0.06]">Sign In</Link>}
          <button onClick={() => navigate(user ? '/overview' : '/login')}
            className="inline-flex items-center gap-1.5 rounded-xl border border-white/10 bg-gradient-to-b from-[#7b98ff] to-[#5a78f0] px-4 py-2 text-sm font-medium text-white shadow-[0_8px_24px_-8px_rgb(108_140_255/0.8)] transition-transform hover:brightness-110 active:scale-[0.97]">
            Launch Console <ArrowRight className="h-4 w-4" />
          </button>
        </div>
        <button className="ml-auto rounded-lg p-2 text-slate-200 hover:bg-white/10 sm:ml-2 lg:hidden" onClick={() => setOpen((o) => !o)} aria-label="Toggle menu" aria-expanded={open}>
          {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
      </div>
      {open && (
        <div className="glass-strong mx-3 mt-2 rounded-2xl p-3 lg:hidden">
          <nav className="flex flex-col" aria-label="Website mobile">
            {LINKS.map((l) => link(l, 'rounded-lg px-3 py-2.5 text-sm text-slate-200 hover:bg-white/[0.06]', true))}
            {!user && <Link to="/login" className="rounded-lg px-3 py-2.5 text-sm text-slate-200 hover:bg-white/[0.06]">Sign In</Link>}
            <Link to={user ? '/overview' : '/login'} className="mt-2 rounded-xl bg-gradient-to-b from-[#7b98ff] to-[#5a78f0] px-3 py-2.5 text-center text-sm font-medium text-white">Launch Console</Link>
          </nav>
        </div>
      )}
    </header>
  );
}

export function SiteFooter() {
  const col = (title: string, items: { label: string; to: string; ext?: boolean }[]) => (
    <div>
      <div className="mb-3 text-xs font-semibold tracking-[0.14em] text-slate-400 uppercase">{title}</div>
      <ul className="space-y-2">
        {items.map((i) => (
          <li key={i.label}>
            {i.ext ? <a href={i.to} target="_blank" rel="noreferrer noopener" className="text-sm text-slate-400 hover:text-white">{i.label}</a>
              : <Link to={i.to} className="text-sm text-slate-400 hover:text-white">{i.label}</Link>}
          </li>
        ))}
      </ul>
    </div>
  );
  return (
    <footer className="relative z-10 border-t border-white/[0.07] bg-[#060a14]/60 backdrop-blur">
      <div className="mx-auto grid max-w-7xl gap-10 px-6 py-14 sm:grid-cols-2 lg:grid-cols-5">
        <div className="lg:col-span-2">
          <Wordmark />
          <p className="mt-4 max-w-sm text-sm leading-relaxed text-slate-400">
            NEXUS NFV is an NFV- and SDN-based virtual firewall management platform: Ubuntu firewall VNFs with nftables,
            lifecycle orchestration, OpenFlow policy, NETCONF configuration and security analytics in one console.
          </p>
          {GITHUB_URL && (
            <a href={GITHUB_URL} target="_blank" rel="noreferrer noopener" className="mt-4 inline-flex items-center gap-2 text-sm text-slate-300 hover:text-white">
              <Github className="h-4 w-4" /> Source code
            </a>
          )}
        </div>
        {col('Platform', [
          { label: 'VNF lifecycle', to: '/#features' }, { label: 'Firewall policy', to: '/#features' },
          { label: 'SDN & OpenFlow', to: '/#architecture' }, { label: 'Threat detection', to: '/#security' },
        ])}
        {col('Technology', [
          { label: 'React · Vite · Tailwind', to: '/docs#stack' }, { label: 'Spring Boot · MySQL', to: '/docs#stack' },
          { label: 'nftables · Netopeer2', to: '/docs#stack' }, { label: 'ONOS · OSM · OVS', to: '/docs#stack' },
        ])}
        {col('Documentation', [
          { label: 'Overview', to: '/docs' }, { label: 'Integration status', to: '/docs#status' },
          { label: 'API reference (Swagger)', to: '/swagger-ui.html', ext: true }, { label: 'Sign in', to: '/login' },
        ])}
      </div>
      <div className="border-t border-white/[0.06] px-6 py-5 text-center text-xs text-slate-500">
        © {new Date().getFullYear()} NEXUS NFV · Final-year engineering project · Released under the MIT License
      </div>
    </footer>
  );
}
