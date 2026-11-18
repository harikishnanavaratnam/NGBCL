'use client';

import { AnimatePresence, motion } from 'motion/react';
import { useEffect, useId, useRef, useState, useSyncExternalStore, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Button } from './Button';
import { panelTransition } from '../motion/presets';

function useEscape(open: boolean, onClose: () => void) {
  useEffect(() => {
    if (!open) return;
    const h = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', h);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', h);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);
}

function Portal({ children }: { children: ReactNode }) {
  const mounted = useSyncExternalStore(
    () => () => {},
    () => true,
    () => false,
  );
  return mounted ? createPortal(children, document.body) : null;
}

/** Focus the first focusable element in the panel when it opens. */
function useInitialFocus(open: boolean) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const t = setTimeout(() => {
      const el = ref.current?.querySelector<HTMLElement>('[data-autofocus], button, [href], input, select, textarea');
      el?.focus();
    }, 30);
    return () => clearTimeout(t);
  }, [open]);
  return ref;
}

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  width?: number;
}

export function Modal({ open, onClose, title, children, footer, width = 520 }: ModalProps) {
  useEscape(open, onClose);
  const ref = useInitialFocus(open);
  const titleId = useId();
  return (
    <Portal>
      <AnimatePresence>
        {open && (
          <motion.div
            className="ds-scrim"
            style={{ position: 'fixed', inset: 0, zIndex: 80, display: 'grid', placeItems: 'center', padding: 16 }}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={panelTransition}
            onMouseDown={(e) => e.target === e.currentTarget && onClose()}
          >
            <motion.div
              ref={ref}
              role="dialog"
              aria-modal="true"
              aria-labelledby={titleId}
              className="ds-modal"
              style={{ width: '100%', maxWidth: width, maxHeight: '90dvh', overflow: 'auto' }}
              initial={{ opacity: 0, y: 16, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8 }}
              transition={panelTransition}
            >
              <div style={{ padding: '20px 24px 0' }}>
                <h2 id={titleId} className="t-h3">
                  {title}
                </h2>
              </div>
              <div style={{ padding: '16px 24px' }}>{children}</div>
              {footer && (
                <div style={{ padding: '16px 24px', borderTop: '1px solid var(--line)', display: 'flex', justifyContent: 'flex-end', gap: 10, flexWrap: 'wrap' }}>
                  {footer}
                </div>
              )}
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </Portal>
  );
}

interface DrawerProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  /** 480 (default) or 720 (review drawer, tie editor) — brief §5. */
  size?: 480 | 720;
}

export function Drawer({ open, onClose, title, children, footer, size = 480 }: DrawerProps) {
  useEscape(open, onClose);
  const ref = useInitialFocus(open);
  const titleId = useId();
  return (
    <Portal>
      <AnimatePresence>
        {open && (
          <motion.div
            className="ds-scrim"
            style={{ position: 'fixed', inset: 0, zIndex: 70 }}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={panelTransition}
            onMouseDown={(e) => e.target === e.currentTarget && onClose()}
          >
            <motion.aside
              ref={ref as never}
              role="dialog"
              aria-modal="true"
              aria-labelledby={titleId}
              className="drawer"
              style={{ position: 'absolute', top: 0, right: 0, bottom: 0, width: `min(${size}px, 100vw)`, background: 'var(--bg-2)', display: 'flex', flexDirection: 'column' }}
              initial={{ x: '100%' }}
              animate={{ x: 0 }}
              exit={{ x: '100%' }}
              transition={panelTransition}
            >
              <header style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '18px 24px', borderBottom: '1px solid var(--line)' }}>
                <h2 id={titleId} className="t-h3" style={{ flex: 1 }}>
                  {title}
                </h2>
                <button type="button" className="btn btn-out btn-sm" onClick={onClose} aria-label="Close panel" style={{ width: 36, padding: 0 }}>
                  ✕
                </button>
              </header>
              <div style={{ flex: 1, overflow: 'auto', padding: 24 }}>{children}</div>
              {footer && <footer style={{ padding: '16px 24px', borderTop: '1px solid var(--line)', display: 'flex', gap: 10, justifyContent: 'flex-end' }}>{footer}</footer>}
            </motion.aside>
          </motion.div>
        )}
      </AnimatePresence>
    </Portal>
  );
}

interface ConfirmProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void | Promise<void>;
  title: ReactNode;
  /** Summary of consequences — required for irreversible actions (brief principle 5). */
  consequences: ReactNode;
  confirmLabel: string;
  tone?: 'volt' | 'gold' | 'danger';
  /** For the riskiest actions: user must type this word to enable the confirm button. */
  typeToConfirm?: string;
}

/** Two-step confirm used for SOLD, approve, confirm result, corrections. */
export function ConfirmDialog({ open, onClose, onConfirm, title, consequences, confirmLabel, tone = 'volt', typeToConfirm }: ConfirmProps) {
  const [busy, setBusy] = useState(false);
  const [typed, setTyped] = useState('');
  const close = () => {
    setTyped('');
    onClose();
  };
  const blocked = Boolean(typeToConfirm) && typed.trim().toUpperCase() !== typeToConfirm!.toUpperCase();
  return (
    <Modal
      open={open}
      onClose={busy ? () => {} : close}
      title={title}
      footer={
        <>
          <Button variant="out" onClick={close} disabled={busy}>
            Cancel
          </Button>
          <Button
            variant={tone === 'danger' ? 'danger' : tone}
            loading={busy}
            disabled={blocked}
            onClick={async () => {
              setBusy(true);
              try {
                await onConfirm();
                close();
              } finally {
                setBusy(false);
              }
            }}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="t-body muted" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {consequences}
        {typeToConfirm && (
          <label className="field">
            <span className="lbl">
              Type <span className="mono">{typeToConfirm}</span> to confirm
            </span>
            <input className="inp" value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off" />
          </label>
        )}
      </div>
    </Modal>
  );
}
