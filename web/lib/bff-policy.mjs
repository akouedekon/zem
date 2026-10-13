export function allowedPath(path, method) {
  const uuid = '[0-9a-fA-F-]{36}';
  const rules = {
    GET: ['auth/me', 'driver/dossier', 'driver/documents', 'driver/card', 'driver/card/qr', 'admin/dossiers',
      `documents/${uuid}`, `admin/dossiers/${uuid}`, `admin/dossiers/${uuid}/documents`, `admin/dossiers/${uuid}/audit`, 'public/cards/[A-Za-z0-9_-]{43}'],
    POST: ['auth/login', 'auth/register', 'auth/logout', 'driver/dossier/submit', 'driver/documents/(IDENTITY|DRIVING_LICENSE|VEHICLE)',
      `admin/dossiers/${uuid}/decision`, `admin/dossiers/${uuid}/revoke`],
    PUT: ['driver/dossier']
  };
  return (rules[method] || []).some(rule => new RegExp(`^${rule}$`).test(path));
}
export function sameOrigin(origin, configured) {
  if (!origin || !configured) return false;
  try { return new URL(origin).origin === new URL(configured).origin; } catch { return false; }
}
export async function readLimitedBody(stream, maxBytes) {
  if (!stream) return new ArrayBuffer(0);
  const reader = stream.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > maxBytes) {
        await reader.cancel();
        throw new RangeError('Body too large');
      }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return bytes.buffer;
}
