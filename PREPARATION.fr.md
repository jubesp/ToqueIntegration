# ToqueIntegration : Toque de Wuki60 + Cookomix, à jour tout seul

Ce dépôt réunit le meilleur des trois projets Toque pour le **Monsieur Cuisine Connect** :

| Dépôt | Ce qu'il apporte | Ce qu'on en garde |
|---|---|---|
| [Wuki60/MrCuisineVibeConnect](https://github.com/Wuki60/MrCuisineVibeConnect) | **L'original**, v0.11.0 (25/11/2026), le plus avancé : recettes Lidl, toqueapp.org, Mealie, Mes recettes, Compagnon téléphone, nombre de personnes, minuteurs, thème clair, 6 langues, SKMC 1100/1200, Smart en test. Scripts d'installation Windows **et** Mac (ceux de flexbox y sont déjà fusionnés). | **Base complète**, sans toucher aux scripts d'installation ni au pilotage moteur. Suivie automatiquement. |
| [flexbox/MrCuisineVibeConnect](https://github.com/flexbox/MrCuisineVibeConnect) | Fork resté en **0.5.1** : installation depuis macOS (mtkclient) et Linux. | Rien de plus : son travail macOS est déjà dans l'original (crédité @flexbox). |
| [aguiran/MrCuisineConnectCookomix](https://github.com/aguiran/MrCuisineConnectCookomix) | Fork de flexbox (donc de la **0.5.1**) monté en 0.7.7 : **recettes Cookomix** converties Thermomix → MCC, cadre photo en veille, molettes tactiles. | **Le module Cookomix**, porté sur la version de Wuki60. |

Installer aguiran tel quel ferait perdre ~200 commits de l'original (Compagnon, Mealie, Mes recettes,
corrections de sécurité…) ; installer l'original seul ferait perdre Cookomix. D'où ce dépôt :
**version de Wuki60 + Cookomix**, numérotée `X.Y.Z.1` (aujourd'hui `0.11.0.1`).

## Ce qui a été fait et vérifié (sans le robot)

- Base : `Wuki60/MrCuisineVibeConnect` 0.11.0, historique conservé.
- Cookomix est une **source de recettes à part** (comme Mealie) : identifiants `ckx-…`, pastille
  « Cookomix », rubrique *Réglages › Recettes › Cookomix*, jamais touchées par la synchro Lidl,
  **réservées au Connect** (réglages convertis pour ses limites).
- Conversion Thermomix → MCC reprise d'aguiran (`core/.../recipes/Cookomix.kt`) : vitesse 3 max en
  chauffe / sens inverse, 4 max au fouet, 130 °C max, pétrissage découpé en 2 min, cuissons > 99 min
  découpées, recette refusée si un réglage n'a pas d'équivalent sûr.
- **Tests du cœur : 529, tous passent**, dont ceux de Cookomix et le test des recettes Cookomix
  contre le **jumeau numérique** du protocole moteur.
- **40 vraies pages cookomix.com** tirées au hasard : 40/40 lues, 31 telles quelles, 9 adaptées,
  0 réglage hors des limites de sécurité.
- APK compilé par GitHub Actions ; même clé de signature que l'original (publique exprès) : il
  s'installe par-dessus un Toque existant.

**Pas encore vérifié** : l'APK sur un vrai robot (c'est pour le retour à la maison).

## Mises à jour automatiques

```
Wuki60 publie vX.Y.Z ──► workflow « Suivre Wuki60 » (toutes les 6 h)
                          fusion + Cookomix → tests → APK → version vX.Y.Z.1 ici
                                                              │
robot (Réglages › Mises à jour) ◄─────────────────────────────┘
  vérifie au démarrage et en sortant de veille (au plus toutes les 6 h),
  installe seul dès que le robot est au calme, puis Toque redémarre (~1 min)
```

- **Côté GitHub** (`.github/workflows/sync-upstream.yml`) : fusionne la dernière *version publiée*
  de Wuki60 (pas son travail en cours). Les fichiers de langue se fusionnent par clé
  (`tools/fork/json-union.py`), `.github/` reste celui de ce dépôt, les réglages propres à ce dépôt
  sont à part (`app/.../ForkPrefs.kt`). Tests du cœur obligatoires avant de publier.
- **En cas de conflit** (Wuki60 a changé une ligne que Cookomix touche aussi) ou de test en échec :
  rien n'est publié, le robot garde sa version, et une **issue** s'ouvre ici avec la liste des
  fichiers. Il suffit alors de demander à un agent : « résous le conflit de l'issue et pousse sur main ».
- **Côté robot** : *Réglages › Mises à jour › L'installer seul, quand le robot est au repos* (activé
  par défaut). Jamais pendant un programme, une recette guidée, une pesée ou une fenêtre ouverte ; en
  pratique, au démarrage ou au réveil du robot. Désactivé : Toque propose la version et attend
  « Installer ».
- **Numéros** : `X.Y.Z.R`, code de version `×10 + R`, `R` dans `tools/fork/REVISION` (1). Pour
  publier un correctif propre à ce dépôt sans attendre Wuki60 : passer `R` à 2 et pousser sur main.
- Conditions : dépôt **public** (le robot lit les versions sans identifiant) et Actions activé.

## À préparer avant de rentrer

### Matériel

- [ ] Un câble **USB-A mâle ↔ USB-A mâle** *de données* (pas USB-A ↔ USB-C : ça ne marche pas).
- [ ] Si le Mac n'a que de l'USB-C : un **adaptateur USB-C → USB-A**.
- [ ] Un **petit tournevis Torx** (trappe sous le robot).
- [ ] Une **clé USB** ou un cloud pour garder la sauvegarde du robot (c'est le retour arrière).
- [ ] Le mot de passe du Wi-Fi.

### Le Mac

Chemin testé de bout en bout sur un SKMC 1200 C3 depuis un Mac Apple silicon
([docs/MACOS.md](docs/MACOS.md)) ; Mac Intel pas encore essayé par le projet.

- [ ] [Homebrew](https://brew.sh), puis `brew install python libusb` et `xcode-select --install`.
- [ ] Ce dépôt sur le Mac : `git clone https://github.com/jubesp/ToqueIntegration`.
- [ ] L'APK `Toque-X.Y.Z.1.apk` de la [dernière version](https://github.com/jubesp/ToqueIntegration/releases/latest)
      dans `~/Toque/` (à défaut : *Actions › Build APK › Artifacts*).
- [ ] Compter **1 à 2 heures**, robot branché sur le secteur, à portée du câble.

## Le jour J

Le plus simple : un agent IA qui pilote le Mac (Claude Code par exemple), avec ce message :

> Installe Toque sur mon Monsieur Cuisine Connect depuis ce Mac en suivant AGENTS.md (partie macOS)
> de ce dépôt, mais à l'étape `install` utilise mon APK : `--apk ~/Toque/Toque-X.Y.Z.1.apk`.

Les étapes (`bash tools/setup/toque-setup.sh <étape>` depuis le dossier du dépôt ; `status` dit où
on en est) :

1. `prepare` (téléchargements vérifiés : mtkclient, adb, Magisk, TWRP).
2. `backup` : trappe ouverte, câble dans le port **« Android USB »**, robot éteint. **Copier ensuite
   `~/Toque/backup/` sur la clé USB.**
3. SKMC 1100 A1 ou SKMC 1200 D4 (étiquette sous le robot) : `own-twrp` d'abord. Puis `flash-twrp` →
   `root` → `flash-root` → `android` (un appui sur **Autoriser** sur l'écran du robot).
4. `install --apk ~/Toque/Toque-X.Y.Z.1.apk`
5. Premier essai **bol vide**, main près de l'interrupteur : choisir *Monsieur Cuisine Connect*, puis
   *Réglages › Avancé › Machine › Machine réelle › Appliquer* ; cuisson manuelle 10 s vitesse 2 ;
   ouvrir le couvercle pendant un essai (arrêt immédiat) ; 30 s à 37 °C.
6. `wifi` (ADB par Wi-Fi) : on peut alors débrancher le câble et refermer la trappe.
7. *Réglages › Recettes › Lidl › Synchroniser* (~15 min).
8. **Cookomix** : *Réglages › Recettes › Cookomix › Activer*. Première synchro **1 h ou plus**, les
   recettes apparaissent au fur et à mesure. Options : *Inclure les recettes adaptées* (oui par
   défaut), *Synchro Cookomix chaque nuit* (à activer si tu veux les nouvelles recettes).
9. Vérifier *Réglages › Mises à jour* : « à jour », et *L'installer seul* activé.

## Cookomix sur le robot

- **Pastille « Cookomix »** dans la liste des recettes (recherche, catégories, tri par note ou durée).
- **Note du site** sur chaque recette (★ 4,8 · 42 notes), photo de la recette.
- **Onglet « Avis »** : les avis les plus utiles de la page (« J'ai testé ! », commentaires,
  questions), avec la note et les **photos des membres** (un appui agrandit la photo). Lus en direct
  à l'ouverture de l'onglet : il faut le Wi-Fi.
- **Favoris** (étoile) : ils restent sur le robot même si la recette disparaît de cookomix.com ou
  devient « adaptée » avec l'option coupée. Les favoris du *compte* Cookomix ne peuvent pas être
  importés (le site refuse la connexion aux applications) : on les remet à l'étoile dans Toque.
- **Ma note** et quantités personnelles, **Ma version** (copie modifiable dans Mes recettes) : comme
  pour toutes les recettes.

## Précautions Cookomix

- Recettes **Thermomix** converties automatiquement : chaque réglage est **à confirmer**
  (« J'ai vérifié ce réglage ») avant de partir. Les premières fois, compare le réglage proposé avec
  le réglage Thermomix d'origine rappelé dans le texte.
- Les recettes « adaptées » portent le mot-clé *Adaptée MCC* et expliquent ce qui a changé.
- Souci de synchro : code `CKX-01` à `CKX-10`, voir [docs/COOKOMIX-ERREURS.md](docs/COOKOMIX-ERREURS.md).
  Rien n'est jamais effacé quand le site change ou est en panne.

## Retour arrière

- Toque d'origine sans Cookomix : installer un `Toque-X.Y.Z.apk` de Wuki60 par-dessus (même
  signature ; avec `adb install -r -d` car son code de version est plus petit).
- Appli Lidl d'origine : `bash tools/restore-robot.sh` avec la sauvegarde de l'étape 2.
