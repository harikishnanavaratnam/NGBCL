'use client';

import clsx from 'clsx';
import { useState } from 'react';
import { EmptyState, ErrorState, Skeleton, StatBlock } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input, Select, Textarea } from '@/design-system/primitives/Inputs';
import { Modal } from '@/design-system/primitives/Overlay';
import { Money } from '@/design-system/primitives/Text';
import { useToast } from '@/design-system/primitives/Toast';
import { BudgetMeter, SquadSlots } from '@/design-system/sport/Budget';
import { CrestTile } from '@/design-system/sport/CrestTile';
import { teamColor } from '@/design-system/tokens';
import { useCorrections, useRosters, type RosterEntry, type TeamRoster } from '@/lib/api/auction';
import { errorMessage } from '@/lib/api/errors';
import { useAuth } from '@/lib/auth/session';
import s from './Auction.module.css';
import { useAuctionLive } from './useAuctionLive';

type Action =
  | { kind: 'release'; entry: RosterEntry; team: TeamRoster }
  | { kind: 'move'; entry: RosterEntry; team: TeamRoster }
  | { kind: 'adjust'; team: TeamRoster };

/** Admin › Auction › Results and budgets: squads, purses and audited corrections (AUC-025..027). */
export function AuctionResultsAdmin() {
  const { can } = useAuth();
  const canCorrect = can('AUCTION_CORRECT');
  const { data, isLoading, error, refetch } = useRosters();
  const [action, setAction] = useState<Action | null>(null);
  useAuctionLive({ staff: true, onEvent: () => void refetch() });

  if (isLoading) {
    return (
      <div className={s.page} aria-busy="true">
        <Skeleton height={40} width={320} />
        <div className={s.rosters}>
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} height={280} />
          ))}
        </div>
      </div>
    );
  }
  if (error || !data) return <ErrorState title="Couldn’t load results" message={errorMessage(error)} onRetry={() => void refetch()} />;

  const spent = data.reduce((sum, r) => sum + r.team.spent, 0);
  const players = data.reduce((sum, r) => sum + r.players.length, 0);
  const top = data.flatMap((r) => r.players).reduce<RosterEntry | null>((best, p) => ((p.purchasePrice ?? 0) > (best?.purchasePrice ?? -1) ? p : best), null);

  return (
    <div className={s.page}>
      <div className={s.head}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <nav aria-label="Breadcrumb" className="t-caption">
            Auction / Results and budgets
          </nav>
          <h1 className="t-h1">Results and budgets</h1>
          <p className="muted t-body-s" style={{ margin: 0 }}>
            Corrections keep the original sale in the history, need a reason and are recorded in the audit log.
          </p>
        </div>
        <Button href="/auction" variant="out" target="_blank">
          Public results ↗
        </Button>
      </div>

      {data.length === 0 ? (
        <EmptyState title="The auction hasn’t started" description="Squads and budgets appear here once the auction is live." action={{ label: 'Auction setup', href: '/admin/auction/setup' }} />
      ) : (
        <>
          <div className={s.stats}>
            <StatBlock label="Players bought" value={players} />
            <StatBlock label="Total spent" value={<Money value={spent} />} />
            <StatBlock label="Top buy" value={top?.purchasePrice ? <Money value={top.purchasePrice} /> : '—'} sub={top?.fullName} />
            <StatBlock label="Teams" value={data.length} />
          </div>
          <div className={s.rosters}>
            {data.map((r) => (
              <section key={r.team.teamId} className={clsx('card', s.roster)} style={{ borderTop: `4px solid ${teamColor(r.team.colorSlot)}` }} aria-label={r.team.name}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <CrestTile logoUrl={r.team.logoUrl} name={r.team.name} colorSlot={r.team.colorSlot} size="s" />
                  <strong style={{ flex: 1 }}>{r.team.name}</strong>
                  {canCorrect && (
                    <Button size="sm" variant="ghost" onClick={() => setAction({ kind: 'adjust', team: r })}>
                      Adjust budget
                    </Button>
                  )}
                </div>
                <BudgetMeter initial={r.team.initialBudget + r.team.adjustments} spent={r.team.spent} />
                {r.team.adjustments !== 0 && (
                  <span className="t-caption">
                    Includes adjustments of <Money value={r.team.adjustments} />
                  </span>
                )}
                <SquadSlots max={r.team.maxSquadSize} filled={r.team.squadCount} />
                <div>
                  {r.players.length === 0 && <p className="muted t-body-s" style={{ margin: 0 }}>No players yet.</p>}
                  {r.players.map((p) => (
                    <div key={p.entryId} className={s.rosterRow}>
                      {p.photoUrl ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img src={p.photoUrl} alt="" className={s.thumb} />
                      ) : (
                        <span className={clsx('ph-person', s.thumb)} aria-hidden="true" />
                      )}
                      <span style={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0 }}>
                        <strong style={{ fontSize: 14 }}>{p.fullName}</strong>
                        <span className="t-caption">
                          {p.categoryName ?? 'No category'}
                          {p.lotNumber ? ` · lot ${p.lotNumber}` : ''}
                        </span>
                      </span>
                      {p.purchasePrice !== null && <Money value={p.purchasePrice} />}
                      {canCorrect && p.lotId && (
                        <span style={{ display: 'flex', gap: 2 }}>
                          <Button size="sm" variant="ghost" onClick={() => setAction({ kind: 'move', entry: p, team: r })}>
                            Move
                          </Button>
                          <Button size="sm" variant="ghost" onClick={() => setAction({ kind: 'release', entry: p, team: r })}>
                            Release
                          </Button>
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              </section>
            ))}
          </div>
        </>
      )}
      {action && <CorrectionDialog action={action} rosters={data} onClose={() => setAction(null)} />}
    </div>
  );
}

function CorrectionDialog({ action, rosters, onClose }: { action: Action; rosters: TeamRoster[]; onClose: () => void }) {
  const fix = useCorrections();
  const toast = useToast();
  const [reason, setReason] = useState('');
  const [teamId, setTeamId] = useState('');
  const [price, setPrice] = useState(action.kind === 'move' ? String(action.entry.purchasePrice ?? '') : '');
  const [amount, setAmount] = useState('');
  const pending = fix.release.isPending || fix.move.isPending || fix.adjust.isPending;
  const priceN = Number(price.replace(/,/g, ''));
  const amountN = Number(amount.replace(/,/g, ''));
  const valid =
    reason.trim().length >= 3 &&
    (action.kind === 'release' ||
      (action.kind === 'move' && teamId !== '' && /^\d+$/.test(price.replace(/,/g, '')) && priceN > 0) ||
      (action.kind === 'adjust' && /^-?\d+$/.test(amount.replace(/,/g, '')) && amountN !== 0));

  const done = (msg: string) => ({
    onSuccess: () => {
      toast.success(msg);
      onClose();
    },
    onError: (e: unknown) => toast.error(errorMessage(e)),
  });

  const submit = () => {
    if (action.kind === 'release') fix.release.mutate({ lotId: action.entry.lotId!, reason: reason.trim() }, done(`${action.entry.fullName} released`));
    else if (action.kind === 'move')
      fix.move.mutate({ lotId: action.entry.lotId!, teamId, price: priceN, reason: reason.trim() }, done(`${action.entry.fullName} moved`));
    else fix.adjust.mutate({ teamId: action.team.team.teamId, amount: amountN, reason: reason.trim() }, done(`Budget adjusted for ${action.team.team.name}`));
  };

  const title =
    action.kind === 'release' ? `Release ${action.entry.fullName}?` : action.kind === 'move' ? `Move ${action.entry.fullName} to another team` : `Adjust ${action.team.team.name}’s budget`;

  return (
    <Modal
      open
      onClose={onClose}
      title={title}
      footer={
        <>
          <Button variant="out" onClick={onClose} disabled={pending}>
            Cancel
          </Button>
          <Button variant={action.kind === 'release' ? 'danger' : 'volt'} disabled={!valid} loading={pending} onClick={submit}>
            {action.kind === 'release' ? 'Release player' : action.kind === 'move' ? 'Move player' : 'Save adjustment'}
          </Button>
        </>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {action.kind === 'release' && (
          <p className="muted" style={{ margin: 0 }}>
            {action.team.team.name} gets {action.entry.purchasePrice ? <Money value={action.entry.purchasePrice} /> : 'the price'} back and a free squad slot. The
            player becomes unsold and can be put up again in a re-auction round.
          </p>
        )}
        {action.kind === 'move' && (
          <>
            <p className="muted" style={{ margin: 0 }}>
              {action.team.team.name} is refunded; the new team pays the price below. The original sale stays in the history as reversed.
            </p>
            <Field label="New team" required>
              {({ id }) => (
                <Select id={id} value={teamId} onChange={(e) => setTeamId(e.target.value)}>
                  <option value="">Choose a team</option>
                  {rosters
                    .filter((r) => r.team.teamId !== action.team.team.teamId)
                    .map((r) => (
                      <option key={r.team.teamId} value={r.team.teamId} disabled={r.team.squadCount >= r.team.maxSquadSize}>
                        {r.team.name} — {r.team.squadCount}/{r.team.maxSquadSize}, {r.team.remaining.toLocaleString('en-LK')} left
                      </option>
                    ))}
                </Select>
              )}
            </Field>
            <Field label="Price (LKR)" required>
              {({ id }) => <Input id={id} inputMode="numeric" value={price} onChange={(e) => setPrice(e.target.value)} />}
            </Field>
          </>
        )}
        {action.kind === 'adjust' && (
          <>
            <p className="muted" style={{ margin: 0 }}>
              Remaining now <Money value={action.team.team.remaining} />. Use a negative amount to take money away. Only possible if manual adjustments are allowed in the
              auction settings.
            </p>
            <Field label="Amount (LKR)" required help="For example 50000 or -25000.">
              {({ id }) => <Input id={id} inputMode="numeric" value={amount} onChange={(e) => setAmount(e.target.value)} />}
            </Field>
          </>
        )}
        <Field label="Reason" required help="Saved in the audit log.">
          {({ id }) => <Textarea id={id} rows={3} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} />}
        </Field>
      </div>
    </Modal>
  );
}
