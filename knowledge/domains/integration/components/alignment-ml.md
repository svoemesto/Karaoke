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

## HTTP API (`serve.py`)

```
POST /align
  body: multipart/form-data
    audio: файл (FLAC)
    text: строка ( известный текст песни)
    use_finetuned: bool (по умолчанию из env ALIGN_DEFAULT_USE_FINETUNED)
  response: JSON
    ok: bool
    syllables: [{label, start_ms, end_ms}]
```

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

## Интеграция с karaoke-app

`AlignmentServiceClient.kt` (`караоке-app/.../services/`) — HTTP
multipart upload к этому сервису.

**Используется** в `FORCED_ALIGN_MARKERS` KaraokeProcessType (см.
[async-process-queue.md](../../../domains/processing/components/async-process-queue.md))
— фоновая задача для всех голосов песни сразу.

## Датасет

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

## Логика обучения (`train.py`)

1. `manifest.jsonl` → train.py читает.
2. `chunking.py` — разбивает длинные аудио на чанки.
3. Дообучение baseline-модели на этих чанках.
4. Результат — чекпоинт в `checkpoints/mms-ft/`.

**Скрипты**:

- `start_trainig.sh` — запуск с нуля.
- `resume_trainig.sh` — продолжить с checkpoint.

## Оценка (`evaluate.py`)

Сравнивает alignment с ground truth (Whisper). Слоги с
`hasGroundTruth=false` НЕ сравниваются (это **ad-libs**, для которых
нет человеческого тайминга).

## Запуск всего pipeline

```bash
# 1. Экспорт датасета (admin UI)
# 2. Обучение
cd alignment-ml
./start_trainig.sh
# 3. Inference-сервер
ALIGN_MODEL_PATH=checkpoints/mms-ft uvicorn serve:app --host 0.0.0.0 --port 8017 &
# 4. karaoke-app использует через AlignmentServiceClient
```

## Known gaps

- [ ] **Модель MMS**: точная версия (mms-300m? mms-1b?).
- [ ] **Fine-tuning hyperparameters** — train.py settings.
- [ ] **Evaluation thresholds** — когда finetuned становится лучше
      baseline (Pass 343+).
- [ ] **Что делать при ошибке align** — retry policy в
      `AlignmentServiceClient`.
- [ ] **Production deployment** — как именно запускается на проде
      (Docker? systemd? uv run?).