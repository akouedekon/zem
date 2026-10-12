# Démarrer et exploiter le MVP

## Local avec Docker

Prérequis : Docker et Compose ≥ 2.24.4. Depuis la racine :

```sh
cp .env.example .env
# Modifier POSTGRES_PASSWORD, WEB_ORIGIN=http://localhost:8088
# Renseigner ZEM_ADMIN_EMAIL et un mot de passe unique de 12 caractères minimum
docker compose config -q
docker compose up -d --build
docker compose logs -f api
```

Ouvrir `http://localhost:8088`. Créer un compte conducteur, enregistrer un dossier, ajouter les trois justificatifs synthétiques et soumettre. Dans un autre navigateur, se connecter comme administrateur, examiner les documents, rejeter avec motif ou valider. Le conducteur retrouve sa carte après actualisation. Son QR ouvre une page publique ne révélant aucune identité. Révoquer la carte depuis la file APPROVED et vérifier que son QR devient non valide.

L’admin est créé une seule fois. Retirer les deux variables de bootstrap après la première création puis recréer l’API (`docker compose up -d --force-recreate api`). Changer ces variables ne change pas un compte existant. Ne jamais utiliser les identifiants synthétiques des tests en production.

## Développement sans Docker

JDK 17 et Maven 3.9+, PostgreSQL, Node 22+, Flutter 3.44.0/Dart 3.12 et Android SDK. Backend : définir POSTGRES_PASSWORD, DATABASE_URL, POSTGRES_USER, ZEM_ADMIN_EMAIL/PASSWORD et ZEM_PUBLIC_URL, puis `mvn spring-boot:run` dans backend. Web : `npm ci`, API_INTERNAL_URL=http://localhost:8080, WEB_ORIGIN=http://localhost:3000, puis `npm run dev` dans web.

## Tests reproductibles

```sh
cd backend
mvn -B verify
cd ../web
npm ci
npm test
npm run typecheck
npm run build
npm audit --omit=dev --audit-level=high
cd ../mobile
flutter pub get
flutter analyze
flutter test
flutter build apk --debug --dart-define=API_BASE_URL=http://10.0.2.2:8088/api
```

Les tests backend lancent un PostgreSQL embarqué isolé (14.21) et appliquent Flyway ; aucune base utilisateur n’est touchée. Ils doivent s’exécuter sous un utilisateur non-root. La CI vérifie aussi PostgreSQL 17 avec le parcours navigateur à travers Compose/Nginx. Les tests Flutter mockent HTTP et le stockage sécurisé : ils ne prouvent pas le comportement sur un téléphone réel.

Pour le navigateur sans Docker : dans backend, `mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/e2e-classpath.txt`, puis `java -cp "target/test-classes:target/classes:$(cat target/e2e-classpath.txt)" bj.zem.LocalE2eServer` sous Linux. Sous Windows, remplacer `:` par `;` et lire le fichier avec PowerShell. Ce serveur éphémère écoute uniquement 127.0.0.1:18080 et crée un admin synthétique. Dans web, installer Chromium avec `npx playwright install chromium`, puis `npm run test:e2e`. Chrome existant peut être utilisé avec PLAYWRIGHT_CHANNEL=chrome. Les traces d’échec restent dans web/test-results.

## Android

L’APK debug vise l’émulateur Android : `10.0.2.2` représente l’ordinateur hôte. Pour un téléphone, fournir une URL accessible ; utiliser HTTPS. Build normal : `flutter build apk --debug --dart-define=API_BASE_URL=https://votre-domaine/api`. Aucun serveur n’est déployé par la compilation.

La version de diffusion doit employer un keystore de production et HTTPS. Aucune signature de release n’est configurée et aucun repli sur la clé debug n’est permis : ne pas distribuer une release avant configuration de signature. Le HTTP est permis seulement dans le manifeste debug ; release refuse le trafic clair et le client exige HTTPS. Sur cette machine, Flutter choisissait un GraalVM dont jlink échouait : le build a été lancé via le wrapper Gradle avec `-Dorg.gradle.java.home=<Temurin 17>` ; ne pas modifier le JDK global sans besoin.

## Hébergement gratuit

Le VPS est abandonné. Suivre [FREE_HOSTING.md](FREE_HOSTING.md) : Render Free, Netlify Free et Neon Free. Les fichiers compose.vps.yml et nginx.vps.conf sont historiques, conservés pour le contrôle local TLS ; ne pas les utiliser pour le déploiement prévu.

## Sauvegarde et récupération

Prérequis Linux : docker, age, jq, tar et sha256sum. Maintenir une copie chiffrée et l’identité de déchiffrement séparées de la machine source. `AGE_RECIPIENT=<clé publique age> bash infra/backup.sh /chemin-protege/zem-<date>.age` depuis la racine crée un snapshot cohérent en interrompant brièvement l’API. L’archive associe dump PostgreSQL (dont les nouveaux documents), volume documentaire historique et sommes SHA-256. Ces scripts concernent seulement Compose local ; pour Neon, voir FREE_HOSTING.md.

`AGE_IDENTITY=/chemin-protege/identite.txt bash infra/restore-check.sh /chemin-protege/zem-<date>.age` sur un hôte de récupération restaure dans un nouveau conteneur sans réseau ni ports publiés. Le script vérifie les sommes et la restauration PostgreSQL, puis détruit uniquement ce conteneur de test. Il ne restaure pas la production. Pour une reprise réelle : maintenance, nouvelle base et nouveau volume de documents, restauration des deux archives correspondantes, vérification des cartes/documents, invalidation des sessions (`DELETE FROM auth_session`), puis bascule contrôlée. Ne jamais restaurer uniquement la base sans ses fichiers associés.
