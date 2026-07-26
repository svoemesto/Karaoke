"""
Тонкий inference-сервер поверх align.py: POST /align (аудио-файл + текст) -> JSON с таймингом
слогов. Прототип контракта, который позже сможет заменить/дополнить WhisperAsrService в
karaoke-app (см. план фичи) - интеграция НЕ входит в эту итерацию, только сам сервис.

Запуск: uvicorn serve:app --host 0.0.0.0 --port 8017

Модель выбирается переменной окружения ALIGN_MODEL_PATH:
  - не задана (по умолчанию) - baseline torchaudio.pipelines.MMS_FA (без дообучения);
  - путь к чекпоинту (train.py, например checkpoints/mms-ft или checkpoints/mms-ft/checkpoint-XXX)
    - дообученная модель. Перед переключением прода - сравнить точность через
    evaluate.py --model <путь> и не включать, если она не лучше baseline.

  ALIGN_MODEL_PATH=checkpoints/mms-ft uvicorn serve:app --host 0.0.0.0 --port 8017
"""

from __future__ import annotations

import os
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, Form, UploadFile

from align import align_syllables

MODEL_PATH = os.environ.get("ALIGN_MODEL_PATH") or None

app = FastAPI(title="karaoke-alignment-ml")

print(f"[serve] модель: {MODEL_PATH or 'baseline MMS_FA (ALIGN_MODEL_PATH не задан)'}", flush=True)


@app.get("/health")
def health():
    return {"ok": True, "model": MODEL_PATH or "baseline"}


@app.post("/align")
async def align(text: str = Form(...), file: UploadFile = File(...)):
    suffix = Path(file.filename or "audio").suffix or ".flac"
    with tempfile.NamedTemporaryFile(suffix=suffix) as tmp:
        tmp.write(await file.read())
        tmp.flush()
        syllables = align_syllables(tmp.name, text, MODEL_PATH)
    return {"ok": True, "syllables": syllables}
