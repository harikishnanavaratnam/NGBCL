import type { ChipTone } from '@/design-system/primitives/StatusChip';
import type { LotState } from '@/lib/api/auction';

export const STATE_LABEL: Record<LotState, string> = {
  QUEUED: 'Waiting',
  OPEN: 'On the block',
  SOLD: 'Sold',
  UNSOLD: 'Unsold',
  WITHDRAWN: 'Withdrawn',
};

export const STATE_TONE: Record<LotState, ChipTone> = {
  QUEUED: 'neutral',
  OPEN: 'live',
  SOLD: 'sold',
  UNSOLD: 'error',
  WITHDRAWN: 'neutral',
};
