import { BookOpen, ExternalLink, Terminal } from 'lucide-react';
import { useEffect, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { AmbientBackground } from '../components/effects';
import { Badge } from '../components/ui';
import { GITHUB_URL, SiteFooter, SiteNav } from './site';

const SECTIONS = [
  { id: 'quick-start', label: 'Quick start' },
  { id: 'stack', label: 'Technology stack' },
  { id: 'status', label: 'Integration status' },
  { id: 'console', label: 'Using the console' },
  { id: 'api', label: 'REST API' },
  { id: 'security', label: 'Security model' },
  { id: 'repository', label: 'Repository documents' },
];

const DOCS = [
  ['PROJECT_OVERVIEW.md', 'Problem, solution, roles and honest scope'],
  ['SYSTEM_ARCHITECTURE.md', 'Components, flows and trust boundaries'],
  ['DATABASE_SCHEMA.md', 'Tables, relationships and retention'],
  ['API_DOCUMENTATION.md', 'Endpoints, roles and error contract'],
  ['NFV_LIFECYCLE.md', 'State machine and orchestrators'],
  ['FIREWALL_IMPLEMENTATION.md', 'VNF image, nftables ruleset and agent'],
  ['ONOS_INTEGRATION.md', 'SDN controller integration and demo mode'],
  ['OPENFLOW_INTEGRATION.md', 'Policy → OpenFlow compilation and lab'],
  ['NETCONF_INTEGRATION.md', 'YANG model, Netopeer2 and client'],
  ['OSM_INTEGRATION.md', 'MANO adapter and descriptors'],
  ['DEPLOYMENT_GUIDE.md', 'Local, Ubuntu and cloud deployment'],
  ['TESTING_GUIDE.md', 'Automated, end-to-end and infrastructure tests'],
  ['SECURITY_DESIGN.md', 'Authentication, RBAC, secrets and hardening'],
  ['DEMO_SCENARIO.md', 'Step-by-step demonstration'],
  ['TROUBLESHOOTING.md', 'Symptoms, causes and fixes'],
];

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section id={id} className="glass scroll-mt-28 p-6">
      <h2 className="mb-3 text-xl font-semibold text-fg">{title}</h2>
      <div className="space-y-3 text-sm leading-relaxed text-slate-300">{children}</div>
    </section>
  );
}

function Code({ children }: { children: string }) {
  return <pre className="mono overflow-x-auto rounded-xl border border-white/10 bg-[#060a14]/70 p-4 text-xs text-slate-300">{children}</pre>;
}

export default function DocsPage() {
  const location = useLocation();
  useEffect(() => {
    const el = location.hash ? document.getElementById(location.hash.slice(1)) : null;
    if (el) setTimeout(() => el.scrollIntoView({ behavior: 'smooth' }), 50);
    else window.scrollTo(0, 0);
  }, [location.hash]);

  return (
    <div className="relative min-h-full overflow-x-hidden">
      <AmbientBackground intensity="subtle" />
      <SiteNav />
      <div className="relative z-10 mx-auto max-w-7xl px-6 pt-32 pb-20">
        <div className="mb-10 max-w-3xl">
          <div className="flex items-center gap-2 text-xs font-semibold tracking-[0.2em] text-accent-400 uppercase"><BookOpen className="h-4 w-4" /> Documentation</div>
          <h1 className="mt-3 text-4xl font-semibold tracking-tight text-fg">NEXUS NFV documentation</h1>
          <p className="mt-3 text-fg-muted">How the platform is built, how to run it, and exactly which integrations are verified.</p>
        </div>
        <div className="grid gap-8 lg:grid-cols-[220px_1fr]">
          <nav className="hidden lg:block" aria-label="Documentation sections">
            <ul className="sticky top-28 space-y-1 text-sm">
              {SECTIONS.map((s) => <li key={s.id}><a href={`#${s.id}`} className="block rounded-lg px-3 py-1.5 text-slate-400 hover:bg-white/[0.05] hover:text-white">{s.label}</a></li>)}
            </ul>
          </nav>
          <div className="min-w-0 space-y-6">
            <Section id="quick-start" title="Quick start">
              <p>The application and the NFV lab run with Docker Compose on Windows (Docker Desktop), macOS or Ubuntu.</p>
              <Code>{`cp .env.example .env                               # set every secret
docker compose --profile build build fw-vnf-image  # Ubuntu firewall VNF image
docker compose --profile lab up -d --build         # console, API, MySQL, lab clients + server
python lab/e2e_test.py                             # 53 live end-to-end checks`}</Code>
              <p>Then open the console, sign in, create a firewall VNF, deploy it and add rules. The full walkthrough is in <span className="mono">docs/DEMO_SCENARIO.md</span>.</p>
            </Section>

            <Section id="stack" title="Technology stack">
              <div className="grid gap-3 sm:grid-cols-2">
                {[
                  ['Console', 'React 18, TypeScript, Vite, Tailwind CSS 4, Recharts, Lucide'],
                  ['Control plane', 'Java 17+, Spring Boot 3.5, Spring Security (JWT), JPA, Flyway'],
                  ['Database', 'MySQL 8.4'],
                  ['Firewall VNF', 'Ubuntu 24.04, nftables, ulogd2 (NFLOG), Python agent'],
                  ['Configuration', 'NETCONF over SSH, Netopeer2 + sysrepo, YANG module nexus-firewall'],
                  ['Orchestration', 'Docker Engine API (NFV lab), OSM NBI (production)'],
                  ['SDN', 'ONOS 2.7 REST, OpenFlow 1.3, Open vSwitch, Mininet'],
                  ['Delivery', 'Docker Compose, nginx, health checks, environment-based configuration'],
                ].map(([k, v]) => (
                  <div key={k} className="rounded-xl border border-white/10 bg-white/[0.03] p-3">
                    <div className="text-xs font-semibold text-accent-300">{k}</div>
                    <div className="mt-1 text-slate-300">{v}</div>
                  </div>
                ))}
              </div>
            </Section>

            <Section id="status" title="Integration status">
              <p>What is real, what runs in the lab, and what still needs infrastructure. Nothing in the console simulates a successful infrastructure operation.</p>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[560px]">
                  <thead><tr><th className="th">Integration</th><th className="th">Status</th><th className="th">Evidence</th></tr></thead>
                  <tbody>
                    {[
                      ['Firewall VNF (nftables)', <Badge key="a" tone="green">Verified</Badge>, '53/53 end-to-end checks with real allowed/blocked traffic'],
                      ['VNF lifecycle (Docker NFV lab)', <Badge key="b" tone="green">Verified</Badge>, 'deploy, stop, start, restart, scale, terminate confirmed by Docker + agent'],
                      ['Traffic telemetry & detection', <Badge key="c" tone="green">Verified</Badge>, 'nftables counters, NFLOG events, 4 detectors triggered by nmap'],
                      ['NETCONF (Netopeer2 in the VNF)', <Badge key="d" tone="green">Verified</Badge>, 'get-config, validate, commit, rollback enforced in the data path, drift'],
                      ['ONOS / OpenFlow', <Badge key="e" tone="violet">Demo without controller</Badge>, 'real REST client; labelled DEMO fixture when no controller is configured'],
                      ['OSM', <Badge key="f" tone="amber">Not live-verified</Badge>, 'adapter and SOL006 packages; needs an OSM installation and a VIM'],
                      ['Hosted public console', <Badge key="g" tone="amber">Lab not attached</Badge>, 'cloud hosting cannot run privileged firewall VNFs; see the deployment guide'],
                    ].map(([a, b, c], i) => (
                      <tr key={i}><td className="td font-medium text-slate-100">{a}</td><td className="td">{b}</td><td className="td text-slate-400">{c}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Section>

            <Section id="console" title="Using the console">
              <ul className="list-disc space-y-1.5 pl-5">
                <li><b>Overview</b> – security posture, KPIs and charts; every figure carries its data source (LIVE, LAB or DEMO).</li>
                <li><b>VNF Management</b> – create a firewall VNF, deploy it, and operate it through the lifecycle state machine.</li>
                <li><b>Firewall</b> – ordered rules; changes are pushed to the VNF and the response tells you whether they are enforced.</li>
                <li><b>Traffic Analytics</b> and <b>Security Alerts</b> – counters, connection events, detections and triage.</li>
                <li><b>Network Topology</b>, <b>ONOS</b>, <b>OpenFlow</b>, <b>NETCONF</b>, <b>OSM</b> – infrastructure views and operations.</li>
                <li>Press <kbd className="rounded border border-white/15 px-1 text-xs">Ctrl K</kbd> anywhere in the console for global search.</li>
              </ul>
            </Section>

            <Section id="api" title="REST API">
              <p>Versioned JSON API under <span className="mono">/api/v1</span>, authenticated with a bearer JWT from <span className="mono">POST /api/v1/auth/login</span>. Interactive OpenAPI documentation:</p>
              <a href="/swagger-ui.html" target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 rounded-lg border border-white/10 bg-white/[0.05] px-3 py-1.5 text-sm text-accent-200 hover:bg-white/10">
                <Terminal className="h-4 w-4" /> Open Swagger UI <ExternalLink className="h-3.5 w-3.5" />
              </a>
              <Code>{`curl -s https://<host>/api/v1/auth/login -H 'Content-Type: application/json' \\
     -d '{"username":"admin","password":"…"}'          # → { accessToken, expiresAt, user }
curl -s -H "Authorization: Bearer $TOKEN" https://<host>/api/v1/vnfs`}</Code>
            </Section>

            <Section id="security" title="Security model">
              <ul className="list-disc space-y-1.5 pl-5">
                <li>JWT with server-side session revocation, BCrypt (cost 12), lockout after 5 failed attempts, no default credentials.</li>
                <li>Roles: ADMIN, NETWORK_OPERATOR, SECURITY_ANALYST – enforced on every endpoint.</li>
                <li>Firewall input validated in the API and again inside the VNF; no shell execution, atomic nftables transactions.</li>
                <li>Secrets only from environment variables; NETCONF credentials encrypted with AES-256-GCM; never returned by the API.</li>
                <li>Management interfaces (agent, NETCONF, Docker) are never exposed publicly.</li>
              </ul>
            </Section>

            <Section id="repository" title="Repository documents">
              <p>The <span className="mono">docs/</span> directory of the repository contains the complete engineering documentation{GITHUB_URL ? '' : ' (available in the source repository)'}:</p>
              <div className="grid gap-2 sm:grid-cols-2">
                {DOCS.map(([file, text]) => {
                  const inner = (<><div className="mono text-xs text-accent-200">{file}</div><div className="text-xs text-slate-400">{text}</div></>);
                  return GITHUB_URL ? (
                    <a key={file} href={`${GITHUB_URL.replace(/\/$/, '')}/blob/main/docs/${file}`} target="_blank" rel="noreferrer"
                      className="rounded-xl border border-white/10 bg-white/[0.03] p-3 hover:border-[#6c8cff]/40">{inner}</a>
                  ) : <div key={file} className="rounded-xl border border-white/10 bg-white/[0.03] p-3">{inner}</div>;
                })}
              </div>
            </Section>
          </div>
        </div>
      </div>
      <SiteFooter />
    </div>
  );
}
