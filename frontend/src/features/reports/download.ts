import type { Report, ReportSheet } from '@/lib/api/standings';

/** Excel sheet names: max 31 characters, none of : \ / ? * [ ], unique within the workbook. */
export function sheetName(name: string, used: Set<string>) {
  const base = (name.replace(/[:\\/?*[\]]/g, ' ').trim() || 'Sheet').slice(0, 31);
  let out = base;
  for (let i = 2; used.has(out.toLowerCase()); i++) {
    const suffix = ` (${i})`;
    out = base.slice(0, 31 - suffix.length) + suffix;
  }
  used.add(out.toLowerCase());
  return out;
}

/** RFC 4180 CSV with a UTF-8 BOM so Excel opens Tamil and Sinhala names correctly. */
export function toCsv(sheet: ReportSheet) {
  const cell = (v: string | number | null) => {
    if (v === null || v === undefined) return '';
    const s = String(v);
    // Stop spreadsheet formula injection from user-entered text.
    const safe = typeof v === 'string' && /^[=@\t\r]|^[+-](?!\d)/.test(s) ? `'${s}` : s;
    return /[",\r\n]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
  };
  const lines = [sheet.columns, ...sheet.rows].map((r) => r.map(cell).join(','));
  return '﻿' + lines.join('\r\n') + '\r\n';
}

function save(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function downloadCsv(report: Report, sheetIndex = 0) {
  const sheet = report.sheets[sheetIndex];
  if (!sheet) return;
  const suffix = report.sheets.length > 1 ? `-${sheet.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}` : '';
  save(new Blob([toCsv(sheet)], { type: 'text/csv;charset=utf-8' }), `${report.fileName}${suffix}.csv`);
}

/** One worksheet per report sheet: bold frozen header, auto filter, sensible column widths. */
export async function downloadXlsx(report: Report) {
  const { default: ExcelJS } = await import('exceljs');
  const wb = new ExcelJS.Workbook();
  wb.creator = 'NGBCL';
  wb.created = new Date();
  const used = new Set<string>();
  for (const sheet of report.sheets) {
    const ws = wb.addWorksheet(sheetName(sheet.name, used), { views: [{ state: 'frozen', ySplit: 1 }] });
    ws.addRow(sheet.columns);
    ws.getRow(1).font = { bold: true };
    for (const r of sheet.rows) {
      // exceljs writes strings as text cells, never formulas.
      ws.addRow(r.map((v) => v ?? ''));
    }
    if (sheet.columns.length) {
      ws.autoFilter = { from: { row: 1, column: 1 }, to: { row: 1, column: sheet.columns.length } };
    }
    ws.columns.forEach((col, i) => {
      const longest = Math.max(sheet.columns[i]?.length ?? 8, ...sheet.rows.slice(0, 500).map((r) => String(r[i] ?? '').length));
      col.width = Math.min(60, Math.max(8, longest + 2));
    });
  }
  const buf = await wb.xlsx.writeBuffer();
  save(new Blob([buf], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), `${report.fileName}.xlsx`);
}
