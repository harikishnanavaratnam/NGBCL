'use client';

import clsx from 'clsx';
import type { ReactNode } from 'react';
import { Pagination } from '../primitives/Pagination';
import { EmptyState, ErrorState, SkeletonRows } from './States';

export interface Column<T> {
  key: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  align?: 'left' | 'right' | 'center';
  sortable?: boolean;
  width?: number | string;
}

export interface SortState {
  key: string;
  dir: 'asc' | 'desc';
}

interface Props<T> {
  columns: Column<T>[];
  rows: T[] | undefined;
  rowKey: (row: T) => string;
  loading?: boolean;
  error?: unknown;
  onRetry?: () => void;
  empty?: { title: string; description?: ReactNode; action?: { label: string; href?: string; onClick?: () => void } };
  sort?: SortState;
  onSort?: (s: SortState) => void;
  selected?: Set<string>;
  onSelect?: (next: Set<string>) => void;
  bulkBar?: ReactNode;
  onRowClick?: (row: T) => void;
  activeKey?: string;
  pagination?: { page: number; size: number; totalElements: number; totalPages: number; onPage: (p: number) => void; onSize?: (s: number) => void };
  caption?: string;
}

/**
 * Admin data table (brief §5): sticky header, sortable columns, row selection + bulk bar,
 * right-aligned numbers, loading / empty / error states, pagination 25/50/100.
 * Sorting & paging are server-side — the table only reports the user's intent.
 */
export function DataTable<T>({
  columns,
  rows,
  rowKey,
  loading,
  error,
  onRetry,
  empty,
  sort,
  onSort,
  selected,
  onSelect,
  bulkBar,
  onRowClick,
  activeKey,
  pagination,
  caption,
}: Props<T>) {
  if (error) return <ErrorState message={error instanceof Error ? error.message : undefined} onRetry={onRetry} />;
  const selectable = Boolean(onSelect && selected);
  const allKeys = rows?.map(rowKey) ?? [];
  const allOn = selectable && allKeys.length > 0 && allKeys.every((k) => selected!.has(k));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {selectable && selected!.size > 0 && bulkBar && (
        <div className="panel-flat" style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '8px 12px', boxShadow: 'inset 3px 0 0 var(--lime)' }}>
          <strong style={{ fontSize: 14 }}>{selected!.size} selected</strong>
          <div style={{ display: 'flex', gap: 8, marginLeft: 'auto' }}>{bulkBar}</div>
        </div>
      )}
      <div className="scroll card" style={{ maxHeight: '70vh', overflow: 'auto' }}>
        <table className="tbl">
          {caption && <caption className="sr-only">{caption}</caption>}
          <thead style={{ position: 'sticky', top: 0, background: 'var(--surface)', zIndex: 1 }}>
            <tr>
              {selectable && (
                <th style={{ width: 40 }}>
                  <input
                    type="checkbox"
                    aria-label="Select all rows"
                    checked={allOn}
                    onChange={() => onSelect!(allOn ? new Set() : new Set(allKeys))}
                  />
                </th>
              )}
              {columns.map((c) => {
                const isSorted = sort?.key === c.key;
                return (
                  <th
                    key={c.key}
                    className={clsx(c.align === 'right' && 'r', c.align === 'center' && 'c')}
                    style={{ width: c.width }}
                    aria-sort={isSorted ? (sort!.dir === 'asc' ? 'ascending' : 'descending') : undefined}
                  >
                    {c.sortable && onSort ? (
                      <button
                        type="button"
                        onClick={() => onSort({ key: c.key, dir: isSorted && sort!.dir === 'asc' ? 'desc' : 'asc' })}
                        style={{ background: 'none', border: 0, padding: 0, font: 'inherit', color: isSorted ? 'var(--text)' : 'inherit', cursor: 'pointer', letterSpacing: 'inherit', textTransform: 'inherit' }}
                      >
                        {c.header} {isSorted ? (sort!.dir === 'asc' ? '↑' : '↓') : ''}
                      </button>
                    ) : (
                      c.header
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>
          {!loading && rows && rows.length > 0 && (
            <tbody>
              {rows.map((r) => {
                const k = rowKey(r);
                return (
                  <tr
                    key={k}
                    onClick={onRowClick ? () => onRowClick(r) : undefined}
                    style={{ cursor: onRowClick ? 'pointer' : undefined, background: activeKey === k ? 'var(--surface-2)' : undefined, boxShadow: activeKey === k ? 'inset 3px 0 0 var(--lime)' : undefined }}
                  >
                    {selectable && (
                      <td onClick={(e) => e.stopPropagation()}>
                        <input
                          type="checkbox"
                          aria-label="Select row"
                          checked={selected!.has(k)}
                          onChange={() => {
                            const next = new Set(selected);
                            if (next.has(k)) next.delete(k);
                            else next.add(k);
                            onSelect!(next);
                          }}
                        />
                      </td>
                    )}
                    {columns.map((c) => (
                      <td key={c.key} className={clsx(c.align === 'right' && 'r', c.align === 'center' && 'c')}>
                        {c.cell(r)}
                      </td>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          )}
        </table>
        {loading && <SkeletonRows cols={Math.min(columns.length, 6)} />}
        {!loading && rows && rows.length === 0 && (
          <div style={{ padding: 16 }}>
            <EmptyState title={empty?.title ?? 'Nothing here yet'} description={empty?.description} action={empty?.action} />
          </div>
        )}
      </div>
      {pagination && <Pagination {...pagination} />}
    </div>
  );
}
