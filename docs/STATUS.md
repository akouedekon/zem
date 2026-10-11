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
- [ ] Parcours navigateur complet confirmé après correction du filtre administratif.
- [ ] Compose/Nginx et restauration de sauvegarde vérifiés en CI.
- [ ] Déploiement VPS et HTTPS.
- [x] APK Android debug compilé pour émulateur ; pas de test sur appareil ni de release signée.

## Vérifications exécutées le 12 octobre 2026

- JDK Temurin 17.0.17, Spring Boot 3.5.16 : 6 tests passés, aucune erreur ; PostgreSQL embarqué 14.21 et Flyway V1 appliquée. Vérifications d’accès, dossier figé, rejet et resoumission, concurrence des décisions, émission/lecture du QR, expiration et révocation.
- Next.js 16.4.0 : 2 tests de la passerelle web passés, TypeScript et build de production passés. Audit npm des dépendances de production : 0 vulnérabilité signalée à la date du contrôle.
- Flutter 3.44.0 : analyse sans problème, 4 tests passés. APK debug créé via Gradle avec Temurin 17 après correction du JDK local et du manifeste debug.
- Le premier test navigateur a atteint l’approbation et la vérification publique, puis identifié un libellé de filtre à corriger ; la relance est en cours.

La CI versionnée vérifie séparément backend, web, Android et parcours complet à travers PostgreSQL 17/Compose/Nginx, ainsi que backup chiffré et restauration isolée. Sa configuration seule ne prouve pas un résultat passé. Aucun déploiement VPS, certificat réel, sauvegarde de production ou test sur téléphone n’a été effectué. Voir le runbook et SECURITY.md avant toute donnée réelle.
