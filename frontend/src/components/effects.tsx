import { useEffect, useRef, useState, type ReactNode } from 'react';
import { cx } from './ui';

export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return true;
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/** Soft blurred gradient bubbles behind the content. Purely decorative (aria-hidden, pointer-events none). */
export function AmbientBackground({ intensity = 'normal' }: { intensity?: 'normal' | 'subtle' }) {
  const o = intensity === 'subtle' ? 'opacity-60' : '';
  return (
    <div aria-hidden className={cx('pointer-events-none fixed inset-0 -z-0 overflow-hidden', o)}>
      <div className="bubble top-[-8rem] left-[-6rem] h-[26rem] w-[26rem] bg-[#6c8cff]/30" />
      <div className="bubble top-[30%] right-[-8rem] h-[24rem] w-[24rem] bg-[#a78bfa]/25 [animation-delay:-3s]" />
      <div className="bubble bottom-[-10rem] left-[30%] h-[22rem] w-[22rem] bg-[#38d9ff]/15 [animation-delay:-6s]" />
    </div>
  );
}

/**
 * Counts up to a numeric value. Used only for real API values; renders the final value immediately when motion is
 * reduced (or in non-browser environments such as tests).
 */
export function AnimatedNumber({ value, format = (n) => Math.round(n).toLocaleString(), duration = 700 }: {
  value: number | null | undefined; format?: (n: number) => string; duration?: number;
}) {
  const [shown, setShown] = useState<number | null>(value ?? null);
  const from = useRef<number>(value ?? 0);

  useEffect(() => {
    if (value == null) {
      setShown(null);
      return;
    }
    if (prefersReducedMotion() || typeof requestAnimationFrame !== 'function') {
      setShown(value);
      from.current = value;
      return;
    }
    const start = performance.now();
    const a = from.current;
    let raf = 0;
    const tick = (t: number) => {
      const p = Math.min(1, (t - start) / duration);
      const eased = 1 - Math.pow(1 - p, 3);
      setShown(a + (value - a) * eased);
      if (p < 1) raf = requestAnimationFrame(tick);
      else from.current = value;
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [value, duration]);

  return <>{shown == null ? '—' : format(shown)}</>;
}

/**
 * Fades content in as it scrolls into view using CSS scroll-driven animations (`.reveal` in index.css).
 * Content is never hidden by JavaScript state: browsers without support, crawlers, link previews, printing
 * and reduced-motion users simply see it. `delay` staggers neighbouring cards slightly.
 */
export function Reveal({ children, className, delay = 0 }: { children: ReactNode; className?: string; delay?: number }) {
  return (
    <div className={cx('reveal', className)} style={delay ? ({ '--reveal-shift': `${12 + delay / 10}px` } as React.CSSProperties) : undefined}>
      {children}
    </div>
  );
}
