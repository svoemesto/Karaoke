#!/usr/bin/env python3
r"""Подсчёт источников Java-стектрейсов в Kotlin-файлах.

Вспомогательный скрипт для `tools/check-log-hygiene.sh` (KARAOKE-387, спека 732).

Зачем отдельный скрипт: прежняя эвристика гварда была регуляркой по одной строке
(`log\.(warn|error)\([^)]*, e\)`) и поэтому:
  * ловила только логгер, названный ровно `log` — а в коде их `monitorLog`,
    `premiumLog`, `consumerLog`, … (то есть НИ ОДНОГО реального источника);
  * пропускала многострочные вызовы и вызовы, у которых в аргументах есть
    скобки (`consumerLog.warn("… {}", Instant.now(), e)`).

Из-за этого baseline был пустым, а стектрейсы в логах шли потоком: инцидент
2026-11-08 — владелец показал лог, полный `at com.svoemesto…` и `Caused by:`.

Как считаем:
  * `printStackTrace` — вызовы в коде (комментарии и KDoc не считаются);
  * `logexc` — вызовы логгера, у которых ПОСЛЕДНИЙ аргумент — переменная
    исключения (`e`, `ex`, `err`, `exception`, `throwable`, `cause`, `t`).
    Имя логгера любое (`*Log`, `*Logger`, `log`, `logger`).

Вывод: `<путь> <printStackTrace> <лог-с-исключением>` — по строке на файл,
только для файлов с ненулевыми счётчиками (совпадает с форматом baseline).
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

# Имя логгера: обычный идентификатор, оканчивающийся на Log/Logger (в любом регистре).
LOG_CALL = re.compile(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*\.\s*(trace|debug|info|warn|error)\s*\(")
LOGGER_NAME = re.compile(r"(?:log|logger)$", re.IGNORECASE)

# Имена переменных-исключений (последний аргумент вызова логгера).
EXCEPTION_VARS = {"e", "ex", "err", "exception", "throwable", "cause", "t"}


def strip_comments(text: str) -> str:
    """Убирает `//`-комментарии, `/* */`-блоки и строковые литералы.

    Строковые литералы убираются, чтобы текст вида «см. log.error(…, e)» в
    сообщении об ошибке не считался источником стектрейса.
    """
    out: list[str] = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if ch == '"':
            # строковый литерал (в т.ч. мультистроковый) — выбрасываем
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == '"':
                    i += 1
                    break
                i += 1
            continue
        if ch == "'":
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == "'":
                    i += 1
                    break
                i += 1
            continue
        if ch == "/" and nxt == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if ch == "/" and nxt == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i += 2
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def split_top_level(args: str) -> list[str]:
    """Делит список аргументов вызова по запятым верхнего уровня."""
    parts: list[str] = []
    depth = 0
    current: list[str] = []
    for ch in args:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(current))
            current = []
            continue
        current.append(ch)
    parts.append("".join(current))
    return [p.strip() for p in parts if p.strip()]


def count_file(path: Path) -> tuple[int, int]:
    src = strip_comments(path.read_text(encoding="utf-8", errors="replace"))
    ps = len(re.findall(r"\.printStackTrace\s*\(", src))
    logexc = 0
    for match in LOG_CALL.finditer(src):
        if not LOGGER_NAME.search(match.group(1)):
            continue
        depth = 1
        i = match.end()
        while i < len(src) and depth > 0:
            if src[i] == "(":
                depth += 1
            elif src[i] == ")":
                depth -= 1
            i += 1
        if depth != 0:
            continue
        args = src[match.end() : i - 1]
        parts = split_top_level(args)
        if not parts:
            continue
        if parts[-1] in EXCEPTION_VARS:
            logexc += 1
    return ps, logexc


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        sys.stderr.write("Использование: log_hygiene_scan.py <каталог|файл>\n")
        return 2
    root = Path(argv[1])
    if root.is_dir():
        paths = sorted(root.rglob("*.kt"))
    elif root.is_file():
        paths = [root]
    else:
        sys.stderr.write(f"НЕ НАЙДЕН {root}\n")
        return 2
    for path in paths:
        ps, logexc = count_file(path)
        if ps or logexc:
            print(f"{path} {ps} {logexc}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
