'use client';

import { Select } from './Inputs';

/** Page size 25 / 50 / 100 (NFR-012, brief data table). */
export function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  onPage,
  onSize,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onPage: (p: number) => void;
  onSize?: (s: number) => void;
}) {
  const from = totalElements === 0 ? 0 : page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);
  return (
    <nav aria-label="Pagination" style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap', fontSize: 13 }} className="muted">
      <span>
        {from}–{to} of {totalElements}
      </span>
      {onSize && (
        <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          Rows
          <Select value={size} onChange={(e) => onSize(Number(e.target.value))} style={{ width: 'auto', minHeight: 32 }}>
            {[25, 50, 100].map((s) => (
              <option key={s}>{s}</option>
            ))}
          </Select>
        </label>
      )}
      <span style={{ marginLeft: 'auto', display: 'flex', gap: 6 }}>
        <button type="button" className="btn btn-out btn-sm" disabled={page <= 0} onClick={() => onPage(page - 1)}>
          Previous
        </button>
        <button type="button" className="btn btn-out btn-sm" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>
          Next
        </button>
      </span>
    </nav>
  );
}
