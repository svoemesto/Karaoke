#!/usr/bin/env bash
# =====================================================================
# tools/install-agent-command-guard.sh — установка машинной части
# защиты команд AI-агента (плагин `agent-command-guard`).
#
# Что делает:
#   1. Кладёт политику запретов в ~/.dsh/policies/deny-commands.json (0600).
#   2. Ставит независимый сборщик следа в ~/.local/bin/dsh-command-ledger
#      и его systemd user-таймер (каждые 5 минут) — ledger из journald,
#      shell-истории и снимков состояния docker.
#   3. Печатает шаг установки самого плагина в профиль DSH — он выполняется
#      инструментом plugin_manager (требует одобрения: код хоста исполняется
#      вне песочницы рабочего каталога).
#
# Использование:
#   bash tools/install-agent-command-guard.sh            # установить
#   bash tools/install-agent-command-guard.sh --check    # только показать состояние
#   bash tools/install-agent-command-guard.sh --force    # перезаписать политику
#
# Ограничения:
#   - без sudo: всё ставится в каталоги пользователя;
#   - секреты в argv не передаются (токенов здесь нет вовсе);
#   - перезапись политики — только с --force (иначе существующий файл не тронут).
# =====================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLUGIN_DIR="${REPO_ROOT}/tools/dsh-agent-command-guard"
POLICY_SRC="${PLUGIN_DIR}/policy/deny-commands.json"
LEDGER_SRC="${REPO_ROOT}/tools/dsh-command-ledger.py"

DSH_HOME="${DSH_HOME:-$HOME/.dsh}"
POLICY_DIR="${DSH_HOME}/policies"
POLICY_DST="${POLICY_DIR}/deny-commands.json"
BIN_DIR="${HOME}/.local/bin"
LEDGER_BIN="${BIN_DIR}/dsh-command-ledger"
UNIT_DIR="${HOME}/.config/systemd/user"
SERVICE_UNIT="${UNIT_DIR}/dsh-command-ledger.service"
TIMER_UNIT="${UNIT_DIR}/dsh-command-ledger.timer"

MODE="install"
FORCE=0
for arg in "$@"; do
    case "$arg" in
        --check) MODE="check" ;;
        --force) FORCE=1 ;;
        -h|--help) sed -n '2,25p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) echo "ERROR: неизвестный аргумент: $arg" >&2; exit 1 ;;
    esac
done

report() {
    printf '%-34s %s\n' "$1" "$2"
}

echo "== Состояние машинной части защиты команд =="
report "политика (правила)" "$POLICY_DST $([ -f "$POLICY_DST" ] && echo 'есть' || echo 'НЕТ')"
report "сборщик следa" "$LEDGER_BIN $([ -x "$LEDGER_BIN" ] && echo 'есть' || echo 'НЕТ')"
report "таймер сбора" "$TIMER_UNIT $([ -f "$TIMER_UNIT" ] && echo 'есть' || echo 'НЕТ')"

if [ "$MODE" = "check" ]; then
    if command -v systemctl >/dev/null 2>&1; then
        systemctl --user list-timers --no-pager 2>/dev/null | grep -E "dsh-command-ledger" || true
    fi
    echo
    echo "Плагин в профиле DSH: проверяется через plugin_manager list_plugins."
    exit 0
fi

# --- 1. Политика -----------------------------------------------------------
mkdir -p "$POLICY_DIR"
chmod 700 "$POLICY_DIR"
if [ -f "$POLICY_DST" ] && [ "$FORCE" -eq 0 ]; then
    report "политика" "уже есть, не тронута (перезапись — --force)"
else
    install -m 0600 "$POLICY_SRC" "$POLICY_DST"
    report "политика" "установлена: $POLICY_DST"
fi

# --- 2. Сборщик следа и таймер ---------------------------------------------
mkdir -p "$BIN_DIR" "$UNIT_DIR"
install -m 0755 "$LEDGER_SRC" "$LEDGER_BIN"
report "сборщик следa" "установлен: $LEDGER_BIN"

cat > "$SERVICE_UNIT" <<'EOF'
[Unit]
Description=DSH command ledger collector (независимый след исполнения команд)

[Service]
Type=oneshot
ExecStart=%h/.local/bin/dsh-command-ledger
EOF

cat > "$TIMER_UNIT" <<'EOF'
[Unit]
Description=Сбор следа команд агента каждые 5 минут

[Timer]
OnBootSec=2min
OnUnitActiveSec=5min
Unit=dsh-command-ledger.service

[Install]
WantedBy=timers.target
EOF

if command -v systemctl >/dev/null 2>&1; then
    export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
    export DBUS_SESSION_BUS_ADDRESS="${DBUS_SESSION_BUS_ADDRESS:-unix:path=${XDG_RUNTIME_DIR}/bus}"
    if systemctl --user daemon-reload 2>/dev/null && systemctl --user enable --now dsh-command-ledger.timer 2>/dev/null; then
        report "таймер сбора" "включён (каждые 5 минут)"
    else
        report "таймер сбора" "НЕ включён: systemctl --user недоступен (внутри bwrap-песочницы)"
    fi
else
    report "таймер сбора" "systemctl не найден — установите юнит вручную"
fi

"$LEDGER_BIN" 2>&1 | tail -1

# --- 3. Плагин в профиле DSH ----------------------------------------------
cat <<EOF

== Остался шаг вручную (его выполняет агент инструментом plugin_manager) ==

  plugin_manager install_bundle target=${PLUGIN_DIR}

Шаг требует одобрения: код хоста исполняется вне песочницы рабочего каталога.
После изменения кода плагина — remove_bundle + install_bundle: правка файла в
установленном бандле на лету не подхватывается. Файл политики применяется сразу
(перечитывается по mtime).

Датированная история команд (нужна для канала владельца):
  в ~/.bashrc — export HISTTIMEFORMAT='%F %T '   (histappend обычно уже включён)
EOF
