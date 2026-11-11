import {
  Activity, ArrowRight, BookOpen, Boxes, CheckCircle2, FileCode2, GitBranch, History, KeyRound, Layers, Lock, Network,
  Radar, ScrollText, Server, Shield, ShieldCheck, Sparkles, Users, Workflow,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { AmbientBackground, prefersReducedMotion, Reveal } from '../components/effects';
import { Badge, cx } from '../components/ui';
import { SiteFooter, SiteNav } from './site';

// ---------------------------------------------------------------- hero visual
function HeroNetwork() {
  const motion = !prefersReducedMotion();
  const node = (x: number, y: number, label: string, sub: string, color: string, Icon: typeof Server, size = 34) => (
    <g transform={`translate(${x},${y})`}>
      <circle r={size + 10} fill={color} opacity="0.08" />
      <circle r={size} fill="rgba(16,24,42,0.85)" stroke={color} strokeOpacity="0.7" strokeWidth="1.5" />
      <foreignObject x={-size / 2.4} y={-size / 2.4} width={size / 1.2} height={size / 1.2}><Icon width="100%" height="100%" color={color} /></foreignObject>
      <text y={size + 22} textAnchor="middle" fill="#f8fafc" fontSize="13" fontWeight="600">{label}</text>
      <text y={size + 38} textAnchor="middle" fill="#94a3b8" fontSize="11">{sub}</text>
    </g>
  );
  const paths = {
    a: 'M110 110 C 180 110, 190 210, 250 210',
    b: 'M110 310 C 180 310, 190 210, 250 210',
    sw: 'M290 210 L 380 210',
    fw: 'M460 210 L 610 210',
  };
  return (
    <svg viewBox="0 0 720 420" className="h-auto w-full" role="img"
      aria-label="Illustration: clients connect through a virtual switch to the virtual firewall, which forwards permitted traffic to the protected server">
      <defs>
        <radialGradient id="fwGlow"><stop offset="0" stopColor="#6c8cff" stopOpacity="0.55" /><stop offset="1" stopColor="#6c8cff" stopOpacity="0" /></radialGradient>
        <linearGradient id="line" x1="0" x2="1"><stop offset="0" stopColor="#38d9ff" /><stop offset="1" stopColor="#a78bfa" /></linearGradient>
        {Object.entries(paths).map(([k, d]) => <path key={k} id={`p-${k}`} d={d} />)}
        <path id="p-allowed" d={`${paths.a} L 380 210 L 610 210`} />
        <path id="p-blocked" d={`${paths.b} L 380 210`} />
      </defs>
      {Object.values(paths).map((d) => (
        <g key={d}>
          <path d={d} stroke="url(#line)" strokeOpacity="0.18" strokeWidth="6" fill="none" />
          <path d={d} stroke="url(#line)" strokeOpacity="0.8" strokeWidth="1.6" fill="none" className="flow-line" />
        </g>
      ))}
      <circle cx="420" cy="210" r="120" fill="url(#fwGlow)" />
      {motion && (
        <>
          {[0, 1.3, 2.6].map((d) => (
            <circle key={`a${d}`} r="5" fill="#34d399">
              <animateMotion dur="3.9s" begin={`${d}s`} repeatCount="indefinite"><mpath href="#p-allowed" /></animateMotion>
            </circle>
          ))}
          {[0.6, 2.4].map((d) => (
            <circle key={`b${d}`} r="5" fill="#fb7185">
              <animateMotion dur="3.6s" begin={`${d}s`} repeatCount="indefinite"><mpath href="#p-blocked" /></animateMotion>
              <animate attributeName="opacity" values="1;1;0" keyTimes="0;0.85;1" dur="3.6s" begin={`${d}s`} repeatCount="indefinite" />
            </circle>
          ))}
        </>
      )}
      {node(80, 110, 'Client A', '10.10.0.11', '#38d9ff', Users, 28)}
      {node(80, 310, 'Client B', '10.10.0.12', '#38d9ff', Users, 28)}
      {node(270, 210, 'vSwitch', 'Open vSwitch', '#a78bfa', GitBranch, 26)}
      <g transform="translate(420,210)">
        <circle r="52" fill="rgba(16,24,42,0.9)" stroke="#6c8cff" strokeWidth="2" />
        <circle r="60" fill="none" stroke="#6c8cff" strokeOpacity="0.35" strokeDasharray="4 8" className={motion ? 'flow-line' : undefined} />
        <foreignObject x="-22" y="-22" width="44" height="44"><Shield width="100%" height="100%" color="#8ba3ff" /></foreignObject>
        <text y="82" textAnchor="middle" fill="#f8fafc" fontSize="14" fontWeight="700">Virtual Firewall VNF</text>
        <text y="99" textAnchor="middle" fill="#94a3b8" fontSize="11">Ubuntu · nftables · NETCONF</text>
      </g>
      {node(640, 210, 'Protected Server', '10.20.0.10', '#34d399', Server, 30)}
    </svg>
  );
}

function FloatingCard({ className, children }: { className: string; children: React.ReactNode }) {
  return (
    <div className={cx('glass absolute hidden animate-float px-3.5 py-2.5 text-xs whitespace-nowrap shadow-xl md:block', className)}>{children}</div>
  );
}

// ---------------------------------------------------------------- data
const FEATURES = [
  { icon: Shield, title: 'Virtual Firewall Management', text: 'Ordered allow/deny rules by IP, CIDR, port and protocol, compiled to nftables and applied atomically inside each VNF.' },
  { icon: Boxes, title: 'NFV Lifecycle Orchestration', text: 'Create, deploy, start, stop, restart, scale and terminate VNFs through a state machine confirmed by the infrastructure.' },
  { icon: Server, title: 'SDN Controller Integration', text: 'ONOS northbound REST: devices, hosts, links, ports and flow tables in one inventory, with an explicit demo mode.' },
  { icon: GitBranch, title: 'OpenFlow Network Policies', text: 'Security intent compiled to OpenFlow 1.3 rules – drop, allow, forward or steer traffic into the firewall VNF.' },
  { icon: FileCode2, title: 'NETCONF Configuration', text: 'YANG-modelled policy over NETCONF/SSH: validate, commit, snapshot, roll back and detect drift per device.' },
  { icon: Activity, title: 'Real-Time Traffic Monitoring', text: 'Exact nftables counters and NFLOG connection events: allowed vs blocked, protocols, top talkers, CSV export.' },
  { icon: Radar, title: 'Security Threat Detection', text: 'Transparent, threshold-based detectors for port scans, brute force, repeated blocks and unexpected flows.' },
  { icon: ScrollText, title: 'Enterprise Audit Logging', text: 'Every login and change recorded with user, target, outcome and client IP – searchable, exportable, PDF reports.' },
];

type Status = { label: string; tone: 'green' | 'amber' | 'violet' | 'blue' };
const ARCH: { name: string; tech: string; icon: typeof Server; status: Status; detail: string }[] = [
  { name: 'React Frontend', tech: 'React 18 · TypeScript · Vite · Tailwind · Recharts', icon: Layers, status: { label: 'Verified', tone: 'green' },
    detail: 'Single-page console with role-aware navigation. Talks only to the versioned REST API through an nginx reverse proxy.' },
  { name: 'Spring Boot API', tech: 'Java 17+ · Spring Security (JWT) · JPA · Flyway · MySQL 8', icon: Server, status: { label: 'Verified', tone: 'green' },
    detail: 'Control plane: RBAC, audit, VNF lifecycle engine, firewall desired-state sync, telemetry collection and detection. 51 automated tests.' },
  { name: 'OSM Orchestration', tech: 'Open Source MANO NBI (SOL005) · SOL006 VNFD/NSD', icon: Workflow, status: { label: 'Implemented · not live-verified', tone: 'amber' },
    detail: 'Production orchestrator adapter (instantiate, operate, scale, terminate). Requires an OSM installation and a VIM; never simulated.' },
  { name: 'ONOS Controller', tech: 'ONOS 2.7 northbound REST', icon: Network, status: { label: 'Real client · demo fixture without controller', tone: 'violet' },
    detail: 'Reads devices, hosts, links and flows and installs policy flows. Without a controller the console runs in a clearly labelled DEMO mode.' },
  { name: 'OpenFlow / Open vSwitch', tech: 'OpenFlow 1.3 · OVS · Mininet lab', icon: GitBranch, status: { label: 'Lab script for Ubuntu hosts', tone: 'amber' },
    detail: 'Two OVS switches, two clients, the firewall and a server under ONOS control (lab/sdn). Requires the openvswitch kernel module.' },
  { name: 'Virtual Firewall VNF', tech: 'Ubuntu 24.04 · nftables · ulogd2 · Netopeer2/sysrepo', icon: Shield, status: { label: 'Verified in the NFV lab', tone: 'green' },
    detail: 'Hardened agent applies validated rulesets atomically, exposes exact counters and NFLOG events, and serves NETCONF on the management network.' },
  { name: 'Protected Network', tech: 'LAN 10.10.0.0/24 → DMZ 10.20.0.0/24', icon: Lock, status: { label: 'Lab networks', tone: 'blue' },
    detail: 'Clients reach the server only through the firewall VNF. 53 end-to-end checks verify allowed and blocked traffic in the data path.' },
];

const SECURITY = [
  { icon: ShieldCheck, title: 'Stateful firewall rules', text: 'Established and related connections accepted first, invalid state dropped, default-deny for everything else.' },
  { icon: Network, title: 'IP-based filtering', text: 'Source and destination addresses or CIDR blocks, validated and normalised before they reach the kernel.' },
  { icon: KeyRound, title: 'Port-based access control', text: 'TCP and UDP ports or ranges per rule; ICMP and protocol-level rules where ports do not apply.' },
  { icon: Activity, title: 'Network traffic monitoring', text: 'Per-rule hit counters, direction and protocol accounting, rate-limited connection logging.' },
  { icon: History, title: 'Configuration rollback', text: 'Every NETCONF push is versioned with a canonical checksum; any version can be committed again.' },
  { icon: Radar, title: 'Security event detection', text: 'Detections de-duplicated per source with severity, evidence and an analyst triage workflow.' },
  { icon: Users, title: 'Role-based access control', text: 'Administrator, network operator and security analyst roles enforced on every API call.' },
];

const FACTS = [
  { value: '53 / 53', label: 'end-to-end checks against live traffic' },
  { value: '51 + 27', label: 'backend and frontend automated tests' },
  { value: '≈ 6 s', label: 'VNF deploy to ACTIVE in the lab' },
  { value: '3 roles', label: 'enforced RBAC with full audit trail' },
];

// ---------------------------------------------------------------- page
export default function LandingPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [arch, setArch] = useState(5);
  const launch = () => navigate(user ? '/overview' : '/login');

  useEffect(() => {
    if (!location.hash) return;
    const el = document.getElementById(location.hash.slice(1));
    if (el) setTimeout(() => el.scrollIntoView({ behavior: prefersReducedMotion() ? 'auto' : 'smooth' }), 50);
  }, [location.hash]);

  const selected = ARCH[arch];

  return (
    <div id="top" className="relative min-h-full overflow-x-hidden">
      <AmbientBackground />
      <SiteNav onLanding />

      {/* ---------------- hero */}
      <section className="relative z-10 mx-auto grid max-w-7xl items-center gap-10 px-6 pt-32 pb-16 lg:grid-cols-[1.05fr_1fr] lg:pt-40">
        <div className="animate-[fade-up_0.6s_ease-out]">
          <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.05] px-3 py-1 text-xs text-slate-300 backdrop-blur">
            <Sparkles className="h-3.5 w-3.5 text-accent-300" /> NFV · SDN · NETCONF · nftables
          </div>
          <h1 className="text-4xl leading-[1.05] font-semibold tracking-tight text-fg sm:text-5xl lg:text-6xl">
            Enterprise Network Security, <span className="text-gradient">Reimagined.</span>
          </h1>
          <p className="mt-5 max-w-xl text-base leading-relaxed text-fg-muted sm:text-lg">
            Deploy, manage, and monitor virtual firewalls through an intelligent NFV and SDN-powered security platform.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <button onClick={launch} className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-gradient-to-b from-[#7b98ff] to-[#5a78f0] px-5 py-3 text-sm font-semibold text-white shadow-[0_12px_32px_-10px_rgb(108_140_255/0.9)] transition-all hover:brightness-110 active:scale-[0.97]">
              Get Started <ArrowRight className="h-4 w-4" />
            </button>
            <a href="#platform" className="inline-flex items-center gap-2 rounded-xl border border-white/15 bg-white/[0.05] px-5 py-3 text-sm font-semibold text-slate-100 backdrop-blur transition-all hover:bg-white/[0.1] active:scale-[0.97]">
              Explore Platform
            </a>
          </div>
          <div className="mt-8 flex flex-wrap gap-x-6 gap-y-2 text-xs text-slate-400">
            {['Default-deny by design', 'Atomic rule application', 'Every action audited'].map((t) => (
              <span key={t} className="flex items-center gap-1.5"><CheckCircle2 className="h-3.5 w-3.5 text-ok" /> {t}</span>
            ))}
          </div>
        </div>

        <div className="relative">
          <div className="glass relative overflow-hidden p-4 sm:p-6">
            <div className="mb-2 flex items-center justify-between text-[11px] text-slate-400">
              <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-ok" /> Service chain</span>
              <span>Illustration of the lab topology</span>
            </div>
            <HeroNetwork />
          </div>
          <FloatingCard className="-top-6 -left-6">
            <div className="text-slate-400">Firewall policy</div>
            <div className="mt-0.5 flex items-center gap-2 font-semibold text-fg">default-deny <Badge tone="green">IN SYNC</Badge></div>
          </FloatingCard>
          <FloatingCard className="right-3 top-[42%] [animation-delay:-2s]">
            <div className="text-slate-400">Client A → :80</div>
            <div className="mt-0.5 font-semibold text-emerald-300">ALLOWED · rule allow-web</div>
          </FloatingCard>
          <FloatingCard className="-bottom-5 left-1/4 [animation-delay:-4s]">
            <div className="text-slate-400">Client B → :22</div>
            <div className="mt-0.5 font-semibold text-rose-300">BLOCKED · logged via NFLOG</div>
          </FloatingCard>
        </div>
      </section>

      {/* ---------------- platform facts */}
      <section id="platform" className="relative z-10 mx-auto max-w-7xl scroll-mt-24 px-6 py-12">
        <Reveal>
          <div className="glass grid gap-px overflow-hidden sm:grid-cols-2 lg:grid-cols-4">
            {FACTS.map((f) => (
              <div key={f.label} className="bg-white/[0.015] p-6">
                <div className="text-3xl font-semibold tracking-tight text-gradient">{f.value}</div>
                <div className="mt-1 text-sm text-fg-muted">{f.label}</div>
              </div>
            ))}
          </div>
          <p className="mt-3 text-center text-xs text-slate-500">Figures from the project's own verification runs (see Documentation → Integration status).</p>
        </Reveal>
      </section>

      {/* ---------------- features */}
      <section id="features" className="relative z-10 mx-auto max-w-7xl scroll-mt-24 px-6 py-20">
        <Reveal className="mx-auto max-w-2xl text-center">
          <div className="text-xs font-semibold tracking-[0.2em] text-accent-400 uppercase">Platform capabilities</div>
          <h2 className="mt-3 text-3xl font-semibold tracking-tight text-fg sm:text-4xl">One console for the whole firewall lifecycle</h2>
          <p className="mt-3 text-fg-muted">From instantiating a VNF to proving which packet was dropped and why.</p>
        </Reveal>
        <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map((f, i) => (
            <Reveal key={f.title} delay={(i % 4) * 80}>
              <div className="glass glass-hover group h-full p-5">
                <div className="mb-4 inline-flex rounded-xl border border-white/10 bg-gradient-to-br from-[#6c8cff]/25 to-[#a78bfa]/10 p-2.5 transition-transform group-hover:scale-110">
                  <f.icon className="h-5 w-5 text-accent-200" />
                </div>
                <h3 className="font-semibold text-fg">{f.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-fg-muted">{f.text}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      {/* ---------------- architecture */}
      <section id="architecture" className="relative z-10 mx-auto max-w-7xl scroll-mt-24 px-6 py-20">
        <Reveal className="mx-auto max-w-2xl text-center">
          <div className="text-xs font-semibold tracking-[0.2em] text-accent-400 uppercase">Architecture</div>
          <h2 className="mt-3 text-3xl font-semibold tracking-tight text-fg sm:text-4xl">From browser to protected network</h2>
          <p className="mt-3 text-fg-muted">Select a layer to see what it does – and honestly, how far it has been verified.</p>
        </Reveal>
        <div className="mt-12 grid gap-6 lg:grid-cols-[1fr_380px]">
          <div className="relative">
            <div aria-hidden className="absolute top-6 bottom-6 left-[27px] w-px bg-gradient-to-b from-[#38d9ff]/60 via-[#6c8cff]/60 to-[#34d399]/60">
              {!prefersReducedMotion() && [0, 1, 2].map((i) => (
                <span key={i} className="absolute -left-[3px] h-[7px] w-[7px] rounded-full bg-[#8ba3ff] shadow-[0_0_10px_#6c8cff]"
                  style={{ animation: `arch-particle 4.5s linear ${i * 1.5}s infinite` }} />
              ))}
              <style>{'@keyframes arch-particle{0%{top:0;opacity:0}10%{opacity:1}90%{opacity:1}100%{top:100%;opacity:0}}'}</style>
            </div>
            <ol className="space-y-3">
              {ARCH.map((a, i) => (
                <li key={a.name}>
                  <button onClick={() => setArch(i)} onMouseEnter={() => setArch(i)} aria-pressed={arch === i}
                    className={cx('glass relative flex w-full items-center gap-4 p-3 pr-4 text-left transition-all',
                      arch === i ? 'glow-ring' : 'hover:border-white/20')}>
                    <span className={cx('relative z-10 flex h-[30px] w-[30px] shrink-0 items-center justify-center rounded-full border',
                      arch === i ? 'border-[#6c8cff] bg-[#6c8cff]/30' : 'border-white/15 bg-[#10182a]')}>
                      <a.icon className="h-3.5 w-3.5 text-accent-200" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block font-semibold text-fg">{a.name}</span>
                      <span className="block truncate text-xs text-slate-400">{a.tech}</span>
                    </span>
                    <span className="hidden sm:block"><Badge tone={a.status.tone}>{a.status.label}</Badge></span>
                  </button>
                </li>
              ))}
            </ol>
          </div>
          <div className="glass sticky top-24 h-fit p-6">
            <selected.icon className="h-8 w-8 text-accent-300" />
            <h3 className="mt-3 text-xl font-semibold text-fg">{selected.name}</h3>
            <div className="mt-1 text-sm text-slate-400">{selected.tech}</div>
            <div className="mt-3"><Badge tone={selected.status.tone}>{selected.status.label}</Badge></div>
            <p className="mt-4 text-sm leading-relaxed text-slate-300">{selected.detail}</p>
            <div className="mt-6 grid grid-cols-2 gap-2 text-[11px] text-slate-400">
              <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-emerald-400" />Verified</span>
              <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-[#6c8cff]" />Lab</span>
              <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-violet-400" />Demo mode</span>
              <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-amber-400" />Needs infrastructure</span>
            </div>
          </div>
        </div>
      </section>

      {/* ---------------- security */}
      <section id="security" className="relative z-10 mx-auto max-w-7xl scroll-mt-24 px-6 py-20">
        <div className="grid items-start gap-10 lg:grid-cols-[1fr_1.2fr]">
          <Reveal>
            <div className="text-xs font-semibold tracking-[0.2em] text-accent-400 uppercase">Security model</div>
            <h2 className="mt-3 text-3xl font-semibold tracking-tight text-fg sm:text-4xl">Defence in depth, from rule to report</h2>
            <p className="mt-3 text-fg-muted">Input is validated in the API and again inside the VNF. Rules are rendered from typed fields only and applied in a single nftables transaction – there is never a half-applied policy.</p>
            <div className="glass mt-8 overflow-hidden">
              <div className="flex items-center gap-1.5 border-b border-white/10 px-4 py-2 text-[11px] text-slate-400">
                <span className="h-2.5 w-2.5 rounded-full bg-rose-400/70" /><span className="h-2.5 w-2.5 rounded-full bg-amber-400/70" /><span className="h-2.5 w-2.5 rounded-full bg-emerald-400/70" />
                <span className="ml-2 mono">nft list table inet nexus_fw</span>
              </div>
              <pre className="mono overflow-x-auto p-4 text-[11.5px] leading-relaxed text-slate-300">{`chain forward {
  type filter hook forward priority filter; policy drop;
  ct state invalid drop
  ct state established,related accept
  `}<span className="text-emerald-300">{`ip saddr 10.10.0.11 ip daddr 10.20.0.10 tcp dport 80 accept`}</span>{`
  `}<span className="text-rose-300">{`tcp dport 22 drop`}</span>{`
  limit rate 200/second log group 1 prefix "NX|D|0|"
}`}</pre>
            </div>
          </Reveal>
          <div className="grid gap-3 sm:grid-cols-2">
            {SECURITY.map((s, i) => (
              <Reveal key={s.title} delay={(i % 2) * 80}>
                <div className="glass glass-hover flex h-full gap-3 p-4">
                  <s.icon className="mt-0.5 h-5 w-5 shrink-0 text-accent-300" />
                  <div>
                    <h3 className="text-sm font-semibold text-fg">{s.title}</h3>
                    <p className="mt-1 text-sm leading-relaxed text-fg-muted">{s.text}</p>
                  </div>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* ---------------- CTA */}
      <section className="relative z-10 mx-auto max-w-5xl px-6 py-24">
        <Reveal>
          <div className="glass relative overflow-hidden px-6 py-14 text-center sm:px-12">
            <div aria-hidden className="absolute -top-24 left-1/2 h-64 w-[36rem] -translate-x-1/2 rounded-full bg-[#6c8cff]/25 blur-3xl" />
            <h2 className="relative text-3xl font-semibold tracking-tight text-fg sm:text-4xl">Take Control of Your Network Security.</h2>
            <p className="relative mx-auto mt-3 max-w-xl text-fg-muted">Sign in to deploy a firewall VNF, write your first rule and watch the blocked traffic appear.</p>
            <div className="relative mt-8 flex flex-wrap justify-center gap-3">
              <button onClick={launch} className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-gradient-to-b from-[#7b98ff] to-[#5a78f0] px-5 py-3 text-sm font-semibold text-white shadow-[0_12px_32px_-10px_rgb(108_140_255/0.9)] hover:brightness-110 active:scale-[0.97]">
                Launch NEXUS NFV <ArrowRight className="h-4 w-4" />
              </button>
              <Link to="/docs" className="inline-flex items-center gap-2 rounded-xl border border-white/15 bg-white/[0.05] px-5 py-3 text-sm font-semibold text-slate-100 hover:bg-white/[0.1] active:scale-[0.97]">
                <BookOpen className="h-4 w-4" /> Explore Documentation
              </Link>
            </div>
          </div>
        </Reveal>
      </section>

      <SiteFooter />
    </div>
  );
}
