import Image from 'next/image';
import { teamColor } from '../tokens';

const SIZES = { s: 32, m: 56, l: 96, xl: 200 } as const;

/**
 * Team logo on a neutral square with a team-colour ring (brief §5), so mismatched uploads look
 * consistent. Falls back to the placeholder crest until a verified logo exists.
 */
export function CrestTile({
  logoUrl,
  name,
  code,
  colorSlot,
  size = 'm',
  showName,
}: {
  logoUrl?: string | null;
  name: string;
  code?: string;
  colorSlot?: number | null;
  size?: keyof typeof SIZES;
  showName?: boolean;
}) {
  const px = SIZES[size];
  const tile = (
    <span
      style={{
        width: px,
        height: px,
        borderRadius: size === 's' ? 6 : 'var(--r-m)',
        background: '#1E252E',
        boxShadow: `0 0 0 ${size === 'xl' ? 3 : 2}px ${teamColor(colorSlot)}`,
        display: 'grid',
        placeItems: 'center',
        overflow: 'hidden',
        flexShrink: 0,
      }}
    >
      {logoUrl ? (
        <Image src={logoUrl} alt={`${name} logo`} width={px} height={px} style={{ objectFit: 'contain', width: '82%', height: '82%' }} unoptimized />
      ) : (
        <span className="ph-crest" role="img" aria-label={`${name} crest placeholder`} style={{ width: '100%', height: '100%', display: 'block' }} />
      )}
    </span>
  );
  if (!showName) return tile;
  return (
    <span style={{ display: 'inline-flex', flexDirection: size === 'xl' || size === 'l' ? 'column' : 'row', alignItems: 'center', gap: 10 }}>
      {tile}
      <span style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.2 }}>
        <strong style={{ fontSize: size === 's' ? 14 : 15 }}>{name}</strong>
        {code && <span className="eyebrow">{code}</span>}
      </span>
    </span>
  );
}
