'use client';

import clsx from 'clsx';
import { motion } from 'motion/react';
import { useState, useSyncExternalStore, type ReactNode } from 'react';
import { Button } from '@/design-system/primitives/Button';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { Tbc } from '@/design-system/primitives/Text';
import { formatDateTime } from '@/lib/format/dates';
import { RegBand } from './Shell';
import s from './Registration.module.css';

const FACEBOOK = 'https://www.facebook.com/';

function useNow() {
  return useSyncExternalStore(
    (cb) => {
      const t = setInterval(cb, 30_000);
      return () => clearInterval(t);
    },
    () => Math.floor(Date.now() / 30_000) * 30_000,
    () => null,
  );
}

function CountdownTiles({ target }: { target: string | null }) {
  const now = useNow();
  const ms = target && now !== null ? Math.max(0, new Date(target).getTime() - now) : null;
  const parts =
    ms === null
      ? null
      : { Days: Math.floor(ms / 86_400_000), Hrs: Math.floor((ms % 86_400_000) / 3_600_000), Min: Math.floor((ms % 3_600_000) / 60_000) };
  return (
    <div className={s.countdown} role="timer" aria-live="off">
      {(['Days', 'Hrs', 'Min'] as const).map((k) => (
        <div key={k} className={s.countCell}>
          <div className="num" style={{ fontSize: 32, lineHeight: 1 }}>
            {parts ? String(parts[k]).padStart(2, '0') : '--'}
          </div>
          <div className="eyebrow">{k}</div>
        </div>
      ))}
    </div>
  );
}

export function NotOpenScreen({
  who,
  opensAt,
  facebookUrl,
  needs,
}: {
  who: 'Player' | 'Team';
  opensAt: string | null;
  facebookUrl?: string | null;
  needs: string[];
}) {
  return (
    <>
      <RegBand variant="state" title={`${who} registration opens soon`} lead={<StatusChip tone="neutral">Opens soon</StatusChip>}>
        <p className="muted" style={{ margin: 0, fontSize: 17 }}>
          Registrations open on {opensAt ? formatDateTime(opensAt) : <Tbc>[OPENING DATE TBC]</Tbc>}.
          {who === 'Player' ? ' Open to Jaffna players only.' : ''}
        </p>
        <CountdownTiles target={opensAt} />
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <Button variant="volt" href={facebookUrl ?? FACEBOOK} target="_blank" rel="noopener noreferrer">
            Follow on Facebook for updates
          </Button>
          <Button variant="out" href="/">
            Back to home
          </Button>
        </div>
      </RegBand>
      <div className={clsx(s.wrap, s.body)} style={{ paddingTop: 40 }}>
        <div className="card" style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 12, maxWidth: 640 }}>
          <strong>Get ready — you&rsquo;ll need</strong>
          <ul style={{ margin: 0, paddingLeft: 20, display: 'flex', flexDirection: 'column', gap: 6 }}>
            {needs.map((n) => (
              <li key={n}>{n}</li>
            ))}
          </ul>
        </div>
      </div>
    </>
  );
}

export function ClosedScreen({ who, closedAt, contact }: { who: 'Player' | 'Team'; closedAt: string | null; contact?: ReactNode }) {
  return (
    <>
      <RegBand variant="state" title={`${who} registration is closed`} lead={<StatusChip tone="neutral">Closed</StatusChip>}>
        <p className="muted" style={{ margin: 0, fontSize: 17 }}>
          Registrations closed{closedAt ? ` on ${formatDateTime(closedAt)}` : ''}. Already registered? The organisers will contact you after
          review.
        </p>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <Button variant="volt" href={who === 'Player' ? '/players' : '/teams'}>
            {who === 'Player' ? 'See approved players' : 'See the teams'}
          </Button>
          <Button variant="out" href="/auction">
            Auction information
          </Button>
        </div>
      </RegBand>
      <div className={clsx(s.wrap, s.body)} style={{ paddingTop: 40 }}>
        <div className="card" style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 8, maxWidth: 640 }}>
          <strong>Questions about your registration?</strong>
          <span className="muted">Contact the organisers with your reference number{contact ? <>: {contact}</> : '.'}</span>
        </div>
      </div>
    </>
  );
}

export function ConfirmationScreen({
  reference,
  who,
  facebookUrl,
  steps,
}: {
  reference: string;
  who: 'player' | 'team';
  facebookUrl?: string | null;
  steps: { title: string; body: ReactNode }[];
}) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(reference);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  };
  const tones = ['var(--ok)', 'var(--warn)', 'var(--line-2)'];
  const chips = [
    { tone: 'ok' as const, label: 'Done' },
    { tone: 'pending' as const, label: 'Next' },
    { tone: 'neutral' as const, label: 'Later' },
  ];
  return (
    <>
      <RegBand
        variant="center"
        title={who === 'player' ? 'Registration received' : 'Application received'}
        lead={<StatusChip tone="pending">Pending review</StatusChip>}
      >
        <p className="muted" style={{ margin: 0, maxWidth: 560 }}>
          Thanks for {who === 'player' ? 'registering' : 'entering a team'} for NGBCL 1.0. Keep your reference — you&rsquo;ll need it if you
          contact the organisers.
        </p>
        <motion.div
          className={s.refBox}
          initial={{ scale: 0.96, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1], delay: 0.1 }}
        >
          <span className="eyebrow">Your {who === 'player' ? 'registration' : 'application'} reference</span>
          <span className={s.refNum} data-testid="reference">
            {reference}
          </span>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', justifyContent: 'center', marginTop: 6 }}>
            <Button variant="volt" onClick={copy} aria-live="polite">
              {copied ? 'Copied' : 'Copy reference'}
            </Button>
          </div>
        </motion.div>
      </RegBand>
      <div className={s.wrap} style={{ paddingTop: 40, paddingBottom: 64, display: 'flex', flexDirection: 'column', gap: 24 }}>
        <h2 style={{ margin: 0, fontSize: 22, fontWeight: 600 }}>What happens next</h2>
        <ol className={s.timeline}>
          {steps.map((st, i) => (
            <motion.li
              key={st.title}
              className="card"
              style={{ borderTop: `3px solid ${tones[i]}` }}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.2 + i * 0.08, duration: 0.32 }}
            >
              <span style={{ alignSelf: 'flex-start' }}>
                <StatusChip tone={chips[i].tone}>{chips[i].label}</StatusChip>
              </span>
              <strong>
                {i + 1} · {st.title}
              </strong>
              <span className="muted" style={{ fontSize: 14 }}>
                {st.body}
              </span>
            </motion.li>
          ))}
        </ol>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <Button variant="volt" href="/">
            Back to home
          </Button>
          <Button variant="out" href={facebookUrl ?? FACEBOOK} target="_blank" rel="noopener noreferrer">
            Follow on Facebook
          </Button>
        </div>
      </div>
    </>
  );
}
