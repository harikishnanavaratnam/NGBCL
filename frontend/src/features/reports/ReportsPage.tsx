'use client';

import { useState } from 'react';
import { EmptyState } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input } from '@/design-system/primitives/Inputs';
import { useToast } from '@/design-system/primitives/Toast';
import { errorMessage } from '@/lib/api/errors';
import { fetchReport, type ReportKind } from '@/lib/api/standings';
import { useAuth } from '@/lib/auth/session';
import type { Permission } from '@/lib/auth/permissions';
import { downloadCsv, downloadXlsx } from './download';

interface Def {
  kind: ReportKind;
  title: string;
  description: string;
  csv: string | null;
  needs: Permission;
}

const REPORTS: Def[] = [
  {
    kind: 'auction',
    title: 'Auction workbook',
    description:
      'Team budgets, one sheet per team’s squad with prices, every player and their auction status, the sales log, every bid and budget adjustments.',
    csv: null,
    needs: 'REPORT_VIEW',
  },
  {
    kind: 'players',
    title: 'Players master list',
    description: 'Every registration with contact details, status, skill category, auction state, base price, team and price paid.',
    csv: 'CSV',
    needs: 'REPORT_VIEW',
  },
  {
    kind: 'results',
    title: 'Results sheet',
    description: 'Every match with players, game scores, winner, outcome, scorer and who confirmed it — plus a sheet of tie results.',
    csv: 'CSV (matches)',
    needs: 'REPORT_VIEW',
  },
  {
    kind: 'standings',
    title: 'Standings',
    description: 'Group tables with all the tie-breaker columns. For a printable PDF use Results › Standings › Print.',
    csv: 'CSV',
    needs: 'REPORT_VIEW',
  },
];

/** Admin › System › Reports (RPT-001..006): Excel workbooks and CSV files built from live data. */
export function ReportsPage() {
  const { can } = useAuth();
  const visible = REPORTS.filter((r) => can(r.needs));
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <nav aria-label="Breadcrumb" className="t-caption">
          System / Reports
        </nav>
        <h1 className="t-h1">Reports</h1>
        <p className="muted t-body-s">
          Each download is built from the data as it is right now. Files with contact details are private — store and share them carefully.
        </p>
      </div>
      {visible.length === 0 && !can('AUDIT_VIEW') ? (
        <EmptyState title="No reports for your role" description="Reports need the report permission. Ask a Tournament Admin." />
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))', gap: 16 }}>
          {visible.map((r) => (
            <ReportCard key={r.kind} def={r} />
          ))}
          {can('AUDIT_VIEW') && <AuditCard />}
        </div>
      )}
    </div>
  );
}

function useDownload() {
  const toast = useToast();
  const [busy, setBusy] = useState<string | null>(null);
  const run = async (key: string, kind: ReportKind, as: 'xlsx' | 'csv', query?: Record<string, string | undefined>) => {
    setBusy(key);
    try {
      const report = await fetchReport(kind, query);
      if (report.sheets.every((s) => s.rows.length === 0))
        toast.show({ tone: 'info', message: `${report.title}: nothing to export yet — the file has headings only.` });
      if (as === 'xlsx') await downloadXlsx(report);
      else downloadCsv(report);
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(null);
    }
  };
  return { busy, run };
}

function ReportCard({ def }: { def: Def }) {
  const { busy, run } = useDownload();
  return (
    <section className="card" style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 12 }} aria-labelledby={`r-${def.kind}`}>
      <h2 id={`r-${def.kind}`} className="t-h3" style={{ margin: 0 }}>
        {def.title}
      </h2>
      <p className="t-body-s muted" style={{ margin: 0, flex: 1 }}>
        {def.description}
      </p>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <Button
          variant="volt"
          size="sm"
          loading={busy === 'xlsx'}
          disabled={busy !== null}
          onClick={() => void run('xlsx', def.kind, 'xlsx')}
        >
          Excel (.xlsx)
        </Button>
        {def.csv && (
          <Button
            variant="out"
            size="sm"
            loading={busy === 'csv'}
            disabled={busy !== null}
            onClick={() => void run('csv', def.kind, 'csv')}
          >
            {def.csv}
          </Button>
        )}
      </div>
    </section>
  );
}

function AuditCard() {
  const { busy, run } = useDownload();
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const q = { from: from || undefined, to: to || undefined };
  return (
    <section className="card" style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 12 }} aria-labelledby="r-audit">
      <h2 id="r-audit" className="t-h3" style={{ margin: 0 }}>
        Audit log
      </h2>
      <p className="t-body-s muted" style={{ margin: 0 }}>
        Every change with who made it, when, the reason and the before/after values. Newest first, up to 20,000 rows.
      </p>
      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <Field label="From" optional>
          {({ id }) => <Input id={id} type="date" value={from} onChange={(e) => setFrom(e.target.value)} />}
        </Field>
        <Field label="To" optional>
          {({ id }) => <Input id={id} type="date" value={to} onChange={(e) => setTo(e.target.value)} />}
        </Field>
      </div>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <Button
          variant="volt"
          size="sm"
          loading={busy === 'xlsx'}
          disabled={busy !== null}
          onClick={() => void run('xlsx', 'audit', 'xlsx', q)}
        >
          Excel (.xlsx)
        </Button>
        <Button
          variant="out"
          size="sm"
          loading={busy === 'csv'}
          disabled={busy !== null}
          onClick={() => void run('csv', 'audit', 'csv', q)}
        >
          CSV
        </Button>
      </div>
    </section>
  );
}
