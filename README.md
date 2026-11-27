<p align="center"><img src="docs/logo.jpg" alt="LOTOPOSBOT" width="640"></p>

# LOTOPOSBOT : analyse probabiliste du Loto

Application Android (Kotlin) qui analyse l'historique des tirages d'un Loto 5/90 et propose **4 numéros par jour**, avec une notification quotidienne. Le projet contient aussi un script Python pour analyser un gros historique sur ordinateur.

> ⚠️ **Rigueur scientifique.** Si le tirage est équitable, chaque tirage est **indépendant** des précédents. Aucun historique, aucune « loi du rattrapage », aucun algorithme ne change la probabilité de gagner. Avec 4 numéros joués sur un 5/90, la probabilité qu'ils sortent tous est de **1 sur 511 038**. LOTOPOSBOT produit une sélection *raisonnée* et le **prouve honnêtement** grâce à son backtest. Joue avec modération ; les jeux d'argent sont interdits aux mineurs.

## Fonctionnalités

| Écran | Contenu |
|---|---|
| **Grille du jour** | 4 numéros calculés une fois par jour et mémorisés (identiques toute la journée), avec la parité et la somme |
| **Modèle** | Curseurs « chauds » (fréquence) et « en retard » (écart), plus des filtres d'équilibre |
| **Historique** | Saisie d'un tirage ou collage de centaines de lignes, validation, annulation du dernier, export |
| **Notification** | Notification quotidienne à l'heure choisie (WorkManager, conservée après un redémarrage) |
| **Statistiques** | Top chauds et retards, écarts moyen et maximum, test du khi-deux, parité, **backtest walk-forward** |
| **Démo** | 500 tirages **simulés** pour tester l'appli (ce ne sont pas de vrais résultats) |

## Le vrai levier : le type de pari

Le Loto Bonheur est un jeu **à cote fixe** (gain = mise × cote). Le choix des numéros ne change donc rien à l'espérance de gain, alors que le **type de pari** la multiplie par 60. Voici les calculs à partir des cotes officielles de la LONACI :

| Pari | Cote | Probabilité | Rendu pour 100 FCFA misés |
|---|---|---|---|
| **2N** | 240 | 1 sur 400 | **59,9 FCFA** |
| 1N | 10 | 1 sur 18 | 55,6 FCFA |
| PN | 40 | 1 sur 90 | 44,4 FCFA |
| 3N | 2 000 | 1 sur 11 748 | 17,0 FCFA |
| 4N | 5 000 | 1 sur 511 038 | **1,0 FCFA** |
| 5N | 40 000 | 1 sur 43 949 268 | 0,1 FCFA |

Tous les paris perdent en moyenne. Jouer les 4 numéros du jour en **2N Perm** (6 combinaisons) au lieu du **4N** fait passer le retour moyen de 1 % à 60 %, avec un gain environ une fois sur 70. L'appli l'affiche sous chaque grille.

## Le chasseur de biais

Un historique ne peut aider que dans un seul cas : une **machine ou des boules réellement défectueuses**. Le mode « chasseur de biais » ignore les numéros chauds et en retard. Il ne favorise que les numéros sur-représentés de façon **statistiquement significative** (test binomial unilatéral, correction de Bonferroni sur les 90 numéros, seuil de 5 %).

- Sur des tirages équitables, aucun numéro n'est retenu, et la grille est alors tirée uniformément.
- Testé sur une machine simulée où le 7 sort deux fois trop souvent : le 7 est détecté après 3 000 tirages, sans aucun faux positif.
- Il faut au moins 100 tirages **réels**, idéalement plus de 1 000, et de préférence ceux d'un même horaire de tirage.

## Importer les vrais résultats

Utilise le bouton **« Importer un fichier de résultats »**. Le fichier contient une ligne par tirage, du plus ancien au plus récent :
```
2026-11-20;5,12,34,67,89
2026-11-21;3,18,40,61,77
```
Les résultats officiels sont publiés sur [lotobonheur.ci/resultats](https://lotobonheur.ci/resultats). Tu peux importer un horaire précis (par exemple 10 h) pour analyser une seule machine.

## Le modèle, en clair

Pour chaque numéro *i* sur *n* tirages (p = 5/90 = 1/18) :

- **Score « chaud »** : z = (fᵢ − n·p) / √(n·p·(1−p)), borné à ±3
- **Ratio de retard** : r = écart actuel / 17, borné à 4 (17 = écart attendu = (1−p)/p)
- **Poids** : w = exp(0,35·a·z) × (1 + 0,5·b·r), où a et b sont les curseurs multipliés par n/(n+50)

Le facteur n/(n+50) ramène les poids vers l'uniforme quand l'historique est court, parce qu'avec peu de tirages les écarts observés sont surtout du bruit.

**Filtres d'équilibre** : ni 4 pairs ni 4 impairs (la répartition 2/2 sort dans environ 38 % des cas), somme dans l'intervalle central de 80 % (182 ± 65) et au plus 2 numéros par dizaine. Ces filtres écartent les grilles atypiques mais **ne changent pas** la probabilité de gain d'une grille.

**Khi-deux** : la statistique est corrigée par le facteur (N−1)/(N−K) parce que les 5 numéros d'un tirage sont tirés sans remise.

**Backtest walk-forward** : pour chacun des 300 derniers tirages, le modèle ne voit que le passé, génère sa grille, puis on compte ses bons numéros. Le résultat est comparé au hasard (0,222 bon numéro par grille). Sur 1 000 tirages simulés équitables, on obtient z ≈ −0,85, soit **aucune différence avec le hasard**. C'est le résultat attendu, et l'appli l'affiche.

### Probabilités exactes (4 numéros joués, Loto 5/90)

| Bons numéros | Probabilité |
|---|---|
| 4 | 1 sur 511 038 |
| 3 | 1 sur 3 006 |
| 2 | 1 sur 72 |
| 1 | 19,3 % |
| 0 | 79,2 % |

## Compiler l'APK

**Avec Android Studio** (Ladybug ou plus récent) : *File > Open*, choisis le dossier du projet, attends la synchronisation Gradle, puis *Build > Build App Bundle(s) / APK(s) > Build APK(s)*.

**En ligne de commande** (JDK 17 requis) :
```bash
./gradlew testDebugUnitTest   # tests du moteur
./gradlew assembleDebug       # APK : app/build/outputs/apk/debug/app-debug.apk
```

**Sans rien installer** : à chaque push, l'action GitHub *Build APK* compile le projet et met l'APK à disposition dans l'onglet *Actions* (rubrique *Artifacts*).

Configuration : minSdk 26 (Android 8), targetSdk 35, Kotlin 2.0, AGP 8.7, Gradle 8.10.

## Changer de jeu

Dans `LotoBot.kt` :
```kotlin
val config = LotoConfig(maxNumero = 49, numerosParTirage = 6, taillePronostic = 4)
```
Toutes les formules (probabilités, écarts, filtres, khi-deux) s'adaptent automatiquement.

## Script Python

```bash
python3 python/analyse_loto.py mon_historique.csv
python3 python/analyse_loto.py mon_historique.csv --chaud 1 --retard 0.3
python3 python/analyse_loto.py --simuler 1000 > demo.csv   # tirages SIMULÉS
```
Format attendu : une ligne par tirage, du plus ancien au plus récent, par exemple `2026-11-20;5,12,34,67,89`. Aucune dépendance n'est obligatoire.

## Structure

```
app/src/main/java/com/example/lotobot/
├── engine/          # moteur pur Kotlin, testé (aucune dépendance Android)
│   ├── LotoConfig.kt      probabilités exactes, écart attendu, somme
│   ├── Statistiques.kt    fréquences, écarts, khi-deux, parité
│   ├── Pronostic.kt       pondération + filtres d'équilibre
│   ├── Backtest.kt        validation walk-forward
│   ├── Tirage.kt          parseur de saisie
│   ├── Rapport.kt         rapport texte
│   └── MathUtils.kt       C(n,k), gamma incomplète, p-valeur
├── data/            # historique (fichier) + préférences
├── notif/           # canal, Worker, planification quotidienne
└── ui/              # MainActivity, StatsActivity
python/analyse_loto.py
```

## Corrections par rapport à la première version

- L'historique est maintenant **sauvegardé** (avant, il était perdu à la fermeture de l'appli).
- La « loi du rattrapage » est présentée comme un critère descriptif et non plus comme un avantage : c'est l'erreur du parieur.
- La grille du jour est mémorisée, alors qu'avant elle changeait dès qu'on ajoutait un tirage, contrairement à ce qui était annoncé.
- Les saisies sont validées : nombre de numéros, plage de valeurs, doublons.
- La boucle de tirage pondéré pouvait ne rien sélectionner à cause d'un arrondi ; c'est corrigé.
- Les poids sont bornés et atténués quand l'historique est court.
- Ajout de la notification quotidienne, des statistiques, du backtest et des tests unitaires.
