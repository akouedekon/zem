# Courses et activité — première tranche fonctionnelle

Cette extension reprend les trois espaces : passager, conducteur et administration. Java 17 / Spring Boot 3, PostgreSQL / Flyway, Next.js et Flutter sont conservés. Aucun paiement, assurance ou Supabase n’est intégré. Aucun mandat officiel ni tarif réglementaire n’est présumé.

## Parcours

1. Créer un compte passager avec `/api/auth/register-passenger` (les comptes conducteurs continuent à utiliser `/api/auth/register`). Le rôle ne peut pas être choisi librement dans le JSON ; les deux routes imposent leur rôle.
2. Le conducteur suit le parcours existant de dossier, justificatifs et validation administrative. Une carte valide est nécessaire pour le service et l’acceptation.
3. Dans « Courses et revenus », autoriser la localisation puis passer en service. Le web ou l’écran de courses Android envoie sa position toutes les 30 secondes lorsqu’il est visible. Aucun suivi en arrière-plan n’est implémenté.
4. Le passager renseigne son départ (GPS possible), sa destination et ses coordonnées, puis demande une course. Pour le pilote : estimation à vol d’oiseau `500 + 200 × km`, arrondie à 100 FCFA ; limite de 100 km. Il ne s’agit ni d’un itinéraire routier ni d’un tarif réglementaire.
5. Les conducteurs en service avec une position de moins de deux minutes consultent les demandes dans une fenêtre de ±0,15 degré autour du départ. L’acceptation est volontaire. Le premier conducteur qui accepte obtient la course ; les autres reçoivent un conflit. Un passager et un conducteur ne peuvent avoir qu’une course active chacun.
6. Le conducteur démarre, puis termine la course et renseigne la recette déclarée en FCFA. Le passager peut consulter une position récente uniquement pendant sa course attribuée/en cours. Web : lien vers OpenStreetMap ; mobile : coordonnées et horodatage. Aucun historique GPS n’est enregistré.
7. Le conducteur déclare les recettes et le nombre de courses hors ZEM, ainsi que ses charges facultatives. La déclaration remplace celle du jour, sans addition de doublons.
8. L’administration consulte les agrégats journaliers, le nombre de déclarants et ceux dont les charges sont renseignées. Le parcours de revue des dossiers reste disponible.

## Transitions et sécurité

`REQUESTED → ACCEPTED → IN_PROGRESS → COMPLETED`. Annulation autorisée au passager propriétaire ou conducteur affecté uniquement avant le démarrage. Les transitions répétées/incompatibles sont refusées. Les événements sont conservés dans `mobility_audit`.

Migration V3 : rôle passager, service conducteur, courses, déclarations et audit. Les indexes uniques partiels et mises à jour conditionnelles protègent l’attribution concurrente. Les routes conducteur, passager et administrateur sont protégées par rôle ; une course ne peut pas être modifiée par un tiers. La position n’est pas exposée par l’observatoire administratif. Passer hors service efface la position en base ; une position ancienne n’est jamais renvoyée au passager ni utilisée pour attribuer une nouvelle demande.

Les justificatifs restent privés dans le stockage existant. Les autorisations de la passerelle BFF sont étendues explicitement, en conservant le cookie HttpOnly et le contrôle d’origine pour les écritures.

## Lecture des revenus

Les courses terminées sont comptées à la date du Bénin (`Africa/Porto-Novo`). Leur recette est déclarée par le conducteur : l’enregistrement du trajet ne certifie pas un encaissement. Les recettes hors ZEM restent séparées. Une absence de déclaration reste `null`, pas zéro. Le solde estimé n’est calculé que si les charges sont renseignées. Les agrégats de sources et de populations différentes ne sont pas présentés comme un bénéfice national.

## API ajoutée

- `POST /auth/register-passenger`.
- `GET/POST /passenger/rides`.
- `GET /driver/rides`, `GET /driver/requests`, `GET/PUT /driver/service`.
- `POST /driver/rides/{id}/accept`, `/start`, `/complete` (`amount` entier, 0–1 000 000).
- `POST /rides/{id}/cancel`, `GET /rides/{id}/location`.
- `GET /driver/earnings?day=YYYY-MM-DD`, `PUT /driver/declarations` (`day`, `offPlatformRevenue`, `offPlatformRides`, `expenses` facultatif).
- `GET /admin/mobility?day=YYYY-MM-DD`.
- `GET /admin/activity?day=YYYY-MM-DD&page=0` : tableau par conducteur, 50 lignes maximum par page, provenance et absence de charges visibles.

Toutes ces routes ont le préfixe `/api`. Les collections sont limitées à 50 courses et 30 demandes pour cette tranche.

## Installation et limites

Flyway applique V3 au démarrage de la nouvelle API ; sauvegarder la base avant la mise à jour. Déployer l’API et le web ensemble puis recompiler le mobile pour l’URL API. L’ancienne application de dossiers reste compatible avec les comptes conducteur. Aucun service payant supplémentaire n’est requis par le code.

Les comptes et le GPS sont destinés à des essais avec consentement. Cette tranche n’est pas une copie complète de Bolt/Heetch/Gozem : la recherche d’adresses, la cartographie intégrée, le routage, les notifications push, les appels masqués, l’assistance opérationnelle, la tarification institutionnelle et les contrôles territoriaux restent à développer. Le forfait gratuit ne garantit pas un service de transport disponible en continu : le pilote conserve un rafraîchissement HTTP et les limites documentées dans FREE_HOSTING.md.

La localisation mobile utilise [Geolocator](https://pub.dev/packages/geolocator/versions/14.0.2), version épinglée pour rester compatible avec le sélecteur de fichiers existant. Les autorisations Android sont limitées à la localisation pendant l’utilisation. Le pilote vise Android ; aucune validation iOS n’est revendiquée.
