'use client';

import clsx from 'clsx';
import { useState } from 'react';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Input, Select } from '@/design-system/primitives/Inputs';
import { useToast } from '@/design-system/primitives/Toast';
import { teamColor } from '@/design-system/tokens';
import { sideLabel, useFixtureCommands, useFixtures, type Fixture } from '@/lib/api/competition';
import { errorMessage } from '@/lib/api/errors';
import { useAuth } from '@/lib/auth/session';
import { fromZonedInput, toZonedInput } from '@/lib/format/dates';
import s from './Competition.module.css';

const DAY_START = 8 * 60;
const DAY_END = 22 * 60;
/** Board rows are 15 minutes, so any quarter-hour start lines up. */
const STEP = 15;

/**
 * Admin › Competition › Schedule board: one day, courts across, time down. Drag a tie from the
 * list (or the board) onto a slot; the API refuses court, team or player clashes (FIX-009).
 */
export function ScheduleBoard() {
  const { data, isLoading, error, refetch } = useFixtures();
  const { can } = useAuth();
  const canEdit = can('FIXTURE_MANAGE');
  const cmd = useFixtureCommands();
  const toast = useToast();
  const [day, setDay] = useState<string | null>(null);
  const [stage, setStage] = useState('');
  const [over, setOver] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);

  if (isLoading) return <Skeleton height={480} />;
  if (error || !data) return <ErrorState title="Couldn’t load the schedule" message={errorMessage(error)} onRetry={() => void refetch()} />;
  if (data.fixtures.length === 0) {
    return <EmptyState title="No fixtures yet" description="Generate fixtures first, then place them on the board." action={{ label: 'Format and draw', href: '/admin/competition/format' }} />;
  }

  const slot = data.matchMinutes + data.restMinutes;
  const courts = data.courts.filter((c) => c.active);
  const days = [...new Set(data.fixtures.filter((f) => f.scheduledAt).map((f) => toZonedInput(f.scheduledAt).slice(0, 10)))].sort();
  const today = toZonedInput(new Date().toISOString()).slice(0, 10);
  const shownDay = day ?? days[0] ?? today;
  const rows = Math.ceil((DAY_END - DAY_START) / STEP);
  const unscheduled = data.fixtures.filter((f) => !f.scheduledAt && (!stage || f.stageId === stage));
  const onDay = data.fixtures.filter((f) => f.scheduledAt && toZonedInput(f.scheduledAt).startsWith(shownDay));

  const timeOf = (row: number) => {
    const m = DAY_START + row * STEP;
    return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
  };

  const place = (fixtureId: string, row: number, courtId: string) => {
    const f = data.fixtures.find((x) => x.id === fixtureId);
    if (!f) return;
    cmd.schedule.mutate(
      { id: f.id, scheduledAt: fromZonedInput(`${shownDay}T${timeOf(row)}`), courtId, version: f.version },
      { onSuccess: () => toast.success(`Tie #${f.number} placed at ${timeOf(row)}`), onError: (e) => toast.error(errorMessage(e)) },
    );
  };

  const rowOf = (f: Fixture) => {
    const t = toZonedInput(f.scheduledAt).slice(11);
    const mins = Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5));
    return Math.max(0, Math.floor((mins - DAY_START) / STEP));
  };

  const card = (f: Fixture, style?: React.CSSProperties) => (
    <div
      key={f.id}
      className={s.card}
      draggable={canEdit && f.status !== 'LIVE' && f.status !== 'COMPLETED'}
      onDragStart={(e) => {
        e.dataTransfer.setData('text/plain', f.id);
        setDragging(true);
      }}
      onDragEnd={() => {
        setDragging(false);
        setOver(null);
      }}
      style={{ borderLeftColor: teamColor(f.teamA?.colorSlot), pointerEvents: dragging ? 'none' : undefined, ...style }}
      title={f.warnings.join(' · ')}
    >
      <strong>
        #{f.number} {f.groupName ?? f.roundName}
      </strong>
      <span>{sideLabel(f.teamA, f.sourceA)}</span>
      <span>v {sideLabel(f.teamB, f.sourceB)}</span>
      {f.scheduledAt && <span className="muted">{toZonedInput(f.scheduledAt).slice(11)} · {f.matches.length} matches</span>}
    </div>
  );

  return (
    <div className={s.page}>
      <div className={s.head}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <nav aria-label="Breadcrumb" className="t-caption">
            Competition / Schedule board
          </nav>
          <h1 className="t-h1">Schedule board</h1>
          <p className="muted t-body-s" style={{ margin: 0 }}>
            Drag a tie onto a court and start time (15-minute rows). A tie fills its matches back to back, {slot} min each.
          </p>
        </div>
        <Button href="/admin/competition/fixtures" variant="out">
          Fixtures list
        </Button>
      </div>
      <div className={s.toolbar}>
        <Input type="date" aria-label="Day" value={shownDay} onChange={(e) => setDay(e.target.value || null)} style={{ maxWidth: 200 }} />
        {days.map((d) => (
          <Button key={d} size="sm" variant={d === shownDay ? 'volt' : 'out'} onClick={() => setDay(d)}>
            {d.slice(5)}
          </Button>
        ))}
      </div>
      <div className={s.boardWrap}>
        <aside className="card" style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 10 }} aria-label="Unscheduled ties">
          <strong>Not scheduled ({unscheduled.length})</strong>
          <Select aria-label="Stage" value={stage} onChange={(e) => setStage(e.target.value)}>
            <option value="">All stages</option>
            {data.stages.map((x) => (
              <option key={x.id} value={x.id}>
                {x.name}
              </option>
            ))}
          </Select>
          <div className={s.pool}>
            {unscheduled.map((f) => card(f))}
            {unscheduled.length === 0 && <span className="muted t-body-s">Every tie has a slot.</span>}
          </div>
        </aside>
        {courts.length === 0 ? (
          <EmptyState title="No courts in use" description="Add courts on the Format page." />
        ) : (
          <div className={s.board} style={{ gridTemplateColumns: `72px repeat(${courts.length}, minmax(160px, 1fr))`, gridTemplateRows: `auto repeat(${rows}, 22px)` }} role="grid" aria-label={`Schedule for ${shownDay}`}>
            <div className={s.cellHead} style={{ gridRow: 1, gridColumn: 1 }} />
            {courts.map((c, ci) => (
              <div key={c.id} className={s.cellHead} style={{ gridRow: 1, gridColumn: ci + 2 }}>
                {c.name}
              </div>
            ))}
            {Array.from({ length: rows }, (_, r) => (
              <div key={`t${r}`} className={s.timeHead} style={{ gridRow: r + 2, gridColumn: 1 }}>
                {(r * STEP) % 60 === 0 ? timeOf(r) : ''}
              </div>
            ))}
            {Array.from({ length: rows }, (_, r) =>
              courts.map((c, ci) => {
                const key = `${r}-${c.id}`;
                return (
                  <div
                    key={key}
                    className={clsx(s.cell, over === key && s.cellOver)}
                    style={{ gridRow: r + 2, gridColumn: ci + 2 }}
                    onDragOver={(e) => {
                      if (!canEdit) return;
                      e.preventDefault();
                      setOver(key);
                    }}
                    onDragLeave={() => setOver((o) => (o === key ? null : o))}
                    onDrop={(e) => {
                      e.preventDefault();
                      setOver(null);
                      setDragging(false);
                      const id = e.dataTransfer.getData('text/plain');
                      if (id) place(id, r, c.id);
                    }}
                  />
                );
              }),
            )}
            {onDay.map((f) => {
              const ci = courts.findIndex((c) => c.id === f.courtId);
              if (ci < 0) return null;
              return card(f, { gridRow: `${rowOf(f) + 2} / span ${Math.max(1, Math.ceil((f.matches.length * slot) / STEP))}`, gridColumn: ci + 2, margin: 2, zIndex: 2, overflow: 'hidden' });
            })}
          </div>
        )}
      </div>
    </div>
  );
}
