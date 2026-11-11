import { ArrowLeft, Lock, ShieldCheck, User } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Wordmark } from '../components/Brand';
import { AmbientBackground } from '../components/effects';
import { Button } from '../components/ui';

const HOME = '/overview';

export default function LoginPage() {
  const { login, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={HOME} replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password) {
      setError('Username and password are required');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await login(username.trim(), password);
      const from = (location.state as { from?: string } | null)?.from ?? HOME;
      navigate(from, { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="relative flex min-h-full items-center justify-center overflow-hidden p-4 sm:p-6">
      <AmbientBackground />
      <div className="relative z-10 grid w-full max-w-5xl overflow-hidden rounded-3xl border border-white/10 bg-white/[0.03] shadow-2xl backdrop-blur-xl md:grid-cols-2">
        {/* brand panel */}
        <div className="relative hidden flex-col justify-between overflow-hidden border-r border-white/10 bg-gradient-to-br from-[#6c8cff]/15 via-transparent to-[#a78bfa]/10 p-10 md:flex">
          <Wordmark />
          <div>
            <h2 className="text-3xl leading-tight font-semibold tracking-tight text-fg">Security operations for your <span className="text-gradient">virtual firewalls</span>.</h2>
            <p className="mt-3 text-sm text-fg-muted">Deploy firewall VNFs, enforce nftables policy, steer traffic with OpenFlow and manage configuration over NETCONF – from one console.</p>
          </div>
          <ul className="space-y-2 text-sm text-slate-300">
            {['JWT sessions with server-side revocation', 'Role-based access: admin, operator, analyst', 'Every action written to the audit trail'].map((t) => (
              <li key={t} className="flex items-center gap-2"><ShieldCheck className="h-4 w-4 text-ok" /> {t}</li>
            ))}
          </ul>
        </div>

        {/* form */}
        <div className="p-6 sm:p-10">
          <Link to="/" className="mb-8 inline-flex items-center gap-1 text-xs text-slate-400 hover:text-slate-200"><ArrowLeft className="h-3.5 w-3.5" /> Back to website</Link>
          <div className="mb-6 md:hidden"><Wordmark /></div>
          <h1 className="text-2xl font-semibold tracking-tight text-fg">Sign in to the console</h1>
          <p className="mt-1 text-sm text-fg-muted">Use the account issued by your NEXUS administrator.</p>
          <form onSubmit={submit} className="mt-8 space-y-4" noValidate>
            <div>
              <label htmlFor="username" className="label">Username</label>
              <div className="relative">
                <User className="pointer-events-none absolute top-2.5 left-3 h-4 w-4 text-slate-500" />
                <input id="username" className="input pl-9" autoComplete="username" autoFocus value={username}
                  onChange={(e) => setUsername(e.target.value)} />
              </div>
            </div>
            <div>
              <label htmlFor="password" className="label">Password</label>
              <div className="relative">
                <Lock className="pointer-events-none absolute top-2.5 left-3 h-4 w-4 text-slate-500" />
                <input id="password" type="password" className="input pl-9" autoComplete="current-password" value={password}
                  onChange={(e) => setPassword(e.target.value)} />
              </div>
            </div>
            {error && <div role="alert" className="rounded-xl border border-rose-400/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-100">{error}</div>}
            <Button type="submit" variant="primary" loading={busy} className="w-full py-2.5">Sign in</Button>
            <p className="text-center text-[11px] text-slate-500">
              Access is logged and audited. Accounts lock for 15 minutes after 5 failed attempts.
            </p>
          </form>
        </div>
      </div>
    </div>
  );
}
