#!/usr/bin/env python3
"""
tools/merge-gate-statuses.py — сбор статус-чеков коммита для merge-гейта.

Зачем отдельный скрипт (KARAOKE-377). Merge-гейт решает «можно ли мержить» по
агрегату `ci-summary`. Раньше сбор статусов был встроен в `tools/merge-pr.sh`, и
полнота выборки проверялась так: собранное число уникальных записей сравнивалось
с `X-Total-Count` из заголовка ответа. Это неверный эталон:

- `X-Total-Count` считает СЫРЫЕ записи журнала статусов — вместе с дублями от
  нескольких прогонов одного коммита (push в ветку + pull_request + перезапуски);
- `/status` (combined status) возвращает ПО ОДНОМУ статусу на контекст.

Поэтому на коммите, где отработали два прогона, получалось `X-Total-Count=37`
при 30 уникальных контекстах — гейт считал выборку неполной и отказывался в
merge даже при зелёном CI (ложная блокировка). А если бы API отдавал только
первые 30 контекстов из 37, та же проверка, наоборот, была бы единственной
страховкой от «красный CI прошёл» — и убрать её нельзя.

Правило здесь: полнота определяется САМИМ ФАКТОМ пагинации (дойти до пустой
страницы), а `X-Total-Count` — только диагностика (сколько сырых записей за
взятом окне). Недообор — это «страницы кончились, лимит не исчерпан, последняя
страница непустая».

Использование:
    merge-gate-statuses.py --dir <каталог со страницами page-NNN.json> \
        [--total <X-Total-Count>] [--more-pages] [--out <файл>]
    merge-gate-statuses.py --self-test

Коды возврата:
    0 — выборка полна (или пагинация дошла до конца), JSON-массив статусов в stdout;
    1 — ошибка разбора;
    2 — usage error;
    3 — выборка неполна (пагинация упёрлась в лимит страниц) — merge не выполняем.
"""

import argparse
import glob
import json
import os
import sys

# Сколько страниц максимум (совпадает с лимитом цикла в merge-pr.sh).
MAX_PAGES = 50


def load_pages(directory):
    """Прочитать страницы page-NNN.json по возрастанию номера."""
    pages = []
    for path in sorted(glob.glob(os.path.join(directory, "page-*.json"))):
        try:
            with open(path, encoding="utf-8") as fh:
                data = json.load(fh)
        except (OSError, ValueError):
            continue
        pages.append((path, data.get("statuses") or []))
    return pages


def collect(directory, more_pages=False):
    """Собрать статусы: по контексту — последний по времени.

    Возвращает (статусы, last_nonempty) — флаг «последняя страница непустая».
    """
    pages = load_pages(directory)
    if not pages:
        return [], False
    by_context = {}
    order = []
    for _, statuses in pages:
        for status in statuses:
            context = status.get("context")
            if context is None:
                continue
            if context not in by_context:
                order.append(context)
                by_context[context] = status
            else:
                # Дубль по контексту (два прогона одного коммита) — берём более
                # свежий по времени; при равном времени — последний по порядку.
                current = by_context[context]
                if str(status.get("updated_at") or "") >= str(current.get("updated_at") or ""):
                    by_context[context] = status
    last_nonempty = bool(pages[-1][1])
    return [by_context[c] for c in order], last_nonempty


def incomplete(pages_count, last_nonempty, more_pages):
    """Неполнота выборки: страницы кончились, но лимит не исчерпан."""
    if not more_pages:
        return False
    return last_nonempty and pages_count >= MAX_PAGES


def run_self_test():
    fixtures = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures", "merge-gate")
    failed = 0

    # 1. Полная выборка: две страницы, 37 разных контекстов.
    #    Последняя страница непустая — это нормально: пагинация останавливается
    #    по достижении X-Total-Count, а не по пустой странице.
    statuses, last_nonempty = collect(os.path.join(fixtures, "full"))
    if len(statuses) == 37 and not incomplete(2, last_nonempty, more_pages=False):
        print("   OK: полная выборка — 37 контекстов собраны со всех страниц")
    else:
        print("   FAIL: полная выборка — получено %d контекстов" % len(statuses))
        failed = 1

    # 2. Дубли прогонов: 37 сырых записей, 30 уникальных контекстов.
    #    Именно этот случай ломал гейт при X-Total-Count=37.
    statuses, _ = collect(os.path.join(fixtures, "dedup"))
    if len(statuses) == 30:
        print("   OK: дубли прогонов схлопнуты — 30 уникальных контекстов из 37 записей")
    else:
        print("   FAIL: дубли прогонов — получено %d контекстов вместо 30" % len(statuses))
        failed = 1

    # 3. Недообор: страницы не кончились, лимит исчерпан.
    statuses, last_nonempty = collect(os.path.join(fixtures, "truncated"))
    if incomplete(MAX_PAGES, last_nonempty, more_pages=True):
        print("   OK: недообор распознан (страницы кончились на лимите)")
    else:
        print("   FAIL: недообор не распознан")
        failed = 1

    # 4. Полная выборка при total больше уникальных контекстов — НЕ ошибка.
    statuses, last_nonempty = collect(os.path.join(fixtures, "dedup"))
    if not incomplete(1, last_nonempty, more_pages=False) and len(statuses) == 30:
        print("   OK: X-Total-Count больше числа контекстов — ошибкой не считается")
    else:
        print("   FAIL: расхождение total/контекстов трактуется как ошибка")
        failed = 1

    return failed


def main(argv):
    parser = argparse.ArgumentParser(description="Сбор статус-чеков для merge-гейта")
    parser.add_argument("--dir", help="каталог со страницами page-NNN.json")
    parser.add_argument("--total", default="", help="X-Total-Count (только диагностика)")
    parser.add_argument("--more-pages", action="store_true", help="пагинация уперлась в лимит")
    parser.add_argument("--out", default="", help="файл для JSON-вывода (иначе stdout)")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)

    if args.self_test:
        return run_self_test()

    if not args.dir:
        print("нужен --dir (каталог со страницами)", file=sys.stderr)
        return 2

    pages = load_pages(args.dir)
    statuses, last_nonempty = collect(args.dir)
    if incomplete(len(pages), last_nonempty, args.more_pages):
        print(
            "INCOMPLETE: выборка статус-чеков неполна (страниц: %d, лимит: %d, "
            "последняя непустая: %s)" % (len(pages), MAX_PAGES, last_nonempty),
            file=sys.stderr,
        )
        return 3

    if args.total:
        print(
            "диагностика: сырых записей в журнале статусов (X-Total-Count) = %s, "
            "уникальных контекстов = %d (дубли прогонов схлопнуты)" % (args.total, len(statuses)),
            file=sys.stderr,
        )

    payload = json.dumps(statuses, ensure_ascii=False)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(payload)
    else:
        sys.stdout.write(payload)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
