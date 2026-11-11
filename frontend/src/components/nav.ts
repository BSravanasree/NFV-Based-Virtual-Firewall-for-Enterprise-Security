import {
  Activity, Boxes, FileCode2, GitBranch, LayoutDashboard, Network, ScrollText, Server, Settings, Shield, ShieldAlert,
  Users, Workflow,
} from 'lucide-react';
import type { Role } from '../api/types';

export interface NavItem { to: string; label: string; icon: typeof Shield; roles?: Role[] }

/** Console navigation (single source for sidebar, mobile drawer and global search). */
export const NAV: { section: string; items: NavItem[] }[] = [
  { section: 'Operations', items: [{ to: '/overview', label: 'Overview', icon: LayoutDashboard }] },
  {
    section: 'Network Functions',
    items: [
      { to: '/vnfs', label: 'VNF Management', icon: Boxes },
      { to: '/firewall', label: 'Firewall', icon: Shield },
      { to: '/osm', label: 'OSM Orchestration', icon: Workflow },
    ],
  },
  {
    section: 'Software-Defined Network',
    items: [
      { to: '/topology', label: 'Network Topology', icon: Network },
      { to: '/onos', label: 'ONOS Controller', icon: Server },
      { to: '/openflow', label: 'OpenFlow', icon: GitBranch },
      { to: '/netconf', label: 'NETCONF', icon: FileCode2 },
    ],
  },
  {
    section: 'Security',
    items: [
      { to: '/monitoring', label: 'Traffic Analytics', icon: Activity },
      { to: '/alerts', label: 'Security Alerts', icon: ShieldAlert },
      { to: '/audit', label: 'Audit Logs', icon: ScrollText, roles: ['ADMIN', 'SECURITY_ANALYST'] },
    ],
  },
  {
    section: 'Administration',
    items: [
      { to: '/users', label: 'User Management', icon: Users, roles: ['ADMIN'] },
      { to: '/settings', label: 'Settings', icon: Settings },
    ],
  },
];
