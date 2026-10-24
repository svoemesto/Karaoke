#!/bin/bash
# =====================================================================
# karaoke_watcher.sh — исполняет .sh-файлы, положенные контейнерами в
# общий каталог /sm-karaoke/system/scriptsFromDocker (createScriptForHost,
# karaoke-app/.../Utils.kt).
#
# BR-555 (аудит #336 / P-72-03, OpenProject #371): раньше watcher исполнял
# ЛЮБОЙ .sh из общей с четырьмя контейнерами директории:
#   * дедупликация была закомментирована (файл исполнялся повторно);
#   * `chmod +x "$script"` + запуск шли прямо в общей директории — между
#     проверкой и exec было окно (~1 c), в которое файл можно перезаписать
#     из контейнера (TOCTOU);
#   * не проверялись ни имя файла, ни владелец, ни права.
# Теперь:
#   1) принимается только файл с именем <uuid>.sh — ровно то, что генерирует
#      createScriptForHost (UUID.randomUUID()); всё остальное уходит в карантин
#      (не исполняется и не зацикливается);
#   2) файл атомарно ПЕРЕНОСИТСЯ (mv) в приватный каталог исполнения 700 —
#      перезаписать его из контейнера после этого нельзя;
#   3) проверяются: обычный файл (не symlink), владелец — root или текущий
#      пользователь, нет group/other-write, размер <= 64 КиБ;
#   4) общая директория обязана быть не-writable для group/other, иначе
#      watcher падает с понятной ошибкой (fail-closed), а не исполняет вслепую.
#
# ОСТАЁТСЯ ЗА ВЛАДЕЛЬЦЕМ (см. specs/371-pkg-s2-08/report.md): пока в контейнерах
# процесс — root на bind-mount, права каталога его не останавливают; полное
# решение — отдельный сервисный пользователь для watcher'а и удаление
# bind-mount /sm-karaoke/system из контейнеров, которым он не нужен
# (webvue/webvue3/mlt), либо отказ от исполнения файлов на хосте.
# =====================================================================

set -uo pipefail
umask 077

# Путь к директории с файлами
SCRIPTS_DIR="${SCRIPTS_DIR:-/sm-karaoke/system/scriptsFromDocker}"
# Приватный каталог исполнения: файл сначала переносится сюда, потом запускается.
EXEC_DIR="${EXEC_DIR:-/tmp/karaoke-script-exec}"
# Карантин для файлов, не прошедших проверки (не исполняются, но и не теряются).
QUARANTINE_DIR="${QUARANTINE_DIR:-/tmp/karaoke-script-rejected}"

# Файл для хранения списка уже обработанных файлов (журнал, не дедупликация:
# перенесённый файл физически покидает общую директорию).
PROCESSED_FILE="${PROCESSED_FILE:-/tmp/karaoke_processed_scripts}"

# Имя скрипта: ровно то, что генерирует createScriptForHost (UUID.randomUUID()).
SCRIPT_NAME_RE='^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\.sh$'
MAX_SCRIPT_BYTES=65536

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }
die() {
    log "ERROR: $*"
    exit 1
}

[[ -d "$SCRIPTS_DIR" ]] || die "нет директории $SCRIPTS_DIR (watcher не может работать)"

# Каталог исполнения и карантин — только для текущего пользователя.
for d in "$EXEC_DIR" "$QUARANTINE_DIR"; do
    [[ -d "$d" ]] || mkdir -p "$d" || die "не удалось создать $d"
    chmod 700 "$d" 2>/dev/null || true
    [[ -O "$d" ]] || die "$d не принадлежит текущему пользователю ($(id -un))"
    [[ "$(stat -c '%a' "$d")" == "700" ]] || die "$d должен иметь права 700 (сейчас $(stat -c '%a' "$d"))"
done

# Общая директория не должна быть writable для group/other: иначе любой
# процесс (не только root) может подбросить скрипт. Права не 700, потому что
# контейнер пишет в неё под root и под пользователем-владельцем каталога.
dir_mode="$(stat -c '%a' "$SCRIPTS_DIR")"
if (( 8#$dir_mode & 8#022 )); then
    log "WARN: $SCRIPTS_DIR доступен на запись группе/остальным (права $dir_mode) — снимаю go-w"
    chmod go-w "$SCRIPTS_DIR" 2>/dev/null || true
    dir_mode="$(stat -c '%a' "$SCRIPTS_DIR")"
    (( 8#$dir_mode & 8#022 )) && die "$SCRIPTS_DIR остаётся group/other-writable (права $dir_mode): исполнять файлы из него небезопасно"
fi

touch "$PROCESSED_FILE" 2>/dev/null || log "WARN: не удалось открыть журнал $PROCESSED_FILE"

log "Скрипт запущен. Отслеживаю директорию: $SCRIPTS_DIR (исполнение: $EXEC_DIR)"

# Проверка метаданных файла: обычный файл, владелец root/текущий, без
# group/other-write, размер в пределах лимита.
file_is_trusted() {
    local f="$1"
    local uid mode size
    [[ -f "$f" ]] || { log "REJECT: не обычный файл: $f"; return 1; }
    [[ -L "$f" ]] && { log "REJECT: symlink: $f"; return 1; }
    uid="$(stat -c '%u' "$f")"
    if [[ "$uid" != "0" && "$uid" != "$(id -u)" ]]; then
        log "REJECT: владелец файла uid=$uid (ожидался 0 или $(id -u)): $f"
        return 1
    fi
    mode="$(stat -c '%a' "$f")"
    if (( 8#$mode & 8#022 )); then
        log "REJECT: файл writable для group/other (права $mode): $f"
        return 1
    fi
    size="$(stat -c '%s' "$f")"
    if (( size > MAX_SCRIPT_BYTES )); then
        log "REJECT: файл больше $MAX_SCRIPT_BYTES байт ($size): $f"
        return 1
    fi
    return 0
}

# Бесконечный цикл проверки
while true; do
    # Получаем список всех .sh файлов, отсортированных по имени
    ALL_SCRIPTS="$(find "$SCRIPTS_DIR" -maxdepth 1 -name '*.sh' -type f 2>/dev/null | sort -V)"

    if [[ -n "$ALL_SCRIPTS" ]]; then
        while IFS= read -r script; do
            [[ -z "$script" ]] && continue
            base="$(basename "$script")"

            # 1. Имя должно быть <uuid>.sh — как у createScriptForHost.
            if ! [[ "$base" =~ $SCRIPT_NAME_RE ]]; then
                log "REJECT: имя '$base' не соответствует шаблону <uuid>.sh — в карантин"
                mv -f "$script" "$QUARANTINE_DIR/$base" 2>/dev/null || rm -f "$script"
                continue
            fi

            # 2. Метаданные проверяются ДО переноса...
            if ! file_is_trusted "$script"; then
                mv -f "$script" "$QUARANTINE_DIR/$base" 2>/dev/null || rm -f "$script"
                continue
            fi

            # 3. ...и файл атомарно переносится в приватный каталог исполнения.
            #    После mv общая директория его уже не содержит — перезаписать
            #    из контейнера поздно (окно TOCTOU закрыто).
            exec_path="$EXEC_DIR/$base"
            if ! mv -f "$script" "$exec_path" 2>/dev/null; then
                log "ERROR: не удалось перенести $script в $EXEC_DIR — пропускаю"
                continue
            fi
            # 4. Повторная проверка уже на копии в приватном каталоге.
            if ! file_is_trusted "$exec_path"; then
                mv -f "$exec_path" "$QUARANTINE_DIR/$base" 2>/dev/null || rm -f "$exec_path"
                continue
            fi
            chmod 700 "$exec_path" 2>/dev/null || true

            log "Запускаю: $exec_path (из $base)"
            /bin/bash "$exec_path"
            rc=$?
            if [[ $rc -eq 0 ]]; then
                log "Скрипт выполнен успешно: $base"
            else
                log "Ошибка при выполнении скрипта: $base (код $rc)"
            fi

            rm -f "$exec_path"
            echo "[$(date '+%Y-%m-%d %H:%M:%S')] $base rc=$rc" >> "$PROCESSED_FILE"
        done <<< "$ALL_SCRIPTS"
    fi

    # Ждем 1 секунду перед следующей проверкой
    sleep 1
done
