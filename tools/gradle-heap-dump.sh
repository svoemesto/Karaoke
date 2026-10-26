#!/usr/bin/env bash
# =====================================================================
# tools/gradle-heap-dump.sh — Gradle с дампом кучи в ПРИВАТНОМ каталоге
# =====================================================================
# Зачем (BR-529, аудит #336, OpenProject #406, пакет PKG-S3-34):
#   Раньше в `gradle.properties` стояло
#     org.gradle.jvmargs=... -XX:+HeapDumpOnOutOfMemoryError \
#                           -XX:HeapDumpPath=/tmp/gradle-heap.hprof
#   JVM при OOM писал ПОЛНЫЙ дамп кучи в общий каталог `/tmp` (режим 1777).
#   Права файла дампа = 0666 & ~umask → при обычном umask 022 получался 0644,
#   то есть дамп читался ЛЮБЫМ локальным пользователем. В дампе лежит карта
#   `System.getenv()` daemon'а — то есть все секреты окружения, из которого
#   запущена сборка (пароли БД, токены, ключи). Файл JVM не удаляет, и он
#   переживает последующие сборки.
#
# Что делает этот скрипт:
#   1. `umask 077` — все файлы, которые создаст процесс (в т.ч. дамп кучи),
#      получают права 0600 вместо 0644.
#   2. Каталог дампов `$GRADLE_USER_HOME/heap-dumps` — права 0700 (создаётся
#      скриптом, `chmod 700` дублируется для надёжности).
#   3. Запускает `./gradlew` с `-Dorg.gradle.jvmargs=... -XX:+HeapDumpOnOutOfMemoryError
#      -XX:HeapDumpPath=$GRADLE_USER_HOME/heap-dumps/gradle-heap.hprof`.
#
# Механика прав на файл дампа: JVM создаёт файл через open(..., 0666) —
# итоговые права = 0666 & ~umask. При umask 077 это 0600.
#
# Обычная сборка дампов не включает (см. `gradle.properties`): диагностика OOM
# ведётся по логам сборки. Этот скрипт — явный opt-in.
#
# Использование:
#   bash tools/gradle-heap-dump.sh :karaoke-app:compileKotlin
#   bash tools/gradle-heap-dump.sh --print-dir     # показать каталог дампов
#   bash tools/gradle-heap-dump.sh --self-test     # проверить права (700/600)
#
# Exit codes:
#   0 — успех
#   1 — нарушение (права не те, что ожидаются) / ошибка запуска
#   2 — проверка не могла быть выполнена (нет gradlew, нет stat и т.п.)
#
# Источник истины: knowledge/guidelines/architecture-conventions.md
# § «R-46 — Конфигурация сборки».
# =====================================================================

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# AGENTS.md Tier-1 (R-372): GRADLE_USER_HOME — от корня репозитория.
if [[ -z "${GRADLE_USER_HOME:-}" ]]; then
    GRADLE_USER_HOME="${KARAOKE_GRADLE_USER_HOME:-$PROJECT_ROOT/.gradle}"
fi
export GRADLE_USER_HOME

DUMP_DIR="${GRADLE_USER_HOME}/heap-dumps"
DUMP_FILE="${DUMP_DIR}/gradle-heap.hprof"

color_red() { printf '\033[0;31m%s\033[0m\n' "$*"; }
color_green() { printf '\033[0;32m%s\033[0m\n' "$*"; }

# Каталог дампов: 0700. umask выставлен ДО создания, поэтому mkdir сам даёт 700;
# chmod — дублирующая страховка (если каталог уже существовал с другими правами).
prepare_dump_dir() {
    umask 077
    if ! mkdir -p "$DUMP_DIR" 2>/dev/null; then
        color_red "❌ не удалось создать каталог дампов: $DUMP_DIR"
        return 1
    fi
    if ! chmod 700 "$DUMP_DIR" 2>/dev/null; then
        color_red "❌ не удалось выставить права 700 на $DUMP_DIR"
        return 1
    fi
    return 0
}

file_mode() {
    stat -c '%a' "$1" 2>/dev/null
}

self_test() {
    local tmp dir_mode file_mode_actual rc=0

    if ! command -v stat >/dev/null 2>&1; then
        color_red "❌ self-test: нет утилиты stat — права проверить нечем"
        return 2
    fi

    tmp="$(mktemp -d)" || {
        color_red "❌ self-test: не удалось создать временный каталог"
        return 2
    }
    trap "rm -rf '$tmp'" EXIT

    GRADLE_USER_HOME="$tmp" || return 2
    DUMP_DIR="${GRADLE_USER_HOME}/heap-dumps"
    DUMP_FILE="${DUMP_DIR}/gradle-heap.hprof"

    prepare_dump_dir || return 2

    dir_mode="$(file_mode "$DUMP_DIR")"
    if [[ "$dir_mode" != "700" ]]; then
        color_red "✗ self-test: права каталога дампов = $dir_mode (ожидалось 700): $DUMP_DIR"
        rc=1
    else
        color_green "✓ self-test: права каталога дампов = 700"
    fi

    # Пробный файл создаётся ТЕМ ЖЕ способом, каким JVM создаёт дамп:
    # open(..., 0666) & ~umask. Проверяем именно права нового файла.
    ( umask 077 && : > "$DUMP_DIR/probe" ) || {
        color_red "✗ self-test: не удалось создать пробный файл"
        rc=1
    }
    file_mode_actual="$(file_mode "$DUMP_DIR/probe")"
    if [[ "$file_mode_actual" != "600" ]]; then
        color_red "✗ self-test: права файла дампа = $file_mode_actual (ожидалось 600)"
        rc=1
    else
        color_green "✓ self-test: права файла дампа = 600"
    fi

    if [[ "$rc" -eq 0 ]]; then
        color_green "✅ self-test: каталог 700, файл дампа 600 — OK"
    fi
    return "$rc"
}

print_dir() {
    printf '%s\n' "$DUMP_DIR"
}

case "${1:-}" in
    --self-test)
        self_test
        exit $?
        ;;
    --print-dir)
        print_dir
        exit 0
        ;;
    --help|-h)
        sed -n '2,55p' "$0" | sed 's/^# \{0,1\}//'
        exit 0
        ;;
esac

GRADLEW="$PROJECT_ROOT/gradlew"
if [[ ! -x "$GRADLEW" ]]; then
    color_red "❌ не найден исполняемый $GRADLEW — запуск невозможен"
    exit 2
fi

prepare_dump_dir || exit 1

BASE_JVMARGS="$(grep -E '^org\.gradle\.jvmargs=' "$PROJECT_ROOT/gradle.properties" 2>/dev/null | head -1 | cut -d= -f2-)"
if [[ -z "$BASE_JVMARGS" ]]; then
    BASE_JVMARGS="-Xmx4g -Dfile.encoding=UTF-8"
fi

color_green "Дамп кучи: $DUMP_FILE (каталог 0700, файл 0600)"

cd "$PROJECT_ROOT" || exit 2
exec "$GRADLEW" \
    "-Dorg.gradle.jvmargs=${BASE_JVMARGS} -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${DUMP_FILE}" \
    "$@"
