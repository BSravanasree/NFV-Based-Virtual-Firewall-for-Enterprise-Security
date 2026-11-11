import { CheckCircle2, Info, XCircle, X } from 'lucide-react';
import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';

type ToastKind = 'success' | 'error' | 'info';
interface ToastItem { id: number; kind: ToastKind; title: string; detail?: string }

interface ToastApi {
  success: (title: string, detail?: string) => void;
  error: (title: string, detail?: string) => void;
  info: (title: string, detail?: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);
let seq = 0;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const remove = useCallback((id: number) => setItems((l) => l.filter((t) => t.id !== id)), []);
  const push = useCallback((kind: ToastKind, title: string, detail?: string) => {
    const id = ++seq;
    setItems((l) => [...l.slice(-4), { id, kind, title, detail }]);
    setTimeout(() => remove(id), kind === 'error' ? 9000 : 5000);
  }, [remove]);
  const api: ToastApi = {
    success: (t, d) => push('success', t, d),
    error: (t, d) => push('error', t, d),
    info: (t, d) => push('info', t, d),
  };
  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="fixed right-4 bottom-4 z-[60] flex w-96 max-w-[calc(100vw-2rem)] flex-col gap-2" aria-live="polite">
        {items.map((t) => (
          <div key={t.id} className="glass-strong flex items-start gap-2.5 rounded-xl p-3 shadow-2xl animate-[fade-up_0.25s_ease-out]" role={t.kind === 'error' ? 'alert' : 'status'}>
            {t.kind === 'success' && <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />}
            {t.kind === 'error' && <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-400" />}
            {t.kind === 'info' && <Info className="mt-0.5 h-4 w-4 shrink-0 text-sky-400" />}
            <div className="min-w-0 flex-1">
              <div className="text-sm font-medium text-slate-100">{t.title}</div>
              {t.detail && <div className="mt-0.5 text-xs break-words text-slate-400">{t.detail}</div>}
            </div>
            <button onClick={() => remove(t.id)} className="text-slate-500 hover:text-slate-300" aria-label="Dismiss">
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within ToastProvider');
  return ctx;
}
