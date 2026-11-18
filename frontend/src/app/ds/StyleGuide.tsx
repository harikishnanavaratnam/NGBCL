'use client';

import { useState, type ReactNode } from 'react';
import { DataTable, type SortState } from '@/design-system/data/DataTable';
import { AuditTimeline } from '@/design-system/data/AuditTimeline';
import { EmptyState, ErrorState, SkeletonRows, StatBlock } from '@/design-system/data/States';
import { ConnectionPill, PendingValue, type Pending } from '@/design-system/live/Live';
import { CountUp } from '@/design-system/motion/CountUp';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Checkbox, Input, PhoneInput, Select, Textarea, Toggle } from '@/design-system/primitives/Inputs';
import { ConfirmDialog, Drawer, Modal } from '@/design-system/primitives/Overlay';
import { RadioCards } from '@/design-system/primitives/RadioCard';
import { StatusChip, type ChipTone } from '@/design-system/primitives/StatusChip';
import { Segmented, Tabs } from '@/design-system/primitives/Tabs';
import { CourtDivider, Money, Mono, Score, Tbc } from '@/design-system/primitives/Text';
import { useToast } from '@/design-system/primitives/Toast';
import { BudgetMeter, SquadSlots } from '@/design-system/sport/Budget';
import { CrestTile } from '@/design-system/sport/CrestTile';
import { PlayerCard } from '@/design-system/sport/PlayerCard';
import { GameScoreTiles, LotStatusBar, type LotState } from '@/design-system/sport/Scores';
import { TEAM_COLORS } from '@/design-system/tokens';
import { formatLkr } from '@/lib/format/money';

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section id={id} style={{ display: 'flex', flexDirection: 'column', gap: 20, padding: '40px 0', borderTop: '1px solid var(--line)' }}>
      <h2 className="t-h2">{title}</h2>
      {children}
    </section>
  );
}

const row = { display: 'flex', flexWrap: 'wrap' as const, gap: 12, alignItems: 'center' };

const SWATCHES = [
  ['--bg', '#101318'],
  ['--bg-2', '#191F27'],
  ['--surface', '#222A34'],
  ['--surface-2', '#2B3440'],
  ['--lime', '#D8FF42'],
  ['--gold', '#C9A45C'],
  ['--gold-2', '#D6B877'],
  ['--text', '#F5F7FA'],
  ['--text-2', '#A5ADB8'],
  ['--ok', '#5CCF95'],
  ['--warn', '#F0B455'],
  ['--err', '#FF7178'],
  ['--info', '#82AEFF'],
  ['--live-dot', '#FF5A62'],
];

const TONES: ChipTone[] = ['live', 'sold', 'gold', 'neutral', 'ok', 'pending', 'error', 'provisional', 'navy'];

interface DemoRow {
  ref: string;
  name: string;
  category: string;
  status: string;
  base: number;
}
const DEMO_ROWS: DemoRow[] = [
  { ref: 'PLY-2026-00001', name: '[Player A]', category: '[Category]', status: 'PENDING', base: 10000 },
  { ref: 'PLY-2026-00002', name: '[Player B]', category: '[Category]', status: 'APPROVED', base: 15000 },
  { ref: 'PLY-2026-00003', name: '[Player C]', category: '—', status: 'REJECTED', base: 10000 },
];

/** Living style guide — mirrors the export's DSFoundations / DSComponents boards. */
export function StyleGuide() {
  const toast = useToast();
  const [tab, setTab] = useState<'players' | 'teams'>('players');
  const [seg, setSeg] = useState<'list' | 'board' | 'bracket'>('list');
  const [cat, setCat] = useState<string | null>('b');
  const [modal, setModal] = useState(false);
  const [drawer, setDrawer] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const [bid, setBid] = useState(125000);
  const [pending, setPending] = useState<Pending>('accepted');
  const [lot, setLot] = useState<LotState>('OPEN');
  const [sort, setSort] = useState<SortState>({ key: 'ref', dir: 'asc' });
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [tableState, setTableState] = useState<'data' | 'loading' | 'empty' | 'error'>('data');

  const simulateBid = (ok: boolean) => {
    setPending('sending');
    setTimeout(() => {
      if (ok) {
        setBid((b) => b + 5000);
        setPending('accepted');
      } else setPending('rejected');
    }, 700);
  };

  return (
    <div className="wrap" style={{ paddingTop: 48, paddingBottom: 96 }}>
      <header style={{ display: 'flex', flexDirection: 'column', gap: 8, paddingBottom: 24 }}>
        <span className="eyebrow">NGBCL design system v1.0 · internal</span>
        <h1 className="disp t-display-xl">Foundations &amp; components</h1>
        <p className="muted">Ported verbatim from the Claude Design export (ngbcl-ds.css). Every screen is built from these parts.</p>
        <nav style={{ ...row, fontSize: 14 }}>
          {['colour', 'type', 'buttons', 'status', 'forms', 'nav', 'data', 'sport', 'live', 'overlays', 'states'].map((s) => (
            <a key={s} href={`#${s}`} className="link">
              {s}
            </a>
          ))}
        </nav>
      </header>

      <Section id="colour" title="Colour">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 12 }}>
          {SWATCHES.map(([name, hex]) => (
            <div key={name} className="card" style={{ overflow: 'hidden' }}>
              <div style={{ height: 64, background: `var(${name})`, borderBottom: '1px solid var(--line)' }} />
              <div style={{ padding: 10, fontSize: 13 }}>
                <Mono>{name}</Mono>
                <div className="muted">{hex}</div>
              </div>
            </div>
          ))}
        </div>
        <span className="eyebrow">Team palette — 4 px band, crest ring, chart series only</span>
        <div style={row}>
          {TEAM_COLORS.map((c) => (
            <span key={c.slot} className="chip c-neutral" style={{ boxShadow: `inset 4px 0 0 ${c.hex}`, paddingLeft: 12 }}>
              T{c.slot} {c.name}
            </span>
          ))}
        </div>
      </Section>

      <Section id="type" title="Typography">
        <div className="t-display-xxl">Rule the court</div>
        <div className="t-display-xl">Display XL</div>
        <div className="t-display-l">Display L</div>
        <div style={row}>
          <Score size={96}>21</Score>
          <Score size={96} dim>
            18
          </Score>
          <Score size={48}>3–2</Score>
        </div>
        <div className="t-h1">Heading 1 — admin page titles</div>
        <div className="t-h2">Heading 2 — card and panel titles</div>
        <div className="t-h3">Heading 3 — subsections</div>
        <p className="t-body-l">Body L — public long text. Team owners build their squads through a live player auction.</p>
        <p className="t-body">Body — default UI text.</p>
        <p className="t-body-s">Body S — table cells and helper text.</p>
        <div style={row}>
          <span className="eyebrow">Round 2 · Court 1</span>
          <Mono>PLY-2026-00142</Mono>
          <Money value={1250000} />
          <Money value={300000} variant="public" />
          <Money value={1250000} variant="compact" />
          <Tbc>[VENUE TBC]</Tbc>
        </div>
      </Section>

      <Section id="buttons" title="Buttons">
        <div style={row}>
          <Button variant="volt">Primary</Button>
          <Button variant="gold">Sold</Button>
          <Button variant="out">Secondary</Button>
          <Button variant="ghost">Ghost</Button>
          <Button variant="danger">Reject</Button>
          <Button variant="ok">Confirm</Button>
          <Button variant="volt" loading>
            Saving
          </Button>
          <Button variant="out" disabled>
            Disabled
          </Button>
        </div>
        <div style={row}>
          <Button variant="volt" size="sm">
            Small
          </Button>
          <Button variant="volt">Medium</Button>
          <Button variant="volt" size="lg">
            Place bid
          </Button>
        </div>
      </Section>

      <Section id="status" title="Status chips">
        <div style={row}>
          {TONES.map((t) => (
            <StatusChip key={t} tone={t}>
              {t}
            </StatusChip>
          ))}
        </div>
        <div style={row}>
          {['PENDING', 'APPROVED', 'REJECTED', 'LIVE', 'PROVISIONAL', 'CONFIRMED', 'SOLD', 'UNSOLD', 'WITHDRAWN', 'DRAFT'].map((s) => (
            <StatusChip key={s} status={s} />
          ))}
        </div>
      </Section>

      <Section id="forms" title="Form fields">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 20 }}>
          <Field label="Full name" required help="As it should appear on your player card.">
            {({ id, describedBy }) => <Input id={id} aria-describedby={describedBy} placeholder="e.g. Tharshan Sivakumar" />}
          </Field>
          <Field label="Contact number" required>
            {({ id }) => <PhoneInput id={id} />}
          </Field>
          <Field label="Email" error="Enter a valid email address">
            {({ id, describedBy }) => <Input id={id} aria-describedby={describedBy} aria-invalid defaultValue="tharshan@" />}
          </Field>
          <Field label="Gender">
            {({ id }) => (
              <Select id={id} defaultValue="">
                <option value="" disabled>
                  Select
                </option>
                <option>Male</option>
                <option>Female</option>
              </Select>
            )}
          </Field>
          <Field label="Playing experience" optional help="300 characters max.">
            {({ id }) => <Textarea id={id} maxLength={300} />}
          </Field>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Toggle id="tg1" label="Pin on homepage" defaultChecked />
            <Checkbox label="I am a Jaffna player and the details above are true." />
          </div>
        </div>
        <RadioCards
          name="cat"
          legend="Skill category (optional)"
          value={cat}
          onChange={setCat}
          options={[
            { value: 'a', label: '[Category A]', description: 'Admin-defined description' },
            { value: 'b', label: '[Category B]', description: 'Admin-defined description' },
            { value: 'c', label: '[Category C]', description: 'Admin-defined description' },
          ]}
        />
      </Section>

      <Section id="nav" title="Tabs & segmented">
        <Tabs
          label="Registrations"
          value={tab}
          onChange={setTab}
          items={[
            { value: 'players', label: 'Players', count: 12 },
            { value: 'teams', label: 'Teams', count: 3 },
          ]}
        />
        <Segmented
          label="Fixture view"
          value={seg}
          onChange={setSeg}
          options={[
            { value: 'list', label: 'List' },
            { value: 'board', label: 'Schedule board' },
            { value: 'bracket', label: 'Bracket' },
          ]}
        />
        <CourtDivider />
      </Section>

      <Section id="data" title="Data table">
        <Segmented
          label="Table state"
          value={tableState}
          onChange={setTableState}
          options={[
            { value: 'data', label: 'Data' },
            { value: 'loading', label: 'Loading' },
            { value: 'empty', label: 'Empty' },
            { value: 'error', label: 'Error' },
          ]}
        />
        <DataTable<DemoRow>
          caption="Player registrations"
          rows={tableState === 'empty' ? [] : DEMO_ROWS}
          loading={tableState === 'loading'}
          error={tableState === 'error' ? new Error('The server returned an error (500).') : undefined}
          onRetry={() => setTableState('data')}
          rowKey={(r) => r.ref}
          sort={sort}
          onSort={setSort}
          selected={selected}
          onSelect={setSelected}
          bulkBar={
            <Button size="sm" variant="volt">
              Approve verified
            </Button>
          }
          empty={{ title: 'No registrations yet', description: 'Applications appear here as soon as they are submitted.' }}
          pagination={{ page: 0, size: 25, totalElements: 3, totalPages: 1, onPage: () => {}, onSize: () => {} }}
          columns={[
            { key: 'ref', header: 'Reference', sortable: true, cell: (r) => <Mono>{r.ref}</Mono> },
            { key: 'name', header: 'Name', sortable: true, cell: (r) => r.name },
            { key: 'category', header: 'Category', cell: (r) => r.category },
            { key: 'base', header: 'Base price', align: 'right', cell: (r) => <Money value={r.base} /> },
            { key: 'status', header: 'Status', cell: (r) => <StatusChip status={r.status} /> },
          ]}
        />
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 24 }}>
          <StatBlock label="Pending review" value="—" sub="oldest waiting — days" />
          <AuditTimeline
            entries={[
              { id: '1', actorName: '[User]', action: 'approved player', reference: 'PLY-2026-00002', at: '2026-11-18T08:35:00Z' },
              { id: '2', actorName: '[User]', action: 'corrected budget for', reference: '[Team A]', at: '2026-11-18T09:02:11Z', changes: [{ field: 'remaining', from: 'LKR 400,000', to: 'LKR 380,000' }], reason: 'Duplicate sale reversed' },
            ]}
          />
        </div>
      </Section>

      <Section id="sport" title="Sport components">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: 16 }}>
          <PlayerCard p={{ fullName: 'Tharshan Sivakumar', category: '[Category]', price: 10000, priceLabel: 'Base' }} />
          <PlayerCard p={{ fullName: 'Player Example', category: '[Category]', teamName: '[Team A]', teamColorSlot: 1, price: 120000, priceLabel: 'Sold' }} />
          <PlayerCard variant="admin" p={{ fullName: 'Admin View', status: 'PENDING', reference: 'PLY-2026-00142' }} />
        </div>
        <div style={row}>
          <CrestTile name="[Team A]" code="TMA" colorSlot={1} size="s" showName />
          <CrestTile name="[Team B]" code="TMB" colorSlot={2} size="m" showName />
          <CrestTile name="[Team C]" code="TMC" colorSlot={3} size="l" showName />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 24 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <BudgetMeter initial={1_000_000} spent={620_000} maxBid={200_000} />
            <SquadSlots max={8} filled={5} />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <GameScoreTiles games={[{ a: 21, b: 18 }, { a: 19, b: 21 }, { a: 21, b: 15 }]} />
            <Segmented
              label="Lot state"
              value={lot}
              onChange={setLot}
              options={(['READY', 'OPEN', 'GOING_ONCE', 'GOING_TWICE', 'CLOSED'] as LotState[]).map((v) => ({ value: v, label: v.replace('_', ' ').toLowerCase() }))}
            />
            <LotStatusBar state={lot} />
          </div>
        </div>
      </Section>

      <Section id="live" title="Live states">
        <div style={row}>
          <ConnectionPill state="live" />
          <ConnectionPill state="reconnecting" />
          <ConnectionPill state="offline" />
        </div>
        <div className="card" style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 520 }}>
          <span className="eyebrow">Highest bid</span>
          <span className="num" style={{ fontSize: 72, color: 'var(--lime)', lineHeight: 1 }}>
            <CountUp value={bid} format={(n) => formatLkr(n)} />
          </span>
          <PendingValue state={pending} reason="Exceeds remaining budget">
            <span>[Team A] · {formatLkr(bid)}</span>
          </PendingValue>
          <div style={row}>
            <Button variant="volt" onClick={() => simulateBid(true)}>
              Simulate accepted bid
            </Button>
            <Button variant="danger" onClick={() => simulateBid(false)}>
              Simulate rejected bid
            </Button>
          </div>
        </div>
      </Section>

      <Section id="overlays" title="Overlays & feedback">
        <div style={row}>
          <Button onClick={() => setModal(true)}>Open modal</Button>
          <Button onClick={() => setDrawer(true)}>Open review drawer (720)</Button>
          <Button variant="gold" onClick={() => setConfirm(true)}>
            SOLD (two-step confirm)
          </Button>
          <Button variant="out" onClick={() => toast.success('Sold to [Team A] for Rs. 120,000')}>
            Success toast
          </Button>
          <Button variant="out" onClick={() => toast.error('Bid rejected — exceeds remaining budget')}>
            Error toast
          </Button>
          <Button variant="out" onClick={() => toast.show({ tone: 'live', message: 'Point to Side A · 12–10', action: { label: 'Undo', onClick: () => toast.success('Point undone') } })}>
            Undo toast
          </Button>
        </div>
        <Modal open={modal} onClose={() => setModal(false)} title="Publish schedule" footer={<Button variant="volt" onClick={() => setModal(false)}>Publish</Button>}>
          <p className="muted">A diff of what changes publicly appears here.</p>
        </Modal>
        <Drawer open={drawer} onClose={() => setDrawer(false)} title="PLY-2026-00142 · Review" size={720} footer={<><Button variant="danger">Reject</Button><Button variant="volt">Approve</Button></>}>
          <p className="muted">Submitted data on the left, photo verification and duplicates on the right.</p>
        </Drawer>
        <ConfirmDialog
          open={confirm}
          onClose={() => setConfirm(false)}
          onConfirm={() => new Promise((r) => setTimeout(r, 600))}
          tone="gold"
          title="Sell [Player] to [Team A]?"
          confirmLabel="Confirm SOLD"
          consequences={
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              <li>[Team A] budget: LKR 400,000 → LKR 280,000</li>
              <li>Squad: 4 → 5 of 8</li>
              <li>The sale is recorded in the audit log and shown on the public display.</li>
            </ul>
          }
        />
      </Section>

      <Section id="states" title="Loading, empty and error">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 16 }}>
          <div className="card">
            <SkeletonRows rows={4} cols={3} />
          </div>
          <EmptyState title="No fixtures yet" description="Create the tournament format to generate fixtures." action={{ label: 'Set up format', onClick: () => {} }} />
          <ErrorState message="Can’t reach the server. Check your connection and try again." onRetry={() => {}} />
        </div>
      </Section>
    </div>
  );
}
