"""Регресс-тесты «до/после» для BR-495 (аудит #336, порция P-69, карточка P-69-07).

Проверяют `train.py`: нечитаемый чанк больше не подменяется случайным другим (раньше
`except Exception` → `random.randrange(len(self.items))`), а исключается — `__getitem__` возвращает
`None` и запоминает индекс в `bad_indices`, трессировка уходит в stderr, а префильтр
`exclude_unreadable_items` выбрасывает нечитаемые файлы ДО создания DataLoader. Исключения, не
связанные с конкретным файлом (ImportError), не глотаются.

Запуск (нужен venv alignment-ml):

    cd alignment-ml
    ./.venv/bin/python -m unittest tests.test_dataset_exclusion -v

На коде ДО правки падают все тесты: подмены/`bad_indices`/префильтра/фильтра None в коллаторе нет.
"""

from __future__ import annotations

import sys
import tempfile
import unittest
import wave
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import torch  # noqa: E402

import train  # noqa: E402


def write_wav(path: Path, seconds: float = 1.0, sample_rate: int = 16000) -> Path:
    with wave.open(str(path), "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(sample_rate)
        wav_file.writeframes(b"\x00\x00" * int(sample_rate * seconds))
    return path


class _FakeTokenizer:
    def pad(self, features, **kwargs):
        batch = len(features)
        return {
            "input_ids": torch.tensor([[1, 2] for _ in range(batch)]),
            "attention_mask": torch.ones(batch, 2, dtype=torch.long),
        }


class _FakeProcessor:
    def __init__(self):
        self.tokenizer = _FakeTokenizer()

    def pad(self, features, **kwargs):
        return {"input_values": torch.zeros(len(features), 4)}


class DatasetExclusionTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self._tmp.name)
        self.good = write_wav(self.dir / "good.wav")
        self.missing = self.dir / "missing.wav"  # файла нет на диске

    def tearDown(self) -> None:
        self._tmp.cleanup()

    @staticmethod
    def _item(audio: Path, start_ms: int = 0, end_ms: int = 500, text: str = "машина"):
        return (str(audio), start_ms, end_ms, text)

    # --- BR-495: главный сценарий карточки P-69-07 -------------------------------------------

    def test_unreadable_chunk_returns_none_and_is_recorded(self) -> None:
        """Нечитаемый чанк: sentinel None + индекс в bad_indices, без подмены другим примером.

        ДО правки: возвращался другой случайный чанк (`random.randrange`), эпоха получала
        дубликаты, а состав выборки тихо отличался от заявленного.
        """
        ds = train.AlignmentDataset([self._item(self.missing)], processor=None, sample_rate=16000)
        with mock.patch("sys.stderr"):
            result = ds[0]
        self.assertIsNone(result)
        self.assertEqual(ds.bad_indices, {0})

    def test_unreadable_chunk_logs_traceback(self) -> None:
        import contextlib
        import io

        ds = train.AlignmentDataset([self._item(self.missing)], processor=None, sample_rate=16000)
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            ds[0]
        self.assertIn("Traceback (most recent call last)", err.getvalue())
        self.assertIn("BR-495", err.getvalue())

    def test_unrelated_error_is_not_swallowed(self) -> None:
        """ImportError (сломано окружение) пробрасывается, а не превращается в «плохой чанк»."""
        ds = train.AlignmentDataset([self._item(self.good)], processor=None, sample_rate=16000)
        with mock.patch.object(train, "read_audio_segment", side_effect=ImportError("нет torchaudio")):
            with self.assertRaises(ImportError):
                ds[0]
        self.assertEqual(ds.bad_indices, set())

    def test_rebuild_without_bad_drops_indices(self) -> None:
        items = [self._item(self.missing), self._item(self.good)]
        ds = train.AlignmentDataset(items, processor=None, sample_rate=16000)
        with mock.patch("sys.stderr"):
            ds[0]
        rebuilt = ds.rebuild_without_bad()
        self.assertEqual(rebuilt.items, [items[1]])
        self.assertEqual(rebuilt.bad_indices, set())

    def test_prefilter_excludes_missing_and_empty_segments(self) -> None:
        """«Заранее» — до DataLoader: нет файла / интервал за концом файла / пустой фрагмент."""
        items = [
            self._item(self.good, 0, 500),
            self._item(self.missing, 0, 500),
            self._item(self.good, 5000, 6000),  # за концом секундного файла
        ]
        with mock.patch("sys.stderr"):
            kept = train.exclude_unreadable_items(items, "теста")
        self.assertEqual(kept, [items[0]])

    def test_prefilter_keeps_readable_items(self) -> None:
        items = [self._item(self.good, 0, 500), self._item(self.good, 500, 900)]
        self.assertEqual(train.exclude_unreadable_items(items, "теста"), items)

    def test_collator_rejects_batch_of_only_bad_chunks(self) -> None:
        collator = train.DataCollatorCTCWithPadding(processor=None)
        with self.assertRaises(RuntimeError) as ctx:
            collator([None, None])
        self.assertIn("BR-495", str(ctx.exception))

    def test_collator_drops_none_and_keeps_real_example(self) -> None:
        collator = train.DataCollatorCTCWithPadding(_FakeProcessor())
        batch = collator([None, {"input_values": [0.0] * 4, "labels": [1, 2]}])
        self.assertEqual(tuple(batch["input_values"].shape), (1, 4))
        self.assertEqual(tuple(batch["labels"].shape), (1, 2))


if __name__ == "__main__":
    unittest.main()
