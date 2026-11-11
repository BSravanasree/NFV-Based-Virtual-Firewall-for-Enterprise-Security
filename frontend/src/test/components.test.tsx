import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmDialog, SourceBadge, StatusBadge } from '../components/ui';
import { ToastProvider } from '../components/Toast';

// ---- mock the API layer (no network in unit tests)
const get = vi.fn();
const post = vi.fn();
vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return { ...actual, api: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), patch: vi.fn(), delete: vi.fn(), put: vi.fn() } };
});

import { AuthProvider } from '../auth/AuthContext';
import App from '../App';
import DashboardPage from '../pages/DashboardPage';

function renderApp(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ToastProvider><AuthProvider><App /></AuthProvider></ToastProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  get.mockReset();
  post.mockReset();
});

describe('UI primitives', () => {
  it('renders status and provenance badges', () => {
    render(<><StatusBadge value="ACTIVE" /><SourceBadge source="DEMO" /><SourceBadge source={null} /></>);
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
    expect(screen.getByText('DEMO')).toHaveAttribute('title', expect.stringMatching(/not a live measurement/));
    expect(screen.getByText('NO DATA')).toBeInTheDocument();
  });

  it('confirm dialog calls the right handlers', async () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(<ConfirmDialog open title="Stop VNF" message="Really?" confirmLabel="Stop" danger onConfirm={onConfirm} onCancel={onCancel} />);
    await userEvent.click(screen.getByRole('button', { name: 'Stop' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});

describe('authentication flow', () => {
  it('redirects anonymous users to the login page', async () => {
    renderApp('/vnfs');
    expect(await screen.findByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('validates required fields before calling the API', async () => {
    renderApp('/login');
    await userEvent.click(await screen.findByRole('button', { name: /sign in/i }));
    expect(screen.getByRole('alert')).toHaveTextContent('Username and password are required');
    expect(post).not.toHaveBeenCalled();
  });

  it('shows the backend error for bad credentials', async () => {
    post.mockRejectedValueOnce(Object.assign(new Error('401'), {
      isAxiosError: true, response: { status: 401, data: { message: 'Invalid username or password' } },
    }));
    renderApp('/login');
    await userEvent.type(await screen.findByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password');
  });

  it('logs in and hides admin-only navigation from analysts', async () => {
    post.mockResolvedValueOnce({ data: { accessToken: 't', user: { id: 2, username: 'analyst', fullName: 'Sec Analyst', email: 'a@x', role: 'SECURITY_ANALYST' } } });
    get.mockImplementation(() => new Promise(() => undefined)); // keep pages loading
    renderApp('/login');
    await userEvent.type(await screen.findByLabelText('Username'), 'analyst');
    await userEvent.type(screen.getByLabelText('Password'), 'Secret!12345');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    expect(await screen.findByText('Sec Analyst')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Audit Logs/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /User Management/ })).not.toBeInTheDocument();
    expect(post).toHaveBeenCalledWith('/auth/login', { username: 'analyst', password: 'Secret!12345' });
  });
});

describe('dashboard', () => {
  const summary = {
    generatedAt: new Date().toISOString(),
    vnfs: { total: 2, active: 1, inactive: 1, failed: 0, transitional: 0, byHealth: { HEALTHY: 1, DEGRADED: 0, UNREACHABLE: 0, UNKNOWN: 0 }, avgCpuPercent: 3.2, avgMemoryPercent: 12.5, throughputBps: 125000, measuredVnfs: 1 },
    rules: { total: 3, enabled: 2, disabled: 1, allow: 2, deny: 1 },
    traffic: { windowMinutes: 60, samples: 4, allowedPackets: 1200, allowedBytes: 1, blockedPackets: 37, blockedBytes: 1, tcpPackets: 1000, udpPackets: 100, icmpPackets: 137, inboundBytes: 1, outboundBytes: 1, allowedConnectionEvents: 12, blockedEvents: 37, dataSources: ['LAB'] },
    alerts: { open: 2, acknowledged: 0, activeBySeverity: { CRITICAL: 0, HIGH: 1, MEDIUM: 1, LOW: 0 } },
    posture: { level: 'AT_RISK', reasons: ['1 active high-severity alert(s)'] },
    integrations: [{ type: 'ONOS', name: 'onos', mode: 'DEMO', status: 'UP' }],
  };

  it('renders measured KPIs with provenance and reloads when the window changes', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/dashboard/summary') return Promise.resolve({ data: summary });
      if (url === '/dashboard/activity') return Promise.resolve({ data: { lifecycle: [], alerts: [], audit: [] } });
      return Promise.resolve({ data: [] });
    });
    render(<MemoryRouter><ToastProvider><DashboardPage /></ToastProvider></MemoryRouter>);
    expect(await screen.findByText(/Security posture: AT RISK/)).toBeInTheDocument();
    expect(screen.getByText('1,200')).toBeInTheDocument();
    expect(screen.getAllByText('LAB').length).toBeGreaterThan(0);
    expect(screen.getAllByText('DEMO').length).toBeGreaterThan(0);

    await userEvent.selectOptions(screen.getByLabelText('Time window'), '1440');
    await waitFor(() => expect(get).toHaveBeenCalledWith('/dashboard/summary', { params: { minutes: 1440 } }));
  });

  it('shows an error state with retry when the API fails', async () => {
    get.mockRejectedValue(Object.assign(new Error('x'), { isAxiosError: true, response: undefined }));
    render(<MemoryRouter><ToastProvider><DashboardPage /></ToastProvider></MemoryRouter>);
    expect(await screen.findByText('Backend unreachable')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });
});
