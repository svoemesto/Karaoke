#!/usr/bin/env bash
# Pass 540 (issue #286), инцидент 2026-10-11.
#
# Доказательство, что деплой-путь больше не может быть «молча зелёным».
# Два опыта:
#   A) заведомо падающая сборка → функция сборки ОБЯЗАНА вернуть ненулевой код;
#   B) отсутствующий образ в pull_* на проде → контейнер ОБЯЗАН остаться живым.
#
# Опыт B ходит на прод по ssh и требует --prod (по умолчанию не выполняется).
# Опыт B не пересоздаёт контейнеры: он лишь проверяет, что при отсутствующем
# образе функция отказывается работать ДО остановки контейнера.
#
# Использование:
#   bash tools/check-do-sh-guards.sh             # только опыт A (локально)
#   bash tools/check-do-sh-guards.sh --prod      # A + B (B — на проде, read-only по сути)
#
# Код возврата: 0 — оба выполненных опыта прошли, 1 — провал.

set -u

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY="$REPO/deploy"

# BR-581: пути к .docker/.gradle — из корня репозитория (переносимо между
# машинами матрицы AGENTS.md), а не зашитый /home/nsa/Karaoke.
MAIN_ROOT="$REPO"
_common_dir="$(git -C "$REPO" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)"
if [[ -n "$_common_dir" && -d "$_common_dir" ]]; then
  MAIN_ROOT="$(dirname "$_common_dir")"
fi
unset _common_dir
export DOCKER_CONFIG="${DOCKER_CONFIG:-${KARAOKE_DOCKER_CONFIG:-$MAIN_ROOT/.docker}}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-${KARAOKE_GRADLE_USER_HOME:-$MAIN_ROOT/.gradle}}"
unset MAIN_ROOT

DOCKERFILE="$DEPLOY/karaoke-public/Dockerfile"
PROD_HOST="${PROD_HOST:-188.127.240.124}"
WITH_PROD=0
[ "${1:-}" = "--prod" ] && WITH_PROD=1

pass=0; fail=0
ok()   { echo "  PASS  $*"; pass=$((pass + 1)); }
bad()  { echo "  FAIL  $*"; fail=$((fail + 1)); }

echo "=== Опыт A: падающая сборка обязана дать ненулевой код ==="
before_sum="$(sha256sum "$DOCKERFILE" | cut -d' ' -f1)"
cp "$DOCKERFILE" "$DOCKERFILE.guardtest.bak"
# Заведомо падающий Dockerfile: в scratch нет /bin/sh, сборка рушится сразу и без сети.
printf 'FROM scratch\nRUN exit 1\n' > "$DOCKERFILE"
( cd "$DEPLOY" && bash do.sh build_public >/tmp/guardtest-build.log 2>&1 )
rc=$?
cp "$DOCKERFILE.guardtest.bak" "$DOCKERFILE"; rm -f "$DOCKERFILE.guardtest.bak"
after_sum="$(sha256sum "$DOCKERFILE" | cut -d' ' -f1)"
[ "$before_sum" = "$after_sum" ] && ok "Dockerfile восстановлен (sha256 ${before_sum:0:16}…)" \
                               || bad "Dockerfile НЕ восстановлен!"
if [ "$rc" -ne 0 ]; then
  ok "build_public на падающей сборке вернул код $rc (было 0 — «молча зелёный»)"
else
  bad "build_public вернул 0 на падающей сборке — дефект жив"
fi

if [ "$WITH_PROD" = "1" ]; then
  echo "=== Опыт B: отсутствующий образ в pull_* не должен сносить контейнер ==="
  # Копия прод-скрипта рядом с оригиналом (иначе DEPLOY_DIR посчитается как /tmp
  # и source do.env не найдётся). Меняем только имя тега — логика та же.
  ssh -o BatchMode=yes root@"$PROD_HOST" \
    "sed 's|karaoke-public:1|karaoke-public:NO_SUCH_TAG_GUARDTEST|g' /root/Karaoke/deploy/do.sh > /root/Karaoke/deploy/do.sh.guardtest"
  ids_before="$(ssh -o BatchMode=yes root@"$PROD_HOST" \
    "docker inspect --format '{{.Id}} {{.State.StartedAt}}' karaoke-public 2>/dev/null")"
  ssh -o BatchMode=yes root@"$PROD_HOST" \
    "cd /root/Karaoke/deploy && bash do.sh.guardtest pull_public" >/tmp/guardtest-pull.log 2>&1
  rc=$?
  ids_after="$(ssh -o BatchMode=yes root@"$PROD_HOST" \
    "docker inspect --format '{{.Id}} {{.State.StartedAt}}' karaoke-public 2>/dev/null")"
  running="$(ssh -o BatchMode=yes root@"$PROD_HOST" \
    "docker ps --filter name=^karaoke-public\$ --format '{{.Status}}'")"
  ssh -o BatchMode=yes root@"$PROD_HOST" "rm -f /root/Karaoke/deploy/do.sh.guardtest"

  [ "$rc" -ne 0 ] && ok "pull_public с отсутствующим образом вернул код $rc" \
                  || bad "pull_public вернул 0 при отсутствующем образе"
  [ -n "$ids_before" ] && [ "$ids_before" = "$ids_after" ] \
    && ok "контейнер karaoke-public не тронут (тот же ID и время старта)" \
    || bad "контейнер пересоздан/удалён: было [$ids_before], стало [$ids_after]"
  case "$running" in Up*) ok "karaoke-public по-прежнему Up ($running)";; *) bad "контейнер не Up: [$running]";; esac
  echo "  --- вывод pull_public ---"; sed 's/^/    /' /tmp/guardtest-pull.log | tail -5
fi

echo
echo "--- итог: PASS=$pass FAIL=$fail ---"
[ "$fail" -eq 0 ] || exit 1
