import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { lazy, Suspense, type ReactNode } from 'react';
import type { Role } from './api/types';
import { useAuth } from './auth/AuthContext';
import Layout from './components/Layout';
import { ErrorBoundary } from './components/ErrorBoundary';
import { Spinner } from './components/ui';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';

// Console pages are code-split so the public landing page loads only what it needs.
const DocsPage = lazy(() => import('./pages/DocsPage'));
const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const VnfListPage = lazy(() => import('./pages/VnfListPage'));
const VnfDetailsPage = lazy(() => import('./pages/VnfDetailsPage'));
const FirewallPage = lazy(() => import('./pages/FirewallPage'));
const RuleEditorPage = lazy(() => import('./pages/RuleEditorPage'));
const TopologyPage = lazy(() => import('./pages/TopologyPage'));
const OnosPage = lazy(() => import('./pages/OnosPage'));
const OsmPage = lazy(() => import('./pages/OsmPage'));
const OpenFlowPage = lazy(() => import('./pages/OpenFlowPage'));
const MonitoringPage = lazy(() => import('./pages/MonitoringPage'));
const AlertsPage = lazy(() => import('./pages/AlertsPage'));
const NetconfPage = lazy(() => import('./pages/NetconfPage'));
const AuditPage = lazy(() => import('./pages/AuditPage'));
const UsersPage = lazy(() => import('./pages/UsersPage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));

/** Default page after signing in. */
export const CONSOLE_HOME = '/overview';

function RequireAuth({ children, roles }: { children: ReactNode; roles?: Role[] }) {
  const { user, loading, hasRole } = useAuth();
  const location = useLocation();
  if (loading) return <Spinner label="Restoring session…" />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (roles && !hasRole(...roles)) {
    return (
      <div className="panel mx-auto mt-10 max-w-md p-6 text-center">
        <h2 className="text-base font-semibold text-fg">Access denied</h2>
        <p className="mt-1 text-sm text-fg-muted">Your role ({user.role}) is not permitted to view this page.</p>
      </div>
    );
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <ErrorBoundary>
      <Suspense fallback={<Spinner />}>
      <Routes>
        {/* public site */}
        <Route path="/" element={<LandingPage />} />
        <Route path="/docs" element={<DocsPage />} />
        <Route path="/login" element={<LoginPage />} />

        {/* authenticated console */}
        <Route element={<RequireAuth><Layout /></RequireAuth>}>
          <Route path="overview" element={<DashboardPage />} />
          <Route path="vnfs" element={<VnfListPage />} />
          <Route path="vnfs/:id" element={<VnfDetailsPage />} />
          <Route path="firewall" element={<FirewallPage />} />
          <Route path="firewall/:vnfId/rules/new" element={<RequireAuth roles={['ADMIN', 'NETWORK_OPERATOR']}><RuleEditorPage /></RequireAuth>} />
          <Route path="firewall/:vnfId/rules/:ruleId" element={<RequireAuth roles={['ADMIN', 'NETWORK_OPERATOR']}><RuleEditorPage /></RequireAuth>} />
          <Route path="topology" element={<TopologyPage />} />
          <Route path="onos" element={<OnosPage />} />
          <Route path="osm" element={<OsmPage />} />
          <Route path="openflow" element={<OpenFlowPage />} />
          <Route path="monitoring" element={<MonitoringPage />} />
          <Route path="alerts" element={<AlertsPage />} />
          <Route path="netconf" element={<NetconfPage />} />
          <Route path="audit" element={<RequireAuth roles={['ADMIN', 'SECURITY_ANALYST']}><AuditPage /></RequireAuth>} />
          <Route path="users" element={<RequireAuth roles={['ADMIN']}><UsersPage /></RequireAuth>} />
          <Route path="settings" element={<SettingsPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      </Suspense>
    </ErrorBoundary>
  );
}
