#!/usr/bin/env python3
"""Pilote de fusion git pour les fichiers de langue (app/src/main/assets/lang/*.json).

Wuki60 et ce dépôt ajoutent chacun leurs textes en fin de fichier : une fusion ligne à ligne y
voit un conflit à chaque version. Ici, fusion par clé :
- clé ajoutée d'un côté : gardée ;
- clé changée d'un seul côté : ce changement ;
- clé changée des deux côtés : celle de Wuki60 (B, la version fusionnée) ;
- clé retirée d'un côté et inchangée de l'autre : retirée.
Usage (git) : json-union.py %O %A %B  → écrit le résultat dans %A, code 0.
"""
import json
import sys


def load(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def main():
    o, a, b = (load(p) for p in sys.argv[1:4])
    out = {}
    for k in list(a.keys()) + [k for k in b.keys() if k not in a]:
        in_a, in_b, in_o = k in a, k in b, k in o
        if in_a and in_b:
            out[k] = b[k] if (a[k] == o.get(k) or a[k] != b[k] and o.get(k) != b[k]) else a[k]
        elif in_a:  # absente de B
            if in_o and a[k] == o[k]:
                continue  # retirée par Wuki60
            out[k] = a[k]
        else:  # absente de A
            if in_o and b[k] == o[k]:
                continue  # retirée ici
            out[k] = b[k]
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        f.write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
