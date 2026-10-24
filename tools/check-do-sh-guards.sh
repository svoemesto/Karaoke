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
# BR-571: опыт A больше НЕ переписывает отслеживаемый
# `deploy/karaoke-public/Dockerfile`. Раньше скрипт подменял его на
# «FROM scratch» и восстанавливал в конце, но `trap` на восстановление не был
# установлен → при Ctrl-C / SIGTERM / падении машины в репозитории оставался
# сломанный Dockerfile и неотслеживаемый .bak. Теперь эксперимент идёт на
# КОПИИ deploy-дерева во временном каталоге: отслеживаемый файл не пишется
# вообще, а прерывание не может оставить мусор в репозитории.
#
# Использование:
#   bash tools/check-do-sh-guards.sh             # только опыт A (локально)
#   bash tools/check-do-sh-guards.sh --prod      # A + B (B — на проде, read-only по сути)
#
# Код возврата: 0 — оба выполненных опыта прошли, 1 — провал.

set -uo pipefail

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

# BR-571: эксперимент выполняется на КОПИИ deploy-дерева во временном
# каталоге. Отслеживаемый Dockerfile не переписывается → прерывание
# (Ctrl-C/SIGTERM) не может оставить в репозитории сломанный файл.
TMP_ROOT="$(mktemp -d)"
BUILD_LOG="$(mktemp)"
cleanup() { rm -rf "$TMP_ROOT"; rm -f "$BUILD_LOG"; }
trap cleanup EXIT INT TERM

cp -a "$DEPLOY" "$TMP_ROOT/deploy"
# do.env (там секреты) не копируем — ссылаемся на исходный файл, если он есть.
rm -f "$TMP_ROOT/deploy/do.env"
if [ -f "$DEPLOY/do.env" ]; then
  ln -s "$DEPLOY/do.env" "$TMP_ROOT/deploy/do.env"
else
  : > "$TMP_ROOT/deploy/do.env"
fi
# do.sh читает версию из gradlew в корне проекта (deploy/do.sh:48); в копии его
# нет — подставляем заглушку. Формат обязан совпадать с build.gradle.kts:81
# (`println("KaraokeVersion: ${project.version}")`): do.sh берёт
# `awk '/':'/{print $2}'`, поэтому лишние пробелы дали бы APP_VERSION=":" и
# невалидный тег образа — опыт падал бы не на Dockerfile.
printf '#!/usr/bin/env bash\necho "KaraokeVersion: 0.0.0-guardtest"\n' > "$TMP_ROOT/gradlew"
chmod +x "$TMP_ROOT/gradlew"

# Заведомо падающий Dockerfile — в КОПИИ: в scratch нет /bin/sh, сборка рушится
# сразу и без сети.
BROKEN_DOCKERFILE="$TMP_ROOT/deploy/karaoke-public/Dockerfile"
printf 'FROM scratch\nRUN exit 1\n' > "$BROKEN_DOCKERFILE"

before_sum="$(sha256sum "$DOCKERFILE" | cut -d' ' -f1)"
# Тег образа обязан быть валидным: иначе docker падает на проверке тега, а не на
# Dockerfile — и опыт ничего не доказывает. Приоритет у do.env (реальный
# registry), иначе — заведомо несуществующий хост (push не выполняется).
( cd "$TMP_ROOT/deploy" \
    && DOCKER_REGISTRY="${DOCKER_REGISTRY:-guardtest.invalid/karaoke}" \
       bash do.sh build_public ) >"$BUILD_LOG" 2>&1
rc=$?
after_sum="$(sha256sum "$DOCKERFILE" | cut -d' ' -f1)"

[ "$before_sum" = "$after_sum" ] \
  && ok "отслеживаемый Dockerfile не изменён (sha256 ${before_sum:0:16}…)" \
  || bad "отслеживаемый Dockerfile ИЗМЕНЁН — эксперимент не должен его трогать"
if [ -e "$DOCKERFILE.guardtest.bak" ]; then
  bad "в репозитории остался мусорный $DOCKERFILE.guardtest.bak"
else
  ok "мусорных файлов (.guardtest.bak) в репозитории нет"
fi
if [ "$rc" -ne 0 ]; then
  ok "build_public на падающей сборке вернул код $rc (было 0 — «молча зелёный»)"
else
  bad "build_public вернул 0 на падающей сборке — дефект жив"
fi
if grep -q 'invalid reference format' "$BUILD_LOG"; then
  bad "эксперимент некорректен: невалидный тег образа — падение вызвано не Dockerfile"
fi
if ! grep -qiE 'RUN exit 1|did not complete successfully|no such file or directory|failed to build' "$BUILD_LOG"; then
  bad "в логе сборки нет признаков падения на «зломанном» Dockerfile — опыт не доказан"
  sed 's/^/    /' "$BUILD_LOG" | tail -10
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
