#!/usr/bin/env bash
# =====================================================================
# tools/check-deploy-hardening.sh — guard «deploy-контур держит S2-инварианты»
# =====================================================================
# Пакет: PKG-S2-08 (аудит #336, OpenProject #371), 15 записей:
#   BR-039 BR-040 BR-041 BR-043 BR-534 BR-539 BR-542 BR-543 BR-544
#   BR-545 BR-546 BR-554 BR-555 BR-556 BR-557
#
# Guard статический: проверяет файлы контура deploy/ (+ два файла
# приложения, которых касаются BR-545/BR-557). Ничего не запускает,
# ни к БД, ни к контейнерам, ни к сети не ходит.
#
# Использование:
#   bash tools/check-deploy-hardening.sh                 # текущий checkout
#   bash tools/check-deploy-hardening.sh --repo /path    # другой checkout
#   bash tools/check-deploy-hardening.sh --list          # список проверок
#   bash tools/check-deploy-hardening.sh --self-test      # проверка самих правил
#
# Exit codes:
#   0 — все проверки пройдены
#   1 — есть нарушения
#   2 — usage error / self-test failed
# =====================================================================

set -uo pipefail

SELF_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "${SELF_PATH}/.." && pwd)"
MODE="check"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --repo)
            REPO="${2:?--repo требует путь}"
            shift 2
            ;;
        --list)
            MODE="list"
            shift
            ;;
        --self-test)
            MODE="self-test"
            shift
            ;;
        -h|--help)
            sed -n '2,30p' "${BASH_SOURCE[0]}"
            exit 0
            ;;
        *)
            echo "Usage: bash tools/check-deploy-hardening.sh [--repo PATH] [--list] [--self-test]" >&2
            exit 2
            ;;
    esac
done

REPO="$(cd "$REPO" && pwd)"

pass=0
fail=0
failed_brs=()

ok() {
    echo "  PASS  [$1] $2"
    pass=$((pass + 1))
}

bad() {
    echo "  FAIL  [$1] $2"
    fail=$((fail + 1))
    failed_brs+=("$1")
}

# Чтение файла (пустой вывод, если файла нет).
read_file() {
    local f="$1"
    [[ -f "$REPO/$f" ]] || return 1
    cat "$REPO/$f"
}

# grep -q по файлу в репозитории (без падения, если файла нет).
# Строки-комментарии не проверяются: в комментарии дефект не исполняется
# (та же конвенция, что в tools/check-secret-in-argv.sh).
file_has() {
    local pattern="$1" file="$2"
    [[ -f "$REPO/$file" ]] || return 1
    # awk, а не `grep -v | grep -q`: при `set -o pipefail` ранний выход второго grep
    # убивает первый SIGPIPE-ом (141), и проверка «есть ли шаблон» ложно падает на больших
    # файлах (Song.kt). Комментарии по-прежнему не проверяются (в них дефект не исполняется).
    awk -v pattern="$pattern" '
        /^[[:space:]]*#/ { next }
        $0 ~ pattern { found = 1; exit }
        END { exit !found }
    ' "$REPO/$file"
}

# grep -q по файлу БЕЗ фильтрации комментариев — для маркеров-документации
# («BR-xxx» живёт в комментарии, потому что иначе это не комментарий).
file_has_anywhere() {
    local pattern="$1" file="$2"
    [[ -f "$REPO/$file" ]] || return 1
    grep -qE "$pattern" "$REPO/$file"
}

# Блок YAML под ключом (строки с отступом больше, чем у самого ключа).
block_under() {
    local file="$1" key="$2"
    [[ -f "$REPO/$file" ]] || return 1
    awk -v key="$key" '
    function indent_of(s,   n) {
        n = 0
        while (n < length(s) && substr(s, n + 1, 1) ~ /[ \t]/) n++
        return n
    }
    {
        if (inblock) {
            if ($0 ~ /^[[:space:]]*$/) { print; next }
            if (indent_of($0) <= keyindent) { inblock = 0; next }
            print
            next
        }
        line = $0
        pos = index(line, key)
        if (pos > 0) {
            pre = substr(line, 1, pos - 1)
            if (pre ~ /^[[:space:]]*$/) {
                keyindent = length(pre)
                inblock = 1
            }
        }
    }' "$REPO/$file"
}

# Секция services: (до следующего ключа верхнего уровня).
services_block() {
    local file="$1"
    [[ -f "$REPO/$file" ]] || return 1
    awk '
        /^services:/ { inside = 1; next }
        inside && /^[^[:space:]#]/ { exit }
        inside { print }
    ' "$REPO/$file"
}
# ---------------------------------------------------------------------
# BR-039 — миграции должны воспроизводиться с нуля (P-70-01).
#   02_result_text_and_index.sql обязан быть идемпотентным, иначе
#   initdb-цепочка (01…50) падает на «column already exists» и БД
#   не поднимается с чистого PGDATA.
#   18_author_aliases.sql обязан создавать tbl_authors: ни один файл
#   каталога её не создавал, а 18 делает ALTER TABLE tbl_authors.
# ---------------------------------------------------------------------
check_br039() {
    local br="BR-039"
    if file_has 'ADD[[:space:]]+COLUMN[[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS[[:space:]]+result_text' \
        "deploy/karaoke-db/02_result_text_and_index.sql"; then
        ok "$br" "02_result_text_and_index.sql: result_text добавляется через ADD COLUMN IF NOT EXISTS"
    else
        bad "$br" "02_result_text_and_index.sql: result_text добавляется без IF NOT EXISTS (падает на БД из 01)"
    fi

    if file_has 'CREATE[[:space:]]+INDEX[[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS' \
        "deploy/karaoke-db/02_result_text_and_index.sql"; then
        ok "$br" "02_result_text_and_index.sql: индекс создаётся через CREATE INDEX IF NOT EXISTS"
    else
        bad "$br" "02_result_text_and_index.sql: индекс создаётся без IF NOT EXISTS (падает на БД из 01)"
    fi

    if file_has 'CREATE[[:space:]]+TABLE[[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS[[:space:]]+public\.tbl_authors' \
        "deploy/karaoke-db/18_author_aliases.sql"; then
        ok "$br" "18_author_aliases.sql: tbl_authors создаётся, если её нет"
    else
        bad "$br" "18_author_aliases.sql: tbl_authors не создаётся — ALTER TABLE падает на чистой БД"
    fi
}

# ---------------------------------------------------------------------
# BR-040 — секреты не лежат в отслеживаемых compose-файлах (P-70-02).
#   Значения переносятся в deploy/do.env (в .gitignore) и подставляются
#   через ${WHISPER_API_KEY} / ${SEARXNG_SECRET}.
# ---------------------------------------------------------------------
# Проверка наличия паттерна в ЗАКОММИЧЕННОМ содержимом файла (git show HEAD:<path>).
# Нужна там, где рабочая копия может быть stale (skip-worktree) или содержать
# локальные значения, которых нет в репозитории.
git_file_has() {
    local pattern="$1" path="$2"
    git -C "$REPO" show "HEAD:$path" 2>/dev/null | grep -qE "$pattern"
}

check_br040() {
    local br="BR-040"
    local leaks
    # Ищем ТОЛЬКО в отслеживаемых git файлах: deploy/do.env — локальный, gitignored
    # файл с реальными значениями секретов (по решению BR-040 они живут там), и его
    # наличие не является закоммиченной утечкой. Проверка по файловой системе давала
    # ложное срабатывание на любой машине, где do.env заполнен.
    leaks="$(git -C "$REPO" grep -lE 'nafanya_whisper_2026|i-love-my-wife-oxana' HEAD -- 'deploy/*' 2>/dev/null \
        | sed "s|^HEAD:||" || true)"
    if [[ -z "$leaks" ]]; then
        ok "$br" "в отслеживаемых файлах deploy/ нет значений WHISPER_API_KEY / SEARXNG_SECRET"
    else
        bad "$br" "закоммиченные секреты в: $(echo "$leaks" | tr '\n' ' ')"
    fi

    if file_has '\$\{WHISPER_API_KEY' "deploy/docker-compose-whisper.yml"; then
        ok "$br" "docker-compose-whisper.yml: WHISPER_API_KEY подставляется из окружения"
    else
        bad "$br" "docker-compose-whisper.yml: WHISPER_API_KEY не подставляется из окружения"
    fi

    if file_has '\$\{SEARXNG_SECRET' "deploy/docker-compose.yml" &&
        file_has '\$\{SEARXNG_SECRET' "deploy/docker-compose-app.yml"; then
        ok "$br" "docker-compose.yml / docker-compose-app.yml: SEARXNG_SECRET подставляется из окружения"
    else
        bad "$br" "docker-compose.yml / docker-compose-app.yml: SEARXNG_SECRET не подставляется из окружения"
    fi

    # settings.yml: в репозитории — только плейсхолдер, который entrypoint
    # образа searxng подменяет на $SEARXNG_SECRET при старте контейнера.
    # Читаем ЗАКОММИЧЕННОЕ содержимое: локально файл может быть stale (skip-worktree),
    # и тогда проверка видит старый реальный секрет, которого в git уже нет.
    if git_file_has 'secret_key:[[:space:]]*"ultrasecretkey"' "deploy/searxng-settings/settings.yml"; then
        ok "$br" "searxng-settings/settings.yml: secret_key — плейсхолдер, реальный секрет не в git"
    else
        bad "$br" "searxng-settings/settings.yml: secret_key не плейсхолдер (реальный секрет в git)"
    fi

    if file_has 'WHISPER_API_KEY' "deploy/do.env.example" && file_has 'SEARXNG_SECRET' "deploy/do.env.example"; then
        ok "$br" "deploy/do.env.example: шаблон с ключами WHISPER_API_KEY / SEARXNG_SECRET есть"
    else
        bad "$br" "deploy/do.env.example: нет ключей WHISPER_API_KEY / SEARXNG_SECRET"
    fi
}

# ---------------------------------------------------------------------
# BR-041 — платёжная история не должна исчезать вместе с пользователем
#   (P-70-03). Для денежной таблицы — ON DELETE RESTRICT.
# ---------------------------------------------------------------------
check_br041() {
    local br="BR-041"
    local subs_block
    subs_block="$(read_file "deploy/karaoke-db/15_monetization.sql" | sed -n '/CREATE TABLE IF NOT EXISTS public.tbl_subscriptions/,/);/p')"
    if [[ -z "$subs_block" ]]; then
        bad "$br" "15_monetization.sql: не найдено определение tbl_subscriptions"
        return
    fi
    if grep -qE 'site_user_id[^;]*REFERENCES[[:space:]]+public\.tbl_site_users\(id\)[[:space:]]+ON[[:space:]]+DELETE[[:space:]]+RESTRICT' <<<"$subs_block"; then
        ok "$br" "15_monetization.sql: tbl_subscriptions.site_user_id — ON DELETE RESTRICT"
    else
        bad "$br" "15_monetization.sql: tbl_subscriptions.site_user_id — не RESTRICT (каскад съедает платежи)"
    fi

    # Для уже существующих БД нужна отдельная миграция (номера 15 не
    # переигрывается — CREATE TABLE IF NOT EXISTS).
    local fix_migrations
    fix_migrations="$(grep -rl 'tbl_subscriptions_site_user_id_fkey' "$REPO/deploy/karaoke-db" 2>/dev/null | sed "s|$REPO/||" || true)"
    if [[ -n "$fix_migrations" ]]; then
        ok "$br" "корректирующая миграция для существующих БД: $(echo "$fix_migrations" | tr '\n' ' ')"
    else
        bad "$br" "нет корректирующей миграции для существующих БД (15_monetization.sql не переигрывается)"
    fi
}

# ---------------------------------------------------------------------
# BR-043 — неудачный push обязан валить деплой (P-70-05).
#   Каждый `docker image push` и каждый `do_registry_login` в push-функциях
#   проверяют код возврата; deploy_*.sh не считают «Image is up to date»
#   успешным деплоем.
# ---------------------------------------------------------------------
check_br043() {
    local br="BR-043"
    local unguarded=0
    local line next
    while IFS= read -r line; do
        [[ "$line" =~ image[[:space:]]+push ]] || continue
        # комментарий — не исполняемая команда (в нём нет проверки кода возврата)
        [[ "$line" =~ ^[[:space:]]*# ]] && continue
        next="$(grep -A 1 -F "$line" "$REPO/deploy/do.sh" | tail -n 1)"
        if [[ "$line" != *'|| {'* && "$next" != *'|| {'* ]]; then
            bad "$br" "do.sh: push без проверки кода возврата: ${line//[[:space:]]/ }"
            unguarded=$((unguarded + 1))
        fi
    done < <(grep -E 'image[[:space:]]+push' "$REPO/deploy/do.sh" || true)
    if [[ "$unguarded" -eq 0 ]]; then
        ok "$br" "do.sh: каждый docker image push проверяет код возврата"
    fi

    if grep -qE 'do_registry_login[[:space:]]*\|\|' "$REPO/deploy/do.sh"; then
        ok "$br" "do.sh: do_registry_login в push-функциях проверяется"
    else
        bad "$br" "do.sh: do_registry_login в push-функциях не проверяется (падает логин → падает push)"
    fi

    local f
    for f in deploy/deploy_web.sh deploy/deploy_public.sh; do
        if [[ ! -f "$REPO/$f" ]]; then
            bad "$br" "$f: файл не найден"
            continue
        fi
        if awk '/Image is up to date/{seen=1} seen && /exit 1/{found=1} END{exit !found}' "$REPO/$f"; then
            ok "$br" "$f: «Image is up to date» — ошибка деплоя (exit 1)"
        else
            bad "$br" "$f: «Image is up to date» — предупреждение, деплой рапортует успех"
        fi
    done
}

# ---------------------------------------------------------------------
# BR-534 — recordhash tbl_authors обязан покрывать ВСЕ колонки таблицы
#   (P-71-02). Миграция 46 переопределяла функцию и теряла
#   description/short_description/warning/ready_songs_count/total_songs_count.
# ---------------------------------------------------------------------
check_br534() {
    local br="BR-534"
    local file="deploy/karaoke-db/46_author_sort_order.sql"
    local func
    func="$(read_file "$file" | sed -n '/CREATE OR REPLACE FUNCTION public.update_tbl_authors_recordhash/,/END;/p')"
    if [[ -z "$func" ]]; then
        bad "$br" "$file: не найдено определение update_tbl_authors_recordhash()"
        return
    fi
    local col
    local missing=0
    for col in id author ym_id vk_id last_album_ym last_album_vk last_album_processed watched skip \
        aliases description short_description warning is_special_order sort_order \
        ready_songs_count total_songs_count; do
        if ! grep -qE "NEW\.${col}::TEXT|NEW\.${col}[,)]" <<<"$func"; then
            bad "$br" "$file: в recordhash нет колонки ${col}"
            missing=$((missing + 1))
        fi
    done
    if [[ "$missing" -eq 0 ]]; then
        ok "$br" "$file: recordhash tbl_authors покрывает все 17 колонок"
    fi

    # Порядок колонок — часть контракта: разные формулы дают разный md5
    # у одной и той же строки, и LOCAL↔SERVER навсегда видят расхождение.
    # 18 (bootstrap) и 46 (миграция) обязаны объявлять ОДИН И ТОТ ЖЕ список.
    local expected="id author ym_id vk_id last_album_ym last_album_vk last_album_processed watched skip aliases is_special_order sort_order description short_description warning ready_songs_count total_songs_count"
    local order46 order18
    order46="$(grep -oE 'NEW\.[a-z_]+' <<<"$func" | sed 's/^NEW\.//' | grep -v '^recordhash$' | tr '\n' ' ' | sed 's/ $//')"
    order18="$(read_file "deploy/karaoke-db/18_author_aliases.sql" \
        | sed -n '/CREATE OR REPLACE FUNCTION public.update_tbl_authors_recordhash/,/END;/p' \
        | grep -oE 'NEW\.[a-z_]+' | sed 's/^NEW\.//' | grep -v '^recordhash$' | tr '\n' ' ' | sed 's/ $//')"
    if [[ "$order46" == "$expected" ]]; then
        ok "$br" "$file: порядок колонок recordhash — канонический"
    else
        bad "$br" "$file: порядок колонок recordhash отличается от канонического: $order46"
    fi
    if [[ "$order18" == "$expected" ]]; then
        ok "$br" "18_author_aliases.sql: порядок колонок recordhash совпадает с 46"
    else
        bad "$br" "18_author_aliases.sql: порядок колонок recordhash не совпадает с 46: $order18"
    fi

    # Миграция обязана быть самодостаточной: колонки, на которые ссылается
    # функция, должны существовать (иначе триггер падает на каждой записи).
    local col2
    local missing2=0
    for col2 in aliases is_special_order description short_description warning \
        ready_songs_count total_songs_count sort_order; do
        if ! file_has "ADD[[:space:]]+COLUMN[[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS[[:space:]]+${col2}" "$file"; then
            bad "$br" "$file: колонка ${col2} не создаётся миграцией (функция сломает запись tbl_authors)"
            missing2=$((missing2 + 1))
        fi
    done
    if [[ "$missing2" -eq 0 ]]; then
        ok "$br" "$file: миграция самодостаточна (все колонки recordhash создаются IF NOT EXISTS)"
    fi
}

# ---------------------------------------------------------------------
# BR-539 — ежедневный бэкап прод-БД обязан создаваться (P-73-01).
#   pg_dump получает пароль (через stdin + одноразовый .pgpass, как в
#   deploy/tracker-db-backup.sh), а не через --no-password без пароля.
# ---------------------------------------------------------------------
check_br539() {
    local br="BR-539"
    local file="deploy/prod-single-host/karaoke-db-backup.sh"
    if [[ ! -f "$REPO/$file" ]]; then
        bad "$br" "$file: файл не найден"
        return
    fi
    if file_has 'PGPASSFILE' "$file"; then
        ok "$br" "$file: пароль передаётся через одноразовый .pgpass (PGPASSFILE)"
    else
        bad "$br" "$file: пароль не передаётся в pg_dump — бэкап не создаётся (fe_sendauth: no password supplied)"
    fi
    if file_has '<<<"\$\{?DB_SERVER_POSTGRES_PASSWORD' "$file" || file_has 'DB_SERVER_POSTGRES_PASSWORD' "$file"; then
        ok "$br" "$file: пароль БД берётся из DB_SERVER_POSTGRES_PASSWORD"
    else
        bad "$br" "$file: DB_SERVER_POSTGRES_PASSWORD не используется"
    fi
    if file_has '\-e[[:space:]]+PGPASSWORD' "$file"; then
        bad "$br" "$file: пароль передаётся в окружение docker exec (виден в argv)"
    else
        ok "$br" "$file: пароль не попадает в argv"
    fi
}

# ---------------------------------------------------------------------
# BR-542 — трекер не должен слушать все интерфейсы (P-73-04).
#   network_mode: host выставлял БД трекера (5435) и UI (8080) на 0.0.0.0.
# ---------------------------------------------------------------------
check_br542() {
    local br="BR-542"
    local file="deploy/tracker-docker-compose.yml"
    if file_has 'network_mode:[[:space:]]*host' "$file"; then
        bad "$br" "$file: network_mode: host — порты трекера слушают все интерфейсы"
    else
        ok "$br" "$file: network_mode: host убран"
    fi
    if file_has '127\.0\.0\.1:\$\{?TRACKER_HTTP_PORT' "$file" && file_has '127\.0\.0\.1:\$\{?TRACKER_DB_PORT' "$file"; then
        ok "$br" "$file: порты UI и БД трекера публикуются только на loopback"
    else
        bad "$br" "$file: порты UI/БД трекера не привязаны к 127.0.0.1"
    fi
    if file_has 'DATABASE_URL:[[:space:]]*postgresql://openproject:\$\{TRACKER_DB_PASSWORD\}@openproject-db:' "$file"; then
        ok "$br" "$file: openproject ходит в БД по имени сервиса (bridge-сеть), а не через localhost хоста"
    else
        bad "$br" "$file: DATABASE_URL указывает на localhost — в bridge-сети это сам контейнер, а не БД"
    fi
    if file_has 'TRACKER_HTTP_PORT' "tools/install-tracker.sh"; then
        ok "$br" "tools/install-tracker.sh: выбранный порт прописывается в TRACKER_HTTP_PORT"
    else
        bad "$br" "tools/install-tracker.sh: выбранный порт не сохраняется (compose опубликует не тот порт)"
    fi
}

# ---------------------------------------------------------------------
# BR-543 — блокировка бэкапа трекера держится на весь прогон (P-73-05).
#   ExecStartPre=flock отпускает блокировку до старта ExecStart.
# ---------------------------------------------------------------------
check_br543() {
    local br="BR-543"
    local file="deploy/tracker-db-backup.service"
    if [[ ! -f "$REPO/$file" ]]; then
        bad "$br" "$file: файл не найден"
        return
    fi
    if file_has '^ExecStart=.*flock' "$file"; then
        ok "$br" "$file: flock держится на весь прогон (в ExecStart)"
    else
        bad "$br" "$file: flock не удерживается на весь прогон"
    fi
    if file_has '^ExecStartPre=.*flock' "$file"; then
        bad "$br" "$file: ExecStartPre=flock — блокировка отпускается до начала бэкапа"
    else
        ok "$br" "$file: ExecStartPre=flock убран"
    fi
}

# ---------------------------------------------------------------------
# BR-544 — TLS без устаревших версий + скрытие версии nginx (P-73-06).
# ---------------------------------------------------------------------
check_br544() {
    local br="BR-544"
    local f
    for f in deploy/prod-single-host/nginx.conf deploy/web-server-deploy/deploy/nginx.conf; do
        if [[ ! -f "$REPO/$f" ]]; then
            bad "$br" "$f: файл не найден"
            continue
        fi
        local proto
        proto="$(grep -E '^[[:space:]]*ssl_protocols' "$REPO/$f" | head -1)"
        if [[ -z "$proto" ]]; then
            bad "$br" "$f: нет директивы ssl_protocols"
        elif grep -qE '(^|[[:space:]])TLSv1([^.]|$)|(^|[[:space:]])TLSv1\.1([[:space:]]|$)' <<<"$proto"; then
            bad "$br" "$f: разрешён устаревший TLS — ${proto//[[:space:]]/ }"
        else
            ok "$br" "$f: ssl_protocols без TLSv1/TLSv1.1"
        fi
        if grep -qE '^[[:space:]]*server_tokens[[:space:]]+off;' "$REPO/$f"; then
            ok "$br" "$f: server_tokens off (версия nginx не отдаётся клиенту)"
        else
            bad "$br" "$f: server_tokens off не включён"
        fi
    done
}

# ---------------------------------------------------------------------
# BR-545 — канал до удалённой БД/хранилища должен уметь TLS (P-73-07).
#   Требования к фиксу (сертификаты на прод-хосте, переключение клиента)
#   — решение владельца; guard следит, чтобы механизм переключения был
#   и не мог тихо исчезнуть.
# ---------------------------------------------------------------------
check_br545() {
    local br="BR-545"
    if file_has 'DB_SSL_ON' "deploy/prod-single-host/docker-compose-database.yml"; then
        ok "$br" "prod docker-compose-database.yml: TLS сервера Postgres включается флагом DB_SSL_ON (по умолчанию off)"
    else
        bad "$br" "prod docker-compose-database.yml: нет управляемого переключателя TLS (DB_SSL_ON)"
    fi
    if file_has 'sslmode' "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Connection.kt"; then
        ok "$br" "Connection.kt: удалённая БД подключается с явным sslmode"
    else
        bad "$br" "Connection.kt: sslmode не задаётся — TLS на клиенте включить нечем"
    fi
    if file_has_anywhere 'BR-545' "deploy/prod-single-host/nginx.conf"; then
        ok "$br" "prod nginx.conf: stream-контур помечен BR-545 (TLS-вариант описан)"
    else
        bad "$br" "prod nginx.conf: stream-контур без отметки BR-545 (TLS-вариант не описан)"
    fi
}

# ---------------------------------------------------------------------
# BR-546 — логи контейнеров прод-хоста обязаны ротироваться (P-73-08).
# ---------------------------------------------------------------------
check_br546() {
    local br="BR-546"
    local f svc
    for f in deploy/prod-single-host/docker-compose-web.yml \
        deploy/prod-single-host/docker-compose-database.yml \
        deploy/prod-single-host/docker-compose-storage.yml \
        deploy/prod-single-host/docker-compose-public.yml; do
        if [[ ! -f "$REPO/$f" ]]; then
            bad "$br" "$f: файл не найден"
            continue
        fi
        local missing=0
        while IFS= read -r svc; do
            [[ -z "$svc" ]] && continue
            local block
            block="$(block_under "$f" "  ${svc}:")"
            if ! grep -q 'logging:' <<<"$block" ||
                ! grep -q 'max-size:' <<<"$block" ||
                ! grep -q 'max-file:' <<<"$block"; then
                bad "$br" "$f: у сервиса ${svc} нет logging с max-size/max-file"
                missing=$((missing + 1))
            fi
        done < <(services_block "$f" | grep -E '^  [A-Za-z0-9_-]+:' | sed 's/^  //; s/:$//' || true)
        if [[ "$missing" -eq 0 ]]; then
            ok "$br" "$f: у всех сервисов задана ротация логов"
        fi
    done
    if file_has 'df[[:space:]]+-h' "deploy/prod-single-host/prune-images.sh"; then
        ok "$br" "prod prune-images.sh: контролируется свободное место на диске"
    else
        bad "$br" "prod prune-images.sh: свободное место на диске не контролируется"
    fi
}

# ---------------------------------------------------------------------
# BR-554 — environment в compose обязан быть списком (P-72-02).
#   Смешение отображения и списка склеивает переменные в одно значение.
# ---------------------------------------------------------------------
check_br554() {
    local br="BR-554"
    local file="deploy/new_comp/sm-karaoke-system/deploy/docker-compose-web-new-comp.yml"
    if [[ ! -f "$REPO/$file" ]]; then
        bad "$br" "$file: файл не найден"
        return
    fi
    local env_block
    env_block="$(block_under "$file" "environment:")"
    if grep -qE '^[[:space:]]+[A-Za-z0-9_-]+:[[:space:]]*[^[:space:]]' <<<"$env_block"; then
        bad "$br" "$file: environment — отображение, смешанное со списком (YAML склеивает значения)"
    elif [[ -n "$env_block" ]]; then
        ok "$br" "$file: environment — список (каждый элемент начинается с «- »)"
    else
        bad "$br" "$file: блок environment не найден"
    fi
    local var
    for var in WORK_IN_CONTAINER DB_LOCAL_POSTGRES_USER DB_LOCAL_POSTGRES_PASSWORD \
        DB_SERVER_POSTGRES_USER DB_SERVER_POSTGRES_PASSWORD; do
        if grep -qE "^[[:space:]]*-[[:space:]]+${var}=" <<<"$env_block"; then
            ok "$br" "$file: переменная ${var} передаётся отдельным элементом списка"
        else
            bad "$br" "$file: переменная ${var} не передаётся (сервис не получит креды БД)"
        fi
    done
}

# ---------------------------------------------------------------------
# BR-555 — watcher не должен исполнять произвольный файл из общей
#   с контейнерами директории (P-72-03).
# ---------------------------------------------------------------------
check_br555() {
    local br="BR-555"
    local file="deploy/new_comp/sm-karaoke-system/karaoke_watcher.sh"
    if [[ ! -f "$REPO/$file" ]]; then
        bad "$br" "$file: файл не найден"
        return
    fi
    if file_has 'EXEC_DIR' "$file" && file_has 'mv([[:space:]]+-[A-Za-z]+)*[[:space:]]+"\$script"' "$file"; then
        ok "$br" "$file: скрипт переносится в приватный каталог исполнения до запуска (нет TOCTOU-окна)"
    else
        bad "$br" "$file: скрипт исполняется прямо из общей с контейнерами директории"
    fi
    if file_has 'SCRIPT_NAME_RE' "$file" || file_has '\^\[0-9a-f\]\{8\}' "$file"; then
        ok "$br" "$file: имя исполняемого файла проверяется по белому шаблону"
    else
        bad "$br" "$file: нет проверки имени исполняемого файла"
    fi
    if grep -qE '^[[:space:]]*chmod[[:space:]]+\+x[[:space:]]+"\$script"' "$REPO/$file"; then
        bad "$br" "$file: скрипт делается исполняемым на месте (окно перезаписи из контейнера)"
    else
        ok "$br" "$file: скрипт не делается исполняемым на месте"
    fi
}

# ---------------------------------------------------------------------
# BR-556 — БД new_comp не должна слушать все интерфейсы (P-72-04).
# ---------------------------------------------------------------------
check_br556() {
    local br="BR-556"
    local file="deploy/new_comp/sm-karaoke-system/deploy/docker-compose-db-new-comp.yml"
    if file_has '127\.0\.0\.1:8832:5432' "$file"; then
        ok "$br" "$file: порт БД публикуется только на loopback"
    else
        bad "$br" "$file: порт БД публикуется на все интерфейсы (0.0.0.0:8832)"
    fi
}

# ---------------------------------------------------------------------
# BR-557 — прод-конфигурация должна явно задавать режим TLS для
#   удалённой БД и хранилища (P-72-05).
# ---------------------------------------------------------------------
check_br557() {
    local br="BR-557"
    local file="deploy/prod-single-host/.env.example"
    if [[ ! -f "$REPO/$file" ]]; then
        bad "$br" "$file: файл не найден"
        return
    fi
    if file_has '^DB_REMOTE_SSL_MODE=' "$file"; then
        ok "$br" "$file: режим TLS удалённой БД задан явно (DB_REMOTE_SSL_MODE)"
    else
        bad "$br" "$file: режим TLS удалённой БД не задан (JDBC молча деградирует в открытый текст)"
    fi
    if file_has_anywhere 'BR-557' "$file"; then
        ok "$br" "$file: риск открытого канала и целевое состояние зафиксированы в конфиге"
    else
        bad "$br" "$file: риск/целевое состояние не зафиксированы"
    fi
    if file_has 'STORAGE_REMOTE_ENDPOINT' "$file" && grep -qE 'STORAGE_REMOTE_ENDPOINT=https?://' "$REPO/$file"; then
        ok "$br" "$file: endpoint удалённого хранилища задан явно"
    else
        bad "$br" "$file: endpoint удалённого хранилища не задан"
    fi
}

# ---------------------------------------------------------------------
# BR-042 — тег образа един для сборки, push и pull на проде (P-70-04).
#   Литерал :1 в прод-скриптах и compose означал: при BUILD_VERSION != 1
#   push уходил в :<номер>, а прод тянул :1 (предыдущий образ).
# ---------------------------------------------------------------------
check_br042() {
    local br="BR-042"
    local f="deploy/prod-single-host/do.sh"
    if file_has 'karaoke-(web|public):1"' "$f"; then
        bad "$br" "$f: тег образа прибит литералом :1"
    else
        ok "$br" "$f: тег образа не прибит литералом :1"
    fi
    if file_has 'IMAGE_TAG="\$\{BUILD_VERSION\}"' "$f" && file_has 'karaoke-web:\$\{IMAGE_TAG\}' "$f"; then
        ok "$br" "$f: IMAGE_TAG — единый источник тега для pull/build/require_image"
    else
        bad "$br" "$f: нет единого IMAGE_TAG (pull/build проверяют разные теги)"
    fi
    if file_has_anywhere '\.version\.prev' "$f"; then
        ok "$br" "$f: предыдущий тег сохраняется для откката (.version.prev)"
    else
        bad "$br" "$f: предыдущий тег не сохраняется — откат невозможен"
    fi
    local compose
    for compose in deploy/prod-single-host/docker-compose-web.yml \
        deploy/prod-single-host/docker-compose-public.yml; do
        if grep -vE '^[[:space:]]*#' "$REPO/$compose" | grep -qE 'image:.*\$\{IMAGE_TAG'; then
            ok "$br" "$compose: образ подставляется через \${IMAGE_TAG}"
        else
            bad "$br" "$compose: образ прибит литералом :1 вместо \${IMAGE_TAG}"
        fi
    done
    local deploy_script
    for deploy_script in deploy/deploy_web.sh deploy/deploy_public.sh; do
        if grep -vE '^[[:space:]]*#' "$REPO/$deploy_script" | grep -qE 'BUILD_VERSION=.*bash do\.sh pull_'; then
            ok "$br" "$deploy_script: собранный тег передаётся на прод по ssh"
        else
            bad "$br" "$deploy_script: на прод не передаётся тег — прод тянет :1"
        fi
    done
}

# ---------------------------------------------------------------------
# BR-045 — GIN-индекс полнотекстового поиска по колонке, а не по константе
#   (P-70-07). Было: to_tsvector(..., 'result_text') — выражение одинаково
#   для всех строк; индекс лежал на tbl_settings, а поиск идёт по tbl_songs.
# ---------------------------------------------------------------------
check_br045() {
    local br="BR-045"
    local f="deploy/karaoke-db/01_initdb.sql"
    if file_has "to_tsvector\\('russian'::regconfig, result_text\\)" "$f"; then
        ok "$br" "$f: индекс строится по колонке result_text"
    else
        bad "$br" "$f: индекс строится не по колонке result_text"
    fi
    if grep -vE '^[[:space:]]*#' "$REPO/$f" | grep -qiE "to_tsvector\\('russian'::regconfig, 'result_text'\\)"; then
        bad "$br" "$f: выражение индекса содержит литерал 'result_text' (индекс бесполезен)"
    else
        ok "$br" "$f: литерала 'result_text' в выражении индекса нет"
    fi

    f="deploy/karaoke-db/02_result_text_and_index.sql"
    if file_has 'DROP[[:space:]]+INDEX[[:space:]]+IF[[:space:]]+EXISTS[[:space:]]+(public\.)?idx_gin_result_text' "$f" &&
        file_has "to_tsvector\\('russian'::regconfig, result_text\\)" "$f"; then
        ok "$br" "$f: старый индекс пересоздаётся по колонке (DROP + CREATE)"
    else
        bad "$br" "$f: индекс не пересоздаётся по колонке (IF NOT EXISTS оставит старое определение)"
    fi

    f="deploy/karaoke-db/54_fix_result_text_gin_index.sql"
    if [[ -f "$REPO/$f" ]] &&
        file_has 'to_regclass' "$f" &&
        file_has 'DROP[[:space:]]+INDEX[[:space:]]+IF[[:space:]]+EXISTS' "$f" &&
        file_has "to_tsvector\\(''russian''::regconfig, result_text\\)" "$f"; then
        ok "$br" "$f: миграция для существующих БД (tbl_songs или tbl_settings)"
    else
        bad "$br" "$f: нет миграции, пересоздающей индекс на существующих БД"
    fi
}

# ---------------------------------------------------------------------
# BR-046 — restore_db/create_clear_db действительно восстанавливают БД
#   (P-70-08): каталог дампов смонтирован, пользователь БД не хардкожен,
#   TTY не требуется, код возврата проверяется.
# ---------------------------------------------------------------------
check_br046() {
    local br="BR-046"
    local f="deploy/docker-compose-database.yml"
    if file_has '\./karaoke-db/dumps:/dumps' "$f"; then
        ok "$br" "$f: каталог дампов смонтирован в /dumps"
    else
        bad "$br" "$f: /dumps не смонтирован — psql --file=/dumps/... не находит файл"
    fi

    f="deploy/do.sh"
    local block
    block="$(sed -n '/^function do_restore_db()/,/^function do_create_clear_db()/p' "$REPO/$f")"
    if [[ -z "$block" ]]; then
        bad "$br" "$f: не найдены функции do_restore_db/do_create_clear_db"
        return
    fi
    if grep -qE 'exec -i karaoke-db psql -U "\$\{DB_LOCAL_POSTGRES_USER\}"' <<<"$block"; then
        ok "$br" "$f: psql запускается без TTY и с пользователем из окружения"
    else
        bad "$br" "$f: psql запускается с -it и/или с хардкодом -U postgres"
    fi
    if grep -qE '^function do_restore_db.*' <<<"$block" && \
        ! grep -vE '^[[:space:]]*#' <<<"$block" | grep -qE 'exec -it'; then
        ok "$br" "$f: в restore-функциях нет docker exec -it (падает без TTY)"
    else
        bad "$br" "$f: в restore-функциях остался docker exec -it"
    fi
    if grep -qE 'if ! \$\{DOCKER\} exec -i karaoke-db (test -f|psql)' <<<"$block"; then
        ok "$br" "$f: код возврата проверяется до announce «Restore DB!»"
    else
        bad "$br" "$f: ошибка psql не проверяется — печатается успех при провале"
    fi
}

# ---------------------------------------------------------------------
# BR-047 — образ хранилища запинен (тег + digest), креды — ROOT-имена
#   (P-70-09).
# ---------------------------------------------------------------------
check_br047() {
    local br="BR-047"
    local f="deploy/docker-compose-storage.yml"
    local image
    image="$(grep -vE '^[[:space:]]*#' "$REPO/$f" | grep -E '^[[:space:]]*image:' | head -1)"
    if grep -qE '@sha256:[0-9a-f]{64}' <<<"$image" && grep -qE 'RELEASE\.[0-9T:-]+Z' <<<"$image"; then
        ok "$br" "$f: образ запинен тегом и digest"
    else
        bad "$br" "$f: образ не запинен (плавающий тег / нет @sha256)"
    fi
    if grep -vE '^[[:space:]]*#' "$REPO/$f" | grep -qE 'MINIO_ACCESS_KEY|MINIO_SECRET_KEY'; then
        bad "$br" "$f: устаревшие MINIO_ACCESS_KEY/MINIO_SECRET_KEY"
    else
        ok "$br" "$f: креды — MINIO_ROOT_USER/MINIO_ROOT_PASSWORD"
    fi
    if file_has 'MINIO_ROOT_USER=\$\{STORAGE_KEY\}' "$f"; then
        ok "$br" "$f: MINIO_ROOT_USER подставляется из STORAGE_KEY"
    else
        bad "$br" "$f: MINIO_ROOT_USER не задан"
    fi
    local prod="deploy/prod-single-host/docker-compose-storage.yml"
    if file_has 'MINIO_ROOT_USER=\$\{STORAGE_KEY\}' "$prod"; then
        ok "$br" "$prod: те же ROOT-имена на прод-хосте"
    else
        bad "$br" "$prod: устаревшие имена кредов MinIO"
    fi
}

# ---------------------------------------------------------------------
# BR-535 — миграция статусов действительно идемпотентна (P-71-03):
#   условие не должно захватывать незавершённые состояния 3/4/5.
# ---------------------------------------------------------------------
check_br535() {
    local br="BR-535"
    local f="deploy/karaoke-db/32_song_status_lifecycle.sql"
    if file_has 'WHERE[[:space:]]+id_status[[:space:]]+IN[[:space:]]*\([[:space:]]*3[[:space:]]*,[[:space:]]*4[[:space:]]*,[[:space:]]*5[[:space:]]*\)' "$f"; then
        ok "$br" "$f: условие сужено до 3/4/5 (повторный запуск — no-op)"
    else
        bad "$br" "$f: условие не сужено — повторный запуск переводит незавершённые песни в READY"
    fi
    if grep -vE '^[[:space:]]*#' "$REPO/$f" | grep -qE 'WHERE[[:space:]]+id_status[[:space:]]*>=[[:space:]]*3'; then
        bad "$br" "$f: осталось WHERE id_status >= 3 (захватывает 3/4/5)"
    else
        ok "$br" "$f: WHERE id_status >= 3 отсутствует"
    fi
}

# ---------------------------------------------------------------------
# BR-536 — INSERT песни переносит album_id и поздние колонки (P-71-04).
#   Запись уже исправлена пакетом PKG-S2-10 (BR-108); здесь — регрессия.
# ---------------------------------------------------------------------
check_br536() {
    local br="BR-536"
    local f="karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt"
    local column
    local missing=0
    for column in album_id status_process_demo id_tariff song_name_censored free_after_on_air \
        description short_description warning; do
        if ! file_has "Pair\(\"${column}\"" "$f"; then
            bad "$br" "$f: колонка ${column} не переносится INSERT'ом (связь/поле не доедет в sync)"
            missing=$((missing + 1))
        fi
    done
    if [[ "$missing" -eq 0 ]]; then
        ok "$br" "$f: album_id и поздние колонки входят в INSERT"
    fi
}

# ---------------------------------------------------------------------
# BR-537 — каскад не уносит строки заданий MinIO (P-71-05): RESTRICT,
#   иначе объекты остаются в бакете навсегда.
# ---------------------------------------------------------------------
check_br537() {
    local br="BR-537"
    local f="deploy/karaoke-db/22_stem_jobs.sql"
    local block
    block="$(sed -n '/CREATE TABLE public.tbl_stem_jobs/,/);/p' "$REPO/$f")"
    if grep -qE 'site_user_id[^;]*REFERENCES public\.tbl_site_users\(id\) ON DELETE RESTRICT' <<<"$block"; then
        ok "$br" "$f: tbl_stem_jobs.site_user_id — ON DELETE RESTRICT"
    else
        bad "$br" "$f: каскад удаляет строки заданий, объекты MinIO остаются сиротами"
    fi
    f="deploy/karaoke-db/55_stem_jobs_on_delete_restrict.sql"
    if [[ -f "$REPO/$f" ]] && file_has 'ON DELETE RESTRICT' "$f" &&
        file_has 'DROP CONSTRAINT' "$f"; then
        ok "$br" "$f: миграция для существующих БД пересоздаёт FK с RESTRICT"
    else
        bad "$br" "$f: нет миграции для существующих БД"
    fi
}

# ---------------------------------------------------------------------
# BR-538 — триггер в 44 пересоздаётся (P-71-06): DROP IF EXISTS перед
#   CREATE, иначе повторное применение падает на «already exists».
# ---------------------------------------------------------------------
check_br538() {
    local br="BR-538"
    local f="deploy/karaoke-db/44_author_song_counts.sql"
    local drop_line create_line
    drop_line="$(grep -nE '^[[:space:]]*DROP[[:space:]]+TRIGGER[[:space:]]+IF[[:space:]]+EXISTS[[:space:]]+trg_tbl_songs_update_author_counts[[:space:]]+ON[[:space:]]+public\.tbl_songs' "$REPO/$f" | head -1 | cut -d: -f1)"
    create_line="$(grep -nE '^[[:space:]]*CREATE[[:space:]]+TRIGGER[[:space:]]+trg_tbl_songs_update_author_counts' "$REPO/$f" | head -1 | cut -d: -f1)"
    if [[ -n "$drop_line" && -n "$create_line" && "$drop_line" -lt "$create_line" ]]; then
        ok "$br" "$f: DROP TRIGGER IF EXISTS стоит перед CREATE TRIGGER"
    else
        bad "$br" "$f: повторное применение падает на «trigger already exists»"
    fi
}

# ---------------------------------------------------------------------
# BR-547 — прод karaoke-web: healthcheck, потолок ресурсов, смонтированные
#   дампы под -XX:HeapDumpPath (P-73-09).
# ---------------------------------------------------------------------
check_br547() {
    local br="BR-547"
    local f="deploy/prod-single-host/docker-compose-web.yml"
    if file_has 'healthcheck:' "$f" && file_has 'CMD-SHELL' "$f"; then
        ok "$br" "$f: healthcheck задан"
    else
        bad "$br" "$f: healthcheck отсутствует — зависший контейнер не виден мониторингу"
    fi
    if file_has 'mem_limit:' "$f" && file_has 'cpus:' "$f"; then
        ok "$br" "$f: mem_limit/cpus заданы (OOM-kiiller не выберет БД)"
    else
        bad "$br" "$f: нет лимитов памяти/CPU"
    fi
    if file_has '\-XX:HeapDumpPath=/dumps' "$f" && file_has '\./karaoke-db/dumps:/dumps' "$f"; then
        ok "$br" "$f: /dumps смонтирован — HeapDumpPath работает"
    else
        bad "$br" "$f: HeapDumpPath указывает в несмонтированный каталог (дамп теряется)"
    fi
}

# ---------------------------------------------------------------------
# BR-548 — TTS-модель проверяется по sha256 до загрузки pickle (P-73-10).
# ---------------------------------------------------------------------
check_br548() {
    local br="BR-548"
    local f="deploy/tts/silero_say.py"
    if file_has '^MODEL_SHA256 = "[0-9a-f]{64}"' "$f"; then
        ok "$br" "$f: sha256 модели зафиксирован"
    else
        bad "$br" "$f: sha256 модели не зафиксирован — pickle грузится без проверки"
    fi
    if file_has 'hashlib\.sha256' "$f" && file_has 'def verify_model' "$f"; then
        ok "$br" "$f: есть проверка sha256 файла модели"
    else
        bad "$br" "$f: нет проверки sha256"
    fi
    if file_has 'def download_model' "$f" && file_has 'os\.replace\(temporary, MODEL_PATH\)' "$f"; then
        ok "$br" "$f: загрузка идёт во временный файл, подмена — только после сверки"
    else
        bad "$br" "$f: модель скачивается сразу в целевой путь (проверка после загрузки бессмысленна)"
    fi
    local verify_line pickle_line
    verify_line="$(awk '!/^[[:space:]]*#/ && /^[[:space:]]+ensure_model\(\)/ { print NR; exit }' "$REPO/$f")"
    pickle_line="$(awk '!/^[[:space:]]*#/ && /load_pickle/ { print NR; exit }' "$REPO/$f")"
    if [[ -n "$verify_line" && -n "$pickle_line" && "$verify_line" -lt "$pickle_line" ]]; then
        ok "$br" "$f: проверка модели выполняется до load_pickle"
    else
        bad "$br" "$f: pickle грузится до проверки целостности"
    fi
}

# ---------------------------------------------------------------------
# BR-550 — лимит тела запроса публичного контура соответствует продукту
#   (P-73-12): 200 МБ (spring multipart), а не 1 ГБ.
# ---------------------------------------------------------------------
check_br550() {
    local br="BR-550"
    local f="deploy/prod-single-host/nginx_karaoke-public.conf"
    # Лимит должен стоять в ОБЕИХ копиях конфига: контейнер берёт конфиг с хоста,
    # а в дереве приложения лежит вторая копия — раньше они разошлись, и правка
    # уходила только в одну из них.
    for mirror in "karaoke-public/nginx_karaoke-public.conf"; do
        if file_has 'client_max_body_size[[:space:]]+200m;' "$mirror"; then
            ok "$br" "$mirror: лимит тела = 200m (вторая копия конфига)"
        else
            bad "$br" "$mirror: во второй копии конфига лимит тела не приведён к 200m"
        fi
    done
    if file_has 'client_max_body_size[[:space:]]+200m;' "$f"; then
        ok "$br" "$f: лимит тела = 200m (как у karaoke-web multipart)"
    else
        bad "$br" "$f: лимит тела не приведён к фактическому (200m)"
    fi
    if grep -vE '^[[:space:]]*#' "$REPO/$f" | grep -qE 'client_max_body_size[[:space:]]+1024m'; then
        bad "$br" "$f: остался client_max_body_size 1024m"
    else
        ok "$br" "$f: гигабайтного лимита нет"
    fi
    if file_has 'client_max_body_size[[:space:]]+200m;' "deploy/prod-single-host/80to8897"; then
        ok "$br" "deploy/prod-single-host/80to8897: тот же лимит продублирован в host-nginx"
    else
        bad "$br" "deploy/prod-single-host/80to8897: host-nginx не подтверждает лимит 200m"
    fi
}

# ---------------------------------------------------------------------
# BR-559 — new_comp do.sh падает на отсутствии env-файла, а не работает
#   на пустых значениях (P-72-07).
# ---------------------------------------------------------------------
check_br559() {
    local br="BR-559"
    local f="deploy/new_comp/sm-karaoke-system/deploy/do.sh"
    if file_has 'set -euo pipefail' "$f"; then
        ok "$br" "$f: set -euo pipefail"
    else
        bad "$br" "$f: нет fail-fast (ошибка compose/login не прерывает скрипт)"
    fi
    if file_has '\[\[ ! -f "\$\{env_file\}" \]\]' "$f" && file_has 'source "\$\{env_file\}"' "$f"; then
        ok "$br" "$f: env-файлы проверяются на существование"
    else
        bad "$br" "$f: отсутствие .env/do.env не проверяется — работа на пустых значениях"
    fi
    if grep -vE '^[[:space:]]*#' "$REPO/$f" | grep -qE '^[[:space:]]*source \$\{DEPLOY_DIR\}/(\.env|do\.env)$'; then
        bad "$br" "$f: остался безусловный source env-файла"
    else
        ok "$br" "$f: безусловного source env-файлов нет"
    fi
}

# ---------------------------------------------------------------------
# BR-560 — new_comp compose: политика перезапуска, healthcheck и потолки
#   ресурсов (P-72-08).
# ---------------------------------------------------------------------
check_br560() {
    local br="BR-560"
    local dir="deploy/new_comp/sm-karaoke-system/deploy"
    local f="$dir/docker-compose-app-new-comp.yml"
    if file_has 'restart:[[:space:]]+unless-stopped' "$f"; then
        ok "$br" "$f: restart: unless-stopped"
    else
        bad "$br" "$f: нет restart — упавшее приложение не поднимается само"
    fi
    if file_has 'healthcheck:' "$f" && file_has 'mem_limit:' "$f" && file_has 'cpus:' "$f"; then
        ok "$br" "$f: healthcheck + mem_limit + cpus"
    else
        bad "$br" "$f: нет healthcheck и/или cgroup-потолка"
    fi
    local compose
    for compose in web webvue webvue3; do
        f="$dir/docker-compose-${compose}-new-comp.yml"
        if file_has 'restart:' "$f"; then
            ok "$br" "$f: политика перезапуска задана"
        else
            bad "$br" "$f: нет restart"
        fi
    done
}

CHECKS=(
    check_br039
    check_br040
    check_br041
    check_br043
    check_br534
    check_br539
    check_br542
    check_br543
    check_br544
    check_br545
    check_br546
    check_br554
    check_br555
    check_br556
    check_br557
    check_br042
    check_br045
    check_br046
    check_br047
    check_br535
    check_br536
    check_br537
    check_br538
    check_br547
    check_br548
    check_br550
    check_br559
    check_br560
)

if [[ "$MODE" == "list" ]]; then
    echo "Проверки guard'а tools/check-deploy-hardening.sh:"
    for c in "${CHECKS[@]}"; do
        echo "  ${c#check_br} — $(grep -m1 -A2 "^${c}()" "${BASH_SOURCE[0]}" | grep -oE 'BR-[0-9]+' | head -1)"
    done
    exit 0
fi

if [[ "$MODE" == "self-test" ]]; then
    # Self-test: каждое правило обязано ловить «плохой» пример.
    # Проверяем на синтетических файлах во временном каталоге.
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' EXIT
    mkdir -p "$tmp/deploy/karaoke-db" "$tmp/deploy/prod-single-host" "$tmp/deploy/new_comp/sm-karaoke-system/deploy" \
        "$tmp/deploy/new_comp/sm-karaoke-system" "$tmp/deploy/searxng-settings" "$tmp/tools" \
        "$tmp/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp"
    : >"$tmp/deploy/karaoke-db/02_result_text_and_index.sql"
    : >"$tmp/deploy/karaoke-db/18_author_aliases.sql"
    : >"$tmp/deploy/karaoke-db/15_monetization.sql"
    : >"$tmp/deploy/karaoke-db/46_author_sort_order.sql"
    : >"$tmp/deploy/docker-compose-whisper.yml"
    : >"$tmp/deploy/docker-compose.yml"
    : >"$tmp/deploy/docker-compose-app.yml"
    : >"$tmp/deploy/do.env.example"
    : >"$tmp/deploy/do.sh"
    : >"$tmp/deploy/deploy_web.sh"
    : >"$tmp/deploy/deploy_public.sh"
    : >"$tmp/deploy/prod-single-host/karaoke-db-backup.sh"
    : >"$tmp/deploy/prod-single-host/nginx.conf"
    : >"$tmp/deploy/prod-single-host/docker-compose-web.yml"
    : >"$tmp/deploy/prod-single-host/docker-compose-database.yml"
    : >"$tmp/deploy/prod-single-host/docker-compose-storage.yml"
    : >"$tmp/deploy/prod-single-host/docker-compose-public.yml"
    : >"$tmp/deploy/prod-single-host/prune-images.sh"
    : >"$tmp/deploy/prod-single-host/.env.example"
    : >"$tmp/deploy/prod-single-host/docker-compose-registry.yml"
    : >"$tmp/deploy/tracker-docker-compose.yml"
    : >"$tmp/deploy/tracker-db-backup.service"
    : >"$tmp/deploy/searxng-settings/settings.yml"
    : >"$tmp/deploy/new_comp/sm-karaoke-system/karaoke_watcher.sh"
    : >"$tmp/deploy/new_comp/sm-karaoke-system/deploy/docker-compose-web-new-comp.yml"
    : >"$tmp/deploy/new_comp/sm-karaoke-system/deploy/docker-compose-db-new-comp.yml"
    : >"$tmp/tools/install-tracker.sh"
    : >"$tmp/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Connection.kt"

    REPO="$tmp"
    broken=0
    for c in "${CHECKS[@]}"; do
        before_fail=$fail
        "$c" >/dev/null 2>&1
        if [[ $((fail - before_fail)) -eq 0 ]]; then
            echo "❌ self-test: ${c} не ловит пустое (битое) состояние" >&2
            broken=$((broken + 1))
        fi
    done
    if [[ "$broken" -eq 0 ]]; then
        echo "✅ self-test: все ${#CHECKS[@]} правил ловят пустое (битое) состояние"
        exit 0
    fi
    echo "❌ self-test: ${broken} из ${#CHECKS[@]} правил не ловят битое состояние" >&2
    exit 2
fi

echo "=== Проверки deploy-контура (PKG-S2-08 / OpenProject #371) ==="
echo "Репозиторий: ${REPO}"
echo
for c in "${CHECKS[@]}"; do
    "$c"
done

echo
echo "--- итог: PASS=$pass FAIL=$fail ---"
if [[ "$fail" -ne 0 ]]; then
    echo "Нарушенные записи: ${failed_brs[*]}" >&2
    exit 1
fi
exit 0
