#!/usr/bin/env python3
"""
dsh-command-ledger — независимый сборщик следа исполнения команд.

Зачем он нужен рядом с журналом плагина `agent-command-guard`. Плагин пишет
то, что он увидел (вызовы инструмента `bash`); сборщик пишет то, что произошло
на машине, — из источников, которые не зависят от плагина:

  1. journald (user) — полный argv запусков DSH-подпроцессов
     (transient-scope `dsh-subprocess-<pid>-<hex>.scope`). Ограничения:
     retentionjournald ограничен по времени, а каталог журналов читается любым
     локальным пользователем → это ВХОД в ledger, а не канон хранения argv.
  2. `~/.bash_history` — канал владельца; метки времени есть, только если в
     `~/.bashrc` включён `HISTTIMEFORMAT` (иначе строки идут без времени).
  3. Снимок состояния среды (`docker ps`, `docker images`) — база для
     «необъяснённых изменений»: каждое изменение контейнера или образа должно
     иметь объяснение в пунктах 1 или 2.

Ledger: `~/.dsh/ledger/ledger.ndjson` (0600), каталог 0700, ротация при 50 МБ.
Скрипт никогда не падает с ненулевым кодом: сбой сбора не должен ломать таймер.
"""
from __future__ import annotations

import json
import os
import subprocess
import time
from pathlib import Path

LEDGER_DIR = Path(os.environ.get("DSH_LEDGER_DIR", str(Path.home() / ".dsh" / "ledger")))
LEDGER = LEDGER_DIR / "ledger.ndjson"
STATE = LEDGER_DIR / ".bash-history.offset"
MAX_BYTES = 50 * 1024 * 1024


def now() -> str:
    stamp = time.time()
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(stamp)) + f".{int(stamp * 1000) % 1000:03d}Z"


def append(record: dict) -> None:
    """Дописать запись в ledger. Ошибка записи не поднимается наружу."""
    try:
        LEDGER_DIR.mkdir(parents=True, exist_ok=True)
        os.chmod(LEDGER_DIR, 0o700)
        if LEDGER.exists() and LEDGER.stat().st_size > MAX_BYTES:
            os.replace(LEDGER, LEDGER.with_suffix(".ndjson.1"))
        with open(LEDGER, "a", encoding="utf-8") as handle:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")
        os.chmod(LEDGER, 0o600)
    except Exception:
        pass


def collect_journald() -> int:
    """Полный argv запусков DSH-подпроцессов из journald пользователя."""
    try:
        output = subprocess.run(
            ["journalctl", "--user", "--no-pager", "-o", "json", "--since", "-6 min"],
            capture_output=True, text=True, timeout=30, check=False,
        ).stdout
    except Exception:
        return 0
    count = 0
    for line in output.splitlines():
        try:
            entry = json.loads(line)
        except Exception:
            continue
        message = entry.get("MESSAGE") or ""
        if not isinstance(message, str) or "dsh-subprocess-" not in message:
            continue
        append({"ts": now(), "src": "journald", "text": message[:4000]})
        count += 1
    return count


def collect_shell_history() -> int:
    """Новые строки ~/.bash_history с метками времени (если HISTTIMEFORMAT задан)."""
    path = Path.home() / ".bash_history"
    if not path.exists():
        return 0
    try:
        size = path.stat().st_size
        offset = int(STATE.read_text().strip() or "0") if STATE.exists() else 0
        if size <= offset:
            return 0
        with open(path, "rb") as handle:
            handle.seek(offset)
            chunk = handle.read().decode("utf-8", errors="replace")
        STATE.write_text(str(size))
    except Exception:
        return 0
    count = 0
    stamp = None
    for raw in chunk.splitlines():
        if raw.startswith("#") and raw[1:].isdigit():
            stamp = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(int(raw[1:])))
            continue
        if not raw.strip():
            continue
        append({"ts": stamp or now(), "src": "shell-history", "text": raw[:4000]})
        count += 1
    return count


def collect_docker_state() -> int:
    """Снимок состояния контейнеров и образов."""
    def run(args: list[str]) -> list[str]:
        try:
            output = subprocess.run(args, capture_output=True, text=True, timeout=20, check=False).stdout
            return [line for line in output.splitlines() if line.strip()]
        except Exception:
            return []

    containers = run(["docker", "ps", "--format", "{{.Names}}|{{.Image}}|{{.CreatedAt}}|{{.Status}}"])
    images = run(["docker", "images", "--format", "{{.Repository}}:{{.Tag}}|{{.CreatedAt}}"])
    append({"ts": now(), "src": "docker-state", "containers": containers[:200], "images": images[:200]})
    return 1


def main() -> None:
    journald = collect_journald()
    history = collect_shell_history()
    state = collect_docker_state()
    print(f"ledger: journald={journald} shell-history={history} docker-state={state} → {LEDGER}")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:  # таймер не должен падать
        print(f"ledger: сбор не удался: {error}")
