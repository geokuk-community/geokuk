#!/bin/sh
# Soukromá konfigurace vývoje v cloud session.
# Použití: soukrome.sh start|end
#
# Proměnné prostředí (nastavují se v cloud environmentu, ne v repu):
#   POZNAMKY_URL    HTTPS adresa soukromého repa (povinná)
#   POZNAMKY_USER   uživatel pro přihlášení
#   POZNAMKY_TOKEN  token; bez něj se spoléhá na přihlášení doplněné proxy

set -u

[ "${CLAUDE_CODE_REMOTE:-false}" = "true" ] || exit 0
[ -n "${POZNAMKY_URL:-}" ] || exit 0

ROOT="${CLAUDE_PROJECT_DIR:-.}"
DIR="$ROOT/poznamky"

# Token se čte z prostředí při každém volání, na disk se nezapisuje.
HELPER='!f() { test "$1" = get || exit 0; echo "username=${POZNAMKY_USER:-git}"; echo "password=${POZNAMKY_TOKEN:-}"; }; f'

case "${1:-}" in
  start)
    EXCLUDE="$ROOT/.git/info/exclude"
    for p in /poznamky/ /CLAUDE.md; do
      grep -qxF "$p" "$EXCLUDE" 2>/dev/null || echo "$p" >> "$EXCLUDE"
    done
    if [ -d "$DIR/.git" ]; then
      git -C "$DIR" pull -q --rebase || echo "soukrome: pull selhal, pracuj s lokální kopií"
    else
      if [ -n "${POZNAMKY_TOKEN:-}" ]; then
        git -c credential.helper= -c "credential.helper=$HELPER" clone -q "$POZNAMKY_URL" "$DIR" || { echo "soukrome: klonování selhalo"; exit 0; }
        git -C "$DIR" config credential.helper ""
        git -C "$DIR" config --add credential.helper "$HELPER"
      else
        git clone -q "$POZNAMKY_URL" "$DIR" || { echo "soukrome: klonování selhalo"; exit 0; }
      fi
      NAME=$(git -C "$ROOT" config user.name || true)
      MAIL=$(git -C "$ROOT" config user.email || true)
      [ -n "$NAME" ] && git -C "$DIR" config user.name "$NAME"
      [ -n "$MAIL" ] && git -C "$DIR" config user.email "$MAIL"
    fi
    [ -f "$DIR/agent/session-start.sh" ] && sh "$DIR/agent/session-start.sh"
    [ -f "$DIR/agent/AGENTS.md" ] && cat "$DIR/agent/AGENTS.md"
    ;;
  end)
    [ -d "$DIR/.git" ] || exit 0
    if [ -n "$(git -C "$DIR" status --porcelain)" ]; then
      git -C "$DIR" add -A && git -C "$DIR" commit -q -m "Uložení na konci session"
    fi
    git -C "$DIR" pull -q --rebase || true
    git -C "$DIR" push -q || echo "soukrome: push selhal"
    ;;
esac
exit 0
