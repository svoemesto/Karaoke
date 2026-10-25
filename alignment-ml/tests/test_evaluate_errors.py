"""Регресс-тесты «до/после» для BR-492 (аудит #336, порция P-69, карточка P-69-04).

Проверяют `evaluate.py`: ошибка выравнивания больше не сливается со штатным расхождением слоговой
разбивки, печатается тип исключения + трессировка, а прогон без измерений или с долей ошибок align
выше 5 % завершается кодом 2 (раньше — «Нет данных для оценки» и код 0).

Запуск (нужен venv alignment-ml: evaluate.py импортирует align.py → torch):

    cd alignment-ml
    ./.venv/bin/python -m unittest tests.test_evaluate_errors -v

На коде ДО правки падают test_failed_align_reports_type_traceback_and_exit_2,
test_error_ratio_above_threshold_exits_2 и test_mismatch_is_not_counted_as_align_failure.
"""

from __future__ import annotations

import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import evaluate  # noqa: E402

SYLLABLES = ["ма", "ши", "на"]  # split_text_into_words("машина") == 3 слога


def _row(song_id: int, audio: Path) -> dict:
    return {
        "songId": song_id,
        "voice": 0,
        "audioFile": str(audio),
        "text": "машина",
        "syllables": [{"label": label, "timeMs": i * 500, "hasGroundTruth": True} for i, label in enumerate(SYLLABLES)],
        "durationMs": 2000,
    }


def _predicted_ok() -> list[dict]:
    return [{"label": label, "start_ms": i * 500, "end_ms": (i + 1) * 500} for i, label in enumerate(SYLLABLES)]


class EvaluateDiagnosticsTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self._tmp.name)
        self._orig_align = evaluate.align_syllables
        self.calls = 0

    def tearDown(self) -> None:
        evaluate.align_syllables = self._orig_align
        self._tmp.cleanup()

    def _manifest(self, rows: int) -> str:
        path = self.dir / "manifest.jsonl"
        audio = self.dir / "vocals.wav"
        audio.write_bytes(b"")  # evaluate.py проверяет только существование (audio_exists)
        with path.open("w", encoding="utf-8") as f:
            for i in range(rows):
                f.write(json.dumps(_row(i, audio), ensure_ascii=False) + "\n")
        return str(path)

    def _stub(self, results: list) -> None:
        """results[i] - список предсказаний или исключение для i-й строки."""
        queue = list(results)

        def fake_align(path: str, text: str, model_path: str | None):
            self.calls += 1
            result = queue.pop(0)
            if isinstance(result, BaseException):
                raise result
            return result

        evaluate.align_syllables = fake_align

    def _run(self, manifest: str) -> tuple[str, str]:
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            evaluate.evaluate(manifest)
        return out.getvalue(), err.getvalue()

    # --- BR-492: главный сценарий карточки P-69-04 -------------------------------------------

    def test_failed_align_reports_type_traceback_and_exit_2(self) -> None:
        """Выравнивание упало на всех строках: отдельный счётчик, тип+трессировка, код 2.

        ДО правки: «ошибка align - <str>» без трессировки, счётчик общий с расхождением слогов,
        пустой отчёт печатался как успех с кодом 0.
        """
        self._stub([RuntimeError("CUDA out of memory"), RuntimeError("CUDA out of memory")])
        with self.assertRaises(SystemExit) as ctx:
            self._run(self._manifest(2))
        self.assertEqual(ctx.exception.code, 2)

    def test_failed_align_output_is_distinguishable(self) -> None:
        self._stub([RuntimeError("CUDA out of memory"), RuntimeError("CUDA out of memory")])
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            with self.assertRaises(SystemExit):
                evaluate.evaluate(self._manifest(2))
        self.assertIn("ошибок align: 2", out.getvalue())
        self.assertIn("пропущено (расхождение числа слогов): 0", out.getvalue())
        self.assertIn("RuntimeError: CUDA out of memory", out.getvalue())
        self.assertIn("Traceback (most recent call last)", err.getvalue())

    def test_mismatch_is_not_counted_as_align_failure(self) -> None:
        """Расхождение слогов — штатный исход, ошибкой align не считается.

        ДО правки оба исхода копились в rows_skipped_mismatch («расхождение разбивки/ошибка»),
        теперь отчёт обязан печатать «ошибок align: 0» при расхождении в отдельном счётчике.
        """
        self._stub([_predicted_ok(), [{"label": "ма", "start_ms": 0, "end_ms": 100}], _predicted_ok()])
        out, _err = self._run(self._manifest(3))
        self.assertIn("пропущено (расхождение числа слогов): 1", out)
        self.assertIn("ошибок align: 0", out)

    def test_error_ratio_above_threshold_exits_2(self) -> None:
        """2 падения из 20 строк = 10 % > 5 % — прогон недействителен."""
        results = [_predicted_ok() for _ in range(20)]
        results[3] = RuntimeError("boom")
        results[7] = OSError("диск отвалился")
        self._stub(results)
        with self.assertRaises(SystemExit) as ctx:
            self._run(self._manifest(20))
        self.assertEqual(ctx.exception.code, 2)

    def test_error_ratio_at_threshold_does_not_fail(self) -> None:
        """1 падение из 20 = ровно 5 % (не больше порога) — прогон валиден, код 0."""
        results = [_predicted_ok() for _ in range(20)]
        results[0] = RuntimeError("boom")
        self._stub(results)
        out, err = self._run(self._manifest(20))
        self.assertIn("ошибок align: 1", out)
        self.assertNotIn("недействителен", err)


if __name__ == "__main__":
    unittest.main()
