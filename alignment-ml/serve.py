"""
Тонкий inference-сервер поверх align.py: POST /align (аудио-файл + текст) -> JSON с таймингом
слогов. Прототип контракта, который позже сможет заменить/дополнить WhisperAsrService в
karaoke-app (см. план фичи) - интеграция НЕ входит в эту итерацию, только сам сервис.

Запуск: uvicorn serve:app --host 0.0.0.0 --port 8017

Дообученный чекпоинт (train.py) задаётся переменной окружения ALIGN_MODEL_PATH (например
checkpoints/mms-ft) - без неё запрос с use_finetuned=true вернёт 400 (нечего использовать). Сам
режим (baseline/дообученная) выбирается НЕ на старте сервиса, а НА КАЖДЫЙ запрос полем формы
use_finetuned (см. /align) - обе модели грузятся в память лениво по первому запросу каждого вида
и остаются закэшированы (align._load_model), чтобы переключение не требовало перезапуска uvicorn
и не платило по нескольку минут загрузки 1B-модели на каждый вызов. ALIGN_DEFAULT_USE_FINETUNED
задаёт, какой режим использовать, если клиент явно не передал use_finetuned (по умолчанию baseline -
до тех пор, пока конкретный дообученный чекпоинт не подтверждён через evaluate.py как минимум не
хуже baseline).

  ALIGN_MODEL_PATH=checkpoints/mms-ft uvicorn serve:app --host 0.0.0.0 --port 8017

Пределы приёма (BR-489/BR-490, аудит #336, порция P-69):
  ALIGN_MAX_UPLOAD_MB      - предел размера загружаемого файла, МиБ (по умолчанию 64);
  ALIGN_MAX_AUDIO_SECONDS  - предел длительности аудио после чтения заголовка контейнера
                             (по умолчанию 600 с = 10 минут; обычная песня 2-4 минуты).

Обработчик /align объявлен ОБЫЧНОЙ синхронной функцией (не async def): Starlette исполняет такие
обработчики в threadpool, поэтому тяжёлое синхронное выравнивание (загрузка 1B-модели, forward,
CTC) больше не блокирует event loop - GET /health отвечает во время выравнивания. Внутри обработчика
нельзя вызывать ничего async (в частности, await file.read()) - тело уже разобрано Starlette и
лежит в UploadFile.file (SpooledTemporaryFile), читаем его синхронно и потоково.
"""

from __future__ import annotations

import os
import tempfile
from pathlib import Path

import soundfile as sf
from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse

from align import align_syllables

MODEL_PATH = os.environ.get("ALIGN_MODEL_PATH") or None
DEFAULT_USE_FINETUNED = os.environ.get("ALIGN_DEFAULT_USE_FINETUNED", "").lower() in ("1", "true", "yes")

# BR-490 (P-69-02): без этих пределов файл целиком читался в RAM и полностью декодировался
# (для часового stereo 44.1 кГц - гигабайты PCM), что валило воркер uvicorn по OOM. Длительность
# проверяется по заголовку (sf.info) ДО декодирования.
MAX_UPLOAD_MB = int(os.environ.get("ALIGN_MAX_UPLOAD_MB", "64"))
MAX_UPLOAD_BYTES = MAX_UPLOAD_MB * 1024 * 1024
MAX_AUDIO_SECONDS = float(os.environ.get("ALIGN_MAX_AUDIO_SECONDS", "600"))
_COPY_CHUNK_BYTES = 1024 * 1024

app = FastAPI(title="karaoke-alignment-ml")

print(
    f"[serve] дообученный чекпоинт: {MODEL_PATH or '(не задан - ALIGN_MODEL_PATH)'}; "
    f"режим по умолчанию: {'дообученная' if DEFAULT_USE_FINETUNED else 'baseline'}; "
    f"пределы: файл <= {MAX_UPLOAD_MB} МиБ, аудио <= {MAX_AUDIO_SECONDS:.0f} с",
    flush=True,
)


def _content_length(request: Request) -> int | None:
    """Content-Length из заголовков; None - заголовка нет или он нечисловой (chunked-запрос)."""
    raw = request.headers.get("content-length")
    if not raw:
        return None
    try:
        return int(raw)
    except ValueError:
        return None


def _reject_oversized_upload(content_length: int | None) -> None:
    if content_length is not None and content_length > MAX_UPLOAD_BYTES:
        raise HTTPException(
            status_code=413,
            detail=(
                f"файл больше предела {MAX_UPLOAD_MB} МиБ (Content-Length={content_length}) - "
                f"если это осознанно, поднимите ALIGN_MAX_UPLOAD_MB"
            ),
        )


@app.middleware("http")
async def _reject_oversized_before_reading_body(request: Request, call_next):
    """413 по Content-Length ещё ДО разбора multipart: иначе гигантское тело успевает целиком
    лечь во временный файл Starlette (BR-490, fail-closed на уровне транспорта)."""
    if request.url.path == "/align":
        try:
            _reject_oversized_upload(_content_length(request))
        except HTTPException as exc:
            return JSONResponse({"detail": exc.detail}, status_code=exc.status_code)
    return await call_next(request)


def _spool_upload(stream, target) -> int:
    """Потоково копирует тело запроса в целевой файл, считая байты (вместо `await file.read()`,
    который тянул весь файл в RAM вторым экземпляром поверх того, что уже лежит на диске).
    Предел проверяется и здесь: Content-Length может отсутствовать (chunked) или врать."""
    written = 0
    while True:
        chunk = stream.read(_COPY_CHUNK_BYTES)
        if not chunk:
            break
        written += len(chunk)
        if written > MAX_UPLOAD_BYTES:
            raise HTTPException(
                status_code=413,
                detail=(
                    f"файл больше предела {MAX_UPLOAD_MB} МиБ (Content-Length отсутствует или "
                    f"не совпадает с телом) - если это осознанно, поднимите ALIGN_MAX_UPLOAD_MB"
                ),
            )
        target.write(chunk)
    if written == 0:
        raise HTTPException(status_code=400, detail="пустой файл")
    return written


def _probe_audio_seconds(path: str) -> float:
    """Длительность по заголовку контейнера (soundfile/libsndfile), БЕЗ декодирования в PCM.
    Нечитаемый файл - 415, а не 500: клиент прислал не аудио."""
    try:
        info = sf.info(path)
    except Exception as exc:  # libsndfile бросает свои типы (LibsndfileError/RuntimeError и т.п.)
        raise HTTPException(
            status_code=415,
            detail=f"не удалось прочитать аудио как FLAC/WAV ({exc})",
        ) from exc
    if info.samplerate <= 0 or info.frames <= 0:
        raise HTTPException(status_code=415, detail="аудиофайл пустой (0 семплов)")
    return info.frames / info.samplerate


@app.get("/health")
def health():
    return {
        "ok": True,
        "finetuned_model_path": MODEL_PATH,
        "finetuned_available": MODEL_PATH is not None,
        "default_use_finetuned": DEFAULT_USE_FINETUNED,
        "max_upload_bytes": MAX_UPLOAD_BYTES,
        "max_audio_seconds": MAX_AUDIO_SECONDS,
    }


@app.post("/align")
def align(
    request: Request,
    text: str = Form(...),
    file: UploadFile = File(...),
    use_finetuned: bool | None = Form(None),
):
    """use_finetuned - явный выбор режима на этот запрос (см. кнопка/настройка "с дообученной
    моделью" в SubsEdit). Не передан (None) - берём ALIGN_DEFAULT_USE_FINETUNED.

    Синхронный (def), а не async def: см. докстринг модуля (BR-489)."""
    finetuned = DEFAULT_USE_FINETUNED if use_finetuned is None else use_finetuned
    if finetuned and MODEL_PATH is None:
        raise HTTPException(
            status_code=400,
            detail="use_finetuned=true, но ALIGN_MODEL_PATH не задан на сервере - нечего использовать",
        )

    _reject_oversized_upload(_content_length(request))

    suffix = Path(file.filename or "audio").suffix or ".flac"
    with tempfile.NamedTemporaryFile(suffix=suffix) as tmp:
        file.file.seek(0)
        _spool_upload(file.file, tmp)
        tmp.flush()
        duration_s = _probe_audio_seconds(tmp.name)
        if duration_s > MAX_AUDIO_SECONDS:
            raise HTTPException(
                status_code=413,
                detail=(
                    f"аудио длиннее предела {MAX_AUDIO_SECONDS:.0f} с ({duration_s:.1f} с) - "
                    f"если это осознанно, поднимите ALIGN_MAX_AUDIO_SECONDS"
                ),
            )
        syllables = align_syllables(tmp.name, text, MODEL_PATH if finetuned else None)
    return {"ok": True, "syllables": syllables, "used_finetuned": finetuned}
