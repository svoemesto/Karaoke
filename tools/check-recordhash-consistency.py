#!/usr/bin/env python3
"""check-recordhash-consistency.py — guard: целостность recordhash-триггеров и sync-таблиц.

Зачем
-----
`recordhash` — md5 от канонизированной строки записи. Sync-движок LOCAL↔SERVER сравнивает
строки ТОЛЬКО по нему (`KaraokeDbTable.getListHashes` → `to.recordhash != from.recordhash`),
поэтому любой дефект формулы имеет один из двух исходов:

* **тихая потеря синхронизации** — поле не входит в md5, изменение не считается расхождением
  и на другую БД не уезжает (BR-534/BR-541);
* **падение записи** — в теле функции-триггера осталось «голое» имя колонки либо функция
  ссылается на колонку, которой нет в таблице; `CREATE FUNCTION` такое не ловит (тело plpgsql
  проверяется только синтаксически), и триггер падает на первой же записи (BR-540, BR-553).

Обе аварии воспроизводились в аудите #336 (волна C, пакет PKG-DB-RECORDHASH, OpenProject #363).

Что проверяется
---------------
1. **NEW-префикс** (`BR-553`). В теле любой триггерной функции (`CREATE [OR REPLACE] FUNCTION …
   RETURNS trigger`) в `deploy/**/*.sql` каждая ссылка на колонку должна быть квалифицирована
   `NEW.` (или `OLD.`). Голое имя в PL/pgSQL передаётся SQL-парсеру как колонка, а у
   триггерной функции нет FROM → `ERROR: column "x" does not exist` при первом срабатывании.

2. **Канонический файл = модель** (`BR-540`, `BR-541`). Для `deploy/recordhash_<table>.sql`:
   * множество колонок md5 совпадает с набором `@KaraokeDbTableField(name = …)` модели
     (кроме помеченных `useInHash = false`) плюс явно перечисленные в `CANONICAL[...]["extras"]`
     денормализованные колонки, которые поддерживает БД-триггер, а не Kotlin-поле;
   * бэкфилл-`UPDATE` в конце файла повторяет формулу функции символ-в-символ (иначе строки
     навсегда останутся со старым хэшем — триггер их не тронет);
   * в файле создаётся триггер именно на эту таблицу.
   Отсюда автоматически ловится и старое имя колонки (`settings_id` вместо `song_id`), и
   пропущенная колонка.

3. **Sync-таблица догоняет основную** (`BR-533`). Все колонки, которые пишет
   `Song.getSqlToInsert(sync = true)` (список `Pair("колонка", …)` в `Song.kt`), обязаны
   существовать в `tbl_songs_sync` — иначе `INSERT INTO tbl_songs_sync` падает на первой песне
   батча (`column "…" of relation "tbl_songs_sync" does not exist`). Колонки `tbl_songs_sync`
   берутся из baseline-дампа `deploy/new_comp/sm-karaoke-system/dumps/karaoke_clear_dump.sql`
   плюс все `ALTER TABLE … tbl_songs_sync ADD COLUMN IF NOT EXISTS` из `deploy/karaoke-db/*.sql`.
   Если дампа в дереве нет — проверка пропускается (SKIP), а не падает.

Запуск
------
    python3 tools/check-recordhash-consistency.py             # gate: exit 1 при нарушениях
    python3 tools/check-recordhash-consistency.py --quiet     # только exit code (pre-commit)
    python3 tools/check-recordhash-consistency.py --root DIR  # другое дерево (тесты/фикстуры)

Exit codes: 0 — OK (возможны SKIP), 1 — нарушения, 2 — usage/ошибка разбора.

Источник: аудит #336, записи BR-540 / BR-541 / BR-553 / BR-533 (`specs/336-code-audit/`),
ТЗ — `specs/336-code-audit/backlog.md` § PKG-DB-RECORDHASH, отчёт —
`specs/363-pkg-db-recordhash/report.md`, регламент — `knowledge/system/infra/sql-migrations.md`
(Решение 3: recordhash-триггер на каждой sync-таблице) и
`knowledge/domains/persistence/domain.md` § «recordhash-колонка».
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# ---------------------------------------------------------------------------------------------
# Слова, которые в md5-выражении не являются колонками: SQL-ключевые слова, имена типов,
# функции и псевдопеременные триггера.
# ---------------------------------------------------------------------------------------------
KEYWORDS = {
    "new",
    "old",
    "null",
    "true",
    "false",
    "and",
    "or",
    "not",
    "case",
    "when",
    "then",
    "else",
    "end",
    "is",
    "distinct",
    "from",
    "cast",
    "as",
    "interval",
    "at",
    "time",
    "zone",
    "current_timestamp",
    "current_date",
    "localtime",
}
FUNCTIONS = {
    "md5",
    "coalesce",
    "nullif",
    "concat",
    "concat_ws",
    "greatest",
    "least",
    "lower",
    "upper",
    "btrim",
    "trim",
    "ltrim",
    "rtrim",
    "length",
    "to_char",
    "date_trunc",
    "now",
    "round",
    "abs",
    "replace",
}
TYPES = {
    "text",
    "varchar",
    "char",
    "bpchar",
    "integer",
    "int",
    "int2",
    "int4",
    "int8",
    "bigint",
    "smallint",
    "boolean",
    "bool",
    "numeric",
    "decimal",
    "double",
    "precision",
    "real",
    "float",
    "date",
    "timestamp",
    "timestamptz",
    "json",
    "jsonb",
    "uuid",
}
ALLOWED_WORDS = KEYWORDS | FUNCTIONS | TYPES

# Канонические файлы: что именно они обязаны покрывать.
CANONICAL = {
    "deploy/recordhash_authors.sql": {
        "table": "tbl_authors",
        "model": "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt",
        # Денормализованные счётчики: колонки таблицы, которые ведёт БД-триггер
        # (trg_tbl_songs_update_author_counts, миграция 44), а не Kotlin-поле модели.
        "extras": {"ready_songs_count", "total_songs_count"},
    },
    "deploy/recordhash_processes.sql": {
        "table": "tbl_processes",
        "model": "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt",
        "extras": set(),
        # song_id — новое имя settings_id (миграция 35). Нужно, чтобы проверка «колонка
        # существует» не спотыкалась о baseline-дамп, снятый до переименования.
        "renamed": {"song_id"},
    },
    "deploy/recordhash_pictures.sql": {
        "table": "tbl_pictures",
        "model": "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Pictures.kt",
        "extras": set(),
    },
    # tbl_events: last_update авто-управляется триггером в каждой БД и намеренно вне recordhash
    # (WebEvent.kt: «намеренно НЕ входит в recordhash», аннотация useInHash = false).
    "deploy/recordhash_events.sql": {
        "table": "tbl_events",
        "model": "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/WebEvent.kt",
        "extras": set(),
    },
}

SONG_MODEL = "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt"
SONGS_SYNC_TABLE = "tbl_songs_sync"
BASE_DUMP = "deploy/new_comp/sm-karaoke-system/dumps/karaoke_clear_dump.sql"

FUNC_RE = re.compile(
    r"CREATE\s+(?:OR\s+REPLACE\s+)?FUNCTION\s+([A-Za-z0-9_.]+)\s*\([^)]*\)\s*RETURNS\s+trigger",
    re.IGNORECASE | re.DOTALL,
)
DOLLAR_RE = re.compile(r"\bAS\s+(\$[A-Za-z0-9_]*\$)", re.IGNORECASE)
IDENT_RE = re.compile(r"(?<![A-Za-z0-9_.])([A-Za-z_][A-Za-z0-9_]*)")
STRING_RE = re.compile(r"'(?:[^']|'')*'")

violations: list[str] = []
skips: list[str] = []


def fail(where: str, message: str) -> None:
    violations.append(f"{where}: {message}")


def strip_strings(text: str) -> str:
    """Убрать строковые литералы: их содержимое — не имена колонок."""
    return STRING_RE.sub("''", text)


def balanced_paren(text: str, start: int) -> str:
    """Вернуть содержимое скобок, открывающаяся скобка которых стоит в `text[start] == '('`."""
    assert text[start] == "(", "ожидалась открывающая скобка"
    depth = 0
    for pos in range(start, len(text)):
        char = text[pos]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return text[start + 1 : pos]
    raise ValueError("несбалансированные скобки в md5-выражении")


def md5_expressions(body: str) -> list[str]:
    """Все выражения `recordhash = md5(...)` в теле функции (содержимое скобок md5)."""
    result = []
    for match in re.finditer(r"recordhash\s*=\s*md5\s*\(", body, re.IGNORECASE):
        result.append(balanced_paren(body, match.end() - 1))
    return result


def qualified_columns(expression: str) -> list[str]:
    """Имена колонок, на которые выражение ссылается через NEW.<колонка> (в порядке появления).

    Учитываются ВСЕ ссылки, не только внутри COALESCE(): в существующих файлах встречается и
    `NEW.site_user_id::TEXT ||` без COALESCE — если смотреть только на COALESCE, такая колонка
    выпадет из проверки и «пропуск колонки» останется незамеченным.
    """
    return re.findall(r"\bNEW\.([A-Za-z0-9_]+)", strip_strings(expression))


def normalize(expression: str) -> str:
    """Выражение без пробелов и без префикса NEW. — для сравнения функции и бэкфилла."""
    return re.sub(r"\s+", "", expression).replace("NEW.", "")


def unqualified_columns(expression: str) -> list[str]:
    """Имена, которые в выражении не квалифицированы NEW./OLD. и не являются ключевыми словами."""
    cleaned = strip_strings(expression)
    bad = []
    for match in IDENT_RE.finditer(cleaned):
        word = match.group(1)
        if word.lower() in ALLOWED_WORDS:
            continue
        prefix = cleaned[: match.start()].rstrip()
        if prefix.endswith("NEW.") or prefix.endswith("OLD."):
            continue
        bad.append(word)
    return bad


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def check_new_prefix(root: Path) -> None:
    """Check 1: тело триггерной функции не содержит «голых» имён колонок."""
    sql_files = sorted((root / "deploy").rglob("*.sql"))
    if not sql_files:
        fail("deploy/", "[check 1] в deploy/ не найдено ни одного .sql — проверка не выполнима")
        return
    for path in sql_files:
        text = read_text(path)
        relative = path.relative_to(root).as_posix()
        for match in FUNC_RE.finditer(text):
            function_name = match.group(1)
            tag_match = DOLLAR_RE.search(text, match.end())
            if tag_match is None:
                fail(relative, f"[check 1] у функции {function_name}() не найден dollar-quoted блок тела")
                continue
            tag = tag_match.group(1)
            body_start = tag_match.end()
            body_end = text.find(tag, body_start)
            if body_end < 0:
                fail(relative, f"[check 1] у функции {function_name}() не закрыт {tag} блок тела")
                continue
            body = text[body_start:body_end]
            expressions = md5_expressions(body)
            if not expressions:
                continue  # триггер без md5 (например, только now()) — вне предмета проверки
            line = text.count("\n", 0, match.start()) + 1
            for expression in expressions:
                bad = unqualified_columns(expression)
                if bad:
                    fail(
                        f"{relative}:{line}",
                        f"[check 1] функция {function_name}(): без префикса NEW. "
                        f"ссылки на колонки {sorted(set(bad))} — триггер упадёт на первой записи "
                        f"(ERROR: column \"{sorted(set(bad))[0]}\" does not exist)",
                    )


def model_fields(path: Path) -> set[str]:
    """Колонки, объявленные в модели через @KaraokeDbTableField(name = …), кроме useInHash = false."""
    text = read_text(path)
    fields = set()
    for match in re.finditer(r"@KaraokeDbTableField\(([^)]*)\)", text):
        arguments = match.group(1)
        name = re.search(r'name\s*=\s*"([A-Za-z0-9_]+)"', arguments)
        if name is None:
            continue
        if re.search(r"useInHash\s*=\s*false", arguments):
            continue
        fields.add(name.group(1))
    return fields


def check_canonical(root: Path) -> None:
    """Check 2: канонический файл покрывает модель и совпадает с собственным бэкфиллом."""
    for relative, spec in CANONICAL.items():
        path = root / relative
        if not path.is_file():
            fail(relative, "[check 2] канонический recordhash-файл отсутствует")
            continue
        text = read_text(path)
        model_path = root / spec["model"]
        if not model_path.is_file():
            fail(spec["model"], f"[check 2] не найдена модель для {relative}")
            continue

        functions = list(FUNC_RE.finditer(text))
        if not functions:
            fail(relative, "[check 2] в файле нет CREATE FUNCTION … RETURNS trigger")
            continue
        tag_match = DOLLAR_RE.search(text, functions[0].end())
        if tag_match is None:
            fail(relative, "[check 2] не найден dollar-quoted блок тела функции")
            continue
        tag = tag_match.group(1)
        body = text[tag_match.end() : text.find(tag, tag_match.end())]
        expressions = md5_expressions(body)
        if len(expressions) != 1:
            fail(relative, f"[check 2] ожидалось одно выражение md5 в функции, найдено {len(expressions)}")
            continue
        function_expression = expressions[0]
        function_names = set(qualified_columns(function_expression))

        expected = model_fields(model_path) | spec["extras"]
        missing = sorted(expected - function_names)
        extra = sorted(function_names - expected)
        if missing:
            fail(
                relative,
                f"[check 2] в md5 НЕ входят колонки модели {Path(spec['model']).name}: {missing} — "
                f"их изменения не попадут в diff LOCAL↔SERVER",
            )
        if extra:
            fail(
                relative,
                f"[check 2] в md5 входят колонки, которых нет в модели {Path(spec['model']).name} "
                f"и в списке extras: {extra} — устаревшее имя колонки или дрейф набора колонок",
            )

        # Колонка, на которую ссылается триггер, обязана существовать в таблице: baseline-дамп
        # (каким таблица была до миграций) + ALTER TABLE ... ADD COLUMN из этого же файла +
        # документированные переименования. Именно этот класс дефекта был у BR-541: файл
        # ссылался на NEW.aliases, а колонки в базовой схеме не было — триггер падал на каждой
        # записи автора.
        dump_path = root / BASE_DUMP
        known_columns = None
        if dump_path.is_file():
            known_columns = table_columns(read_text(dump_path), spec["table"])
        if known_columns is not None:
            for match in re.finditer(
                r"ALTER\s+TABLE\s+(?:public\.)?" + re.escape(spec["table"])
                + r"\s+ADD\s+COLUMN\s+(?:IF\s+NOT\s+EXISTS\s+)?([A-Za-z0-9_]+)",
                text,
                re.IGNORECASE,
            ):
                known_columns.add(match.group(1))
            known_columns |= spec.get("renamed", set())
            unknown = sorted(function_names - known_columns)
            if unknown:
                fail(
                    relative,
                    f"[check 2] md5 ссылается на колонки, которых нет в {spec['table']} и которые "
                    f"не добавляет сам файл: {unknown} — триггер упадёт при первой записи",
                )

        # Бэкфилл обязан повторять формулу функции: иначе существующие строки навсегда останутся
        # со старым хэшем (триггер их уже не тронет) и будут вечными «расхождениями» в diff.
        backfill = re.search(
            r"UPDATE\s+(?:public\.)?" + re.escape(spec["table"]) + r"\s+SET\s+recordhash\s*=\s*md5\s*\(",
            text,
            re.IGNORECASE,
        )
        if backfill is None:
            fail(relative, f"[check 2] нет бэкфилла UPDATE {spec['table']} SET recordhash = md5(...)")
        else:
            backfill_expression = balanced_paren(text, backfill.end() - 1)
            if normalize(backfill_expression) != normalize(function_expression):
                fail(
                    relative,
                    "[check 2] формула бэкфилла не совпадает с формулой функции "
                    "(порядок/состав колонок) — существующие строки останутся со старым recordhash",
                )

        trigger = re.search(
            r"CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+([A-Za-z0-9_]+)[\s\S]{0,200}?ON\s+(?:public\.)?"
            + re.escape(spec["table"])
            + r"\b",
            text,
            re.IGNORECASE,
        )
        if trigger is None:
            fail(relative, f"[check 2] нет CREATE TRIGGER … ON {spec['table']}")


def table_columns(dump: str, table: str) -> set[str] | None:
    header = f"CREATE TABLE public.{table} ("
    start = dump.find(header)
    if start < 0:
        return None
    end = dump.find(");", start)
    if end < 0:
        return None
    return set(re.findall(r"^\s{4}([A-Za-z0-9_]+)\s", dump[start:end], re.MULTILINE))


def check_songs_sync_columns(root: Path) -> None:
    """Check 3: каждая колонка INSERT в tbl_songs_sync существует в самой таблице."""
    song_path = root / SONG_MODEL
    dump_path = root / BASE_DUMP
    if not song_path.is_file():
        fail(SONG_MODEL, "[check 3] не найдена модель Song.kt")
        return
    if not dump_path.is_file():
        skips.append(f"[check 3] нет baseline-дампа {BASE_DUMP} — проверка состава колонок пропущена")
        return

    song = read_text(song_path)
    try:
        start = song.index("fun getSqlToInsert(sync: Boolean)")
        end = song.index('return "INSERT INTO tbl_songs', start)
    except ValueError:
        fail(SONG_MODEL, "[check 3] не найден метод getSqlToInsert(sync: Boolean)")
        return
    inserted = re.findall(r'Pair\("([A-Za-z0-9_]+)"', song[start:end])
    if not inserted:
        fail(SONG_MODEL, "[check 3] в getSqlToInsert(sync) не найдено ни одной колонки")
        return

    known = table_columns(read_text(dump_path), SONGS_SYNC_TABLE)
    if known is None:
        skips.append(f"[check 3] в baseline-дампе нет таблицы {SONGS_SYNC_TABLE} — проверка пропущена")
        return
    for migration in sorted((root / "deploy/karaoke-db").glob("*.sql")):
        text = read_text(migration)
        for match in re.finditer(
            r"ALTER\s+TABLE\s+(?:public\.)?" + SONGS_SYNC_TABLE + r"\s+ADD\s+COLUMN\s+(?:IF\s+NOT\s+EXISTS\s+)?([A-Za-z0-9_]+)",
            text,
            re.IGNORECASE,
        ):
            known.add(match.group(1))

    missing = [column for column in inserted if column not in known]
    if missing:
        fail(
            f"{SONG_MODEL} → {SONGS_SYNC_TABLE}",
            f"[check 3] INSERT пишет колонки, которых нет в {SONGS_SYNC_TABLE}: {missing} — "
            f"любой push песни в sync упадёт на первой записи (нет миграции, добавляющей их)",
        )


def main() -> int:
    parser = argparse.ArgumentParser(description="recordhash consistency guard")
    parser.add_argument("--root", default=None, help="корень репозитория (по умолчанию — родитель tools/)")
    parser.add_argument("--quiet", action="store_true", help="печатать только нарушения")
    arguments = parser.parse_args()

    root = Path(arguments.root).resolve() if arguments.root else Path(__file__).resolve().parent.parent
    if not (root / "deploy").is_dir():
        print(f"usage: {Path(__file__).name}: в {root} нет каталога deploy/", file=sys.stderr)
        return 2

    try:
        check_new_prefix(root)
        check_canonical(root)
        check_songs_sync_columns(root)
    except (OSError, ValueError) as error:  # pragma: no cover — защита от битого входа
        print(f"ERROR: {error}", file=sys.stderr)
        return 2

    for skip in skips:
        if not arguments.quiet:
            print(f"SKIP {skip}")

    if violations:
        for violation in violations:
            print(f"FAIL {violation}")
        print(f"\n{len(violations)} нарушение(й) recordhash-контракта (BR-533/540/541/553).")
        return 1

    if not arguments.quiet:
        print(
            "OK recordhash-контракт: NEW-префикс в триггерах, канонические файлы == модели "
            "(+бэкфилл), tbl_songs_sync содержит все колонки INSERT."
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
