"""
Тонкий inference-сервер поверх align.py: POST /align (аудио-файл + текст) -> JSON с таймингом
слогов. Прототип контракта, который позже сможет заменить/дополнить WhisperAsrService в
karaoke-app (см. план фичи) - интеграция НЕ входит в эту итерацию, только сам сервис.

Запуск: uvicorn serve:app --host 0.0.0.0 --port 8017
"""

from __future__ import annotations

import tempfile
from pathlib import Path

from fastapi import FastAPI, File, Form, UploadFile

from align import align_syllables

app = FastAPI(title="karaoke-alignment-ml")


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/align")
async def align(text: str = Form(...), file: UploadFile = File(...)):
    suffix = Path(file.filename or "audio").suffix or ".flac"
    with tempfile.NamedTemporaryFile(suffix=suffix) as tmp:
        tmp.write(await file.read())
        tmp.flush()
        syllables = align_syllables(tmp.name, text)
    return {"ok": True, "syllables": syllables}
