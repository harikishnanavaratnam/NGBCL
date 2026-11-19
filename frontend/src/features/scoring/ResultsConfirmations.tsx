'use client';

import { useState } from 'react';
import { EmptyState, ErrorState, SkeletonRows } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Select, Textarea } from '@/design-system/primitives/Inputs';
import { Modal } from '@/design-system/primitives/Overlay';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { useToast } from '@/design-system/primitives/Toast';
import { GameScoreTiles } from '@/design-system/sport/Scores';
import { errorMessage } from '@/lib/api/errors';
import { usePendingResults, useRecentResults, useResultCommands, type MatchState, type Outcome, type Side } from '@/lib/api/scoring';
import { useAuth } from '@/lib/auth/session';
import { formatDateTime } from '@/lib/format/dates';
import { GameEntry, parseRows, toRows, type GameRows } from './GameEntry';
import { rulesSummary } from './ScoringSettingsPanel';

const OUTCOME_LABEL: Record<Outcome, string> = { NORMAL: '', WALKOVER: 'Walkover', RETIRED: 'Retired' };

function resultLine(m: MatchState) {
  const w = m.winnerSide === 'A' ? m.teamA : m.winnerSide === 'B' ? m.teamB : null;
  const how = m.outcome && m.outcome !== 'NORMAL' ? ` (${OUTCOME_LABEL[m.outcome].toLowerCase()})` : '';
  return w ? `${w.name} win ${Math.max(m.gamesA, m.gamesB)}–${Math.min(m.gamesA, m.gamesB)}${how}` : 'Undecided';
}

function MatchSummary({ m }: { m: MatchState }) {
  const names = (side: MatchState['sideA']) => side.map((p) => p.fullName).join(' / ');
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
      <span className="t-caption">
        Tie {m.fixtureNumber} · {m.roundName}
        {m.groupName ? ` · ${m.groupName}` : ''} · Match {m.order} · {m.categoryName} · {m.courtName ?? 'Court TBC'}
      </span>
      <strong>
        {m.teamA.name} v {m.teamB.name}
      </strong>
      <span className="t-body-s muted">
        {names(m.sideA)} v {names(m.sideB)}
      </span>
      <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <span className="t-body-s">{resultLine(m)}</span>
        {m.games.length > 0 && <GameScoreTiles games={m.games.map((g) => ({ a: g.scoreA, b: g.scoreB }))} compact />}
      </div>
      {m.outcomeNote && <span className="t-caption">Note: {m.outcomeNote}</span>}
    </div>
  );
}

/** Admin › Results › Confirmations (RES-001..006): two-step approval queue and audited corrections. */
export function ResultsConfirmations() {
  const { can } = useAuth();
  const pending = usePendingResults();
  const recent = useRecentResults();
  const cmd = useResultCommands();
  const toast = useToast();
  const [returning, setReturning] = useState<MatchState | null>(null);
  const [correcting, setCorrecting] = useState<MatchState | null>(null);
  const canCorrect = can('RESULT_CORRECT');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <nav aria-label="Breadcrumb" className="t-caption">
          Results / Confirmations
        </nav>
        <h1 className="t-h1">Result confirmations</h1>
        <p className="muted t-body-s">
          Submitted results count only after you confirm them. Send a result back to the scorer if it’s wrong while the match is still on
          court.
        </p>
      </div>

      <section className="card" aria-labelledby="pend-title" style={{ display: 'flex', flexDirection: 'column', gap: 16, padding: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <h2 id="pend-title" className="t-h2" style={{ margin: 0 }}>
            Waiting for confirmation
          </h2>
          {pending.data && <StatusChip tone={pending.data.length ? 'pending' : 'neutral'}>{pending.data.length}</StatusChip>}
        </div>
        {pending.isLoading ? (
          <SkeletonRows rows={3} cols={3} />
        ) : pending.error ? (
          <ErrorState message={errorMessage(pending.error)} onRetry={() => void pending.refetch()} />
        ) : !pending.data?.length ? (
          <EmptyState title="Nothing to confirm" description="Results appear here as scorers submit them." />
        ) : (
          <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 12 }}>
            {pending.data.map((m) => (
              <li
                key={m.matchId}
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'minmax(0,1fr) auto',
                  gap: 16,
                  alignItems: 'center',
                  padding: 14,
                  border: '1px solid var(--line)',
                  borderRadius: 'var(--r-m)',
                }}
              >
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  <MatchSummary m={m} />
                  <span className="t-caption">
                    Submitted {m.submittedAt ? formatDateTime(m.submittedAt) : ''}
                    {m.submittedBy ? ` by ${m.submittedBy}` : ''}
                  </span>
                </div>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                  <Button variant="out" onClick={() => setReturning(m)}>
                    Return
                  </Button>
                  {canCorrect && (
                    <Button variant="out" onClick={() => setCorrecting(m)}>
                      Correct
                    </Button>
                  )}
                  <Button
                    variant="volt"
                    loading={cmd.confirm.isPending && cmd.confirm.variables === m.matchId}
                    onClick={() =>
                      cmd.confirm.mutate(m.matchId, {
                        onSuccess: () => toast.success(`Confirmed: ${resultLine(m)}`),
                        onError: (e) => toast.error(errorMessage(e)),
                      })
                    }
                  >
                    Confirm
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card" aria-labelledby="rec-title" style={{ display: 'flex', flexDirection: 'column', gap: 16, padding: 20 }}>
        <h2 id="rec-title" className="t-h2" style={{ margin: 0 }}>
          Recent results
        </h2>
        {recent.isLoading ? (
          <SkeletonRows rows={3} cols={3} />
        ) : recent.error ? (
          <ErrorState message={errorMessage(recent.error)} onRetry={() => void recent.refetch()} />
        ) : !recent.data?.length ? (
          <EmptyState title="No confirmed results yet" />
        ) : (
          <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 10 }}>
            {recent.data.map((m) => (
              <li
                key={m.matchId}
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'minmax(0,1fr) auto',
                  gap: 16,
                  alignItems: 'center',
                  padding: 12,
                  borderBottom: '1px solid var(--line)',
                }}
              >
                <MatchSummary m={m} />
                {canCorrect && (
                  <Button variant="out" size="sm" onClick={() => setCorrecting(m)}>
                    Correct
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <ReturnDialog
        key={returning?.matchId ?? 'none'}
        m={returning}
        pending={cmd.ret.isPending}
        onClose={() => setReturning(null)}
        onSave={(reason) =>
          returning &&
          cmd.ret.mutate(
            { id: returning.matchId, reason },
            {
              onSuccess: () => {
                setReturning(null);
                toast.success('Sent back to the scorer');
              },
              onError: (e) => toast.error(errorMessage(e)),
            },
          )
        }
      />
      {correcting && (
        <CorrectDialog
          m={correcting}
          pending={cmd.correct.isPending}
          onClose={() => setCorrecting(null)}
          onSave={(v) =>
            correcting &&
            cmd.correct.mutate(
              { id: correcting.matchId, ...v },
              {
                onSuccess: () => {
                  setCorrecting(null);
                  toast.success('Result corrected');
                },
                onError: (e) => toast.error(errorMessage(e)),
              },
            )
          }
        />
      )}
    </div>
  );
}

function ReturnDialog({
  m,
  pending,
  onClose,
  onSave,
}: {
  m: MatchState | null;
  pending: boolean;
  onClose: () => void;
  onSave: (reason: string) => void;
}) {
  const [reason, setReason] = useState('');
  return (
    <Modal
      open={m !== null}
      onClose={onClose}
      title="Return to the scorer?"
      footer={
        <>
          <Button variant="out" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="volt" loading={pending} disabled={reason.trim().length < 3} onClick={() => onSave(reason.trim())}>
            Return result
          </Button>
        </>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <p className="t-body-s muted" style={{ margin: 0 }}>
          The match goes back to live scoring on the scorer’s tablet with your reason shown at the top.
        </p>
        <Field label="Reason" required>
          {({ id }) => (
            <Textarea
              id={id}
              rows={3}
              maxLength={300}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Game 2 should be 21-19"
            />
          )}
        </Field>
      </div>
    </Modal>
  );
}

function CorrectDialog({
  m,
  pending,
  onClose,
  onSave,
}: {
  m: MatchState;
  pending: boolean;
  onClose: () => void;
  onSave: (v: { games: [number, number][]; outcome: Outcome; winnerSide?: Side; reason: string }) => void;
}) {
  const [rows, setRows] = useState<GameRows>(() => {
    const r = toRows(m.games);
    return r.length ? r : [['', '']];
  });
  const [outcome, setOutcome] = useState<Outcome>(m.outcome ?? 'NORMAL');
  const [winner, setWinner] = useState<Side>(m.winnerSide ?? 'A');
  const [reason, setReason] = useState('');
  const [err, setErr] = useState<string | null>(null);
  return (
    <Modal
      open
      onClose={onClose}
      width={600}
      title="Correct result"
      footer={
        <>
          <Button variant="out" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="volt"
            loading={pending}
            disabled={reason.trim().length < 3}
            onClick={() => {
              const g = parseRows(rows);
              if (!g || (outcome === 'NORMAL' && !g.length)) return setErr('Enter each game’s score as whole numbers.');
              setErr(null);
              onSave({ games: g, outcome, winnerSide: outcome === 'NORMAL' ? undefined : winner, reason: reason.trim() });
            }}
          >
            Save correction
          </Button>
        </>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <p className="t-body-s muted" style={{ margin: 0 }}>
          {m.teamA.name} v {m.teamB.name} · {m.categoryName} · {rulesSummary(m.rules)}. The tie result, standings and knockout progression
          update; the change is audited.
        </p>
        <Field label="Result type">
          {({ id }) => (
            <Select id={id} value={outcome} onChange={(e) => setOutcome(e.target.value as Outcome)}>
              <option value="NORMAL">Played to a finish</option>
              <option value="WALKOVER">Walkover</option>
              <option value="RETIRED">Retirement</option>
            </Select>
          )}
        </Field>
        {outcome !== 'NORMAL' && (
          <Field label="Winner">
            {({ id }) => (
              <Select id={id} value={winner} onChange={(e) => setWinner(e.target.value as Side)}>
                <option value="A">{m.teamA.name}</option>
                <option value="B">{m.teamB.name}</option>
              </Select>
            )}
          </Field>
        )}
        <GameEntry
          rows={rows}
          onChange={setRows}
          maxGames={m.rules.gamesToWin * 2 - 1}
          labelA={m.teamA.shortCode}
          labelB={m.teamB.shortCode}
        />
        <Field label="Reason" required>
          {({ id }) => <Textarea id={id} rows={2} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} />}
        </Field>
        {err && (
          <p role="alert" className="t-body-s" style={{ color: 'var(--err)', margin: 0 }}>
            {err}
          </p>
        )}
      </div>
    </Modal>
  );
}
