#!/bin/sh
# Soukromá konfigurace vývoje v cloud session.
# Použití: soukrome.sh start|end
#
# Proměnné prostředí (nastavují se v cloud environmentu, ne v repu):
#   POZNAMKY_URL          HTTPS adresa soukromého repa (povinná, končí .git)
#   POZNAMKY_USER         uživatel pro přihlášení
#   POZNAMKY_TOKEN        token; bez něj se spoléhá na přihlášení doplněné proxy
#   POZNAMKY_PEM_B64      base64 PEM s klientským certifikátem i klíčem (mTLS)

set -u

[ "${CLAUDE_CODE_REMOTE:-false}" = "true" ] || exit 0
[ -n "${POZNAMKY_URL:-}" ] || exit 0

ROOT="${CLAUDE_PROJECT_DIR:-.}"
DIR="$ROOT/poznamky"

# Bez terminálu by git při odmítnutém přihlášení čekal na vstup.
GIT_TERMINAL_PROMPT=0
export GIT_TERMINAL_PROMPT

# Token se čte z prostředí při každém volání, na disk se nezapisuje.
HELPER='!f() { test "$1" = get || exit 0; echo "username=${POZNAMKY_USER:-git}"; echo "password=${POZNAMKY_TOKEN:-}"; }; f'

# Klientský certifikát jen v dočasném souboru, který po doběhnutí zmizí.
# Vadný certifikát hook nezastaví, aby se na konci session neztratila práce.
if [ -n "${POZNAMKY_PEM_B64:-}" ]; then
  if PEMDIR=$(mktemp -d); then
    trap 'rm -rf "$PEMDIR"' EXIT
    PEM="$PEMDIR/klient.pem"
    if printf '%s' "$POZNAMKY_PEM_B64" | (umask 077 && base64 -d > "$PEM") \
        && grep -q 'BEGIN CERTIFICATE' "$PEM" && grep -q 'PRIVATE KEY' "$PEM"; then
      chmod 600 "$PEM"
      GIT_SSL_CERT="$PEM"
      GIT_SSL_KEY="$PEM"
      export GIT_SSL_CERT GIT_SSL_KEY
    else
      echo "soukrome: POZNAMKY_PEM_B64 není base64 PEM s certifikátem a klíčem, pokračuji bez něj"
    fi
  else
    echo "soukrome: nejde vytvořit dočasný adresář pro certifikát, pokračuji bez něj"
  fi
fi

# Git po síti; přenos pod 1 kB/s trvající CAS sekund se ukončí, aby hook nevisel.
CAS=20
gs() {
  git -c http.lowSpeedLimit=1000 -c "http.lowSpeedTime=$CAS" "$@"
}

# Git nad poznámkami; s tokenem i u kopie, která helper v konfiguraci nemá.
gp() {
  if [ -n "${POZNAMKY_TOKEN:-}" ]; then
    gs -C "$DIR" -c credential.helper= -c "credential.helper=$HELPER" "$@"
  else
    gs -C "$DIR" "$@"
  fi
}

# Rebase zastavený na konfliktech vrátí zpět; úspěch znamená, že konflikt byl.
zrus_rebase() {
  [ -d "$DIR/.git/rebase-merge" ] || [ -d "$DIR/.git/rebase-apply" ] || return 1
  gp rebase --abort >/dev/null 2>&1
  return 0
}

# Pull a push; při konfliktu nastaví KONFLIKT a kopii vrátí do použitelného stavu.
synchronizuj() {
  if ! gp pull -q --rebase --autostash; then
    zrus_rebase && KONFLIKT=1
    return 1
  fi
  gp push -q
}

# Prázdná kopie (git init bez commitů) se napojí na výchozí větev vzdáleného repa; soubory v ní zůstanou.
napoj_prazdnou_kopii() {
  git -C "$DIR" remote get-url origin >/dev/null 2>&1 || git -C "$DIR" remote add origin "$POZNAMKY_URL"
  VETEV=$(gp ls-remote --symref origin HEAD 2>/dev/null | sed -n 's|^ref: refs/heads/\([^[:space:]]*\)[[:space:]]*HEAD$|\1|p')
  VETEV=${VETEV:-main}
  if gp fetch -q origin "$VETEV" && git -C "$DIR" checkout -q -B "$VETEV" "origin/$VETEV" \
      && git -C "$DIR" branch -q --set-upstream-to="origin/$VETEV" "$VETEV"; then
    return 0
  fi
  echo "soukrome: prázdnou kopii se nepodařilo napojit na větev $VETEV, pracuj s lokální kopií"
}

# Patch ze stdin uloží do poznámek a vypíše, aby neodeslaná práce nezmizela se session.
zachrana() {
  SOUBOR="$DIR/ZACHRANA-$(date -u +%Y%m%d-%H%M%S)-$1.patch"
  echo "soukrome: push selhal, patch s neodeslanou prací: $SOUBOR"
  echo "----- ZACHRANA $1 -----"
  tee "$SOUBOR"
  echo "----- KONEC ZACHRANY $1 -----"
}

case "${1:-}" in
  start)
    EXCLUDE="$ROOT/.git/info/exclude"
    for p in /poznamky/ /CLAUDE.md; do
      grep -qxF "$p" "$EXCLUDE" 2>/dev/null || echo "$p" >> "$EXCLUDE"
    done
    if [ -d "$DIR/.git" ]; then
      if ! git -C "$DIR" rev-parse -q --verify HEAD >/dev/null 2>&1; then
        napoj_prazdnou_kopii
      elif ! gp pull -q --rebase --autostash; then
        if zrus_rebase; then
          echo "soukrome: pull má konflikty, lokální změny zůstaly nad starší verzí"
        else
          echo "soukrome: pull selhal, pracuj s lokální kopií"
        fi
      fi
    else
      if [ -n "${POZNAMKY_TOKEN:-}" ]; then
        gs -c credential.helper= -c "credential.helper=$HELPER" clone -q "$POZNAMKY_URL" "$DIR" || { echo "soukrome: klonování selhalo"; exit 0; }
      else
        gs clone -q "$POZNAMKY_URL" "$DIR" || { echo "soukrome: klonování selhalo"; exit 0; }
      fi
      NAME=$(git -C "$ROOT" config user.name || true)
      MAIL=$(git -C "$ROOT" config user.email || true)
      [ -n "$NAME" ] && git -C "$DIR" config user.name "$NAME"
      [ -n "$MAIL" ] && git -C "$DIR" config user.email "$MAIL"
    fi
    # Helper i do kopie, která ho nemá, aby prošel i ruční push mimo hook.
    if [ -n "${POZNAMKY_TOKEN:-}" ]; then
      git -C "$DIR" config --replace-all credential.helper ""
      git -C "$DIR" config --add credential.helper "$HELPER"
    fi
    [ -f "$DIR/agent/AGENTS.md" ] && cat "$DIR/agent/AGENTS.md"
    ;;
  end)
    [ -d "$DIR/.git" ] || exit 0
    # SessionEnd má na vše nejvýš 60 s, i s opakovaným pokusem a záchranou.
    CAS=10
    if [ -n "$(git -C "$DIR" status --porcelain)" ]; then
      NAME=$(git -C "$DIR" config user.name || git -C "$ROOT" config user.name || echo cloud-session)
      MAIL=$(git -C "$DIR" config user.email || git -C "$ROOT" config user.email || echo cloud-session@localhost)
      git -C "$DIR" add -A && git -C "$DIR" -c "user.name=$NAME" -c "user.email=$MAIL" commit -q -m "Uložení na konci session" \
        || echo "soukrome: commit selhal, změny zůstávají nezapsané"
    fi
    NEZAPSANO=$(git -C "$DIR" status --porcelain)
    UP=$(git -C "$DIR" rev-parse -q --verify '@{u}' 2>/dev/null || git -C "$DIR" rev-parse -q --verify origin/HEAD 2>/dev/null || true)
    if [ -z "$UP" ] || [ -n "$(git -C "$DIR" rev-list -n 1 "$UP..HEAD")" ]; then
      KONFLIKT=
      ODESLANO=
      synchronizuj && ODESLANO=1
      if [ -z "$ODESLANO" ] && [ -z "$KONFLIKT" ]; then
        synchronizuj && ODESLANO=1
      fi
      if [ -z "$ODESLANO" ]; then
        VETEV="zachrana-$(date -u +%Y%m%d-%H%M%S)"
        if gp push -q origin "HEAD:refs/heads/$VETEV"; then
          echo "soukrome: push do hlavní větve selhal, commity jsou ve větvi $VETEV"
        else
          if [ -n "$UP" ]; then ROZSAH="$UP..HEAD"; else ROZSAH=--root; fi
          git -C "$DIR" format-patch --stdout --binary "$ROZSAH" | zachrana commity
        fi
      fi
    fi
    if [ -n "$NEZAPSANO" ]; then
      git -C "$DIR" diff --cached --binary HEAD | zachrana zmeny
    fi
    ;;
esac
exit 0
