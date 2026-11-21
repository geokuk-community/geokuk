#!/usr/bin/env bash
# Kontroly před schválením vydání. Vstup z prostředí: TAG (v<verze>), GITHUB_SHA, GITHUB_REF,
# GITHUB_REF_TYPE, GH_REPO; větve repozitáře jako refs/remotes/origin/*.
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

VERZE="${TAG#v}"
ZAKLAD="${VERZE%%-*}"

# 1. Verze a tag
if ! printf '%s\n' "$VERZE" | grep -qxE '[0-9]+\.[0-9]+\.[0-9]+(-[a-z]+\.[0-9]+)?' || [ "$TAG" != "v$VERZE" ]; then
  chyba "Verze" "Tag $TAG nemá tvar vX.Y.Z nebo vX.Y.Z-beta.N."
  printf '### Kontrola vydání %s\n\n| | kontrola | výsledek |\n|---|---|---|\n%s' "$TAG" "$RADKY" >> "$SOUHRN"
  exit 1
fi
ok "Verze" "$VERZE, tag $TAG"

if [ "$GITHUB_REF_TYPE" = tag ]; then
  ok "Tag" "vydání spuštěno tagem $TAG"
else
  ODPOVED=$(api "repos/$GH_REPO/git/ref/tags/$TAG"); RC=$?
  case $RC in
    4) ok "Tag" "$TAG zatím neexistuje" ;;
    0) chyba "Tag" "Tag $TAG už existuje, zvol další číslo verze." ;;
    *) chyba "Tag" "Dotaz na tag $TAG selhal: $ODPOVED" ;;
  esac
fi

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
    POCET=$(printf '%s' "$JOBY" | jq '[.jobs[] | select(.conclusion == "success") | .labels[]
      | select(. == "ubuntu-latest" or . == "windows-latest" or . == "macos-latest")] | unique | length')
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

# 4. Větev: stabilní jen z main nebo release/X.Y.x téže řady, beta z main nebo release/*;
# ruční běh na release/* jen s verzí řady té větve.
RADA="${ZAKLAD%.*}"
case "$GITHUB_REF" in
  refs/heads/*) ZDROJ="${GITHUB_REF#refs/heads/}" ;;
  *) ZDROJ="" ;;
esac
VETVE=""
POVOLENE=""
for V in $(git for-each-ref --format='%(refname:lstrip=3)' refs/remotes/origin/main refs/remotes/origin/release); do
  git merge-base --is-ancestor "$GITHUB_SHA" "refs/remotes/origin/$V" 2>/dev/null || continue
  VETVE="$VETVE $V"
  if [ -n "$ZDROJ" ] && [ "$V" != "$ZDROJ" ]; then continue; fi
  case "$V" in
    main | "release/$RADA.x") POVOLENE="$POVOLENE $V" ;;
    release/*) [ -z "$ZDROJ" ] && [ "$VERZE" != "$ZAKLAD" ] && POVOLENE="$POVOLENE $V" ;;
  esac
done
if [ -z "$VETVE" ]; then
  chyba "Větev" "Commit ${GITHUB_SHA::7} neleží na main ani na release/*, vydání se nesestaví."
elif [ -z "$POVOLENE" ]; then
  if [ -n "$ZDROJ" ]; then
    chyba "Větev" "Verze $VERZE nepatří na větev $ZDROJ, z release/* jde vydat jen verze řady té větve."
  else
    chyba "Větev" "Stabilní verze $VERZE se vydává jen z main nebo z release/$RADA.x, commit leží na:$VETVE."
  fi
else
  ok "Větev" "commit ${GITHUB_SHA::7} leží na:$POVOLENE"
fi

printf '### Kontrola vydání %s\n\n| | kontrola | výsledek |\n|---|---|---|\n%s' "$TAG" "$RADKY" >> "$SOUHRN"
[ "$CHYB" = 0 ] || { echo "::error::Kontrola vydání $TAG: $CHYB chyb, vydání se nedostane ke schválení."; exit 1; }
