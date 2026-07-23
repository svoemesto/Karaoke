"""
Дообучение CTC-модели на датасете пения (manifest.jsonl) - ВТОРОЙ шаг, нужен только если
evaluate.py показал, что baseline (MMS_FA "из коробки", align.py) недостаточно точен на реальном
пении (растянутые гласные, вибрато - вероятный домен-мисматч с речью, на которой модель обучена).

Дообучаем `facebook/mms-1b-all` (тот же MMS-чекпоинт, что и baseline, доступен и через
torchaudio.pipelines.MMS_FA, и через HuggingFace transformers Wav2Vec2ForCTC) на своих (аудио,
текст) парах через обычный CTC fine-tuning - тот же рецепт, что используется для дообучения
Wav2Vec2 под ASR (CTC loss сама по себе улучшает и распознавание, и forced alignment, т.к. и то,
и другое строится на одних и тех же по-фреймовых вероятностях символов).

Целые песни (2-4 минуты) не грузим - нетипично длинные клипы для CTC-тренировки, риск OOM/очень
медленных шагов, особенно на 1B-параметров модели при 16GB VRAM. Режем на короткие сегменты по
паузам между строками/куплетами (chunking.py) - см. README про то, почему именно так.

16GB VRAM под модель такого размера - тесно даже с fp16 + gradient checkpointing: по умолчанию
--batch-size 1 + --grad-accum-steps (эмулирует больший эффективный батч без доп. памяти на
активации) + 8-bit AdamW (bitsandbytes, см. requirements.txt) - существенно снижает память под
состояния оптимизатора по сравнению с обычным fp32 AdamW.

Ожидаемо потребует правок при первом реальном запуске на данных админа (конкретные версии
transformers/torch, доступность чекпоинта, реальный бюджет памяти и т.п.) - см. план фичи.
"""

from __future__ import annotations

import argparse
import json
import re
import statistics
import time
from pathlib import Path

import torch
import torchaudio
from torch.utils.data import Dataset
from transformers import (
    Trainer,
    TrainerCallback,
    TrainingArguments,
    Wav2Vec2CTCTokenizer,
    Wav2Vec2FeatureExtractor,
    Wav2Vec2ForCTC,
    Wav2Vec2Processor,
)
from transformers.trainer_utils import get_last_checkpoint

from align import read_audio_segment
from chunking import (
    DEFAULT_LEAD_PAD_MS,
    DEFAULT_MAX_CHUNK_MS,
    DEFAULT_MIN_CHUNK_MS,
    DEFAULT_SILENCE_THRESHOLD_MS,
    DEFAULT_TAIL_PAD_MS,
    build_chunks,
)
from manifest import load_manifest

BASE_CHECKPOINT = "facebook/mms-1b-all"  # тот же чекпоинт, что использует align.py как baseline


def _format_duration(seconds: float) -> str:
    if seconds == float("inf") or seconds != seconds:  # inf или NaN (rate ещё не посчитан)
        return "?"
    seconds = int(seconds)
    hours, seconds = divmod(seconds, 3600)
    minutes, seconds = divmod(seconds, 60)
    return f"{hours}ч{minutes:02d}м{seconds:02d}с" if hours else f"{minutes}м{seconds:02d}с"


class ProgressLoggerCallback(TrainerCallback):
    """Печатает прогресс дообучения ПОСТРОЧНО (не через tqdm-бар с '\\r') - при многочасовом фоновом
    прогоне (nohup/screen) удобно смотреть через `tail -f`, а перезаписываемая на месте строка
    прогресс-бара в файле лога превращается в мусор. См. disable_tqdm=True рядом в TrainingArguments -
    эта колбэк-печать полностью заменяет стандартный прогресс-бар, а не дублирует его."""

    def __init__(self):
        self.start_time: float | None = None

    def on_train_begin(self, args, state, control, **kwargs):
        self.start_time = time.time()
        print(
            f"[train] начало: всего шагов={state.max_steps}, эпох={args.num_train_epochs}, "
            f"текущий шаг={state.global_step} (0, если не --resume)",
            flush=True,
        )

    def on_log(self, args, state, control, logs=None, **kwargs):
        if not logs or self.start_time is None:
            return
        # eval-логи (eval_loss и т.п.) идут отдельным вызовом on_log без "loss" - печатаем и их,
        # чтобы было видно и обучающий, и eval-лосс на каждой контрольной точке (--save-steps).
        elapsed = time.time() - self.start_time
        step = state.global_step
        total = state.max_steps or 1
        percent = 100 * step / total
        rate = step / elapsed if elapsed > 0 else 0
        remaining = (total - step) / rate if rate > 0 else float("inf")

        parts = [f"шаг {step}/{total} ({percent:.1f}%)", f"эпоха {logs.get('epoch', state.epoch or 0):.2f}"]
        for key, label in (("loss", "loss"), ("eval_loss", "eval_loss"), ("learning_rate", "lr")):
            if key in logs:
                value = logs[key]
                parts.append(f"{label}={value:.2e}" if key == "learning_rate" else f"{label}={value:.4f}")
        parts.append(f"прошло={_format_duration(elapsed)}")
        parts.append(f"осталось≈{_format_duration(remaining)}")
        print("[train] " + " | ".join(parts), flush=True)

    def on_save(self, args, state, control, **kwargs):
        print(f"[train] чекпоинт сохранён: шаг {state.global_step} -> {args.output_dir}", flush=True)


def build_vocab(texts: list[str]) -> dict[str, int]:
    chars = sorted({c for text in texts for c in text.lower() if not c.isspace()})
    vocab = {c: i for i, c in enumerate(chars)}
    vocab["|"] = len(vocab)  # разделитель слов (соглашение wav2vec2 CTC tokenizer)
    vocab["[UNK]"] = len(vocab)
    vocab["[PAD]"] = len(vocab)
    return vocab


def build_chunk_items(rows, chunk_args: dict) -> list[tuple[str, int, int, str]]:
    """Раскладывает строки манифеста на короткие (аудио-файл, start_ms, end_ms, текст) - см.
    chunking.py. Строка пропускается целиком (как и в evaluate.py), если: слоговая разбивка текста
    разошлась с числом слогов в манифесте (не должно происходить в норме - обе стороны используют
    один и тот же алгоритм, см. WhisperMarkerAligner.kt/syllables.py), ИЛИ тайминги слогов оказались
    не монотонны (редкий побочный эффект неточной вставки Whisper, см. проверку в build_chunks)."""
    items = []
    skipped_rows = 0
    for row in rows:
        chunks = build_chunks(row.text, row.syllables, row.duration_ms, **chunk_args)
        if not chunks:
            skipped_rows += 1
            continue
        for chunk in chunks:
            items.append((row.audio_file, chunk.start_ms, chunk.end_ms, chunk.text))
    print(f"Песен: {len(rows)}, пропущено (несогласованные слоги/тайминг): {skipped_rows}, "
          f"итого чанков для обучения: {len(items)}")
    return items


def _feat_extract_output_length(num_samples: int, model: Wav2Vec2ForCTC) -> int:
    """Длина выходной последовательности conv-фичеэкстрактора Wav2Vec2/MMS для входа в num_samples
    семплов - та же формула, что transformers считает внутри себя (config.conv_kernel/conv_stride),
    нужна ЗАРАНЕЕ (до реального forward), чтобы отфильтровать чанки, для которых аудио физически
    коротко для их собственного текста (см. _filter_ctc_viable)."""
    length = num_samples
    for kernel, stride in zip(model.config.conv_kernel, model.config.conv_stride):
        length = (length - kernel) // stride + 1
    return max(0, length)


def _filter_ctc_viable(
    items: list[tuple[str, int, int, str]],
    model: Wav2Vec2ForCTC,
    tokenizer: Wav2Vec2CTCTokenizer,
    sample_rate: int,
    label_for_log: str,
) -> list[tuple[str, int, int, str]]:
    """CTC loss требует, чтобы длина выхода модели (T, кадров) была не меньше 2*L+1, где L - число
    токенов текста (L, включая дубликаты - в худшем случае между КАЖДОЙ парой соседних токенов нужен
    разделительный blank) - иначе loss/градиент становится -inf/NaN, а поскольку optimizer.step()
    молча "съедает" NaN-градиент, ВСЯ модель необратимо портится с этого шага (не только один пример).
    На реальном датасете такое бывает: см. build_chunks/_merge_short_chunks в chunking.py - изолированный
    короткий кусок (одно слово между двумя длинными паузами) НАМЕРЕННО остаётся коротким, клеить его
    не с чем, но CTC-тренировке такой кусок просто физически не по силам (аудио короче собственного
    текста), поэтому отсеиваем его здесь, а не полагаемся на упавший через часы NaN grad_norm."""
    viable = []
    skipped = 0
    for item in items:
        _, start_ms, end_ms, text = item
        num_samples = int((end_ms - start_ms) / 1000 * sample_rate)
        output_len = _feat_extract_output_length(num_samples, model)
        text_normalized = re.sub(r"\s+", "|", text.strip().lower())
        label_len = len(tokenizer(text_normalized).input_ids)
        if output_len >= 2 * label_len + 1:
            viable.append(item)
        else:
            skipped += 1
    if skipped:
        print(f"Отфильтровано чанков для {label_for_log} (аудио физически коротко под собственный "
              f"текст, T<2L+1 для CTC): {skipped} из {len(items)}")
    return viable


class AlignmentDataset(Dataset):
    """Каждый элемент - короткий (20-30 сек, см. chunking.py) кусок аудио + текст ИМЕННО этого
    куска (не всей песни). Точные тайминги слогов из манифеста здесь дальше не нужны - только для
    подбора границ чанка и для evaluate.py."""

    def __init__(self, items: list[tuple[str, int, int, str]], processor: Wav2Vec2Processor, sample_rate: int):
        self.items = items
        self.processor = processor
        self.sample_rate = sample_rate

    def __len__(self):
        return len(self.items)

    def __getitem__(self, idx):
        audio_file, start_ms, end_ms, text = self.items[idx]
        waveform, sr = read_audio_segment(audio_file, start_ms, end_ms)
        if waveform.size(0) > 1:
            waveform = waveform.mean(dim=0, keepdim=True)
        if sr != self.sample_rate:
            waveform = torchaudio.functional.resample(waveform, sr, self.sample_rate)

        input_values = self.processor(waveform.squeeze(0).numpy(), sampling_rate=self.sample_rate).input_values[0]
        text_normalized = re.sub(r"\s+", "|", text.strip().lower())
        # processor.as_target_processor() - устаревший (и в установленной версии transformers уже
        # убранный) способ переключить процессор на токенизатор текста; современный - звать
        # tokenizer напрямую, он всегда доступен как атрибут Wav2Vec2Processor.
        labels = self.processor.tokenizer(text_normalized).input_ids

        return {"input_values": input_values, "labels": labels}


class DataCollatorCTCWithPadding:
    def __init__(self, processor: Wav2Vec2Processor):
        self.processor = processor

    def __call__(self, features):
        input_features = [{"input_values": f["input_values"]} for f in features]
        label_features = [{"input_ids": f["labels"]} for f in features]

        batch = self.processor.pad(input_features, padding=True, return_tensors="pt")
        labels_batch = self.processor.tokenizer.pad(label_features, padding=True, return_tensors="pt")

        labels = labels_batch["input_ids"].masked_fill(labels_batch["attention_mask"].ne(1), -100)
        batch["labels"] = labels
        return batch


def main():
    parser = argparse.ArgumentParser(description="Fine-tuning CTC-модели на manifest.jsonl")
    parser.add_argument("--manifest", default="data/manifest.jsonl")
    parser.add_argument("--output-dir", default="checkpoints/mms-ft")
    parser.add_argument("--limit", type=int, default=None, help="Ограничить число песен (смоук-тест)")
    parser.add_argument("--eval-holdout", type=float, default=0.05, help="Доля чанков на eval-выборку")
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--batch-size", type=int, default=1, help="1 по умолчанию - 1B-модель на 16GB VRAM тесно")
    parser.add_argument("--grad-accum-steps", type=int, default=8,
                         help="Эмулирует batch-size=batch-size*grad-accum-steps без доп. памяти на активации")
    parser.add_argument("--optim", default="adamw_bnb_8bit",
                         help="8-bit AdamW (bitsandbytes) по умолчанию - экономит память под состояния "
                              "оптимизатора; 'adamw_torch' если bitsandbytes недоступен/не работает")
    parser.add_argument("--silence-threshold-ms", type=int, default=DEFAULT_SILENCE_THRESHOLD_MS,
                         help="Пауза между слогами длиннее этого - граница чанка (см. chunking.py)")
    parser.add_argument("--max-chunk-ms", type=int, default=DEFAULT_MAX_CHUNK_MS,
                         help="Потолок длины чанка, даже если естественной паузы не нашлось")
    parser.add_argument("--min-chunk-ms", type=int, default=DEFAULT_MIN_CHUNK_MS,
                         help="Чанки короче этого клеятся к соседнему - почти не несут сигнала как есть")
    parser.add_argument("--lead-pad-ms", type=int, default=DEFAULT_LEAD_PAD_MS)
    parser.add_argument("--tail-pad-ms", type=int, default=DEFAULT_TAIL_PAD_MS)
    parser.add_argument("--dry-run", action="store_true",
                         help="Только построить чанки и вывести статистику - без загрузки модели и GPU "
                              "(можно гонять параллельно с align.py/evaluate.py на той же видеокарте)")
    parser.add_argument("--include-secondary-voices", action="store_true",
                         help="Включить voice>0 (по умолчанию исключены - один файл вокала на несколько "
                              "голосов, with_star помогает частично/нестабильно, см. align.py)")
    parser.add_argument("--save-steps", type=int, default=500,
                         help="Чекпоинт каждые N шагов оптимизатора (не только по эпохам) - точка, с "
                              "которой можно продолжить после остановки (см. --resume). Меньше значение "
                              "= чаще пишем на диск (I/O-накладные расходы), но меньше теряем при остановке.")
    parser.add_argument("--resume", action="store_true",
                         help="Продолжить обучение с последнего чекпоинта в --output-dir (если он там "
                              "есть) - вместо того чтобы начинать с нуля от facebook/mms-1b-all.")
    parser.add_argument("--logging-steps", type=int, default=None,
                         help="Как часто печатать прогресс в лог (шагов оптимизатора). "
                              "По умолчанию - save-steps/10 (можно чаще логировать, чем сохранять).")
    args = parser.parse_args()

    rows = load_manifest(args.manifest)
    rows = [r for r in rows if r.audio_exists]
    if not args.include_secondary_voices:
        before = len(rows)
        rows = [r for r in rows if r.voice == 0]
        print(f"Исключено голосов voice>0: {before - len(rows)} (--include-secondary-voices, чтобы включить)")
    if args.limit:
        rows = rows[: args.limit]
    if not rows:
        raise SystemExit("Манифест пуст или ни один аудиофайл не найден на диске")

    chunk_args = dict(
        silence_threshold_ms=args.silence_threshold_ms,
        max_chunk_ms=args.max_chunk_ms,
        min_chunk_ms=args.min_chunk_ms,
        lead_pad_ms=args.lead_pad_ms,
        tail_pad_ms=args.tail_pad_ms,
    )
    items = build_chunk_items(rows, chunk_args)
    if not items:
        raise SystemExit("Ни одного чанка не получилось построить - проверьте манифест/пороги чанкинга")

    durations_sec = [(end - start) / 1000 for _, start, end, _ in items]
    print(f"Длительность чанков (сек): среднее={statistics.mean(durations_sec):.1f}, "
          f"медиана={statistics.median(durations_sec):.1f}, "
          f"мин={min(durations_sec):.1f}, макс={max(durations_sec):.1f}")

    if args.dry_run:
        print("--dry-run: модель не загружается, обучение не запускается.")
        return

    holdout_n = max(1, int(len(items) * args.eval_holdout))
    train_items, eval_items = items[:-holdout_n], items[-holdout_n:]
    print(f"Чанков для обучения: {len(train_items)}, для оценки: {len(eval_items)}")

    vocab_path = Path(args.output_dir) / "vocab.json"
    vocab_path.parent.mkdir(parents=True, exist_ok=True)
    if vocab_path.exists():
        # Уже есть словарь от предыдущего запуска (в т.ч. при --resume) - переиспользуем как есть,
        # НЕ перестраиваем из текущих train_items: у уже сохранённых чекпоинтов CTC-голова модели
        # зашита под конкретный vocab_size/индексы символов - малейшее расхождение (другой --limit,
        # другой набор голосов и т.п. дали бы другой набор символов) сломает загрузку весов чекпоинта.
        vocab = json.loads(vocab_path.read_text(encoding="utf-8"))
        print(f"Использую существующий словарь {vocab_path} (для совместимости с уже сохранёнными чекпоинтами)")
    else:
        vocab = build_vocab([text for _, _, _, text in train_items])
        vocab_path.write_text(json.dumps(vocab, ensure_ascii=False), encoding="utf-8")

    tokenizer = Wav2Vec2CTCTokenizer(str(vocab_path), unk_token="[UNK]", pad_token="[PAD]", word_delimiter_token="|")
    feature_extractor = Wav2Vec2FeatureExtractor(
        feature_size=1, sampling_rate=16000, padding_value=0.0, do_normalize=True, return_attention_mask=True
    )
    processor = Wav2Vec2Processor(feature_extractor=feature_extractor, tokenizer=tokenizer)

    model = Wav2Vec2ForCTC.from_pretrained(
        BASE_CHECKPOINT,
        ctc_loss_reduction="mean",
        pad_token_id=tokenizer.pad_token_id,
        vocab_size=len(tokenizer),
        ignore_mismatched_sizes=True,  # свой словарь по символам, не оригинальный MMS-словарь
    )
    model.freeze_feature_encoder()
    # SpecAugment (случайное маскирование по времени/фиче, Wav2Vec2Model._mask_hidden_states) требует
    # sequence_length > mask_time_length (обычно 10 кадров) - у части наших коротких чанков (после
    # conv-даунсемплинга) кадров меньше, что валит forward с ValueError. Регуляризация от SpecAugment
    # не критична при дообучении (не с нуля) на датасете такого размера - проще выключить целиком,
    # чем городить ещё один порог длины вдобавок к CTC-фильтру выше (_filter_ctc_viable).
    model.config.apply_spec_augment = False

    train_items = _filter_ctc_viable(train_items, model, tokenizer, feature_extractor.sampling_rate, "обучения")
    eval_items = _filter_ctc_viable(eval_items, model, tokenizer, feature_extractor.sampling_rate, "оценки")
    if not train_items:
        raise SystemExit("После фильтрации не осталось ни одного чанка для обучения - см. вывод выше")

    train_dataset = AlignmentDataset(train_items, processor, feature_extractor.sampling_rate)
    eval_dataset = AlignmentDataset(eval_items, processor, feature_extractor.sampling_rate)
    data_collator = DataCollatorCTCWithPadding(processor)

    training_args = TrainingArguments(
        output_dir=args.output_dir,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size,
        gradient_accumulation_steps=args.grad_accum_steps,
        optim=args.optim,
        num_train_epochs=args.epochs,
        eval_strategy="steps",
        eval_steps=args.save_steps,
        save_strategy="steps",
        save_steps=args.save_steps,
        logging_steps=args.logging_steps or max(1, args.save_steps // 10),
        logging_first_step=True,  # лог сразу на первом шаге - видно, что процесс реально пошёл
        disable_tqdm=True,  # прогресс печатает ProgressLoggerCallback построчно (см. его докстринг)
        learning_rate=1e-5,
        warmup_steps=100,
        fp16=torch.cuda.is_available(),
        gradient_checkpointing=True,
        save_total_limit=2,
        report_to=[],  # без wandb/tensorboard - без этого Trainer может ждать интерактивный wandb-логин
    )

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=eval_dataset,
        data_collator=data_collator,
        processing_class=processor.feature_extractor,  # Trainer(tokenizer=...) переименован/убран
        callbacks=[ProgressLoggerCallback()],
    )

    # --resume - продолжаем с последнего чекпоинта в output-dir (веса модели, состояние оптимизатора
    # и шедулера, пройденные шаги/эпохи - HF Trainer восстанавливает всё это сам). Без --resume, но с
    # уже существующими чекпоинтами в output-dir, НАМЕРЕННО не подхватываем их автоматически -
    # чтобы случайный повторный запуск команды без --resume не тихо "доучивал" старую модель, а
    # начинал заново (что и было поведением по умолчанию раньше).
    resume_from_checkpoint = None
    if args.resume:
        resume_from_checkpoint = get_last_checkpoint(args.output_dir)
        if resume_from_checkpoint is None:
            print(f"--resume указан, но в {args.output_dir} нет сохранённых чекпоинтов - начинаем с нуля.")
        else:
            print(f"Продолжаем обучение с чекпоинта: {resume_from_checkpoint}")

    trainer.train(resume_from_checkpoint=resume_from_checkpoint)
    trainer.save_model(args.output_dir)
    processor.save_pretrained(args.output_dir)
    print(f"Готово. Чекпоинт сохранён в {args.output_dir} - передайте его в align.py через --model.")


if __name__ == "__main__":
    main()
