'use client';

import { useState } from 'react';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { ConfirmDialog } from '@/design-system/primitives/Overlay';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { useToast } from '@/design-system/primitives/Toast';
import { errorMessage } from '@/lib/api/errors';
import { fetchReport, useAdminStandings, useApplySeeding, useSeeding, type Seeding } from '@/lib/api/standings';
import { useAuth } from '@/lib/auth/session';
import { formatDateTime } from '@/lib/format/dates';
import { downloadXlsx } from '../reports/download';
import { GroupHead, StandingsTable, TIEBREAKER_LABEL } from './StandingsTable';

/** Admin › Results › Standings: live tables, print/PDF and Excel, and filling the knockout from final tables. */
export function AdminStandings() {
  const { can } = useAuth();
  const { data, isLoading, error, refetch } = useAdminStandings();
  const seeding = useSeeding(can('STANDINGS_MANAGE', 'FIXTURE_MANAGE'));
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  const excel = async () => {
    setBusy(true);
    try {
      await downloadXlsx(await fetchReport('standings'));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 16, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, flex: 1, minWidth: 260 }}>
          <nav aria-label="Breadcrumb" className="t-caption no-print">
            Results / Standings
          </nav>
          <h1 className="t-h1">Standings</h1>
          <p className="muted t-body-s" style={{ margin: 0 }}>
            Confirmed results only · printed {formatDateTime(new Date())}
          </p>
        </div>
        <div className="no-print" style={{ display: 'flex', gap: 8 }}>
          <Button variant="out" onClick={() => window.print()}>
            Print / save as PDF
          </Button>
          {can('REPORT_VIEW') && (
            <Button variant="out" loading={busy} onClick={() => void excel()}>
              Excel
            </Button>
          )}
        </div>
      </div>

      {isLoading ? (
        <Skeleton height={320} />
      ) : error || !data ? (
        <ErrorState message={errorMessage(error)} onRetry={() => void refetch()} />
      ) : data.groups.length === 0 ? (
        <EmptyState
          title="No tables yet"
          description="Tables appear once group or league fixtures are generated."
          action={{ label: 'Fixtures', href: '/admin/competition/fixtures' }}
        />
      ) : (
        <>
          {data.groups.map((g) => (
            <section key={g.name} className="card print-break" style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 12 }}>
              <GroupHead g={g} />
              <StandingsTable g={g} linkTeams={false} />
            </section>
          ))}
          <p className="help" style={{ margin: 0 }}>
            {data.rules.pointsWin} points for a win, {data.rules.pointsDraw} for a draw, {data.rules.pointsLoss} for a loss. Level teams are
            split by {data.rules.tiebreakers.map((x) => TIEBREAKER_LABEL[x].toLowerCase()).join(', then ') || 'name'}. Change this in
            Settings › Standings.
          </p>
        </>
      )}

      {seeding.data && seeding.data.slots.length > 0 && <SeedingPanel seeding={seeding.data} canApply={can('FIXTURE_MANAGE')} />}
    </div>
  );
}

function SeedingPanel({ seeding, canApply }: { seeding: Seeding; canApply: boolean }) {
  const apply = useApplySeeding();
  const toast = useToast();
  const [confirm, setConfirm] = useState(false);
  return (
    <section
      className="card no-print"
      style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 14 }}
      aria-labelledby="seed-title"
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <h2 id="seed-title" className="t-h2" style={{ margin: 0 }}>
          Knockout places
        </h2>
        {seeding.pendingChanges > 0 ? (
          <StatusChip tone="pending">{seeding.pendingChanges} to fill</StatusChip>
        ) : (
          <StatusChip tone="neutral">{seeding.allGroupsComplete ? 'Up to date' : 'Waiting for groups'}</StatusChip>
        )}
      </div>
      <p className="t-body-s muted" style={{ margin: 0 }}>
        When a group is finished its places are proposed here. Check them, then confirm — the ties update privately; publish fixtures to
        show them on the public site.
      </p>
      <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column' }}>
        {seeding.slots.map((s) => (
          <li
            key={`${s.fixtureId}-${s.side}`}
            style={{
              display: 'grid',
              gridTemplateColumns: 'minmax(140px, 1fr) minmax(140px, 1fr) minmax(0, 2fr)',
              gap: 12,
              padding: '10px 0',
              borderBottom: '1px solid var(--line)',
              alignItems: 'center',
            }}
          >
            <span>
              <span className="mono muted">#{s.fixtureNumber}</span> {s.roundName} · side {s.side}
              <br />
              <span className="t-caption">{s.source}</span>
            </span>
            <span>
              {s.proposedTeam ? <strong>{s.proposedTeam.name}</strong> : <span className="muted">Not decided</span>}
              {s.currentTeam && s.currentTeam.slug !== s.proposedTeam?.slug && (
                <span className="t-caption"> (now {s.currentTeam.name})</span>
              )}
            </span>
            <span className="t-body-s" style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
              {s.changes ? (
                <StatusChip tone="pending">Will change</StatusChip>
              ) : s.ready && !s.locked ? (
                <StatusChip tone="ok">Set</StatusChip>
              ) : null}
              {s.locked && <StatusChip tone="neutral">Started</StatusChip>}
              {s.note && <span className="muted">{s.note}</span>}
            </span>
          </li>
        ))}
      </ul>
      {canApply && (
        <div>
          <Button variant="volt" disabled={seeding.pendingChanges === 0} loading={apply.isPending} onClick={() => setConfirm(true)}>
            Confirm knockout places
          </Button>
        </div>
      )}
      <ConfirmDialog
        open={confirm}
        onClose={() => setConfirm(false)}
        title="Put these teams into the knockout?"
        consequences={
          <ul style={{ margin: 0, paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 6 }}>
            <li>
              {seeding.pendingChanges} place{seeding.pendingChanges === 1 ? '' : 's'} will be filled from the final group tables.
            </li>
            <li>Line-ups already picked for a side that changes are cleared.</li>
            <li>Nothing changes on the public site until you publish the fixtures.</li>
          </ul>
        }
        confirmLabel="Confirm places"
        onConfirm={async () => {
          try {
            const r = await apply.mutateAsync();
            toast.success(`${r.tiesUpdated} knockout tie${r.tiesUpdated === 1 ? '' : 's'} updated — publish fixtures to show them.`);
          } catch (e) {
            toast.error(errorMessage(e));
          }
        }}
      />
    </section>
  );
}
