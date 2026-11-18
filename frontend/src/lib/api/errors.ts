/** RFC 9457 problem+json body returned by the backend for every error. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  /** Stable machine code, e.g. BID_EXCEEDS_BUDGET, VALIDATION_FAILED. */
  code?: string;
  errors?: { field: string; message: string }[];
}

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly fieldErrors: Record<string, string>;
  readonly problem?: ProblemDetail;

  constructor(status: number, problem?: ProblemDetail) {
    super(problem?.detail ?? problem?.title ?? `Request failed (${status})`);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem?.code;
    this.problem = problem;
    this.fieldErrors = Object.fromEntries((problem?.errors ?? []).map((e) => [e.field, e.message]));
  }

  get isNetwork() {
    return this.status === 0;
  }
}

/** Human message for toasts / inline banners. Server text wins; we only fill gaps. */
export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.isNetwork) return 'Can’t reach the server. Check your connection and try again.';
    if (err.status === 403) return err.message || 'You don’t have permission to do that.';
    return err.message;
  }
  if (err instanceof Error) return err.message;
  return 'Something went wrong.';
}
