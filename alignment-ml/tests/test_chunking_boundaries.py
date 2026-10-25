"""Регресс-тесты «до/после» для BR-496 (аудит #336, порция P-69, карточка P-69-08).

Проверяют `chunking.py`: на принудительном резе по `max_chunk_ms` (и на склейке коротких кусков)
padding не добавляется — паузы там нет, и 200/800 мс затягивали в чанк аудио соседнего слова,
которого нет в `text_chunk`; сам рез делается по границе слова, а не посреди слова.

Запуск:

    cd alignment-ml
    ./.venv/bin/python -m unittest tests.test_chunking_boundaries -v

В этом тесте torch не нужен (импортируется только `chunking`/`syllables`/`manifest`), поэтому
подойдёт и системный python3. На коде ДО правки падают
test_forced_cut_does_not_add_padding_and_keeps_words_whole и
test_forced_cut_lands_on_word_boundary.
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from chunking import build_chunks  # noqa: E402
from manifest import Syllable  # noqa: E402


def syllables_at(times_ms: list[int]) -> list[Syllable]:
    return [Syllable(label=f"s{i}", time_ms=t) for i, t in enumerate(times_ms)]


TEXT = "машина машина машина машина"  # 4 слова × 3 слога
UNIFORM_TIMES = [i * 500 for i in range(12)]


class ChunkBoundaryTest(unittest.TestCase):
    # --- BR-496: главный сценарий карточки P-69-08 -------------------------------------------

    def test_forced_cut_does_not_add_padding_and_keeps_words_whole(self) -> None:
        """Непрерывное пение, рез по max_chunk_ms=3500: без padding и без расщепления слова.

        ДО правки: chunks[0] заканчивался на 2500+800=3300 мс, chunks[1] начинался на
        3000-200=2800 мс (перехлёст 500 мс), а «машина» на границе попадала в текст обоих чанков.
        """
        chunks = build_chunks(TEXT, syllables_at(UNIFORM_TIMES), duration_ms=6000, max_chunk_ms=3500)
        self.assertEqual([c.text for c in chunks], ["машина машина", "машина машина"])
        self.assertEqual(chunks[0].end_ms, 2500)
        self.assertEqual(chunks[1].start_ms, 3000)
        self.assertLessEqual(chunks[0].end_ms, chunks[1].start_ms)

    def test_forced_cut_lands_on_word_boundary(self) -> None:
        """Ни одно слово не дублируется между чанками: рез только между словами."""
        chunks = build_chunks(TEXT, syllables_at(UNIFORM_TIMES), duration_ms=6000, max_chunk_ms=3500)
        words = [w for chunk in chunks for w in chunk.text.split()]
        self.assertEqual(words, ["машина"] * 4)

    def test_quiet_boundary_keeps_padding(self) -> None:
        """На настоящей паузе (> silence_threshold_ms) padding остаётся: он целиком в тишине."""
        times = [0, 500, 1000, 7000, 7500, 8000]
        chunks = build_chunks("машина машина", syllables_at(times), duration_ms=9000, silence_threshold_ms=3000)
        self.assertEqual(len(chunks), 2)
        self.assertEqual(chunks[0].end_ms, 1800)  # 1000 + tail_pad 800
        self.assertEqual(chunks[1].start_ms, 6800)  # 7000 - lead_pad 200

    def test_padding_can_be_disabled_explicitly(self) -> None:
        """Явные --lead-pad-ms 0/--tail-pad-ms 0 по-прежнему работают."""
        chunks = build_chunks(
            TEXT,
            syllables_at(UNIFORM_TIMES),
            duration_ms=6000,
            max_chunk_ms=3500,
            lead_pad_ms=0,
            tail_pad_ms=0,
        )
        self.assertEqual(chunks[0].start_ms, 0)
        self.assertEqual(chunks[0].end_ms, 2500)
        self.assertEqual(chunks[1].start_ms, 3000)


if __name__ == "__main__":
    unittest.main()
