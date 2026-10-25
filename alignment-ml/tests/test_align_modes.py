"""Регресс-тесты «до/после» для BR-493 и BR-494 (аудит #336, порция P-69, карточки P-69-05/06).

BR-493: откат на режим без звёздного токена больше не тихий — флаг `star_token` есть в состоянии
модели и в ответе `/align`, причина с трессировкой уходит в stderr, а сбой загрузки, не связанный
с отсутствием `with_star` в torchaudio, пробрасывается (5xx), а не приводит к тихой деградации.

BR-494: отсутствие `uroman` останавливает выравнивание (`AlignmentSetupError` → 503 в `/align`), а
доля слов, ушедших в заглушку, выше 10 % отклоняет запрос вместо выравнивания «a a a …».

Запуск (нужен venv alignment-ml):

    cd alignment-ml
    ./.venv/bin/python -m unittest tests.test_align_modes -v

На коде ДО правки падают все тесты, кроме test_star_token_true_on_success: в состоянии нет
`star_token`, откат происходит при любом исключении, а `_romanize`/`_sanitize_for_vocab` не бросают.
"""

from __future__ import annotations

import contextlib
import io
import sys
import tempfile
import unittest
import wave
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import httpx  # noqa: E402
import torchaudio  # noqa: E402

import align  # noqa: E402
import serve  # noqa: E402

FAKE_VOCAB = {"|": 0, "a": 1, "b": 2}


class _FakeModel:
    def eval(self):
        return self


class _FakeBundle:
    """Минимальная заглушка torchaudio.pipelines.MMS_FA: словарь, токенизатор, модель/алайнер."""

    sample_rate = 16000

    def __init__(self, star_error: BaseException | None = None) -> None:
        self.star_error = star_error
        self.star_requests = 0
        self.plain_requests = 0

    def get_tokenizer(self):
        return object()

    def get_aligner(self):
        return object()

    def get_dict(self, star=None):
        vocab = dict(FAKE_VOCAB)
        if star:
            vocab[star] = 3
        return vocab

    def get_model(self, with_star: bool = False):
        if with_star:
            self.star_requests += 1
            if self.star_error is not None:
                raise self.star_error
        else:
            self.plain_requests += 1
        return _FakeModel()


def make_wav(seconds: float = 0.5, sample_rate: int = 16000) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(sample_rate)
        wav_file.writeframes(b"\x00\x00" * int(sample_rate * seconds))
    return buf.getvalue()


class StarTokenModeTest(unittest.TestCase):
    def setUp(self) -> None:
        self._cache = dict(align._model_cache)
        align._model_cache.clear()
        self._orig_bundle = torchaudio.pipelines.MMS_FA
        self._orig_align = serve.align_syllables
        self._orig_star = getattr(serve, "star_token_enabled", None)

    def tearDown(self) -> None:
        torchaudio.pipelines.MMS_FA = self._orig_bundle
        align._model_cache.clear()
        align._model_cache.update(self._cache)
        serve.align_syllables = self._orig_align
        if self._orig_star is not None:
            serve.star_token_enabled = self._orig_star
        else:
            delattr(serve, "star_token_enabled")

    def _install(self, bundle: _FakeBundle) -> _FakeBundle:
        torchaudio.pipelines.MMS_FA = bundle
        return bundle

    # --- BR-493: главный сценарий карточки P-69-05 -------------------------------------------

    def test_star_token_true_on_success(self) -> None:
        self._install(_FakeBundle())
        state = align._load_model(None)
        self.assertIs(state["star_token"], True)
        self.assertEqual(state["star_id"], 3)
        self.assertIsNone(state["star_fallback_reason"])
        self.assertTrue(align.star_token_enabled(None))

    def test_typeerror_fallback_is_visible_in_state(self) -> None:
        """torchaudio без with_star: откат остаётся, но с флагом и причиной (а не молча)."""
        bundle = self._install(_FakeBundle(TypeError("get_model() got an unexpected keyword argument 'with_star'")))
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            state = align._load_model(None)
        self.assertIs(state["star_token"], False)
        self.assertIn("TypeError", state["star_fallback_reason"])
        self.assertIsNone(state["star_id"])
        self.assertIn("Traceback (most recent call last)", err.getvalue())
        self.assertIn("with_star=True недоступен", err.getvalue())
        self.assertEqual(bundle.plain_requests, 1)

    def test_environment_failure_is_not_swallowed(self) -> None:
        """Оборванная загрузка весов/OOM — отказ, а не второй тихий проход без звёздного токена."""
        bundle = self._install(_FakeBundle(RuntimeError("download failed: connection reset")))
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            with self.assertRaises(RuntimeError):
                align._load_model(None)
        self.assertEqual(bundle.plain_requests, 0, "модель не должна грузиться второй раз после сбоя")
        self.assertNotIn(None, align._model_cache)
        self.assertIn("Traceback (most recent call last)", err.getvalue())

    def test_finetuned_mode_does_not_report_star_token(self) -> None:
        self.assertIsNone(align.star_token_enabled("/tmp/checkpoint"))

    def test_align_response_exposes_star_token(self) -> None:
        """Флаг режима уходит клиенту: по ответу /align видно, что сработал откат."""
        serve.align_syllables = lambda path, text, model_path: [{"label": "ма", "start_ms": 0, "end_ms": 100}]
        serve.star_token_enabled = lambda model_path=None: False

        async def scenario():
            transport = httpx.ASGITransport(app=serve.app)
            async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
                return await client.post(
                    "/align",
                    data={"text": "машина"},
                    files={"file": ("vocals.wav", make_wav(), "audio/wav")},
                )

        import asyncio

        response = asyncio.run(scenario())
        self.assertEqual(response.status_code, 200, response.text)
        self.assertIs(response.json()["star_token"], False)


class RomanizeGuardTest(unittest.TestCase):
    # --- BR-494: главный сценарий карточки P-69-06 -------------------------------------------

    def test_missing_uroman_stops_alignment(self) -> None:
        """ImportError при импорте uroman — отказ, а не кириллица «как есть»."""
        with mock.patch.dict(sys.modules, {"uroman": None}):
            with self.assertRaises(align.AlignmentSetupError) as ctx:
                align._romanize(["привет"])
        self.assertIn("uroman", str(ctx.exception))

    def test_placeholder_ratio_above_threshold_rejected(self) -> None:
        """90 % слов вне словаря MMS_FA (сломанная романизация) — отказ."""
        words = ["привет"] * 9 + ["ab"]
        bundle = _FakeBundle()
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            with self.assertRaises(align.AlignmentSetupError) as ctx:
                align._sanitize_for_vocab(words, bundle)
        self.assertIn("порог", str(ctx.exception))
        self.assertIn("10%", str(ctx.exception))

    def test_placeholder_ratio_at_threshold_allowed(self) -> None:
        """1 слово из 10 (10 %, не больше порога) — не повод ронять весь запрос."""
        words = ["привет"] + ["ab"] * 9
        with contextlib.redirect_stderr(io.StringIO()):
            result = align._sanitize_for_vocab(words, _FakeBundle())
        self.assertEqual(len(result), len(words))
        self.assertIn(result[0], {"a", "b"})  # заглушка вместо вычищенного слова (символ из словаря)

    def test_serve_maps_setup_error_to_503(self) -> None:
        def failing(path, text, model_path):
            raise align.AlignmentSetupError("uroman не установлен - романизация обязательна для MMS_FA")

        orig = serve.align_syllables
        serve.align_syllables = failing
        try:
            async def scenario():
                transport = httpx.ASGITransport(app=serve.app)
                async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
                    return await client.post(
                        "/align",
                        data={"text": "машина"},
                        files={"file": ("vocals.wav", make_wav(), "audio/wav")},
                    )

            import asyncio

            response = asyncio.run(scenario())
        finally:
            serve.align_syllables = orig
        self.assertEqual(response.status_code, 503, response.text)
        self.assertIn("uroman", response.json()["detail"])


if __name__ == "__main__":
    unittest.main()
