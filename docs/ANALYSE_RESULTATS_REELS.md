# Analyse des résultats officiels du Loto Bonheur

**Données :** 16 003 tirages officiels du 5 octobre 2020 au 27 novembre 2026, récupérés sur l'API publique de lotobonheur.ci (`data/loto_bonheur_complet.csv`), soit 40 types de tirage.

- **Tirages physiques** (machines à boules : Reveil, Etoile, Cash, National…) : 8 989
- **Tirages digitaux** (générateur électronique : Digital 21h/22h/23h, Digital Reveil 7h/8h, Special Weekend…) : 7 014, à partir de 2022

Toutes les vérifications ci-dessous sont reproductibles avec les scripts du dossier `python/`.

## 1. Les données sont propres

- Aucun numéro invalide, aucun doublon dans un même tirage, aucun doublon entre tirages du même jour.
- Le total correspond exactement à ce qu'affiche le site.

## 2. Tirages physiques : un hasard parfait

| Test | Résultat |
|---|---|
| Khi-deux d'uniformité | χ² = 95,6 (ddl 89), **p = 0,30** |
| Numéros significativement sur-représentés (Bonferroni) | **aucun** |
| Chaque tirage physique pris séparément (environ 320 résultats) | aucun biais confirmé |

Sur les tirages physiques, les numéros « chauds » et « en retard » ne sont que du bruit statistique.

## 3. Tirages digitaux : un biais réel

| Test | Résultat |
|---|---|
| Khi-deux d'uniformité | χ² = 334,8, **p ≈ 3·10⁻²⁹** |
| Numéros sur-représentés (p corrigé < 5 %) | **88** (512 sorties au lieu de 390), **19** (490), 33 (464), 66 (458) |

**Validation hors échantillon.** On repère les numéros biaisés sur la première moitié chronologique (2022 à début 2025), puis on vérifie sur la seconde (2025-2026), que le test n'a pas vue :

- Le **88** est le seul numéro significatif sur la première moitié.
- Sur la seconde moitié, il sort dans **7,73 %** des tirages au lieu de 5,56 % (**z = 5,61**). **Le biais persiste : c'est un vrai défaut du générateur**, pas une coïncidence.

## 4. Ce biais est-il exploitable ? Non (pour l'instant)

| Stratégie (testée hors échantillon) | Rendu pour 100 FCFA |
|---|---|
| 88 en 1N (cote 10) | 77,3 |
| 2N Perm sur les 4 numéros les plus chauds, testé année par année | 30,7 / 39,8 / 89,4 / 79,4 (2023 / 2024 / 2025 / 2026) |
| Paire 88-19 en 2N (2025-2026) | 109,5, mais intervalle de confiance à 95 % de 56 à 163 |
| Même stratégie sur les tirages physiques (témoin) | 58,7 (le hasard donne 59,9) |
| **Seuil de rentabilité** | **100** |

**Conclusion.** Le biais du générateur digital est réel, mais trop faible face aux cotes de la LONACI. Aucune stratégie n'est rentable de façon démontrée. La paire 88-19 dépasse 100 sur une période, mais son incertitude est énorme et les années précédentes étaient perdantes.

## 5. Ce que fait l'appli de ces résultats

- Le bouton **« Télécharger les résultats officiels »** charge l'historique choisi : physiques, digitaux ou un tirage précis. Il est mis à jour chaque nuit par l'action GitHub.
- Le **mode chasseur de biais**, appliqué aux tirages digitaux, favorise automatiquement les numéros réellement biaisés (88, 19…) et n'invente rien sur les tirages physiques.
- La surveillance continue : si le biais du digital se renforce, le rapport `data/rapport_digitaux.txt`, régénéré chaque nuit, le montrera.

**Rappel :** même avec un biais réel, tous les paris du Loto Bonheur restent perdants en moyenne avec les cotes actuelles. Joue avec modération.
