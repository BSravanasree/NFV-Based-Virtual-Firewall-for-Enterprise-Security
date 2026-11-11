import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Alert } from '../api/types';

const get = vi.fn();
vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return { ...actual, api: { get: (...a: unknown[]) => get(...a), post: vi.fn(), patch: vi.fn(), delete: vi.fn(), put: vi.fn() } };
});

import { AuthProvider } from '../auth/AuthContext';
import { CommandPalette } from '../components/CommandPalette';
import { AnimatedNumber } from '../components/effects';
import { ToastProvider } from '../components/Toast';
import { Drawer } from '../components/ui';
import { bucketAlerts } from '../pages/DashboardPage';
import DocsPage from '../pages/DocsPage';
import LandingPage from '../pages/LandingPage';

function Where() {
  const l = useLocation();
  return <div data-testid="where">{l.pathname + l.search}</div>;
}

function wrap(ui: React.ReactNode, path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ToastProvider><AuthProvider>
        <Routes>
          <Route path="/" element={ui} />
          <Route path="/docs" element={ui} />
          <Route path="*" element={<Where />} />
        </Routes>
      </AuthProvider></ToastProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => get.mockReset());

describe('landing page', () => {
  it('communicates the product and every required section', () => {
    wrap(<LandingPage />);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Enterprise Network Security, Reimagined.');
    for (const id of ['platform', 'features', 'architecture', 'security']) expect(document.getElementById(id)).not.toBeNull();
    expect(screen.getByText('Take Control of Your Network Security.')).toBeInTheDocument();
    expect(screen.getByText('Virtual Firewall Management')).toBeInTheDocument();
    expect(screen.getByText('Enterprise Audit Logging')).toBeInTheDocument();
  });

  it('labels integrations honestly in the architecture view', async () => {
    wrap(<LandingPage />);
    expect(screen.getAllByText('Implemented · not live-verified').length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: /ONOS Controller/ }));
    expect(screen.getAllByText(/demo fixture without controller/i).length).toBeGreaterThan(0);
  });

  it('Launch Console sends anonymous visitors to sign in', async () => {
    wrap(<LandingPage />);
    await userEvent.click(screen.getAllByRole('button', { name: /Launch Console/ })[0]);
    expect(screen.getByTestId('where')).toHaveTextContent('/login');
  });

  it('documentation page states integration status without overclaiming', () => {
    wrap(<DocsPage />, '/docs');
    expect(screen.getByText('Not live-verified')).toBeInTheDocument();
    expect(screen.getByText('Demo without controller')).toBeInTheDocument();
  });
});

describe('security events over time', () => {
  it('buckets alerts by first-seen time and severity inside the window only', () => {
    const now = Date.parse('2026-11-11T12:00:00Z');
    const a = (sev: Alert['severity'], minsAgo: number) => ({ severity: sev, firstSeen: new Date(now - minsAgo * 60_000).toISOString() } as Alert);
    const buckets = bucketAlerts([a('HIGH', 3), a('HIGH', 4), a('LOW', 40), a('CRITICAL', 500)], 60, now);
    const total = buckets.reduce((s, b) => s + b.CRITICAL + b.HIGH + b.MEDIUM + b.LOW, 0);
    expect(total).toBe(3); // the 500-minute-old alert is outside the 60 minute window
    expect(buckets.find((b) => b.HIGH > 0)!.HIGH).toBe(2); // 11:56 and 11:57 share the 11:55 bucket
    expect(buckets.find((b) => b.LOW > 0)!.LOW).toBe(1);
  });
});

describe('global search', () => {
  it('finds pages and pivots IP addresses into traffic and alerts', async () => {
    get.mockResolvedValue({ data: [] });
    const onClose = vi.fn();
    render(
      <MemoryRouter initialEntries={['/overview']}>
        <ToastProvider><AuthProvider>
          <Routes><Route path="*" element={<><CommandPalette open onClose={onClose} /><Where /></>} /></Routes>
        </AuthProvider></ToastProvider>
      </MemoryRouter>,
    );
    const input = screen.getByRole('textbox', { name: 'Search' });
    await userEvent.type(input, 'firewall');
    expect(screen.getByRole('option', { name: /Firewall/ })).toBeInTheDocument();
    await userEvent.clear(input);
    await userEvent.type(input, '10.10.0.12{Enter}');
    expect(screen.getByTestId('where')).toHaveTextContent('/monitoring?srcIp=10.10.0.12');
    expect(onClose).toHaveBeenCalled();
  });
});

describe('overlays and motion', () => {
  it('drawer closes on Escape', async () => {
    const onClose = vi.fn();
    render(<Drawer open title="Add firewall rule" onClose={onClose}><p>form</p></Drawer>);
    expect(screen.getByRole('dialog', { name: 'Add firewall rule' })).toBeInTheDocument();
    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });

  it('animated numbers render the real value immediately when motion is reduced', () => {
    render(<AnimatedNumber value={1234567} />);
    expect(screen.getByText((1234567).toLocaleString())).toBeInTheDocument(); // locale-aware grouping
  });
});
