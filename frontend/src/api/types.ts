export type Role = 'ADMIN' | 'NETWORK_OPERATOR' | 'SECURITY_ANALYST';
export type DataSource = 'LIVE' | 'LAB' | 'DEMO';

export interface Me {
  id: number;
  username: string;
  fullName: string;
  email: string;
  role: Role;
  lastLoginAt?: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type VnfState =
  | 'CREATED' | 'INSTANTIATING' | 'ACTIVE' | 'STOPPING' | 'STOPPED' | 'STARTING' | 'TERMINATING' | 'TERMINATED' | 'FAILED';
export type Health = 'HEALTHY' | 'DEGRADED' | 'UNREACHABLE' | 'UNKNOWN';
export type SyncStatus = 'IN_SYNC' | 'PENDING' | 'FAILED' | 'DRIFT' | 'UNKNOWN';

export interface Vnf {
  id: number;
  vnfId: string;
  name: string;
  vnfType: 'FIREWALL';
  description?: string;
  orchestrator: 'DOCKER' | 'OSM';
  state: VnfState;
  health: Health;
  externalId?: string;
  mgmtIp?: string;
  lanIp?: string;
  wanIp?: string;
  cpuLimit: number;
  memoryLimitMb: number;
  defaultAction: 'DROP' | 'ACCEPT';
  rulesRevision: number;
  appliedRevision: number;
  rulesAppliedAt?: string;
  syncStatus: SyncStatus;
  cpuPercent?: number;
  memoryPercent?: number;
  memoryUsageMb?: number;
  rxBytes?: number;
  txBytes?: number;
  throughputBps?: number;
  metricsSource?: DataSource;
  metricsUpdatedAt?: string;
  lastError?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface LifecycleEvent {
  id: number;
  vnfId: number;
  operation: string;
  fromState?: VnfState;
  toState: VnfState;
  outcome: 'STARTED' | 'SUCCEEDED' | 'FAILED';
  message?: string;
  actor: string;
  occurredAt: string;
}

export interface MetricSample {
  sampledAt: string;
  cpuPercent?: number;
  memoryPercent?: number;
  memoryUsageMb?: number;
  throughputBps?: number;
  dataSource: DataSource;
}

export type RuleAction = 'ALLOW' | 'DENY';
export type RuleProtocol = 'ANY' | 'TCP' | 'UDP' | 'ICMP';

export interface FirewallRule {
  id: number;
  vnfId: number;
  name: string;
  priority: number;
  action: RuleAction;
  protocol: RuleProtocol;
  sourceCidr?: string;
  destinationCidr?: string;
  sourcePort?: string;
  destinationPort?: string;
  enabled: boolean;
  logEnabled: boolean;
  description?: string;
  hitPackets: number;
  hitBytes: number;
  countersUpdatedAt?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface RuleRequest {
  name: string;
  priority: number;
  action: RuleAction;
  protocol: RuleProtocol;
  sourceCidr?: string;
  destinationCidr?: string;
  sourcePort?: string;
  destinationPort?: string;
  enabled: boolean;
  logEnabled: boolean;
  description?: string;
}

export interface SyncResult {
  attempted: boolean;
  applied: boolean;
  status: SyncStatus;
  desiredRevision: number;
  appliedRevision: number;
  message: string;
}

export interface Policy {
  vnfId: number;
  vnfName: string;
  vnfState: VnfState;
  defaultAction: 'DROP' | 'ACCEPT';
  rulesRevision: number;
  appliedRevision: number;
  rulesAppliedAt?: string;
  syncStatus: SyncStatus;
  rules: FirewallRule[];
}

export interface RuntimeRuleset {
  revision: number;
  defaultAction: string;
  source: string;
  rendered: string;
  rules: unknown[];
}

export interface TrafficSummary {
  windowMinutes: number;
  samples: number;
  allowedPackets: number;
  allowedBytes: number;
  blockedPackets: number;
  blockedBytes: number;
  tcpPackets: number;
  udpPackets: number;
  icmpPackets: number;
  inboundBytes: number;
  outboundBytes: number;
  allowedConnectionEvents: number;
  blockedEvents: number;
  dataSources: DataSource[];
}

export interface TimePoint {
  t: string;
  allowedPackets: number;
  blockedPackets: number;
  throughputBps: number;
  tcpPackets: number;
  udpPackets: number;
  icmpPackets: number;
  inboundBytes: number;
  outboundBytes: number;
}

export interface KeyValue {
  key: string;
  count: number;
}

export interface TopTalkers {
  sources: KeyValue[];
  blockedSources: KeyValue[];
  destinations: KeyValue[];
  destinationPorts: KeyValue[];
  protocols: KeyValue[];
}

export interface TrafficEvent {
  id: number;
  vnfId: number;
  occurredAt: string;
  action: 'ALLOWED' | 'BLOCKED';
  protocol: string;
  srcIp: string;
  srcPort?: number;
  dstIp: string;
  dstPort?: number;
  ruleId?: number;
  direction?: string;
  packetLength?: number;
  dataSource: DataSource;
}

export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'FALSE_POSITIVE';

export interface Alert {
  id: number;
  alertId: string;
  vnfId?: number;
  detector: string;
  eventType: string;
  severity: Severity;
  status: AlertStatus;
  sourceIp?: string;
  destinationIp?: string;
  description: string;
  evidenceCount: number;
  firstSeen: string;
  lastSeen: string;
  assignedTo?: string;
  resolutionNote?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AlertStats {
  open: number;
  acknowledged: number;
  activeBySeverity: Record<Severity, number>;
}

export interface AuditLog {
  id: number;
  occurredAt: string;
  username: string;
  action: string;
  category: string;
  targetType?: string;
  targetId?: string;
  outcome: 'SUCCESS' | 'FAILURE';
  details?: string;
  ipAddress?: string;
}

export interface UserDto {
  id: number;
  username: string;
  fullName: string;
  email: string;
  role: Role;
  enabled: boolean;
  locked: boolean;
  lastLoginAt?: string;
  createdAt: string;
}

export interface SessionDto {
  id: number;
  username: string;
  issuedAt: string;
  expiresAt: string;
  ipAddress?: string;
  userAgent?: string;
}

export interface Connection {
  type: string;
  name: string;
  endpoint?: string;
  mode: string;
  status: 'UP' | 'DOWN' | 'UNKNOWN' | 'DISABLED';
  latencyMs?: number;
  details?: string;
  lastError?: string;
  lastCheckedAt?: string;
  lastSuccessAt?: string;
}

export interface Setting {
  key: string;
  value: string;
  valueType: 'INT' | 'BOOL' | 'STRING';
  category: string;
  description: string;
  updatedBy?: string;
  updatedAt?: string;
}

export interface DashboardSummary {
  generatedAt: string;
  vnfs: {
    total: number;
    active: number;
    inactive: number;
    failed: number;
    transitional: number;
    byHealth: Record<Health, number>;
    avgCpuPercent?: number;
    avgMemoryPercent?: number;
    throughputBps: number;
    measuredVnfs: number;
  };
  rules: { total: number; enabled: number; disabled: number; allow: number; deny: number };
  traffic: TrafficSummary;
  alerts: AlertStats;
  posture: { level: 'SECURE' | 'DEGRADED' | 'AT_RISK' | 'CRITICAL' | 'UNPROTECTED'; reasons: string[] };
  integrations: Connection[];
}

// ---- SDN
export interface ControllerStatus {
  mode: 'REAL' | 'DEMO';
  dataSource: DataSource;
  endpoint: string;
  reachable: boolean;
  latencyMs: number;
  version?: string;
  nodeId?: string;
  devices: number;
  hosts: number;
  links: number;
  flows: number;
  activeApps: string[];
  error?: string;
}

export interface Device {
  id: string;
  name?: string;
  type: string;
  available: boolean;
  manufacturer?: string;
  hwVersion?: string;
  swVersion?: string;
  protocol?: string;
  managementAddress?: string;
  channelId?: string;
  role?: string;
  dataSource: DataSource;
}

export interface Port {
  port: string;
  name?: string;
  enabled: boolean;
  type?: string;
  speedMbps: number;
  mac?: string;
  packetsReceived?: number;
  packetsSent?: number;
  bytesReceived?: number;
  bytesSent?: number;
  rxDropped?: number;
  txDropped?: number;
}

export interface Host {
  id: string;
  name?: string;
  mac: string;
  vlan?: string;
  ipAddresses: string[];
  deviceId?: string;
  port?: string;
  dataSource: DataSource;
}

export interface Link {
  srcDevice: string;
  srcPort: string;
  dstDevice: string;
  dstPort: string;
  type?: string;
  state?: string;
  dataSource: DataSource;
}

export interface Flow {
  id: string;
  deviceId: string;
  appId: string;
  priority: number;
  tableId: number;
  state: string;
  packets: number;
  bytes: number;
  lifeSeconds: number;
  permanent: boolean;
  match: string;
  actions: string;
  managedByNexus: boolean;
  dataSource: DataSource;
}

export type PolicyAction = 'DROP' | 'ALLOW' | 'FORWARD' | 'STEER_TO_FIREWALL';

export interface NetworkPolicy {
  id: number;
  name: string;
  description?: string;
  deviceId: string;
  priority: number;
  ipProtocol: string;
  sourceCidr?: string;
  destinationCidr?: string;
  destinationPort?: number;
  action: PolicyAction;
  outputPort?: string;
  onosFlowId?: string;
  status: 'PENDING' | 'INSTALLED' | 'FAILED' | 'REMOVED';
  dataSource?: DataSource;
  lastError?: string;
  createdBy: string;
  createdAt: string;
}

export interface TopoNode {
  id: string;
  type: 'CONTROLLER' | 'SWITCH' | 'HOST' | 'FIREWALL' | 'SEGMENT' | 'CLIENT' | 'SERVER';
  label: string;
  detail?: string;
  status: string;
  dataSource: DataSource;
}

export interface TopoEdge {
  id: string;
  source: string;
  target: string;
  type: 'CONTROL' | 'LINK' | 'ACCESS' | 'ATTACHMENT';
  label?: string;
}

export interface Topology {
  view: 'sdn' | 'nfv';
  dataSource: DataSource;
  available: boolean;
  message?: string;
  nodes: TopoNode[];
  edges: TopoEdge[];
}

// ---- NETCONF
export interface NetconfDevice {
  id: number;
  name: string;
  host: string;
  port: number;
  username: string;
  vnfId?: number;
  status: 'UNKNOWN' | 'CONNECTED' | 'UNREACHABLE' | 'AUTH_FAILED';
  capabilities: string[];
  hostKeyFingerprint?: string;
  lastError?: string;
  lastConnectedAt?: string;
  createdBy: string;
  createdAt: string;
}

export interface ConfigVersion {
  id: number;
  deviceId: number;
  versionNo: number;
  operation: 'PUSH' | 'ROLLBACK' | 'RETRIEVE';
  datastore: string;
  checksum: string;
  status: 'APPLIED' | 'FAILED' | 'RETRIEVED';
  message?: string;
  ruleCount: number;
  createdBy: string;
  createdAt: string;
}

export interface SyncReport {
  deviceId: number;
  status: 'IN_SYNC' | 'DRIFT' | 'UNKNOWN' | 'NO_BASELINE';
  runningChecksum?: string;
  desiredChecksum?: string;
  lastAppliedChecksum?: string;
  lastAppliedVersion?: number;
  detail: string;
}

export interface OperationResult {
  success: boolean;
  message: string;
  version?: ConfigVersion;
}
