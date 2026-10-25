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
    star_token: bool|null   (BR-493: True — baseline со звёздным токеном;
                             False — сработал явный откат без него; null —
                             finetuned-чекпоинт, звёздный токен не используется)
  ошибка: 400, если use_finetuned=true, а ALIGN_MODEL_PATH не задан;
          413 — файл больше ALIGN_MAX_UPLOAD_MB или аудио длиннее
          ALIGN_MAX_AUDIO_SECONDS (проверка по заголовку до декодирования);
          415 — файл не читается как FLAC/WAV;
          503 — окружение не позволяет выравнивать (BR-494): нет uroman,
          романизация выродилась в заглушки выше порога и т.п.
```

`/align` объявлен **синхронным** обработчиком (`def`, не `async def`):
Starlette исполняет такой обработчик в threadpool, поэтому выравнивание
(загрузка 1B-модели, forward, CTC) не блокирует event loop и `GET /health`
отвечает во время запроса. Тело запроса читается из `UploadFile.file`
потоково (не `await file.read()` целиком в память).

**Режим выравнивания наблюдаем.** Откат baseline-модели на загрузку без
звёздного токена (`with_star=True` недоступен в версии torchaudio) остаётся,
но перестал быть тихим (BR-493): флаг `star_token` уходит в ответ `/align`,
причина с трессировкой печатается в stderr. Откат разрешён только для
`TypeError`/`AttributeError` («в этой версии нет with_star»); любой другой
сбой загрузки (недокачанная/битая модель, OOM) пробрасывается и даёт 5xx —
раньше он тоже приводил к тихой загрузке без звёздного токена и второй
загрузке модели.


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
- `ALIGN_MAX_UPLOAD_MB` — предел размера загружаемого файла, МиБ
  (по умолчанию **64**). Превышение — **413**, в том числе по
  `Content-Length` до разбора multipart.
- `ALIGN_MAX_AUDIO_SECONDS` — предел длительности аудио в секундах
  (по умолчанию **600**). Проверяется через `soundfile.info` по
  заголовку контейнера, **до** декодирования в PCM.

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

**Романизация обязательна (BR-494).** MMS_FA обучен на латинизированном
тексте, поэтому `_romanize` гоняет слова через `uroman` (нужен системный
`perl`). Если `uroman` не установлен — `AlignmentSetupError` → 503 в
`/align`, а не подстановка кириллицы «как есть»: без романизации
`_sanitize_for_vocab` вычищал весь русский текст в заглушки `"a a a …"`,
модель выдавала правдоподобные, но бессмысленные интервалы, а ответ был
`ok: true`. Та же защита срабатывает, если заглушкой заменено больше 10 %
слов (`MAX_PLACEHOLDER_RATIO`, `align.py`).

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

### Логика обучения (`train.py`)

1. `manifest.jsonl` → train.py читает.
2. `chunking.py` — разбивает длинные аудио на чанки.
3. Дообучение baseline-модели на этих чанках.
4. Результат — чекпоинт в `checkpoints/mms-ft/`.

**Словарь CTC-головы привязан к данным.** Рядом с «vocab.json» пишется
«vocab.meta.json» с отпечатком (sha256) состава обучающих чанков (файлы,
интервалы, тексты) и их количеством. Правила (`train.resolve_vocab`):

- отпечаток совпал → словарь переиспользуется (`--resume` штатно);
- отпечаток не совпал, **чекпоинтов в `--output-dir` нет** → словарь
  перестраивается по текущим данным (безопасно: `vocab_size` и
  `ignore_mismatched_sizes=True` задаются заново);
- отпечаток не совпал, **чекпоинты есть** → `SystemExit` с требованием
  отдельного `--output-dir`: перестройка сломала бы CTC-голову весов;
- словарь без метаданных (создан до BR-491) + чекпоинты → используется с
  громким предупреждением «совместимость не проверена»; без чекпоинтов —
  перестраивается.

«vocab.json» и «vocab.meta.json» пишутся **после** CTC-фильтра и проверки
«остались ли пригодные чанки», атомарно (`os.replace`): словарь от
смоук-прогона (`--limit`) или прерванного прогона больше не остаётся на
диске и не подхватывается молча полным прогоном.

**Чанки и нечитаемые примеры (BR-495/BR-496).**

- `AlignmentDataset.__getitem__` при ошибке чтения/подготовки конкретного
  чанка возвращает `None` (sentinel), запоминает индекс в
  `bad_indices` и печатает трессировку в stderr. Случайная подмена
  примера другим (`random.randrange`) убрана — она давала дубликаты в
  эпохе и тихо меняла состав выборки; `rebuild_without_bad()` пересобирает
  датасет без «плохих» индексов. Ошибки, не связанные с файлом
  (`ImportError`, `KeyError`, `TypeError`), не глотаются.
- Префильтр `exclude_unreadable_items` исключает чанки с отсутствующим/
  нечитаемым файлом или пустым интервалом **до** создания DataLoader,
  проверяя только заголовок (`soundfile.info`).
- Коллатор выбрасывает `None`-элементы; батч, целиком состоящий из
  нечитаемых чанков, — явная `RuntimeError`, а не обучение на чужой
  выборке.
- `chunking.py` режет по `max_chunk_ms` только по границам **слов**, а
  `lead_pad_ms`/`tail_pad_ms` добавляет лишь на границах настоящих пауз
  (`> silence_threshold_ms`). На принудительном резе padding затягивал в
  чанк аудио соседнего слова, которого нет в `text_chunk`, — обучение
  получало рассогласованные пары «аудио ↔ текст».

**Скрипты** (`set -euo pipefail`, BR-497):

- `start_trainig.sh` — запуск с нуля.
- `resume_trainig.sh` — продолжить с checkpoint.

Оба переходят в каталог **самого скрипта** (`dirname "$0"`), проверяют
наличие `.venv/bin/python` и файла манифеста и запускают
`./.venv/bin/python train.py …`. Манифест переопределяется
`ALIGN_MANIFEST`; остальные аргументы передаются `train.py` как есть.

**Зависимости (BR-498).** `torch` и `torchaudio` зафиксированы одной парой
(`==2.11.0`): torchaudio ABI-привязан к версии torch, независимые нижние
границы давали несовместимую комбинацию. Нижняя граница `transformers` —
`>=4.46` (`eval_strategy` с 4.41, `processing_class` с 4.46). Для `uroman`
нужен системный `perl` — требование описано в `alignment-ml/README.md`.

### Оценка (`evaluate.py`)

Сравнивает alignment с ground truth (Whisper). Слоги с
`hasGroundTruth=false` НЕ сравниваются (это **ad-libs**, для которых
нет человеческого тайминга).

**Диагностика и код возврата (BR-492).** Ошибка выравнивания и штатное
расхождение слоговой разбивки считаются **раздельно** и печатаются
разными счётчиками; при ошибке печатаются тип исключения и трессировка
(`traceback.format_exc()`). Прогон завершается кодом **2**, если не
получено ни одного измерения или выравнивание упало более чем на 5 %
строк (`MAX_ALIGN_ERROR_RATIO`). Раньше полностью упавший прогон
печатал «Нет данных для оценки» и выходил с кодом 0, то есть решение
«baseline достаточен / finetuned лучше» можно было принять по пустым
данным.

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

- **BR-492/493/494/495/496/497/498** (2026-10-25, OpenProject #392, аудит #336,
  пакет PKG-S3-20): диагностика `evaluate.py` (раздельные счётчики,
  трессировка, код 2 при пустом/сбойном прогоне); наблюдаемый и ограниченный
  `TypeError`/`AttributeError` откат `with_star` + `star_token` в ответе
  `/align`; `uroman` обязателен (503 вместо кириллицы без романизации) и порог
  заглушек 10 %; исключение нечитаемых чанков вместо случайной подмены
  (`bad_indices` + префильтр + коллатор); рез чанков по границам слов и padding
  только на настоящих паузах; скрипты обучения — `set -euo pipefail`, каталог
  скрипта, проверки venv/манифеста и `ALIGN_MANIFEST`; зафиксирована пара
  `torch==2.11.0`/`torchaudio==2.11.0`, `transformers>=4.46`, требование `perl`.
  Тесты — `alignment-ml/tests/test_evaluate_errors.py`,
  `alignment-ml/tests/test_align_modes.py`,
  `alignment-ml/tests/test_dataset_exclusion.py`,
  `alignment-ml/tests/test_chunking_boundaries.py`,
  `alignment-ml/tests/test_train_scripts.py`,
  `alignment-ml/tests/test_requirements_pin.py`. Автор: agent (Karaoke).
- **BR-489/490/491** (2026-10-24, OpenProject #370, аудит #336, пакет PKG-S2-07):
  `/align` переведён на синхронный обработчик (threadpool) — выравнивание
  больше не блокирует event loop; добавлены пределы приёма
  (`ALIGN_MAX_UPLOAD_MB`, `ALIGN_MAX_AUDIO_SECONDS`, `soundfile.info` до
  декодирования, потоковое чтение тела) и коды 413/415; словарь CTC-головы
  привязан к составу данных через «vocab.meta.json». Автор: agent (Karaoke).
- **Pass 484** (2026-09-27, spec `484-knowledge-domain-integration`): секции приведены к шаблону. Автор: agent (Karaoke).
