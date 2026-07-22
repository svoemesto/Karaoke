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
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
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
    return rows
