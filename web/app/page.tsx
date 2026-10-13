'use client';
import { useCallback, useEffect, useState, FormEvent } from 'react';
import Mobility from './mobility';
import RouteVisual from './route-visual';

type User = { id: string; email: string; role: 'DRIVER' | 'ADMIN' | 'PASSENGER' };
type Dossier = { id: string; fullName: string; phone: string; plate: string; status: string; rejectionReason: string | null };
type Doc = { id: string; kind: string; mediaType: string; byteSize: number };
type Card = { id: string; verificationUrl: string; expiresAt: string; revokedAt: string | null };
type Audit = { actor_id: string; action: string; reason: string | null; created_at: string };
const kinds: Record<string, string> = { IDENTITY: 'Pièce d’identité', DRIVING_LICENSE: 'Permis de conduire', VEHICLE: 'Justificatif du véhicule' };
const statuses: Record<string, string> = { DRAFT: 'Brouillon', SUBMITTED: 'En cours de revue', APPROVED: 'Validé', REJECTED: 'À corriger' };
class ApiError extends Error { constructor(message: string, public status: number) { super(message); } }
async function api<T>(path: string, method = 'GET', body?: object | FormData): Promise<T> {
  const form = body instanceof FormData;
  const res = await fetch(`/bff/${path}`, { method, headers: body && !form ? { 'Content-Type': 'application/json' } : {}, body: form ? body : body ? JSON.stringify(body) : undefined });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new ApiError(data.message || (res.status === 401 ? 'Connectez-vous pour continuer' : 'La demande a été refusée'), res.status);
  }
  return res.status === 204 ? undefined as T : await res.json();
}
export default function Home() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [register, setRegister] = useState(false);
  const [accountRole, setAccountRole] = useState('PASSENGER');
  const [dossier, setDossier] = useState<Dossier | null>(null);
  const [docs, setDocs] = useState<Doc[]>([]);
  const [card, setCard] = useState<Card | null>(null);
  const [queue, setQueue] = useState<Dossier[]>([]);
  const [filter, setFilter] = useState('SUBMITTED');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Dossier | null>(null);
  const [reviewDocs, setReviewDocs] = useState<Doc[]>([]);
  const [audit, setAudit] = useState<Audit[]>([]);
  const [reason, setReason] = useState('');
  const error = (e: unknown) => { setMessage(e instanceof Error ? e.message : 'Une erreur est survenue'); if (e instanceof ApiError && e.status === 401) setUser(null); };
  const refresh = useCallback(async (u: User) => {
    if (u.role === 'DRIVER') {
      try {
        setDossier(await api<Dossier>('driver/dossier')); setDocs(await api<Doc[]>('driver/documents'));
        try { setCard(await api<Card>('driver/card')); } catch (e) { if (!(e instanceof ApiError) || e.status !== 404) throw e; setCard(null); }
      } catch (e) { if (e instanceof ApiError && e.status === 404) { setDossier(null); setDocs([]); setCard(null); } else throw e; }
    } else if (u.role === 'ADMIN') setQueue(await api<Dossier[]>(`admin/dossiers?status=${filter}&page=${page}`));
  }, [filter, page]);
  useEffect(() => { api<User>('auth/me').then(setUser).catch(e => { if (!(e instanceof ApiError) || e.status !== 401) setMessage(e.message); }).finally(() => setLoading(false)); }, []);
  useEffect(() => { if (user) refresh(user).catch(error); }, [user, refresh]);
  async function action(fn: () => Promise<void>) { setBusy(true); setMessage(''); try { await fn(); } catch (e) { error(e); } finally { setBusy(false); } }
  async function signIn(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const values = new FormData(e.currentTarget);
    await action(async () => {
      const body = { email: values.get('email'), password: values.get('password') };
      if (register) await api(accountRole === 'PASSENGER' ? 'auth/register-passenger' : 'auth/register', 'POST', body);
      const result = await api<{ user: User }>('auth/login', 'POST', body); setUser(result.user); setRegister(false);
    });
  }
  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const values = new FormData(e.currentTarget);
    await action(async () => { await api('driver/dossier', 'PUT', Object.fromEntries(values)); await refresh(user!); setMessage('Dossier enregistré. Ajoutez vos justificatifs avant de soumettre.'); });
  }
  async function select(d: Dossier) {
    await action(async () => {
      const detail = await api<Dossier>(`admin/dossiers/${d.id}`);
      const documents = await api<Doc[]>(`admin/dossiers/${d.id}/documents`);
      const history = await api<Audit[]>(`admin/dossiers/${d.id}/audit`);
      setSelected(detail); setReviewDocs(documents); setAudit(history); setReason('');
    });
  }
  async function decide(approve: boolean) {
    if (!selected) return;
    await action(async () => { await api(`admin/dossiers/${selected.id}/decision`, 'POST', { approve, reason }); setSelected(null); await refresh(user!); setMessage(approve ? 'Dossier validé : carte numérique délivrée.' : 'Dossier rejeté : le conducteur peut le corriger.'); });
  }
  const editable = !dossier || ['DRAFT', 'REJECTED'].includes(dossier.status);
  return <main className={user ? 'workspace' : 'welcome'}>
    <header><a className="brand" href="/">ZEM<span>2.0</span></a><span className="tag">Mobilité · Bénin</span>{user && <button className="quiet" disabled={busy} onClick={() => action(async () => { await api('auth/logout', 'POST'); setUser(null); setDossier(null); setCard(null); setSelected(null); })}>Se déconnecter</button>}</header>
    <section className="intro"><div><p className="eyebrow">MOBILITÉ · BÉNIN</p><h1>{user?.role === 'ADMIN' ? 'Piloter la mobilité.' : user?.role === 'DRIVER' ? 'Votre activité, en mouvement.' : 'Votre zem, votre trajet.'}</h1><p>{user?.role === 'ADMIN' ? 'Suivez les courses et les recettes déclarées, et instruisez les dossiers conducteurs.' : 'Une course de proximité. Des conducteurs habilités. Une activité mieux reconnue.'}</p><div className="intro-chips"><span>Passager</span><span>Conducteur</span><span>Administration</span></div></div>{!user && <RouteVisual />}</section>
    <aside className="notice">Version pilote · Aucune couverture d’assurance ni paiement. Utilisez des données de démonstration.</aside>
    {message && <div className="message" role="status">{message}</div>}
    {user && <Mobility key={user.id} role={user.role} />}
    {loading ? <p>Chargement…</p> : !user ? <section className="panel auth"><h2>{register ? 'Créer mon compte' : 'Accéder à mon espace'}</h2><form onSubmit={signIn}>
      {register && <label>Mon espace<select value={accountRole} onChange={e => setAccountRole(e.target.value)}><option value="PASSENGER">Passager</option><option value="DRIVER">Conducteur</option></select></label>}
      <label>Adresse e-mail<input name="email" type="email" required maxLength={254} autoComplete="email" /></label>
      <label>Mot de passe<input name="password" type="password" required minLength={register ? 12 : undefined} maxLength={72} autoComplete={register ? 'new-password' : 'current-password'} /></label>
      {register && <p className="muted">12 caractères minimum. Choisissez votre espace passager ou conducteur.</p>}
      <button disabled={busy}>{busy ? 'Veuillez patienter…' : register ? 'Créer le compte' : 'Se connecter'}</button>
    </form><button className="quiet" disabled={busy} onClick={() => { setRegister(!register); setMessage(''); }}>{register ? 'J’ai déjà un compte' : 'Créer un compte'}</button><p className="muted">Votre espace est protégé. Vos justificatifs restent privés.</p></section>
    : user.role === 'PASSENGER' ? null : user.role === 'DRIVER' ? <div className="grid">
      <section className="panel"><div className="section-title"><h2>Mon dossier</h2><span className={`badge ${dossier?.status || ''}`}>{statuses[dossier?.status || 'DRAFT']}</span></div>
        {dossier?.rejectionReason && <p className="message">Motif du rejet : {dossier.rejectionReason}</p>}
        <form key={dossier ? `${dossier.id}-${dossier.status}` : 'new'} onSubmit={save}><fieldset disabled={busy || !editable}>
          <label>Nom complet<input name="fullName" defaultValue={dossier?.fullName} required maxLength={120} /></label>
          <label>Téléphone<input name="phone" type="tel" defaultValue={dossier?.phone} required pattern="\+?[0-9 ]{8,24}" maxLength={24} placeholder="+229…" /></label>
          <label>Immatriculation<input name="plate" defaultValue={dossier?.plate} required maxLength={24} /></label>
          <button>Enregistrer le dossier</button>
        </fieldset></form>
        {!editable && <p className="muted">Le dossier est figé pendant la revue et après validation.</p>}
      </section>
      <section className="panel"><h2>Mes justificatifs</h2><p className="muted">PDF, JPEG ou PNG · 4 Mo maximum par fichier. Accès privé.</p>
        {Object.entries(kinds).map(([kind, label]) => { const doc = docs.find(d => d.kind === kind); return <div className="document" key={kind}>
          <strong>{label}</strong>{doc ? <a href={`/bff/documents/${doc.id}`}>Télécharger le document</a> : <span className="muted">À ajouter</span>}
          <label className="file-label">{doc ? 'Remplacer' : 'Choisir le document'}<input type="file" aria-label={label} accept="application/pdf,image/jpeg,image/png" disabled={busy || !editable || !dossier} onChange={e => {
            const file = e.target.files?.[0]; e.target.value = ''; if (!file) return;
            if (file.size > 4 * 1024 * 1024) { setMessage('Document trop volumineux (4 Mo maximum)'); return; }
            const form = new FormData(); form.set('file', file);
            action(async () => { await api(`driver/documents/${kind}`, 'POST', form); await refresh(user); setMessage('Justificatif enregistré.'); });
          }} /></label></div>; })}
        <button disabled={busy || !editable || docs.length !== 3} onClick={() => action(async () => { await api('driver/dossier/submit', 'POST'); await refresh(user); setMessage('Dossier transmis à l’administration.'); })}>Soumettre à la revue</button>
      </section>
      {card && <section className="panel card"><p className="eyebrow">CARTE NUMÉRIQUE ZEM</p><h2>{dossier?.fullName}</h2><p>{dossier?.plate}</p><p>{card.revokedAt ? 'Carte révoquée' : `Échéance : ${new Date(card.expiresAt).toLocaleDateString('fr-FR')}`}</p>
        {/* Authenticated image request goes through the HttpOnly-cookie BFF. */}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/bff/driver/card/qr" alt="QR de vérification de la carte" width={240} height={240} />
        <a href={card.verificationUrl}>Vérifier la validité</a><p className="muted">Le QR contient uniquement un lien opaque. Aucun justificatif personnel.</p></section>}
    </div> : <div className="grid admin-grid">
      <section className="panel"><h2>File administrative</h2><label>Statut<select aria-label="Statut" value={filter} disabled={busy} onChange={e => { setFilter(e.target.value); setPage(0); setSelected(null); }}>{Object.entries(statuses).map(([s, label]) => <option key={s} value={s}>{label}</option>)}</select></label>
        {queue.length === 0 && <p className="muted">Aucun dossier dans cette file.</p>}
        {queue.map(d => <button className="queue-item" key={d.id} disabled={busy} onClick={() => select(d)}><strong>{d.fullName}</strong><span>{d.plate} · {statuses[d.status]}</span></button>)}
        <div className="row"><button className="quiet" disabled={busy || page === 0} onClick={() => setPage(page - 1)}>Précédent</button><span>Page {page + 1}</span><button className="quiet" disabled={busy || queue.length < 50} onClick={() => setPage(page + 1)}>Suivant</button></div>
      </section>
      <section className="panel"><h2>Instruction</h2>{!selected ? <p className="muted">Sélectionnez un dossier pour examiner ses justificatifs.</p> : <>
        <h3>{selected.fullName}</h3><p>{selected.phone} · {selected.plate}</p><span className={`badge ${selected.status}`}>{statuses[selected.status]}</span>
        {selected.rejectionReason && <p>Motif : {selected.rejectionReason}</p>}
        {reviewDocs.map(doc => <div className="document" key={doc.id}><strong>{kinds[doc.kind]}</strong><a href={`/bff/documents/${doc.id}`}>Télécharger pour examen</a></div>)}
        {['SUBMITTED', 'APPROVED'].includes(selected.status) && <label>Motif {selected.status === 'SUBMITTED' ? '(obligatoire pour rejeter)' : 'de révocation'}<textarea value={reason} onChange={e => setReason(e.target.value)} maxLength={1000} /></label>}
        {selected.status === 'SUBMITTED' && <div className="row"><button disabled={busy} onClick={() => decide(true)}>Valider et délivrer la carte</button><button className="danger" disabled={busy || !reason.trim()} onClick={() => decide(false)}>Rejeter</button></div>}
        {selected.status === 'APPROVED' && <button className="danger" disabled={busy || !reason.trim()} onClick={() => action(async () => { await api(`admin/dossiers/${selected.id}/revoke`, 'POST', { reason }); setSelected(null); await refresh(user); setMessage('Carte révoquée.'); })}>Révoquer la carte</button>}
        <h3>Historique des décisions</h3>{audit.map((event, index) => <p className="muted" key={index}>{new Date(event.created_at).toLocaleString('fr-FR')} · {event.action}{event.reason ? ` · ${event.reason}` : ''}</p>)}
      </>}</section>
    </div>}
    <footer>ZEM 2.0 · Formalisation des conducteurs de taxi-moto</footer>
  </main>;
}
