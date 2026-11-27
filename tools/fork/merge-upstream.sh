#!/usr/bin/env bash
# Fusionne la dernière version publiée de Wuki60 dans la branche courante, Cookomix compris.
#  - fichiers de langue : fusion par clé (tools/fork/json-union.py) ;
#  - .github/ : toujours celui de ce dépôt (le workflow de publication de Wuki60 n'a rien à faire ici) ;
#  - tout autre conflit : fusion annulée, code 3, fichiers en conflit dans $CONFLICTS_FILE.
# Écrit dans $GITHUB_OUTPUT (si présent) : tag=<version Wuki60>, merged=0|1.
set -euo pipefail
cd "$(dirname "$0")/../.."
UPSTREAM_REPO=${UPSTREAM_REPO:-Wuki60/MrCuisineVibeConnect}
OUT=${GITHUB_OUTPUT:-/dev/null}
CONFLICTS_FILE=${CONFLICTS_FILE:-conflicts.txt}

auth=()
[ -n "${GH_TOKEN:-}" ] && auth=(-H "Authorization: Bearer $GH_TOKEN")
UPSTREAM_URL=${UPSTREAM_URL:-https://github.com/$UPSTREAM_REPO.git}
# UPSTREAM_TAG : pour essayer avec une version donnée (sinon : la dernière publiée).
tag=${UPSTREAM_TAG:-}
[ -n "$tag" ] || tag=$(curl -fsSL "${auth[@]}" "https://api.github.com/repos/$UPSTREAM_REPO/releases/latest" |
  python3 -c 'import json,sys; print(json.load(sys.stdin)["tag_name"])')
echo "Dernière version de $UPSTREAM_REPO : $tag"
echo "tag=$tag" >> "$OUT"

git fetch --no-tags "$UPSTREAM_URL" "+refs/tags/$tag:refs/upstream/$tag"
if git merge-base --is-ancestor "refs/upstream/$tag" HEAD; then
  echo "$tag déjà fusionnée."
  echo "merged=0" >> "$OUT"
  exit 0
fi

git config merge.jsonunion.name "fusion par clé des fichiers de langue"
git config merge.jsonunion.driver "python3 tools/fork/json-union.py %O %A %B"
mkdir -p .git/info
grep -qs 'merge=jsonunion' .git/info/attributes || echo 'app/src/main/assets/lang/*.json merge=jsonunion' >> .git/info/attributes

git merge --no-commit --no-ff "refs/upstream/$tag" || true
# .github/ tel qu'il est dans ce dépôt, quoi que Wuki60 y ait changé.
git rm -r -q --cached --ignore-unmatch .github
rm -rf .github
git checkout HEAD -- .github
git add .github

conflicts=$(git diff --name-only --diff-filter=U)
if [ -n "$conflicts" ]; then
  echo "$conflicts" > "$CONFLICTS_FILE"
  echo "Conflits avec $tag :"; echo "$conflicts"
  git merge --abort
  exit 3
fi
git commit -q -m "Wuki60 $tag + Cookomix (fusion automatique)"
echo "Fusion de $tag faite : $(git rev-parse --short HEAD)"
echo "merged=1" >> "$OUT"
