#!/usr/bin/env python3
"""
Récupère TOUS les résultats officiels du Loto Bonheur depuis l'API publique
du site lotobonheur.ci (la même que celle qu'utilise la page « Résultats »).

Produit :
  data/loto_bonheur_complet.csv      date;tirage;gagnants (ordre de tirage);machine
  data/import/tous.txt               format d'import de l'appli : date;n1,n2,n3,n4,n5
  data/import/<Tirage>.txt           un fichier par tirage (une machine/horaire)

Utilisation :
  python3 python/recuperer_resultats.py            # tout l'historique (depuis 10/2020)
  python3 python/recuperer_resultats.py --mois 3   # seulement les 3 derniers mois (mise à jour)

Aucune dépendance externe.
"""
from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://lotobonheur.ci/api/results"
MOIS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet",
        "août", "septembre", "octobre", "novembre", "décembre"]
DEBUT = (2020, 10)
RACINE = Path(__file__).resolve().parent.parent / "data"


def appeler(mois: int, annee: int) -> dict:
    params = urllib.parse.urlencode({"monthYear": f"{MOIS[mois - 1]} {annee}", "drawType": "Tous les tirages"})
    req = urllib.request.Request(
        f"{API}?{params}",
        headers={"User-Agent": "Mozilla/5.0 (LOTOPOSBOT; analyse statistique)", "Accept": "application/json"},
    )
    for essai in range(4):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:  # réseau capricieux : on réessaie
            if essai == 3:
                raise
            print(f"  nouvel essai ({e})", file=sys.stderr)
            time.sleep(2 * (essai + 1))
    return {}


def tirages_du_mois(mois: int, annee: int):
    data = appeler(mois, annee)

    def parcourir(o):
        if isinstance(o, list):
            for x in o:
                yield from parcourir(x)
        elif isinstance(o, dict):
            if "drawName" in o and "winningNumbers" in o:
                yield o
            else:
                for v in o.values():
                    yield from parcourir(v)

    for semaine in data.get("drawsResultsWeekly", []):
        for jour in semaine.get("drawResultsDaily", []):
            m = re.search(r"(\d{2})/(\d{2})", jour.get("date", ""))
            if not m:
                continue
            j, mo = int(m.group(1)), int(m.group(2))
            an = annee
            if mois == 1 and mo == 12:
                an -= 1
            elif mois == 12 and mo == 1:
                an += 1
            for t in parcourir(jour.get("drawResults", {})):
                nom = (t.get("drawName") or "").strip()
                gagnants = [int(x) for x in re.findall(r"\d+", t.get("winningNumbers") or "")]
                machine = [int(x) for x in re.findall(r"\d+", t.get("machineNumbers") or "")]
                if nom in ("", "-") or len(gagnants) != 5 or len(set(gagnants)) != 5 \
                        or not all(1 <= x <= 90 for x in gagnants):
                    continue
                yield {
                    "date": dt.date(an, mo, j).isoformat(),
                    "tirage": nom,
                    "gagnants": gagnants,
                    "machine": machine if len(machine) == 5 else [],
                }


def liste_mois(nb_derniers: int | None):
    aujourd_hui = dt.date.today()
    a, m = aujourd_hui.year, aujourd_hui.month
    mois = []
    while (a, m) >= DEBUT:
        mois.append((m, a))
        m -= 1
        if m == 0:
            m, a = 12, a - 1
    mois.reverse()
    return mois[-nb_derniers:] if nb_derniers else mois


def charger_existant(chemin: Path) -> dict:
    if not chemin.exists():
        return {}
    res = {}
    with chemin.open(encoding="utf-8") as f:
        for ligne in csv.DictReader(f, delimiter=";"):
            res[(ligne["date"], ligne["tirage"])] = {
                "date": ligne["date"],
                "tirage": ligne["tirage"],
                "gagnants": [int(x) for x in ligne["gagnants"].split(",")],
                "machine": [int(x) for x in ligne["machine"].split(",")] if ligne["machine"] else [],
            }
    return res


def nom_fichier(tirage: str) -> str:
    return re.sub(r"[^A-Za-z0-9]+", "_", tirage).strip("_") + ".txt"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mois", type=int, help="ne récupérer que les N derniers mois")
    args = ap.parse_args()

    complet = RACINE / "loto_bonheur_complet.csv"
    tous = charger_existant(complet)
    avant = len(tous)

    for mois, annee in liste_mois(args.mois):
        n = 0
        for t in tirages_du_mois(mois, annee):
            tous[(t["date"], t["tirage"])] = t
            n += 1
        print(f"{MOIS[mois - 1]} {annee} : {n} tirages", file=sys.stderr)
        time.sleep(0.3)  # politesse envers le serveur

    lignes = sorted(tous.values(), key=lambda t: (t["date"], t["tirage"]))
    if not lignes:
        sys.exit("Aucun tirage récupéré : l'API a peut-être changé.")

    RACINE.mkdir(parents=True, exist_ok=True)
    with complet.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter=";", lineterminator="\n")
        w.writerow(["date", "tirage", "gagnants", "machine"])
        for t in lignes:
            w.writerow([t["date"], t["tirage"], ",".join(map(str, t["gagnants"])), ",".join(map(str, t["machine"]))])

    dossier = RACINE / "import"
    dossier.mkdir(exist_ok=True)
    for ancien in dossier.glob("*.txt"):
        ancien.unlink()
    entete = "# Résultats officiels Loto Bonheur (source : lotobonheur.ci) — {}\n"
    par_tirage: dict[str, list] = {}
    for t in lignes:
        par_tirage.setdefault(t["tirage"], []).append(t)
    with (dossier / "tous.txt").open("w", encoding="utf-8") as f:
        f.write(entete.format("tous les tirages"))
        f.writelines(f"{t['date']};{','.join(map(str, t['gagnants']))}\n" for t in lignes)
    for nom, ts in par_tirage.items():
        with (dossier / nom_fichier(nom)).open("w", encoding="utf-8") as f:
            f.write(entete.format(nom))
            f.writelines(f"{t['date']};{','.join(map(str, t['gagnants']))}\n" for t in ts)

    print(f"{len(lignes)} tirages au total ({len(lignes) - avant:+d}), {len(par_tirage)} types de tirage.",
          file=sys.stderr)


if __name__ == "__main__":
    main()
