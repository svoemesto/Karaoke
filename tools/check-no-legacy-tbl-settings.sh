#!/usr/bin/env bash
# check-no-legacy-tbl-settings.sh
#
# CI-gate для Karaoke. Проверяет, что новые файлы НЕ содержат
# устаревшее имя таблицы `tbl_settings` (вместо правильного `tbl_songs`).
#
# Прецедент (Pass 369, 2026-09-11): агент в спецификации
# specs/369-free-after-onair-flag взял имя таблицы `tbl_settings` из
# имён старых файлов recordhash и миграций, не проверив реальное состояние
# БД через код. Таблица была переименована в `tbl_songs` в миграции 28
# (Pass ~28, spec 011 — `28_rename_settings_to_songs.sql`). Миграция
# `50_tbl_settings_free_after_on_air.sql` упала бы на проде.
#
# Использование: запускается в CI как блокирующий gate.
#
# Правила:
# 1. В SQL-файлах (deploy/karaoke-db/<NN>_<name>.sql, deploy/recordhash_*.sql)
#    ЗАПРЕЩЕНО использовать `tbl_settings` как имя таблицы в любом SQL-выражении
#    (ALTER TABLE, SELECT, FROM, JOIN, INSERT INTO, UPDATE, CREATE, REFERENCES).
#    Разрешено упоминать в комментариях (-- tbl_settings — старое имя).
# 2. В Kotlin-файлах ЗАПРЕЩЕНо использовать `tbl_settings` (см. Song.kt,
#    SongDTO.kt, и т.п. — там должно быть `tbl_songs`).
# 3. В markdown-файлах спецификаций ЗАПРЕЩЕНО упоминать `tbl_settings`
#    в code-block'ах ```sql (там — это код, а не комментарий).
#
# При срабатывании: выводит список «файл:строка» и завершается с кодом 1.
#
# Версия: 1.2.0 (Pass 369 + BR-582 + BR-225, OpenProject #409).
#
# BR-225: добавлена проверка легаси-документации
# karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/docs/*.sql — рабочих
# ad-hoc-запросов админа. Они должны использовать ДЕЙСТВУЮЩЕЕ имя таблицы
# песен (tbl_songs, миграция 28), а не устаревшее tbl_settings.
#
# BR-582 (аудит #336, пакет PKG-S3-30): промежуточные файлы писались в
# ПРЕДСКАЗУЕМЫЕ пути /tmp/_nocomm.txt и /tmp/_sql.txt. Любой пользователь
# машины мог заранее создать такой файл (или symlink на свой) и получить
# подмену проверяемых данных. Теперь — приватный mktemp -d с trap-очисткой
# (как в check-endpoint-field-coverage.sh).

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT" || exit 2

# Приватный временный каталог вместо предсказуемых путей в /tmp (BR-582).
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT
NOCOMM_FILE="${TMP_DIR}/nocomm.txt"
SQL_BLOCK_FILE="${TMP_DIR}/sql_blocks.txt"

if [ -t 1 ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    NC='\033[0m'
else
    RED=''
    GREEN=''
    NC=''
fi

VIOLATIONS=0
CHECKED=0

# Регулярка для поиска `tbl_settings` в SQL как имени таблицы.
SQL_TABLE_USE='\btbl_settings\b'

# Для Kotlin — ищем как идентификатор.
KOTLIN_USE='\btbl_settings\b'

# 1. Новые миграции: deploy/karaoke-db/<NN>_<name>.sql
echo "[1/6] Проверка новых миграций deploy/karaoke-db/..."
for f in deploy/karaoke-db/*.sql; do
    # Пропускаем саму миграцию переименования и все миграции с номером <= 28
    # (они immutable, выполнены до переименования tbl_settings -> tbl_songs).
    fname=$(basename "$f")
    num=$(echo "$fname" | sed -E 's/^([0-9]+).*/\1/')
    if [ "$fname" = "28_rename_settings_to_songs.sql" ] || [ "$num" -lt 29 ] 2>/dev/null; then
        continue
    fi
    CHECKED=$((CHECKED + 1))
    # Удаляем строки-комментарии (-- в начале), затем ищем tbl_settings.
    # Для inline-комментариев (-- в середине строки) — вырезаем всё после --.
    awk '
        BEGIN{}
        /^--/ { next }
        { line=$0; sub(/--.*$/, "", line); print NR": "line }
    ' "$f" > "${NOCOMM_FILE}"
    if grep -nE "${SQL_TABLE_USE}" "${NOCOMM_FILE}" > /dev/null 2>&1; then
        echo -e "  ${RED}✗${NC} $f содержит tbl_settings (вне комментария):"
        grep -nE "${SQL_TABLE_USE}" "${NOCOMM_FILE}" | head -5 | sed 's/^/    /'
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done

# 2. Recordhash-файлы: deploy/recordhash_*.sql
echo "[2/6] Проверка recordhash-файлов deploy/recordhash_*.sql..."
for f in deploy/recordhash_*.sql; do
    [ -f "$f" ] || continue
    CHECKED=$((CHECKED + 1))
    awk '
        BEGIN{}
        /^--/ { next }
        { line=$0; sub(/--.*$/, "", line); print NR": "line }
    ' "$f" > "${NOCOMM_FILE}"
    if grep -nE "${SQL_TABLE_USE}" "${NOCOMM_FILE}" > /dev/null 2>&1; then
        echo -e "  ${RED}✗${NC} $f содержит tbl_settings (вне комментария):"
        grep -nE "${SQL_TABLE_USE}" "${NOCOMM_FILE}" | head -5 | sed 's/^/    /'
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done

# 3. Kotlin: ищем tbl_settings во всех .kt файлах в karaoke-app/, karaoke-web/
echo "[3/6] Проверка Kotlin-файлов..."
# Baseline: допустимые вхождения (как в check-file-permissions.baseline).
# Нужны для тестов, которые УТВЕРЖДАЮТ отсутствие старого имени: литерал
# «tbl_settings» в таком тесте — это проверка, а не использование.
# Формат строки: <путь к файлу>:<строка>  # <причина>
BASELINE_FILE="$REPO_ROOT/tools/check-no-legacy-tbl-settings.baseline"
declare -A ALLOWED=()
if [ -f "$BASELINE_FILE" ]; then
    while IFS= read -r line; do
        case "$line" in
            ''|'#'*) continue ;;
        esac
        key="$(echo "$line" | sed -E 's/[[:space:]]*#.*$//' | sed -E 's/[[:space:]]+$//')"
        [ -n "$key" ] && ALLOWED["$key"]=1
    done < "$BASELINE_FILE"
fi

for f in $(find karaoke-app karaoke-web -name "*.kt" -not -path "*/build/*" 2>/dev/null); do
    CHECKED=$((CHECKED + 1))
    hits="$(grep -nE "${KOTLIN_USE}" "$f" 2>/dev/null || true)"
    [ -z "$hits" ] && continue
    # Оставляем только те вхождения, которых нет в baseline.
    remaining=""
    while IFS= read -r h; do
        [ -z "$h" ] && continue
        ln="${h%%:*}"
        key="$(echo "$f" | sed -E "s|^${REPO_ROOT}/||"):${ln}"
        if [ -z "${ALLOWED[$key]:-}" ]; then
            remaining="${remaining}${h}"$'\n'
        fi
    done <<< "$hits"
    if [ -n "$remaining" ]; then
        echo -e "  ${RED}✗${NC} $f содержит tbl_settings:"
        echo "$remaining" | head -3 | sed 's/^/    /'
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done

# 4. Спецификации: проверяем code-blocks ```sql на tbl_settings (там — нарушение).
echo "[4/6] Проверка code-blockов SQL в спецификациях specs/NNN-*/..."
for f in specs/*/spec.md specs/*/plan.md specs/*/research.md \
         specs/*/data-model.md specs/*/quickstart.md specs/*/report.md \
         specs/*/contracts/*.md; do
    [ -f "$f" ] || continue
    CHECKED=$((CHECKED + 1))
    # Извлекаем содержимое ```sql ... ``` блоков.
    awk '
        /^```sql/ { in_block=1; next }
        /^```/ && in_block { in_block=0; next }
        in_block { print NR": "$0 }
    ' "$f" > "${SQL_BLOCK_FILE}"
    if grep -nE '\btbl_settings\b' "${SQL_BLOCK_FILE}" > /dev/null 2>&1; then
        echo -e "  ${RED}✗${NC} $f содержит tbl_settings в SQL code-block:"
        grep -nE '\btbl_settings\b' "${SQL_BLOCK_FILE}" | head -5 | sed 's/^/    /'
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done

# 5. Per-feature документы: docs/features/<slug>.md
echo "[5/6] Проверка code-blockов SQL в knowledge/product/features/*.md..."
for f in knowledge/product/features/*.md; do
    [ -f "$f" ] || continue
    CHECKED=$((CHECKED + 1))
    awk '
        /^```sql/ { in_block=1; next }
        /^```/ && in_block { in_block=0; next }
        in_block { print NR": "$0 }
    ' "$f" > "${SQL_BLOCK_FILE}"
    if grep -nE '\btbl_settings\b' "${SQL_BLOCK_FILE}" > /dev/null 2>&1; then
        echo -e "  ${RED}✗${NC} $f содержит tbl_settings в SQL code-block:"
        grep -nE '\btbl_settings\b' "${SQL_BLOCK_FILE}" | head -5 | sed 's/^/    /'
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done

# 6. BR-225 (аудит #336, OpenProject #409): легаси-документация —
#    karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/docs/*.sql.
#    Это рабочие ad-hoc-запросы админа, а не документация: они должны
#    соответствовать ДЕЙСТВУЮЩЕЙ схеме (tbl_songs после миграции 28).
echo "[6/6] Проверка легаси-документации karaoke-app/.../docs/*.sql..."
LEGACY_DOCS_DIR="karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/docs"
for f in "${LEGACY_DOCS_DIR}"/*.sql; do
    [ -f "$f" ] || continue
    CHECKED=$((CHECKED + 1))
    # Комментарии (`--`) допускаются — как и в миграциях.
    awk '
        BEGIN{}
        /^--/ { next }
        { line=$0; sub(/--.*$/, "", line); print NR": "line }
    ' "$f" > "${NOCOMM_FILE}"
    if grep -nE "${SQL_TABLE_USE}" "${NOCOMM_FILE}" > /dev/null 2>&1; then
        echo -e "  ${RED}✗${NC} $f содержит tbl_settings (вне комментария):"
        grep -nE "${SQL_TABLE_USE}" "${NOCOMM_FILE}" | head -5 | sed 's/^/    /'
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done

echo ""
if [ $VIOLATIONS -gt 0 ]; then
    echo -e "${RED}FAIL${NC}: найдено $VIOLATIONS нарушений (проверено $CHECKED файлов)."
    echo ""
    echo "tbl_settings — УСТАРЕВШЕЕ имя таблицы (было переименовано в tbl_songs"
    echo "в миграции 28 — deploy/karaoke-db/28_rename_settings_to_songs.sql)."
    echo ""
    echo "Используйте tbl_songs. Если нашли старую ссылку:"
    echo "  1. Проверьте реальное состояние БД:"
    echo "     docker exec karaoke-db psql -U postgres -d karaoke -c '\\d tbl_songs'"
    echo "  2. Найдите источник ссылки (старые recordhash-файлы — мёртвый код,"
    echo "     не отражает реальное состояние БД с момента переименования)."
    echo "  3. Исправьте на tbl_songs."
    echo ""
    exit 1
fi

echo -e "${GREEN}OK${NC}: $CHECKED файлов проверено, нарушений нет."
exit 0