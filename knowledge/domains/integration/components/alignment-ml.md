# Component: alignment-ml

> **Домен**: [integration](../domain.md)
> **Компонента**: Python ML-сервис forced alignment.

## Ответственность | Responsibility

`alignment-ml` — отдельный Python-сервис (НЕ Kotlin/Gradle), реализующий
**forced alignment**: по известному тексту песни и вокальному стему
проставляет тайминг каждого слога. Альтернатива/дополнение Whisper ASR.

**Зачем**: у Whisper качество оказалось недостаточным (Whisper не
знает текст заранее, может расслышать неверно, точность таймкодов не
выше слова). Forced alignment выравнивает уже ИЗВЕСТНЫЙ текст по
аудио на CTC-модели — принципиально точнее.

**NB**: стек полностью отдельный (Python/PyTorch, не Kotlin/Vue) —
обычная директория репозитория, не отдельный git, но со своим
`requirements.txt`/venv, никак не завязана на Gradle.

## Стек

- Python 3.x + PyTorch.
- FastAPI для HTTP-сервера (uvicorn).
- CTC-модель (MMS — massively multilingual speech, или дообученная).

## Файлы (по `ls alignment-ml/`)

| Файл | Что |
|---|---|
| `serve.py` | FastAPI inference-сервер (POST /align). |
| `align.py` | Алгоритм alignment (вызывается из serve.py). |
| `train.py` | Дообучение baseline-модели на датасете. |
| `chunking.py` | Разбиение длинных аудио на чанки для обучения. |
| `evaluate.py` | Сравнение alignment с ground truth (Whisper). |
| `syllables.py` | Утилиты для работы со слогами. |
| `manifest.py` | Генератор/парсер manifest.jsonl. |
| `ExportAlignmentDataset.kt` | (НЕ в Python — это karaoke-app), экспорт датасета из БД. |
| `checkpoints/` | Дообученные чекпоинты (`mms-ft`). |
| `requirements.txt` | Python deps. |
| `start_trainig.sh`, `resume_trainig.sh` | Скрипты обучения. |
| `README.md` | Документация. |

## Интерфейсы и Контракты | Interfaces and Contracts

HTTP-контракт задаётся `serve.py` (FastAPI). Клиент в Karaoke —
`AlignmentServiceClient.kt`, который шлёт multipart с теми же именами
полей.

### `POST /align`

```
POST /align
  body: multipart/form-data
    file: файл (FLAC; имя поля — `file`, суффикс берётся из filename)
    text: строка (известный текст песни, как в Settings.sourceText)
    use_finetuned: bool, необязательный (None → ALIGN_DEFAULT_USE_FINETUNED)
  response: JSON
    ok: bool
    syllables: [{label, start_ms, end_ms}]
    used_finetuned: bool
  ошибка: 400, если use_finetuned=true, а ALIGN_MODEL_PATH не задан
```

### `GET /health`

Возвращает `{ok, finetuned_model_path, finetuned_available,
default_use_finetuned}`.

**Запуск**:

```bash
ALIGN_MODEL_PATH=checkpoints/mms-ft uvicorn serve:app --host 0.0.0.0 --port 8017
```

**Поведение моделей**:

- **Два режима**: baseline (предобученная) и finetuned (дообученная).
- **Обе модели грузятся лениво** по первому запросу каждого вида и
  остаются закэшированы (`align._load_model`). Переключение не
  требует перезапуска uvicorn.
- **`ALIGN_DEFAULT_USE_FINETUNED`** — какой режим использовать, если
  клиент явно не передал `use_finetuned` (по умолчанию **baseline**,
  до тех пор, пока finetuned не подтверждён через `evaluate.py`).

**Env-переменные**:

- `ALIGN_MODEL_PATH` — путь к finetuned чекпоинту (например,
  `checkpoints/mms-ft`). Без неё запрос с `use_finetuned=true` вернёт
  **400** (нечего использовать).
- `ALIGN_DEFAULT_USE_FINETUNED` — дефолт для режима.

## Логика и Алгоритмы | Logic and Algorithms

**Шаги выравнивания** (`align_syllables` в `align.py`, вызывается из
`/align`):

1. `split_text_into_words(text)` — текст → слова, каждое слово — список
   слогов (та же разбивка, что дала ground truth в датасете).
2. Слова склеиваются обратно (`flat_words`) и уходят в `align_words` —
   это forced alignment по **известному** тексту, а не по тому, что
   распознал ASR.
3. `_load_model(model_path)` лениво грузит модель (baseline MMS_FA или
   finetuned-чекпоинт), `_load_audio` приводит аудио к `sample_rate`
   модели.
4. `_align_words_finetuned`, если в state есть `custom_processor`,
   иначе `_align_words_baseline` — по одному интервалу
   `(start_sec, end_sec)` на слово.
5. Тайминг слогов внутри слова раздаётся **пропорционально длине слога
   в символах** (`duration * len(syl) / total_chars`) — тот же приём,
   что в `WhisperMarkerAligner.kt` (упрощение до появления
   посимвольной/пофонемной привязки).
6. Результат — `[{label, start_ms, end_ms}]`, миллисекунды округляются.

### Датасет

**Источник**: `ExportAlignmentDataset.kt` в karaoke-app — кнопка
«Экспорт датасета для forced-alignment» на Home-странице.

**Что делает**:

- Сканирует все песни с `idStatus >= 3` (маркеры уже финальны/проверены).
- Пишет `manifest.jsonl` в `/sm-karaoke/system/alignment-dataset/`.
- **Без копирования аудио**: в манифесте абсолютные пути к уже
  существующим на диске FLAC-файлам (`Settings.vocalsNameFlac`).
- Каждая песня дополнительно прогоняется через Whisper для поиска
  ВСТАВОК: слова, реально спетые, но отсутствующие в официальном
  тексте (ad-libs и т.п.). Слоги вставок помечаются
  `hasGroundTruth: false` в манифесте.

### Логика обучения (`train.py`)

1. `manifest.jsonl` → train.py читает.
2. `chunking.py` — разбивает длинные аудио на чанки.
3. Дообучение baseline-модели на этих чанках.
4. Результат — чекпоинт в `checkpoints/mms-ft/`.

**Скрипты**:

- `start_trainig.sh` — запуск с нуля.
- `resume_trainig.sh` — продолжить с checkpoint.

### Оценка (`evaluate.py`)

Сравнивает alignment с ground truth (Whisper). Слоги с
`hasGroundTruth=false` НЕ сравниваются (это **ad-libs**, для которых
нет человеческого тайминга).

### Запуск всего pipeline

```bash
# 1. Экспорт датасета (admin UI)
# 2. Обучение
cd alignment-ml
./start_trainig.sh
# 3. Inference-сервер
ALIGN_MODEL_PATH=checkpoints/mms-ft uvicorn serve:app --host 0.0.0.0 --port 8017 &
# 4. karaoke-app использует через AlignmentServiceClient
```

## Зависимости | Dependencies

`AlignmentServiceClient.kt` (`караоке-app/.../services/`) — HTTP
multipart upload к этому сервису.

**Используется** в `FORCED_ALIGN_MARKERS` KaraokeProcessType (см.
[async-process-queue.md](../../../domains/processing/components/async-process-queue.md))
— фоновая задача для всех голосов песни сразу.

## Known gaps

- [ ] **Модель MMS**: точная версия (mms-300m? mms-1b?).
- [ ] **Fine-tuning hyperparameters** — train.py settings.
- [ ] **Evaluation thresholds** — когда finetuned становится лучше
      baseline (Pass 343+).
- [ ] **Что делать при ошибке align** — retry policy в
      `AlignmentServiceClient`.
- [ ] **Production deployment** — как именно запускается на проде
      (Docker? systemd? uv run?).

## Changelog

- **Pass 484** (2026-09-27, spec `484-knowledge-domain-integration`): секции приведены к шаблону. Автор: agent (Karaoke).
