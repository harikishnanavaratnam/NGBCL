'use client';

import { AnimatePresence, motion } from 'motion/react';
import { useCallback, useId, useRef, useState, type DragEvent } from 'react';
import Cropper, { type Area } from 'react-easy-crop';
import { ALLOWED_IMAGE_TYPES, PURPOSE_RULES, uploadImage, type FileAssetView, type FilePurpose } from '@/lib/api/files';
import { errorMessage } from '@/lib/api/errors';
import { Button } from '../primitives/Button';
import { Modal } from '../primitives/Overlay';
import { StatusChip } from '../primitives/StatusChip';
import { panelTransition } from '../motion/presets';
import styles from './ImageUploader.module.css';

type Stage =
  | { kind: 'idle' }
  | { kind: 'cropping'; src: string; file: File; natural: { w: number; h: number } }
  | { kind: 'uploading'; pct: number }
  | { kind: 'checking' }
  | { kind: 'done'; asset: FileAssetView }
  | { kind: 'error'; message: string };

interface Props {
  purpose: FilePurpose;
  label: string;
  hint?: string;
  /** Image currently in use (public or preview URL). */
  currentUrl?: string | null;
  /** Max bytes accepted by the server for this purpose. */
  maxBytes?: number;
  minWidth?: number;
  minHeight?: number;
  onUploaded: (asset: FileAssetView) => void;
  /** Square / portrait / wide preview tile. */
  previewAspect?: number;
}

const MAX_EXPORT_SIDE = 2400;

function loadImage(src: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('This file couldn’t be opened as an image.'));
    img.src = src;
  });
}

/** Draws the cropped region at full resolution (capped) in the original format where possible. */
async function cropToBlob(src: string, area: Area, type: string): Promise<Blob> {
  const img = await loadImage(src);
  const scale = Math.min(1, MAX_EXPORT_SIDE / Math.max(area.width, area.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(area.width * scale);
  canvas.height = Math.round(area.height * scale);
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Your browser can’t crop images.');
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(img, area.x, area.y, area.width, area.height, 0, 0, canvas.width, canvas.height);
  const outType = type === 'image/png' || type === 'image/webp' ? type : 'image/jpeg';
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Cropping failed.'))), outType, 0.92),
  );
}

/**
 * Drag-and-drop image uploader (brief §5): checks type and size in the browser, crops to the
 * required ratio (4:5 photo, 1:1 logo…), uploads straight to storage with progress, then shows
 * the server's verdict. The server re-checks everything — these checks are only for speed.
 */
export function ImageUploader({ purpose, label, hint, currentUrl, maxBytes = 5 * 1024 * 1024, minWidth, minHeight, onUploaded, previewAspect }: Props) {
  const rules = PURPOSE_RULES[purpose];
  const minW = minWidth ?? rules.minWidth;
  const minH = minHeight ?? rules.minHeight;
  const inputId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [stage, setStage] = useState<Stage>({ kind: 'idle' });
  const [dragging, setDragging] = useState(false);
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [area, setArea] = useState<Area | null>(null);

  const upload = useCallback(
    async (blob: Blob, name: string) => {
      if (blob.size > maxBytes) {
        setStage({ kind: 'error', message: `The image is ${(blob.size / 1048576).toFixed(1)} MB — the limit is ${(maxBytes / 1048576).toFixed(0)} MB.` });
        return;
      }
      setStage({ kind: 'uploading', pct: 0 });
      try {
        const asset = await uploadImage(blob, purpose, name, (pct) => setStage(pct >= 100 ? { kind: 'checking' } : { kind: 'uploading', pct }));
        setStage({ kind: 'done', asset });
        onUploaded(asset);
      } catch (e) {
        setStage({ kind: 'error', message: errorMessage(e) });
      }
    },
    [maxBytes, purpose, onUploaded],
  );

  const accept = useCallback(
    async (file: File | undefined) => {
      if (!file) return;
      if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
        setStage({ kind: 'error', message: 'Use a JPEG, PNG or WebP image.' });
        return;
      }
      const src = URL.createObjectURL(file);
      try {
        const img = await loadImage(src);
        if (img.naturalWidth < minW || img.naturalHeight < minH) {
          setStage({ kind: 'error', message: `The image must be at least ${minW} × ${minH} px (yours is ${img.naturalWidth} × ${img.naturalHeight}).` });
          URL.revokeObjectURL(src);
          return;
        }
        if (rules.aspect) {
          setCrop({ x: 0, y: 0 });
          setZoom(1);
          setStage({ kind: 'cropping', src, file, natural: { w: img.naturalWidth, h: img.naturalHeight } });
        } else {
          URL.revokeObjectURL(src);
          await upload(file, file.name);
        }
      } catch (e) {
        URL.revokeObjectURL(src);
        setStage({ kind: 'error', message: errorMessage(e) });
      }
    },
    [minW, minH, rules.aspect, upload],
  );

  const confirmCrop = async () => {
    if (stage.kind !== 'cropping' || !area) return;
    if (area.width < minW || area.height < minH) {
      setStage({ kind: 'error', message: `Zoom out — the cropped area must be at least ${minW} × ${minH} px.` });
      URL.revokeObjectURL(stage.src);
      return;
    }
    const { src, file } = stage;
    try {
      const blob = await cropToBlob(src, area, file.type);
      URL.revokeObjectURL(src);
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

  const shownUrl = stage.kind === 'done' ? (stage.asset.publicUrl ?? stage.asset.previewUrl) : currentUrl;
  const busy = stage.kind === 'uploading' || stage.kind === 'checking';

  return (
    <div className={styles.root}>
      <div
        className={`${styles.drop} ${dragging ? styles.dragging : ''}`}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
      >
        <div className={styles.preview} style={{ aspectRatio: String(previewAspect ?? rules.aspect ?? 1) }}>
          {shownUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={shownUrl} alt={`${label} preview`} />
          ) : (
            <span className="ph" style={{ width: '100%', height: '100%' }}>
              No image
            </span>
          )}
          <AnimatePresence>
            {busy && (
              <motion.div className={styles.overlay} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={panelTransition}>
                <span className="num" style={{ fontSize: 28 }}>
                  {stage.kind === 'uploading' ? `${stage.pct}%` : '…'}
                </span>
                <span className="t-caption">{stage.kind === 'uploading' ? 'Uploading' : 'Checking image'}</span>
                {stage.kind === 'uploading' && (
                  <span className={styles.bar}>
                    <motion.span className={styles.fill} animate={{ width: `${stage.pct}%` }} transition={{ duration: 0.2 }} />
                  </span>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
        <div className={styles.meta}>
          <strong style={{ fontSize: 14 }}>{label}</strong>
          <span className="help">{hint ?? `${rules.label} · min ${minW} × ${minH} px · JPEG, PNG or WebP · up to ${Math.round(maxBytes / 1048576)} MB`}</span>
          {stage.kind === 'done' && (
            <span style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              <StatusChip tone="ok">Checked</StatusChip>
              <span className="t-caption">
                {stage.asset.width} × {stage.asset.height} px · save to apply
              </span>
            </span>
          )}
          {stage.kind === 'error' && (
            <span className="errmsg" role="alert">
              {stage.message}
            </span>
          )}
          <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
            <label htmlFor={inputId} className="btn btn-out btn-sm" aria-disabled={busy} style={{ pointerEvents: busy ? 'none' : undefined, opacity: busy ? 0.5 : 1 }}>
              {shownUrl ? 'Replace' : 'Choose image'}
            </label>
            <span className="t-caption" style={{ alignSelf: 'center' }}>
              or drop a file here
            </span>
          </div>
          <input
            ref={inputRef}
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
        </div>
      </div>

      <Modal
        open={stage.kind === 'cropping'}
        onClose={() => {
          if (stage.kind === 'cropping') URL.revokeObjectURL(stage.src);
          setStage({ kind: 'idle' });
        }}
        title={`Crop ${label.toLowerCase()}`}
        width={640}
        footer={
          <>
            <Button
              variant="out"
              onClick={() => {
                if (stage.kind === 'cropping') URL.revokeObjectURL(stage.src);
                setStage({ kind: 'idle' });
              }}
            >
              Cancel
            </Button>
            <Button variant="volt" onClick={confirmCrop}>
              Use this crop
            </Button>
          </>
        }
      >
        {stage.kind === 'cropping' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div className={styles.cropper}>
              <Cropper
                image={stage.src}
                crop={crop}
                zoom={zoom}
                aspect={rules.aspect}
                onCropChange={setCrop}
                onZoomChange={setZoom}
                onCropComplete={(_, px) => setArea(px)}
                objectFit="contain"
              />
            </div>
            <label style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: 13 }}>
              <span className="eyebrow">Zoom</span>
              <input type="range" min={1} max={4} step={0.01} value={zoom} onChange={(e) => setZoom(Number(e.target.value))} style={{ flex: 1 }} aria-label="Zoom" />
            </label>
            <span className="help">
              {rules.label}. Crop must be at least {minW} × {minH} px
              {area ? ` — current ${Math.round(area.width)} × ${Math.round(area.height)}` : ''}.
            </span>
          </div>
        )}
      </Modal>
    </div>
  );
}
