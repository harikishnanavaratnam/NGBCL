'use client';

import clsx from 'clsx';
import { AnimatePresence, motion } from 'motion/react';
import { useCallback, useEffect, useId, useState, type DragEvent } from 'react';
import Cropper, { type Area } from 'react-easy-crop';
import { Button } from '@/design-system/primitives/Button';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { teamColor } from '@/design-system/tokens';
import { ALLOWED_IMAGE_TYPES } from '@/lib/api/files';
import { errorMessage } from '@/lib/api/errors';
import type { PhotoRules, RegistrationSession, UploadedImage } from '@/lib/api/registration';
import s from './Registration.module.css';

export interface PickedImage extends UploadedImage {
  /** Local object URL of exactly what was uploaded (the server copy stays private until approval). */
  previewUrl: string;
  filename: string;
  sizeBytes: number;
}

type Stage =
  | { kind: 'empty' }
  | { kind: 'cropping'; src: string; file: File; natural: { w: number; h: number } }
  | { kind: 'uploading'; pct: number; name: string }
  | { kind: 'checking'; name: string }
  | { kind: 'done' }
  | { kind: 'error'; message: string; retry?: { blob: Blob; name: string } };

const MAX_EXPORT_SIDE = 2400;

function loadImage(src: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('This file couldn’t be opened as an image.'));
    img.src = src;
  });
}

async function canvasBlob(canvas: HTMLCanvasElement, type: string) {
  const out = type === 'image/png' || type === 'image/webp' ? type : 'image/jpeg';
  return new Promise<Blob>((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Cropping failed.'))), out, 0.92));
}

async function rotate90(src: string, type: string) {
  const img = await loadImage(src);
  const c = document.createElement('canvas');
  c.width = img.naturalHeight;
  c.height = img.naturalWidth;
  const ctx = c.getContext('2d')!;
  ctx.translate(c.width / 2, c.height / 2);
  ctx.rotate(Math.PI / 2);
  ctx.drawImage(img, -img.naturalWidth / 2, -img.naturalHeight / 2);
  return URL.createObjectURL(await canvasBlob(c, type));
}

async function crop(src: string, area: Area, type: string) {
  const img = await loadImage(src);
  const scale = Math.min(1, MAX_EXPORT_SIDE / Math.max(area.width, area.height));
  const c = document.createElement('canvas');
  c.width = Math.round(area.width * scale);
  c.height = Math.round(area.height * scale);
  const ctx = c.getContext('2d');
  if (!ctx) throw new Error('Your browser can’t crop images.');
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(img, area.x, area.y, area.width, area.height, 0, 0, c.width, c.height);
  return canvasBlob(c, type);
}

const mb = (b: number) => `${Math.round((b / 1048576) * 10) / 10} MB`;

/**
 * Inline crop + upload for the public forms (export Reg · Photo, TeamReg · Logo): checks type,
 * size and dimensions in the browser, crops to the fixed ratio, uploads against the applicant's
 * registration session and shows the server's verdict. The server re-checks everything.
 */
export function CropUpload({
  variant,
  rules,
  session,
  value,
  onChange,
  error,
  colorSlot,
}: {
  variant: 'photo' | 'logo';
  rules: PhotoRules;
  session: RegistrationSession;
  value: PickedImage | null;
  onChange: (v: PickedImage | null) => void;
  error?: string;
  colorSlot?: number | null;
}) {
  const inputId = useId();
  const camId = useId();
  const aspect = rules.ratioW > 0 && rules.ratioH > 0 ? rules.ratioW / rules.ratioH : 1;
  const [stage, setStage] = useState<Stage>(value ? { kind: 'done' } : { kind: 'empty' });
  const [cropXY, setCropXY] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [area, setArea] = useState<Area | null>(null);
  const [dragging, setDragging] = useState(false);
  const [checks, setChecks] = useState<{ type?: boolean; size?: boolean; dims?: boolean }>(
    value ? { type: true, size: true, dims: true } : {},
  );

  // Free object URLs that are no longer shown
  useEffect(
    () => () => {
      if (stage.kind === 'cropping') URL.revokeObjectURL(stage.src);
    },
    [stage],
  );

  const accept = useCallback(
    async (file: File | undefined) => {
      if (!file) return;
      const typeOk = ALLOWED_IMAGE_TYPES.includes(file.type);
      const sizeOk = file.size <= rules.maxBytes * 4; // the crop is re-encoded, so allow larger originals
      setChecks({ type: typeOk, size: sizeOk });
      if (!typeOk) return setStage({ kind: 'error', message: 'Use a JPEG, PNG or WebP image.' });
      if (!sizeOk) return setStage({ kind: 'error', message: `This file is ${mb(file.size)} — choose one under ${mb(rules.maxBytes)}.` });
      const src = URL.createObjectURL(file);
      try {
        const img = await loadImage(src);
        const dimsOk = img.naturalWidth >= rules.minWidth && img.naturalHeight >= rules.minHeight;
        setChecks({ type: true, size: true, dims: dimsOk });
        if (!dimsOk) {
          URL.revokeObjectURL(src);
          return setStage({
            kind: 'error',
            message: `The image must be at least ${rules.minWidth} × ${rules.minHeight} px (yours is ${img.naturalWidth} × ${img.naturalHeight}).`,
          });
        }
        setCropXY({ x: 0, y: 0 });
        setZoom(1);
        setStage({ kind: 'cropping', src, file, natural: { w: img.naturalWidth, h: img.naturalHeight } });
      } catch (e) {
        URL.revokeObjectURL(src);
        setStage({ kind: 'error', message: errorMessage(e) });
      }
    },
    [rules],
  );

  const upload = useCallback(
    async (blob: Blob, name: string) => {
      if (blob.size > rules.maxBytes) {
        setChecks((c) => ({ ...c, size: false }));
        return setStage({
          kind: 'error',
          message: `The cropped image is ${mb(blob.size)} — the limit is ${mb(rules.maxBytes)}. Zoom in or use a smaller photo.`,
        });
      }
      setStage({ kind: 'uploading', pct: 0, name });
      try {
        const res = await session.upload(blob, name, (pct) =>
          setStage(pct >= 100 ? { kind: 'checking', name } : { kind: 'uploading', pct, name }),
        );
        if (value) URL.revokeObjectURL(value.previewUrl);
        onChange({ ...res, previewUrl: URL.createObjectURL(blob), filename: name, sizeBytes: blob.size });
        setChecks({ type: true, size: true, dims: true });
        setStage({ kind: 'done' });
      } catch (e) {
        setStage({ kind: 'error', message: errorMessage(e), retry: { blob, name } });
      }
    },
    [rules.maxBytes, session, onChange, value],
  );

  const applyCrop = async () => {
    if (stage.kind !== 'cropping' || !area) return;
    if (area.width < rules.minWidth || area.height < rules.minHeight) {
      setChecks((c) => ({ ...c, dims: false }));
      return;
    }
    const { src, file } = stage;
    try {
      const blob = await crop(src, area, file.type);
      await upload(blob, file.name);
    } catch (e) {
      setStage({ kind: 'error', message: errorMessage(e) });
    }
  };

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    void accept(e.dataTransfer.files?.[0]);
  };

  const busy = stage.kind === 'uploading' || stage.kind === 'checking';
  const cropTooSmall = stage.kind === 'cropping' && area !== null && (area.width < rules.minWidth || area.height < rules.minHeight);
  const fileInput = (
    <input
      id={inputId}
      type="file"
      accept={ALLOWED_IMAGE_TYPES.join(',')}
      className="sr-only"
      disabled={busy}
      onChange={(e) => {
        void accept(e.target.files?.[0]);
        e.target.value = '';
      }}
    />
  );

  return (
    <div className={s.row2} style={{ alignItems: 'start' }}>
      {/* Left: crop / preview */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <span className="lbl">
          {stage.kind === 'cropping' ? `Crop to ${rules.ratioW} : ${rules.ratioH}` : variant === 'photo' ? 'Your photo' : 'Your logo'}
        </span>
        <div className={clsx(s.cropBox, variant === 'logo' && s.cropBoxSquare)}>
          {stage.kind === 'cropping' ? (
            <Cropper
              image={stage.src}
              crop={cropXY}
              zoom={zoom}
              aspect={aspect}
              onCropChange={setCropXY}
              onZoomChange={setZoom}
              onCropComplete={(_, px) => setArea(px)}
              objectFit="contain"
              showGrid={false}
            />
          ) : value ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={value.previewUrl}
              alt={variant === 'photo' ? 'Your player photo' : 'Your team logo'}
              style={{ width: '100%', height: '100%', objectFit: variant === 'logo' ? 'contain' : 'cover' }}
            />
          ) : (
            <div
              className={clsx('ph', variant === 'photo' ? 'ph-person' : 'ph-crest')}
              style={{ position: 'absolute', inset: 0, border: 0, borderRadius: 0 }}
            >
              {variant === 'photo' ? 'Photo 4 : 5' : 'Logo 1 : 1'}
            </div>
          )}
          <AnimatePresence>
            {busy && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                style={{
                  position: 'absolute',
                  inset: 0,
                  background: 'rgba(16,19,24,.72)',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 6,
                }}
              >
                <span className="num" style={{ fontSize: 30 }}>
                  {stage.kind === 'uploading' ? `${stage.pct}%` : '…'}
                </span>
                <span className="help">{stage.kind === 'uploading' ? 'Uploading' : 'Checking image'}</span>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
        {stage.kind === 'cropping' && (
          <>
            <label className="field" style={{ maxWidth: 320 }}>
              <span className="lbl">Zoom</span>
              <input
                type="range"
                min={1}
                max={3}
                step={0.01}
                value={zoom}
                onChange={(e) => setZoom(Number(e.target.value))}
                aria-label="Zoom"
              />
            </label>
            {cropTooSmall && (
              <span className="errmsg" role="alert">
                Zoom out — the crop must be at least {rules.minWidth} × {rules.minHeight} px.
              </span>
            )}
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <Button
                variant="out"
                size="sm"
                onClick={async () => {
                  const next = await rotate90(stage.src, stage.file.type);
                  URL.revokeObjectURL(stage.src);
                  setStage({ ...stage, src: next, natural: { w: stage.natural.h, h: stage.natural.w } });
                }}
              >
                Rotate
              </Button>
              <Button variant="volt" size="sm" onClick={applyCrop} disabled={cropTooSmall}>
                {variant === 'photo' ? 'Use this photo' : 'Use this logo'}
              </Button>
            </div>
          </>
        )}
        {stage.kind === 'done' && value && (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <label htmlFor={inputId} className="btn btn-out btn-sm">
              {variant === 'photo' ? 'Replace photo' : 'Replace logo'}
            </label>
          </div>
        )}
      </div>

      {/* Right: dropzone, progress, checks */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div
          className={clsx(s.dropzone, dragging && s.dropzoneOn)}
          onDragOver={(e) => {
            e.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
        >
          <svg
            width="28"
            height="28"
            viewBox="0 0 24 24"
            fill="none"
            stroke="var(--text-2)"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M12 16V4M7 9l5-5 5 5M4 16v4h16v-4" />
          </svg>
          <strong>{variant === 'photo' ? 'Drag a photo here' : 'Drag your logo here'}</strong>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'center' }}>
            <label htmlFor={inputId} className="btn btn-volt" style={{ minHeight: 44 }} aria-disabled={busy}>
              Choose file
            </label>
            {variant === 'photo' && (
              <>
                <label htmlFor={camId} className="btn btn-out" style={{ minHeight: 44 }}>
                  Take a photo
                </label>
                <input
                  id={camId}
                  type="file"
                  accept="image/*"
                  capture="user"
                  className="sr-only"
                  disabled={busy}
                  onChange={(e) => {
                    void accept(e.target.files?.[0]);
                    e.target.value = '';
                  }}
                />
              </>
            )}
          </div>
          <span className="help">
            {variant === 'photo'
              ? '“Take a photo” opens the camera on phones'
              : `Square (1 : 1) · PNG with transparent background works best · JPEG, PNG or WebP · under ${mb(rules.maxBytes)}`}
          </span>
          {fileInput}
        </div>

        {(busy || (stage.kind === 'done' && value)) && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, gap: 8 }}>
              <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {busy ? stage.name : value?.filename}
              </span>
              <span className="muted">
                {stage.kind === 'uploading' ? `${stage.pct}%` : stage.kind === 'checking' ? 'Checking…' : 'Uploaded'}
              </span>
            </div>
            <div className={s.progress}>
              <motion.div
                className={s.progressFill}
                initial={false}
                animate={{ width: stage.kind === 'uploading' ? `${stage.pct}%` : '100%' }}
              />
            </div>
          </div>
        )}

        <ul className={s.checks} aria-label="Image checks">
          <Check ok={checks.type}>JPEG, PNG or WebP</Check>
          <Check ok={checks.size}>Under {mb(rules.maxBytes)}</Check>
          <Check ok={checks.dims}>
            At least {rules.minWidth} × {rules.minHeight} px
          </Check>
          {variant === 'photo' ? <Check>Face clear, plain background</Check> : <Check>Reads well on light and dark</Check>}
        </ul>

        {variant === 'logo' && value && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <span className="help">Preview — check it reads on every background</span>
            <div className={s.row3}>
              {[
                { bg: '#F5F7FA', label: 'On light' },
                { bg: '#101318', label: 'On dark' },
                { bg: '#1E252E', label: 'On crest tile', ring: true },
              ].map((p) => (
                <div key={p.label} className={s.logoPreview}>
                  <span
                    className={s.logoTile}
                    style={{ background: p.bg, boxShadow: p.ring ? `0 0 0 2px ${teamColor(colorSlot)}` : '0 0 0 1px var(--line-2)' }}
                  >
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={value.previewUrl} alt="" />
                  </span>
                  <span className="help">{p.label}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {variant === 'photo' && !value && stage.kind !== 'cropping' && (
          <div className={s.examples}>
            <div className={s.example}>
              <div className="ph ph-person" style={{ aspectRatio: '4 / 5' }}>
                Good
              </div>
              <span className="help">Face centred, good light</span>
            </div>
            <div className={s.example}>
              <div className="ph" style={{ aspectRatio: '4 / 5' }}>
                Poor
              </div>
              <span className="help">Group shot, dark, cropped</span>
            </div>
          </div>
        )}
      </div>

      {(stage.kind === 'error' || error) && (
        <div role="alert" className={s.alert} style={{ gridColumn: '1 / -1' }}>
          <strong style={{ color: 'var(--err)' }}>{stage.kind === 'error' ? 'Upload failed.' : 'Image needed.'}</strong>
          <span>{stage.kind === 'error' ? `${stage.message} Your other details are kept.` : error}</span>
          {stage.kind === 'error' && stage.retry && (
            <button
              type="button"
              className={s.linkBtn}
              style={{ marginLeft: 'auto' }}
              onClick={() => stage.retry && void upload(stage.retry.blob, stage.retry.name)}
            >
              Retry
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function Check({ ok, children }: { ok?: boolean; children: React.ReactNode }) {
  return (
    <li>
      {ok === undefined ? (
        <StatusChip tone="neutral">Check</StatusChip>
      ) : ok ? (
        <StatusChip tone="ok">Pass</StatusChip>
      ) : (
        <StatusChip tone="error">Fix</StatusChip>
      )}
      {children}
    </li>
  );
}
