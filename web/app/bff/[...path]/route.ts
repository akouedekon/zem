import { NextRequest, NextResponse } from 'next/server';
import { allowedPath, sameOrigin, readLimitedBody } from '@/lib/bff-policy.mjs';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
const cookieName = 'zem_session';
const upstream = process.env.API_INTERNAL_URL || 'http://localhost:8080';
const origin = process.env.WEB_ORIGIN || 'http://localhost:3000';

async function handle(req: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const path = (await context.params).path.join('/');
  const method = req.method;
  if (!allowedPath(path, method)) return NextResponse.json({ message: 'Route inconnue' }, { status: 404 });
  if (method !== 'GET' && !sameOrigin(req.headers.get('origin'), origin))
    return NextResponse.json({ message: 'Origine refusée' }, { status: 403 });
  const token = req.cookies.get(cookieName)?.value;
  const headers = new Headers();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const type = req.headers.get('content-type');
  if (type) headers.set('Content-Type', type);
  const declaredLength = Number(req.headers.get('content-length') || 0);
  if (declaredLength > 6 * 1024 * 1024) return NextResponse.json({ message: 'Fichier trop volumineux' }, { status: 413 });
  let body: ArrayBuffer | undefined;
  try { body = method === 'GET' ? undefined : await readLimitedBody(req.body, 6 * 1024 * 1024); }
  catch (e) { return NextResponse.json({ message: e instanceof RangeError ? 'Fichier trop volumineux' : 'Corps de requête invalide' }, { status: e instanceof RangeError ? 413 : 400 }); }
  let response: Response;
  try {
    response = await fetch(`${upstream}/api/${path}${req.nextUrl.search}`, { method, headers, body, cache: 'no-store', signal: AbortSignal.timeout(30000), redirect: 'error' });
  } catch {
    return NextResponse.json({ message: 'Service temporairement indisponible' }, { status: 503 });
  }
  if (path === 'auth/login' && response.ok) {
    const data = await response.json();
    if (token) await fetch(`${upstream}/api/auth/logout`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(5000) }).catch(() => {});
    const result = NextResponse.json({ user: data.user, expiresAt: data.expiresAt }, { headers: { 'Cache-Control': 'no-store' } });
    result.cookies.set(cookieName, data.token, { httpOnly: true, secure: new URL(origin).protocol === 'https:', sameSite: 'strict', path: '/', maxAge: 8 * 3600 });
    return result;
  }
  const result = new NextResponse(response.status === 204 ? null : response.body, { status: response.status });
  for (const h of ['content-type', 'content-disposition', 'retry-after']) {
    const value = response.headers.get(h); if (value) result.headers.set(h, value);
  }
  result.headers.set('Cache-Control', 'no-store');
  result.headers.set('X-Content-Type-Options', 'nosniff');
  if ((path === 'auth/logout' && response.ok) || response.status === 401) result.cookies.delete(cookieName);
  return result;
}
export { handle as GET, handle as POST, handle as PUT };
