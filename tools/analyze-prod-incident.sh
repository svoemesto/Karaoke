#!/bin/bash
# Analyze prod incidents за последние N часов.
# Использование: ./tools/analyze-prod-incident.sh [hours]
#
# Источники (4 источника логов прода, синхронизированные по TZ MSK после 288-prod-diagnostics-logging):
# 1. ProdContainerCheck (admin-машина, docker logs karaoke-app) — infra.prod.*
# 2. PostgreSQL pg_log (прод, docker logs karaoke-db) — duration:, still waiting, ERROR
# 3. karaoke-web (прод, docker logs karaoke-web) — Spring Boot ERROR
# 4. nginx (прод, /var/log/nginx/access.log) — HTTP коды
#
# Требования:
# - SSH-доступ к прод-серверу 188.119.64.111 (ssh root@... без пароля)
# - Локальный docker доступ к контейнеру karaoke-db (для прямого psql)
# - Пароль DB_SERVER_POSTGRES_PASSWORD в .env (или hardcode в скрипте)
#
# Per docs/ops/log-correlation.md (FR-019).

set -e

HOURS=${1:-24}
SINCE="${HOURS}h"

# Цвета
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Извлечение пароля БД из deploy/.env (НЕ коммитится в git)
DB_PASS=$(grep '^DB_SERVER_POSTGRES_PASSWORD=' /home/nsa/Karaoke/deploy/.env 2>/dev/null | cut -d'=' -f2)
if [ -z "$DB_PASS" ]; then
    echo -e "${RED}ERROR: DB_SERVER_POSTGRES_PASSWORD не найден в /home/nsa/Karaoke/deploy/.env${NC}"
    exit 1
fi

PROD_HOST="188.119.64.111"
PROD_PORT="5433"
PROD_USER="SvoeMestoKaraokeUser905"
PROD_DB="karaoke"

PSQL_REMOTE="docker exec karaoke-db psql \"host=${PROD_HOST} port=${PROD_PORT} user=${PROD_USER} password=${DB_PASS} dbname=${PROD_DB} sslmode=disable\" -t -A"

echo "═══════════════════════════════════════════════════════════════"
echo "  PROD INCIDENT ANALYSIS — последние ${HOURS}ч (с ${SINCE})"
echo "  TZ: $(date +%Z) ($(date +%z))  Сгенерировано: $(date)"
echo "═══════════════════════════════════════════════════════════════"
echo ""

echo "┌─ 1. ProdContainerCheck (admin-машина, infra.prod.*)"
echo "│"
DOCKER_PROD_LOGS=$(docker logs karaoke-app --since "$SINCE" 2>&1 | grep -E "infra\.prod\.(ping|db)" | tail -20 || true)
if [ -z "$DOCKER_PROD_LOGS" ]; then
    echo -e "│  ${GREEN}✓ Нет WARN/INFO — пинги прода проходят OK${NC}"
else
    echo "$DOCKER_PROD_LOGS" | sed 's/^/│  /'
fi
echo "└─"
echo ""

echo "┌─ 2. Медленные SQL на проде (pg_log, top-20 по duration)"
echo "│"
SLOW_SQL=$(ssh root@${PROD_HOST} "docker logs karaoke-db --since '$SINCE' 2>&1 | grep 'duration:' | sed 's/.*duration: //' | awk '{print \$1, \$0}' | sort -rn | head -20")
SLOW_COUNT=$(echo "$SLOW_SQL" | grep -c '.' || echo 0)
if [ "$SLOW_COUNT" -gt 0 ]; then
    echo -e "│  ${YELLOW}⚠ Найдено $SLOW_COUNT медленных SQL:${NC}"
    echo "$SLOW_SQL" | sed 's/^/│    /'
else
    echo -e "│  ${GREEN}✓ Медленных SQL нет${NC}"
fi
echo "└─"
echo ""

echo "┌─ 3. Lock waits на проде (pg_log)"
echo "│"
LOCK_COUNT=$(ssh root@${PROD_HOST} "docker logs karaoke-db --since '$SINCE' 2>&1 | grep -c 'still waiting'" 2>/dev/null | tr -d '[:space:]' || echo "0")
LOCK_COUNT=${LOCK_COUNT:-0}
if [ "$LOCK_COUNT" -gt 0 ] 2>/dev/null; then
    echo -e "│  ${YELLOW}⚠ Найдено $LOCK_COUNT записей lock waits${NC}"
    ssh root@${PROD_HOST} "docker logs karaoke-db --since '$SINCE' 2>&1 | grep 'still waiting'" | sed 's/^/│    /' | head -5
else
    echo -e "│  ${GREEN}✓ Lock waits нет${NC}"
fi
echo "└─"
echo ""

echo "┌─ 4. PostgreSQL ERROR/FATAL на проде"
echo "│"
ERR_COUNT=$(ssh root@${PROD_HOST} "docker logs karaoke-db --since '$SINCE' 2>&1 | grep -cE 'ERROR:|FATAL:'" 2>/dev/null | tr -d '[:space:]' || echo "0")
ERR_COUNT=${ERR_COUNT:-0}
if [ "$ERR_COUNT" -gt 0 ] 2>/dev/null; then
    echo -e "│  ${RED}⚠ Найдено $ERR_COUNT ошибок${NC}"
    ssh root@${PROD_HOST} "docker logs karaoke-db --since '$SINCE' 2>&1 | grep -E 'ERROR:|FATAL:'" | sed 's/^/│    /' | head -5
else
    echo -e "│  ${GREEN}✓ PostgreSQL ошибок нет${NC}"
fi
echo "└─"
echo ""

echo "┌─ 5. Spring Boot ERROR в karaoke-web"
echo "│"
WEB_ERR=$(ssh root@${PROD_HOST} "docker logs karaoke-web --since '$SINCE' 2>&1 | grep -c ' ERROR '" 2>/dev/null | tr -d '[:space:]' || echo "0")
WEB_ERR=${WEB_ERR:-0}
if [ "$WEB_ERR" -gt 0 ] 2>/dev/null; then
    echo -e "│  ${YELLOW}⚠ Найдено $WEB_ERR Spring Boot ошибок${NC}"
    ssh root@${PROD_HOST} "docker logs karaoke-web --since '$SINCE' 2>&1 | grep ' ERROR '" | sed 's/^/│    /' | head -5
else
    echo -e "│  ${GREEN}✓ Spring Boot ошибок нет${NC}"
fi
echo "└─"
echo ""

echo "┌─ 6. nginx: топ-10 endpoint'ов (combined format, \$7=URL \$9=status)"
echo "│"
TOP_URLS=$(ssh root@${PROD_HOST} "awk '\$9 ~ /^[0-9]+$/ {print \$7}' /var/log/nginx/access.log | sort | uniq -c | sort -rn | head -10" 2>/dev/null)
if [ -n "$TOP_URLS" ]; then
    echo "$TOP_URLS" | sed 's/^/│    /'
else
    echo -e "│  ${YELLOW}(access.log пустой или недоступен)${NC}"
fi
echo "└─"
echo ""

echo "┌─ 7. nginx: 5xx ответы за период"
echo "│"
SINCE_HOUR=$(date -d "${HOURS} hours ago" "+%d/%b/%Y:%H" 2>/dev/null || date -v-${HOURS}H "+%d/%b/%Y:%H" 2>/dev/null)
if [ -n "$SINCE_HOUR" ]; then
    NGINX_5XX=$(ssh root@${PROD_HOST} "grep '${SINCE_HOUR}' /var/log/nginx/access.log 2>/dev/null | awk '\$9 ~ /^50/ {print \$7, \$9}' | sort | uniq -c | sort -rn | head -10")
    if [ -n "$NGINX_5XX" ]; then
        echo "$NGINX_5XX" | sed 's/^/│    /'
    else
        echo -e "│  ${GREEN}✓ Нет 5xx ответов за период${NC}"
    fi
else
    echo -e "│  ${YELLOW}(не удалось вычислить SINCE_HOUR)${NC}"
fi
echo "└─"
echo ""

echo "┌─ 8. Текущее состояние БД (pg_stat_activity)"
echo "│"
PG_STATE=$(eval "$PSQL_REMOTE -c \"SELECT count(*) FILTER (WHERE state='active') AS active, count(*) FILTER (WHERE state='idle') AS idle, count(*) FILTER (WHERE state='idle in transaction') AS idle_in_tx, (SELECT setting FROM pg_settings WHERE name='max_connections') AS max_conn FROM pg_stat_activity;\"" 2>/dev/null)
echo "$PG_STATE" | sed 's/^/│    /'
echo "└─"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo "  Дополнительные команды для глубокого анализа:"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  # Топ медленных SQL с текстом:"
echo "  ssh root@${PROD_HOST} \"docker logs karaoke-db --since '$SINCE' 2>&1 | grep 'duration:' | head -50\""
echo ""
echo "  # Активные сессии БД прямо сейчас:"
echo "  eval \"$PSQL_REMOTE -c 'SELECT pid, state, query_start, LEFT(query, 80) FROM pg_stat_activity WHERE state != \\\"idle\\\" ORDER BY query_start;'\""
echo ""
echo "  # Поиск конкретного URL в nginx за период:"
echo "  ssh root@${PROD_HOST} \"grep '\\$(date -d \"${HOURS} hours ago\" \"+%d/%b/%Y:%H\" 2>/dev/null)' /var/log/nginx/access.log | grep '/api/public/events' | head -50\""
echo ""
echo "  # Все запросы конкретного SQL (из топ-10 выше):"
echo "  ssh root@${PROD_HOST} \"docker logs karaoke-db --since '$SINCE' 2>&1 | grep 'select count(DISTINCT id)' | head -20\""