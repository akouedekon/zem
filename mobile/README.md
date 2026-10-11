# ZEM Android

Client conducteur Flutter : compte, dossier, justificatifs, soumission, suivi et carte QR. La revue administrative est sur le web. HTTP réel vers Spring Boot, aucune connexion directe à la base ni Supabase.

```sh
flutter pub get
flutter analyze
flutter test
flutter build apk --debug --dart-define=API_BASE_URL=http://10.0.2.2:8088/api
```

Ce build debug cible un émulateur avec Compose sur la machine hôte. Pour un appareil, fournir une URL HTTPS accessible. Le défaut `https://zem.example/api` est un placeholder et ne correspond pas à un serveur déployé. Aucun keystore de diffusion n’est inclus. Voir [le runbook](../docs/RUNBOOK.md) et [les limites de sécurité](../docs/SECURITY.md).
