import { describe, expect, it } from 'vitest';
import { describeRule, validateRule } from '../pages/RuleEditorPage';
import { passwordProblems } from '../pages/UsersPage';
import { layout } from '../pages/TopologyPage';
import { prettyXml } from '../pages/NetconfPage';
import { availableOps } from '../pages/vnfActions';
import { bps, bytes, CIDR_RE, IPV4_RE, validPortSpec } from '../utils/format';
import type { RuleRequest, TopoNode, Vnf } from '../api/types';

const base: RuleRequest = {
  name: 'allow-web', priority: 10, action: 'ALLOW', protocol: 'TCP', sourceCidr: '10.10.0.11',
  destinationCidr: '10.20.0.10/32', sourcePort: '', destinationPort: '80', enabled: true, logEnabled: true, description: '',
};

describe('firewall rule form validation', () => {
  it('accepts a valid rule', () => {
    expect(validateRule(base)).toEqual({});
  });
  it('rejects invalid CIDRs, ports and names', () => {
    const e = validateRule({ ...base, name: 'bad;name', sourceCidr: '10.0.0.0/33', destinationPort: '70000' });
    expect(Object.keys(e).sort()).toEqual(['destinationPort', 'name', 'sourceCidr']);
  });
  it('rejects ports on ICMP and duplicate priorities', () => {
    const e = validateRule({ ...base, protocol: 'ICMP' }, [10]);
    expect(e.destinationPort).toMatch(/TCP or UDP/);
    expect(e.priority).toMatch(/already used/);
  });
  it('rejects shell-style injection in address fields', () => {
    expect(validateRule({ ...base, sourceCidr: '10.0.0.0/8; flush ruleset' }).sourceCidr).toBeDefined();
  });
  it('describes the effect in plain language', () => {
    expect(describeRule(base)).toBe('Allow TCP traffic from 10.10.0.11 to 10.20.0.10/32:80');
    expect(describeRule({ ...base, action: 'DENY', protocol: 'ANY', sourceCidr: '', destinationCidr: '', destinationPort: '' }))
      .toBe('Deny all IPv4 traffic from any to any');
  });
});

describe('password policy mirror', () => {
  it('lists every missing requirement', () => {
    expect(passwordProblems('short')).toEqual(expect.arrayContaining(['at least 10 characters', 'an upper-case letter', 'a digit', 'a symbol']));
    expect(passwordProblems('Str0ng!Passw0rd')).toEqual([]);
    expect(passwordProblems('Admin!12345x', 'admin')).toContain('must not contain the username');
  });
});

describe('formatting & regexes', () => {
  it('formats sizes and rates', () => {
    expect(bytes(1536)).toBe('1.5 KB');
    expect(bytes(null)).toBe('—');
    expect(bps(2_500_000)).toBe('2.5 Mbps');
  });
  it('validates addresses and port specs', () => {
    expect(IPV4_RE.test('10.10.0.254')).toBe(true);
    expect(IPV4_RE.test('10.10.0.256')).toBe(false);
    expect(CIDR_RE.test('10.0.0.0/24')).toBe(true);
    expect(CIDR_RE.test('10.0.0.0/40')).toBe(false);
    expect(validPortSpec('1000-2000')).toBe(true);
    expect(validPortSpec('2000-1000')).toBe(false);
  });
});

describe('VNF lifecycle actions offered per state', () => {
  const v = (state: Vnf['state'], externalId?: string) => ({ state, externalId } as Vnf);
  it('maps states to valid operations only', () => {
    expect(availableOps(v('CREATED'))).toEqual(['deploy', 'delete']);
    expect(availableOps(v('ACTIVE'))).toEqual(['stop', 'restart', 'terminate']);
    expect(availableOps(v('STOPPED'))).toEqual(['start', 'terminate']);
    expect(availableOps(v('INSTANTIATING'))).toEqual([]);
    expect(availableOps(v('FAILED', 'abc'))).toContain('terminate');
    expect(availableOps(v('FAILED'))).toContain('delete');
  });
});

describe('topology layout', () => {
  it('places nodes in layers by type', () => {
    const nodes: TopoNode[] = [
      { id: 'h1', type: 'HOST', label: 'h1', status: 'UP', dataSource: 'DEMO' },
      { id: 'c', type: 'CONTROLLER', label: 'ONOS', status: 'UP', dataSource: 'DEMO' },
      { id: 's1', type: 'SWITCH', label: 's1', status: 'UP', dataSource: 'DEMO' },
    ];
    const { nodes: placed } = layout(nodes, 900);
    const y = Object.fromEntries(placed.map((n) => [n.id, n.y]));
    expect(y.c).toBeLessThan(y.s1);
    expect(y.s1).toBeLessThan(y.h1);
  });
});

describe('NETCONF XML pretty printer', () => {
  it('indents nested elements and keeps leaves inline', () => {
    const out = prettyXml('<firewall><rule><id>1</id></rule></firewall>');
    expect(out).toContain('  <rule>');
    expect(out).toContain('<id>1</id>');
  });
});
