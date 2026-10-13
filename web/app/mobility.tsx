'use client';
import { FormEvent, useCallback, useEffect, useState } from 'react';

type Ride = { id: string; pickup: string; destination: string; status: string; estimated_fare: number; declared_fare: number | null };
type Earnings = { recorded_revenue: number; offPlatformRevenue: number | null; expenses: number | null; trackedRevenue: number; estimatedBalance: number | null; declarationPresent: boolean };
type Overview = { onlineDrivers: number; courses: { completed_rides: number; recorded_revenue: number }; declarations: { declaring_drivers: number; expenses_known: number; off_platform_revenue: number; declared_expenses: number } };
type DriverActivity = { driver_id: string; full_name: string; plate: string; recorded_rides: number; recorded_revenue: number; off_platform_revenue: number | null; expenses: number | null; estimated_balance: number | null };
const labels: Record<string, string> = { REQUESTED: 'Recherche de conducteur', ACCEPTED: 'Conducteur attribué', IN_PROGRESS: 'En cours', COMPLETED: 'Terminée', CANCELLED: 'Annulée' };
const money = (n: number | null) => n == null ? 'Non renseigné' : `${n.toLocaleString('fr-FR')} FCFA`;
function today() { return new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Porto-Novo' }).format(new Date()); }
async function request<T>(path: string, method = 'GET', body?: object): Promise<T> {
  const res = await fetch(`/bff/${path}`, { method, headers: body ? { 'Content-Type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || `Requête refusée (${res.status})`);
  return data;
}
function position(): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => navigator.geolocation ? navigator.geolocation.getCurrentPosition(resolve, e => reject(new Error(e.code === 1 ? 'Autorisez la localisation pour passer en service' : 'Position indisponible. Vérifiez votre GPS et réessayez.')), { timeout: 15000, maximumAge: 10000 }) : reject(new Error('Localisation indisponible')));
}
export default function Mobility({ role }: { role: string }) {
  const [rides, setRides] = useState<Ride[]>([]), [queue, setQueue] = useState<Ride[]>([]);
  const [online, setOnline] = useState(false), [day, setDay] = useState(today);
  const [earnings, setEarnings] = useState<Earnings | null>(null), [overview, setOverview] = useState<Overview | null>(null);
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [lat, setLat] = useState(''), [lon, setLon] = useState('');
  const [destination, setDestination] = useState(''), [destLat, setDestLat] = useState(''), [destLon, setDestLon] = useState('');
  const [location, setLocation] = useState<{ latitude: number; longitude: number; updated_at: string } | null>(null);
  const [drivers, setDrivers] = useState<DriverActivity[]>([]), [activityPage, setActivityPage] = useState(0);
  const refresh = useCallback(async () => {
    if (role === 'ADMIN') { setOverview(await request<Overview>(`admin/mobility?day=${day}`)); setDrivers(await request<DriverActivity[]>(`admin/activity?day=${day}&page=${activityPage}`)); return; }
    const list = await request<Ride[]>(`${role === 'DRIVER' ? 'driver' : 'passenger'}/rides`); setRides(list);
    if (role === 'DRIVER') {
      const s = await request<{ online: boolean }>('driver/service'); setOnline(s.online);
      setEarnings(await request<Earnings>(`driver/earnings?day=${day}`));
      setQueue(s.online ? await request<Ride[]>('driver/requests') : []);
    } else {
      const active = list.find(r => ['ACCEPTED', 'IN_PROGRESS'].includes(r.status));
      setLocation(active ? await request<typeof location>(`rides/${active.id}/location`).catch(() => null) : null);
    }
  }, [role, day, activityPage]);
  useEffect(() => { let active = true; const run = () => refresh().catch(e => { if (active) setError(e.message); }); run(); const timer = setInterval(run, 15000); return () => { active = false; clearInterval(timer); }; }, [refresh]);
  async function action(fn: () => Promise<unknown>) { setBusy(true); setError(''); try { await fn(); await refresh(); } catch (e) { setError(e instanceof Error ? e.message : 'Connexion impossible'); } finally { setBusy(false); } }
  async function share() { const p = await position(); await request('driver/service', 'PUT', { online: true, latitude: p.coords.latitude, longitude: p.coords.longitude }); }
  // Only while this space is visible and the driver explicitly opted into service.
  useEffect(() => { if (role !== 'DRIVER' || !online) return; const timer = setInterval(() => { if (document.visibilityState === 'visible') share().catch(() => setError('Position non actualisée. Actualisez ou passez hors service.')); }, 30000); return () => clearInterval(timer); }, [role, online]);
  async function booking(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget);
    await action(() => request('passenger/rides', 'POST', { pickup: f.get('pickup'), destination: f.get('destination'), pickupLat: Number(lat), pickupLon: Number(lon), destinationLat: Number(f.get('destinationLat')), destinationLon: Number(f.get('destinationLon')) }));
  }
  return <section className="panel mobility"><div className="section-title"><h2>{role === 'ADMIN' ? 'Activité et recettes' : role === 'DRIVER' ? 'Courses et revenus' : 'Commander un zem'}</h2><button className="quiet" disabled={busy} onClick={() => action(refresh)}>Actualiser</button></div>
    {error && <p role="alert" className="message">{error}</p>}
    {role === 'PASSENGER' && !rides.some(r => ['REQUESTED', 'ACCEPTED', 'IN_PROGRESS'].includes(r.status)) && <form onSubmit={booking}><fieldset disabled={busy}>
      <label>Lieu de départ<input name="pickup" required maxLength={160} placeholder="Place de l’Étoile Rouge" /></label>
      <button type="button" className="quiet" onClick={() => action(async () => { const p = await position(); setLat(String(p.coords.latitude)); setLon(String(p.coords.longitude)); })}>Utiliser ma position comme départ</button>
      <div className="grid"><label>Latitude de départ<input type="number" step="any" min={-90} max={90} required value={lat} onChange={e => setLat(e.target.value)} /></label><label>Longitude de départ<input type="number" step="any" min={-180} max={180} required value={lon} onChange={e => setLon(e.target.value)} /></label></div>
      <p className="muted">Choisir un repère à Cotonou, ou renseigner une autre destination :</p>
      <div className="destination-options">{[{name:'Étoile Rouge',lat:6.373,lon:2.406},{name:'Marché Dantokpa',lat:6.383,lon:2.434},{name:'Place de l’Amazone',lat:6.349,lon:2.397}].map(p => <button type="button" className="quiet" key={p.name} onClick={() => { setDestination(p.name); setDestLat(String(p.lat)); setDestLon(String(p.lon)); }}>{p.name}</button>)}</div>
      <label>Destination<input name="destination" value={destination} onChange={e => { setDestination(e.target.value); setDestLat(''); setDestLon(''); }} required maxLength={160} placeholder="Marché Dantokpa" /></label>
      <div className="grid"><label>Latitude de destination<input name="destinationLat" value={destLat} onChange={e => setDestLat(e.target.value)} type="number" step="any" min={-90} max={90} required /></label><label>Longitude de destination<input name="destinationLon" value={destLon} onChange={e => setDestLon(e.target.value)} type="number" step="any" min={-180} max={180} required /></label></div>
      <p className="muted">Les repères sont approximatifs. Confirmez le point de rendez-vous avec le conducteur.</p>
      <p className="muted">Pilote : destination par coordonnées, estimation à vol d’oiseau (500 FCFA + 200/km, arrondi à 100). Aucun débit. Pas de tarif réglementaire ni de calcul routier.</p><button>Demander une course</button>
    </fieldset></form>}
    {role === 'DRIVER' && <><p><strong>{online ? 'En service' : 'Hors service'}</strong> · position partagée uniquement en service, avec le passager de votre course.</p><button disabled={busy} onClick={() => action(() => online ? request('driver/service', 'PUT', { online: false }) : share())}>{online ? 'Passer hors service et effacer ma position' : 'Passer en service avec ma position'}</button>
      {queue.length === 0 && <p className="empty-state">{online ? 'Aucune demande à proximité pour le moment. Les demandes sont actualisées automatiquement.' : 'Passez en service avec une carte valide pour recevoir des demandes à proximité.'}</p>}
      {queue.map(r => <article className="document" key={r.id}><strong>{r.pickup} → {r.destination}</strong><p>Estimation : {money(r.estimated_fare)}</p><button disabled={busy} onClick={() => action(() => request(`driver/rides/${r.id}/accept`, 'POST'))}>Accepter</button></article>)}
    </>}
    {location && <p>Position récente du conducteur : <a rel="noreferrer" target="_blank" href={`https://www.openstreetmap.org/?mlat=${location.latitude}&mlon=${location.longitude}#map=16/${location.latitude}/${location.longitude}`}>Voir sur la carte</a> · {new Date(location.updated_at).toLocaleTimeString('fr-FR')}</p>}
    {rides.map(r => <article className="document" key={r.id}><strong>{r.pickup} → {r.destination}</strong><p>{labels[r.status]} · {r.status === 'COMPLETED' ? 'Recette déclarée' : 'Estimation'} : {money(r.declared_fare ?? r.estimated_fare)}</p>
      {['REQUESTED', 'ACCEPTED'].includes(r.status) && <button className="quiet" disabled={busy} onClick={() => action(() => request(`rides/${r.id}/cancel`, 'POST'))}>Annuler la course</button>}
      {role === 'DRIVER' && r.status === 'ACCEPTED' && <button disabled={busy} onClick={() => action(() => request(`driver/rides/${r.id}/start`, 'POST'))}>Démarrer la course</button>}
      {role === 'DRIVER' && r.status === 'IN_PROGRESS' && <form onSubmit={e => { e.preventDefault(); const f = new FormData(e.currentTarget); action(() => request(`driver/rides/${r.id}/complete`, 'POST', { amount: Number(f.get('amount')) })); }}><label>Montant reçu en espèces (déclaration)<input name="amount" type="number" min={0} max={1000000} defaultValue={r.estimated_fare} required /></label><button disabled={busy}>Terminer et enregistrer la recette</button></form>}
    </article>)}
    {role !== 'PASSENGER' && <label>Journée d’activité (Bénin)<input type="date" value={day} max={today()} onChange={e => { if (e.target.value) setDay(e.target.value); }} /></label>}
    {earnings && <><div className="grid"><article><h3>Courses enregistrées</h3><p>{money(earnings.recorded_revenue)}</p></article><article><h3>Recettes hors ZEM déclarées</h3><p>{money(earnings.offPlatformRevenue)}</p></article><article><h3>Charges déclarées</h3><p>{money(earnings.expenses)}</p></article><article><h3>Solde estimé</h3><p>{earnings.estimatedBalance == null ? 'Non calculable sans charges' : money(earnings.estimatedBalance)}</p></article></div>
      <form key={day} onSubmit={e => { e.preventDefault(); const f = new FormData(e.currentTarget); action(() => request('driver/declarations', 'PUT', { day, offPlatformRevenue: Number(f.get('revenue')), offPlatformRides: Number(f.get('count')), expenses: f.get('expenses') === '' ? null : Number(f.get('expenses')) })); }}><fieldset disabled={busy}><legend>Déclaration journalière · remplace la précédente</legend><label>Recettes hors ZEM (FCFA)<input name="revenue" type="number" min={0} max={1000000} required defaultValue={earnings.offPlatformRevenue ?? ''} /></label><label>Nombre de courses hors ZEM<input name="count" type="number" min={0} max={1000} required /></label><label>Charges (FCFA) · vide si inconnues<input name="expenses" type="number" min={0} max={1000000} defaultValue={earnings.expenses ?? ''} /></label><p className="muted">Ne comptez pas à nouveau les courses ZEM. Les montants restent déclaratifs, sans encaissement vérifié.</p><button>Enregistrer la déclaration</button></fieldset></form></>}
    {overview && <div className="grid"><article><h3>Conducteurs en service</h3><p>{overview.onlineDrivers}</p></article><article><h3>Courses terminées</h3><p>{overview.courses.completed_rides} · {money(overview.courses.recorded_revenue)}</p></article><article><h3>Hors ZEM déclaré</h3><p>{money(overview.declarations.off_platform_revenue)} · {overview.declarations.declaring_drivers} déclarants</p></article><article><h3>Charges renseignées</h3><p>{money(overview.declarations.declared_expenses)} · {overview.declarations.expenses_known} déclarants</p></article><p>Vue agrégée : les absences de déclaration ne sont pas des zéros. Ces chiffres ne représentent pas l’ensemble des revenus du pays.</p></div>}
    {role === 'ADMIN' && <><h3>Activité par conducteur · accès administratif</h3><div style={{ overflowX: 'auto' }}><table><thead><tr><th>Conducteur</th><th>Courses ZEM</th><th>Recettes ZEM déclarées</th><th>Hors ZEM déclaré</th><th>Charges déclarées</th><th>Solde estimé</th></tr></thead><tbody>{drivers.map(d => <tr key={d.driver_id}><td>{d.full_name}<br />{d.plate}</td><td>{d.recorded_rides}</td><td>{money(d.recorded_revenue)}</td><td>{money(d.off_platform_revenue)}</td><td>{money(d.expenses)}</td><td>{d.estimated_balance == null ? 'Non calculable' : money(d.estimated_balance)}</td></tr>)}</tbody></table></div><div className="row"><button disabled={busy || activityPage === 0} onClick={() => setActivityPage(activityPage - 1)}>Activité précédente</button><span>Page {activityPage + 1}</span><button disabled={busy || drivers.length < 50} onClick={() => setActivityPage(activityPage + 1)}>Activité suivante</button></div></>}
  </section>;
}
