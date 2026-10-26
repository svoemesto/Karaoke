#!/usr/bin/env bash
# =====================================================================
# tools/check-gradle-wrapper.sh — guard «единый gradle-wrapper с checksum»
# =====================================================================
# Зачем (аудит #336, OpenProject #406, пакет PKG-S3-34):
#   BR-499 (gradle-wrapper-outdated:build) — в репозитории было ТРИ разных
#     wrapper'а: корневой 8.14, karaoke-app 9.1.0, karaoke-web 9.1.0,
#     karaoke-db 7.5.1. Фактическая сборка приложения и CI-job'ы
#     backend-lint/backend-build/backend-test и deploy/do.sh идут через
#     КОРНЕВОЙ wrapper, а `./gradlew` из каталога модуля — через 9.1.0/7.5.1.
#     Разнобой означает, что «то, что проверяет CI» и «то, что запускает
#     разработчик в модуле» — разные Gradle.
#   BR-528 (gradle-wrapper-no-checksum:build) — ни в одном wrapper'е не было
#     `distributionSha256Sum`. Gradle скачивал и распаковывал дистрибутив
#     (~130 МБ) и запускал его в локальной JVM БЕЗ проверки целостности;
#     `validateDistributionUrl=true` проверяет только доступность URL.
#
# Проверяется:
#   1. wrapper-version-skew     — все wrapper'ы репозитория указывают на
#      ОДНУ версию дистрибутива;
#   2. wrapper-version-too-old  — версия не ниже MIN_VERSION (версия, на
#      которой зелёная CI-сборка);
#   3. wrapper-no-checksum      — в каждом wrapper'е есть `distributionSha256Sum`;
#   4. wrapper-checksum-mismatch — контрольная сумма совпадает с
#      ОФИЦИАЛЬНО ОПУБЛИКОВАННОЙ (services.gradle.org).
#
# Источник эталонных сумм: `https://services.gradle.org/distributions/
# gradle-<версия>-bin.zip.sha256` (тот же файл публикует
# downloads.gradle.org). ТаблицаKNOWN_CHECKSUMS продублирована здесь, чтобы
# проверка была офлайновой и детерминированной; `--online` перепроверяет
# таблицу по сети.
#
# Использование:
#   bash tools/check-gradle-wrapper.sh              # guard-режим
#   bash tools/check-gradle-wrapper.sh --quiet      # для pre-commit
#   bash tools/check-gradle-wrapper.sh --online     # сверить суммы с services.gradle.org
#   bash tools/check-gradle-wrapper.sh --self-test  # фикстуры «до/после»
#   bash tools/check-gradle-wrapper.sh --root DIR   # сканировать копию дерева
#
# Exit codes:
#   0 — нарушений нет
#   1 — найдены нарушения
#   2 — проверка не могла быть выполнена (нет wrapper'ов, версия не в таблице,
#        недоступен network в --online)
# =====================================================================

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$PROJECT_ROOT"
QUIET=0
SELF_TEST=0
ONLINE=0

# Версия, на которой зелёная CI-сборка (root wrapper, backend-lint/backend-build/
# backend-test и deploy/do.sh). Понижать версию нельзя.
MIN_VERSION="9.8.2"

# Официально опубликованные суммы дистрибутивов
# (https://services.gradle.org/distributions/gradle-<v>-bin.zip.sha256).
KNOWN_CHECKSUMS_8_14="61ad310d3c7d3e5da131b76bbf22b5a4c0786e9d892dae8c1658d4b484de3caa"
KNOWN_CHECKSUMS_9_8_2="21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607"
KNOWN_CHECKSUMS_9_1_0="a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806"
KNOWN_CHECKSUMS_7_5_1="f6b8596b10cce501591e92f229816aa4046424f3b24d771751b06779d58c8ec4"

color_red() { [[ "$QUIET" -eq 1 ]] || printf '\033[0;31m%s\033[0m\n' "$*"; }
color_green() { [[ "$QUIET" -eq 1 ]] || printf '\033[0;32m%s\033[0m\n' "$*"; }
color_yellow() { [[ "$QUIET" -eq 1 ]] || printf '\033[0;33m%s\033[0m\n' "$*"; }

usage() {
    sed -n '2,58p' "$0" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --quiet) QUIET=1 ;;
        --self-test) SELF_TEST=1 ;;
        --online) ONLINE=1 ;;
        --root)
            ROOT="${2:-}"
            [[ -n "$ROOT" ]] || { usage >&2; exit 2; }
            shift
            ;;
        -h|--help) usage; exit 0 ;;
        *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
    shift
done

ROOT="$(cd "$ROOT" 2>/dev/null && pwd)" || {
    echo "нет такого каталога: $ROOT" >&2
    exit 2
}

# --- эталонная сумма для версии -------------------------------------------
known_checksum() {
    case "$1" in
        8.14) printf '%s' "$KNOWN_CHECKSUMS_8_14" ;;
        9.8.2) printf '%s' "$KNOWN_CHECKSUMS_9_8_2" ;;
        9.1.0) printf '%s' "$KNOWN_CHECKSUMS_9_1_0" ;;
        7.5.1) printf '%s' "$KNOWN_CHECKSUMS_7_5_1" ;;
        *) return 1 ;;
    esac
}

online_checksum() {
    local version="$1" url out
    url="https://services.gradle.org/distributions/gradle-${version}-bin.zip.sha256"
    if ! command -v curl >/dev/null 2>&1; then
        return 2
    fi
    out="$(curl -fsSL --max-time 30 "$url" 2>/dev/null)" || return 2
    out="$(printf '%s' "$out" | tr -d '[:space:]')"
    [[ "$out" =~ ^[0-9a-fA-F]{64}$ ]] || return 2
    printf '%s' "$(printf '%s' "$out" | tr 'A-F' 'a-f')"
    return 0
}

# --- сбор wrapper'ов ------------------------------------------------------
WRAPPERS=()

# Файлы под контролем версий текущего репозитория — ровно то, что видит CI.
# Такой список не включает ЧУЖИЕ checkout'ы, лежащие внутри репозитория
# (например, `.worktrees/*` — отдельные git-worktree с собственными
# gradle-wrapper'ами: без этого фильтра guard ругался на версии, которых
# в master нет, и блокировал работу в главном checkout'е).
# Fallback для не-git каталогов (фикстуры self-test): find с исключениями.
collect_wrappers() {
    WRAPPERS=()
    local f
    if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
        while IFS= read -r f; do
            [[ -z "$f" ]] && continue
            [[ "$f" == */gradle/wrapper/gradle-wrapper.properties ]] || continue
            WRAPPERS+=("$f")
        done < <(git -C "$ROOT" ls-files 2>/dev/null | LC_ALL=C sort)
        return 0
    fi
    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        # Пропускаем файлы внутри вложенного git-репозитория (чужой checkout):
        # его gradle-wrapper'ы — не часть нашей сборки.
        local d skip=0
        d="$(dirname "$f")"
        while [[ "$d" != "$ROOT" && "$d" != "/" ]]; do
            if [[ -e "$d/.git" ]]; then
                skip=1
                break
            fi
            d="$(dirname "$d")"
        done
        [[ "$skip" -eq 1 ]] && continue
        WRAPPERS+=("${f#"$ROOT"/}")
    done < <(find "$ROOT" -type f -name 'gradle-wrapper.properties' \
        -path '*/gradle/wrapper/*' \
        -not -path '*/build/*' -not -path '*/.gradle/*' -not -path '*/node_modules/*' \
        -not -path '*/.worktrees/*' -not -path '*/.git/*' \
        | LC_ALL=C sort)
}

prop_value() {
    local file="$1" key="$2"
    sed -n "s/^${key}=//p" "$file" 2>/dev/null | head -1
}

# Версия из distributionUrl: .../gradle-<version>-bin.zip
dist_version() {
    local url="$1"
    printf '%s' "$url" | sed -n 's#.*/gradle-\([^/]*\)-bin\.zip$#\1#p'
}

version_ge() {
    # "$1" >= "$2" ? (GNU sort -V)
    [[ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | head -1)" == "$2" ]]
}

# --- основная проверка ----------------------------------------------------
violations=0
unverifiable=0

report_violation() {
    violations=$((violations + 1))
    color_red "  ❌ [$1] $2"
    [[ -n "${3:-}" ]] && printf '     %s\n' "$3"
    return 0
}

check_wrappers() {
    local file version sum expected ref_file ref_version
    local -a versions=()

    violations=0
    unverifiable=0

    if [[ "${#WRAPPERS[@]}" -eq 0 ]]; then
        color_red "❌ не найдено ни одного gradle-wrapper.properties под $ROOT"
        return 2
    fi

    # Опорный wrapper — корневой (его версию используют CI и deploy/do.sh).
    ref_file="gradle/wrapper/gradle-wrapper.properties"
    ref_version=""
    if [[ -f "$ROOT/$ref_file" ]]; then
        ref_version="$(dist_version "$(prop_value "$ROOT/$ref_file" distributionUrl)")"
    fi
    if [[ -z "$ref_version" ]]; then
        color_red "❌ не удалось определить версию корневого wrapper'а ($ref_file)"
        return 2
    fi

    color_green "Опорный wrapper (используется CI и deploy/do.sh): $ref_file → gradle $ref_version"

    for file in "${WRAPPERS[@]}"; do
        local url version_actual sum_actual
        url="$(prop_value "$ROOT/$file" distributionUrl)"
        version_actual="$(dist_version "$url")"
        sum_actual="$(prop_value "$ROOT/$file" distributionSha256Sum)"

        if [[ -z "$version_actual" ]]; then
            report_violation "wrapper-version-unparsable" \
                "$file: не удалось определить версию дистрибутива" "distributionUrl=$url"
            continue
        fi
        versions+=("$version_actual")

        # 1. разнобой версий
        if [[ "$version_actual" != "$ref_version" ]]; then
            report_violation "wrapper-version-skew" \
                "$file: версия gradle-$version_actual != опорная gradle-$ref_version" \
                "Один репозиторий — один Gradle: иначе CI проверяет не то, что запускает разработчик."
        fi

        # 2. слишком старая версия
        if ! version_ge "$version_actual" "$MIN_VERSION"; then
            report_violation "wrapper-version-too-old" \
                "$file: gradle-$version_actual ниже минимума gradle-$MIN_VERSION" \
                "MIN_VERSION=$MIN_VERSION — версия, на которой зелёная CI-сборка."
        fi

        # 3. нет контрольной суммы
        if [[ -z "$sum_actual" ]]; then
            report_violation "wrapper-no-checksum" \
                "$file: нет distributionSha256Sum" \
                "Без суммы Gradle распаковывает дистрибутив без проверки целостности."
            continue
        fi
        if ! [[ "$sum_actual" =~ ^[0-9a-fA-F]{64}$ ]]; then
            report_violation "wrapper-checksum-malformed" \
                "$file: distributionSha256Sum не 64 hex-символа" "значение: $sum_actual"
            continue
        fi

        # 4. сверка с официально опубликованной суммой
        expected="$(known_checksum "$version_actual" || true)"
        if [[ -z "$expected" ]]; then
            if [[ "$ONLINE" -eq 1 ]]; then
                expected="$(online_checksum "$version_actual" || true)"
                if [[ -z "$expected" ]]; then
                    color_yellow "  ⚠ $file: не удалось получить официальную сумму для gradle-$version_actual (network?)"
                    unverifiable=$((unverifiable + 1))
                    continue
                fi
            else
                color_yellow "  ⚠ $file: версия gradle-$version_actual не в таблицеKNOWN_CHECKSUMS — сумма не проверена"
                color_yellow "     (обновить таблицу в tools/check-gradle-wrapper.sh или запустить с --online)"
                unverifiable=$((unverifiable + 1))
                continue
            fi
        fi
        if [[ "$ONLINE" -eq 1 ]]; then
            local live
            live="$(online_checksum "$version_actual" || true)"
            if [[ -n "$live" && "$live" != "$expected" ]]; then
                report_violation "wrapper-checksum-table-stale" \
                    "$file: сумма в таблице ($expected) != опубликованная ($live)" \
                    "Обновить таблицу в tools/check-gradle-wrapper.sh"
                continue
            fi
        fi
        if [[ "${sum_actual,,}" != "${expected,,}" ]]; then
            report_violation "wrapper-checksum-mismatch" \
                "$file: distributionSha256Sum не совпадает с официальной суммой gradle-$version_actual" \
                "в файле:    $sum_actual
ожидалась: $expected"
        fi
    done

    if [[ "$violations" -gt 0 ]]; then
        color_red "Найдено нарушений конфигурации gradle-wrapper: $violations"
        return 1
    fi
    if [[ "$unverifiable" -gt 0 ]]; then
        color_red "Часть проверок выполнить не удалось: $unverifiable (проверка неполная)"
        return 2
    fi
    color_green "OK: все wrapper'ы — gradle $ref_version, у всех есть корректная distributionSha256Sum"
    return 0
}

# --- self-test (фикстуры «до/после») --------------------------------------
# Фикстуры повторяют layout репозитория: <root>/gradle/wrapper (опорный),
# <root>/karaoke-app/..., karaoke-web/...
make_wrapper() {
    local dir="$1" version="$2" sum="$3"
    mkdir -p "$dir/gradle/wrapper"
    {
        echo "distributionBase=GRADLE_USER_HOME"
        echo "distributionPath=wrapper/dists"
        echo "distributionUrl=https\\://services.gradle.org/distributions/gradle-${version}-bin.zip"
        [[ -n "$sum" ]] && echo "distributionSha256Sum=${sum}"
        echo "networkTimeout=10000"
        echo "validateDistributionUrl=true"
        echo "zipStoreBase=GRADLE_USER_HOME"
        echo "zipStorePath=wrapper/dists"
    } > "$dir/gradle/wrapper/gradle-wrapper.properties"
}

# Раскладка «хорошего» дерева: все три wrapper'а на одной версии с суммой.
# Путь wrapper'а — <project_dir>/gradle/wrapper/gradle-wrapper.properties
# (так устроены и корневой проект, и модули).
make_good_tree() {
    local root="$1" version="${2:-$MIN_VERSION}" sum="${3:-$KNOWN_CHECKSUMS_9_8_2}"
    make_wrapper "$root" "$version" "$sum"
    make_wrapper "$root/karaoke-app" "$version" "$sum"
    make_wrapper "$root/karaoke-web" "$version" "$sum"
}

self_test() {
    local tmp rc=0 sum8 rc_case
    sum8="$KNOWN_CHECKSUMS_9_8_2"

    tmp="$(mktemp -d)" || {
        echo "self-test: не удалось создать временный каталог" >&2
        return 2
    }
    trap "rm -rf '$tmp'" EXIT

    # 1. Корректное дерево → PASS (exit 0)
    make_good_tree "$tmp/good" "$MIN_VERSION" "$sum8"
    ROOT="$tmp/good"
    collect_wrappers
    if check_wrappers >/dev/null 2>&1; then
        color_green "✓ self-test: корректное дерево → PASS"
    else
        color_red "✗ self-test: корректное дерево должно проходить"
        rc=1
    fi

    # 2. Разнобой версий → FAIL
    make_good_tree "$tmp/skew" "$MIN_VERSION" "$sum8"
    make_wrapper "$tmp/skew/karaoke-web" "9.1.0" "$KNOWN_CHECKSUMS_9_1_0"
    ROOT="$tmp/skew"
    collect_wrappers
    if check_wrappers >/dev/null 2>&1; then
        color_red "✗ self-test: разнобой версий не пойман"
        rc=1
    else
        color_green "✓ self-test: разнобой версий → FAIL"
    fi

    # 3. Старая версия (ниже MIN_VERSION) → FAIL
    make_good_tree "$tmp/old" "7.5.1" "$KNOWN_CHECKSUMS_7_5_1"
    ROOT="$tmp/old"
    collect_wrappers
    if check_wrappers >/dev/null 2>&1; then
        color_red "✗ self-test: устаревшая версия не поймана"
        rc=1
    else
        color_green "✓ self-test: устаревшая версия → FAIL"
    fi

    # 4. Нет checksum → FAIL
    make_good_tree "$tmp/nosum" "$MIN_VERSION" "$sum8"
    make_wrapper "$tmp/nosum/karaoke-web" "$MIN_VERSION" ""
    ROOT="$tmp/nosum"
    collect_wrappers
    if check_wrappers >/dev/null 2>&1; then
        color_red "✗ self-test: отсутствие checksum не поймано"
        rc=1
    else
        color_green "✓ self-test: отсутствие checksum → FAIL"
    fi

    # 5. Неверная checksum → FAIL
    make_good_tree "$tmp/badsum" "$MIN_VERSION" "$sum8"
    make_wrapper "$tmp/badsum/karaoke-web" "$MIN_VERSION" \
        "0000000000000000000000000000000000000000000000000000000000000000"
    ROOT="$tmp/badsum"
    collect_wrappers
    if check_wrappers >/dev/null 2>&1; then
        color_red "✗ self-test: неверная checksum не поймана"
        rc=1
    else
        color_green "✓ self-test: неверная checksum → FAIL"
    fi

    # 6. Версия вне таблицы (офлайн) → код 2 «проверка не выполнена»
    make_good_tree "$tmp/unknown" "9.9.0" \
        "1111111111111111111111111111111111111111111111111111111111111111"
    ROOT="$tmp/unknown"
    collect_wrappers
    check_wrappers >/dev/null 2>&1
    rc_case=$?
    if [[ "$rc_case" -ne 2 ]]; then
        color_red "✗ self-test: неизвестная версия вне таблицы должна давать код 2, получено $rc_case"
        rc=1
    else
        color_green "✓ self-test: неизвестная версия вне таблицы → код 2 (проверка не выполнена)"
    fi

    # 8. Чужой вложенный checkout (например, .worktrees/*) — не наш репозиторий:
    #    его wrapper'ы не проверяются (иначе guard ругался бы на версии,
    #    которых в master нет, и блокировал работу в главном checkout'е).
    make_good_tree "$tmp/nested" "$MIN_VERSION" "$sum8"
    make_wrapper "$tmp/nested/.worktrees/other" "7.5.1" "$KNOWN_CHECKSUMS_7_5_1"
    ROOT="$tmp/nested"
    collect_wrappers
    if check_wrappers >/dev/null 2>&1; then
        color_green "✓ self-test: вложенный чужой checkout не учитывается → PASS"
    else
        color_red "✗ self-test: guard ругается на чужой вложенный checkout"
        rc=1
    fi

    # 9. Ни одного wrapper'а → код 2
    local empty
    empty="$(mktemp -d)" || { rc=1; empty=""; }
    if [[ -n "$empty" ]]; then
        ROOT="$empty"
    collect_wrappers
        check_wrappers >/dev/null 2>&1
        rc_case=$?
        rm -rf "$empty"
        if [[ "$rc_case" -ne 2 ]]; then
            color_red "✗ self-test: пустое дерево должно давать код 2, получено $rc_case"
            rc=1
        else
            color_green "✓ self-test: нет wrapper'ов → код 2 (проверка не выполнена)"
        fi
    fi

    if [[ "$rc" -eq 0 ]]; then
        color_green "✅ self-test: все правила работают (плохое → FAIL, хорошее → PASS)"
    fi
    return "$rc"
}

if [[ "$SELF_TEST" -eq 1 ]]; then
    self_test
    exit $?
fi

collect_wrappers
check_wrappers
exit $?
