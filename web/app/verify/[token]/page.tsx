export const dynamic = 'force-dynamic';
type Verification = { valid: boolean; status: string; issuedAt: string; expiresAt: string };
export default async function Verify({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  let data: Verification | null = null;
  let unavailable = false;
  if (/^[A-Za-z0-9_-]{43}$/.test(token)) {
    try {
      const res = await fetch(`${process.env.API_INTERNAL_URL || 'http://localhost:8080'}/api/public/cards/${token}`, { cache: 'no-store', signal: AbortSignal.timeout(10000) });
      if (res.ok) data = await res.json(); else if (res.status !== 404) unavailable = true;
    } catch { unavailable = true; }
  }
  return <main><header><a className="brand" href="/">ZEM<span>2.0</span></a></header><section className="intro"><p className="eyebrow">VÉRIFICATION PUBLIQUE</p><h1>{unavailable ? 'Vérification indisponible' : data?.valid ? 'Carte valide' : 'Carte non valide'}</h1><p>{unavailable ? 'Réessayez plus tard. La validité ne peut pas être confirmée actuellement.' : !data ? 'Cette carte est inconnue.' : data.status === 'REVOKED' ? 'Cette carte a été révoquée.' : data.status === 'EXPIRED' ? 'Cette carte a expiré.' : data.valid ? 'La carte est active dans le registre ZEM.' : 'La carte n’est pas active.'}</p></section>
    {data && <section className="panel"><p>Délivrée le {new Date(data.issuedAt).toLocaleDateString('fr-FR')}</p><p>Échéance : {new Date(data.expiresAt).toLocaleDateString('fr-FR')}</p><p className="muted">Cette vérification ne présente aucune identité ni document personnel. Elle ne constitue ni une assurance ni une autorisation réglementaire.</p></section>}
    <footer>Version pilote · ZEM 2.0</footer></main>;
}
