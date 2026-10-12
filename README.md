# ZEM 2.0

MVP de formalisation des conducteurs de taxi-moto au Bénin.

## Architecture
- API : Java 17, Spring Boot 3, Spring Security, Spring Data JPA, Flyway
- Base : PostgreSQL
- Web : Next.js / TypeScript
- Mobile : Flutter
- Hébergement prévu : Render Free + Netlify Free + Neon Free, sans VPS
- Développement local : Docker Compose + Nginx

## Démarrage
Le parcours MVP est implémenté : compte conducteur, justificatifs privés, soumission, revue administrative, rejet/correction, validation, carte et QR public, révocation. Aucune intégration Supabase, paiement ou assurance.

Copier `.env.example` vers `.env`, renseigner le mot de passe PostgreSQL et le compte administrateur initial, puis lancer `docker compose up -d --build`. Interface locale : http://localhost:8088. Instructions locales : [runbook](docs/RUNBOOK.md). Mise en ligne, quotas et limites : [hébergement gratuit](docs/FREE_HOSTING.md). Les justificatifs nouveaux sont privés dans PostgreSQL, limités à 3 Mio par fichier et 150 Mio au total.

Ne pas utiliser avec des données personnelles réelles avant audit de sécurité et validation juridique.

## Priorité MVP
Inscription conducteur → dépôt de dossier → instruction administrative → carte numérique → vérification QR.

Voir [le plan](docs/STATUS.md).

Voir aussi [l’API](docs/API.md), [les contrôles et limites de sécurité](docs/SECURITY.md), et [la CI](.github/workflows/verify.yml). Le mobile Flutter est l’espace conducteur ; la revue administrative s’effectue sur le web.
