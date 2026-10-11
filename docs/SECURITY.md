# Sécurité et limites du pilote

## Contrôles implémentés

- Spring Security contrôle les rôles et chaque téléchargement vérifie le propriétaire ou l’administration.
- BCrypt coût 12, jetons aléatoires 256 bits, empreintes SHA-256 seules en base, expiration 8 h et révocation à la déconnexion. Pas de Basic Auth ni compte de démonstration dans l’image de production.
- Web : cookie HttpOnly, SameSite=Strict, Secure avec origine HTTPS ; contrôle d’Origin sur toute mutation, routes BFF explicitement autorisées, aucun stockage local du jeton. API sans cookies et sans CORS ; Flutter utilise un bearer conservé dans le stockage sécurisé du système.
- Justificatifs dans un volume privé monté uniquement sur l’API. Noms UUID sans réutilisation du nom fourni. PDF/JPEG/PNG identifiés par signature, plafond 5 Mo, téléchargements en pièce jointe et `nosniff`. Les échecs transactionnels retirent les nouveaux fichiers ; remplacement après commit.
- Verrouillage PostgreSQL des transitions et journal des soumissions, décisions et révocations dans la transaction. Aucun endpoint d’édition/suppression du journal.
- QR opaque, réponse publique minimale et non mise en cache. Expiration et révocation contrôlées à chaque vérification.
- API et web sous utilisateurs non-root, fichiers système en lecture seule et capacités retirées. PostgreSQL et API sans ports publiés. TLS via Nginx en profil VPS.
- Secrets, certificats, keystores, fichiers privés et dépendances générées exclus du dépôt. Sauvegardes DB + documents chiffrées avec age ; vérification dans une base isolée.

## Limites explicites

Le pilote n’est pas une certification réglementaire, une assurance ou un service de paiement. Les trois types de justificatifs et la durée de carte d’un an sont des choix de démonstration, à confirmer avec l’autorité compétente. Aucun mail de confirmation, récupération de mot de passe, MFA ou renouvellement de carte n’est encore implémenté.

La signature de fichier ne remplace pas une analyse antivirus. Avant les pièces réelles : ajouter quarantaine/antivirus, valider finalités et conservation des données, obtenir la validation juridique, auditer les contrôles d’accès, les hôtes et les sauvegardes. Le pilote accepte seulement des documents synthétiques. Le volume privé n’est pas chiffré par l’application : le disque du VPS doit être chiffré et son accès limité.

Le limiteur API est en mémoire, pour une instance. Derrière le web/Nginx, sa limite agrège les requêtes du proxy ; Nginx applique la limite par IP réelle. Une infrastructure à plusieurs instances nécessitera un limiteur partagé et une stratégie de protection anti-abus par compte. Aucun en-tête client non vérifié n’est considéré comme une identité réseau fiable.

La base Compose initialise un utilisateur propriétaire pour les migrations. En production, séparer le compte Flyway et un compte d’exécution avec droits minimaux, conserver les journaux hors application et définir rotation/purge des sessions et des fichiers orphelins après interruption brutale. Un opérateur DB peut altérer le journal : il n’est pas un registre infalsifiable.

Les sauvegardes incluent les hash de mots de passe et sessions : protéger l’identité age, appliquer une rétention et révoquer les sessions après toute restauration. Ne jamais publier les archives ou traces issues de données réelles.
