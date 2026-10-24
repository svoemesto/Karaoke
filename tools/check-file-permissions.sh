#!/usr/bin/env bash
# =====================================================================
# tools/check-file-permissions.sh — guard «нет chmod с записью для всех»
# =====================================================================
# Зачем: `chmod 666`/`chmod 777` (и `o+w`/`a+w`) дают право ЗАПИСИ любому
# локальному пользователю и любому контейнеру, куда смонтирован каталог.
# Для опубликованных картинок это подмена артефакта, для файла настроек
# `Karaoke.properties` — кража/подмена токенов VK/Telegram/Whisper.
#
# Прецеденты (аудит #336, OpenProject #407, пакет PKG-SEC-CHMOD):
#   BR-115 karaoke-app/.../UtilsPictures.kt — `chmod 777` на каталог публикации
#          и `chmod 666` на 12 файлов картинок/превью;
#   BR-247 karaoke-app/.../KaraokeProperties.kt — `chmod 666` на файл с секретами
#          (и umask-овые `644` на `.bak`).
#
# Ожидаемые права (см. knowledge/guidelines/architecture-conventions.md
# «Права на файлы, создаваемые приложением»):
#   644 — опубликованный файл, 755 — каталог публикации, 600 — секреты.
#   Права ставятся в JVM (`setPublishedFilePermissions` и т.п.), без
#   внешнего процесса `chmod`.
#
# Что проверяется: строки кода (не комментарии) в
#   karaoke-app/src/main/**, karaoke-web/src/main/**, deploy/**, tools/**
# со следующими формами:
#   1. Kotlin: `runCommand(listOf("chmod", "NNN", ...))` — NNN с битом others-write;
#   2. shell:  `chmod [-R] NNN` — NNN с битом others-write;
#   3. символическая запись `o+w` / `a+w` / `chmod +w`.
#
# Baseline — по СОДЕРЖИМОМУ строки, не по номеру (конвенция Pass 455):
#   ключ = <путь>:sha256(trim(строки))[:16]. Вставка строк выше базу не ломает,
#   изменение текста нарушителя — ломает (намеренно).
#
# Baseline содержит ТОЛЬКО известные legacy-места, которые принадлежат другим
# пакетам аудита #336 (BR-271/OpenProject #397 и записи, ещё не разложенные по
# пакетам). Пакет PKG-SEC-CHMOD свои места убрал; новые нарушения не допускаются.
#
# Использование:
#   bash tools/check-file-permissions.sh                  # guard-режим
#   bash tools/check-file-permissions.sh --quiet          # для pre-commit
#   bash tools/check-file-permissions.sh --update-baseline
#   bash tools/check-file-permissions.sh --self-test      # проверка самих правил
#   bash tools/check-file-permissions.sh --root DIR       # скан копии дерева (для теста «до/после»)
#
# Exit codes:
#   0 — нарушений нет (после baseline)
#   1 — найдены нарушения
#   2 — usage error / self-test failed
# =====================================================================

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="${PROJECT_ROOT}"
BASELINE_FILE=""
HASH_LEN=16
QUIET=0
UPDATE_BASELINE=0
SELF_TEST=0

usage() {
    sed -n '2,60p' "$0" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --quiet) QUIET=1 ;;
        --update-baseline) UPDATE_BASELINE=1 ;;
        --self-test) SELF_TEST=1 ;;
        --root)
            ROOT="${2:-}"
            if [[ -z "$ROOT" ]]; then
                usage >&2
                exit 2
            fi
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "unknown argument: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
    shift
done

ROOT="$(cd "$ROOT" && pwd)" || {
    echo "нет такого каталога: $ROOT" >&2
    exit 2
}
BASELINE_FILE="$ROOT/tools/check-file-permissions.baseline"

color_red() { [[ "$QUIET" -eq 1 ]] || printf '\033[0;31m%s\033[0m\n' "$*"; }
color_green() { [[ "$QUIET" -eq 1 ]] || printf '\033[0;32m%s\033[0m\n' "$*"; }
color_yellow() { [[ "$QUIET" -eq 1 ]] || printf '\033[0;33m%s\033[0m\n' "$*"; }

trim() {
    local s="$1"
    s="${s#"${s%%[![:space:]]*}"}"
    s="${s%"${s##*[![:space:]]}"}"
    printf '%s' "$s"
}

baseline_key() {
    local file="$1" content="$2" hash
    hash="$(trim "$content" | sha256sum | cut -c1-"$HASH_LEN")"
    printf '%s:%s' "$file" "$hash"
}

declare -A BASELINE_SET=()
BASELINE_COUNT=0

load_baseline() {
    BASELINE_SET=()
    BASELINE_COUNT=0
    [[ -f "$BASELINE_FILE" ]] || return 0
    local raw key
    while IFS= read -r raw; do
        key="$(trim "${raw%%#*}")"
        [[ -z "$key" ]] && continue
        BASELINE_SET["$key"]=1
        BASELINE_COUNT=$((BASELINE_COUNT + 1))
    done < "$BASELINE_FILE"
}

# Список отслеживаемых файлов. Скан ограничен production-кодом и скриптами:
# тесты намеренно могут содержать «плохие» примеры для собственных guard'ов.
FILES=()

collect_files() {
    FILES=()
    local dir f rel
    for dir in karaoke-app/src/main karaoke-web/src/main deploy tools; do
        [[ -d "$ROOT/$dir" ]] || continue
        while IFS= read -r f; do
            [[ -z "$f" ]] && continue
            rel="${f#"$ROOT"/}"
            case "$rel" in
                tools/check-file-permissions.sh | tools/check-file-permissions.baseline) continue ;;
            esac
            FILES+=("$rel")
        done < <(find "$ROOT/$dir" -type f \( -name '*.kt' -o -name '*.sh' \) | LC_ALL=C sort)
    done
}

# Совпадения пишутся в RAW_HITS: <file>:<line>:<rule>:<content>.
RAW_HITS=()

check_line() {
    local file="$1" lineno="$2" content="$3" mode="" rule=""

    # Комментарии не исполняются — в том числе KDoc с примерами «как было».
    local trimmed="${content#"${content%%[![:space:]]*}"}"
    case "$trimmed" in
        \#* | //* | \** | /\**) return 0 ;;
    esac

    # 1. Kotlin: runCommand(listOf("chmod", "666", …))
    if [[ "$content" =~ \"chmod\"[[:space:]]*,[[:space:]]*\"([0-7]{3,4})\" ]]; then
        mode="${BASH_REMATCH[1]}"
        rule="kotlin-chmod"
    # 2. shell: chmod [-R] 777 …
    elif [[ "$content" =~ (^|[^[:alnum:]_])chmod[[:space:]]+(-[^[:space:]]+[[:space:]]+)*([0-7]{3,4})([[:space:]]|$) ]]; then
        mode="${BASH_REMATCH[3]}"
        rule="shell-chmod"
    fi

    if [[ -n "$mode" ]]; then
        # Бит 0002 — запись для остальных (others-write).
        if (( (8#$mode & 8#2) != 0 )); then
            RAW_HITS+=("$file:$lineno:$rule:$content")
        fi
        return 0
    fi

    # 3. Символическая запись: chmod o+w / a+w / +w.
    if [[ "$content" =~ chmod[^\"\']*(o\+w|a\+w) ]] ||
        [[ "$content" =~ chmod[[:space:]]+\+w([[:space:]]|$) ]]; then
        RAW_HITS+=("$file:$lineno:symbolic-chmod:$content")
    fi
}

collect_hits() {
    RAW_HITS=()
    local file lineno content
    for file in "${FILES[@]}"; do
        [[ -f "$ROOT/$file" ]] || continue
        # Предфильтр по подстроке: bash-regex на каждой строке 12 МБ исходников
        # считается минутами, а совпадений «chmod» — единицы на файл.
        while IFS=: read -r lineno content; do
            [[ -z "$lineno" ]] && continue
            check_line "$file" "$lineno" "$content"
        done < <(grep -nE 'chmod' "$ROOT/$file" || true)
    done
}

update_baseline() {
    collect_files
    collect_hits

    local -A seen=()
    local out=() hit file rest lineno rule content key snippet
    for hit in "${RAW_HITS[@]:-}"; do
        [[ -z "$hit" ]] && continue
        file="${hit%%:*}"
        rest="${hit#*:}"
        lineno="${rest%%:*}"
        rest="${rest#*:}"
        rule="${rest%%:*}"
        content="${rest#*:}"
        key="$(baseline_key "$file" "$content")"
        [[ -n "${seen["$key"]:-}" ]] && continue
        seen["$key"]=1
        snippet="$(trim "$content")"
        [[ ${#snippet} -gt 120 ]] && snippet="${snippet:0:117}..."
        out+=("$key  # [$rule] $snippet")
    done

    {
        echo "# Baseline для tools/check-file-permissions.sh (нет chmod с записью для всех)."
        echo "#"
        echo "# Формат: <путь>:<sha256(trim(строки))[:$HASH_LEN]>  # [<rule>] <сниппет>"
        echo "# Всё после '#' — комментарий-сниппет, на сопоставление не влияет."
        echo "#"
        echo "# Номер строки НЕ используется (конвенция Pass 455): вставка строк выше"
        echo "# базу не ломает, изменение текста нарушителя — ломает (намеренно)."
        echo "#"
        echo "# Здесь только известные legacy-места ДРУГИХ пакетов аудита #336"
        echo "# (BR-271 / OpenProject #397 и записи вне пакетов). Пакет PKG-SEC-CHMOD"
        echo "# (#407) свои места убрал; новые нарушения не допускаются."
        echo "#"
        echo "# Перегенерировать: bash tools/check-file-permissions.sh --update-baseline"
        echo ""
        printf '%s\n' "${out[@]:-}" | LC_ALL=C sort
    } > "$BASELINE_FILE"

    color_green "Baseline перегенерирован: ${#seen[@]} записей -> $BASELINE_FILE"
}

report() {
    local hit file rest lineno rule content key snippet violations=0 baselined=0 mode
    for hit in "${RAW_HITS[@]:-}"; do
        [[ -z "$hit" ]] && continue
        file="${hit%%:*}"
        rest="${hit#*:}"
        lineno="${rest%%:*}"
        rest="${rest#*:}"
        rule="${rest%%:*}"
        content="${rest#*:}"
        key="$(baseline_key "$file" "$content")"
        if [[ -n "${BASELINE_SET["$key"]:-}" ]]; then
            baselined=$((baselined + 1))
            continue
        fi
        violations=$((violations + 1))
        snippet="$(trim "$content")"
        [[ ${#snippet} -gt 140 ]] && snippet="${snippet:0:137}..."
        color_red "  ❌ $file:$lineno [$rule]"
        printf '     %s\n' "$snippet"
    done

    if [[ "$violations" -gt 0 ]]; then
        color_red "Найдено chmod с правом записи для всех: $violations (покрыто baseline: $baselined)"
        printf '%s\n' \
            "  Нужно: 644 для опубликованного файла, 755 для каталога, 600 для секретов." \
            "  В Kotlin — setPublishedFilePermissions()/setPublishedDirectoryPermissions()/" \
            "  setSecretFilePermissions() (Extentions.kt), без внешнего chmod."
        return 1
    fi

    color_green "OK: chmod с правом записи для всех нет (покрыто baseline: $baselined совпадений)"
    return 0
}

self_test() {
    local tmp bad good shell_bad rc
    tmp="$(mktemp -d)"
    trap "rm -rf '$tmp'" EXIT

    mkdir -p "$tmp/karaoke-app/src/main" "$tmp/tools"
    printf 'fun bad(p: String) { runCommand(listOf("chmod", "777", p)) }\n' > "$tmp/karaoke-app/src/main/Bad.kt"
    printf 'val ok = "chmod 644"\n' > "$tmp/karaoke-app/src/main/Ok.kt"
    printf '#!/bin/sh\nchmod -R 777 /tmp/x\n' > "$tmp/tools/bad.sh"
    printf '# chmod 777 в комментарии не считается\n' > "$tmp/tools/comment.sh"

    ROOT="$tmp"
    BASELINE_FILE="$tmp/tools/check-file-permissions.baseline"
    collect_files
    collect_hits

    rc=0
    [[ ${#RAW_HITS[@]} -eq 2 ]] || {
        echo "self-test: ожидалось 2 нарушения, получено ${#RAW_HITS[@]}" >&2
        rc=1
    }
    load_baseline
    if report >/dev/null 2>&1; then
        echo "self-test: guard не упал на заведомо плохом коде" >&2
        rc=1
    fi

    # Чистое дерево: тот же скан без нарушителей должен проходить.
    rm -f "$tmp/karaoke-app/src/main/Bad.kt" "$tmp/tools/bad.sh"
    collect_hits
    if ! report >/dev/null 2>&1; then
        echo "self-test: guard падает на чистом дереве" >&2
        rc=1
    fi

    [[ "$rc" -eq 0 ]] && color_green "OK: self-test пройден (плохо -> exit 1, чисто -> exit 0)"
    return "$rc"
}

if [[ "$SELF_TEST" -eq 1 ]]; then
    self_test
    exit $?
fi

if [[ "$UPDATE_BASELINE" -eq 1 ]]; then
    update_baseline
    exit 0
fi

load_baseline
collect_files
if [[ ${#FILES[@]} -eq 0 ]]; then
    echo "не найдено файлов для сканирования под $ROOT" >&2
    exit 2
fi
collect_hits
report
