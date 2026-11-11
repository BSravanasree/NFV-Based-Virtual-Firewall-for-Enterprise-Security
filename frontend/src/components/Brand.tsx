import { cx } from './ui';

/** NEXUS NFV logo mark: a shield built from network nodes. */
export function LogoMark({ className = 'h-8 w-8' }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" className={className} aria-hidden>
      <defs>
        <linearGradient id="nx-g" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#8ba3ff" />
          <stop offset="0.55" stopColor="#a78bfa" />
          <stop offset="1" stopColor="#38d9ff" />
        </linearGradient>
      </defs>
      <rect width="40" height="40" rx="11" fill="rgba(255,255,255,0.06)" stroke="rgba(255,255,255,0.12)" />
      <path d="M20 7.5 10.5 11v7.2c0 6.1 4 11.3 9.5 13.3 5.5-2 9.5-7.2 9.5-13.3V11L20 7.5Z" fill="none" stroke="url(#nx-g)" strokeWidth="2" strokeLinejoin="round" />
      <path d="M15 19.5h10M20 14.5v10" stroke="url(#nx-g)" strokeWidth="1.6" strokeLinecap="round" opacity="0.8" />
      <circle cx="15" cy="19.5" r="1.9" fill="#8ba3ff" />
      <circle cx="25" cy="19.5" r="1.9" fill="#a78bfa" />
      <circle cx="20" cy="14.5" r="1.9" fill="#38d9ff" />
      <circle cx="20" cy="24.5" r="1.9" fill="#8ba3ff" />
    </svg>
  );
}

export function Wordmark({ compact, className }: { compact?: boolean; className?: string }) {
  return (
    <div className={cx('flex items-center gap-2.5', className)}>
      <LogoMark />
      {!compact && (
        <div className="leading-tight">
          <div className="text-sm font-bold tracking-wide text-fg">NEXUS <span className="text-gradient">NFV</span></div>
          <div className="text-[10px] tracking-[0.16em] text-slate-500 uppercase">Virtual Firewall Platform</div>
        </div>
      )}
    </div>
  );
}
