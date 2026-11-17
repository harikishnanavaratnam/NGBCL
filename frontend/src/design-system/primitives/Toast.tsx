'use client';

import { AnimatePresence, motion } from 'motion/react';
import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react';
import { panelTransition } from '../motion/presets';

type Tone = 'ok' | 'error' | 'info' | 'live';

interface Toast {
  id: number;
  tone: Tone;
  message: ReactNode;
  action?: { label: string; onClick: () => void };
  duration: number;
}

interface ToastApi {
  show: (t: Omit<Toast, 'id' | 'duration'> & { duration?: number }) => number;
  success: (message: ReactNode) => number;
  error: (message: ReactNode) => number;
  dismiss: (id: number) => void;
}

const Ctx = createContext<ToastApi | null>(null);

const toneStyle: Record<Tone, { bar: string }> = {
  ok: { bar: 'var(--ok)' },
  error: { bar: 'var(--err)' },
  info: { bar: 'var(--info)' },
  live: { bar: 'var(--lime)' },
};

/** Toasts, including the scorer's 5-second undo window (brief §5 live components). */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const seq = useRef(0);

  const dismiss = useCallback((id: number) => setToasts((t) => t.filter((x) => x.id !== id)), []);
  const show = useCallback<ToastApi['show']>(
    (t) => {
      const id = ++seq.current;
      const toast: Toast = { duration: 5000, ...t, id };
      setToasts((list) => [...list.slice(-3), toast]);
      if (toast.duration > 0) setTimeout(() => dismiss(id), toast.duration);
      return id;
    },
    [dismiss],
  );

  const api = useMemo<ToastApi>(
    () => ({
      show,
      dismiss,
      success: (message) => show({ tone: 'ok', message }),
      error: (message) => show({ tone: 'error', message, duration: 8000 }),
    }),
    [show, dismiss],
  );

  return (
    <Ctx.Provider value={api}>
      {children}
      <div aria-live="polite" style={{ position: 'fixed', bottom: 16, right: 16, zIndex: 90, display: 'flex', flexDirection: 'column', gap: 8, width: 'min(380px, calc(100vw - 32px))' }}>
        <AnimatePresence initial={false}>
          {toasts.map((t) => (
            <motion.div
              key={t.id}
              layout
              role={t.tone === 'error' ? 'alert' : 'status'}
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, x: 24 }}
              transition={panelTransition}
              className="ds-modal"
              style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px', boxShadow: `inset 3px 0 0 ${toneStyle[t.tone].bar}, var(--shadow-overlay)` }}
            >
              <span style={{ flex: 1, fontSize: 14 }}>{t.message}</span>
              {t.action && (
                <button
                  type="button"
                  className="btn btn-out btn-sm"
                  onClick={() => {
                    t.action!.onClick();
                    dismiss(t.id);
                  }}
                >
                  {t.action.label}
                </button>
              )}
              <button type="button" aria-label="Dismiss" onClick={() => dismiss(t.id)} style={{ background: 'none', border: 0, color: 'var(--text-2)', cursor: 'pointer', fontSize: 16 }}>
                ✕
              </button>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </Ctx.Provider>
  );
}

export function useToast() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
}
