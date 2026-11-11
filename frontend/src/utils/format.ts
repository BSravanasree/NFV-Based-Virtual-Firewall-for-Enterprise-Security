export function bytes(n?: number | null): string {
  if (n == null) return '—';
  const u = ['B', 'KB', 'MB', 'GB', 'TB'];
  let i = 0;
  let v = n;
  while (v >= 1024 && i < u.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(v < 10 && i > 0 ? 1 : 0)} ${u[i]}`;
}

export function bps(n?: number | null): string {
  if (n == null) return '—';
  const u = ['bps', 'Kbps', 'Mbps', 'Gbps'];
  let i = 0;
  let v = n;
  while (v >= 1000 && i < u.length - 1) {
    v /= 1000;
    i++;
  }
  return `${v.toFixed(v < 10 && i > 0 ? 1 : 0)} ${u[i]}`;
}

export function num(n?: number | null): string {
  return n == null ? '—' : n.toLocaleString();
}

export function pct(n?: number | null): string {
  return n == null ? '—' : `${Number(n).toFixed(1)}%`;
}

export function dateTime(s?: string | null): string {
  if (!s) return '—';
  return new Date(s).toLocaleString(undefined, {
    year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
}

export function timeShort(s: string): string {
  return new Date(s).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

export function ago(s?: string | null): string {
  if (!s) return '—';
  const d = (Date.now() - new Date(s).getTime()) / 1000;
  if (d < 5) return 'just now';
  if (d < 60) return `${Math.floor(d)}s ago`;
  if (d < 3600) return `${Math.floor(d / 60)}m ago`;
  if (d < 86400) return `${Math.floor(d / 3600)}h ago`;
  return `${Math.floor(d / 86400)}d ago`;
}

/** Client-side mirrors of backend validation (backend remains authoritative). */
const OCTET = '(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)';
export const IPV4_RE = new RegExp(`^${OCTET}(\\.${OCTET}){3}$`);
export const CIDR_RE = new RegExp(`^${OCTET}(\\.${OCTET}){3}(\\/(3[0-2]|[12]?\\d))?$`);

export function validPortSpec(s: string): boolean {
  if (!/^\d{1,5}(-\d{1,5})?$/.test(s)) return false;
  const [lo, hi = lo] = s.split('-').map(Number);
  return lo >= 1 && hi <= 65535 && lo <= hi;
}
