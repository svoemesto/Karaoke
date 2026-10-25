#!/usr/bin/env python3
import hashlib
import os
import re
import subprocess
import sys
import tempfile
import xml.sax.saxutils as saxutils

MODEL_DIR = os.path.expanduser("~/.cache/silero-tts")
MODEL_PATH = os.path.join(MODEL_DIR, "v4_ru.pt")
MODEL_URL = "https://models.silero.ai/models/tts/ru/v4_ru.pt"
# BR-548 (аудит #336 / P-73-10, OpenProject #393): целостность модели TTS.
#
# Было: `torch.hub.download_url_to_file` качал модель без сверки контрольной суммы, а
# `torch.package.PackageImporter(...).load_pickle(...)` исполняет содержимое файла как код
# (pickle) в процессе пользователя, запустившего деплой. Компрометация models.silero.ai / CDN /
# канала → произвольный код на admin-машине (доступ к ~/.docker/config.json, репозиторию).
#
# Стало: sha256 модели зафиксирован (это файл, который реально использовался на admin-машине;
# размер — 40 107 184 байта). Загрузка идёт во временный файл и переименовывается только после
# совпадения хэша; существующий файл проверяется перед каждой загрузкой pickle. Несовпадение —
# отказ (SystemExit), pickle не загружается.
#
# Обновление модели — только осознанно: скачать, сверить глазами источник, заменить константу
# и добавить запись в отчёт. `sha256sum ~/.cache/silero-tts/v4_ru.pt` — как получить значение.
MODEL_SHA256 = "896ab96347d5bd781ab97959d4fd6885620e5aab52405d3445626eb7c1414b00"
MODEL_SIZE_BYTES = 40107184
SAMPLE_RATE = 48000
# Нумерация голосов — как при прослушивании образцов: 1=aidar, 2=baya, 3=kseniya,
# 4=xenia, 5=eugene. Задать конкретный номер можно вторым аргументом CLI или
# через SILERO_SPEAKER (принимает и номер, и имя напрямую).
SPEAKER_MAP = {"1": "aidar", "2": "baya", "3": "kseniya", "4": "xenia", "5": "eugene"}
DEFAULT_SPEAKER = "2"


def resolve_speaker(value):
    if not value:
        value = os.environ.get("SILERO_SPEAKER", DEFAULT_SPEAKER)
    return SPEAKER_MAP.get(value, value)


# Ударение — символ "+" перед ударной гласной прямо в тексте (напр. "г+олоса"),
# модель поддерживает это нативно, без дополнительной обработки в этом скрипте.
# Скорость — через SSML <prosody rate="...">: x-slow, slow, medium, fast, x-fast.
RATES = {"x-slow", "slow", "medium", "fast", "x-fast"}


def build_ssml(text, rate):
    return f'<speak><prosody rate="{rate}">{saxutils.escape(text)}</prosody></speak>'

# Модель либо падает с ValueError (фраза целиком без кириллицы, напр. "Pushing!"),
# либо молча проглатывает латинские слова внутри смешанной фразы без единой ошибки
# (напр. "WEBVUE3 запущен" реально озвучивает только "запущен" — сам акроним
# исчезает бесследно). Поэтому транслитерируем ЛЮБЫЕ латинские слова в тексте
# всегда, не только когда во фразе вообще нет кириллицы; исходный текст (он же
# уходит в notify-send) не трогаем.
_DIGRAPHS = [
    ("sch", "щ"), ("shch", "щ"), ("sh", "ш"), ("ch", "ч"), ("ck", "к"),
    ("th", "т"), ("ph", "ф"), ("qu", "кв"), ("wh", "в"),
    ("oo", "у"), ("ee", "и"), ("ea", "и"), ("ay", "эй"), ("ey", "эй"), ("oy", "ой"),
]
_LETTERS = {
    "a": "а", "b": "б", "c": "к", "d": "д", "e": "е", "f": "ф", "g": "г",
    "h": "х", "i": "и", "j": "дж", "k": "к", "l": "л", "m": "м", "n": "н",
    "o": "о", "p": "п", "q": "к", "r": "р", "s": "с", "t": "т", "u": "у",
    "v": "в", "w": "в", "x": "кс", "y": "й", "z": "з",
}


def transliterate_word(word):
    result = []
    i = 0
    lower = word.lower()
    while i < len(lower):
        for digraph, repl in _DIGRAPHS:
            if lower.startswith(digraph, i):
                result.append(repl)
                i += len(digraph)
                break
        else:
            ch = lower[i]
            result.append(_LETTERS.get(ch, ch))
            i += 1
    translit = "".join(result)
    return translit.capitalize() if word[:1].isupper() else translit


def ensure_pronounceable(text):
    return re.sub(r"[A-Za-z]+", lambda m: transliterate_word(m.group(0)), text)


def sha256_file(path):
    """sha256 файла (потоково, чтобы не держать 40 МБ в памяти)."""
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_model(path):
    """Проверка размера и sha256; при несовпадении — отказ (pickle не загружаем)."""
    size = os.path.getsize(path)
    if size != MODEL_SIZE_BYTES:
        raise SystemExit(
            f"ERROR: размер модели TTS {size} != ожидаемого {MODEL_SIZE_BYTES} байт; "
            f"файл {path} не загружаем (BR-548)"
        )
    actual = sha256_file(path)
    if actual != MODEL_SHA256:
        raise SystemExit(
            f"ERROR: sha256 модели TTS {actual} != ожидаемого {MODEL_SHA256}; "
            f"файл {path} не загружаем — удалите его и скачайте заново из доверенного источника (BR-548)"
        )


def download_model():
    """Скачать модель во временный файл и переименовать только после сверки sha256."""
    import torch  # тяжёлый импорт — только на пути загрузки/синтеза

    temporary = MODEL_PATH + ".part"
    try:
        torch.hub.download_url_to_file(MODEL_URL, temporary)
        verify_model(temporary)
        os.replace(temporary, MODEL_PATH)
    finally:
        if os.path.isfile(temporary):
            os.remove(temporary)


def ensure_model():
    os.makedirs(MODEL_DIR, exist_ok=True)
    if not os.path.isfile(MODEL_PATH):
        download_model()
    # Проверяем и уже лежащий файл: подмена в кеше так же опасна, как подмена при загрузке.
    verify_model(MODEL_PATH)


def play(wav_path):
    for player in ("paplay", "aplay"):
        if subprocess.run(["which", player], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
            subprocess.run([player, wav_path], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return


def main():
    if len(sys.argv) < 2 or not sys.argv[1].strip():
        return
    text = ensure_pronounceable(sys.argv[1])
    speaker = resolve_speaker(sys.argv[2].strip() if len(sys.argv) > 2 else "")
    rate = sys.argv[3].strip() if len(sys.argv) > 3 and sys.argv[3].strip() else None

    # BR-548: сначала целостность модели, и только потом torch/soundfile и pickle.
    ensure_model()
    import soundfile as sf
    import torch

    torch.set_num_threads(2)
    model = torch.package.PackageImporter(MODEL_PATH).load_pickle("tts_models", "model")
    model.to(torch.device("cpu"))

    if rate and rate in RATES:
        audio = model.apply_tts(ssml_text=build_ssml(text, rate), speaker=speaker, sample_rate=SAMPLE_RATE)
    else:
        audio = model.apply_tts(text=text, speaker=speaker, sample_rate=SAMPLE_RATE)

    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        wav_path = f.name
    try:
        sf.write(wav_path, audio.numpy(), SAMPLE_RATE)
        play(wav_path)
    finally:
        os.remove(wav_path)


if __name__ == "__main__":
    main()
