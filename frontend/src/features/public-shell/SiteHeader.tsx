'use client';

import { AnimatePresence, motion } from 'motion/react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useState } from 'react';
import { panelTransition } from '@/design-system/motion/presets';
import { FACEBOOK_URL, PUBLIC_NAV } from './site-nav';
import styles from './SiteHeader.module.css';

export interface LiveSummary {
  label: string;
  href: string;
}

/** Sticky top bar: wordmark + 1.0 seal, 8 links, Live pill, Facebook, Register (export Main · 01). */
export function SiteHeader({ live, facebookUrl }: { live?: LiveSummary | null; facebookUrl?: string | null }) {
  const fb = facebookUrl ?? FACEBOOK_URL;
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const [lastPath, setLastPath] = useState(pathname);
  if (lastPath !== pathname) {
    setLastPath(pathname);
    setOpen(false);
  }

  const isActive = (href: string) => (href === '/' ? pathname === '/' : pathname.startsWith(href));

  return (
    <header className={styles.header}>
      {live && (
        <div className={styles.liveStrip}>
          <div className={`wrap ${styles.liveInner}`}>
            <span className="dot" aria-hidden="true" />
            <span style={{ whiteSpace: 'nowrap' }}>LIVE NOW</span>
            <span style={{ fontWeight: 500, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{live.label}</span>
            <Link href={live.href}>Watch live</Link>
          </div>
        </div>
      )}
      <nav className={`wrap ${styles.nav}`} aria-label="Main">
        <Link href="/" className={styles.brand} aria-label="NGBCL 1.0 home">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/brand/ngbcl-wordmark-on-dark.svg" alt="NGBCL — Next Gen Badminton Champions League" width={70} height={40} />
          <span className={`chip ${styles.seal}`}>1.0</span>
        </Link>
        <div className={styles.links}>
          {PUBLIC_NAV.map((l) => (
            <Link key={l.href} href={l.href} aria-current={isActive(l.href) ? 'page' : undefined}>
              {l.label}
            </Link>
          ))}
        </div>
        <div className={styles.actions}>
          {live && (
            <Link href={live.href} className="chip c-live" style={{ textDecoration: 'none' }}>
              <span className="dot" aria-hidden="true" />
              Live
            </Link>
          )}
          <a href={fb} className="link" style={{ fontSize: 13 }} target="_blank" rel="noopener noreferrer">
            <span className={styles.fbFull}>Follow on </span>Facebook
          </a>
          <Link href="/register/player" className="btn btn-volt btn-sm">
            Register
          </Link>
          <button type="button" className={styles.burger} aria-label="Open menu" aria-expanded={open} onClick={() => setOpen(true)}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
              <path d="M4 7h16M4 12h16M4 17h16" />
            </svg>
          </button>
        </div>
      </nav>

      <AnimatePresence>
        {open && (
          <motion.div
            className={styles.sheet}
            role="dialog"
            aria-modal="true"
            aria-label="Menu"
            initial={{ opacity: 0, y: -12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -12 }}
            transition={panelTransition}
          >
            <div className={styles.sheetTop}>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src="/brand/ngbcl-wordmark-on-dark.svg" alt="NGBCL" style={{ height: 34, width: 'auto' }} />
              <button type="button" className={styles.burger} style={{ display: 'inline-flex' }} aria-label="Close menu" onClick={() => setOpen(false)}>
                ✕
              </button>
            </div>
            <div className={styles.sheetLinks}>
              {PUBLIC_NAV.map((l) => (
                <Link key={l.href} href={l.href} aria-current={isActive(l.href) ? 'page' : undefined}>
                  {l.label}
                </Link>
              ))}
            </div>
            <div className={styles.sheetCtas}>
              <Link href="/register/player" className="btn btn-volt">
                Register as a player
              </Link>
              <Link href="/register/team" className="btn btn-out">
                Enter a team
              </Link>
              <a href={fb} className="btn btn-ghost" target="_blank" rel="noopener noreferrer">
                Follow on Facebook
              </a>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  );
}
