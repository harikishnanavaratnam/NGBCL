'use client';

import { useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Select, Textarea } from '@/design-system/primitives/Inputs';
import { Modal } from '@/design-system/primitives/Overlay';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { PlayerName } from '@/design-system/primitives/Text';
import { useToast } from '@/design-system/primitives/Toast';
import { CrestTile } from '@/design-system/sport/CrestTile';
import { GameScoreTiles } from '@/design-system/sport/Scores';
import { teamColor } from '@/design-system/tokens';
import { ApiError, errorMessage } from '@/lib/api/errors';
import { matchKey, newEventId, situation, useMatchState, useScorerCommands, type MatchState, type Side } from '@/lib/api/scoring';
import { useTopic } from '@/lib/realtime/useTopic';
import { splitName } from '@/lib/format/names';
import { rulesSummary } from './ScoringSettingsPanel';
import { GameEntry, parseRows, toRows, type GameRows } from './GameEntry';
import styles from './Scorer.module.css';

interface Tap {
  side: Side;
  id: string;
}

/**
 * The screen as it will look once queued taps are accepted, so the score moves the instant the
 * scorer taps. The server stays the source of truth — its reply replaces this projection.
 */
export function project(s: MatchState, taps: Tap[]): MatchState {
  if (!taps.length || s.currentGame === null) return s;
  const r = s.rules;
  let { scoreA, scoreB, gamesA, gamesB, server } = s;
  let current: number | null = s.currentGame;
  for (const t of taps) {
    if (current === null) break;
    if (t.side === 'A') scoreA++;
    else scoreB++;
    server = t.side;
    const a = t.side === 'A' ? scoreA : scoreB;
    const b = t.side === 'A' ? scoreB : scoreA;
    if (a >= r.pointCap || (a >= r.pointsToWin && a - b >= r.winBy)) {
      if (t.side === 'A') gamesA++;
      else gamesB++;
      if (gamesA >= r.gamesToWin || gamesB >= r.gamesToWin) {
        current = null;
      } else {
        current += 1;
        scoreA = 0;
        scoreB = 0;
      }
    }
  }
  return {
    ...s,
    scoreA,
    scoreB,
    gamesA,
    gamesB,
    server,
    currentGame: current,
    winnerSide: current === null ? (gamesA > gamesB ? 'A' : 'B') : null,
  };
}

export function ScoringScreen({ matchId }: { matchId: string }) {
  const { data, isLoading, error, refetch } = useMatchState(matchId);
  if (isLoading) {
    return (
      <div className={styles.app} aria-busy="true">
        <div className={styles.top}>
          <Skeleton height={28} width={320} />
        </div>
        <div className={styles.courts} style={{ paddingTop: 16 }}>
          <Skeleton height={420} />
          <Skeleton height={420} />
        </div>
      </div>
    );
  }
  if (error || !data) {
    const notFound = error instanceof ApiError && (error.status === 404 || error.status === 403);
    return (
      <div style={{ padding: 48 }}>
        {notFound ? (
          <EmptyState title="Match not available" description={errorMessage(error)} action={{ label: 'My matches', href: '/score' }} />
        ) : (
          <ErrorState message={errorMessage(error)} onRetry={() => void refetch()} />
        )}
      </div>
    );
  }
  return <Scoring s={data} />;
}

function Scoring({ s }: { s: MatchState }) {
  const toast = useToast();
  const qc = useQueryClient();
  const cmd = useScorerCommands(s.matchId);
  const [taps, setTaps] = useState<Tap[]>([]);
  const sending = useRef(false);
  const [dialog, setDialog] = useState<null | 'manual' | 'concede' | 'interval' | 'submit'>(null);

  // Send queued taps one at a time, in order (the order decides who serves and when a game ends).
  useEffect(() => {
    if (sending.current || !taps.length) return;
    sending.current = true;
    const t = taps[0]!;
    cmd.point
      .mutateAsync({ side: t.side, clientEventId: t.id })
      .then(() => setTaps((q) => q.slice(1)))
      .catch((e: unknown) => {
        setTaps([]);
        toast.error(errorMessage(e));
      })
      .finally(() => {
        sending.current = false;
      });
  }, [taps, cmd.point, toast]);

  // Another device (or an official) changed this match: refetch unless we're mid-send.
  useTopic<{ stateVersion: number }>(
    `/topic/public/match/${s.matchId}`,
    (msg) => {
      if (!sending.current && msg.payload.stateVersion > s.stateVersion) void qc.invalidateQueries({ queryKey: matchKey(s.matchId) });
    },
    () => void qc.invalidateQueries({ queryKey: matchKey(s.matchId) }),
  );

  // Keep a tablet awake while a match is on.
  useEffect(() => {
    if (s.status !== 'LIVE' || typeof navigator === 'undefined' || !('wakeLock' in navigator)) return;
    let lock: { release: () => Promise<void> } | null = null;
    navigator.wakeLock
      .request('screen')
      .then((l) => {
        lock = l;
      })
      .catch(() => undefined);
    return () => void lock?.release().catch(() => undefined);
  }, [s.status]);

  const v = useMemo(() => project(s, taps), [s, taps]);
  const live = s.status === 'LIVE';
  const decided = v.winnerSide !== null;
  const canTap = live && !decided;
  const tap = useCallback((side: Side) => setTaps((q) => [...q, { side, id: newEventId() }]), []);
  const label = situation(v);
  // Game/match point belongs to the side ahead; deuce/golden point to the server.
  const labelSide: Side | null = v.scoreA > v.scoreB ? 'A' : v.scoreB > v.scoreA ? 'B' : v.server;
  const doneGames = s.games.filter((g) => g.winnerSide !== null).map((g) => ({ a: g.scoreA, b: g.scoreB }));
  const fail = (e: unknown) => toast.error(errorMessage(e));
  const busy = taps.length > 0 || Object.values(cmd).some((m) => m.isPending);

  return (
    <div className={styles.app}>
      <header className={styles.top}>
        {live ? (
          <StatusChip tone="live">Live</StatusChip>
        ) : (
          <StatusChip status={s.status}>{s.status === 'SCHEDULED' ? 'Not started' : undefined}</StatusChip>
        )}
        <span className={styles.topTitle}>
          {s.categoryName} · Match {s.order}
        </span>
        <span className={styles.topMeta}>
          {s.courtName ?? 'Court TBC'} · {s.teamA.name} v {s.teamB.name} · Tie {s.fixtureNumber}
        </span>
        <span className="chip c-neutral" role="status" aria-live="polite">
          <span
            style={{ width: 7, height: 7, borderRadius: '50%', background: busy ? 'var(--warn)' : 'var(--lime)', display: 'inline-block' }}
            aria-hidden="true"
          />
          {busy ? 'Sending…' : 'Synced'}
        </span>
      </header>

      <div className={styles.bar}>
        {v.currentGame !== null ? (
          <span className="chip c-live">G{v.currentGame} · In play</span>
        ) : decided ? (
          <span className="chip c-live">Match decided</span>
        ) : null}
        {doneGames.length > 0 && <GameScoreTiles games={doneGames} compact />}
        <span className={styles.rules}>
          {rulesSummary(s.rules)}
          {s.rules.custom ? ' · set for this match' : ''}
        </span>
      </div>

      {s.returnedReason && live && (
        <div className={clsx(styles.banner, styles.bannerWarn)} role="alert">
          <strong>Returned by an official:</strong> {s.returnedReason}
        </div>
      )}

      {s.status === 'SCHEDULED' ? (
        <StartPanel
          s={s}
          onStart={(side) => cmd.start.mutate(side, { onError: fail })}
          starting={cmd.start.isPending}
          onManual={() => setDialog('manual')}
          onConcede={() => setDialog('concede')}
        />
      ) : (
        <>
          {decided && live && (
            <div className={clsx(styles.banner, styles.bannerOk)} role="status">
              <span>
                <strong>{(v.winnerSide === 'A' ? s.teamA : s.teamB).name}</strong> win{' '}
                {s.outcome === 'WALKOVER'
                  ? 'by walkover'
                  : s.outcome === 'RETIRED'
                    ? 'by retirement'
                    : `${Math.max(v.gamesA, v.gamesB)}–${Math.min(v.gamesA, v.gamesB)}`}
                . Check the score, then submit.
              </span>
              <Button variant="volt" style={{ marginLeft: 'auto' }} disabled={busy} onClick={() => setDialog('submit')}>
                Submit result
              </Button>
            </div>
          )}
          {s.status === 'SUBMITTED' && (
            <div className={clsx(styles.banner)} role="status">
              <StatusChip status="SUBMITTED">Awaiting confirmation</StatusChip>
              <span className="t-body-s">
                A referee or tournament admin will confirm the result. If they send it back you’ll see why here.
              </span>
            </div>
          )}
          {s.status === 'COMPLETED' && (
            <div className={clsx(styles.banner)} role="status">
              <StatusChip status="COMPLETED">Final</StatusChip>
              <span className="t-body-s">This result is final. Ask an official if it needs a correction.</span>
            </div>
          )}
          <div className={styles.courts}>
            {(['A', 'B'] as const).map((side) => {
              const team = side === 'A' ? s.teamA : s.teamB;
              const players = side === 'A' ? s.sideA : s.sideB;
              // Once decided the server reports 0–0; keep showing the final game's score.
              const last = s.currentGame === null ? s.games[s.games.length - 1] : undefined;
              const fa = last ? last.scoreA : v.scoreA;
              const fb = last ? last.scoreB : v.scoreB;
              const score = side === 'A' ? fa : fb;
              const other = side === 'A' ? fb : fa;
              const games = side === 'A' ? v.gamesA : v.gamesB;
              const serving = canTap && v.server === side;
              return (
                <button
                  key={side}
                  type="button"
                  className={clsx(styles.side, serving && styles.serving)}
                  style={{ ['--team' as string]: teamColor(team.colorSlot) }}
                  disabled={!canTap}
                  onClick={() => tap(side)}
                  aria-label={`Point to ${team.name}. Score ${score} to ${other}.`}
                >
                  {canTap && labelSide === side && label && (
                    <span className={clsx('chip c-gold', styles.situation)} aria-live="polite">
                      {label}
                    </span>
                  )}
                  <CrestTile name={team.name} code={team.shortCode} colorSlot={team.colorSlot} logoUrl={team.logoUrl} size="s" />
                  <span className={styles.teamName}>{team.name}</span>
                  <span className={styles.players}>
                    {players.map((p) => {
                      const n = splitName(p.fullName);
                      return <PlayerName key={p.fullName} first={n.first} last={n.last} />;
                    })}
                  </span>
                  <span className={styles.gamesWon} aria-label={`${games} games won`}>
                    {Array.from({ length: s.rules.gamesToWin }, (_, i) => (
                      <span key={i} className={clsx(styles.pip, i < games && styles.pipOn)} />
                    ))}
                  </span>
                  {serving ? <span className="chip c-live">Serving</span> : <span style={{ height: 22 }} aria-hidden="true" />}
                  <span className={clsx(styles.big, score < other && styles.dim)} aria-hidden="true">
                    {score}
                  </span>
                </button>
              );
            })}
          </div>
        </>
      )}

      <nav className={styles.actions} aria-label="Match actions">
        <Button
          variant="out"
          disabled={!live || !s.canUndo || busy}
          loading={cmd.undo.isPending}
          onClick={() => cmd.undo.mutate(undefined, { onError: fail })}
        >
          Undo last point
        </Button>
        <Button variant="out" disabled={!live} onClick={() => setDialog('interval')}>
          Interval
        </Button>
        <Button
          variant="out"
          disabled={!(live || s.status === 'SCHEDULED') || (!s.allowWalkover && !s.allowRetirement) || busy}
          onClick={() => setDialog('concede')}
        >
          Retired / walkover
        </Button>
        <Button variant="out" disabled={!(live || s.status === 'SCHEDULED') || busy} onClick={() => setDialog('manual')}>
          Game-by-game
        </Button>
        <Button variant="out" href="/score">
          Save and exit
        </Button>
      </nav>

      {dialog === 'manual' && (
        <ManualDialog
          open
          s={s}
          pending={cmd.manual.isPending}
          onClose={() => setDialog(null)}
          onSave={(games) => cmd.manual.mutate({ games }, { onSuccess: () => setDialog(null), onError: fail })}
        />
      )}
      {dialog === 'concede' && (
        <ConcedeDialog
          open
          s={s}
          pending={cmd.concede.isPending}
          onClose={() => setDialog(null)}
          onSave={(x) => cmd.concede.mutate(x, { onSuccess: () => setDialog(null), onError: fail })}
        />
      )}
      {dialog === 'interval' && <IntervalDialog onClose={() => setDialog(null)} />}
      <Modal
        open={dialog === 'submit'}
        onClose={() => setDialog(null)}
        title="Submit this result?"
        footer={
          <>
            <Button variant="out" onClick={() => setDialog(null)}>
              Keep scoring
            </Button>
            <Button
              variant="volt"
              loading={cmd.submit.isPending}
              onClick={() =>
                cmd.submit.mutate(undefined, {
                  onSuccess: (r) => {
                    setDialog(null);
                    toast.success(r.status === 'COMPLETED' ? 'Result saved' : 'Result sent for confirmation');
                  },
                  onError: fail,
                })
              }
            >
              Submit
            </Button>
          </>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <p className="t-body" style={{ margin: 0 }}>
            {s.teamA.name} {s.gamesA}–{s.gamesB} {s.teamB.name}
          </p>
          {s.games.length > 0 && <GameScoreTiles games={s.games.map((g) => ({ a: g.scoreA, b: g.scoreB }))} />}
          <p className="t-body-s muted" style={{ margin: 0 }}>
            {s.twoStepApproval
              ? 'A referee or tournament admin confirms it before it counts. Until then it shows as provisional.'
              : 'The result counts straight away.'}
          </p>
        </div>
      </Modal>
    </div>
  );
}

function StartPanel({
  s,
  onStart,
  starting,
  onManual,
  onConcede,
}: {
  s: MatchState;
  onStart: (s: Side) => void;
  starting: boolean;
  onManual: () => void;
  onConcede: () => void;
}) {
  const ready = s.lineupComplete;
  return (
    <div style={{ flex: 1, padding: '8px 16px 16px', display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 16, padding: 20 }}>
        <h1 className="t-h2" style={{ margin: 0 }}>
          Who serves first?
        </h1>
        {!ready && (
          <div className={clsx(styles.banner, styles.bannerWarn)} style={{ margin: 0 }}>
            Both line-ups must be set before the match starts. Ask the tournament admin to pick the players for this match.
          </div>
        )}
        <div className={styles.courts} style={{ padding: 0 }}>
          {(['A', 'B'] as const).map((side) => {
            const team = side === 'A' ? s.teamA : s.teamB;
            const players = side === 'A' ? s.sideA : s.sideB;
            return (
              <button
                key={side}
                type="button"
                className={styles.side}
                style={{ ['--team' as string]: teamColor(team.colorSlot), minHeight: 220 }}
                disabled={!ready || starting}
                onClick={() => onStart(side)}
              >
                <CrestTile name={team.name} code={team.shortCode} colorSlot={team.colorSlot} logoUrl={team.logoUrl} size="m" />
                <span className={styles.teamName}>{team.name}</span>
                <span className={styles.players}>
                  {players.length ? players.map((p) => <span key={p.fullName}>{p.fullName}</span>) : 'Line-up not set'}
                </span>
                <span className="chip c-live">{starting ? 'Starting…' : `${team.shortCode} serves first`}</span>
              </button>
            );
          })}
        </div>
        <p className="t-caption" style={{ margin: 0 }}>
          Format: {rulesSummary(s.rules)}. Scoring from a paper sheet instead? Use game-by-game. No-show? Record a walkover.
        </p>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <Button variant="out" onClick={onManual} disabled={!ready}>
            Enter game-by-game
          </Button>
          {s.allowWalkover && (
            <Button variant="out" onClick={onConcede} disabled={!ready}>
              Record a walkover
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

function ManualDialog({
  open,
  s,
  pending,
  onClose,
  onSave,
}: {
  open: boolean;
  s: MatchState;
  pending: boolean;
  onClose: () => void;
  onSave: (g: [number, number][]) => void;
}) {
  const initial = (): GameRows => {
    const r = toRows(s.games.filter((g) => g.winnerSide !== null || g.scoreA + g.scoreB > 0));
    return r.length ? r : [['', '']];
  };
  const [rows, setRows] = useState<GameRows>(initial);
  const [err, setErr] = useState<string | null>(null);
  const maxGames = s.rules.gamesToWin * 2 - 1;
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Game-by-game score"
      footer={
        <>
          <Button variant="out" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="volt"
            loading={pending}
            onClick={() => {
              const g = parseRows(rows);
              if (!g || !g.length) return setErr('Enter each game’s score as whole numbers.');
              setErr(null);
              onSave(g);
            }}
          >
            Save score
          </Button>
        </>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <p className="t-body-s muted" style={{ margin: 0 }}>
          Enter the full result, e.g. from the paper scoresheet. It replaces the rally-by-rally score and must be a finished match under{' '}
          {rulesSummary(s.rules)}.
        </p>
        <GameEntry rows={rows} onChange={setRows} maxGames={maxGames} labelA={s.teamA.shortCode} labelB={s.teamB.shortCode} />
        {err && (
          <p role="alert" className="t-body-s" style={{ color: 'var(--err)', margin: 0 }}>
            {err}
          </p>
        )}
      </div>
    </Modal>
  );
}

function ConcedeDialog({
  open,
  s,
  pending,
  onClose,
  onSave,
}: {
  open: boolean;
  s: MatchState;
  pending: boolean;
  onClose: () => void;
  onSave: (v: { type: 'WALKOVER' | 'RETIRED'; side: Side; note: string }) => void;
}) {
  const types = [
    ...(s.allowWalkover ? (['WALKOVER'] as const) : []),
    ...(s.allowRetirement && s.status === 'LIVE' ? (['RETIRED'] as const) : []),
  ];
  const [type, setType] = useState<'WALKOVER' | 'RETIRED'>(types[0] ?? 'WALKOVER');
  const [side, setSide] = useState<Side>('A');
  const [note, setNote] = useState('');
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Retired / walkover"
      footer={
        <>
          <Button variant="out" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="volt"
            loading={pending}
            disabled={note.trim().length < 2 || !types.length}
            onClick={() => onSave({ type, side, note: note.trim() })}
          >
            Record
          </Button>
        </>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <Field label="What happened">
          {({ id }) => (
            <Select id={id} value={type} onChange={(e) => setType(e.target.value as 'WALKOVER' | 'RETIRED')}>
              {types.includes('WALKOVER') && <option value="WALKOVER">Walkover — the other side didn’t play</option>}
              {types.includes('RETIRED') && <option value="RETIRED">Retirement — a side stopped during the match</option>}
            </Select>
          )}
        </Field>
        <Field label={type === 'WALKOVER' ? 'Winner' : 'Side that retired'}>
          {({ id }) => (
            <Select id={id} value={side} onChange={(e) => setSide(e.target.value as Side)}>
              <option value="A">{s.teamA.name}</option>
              <option value="B">{s.teamB.name}</option>
            </Select>
          )}
        </Field>
        <Field label="Note" required help="E.g. injury, no-show. Officials see this when they confirm.">
          {({ id }) => <Textarea id={id} rows={2} maxLength={300} value={note} onChange={(e) => setNote(e.target.value)} />}
        </Field>
        {type === 'RETIRED' && (
          <p className="t-caption" style={{ margin: 0 }}>
            The score so far is kept; the other side wins the match.
          </p>
        )}
      </div>
    </Modal>
  );
}

function IntervalDialog({ onClose }: { onClose: () => void }) {
  const [ends, setEnds] = useState<number | null>(null);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (ends === null) return;
    const t = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(t);
  }, [ends]);
  const left = ends === null ? null : Math.max(0, Math.ceil((ends - now) / 1000));
  return (
    <Modal
      open
      onClose={onClose}
      title="Interval"
      footer={
        <Button variant="volt" onClick={onClose}>
          Back to scoring
        </Button>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16, padding: '8px 0' }}>
        <span className="num" style={{ fontSize: 96, lineHeight: 1 }} aria-live="polite">
          {left === null ? '—' : `${Math.floor(left / 60)}:${String(left % 60).padStart(2, '0')}`}
        </span>
        <div style={{ display: 'flex', gap: 12 }}>
          <Button
            variant="out"
            onClick={() => {
              setNow(Date.now());
              setEnds(Date.now() + 60_000);
            }}
          >
            60 seconds
          </Button>
          <Button
            variant="out"
            onClick={() => {
              setNow(Date.now());
              setEnds(Date.now() + 120_000);
            }}
          >
            2 minutes
          </Button>
        </div>
        <p className="t-caption" style={{ margin: 0 }}>
          A timer for this tablet only — nothing is recorded.
        </p>
      </div>
    </Modal>
  );
}
