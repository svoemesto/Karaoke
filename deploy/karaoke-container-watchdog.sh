#!/usr/bin/env bash
# =====================================================================
# karaoke-container-watchdog.sh — сторож «зомби»-контейнеров на прод-хосте
# =====================================================================
#
# ЗАЧЕМ (инцидент 2026-11-18): в 07:11 systemd остановил containerd (окно
# автообновлений), тот поднялся в 07:16, но задачи контейнеров были потеряны.
# Docker остался с устаревшим статусом: `docker ps` показывал `karaoke-db Up 8
# days`, а процесса не существовало (`/proc/<pid>` отсутствовал). База не
# принимала соединения 2.5 часа: `karaoke-web` висел unhealthy, приложение
# получало `DB-CONNECT-001`, публичный каталог не отдавал данные.
#
# Политика `restart: always` здесь НЕ помогает: docker считает контейнер
# работающим и перезапуск не инициирует. Нужен внешний наблюдатель, который
# сверяет статус с реальностью.
#
# ЧТО ДЕЛАЕТ: для каждого контейнера из списка проверяет, что статус `running`
# соответствует живому процессу — `/proc/<pid>` существует и cgroup процесса
# принадлежит именно этому контейнеру (защита от переиспользования PID).
# Расхождение = «зомби» → контейнер поднимается ШТАТНЫМ путём (`deploy/do.sh`),
# а не `docker restart` (AGENTS.md, Pass 374).
#
# Режимы:
#   --check       только показать состояние, ничего не поднимать
#   --apply       обнаружить и поднять (режим таймера)
#   --self-test   изолированная проверка решений на поддельных входных данных
#
# Коды возврата: 0 — всё живо либо восстановлено; 1 — есть невосстановленный
# зомби; 2 — ошибка использования.

set -uo pipefail

MODE="apply"
case "${1:-}" in
    --check) MODE="check" ;;
    --apply | "") MODE="apply" ;;
    --self-test) MODE="self-test" ;;
    --help | -h)
        sed -n '2,30p' "$0"
        exit 0
        ;;
    *)
        echo "использование: $0 [--check|--apply|--self-test]" >&2
        exit 2
        ;;
esac

DEPLOY_DIR="${WATCHDOG_DEPLOY_DIR:-/root/Karaoke/deploy}"
STATE_DIR="${WATCHDOG_STATE_DIR:-/var/lib/karaoke-watchdog}"
COOLDOWN_MIN="${WATCHDOG_COOLDOWN_MIN:-10}"

# Контейнер → штатная команда восстановления. Пустое значение = восстанавливать
# нечем, только сообщить (такой контейнер поднимает владелец).
recover_cmd() {
    case "$1" in
        karaoke-db) echo "start_db" ;;
        karaoke-web) echo "start_web" ;;
        karaoke-public) echo "start_public" ;;
        karaoke-storage) echo "start_storage" ;;
        # У registry штатной команды в do.sh нет — он поднимается своим
        # compose-файлом проекта. Восстановление разрешено владельцем 2026-11-18.
        karaoke-registry) echo "compose_registry" ;;
        *) echo "" ;;
    esac
}

CONTAINERS="${WATCHDOG_CONTAINERS:-karaoke-db karaoke-web karaoke-public karaoke-storage karaoke-registry}"

log() { printf '%s\n' "$*"; }

# Проверка «жив ли процесс контейнера».
# 0 — жив; 1 — зомби.
is_alive() {
    local name="$1" cid="$2"
    local state pid
    state="$(docker inspect -f '{{.State.Status}}' "${name}" 2>/dev/null)" || {
        echo "  ${name}: контейнер не найден"
        return 1
    }
    if [ "${state}" != "running" ]; then
        # Остановленный контейнер — не наша зона: им занимается политика docker.
        echo "  ${name}: статус ${state} (не running) — пропуск"
        return 0
    fi
    # Авторитетная проверка — cgroup: жив ли ХОТЬ ОДИН процесс контейнера.
    # PID из docker ненадёжен в обе стороны: после перезапуска containerd он
    # остаётся старым (инцидент 2026-11-18: PID 623024 не существовал), а сам PID
    # может быть переиспользован чужим процессом. Поэтому сначала сканируем
    # /proc на cgroup этого контейнера и только потом смотрим на PID из docker.
    if [ -n "${cid}" ]; then
        for proc_dir in /proc/[0-9]*; do
            if grep -q "${cid}" "${proc_dir}/cgroup" 2>/dev/null; then
                echo "  ${name}: жив (pid ${proc_dir#/proc/})"
                return 0
            fi
        done
    fi
    pid="$(docker inspect -f '{{.State.Pid}}' "${name}" 2>/dev/null)"
    if [ -z "${pid}" ] || [ "${pid}" = "0" ]; then
        echo "  ${name}: ЗОМБИ (running, процессов контейнера нет, PID=${pid:-пусто})"
    else
        echo "  ${name}: ЗОМБИ (running, но ни одного живого процесса нет; сохранённый PID ${pid})"
    fi
    return 1
}

# Защита от циклов: не чаще одного восстановления на контейнер за COOLDOWN_MIN.
in_cooldown() {
    local name="$1" stamp now stamp_file
    stamp_file="${STATE_DIR}/${name}.last"
    [ -f "${stamp_file}" ] || return 1
    stamp="$(cat "${stamp_file}" 2>/dev/null || echo 0)"
    now="$(date +%s)"
    [ $(( now - stamp )) -lt $(( COOLDOWN_MIN * 60 )) ]
}

mark_recovered() {
    mkdir -p "${STATE_DIR}" 2>/dev/null
    date +%s >"${STATE_DIR}/${1}.last" 2>/dev/null
}

recover() {
    local name="$1" cmd
    cmd="$(recover_cmd "${name}")"
    if [ -z "${cmd}" ]; then
        log "  ${name}: штатной команды восстановления нет — нужно вмешательство владельца"
        return 1
    fi
    if in_cooldown "${name}"; then
        log "  ${name}: восстановление уже выполнялось менее ${COOLDOWN_MIN} мин назад — ждём (защита от цикла)"
        return 1
    fi
    local restart_ok=0
    # Для registry повторяем семантику do.sh: сначала снять контейнер, потом
    # поднять. Одного `up -d` НЕДОСТАТОЧНО: у «зомби» docker считает контейнер
    # работающим, и `up -d` ничего не делает (проверено 2026-11-18 — состояние
    # осталось running со старым PID). `down` снимает устаревшую запись, после
    # чего `up -d` создаёт контейнер заново.
    if [ "${cmd}" = "compose_registry" ]; then
        log "  ${name}: снимаю и поднимаю заново через docker-compose-registry.yml"
        ( cd "${DEPLOY_DIR}" &&
            docker compose -f docker-compose-registry.yml down >/dev/null 2>&1
          cd "${DEPLOY_DIR}" &&
            docker compose -f docker-compose-registry.yml up -d ) >/dev/null 2>&1 && restart_ok=1
    else
        log "  ${name}: поднимаю через do.sh ${cmd}"
        ( cd "${DEPLOY_DIR}" && bash do.sh "${cmd}" ) >/dev/null 2>&1 && restart_ok=1
    fi
    if [ "${restart_ok}" = "1" ]; then
        mark_recovered "${name}"
        sleep 5
        local pid state
        state="$(docker inspect -f '{{.State.Status}}' "${name}" 2>/dev/null)"
        pid="$(docker inspect -f '{{.State.Pid}}' "${name}" 2>/dev/null)"
        if [ "${state}" = "running" ] && [ -d "/proc/${pid}" ]; then
            log "  ${name}: восстановлен (pid ${pid})"
            return 0
        fi
        log "  ${name}: после восстановления состояние ${state} pid=${pid} — требуется вмешательство"
        return 1
    fi
    log "  ${name}: команда восстановления (${cmd}) вернула ошибку"
    return 1
}

# --- self-test ------------------------------------------------------------
if [ "${MODE}" = "self-test" ]; then
    failures=0
    check() { # описание, ожидаемый код, имя, cid
        local desc="$1" want="$2" name="$3" cid="$4" got
        is_alive "${name}" "${cid}" >/dev/null 2>&1
        got=$?
        if [ "${got}" = "${want}" ]; then
            log "OK   ${desc}"
        else
            log "FAIL ${desc} (ожидалось ${want}, получено ${got})"
            failures=$((failures + 1))
        fi
    }
    cid="$(docker inspect -f '{{.Id}}' karaoke-db 2>/dev/null)"
    check "живой контейнер распознаётся как живой" 0 karaoke-db "${cid}"
    check "несуществующий контейнер = проблема" 1 karaoke-db-no-such "${cid}"
    # Подделка: имя реального контейнера, но cgroup сверяется с чужим id —
    # PID существует и не принадлежит «этому» контейнеру.
    check "чужой cgroup трактуется как зомби" 1 karaoke-db "0"$([[ -n "${cid}" ]] && echo "deadbeefdeadbeef")
    if [ -z "$(recover_cmd karaoke-db)" ]; then log "FAIL у karaoke-db нет команды восстановления"; failures=$((failures + 1)); else log "OK   у karaoke-db есть команда восстановления"; fi
    if [ -n "$(recover_cmd karaoke-web)" ]; then log "OK   у karaoke-web есть команда восстановления"; else log "FAIL у karaoke-web нет команды"; failures=$((failures + 1)); fi
    # registry намеренно без команды: поднимается владельцем
    if [ -n "$(recover_cmd karaoke-registry)" ]; then log "OK   у karaoke-registry есть команда восстановления (compose)"; else log "FAIL у karaoke-registry нет команды"; failures=$((failures + 1)); fi
    log "--- итог self-test: провалов ${failures} ---"
    [ "${failures}" -eq 0 ] && exit 0 || exit 1
fi

# --- основной цикл --------------------------------------------------------
log "== сторож контейнеров ($(date '+%Y-%m-%d %H:%M:%S')) режим=${MODE}"
problems=0
for name in ${CONTAINERS}; do
    cid="$(docker inspect -f '{{.Id}}' "${name}" 2>/dev/null)"
    if is_alive "${name}" "${cid:-}"; then
        continue
    fi
    problems=$((problems + 1))
    [ "${MODE}" = "check" ] && continue
    if recover "${name}"; then
        problems=$((problems - 1))
    fi
done

if [ "${problems}" -gt 0 ]; then
    log "== ИТОГ: проблемных контейнеров ${problems} — требуется вмешательство"
    exit 1
fi
log "== ИТОГ: все контейнеры живы"
exit 0
