'use client';

import clsx from 'clsx';
import { useState } from 'react';
import { EmptyState, ErrorState, Skeleton, StatBlock } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Inputs';
import { ConfirmDialog } from '@/design-system/primitives/Overlay';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { Money } from '@/design-system/primitives/Text';
import { useToast } from '@/design-system/primitives/Toast';
import { CrestTile } from '@/design-system/sport/CrestTile';
import { useAuctionCommands, useAuctionConsole, type QueueItem } from '@/lib/api/auction';
import { errorMessage } from '@/lib/api/errors';
import { useAuth } from '@/lib/auth/session';
import { formatDateTime } from '@/lib/format/dates';
import { STATE_LABEL, STATE_TONE } from './labels';
import s from './Auction.module.css';
import { useAuctionLive } from './useAuctionLive';

/** Admin › Auction › Setup: readiness, rules summary, queue order and per-player base prices. */
export function AuctionSetup() {
  const { can } = useAuth();
  const canConfigure = can('AUCTION_CONFIGURE');
  const canControl = can('AUCTION_CONTROL');
  const { data, isLoading, error, refetch } = useAuctionConsole();
  const cmd = useAuctionCommands();
  const toast = useToast();
  const [confirmStart, setConfirmStart] = useState(false);
  useAuctionLive({ staff: true });

  if (isLoading) {
    return (
      <div className={s.page} aria-busy="true">
        <Skeleton height={40} width={280} />
        <div className={s.stats}>
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} height={96} />
          ))}
        </div>
        <Skeleton height={360} />
      </div>
    );
  }
  if (error || !data) return <ErrorState title="Couldn’t load the auction" message={errorMessage(error)} onRetry={() => void refetch()} />;

  const st = data.settings;
  const waiting = data.queue.filter((q) => q.state === 'QUEUED' && q.round === data.round);
  const others = data.queue.filter((q) => !(q.state === 'QUEUED' && q.round === data.round));
  const started = data.status !== 'NOT_STARTED';

  const move = (i: number, dir: -1 | 1) => {
    const ids = waiting.map((q) => q.lotId);
    const j = i + dir;
    if (j < 0 || j >= ids.length) return;
    [ids[i], ids[j]] = [ids[j], ids[i]];
    cmd.reorder.mutate({ lotIds: ids }, { onError: (e) => toast.error(errorMessage(e)) });
  };

  return (
    <div className={s.page}>
      <div className={s.head}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <nav aria-label="Breadcrumb" className="t-caption">
            Auction / Setup
          </nav>
          <h1 className="t-h1">Auction setup</h1>
          <p className="muted t-body-s" style={{ margin: 0 }}>
            Approved players join the queue automatically. Teams that are active when the auction starts each get the full budget.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <StatusChip dot={data.status === 'LIVE'} tone={data.status === 'PAUSED' ? 'pending' : data.status === 'COMPLETED' ? 'gold' : data.status === 'LIVE' ? 'live' : 'neutral'}>
            {data.status === 'NOT_STARTED' ? 'Not started' : data.status === 'LIVE' ? `Live · round ${data.round}` : data.status === 'PAUSED' ? 'Paused' : 'Completed'}
          </StatusChip>
          {canConfigure && (
            <Button href="/admin/system/settings/auction" variant="out">
              Auction rules
            </Button>
          )}
          {canControl &&
            (started ? (
              <Button href="/console/auction" variant="volt">
                Open console
              </Button>
            ) : (
              <Button variant="volt" disabled={data.startBlockers.length > 0} onClick={() => setConfirmStart(true)}>
                Start auction
              </Button>
            ))}
        </div>
      </div>

      {!started && data.startBlockers.length > 0 && (
        <div role="status" className={s.notice}>
          <strong>Not ready to start</strong>
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            {data.startBlockers.map((b) => (
              <li key={b}>{b}</li>
            ))}
          </ul>
        </div>
      )}

      <div className={s.stats}>
        <StatBlock label="Teams" value={data.teams.length} sub={`Squad of up to ${st.maxSquadSize}${st.minSquadSize ? `, at least ${st.minSquadSize}` : ''}`} />
        <StatBlock label="Players in queue" value={data.counts.queued} sub={`${data.counts.sold} sold · ${data.counts.unsold} unsold · ${data.counts.withdrawn} withdrawn`} />
        <StatBlock label="Budget per team" value={<Money value={st.initialBudget} />} sub={`Base ${st.defaultBasePrice.toLocaleString('en-LK')} · +${st.minIncrement.toLocaleString('en-LK')} per raise`} />
        <StatBlock label="Starts" value={st.startsAt ? formatDateTime(st.startsAt) : 'TBC'} sub={st.allowReauction ? 'Re-auction round allowed' : 'No re-auction round'} />
      </div>

      <section className="card" aria-labelledby="teams-h" style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 12 }}>
        <h2 id="teams-h" className="t-h3">
          Teams
        </h2>
        {data.teams.length === 0 ? (
          <EmptyState title="No active teams yet" description="Approve team registrations or add teams in the directory." action={{ label: 'Teams', href: '/admin/directory/teams' }} />
        ) : (
          <div className={s.teamWrap}>
            {data.teams.map((t) => (
              <span key={t.teamId} className={s.teamPill}>
                <CrestTile logoUrl={t.logoUrl} name={t.name} colorSlot={t.colorSlot} size="s" />
                {t.name}
                {started && <span className="muted t-caption">{t.squadCount}/{t.maxSquadSize}</span>}
              </span>
            ))}
          </div>
        )}
      </section>

      <section className="card" aria-labelledby="queue-h" style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
          <h2 id="queue-h" className="t-h3">
            {started ? `Round ${data.round} queue` : 'Auction order'}
          </h2>
          <span className="t-caption">Players come up in this order unless the auctioneer picks one. Base price defaults to {st.defaultBasePrice.toLocaleString('en-LK')}.</span>
        </div>
        {waiting.length === 0 ? (
          <EmptyState title="No players waiting" description={started ? 'This round’s queue is finished.' : 'Approve player registrations to fill the queue.'} />
        ) : (
          <div className="scroll" style={{ overflowX: 'auto' }}>
            <table className="tbl">
              <thead>
                <tr>
                  <th style={{ width: 56 }}>#</th>
                  <th>Player</th>
                  <th>Category</th>
                  <th className="r">Base price (LKR)</th>
                  {canConfigure && <th className="r">Order</th>}
                </tr>
              </thead>
              <tbody>
                {waiting.map((q, i) => (
                  <tr key={q.lotId}>
                    <td className="mono muted">{i + 1}</td>
                    <td>
                      <PlayerCell q={q} />
                    </td>
                    <td>{q.categoryName ?? <span className="muted">—</span>}</td>
                    <td className="r">
                      <BasePriceCell q={q} defaultPrice={st.defaultBasePrice} editable={canConfigure} />
                    </td>
                    {canConfigure && (
                      <td className="r" style={{ whiteSpace: 'nowrap' }}>
                        <Button size="sm" variant="ghost" aria-label={`Move ${q.fullName} up`} disabled={i === 0 || cmd.reorder.isPending} onClick={() => move(i, -1)}>
                          ↑
                        </Button>
                        <Button size="sm" variant="ghost" aria-label={`Move ${q.fullName} down`} disabled={i === waiting.length - 1 || cmd.reorder.isPending} onClick={() => move(i, 1)}>
                          ↓
                        </Button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {others.length > 0 && (
        <section className="card" aria-labelledby="done-h" style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 12 }}>
          <h2 id="done-h" className="t-h3">
            Already up, unsold or withdrawn
          </h2>
          <div className="scroll" style={{ overflowX: 'auto' }}>
            <table className="tbl">
              <thead>
                <tr>
                  <th>Lot</th>
                  <th>Player</th>
                  <th>Status</th>
                  <th>Team</th>
                  <th className="r">Price / base (LKR)</th>
                </tr>
              </thead>
              <tbody>
                {others.map((q) => (
                  <tr key={q.lotId}>
                    <td className="mono muted">{q.lotNumber ?? '—'}</td>
                    <td>
                      <PlayerCell q={q} />
                    </td>
                    <td>
                      <StatusChip tone={STATE_TONE[q.state]}>{STATE_LABEL[q.state]}</StatusChip>
                    </td>
                    <td>{q.winningTeamName ?? <span className="muted">—</span>}</td>
                    <td className="r">
                      {q.state === 'UNSOLD' ? (
                        <BasePriceCell q={q} defaultPrice={st.defaultBasePrice} editable={canConfigure} />
                      ) : (
                        <Money value={q.finalPrice ?? q.basePrice} />
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <ConfirmDialog
        open={confirmStart}
        onClose={() => setConfirmStart(false)}
        title="Start the auction?"
        confirmLabel="Start auction"
        consequences={
          <>
            <p style={{ margin: 0 }}>
              {data.teams.length} teams each get <Money value={st.initialBudget} />. {data.counts.queued} players are in the queue.
            </p>
            <p style={{ margin: 0 }}>After the start the team budget is fixed; other rule changes need a reason and are audited.</p>
          </>
        }
        onConfirm={() =>
          new Promise<void>((resolve) =>
            cmd.start.mutate(undefined, {
              onSuccess: () => {
                toast.success('The auction is live');
                resolve();
              },
              onError: (e) => {
                toast.error(errorMessage(e));
                resolve();
              },
            }),
          )
        }
      />
    </div>
  );
}

function PlayerCell({ q }: { q: QueueItem }) {
  return (
    <span style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
      {q.photoUrl ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={q.photoUrl} alt="" className={s.thumb} />
      ) : (
        <span className={clsx('ph-person', s.thumb)} aria-hidden="true" />
      )}
      <strong>{q.fullName}</strong>
    </span>
  );
}

function BasePriceCell({ q, defaultPrice, editable }: { q: QueueItem; defaultPrice: number; editable: boolean }) {
  const cmd = useAuctionCommands();
  const toast = useToast();
  const [draft, setDraft] = useState<string | null>(null);
  if (!editable) return <Money value={q.basePrice} />;
  if (draft === null) {
    return (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
        <Money value={q.basePrice} />
        {q.basePriceOverridden && <span className="chip c-pend">Custom</span>}
        <Button size="sm" variant="ghost" onClick={() => setDraft(String(q.basePrice))} aria-label={`Change base price for ${q.fullName}`}>
          Edit
        </Button>
      </span>
    );
  }
  const value = Number(draft.replace(/,/g, ''));
  const valid = /^\d{1,12}$/.test(draft.replace(/,/g, '')) && value > 0;
  const save = (price: number | null) =>
    cmd.basePrice.mutate(
      { entryId: q.entryId, basePrice: price },
      {
        onSuccess: () => {
          setDraft(null);
          toast.success(price === null ? `${q.fullName} uses the default base price` : `Base price saved for ${q.fullName}`);
        },
        onError: (e) => toast.error(errorMessage(e)),
      },
    );
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      <Input
        aria-label={`Base price for ${q.fullName}`}
        inputMode="numeric"
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        style={{ width: 120, minHeight: 36 }}
        aria-invalid={!valid}
        autoFocus
        onKeyDown={(e) => {
          if (e.key === 'Enter' && valid) save(value);
          if (e.key === 'Escape') setDraft(null);
        }}
      />
      <Button size="sm" variant="volt" disabled={!valid} loading={cmd.basePrice.isPending} onClick={() => save(value)}>
        Save
      </Button>
      {q.basePriceOverridden && (
        <Button size="sm" variant="ghost" onClick={() => save(null)} title={`Back to default ${defaultPrice}`}>
          Default
        </Button>
      )}
      <Button size="sm" variant="ghost" onClick={() => setDraft(null)}>
        Cancel
      </Button>
    </span>
  );
}
