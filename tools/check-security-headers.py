#!/usr/bin/env python3
"""check-security-headers.py — guard: заголовки безопасности в прод-конфиге nginx.

Что проверяет
-------------
`deploy/prod-single-host/80to8897` — host-nginx публичного сайта
`sm-karaoke.ru` (TLS-терминация, 443 + 80). Конфиг разбирается
статически (собственный мини-парсер nginx-синтаксиса), блоки `server{}` и
`location{}` строятся в дерево, и для каждого блока проверяется набор
заголовков безопасности.

Проверки:

1. **server-блок**: объявлены `X-Frame-Options`, `X-Content-Type-Options`,
   `Referrer-Policy` (+ `Strict-Transport-Security` для TLS-сервера), каждый
   с флагом `always` (заголовок должен приходить и на ошибочные ответы).
2. **Значения**: `X-Frame-Options` ∈ {SAMEORIGIN, DENY},
   `X-Content-Type-Options` = `nosniff`, `Referrer-Policy` ∈ известных
   значений, `Strict-Transport-Security` содержит `max-age=<число>`.
3. **Наследование `add_header` (главное!)**: в nginx директивы `add_header`
   наследуются с верхнего уровня **только если в самом блоке нет ни одной
   своей `add_header`**. Поэтому любой `location` со своими `add_header`
   (у нас — `/minio/` и `@minio_404`) теряет серверные заголовки целиком.
   Guard требует, чтобы такой `location` объявил нужные заголовки сам либо
   явно пометил исключение строкой-маркером:
   `# security-headers-exempt: X-Content-Type-Options`
   (маркер виден на ревью, а не спрятан в коде guard'а).
4. **HSTS не на HTTP-контуре**: `Strict-Transport-Security` в server без
   `ssl` — ошибка (браузер игнорирует HSTS, полученный по небезопасному
   каналу, RFC 6797 §7.2).

Запуск:

    python3 tools/check-security-headers.py                # gate (exit 1 при ошибках)
    python3 tools/check-security-headers.py --quiet        # только exit code (pre-commit)
    python3 tools/check-security-headers.py FILE...       # свои файлы вместо дефолтных

Exit codes: 0 — OK, 1 — нарушения, 2 — usage/ошибка разбора.

Источник: BR-558 аудита #336 (порция P-72-06, `specs/336-code-audit/`),
регламент — `knowledge/guidelines/security-headers.md`.
"""

from __future__ import annotations

import argparse
import os
import sys

DEFAULT_FILES = (
    # BR-558 (порция P-72-06): host-nginx публичного сайта, TLS-терминация.
    "deploy/prod-single-host/80to8897",
    # BR-549 (порция P-73-11, OpenProject #393): SPA-контейнер публичного сайта. Раньше
    # guard его не видел — заголовки отсутствовали и в host-nginx, и здесь.
    "deploy/prod-single-host/nginx_karaoke-public.conf",
)

# Заголовки, обязательные для каждого server-блока.
BASE_HEADERS = ("X-Frame-Options", "X-Content-Type-Options", "Referrer-Policy")
# Дополнительно — только для TLS-сервера.
TLS_ONLY_HEADERS = ("Strict-Transport-Security",)

VALID_VALUES = {
    "X-Frame-Options": {"SAMEORIGIN", "DENY"},
    "X-Content-Type-Options": {"nosniff"},
    "Referrer-Policy": {
        "no-referrer",
        "no-referrer-when-downgrade",
        "origin",
        "origin-when-cross-origin",
        "same-origin",
        "strict-origin",
        "strict-origin-when-cross-origin",
        "unsafe-url",
    },
}

EXEMPT_MARKER = "security-headers-exempt:"

# Исключать можно только X-Content-Type-Options: объекты MinIO кладутся без
# Content-Type и отдаются как application/octet-stream — с nosniff браузер
# может отказаться показывать картинку. X-Frame-Options (clickjacking) и
# Referrer-Policy (утечка идентификаторов в Referer) обязательны везде.
EXEMPTABLE = {"X-Content-Type-Options"}


# --------------------------------------------------------------------------
# Мини-парсер конфига nginx
# --------------------------------------------------------------------------


class Directive:
    """Директива (`add_header X-Frame-Options "SAMEORIGIN" always;`)."""

    def __init__(self, name: str, args: list[str], line: int) -> None:
        self.name = name
        self.args = args
        self.line = line

    @property
    def header(self) -> str:
        """Имя заголовка — первый аргумент add_header."""
        return self.args[0] if self.args else ""

    @property
    def value(self) -> str:
        """Значение заголовка — второй аргумент add_header."""
        return self.args[1] if len(self.args) > 1 else ""

    @property
    def always(self) -> bool:
        """Флаг `always` (заголовок шлётся и на ошибочные ответы)."""
        return "always" in self.args[2:]


class Block:
    """Блок (`server { ... }`, `location /minio/ { ... }`)."""

    def __init__(self, name: str, args: list[str], line: int) -> None:
        self.name = name
        self.args = args
        self.line = line
        self.directives: list[Directive] = []
        self.blocks: list["Block"] = []
        self.exempt: set[str] = set()
        self.end_line = line

    @property
    def label(self) -> str:
        if self.name == "location" and self.args:
            return f"location {self.args[0]}"
        return self.name


class ConfigError(Exception):
    pass


def tokenize(text: str) -> list[tuple[str, int]]:
    """Токены nginx: слова, строки в кавычках, `{`, `}`, `;`. + номер строки."""
    tokens: list[tuple[str, int]] = []
    i = 0
    line = 1
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == "\n":
            line += 1
            i += 1
            continue
        if ch in " \t\r":
            i += 1
            continue
        if ch == "#":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if ch in "{};":
            tokens.append((ch, line))
            i += 1
            continue
        if ch in "\"'":
            quote = ch
            i += 1
            buf = []
            while i < n and text[i] != quote:
                if text[i] == "\n":
                    line += 1
                buf.append(text[i])
                i += 1
            i += 1  # закрывающая кавычка
            tokens.append(("".join(buf), line))
            continue
        buf = []
        while i < n and text[i] not in " \t\r\n{};\"'#":
            buf.append(text[i])
            i += 1
        tokens.append(("".join(buf), line))
    return tokens


def parse(text: str) -> Block:
    """Разбирает конфиг в дерево блоков. Корень — искусственный блок 'root'."""
    tokens = tokenize(text)
    root = Block("root", [], 1)
    stack: list[Block] = [root]
    idx = 0
    total = len(tokens)
    while idx < total:
        token, line = tokens[idx]
        if token == "}":
            if len(stack) == 1:
                raise ConfigError(f"лишняя '}}' на строке {line}")
            finished = stack.pop()
            finished.end_line = line
            idx += 1
            continue
        if token in ("{", ";"):
            raise ConfigError(f"неожиданный '{token}' на строке {line}")
        # Собираем директиву: имя + аргументы до ';' (обычная) или '{' (блок).
        name = token
        args: list[str] = []
        idx += 1
        is_block = False
        closed = False
        while idx < total:
            nxt, _nline = tokens[idx]
            if nxt == ";":
                idx += 1
                closed = True
                break
            if nxt == "{":
                block = Block(name, args, line)
                stack[-1].blocks.append(block)
                stack.append(block)
                idx += 1
                is_block = True
                closed = True
                break
            if nxt == "}":
                raise ConfigError(f"директива '{name}' без ';' на строке {line}")
            args.append(nxt)
            idx += 1
        if not closed:
            raise ConfigError(f"незакрытая директива '{name}' (строка {line})")
        if not is_block:
            stack[-1].directives.append(Directive(name, args, line))
    if len(stack) != 1:
        raise ConfigError(f"незакрытый блок '{stack[-1].label}' (строка {stack[-1].line})")
    return root


def collect_exemptions(text: str) -> dict[int, dict[str, str]]:
    """Маркеры исключений: номер строки → {имя заголовка: причина}."""
    result: dict[int, dict[str, str]] = {}
    for number, raw in enumerate(text.splitlines(), start=1):
        if EXEMPT_MARKER not in raw:
            continue
        tail = raw.split(EXEMPT_MARKER, 1)[1]
        names: dict[str, str] = {}
        for part in tail.split(","):
            part = part.strip()
            if not part:
                continue
            pieces = part.split(None, 1)
            name = pieces[0].strip('"')
            reason = pieces[1].strip() if len(pieces) > 1 else ""
            names[name] = reason
        result.setdefault(number, {}).update(names)
    return result


def block_exemptions(block: Block, markers: dict[int, dict[str, str]]) -> dict[str, str]:
    """Исключения, объявленные внутри блока (включая вложенные): имя → причина."""
    found: dict[str, str] = {}
    for line in range(block.line, block.end_line + 1):
        found.update(markers.get(line, {}))
    return found


def iter_servers(block: Block):
    """Все server-блоки рекурсивно: полный nginx.conf оборачивает их в http{}."""
    for child in block.blocks:
        if child.name == "server":
            yield child
        yield from iter_servers(child)


def iter_locations(block: Block):
    """Все location-блоки внутри (рекурсивно), включая вложенные в if/limit."""
    for child in block.blocks:
        if child.name == "location":
            yield child
        yield from iter_locations(child)


def add_headers(block: Block) -> list[Directive]:
    """Все add_header директивы блока, включая вложенные (if/limit)."""
    result = [d for d in block.directives if d.name == "add_header"]
    for child in block.blocks:
        result.extend(add_headers(child))
    return result


def is_tls_server(block: Block) -> bool:
    for directive in block.directives:
        if directive.name == "listen" and "ssl" in directive.args:
            return True
    return False


# --------------------------------------------------------------------------
# Проверки
# --------------------------------------------------------------------------


def check_file(path: str) -> list[str]:
    errors: list[str] = []
    try:
        with open(path, "r", encoding="utf-8") as handle:
            text = handle.read()
    except OSError as exc:
        return [f"{path}: не удалось прочитать файл: {exc}"]

    try:
        root = parse(text)
    except ConfigError as exc:
        return [f"{path}: не удалось разобрать конфиг: {exc}"]

    markers = collect_exemptions(text)

    servers = list(iter_servers(root))
    if not servers:
        return [f"{path}: в файле нет ни одного server-блока"]

    for server in servers:
        tls = is_tls_server(server)
        # На уровне server обязателен полный набор (HSTS — только для TLS-сервера).
        server_required = list(BASE_HEADERS) + (list(TLS_ONLY_HEADERS) if tls else [])
        # На уровне location HSTS НЕ требуется: политика HSTS действует на хост
        # целиком (браузер запоминает её по хосту, а не по пути) — её достаточно
        # один раз прислать с любого ответа сайта. Остальные заголовки — про
        # конкретный ресурс, поэтому требуются в каждом location, который
        # перекрывает наследование своими add_header.
        location_required = list(BASE_HEADERS)

        # 1+2: заголовки уровня server.
        own = {d.header: d for d in server.directives if d.name == "add_header"}
        for header in server_required:
            directive = own.get(header)
            if directive is None:
                errors.append(
                    f"{path}:{server.line}: server-блок «{server.label}» не объявляет "
                    f"заголовок безопасности «{header}»"
                )
                continue
            if not directive.always:
                errors.append(
                    f"{path}:{directive.line}: «{header}» объявлен без флага «always» — "
                    "заголовок не попадёт в ошибочные ответы (4xx/5xx)"
                )
            value = directive.value
            if header in VALID_VALUES:
                allowed = {item.upper() for item in VALID_VALUES[header]}
                if value.upper() not in allowed:
                    errors.append(
                        f"{path}:{directive.line}: «{header}: {value}» — недопустимое значение "
                        f"(допустимо: {', '.join(sorted(VALID_VALUES[header]))})"
                    )
            if header == "Strict-Transport-Security" and "max-age=" not in value:
                errors.append(
                    f"{path}:{directive.line}: «Strict-Transport-Security» без max-age= "
                    "(браузер проигнорирует заголовок)"
                )

        # HSTS на HTTP-контуре — ошибка (RFC 6797 §7.2: по небезопасному каналу игнорируется).
        if not tls and "Strict-Transport-Security" in own:
            errors.append(
                f"{path}:{own['Strict-Transport-Security'].line}: "
                "«Strict-Transport-Security» объявлен в server без ssl — по HTTP браузер "
                "его игнорирует (RFC 6797 §7.2), заголовок вводит в заблуждение"
            )

        # 3: наследование add_header в location-ах.
        for location in iter_locations(server):
            headers = add_headers(location)
            if not headers:
                # Наследует весь набор server-блока — нарушений нет.
                continue
            declared = {d.header for d in headers}
            exempt = block_exemptions(location, markers)
            for header, reason in exempt.items():
                if header not in EXEMPTABLE:
                    errors.append(
                        f"{path}:{location.line}: «{header}» исключён в location "
                        f"«{location.label}», но исключать его нельзя — этот заголовок "
                        f"обязателен везде. Допустимы исключения только: "
                        f"{', '.join(sorted(EXEMPTABLE))}"
                    )
                elif not reason:
                    errors.append(
                        f"{path}:{location.line}: исключение «{header}» в location "
                        f"«{location.label}» без причины — укажите её в скобках после "
                        "имени заголовка в маркере"
                    )
            for header in location_required:
                if header in declared:
                    continue
                if header in exempt:
                    continue
                errors.append(
                    f"{path}:{location.line}: location «{location.label}» имеет свои add_header "
                    f"→ не наследует серверные заголовки, но «{header}» не объявлен. "
                    f"Добавьте «add_header {header} ... always;» либо явное исключение "
                    f"«# {EXEMPT_MARKER} {header} (причина)»."
                )
            for directive in headers:
                if directive.header in location_required and not directive.always:
                    errors.append(
                        f"{path}:{directive.line}: «{directive.header}» в location «{location.label}» "
                        "объявлен без флага «always»"
                    )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", nargs="*", help="конфиги nginx для проверки")
    parser.add_argument("--quiet", action="store_true", help="только exit code")
    args = parser.parse_args()

    files = args.files or list(DEFAULT_FILES)
    errors: list[str] = []
    for path in files:
        if not os.path.isfile(path):
            errors.append(f"{path}: файл не найден")
            continue
        errors.extend(check_file(path))

    if errors:
        if not args.quiet:
            print("FAIL: заголовки безопасности в nginx-конфиге:")
            for error in errors:
                print(f"  - {error}")
            print("")
            print("Регламент: knowledge/guidelines/security-headers.md")
            print("Источник: BR-558 аудита #336 (порция P-72-06)")
        return 1

    if not args.quiet:
        for path in files:
            print(f"OK [{path}]: заголовки безопасности на месте")
    return 0


if __name__ == "__main__":
    sys.exit(main())
