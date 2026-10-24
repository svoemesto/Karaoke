"""Регресс-тесты «до/после» для BR-489 и BR-490 (аудит #336, порция P-69).

Проверяют обвязку `/align` (BR-489: синхронный обработчик не блокирует event loop; BR-490: пределы
размера/длительности и потоковое чтение вместо `await file.read()`), а не саму 1B-модель:
`serve.align_syllables` подменяется заглушкой.

Запуск (нужны fastapi/httpx/soundfile/torch из venv alignment-ml):

    cd alignment-ml
    ./.venv/bin/python -m unittest discover -s tests -v

На коде ДО правки падают: test_handler_is_sync_function, test_health_answers_while_align_is_running,
test_oversized_upload_rejected, test_too_long_audio_rejected, test_non_audio_upload_rejected.
"""

from __future__ import annotations

import asyncio
import inspect
import io
import sys
import time
import unittest
import wave
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import httpx  # noqa: E402

import serve  # noqa: E402


def make_wav(seconds: float, sample_rate: int = 16000) -> bytes:
    """Корректный mono WAV заданной длительности (без numpy/torch)."""
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(sample_rate)
        wav_file.writeframes(b"\x00\x00" * int(sample_rate * seconds))
    return buf.getvalue()


class _StubAlign:
    """Заглушка align_syllables: запоминает вызовы и (опционально) «выравнивает» с задержкой."""

    def __init__(self, delay: float = 0.0) -> None:
        self.delay = delay
        self.calls: list[tuple[str, str, str | None]] = []

    def __call__(self, path: str, text: str, model_path: str | None):
        self.calls.append((path, text, model_path))
        if self.delay:
            time.sleep(self.delay)
        return [{"label": "тест", "start_ms": 0, "end_ms": 100}]


class AlignEndpointTest(unittest.TestCase):
    def setUp(self) -> None:
        self._orig_align = serve.align_syllables
        # На коде ДО правки этих констант нет: берём getattr, чтобы тесты падали на ПОВЕДЕНИИ
        # (200 вместо 413, health ждёт выравнивание), а не на инициализации.
        self._orig_limits = {
            name: getattr(serve, name, None)
            for name in ("MAX_UPLOAD_BYTES", "MAX_AUDIO_SECONDS", "MAX_UPLOAD_MB")
        }
        self.stub = _StubAlign()
        serve.align_syllables = self.stub

    def tearDown(self) -> None:
        serve.align_syllables = self._orig_align
        for name, value in self._orig_limits.items():
            if value is not None:
                setattr(serve, name, value)

    async def _post_align(self, audio: bytes | None = None, filename: str = "vocals.wav"):
        transport = httpx.ASGITransport(app=serve.app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            return await client.post(
                "/align",
                data={"text": "тестовый текст"},
                files={"file": (filename, make_wav(0.5) if audio is None else audio, "audio/wav")},
            )

    def test_handler_is_sync_function(self) -> None:
        """BR-489: обработчик обязан быть `def`, чтобы Starlette увёл его в threadpool."""
        self.assertFalse(
            inspect.iscoroutinefunction(serve.align),
            "align() объявлен async def - тяжёлое выравнивание снова заблокирует event loop",
        )

    def test_health_answers_while_align_is_running(self) -> None:
        """BR-489: пока один запрос выравнивается, GET /health должен отвечать сразу."""
        self.stub.delay = 0.6

        async def scenario():
            transport = httpx.ASGITransport(app=serve.app)
            async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
                started = time.perf_counter()
                align_task = asyncio.create_task(self._post_align(audio=make_wav(0.5)))
                await asyncio.sleep(0.15)  # даём /align дойти до заглушки выравнивания
                health = await client.get("/health")
                elapsed = time.perf_counter() - started
                still_running = not align_task.done()
                align_response = await align_task
                return health, align_response, elapsed, still_running

        health, align_response, elapsed, still_running = asyncio.run(scenario())
        self.assertEqual(health.status_code, 200)
        self.assertEqual(align_response.status_code, 200)
        self.assertTrue(still_running, "GET /health дождался конца выравнивания - event loop заблокирован")
        self.assertLess(
            elapsed,
            0.3,
            f"GET /health ответил через {elapsed:.2f} с после старта выравнивания: event loop заблокирован",
        )

    def test_oversized_upload_rejected(self) -> None:
        """BR-490: тело больше предела - 413 ещё до копирования/декодирования."""
        serve.MAX_UPLOAD_MB = 0
        serve.MAX_UPLOAD_BYTES = 1024  # 1 КиБ - тестовый WAV заведомо больше
        response = asyncio.run(self._post_align(audio=make_wav(0.5)))
        self.assertEqual(response.status_code, 413, response.text)
        self.assertEqual(self.stub.calls, [], "выравнивание не должно запускаться для отвергнутого файла")

    def test_too_long_audio_rejected(self) -> None:
        """BR-490: длительность проверяется по заголовку (sf.info) до декодирования."""
        serve.MAX_AUDIO_SECONDS = 0.1
        response = asyncio.run(self._post_align(audio=make_wav(0.5)))
        self.assertEqual(response.status_code, 413, response.text)
        self.assertIn("длиннее предела", response.text)
        self.assertEqual(self.stub.calls, [])

    def test_non_audio_upload_rejected(self) -> None:
        """BR-490: нечитаемый файл - 415 (клиент прислал не аудио), а не 500."""
        response = asyncio.run(self._post_align(audio=b"this is definitely not audio" * 64))
        self.assertEqual(response.status_code, 415, response.text)
        self.assertEqual(self.stub.calls, [])

    def test_content_length_limit_helper(self) -> None:
        """BR-490: Content-Length больше предела отклоняется без чтения тела."""
        from fastapi import HTTPException

        serve._reject_oversized_upload(None)  # заголовка нет - не наша забота
        with self.assertRaises(HTTPException) as ctx:
            serve._reject_oversized_upload(serve.MAX_UPLOAD_BYTES + 1)
        self.assertEqual(ctx.exception.status_code, 413)

    def test_valid_upload_still_aligns(self) -> None:
        """Пределы не должны ломать штатный запрос."""
        response = asyncio.run(self._post_align(audio=make_wav(0.5)))
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(len(self.stub.calls), 1)
        self.assertTrue(response.json()["ok"])


if __name__ == "__main__":
    unittest.main()
