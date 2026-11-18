#!/usr/bin/env bash
# Deploys one version to the server (docs/deploy.md). Run by the deploy workflow on the self-hosted runner,
# from a checkout of the commit being deployed, or by hand:
#
#   ./deploy.sh <commit-sha>
#
# Steps: copy the production compose file, set IMAGE_TAG and pull the images, dump the database, start the
# stack and wait until every service is healthy. If anything fails after the tag changed, the previous
# IMAGE_TAG is restored and started again, and the script exits non-zero so the workflow turns red.
#
# Environment:
#   PURCHASE_DIR       server directory with .env (default /opt/purchase)
#   WAIT_TIMEOUT       seconds to wait for healthy services (default 300)
#   DEPLOY_SKIP_PULL   any value: use local images (to test the script without the registry)
set -euo pipefail

sha=${1:?usage: deploy.sh <commit-sha>}
if ! [[ $sha =~ ^[0-9a-f]{7,40}$ ]]; then
  echo "invalid commit SHA: $sha" >&2
  exit 1
fi

PURCHASE_DIR=${PURCHASE_DIR:-/opt/purchase}
WAIT_TIMEOUT=${WAIT_TIMEOUT:-300}
DEPLOY_SKIP_PULL=${DEPLOY_SKIP_PULL:-}
repo_dir=$(cd "$(dirname "$0")" && pwd)
env_file="$PURCHASE_DIR/.env"
short=${sha:0:7}

# Reads a variable from the server .env without executing the file.
env_get() { grep -E "^$1=" "$env_file" | tail -n 1 | cut -d= -f2- || true; }

notify() {
  echo "$1"
  local token chat
  token=$(env_get TELEGRAM_BOT_TOKEN)
  chat=$(env_get TELEGRAM_CHAT_ID)
  [ -n "$token" ] && [ -n "$chat" ] || return 0
  curl -fsS --max-time 15 -o /dev/null "https://api.telegram.org/bot$token/sendMessage" \
    --data-urlencode "chat_id=$chat" --data-urlencode "text=$1" || echo "could not notify Telegram" >&2
}

set_tag() {
  if grep -qE '^IMAGE_TAG=' "$env_file"; then
    sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$1/" "$env_file"
  else
    echo "IMAGE_TAG=$1" >> "$env_file"
  fi
}

up() { docker compose up -d --remove-orphans --wait --wait-timeout "${1:-$WAIT_TIMEOUT}"; }

if [ ! -f "$env_file" ]; then
  echo "missing $env_file: create it from the production section of .env.example" >&2
  exit 1
fi
cd "$PURCHASE_DIR"
previous=$(env_get IMAGE_TAG)

rollback() {
  local reason=$1
  if [ -n "$previous" ] && [ "$previous" != "$sha" ]; then
    set_tag "$previous"
    # the previous version was healthy before: give it extra time (cold JVM, migrations already applied)
    if up $((WAIT_TIMEOUT * 2)); then
      notify "❌ Deploy de $short falhou ($reason). Voltou para ${previous:0:7}."
    else
      notify "🔥 Deploy de $short falhou ($reason) e a versão ${previous:0:7} também não subiu. Verifique o servidor."
    fi
  else
    notify "❌ Deploy de $short falhou ($reason). Não havia versão anterior para voltar."
  fi
  exit 1
}

# 1. Compose file of the new version and its images.
install -m 644 "$repo_dir/docker-compose.prod.yml" docker-compose.yml
mkdir -p backup && chmod 700 backup 2>/dev/null || true  # the chmod only matters on the Linux server
set_tag "$sha"
if [ -z "$DEPLOY_SKIP_PULL" ] && ! docker compose pull --quiet; then
  rollback "não foi possível baixar as imagens"
fi

# 2. Safety dump before Flyway migrates (the 5 most recent are kept). Skipped on the first deploy, when the
#    database is not running yet.
if [ -n "$(docker compose ps -q --status running postgres 2>/dev/null)" ]; then
  dump="backup/purchase-$(date -u +%Y%m%dT%H%M%SZ)-before-$short.sql.gz"
  if ! docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' | gzip > "$dump"; then
    rm -f "$dump"
    [ -n "$previous" ] && set_tag "$previous"
    notify "❌ Deploy de $short cancelado: o backup do banco falhou. Nenhum container foi alterado."
    exit 1
  fi
  ls -1t backup/purchase-*.sql.gz | tail -n +6 | xargs -r rm -f
fi

# 3. Start and wait until every service with a healthcheck is healthy.
if ! up; then
  docker compose ps
  docker compose logs --tail 50 backend frontend || true
  rollback "os serviços não ficaram saudáveis em ${WAIT_TIMEOUT}s"
fi

docker image prune -f > /dev/null
notify "✅ Deploy de $short concluído."
