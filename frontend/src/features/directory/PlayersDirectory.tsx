'use client';

import { useState } from 'react';
import { DataTable, type Column } from '@/design-system/data/DataTable';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input, Select } from '@/design-system/primitives/Inputs';
import { Modal } from '@/design-system/primitives/Overlay';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { Segmented } from '@/design-system/primitives/Tabs';
import { useToast } from '@/design-system/primitives/Toast';
import { useCategories } from '@/lib/api/categories';
import { usePlayerCategory, usePlayerDirectory, usePlayerStatus, type PlayerRow, type TournamentPlayerStatus } from '@/lib/api/directory';
import { errorMessage } from '@/lib/api/errors';
import { formatLkPhone } from '@/lib/api/registration';
import { useAuth } from '@/lib/auth/session';
import { splitName } from '@/lib/format/names';
import { useDebounced } from '../registrations/shared';
import s from '../registrations/Registrations.module.css';

const STATUS_LABEL: Record<TournamentPlayerStatus, string> = { APPROVED: 'Approved', WITHDRAWN: 'Withdrawn', INACTIVE: 'Inactive' };

export function PlayersDirectory() {
  const { can } = useAuth();
  const toast = useToast();
  const canManage = can('DIRECTORY_MANAGE');
  const [status, setStatus] = useState<TournamentPlayerStatus | 'ALL'>('ALL');
  const [categoryId, setCategoryId] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [change, setChange] = useState<{ row: PlayerRow; to: TournamentPlayerStatus; reason: string } | null>(null);
  const query = useDebounced(q);
  const { data: cats } = useCategories();
  const { data, isLoading, error, refetch } = usePlayerDirectory({
    status: status === 'ALL' ? undefined : status,
    categoryId: categoryId || undefined,
    q: query || undefined,
    page,
    size,
  });
  const setStatusM = usePlayerStatus();
  const setCategory = usePlayerCategory();

  const columns: Column<PlayerRow>[] = [
    {
      key: 'player',
      header: 'Player',
      cell: (r) => {
        const n = splitName(r.fullName);
        return (
          <span style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            {r.photoUrl ? <img className={s.thumb} src={r.photoUrl} alt="" loading="lazy" /> : <span className={s.thumb} />}
            <span>
              {n.first} <strong style={{ textTransform: 'uppercase' }}>{n.last}</strong>
            </span>
          </span>
        );
      },
    },
    {
      key: 'cat',
      header: 'Category',
      cell: (r) =>
        canManage && cats ? (
          <Select
            aria-label={`Category for ${r.fullName}`}
            value={r.categoryId ?? ''}
            onClick={(e) => e.stopPropagation()}
            onChange={(e) =>
              setCategory.mutateAsync({ id: r.id, categoryId: e.target.value || null, version: r.version }).then(
                () => toast.success('Category updated'),
                (err) => toast.error(errorMessage(err)),
              )
            }
            style={{ minWidth: 140 }}
          >
            <option value="">Not set</option>
            {cats
              .filter((c) => c.active || c.id === r.categoryId)
              .map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
          </Select>
        ) : (
          (r.categoryName ?? <span className="muted">—</span>)
        ),
    },
    { key: 'area', header: 'Home area', cell: (r) => r.homeArea ?? <span className="muted">—</span> },
    {
      key: 'contact',
      header: 'Contact (private)',
      cell: (r) => (
        <span style={{ display: 'flex', flexDirection: 'column', fontSize: 13 }}>
          <span>{r.email}</span>
          <span className="muted">{formatLkPhone(r.phone)}</span>
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      cell: (r) => (
        <span title={r.statusReason ?? undefined}>
          <StatusChip status={r.status}>{STATUS_LABEL[r.status]}</StatusChip>
        </span>
      ),
    },
    {
      key: 'actions',
      header: '',
      align: 'right',
      cell: (r) =>
        canManage ? (
          <span style={{ display: 'inline-flex', gap: 6 }} onClick={(e) => e.stopPropagation()}>
            {r.status === 'APPROVED' ? (
              <>
                <Button size="sm" variant="out" onClick={() => setChange({ row: r, to: 'WITHDRAWN', reason: '' })}>
                  Withdraw…
                </Button>
                <Button size="sm" variant="ghost" onClick={() => setChange({ row: r, to: 'INACTIVE', reason: '' })}>
                  Deactivate…
                </Button>
              </>
            ) : (
              <Button size="sm" variant="out" onClick={() => setChange({ row: r, to: 'APPROVED', reason: '' })}>
                Reactivate
              </Button>
            )}
          </span>
        ) : null,
    },
  ];

  return (
    <div className={s.page}>
      <div className={s.head}>
        <div className={s.titleRow}>
          <span className="eyebrow">Directory</span>
          <h1 className="t-h1">Players</h1>
          <span className="muted">Approved players in NGBCL 1.0. Pending ones live in Registrations.</span>
        </div>
      </div>
      <section className="card" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div className={s.filters}>
          <Segmented
            label="Status"
            value={status}
            onChange={(v) => (setStatus(v), setPage(0))}
            options={[
              { value: 'ALL', label: 'All' },
              { value: 'APPROVED', label: 'Approved' },
              { value: 'WITHDRAWN', label: 'Withdrawn' },
              { value: 'INACTIVE', label: 'Inactive' },
            ]}
          />
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
            placeholder="Search name, email or phone"
            aria-label="Search players"
            value={q}
            onChange={(e) => (setQ(e.target.value), setPage(0))}
            style={{ marginLeft: 'auto' }}
          />
        </div>
        <DataTable
          caption="Players"
          columns={columns}
          rows={data?.content}
          rowKey={(r) => r.id}
          loading={isLoading}
          error={error}
          onRetry={() => void refetch()}
          empty={{
            title: 'No players yet',
            description: 'Players appear here once their registration is approved.',
            action: { label: 'Open registrations', href: '/admin/registrations/players' },
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
        <span className="help">Nothing is deleted. Withdrawn and inactive players keep their history.</span>
      </section>

      <Modal
        open={change !== null}
        onClose={() => setChange(null)}
        title={
          change
            ? `${change.to === 'APPROVED' ? 'Reactivate' : change.to === 'WITHDRAWN' ? 'Withdraw' : 'Deactivate'} ${change.row.fullName}?`
            : ''
        }
        footer={
          <>
            <Button variant="out" onClick={() => setChange(null)}>
              Cancel
            </Button>
            <Button
              variant={change?.to === 'APPROVED' ? 'volt' : 'danger'}
              loading={setStatusM.isPending}
              disabled={change?.to !== 'APPROVED' && !change?.reason.trim()}
              onClick={async () => {
                if (!change) return;
                try {
                  await setStatusM.mutateAsync({
                    id: change.row.id,
                    status: change.to,
                    reason: change.reason.trim() || undefined,
                    version: change.row.version,
                  });
                  toast.success('Player updated');
                  setChange(null);
                } catch (e) {
                  toast.error(errorMessage(e));
                }
              }}
            >
              Confirm
            </Button>
          </>
        }
      >
        {change && change.to !== 'APPROVED' ? (
          <Field label="Reason" required help="Kept in the player’s history and the audit log.">
            {(a) => (
              <Input
                id={a.id}
                value={change.reason}
                maxLength={300}
                onChange={(e) => setChange({ ...change, reason: e.target.value })}
                autoFocus
              />
            )}
          </Field>
        ) : (
          <p className="muted" style={{ margin: 0 }}>
            The player returns to the approved list for this edition.
          </p>
        )}
      </Modal>
    </div>
  );
}
