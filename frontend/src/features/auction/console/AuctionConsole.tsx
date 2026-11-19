'use client';

import clsx from 'clsx';
import { AnimatePresence, motion } from 'motion/react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useMemo, useRef, useState } from 'react';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/data/States';
import { ConnectionPill } from '@/design-system/live/Live';
import { shake } from '@/design-system/motion/presets';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input, Select, Textarea } from '@/design-system/primitives/Inputs';
import { ConfirmDialog, Modal } from '@/design-system/primitives/Overlay';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { Segmented } from '@/design-system/primitives/Tabs';
import { Money } from '@/design-system/primitives/Text';
import { useToast } from '@/design-system/primitives/Toast';
import { CrestTile } from '@/design-system/sport/CrestTile';
import { LotStatusBar, type LotState as BarState } from '@/design-system/sport/Scores';
import { teamColor } from '@/design-system/tokens';
import {
  BLOCKED_LABEL,
  newIdempotencyKey,
  useAuctionCommands,
  useAuctionConsole,
  usePlaceBid,
  type ConsoleSnapshot,
  type Lot,
  type QueueItem,
  type TeamRail,
} from '@/lib/api/auction';
import { ApiError, errorMessage } from '@/lib/api/errors';
import { useAuth } from '@/lib/auth/session';
import { formatAuditTime } from '@/lib/format/dates';
import { formatAmount, formatLkr } from '@/lib/format/money';
import { splitName } from '@/lib/format/names';
import { useConnectionState } from '@/lib/realtime/useTopic';
import { useAuctionLive } from '../useAuctionLive';
import c from './Console.module.css';

type Feedback =
  | { kind: 'sending'; teamName: string; amount: number }
  | { kind: 'ok'; teamName: string; amount: number }
  | { kind: 'err'; teamName: string; amount: number; message: string; retryable: boolean };

interface Rejected {
  key: string;
  teamName: string;
  amount: number;
  reason: string;
  label: string;
}

/** Short chip text for the ladder; the full server message is the tooltip and the feedback box. */
const REJECT_LABEL: Record<string, string> = {
  BID_TOO_LOW: 'Below minimum',
  EXCEEDS_BUDGET: 'Over budget',
  RESERVE_REQUIRED: 'Reserve',
  SQUAD_FULL: 'Squad full',
  ALREADY_LEADING: 'Already leading',
  AUCTION_PAUSED: 'Paused',
  LOT_CHANGED: 'Lot changed',
};

/** Signed-in gate for the console surface (no admin sidebar — brief §6). */
export function AuctionConsoleGate() {
  const { status, can } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  useEffect(() => {
    if (status === 'anonymous') router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [status, router, pathname]);
  if (status !== 'authenticated') {
    return (
      <div style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 16 }} aria-busy="true">
        <Skeleton height={56} />
        <Skeleton height={480} />
      </div>
    );
  }
  if (!can('AUCTION_CONTROL')) {
    return (
      <div style={{ padding: 48 }}>
        <EmptyState title="You can’t run the auction" description="The console needs the auctioneer permission. Ask a Tournament Admin to grant it." action={{ label: 'Back to admin', href: '/admin' }} />
      </div>
    );
  }
  return <AuctionConsole />;
}

function AuctionConsole() {
  const { data, isLoading, error, refetch } = useAuctionConsole();
  const conn = useConnectionState();
  useAuctionLive({ staff: true });

  if (isLoading) {
    return (
      <div className={c.root} aria-busy="true">
        <div className={c.header}>
          <Skeleton height={36} width={200} />
        </div>
        <div className={c.cols}>
          <Skeleton height={600} />
          <Skeleton height={600} />
          <Skeleton height={600} />
        </div>
      </div>
    );
  }
  if (error || !data) {
    return (
      <div style={{ padding: 48 }}>
        <ErrorState title="Couldn’t load the auction" message={errorMessage(error)} onRetry={() => void refetch()} />
      </div>
    );
  }
  return <ConsoleBody snap={data} conn={conn} />;
}

function ConsoleBody({ snap, conn }: { snap: ConsoleSnapshot; conn: ReturnType<typeof useConnectionState> }) {
  const cmd = useAuctionCommands();
  const placeBid = usePlaceBid();
  const toast = useToast();
  const lot = snap.currentLot;
  const live = snap.status === 'LIVE';
  const paused = snap.status === 'PAUSED';

  // Restored-session banner: shown when the console loads (or reconnects) with a lot already open.
  const [restored, setRestored] = useState(() => Boolean(lot && lot.bids.length));
  const [prevConn, setPrevConn] = useState(conn);
  if (prevConn !== conn) {
    setPrevConn(conn);
    if (prevConn === 'reconnecting' && conn === 'live' && lot) setRestored(true);
  }

  // Bid entry state — reset whenever the lot or the lead changes.
  const [selected, setSelected] = useState<string | null>(null);
  const [amountFor, setAmountFor] = useState<{ key: string; value: string } | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const [rejected, setRejected] = useState<{ lotId: string; items: Rejected[] }>({ lotId: '', items: [] });
  const retryKey = useRef<{ sig: string; key: string } | null>(null);
  const leadBid = lot?.bids.find((b) => b.status === 'ACCEPTED') ?? null;
  const leadKey = `${lot?.id ?? ''}:${leadBid?.id ?? 'none'}`;
  const amount = amountFor && amountFor.key === leadKey ? amountFor.value : lot ? String(lot.nextMinimum) : '';
  const setAmount = (v: string) => setAmountFor({ key: leadKey, value: v });
  const lotRejected = rejected.lotId === lot?.id ? rejected.items : [];

  const [lastLot, setLastLot] = useState(lot?.id ?? null);
  if (lastLot !== (lot?.id ?? null)) {
    setLastLot(lot?.id ?? null);
    setFeedback(null);
    setSelected(null);
  }

  const teams = snap.teams;
  const team = teams.find((t) => t.teamId === selected) ?? null;
  const leader = lot?.leadingTeamId ? (teams.find((t) => t.teamId === lot.leadingTeamId) ?? null) : null;

  const [sellOpen, setSellOpen] = useState(false);
  const [withdraw, setWithdraw] = useState<{ lotId: string; name: string } | null>(null);
  const [withdrawReason, setWithdrawReason] = useState('');
  const [endOpen, setEndOpen] = useState(false);
  const [tab, setTab] = useState<'queue' | 'reauction'>('queue');
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [picked, setPicked] = useState<Set<string>>(new Set());

  const fail = (e: unknown) => toast.error(errorMessage(e));

  const amountValue = Number(amount.replace(/[^\d]/g, ''));
  const canPlace = Boolean(live && lot && team && team.canBid && amountValue > 0 && !placeBid.isPending);

  const submitBid = () => {
    if (!lot || !team || !canPlace) return;
    const sig = `${lot.id}|${team.teamId}|${amountValue}`;
    // Same bid retried after a timeout keeps its key, so it can never be recorded twice.
    const key = retryKey.current?.sig === sig ? retryKey.current.key : newIdempotencyKey();
    retryKey.current = { sig, key };
    setFeedback({ kind: 'sending', teamName: team.name, amount: amountValue });
    placeBid.mutate(
      { lotId: lot.id, teamId: team.teamId, amount: amountValue, idempotencyKey: key },
      {
        onSuccess: () => {
          retryKey.current = null;
          setFeedback({ kind: 'ok', teamName: team.name, amount: amountValue });
          setSelected(null);
        },
        onError: (e) => {
          const network = e instanceof ApiError && (e.isNetwork || e.status >= 500);
          if (!network) retryKey.current = null;
          const message = network ? 'No answer from the server. Retry sends the same bid safely.' : errorMessage(e);
          setFeedback({ kind: 'err', teamName: team.name, amount: amountValue, message, retryable: network });
          if (!network) {
            setRejected((r) => ({
              lotId: lot.id,
              items: [{ key, teamName: team.name, amount: amountValue, reason: message, label: (e instanceof ApiError && e.code && REJECT_LABEL[e.code]) || 'Rejected' }, ...(r.lotId === lot.id ? r.items : [])].slice(0, 3),
            }));
          }
        },
      },
    );
  };

  const bump = (k: number) => {
    if (!lot) return;
    const inc = snap.settings.minIncrement;
    const from = lot.currentAmount ?? lot.basePrice - inc;
    setAmount(String(from + k * inc));
  };

  // Keyboard: 1–9 pick a team, Enter places the bid (export console hint).
  const keyState = useRef({ teams, submitBid, live });
  useEffect(() => {
    keyState.current = { teams, submitBid, live };
  });
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      const typing = target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT' || target.isContentEditable);
      if (typing || e.metaKey || e.ctrlKey || e.altKey || document.querySelector('[role="dialog"]')) return;
      const s = keyState.current;
      if (/^[1-9]$/.test(e.key)) {
        const t = s.teams[Number(e.key) - 1];
        if (t && t.canBid && s.live) {
          setSelected(t.teamId);
          e.preventDefault();
        }
      } else if (e.key === 'Enter') {
        s.submitBid();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const queue = snap.queue;
  const waiting = queue.filter((q) => q.state === 'QUEUED' && q.round === snap.round);
  const unsold = queue.filter((q) => q.state === 'UNSOLD');
  const withdrawn = queue.filter((q) => q.state === 'WITHDRAWN');
  const categories = useMemo(() => [...new Set(queue.map((q) => q.categoryName).filter(Boolean))] as string[], [queue]);
  const filter = (list: QueueItem[]) =>
    list.filter((q) => (!search || q.fullName.toLowerCase().includes(search.toLowerCase())) && (!category || q.categoryName === category));

  const barState: BarState = !lot ? 'READY' : lot.callState === 'GOING_ONCE' ? 'GOING_ONCE' : lot.callState === 'GOING_TWICE' ? 'GOING_TWICE' : 'OPEN';
  const roundDone = live && !lot && waiting.length === 0;

  return (
    <div className={c.root}>
      <header className={c.header}>
        <Link href="/admin/auction/setup" aria-label="Auction setup">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/brand/ngbcl-wordmark-on-dark.svg" alt="NGBCL — Next Gen Badminton Champions League" />
        </Link>
        <StatusChip tone={live ? 'live' : paused ? 'pending' : snap.status === 'COMPLETED' ? 'gold' : 'neutral'} dot={live}>
          {live ? 'Live auction' : paused ? 'Paused' : snap.status === 'COMPLETED' ? 'Auction complete' : 'Not started'}
        </StatusChip>
        <span className="mono muted">
          Lot {snap.lotsOpened || '—'} / {snap.counts.total} · round {snap.round}
        </span>
        <div className={c.controls}>
          {snap.status === 'NOT_STARTED' && (
            <Button variant="volt" disabled={snap.startBlockers.length > 0} loading={cmd.start.isPending} onClick={() => cmd.start.mutate(undefined, { onError: fail })} title={snap.startBlockers.join(' ')}>
              Start
            </Button>
          )}
          {live && (
            <Button variant="out" loading={cmd.pause.isPending} onClick={() => cmd.pause.mutate(undefined, { onError: fail })}>
              Pause
            </Button>
          )}
          {paused && (
            <Button variant="volt" loading={cmd.resume.isPending} onClick={() => cmd.resume.mutate(undefined, { onError: fail })}>
              Resume
            </Button>
          )}
          <Button variant="out" disabled={!lot || !leadBid || cmd.undo.isPending} onClick={() => lot && cmd.undo.mutate({ lotId: lot.id }, { onError: fail })}>
            Undo last bid
          </Button>
          <Button variant="out" disabled={!live || Boolean(lot) || waiting.length === 0} loading={cmd.open.isPending} onClick={() => cmd.open.mutate({}, { onError: fail })}>
            Next player
          </Button>
          {(live || paused) && (
            <Button variant="ghost" disabled={Boolean(lot)} onClick={() => setEndOpen(true)}>
              End auction
            </Button>
          )}
        </div>
        <div className={c.right}>
          <ConnectionPill state={conn} />
          <a href="/display/auction" target="_blank" rel="noreferrer" className="link" style={{ fontSize: 13, fontWeight: 600 }}>
            Display ↗
          </a>
          <Link href="/admin" className="link" style={{ fontSize: 13, fontWeight: 600 }}>
            Exit console
          </Link>
        </div>
      </header>

      {paused && (
        <div role="status" className={clsx(c.banner, c.paused)}>
          <span>Auction paused · the public display shows “Short break”. Bidding is disabled.</span>
          <Button variant="out" style={{ marginLeft: 'auto', background: 'var(--bg-2)', color: 'var(--text)' }} onClick={() => cmd.resume.mutate(undefined, { onError: fail })}>
            Resume auction
          </Button>
        </div>
      )}
      {restored && lot && (
        <div role="status" className={clsx(c.banner, c.restored)}>
          <strong>Restored session{snap.lastActionAt ? ` — last action ${formatAuditTime(snap.lastActionAt)}` : ''}</strong>
          <span>
            Current lot and {lot.bids.filter((b) => b.status === 'ACCEPTED').length} bids reloaded from the server. Check the ladder before continuing.
          </span>
          <Button variant="out" style={{ marginLeft: 'auto' }} onClick={() => setRestored(false)}>
            Continue
          </Button>
        </div>
      )}

      <div className={clsx(c.cols, paused && c.dim)}>
        {/* LEFT: queue */}
        <section className={clsx(c.panel, c.queue)} aria-label="Lot queue">
          <div className={c.queueTools}>
            <Segmented
              label="Queue view"
              value={tab}
              onChange={setTab}
              options={[
                { value: 'queue', label: `Queue ${waiting.length}` },
                { value: 'reauction', label: `Re-auction ${unsold.length}` },
              ]}
            />
            <Input type="search" placeholder="Search player" aria-label="Search queue" value={search} onChange={(e) => setSearch(e.target.value)} />
            {categories.length > 1 && (
              <Select aria-label="Category" value={category} onChange={(e) => setCategory(e.target.value)}>
                <option value="">All categories</option>
                {categories.map((cat) => (
                  <option key={cat} value={cat}>
                    {cat}
                  </option>
                ))}
              </Select>
            )}
          </div>
          <div className={c.queueList}>
            {tab === 'queue' ? (
              <>
                {lot?.player && (
                  <div className={clsx(c.qItem, c.onBlock)}>
                    <span className="mono muted" style={{ width: 30 }}>
                      #{lot.lotNumber}
                    </span>
                    <Thumb url={lot.player.photoUrl} />
                    <span className={c.qName}>
                      <span>{lot.player.fullName}</span>
                      <span className="muted" style={{ fontSize: 12 }}>
                        {lot.player.categoryName ?? 'No category'} · base {formatAmount(lot.basePrice)}
                      </span>
                    </span>
                    <span className="chip c-live">On block</span>
                  </div>
                )}
                {filter(waiting).map((q, i) => (
                  <div key={q.lotId} className={c.qItem}>
                    <span className="mono muted" style={{ width: 30 }}>
                      {i + 1}
                    </span>
                    <Thumb url={q.photoUrl} />
                    <span className={c.qName}>
                      <span>{q.fullName}</span>
                      <span className="muted" style={{ fontSize: 12 }}>
                        {q.categoryName ?? 'No category'} · base {formatAmount(q.basePrice)}
                      </span>
                    </span>
                    {live && !lot ? (
                      <Button size="sm" variant="out" onClick={() => cmd.open.mutate({ lotId: q.lotId }, { onError: fail })} aria-label={`Put ${q.fullName} up`}>
                        Put up
                      </Button>
                    ) : (
                      <Button size="sm" variant="ghost" onClick={() => setWithdraw({ lotId: q.lotId, name: q.fullName })} aria-label={`Withdraw ${q.fullName}`} disabled={snap.status === 'COMPLETED'}>
                        Withdraw
                      </Button>
                    )}
                  </div>
                ))}
                {waiting.length === 0 && !lot && <p className="muted" style={{ padding: 14, margin: 0 }}>No players waiting in round {snap.round}.</p>}
                {withdrawn.length > 0 && (
                  <>
                    <p className="eyebrow" style={{ padding: '14px 14px 6px', margin: 0 }}>
                      Withdrawn
                    </p>
                    {filter(withdrawn).map((q) => (
                      <div key={q.lotId} className={c.qItem} style={{ opacity: 0.7 }}>
                        <Thumb url={q.photoUrl} />
                        <span className={c.qName}>
                          <span>{q.fullName}</span>
                        </span>
                        <Button size="sm" variant="ghost" disabled={snap.status === 'COMPLETED'} onClick={() => cmd.restore.mutate({ lotId: q.lotId }, { onError: fail })}>
                          Restore
                        </Button>
                      </div>
                    ))}
                  </>
                )}
              </>
            ) : (
              <>
                {filter(unsold).map((q) => (
                  <label key={q.lotId} className={c.check}>
                    <input
                      type="checkbox"
                      checked={picked.has(q.lotId)}
                      onChange={(e) =>
                        setPicked((p) => {
                          const n = new Set(p);
                          if (e.target.checked) n.add(q.lotId);
                          else n.delete(q.lotId);
                          return n;
                        })
                      }
                    />
                    <Thumb url={q.photoUrl} />
                    <span className={c.qName}>
                      <span>{q.fullName}</span>
                      <span className="muted" style={{ fontSize: 12 }}>
                        {q.categoryName ?? 'No category'} · base {formatAmount(q.basePrice)}
                        {q.lotNumber ? ` · was lot ${q.lotNumber}` : ''}
                      </span>
                    </span>
                  </label>
                ))}
                {unsold.length === 0 && <p className="muted" style={{ padding: 14, margin: 0 }}>No unsold players.</p>}
              </>
            )}
          </div>
          <p className={clsx('muted', c.queueFoot)}>
            {tab === 'queue'
              ? 'Only approved players appear. Reorder on the setup page.'
              : snap.settings.allowReauction
                ? 'Tick players for the next round, or start it with all unsold players.'
                : 'Re-auction is turned off in the auction settings.'}
          </p>
          {tab === 'reauction' && (
            <div style={{ padding: '0 14px 14px', display: 'flex', flexDirection: 'column', gap: 8 }}>
              <Button
                variant="volt"
                disabled={!snap.canStartNextRound}
                loading={cmd.nextRound.isPending}
                onClick={() =>
                  cmd.nextRound.mutate(
                    { lotIds: [...picked].filter((id) => unsold.some((u) => u.lotId === id)) },
                    {
                      onSuccess: (s) => {
                        setPicked(new Set());
                        setTab('queue');
                        toast.success(`Round ${s.round} started`);
                      },
                      onError: fail,
                    },
                  )
                }
              >
                {picked.size > 0 ? `Start round ${snap.round + 1} with ${picked.size} selected` : `Start round ${snap.round + 1} with all unsold`}
              </Button>
              {!snap.canStartNextRound && snap.settings.allowReauction && unsold.length > 0 && (
                <span className="t-caption">Available once this round’s queue is finished and no lot is open.</span>
              )}
            </div>
          )}
        </section>

        {/* CENTRE: current lot */}
        <section className={c.centre} aria-label="Current lot">
          {lot && lot.player ? (
            <>
              <LotCard lot={lot} leader={leader} />
              <div style={{ display: 'flex', gap: 8 }}>
                <LotStatusBarWrap state={barState} />
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                <Button
                  variant={lot.callState === 'GOING_ONCE' ? 'volt' : 'out'}
                  disabled={!live || !leadBid}
                  onClick={() => cmd.call.mutate({ lotId: lot.id, state: lot.callState === 'GOING_ONCE' ? 'NONE' : 'GOING_ONCE' }, { onError: fail })}
                >
                  Going once
                </Button>
                <Button
                  variant={lot.callState === 'GOING_TWICE' ? 'volt' : 'out'}
                  disabled={!live || !leadBid}
                  onClick={() => cmd.call.mutate({ lotId: lot.id, state: lot.callState === 'GOING_TWICE' ? 'NONE' : 'GOING_TWICE' }, { onError: fail })}
                >
                  Going twice
                </Button>
              </div>
              <div className={c.split}>
                <div className={clsx(c.panel, c.entry)} aria-label="Bid entry">
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span className="eyebrow">Bid for</span>
                    <span className="muted" style={{ fontSize: 12 }}>
                      <span className="kbd">1</span>–<span className="kbd">9</span> team · <span className="kbd">Enter</span> place
                    </span>
                  </div>
                  <div className={clsx(c.bidFor, !team && c.bidForEmpty)}>
                    {team ? (
                      <>
                        <CrestTile logoUrl={team.logoUrl} name={team.name} colorSlot={team.colorSlot} size="s" />
                        <strong>{team.name}</strong>
                        <span className="muted" style={{ marginLeft: 'auto', fontSize: 12 }}>
                          Max bid {formatLkr(team.maxBid)}
                        </span>
                      </>
                    ) : (
                      <span>Tap a team on the right</span>
                    )}
                  </div>
                  <label htmlFor="bid-amount" className="eyebrow">
                    Amount · next valid minimum {formatLkr(lot.nextMinimum)}
                  </label>
                  <Input
                    id="bid-amount"
                    className={c.amount}
                    inputMode="numeric"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value.replace(/[^\d,]/g, ''))}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        submitBid();
                      }
                    }}
                  />
                  <div style={{ display: 'flex', gap: 8 }}>
                    {[1, 2, 5].map((k) => (
                      <Button key={k} variant="out" style={{ flex: 1 }} onClick={() => bump(k)}>
                        +{k} inc
                      </Button>
                    ))}
                  </div>
                  <Button variant="volt" className={c.xl} disabled={!canPlace} loading={placeBid.isPending} onClick={submitBid}>
                    Place bid
                  </Button>
                  <BidFeedback fb={feedback} onRetry={submitBid} />
                </div>
                <Ladder lot={lot} rejected={lotRejected} pending={feedback?.kind === 'sending' ? feedback : null} />
              </div>
              <div className={clsx(c.panel, c.close)} aria-label="Close lot">
                <Button variant="gold" className={c.xl} disabled={!leadBid || (!live && !paused)} onClick={() => setSellOpen(true)}>
                  Sold
                </Button>
                <Button
                  variant="out"
                  className={c.xl}
                  disabled={Boolean(leadBid) || (!live && !paused)}
                  title={leadBid ? 'Undo the bids first, or sell the player' : undefined}
                  loading={cmd.unsold.isPending}
                  onClick={() => cmd.unsold.mutate({ lotId: lot.id }, { onError: fail })}
                >
                  Unsold
                </Button>
                <Button variant="out" className={c.xl} onClick={() => setWithdraw({ lotId: lot.id, name: lot.player!.fullName })}>
                  Withdrawn
                </Button>
              </div>
            </>
          ) : (
            <IdlePanel snap={snap} waiting={waiting.length} roundDone={roundDone} onNext={() => cmd.open.mutate({}, { onError: fail })} onReauction={() => setTab('reauction')} opening={cmd.open.isPending} />
          )}
        </section>

        {/* RIGHT: team rail */}
        <section className={clsx(c.panel, c.rail)} aria-label="Teams">
          <div style={{ padding: 14, display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--line)', gap: 8 }}>
            <span className="eyebrow">Teams</span>
            <span className="muted" style={{ fontSize: 12 }}>
              Purse {formatLkr(snap.settings.initialBudget)} · squad {snap.settings.maxSquadSize}
            </span>
          </div>
          <div className={c.railList}>
            {teams.map((t, i) => (
              <TeamButton key={t.teamId} t={t} index={i} selected={t.teamId === selected} leading={t.teamId === lot?.leadingTeamId} disabled={!live || !lot || !t.canBid} onPick={() => setSelected(t.teamId)} />
            ))}
            {teams.length === 0 && <p className="muted" style={{ padding: 14, margin: 0 }}>Team budgets appear when the auction starts.</p>}
          </div>
          <p className="muted" style={{ margin: 0, padding: '12px 14px', fontSize: 12 }}>
            Greyed teams can’t bid now; the reason is shown. Squad slots = configured maximum.
          </p>
        </section>
      </div>

      {/* SOLD confirm sheet */}
      <Modal
        open={sellOpen && Boolean(lot && leader && leadBid)}
        onClose={() => setSellOpen(false)}
        width={560}
        title={
          lot && leader && leadBid ? (
            <>
              Sell {lot.player?.fullName} to {leader.name} for <span className="num gold">{formatLkr(leadBid.amount)}</span>?
            </>
          ) : (
            'Confirm sale'
          )
        }
        footer={
          <>
            <Button variant="out" onClick={() => setSellOpen(false)} disabled={cmd.sell.isPending}>
              Cancel
            </Button>
            <Button
              variant="gold"
              size="lg"
              loading={cmd.sell.isPending}
              onClick={() =>
                lot &&
                leadBid &&
                cmd.sell.mutate(
                  { lotId: lot.id, bidId: leadBid.id },
                  {
                    onSuccess: () => {
                      setSellOpen(false);
                      toast.success(`${lot.player?.fullName} sold to ${leader?.name}`);
                    },
                    onError: (e) => {
                      setSellOpen(false);
                      fail(e);
                    },
                  },
                )
              }
            >
              Confirm sale
            </Button>
          </>
        }
      >
        {leader && leadBid && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <span className="chip c-sold" style={{ alignSelf: 'flex-start' }}>
              Confirm sale
            </span>
            <div className={c.sheetGrid}>
              <div className={c.sheetBox}>
                <span className="eyebrow">Remaining budget becomes</span>
                <span className="num" style={{ fontSize: 28 }}>
                  {formatLkr(leader.remaining - leadBid.amount)}
                </span>
              </div>
              <div className={c.sheetBox}>
                <span className="eyebrow">Squad after sale</span>
                <span className="num" style={{ fontSize: 28 }}>
                  {leader.squadCount + 1} / {leader.maxSquadSize}
                </span>
              </div>
            </div>
            <span className="muted" style={{ fontSize: 13 }}>
              Sale, budget deduction and roster update happen together. Reversing it needs an audited correction.
            </span>
          </div>
        )}
      </Modal>

      <Modal
        open={withdraw !== null}
        onClose={() => setWithdraw(null)}
        title={`Withdraw ${withdraw?.name ?? 'player'} from the auction?`}
        footer={
          <>
            <Button variant="out" onClick={() => setWithdraw(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              disabled={withdrawReason.trim().length < 3}
              loading={cmd.withdraw.isPending}
              onClick={() =>
                withdraw &&
                cmd.withdraw.mutate(
                  { lotId: withdraw.lotId, reason: withdrawReason.trim() },
                  {
                    onSuccess: () => {
                      setWithdraw(null);
                      setWithdrawReason('');
                    },
                    onError: fail,
                  },
                )
              }
            >
              Withdraw player
            </Button>
          </>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <p className="muted" style={{ margin: 0 }}>
            The player leaves the queue. Bids on an open lot stay in the history. You can restore the player later.
          </p>
          <Field label="Reason" required>
            {({ id }) => <Textarea id={id} rows={3} maxLength={300} value={withdrawReason} onChange={(e) => setWithdrawReason(e.target.value)} />}
          </Field>
        </div>
      </Modal>

      <ConfirmDialog
        open={endOpen}
        onClose={() => setEndOpen(false)}
        tone="danger"
        typeToConfirm="END"
        title="End the auction?"
        confirmLabel="End auction"
        consequences={
          <p style={{ margin: 0 }}>
            {waiting.length > 0 ? `${waiting.length} players still waiting will be recorded as unsold. ` : ''}
            Squads and budgets become final; later fixes need audited corrections. The display switches to the summary.
          </p>
        }
        onConfirm={() =>
          new Promise<void>((resolve) =>
            cmd.end.mutate(
              {},
              {
                onSuccess: () => resolve(),
                onError: (e) => {
                  fail(e);
                  resolve();
                },
              },
            ),
          )
        }
      />
    </div>
  );
}

function Thumb({ url }: { url: string | null }) {
  // eslint-disable-next-line @next/next/no-img-element
  return url ? <img src={url} alt="" className={c.thumb} /> : <span className={clsx('ph-person', c.thumb)} aria-hidden="true" />;
}

function LotStatusBarWrap({ state }: { state: BarState }) {
  return (
    <div style={{ flex: 1, transform: 'skewX(-10deg)' }}>
      <LotStatusBar state={state} />
    </div>
  );
}

function LotCard({ lot, leader }: { lot: Lot; leader: TeamRail | null }) {
  const p = lot.player!;
  const { first, last } = splitName(p.fullName);
  return (
    <div className={clsx(c.panel, c.lotCard)}>
      <div>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        {p.photoUrl ? <img src={p.photoUrl} alt={p.fullName} className={c.photo} /> : <span className={clsx('ph-person', c.photo)} role="img" aria-label="Photo placeholder" />}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14, minWidth: 0 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <span className="eyebrow">
            Lot {lot.lotNumber}
            {lot.round > 1 ? ` · re-auction round ${lot.round}` : ''}
          </span>
          <span style={{ fontSize: 26 }}>
            {first} <strong className="disp" style={{ fontSize: 30 }}>{last}</strong>
          </span>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            {p.categoryName && <span className="chip c-neutral">{p.categoryName}</span>}
            <span className="muted">
              Base price <Money value={lot.basePrice} />
              {lot.basePriceOverridden && ' (custom)'}
            </span>
          </div>
        </div>
        <div className={c.highest} aria-live="polite">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <span className="eyebrow">Highest bid</span>
            <AnimatePresence mode="popLayout" initial={false}>
              <motion.span
                key={lot.currentAmount ?? 'none'}
                className={clsx('num', c.highestNum)}
                initial={{ y: '40%', opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.32, ease: [0.16, 1, 0.3, 1] }}
              >
                {lot.currentAmount === null ? (
                  'No bids'
                ) : (
                  <>
                    <span className="eyebrow" style={{ fontSize: 18, marginRight: 8, color: 'var(--lime)' }}>
                      LKR
                    </span>
                    {formatAmount(lot.currentAmount)}
                  </>
                )}
              </motion.span>
            </AnimatePresence>
          </div>
          <div style={{ marginLeft: 'auto', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
            {leader ? (
              <>
                <CrestTile logoUrl={leader.logoUrl} name={leader.name} colorSlot={leader.colorSlot} size="m" />
                <strong style={{ fontSize: 13 }}>{leader.name}</strong>
              </>
            ) : (
              <span className="muted" style={{ fontSize: 13 }}>
                Opening at {formatLkr(lot.basePrice)}
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function BidFeedback({ fb, onRetry }: { fb: Feedback | null; onRetry: () => void }) {
  if (!fb) return null;
  if (fb.kind === 'sending')
    return (
      <div role="status" className={clsx(c.fb, c.fbSending)}>
        <span className="chip c-neutral">Sending…</span>Waiting for the server
      </div>
    );
  if (fb.kind === 'ok')
    return (
      <motion.div role="status" className={clsx(c.fb, c.fbOk)} initial={{ opacity: 0.4 }} animate={{ opacity: 1 }}>
        <span className="chip c-live">Accepted</span>
        {fb.teamName} leads at {formatLkr(fb.amount)}
      </motion.div>
    );
  return (
    <motion.div role="alert" className={clsx(c.fb, c.fbErr)} animate={shake}>
      <span className="chip c-err">Rejected</span>
      <span>{fb.message}</span>
      {fb.retryable && (
        <Button size="sm" variant="out" onClick={onRetry} style={{ marginLeft: 'auto' }}>
          Retry
        </Button>
      )}
    </motion.div>
  );
}

function Ladder({ lot, rejected, pending }: { lot: Lot; rejected: Rejected[]; pending: { teamName: string; amount: number } | null }) {
  const leadId = lot.bids.find((b) => b.status === 'ACCEPTED')?.id;
  return (
    <div className={clsx(c.panel, c.ladder)} aria-label="Bid ladder">
      <span className="eyebrow">Bids this lot</span>
      {pending && (
        <div className={c.rung} style={{ opacity: 0.6, background: 'rgba(245,247,250,.04)' }}>
          <span className="mono muted">#{lot.bids.length + 1}</span>
          <span>{pending.teamName}</span>
          <span className="num">{formatLkr(pending.amount)}</span>
          <span className="chip c-neutral">Sending</span>
        </div>
      )}
      {rejected.map((r) => (
        <div key={r.key} className={clsx(c.rung, c.rejected)}>
          <span className="mono muted">—</span>
          <span>{r.teamName}</span>
          <span className="num">{formatLkr(r.amount)}</span>
          <span className="chip c-err" title={r.reason}>
            {r.label}
          </span>
        </div>
      ))}
      {lot.bids.map((b) => (
        <div key={b.id} className={clsx(c.rung, b.id === leadId && c.leading, b.status === 'VOIDED' && c.voided)}>
          <span className="mono muted">#{b.bidNumber}</span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: 2, background: teamColor(b.teamColorSlot) }} aria-hidden="true" />
            {b.id === leadId ? <strong>{b.teamName}</strong> : b.teamName}
          </span>
          <span className="num">{formatLkr(b.amount)}</span>
          {b.status === 'VOIDED' ? <span className="chip c-neutral">Undone</span> : b.id === leadId ? <span className="chip c-live">Leading</span> : <span className="chip c-neutral">Outbid</span>}
        </div>
      ))}
      {lot.bids.length === 0 && !pending && rejected.length === 0 && <span className="muted">No bids yet. The first bid must be at least the base price.</span>}
      <span className="muted" style={{ fontSize: 12, marginTop: 'auto' }}>
        Rejected bids show the server’s reason; they never change the lead.
      </span>
    </div>
  );
}

function TeamButton({ t, index, selected, leading, disabled, onPick }: { t: TeamRail; index: number; selected: boolean; leading: boolean; disabled: boolean; onPick: () => void }) {
  const total = t.initialBudget + t.adjustments;
  const pct = total > 0 ? Math.min(100, (t.spent / total) * 100) : 0;
  const reason = t.blockedReason ? BLOCKED_LABEL[t.blockedReason] : '';
  return (
    <button type="button" className={clsx(c.team, selected && c.selected, leading && c.isLeading)} disabled={disabled} onClick={onPick} aria-pressed={selected}>
      <span className={c.teamRow}>
        <span className="kbd">{index < 9 ? index + 1 : '·'}</span>
        <CrestTile logoUrl={t.logoUrl} name={t.name} colorSlot={t.colorSlot} size="s" />
        <strong style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t.name}</strong>
        <span className="mono" style={{ fontSize: 12 }}>
          {t.squadCount >= t.maxSquadSize ? '—' : `Max ${formatAmount(t.maxBid)}`}
        </span>
      </span>
      <span className={c.teamRow}>
        <span className={c.meter} role="meter" aria-valuemin={0} aria-valuemax={total} aria-valuenow={t.spent} aria-label={`${t.name} spent ${t.spent} of ${total}`}>
          <span style={{ width: `${pct}%` }} />
        </span>
        <span className="muted" style={{ fontSize: 12 }}>
          {formatLkr(t.remaining)} left
        </span>
      </span>
      <span className={c.teamRow} style={{ gap: 4 }}>
        {Array.from({ length: t.maxSquadSize }, (_, k) => (
          <span key={k} style={{ width: 14, height: 14, borderRadius: 2, border: '1px solid var(--line-3)', background: k < t.squadCount ? 'var(--gold)' : 'transparent', borderColor: k < t.squadCount ? 'var(--gold)' : undefined }} />
        ))}
        <span className="muted" style={{ fontSize: 12, marginLeft: 6 }}>
          {t.squadCount}/{t.maxSquadSize}
        </span>
        {leading ? (
          <span className="chip c-live" style={{ marginLeft: 'auto' }}>
            Leading
          </span>
        ) : (
          reason && (
            <span className="chip c-err" style={{ marginLeft: 'auto' }}>
              {reason}
            </span>
          )
        )}
      </span>
    </button>
  );
}

function IdlePanel({ snap, waiting, roundDone, onNext, onReauction, opening }: { snap: ConsoleSnapshot; waiting: number; roundDone: boolean; onNext: () => void; onReauction: () => void; opening: boolean }) {
  if (snap.status === 'NOT_STARTED') {
    return (
      <div className={clsx(c.panel, c.idle)}>
        <h2 className="t-h2">Ready when you are</h2>
        <p className="muted" style={{ margin: 0 }}>
          {snap.teams.length} teams · {snap.counts.queued} players in the queue. Press Start in the header to go live.
        </p>
        {snap.startBlockers.map((b) => (
          <span key={b} className="chip c-pend">
            {b}
          </span>
        ))}
        <Button href="/admin/auction/setup" variant="out">
          Review setup
        </Button>
      </div>
    );
  }
  if (snap.status === 'COMPLETED') {
    return (
      <div className={clsx(c.panel, c.idle)}>
        <h2 className="t-h2">Auction complete</h2>
        <p className="muted" style={{ margin: 0 }}>
          {snap.counts.sold} sold · {snap.counts.unsold} unsold. Corrections are made from Results and budgets.
        </p>
        <Button href="/admin/auction/results" variant="volt">
          Results and budgets
        </Button>
      </div>
    );
  }
  if (roundDone) {
    return (
      <div className={clsx(c.panel, c.idle)}>
        <h2 className="t-h2">Round {snap.round} finished</h2>
        <p className="muted" style={{ margin: 0 }}>
          {snap.counts.unsold > 0
            ? `${snap.counts.unsold} unsold players can go to a re-auction round. Choose all of them or pick from the unsold panel.`
            : 'Every player has been sold or withdrawn.'}
        </p>
        <div style={{ display: 'flex', gap: 8 }}>
          {snap.counts.unsold > 0 && snap.settings.allowReauction && (
            <Button variant="volt" onClick={onReauction}>
              Open re-auction panel
            </Button>
          )}
        </div>
      </div>
    );
  }
  return (
    <div className={clsx(c.panel, c.idle)}>
      <h2 className="t-h2">No player on the block</h2>
      <p className="muted" style={{ margin: 0 }}>
        {waiting} waiting in round {snap.round}. Bring up the next player, or put one up from the queue.
      </p>
      <Button variant="volt" className={c.xl} disabled={snap.status !== 'LIVE' || waiting === 0} loading={opening} onClick={onNext}>
        Next player
      </Button>
    </div>
  );
}
