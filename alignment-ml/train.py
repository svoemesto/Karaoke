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
from pathlib import Path

import torch
import torchaudio
from torch.utils.data import Dataset
from transformers import (
    Trainer,
    TrainingArguments,
    Wav2Vec2CTCTokenizer,
    Wav2Vec2FeatureExtractor,
    Wav2Vec2ForCTC,
    Wav2Vec2Processor,
)

from align import read_audio_segment
from chunking import DEFAULT_LEAD_PAD_MS, DEFAULT_MAX_CHUNK_MS, DEFAULT_SILENCE_THRESHOLD_MS, DEFAULT_TAIL_PAD_MS, build_chunks
from manifest import load_manifest

BASE_CHECKPOINT = "facebook/mms-1b-all"  # тот же чекпоинт, что использует align.py как baseline


def build_vocab(texts: list[str]) -> dict[str, int]:
    chars = sorted({c for text in texts for c in text.lower() if not c.isspace()})
    vocab = {c: i for i, c in enumerate(chars)}
    vocab["|"] = len(vocab)  # разделитель слов (соглашение wav2vec2 CTC tokenizer)
    vocab["[UNK]"] = len(vocab)
    vocab["[PAD]"] = len(vocab)
    return vocab


def build_chunk_items(rows, chunk_args: dict) -> list[tuple[str, int, int, str]]:
    """Раскладывает строки манифеста на короткие (аудио-файл, start_ms, end_ms, текст) - см.
    chunking.py. Строки, где слоговая разбивка текста разошлась с числом слогов в манифесте (не
    должно происходить в норме - обе стороны используют один и тот же алгоритм, см.
    WhisperMarkerAligner.kt/syllables.py), пропускаются целиком (как и в evaluate.py)."""
    items = []
    skipped_rows = 0
    for row in rows:
        chunks = build_chunks(row.text, row.syllables, row.duration_ms, **chunk_args)
        if not chunks:
            skipped_rows += 1
            continue
        for chunk in chunks:
            items.append((row.audio_file, chunk.start_ms, chunk.end_ms, chunk.text))
    print(f"Песен: {len(rows)}, пропущено (расхождение слоговой разбивки): {skipped_rows}, "
          f"итого чанков для обучения: {len(items)}")
    return items


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
        with self.processor.as_target_processor():
            labels = self.processor(text_normalized).input_ids

        return {"input_values": input_values, "labels": labels}


class DataCollatorCTCWithPadding:
    def __init__(self, processor: Wav2Vec2Processor):
        self.processor = processor

    def __call__(self, features):
        input_features = [{"input_values": f["input_values"]} for f in features]
        label_features = [{"input_ids": f["labels"]} for f in features]

        batch = self.processor.pad(input_features, padding=True, return_tensors="pt")
        with self.processor.as_target_processor():
            labels_batch = self.processor.pad(label_features, padding=True, return_tensors="pt")

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
    parser.add_argument("--lead-pad-ms", type=int, default=DEFAULT_LEAD_PAD_MS)
    parser.add_argument("--tail-pad-ms", type=int, default=DEFAULT_TAIL_PAD_MS)
    parser.add_argument("--dry-run", action="store_true",
                         help="Только построить чанки и вывести статистику - без загрузки модели и GPU "
                              "(можно гонять параллельно с align.py/evaluate.py на той же видеокарте)")
    args = parser.parse_args()

    rows = load_manifest(args.manifest)
    rows = [r for r in rows if r.audio_exists]
    if args.limit:
        rows = rows[: args.limit]
    if not rows:
        raise SystemExit("Манифест пуст или ни один аудиофайл не найден на диске")

    chunk_args = dict(
        silence_threshold_ms=args.silence_threshold_ms,
        max_chunk_ms=args.max_chunk_ms,
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

    vocab = build_vocab([text for _, _, _, text in train_items])
    vocab_path = Path(args.output_dir) / "vocab.json"
    vocab_path.parent.mkdir(parents=True, exist_ok=True)
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
        eval_strategy="epoch",
        save_strategy="epoch",
        learning_rate=1e-5,
        warmup_steps=100,
        fp16=torch.cuda.is_available(),
        group_by_length=True,
        gradient_checkpointing=True,
        save_total_limit=2,
    )

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=eval_dataset,
        data_collator=data_collator,
        tokenizer=processor.feature_extractor,
    )

    trainer.train()
    trainer.save_model(args.output_dir)
    processor.save_pretrained(args.output_dir)
    print(f"Готово. Чекпоинт сохранён в {args.output_dir} - передайте его в align.py через --model.")


if __name__ == "__main__":
    main()
