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
