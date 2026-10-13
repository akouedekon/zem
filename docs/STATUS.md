# État de livraison

## Initialisation
- [x] Dépôt GitHub identifié (branche main, dépôt initialement vide).
- [x] Documentation de cadrage et fichiers initiaux.
- [x] API Java 17 / Spring Boot démarrable et testée.
- [x] Migrations PostgreSQL appliquées en tests isolés.
- [x] Authentification, sessions révocables et contrôle des rôles.
- [x] Web Next.js compilé ; espaces conducteur/administration et vérification publique implémentés.
- [x] Client conducteur Flutter implémenté ; analyse et tests passés.
- [x] Flux de validation, rejet/correction, carte, QR et révocation.
- [x] Six tests d’intégration backend avec PostgreSQL réel.
- [x] Parcours navigateur complet confirmé après correction du filtre administratif.
- [x] Compose/Nginx et restauration de sauvegarde vérifiés en CI.
- [ ] Déploiement gratuit Render / Netlify / Neon et vérification distante (VPS abandonné).
- [x] APK Android debug compilé pour émulateur ; pas de test sur appareil ni de release signée.

## Vérifications exécutées le 12 octobre 2026

- JDK Temurin 17.0.17, Spring Boot 3.5.16 : 6 tests passés, aucune erreur ; PostgreSQL embarqué 14.21 et Flyway V1 appliquée. Vérifications d’accès, dossier figé, rejet et resoumission, concurrence des décisions, émission/lecture du QR, expiration et révocation.
- Next.js 16.4.0 : 3 tests de la passerelle web, TypeScript et build de production passés. Le plafond de lecture des uploads en streaming est testé sans Content-Length. Audit npm des dépendances de production : 0 vulnérabilité signalée à la date du contrôle.
- Flutter 3.44.0 : analyse sans problème, 4 tests passés. APK debug créé via Gradle avec Temurin 17 après correction du JDK local et du manifeste debug.
- Playwright : parcours complet passé contre l’API HTTP réelle, jusqu’à la révocation et déconnexion. Le cookie HttpOnly, le refus d’une origine tierce et l’absence de données personnelles sur la vérification publique sont contrôlés.
- GitHub Actions, commit `191aec8` : backend et web passés ; Compose/Nginx démarrés avec PostgreSQL 17.11, parcours navigateur passé, archive DB + documents chiffrée avec age et restaurée dans une base isolée, configuration TLS validée avec un certificat synthétique. La limite Nginx cible les routes API et ne compte plus les fichiers statiques.

Résultats CI : [Verify MVP](https://github.com/akouedekon/zem/actions/runs/38208482264), commit `191aec8`. Les quatre jobs backend, web, mobile et compose-e2e ont réussi. Android : analyse, 4 tests et compilation APK debug passés sur Linux, artefact disponible dans la CI. Ce compte rendu est une mise à jour documentaire du code vérifié.

Aucun déploiement VPS, certificat réel, sauvegarde de production ou test sur téléphone n’a été effectué. Voir le runbook et SECURITY.md avant toute donnée réelle.

## Adaptation sans VPS — 12 octobre 2026

Configurations Render Free et Netlify Free ajoutées, PostgreSQL Neon Free prévu. Flyway V2 ajoute le stockage privé transactionnel des pièces en base : pas de disque payant. Limite de 3 Mio par document et budget de 150 Mio, avec refus de remplacement sans perte de la pièce précédente à quota atteint.

Tests backend relancés localement : 8 passés sur PostgreSQL réel, migrations V1/V2 appliquées. Les deux tests ajoutés vérifient lecture après recréation du service, accès du propriétaire, remplacement et conservation à quota atteint. Les résultats CI historiques ci-dessus précèdent cette adaptation ; aucun déploiement Render/Netlify/Neon n’est revendiqué. Voir [FREE_HOSTING.md](FREE_HOSTING.md).

Web : 3 tests et TypeScript passés après adaptation. Flutter : analyse sans problème et 4 tests passés. L’APK émulateur fourni auparavant reste celui de la version précédente ; il doit être recompilé pour l’URL gratuite effective.

## Courses et activité — 13 octobre 2026

Première tranche de mobilité implémentée dans le code : compte passager, demande de course, service conducteur avec position récente, acceptation concurrente, démarrage/fin/annulation, recettes de course déclarées, déclarations hors ZEM et charges facultatives. L’administration dispose d’agrégats et d’un tableau par conducteur. Web Next.js et mobile Flutter Android reliés à ces API. Aucun paiement, assurance ou service payant ajouté. Détails et limites : [MOBILITY.md](MOBILITY.md).

Contrôles locaux : 11 tests backend passés avec PostgreSQL réel et migrations V1/V2/V3 ; 3 tests de passerelle web et build Next.js/TypeScript passés ; 5 tests Flutter passés, analyse sans problème. Le parcours navigateur est passé contre l’API HTTP réelle : dossier/rejet/correction/validation, réservation passager, GPS simulé avec permission, acceptation, démarrage, recette de fin, remise hors service, reçu, vérification du tableau administratif par conducteur, carte et révocation. Le GPS physique n’est pas testé. Aucun déploiement de cette tranche n’est revendiqué.
