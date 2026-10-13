# Hébergement gratuit du pilote

Décision du 12 octobre 2026 : aucun VPS, aucun Supabase, aucun abonnement payant. Java 17 / Spring Boot 3, Next.js / TypeScript et Flutter sont conservés. Docker Compose et Nginx servent au développement et aux tests locaux. Les anciens fichiers VPS ne sont plus la cible de déploiement.

## Services et limites vérifiées

| Usage | Offre | Limites et comportement |
| --- | --- | --- |
| API Java | Render Free, Docker | 750 heures/mois partagées par espace ; veille après 15 minutes, réveil d'environ une minute. Disque éphémère. Sans moyen de paiement, dépassement : suspension ou builds bloqués, pas d'achat supplémentaire. |
| Web Next.js | Netlify Free | 300 crédits/mois, limite ferme sans recharge automatique ; sites suspendus lorsque les crédits sont épuisés. HTTPS et sous-domaine fournis. |
| PostgreSQL et pièces privées | Neon Free | Offre annoncée : 1 Go/projet, 100 CU-heures/mois, restauration instantanée sur 6 heures. Choisir explicitement Free ; vérifier les quotas affichés à la création. |
| Android | Flutter + APK debug | Installation directe pour le pilote ; aucune publication en boutique payante. |

Sources officielles consultées le 12 octobre 2026 : [Render](https://render.com/docs/free), [Netlify Free](https://docs.netlify.com/manage/accounts-and-billing/billing/billing-for-credit-based-plans/credit-based-pricing-plans/), [Neon Free](https://neon.com/blog/neon-free-plan-1-gb-per-project), [Next.js sur Netlify](https://docs.netlify.com/build/frameworks/framework-setup-guides/nextjs/overview/), [limites des fonctions](https://docs.netlify.com/build/functions/configuration/).

Ce montage convient à une démonstration avec données synthétiques, sans disponibilité garantie. Render déconseille son offre gratuite pour la production et peut suspendre un service générant beaucoup de trafic sortant vers une base externe. Ne pas ajouter de carte bancaire, ne pas activer de recharge, de module payant ou de sonde artificielle pour empêcher la veille. Utiliser les sous-domaines gratuits, sans acheter de domaine. À quota atteint, attendre le renouvellement et limiter l'usage ; ne pas migrer automatiquement vers une offre payante.

## Stockage et évolution du schéma

Flyway V2 ajoute `document_content` avec `bytea`, privé et accessible uniquement par l'API après contrôle du propriétaire ou du rôle administrateur. Les fichiers nouveaux et leurs métadonnées sont enregistrés dans la même transaction. Une ligne verrouillée sérialise la vérification du budget entre conducteurs. Le plafond applicatif est de 150 Mio de contenu, avec 3 Mio par document. Cette réserve ne garantit pas la taille physique totale de PostgreSQL : index, versions de lignes, WAL et branches consomment aussi du stockage. Surveiller le quota Neon et arrêter les nouveaux dépôts avant saturation.

La limite de 3 Mio laisse une marge sous les 4,5 Mo utiles des requêtes binaires Netlify. Le mode `ZEM_DOCUMENT_STORAGE=database` est le défaut. `filesystem` reste disponible pour les anciens environnements locaux. V2 ne convertit pas les anciens fichiers : conserver le volume et les sauvegardes associés. Pour ce nouveau pilote gratuit, utiliser une base neuve avec données synthétiques ; ne pas copier uniquement les métadonnées de fichiers vers Neon.

## Mise en ligne sans abonnement

1. Créer un projet Neon **Free**, dans une région proche de Francfort. Conserver l'URL directe PostgreSQL pour Flyway. Dans Render, `DATABASE_URL` doit être une URL JDBC, par exemple `jdbc:postgresql://<hote-neon>/<base>?sslmode=verify-full` ; renseigner séparément `POSTGRES_USER` et `POSTGRES_PASSWORD`. Ne jamais publier ces valeurs dans Git ou le navigateur. Ne pas utiliser une URL `postgresql://...` telle quelle en JDBC.
2. Dans Netlify **Free**, importer le dépôt, choisir la branche contenant ce changement (`feat/zem-mvp` tant que la PR n'est pas fusionnée, puis `main`). `netlify.toml` configure le dossier web et le build. Noter le sous-domaine HTTPS effectivement attribué. Désactiver les builds automatiques et les aperçus pour maîtriser les crédits. Les aperçus de branche sont bloqués par la configuration du pilote.
3. Dans Render, importer `render.yaml` avec la même branche. Le seul service est `zem-api`, explicitement `plan: free`, sans disque ni base Render. Renseigner les variables demandées, notamment `ZEM_PUBLIC_URL=https://<site>.netlify.app` et le compte administrateur initial avec un mot de passe unique. Le port fourni par Render est utilisé automatiquement. L'API accepte uniquement des bearers ; elle ne dépend pas des cookies cross-domain.
4. Dans les variables Netlify applicables aux fonctions de production, définir `API_INTERNAL_URL=https://<api>.onrender.com` et `WEB_ORIGIN=https://<site>.netlify.app`, sans slash final. Relancer le build web. Ne pas déduire l'origine d'un en-tête du visiteur et ne pas employer `NEXT_PUBLIC_` pour ces variables.
5. Après création de l'admin, retirer ses variables de bootstrap dans Render et redéployer manuellement. Vérifier `/api/health`, puis le parcours complet avec des pièces synthétiques : dépôt, rejet/correction, validation, téléchargement privé, QR et révocation. Après veille, la première requête peut afficher une indisponibilité ; patienter une minute puis réessayer, sans soumettre automatiquement à nouveau une opération.
6. Recompiler le mobile avec `flutter build apk --debug --dart-define=API_BASE_URL=https://<api>.onrender.com/api`. L'APK émulateur déjà fourni ne vise pas cet hébergement. Tester sur appareil avant diffusion.

Aucun compte Render/Netlify/Neon ni déploiement distant n'a été créé par cette modification. Les configurations sont préparées ; leur exécution sur ces plateformes reste à vérifier avec les comptes concernés. Aucun abonnement ni service facturable n'est provisionné par les tests.

## Sauvegardes gratuites

La fenêtre de restauration Neon de six heures ne remplace pas une sauvegarde indépendante. Depuis une machine autorisée, utiliser le client PostgreSQL et `pg_dump --format=custom` avec l'URL directe et TLS, puis chiffrer le fichier avec `age` vers une clé publique. Garder la clé privée séparée. Ne pas mettre les secrets, le dump ni la clé privée dans GitHub ou des artefacts CI. Tester `pg_restore --exit-on-error` dans une base locale isolée avant d'envisager une reprise. Tous les nouveaux justificatifs sont inclus dans le dump SQL. Les scripts Compose existants concernent uniquement le stockage local et n'accèdent pas à Neon.
