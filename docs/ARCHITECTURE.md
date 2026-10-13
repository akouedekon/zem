# Architecture cible

Web Next.js et mobile Flutter communiquent exclusivement avec une API REST Spring Boot Java 17. PostgreSQL conserve les données, Flyway gère les migrations. Les justificatifs nouveaux sont privés dans PostgreSQL (bytea, migration V2). Hébergement prévu : Render Free pour l’API, Netlify Free pour Next.js et Neon Free pour PostgreSQL, sans VPS. Voir [FREE_HOSTING.md](FREE_HOSTING.md).

## Règles
- Jamais de validation de dossier par le conducteur.
- Contrôle d'autorisation côté API, pas uniquement dans l'interface.
- Aucun document personnel dans le QR ; uniquement un identifiant public opaque.
- Journaliser les décisions administratives.
- Sauvegardes chiffrées et test de restauration avant mise en production.
- Aucune intégration financière réelle dans le MVP 48h.

## Livrables
API, web, mobile Android testable, migrations, configurations gratuites, tests et documentation.

## Implémentation MVP

L’API utilise Spring Data JPA pour les comptes et la validation du mapping utilisateur, et JdbcTemplate pour les transitions SQL explicites et les verrous de dossier. Le gestionnaire de transactions Spring associe PostgreSQL, décision, carte et audit. Flyway reste seul responsable du schéma ; Hibernate ne génère aucune table.

Le web Next.js sert de passerelle de session : cookie HttpOnly côté navigateur, bearer opaque côté API. Il vérifie l’origine des mutations. Flutter communique directement avec `/api` via bearer conservé dans le stockage sécurisé Android. Aucun accès direct à PostgreSQL depuis les clients.

Le stockage privé par défaut est transactionnel dans PostgreSQL avec un budget global verrouillé. Le mode filesystem conserve la compatibilité locale des fichiers historiques. Le QR contient une URL avec 256 bits aléatoires ; la vérification publique ne retourne ni identité ni justificatif. Les limites du pilote et les choix métier encore à valider sont consignés dans SECURITY.md.
