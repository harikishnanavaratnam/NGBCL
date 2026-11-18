'use client';

import { useMemo, useState } from 'react';
import { DataTable, type Column } from '@/design-system/data/DataTable';
import { Button } from '@/design-system/primitives/Button';
import { Input, Select } from '@/design-system/primitives/Inputs';
import { Segmented } from '@/design-system/primitives/Tabs';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { useToast } from '@/design-system/primitives/Toast';
import { useCategories } from '@/lib/api/categories';
import { errorMessage } from '@/lib/api/errors';
import { useBulkApprove, usePlayerRegistrations, type PlayerRegistrationRow, type RegistrationStatus } from '@/lib/api/registration';
import { useAuth } from '@/lib/auth/session';
import { formatDateTime } from '@/lib/format/dates';
import { PlayerReviewDrawer } from './PlayerReviewDrawer';
import { InboxHeader, STATUS_OPTIONS, queueAge, useDebounced, useInboxKeys } from './shared';
import s from './Registrations.module.css';

export function PlayerInbox({ duplicatesOnly = false }: { duplicatesOnly?: boolean }) {
  const { can } = useAuth();
  const toast = useToast();
  const [status, setStatus] = useState<RegistrationStatus | 'ALL'>('PENDING');
  const [q, setQ] = useState('');
  const [dupOnly, setDupOnly] = useState(duplicatesOnly);
  const [categoryId, setCategoryId] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [active, setActive] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const query = useDebounced(q);
  const { data: cats } = useCategories();

  const filter = {
    status: status === 'ALL' ? undefined : status,
    q: query || undefined,
    duplicate: dupOnly ? true : undefined,
    categoryId: categoryId || undefined,
    page,
    size,
  };
  const { data, isLoading, error, refetch, isFetching } = usePlayerRegistrations(filter);
  const rows = data?.content;
  const bulk = useBulkApprove();
  const canReview = can('REGISTRATION_REVIEW');

  const move = (delta: number) => {
    if (!rows?.length) return;
    const ids = rows.map((r) => r.id);
    const cur = openId ?? active;
    const idx = cur ? ids.indexOf(cur) : -1;
    const next = ids[Math.min(ids.length - 1, Math.max(0, idx + delta))];
    setActive(next);
    if (openId) setOpenId(next);
  };

  const keys = useMemo(
    () => ({
      j: () => move(1),
      k: () => move(-1),
      enter: () => active && setOpenId(active),
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [rows, active, openId],
  );
  useInboxKeys(keys, !openId);

  const columns: Column<PlayerRegistrationRow>[] = [
    {
      key: 'ref',
      header: 'Reference',
      cell: (r) => (
        <span className="mono" style={{ whiteSpace: 'nowrap' }}>
          {r.registrationNumber}
        </span>
      ),
    },
    {
      key: 'photo',
      header: 'Photo',
      cell: (r) =>
        r.photoPreviewUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img className={s.thumb} src={r.photoPreviewUrl} alt="" loading="lazy" />
        ) : (
          <span className={s.thumb} />
        ),
    },
    { key: 'name', header: 'Name', cell: (r) => <strong>{r.fullName}</strong> },
    { key: 'cat', header: 'Category', cell: (r) => r.categoryName ?? <span className="muted">Not selected</span> },
    { key: 'area', header: 'Home area', cell: (r) => r.homeArea ?? <span className="muted">—</span> },
    { key: 'sub', header: 'Submitted', cell: (r) => <span className="mono muted">{formatDateTime(r.submittedAt)}</span> },
    {
      key: 'age',
      header: 'In queue',
      cell: (r) => (r.status === 'PENDING' ? <span className="mono">{queueAge(r.submittedAt)}</span> : <span className="muted">—</span>),
    },
    {
      key: 'flags',
      header: 'Flags',
      cell: (r) => (
        <span className={s.flags}>
          {r.duplicateFlag && <StatusChip tone="error">Possible duplicate</StatusChip>}
          {r.status === 'PENDING' && r.photoStatus !== 'VERIFIED' && (
            <StatusChip tone={r.photoStatus === 'REJECTED' ? 'error' : 'pending'}>
              {r.photoStatus === 'REJECTED' ? 'Photo rejected' : 'Photo unverified'}
            </StatusChip>
          )}
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      cell: (r) => <StatusChip status={r.status}>{r.status.charAt(0) + r.status.slice(1).toLowerCase()}</StatusChip>,
    },
  ];

  const selectedRows = rows?.filter((r) => selected.has(r.id)) ?? [];
  const verifiedSelected = selectedRows.filter((r) => r.photoStatus === 'VERIFIED' && !r.duplicateFlag && r.status === 'PENDING');

  return (
    <div className={s.page}>
      <InboxHeader tab={duplicatesOnly ? 'duplicates' : 'players'} />
      <section className="card" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div className={s.filters}>
          <Segmented
            label="Status"
            value={status}
            onChange={(v) => {
              setStatus(v);
              setPage(0);
              setSelected(new Set());
            }}
            options={STATUS_OPTIONS}
          />
          {!duplicatesOnly && (
            <button type="button" className={s.chipBtn} aria-pressed={dupOnly} onClick={() => (setDupOnly(!dupOnly), setPage(0))}>
              Possible duplicates
            </button>
          )}
          {cats && cats.length > 0 && (
            <Select aria-label="Category" value={categoryId} onChange={(e) => (setCategoryId(e.target.value), setPage(0))}>
              <option value="">All categories</option>
              {cats.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          )}
          <Input
            type="search"
            placeholder="Search name, email, phone or reference"
            aria-label="Search"
            value={q}
            onChange={(e) => (setQ(e.target.value), setPage(0))}
            style={{ marginLeft: 'auto' }}
          />
        </div>
        <DataTable
          caption="Player registrations"
          columns={columns}
          rows={rows}
          rowKey={(r) => r.id}
          loading={isLoading}
          error={error}
          onRetry={() => void refetch()}
          activeKey={active ?? undefined}
          onRowClick={(r) => {
            setActive(r.id);
            setOpenId(r.id);
          }}
          selected={canReview && status === 'PENDING' ? selected : undefined}
          onSelect={canReview && status === 'PENDING' ? setSelected : undefined}
          bulkBar={
            <>
              <Button
                variant="ok"
                size="sm"
                loading={bulk.isPending}
                disabled={verifiedSelected.length === 0}
                onClick={async () => {
                  try {
                    const res = await bulk.mutateAsync(verifiedSelected.map((r) => r.id));
                    setSelected(new Set());
                    toast.success(`${res.approved.length} approved${res.skipped.length ? ` · ${res.skipped.length} skipped` : ''}`);
                    res.skipped.forEach((sk) => toast.error(`${sk.registrationNumber ?? sk.id}: ${sk.message}`));
                  } catch (e) {
                    toast.error(errorMessage(e));
                  }
                }}
              >
                Approve verified ({verifiedSelected.length})
              </Button>
              <Button variant="out" size="sm" onClick={() => setSelected(new Set())}>
                Clear
              </Button>
            </>
          }
          empty={{
            title: status === 'PENDING' ? (dupOnly ? 'No possible duplicates' : 'Inbox zero') : 'Nothing here',
            description:
              status === 'PENDING'
                ? 'New registrations appear here as soon as they are submitted.'
                : 'Try another status or clear the search.',
          }}
          pagination={
            data
              ? {
                  page: data.page,
                  size: data.size,
                  totalElements: data.totalElements,
                  totalPages: data.totalPages,
                  onPage: setPage,
                  onSize: (n) => (setSize(n), setPage(0)),
                }
              : undefined
          }
        />
        {selected.size > 0 && <span className="help">Unverified photos and unresolved duplicates are skipped by bulk approve.</span>}
        {isFetching && !isLoading && (
          <span className="help" aria-live="polite">
            Refreshing…
          </span>
        )}
      </section>
      <PlayerReviewDrawer
        id={openId}
        onClose={() => setOpenId(null)}
        onPrev={() => move(-1)}
        onNext={() => move(1)}
        canReview={canReview}
      />
    </div>
  );
}
