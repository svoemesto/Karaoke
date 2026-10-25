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

ЭКСПЕРИМЕНТАЛЬНО: у многоголосых песен несколько голосов делят один файл вокала - текст одного
голоса покрывает лишь часть аудио. Без специального механизма forced_align размазывает весь текст
по ВСЕЙ длительности - на реальном датасете наблюдались ошибки в десятки СЕКУНД именно на
второстепенных голосах (voice > 0). Добавлен звёздный токен MMS_FA (with_star=True, "*" между
словами - модель может списать на него кусок аудио, не описанный транскриптом) как попытка это
починить - требует проверки реальным прогоном на тех же песнях, где были катастрофические ошибки.
Если версия torchaudio не поддерживает with_star - откат на обычный режим без звёздного токена,
но он ЯВНЫЙ: флаг star_token уходит в /align, причина печатается в stderr (BR-493). Любой другой
сбой загрузки (битая/недокачанная модель, OOM) - отказ, а не тихий откат.
"""

from __future__ import annotations

import argparse
import json
import sys
import traceback

import numpy as np
import soundfile as sf
import torch
import torchaudio

from syllables import split_text_into_words


class AlignmentSetupError(RuntimeError):
    """Окружение сервиса не позволяет выравнивать: нет романизатора для MMS_FA, романизация
    выродилась в заглушки и т.п. (BR-494).

    Отдельный тип нужен, чтобы serve.py отвечал явной ошибкой 5xx, а не молча отдавал
    ``ok: true`` с заведомо неверными таймингами (см. карточку P-69-06)."""


# BR-494 (P-69-06): доля слов, ушедших в заглушку после романизации, выше которой выравнивание
# отклоняется. 0.10 - порог из карточки аудита; при меньшей доле отдельные слова-исключения
# (аббревиатуры, эмодзи) не должны ронять весь запрос.
MAX_PLACEHOLDER_RATIO = 0.10


def read_audio(path: str) -> tuple[torch.Tensor, int]:
    """Читает аудио через soundfile (libsndfile) вместо torchaudio.load - новые версии torchaudio
    (2.9+) убрали встроенный FFmpeg-бэкенд и требуют отдельный пакет torchcodec (+ подходящую по
    версии системную FFmpeg) для .load(); soundfile читает FLAC/WAV нативно без этой возни."""
    data, sample_rate = sf.read(path, dtype="float32", always_2d=True)  # (frames, channels)
    waveform = torch.from_numpy(np.ascontiguousarray(data.T))  # (channels, frames)
    return waveform, sample_rate


def read_audio_segment(path: str, start_ms: int, end_ms: int) -> tuple[torch.Tensor, int]:
    """Как read_audio, но читает только указанный отрезок (в мс) через soundfile start/frames - не
    грузя в память весь файл. Нужно для train.py: тренировочные чанки (20-30 сек, см. chunking.py)
    вырезаются из многоминутных исходников, грузить каждый раз всю песню целиком расточительно."""
    info = sf.info(path)
    start_frame = max(0, int(start_ms / 1000 * info.samplerate))
    end_frame = min(info.frames, int(end_ms / 1000 * info.samplerate))
    frames = max(0, end_frame - start_frame)
    data, sample_rate = sf.read(path, start=start_frame, frames=frames, dtype="float32", always_2d=True)
    waveform = torch.from_numpy(np.ascontiguousarray(data.T))
    return waveform, sample_rate

# Кэш загруженных моделей, отдельно по model_path (None = baseline MMS_FA) - serve.py должен уметь
# держать baseline И дообученный чекпоинт ОДНОВРЕМЕННО в памяти и переключаться между ними на
# каждый запрос (см. --use-finetuned в /align), поэтому кэш не может быть одним набором глобальных
# переменных "текущей" модели, как было раньше (это работало только пока процесс за весь свой
# жизненный цикл вызывал align с одним и тем же model_path - верно для CLI/evaluate.py/train.py,
# но не для serve.py с переключением из UI).
_model_cache: dict[str | None, dict] = {}


def _load_model(model_path: str | None = None) -> dict:
    """Без model_path - baseline torchaudio.pipelines.MMS_FA (готовая модель, без обучения).
    С model_path - дообученный HF Wav2Vec2ForCTC чекпоинт (см. train.py) со своим словарём
    символов; выравнивание тогда идёт через общую torchaudio.functional.forced_align (не через
    bundle.get_aligner(), который завязан на словарь именно MMS_FA). Возвращает dict состояния
    модели (тот же dict при повторном вызове с тем же model_path - кэш, тяжёлая модель грузится
    только один раз за процесс)."""
    if model_path in _model_cache:
        return _model_cache[model_path]

    state: dict = {}

    if model_path:
        from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor

        state["custom_processor"] = Wav2Vec2Processor.from_pretrained(model_path)
        state["model"] = Wav2Vec2ForCTC.from_pretrained(model_path)
        state["model"].eval()
        state["sample_rate"] = state["custom_processor"].feature_extractor.sampling_rate
        _model_cache[model_path] = state
        return state

    from torchaudio.pipelines import MMS_FA as bundle

    state["bundle"] = bundle
    state["tokenizer"] = bundle.get_tokenizer()
    state["sample_rate"] = bundle.sample_rate

    # with_star=True: у многоголосых песен несколько голосов делят один и тот же файл вокала -
    # текст ОДНОГО голоса покрывает только часть аудио (остальное время поёт другой голос). Без
    # звёздного токена forced_align вынужден размазать весь текст по ВСЕЙ длительности аудио -
    # наблюдались ошибки в десятки секунд именно на второстепенных голосах. "*" - специальный токен
    # MMS_FA именно под "кусок аудио не описан транскриптом" (экспериментально - первый реальный
    # прогон должен показать, действительно ли это чинит именно эти случаи).
    #
    # BR-493 (P-69-05): откат на режим без звёздного токена остаётся, но перестал быть тихим:
    #   * успех -> state["star_token"] = True;
    #   * откат -> state["star_token"] = False + state["star_fallback_reason"], причина уходит в
    #     stderr вместе с трессировкой, а serve.py отдаёт признак режима в ответе /align;
    #   * откат разрешён ТОЛЬКО для TypeError/AttributeError - это единственные исключения,
    #     которые означают "в этой версии torchaudio нет get_model(with_star=...)". Любой другой
    #     сбой (недокачанная/битая модель, OOM, сетевой сбой) - реальный отказ окружения:
    #     раньше он тоже приводил к "тихой" загрузке без звёздного токена (и второй загрузке
    #     модели), теперь пробрасывается наверх и превращается в 5xx, а не в неверные тайминги.
    state["star_token"] = True
    state["star_fallback_reason"] = None
    try:
        state["model"] = bundle.get_model(with_star=True)
        state["aligner"] = bundle.get_aligner()
        star_dict = bundle.get_dict(star="*")
        state["star_id"] = star_dict["*"]
    except (TypeError, AttributeError) as e:
        print(
            f"[align] with_star=True недоступен ({type(e).__name__}: {e}) - откатываюсь на загрузку "
            f"без звёздного токена; тайминги многоголосых песен могут быть заметно хуже",
            file=sys.stderr,
            flush=True,
        )
        print(traceback.format_exc(), file=sys.stderr, flush=True)
        state["model"] = bundle.get_model()
        state["aligner"] = bundle.get_aligner()
        state["star_id"] = None
        state["star_token"] = False
        state["star_fallback_reason"] = f"{type(e).__name__}: {e}"
    except Exception:
        # Не откат, а отказ: печатаем полную причину и не грузим модель второй раз (BR-493).
        print("[align] загрузка MMS_FA с звёздным токеном упала - выравнивание невозможно", file=sys.stderr, flush=True)
        print(traceback.format_exc(), file=sys.stderr, flush=True)
        raise
    state["model"].eval()
    _model_cache[model_path] = state
    return state


def star_token_enabled(model_path: str | None = None) -> bool | None:
    """Признак режима baseline-выравнивания для ответа /align (BR-493, P-69-05).

    True - модель загружена со звёздным токеном; False - сработал явный откат (причина в
    ``star_fallback_reason`` и в stderr); None - используется дообученный чекпоинт, звёздный
    токен в этой ветке не применяется. Модель уже загружена вызовом ``align_syllables`` (кэш),
    поэтому повторной загрузки 1B-весов здесь нет."""
    if model_path is not None:
        return None
    return bool(_load_model(None).get("star_token"))


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
    except ImportError as e:
        # BR-494 (P-69-06): раньше здесь кириллица уходила в MMS_FA как есть и целиком вычищалась
        # _sanitize_for_vocab в заглушки "a a a ..." - модель выдавала правдоподобные, но
        # бессмысленные интервалы, а /align отвечал ok: true. Романизация для MMS_FA обязательна,
        # поэтому отсутствие uroman - отказ, а не тихая деградация.
        raise AlignmentSetupError(
            "uroman не установлен - романизация обязательна для MMS_FA "
            "(установите пакет uroman и системный perl: pip install -r requirements.txt); "
            "выравнивание остановлено, чтобы не отдавать неверные тайминги"
        ) from e


def _sanitize_for_vocab(words: list[str], bundle) -> list[str]:
    """MMS_FA словарь (bundle.get_dict(): символ -> индекс) - конечный набор символов, на которых
    обучена модель. Любой другой символ в romanized-тексте (артефакт uroman - апострофы, диакритика,
    случайно оставшийся пробел и т.п.) токенизатор либо не найдёт, либо смапит на blank/id=0, а
    forced_align считает такой target невалидным ("targets Tensor shouldn't contain blank index").
    Фильтруем строго по реальному словарю модели, а не гадаем заранее, какие символы "безопасны".

    BR-494: если заглушкой заменена слишком большая доля слов (MAX_PLACEHOLDER_RATIO), выравнивание
    отклоняется. Иначе весь русский текст мог молча превратиться в "a a a ..." при сломанной
    романизации, и заведомо неверные тайминги уходили бы в маркеры."""
    vocab = bundle.get_dict()
    blank_chars = {c for c, i in vocab.items() if i == 0}
    valid = set(vocab.keys()) - blank_chars

    result = []
    placeholders = 0
    for w in words:
        cleaned = "".join(c for c in w if c in valid)
        if cleaned == "":
            # Слово целиком состояло из символов вне словаря - не оставляем его вовсе без токенов
            # (сломало бы соответствие индексов со списком слов дальше по конвейеру), берём заглушку.
            cleaned = next(iter(valid), "a")
            placeholders += 1
            print(
                f"[align] слово '{w}' целиком вне словаря MMS_FA после романизации - заменено заглушкой",
                file=sys.stderr,
                flush=True,
            )
        result.append(cleaned)

    if words and placeholders / len(words) > MAX_PLACEHOLDER_RATIO:
        raise AlignmentSetupError(
            f"после романизации {placeholders} из {len(words)} слов "
            f"({placeholders / len(words):.0%}) целиком вне словаря MMS_FA - больше порога "
            f"{MAX_PLACEHOLDER_RATIO:.0%}; выравнивание остановлено (проверьте романизацию uroman и "
            f"текст), иначе получился бы вырожденный транскрипт и неверные тайминги"
        )
    return result


def _load_audio(audio_path: str, sample_rate_target: int) -> torch.Tensor:
    waveform, sample_rate = read_audio(audio_path)
    if waveform.size(0) > 1:
        waveform = waveform.mean(dim=0, keepdim=True)
    if sample_rate != sample_rate_target:
        waveform = torchaudio.functional.resample(waveform, sample_rate, sample_rate_target)
    return waveform


def _align_words_baseline(waveform: torch.Tensor, words: list[str], state: dict) -> list[tuple[float, float]]:
    bundle = state["bundle"]
    sample_rate = state["sample_rate"]
    with torch.inference_mode():
        emission, _ = state["model"](waveform)

    romanized = _sanitize_for_vocab(_romanize(words), bundle)
    token_sequences = state["tokenizer"](romanized)

    star_id = state["star_id"]
    if star_id is not None:
        # Звёздный токен ДО, МЕЖДУ и ПОСЛЕ каждого реального слова - модель может "списать" на "*"
        # произвольный кусок аудио между словами (в т.ч. пение другого голоса), а не размазывать
        # реальные слова по всей длительности. Реальные слова после этого - на НЕЧЁТНЫХ позициях
        # (0=*, 1=word0, 2=*, 3=word1, ..., 2N=*).
        interleaved = [[star_id]]
        for seq in token_sequences:
            interleaved.append(seq)
            interleaved.append([star_id])
        all_spans = state["aligner"](emission[0], interleaved)
        token_spans = all_spans[1::2]
    else:
        token_spans = state["aligner"](emission[0], token_sequences)

    num_frames = emission.size(1)
    ratio = waveform.size(1) / num_frames / sample_rate

    return [(spans[0].start * ratio, spans[-1].end * ratio) for spans in token_spans]


def _align_words_finetuned(waveform: torch.Tensor, words: list[str], state: dict) -> list[tuple[float, float]]:
    """Ветка --model/use_finetuned: дообученный HF Wav2Vec2ForCTC (train.py), выравнивание через
    общую torchaudio.functional.forced_align (не привязана к словарю MMS_FA, работает с любым CTC)."""
    processor = state["custom_processor"]
    sample_rate = state["sample_rate"]
    text_normalized = "|".join(words).lower()
    with torch.inference_mode():
        input_values = processor(waveform.squeeze(0).numpy(), sampling_rate=sample_rate).input_values
        logits = state["model"](torch.tensor(input_values)).logits
        emission = torch.log_softmax(logits, dim=-1)

    # as_target_processor() - устаревший способ переключить процессор на токенизатор текста (и уже
    # убран в некоторых версиях transformers, см. train.py) - зовём tokenizer напрямую.
    token_ids = torch.tensor([processor.tokenizer(text_normalized).input_ids])

    aligned_tokens, scores = torchaudio.functional.forced_align(emission, token_ids, blank=processor.tokenizer.pad_token_id)
    token_spans = torchaudio.functional.merge_tokens(aligned_tokens[0], scores[0])

    # merge_tokens даёт спаны по СИМВОЛАМ, включая "|" (разделитель слов) - схлопываем в спаны по словам.
    # span.token - ЧИСЛОВОЙ индекс токена (id из словаря), а не строка - сравнивать нужно с
    # word_delimiter_token_id, а не с самим word_delimiter_token (строкой "|"); иначе сравнение
    # int == str всегда False, весь список токенов схлопывается в один спан на всю песню.
    word_delimiter_id = processor.tokenizer.word_delimiter_token_id
    word_spans: list[tuple[float, float]] = []
    current_start = None
    current_end = None
    ratio = waveform.size(1) / emission.size(1) / sample_rate
    for span in token_spans:
        if span.token == word_delimiter_id:
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
    state = _load_model(model_path)
    waveform = _load_audio(audio_path, state["sample_rate"])
    if "custom_processor" in state:
        return _align_words_finetuned(waveform, words, state)
    return _align_words_baseline(waveform, words, state)


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
