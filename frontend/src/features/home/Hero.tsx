'use client';

import { motion } from 'motion/react';
import Link from 'next/link';
import { OrTbc } from '@/design-system/primitives/Text';
import { Countdown } from './Countdown';
import { HeroVideo } from './HeroVideo';
import styles from './Hero.module.css';

export type TournamentPhase = 'PRE_LAUNCH' | 'REGISTRATION' | 'AUCTION' | 'RUNNING' | 'COMPLETED';

export interface HeroData {
  phase: TournamentPhase;
  dates: string | null;
  venue: string | null;
  registrationClosesAt: string | null;
}

/** Championship hero (export Main · 02) with phase-aware CTAs (brief §7.1). */
export function Hero({ data }: { data: HeroData }) {
  return (
    <section id="top" className={styles.hero} aria-labelledby="hero-title">
      <HeroVideo />
      <div className={styles.overlay} />
      <motion.div
        className={`wrap ${styles.content}`}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
      >
        <div className="eyebrow" style={{ color: 'var(--gold-2)' }}>
          Mighty Sharks Battalion presents
        </div>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img className={styles.logo} src="/brand/ngbcl-logo-on-dark.svg" alt="NGBCL — Next Gen Badminton Champions League" width={233} height={150} />
        <h1 id="hero-title" className={`disp ${styles.title}`}>
          Next Gen Badminton Champions League <span style={{ color: 'var(--gold-2)' }}>1.0</span>
        </h1>
        <p className={styles.tagline}>Rise. Rally. Rule the Court.</p>
        <div className={styles.meta}>
          <span>
            <span className="eyebrow">Dates </span>
            <OrTbc value={data.dates} label="DATES" />
          </span>
          <span>
            <span className="eyebrow">Venue </span>
            <OrTbc value={data.venue} label="VENUE" />, Jaffna
          </span>
          <span className="chip c-neutral">Inaugural edition</span>
        </div>
        <PhaseCta data={data} />
      </motion.div>
    </section>
  );
}

function PhaseCta({ data }: { data: HeroData }) {
  switch (data.phase) {
    case 'AUCTION':
      return (
        <div className={styles.ctas}>
          <Link href="/auction" className="btn btn-volt">
            <span className="dot" aria-hidden="true" /> Watch the auction live
          </Link>
        </div>
      );
    case 'RUNNING':
      return (
        <div className={styles.ctas}>
          <Link href="/live" className="btn btn-volt">
            Live now
          </Link>
          <Link href="/fixtures" className="btn btn-out">
            Fixtures
          </Link>
        </div>
      );
    case 'COMPLETED':
      return (
        <div className={styles.ctas}>
          <Link href="/standings" className="btn btn-gold">
            See the champions
          </Link>
        </div>
      );
    case 'PRE_LAUNCH':
      return <p className="muted">Registrations open soon — follow us on Facebook for the announcement.</p>;
    default:
      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20, width: '100%', maxWidth: 640, marginTop: 8 }}>
          <div className={styles.ctas}>
            <Link href="/register/player" className="btn btn-volt">
              Register as a player
            </Link>
            <Link href="/register/team" className="btn btn-out">
              Enter a team
            </Link>
            <span className={`chip ${styles.jaffna}`}>Jaffna players only</span>
          </div>
          <Countdown target={data.registrationClosesAt} label="Registration closes in ·" />
        </div>
      );
  }
}
