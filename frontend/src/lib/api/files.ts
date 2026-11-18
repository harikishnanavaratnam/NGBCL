'use client';

import { api } from './client';
import { ApiError } from './errors';

export type FilePurpose =
  | 'PLAYER_PHOTO'
  | 'TEAM_LOGO'
  | 'TOURNAMENT_LOGO'
  | 'FAVICON'
  | 'ORGANISER_LOGO'
  | 'POSTER'
  | 'SPONSOR_LOGO'
  | 'COMMITTEE_PHOTO';

export interface FileAssetView {
  id: string;
  purpose: FilePurpose;
  uploadStatus: 'PENDING' | 'UPLOADED' | 'INVALID' | 'DELETED';
  verificationStatus: 'UNVERIFIED' | 'VERIFIED' | 'REJECTED';
  contentType: string | null;
  size: number | null;
  width: number | null;
  height: number | null;
  invalidReason: string | null;
  rejectionReason: string | null;
  previewUrl: string | null;
  publicUrl: string | null;
  publishedAt: string | null;
}

interface UploadTicket {
  assetId: string;
  uploadUrl: string;
  method: 'PUT';
  headers: Record<string, string>;
  expiresAt: string;
  maxBytes: number;
  minWidth: number;
  minHeight: number;
}

/** Upload rules per purpose — mirrors lk.ngbcl.files.domain.FilePurpose (server re-checks everything). */
export const PURPOSE_RULES: Record<FilePurpose, { aspect?: number; minWidth: number; minHeight: number; label: string }> = {
  PLAYER_PHOTO: { aspect: 4 / 5, minWidth: 600, minHeight: 750, label: '4 : 5 portrait' },
  TEAM_LOGO: { aspect: 1, minWidth: 256, minHeight: 256, label: '1 : 1 square' },
  TOURNAMENT_LOGO: { minWidth: 256, minHeight: 128, label: 'any shape, transparent PNG works best' },
  FAVICON: { aspect: 1, minWidth: 180, minHeight: 180, label: '1 : 1 square' },
  ORGANISER_LOGO: { minWidth: 128, minHeight: 128, label: 'any shape' },
  POSTER: { aspect: 16 / 9, minWidth: 1280, minHeight: 720, label: '16 : 9 landscape' },
  SPONSOR_LOGO: { minWidth: 200, minHeight: 100, label: 'any shape' },
  COMMITTEE_PHOTO: { aspect: 4 / 5, minWidth: 300, minHeight: 375, label: '4 : 5 portrait' },
};

export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

function putWithProgress(url: string, blob: Blob, headers: Record<string, string>, onProgress: (pct: number) => void) {
  return new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    Object.entries(headers).forEach(([k, v]) => xhr.setRequestHeader(k, v));
    xhr.upload.onprogress = (e) => e.lengthComputable && onProgress(Math.round((e.loaded / e.total) * 100));
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new ApiError(xhr.status, { status: xhr.status, code: 'UPLOAD_FAILED', detail: 'The upload was refused by storage. Try again.' })));
    xhr.onerror = () => reject(new ApiError(0, { status: 0, code: 'UPLOAD_FAILED', detail: 'The upload failed. Check your connection and try again.' }));
    xhr.send(blob);
  });
}

/**
 * SRS §7.2: ask the API for a presigned URL → PUT straight to R2 → ask the API to verify the
 * real bytes. Resolves with the checked asset (uploadStatus UPLOADED) or throws ApiError.
 */
export async function uploadImage(blob: Blob, purpose: FilePurpose, filename: string, onProgress: (pct: number) => void) {
  const ticket = await api<UploadTicket>('/files/uploads', {
    method: 'POST',
    body: { purpose, filename, contentType: blob.type, size: blob.size },
  });
  await putWithProgress(ticket.uploadUrl, blob, ticket.headers, onProgress);
  return api<FileAssetView>(`/files/${ticket.assetId}/complete`, { method: 'POST' });
}
