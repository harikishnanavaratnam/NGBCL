/** Motion tokens from the brief (§5 Foundations) + the export's --ease curve. */
export const EASE_OUT_EXPO = [0.16, 1, 0.3, 1] as const;

export const DURATION = {
  hover: 0.12,
  panel: 0.2,
  bid: 0.32,
  sold: 0.9,
} as const;

export const panelTransition = { duration: DURATION.panel, ease: EASE_OUT_EXPO };
export const bidTransition = { duration: DURATION.bid, ease: EASE_OUT_EXPO };
export const soldTransition = { duration: DURATION.sold, ease: EASE_OUT_EXPO };

export const fadeUp = {
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: 8 },
  transition: panelTransition,
};

/** Rejected bid / invalid score: short horizontal shake (Smash Red context). */
export const shake = {
  x: [0, -8, 8, -6, 6, -3, 3, 0],
  transition: { duration: 0.42 },
};
