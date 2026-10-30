#!/usr/bin/env bash
# =====================================================================
# deploy/heap-dump-retention.sh — политика хранения heap-дампов karaoke-app
# =====================================================================
#
# BR-630 (спека 676 / KARAOKE-245, FR-011): том дампов не должен переполняться.
#
# Политика (решение владельца 2026-10-30):
#   * хранить последние KEEP_RECENT дампов;
#   * суммарный объём дампов — не больше MAX_TOTAL_BYTES;
#   * вытеснение по возрасту (старые — первыми);
#   * защита по возрасту: файл моложе MIN_AGE_MINUTES не удаляется (это может быть
#     ещё не дописанный дамп — JVM пишет его перед тем, как упасть).
#
# ВАЖНО: каталог дампов общий с SQL-дампами БД — BR-046 монтирует
# ./karaoke-db/dumps и в karaoke-db (там лежат дампы БД для restore_db). Поэтому
# политика работает ТОЛЬКО с файлами *.hprof; дампы БД (.sql) не трогаются.
#
# Использование:
#   bash deploy/heap-dump-retention.sh [--dir <каталог>]   # применить политику
#   bash deploy/heap-dump-retention.sh --self-test        # проверка на фикстуре
#
# Скрипт НЕ требует установки: systemd-юниты (deploy/heap-dump-retention.service
# и .timer) — необязательная автоматизация, их установка и включение — действие
# владельца. Ручной путь (`bash deploy/heap-dump-retention.sh`) работает всегда.
#
# Коды возврата:
#   0 — политика применена (или self-test пройден);
#   1 — ошибка: каталог недоступен, файл не удалился или лимит тома не достигнут
#       из-за защиты по возрасту (нужно вмешательство оператора).
# =====================================================================

set -uo pipefail

# Хранимые дампы (решение владельца).
KEEP_RECENT=2

# Суммарный литом тома дампов: 20 ГБ (решение владельца).
MAX_TOTAL_BYTES=$((20 * 1024 * 1024 * 1024))

# Защита по возрасту (решение владельца, 2026-10-30): 60 минут защищают ещё не
# дописанный дамп и удерживают последний час; короче — риск удалить недописанный
# файл.
MIN_AGE_MINUTES=60

# Только heap-дампы. SQL-дампы БД в этом же каталоге не трогаем.
DUMP_PATTERN='*.hprof'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_DIR="$SCRIPT_DIR/karaoke-db/dumps"

# Каталог развёртывания: compose монтирует ./karaoke-db/dumps в /dumps, поэтому
# путь внутри контейнера — /dumps, на хосте — каталог deploy/karaoke-db/dumps.

log() {
    printf '%s %s\n' "heap-dump-retention:" "$*"
}

# Удалить файл и учесть результат. Возвращает 0, если файл удалён или уже отсутствует.
remove_dump() {
    local file="$1"
    if [[ ! -e "$file" ]]; then
        return 0
    fi
    if rm -f -- "$file"; then
        return 0
    fi
    log "не удалось удалить $file"
    return 1
}

# Применить политику к каталогу. Печатает сводку: kept=… removed=… freedBytes=… totalBytes=…
apply_policy() {
    local dir="$1"
    local rc=0

    if [[ ! -d "$dir" ]]; then
        log "каталог $dir не найден — нечего убирать"
        return 0
    fi
    if [[ ! -r "$dir" ]]; then
        log "каталог $dir недоступен для чтения"
        return 1
    fi

    # Сбор дампов: новые → старые (mtime по убыванию).
    local -a names=() mtimes=() sizes=()
    local file mtime size
    while IFS= read -r file; do
        [[ -n "$file" ]] || continue
        mtime="$(stat -c '%Y' -- "$file" 2>/dev/null)" || continue
        size="$(stat -c '%s' -- "$file" 2>/dev/null)" || continue
        names+=("$file")
        mtimes+=("$mtime")
        sizes+=("$size")
    done < <(find "$dir" -maxdepth 1 -type f -name "$DUMP_PATTERN" -printf '%T@ %p\n' 2>/dev/null | sort -rn | cut -d' ' -f2-)

    local total=0
    local i
    for (( i = 0; i < ${#names[@]}; i++ )); do
        total=$(( total + sizes[i] ))
    done

    local now age_limit
    now="$(date +%s)"
    age_limit=$(( now - MIN_AGE_MINUTES * 60 ))

    local removed=0 freed=0

    # Проход 1 — «последние KEEP_RECENT»: всё старше двух новейших вытесняется
    # по возрасту. Молодые файлы (моложе MIN_AGE_MINUTES) защищены: это может быть
    # недописанный дамп.
    for (( i = KEEP_RECENT; i < ${#names[@]}; i++ )); do
        if (( mtimes[i] > age_limit )); then
            continue
        fi
        if remove_dump "${names[i]}"; then
            removed=$(( removed + 1 ))
            freed=$(( freed + sizes[i] ))
            total=$(( total - sizes[i] ))
        else
            rc=1
        fi
    done

    # Проход 2 — лимит тома. Приоритет у лимита (FR-011): том не должен
    # переполняться. Если два новейших дампа сами не помещаются в лимит,
    # вытесняем дальше по возрасту — в том числе среди «последних двух».
    # Молодой файл при этом НЕ трогаем: лучше временно превысить лимит с
    # предупреждением, чем удалить недописанный дамп (см. код возврата 1).
    for (( i = ${#names[@]} - 1; i >= 0; i-- )); do
        (( total <= MAX_TOTAL_BYTES )) && break
        [[ -e "${names[i]}" ]] || continue
        if (( mtimes[i] > age_limit )); then
            log "лимит тома не достигнут: ${names[i]} моложе ${MIN_AGE_MINUTES} мин и защищён от удаления"
            rc=1
            break
        fi
        if remove_dump "${names[i]}"; then
            removed=$(( removed + 1 ))
            freed=$(( freed + sizes[i] ))
            total=$(( total - sizes[i] ))
        else
            rc=1
        fi
    done

    local kept
    kept=$(find "$dir" -maxdepth 1 -type f -name "$DUMP_PATTERN" 2>/dev/null | wc -l)
    log "dir=$dir kept=$kept removed=$removed freedBytes=$freed totalBytes=$total"
    return "$rc"
}

# Каталоги фикстуры self-test — глобальные, чтобы их видела функция очистки
# (вложенная функция не видит `local` переменные объемлющей).
SELF_TEST_DIR=""
SELF_TEST_BIG_DIR=""

cleanup_self_test() {
    [[ -n "$SELF_TEST_DIR" ]] && rm -rf "$SELF_TEST_DIR"
    [[ -n "$SELF_TEST_BIG_DIR" ]] && rm -rf "$SELF_TEST_BIG_DIR"
    return 0
}

# Проверка политики на синтетических файлах: 4 старых дампа + 3 молодых + SQL-дамп.
# Ожидания: старые вытесняются по возрасту, молодой защищён, SQL-дамп не тронут.
self_test() {
    SELF_TEST_DIR="$(mktemp -d)"
    SELF_TEST_BIG_DIR="$(mktemp -d)"
    trap cleanup_self_test EXIT
    local dir="$SELF_TEST_DIR"
    local rc=0

    local now
    now="$(date +%s)"
    # Молодые (моложе MIN_AGE_MINUTES) — защищены от вытеснения.
    touch -d "@$(( now - 10 * 60 ))" "$dir/young-newest.hprof"
    touch -d "@$(( now - 20 * 60 ))" "$dir/young-middle.hprof"
    touch -d "@$(( now - 30 * 60 ))" "$dir/young-oldest.hprof"
    # Старые — вытесняются по возрасту (от самых старых).
    touch -d "@$(( now - 10 * 86400 ))" "$dir/old-1.hprof"
    touch -d "@$(( now - 9 * 86400 ))" "$dir/old-2.hprof"
    touch -d "@$(( now - 8 * 86400 ))" "$dir/old-3.hprof"
    # SQL-дамп БД лежит в том же каталоге — трогать его нельзя.
    touch -d "@$(( now - 10 * 86400 ))" "$dir/dump.sql"
    # Посторонний файл — тоже не трогаем.
    touch -d "@$(( now - 10 * 86400 ))" "$dir/notes.txt"

    apply_policy "$dir" >/dev/null

    local remaining
    remaining="$(find "$dir" -maxdepth 1 -type f -name "$DUMP_PATTERN" | wc -l)"
    if [[ "$remaining" != "3" ]]; then
        log "self-test FAIL: осталось $remaining .hprof, ожидалось 3 (два новейших + защищённый по возрасту)"
        rc=1
    fi
    for expected in young-newest.hprof young-middle.hprof young-oldest.hprof; do
        if [[ ! -f "$dir/$expected" ]]; then
            log "self-test FAIL: удалён $expected (молодой файл защищён по возрасту)"
            rc=1
        fi
    done
    for expected in old-1.hprof old-2.hprof old-3.hprof; do
        if [[ -f "$dir/$expected" ]]; then
            log "self-test FAIL: $expected не вытеснен (старый дамп)"
            rc=1
        fi
    done
    if [[ ! -f "$dir/dump.sql" ]]; then
        log "self-test FAIL: SQL-дамп БД удалён — политика не должна трогать .sql"
        rc=1
    fi
    if [[ ! -f "$dir/notes.txt" ]]; then
        log "self-test FAIL: посторонний файл удалён"
        rc=1
    fi

    # Сценарий лимита тома: три дампа по 15 ГБ (sparse-файлы — места на диске не
    # занимают). Политика обязана уложиться в 20 ГБ: 15 ГБ уцелеет.
    local big="$SELF_TEST_BIG_DIR"
    truncate -s 15G "$big/dump-1.hprof"
    truncate -s 15G "$big/dump-2.hprof"
    truncate -s 15G "$big/dump-3.hprof"
    touch -d "@$(( now - 3 * 86400 ))" "$big/dump-1.hprof"
    touch -d "@$(( now - 2 * 86400 ))" "$big/dump-2.hprof"
    touch -d "@$(( now - 1 * 86400 ))" "$big/dump-3.hprof"
    apply_policy "$big" >/dev/null
    local big_total
    big_total="$(find "$big" -maxdepth 1 -type f -name "$DUMP_PATTERN" -printf '%s\n' | awk '{ s += $1 } END { print s + 0 }')"
    if (( big_total > MAX_TOTAL_BYTES )); then
        log "self-test FAIL: после уборки осталось $big_total байт — лимит ${MAX_TOTAL_BYTES} превышен"
        rc=1
    fi
    if [[ "$rc" == "0" ]]; then
        log "self-test: OK (политика хранения работает: последние 2, лимит ${MAX_TOTAL_BYTES} байт, вытеснение по возрасту)"
    fi
    return "$rc"
}

main() {
    local dir="$DEFAULT_DIR"
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --self-test)
                self_test
                return $?
                ;;
            --dir)
                dir="${2:?--dir требует путь}"
                shift 2
                ;;
            -h | --help)
                sed -n '2,40p' "${BASH_SOURCE[0]}"
                return 0
                ;;
            *)
                echo "Неизвестный аргумент: $1" >&2
                return 1
                ;;
        esac
    done
    apply_policy "$dir"
}

main "$@"
