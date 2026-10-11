# Architecture cible

Web Next.js et mobile Flutter communiquent exclusivement avec une API REST Spring Boot Java 17. PostgreSQL conserve les données, Flyway gère les migrations. Les justificatifs sont privés (MinIO ou volume privé). Le VPS héberge les services derrière Nginx HTTPS.

## Règles
- Jamais de validation de dossier par le conducteur.
- Contrôle d'autorisation côté API, pas uniquement dans l'interface.
- Aucun document personnel dans le QR ; uniquement un identifiant public opaque.
- Journaliser les décisions administratives.
- Sauvegardes chiffrées et test de restauration avant mise en production.
- Aucune intégration financière réelle dans le MVP 48h.

## Livrables
API, web, mobile Android testable, migrations, scripts VPS, tests et documentation.
