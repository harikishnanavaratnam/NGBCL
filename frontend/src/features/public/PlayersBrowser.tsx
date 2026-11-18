'use client';

import { AnimatePresence, motion } from 'motion/react';
import { useMemo, useState } from 'react';
import { Input, Select } from '@/design-system/primitives/Inputs';
import type { PublicPlayer } from '@/lib/api/public';
import { PlayerGrid } from './Sections';
import s from './Public.module.css';

const PAGE = 20;

/** Client-side filters over the (small) approved-player list: category and name search. */
export function PlayersBrowser({ players }: { players: PublicPlayer[] }) {
  const [q, setQ] = useState('');
  const [cat, setCat] = useState('');
  const [shown, setShown] = useState(PAGE);
  const categories = useMemo(
    () => [...new Set(players.map((p) => p.categoryName).filter((c): c is string => Boolean(c)))].sort(),
    [players],
  );
  const filtered = players.filter(
    (p) => (!cat || p.categoryName === cat) && (!q.trim() || p.fullName.toLowerCase().includes(q.trim().toLowerCase())),
  );
  return (
    <>
      <div className={s.filters}>
        {categories.length > 0 && (
          <Select aria-label="Category" value={cat} onChange={(e) => (setCat(e.target.value), setShown(PAGE))}>
            <option value="">All categories</option>
            {categories.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </Select>
        )}
        <Input
          type="search"
          placeholder="Search players"
          aria-label="Search players"
          value={q}
          onChange={(e) => (setQ(e.target.value), setShown(PAGE))}
        />
        <span className="muted" aria-live="polite">
          {filtered.length} {filtered.length === 1 ? 'player' : 'players'}
        </span>
      </div>
      <AnimatePresence mode="wait">
        <motion.div
          key={`${cat}|${q}`}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.15 }}
        >
          {filtered.length ? (
            <PlayerGrid players={filtered.slice(0, shown)} />
          ) : (
            <p className="muted">No players match. Try another search.</p>
          )}
        </motion.div>
      </AnimatePresence>
      {shown < filtered.length && (
        <div style={{ marginTop: 24 }}>
          <button type="button" className="btn btn-out" onClick={() => setShown((n) => n + PAGE)}>
            Load more
          </button>
        </div>
      )}
    </>
  );
}
