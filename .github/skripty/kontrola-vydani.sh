#!/usr/bin/env bash
# Kontroly před schválením vydání z ručního běhu Build. Vstup z prostředí: TAG (v<verze>),
# GITHUB_SHA, GITHUB_REF, GH_REPO, SPUSTIL (kdo běh spustil); větve repozitáře jako refs/remotes/origin/*.
set -uo pipefail

SOUHRN="${GITHUB_STEP_SUMMARY:-/dev/null}"
CHYB=0
RADKY=""

ok() { RADKY="$RADKY| ✅ | $1 | $2 |"$'\n'; }
chyba() {
  RADKY="$RADKY| ❌ | $1 | $2 |"$'\n'
  echo "::error title=$1::$2"
  CHYB=$((CHYB + 1))
}

# Vypíše odpověď gh api; návratový kód 0 = nalezeno, 4 = HTTP 404, jinak chyba dotazu.
api() {
  local vystup
  if vystup=$(gh api "$@" 2>&1); then
    printf '%s' "$vystup"
    return 0
  fi
  printf '%s' "$vystup"
  if printf '%s' "$vystup" | grep -q 'HTTP 404'; then return 4; fi
  return 1
}

# Hodnota přichází z formuláře, do hlášek a dotazů smí jen po kontrole celého řetězce.
if [[ ! $TAG =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-beta\.[0-9]+)?$ ]]; then
  chyba "Verze" "Verze $(printf '%s' "$TAG" | tr -c '0-9A-Za-z.-' '?') nemá tvar X.Y.Z nebo X.Y.Z-beta.N."
  printf '### Kontrola vydání\n\n| | kontrola | výsledek |\n|---|---|---|\n%s' "$RADKY" >> "$SOUHRN"
  exit 1
fi
VERZE="${TAG#v}"
ZAKLAD="${VERZE%%-*}"
ok "Verze" "$VERZE, tag $TAG"

ODPOVED=$(api "repos/$GH_REPO/git/ref/tags/$TAG"); RC=$?
case $RC in
  4) ok "Tag" "$TAG zatím neexistuje" ;;
  0) chyba "Tag" "Tag $TAG už existuje, zvol další číslo verze." ;;
  *) chyba "Tag" "Dotaz na tag $TAG selhal: $ODPOVED" ;;
esac

# Bez práva zápisu vrací seznam jen publikovaná vydání, koncepty se v něm neukážou.
if ODPOVED=$(api --paginate "repos/$GH_REPO/releases?per_page=100"); then
  NALEZENO=$(printf '%s' "$ODPOVED" | jq -rs --arg t "$TAG" '[.[][] | select(.tag_name == $t) | if .draft then "koncept" else "vydání" end] | unique | join(", ")')
  if [ -z "$NALEZENO" ]; then
    ok "Release" "release s tagem $TAG neexistuje"
  else
    chyba "Release" "S tagem $TAG už existuje $NALEZENO, zvol další číslo verze."
  fi
else
  chyba "Release" "Dotaz na seznam vydání selhal: $ODPOVED"
fi

# 2. Ruční Smoke na 3 OS na stejném commitu
SMOKE=""
if ODPOVED=$(api "repos/$GH_REPO/actions/workflows/smoke.yml/runs?head_sha=$GITHUB_SHA&event=workflow_dispatch&status=success&per_page=100"); then
  for BEH in $(printf '%s' "$ODPOVED" | jq -r '.workflow_runs[] | select(.conclusion == "success") | .id'); do
    JOBY=$(api "repos/$GH_REPO/actions/runs/$BEH/jobs?per_page=100") || continue
    POCET=$(printf '%s' "$JOBY" | jq '[.jobs[] | select(.conclusion == "success") | .name
      | select(. == "smoke (ubuntu-latest)" or . == "smoke (windows-latest)" or . == "smoke (macos-latest)")] | unique | length')
    if [ "$POCET" = 3 ]; then SMOKE="$BEH"; break; fi
  done
  if [ -n "$SMOKE" ]; then
    ok "Smoke" "ruční běh $SMOKE prošel na Linuxu, Windows i macOS"
  else
    chyba "Smoke" "Na commitu ${GITHUB_SHA::7} není zelený ruční běh Smoke na Linuxu, Windows i macOS. Spusť Smoke ručně na této větvi a počkej na výsledek."
  fi
else
  chyba "Smoke" "Dotaz na běhy Smoke selhal: $ODPOVED"
fi

# 3. CHANGELOG a pom.xml
POPIS=$(awk -v z="## $ZAKLAD" '$0==z{f=1;next} /^## /{f=0} f' CHANGELOG.md | tr -d '[:space:]')
if [ -n "$POPIS" ]; then
  ok "CHANGELOG" "oddíl ## $ZAKLAD je popis vydání"
else
  chyba "CHANGELOG" "CHANGELOG.md nemá neprázdný oddíl ## $ZAKLAD, vydání by nemělo popis."
fi

POM=$(grep -m1 -oP '(?<=<version>)[^<]+' pom.xml)
if [ "$POM" = "$ZAKLAD" ]; then
  ok "pom.xml" "verze $POM"
else
  chyba "pom.xml" "Verze v pom.xml je $POM, vydání $VERZE potřebuje $ZAKLAD."
fi

# 4. Větev: z main, nebo z release/X.Y.x řady vydávané verze.
RADA="${ZAKLAD%.*}"
ZDROJ="${GITHUB_REF#refs/heads/}"
case "$GITHUB_REF" in
  refs/heads/main | "refs/heads/release/$RADA.x")
    if git merge-base --is-ancestor "$GITHUB_SHA" "refs/remotes/origin/$ZDROJ" 2>/dev/null; then
      ok "Větev" "commit ${GITHUB_SHA::7} leží na $ZDROJ"
    else
      chyba "Větev" "Commit ${GITHUB_SHA::7} neleží na větvi $ZDROJ."
    fi ;;
  refs/heads/release/*)
    chyba "Větev" "Verze $VERZE nepatří na větev $ZDROJ, z release/* jde vydat jen verze řady té větve." ;;
  *)
    chyba "Větev" "Vydání jde spustit jen z main nebo z release/X.Y.x, ne z $GITHUB_REF." ;;
esac

printf '### Kontrola vydání %s (spustil %s)\n\n| | kontrola | výsledek |\n|---|---|---|\n%s' "$VERZE" "${SPUSTIL:-?}" "$RADKY" >> "$SOUHRN"
[ "$CHYB" = 0 ] || { echo "::error::Kontrola vydání $TAG neprošla (počet chyb: $CHYB), vydání se nedostane ke schválení."; exit 1; }
