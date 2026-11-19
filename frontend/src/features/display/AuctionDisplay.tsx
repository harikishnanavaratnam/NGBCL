'use client';

import { AnimatePresence, motion } from 'motion/react';
import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { ConnectionPill } from '@/design-system/live/Live';
import { teamColor } from '@/design-system/tokens';
import { usePublicAuctionState, type PublicAuctionState, type PublicLot, type PublicTeamTicker } from '@/lib/api/auction';
import { formatDateTime } from '@/lib/format/dates';
import { formatCompact, formatRs } from '@/lib/format/money';
import { splitName } from '@/lib/format/names';
import { useConnectionState } from '@/lib/realtime/useTopic';
import { useAuctionLive } from '../auction/useAuctionLive';
import { ScaledCanvas } from './ScaledCanvas';

export interface DisplayTeam {
  name: string;
  colorSlot: number | null;
  logoUrl: string | null;
}

export interface DisplaySponsor {
  name: string;
  logoUrl: string | null;
}

interface Props {
  presenterLine: string | null;
  titleSponsor: DisplaySponsor | null;
  sponsors: DisplaySponsor[];
  /** Public team list, for the intro crest parade before budgets exist. */
  teams: DisplayTeam[];
}

type Reveal = { kind: 'sold' | 'unsold'; lot: PublicLot; key: number };

const EASE = [0.16, 1, 0.3, 1] as const;
const SOLD_MS = 6000;
const UNSOLD_MS = 3000;

/**
 * Big-screen auction display (export AuctionDisplay), 1920×1080 scaled to fit. Driven only by
 * the public state broadcast; reloading the page recovers instantly from the REST snapshot.
 */
export function AuctionDisplay(props: Props) {
  const { data } = usePublicAuctionState();
  const conn = useConnectionState();
  const [reveal, setReveal] = useState<Reveal | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useAuctionLive({
    onEvent: (type, state) => {
      const closed = state.lastClosed;
      if ((type === 'LOT_SOLD' || type === 'LOT_UNSOLD') && closed) {
        if (timer.current) clearTimeout(timer.current);
        const kind = type === 'LOT_SOLD' ? 'sold' : 'unsold';
        setReveal({ kind, lot: closed, key: Date.now() });
        timer.current = setTimeout(() => setReveal(null), kind === 'sold' ? SOLD_MS : UNSOLD_MS);
      } else if (type === 'LOT_OPENED' || type === 'CORRECTION' || type === 'ENDED') {
        if (timer.current) clearTimeout(timer.current);
        setReveal(null);
      }
    },
  });
  useEffect(() => () => {
    if (timer.current) clearTimeout(timer.current);
  }, []);

  const s = data;
  const scene: string = !s
    ? 'intro'
    : s.status === 'NOT_STARTED'
      ? 'intro'
      : s.status === 'COMPLETED'
        ? 'ended'
        : reveal
          ? reveal.kind
          : s.status === 'PAUSED'
            ? 'paused'
            : s.currentLot
              ? 'lot'
              : 'between';
  const liveChrome = scene !== 'intro';

  return (
    <ScaledCanvas>
      <div style={{ position: 'absolute', inset: 0, background: 'var(--bg)', color: 'var(--text)', cursor: 'none' }} role="img" aria-label={`Auction display: ${scene}`}>
        {liveChrome && s && <TopBar state={s} titleSponsor={props.titleSponsor} />}
        <AnimatePresence mode="wait">
          <motion.div key={scene + (reveal?.key ?? '')} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.4, ease: EASE }} style={{ position: 'absolute', inset: 0 }}>
            {scene === 'intro' && <Intro presenterLine={props.presenterLine} startsAt={s?.startsAt ?? null} teams={props.teams} />}
            {scene === 'lot' && s?.currentLot && <LotScene lot={s.currentLot} />}
            {scene === 'between' && s && <Between state={s} />}
            {scene === 'sold' && reveal && <SoldScene lot={reveal.lot} budgetsPublic={Boolean(s?.budgetsPublic)} />}
            {scene === 'unsold' && reveal && <UnsoldScene lot={reveal.lot} />}
            {scene === 'paused' && s && <Paused state={s} />}
            {scene === 'ended' && s && <Ended state={s} />}
          </motion.div>
        </AnimatePresence>
        {liveChrome && scene !== 'ended' && s && <Ticker teams={s.teams} budgetsPublic={s.budgetsPublic} sponsors={props.sponsors} />}
        <div style={{ position: 'absolute', right: 24, bottom: liveChrome && scene !== 'ended' ? 160 : 24, opacity: conn === 'live' ? 0 : 1, transition: 'opacity .3s', zIndex: 9 }}>
          <ConnectionPill state={conn} />
        </div>
      </div>
    </ScaledCanvas>
  );
}

// ---- Chrome --------------------------------------------------------------------------------

function TopBar({ state, titleSponsor }: { state: PublicAuctionState; titleSponsor: DisplaySponsor | null }) {
  const live = state.status === 'LIVE';
  return (
    <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 110, display: 'flex', alignItems: 'center', gap: 28, padding: '0 56px', background: 'linear-gradient(#191F27, rgba(16,19,24,0))', zIndex: 5 }}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/brand/ngbcl-wordmark-on-dark.svg" alt="NGBCL — Next Gen Badminton Champions League" style={{ height: 64, width: 'auto' }} />
      {state.status !== 'COMPLETED' && (
        <span className="chip" style={{ ...bigChip, background: live ? 'var(--lime)' : 'var(--warn)', color: '#101318' }}>
          <span style={{ width: 14, height: 14, borderRadius: '50%', background: '#FF5A62', display: 'inline-block' }} />
          {live ? 'Live auction' : 'Paused'}
        </span>
      )}
      <span className="mono" style={{ fontSize: 26 }}>
        Lot {state.lotsOpened || '—'} / {state.totalLots}
        {state.round > 1 ? ` · re-auction round ${state.round}` : ''}
      </span>
      {titleSponsor && (
        <>
          <span className="muted" style={{ marginLeft: 'auto', fontSize: 20 }}>
            presented by
          </span>
          {titleSponsor.logoUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={titleSponsor.logoUrl} alt={titleSponsor.name} style={{ height: 56, maxWidth: 220, objectFit: 'contain' }} />
          ) : (
            <strong style={{ fontSize: 26 }}>{titleSponsor.name}</strong>
          )}
        </>
      )}
    </div>
  );
}

function Ticker({ teams, budgetsPublic, sponsors }: { teams: PublicTeamTicker[]; budgetsPublic: boolean; sponsors: DisplaySponsor[] }) {
  return (
    <div style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: 150, background: '#191F27', borderTop: '2px solid rgba(201,164,92,.6)', display: 'flex', flexDirection: 'column', zIndex: 5 }}>
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', padding: '0 30px', overflow: 'hidden' }}>
        {teams.map((t) => (
          <div key={t.slug} style={{ flex: 1, minWidth: 0, display: 'flex', alignItems: 'center', gap: 14, padding: '0 18px', borderLeft: `6px solid ${teamColor(t.colorSlot)}`, height: 70 }}>
            <Crest url={t.logoUrl} name={t.name} slot={t.colorSlot} size={52} />
            <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
              <strong style={{ fontSize: 20, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{t.name}</strong>
              <span style={{ fontSize: 18 }}>
                {budgetsPublic && t.remaining !== null && (
                  <span className="num" style={{ fontSize: 24 }}>
                    Rs. {formatCompact(t.remaining)}{' '}
                  </span>
                )}
                <span className="muted">
                  · {t.squadCount}/{t.maxSquadSize}
                </span>
              </span>
            </div>
          </div>
        ))}
      </div>
      <div style={{ height: 40, display: 'flex', alignItems: 'center', gap: 40, padding: '0 56px', background: '#101318', fontSize: 14 }} className="muted">
        {sponsors.length > 0 && <span className="mono">SPONSORS</span>}
        {sponsors.slice(0, 10).map((sp) =>
          sp.logoUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img key={sp.name} src={sp.logoUrl} alt={sp.name} style={{ height: 26, maxWidth: 140, objectFit: 'contain' }} />
          ) : (
            <span key={sp.name} style={{ fontWeight: 600 }}>
              {sp.name}
            </span>
          ),
        )}
      </div>
    </div>
  );
}

// ---- Scenes --------------------------------------------------------------------------------

function Intro({ presenterLine, startsAt, teams }: { presenterLine: string | null; startsAt: string | null; teams: DisplayTeam[] }) {
  return (
    <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 30, textAlign: 'center', background: 'radial-gradient(ellipse at 50% 38%, rgba(16,19,24,.35), rgba(16,19,24,.92) 70%), url(/brand/court.webp) center 60%/cover no-repeat' }}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/brand/shuttle.webp" alt="" aria-hidden="true" style={{ position: 'absolute', right: 120, top: 140, height: 300, transform: 'rotate(8deg)', filter: 'drop-shadow(0 30px 40px rgba(0,0,0,.6))' }} />
      <div className="eyebrow" style={{ fontSize: 20, color: 'var(--gold-2)', letterSpacing: '.12em' }}>
        {presenterLine ?? 'Mighty Sharks Battalion presents'}
      </div>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/brand/ngbcl-logo-on-dark.svg" alt="NGBCL — Next Gen Badminton Champions League" style={{ height: 287, width: 'auto' }} />
      <h1 className="disp" style={{ fontSize: 96, lineHeight: 0.95 }}>
        Player auction
      </h1>
      <div style={{ fontSize: 40 }}>{startsAt ? `Auction starts ${formatDateTime(startsAt)}` : 'Starting soon'}</div>
      {teams.length > 0 && (
        <div style={{ display: 'flex', gap: 28, marginTop: 30, flexWrap: 'wrap', justifyContent: 'center', maxWidth: 1700 }}>
          {teams.map((t, i) => (
            <motion.div key={t.name} initial={{ opacity: 0, y: 30 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 + i * 0.25, duration: 0.6, ease: EASE }} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10, width: 150 }}>
              <Crest url={t.logoUrl} name={t.name} slot={t.colorSlot} size={140} />
              <span style={{ fontSize: 20 }}>{t.name}</span>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}

function LotScene({ lot }: { lot: PublicLot }) {
  const { first, last } = splitName(lot.fullName ?? '');
  const leader = lot.leadingTeam;
  return (
    <div style={{ position: 'absolute', inset: '110px 0 150px 0', display: 'grid', gridTemplateColumns: '45% 55%' }}>
      <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', padding: '0 0 40px 56px', gap: 18 }}>
        <div style={{ position: 'absolute', left: 56, top: 40, width: 560, height: 520, borderRadius: '50% 50% 0 0', border: '2px dashed rgba(201,164,92,.45)' }} aria-hidden="true" />
        <Portrait url={lot.photoUrl} name={lot.fullName ?? ''} style={{ position: 'absolute', left: 136, top: 70, width: 400, height: 500 }} />
        <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', gap: 10, background: 'linear-gradient(transparent, rgba(16,19,24,.85) 30%)', paddingTop: 30 }}>
          <span style={{ fontSize: 40 }}>{first}</span>
          <h2 className="disp" style={{ fontSize: 76, lineHeight: 0.95 }}>
            {last || first}
          </h2>
          <div style={{ display: 'flex', gap: 20, alignItems: 'center' }}>
            {lot.categoryName && (
              <span className="chip" style={{ ...bigChip, background: 'rgba(245,247,250,.1)' }}>
                {lot.categoryName}
              </span>
            )}
            <span className="muted" style={{ fontSize: 26 }}>
              Base price{' '}
              <span className="num" style={{ fontSize: 34, color: 'var(--text)' }}>
                {formatRs(lot.basePrice)}
              </span>
            </span>
          </div>
        </div>
      </div>
      <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', padding: '40px 56px 40px 40px', gap: 26 }}>
        <span className="eyebrow" style={{ fontSize: 20 }}>
          {lot.currentAmount === null ? 'Opening bid' : 'Current bid'}
        </span>
        <div style={{ overflow: 'hidden', height: 180 }}>
          <AnimatePresence mode="popLayout" initial={false}>
            <motion.div
              key={lot.currentAmount ?? 'base'}
              className="num"
              style={{ fontSize: 200, lineHeight: 0.85, color: 'var(--lime)' }}
              initial={{ y: '40%', opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              exit={{ y: '-30%', opacity: 0 }}
              transition={{ duration: 0.32, ease: EASE }}
            >
              Rs. {formatCompact(lot.currentAmount ?? lot.basePrice)}
            </motion.div>
          </AnimatePresence>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 28, minHeight: 200 }}>
          {leader ? (
            <>
              <motion.div key={leader.slug + (lot.currentAmount ?? '')} initial={{ boxShadow: '0 0 0 0 rgba(216,255,66,.9)' }} animate={{ boxShadow: '0 0 0 40px rgba(216,255,66,0)' }} transition={{ duration: 0.9, ease: 'easeOut' }} style={{ borderRadius: 12 }}>
                <Crest url={leader.logoUrl} name={leader.name} slot={leader.colorSlot} size={200} />
              </motion.div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                <span className="eyebrow" style={{ fontSize: 20 }}>
                  Leading
                </span>
                <span className="disp" style={{ fontSize: 52 }}>
                  {leader.name}
                </span>
              </div>
            </>
          ) : (
            <span className="disp muted" style={{ fontSize: 52 }}>
              Awaiting first bid
            </span>
          )}
          {lot.callState !== 'NONE' && (
            <motion.span key={lot.callState} initial={{ scale: 0.6, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} className="chip" style={{ ...bigChip, marginLeft: 'auto', background: 'var(--warn)', color: '#101318', fontSize: 28 }}>
              {lot.callState === 'GOING_ONCE' ? 'Going once' : 'Going twice'}
            </motion.span>
          )}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 'auto', maxWidth: 760 }}>
          {lot.lastBids.length > 0 && (
            <span className="eyebrow" style={{ fontSize: 20 }}>
              Last bids
            </span>
          )}
          {lot.lastBids.slice(0, 5).map((b, i) => (
            <div key={`${b.amount}-${i}`} style={{ display: 'flex', alignItems: 'center', gap: 18, padding: '10px 18px', borderRadius: 6, background: i === 0 ? 'rgba(216,255,66,.14)' : 'transparent', opacity: 1 - i * 0.15, fontSize: 26 }}>
              <span style={{ width: 12, height: 40, borderRadius: 2, background: teamColor(b.teamColorSlot) }} aria-hidden="true" />
              <span style={{ flex: 1 }}>{b.teamName}</span>
              <span className="num" style={{ fontSize: 34 }}>
                {formatRs(b.amount)}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function SoldScene({ lot, budgetsPublic }: { lot: PublicLot; budgetsPublic: boolean }) {
  const team = lot.winningTeam;
  const { first, last } = splitName(lot.fullName ?? '');
  const wash = team ? teamColor(team.colorSlot) : '#F05A28';
  return (
    <div style={{ position: 'absolute', inset: '110px 0 150px 0', display: 'flex', alignItems: 'center', justifyContent: 'center', background: `radial-gradient(ellipse at center, ${hexA(wash, 0.55)}, rgba(12,14,18,.95) 70%)`, overflow: 'hidden' }}>
      <motion.div
        aria-hidden="true"
        initial={{ scale: 0.4, opacity: 0 }}
        animate={{ scale: 1.15, opacity: [0, 1, 0] }}
        transition={{ duration: 4, ease: 'easeOut' }}
        style={{ position: 'absolute', width: 1100, height: 1100, borderRadius: '50%', background: 'repeating-conic-gradient(from 0deg, rgba(201,164,92,.5) 0deg 2deg, transparent 2deg 15deg)' }}
      />
      <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 20, textAlign: 'center' }}>
        <motion.h2 className="disp" initial={{ scale: 1.6, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ duration: 0.9, ease: EASE }} style={{ fontSize: 220, lineHeight: 0.85, color: 'var(--gold-2)' }}>
          Sold
        </motion.h2>
        <div style={{ display: 'flex', alignItems: 'center', gap: 40 }}>
          <Portrait url={lot.photoUrl} name={lot.fullName ?? ''} style={{ width: 180, height: 225 }} />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, alignItems: 'flex-start', textAlign: 'left' }}>
            <span style={{ fontSize: 38 }}>
              {first} <strong className="disp" style={{ fontSize: 52 }}>{last}</strong>
            </span>
            {team && (
              <span style={{ fontSize: 32 }}>
                to <strong>{team.name}</strong>
              </span>
            )}
            {lot.finalPrice !== null && (
              <span className="num" style={{ fontSize: 110, lineHeight: 1, color: 'var(--gold-2)' }}>
                {formatRs(lot.finalPrice)}
              </span>
            )}
          </div>
          {team && <Crest url={team.logoUrl} name={team.name} slot={team.colorSlot} size={220} />}
        </div>
        {team && (
          <span className="muted" style={{ fontSize: 22 }}>
            Squad {team.squadCount} / {team.maxSquadSize}
            {budgetsPublic && team.remaining !== null ? ` · ${formatRs(team.remaining)} left` : ''}
          </span>
        )}
      </div>
    </div>
  );
}

function UnsoldScene({ lot }: { lot: PublicLot }) {
  const { first, last } = splitName(lot.fullName ?? '');
  return (
    <div style={{ position: 'absolute', inset: '110px 0 150px 0', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 48, padding: '48px 64px', borderRadius: 8, background: '#222A34', border: '1px solid rgba(245,247,250,.12)', opacity: 0.85 }}>
        <Portrait url={lot.photoUrl} name={lot.fullName ?? ''} style={{ width: 200, height: 250, filter: 'grayscale(1)' }} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <span className="disp" style={{ fontSize: 96, color: 'var(--text-2)' }}>
            Unsold
          </span>
          <span style={{ fontSize: 36 }}>
            {first} <strong>{last}</strong>
          </span>
          <span className="muted" style={{ fontSize: 24 }}>
            May return in a re-auction round
          </span>
        </div>
      </div>
    </div>
  );
}

function Between({ state }: { state: PublicAuctionState }) {
  return (
    <div style={{ position: 'absolute', inset: '110px 0 150px 0', display: 'grid', gridTemplateColumns: '45% 55%', alignItems: 'center', padding: '0 56px', gap: 56 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        <span className="eyebrow" style={{ fontSize: 22, color: 'var(--gold-2)' }}>
          {state.round > 1 ? `Re-auction round ${state.round}` : 'Player auction'}
        </span>
        <h2 className="disp" style={{ fontSize: 120, lineHeight: 0.9 }}>
          Next player
          <br />
          coming up
        </h2>
      </div>
      {state.topBuys.length > 0 && <TopBuys buys={state.topBuys} title="Top buys so far" />}
    </div>
  );
}

function Paused({ state }: { state: PublicAuctionState }) {
  return (
    <div style={{ position: 'absolute', inset: '110px 0 150px 0', display: 'grid', gridTemplateColumns: '40% 60%', alignItems: 'center', padding: '0 56px', gap: 56 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        <span className="chip" style={{ ...bigChip, background: 'var(--warn)', color: '#101318', alignSelf: 'flex-start' }}>
          Paused
        </span>
        <h2 className="disp" style={{ fontSize: 120, lineHeight: 0.9 }}>
          Short break
        </h2>
        <span className="muted" style={{ fontSize: 30 }}>
          Bidding resumes shortly.
        </span>
      </div>
      <div style={{ borderRadius: 8, background: '#191F27', border: '1px solid rgba(245,247,250,.12)', overflow: 'hidden' }}>
        <div style={{ ...standingsRow, padding: '18px 28px', fontSize: 18, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--text-2)', fontWeight: 600, borderBottom: '1px solid rgba(245,247,250,.12)' }}>
          <span>Team</span>
          <span style={{ textAlign: 'right' }}>{state.budgetsPublic ? 'Spent' : ''}</span>
          <span style={{ textAlign: 'right' }}>{state.budgetsPublic ? 'Left' : ''}</span>
          <span style={{ textAlign: 'right' }}>Squad</span>
        </div>
        {state.teams.map((t) => (
          <div key={t.slug} style={{ ...standingsRow, alignItems: 'center', padding: '14px 28px', borderBottom: '1px solid rgba(245,247,250,.06)', fontSize: 28, borderLeft: `6px solid ${teamColor(t.colorSlot)}` }}>
            <span style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
              <Crest url={t.logoUrl} name={t.name} slot={t.colorSlot} size={44} />
              {t.name}
            </span>
            <span className="num" style={{ textAlign: 'right', color: 'var(--gold-2)' }}>
              {t.spent !== null ? formatCompact(t.spent) : ''}
            </span>
            <span className="num" style={{ textAlign: 'right' }}>
              {t.remaining !== null ? formatCompact(t.remaining) : ''}
            </span>
            <span className="num" style={{ textAlign: 'right' }}>
              {t.squadCount}/{t.maxSquadSize}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function Ended({ state }: { state: PublicAuctionState }) {
  return (
    <div style={{ position: 'absolute', inset: '110px 0 40px 0', display: 'grid', gridTemplateColumns: '42% 58%', gap: 48, padding: '30px 56px' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        <h2 className="disp" style={{ fontSize: 76, lineHeight: 0.95 }}>
          Auction complete
        </h2>
        <TopBuys buys={state.topBuys} title={state.topBuys.some((b) => b.price !== null) ? 'Top 5 buys' : 'Latest buys'} />
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 20, alignContent: 'start' }}>
        {state.teams.map((t) => (
          <div key={t.slug} style={{ padding: 20, borderRadius: 8, background: '#191F27', borderTop: `6px solid ${teamColor(t.colorSlot)}`, display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
              <Crest url={t.logoUrl} name={t.name} slot={t.colorSlot} size={64} />
              <strong style={{ fontSize: 26 }}>{t.name}</strong>
            </div>
            {state.budgetsPublic && t.spent !== null && (
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 22 }}>
                <span className="muted">Spent</span>
                <span className="num" style={{ fontSize: 30, color: 'var(--gold-2)' }}>
                  {formatRs(t.spent)}
                </span>
              </div>
            )}
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {Array.from({ length: t.maxSquadSize }, (_, i) => (
                <span key={i} style={{ width: 34, height: 42, borderRadius: 4, background: i < t.squadCount ? 'var(--gold)' : 'rgba(245,247,250,.1)', border: i < t.squadCount ? 0 : '1px dashed rgba(245,247,250,.3)' }} />
              ))}
            </div>
            <span className="muted" style={{ fontSize: 18 }}>
              Squad {t.squadCount} / {t.maxSquadSize}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function TopBuys({ buys, title }: { buys: PublicAuctionState['topBuys']; title: string }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <span className="eyebrow" style={{ fontSize: 20, color: 'var(--gold-2)' }}>
        {title}
      </span>
      {buys.map((b, i) => {
        const { first, last } = splitName(b.fullName ?? '');
        return (
          <div key={`${b.slug}-${i}`} style={{ display: 'flex', alignItems: 'center', gap: 20, padding: '14px 20px', borderRadius: 8, background: '#191F27', border: `1px solid ${i === 0 ? '#C9A45C' : 'rgba(245,247,250,.12)'}` }}>
            <span className="num" style={{ fontSize: 44, width: 44, color: 'var(--gold-2)' }}>
              {i + 1}
            </span>
            <Portrait url={b.photoUrl} name={b.fullName ?? ''} style={{ width: 56, height: 70 }} />
            <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0 }}>
              <span style={{ fontSize: 26 }}>
                {first} <strong>{last}</strong>
              </span>
              <span className="muted" style={{ fontSize: 20 }}>
                to {b.teamName}
              </span>
            </div>
            {b.price !== null && (
              <span className="num" style={{ fontSize: 44, color: 'var(--gold-2)' }}>
                {formatRs(b.price)}
              </span>
            )}
          </div>
        );
      })}
    </div>
  );
}

// ---- Bits ---------------------------------------------------------------------------------

const bigChip: CSSProperties = { gap: 10, fontSize: 18, letterSpacing: '.1em', fontWeight: 700, padding: '10px 16px', borderRadius: 3 };
const standingsRow: CSSProperties = { display: 'grid', gridTemplateColumns: '1fr 220px 220px 160px' };

function Crest({ url, name, slot, size }: { url: string | null; name: string; slot: number | null; size: number }) {
  return (
    <span style={{ width: size, height: size, borderRadius: 12, background: '#1E252E', boxShadow: `0 0 0 ${size > 100 ? 4 : 2}px ${teamColor(slot)}`, display: 'grid', placeItems: 'center', overflow: 'hidden', flexShrink: 0 }}>
      {url ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={url} alt={`${name} logo`} style={{ width: '82%', height: '82%', objectFit: 'contain' }} />
      ) : (
        <span className="disp" style={{ fontSize: size * 0.32 }}>
          {initialsOf(name)}
        </span>
      )}
    </span>
  );
}

function Portrait({ url, name, style }: { url: string | null; name: string; style: CSSProperties }): ReactNode {
  return url ? (
    // eslint-disable-next-line @next/next/no-img-element
    <img src={url} alt={name} style={{ objectFit: 'cover', borderRadius: 8, background: '#191F27', ...style }} />
  ) : (
    <span className="ph-person" role="img" aria-label={`${name} photo placeholder`} style={{ display: 'block', borderRadius: 8, background: '#191F27', ...style }} />
  );
}

function initialsOf(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]!.toUpperCase())
    .join('');
}

function hexA(hex: string, a: number) {
  if (!hex.startsWith('#') || hex.length !== 7) return `rgba(240,90,40,${a})`;
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}
