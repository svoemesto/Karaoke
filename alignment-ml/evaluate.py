"""
Прогоняет align.py по манифесту (или его части, --limit) и сравнивает предсказанные тайминги
слогов с ground truth (реальной, вручную выставленной разметкой) - средняя/медианная абсолютная
ошибка в мс на слог. Это критерий "приемлемо/нет" ПЕРЕД тем, как думать про дообучение (train.py)
или интеграцию в karaoke-app вместо Whisper (см. план фичи).

Раз текст и его слоговая разбивка (syllables.split_text_into_words) у align.py и у ground truth
ОДИНАКОВЫЕ (обе стороны используют один и тот же алгоритм разбивки), количество и порядок слогов
должны совпадать 1:1 - сравниваем по индексу, без fuzzy-сопоставления (в отличие от Whisper-ветки,
где сопоставление с распознанным текстом было необходимо и являлось источником ошибок).
"""

from __future__ import annotations

import argparse
import statistics

from align import align_syllables
from manifest import load_manifest


def evaluate(manifest_path: str, limit: int | None = None, model_path: str | None = None) -> None:
    rows = load_manifest(manifest_path)
    if limit:
        rows = rows[:limit]

    errors_ms: list[float] = []
    rows_ok = 0
    rows_skipped_no_audio = 0
    rows_skipped_mismatch = 0

    for row in rows:
        if not row.audio_exists:
            rows_skipped_no_audio += 1
            continue
        try:
            predicted = align_syllables(row.audio_file, row.text, model_path)
        except Exception as e:
            print(f"[evaluate] song={row.song_id} voice={row.voice}: ошибка align - {e}")
            rows_skipped_mismatch += 1
            continue

        if len(predicted) != len(row.syllables):
            print(
                f"[evaluate] song={row.song_id} voice={row.voice}: расхождение числа слогов "
                f"(предсказано {len(predicted)}, в разметке {len(row.syllables)}) - пропущено"
            )
            rows_skipped_mismatch += 1
            continue

        rows_ok += 1
        for pred, truth in zip(predicted, row.syllables):
            errors_ms.append(abs(pred["start_ms"] - truth.time_ms))

    print(f"\nПесен/голосов обработано: {rows_ok}, пропущено (нет аудио): {rows_skipped_no_audio}, "
          f"пропущено (расхождение разбивки/ошибка): {rows_skipped_mismatch}")
    if errors_ms:
        print(f"Ошибка на слог (мс): среднее={statistics.mean(errors_ms):.1f}, "
              f"медиана={statistics.median(errors_ms):.1f}, "
              f"p90={sorted(errors_ms)[int(len(errors_ms) * 0.9)]:.1f}, "
              f"максимум={max(errors_ms):.1f} (всего слогов: {len(errors_ms)})")
    else:
        print("Нет данных для оценки - проверьте путь к манифесту и наличие аудио на диске.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Оценка точности forced-alignment против ground truth манифеста")
    parser.add_argument("--manifest", default="data/manifest.jsonl", help="Путь к manifest.jsonl")
    parser.add_argument("--limit", type=int, default=None, help="Ограничить количество строк (для смоук-теста)")
    parser.add_argument("--model", default=None, help="Путь к дообученному чекпоинту (train.py); без флага - baseline MMS_FA")
    args = parser.parse_args()

    evaluate(args.manifest, args.limit, args.model)
