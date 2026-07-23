"""Загрузка manifest.jsonl, экспортированного karaoke-app (ExportAlignmentDataset.kt, кнопка
"Экспорт датасета для forced-alignment" на Home). Одна строка - один голос одной песни:
{songId, voice, audioFile, text, syllables: [{label, timeMs, hasGroundTruth}], durationMs}.
audioFile - абсолютный путь на диске админской машины (аудио не копируется, см. план фичи).

hasGroundTruth=False - слог из вставки, найденной сверкой официального текста с Whisper
(WhisperMarkerAligner.reconcileWithGroundTruth) - что-то реально спето, но отсутствовало в
официальном тексте; реального тайминга от человека для таких слогов нет (см. evaluate.py)."""

import json
from dataclasses import dataclass
from pathlib import Path


@dataclass
class Syllable:
    label: str
    time_ms: int
    has_ground_truth: bool = True  # False - вставка Whisper (реального тайминга от человека нет, см. план фичи)


@dataclass
class ManifestRow:
    song_id: int
    voice: int
    audio_file: str
    text: str
    syllables: list[Syllable]
    duration_ms: int

    @property
    def audio_exists(self) -> bool:
        return Path(self.audio_file).exists()


def load_manifest(path: str) -> list[ManifestRow]:
    """Построчный JSONL. Экспорт (ExportAlignmentDataset.kt) может быть прерван (перезапуск
    karaoke-app на середине многочасового прогона) - если это случилось ровно в момент записи
    строки, в файле может остаться битая/склеенная строка. Сам экспортёр уже терпим к такому при
    подсчёте "что уже сделано" (см. alreadyExportedSongIds), но старую битую строку из файла не
    убирает - поэтому читаем терпимо и здесь: пропускаем нечитаемую строку с предупреждением, а не
    роняем весь пайплайн обучения/оценки из-за одной строки из тысяч."""
    rows = []
    skipped = 0
    with open(path, encoding="utf-8") as f:
        for line_no, line in enumerate(f, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                data = json.loads(line)
                rows.append(
                    ManifestRow(
                        song_id=data["songId"],
                        voice=data["voice"],
                        audio_file=data["audioFile"],
                        text=data["text"],
                        syllables=[
                            Syllable(s["label"], s["timeMs"], s.get("hasGroundTruth", True)) for s in data["syllables"]
                        ],
                        duration_ms=data["durationMs"],
                    )
                )
            except (json.JSONDecodeError, KeyError, TypeError) as e:
                skipped += 1
                print(f"manifest.py: пропущена битая строка {line_no} в {path} ({e})")
    if skipped:
        print(f"manifest.py: всего пропущено битых строк: {skipped} из {path} "
              f"(вероятно, экспорт karaoke-app был прерван на середине записи строки - песня, чья "
              f"строка повреждена, не считается уже экспортированной и дозапишется заново при "
              f"следующем запуске экспорта, см. alreadyExportedSongIds в ExportAlignmentDataset.kt)")
    return rows
