"""
Прогоняет align.py по манифесту (или его части, --limit) и сравнивает предсказанные тайминги
слогов с ground truth (реальной, вручную выставленной разметкой) - средняя/медианная абсолютная
ошибка в мс на слог. Это критерий "приемлемо/нет" ПЕРЕД тем, как думать про дообучение (train.py)
или интеграцию в karaoke-app вместо Whisper (см. план фичи).

Раз текст и его слоговая разбивка (syllables.split_text_into_words) у align.py и у ground truth
ОДИНАКОВЫЕ (обе стороны используют один и тот же алгоритм разбивки), количество и порядок слогов
должны совпадать 1:1 - сравниваем по индексу, без fuzzy-сопоставления (в отличие от Whisper-ветки,
где сопоставление с распознанным текстом было необходимо и являлось источником ошибок).

Текст в манифесте может быть дополнен вставками, найденными сверкой с Whisper (см.
WhisperMarkerAligner.reconcileWithGroundTruth в karaoke-app) - их слоги помечены
hasGroundTruth=False и НЕ участвуют в подсчёте ошибки (реального тайминга от человека для них нет).
"""

from __future__ import annotations

import argparse
import statistics
import sys
import traceback

from align import align_syllables
from manifest import load_manifest

# BR-492 (P-69-04): доля строк, на которых выравнивание УПАЛО (а не разошлось число слогов), выше
# которой прогон оценки считается недействительным и завершается кодом 2. Раньше эти два исхода
# сливались в один счётчик, и полностью упавший прогон печатал "Нет данных для оценки" с кодом 0.
MAX_ALIGN_ERROR_RATIO = 0.05


def evaluate(
    manifest_path: str,
    limit: int | None = None,
    model_path: str | None = None,
    include_secondary_voices: bool = False,
) -> None:
    rows = load_manifest(manifest_path)

    if not include_secondary_voices:
        # voice>0 (второй/третий голос и т.п.) делит ОДИН файл вокала с voice=0 - текст одного
        # голоса покрывает лишь часть аудио. with_star=True (см. align.py) помогает частично и
        # нестабильно (проверено на реальных песнях), катастрофические ошибки (десятки секунд)
        # остаются нередки. Пока исключаем из обучения/оценки - решать отдельно, не мешая обычному
        # случаю (voice=0), где baseline уже стабильно хорош (медиана ~60мс).
        before = len(rows)
        rows = [r for r in rows if r.voice == 0]
        print(f"[evaluate] исключено голосов voice>0: {before - len(rows)} (--include-secondary-voices, чтобы включить)")

    if limit:
        rows = rows[:limit]

    print(f"[evaluate] строк в манифесте (после --limit): {len(rows)}", flush=True)

    errors_ms: list[float] = []
    rows_ok = 0
    rows_skipped_no_audio = 0
    rows_skipped_mismatch = 0
    rows_failed_align = 0

    for idx, row in enumerate(rows):
        prefix = f"[evaluate] ({idx + 1}/{len(rows)}) song={row.song_id} voice={row.voice}"

        if not row.audio_exists:
            print(f"{prefix}: пропущено - нет аудио по пути {row.audio_file}", flush=True)
            rows_skipped_no_audio += 1
            continue

        print(f"{prefix}: выравниваю...", flush=True)
        try:
            predicted = align_syllables(row.audio_file, row.text, model_path)
        except Exception as e:
            # BR-492: тип исключения и трессировка обязательны - без них "ошибка align" неотличима
            # от штатного расхождения слоговой разбивки (см. карточку P-69-04).
            print(f"{prefix}: ошибка align - {type(e).__name__}: {e}", flush=True)
            print(traceback.format_exc(), file=sys.stderr, flush=True)
            rows_failed_align += 1
            continue

        if len(predicted) != len(row.syllables):
            print(
                f"{prefix}: расхождение числа слогов "
                f"(предсказано {len(predicted)}, в разметке {len(row.syllables)}) - пропущено",
                flush=True,
            )
            rows_skipped_mismatch += 1
            continue

        rows_ok += 1
        # Слоги вставок (hasGroundTruth=False, см. план фичи "Согласование текста с Whisper") не с
        # чем сравнивать по-настоящему - у них нет реального тайминга от человека, только
        # приблизительный из самого Whisper. Пропускаем именно их, а не всю строку.
        row_errors = [
            abs(pred["start_ms"] - truth.time_ms)
            for pred, truth in zip(predicted, row.syllables)
            if truth.has_ground_truth
        ]
        skipped_syllables = len(row.syllables) - len(row_errors)
        errors_ms.extend(row_errors)
        suffix = f" ({skipped_syllables} без ground truth - вставки)" if skipped_syllables else ""
        if row_errors:
            print(f"{prefix}: ok, слогов {len(row_errors)}{suffix}, "
                  f"средняя ошибка {statistics.mean(row_errors):.0f} мс", flush=True)
        else:
            print(f"{prefix}: ok, но нет слогов с ground truth для сравнения{suffix}", flush=True)

    print(f"\nПесен/голосов обработано: {rows_ok}, пропущено (нет аудио): {rows_skipped_no_audio}, "
          f"пропущено (расхождение числа слогов): {rows_skipped_mismatch}, "
          f"ошибок align: {rows_failed_align}")
    if errors_ms:
        print(f"Ошибка на слог (мс): среднее={statistics.mean(errors_ms):.1f}, "
              f"медиана={statistics.median(errors_ms):.1f}, "
              f"p90={sorted(errors_ms)[int(len(errors_ms) * 0.9)]:.1f}, "
              f"максимум={max(errors_ms):.1f} (всего слогов: {len(errors_ms)})")
    else:
        print("Нет данных для оценки - проверьте путь к манифесту и наличие аудио на диске.")

    # BR-492: прогон, не давший ни одного измерения или потерявший на ошибках align больше
    # MAX_ALIGN_ERROR_RATIO строк, не должен выглядеть успешным (код 0): решение "baseline
    # достаточен / finetuned лучше" принимается по этим числам.
    if not errors_ms:
        print(
            "[evaluate] ни одного измерения не получено - прогон недействителен (код 2)",
            file=sys.stderr,
            flush=True,
        )
        raise SystemExit(2)
    attempted = rows_ok + rows_skipped_mismatch + rows_failed_align
    if attempted and rows_failed_align / attempted > MAX_ALIGN_ERROR_RATIO:
        print(
            f"[evaluate] выравнивание упало на {rows_failed_align} из {attempted} строк "
            f"({rows_failed_align / attempted:.1%}) - больше порога "
            f"{MAX_ALIGN_ERROR_RATIO:.0%}, прогон недействителен (код 2)",
            file=sys.stderr,
            flush=True,
        )
        raise SystemExit(2)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Оценка точности forced-alignment против ground truth манифеста")
    parser.add_argument("--manifest", default="data/manifest.jsonl", help="Путь к manifest.jsonl")
    parser.add_argument("--limit", type=int, default=None, help="Ограничить количество строк (для смоук-теста)")
    parser.add_argument("--model", default=None, help="Путь к дообученному чекпоинту (train.py); без флага - baseline MMS_FA")
    parser.add_argument("--include-secondary-voices", action="store_true",
                         help="Включить voice>0 (по умолчанию исключены - см. комментарий в evaluate())")
    args = parser.parse_args()

    evaluate(args.manifest, args.limit, args.model, args.include_secondary_voices)
