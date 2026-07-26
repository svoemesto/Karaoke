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
"""

from __future__ import annotations

import os
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from align import align_syllables

MODEL_PATH = os.environ.get("ALIGN_MODEL_PATH") or None
DEFAULT_USE_FINETUNED = os.environ.get("ALIGN_DEFAULT_USE_FINETUNED", "").lower() in ("1", "true", "yes")

app = FastAPI(title="karaoke-alignment-ml")

print(
    f"[serve] дообученный чекпоинт: {MODEL_PATH or '(не задан - ALIGN_MODEL_PATH)'}; "
    f"режим по умолчанию: {'дообученная' if DEFAULT_USE_FINETUNED else 'baseline'}",
    flush=True,
)


@app.get("/health")
def health():
    return {
        "ok": True,
        "finetuned_model_path": MODEL_PATH,
        "finetuned_available": MODEL_PATH is not None,
        "default_use_finetuned": DEFAULT_USE_FINETUNED,
    }


@app.post("/align")
async def align(
    text: str = Form(...),
    file: UploadFile = File(...),
    use_finetuned: bool | None = Form(None),
):
    """use_finetuned - явный выбор режима на этот запрос (см. кнопка/настройка "с дообученной
    моделью" в SubsEdit). Не передан (None) - берём ALIGN_DEFAULT_USE_FINETUNED."""
    finetuned = DEFAULT_USE_FINETUNED if use_finetuned is None else use_finetuned
    if finetuned and MODEL_PATH is None:
        raise HTTPException(
            status_code=400,
            detail="use_finetuned=true, но ALIGN_MODEL_PATH не задан на сервере - нечего использовать",
        )

    suffix = Path(file.filename or "audio").suffix or ".flac"
    with tempfile.NamedTemporaryFile(suffix=suffix) as tmp:
        tmp.write(await file.read())
        tmp.flush()
        syllables = align_syllables(tmp.name, text, MODEL_PATH if finetuned else None)
    return {"ok": True, "syllables": syllables, "used_finetuned": finetuned}
