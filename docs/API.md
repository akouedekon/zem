# API MVP

Base `/api`. JSON, dates ISO 8601 et UUID. Toutes les routes privées exigent `Authorization: Bearer <token>` ; l’API ne lit aucun cookie. Le web utilise `/bff` et un cookie HttpOnly. Aucun jeton n’est exposé au JavaScript du navigateur.

## Authentification

| Méthode | Route | Corps / résultat |
|---|---|---|
| POST | `/auth/register` | `{email,password}` → compte DRIVER (201). Aucun choix de rôle. |
| POST | `/auth/login` | `{email,password}` → `{token,expiresAt,user}`. Durée 8 h. |
| GET | `/auth/me` | Compte de la session. |
| POST | `/auth/logout` | Révocation serveur immédiate (204). |

Le mot de passe comporte au moins 12 caractères et au plus 72 octets UTF-8, limite de BCrypt. E-mail normalisé en minuscules. L’administrateur est initialisé par variables d’environnement uniquement ; une adresse DRIVER existante n’est jamais promue.

## Conducteur

| Méthode | Route | Corps / résultat |
|---|---|---|
| GET / PUT | `/driver/dossier` | Lecture / `{fullName,phone,plate}`. Un dossier par compte. |
| GET | `/driver/documents` | Métadonnées privées : `{id,kind,mediaType,byteSize}`. |
| POST | `/driver/documents/{kind}` | Multipart `file`, 5 Mo max. IDENTITY, DRIVING_LICENSE ou VEHICLE. |
| POST | `/driver/dossier/submit` | Exige les trois justificatifs. |
| GET | `/driver/card` | Carte privée, lien public opaque, dates et éventuelle révocation. |
| GET | `/driver/card/qr` | PNG du QR. |
| GET | `/documents/{id}` | Téléchargement en pièce jointe, propriétaire ou ADMIN uniquement. |

Seuls DRAFT et REJECTED sont modifiables. Une modification ramène à DRAFT. Une nouvelle soumission après rejet garde le même dossier et historise l’événement. SUBMITTED et APPROVED sont figés.

## Administration

| Méthode | Route | Corps / résultat |
|---|---|---|
| GET | `/admin/dossiers?status=SUBMITTED&page=0` | Pages de 50, ordre stable ; filtres DRAFT/SUBMITTED/APPROVED/REJECTED. |
| GET | `/admin/dossiers/{id}` | Dossier complet. |
| GET | `/admin/dossiers/{id}/documents` | Justificatifs à télécharger via `/documents/{id}`. |
| GET | `/admin/dossiers/{id}/audit` | Acteur, action, motif et date. |
| POST | `/admin/dossiers/{id}/decision` | `{approve:true}` ou `{approve:false,reason:"..."}`. Motif non vide pour rejet. |
| POST | `/admin/dossiers/{id}/revoke` | `{reason:"..."}`, carte invalidée immédiatement. |

La décision verrouille le dossier en base et vérifie SUBMITTED. Carte et journal sont enregistrés dans la même transaction. Deux décisions simultanées ne peuvent produire deux cartes. Une carte révoquée reste archivée ; aucun renouvellement dans ce MVP.

## Vérification publique

GET `/public/cards/{token}` → `{valid,status,issuedAt,expiresAt}`. Statuts VALID, REVOKED, EXPIRED, INVALID. Carte inconnue : 404. Aucune identité, téléphone, immatriculation ou pièce personnelle. QR : URL `/verify/{token}` avec 256 bits aléatoires, sans identifiant séquentiel.

Erreurs : 400 champs invalides, 401 session absente/expirée, 403 mauvais rôle, 404 ressource inexistante ou inaccessible, 409 transition/conflit, 413 fichier trop volumineux, 429 trop de tentatives. Nginx limite les requêtes publiques et d’authentification.
