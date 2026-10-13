# Présentation ZEM — pilote de mobilité pour le Bénin

Le projet est une proposition de service, sans homologation ni intégration officielle à un système de l’État. La démonstration utilise exclusivement des comptes, justificatifs et courses de démonstration.

## Parcours de présentation (8 à 10 minutes)

1. Ouvrir trois fenêtres distinctes : passager, conducteur et administration. Utiliser des comptes séparés ; ne pas partager la session administrative sur le téléphone conducteur.
2. Conducteur : enregistrer son dossier, ajouter trois pièces synthétiques, soumettre. Administration : examiner, rejeter avec un motif, puis valider après correction. Montrer la carte numérique et son QR public qui ne révèle pas les justificatifs.
3. Passager : saisir le départ ou utiliser le GPS, choisir un repère de destination à Cotonou. Les coordonnées des repères sont approximatives ; elles ne constituent pas un itinéraire routier.
4. Conducteur habilité : passer en service avec permission GPS, accepter la demande, démarrer, terminer avec une recette déclarée. Le conducteur doit être à proximité du départ et sa position récente. Pour une répétition locale, le test navigateur simule un GPS ; cela ne valide pas un téléphone réel.
5. Administration : sélectionner la journée au Bénin, consulter les courses et les revenus par conducteur. Montrer séparément les recettes des courses enregistrées, les recettes hors ZEM déclarées et les charges. Une information absente reste inconnue ; le solde n’est pas calculé sans charges.
6. Révoquer une carte et vérifier que le QR public indique la révocation.

## Répétition avant la présentation

- Fusionner et déployer la même version du backend et du web après succès de la CI. Vérifier les URL effectives, les variables Render/Netlify et l’application Flyway V3 ; un lien web existant ne prouve pas que cette version y tourne.
- Télécharger `zem-android-pilot-render` depuis la [CI vérifiée](https://github.com/akouedekon/zem/actions/runs/38452787167), extraire le ZIP et installer `app-debug.apk` pour le téléphone. Il est compilé avec `API_BASE_URL=https://zem-api-akx6.onrender.com/api`. L’artefact `zem-android-debug-emulator` pointe vers l’émulateur local et ne convient pas au téléphone de présentation. Ces APK debug servent au pilote, sans signature de distribution finale ; ils expirent de la CI après 7 jours.
- Faire une répétition sur le téléphone et le réseau utilisés le lendemain : autorisation GPS, session, réservation, acceptation, fin et affichage administratif. Cette vérification terrain reste nécessaire.
- Démarrer l’API gratuite avant la présentation et contrôler sa disponibilité. Préparer une démonstration locale reproductible et une capture vidéo du parcours comme secours ; aucune continuité de service gratuite n’est garantie.
- Ne présenter aucun revenu synthétique comme statistique nationale réelle. Aucun encaissement, paiement ou assurance n’est fourni.

## Vérifications de sécurité reproductibles

`node scripts/security-probes.mjs` utilise par défaut l’API locale isolée du serveur de test sur le port 18080. `SECURITY_BASE_URL` accepte uniquement une adresse de boucle locale. Le script crée deux comptes synthétiques et une course de test puis révoque les sessions. Le rapport sans jetons ni mots de passe est écrit dans `security-results/local-probes.json`.

Les sondes contrôlent l’accès anonyme, les jetons forgés/révoqués, l’escalade de rôle à l’inscription, les accès passager aux fonctions conducteur/administration, les accès entre propriétaires, des entrées invalides et une tentative d’authentification avec métacaractères SQL. La CI les exécute aussi derrière Nginx. Les tests unitaires vérifient le hachage SHA-256 et le format des jetons ; les tests d’intégration vérifient les droits, documents privés, migrations et courses concurrentes. Playwright vérifie le refus d’une origine tierce et le cookie HttpOnly.

Il s’agit de contrôles actifs ciblés, pas d’un pentest exhaustif ni indépendant. Ils ne couvrent pas une attaque contre les services Render/Netlify/Neon, l’ingénierie sociale, le terminal physique, un audit cryptographique, les performances nationales ou la sécurité de toute l’infrastructure. Aucun scan offensif de ces services publics n’a été lancé.

## Limites à annoncer

Le pilote propose réservation, affectation à un conducteur habilité, suivi de position de la course, historique, déclaration de recettes et revue administrative. Il reste à développer le géocodage complet, les itinéraires routiers, les notifications push, les évaluations, le support opérationnel, les rôles territoriaux et la supervision nationale. Le suivi GPS est au premier plan avec expiration de la position, sans historique de déplacements. Voir [MOBILITY.md](MOBILITY.md).
