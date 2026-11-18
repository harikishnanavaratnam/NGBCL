'use client';

import clsx from 'clsx';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Skeleton } from '@/design-system/data/States';
import { Select } from '@/design-system/primitives/Inputs';
import { ROLE_LABEL, hasAny } from '@/lib/auth/permissions';
import { homePathFor, useAuth } from '@/lib/auth/session';
import { initials } from '@/lib/format/names';
import { ADMIN_NAV } from './nav';
import styles from './AdminShell.module.css';

/** Admin portal shell: 240 px role-filtered sidebar (→ 64 px icons / drawer < 1024 px) + top bar. */
export function AdminShell({ children }: { children: ReactNode }) {
  const { status, user, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const [collapsed, setCollapsed] = useState(false);
  const [drawer, setDrawer] = useState(false);

  const [lastPath, setLastPath] = useState(pathname);
  if (lastPath !== pathname) {
    setLastPath(pathname);
    setDrawer(false);
  }
  useEffect(() => {
    if (status === 'anonymous') router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    // Auctioneers and scorers have their own surfaces (brief §6) — the API would refuse admin calls anyway.
    else if (status === 'authenticated' && user && user.homeSurface !== 'ADMIN') router.replace(homePathFor(user));
  }, [status, user, pathname, router]);

  const groups = useMemo(
    () =>
      ADMIN_NAV.filter((g) => hasAny(user?.permissions, g.anyOf)).map((g) => ({
        ...g,
        items: g.items.filter((i) => !i.anyOf || hasAny(user?.permissions, i.anyOf)),
      })),
    [user],
  );

  if (status !== 'authenticated' || !user || user.homeSurface !== 'ADMIN') {
    return (
      <div className={styles.layout} aria-busy="true">
        <aside className={styles.side} style={{ padding: 20, gap: 12 }}>
          {Array.from({ length: 8 }, (_, i) => (
            <Skeleton key={i} height={28} />
          ))}
        </aside>
        <div className={styles.content}>
          <Skeleton height={36} width={320} />
        </div>
      </div>
    );
  }

  const groupActive = (g: (typeof groups)[number]) =>
    g.key === 'overview' ? pathname === '/admin' : pathname.startsWith(`/admin/${g.key}`);

  return (
    <div className={clsx(styles.layout, collapsed && styles.collapsed)}>
      {drawer && <div className="ds-scrim" style={{ position: 'fixed', inset: 0, zIndex: 74 }} onClick={() => setDrawer(false)} aria-hidden="true" />}
      <aside className={clsx(styles.side, drawer && styles.sideOpen)} aria-label="Admin navigation">
        <Link href="/admin" className={styles.brand}>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/brand/ngbcl-mark-on-dark.svg" alt="" aria-hidden="true" />
          <span className={styles.brandText} style={{ display: 'flex', flexDirection: 'column' }}>
            <strong style={{ fontSize: 14, color: 'var(--text)' }}>NGBCL Admin</strong>
            <span style={{ fontSize: 12, color: 'var(--text-2)' }}>Tournament management</span>
          </span>
        </Link>
        <nav className={styles.nav}>
          {groups.map((g) => {
            const on = groupActive(g);
            return (
              <div key={g.key} style={{ display: 'flex', flexDirection: 'column' }}>
                <Link href={g.href} className={clsx(styles.group, on && styles.on)} aria-current={on && g.key === 'overview' ? 'page' : undefined} title={collapsed ? g.label : undefined}>
                  <span className={styles.icon} aria-hidden="true" />
                  <span className={styles.label}>{g.label}</span>
                </Link>
                {g.items.length > 0 && (
                  <div className={styles.sub}>
                    {g.items.map((i) => (
                      <Link key={i.href} href={i.href} className={clsx(pathname === i.href && styles.on)} aria-current={pathname === i.href ? 'page' : undefined}>
                        {i.label}
                      </Link>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </nav>
        <div className={styles.sideFoot}>
          <button type="button" className={clsx('btn btn-ghost btn-sm', styles.collapseBtn)} onClick={() => setCollapsed((c) => !c)} aria-pressed={collapsed}>
            {collapsed ? '»' : '« Collapse'}
          </button>
        </div>
      </aside>

      <div className={styles.main}>
        <header className={styles.top}>
          <button type="button" className={clsx('btn btn-out', styles.burger)} style={{ width: 44, padding: 0 }} aria-label="Open navigation" onClick={() => setDrawer(true)}>
            ☰
          </button>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
            <span className="eyebrow">Edition</span>
            <Select style={{ width: 'auto', minHeight: 36, fontWeight: 600 }} defaultValue="1.0" aria-label="Tournament edition">
              <option value="1.0">NGBCL 1.0</option>
            </Select>
          </label>
          <div className={styles.search} role="search">
            <label htmlFor="gsearch" className="sr-only">
              Search
            </label>
            <input id="gsearch" className="inp" type="search" placeholder="Search players, teams, references (PLY-…, TEM-…)" />
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
              <circle cx="11" cy="11" r="7" />
              <path d="M20 20l-3.5-3.5" strokeLinecap="round" />
            </svg>
          </div>
          <div className={styles.user}>
            <span className="ph" style={{ width: 32, height: 32, borderRadius: '50%', padding: 0, fontSize: 11, color: 'var(--text)' }} aria-hidden="true">
              {initials(user.displayName)}
            </span>
            <span style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.2 }}>
              <strong style={{ fontSize: 13 }}>{user.displayName}</strong>
              <span className="muted" style={{ fontSize: 12 }}>
                {user.roles.map((r) => ROLE_LABEL[r]).join(', ')}
              </span>
            </span>
            <button
              type="button"
              className="btn btn-out btn-sm"
              onClick={async () => {
                await logout();
                router.replace('/login');
              }}
            >
              Sign out
            </button>
          </div>
        </header>
        <main id="main" className={styles.content}>
          {children}
        </main>
      </div>
    </div>
  );
}
