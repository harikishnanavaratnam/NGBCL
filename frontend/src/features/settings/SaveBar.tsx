'use client';

import { AnimatePresence, motion } from 'motion/react';
import { Button } from '@/design-system/primitives/Button';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { panelTransition } from '@/design-system/motion/presets';
import { formatDateTime } from '@/lib/format/dates';
import styles from './Settings.module.css';

/** Sticky "Unsaved changes · Discard · Save changes" bar (export AdminSettings footer). */
export function SaveBar({ dirty, saving, updatedAt, onDiscard }: { dirty: boolean; saving: boolean; updatedAt?: string | null; onDiscard: () => void }) {
  return (
    <AnimatePresence initial={false}>
      {dirty && (
        <motion.div className={styles.saveBar} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 12 }} transition={panelTransition}>
          <StatusChip tone="pending">Unsaved changes</StatusChip>
          {updatedAt && <span className="t-caption">Last saved {formatDateTime(updatedAt)}</span>}
          <div className={styles.actions}>
            <Button variant="out" onClick={onDiscard} disabled={saving}>
              Discard
            </Button>
            <Button type="submit" variant="volt" loading={saving}>
              Save changes
            </Button>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
