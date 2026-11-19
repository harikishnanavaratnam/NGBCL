'use client';

import clsx from 'clsx';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import styles from './Settings.module.css';

const TABS = [
  { href: '/admin/system/settings', label: 'General' },
  { href: '/admin/system/settings/branding', label: 'Branding' },
  { href: '/admin/system/settings/registration', label: 'Registration' },
  { href: '/admin/system/settings/categories', label: 'Skill categories' },
  { href: '/admin/system/settings/auction', label: 'Auction' },
  { href: '/admin/system/settings/match-categories', label: 'Match categories' },
  { href: '/admin/system/settings/scoring', label: 'Scoring rules', flag: 'M6' },
  { href: '/admin/system/settings/standings', label: 'Standings', flag: 'M7' },
];

/** Admin › System › Settings (export AdminSettings): vertical tabs on desktop, scrolling row on tablet. */
export function SettingsShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  return (
    <div className={styles.page}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <nav aria-label="Breadcrumb" className="t-caption">
          System / Settings
        </nav>
        <h1 className="t-h1">Tournament settings</h1>
        <p className="muted t-body-s">Every change is saved to the audit log with who changed it and when.</p>
      </div>
      <div className={styles.layout}>
        <nav className={styles.tabs} aria-label="Settings sections">
          {TABS.map((t) => (
            <Link
              key={t.href}
              href={t.href}
              className={clsx(styles.tab, t.flag && styles.tabDisabled)}
              aria-current={pathname === t.href ? 'page' : undefined}
              aria-disabled={t.flag ? true : undefined}
              tabIndex={t.flag ? -1 : undefined}
            >
              {t.label}
              {t.flag && <span className={styles.flag}>{t.flag}</span>}
            </Link>
          ))}
        </nav>
        <div className="card">{children}</div>
      </div>
    </div>
  );
}
