import { NextResponse, type NextRequest } from 'next/server';

/**
 * Coarse route gate for staff surfaces. The API sets a non-sensitive `ngbcl_session` hint
 * cookie next to the httpOnly refresh token; without it we send people to /login.
 * This is UX only — every API call is authorised server-side (SRS §2.1).
 */
export function proxy(request: NextRequest) {
  if (!request.cookies.has('ngbcl_session')) {
    const url = request.nextUrl.clone();
    url.pathname = '/login';
    url.search = `?next=${encodeURIComponent(request.nextUrl.pathname + request.nextUrl.search)}`;
    return NextResponse.redirect(url);
  }
  return NextResponse.next();
}

export const config = {
  // Public auction display (/display/auction) is intentionally NOT gated.
  matcher: ['/admin/:path*', '/console/:path*', '/score/:path*'],
};
