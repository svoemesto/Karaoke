#!/usr/bin/env bash
# Guard: в корне репозитория не должно быть файлов-осколков shell-редиректов.
#
# КАК ПОЯВЛЯЕТСЯ. Команда с опечаткой в перенаправлении (`... > re`, `2>os`)
# создаёт файл с именем стандартного модуля Python прямо в корне репозитория.
# Такой файл (~23 МБ) попадал в коммиты пакета PKG-S3-22 (аудит #336, #394) —
# `git add -A` забирает его молча. Ignored-правила .gitignore от этого не спасают:
# `git add -f` обходит их.
#
# ЧТО ДЕЛАЕТ. Проверяет, что в корне нет файлов с именами стандартных модулей
# Python (список ниже) и что таких файлов нет в индексе git.
#
# ИСПОЛЬЗОВАНИЕ: bash tools/check-root-junk.sh [--root DIR]
set -uo pipefail

ROOT="."
while [ $# -gt 0 ]; do
    case "$1" in
        --root) ROOT="$2"; shift 2 ;;
        *) shift ;;
    esac
done

# Имена стандартных модулей Python — именно их получали осколки редиректов.
JUNK_MODULES="argparse collections os re subprocess sys json typing"

found=0
for name in $JUNK_MODULES; do
    if [ -f "$ROOT/$name" ]; then
        echo "ОШИБКА: в корне репозитория лежит файл-осколок «$name» ($ROOT/$name)."
        echo "        Это перенаправление вида '... > $name' — удалите файл и исправьте команду."
        found=1
    fi
done

# Тот же мусор, но уже в индексе (не спасает .gitignore, если сделали git add -f).
if [ -d "$ROOT/.git" ] || [ -f "$ROOT/.git" ]; then
    tracked=$(git -C "$ROOT" ls-files --error-unmatch $JUNK_MODULES 2>/dev/null | head -5)
    if [ -n "$tracked" ]; then
        echo "ОШИБКА: файлы-осколки попали в индекс git:"
        echo "$tracked" | sed 's/^/        /'
        found=1
    fi
fi

if [ "$found" -ne 0 ]; then
    exit 1
fi
echo "OK: в корне репозитория нет файлов-осколков (проверено: $JUNK_MODULES)"
