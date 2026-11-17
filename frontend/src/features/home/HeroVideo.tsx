'use client';

import { useEffect, useRef, useSyncExternalStore } from 'react';

type Conn = { saveData?: boolean; effectiveType?: string };

const QUERIES = ['(prefers-reduced-motion: reduce)', '(max-width: 767px)'];

function subscribeMedia(cb: () => void) {
  const lists = QUERIES.map((q) => window.matchMedia(q));
  lists.forEach((l) => l.addEventListener('change', cb));
  return () => lists.forEach((l) => l.removeEventListener('change', cb));
}

function shouldPlayVideo() {
  if (QUERIES.some((q) => window.matchMedia(q).matches)) return false;
  const conn = (navigator as Navigator & { connection?: Conn }).connection;
  return !(conn?.saveData || /(^|-)2g|3g/.test(conn?.effectiveType ?? ''));
}

/**
 * Floodlit hero background (brief §7.1 "Background video handling"):
 * - muted, looped, inline; poster frame on first paint
 * - paused when scrolled off-screen
 * - still image under prefers-reduced-motion, Save-Data, slow (2g/3g) connections and < 768 px
 *   (mobile uses a portrait still; the video is 2.16:1 and would crop to the torso)
 */
export function HeroVideo() {
  const ref = useRef<HTMLVideoElement>(null);
  const useVideo = useSyncExternalStore(subscribeMedia, shouldPlayVideo, () => false);

  useEffect(() => {
    const v = ref.current;
    if (!v || !useVideo) return;
    const io = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) void v.play().catch(() => {});
        else v.pause();
      },
      { threshold: 0.1 },
    );
    io.observe(v);
    return () => io.disconnect();
  }, [useVideo]);

  return (
    <>
      <picture>
        <source media="(max-width: 767px)" srcSet="/brand/hero-mobile.jpg" />
        <img
          src="/brand/hero-poster.jpg"
          alt=""
          aria-hidden="true"
          fetchPriority="high"
          style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', objectPosition: '72% 30%' }}
        />
      </picture>
      {useVideo && (
        <video
          ref={ref}
          autoPlay
          muted
          loop
          playsInline
          preload="auto"
          poster="/brand/hero-poster.jpg"
          aria-hidden="true"
          style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', objectPosition: '72% 30%' }}
        >
          <source src="/brand/hero.webm" type="video/webm" />
          <source src="/brand/hero.mp4" type="video/mp4" />
        </video>
      )}
    </>
  );
}
