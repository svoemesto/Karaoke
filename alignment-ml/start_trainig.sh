#!/usr/bin/env bash
# Запуск обучения с нуля (BR-497, аудит #336, порция P-69).
#
# Раньше скрипт начинался с `cd ~/Karaoke/alignment-ml`: в другой копии репозитория/под другим
# пользователем `cd` падал, скрипт продолжал работу в текущем каталоге вызывающего, активировал
# чужой (или никакой) venv и запускал `python train.py` системным интерпретатором без torch.
# Теперь: строгий режим, каталог берётся из расположения самого скрипта, venv и манифест
# проверяются явно, интерпретатор venv вызывается по пути.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

if [ ! -x .venv/bin/python ]; then
    echo "нет виртуального окружения: $(pwd)/.venv/bin/python" >&2
    echo "создайте его: python3 -m venv .venv && ./.venv/bin/pip install -r requirements.txt" >&2
    exit 1
fi

MANIFEST="${ALIGN_MANIFEST:-/sm-karaoke/system/alignment-dataset/manifest.jsonl}"
if [ ! -f "$MANIFEST" ]; then
    echo "нет файла манифеста: $MANIFEST" >&2
    echo "экспортируйте датасет в админке либо задайте ALIGN_MANIFEST" >&2
    exit 1
fi

exec ./.venv/bin/python train.py --manifest "$MANIFEST" "$@"
