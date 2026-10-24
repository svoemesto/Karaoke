#!/usr/bin/env python3
"""Проверка сборки текста подтверждения из пользовательских данных.

Спека #558 (OpenProject #360, дефект BR-123 из аудита #336). Проверяет три
инварианта админских подтверждений (`webvue3/src/components/Common/CustomConfirm.vue`):

R1 (компонент)
    Общий компонент подтверждения по умолчанию НЕ принимает сырую разметку:
    `header`/`body` выводятся как текст, а `v-html` применяется только при
    явном `params.allowHtml === true`.

R2 (формат)
    Блок `customConfirmParams` объявляет `allowHtml: true` тогда и только тогда,
    когда в его `header`/`body` есть статичная разметка (`<strong>`, `<br>`, `<hr>`).
    Иначе либо оформление подтверждения потеряется (забыт флаг), либо лишняя
    доверенная поверхность (лишний флаг).

R3 (данные)
    В блоке с `allowHtml: true` каждая интерполяция небезопасного выражения
    обязана быть обёрнута в `escapeHtml(...)`. Экранирование выполняется
    «на входе» в HTML-буфер, а не «на выходе» — по правилу проекта
    (`knowledge/domains/integration/components/song-public-dto.md`, прецедент #215).

Безопасными считаются: числовой литерал, обращение к `.length` и выражения,
перечисленные в `config/frontend/confirm-safe-expressions.txt` (явный список с
причинами). Всё остальное — пользовательские данные.

Использование:
    python3 tools/check-confirm-escape.py              # проверка репозитория
    python3 tools/check-confirm-escape.py --report     # подробный отчёт
    python3 tools/check-confirm-escape.py --src DIR    # другой каталог (фикстуры)

Возвращает 0 при отсутствии нарушений, 1 — иначе.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

TAG_RE = re.compile(r"<[a-zA-Z/][^>]*>")
INTERPOLATION_RE = re.compile(r"\$\{([^{}]*)\}")
IDENT_RE = re.compile(r"^[A-Za-z_$][\w$]*$")
NUMERIC_RE = re.compile(r"^[\s\d.+\-*/()]+$")

CONFIRM_COMPONENT = Path("components/Common/CustomConfirm.vue")


# --------------------------------------------------------------------------
# Разбор исходника
# --------------------------------------------------------------------------
def code_mask(src: str) -> list[bool]:
    """Маска: True — символ является кодом, False — часть строки/комментария."""
    mask = [True] * len(src)
    i = 0
    n = len(src)
    while i < n:
        ch = src[i]
        if ch == "/" and i + 1 < n and src[i + 1] == "/":
            while i < n and src[i] != "\n":
                mask[i] = False
                i += 1
        elif ch == "/" and i + 1 < n and src[i + 1] == "*":
            while i < n and not (src[i] == "*" and i + 1 < n and src[i + 1] == "/"):
                mask[i] = False
                i += 1
            for _ in range(2):
                if i < n:
                    mask[i] = False
                    i += 1
        elif ch in "'\"":
            quote = ch
            mask[i] = False
            i += 1
            while i < n:
                if src[i] == "\\":
                    mask[i] = False
                    i += 1
                    if i < n:
                        mask[i] = False
                        i += 1
                    continue
                mask[i] = False
                if src[i] == quote:
                    i += 1
                    break
                i += 1
        elif ch == "`":
            # Шаблонная строка: тело — код (в нём интерполяции), но сами
            # обратные кавычки — не код.
            mask[i] = False
            i += 1
            while i < n:
                if src[i] == "\\":
                    mask[i] = False
                    i += 1
                    if i < n:
                        mask[i] = False
                        i += 1
                    continue
                if src[i] == "`":
                    mask[i] = False
                    i += 1
                    break
                i += 1
        else:
            i += 1
    return mask


def find_object_spans(src: str, mask: list[bool]) -> list[tuple[int, int]]:
    """Диапазоны тела объекта для каждого `customConfirmParams = {`."""
    spans = []
    for m in re.finditer(r"customConfirmParams\s*=\s*\{", src):
        i = m.end()
        depth = 1
        while i < len(src) and depth > 0:
            if mask[i]:
                if src[i] == "{":
                    depth += 1
                elif src[i] == "}":
                    depth -= 1
            i += 1
        spans.append((m.end(), i - 1))
    return spans


def _line_indent(text: str, pos: int) -> int:
    """Отступ строки, в которой находится позиция `pos`."""
    ls = text.rfind("\n", 0, pos) + 1
    i = ls
    while i < len(text) and text[i] == " ":
        i += 1
    return i - ls


def _next_line_indent(text: str, pos: int) -> int:
    """Отступ следующей непустой строки после `pos` (0 — если такой строки нет)."""
    i = pos
    n = len(text)
    while i < n:
        nl = text.find("\n", i)
        if nl == -1:
            return 0
        line_start = nl + 1
        line_end = text.find("\n", line_start)
        if line_end == -1:
            line_end = n
        line = text[line_start:line_end]
        if line.strip():
            return len(line) - len(line.lstrip(" "))
        i = line_end
    return 0


def read_value(body: str, i: int) -> tuple[str, int]:
    """Читает значение свойства: литерал, шаблонная строка (с конкатенацией) или выражение.

    Выражение заканчивается на запятой/закрывающей скобке верхнего уровня либо
    на новой строке с отступом не больше отступа строки начала выражения —
    так граница выражения не «съедает» следующие операторы (нужно для разбора
    локальных переменных).
    """
    n = len(body)
    while i < n and body[i] in " \n":
        i += 1
    if i >= n:
        return "", i
    ch = body[i]
    if ch == "`":
        j = i + 1
        depth = 0
        while j < n:
            if body[j] == "\\":
                j += 2
                continue
            if body[j] == "$" and j + 1 < n and body[j + 1] == "{":
                depth += 1
                j += 2
                continue
            if depth > 0 and body[j] == "}":
                depth -= 1
                j += 1
                continue
            if depth == 0 and body[j] == "`":
                j += 1
                break
            j += 1
        # конкатенация через '+'
        while True:
            k = j
            while k < n and body[k] in " \n":
                k += 1
            if k < n and body[k] == "+":
                k += 1
                while k < n and body[k] in " \n":
                    k += 1
                nxt, j2 = read_value(body, k)
                body = body[:j] + nxt + body[j2:]
                n = len(body)
                j += len(nxt)
                continue
            break
        return body[i:j], j
    if ch in "'\"":
        j = i + 1
        while j < n:
            if body[j] == "\\":
                j += 2
                continue
            if body[j] == ch:
                j += 1
                break
            j += 1
        return body[i:j], j
    j = i
    depth = 0
    base_indent = _line_indent(body, i)
    while j < n:
        c = body[j]
        if c in "([{":
            depth += 1
        elif c in ")]}":
            if depth == 0:
                break
            depth -= 1
        elif c == "," and depth == 0:
            break
        elif c == "\n" and depth == 0:
            if _next_line_indent(body, j) <= base_indent:
                break
        j += 1
    return body[i:j].strip(), j


def top_level_props(body: str, mask: list[bool]) -> list[tuple[str, int, str]]:
    """Свойства объекта верхнего уровня: [(имя, смещение, текст значения)]."""
    props = []
    i = 0
    n = len(body)
    depth = 0
    while i < n:
        if not mask[i]:
            # строка/комментарий — пропускаем, но текст шаблонной строки важен,
            # поэтому читаем его целиком
            if body[i] == "`":
                _, i = read_value(body, i)
                continue
            i += 1
            continue
        ch = body[i]
        if ch in "([{":
            depth += 1
            i += 1
            continue
        if ch in ")]}":
            depth -= 1
            i += 1
            continue
        if depth == 0:
            m = re.match(r"([A-Za-z_$][\w$]*)\s*:\s*", body[i:])
            if m:
                name = m.group(1)
                vstart = i + m.end()
                vtext, vend = read_value(body, vstart)
                props.append((name, vstart, vtext))
                i = vend
                continue
        i += 1
    return props


def enclosing_block(src: str, mask: list[bool], pos: int) -> tuple[int, int]:
    """Границы блока, содержащего позицию `pos`.

    `pos` — позиция открывающей `{` объ��кта `customConfirmParams`. Обратный
    обход начинается с `pos - 1`, то есть ДО фигурной скобки объекта: иначе
    нашёлся бы сам объект, а не содержащий его метод. Вложенные фигурные
    скобки внутри объекта сбалансированы, поэтому прямой обход находит
    закрывающую скобку метода.
    """
    depth = 0
    start = pos
    i = pos - 1
    while i >= 0:
        if mask[i]:
            if src[i] == "}":
                depth += 1
            elif src[i] == "{":
                if depth == 0:
                    start = i + 1
                    break
                depth -= 1
        i -= 1
    depth = 0
    end = len(src)
    i = pos
    while i < len(src):
        if mask[i]:
            if src[i] == "{":
                depth += 1
            elif src[i] == "}":
                if depth == 0:
                    end = i
                    break
                depth -= 1
        i += 1
    return start, end


def local_definitions(src: str, mask: list[bool], bstart: int, bend: int, name: str) -> list[str]:
    """Присваивания локальной переменной `name` внутри блока [bstart, bend)."""
    out = []
    chunk = src[bstart:bend]
    chunk_mask = mask[bstart:bend]
    for m in re.finditer(r"(?<![\w.$])" + re.escape(name) + r"\s*=\s*", chunk):
        # присваивание должно быть в коде, а не внутри строки/комментария
        if not chunk_mask[m.start()]:
            continue
        # пропускаем `x ==`, `x ===`
        if m.end() < len(chunk) and chunk[m.end()] == "=":
            continue
        vtext, _ = read_value(chunk, m.end())
        out.append(vtext)
    return out


# --------------------------------------------------------------------------
# Классификация выражений
# --------------------------------------------------------------------------
class SafeList:
    def __init__(self, path: Path):
        self.entries: dict[str, str] = {}
        if path and path.exists():
            for raw in path.read_text(encoding="utf-8").splitlines():
                line = raw.strip()
                if not line or line.startswith("#"):
                    continue
                expr, _, reason = line.partition("#")
                expr = expr.strip()
                if expr:
                    self.entries[expr] = reason.strip()

    def is_safe(self, expr: str) -> bool:
        e = expr.strip()
        if e in self.entries:
            return True
        if NUMERIC_RE.fullmatch(e):
            return True
        if ".length" in e:
            return True
        return False


def collect_interpolations(value: str) -> list[str]:
    """Интерполяции `${...}` из текста значения (шаблонная строка или конкатенация)."""
    return [m.group(1).strip() for m in INTERPOLATION_RE.finditer(value)]


def identifiers_in(expr: str) -> list[str]:
    """Идентификаторы, на которые ссылается выражение (для разрешения переменных)."""
    out = []
    for m in re.finditer(r"(?<![\w.$])([A-Za-z_$][\w$]*)", expr):
        name = m.group(1)
        if name in ("escapeHtml", "String", "Number", "Boolean"):
            continue
        out.append(name)
    return out


# --------------------------------------------------------------------------
# Основной проход
# --------------------------------------------------------------------------
class Finding:
    def __init__(self, file: str, line: int, rule: str, detail: str):
        self.file = file
        self.line = line
        self.rule = rule
        self.detail = detail

    def as_dict(self) -> dict:
        return {
            "file": self.file,
            "line": self.line,
            "rule": self.rule,
            "detail": self.detail,
        }

    def __str__(self) -> str:
        return f"{self.file}:{self.line} [{self.rule}] {self.detail}"


def resolve_value(
    value: str,
    src: str,
    mask: list[bool],
    bstart: int,
    bend: int,
    seen: set[str],
) -> tuple[list[str], str]:
    """Возвращает (интерполяции, текст со всей развёрнутой разметкой).

    Интерполяция вида `${localVar}` разрешается в определение `localVar`:
    вместо самого `${localVar}` в результат попадают интерполяции его
    определения. Так «счётчик, посчитанный в промежуточной переменной», не
    считается пользовательскими данными, а «имя автора, спрятанное в
    `scope`», — считается.
    """
    interps: list[str] = []
    text = value
    stripped = value.strip()

    def expand(name: str, next_seen: set[str]) -> list[str]:
        out: list[str] = []
        for dtext in local_definitions(src, mask, bstart, bend, name):
            sub_i, sub_t = resolve_value(dtext, src, mask, bstart, bend, next_seen)
            out.extend(sub_i)
            nonlocal_text.append(sub_t)
        return out

    nonlocal_text: list[str] = []

    # значение само является идентификатором (например, `body: body`)
    if IDENT_RE.fullmatch(stripped) and stripped not in seen:
        defs = local_definitions(src, mask, bstart, bend, stripped)
        if defs:
            interps.extend(expand(stripped, seen | {stripped}))
            return interps, value + "\n" + "\n".join(nonlocal_text)

    # значение — выражение с идентификаторами (например, `lines.join('<br>')`):
    # разворачиваем локальные переменные, на которые оно ссылается
    if not (value.startswith("`") or value.startswith("'") or value.startswith('"')):
        for name in identifiers_in(value):
            if name in seen or not IDENT_RE.fullmatch(name):
                continue
            defs = local_definitions(src, mask, bstart, bend, name)
            if defs:
                interps.extend(expand(name, seen | {name}))

    # интерполяции внутри шаблонных строк
    for m in INTERPOLATION_RE.finditer(value):
        expr = m.group(1).strip()
        if IDENT_RE.fullmatch(expr):
            if expr in seen:
                # уже развёрнуто выше по стеку — повторно не раскрываем
                # (и не считаем нарушением: это рекурсивная ссылка)
                continue
            defs = local_definitions(src, mask, bstart, bend, expr)
            if defs:
                interps.extend(expand(expr, seen | {expr}))
                continue
        interps.append(expr)

    return interps, value + "\n" + "\n".join(nonlocal_text)


def scan_file(path: Path, rel: str, safe: SafeList) -> tuple[list[Finding], dict]:
    src = path.read_text(encoding="utf-8")
    mask = code_mask(src)
    findings: list[Finding] = []
    stats = {"blocks": 0, "html_blocks": 0, "interpolations": 0}

    for bstart, bend in find_object_spans(src, mask):
        stats["blocks"] += 1
        body = src[bstart:bend]
        body_mask = mask[bstart:bend]
        props = {name: (off, text) for name, off, text in top_level_props(body, body_mask)}
        has_allow_html = "allowHtml" in props
        allow_html_value = props.get("allowHtml", (0, ""))[1].strip()
        if has_allow_html and allow_html_value not in ("true", "True"):
            findings.append(
                Finding(
                    rel,
                    src[: bstart + props["allowHtml"][0]].count("\n") + 1,
                    "R2",
                    f"allowHtml объявлен, но не равен true: {allow_html_value!r} "
                    "(HTML-режим включается только явно)",
                )
            )
            has_allow_html = False

        # границы объемлющего блока (метода) — для разрешения локальных переменных
        mstart, mend = enclosing_block(src, mask, bstart - 1)

        interps: list[str] = []
        full_text = ""
        for prop in ("header", "body"):
            if prop not in props:
                continue
            off, text = props[prop]
            i, t = resolve_value(text, src, mask, mstart, mend, set())
            interps.extend(i)
            full_text += "\n" + t

        markup = bool(TAG_RE.search(full_text))
        stats["interpolations"] += len(interps)

        if markup and not has_allow_html:
            findings.append(
                Finding(
                    rel,
                    src[:bstart].count("\n") + 1,
                    "R2",
                    "в header/body есть статичная разметка, но не объявлен allowHtml: true — "
                    "оформление подтверждения будет потеряно",
                )
            )
        elif has_allow_html and not markup:
            findings.append(
                Finding(
                    rel,
                    src[:bstart].count("\n") + 1,
                    "R2",
                    "объявлен allowHtml: true, но в header/body нет разметки — "
                    "лишняя доверенная поверхность (безопаснее текстовый режим)",
                )
            )

        if not markup:
            # Текстовый режим: значение выводится как текст (textContent) —
            # разметка в нём не выполняется, а экранирование дало бы двойное
            # отображение символов (`&` → `&amp;` на экране). Экранирование
            # здесь не требуется и не применяется.
            continue

        stats["html_blocks"] += 1
        for expr in interps:
            if "escapeHtml" in expr:
                continue
            if safe.is_safe(expr):
                continue
            findings.append(
                Finding(
                    rel,
                    src[:bstart].count("\n") + 1,
                    "R3",
                    f"пользовательское значение попадает в текст подтверждения без экранирования: "
                    f"${{{expr}}} — оберните в escapeHtml(...)",
                )
            )

    return findings, stats


def check_component(src_root: Path, rel_root: str) -> list[Finding]:
    findings = []
    comp = src_root / CONFIRM_COMPONENT
    if not comp.exists():
        return [Finding(rel_root, 0, "R1", f"не найден компонент {CONFIRM_COMPONENT}")]
    lines = comp.read_text(encoding="utf-8").splitlines()
    rel = f"{rel_root}/{CONFIRM_COMPONENT.as_posix()}"
    for prop in ("header", "body"):
        if not any(f'v-text="params.{prop}"' in ln for ln in lines):
            findings.append(
                Finding(rel, 0, "R1", f"нет текстового режима: v-text=\"params.{prop}\" не найден")
            )
        for idx, ln in enumerate(lines):
            if f'v-html="params.{prop}"' in ln:
                window = "\n".join(lines[max(0, idx - 3) : idx + 1])
                if "params.allowHtml" not in window:
                    findings.append(
                        Finding(
                            rel,
                            idx + 1,
                            "R1",
                            f'v-html="params.{prop}" без явного условия params.allowHtml — '
                            "сырая разметка принимается по умолчанию",
                        )
                    )
    return findings


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--src",
        default=str(REPO_ROOT / "webvue3" / "src"),
        help="каталог с исходниками webvue3 (по умолчанию — репозиторий)",
    )
    parser.add_argument(
        "--safe-list",
        default=str(REPO_ROOT / "config" / "frontend" / "confirm-safe-expressions.txt"),
        help="файл со списком безопасных выражений",
    )
    parser.add_argument("--report", action="store_true", help="подробный отчёт по всем блокам")
    parser.add_argument("--json", action="store_true", help="вывод в JSON")
    args = parser.parse_args()

    src_root = Path(args.src).resolve()
    rel_root = str(src_root.relative_to(REPO_ROOT)) if str(src_root).startswith(str(REPO_ROOT)) else str(src_root)
    safe = SafeList(Path(args.safe_list))

    findings: list[Finding] = []
    findings.extend(check_component(src_root, rel_root))

    stats = {"blocks": 0, "html_blocks": 0, "interpolations": 0}
    files = sorted(list(src_root.rglob("*.vue")) + list(src_root.rglob("*.js")))
    for path in files:
        rel = str(path.relative_to(src_root))
        f, s = scan_file(path, rel, safe)
        findings.extend(f)
        for k in stats:
            stats[k] += s[k]

    # одна и та же находка может быть получена несколькими путями разбора
    # (одно и то же выражение встречается в разных ветвях сборки текста) —
    # оставляем по одной.
    deduped: list[Finding] = []
    seen_keys = set()
    for f in findings:
        key = (f.file, f.line, f.rule, f.detail)
        if key in seen_keys:
            continue
        seen_keys.add(key)
        deduped.append(f)
    findings = deduped

    if args.json:
        print(
            json.dumps(
                {
                    "stats": stats,
                    "safe_expressions": sorted(safe.entries),
                    "violations": [f.as_dict() for f in findings],
                },
                ensure_ascii=False,
                indent=2,
            )
        )
        return 1 if findings else 0

    print("=" * 78)
    print("Проверка сборки текста подтверждения из пользовательских данных")
    print("(спека #558 / OpenProject #360 / BR-123)")
    print("=" * 78)
    print(f"Каталог: {rel_root}")
    print(f"Блоков подтверждений: {stats['blocks']} (из них с HTML-режимом: {stats['html_blocks']})")
    print(f"Точек интерполяции в header/body: {stats['interpolations']}")
    print(f"Безопасных выражений в списке: {len(safe.entries)}")
    print(f"Нарушений: {len(findings)}")
    if args.report:
        print("\nСостав безопасного списка:")
        for expr, reason in sorted(safe.entries.items()):
            print(f"  {expr}  — {reason}")
    if findings:
        print("\nНАРУШЕНИЯ:")
        for f in findings:
            print(f"  - {f}")
        print(
            "\nFAIL: пользовательские данные обязаны экранироваться при сборке текста "
            "подтверждения; HTML-режим включается только явно (allowHtml: true)."
        )
        return 1
    print("\nPASS: все пользовательские значения экранированы при сборке текста подтверждения.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
