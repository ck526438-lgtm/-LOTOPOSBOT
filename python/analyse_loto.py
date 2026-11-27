#!/usr/bin/env python3
"""
LOTOPOSBOT — analyse statistique d'un historique de Loto (par défaut 5/90).

Même modèle que l'appli Android, pour analyser un gros historique sur ordinateur :
  - fréquences, score z, écarts (actuel, moyen, max)
  - test du khi-deux d'uniformité (corrigé pour un tirage sans remise)
  - parité observée / théorique, somme moyenne
  - grille pondérée du jour (chauds + retards + filtres d'équilibre)
  - backtest walk-forward contre le hasard

Format du fichier (une ligne par tirage, du plus ancien au plus récent) :
    2026-11-20;5,12,34,67,89
    12 34 56 78 90
Les lignes vides et celles qui commencent par # sont ignorées.

Utilisation :
    python3 analyse_loto.py historique.csv
    python3 analyse_loto.py historique.csv --max 49 --tires 6 --grille 4
    python3 analyse_loto.py --simuler 1000 > demo.csv      (génère des tirages SIMULÉS)

Aucune dépendance obligatoire (scipy est utilisé s'il est installé, pour la p-valeur exacte).

RAPPEL : si le tirage est équitable, chaque tirage est indépendant des précédents.
Ce script décrit le passé ; il ne prédit pas l'avenir.
"""
from __future__ import annotations

import argparse
import datetime as dt
import math
import random
import re
import sys
from dataclasses import dataclass

DATE_RE = re.compile(r"^\s*(\d{4}-\d{2}-\d{2}|\d{2}/\d{2}/\d{4})\s*[;:|]?\s*")


@dataclass
class Config:
    maxi: int = 90
    tires: int = 5
    grille: int = 4

    @property
    def p(self) -> float:
        return self.tires / self.maxi

    @property
    def ecart_attendu(self) -> float:
        return (1 - self.p) / self.p

    def proba_bons(self, k: int) -> float:
        return math.comb(self.tires, k) * math.comb(self.maxi - self.tires, self.grille - k) / math.comb(self.maxi, self.grille)

    @property
    def bons_attendus(self) -> float:
        return self.grille * self.p

    @property
    def bons_sigma(self) -> float:
        n, k, p = self.maxi, self.grille, self.p
        return math.sqrt(k * p * (1 - p) * (n - k) / (n - 1))

    @property
    def somme_moy(self) -> float:
        return self.grille * (self.maxi + 1) / 2

    @property
    def somme_sigma(self) -> float:
        n, k = self.maxi, self.grille
        return math.sqrt(k * (n * n - 1) / 12 * (n - k) / (n - 1))


def lire(chemin: str, cfg: Config) -> list[list[int]]:
    tirages, erreurs = [], 0
    with open(chemin, encoding="utf-8") as f:
        for i, ligne in enumerate(f, 1):
            ligne = ligne.strip()
            if not ligne or ligne.startswith("#") or not any(c.isdigit() for c in ligne):
                continue
            ligne = DATE_RE.sub("", ligne)
            nums = [int(x) for x in re.findall(r"\d+", ligne)]
            if len(nums) != cfg.tires or len(set(nums)) != cfg.tires or not all(1 <= x <= cfg.maxi for x in nums):
                erreurs += 1
                print(f"  ligne {i} ignorée : {ligne}", file=sys.stderr)
                continue
            tirages.append(sorted(nums))
    if erreurs:
        print(f"{erreurs} ligne(s) invalide(s) ignorée(s)", file=sys.stderr)
    return tirages


def p_valeur_khi2(stat: float, ddl: int) -> float:
    try:
        from scipy.stats import chi2  # type: ignore
        return float(chi2.sf(stat, ddl))
    except ImportError:
        # Approximation de Wilson-Hilferty (très bonne pour ddl > 30)
        z = ((stat / ddl) ** (1 / 3) - (1 - 2 / (9 * ddl))) / math.sqrt(2 / (9 * ddl))
        return 0.5 * math.erfc(z / math.sqrt(2))


def statistiques(tirages: list[list[int]], cfg: Config) -> dict:
    n = len(tirages)
    freq = [0] * (cfg.maxi + 1)
    derniere = [-1] * (cfg.maxi + 1)
    ecarts: list[list[int]] = [[] for _ in range(cfg.maxi + 1)]
    for t, tirage in enumerate(tirages):
        for x in tirage:
            freq[x] += 1
            if derniere[x] >= 0:
                ecarts[x].append(t - derniere[x] - 1)
            derniere[x] = t
    esp = n * cfg.p
    sigma = math.sqrt(n * cfg.p * (1 - cfg.p)) or 1.0
    par_num = {}
    for x in range(1, cfg.maxi + 1):
        actuel = n if derniere[x] < 0 else n - 1 - derniere[x]
        par_num[x] = {
            "freq": freq[x],
            "z": (freq[x] - esp) / sigma,
            "ecart": actuel,
            "ecart_moy": (sum(ecarts[x]) / len(ecarts[x])) if ecarts[x] else None,
            "ecart_max": max(ecarts[x] + [actuel]),
        }
    chi2 = sum((freq[x] - esp) ** 2 / esp for x in range(1, cfg.maxi + 1)) if n else 0.0
    chi2_corr = chi2 * (cfg.maxi - 1) / (cfg.maxi - cfg.tires)
    return {"n": n, "par_num": par_num, "chi2": chi2_corr, "p": p_valeur_khi2(chi2_corr, cfg.maxi - 1) if n >= 10 else None}


def poids(stats: dict, cfg: Config, chaud: float, retard: float) -> list[float]:
    n = stats["n"]
    confiance = n / (n + 50)
    a_c, a_r = chaud * confiance, retard * confiance
    w = [0.0] * (cfg.maxi + 1)
    for x, s in stats["par_num"].items():
        z = max(-3.0, min(3.0, s["z"]))
        r = max(0.0, min(4.0, s["ecart"] / cfg.ecart_attendu))
        w[x] = math.exp(0.35 * a_c * z) * (1 + 0.5 * a_r * r)
    return w


def equilibre(nums: list[int], cfg: Config) -> bool:
    k = len(nums)
    pairs = sum(1 for x in nums if x % 2 == 0)
    if k >= 3 and pairs in (0, k):
        return False
    if abs(sum(nums) - cfg.somme_moy) > 1.28 * cfg.somme_sigma:
        return False
    dizaines: dict[int, int] = {}
    for x in nums:
        dizaines[(x - 1) // 10] = dizaines.get((x - 1) // 10, 0) + 1
    return max(dizaines.values()) <= 2


def tirer(w: list[float], k: int, rng: random.Random) -> list[int]:
    restants = list(range(1, len(w)))
    choix = []
    for _ in range(k):
        x = rng.choices(restants, weights=[w[i] for i in restants])[0]
        restants.remove(x)
        choix.append(x)
    return choix


def grille(tirages, cfg: Config, chaud: float, retard: float, graine: int, avec_equilibre=True) -> list[int]:
    w = poids(statistiques(tirages, cfg), cfg, chaud, retard)
    rng = random.Random(graine)
    g = tirer(w, cfg.grille, rng)
    essais = 0
    while avec_equilibre and not equilibre(g, cfg) and essais < 5000:
        g = tirer(w, cfg.grille, rng)
        essais += 1
    return sorted(g)


def backtest(tirages, cfg: Config, chaud: float, retard: float, mini=30, max_tests=300):
    debut = max(mini, len(tirages) - max_tests)
    if debut >= len(tirages):
        return None
    bons_m, bons_a, repart = [], [], [0] * (cfg.grille + 1)
    for t in range(debut, len(tirages)):
        reel = set(tirages[t])
        g = grille(tirages[:t], cfg, chaud, retard, graine=t)
        b = len(reel & set(g))
        bons_m.append(b)
        repart[b] += 1
        alea = random.Random(1_000_003 * t + 7).sample(range(1, cfg.maxi + 1), cfg.grille)
        bons_a.append(len(reel & set(alea)))
    m = len(bons_m)
    moy = sum(bons_m) / m
    z = (moy - cfg.bons_attendus) / (cfg.bons_sigma / math.sqrt(m))
    return {"m": m, "moy": moy, "alea": sum(bons_a) / m, "z": z, "repart": repart}


def rapport(tirages, cfg: Config, chaud: float, retard: float) -> None:
    s = statistiques(tirages, cfg)
    n = s["n"]
    print(f"LOTO {cfg.tires}/{cfg.maxi} · {n} tirages · grille de {cfg.grille} numéros\n")
    print("REPÈRES THÉORIQUES")
    print(f"  P(un numéro sort) = 1/{1 / cfg.p:.0f} · sorties attendues/numéro = {n * cfg.p:.1f} · écart actuel attendu = {cfg.ecart_attendu:.0f}")
    for k in range(cfg.grille, 1, -1):
        print(f"  P({k} bons sur {cfg.grille}) = 1 sur {1 / cfg.proba_bons(k):,.0f}".replace(",", " "))
    if (cfg.maxi, cfg.tires) == (90, 5):
        print("\nLOTO BONHEUR : RETOUR MOYEN PAR PARI (cotes officielles LONACI)")
        paris = {"1N": (5 / 90, 10), "PN": (1 / 90, 40), "2N": (10 / 4005, 240),
                 "3N": (10 / 117480, 2000), "4N": (5 / 2555190, 5000), "5N": (1 / 43949268, 40000)}
        for nom, (p, cote) in sorted(paris.items(), key=lambda kv: -kv[1][0] * kv[1][1]):
            print(f"  {nom:3} cote {cote:6d}  1 sur {1 / p:>11,.0f}  rend {100 * p * cote:5.1f} FCFA / 100".replace(",", " "))
        print("  → le 2N perd le moins ; le 4N rend 1 FCFA pour 100 misés.")
    print("\nPLUS SORTIS")
    for x, v in sorted(s["par_num"].items(), key=lambda kv: (-kv[1]["freq"], kv[0]))[:10]:
        print(f"  {x:02d}  {v['freq']:4d} fois  z = {v['z']:+.2f}")
    print("\nPLUS EN RETARD")
    for x, v in sorted(s["par_num"].items(), key=lambda kv: (-kv[1]["ecart"], kv[0]))[:10]:
        moy = f"{v['ecart_moy']:.1f}" if v["ecart_moy"] is not None else "—"
        print(f"  {x:02d}  écart {v['ecart']:3d}  moyen {moy}  max {v['ecart_max']}")
    if s["p"] is not None:
        print(f"\nKHI-DEUX D'UNIFORMITÉ : χ² = {s['chi2']:.1f} (ddl {cfg.maxi - 1}), p = {s['p']:.3f}")
        print("  → compatible avec un tirage équitable" if s["p"] >= 0.01 else "  → écart inhabituel : vérifie d'abord les données")
    print("\nCHASSEUR DE BIAIS (test binomial unilatéral, correction de Bonferroni)")
    if n < 100:
        print("  Il faut au moins 100 tirages réels.")
    else:
        sigma = math.sqrt(n * cfg.p * (1 - cfg.p))
        biais = []
        for x, v in s["par_num"].items():
            z = (v["freq"] - 0.5 - n * cfg.p) / sigma
            p_corr = min(1.0, 0.5 * math.erfc(z / math.sqrt(2)) * cfg.maxi)
            if p_corr < 0.05:
                biais.append((p_corr, x, v["freq"]))
        if not biais:
            print("  Aucun numéro significativement sur-représenté → aucun biais exploitable.")
        for p_corr, x, f in sorted(biais):
            print(f"  {x:02d}  {f} sorties  p corrigé = {p_corr:.4f}  → à confirmer sur les tirages suivants")

    print("\nPARITÉ (nb de pairs par tirage)")
    pairs_tot = cfg.maxi // 2
    for p in range(cfg.tires + 1):
        obs = sum(1 for t in tirages if sum(1 for x in t if x % 2 == 0) == p) / max(n, 1)
        th = math.comb(pairs_tot, p) * math.comb(cfg.maxi - pairs_tot, cfg.tires - p) / math.comb(cfg.maxi, cfg.tires)
        print(f"  {p} pairs : observé {obs:6.1%}  théorique {th:6.1%}")

    g = grille(tirages, cfg, chaud, retard, graine=dt.date.today().toordinal())
    print(f"\nGRILLE PONDÉRÉE (chaud {chaud:.0%}, retard {retard:.0%}) : {' - '.join(f'{x:02d}' for x in g)}")

    bt = backtest(tirages, cfg, chaud, retard)
    print("\nBACKTEST WALK-FORWARD")
    if bt is None:
        print("  Il faut plus de 30 tirages.")
    else:
        print(f"  {bt['m']} grilles · bons/grille : modèle {bt['moy']:.3f} · aléatoire {bt['alea']:.3f} · théorique {cfg.bons_attendus:.3f} · z = {bt['z']:+.2f}")
        for k, c in enumerate(bt["repart"]):
            print(f"    {k} bon(s) : {c / bt['m']:6.1%}  attendu {cfg.proba_bons(k):6.1%}")
        print("  → aucune différence significative avec le hasard" if bt["z"] < 2 else
              "  → mieux que le hasard sur cette période (coup de chance si les tirages sont équitables)")
    print("\nChaque tirage est indépendant : aucune méthode ne prédit les numéros. Joue avec modération.")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("fichier", nargs="?", help="historique (un tirage par ligne)")
    ap.add_argument("--max", type=int, default=90, help="plus grand numéro (90)")
    ap.add_argument("--tires", type=int, default=5, help="numéros tirés par tirage (5)")
    ap.add_argument("--grille", type=int, default=4, help="numéros à proposer (4)")
    ap.add_argument("--chaud", type=float, default=0.5, help="poids des numéros fréquents, 0 à 1")
    ap.add_argument("--retard", type=float, default=0.5, help="poids des numéros en retard, 0 à 1")
    ap.add_argument("--simuler", type=int, metavar="N", help="écrit N tirages SIMULÉS sur la sortie standard")
    a = ap.parse_args()
    cfg = Config(a.max, a.tires, a.grille)

    if a.simuler:
        rng = random.Random(2026)
        print(f"# {a.simuler} tirages SIMULÉS ({cfg.tires}/{cfg.maxi}) — PAS de vrais résultats, pour tester uniquement")
        for _ in range(a.simuler):
            print(",".join(str(x) for x in sorted(rng.sample(range(1, cfg.maxi + 1), cfg.tires))))
        return
    if not a.fichier:
        ap.error("indique un fichier d'historique ou --simuler N")
    tirages = lire(a.fichier, cfg)
    if not tirages:
        sys.exit("Aucun tirage valide dans le fichier.")
    rapport(tirages, cfg, a.chaud, a.retard)


if __name__ == "__main__":
    main()
