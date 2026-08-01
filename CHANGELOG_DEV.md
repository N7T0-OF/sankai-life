# Changelog développeur

## Non publié — 1er août 2026

### Intégrité

- écritures atomiques pour pièces, gemmes, XP et statistiques ;
- achats de slots recalculés et livrés dans une transaction ;
- récompenses coffres, défis et arènes livrées transactionnellement ;
- progression des défis bornée et réclamation anti-double ;
- verrou de réponse flash cards et reprise unique des cartes ratées.

### Données et sécurité

- lecteur ZIP borné pendant la décompression ;
- installation de modules transactionnelle avec ordre stable ;
- restauration conditionnée par une sauvegarde de sécurité réellement écrite ;
- sauvegarde étendue aux données persistantes promises ;
- consentement UMP 4.0.0 avant initialisation AdMob.

### Notifications

- créneaux aléatoires mémo stables par profil et journée ;
- replanification après activation/désactivation ;
- alarmes coffres restaurées au lancement et après événements système ;
- réglage global respecté par les notifications de coffres.

### Jardin

- synchronisation temporelle liée au cycle de vie ;
- tick partagé et index des cultures ;
- origine caméra stable, recentrage réel, petit terrain centré ;
- moteur visuel météo, vent partagé, éclairage couvert, ombres nuageuses
  procédurales et profils de qualité ;
- économie batterie forçant la qualité faible.

### UI

- tokens d'espacement/rayons et primitives liquid glass ;
- accueil responsive non défilant avec Arène centrale et dock coffres ;
- navigation basse accessible et compatible edge-to-edge ;
- recentrage du parcours Arènes.

### Livraison

- release sans repli sur signature debug ;
- publication dépendante des tests et bloquée si secrets incomplets ;
- lint release bloquant.

### Validation finale

- 239 tests unitaires dans 24 suites, réussis en debug et en release
  (478 exécutions, aucun échec, erreur ou test ignoré) ;
- `lintDebug` et `lintRelease` réussis avec 0 erreur ;
- `assembleDebug` et `assembleRelease` réussis ;
- signatures APK v2 vérifiées, dont la release avec le certificat Sankai Life.
