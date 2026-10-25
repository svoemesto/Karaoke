#!/usr/bin/env bash
# Продолжение обучения с последнего чекпоинта в --output-dir (BR-497, аудит #336, порция P-69).
# Те же правила, что и в start_trainig.sh: строгий режим, каталог скрипта вместо ~/Karaoke/...,
# явные проверки venv и манифеста, интерпретатор venv по пути.
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

exec ./.venv/bin/python train.py --manifest "$MANIFEST" --resume "$@"
