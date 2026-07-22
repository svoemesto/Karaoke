"""
Дообучение CTC-модели на датасете пения (manifest.jsonl) - ВТОРОЙ шаг, нужен только если
evaluate.py показал, что baseline (MMS_FA "из коробки", align.py) недостаточно точен на реальном
пении (растянутые гласные, вибрато - вероятный домен-мисматч с речью, на которой модель обучена).

Дообучаем `facebook/mms-1b-all` (тот же MMS-чекпоинт, что и baseline, доступен и через
torchaudio.pipelines.MMS_FA, и через HuggingFace transformers Wav2Vec2ForCTC) на своих (аудио,
текст) парах через обычный CTC fine-tuning - тот же рецепт, что используется для дообучения
Wav2Vec2 под ASR (CTC loss сама по себе улучшает и распознавание, и forced alignment, т.к. и то,
и другое строится на одних и тех же по-фреймовых вероятностях символов).

Ожидаемо потребует правок при первом реальном запуске на данных админа (конкретные версии
transformers/torch, доступность чекпоинта, память GPU и т.п.) - см. план фичи.
"""

from __future__ import annotations

import argparse
import json
import re
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

from align import read_audio
from manifest import load_manifest

BASE_CHECKPOINT = "facebook/mms-1b-all"  # тот же чекпоинт, что использует align.py как baseline


def build_vocab(texts: list[str]) -> dict[str, int]:
    chars = sorted({c for text in texts for c in text.lower() if not c.isspace()})
    vocab = {c: i for i, c in enumerate(chars)}
    vocab["|"] = len(vocab)  # разделитель слов (соглашение wav2vec2 CTC tokenizer)
    vocab["[UNK]"] = len(vocab)
    vocab["[PAD]"] = len(vocab)
    return vocab


class AlignmentDataset(Dataset):
    """Каждый элемент - (аудио, полный текст голоса). CTC-цель строится из текста напрямую -
    точные тайминги слогов из манифеста здесь НЕ нужны, они нужны только для evaluate.py."""

    def __init__(self, rows, processor: Wav2Vec2Processor, sample_rate: int):
        self.rows = rows
        self.processor = processor
        self.sample_rate = sample_rate

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, idx):
        row = self.rows[idx]
        waveform, sr = read_audio(row.audio_file)
        if waveform.size(0) > 1:
            waveform = waveform.mean(dim=0, keepdim=True)
        if sr != self.sample_rate:
            waveform = torchaudio.functional.resample(waveform, sr, self.sample_rate)

        input_values = self.processor(waveform.squeeze(0).numpy(), sampling_rate=self.sample_rate).input_values[0]
        text_normalized = re.sub(r"\s+", "|", row.text.strip().lower())
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
    parser.add_argument("--limit", type=int, default=None, help="Ограничить число строк (смоук-тест)")
    parser.add_argument("--eval-holdout", type=float, default=0.05, help="Доля строк на eval-выборку")
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--batch-size", type=int, default=2)
    args = parser.parse_args()

    rows = load_manifest(args.manifest)
    rows = [r for r in rows if r.audio_exists]
    if args.limit:
        rows = rows[: args.limit]
    if not rows:
        raise SystemExit("Манифест пуст или ни один аудиофайл не найден на диске")

    holdout_n = max(1, int(len(rows) * args.eval_holdout))
    train_rows, eval_rows = rows[:-holdout_n], rows[-holdout_n:]
    print(f"Строк для обучения: {len(train_rows)}, для оценки: {len(eval_rows)}")

    vocab = build_vocab([r.text for r in train_rows])
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

    train_dataset = AlignmentDataset(train_rows, processor, feature_extractor.sampling_rate)
    eval_dataset = AlignmentDataset(eval_rows, processor, feature_extractor.sampling_rate)
    data_collator = DataCollatorCTCWithPadding(processor)

    training_args = TrainingArguments(
        output_dir=args.output_dir,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size,
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
