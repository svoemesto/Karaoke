"""Регресс-тесты «до/после» для BR-491 (аудит #336, порция P-69): словарь CTC-головы привязан к
составу обучающих данных через `vocab.meta.json` с отпечатком, и больше не переиспользуется молча
после смоук-прогона (`--limit`) или прерванного прогона.

Запуск (нужен venv alignment-ml: train.py импортирует torch/transformers):

    cd alignment-ml
    ./.venv/bin/python -m unittest discover -s tests -v

На коде ДО правки падает уже на импорте: `dataset_fingerprint`/`resolve_vocab`/`has_checkpoints`
не существуют - старого поведения (переиспользовать любой существующий vocab.json) больше нет.
"""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import train  # noqa: E402


def chunk(song: str, start_ms: int, end_ms: int, text: str) -> tuple[str, int, int, str]:
    return (f"/data/{song}.flac", start_ms, end_ms, text)


# Смоук-прогон: 2 песни, в тексте нет «ё».
SMOKE = [chunk("a", 0, 1000, "привет"), chunk("b", 1000, 2000, "мир")]
# Полный прогон: тот же набор плюс третья песня с символом «ё».
FULL = [*SMOKE, chunk("c", 0, 1000, "ёлка")]


class VocabBindingTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.out_dir = Path(self._tmp.name)
        self.vocab_path = self.out_dir / "vocab.json"
        self.meta_path = self.out_dir / train.VOCAB_META_NAME

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def _write(self, items, *, meta: bool = True) -> dict[str, int]:
        vocab = train.build_vocab([text for _audio, _start, _end, text in items])
        train.write_json_atomic(self.vocab_path, vocab)
        if meta:
            train.write_json_atomic(self.meta_path, train.dataset_fingerprint(items))
        return vocab

    # --- BR-491: главный сценарий карточки P-69-03 -------------------------------------------

    def test_smoke_then_full_run_without_checkpoints_rebuilds_vocab(self) -> None:
        """--limit 5 записал словарь, затем полный прогон в тот же --output-dir (чекпоинтов нет).

        ДО правки: существующий vocab.json переиспользовался всегда, «ё» из полного набора
        оставалась вне словаря → [UNK] у всех её вхождений, прогон выглядел успешным.
        """
        smoke_vocab = self._write(SMOKE)
        self.assertNotIn("ё", smoke_vocab)

        vocab, source = train.resolve_vocab(self.vocab_path, self.meta_path, FULL, checkpoints_present=False)

        self.assertEqual(source, "rebuild")
        self.assertIn("ё", vocab, "словарь обязан быть перестроен по текущему набору данных")

    def test_mismatch_with_checkpoints_fails_closed(self) -> None:
        """Если рядом есть чекпоинты, перестройка сломала бы их CTC-голову - отказ, а не тихая порча."""
        self._write(SMOKE)
        (self.out_dir / "checkpoint-200").mkdir()

        with self.assertRaises(SystemExit) as ctx:
            train.resolve_vocab(self.vocab_path, self.meta_path, FULL, checkpoints_present=True)
        self.assertIn("--output-dir", str(ctx.exception))

    def test_corrupt_vocab_without_checkpoints_rebuilds(self) -> None:
        self.vocab_path.write_text("{ это не json", encoding="utf-8")
        vocab, source = train.resolve_vocab(self.vocab_path, self.meta_path, FULL, checkpoints_present=False)
        self.assertEqual(source, "rebuild")
        self.assertIn("ё", vocab)

    def test_corrupt_vocab_with_checkpoints_fails_closed(self) -> None:
        self.vocab_path.write_text("{ это не json", encoding="utf-8")
        with self.assertRaises(SystemExit):
            train.resolve_vocab(self.vocab_path, self.meta_path, FULL, checkpoints_present=True)

    # --- Совместимость с --resume ------------------------------------------------------------

    def test_matching_fingerprint_reuses_vocab_without_rewriting(self) -> None:
        self._write(FULL)
        before_bytes = self.vocab_path.read_bytes()
        before_meta = self.meta_path.read_bytes()

        vocab, source = train.resolve_vocab(self.vocab_path, self.meta_path, FULL, checkpoints_present=True)

        self.assertEqual(source, "reuse")
        self.assertIn("ё", vocab)
        self.assertEqual(self.vocab_path.read_bytes(), before_bytes)
        self.assertEqual(self.meta_path.read_bytes(), before_meta)

    def test_legacy_vocab_without_fingerprint_and_with_checkpoints_reuses_unverified(self) -> None:
        """Словарь до BR-491 (метаданных нет) + чекпоинты: перестраивать нельзя, но и «совпало»
        выдавать за проверенное нельзя - отдельный источник reuse-unverified с предупреждением."""
        self._write(SMOKE, meta=False)
        vocab, source = train.resolve_vocab(self.vocab_path, self.meta_path, FULL, checkpoints_present=True)
        self.assertEqual(source, "reuse-unverified")
        self.assertNotIn("ё", vocab)

    def test_legacy_vocab_without_fingerprint_and_without_checkpoints_rebuilds(self) -> None:
        self._write(SMOKE, meta=False)
        vocab, source = train.resolve_vocab(self.vocab_path, self.meta_path, FULL, checkpoints_present=False)
        self.assertEqual(source, "rebuild")
        self.assertIn("ё", vocab)

    # --- Прочие инварианты ------------------------------------------------------------------

    def test_new_output_dir_builds_vocab(self) -> None:
        vocab, source = train.resolve_vocab(self.vocab_path, self.meta_path, FULL, checkpoints_present=False)
        self.assertEqual(source, "new")
        self.assertIn("ё", vocab)

    def test_resolve_does_not_persist_vocab(self) -> None:
        """Словарь пишет main() и только ПОСЛЕ CTC-фильтра/проверки «остались чанки» (BR-491);
        само решение ничего не создаёт в --output-dir."""
        train.resolve_vocab(self.vocab_path, self.meta_path, FULL, checkpoints_present=False)
        self.assertFalse(self.vocab_path.exists(), "resolve_vocab не должен писать vocab.json")
        self.assertFalse(self.meta_path.exists(), "resolve_vocab не должен писать vocab.meta.json")

    def test_fingerprint_ignores_chunk_order_but_not_composition(self) -> None:
        self.assertEqual(train.dataset_fingerprint(FULL), train.dataset_fingerprint(list(reversed(FULL))))
        self.assertNotEqual(train.dataset_fingerprint(SMOKE), train.dataset_fingerprint(FULL))

    def test_has_checkpoints_detects_only_checkpoint_dirs(self) -> None:
        self.assertFalse(train.has_checkpoints(self.out_dir))
        (self.out_dir / "vocab.json").write_text("{}", encoding="utf-8")
        (self.out_dir / "runs").mkdir()
        self.assertFalse(train.has_checkpoints(self.out_dir))
        (self.out_dir / "checkpoint-400").mkdir()
        self.assertTrue(train.has_checkpoints(self.out_dir))


if __name__ == "__main__":
    unittest.main()
