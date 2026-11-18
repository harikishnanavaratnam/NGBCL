'use client';

export default function GlobalError({ reset }: { error: Error; reset: () => void }) {
  return (
    <html lang="en">
      <body style={{ background: '#101318', color: '#F5F7FA', fontFamily: 'system-ui, sans-serif', display: 'grid', placeItems: 'center', minHeight: '100vh', margin: 0 }}>
        <div style={{ textAlign: 'center' }}>
          <h1>Something went wrong</h1>
          <button type="button" onClick={reset} style={{ marginTop: 16, padding: '12px 20px', background: '#D8FF42', border: 0, borderRadius: 6, fontWeight: 600, cursor: 'pointer' }}>
            Try again
          </button>
        </div>
      </body>
    </html>
  );
}
