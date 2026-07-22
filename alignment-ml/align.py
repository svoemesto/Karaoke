"""
Forced alignment: дан (аудио, известный текст) - выдаёт тайминг каждого слога.

Baseline - готовая multilingual CTC-модель torchaudio.pipelines.MMS_FA (Meta MMS, forced
alignment, 1000+ языков включая русский) БЕЗ дообучения. --model позволяет позже подставить путь
к дообученным весам (train.py), но по умолчанию используется baseline - сначала измеряем
(evaluate.py), дообучаем только если baseline недостаточно точен (см. план фичи).

ВАЖНО (первое, что стоит перепроверить при первом реальном запуске): словарь MMS_FA обучен на
романизированном тексте (через uroman) для нелатинских языков, в т.ч. русского. Романизация не
меняет порядок/количество слов (посимвольная транслитерация 1:1 по словам), поэтому для нашей
задачи (нужны только тайминги ПО СЛОВАМ, не посимвольная привязка) romanize-then-align с
последующим сопоставлением по индексу слова должно работать. Если версия torchaudio/MMS_FA
ведёт себя иначе - см. официальный тьюториал "Forced alignment for multilingual data".
"""

from __future__ import annotations

import argparse
import json

import numpy as np
import soundfile as sf
import torch
import torchaudio

from syllables import split_text_into_words


def read_audio(path: str) -> tuple[torch.Tensor, int]:
    """Читает аудио через soundfile (libsndfile) вместо torchaudio.load - новые версии torchaudio
    (2.9+) убрали встроенный FFmpeg-бэкенд и требуют отдельный пакет torchcodec (+ подходящую по
    версии системную FFmpeg) для .load(); soundfile читает FLAC/WAV нативно без этой возни."""
    data, sample_rate = sf.read(path, dtype="float32", always_2d=True)  # (frames, channels)
    waveform = torch.from_numpy(np.ascontiguousarray(data.T))  # (channels, frames)
    return waveform, sample_rate

_bundle = None
_model = None
_tokenizer = None
_aligner = None
_sample_rate = None
_custom_processor = None  # заполнено только в ветке --model (дообученный чекпоинт из train.py)


def _load_model(model_path: str | None = None):
    """Без --model - baseline torchaudio.pipelines.MMS_FA (готовая модель, без обучения).
    С --model <путь> - дообученный HF Wav2Vec2ForCTC чекпоинт (см. train.py) со своим словарём
    символов; выравнивание тогда идёт через общую torchaudio.functional.forced_align (не через
    bundle.get_aligner(), который завязан на словарь именно MMS_FA)."""
    global _bundle, _model, _tokenizer, _aligner, _sample_rate, _custom_processor
    if _model is not None:
        return

    if model_path:
        from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor

        _custom_processor = Wav2Vec2Processor.from_pretrained(model_path)
        _model = Wav2Vec2ForCTC.from_pretrained(model_path)
        _model.eval()
        _sample_rate = _custom_processor.feature_extractor.sampling_rate
        return

    from torchaudio.pipelines import MMS_FA as bundle

    _bundle = bundle
    _model = bundle.get_model()
    _model.eval()
    _tokenizer = bundle.get_tokenizer()
    _aligner = bundle.get_aligner()
    _sample_rate = bundle.sample_rate


def _romanize(words: list[str]) -> list[str]:
    """Романизация через uroman (pip install uroman) - MMS_FA обучен на латинизированном тексте
    для нелатинских языков. Слово-в-слово, порядок/количество слов не меняется.

    .lower() ОБЯЗАТЕЛЕН: uroman сохраняет регистр исходного текста (например, первая буква строки
    после романизации кириллицы - заглавная латинская), а словарь MMS_FA построен только по
    строчным буквам - без lower() токенизатор падает с KeyError на такую заглавную букву."""
    try:
        import uroman as ur

        romanizer = ur.Uroman()
        return [romanizer.romanize_string(w).lower() for w in words]
    except ImportError:
        print("[align] uroman не установлен - пробуем подать кириллицу как есть (может не сработать)")
        return [w.lower() for w in words]


def _sanitize_for_vocab(words: list[str]) -> list[str]:
    """MMS_FA словарь (bundle.get_dict(): символ -> индекс) - конечный набор символов, на которых
    обучена модель. Любой другой символ в romanized-тексте (артефакт uroman - апострофы, диакритика,
    случайно оставшийся пробел и т.п.) токенизатор либо не найдёт, либо смапит на blank/id=0, а
    forced_align считает такой target невалидным ("targets Tensor shouldn't contain blank index").
    Фильтруем строго по реальному словарю модели, а не гадаем заранее, какие символы "безопасны"."""
    vocab = _bundle.get_dict()
    blank_chars = {c for c, i in vocab.items() if i == 0}
    valid = set(vocab.keys()) - blank_chars

    result = []
    for w in words:
        cleaned = "".join(c for c in w if c in valid)
        if cleaned == "":
            # Слово целиком состояло из символов вне словаря - не оставляем его вовсе без токенов
            # (сломало бы соответствие индексов со списком слов дальше по конвейеру), берём заглушку.
            cleaned = next(iter(valid), "a")
            print(f"[align] слово '{w}' целиком вне словаря MMS_FA после романизации - заменено заглушкой")
        result.append(cleaned)
    return result


def _load_audio(audio_path: str) -> torch.Tensor:
    waveform, sample_rate = read_audio(audio_path)
    if waveform.size(0) > 1:
        waveform = waveform.mean(dim=0, keepdim=True)
    if sample_rate != _sample_rate:
        waveform = torchaudio.functional.resample(waveform, sample_rate, _sample_rate)
    return waveform


def _align_words_baseline(waveform: torch.Tensor, words: list[str]) -> list[tuple[float, float]]:
    with torch.inference_mode():
        emission, _ = _model(waveform)

    romanized = _sanitize_for_vocab(_romanize(words))
    token_spans = _aligner(emission[0], _tokenizer(romanized))

    num_frames = emission.size(1)
    ratio = waveform.size(1) / num_frames / _sample_rate

    return [(spans[0].start * ratio, spans[-1].end * ratio) for spans in token_spans]


def _align_words_finetuned(waveform: torch.Tensor, words: list[str]) -> list[tuple[float, float]]:
    """Ветка --model: дообученный HF Wav2Vec2ForCTC (train.py), выравнивание через общую
    torchaudio.functional.forced_align (не привязана к словарю MMS_FA, работает с любым CTC)."""
    text_normalized = "|".join(words).lower()
    with torch.inference_mode():
        input_values = _custom_processor(waveform.squeeze(0).numpy(), sampling_rate=_sample_rate).input_values
        logits = _model(torch.tensor(input_values)).logits
        emission = torch.log_softmax(logits, dim=-1)

    with _custom_processor.as_target_processor():
        token_ids = torch.tensor([_custom_processor(text_normalized).input_ids])

    aligned_tokens, scores = torchaudio.functional.forced_align(emission, token_ids, blank=_custom_processor.tokenizer.pad_token_id)
    token_spans = torchaudio.functional.merge_tokens(aligned_tokens[0], scores[0])

    # merge_tokens даёт спаны по СИМВОЛАМ, включая "|" (разделитель слов) - схлопываем в спаны по словам.
    word_spans: list[tuple[float, float]] = []
    current_start = None
    current_end = None
    ratio = waveform.size(1) / emission.size(1) / _sample_rate
    for span in token_spans:
        if span.token == _custom_processor.tokenizer.word_delimiter_token:
            if current_start is not None:
                word_spans.append((current_start * ratio, current_end * ratio))
            current_start = None
            continue
        if current_start is None:
            current_start = span.start
        current_end = span.end
    if current_start is not None:
        word_spans.append((current_start * ratio, current_end * ratio))
    return word_spans


def align_words(audio_path: str, words: list[str], model_path: str | None = None) -> list[tuple[float, float]]:
    """Возвращает список (start_sec, end_sec) - по одному на каждое слово из `words`, в том же
    порядке. Слова - НЕ то, что распознал сам аудиофайл, а известный ground-truth текст (в этом и
    смысл forced alignment в отличие от ASR)."""
    _load_model(model_path)
    waveform = _load_audio(audio_path)
    if _custom_processor is not None:
        return _align_words_finetuned(waveform, words)
    return _align_words_baseline(waveform, words)


def align_syllables(audio_path: str, text: str, model_path: str | None = None) -> list[dict]:
    """Высокоуровневая обёртка: текст -> слова (syllables.split_text_into_words, та же разбивка,
    что дала ground truth в датасете) -> forced alignment по словам -> тайминг каждого слога
    пропорционально его длине в символах внутри временного отрезка слова (тот же приём, что и в
    WhisperMarkerAligner.kt для word-level ASR - там это единственный доступный уровень точности;
    здесь это упрощение до появления посимвольной/пофонемной привязки)."""
    words = split_text_into_words(text)  # список слов, каждое - список слогов
    flat_words = ["".join(syllables) for syllables in words]
    if not flat_words:
        return []

    spans = align_words(audio_path, flat_words, model_path)

    result = []
    for word_syllables, (start, end) in zip(words, spans):
        total_chars = sum(len(s) for s in word_syllables) or 1
        duration = max(0.0, end - start)
        cursor = start
        for syl in word_syllables:
            share = duration * len(syl) / total_chars
            result.append({"label": syl, "start_ms": round(cursor * 1000), "end_ms": round((cursor + share) * 1000)})
            cursor += share
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Forced-align один (аудио, текст) для отладки/смоук-теста")
    parser.add_argument("--audio", required=True, help="Путь к аудиофайлу (FLAC/WAV/...)")
    parser.add_argument("--text", required=True, help="Известный текст (как в Settings.sourceText)")
    parser.add_argument("--model", default=None, help="Путь к дообученному чекпоинту (train.py); без флага - baseline MMS_FA")
    args = parser.parse_args()

    syllables = align_syllables(args.audio, args.text, args.model)
    print(json.dumps(syllables, ensure_ascii=False, indent=2))
