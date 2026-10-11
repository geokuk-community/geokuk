#!/bin/sh
# Maven v cloud session: proxy pro stahování závislostí a jejich předstažení.
# Java nečte https_proxy. Když prostředí nedá proxy do JAVA_TOOL_OPTIONS,
# zapíše se do ~/.m2/settings.xml, které Maven čte při každém běhu.
# https://github.com/anthropics/claude-code/issues/13372
# https://github.com/anthropics/claude-code/issues/16222

set -u

[ "${CLAUDE_CODE_REMOTE:-false}" = "true" ] || exit 0
[ -n "${HOME:-}" ] || exit 0

ROOT="${CLAUDE_PROJECT_DIR:-.}"
SETTINGS_XML="$HOME/.m2/settings.xml"
ID=cloud-session-proxy

# settings.xml zapsaný tímto hookem; po obnovení session má proxy jiný port.
nas() { grep -q "<id>$ID</id>" "$SETTINGS_XML" 2>/dev/null; }

case "${JAVA_TOOL_OPTIONS:-}" in
  *https.proxyHost=*)
    if nas; then
      rm -f "$SETTINGS_XML"
      echo "maven-proxy: proxy je v JAVA_TOOL_OPTIONS, starý $SETTINGS_XML smazán"
    fi
    ;;
  *)
    PROXY_URL="${https_proxy:-${HTTPS_PROXY:-${http_proxy:-${HTTP_PROXY:-}}}}"
    if [ -z "$PROXY_URL" ]; then
      echo "maven-proxy: proxy není nastavená, settings.xml se nemění"
    elif [ -f "$SETTINGS_XML" ] && ! nas; then
      echo "maven-proxy: cizí $SETTINGS_XML se nemění"
    else
      # scheme://[user:pass@]host:port
      STRIPPED=$(printf '%s' "$PROXY_URL" | sed -E 's#^[a-zA-Z]+://##')
      USERINFO=""
      case "$STRIPPED" in
        *@*)
          USERINFO="${STRIPPED%%@*}"
          STRIPPED="${STRIPPED#*@}"
          ;;
      esac
      PROXY_HOST="${STRIPPED%%:*}"
      PROXY_PORT="${STRIPPED##*:}"
      PROXY_PORT="${PROXY_PORT%%/*}"
      PROXY_USER=""
      PROXY_PASS=""
      if [ -n "$USERINFO" ]; then
        PROXY_USER="${USERINFO%%:*}"
        case "$USERINFO" in
          *:*) PROXY_PASS="${USERINFO#*:}" ;;
        esac
      fi

      NONPROXY="localhost|127.0.0.1"
      if [ -n "${NO_PROXY:-${no_proxy:-}}" ]; then
        NONPROXY="$NONPROXY|$(printf '%s' "${NO_PROXY:-$no_proxy}" | tr ',' '|')"
      fi

      mkdir -p "$HOME/.m2"
      {
        echo '<?xml version="1.0" encoding="UTF-8"?>'
        echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">'
        echo '  <proxies>'
        for PROTOKOL in http https; do
          echo '    <proxy>'
          if [ "$PROTOKOL" = http ]; then echo "      <id>$ID</id>"; else echo "      <id>$ID-https</id>"; fi
          echo '      <active>true</active>'
          echo "      <protocol>$PROTOKOL</protocol>"
          echo "      <host>${PROXY_HOST}</host>"
          echo "      <port>${PROXY_PORT}</port>"
          if [ -n "$PROXY_USER" ]; then
            echo "      <username>${PROXY_USER}</username>"
            echo "      <password>${PROXY_PASS}</password>"
          fi
          echo "      <nonProxyHosts>${NONPROXY}</nonProxyHosts>"
          echo '    </proxy>'
        done
        echo '  </proxies>'
        echo '</settings>'
      } > "$SETTINGS_XML"
      echo "maven-proxy: zapsán $SETTINGS_XML (proxy ${PROXY_HOST}:${PROXY_PORT})"
    fi
    ;;
esac

# Předstažení závislostí; když selže, stáhnou se při prvním buildu.
if [ -x "$ROOT/mvnw" ]; then
  (cd "$ROOT" && ./mvnw -B -q dependency:go-offline) || true
fi
exit 0
