# ToqueIntegration : Toque 0.11 + Cookomix, prêt à installer

Ce dépôt réunit le meilleur des trois projets Toque pour le **Monsieur Cuisine Connect** :

| Dépôt | Ce qu'il apporte | Ce qu'on en garde |
|---|---|---|
| [Wuki60/MrCuisineVibeConnect](https://github.com/Wuki60/MrCuisineVibeConnect) | **L'original**, v0.11.0 (25/11/2026), le plus avancé : recettes Lidl, toqueapp.org, Mealie, Mes recettes, Compagnon téléphone, nombre de personnes, minuteurs, thème clair, 6 langues, SKMC 1100/1200, Smart en test. Scripts d'installation Windows **et** Mac (ceux de flexbox y sont déjà fusionnés). | **Base complète**, sans modification des scripts d'installation ni du pilotage moteur. |
| [flexbox/MrCuisineVibeConnect](https://github.com/flexbox/MrCuisineVibeConnect) | Fork resté en **0.5.1** : installation depuis macOS (mtkclient) et Linux. | Rien de plus : son travail macOS est déjà dans l'original (crédité @flexbox). Seule la partie **Linux** (non nécessaire si tu as un PC Windows ou un Mac) n'y est pas. |
| [aguiran/MrCuisineConnectCookomix](https://github.com/aguiran/MrCuisineConnectCookomix) | Fork de flexbox (donc de la **0.5.1**) monté en 0.7.7 : **recettes Cookomix** converties Thermomix → MCC, cadre photo en veille, molettes tactiles. | **Le module Cookomix**, porté sur la 0.11.0. Le reste (cadre photo, molettes) n'est pas porté : l'original a ses propres équivalents ou mieux. |

Installer aguiran tel quel ferait perdre ~200 commits de l'original (0.6 → 0.11 : Compagnon, Mealie,
Mes recettes, Smart, corrections de sécurité…). Installer l'original seul ferait perdre Cookomix.
D'où ce dépôt : **Toque 0.11.0 + Cookomix = version `0.11.0.1`**.

## Ce qui a été fait et vérifié (sans le robot)

- Base : `Wuki60/MrCuisineVibeConnect` @ `1d27462` (0.11.0), historique conservé.
- Cookomix porté comme une **source de recettes à part** (comme Mealie) : identifiants `ckx-…`,
  pastille « Cookomix » dans la liste, rubrique *Réglages › Autres › Cookomix*, jamais touchées par
  la synchro Lidl, et **réservées au Connect** (réglages convertis pour ses limites).
- Conversion Thermomix → MCC reprise à l'identique d'aguiran (`core/.../recipes/Cookomix.kt`) :
  vitesse 3 max en chauffe / sens inverse, 4 max au fouet, 130 °C max, pétrissage découpé en 2 min,
  cuissons > 99 min découpées, recette refusée si un réglage n'a pas d'équivalent sûr.
- **Tests : 529 tests du cœur passent**, dont les 14 tests Cookomix d'aguiran, un test d'intégration
  ajouté, et le test des recettes Cookomix contre le **jumeau numérique** du protocole moteur
  (SDK `antrea/monsieur-cuisine-developer-sdk`).
- **40 vraies pages cookomix.com** (tirées au hasard parmi 2 441, le 27/11/2026) : 40/40 lues,
  31 compatibles telles quelles, 9 adaptées, 0 illisible, 0 réglage hors des limites de sécurité,
  toutes acceptées par le contrôle « ce robot sait faire cette recette » de la 0.11.
- Le code de l'appli compile (contre les classes Android 14). L'APK lui-même est compilé par
  GitHub Actions (`.github/workflows/build-apk.yml`) à chaque push.
- Mises à jour : *Réglages › Mises à jour* regarde **ce** dépôt (`jubesp/ToqueIntegration`), pas
  l'original, pour ne pas remplacer cette version par une version sans Cookomix.
- Même clé de signature que l'original (publique exprès) : s'installe par-dessus un Toque existant.

**Pas encore vérifié** : l'APK sur un vrai robot (c'est l'étape de la semaine prochaine). L'écran
*Réglages › Autres › Cookomix* n'a été vu qu'à la compilation.

## À préparer avant de rentrer

### Matériel (à acheter si besoin)

- [ ] Un câble **USB-A mâle ↔ USB-A mâle** *de données* (pas USB-A ↔ USB-C : ça ne marche pas).
      Sur un Mac qui n'a que de l'USB-C : ajouter un **adaptateur USB-C → USB-A**.
- [ ] Un **petit tournevis Torx** (trappe sous le robot).
- [ ] Une **clé USB** ou un cloud pour garder la sauvegarde du robot (c'est le retour arrière).
- [ ] Le mot de passe du Wi-Fi.

### Ordinateur

- **Windows 10/11** avec un port USB-A : *Python n'est pas nécessaire*. Deux fichiers sont à
  télécharger à la main (pilote MediaTek, SP Flash Tool) : l'agent te guide.
- **ou Mac** (Apple silicon testé) : installer à l'avance [Homebrew](https://brew.sh), puis
  `brew install python libusb` et `xcode-select --install`.
- Récupérer ce dépôt sur l'ordinateur (`git clone` ou *Code › Download ZIP* sur GitHub).
- Compter **1 à 2 heures**, robot branché sur le secteur, à portée du câble.

### L'APK

Télécharger `Toque-0.11.0.1.apk` depuis GitHub : *Actions › Build APK › dernière exécution ›
Artifacts › Toque-connect-apk* (fichier zip contenant l'APK et son SHA-256), et le mettre par
exemple dans `~/Toque/` (Mac) ou `C:\Toque\` (Windows).

## Le jour J

Le plus simple : un agent IA qui pilote l'ordinateur (Claude Code par exemple), avec ce message :

> Installe Toque sur mon Monsieur Cuisine Connect en suivant AGENTS.md de ce dépôt, mais à l'étape
> `install` utilise mon APK : `--apk <chemin>/Toque-0.11.0.1.apk` (Mac) ou
> `-Apk <chemin>\Toque-0.11.0.1.apk` (Windows).

Les étapes (script `tools/setup/toque-setup.sh` sur Mac, `tools\setup\toque-setup.ps1` sur Windows) :

1. `prepare` (téléchargements vérifiés) → (Windows : `driver`)
2. `backup` : trappe ouverte, câble dans le port **« Android USB »**, robot éteint. **Copier
   ensuite le dossier `backup` sur la clé USB.**
3. `flash-twrp` → `root` → `flash-root` → `android` (un appui sur **Autoriser** sur l'écran du robot)
4. `install --apk …/Toque-0.11.0.1.apk`
5. Premier essai **bol vide**, main près de l'interrupteur : choisir *Monsieur Cuisine Connect*, puis
   *Réglages › Avancé › Machine › Machine réelle › Appliquer* ; cuisson manuelle 10 s vitesse 2 ;
   ouvrir le couvercle pendant un essai (arrêt immédiat) ; 30 s à 37 °C.
6. `wifi` (ADB par Wi-Fi), on peut alors débrancher le câble et refermer la trappe.
7. *Réglages › Recettes › Lidl › Synchroniser* (~15 min).
8. **Cookomix** : *Réglages › Autres › Cookomix › Activer*. Première synchro **1 h ou plus** (une
   page toutes les 1 à 2 s, photos comprises), les recettes apparaissent au fur et à mesure.
   Options : *Inclure les recettes adaptées* (oui par défaut), *Synchro Cookomix chaque nuit*
   (non par défaut).

## Précautions Cookomix

- Ce sont des recettes **Thermomix** converties automatiquement. Chaque réglage d'une recette
  Cookomix est **à confirmer** (« J'ai vérifié ce réglage ») avant de partir : les premières fois,
  lis le réglage proposé et le réglage Thermomix d'origine rappelé dans le texte.
- Les recettes « adaptées » portent le mot-clé *Adaptée MCC* et expliquent ce qui a changé dans
  leurs notes.
- En cas de souci de synchro : code `CKX-01` à `CKX-10`, voir [docs/COOKOMIX-ERREURS.md](docs/COOKOMIX-ERREURS.md).
  Rien n'est jamais effacé quand le site change ou est en panne.

## Retour arrière

- Revenir au Toque d'origine sans Cookomix : installer `Toque-0.11.x.apk` de Wuki60 par-dessus
  (même signature).
- Revenir à l'appli Lidl d'origine : `tools/restore-robot.sh` (Mac) ou `tools\restore-robot.ps1`
  (Windows), avec la sauvegarde de l'étape 2.

## Points ouverts

- Le dépôt est **privé** : le robot ne pourra pas trouver les mises à jour de ce dépôt tout seul
  (*Réglages › Mises à jour* affichera une erreur). Les mises à jour se feront à la main
  (`install --apk`), ou rendre le dépôt public plus tard.
- Pour suivre les nouvelles versions de Wuki60 : refusionner `Wuki60/main` dans ce dépôt (le module
  Cookomix est isolé : `core/.../recipes/Cookomix.kt`, `app/.../cookomix/CookomixAccount.kt`).
