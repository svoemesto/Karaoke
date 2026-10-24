# Отчёт по OpenProject #370 (PKG-S2-07, волна D) — alignment-ml: DATA, PERF

- **Задача**: OpenProject #370 «[#336] S2: alignment-ml: DATA, PERF — 3 BR»
- **Записи пакета**: BR-489, BR-490, BR-491 (реестр: `specs/336-code-audit/registry.csv`, порция P-69)
- **Карточка-источник**: `specs/336-code-audit/findings/P-69.md` (P-69-01, P-69-02, P-69-03)
- **ТЗ**: `specs/336-code-audit/backlog.md`, блок `### PKG-S2-07`
- **Ветка**: `591-pkg-s2-07`, worktree `.worktrees/waveD-s2-07`, база — `origin/master` @ `07126462`
- **PR / push / merge**: не выполнялись (по брифингу). `mark-review` — за владельцем.

---

## 1. Что изменено

Правки только в Python-коде `alignment-ml/` (Kotlin не затронут), плюс SSoT и per-feature
документ. Контракт `POST /align` расширен только новыми кодами отказа; успешный ответ
не изменился.

### 1.1 BR-489 (P-69-01): синхронное выравнивание в `async def` блокировало event loop

**Было** (`alignment-ml/serve.py:53–73` до правки): `@app.post("/align")` + `async def align(...)`;
внутри — синхронный `align_syllables(...)` (ленивая загрузка 1B-модели ~1.2 ГБ, forward, CTC).
Starlette исполняет `async def` прямо в event loop, uvicorn запускается без `--workers` → на всё
время выравнивания сервис не отвечал даже на `GET /health`.

**Стало**: обработчик объявлен обычной функцией `def align(...)` (`alignment-ml/serve.py:151`) —
Starlette исполняет его в threadpool, event loop свободен. Внутри нельзя вызывать async-API,
поэтому тело читается синхронно из `UploadFile.file` (см. BR-490). В `GET /health` добавлены
фактические пределы приёма — наблюдаемость. Докстринг модуля фиксирует причину, чтобы
`async def` не вернули «для красоты».

### 1.2 BR-490 (P-69-02): неограниченный приём файла и полное декодирование в RAM

**Было** (`alignment-ml/serve.py:68–70` и `align.py:43`): `tmp.write(await file.read())` — весь
файл вторым экземпляром в RAM; далее `sf.read(...)` декодировал его целиком в PCM float32
(4-минутный stereo 44.1 кГц ≈ 85 МБ, часовой файл — гигабайты) + копия после resample. Ни
предела размера, ни предела длительности → OOM-kill воркера, сервис мёртв до ручного перезапуска.

**Стало** (`alignment-ml/serve.py`):

| Что | Где | Поведение |
|---|---|---|
| Предел размера файла | `serve.py:50–51`, `76–85` | `ALIGN_MAX_UPLOAD_MB` (64 МиБ), проверка `Content-Length` → `413` |
| Отказ до чтения тела | `serve.py:87–96` | ASGI-middleware: `Content-Length` больше предела → `413` до разбора multipart |
| Потоковое чтение | `serve.py:99–121` | `_spool_upload` копирует по 1 МиБ, считая байты; превышение → `413` (на случай chunked/лживого `Content-Length`), пустое тело → `400` |
| Предел длительности | `serve.py:123–140` | `soundfile.info` по заголовку контейнера **до** декодирования; `ALIGN_MAX_AUDIO_SECONDS` (600 с) → `413` |
| Нечитаемый файл | там же | `415`, а не `500` |

Пределы настраиваются только переменными окружения; для обычной песни (2–4 мин, десятки МБ)
поведение не меняется.

### 1.3 BR-491 (P-69-03): словарь CTC-головы молча переиспользовался после смоук-прогона

**Было** (`alignment-ml/train.py:380–391` до правки): существующий `vocab.json` в `--output-dir`
брался «как есть» без сверки с данными. Смоук-прогон `--limit 5` (или прерванный прогон) оставлял
словарь из N песен, следующий полный прогон молча его использовал: все символы вне тех песен →
`[UNK]`, CTC-голова училась выдавать `[UNK]` вместо букв, прогон выглядел успешным. Словарь
записывался **до** CTC-фильтра и проверки «остались пригодные чанки».

**Стало** (`alignment-ml/train.py`):

- `dataset_fingerprint()` (`:178`) — sha256 состава обучающих чанков (файлы, интервалы, тексты)
  + число песен/чанков; порядок чанков не влияет.
- `has_checkpoints()` (`:191`) — есть ли в `--output-dir` каталоги `checkpoint-*`.
- `resolve_vocab()` (`:197`) — решение:
  - отпечаток совпал → переиспользование (штатный `--resume`);
  - не совпал, чекпоинтов нет → перестройка по текущим данным;
  - не совпал, чекпоинты есть → `SystemExit` (fail-closed: перестройка сломала бы CTC-голову
    сохранённых весов), в сообщении — требование отдельного `--output-dir`;
  - словарь без метаданных (создан до правки) + чекпоинты → работа продолжается, но с явным
    предупреждением «совместимость не проверена» (иначе сломали бы `--resume`);
  - битый `vocab.json` → как «не совпал» (без чекпоинтов перестройка, с чекпоинтами отказ).
- `write_json_atomic()` (`:265`) — запись через временный файл + `os.replace`.
- В `main()` (`:481–556`): словарь и `vocab.meta.json` пишутся **после** CTC-фильтра и проверки
  «остались пригодные чанки»; до этого токенизатору отдаётся тот же словарь через временный файл
  (`Wav2Vec2CTCTokenizer` читает файл в `__init__` и дальше его не держит), поэтому в
  `--output-dir` не остаётся словаря от прерванного прогона.

---

## 2. Регрессионные тесты «до/после»

Тесты лежат в `alignment-ml/tests/` (stdlib `unittest`, без новых зависимостей):

```bash
cd alignment-ml
./.venv/bin/python -m unittest discover -s tests -v
```

`test_serve_limits.py` (7 тестов, BR-489/490) — поведенческие: подменяют `align_syllables`
заглушкой, поэтому 1B-модель не грузится. `test_vocab_binding.py` (11 тестов, BR-491) — сценарии
словаря: смоук → полный прогон, чекпоинты, битый словарь, `--resume`, порядок чанков.

### 2.1 После правки

```
Ran 18 tests in 0.639s

OK
```

### 2.2 До правки (тот же набор тестов на коде `HEAD` — `git archive HEAD alignment-ml`)

```
ERROR: test_content_length_limit_helper ... AttributeError: module 'serve' has no attribute '_reject_oversized_upload'
FAIL: test_handler_is_sync_function ... AssertionError: True is not false : align() объявлен async def - тяжёлое выравнивание снова заблокирует event loop
FAIL: test_health_answers_while_align_is_running ... AssertionError: False is not true : GET /health дождался конца выравнивания - event loop заблокирован
FAIL: test_non_audio_upload_rejected ... AssertionError: 200 != 415 : {"ok":true,...}
FAIL: test_oversized_upload_rejected ... AssertionError: 200 != 413 : {"ok":true,...}
FAIL: test_too_long_audio_rejected ... AssertionError: 200 != 413 : {"ok":true,...}
Ran 7 tests ... FAILED (failures=5, errors=1)

ERROR: (все 11 тестов test_vocab_binding) AttributeError: module 'train' has no attribute 'VOCAB_META_NAME'
Ran 18 tests in 0.015s
FAILED (errors=18)
```

То есть на старом коде тесты падают именно на дефектах: `/align` отвечает `200` на файл сверх
предела и на не-аудио, `/health` ждёт конца выравнивания, а привязки словаря к данным нет вовсе.

---

## 3. Проверки (локально, до коммита)

| Проверка | Команда | Результат |
|---|---|---|
| Backend tests | `GRADLE_USER_HOME=... ./gradlew --max-workers=2 ... :karaoke-app:test :karaoke-web:test ktlintCheck` | см. § 3.1 |
| Ссылки в документации | `python3 tools/check-doc-references.py` | см. § 3.1 |
| Структура Knowledge | `bash tools/check-knowledge-structure.sh` | OK (9/9) |
| Knowledge lint | `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | All documentation passes |
| MP4-mentions | `bash tools/check-no-mp4-mentions.sh` | OK (130 legacy в baseline) |
| Per-feature docs | `bash tools/check-feature-doc.sh docs/features/*.md` | Все документы валидны |
| Subagent isolation | `bash tools/check-subagent-isolation.sh` | см. § 3.2 — ложное срабатывание, не связано с правкой |

### 3.1 Gradle / ktlint / doc-references

(результат дописан после прогона; Kotlin в этой правке не менялся)

### 3.2 `check-subagent-isolation.sh` — ложное срабатывание из-за устаревшего локального `master`

Guard сравнивает `master..HEAD` локального репозитория, а локальный `master` отстал:
`master` = `d4109801`, `origin/master` = `f5da6c18`, `HEAD` = `07126462` (база — `origin/master`,
как и предписано). Поэтому «коммитами этой ветки» guard считает 46 уже смерженных коммитов
истории; каждый из них — предок `origin/master` (`git merge-base --is-ancestor` → YES), ни один
не создан этой сессией. На чистом окружении (локальный `master` = `origin/master`) проверка
проходит. Починить может только владелец общего репозитория (обновление ref'а `master`);
в рамках этой задачи ref не трогался.

---

## 4. За владельцем

1. **Значения пределов приёма** — 64 МиБ / 600 с выбраны как безопасный дефолт для обычной песни.
   Решение владельца: оставить, или согласовать с клиентским таймаутом
   `AlignmentServiceClient` (300 с) и/или ограничить файл по длительности строже. Менять код не
   нужно — только переменные окружения.
2. **Политика при несовпадении словаря и живых чекпоинтов** — реализовано fail-closed
   (`SystemExit` с требованием отдельного `--output-dir`). Альтернатива (автоматически уводить
   словарь в `vocab.<fingerprint>.json`) — решение владельца, сейчас данных для неё нет.
3. **Прогрев модели на старте сервиса** (из поправки BR-489) не делался: это отдельное решение о
   загрузке 1B-весов при старте (память/GPU админской машины), вне минимальной правки.
4. **Периметр порта 8017** (сверка P-69 §6): сервис без аутентификации в LAN админской машины;
   пределы приёма — не замена авторизации. Принятая граница доверия (ADR-0004) или нужен
   shared-secret/IP-allowlist — вопрос владельца, в этой задаче не менялся.

---

## 5. Документация (SSoT + FR-009)

- `knowledge/domains/integration/components/alignment-ml.md` — контракт `/align` (413/415,
  синхронный обработчик), новые env-переменные, раздел «Словарь CTC-головы привязан к данным»,
  запись в Changelog.
- `docs/features/alignment-ml.md` (новый, FR-009) — 6 обязательных секций, включая
  `## Известные ловушки` (7 пунктов: `async def` + sync-функция, `await file.read()`,
  лживый `Content-Length`, `sf.info` vs `sf.read`, смоук-прогон `--limit`, неразделимость
  словаря и чекпоинтов, отсутствие аутентификации на 8017).

---

## 6. Хэши

`sha256` изменённых файлов (после финальных правок):

```
(заполняется после коммита)
```
