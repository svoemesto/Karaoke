# Отчёт по OpenProject #392 (PKG-S3-20, волна E) — alignment-ml: BUILD, DATA, ERR

- **Задача**: OpenProject #392 «[#336] S3: alignment-ml: BUILD, DATA, ERR — 7 BR»
- **Записи пакета**: BR-492, BR-493, BR-494, BR-495, BR-496, BR-497, BR-498 (реестр:
  `specs/336-code-audit/registry.csv`, порция P-69)
- **Карточка-источник**: `specs/336-code-audit/findings/P-69.md` (P-69-04 … P-69-10)
- **ТЗ**: `specs/336-code-audit/backlog.md`, блок `### PKG-S3-20`
- **Ветка**: `610-pkg-s3-20`, worktree `.worktrees/waveE-s3-20`, база — `origin/master` @ `35026a20`
- **PR / push / merge / деплой**: не выполнялись (по брифингу). `mark-review` — за владельцем.

Правки только в Python-коде `alignment-ml/` (Kotlin не затронут), плюс SSoT и per-feature документ.
Карточки P-69-01/02/03 (S2, BR-489/490/491) в этом пакете НЕ трогались — они закрыты ранее
(OpenProject #370).

---

## 1. Что изменено (по записям)

### 1.1 BR-492 (P-69-04): `evaluate.py` — потеря диагностики и ложно-успешный код возврата

**Было** (`alignment-ml/evaluate.py:63–68, 97–105`): исключение при выравнивании ловилось без типа
и трессировки (`print(f"... ошибка align - {e}")`), а счётчик у него был тот же, что у штатного
расхождения слоговой разбивки (`rows_skipped_mismatch`). Прогон, на котором упало выравнивание на
каждой строке (CUDA OOM, битая модель, сломанный torchaudio), печатал «Нет данных для оценки —
проверьте путь к манифесту…» и **выходил с кодом 0**: решение «baseline достаточен / finetuned
лучше» можно было принять по пустым данным.

**Стало** (`alignment-ml/evaluate.py`):

- отдельные счётчики `rows_failed_align` и `rows_skipped_mismatch`, печатаются раздельно
  (`:60`, `:78`, `:109–110`);
- при ошибке печатаются `type(e).__name__` + сообщение (`:75`) и `traceback.format_exc()` в stderr
  (`:77`);
- порог `MAX_ALIGN_ERROR_RATIO = 0.05` (`:30`): если не получено ни одного измерения (`:120–128`)
  или доля упавших строк выше 5 % (`:129–138`) — `raise SystemExit(2)`.

### 1.2 BR-493 (P-69-05): `align.py` — тихий откат baseline-модели без звёздного токена

**Было** (`alignment-ml/align.py:103–112`): любой `except Exception` вокруг
`bundle.get_model(with_star=True)` молча (только `print` в stdout) переключал выравнивание в режим
без звёздного токена — тот самый, который докстринг модуля называет катастрофическим для
многоголосых песен (ошибки в десятки секунд). Откат кэшировался на весь процесс, в ответе `/align`
признака режима не было; второе исключение (сбой окружения) приводило ещё и ко второй загрузке
модели.

**Стало** (`alignment-ml/align.py`):

- в состоянии модели всегда есть `star_token` (True — загружено с `"*"`; False — откат) и
  `star_fallback_reason` (`:130–131`, `:141–143`, `:147–148`);
- откат разрешён **только** для `TypeError`/`AttributeError` («в этой версии torchaudio нет
  `with_star`») — `:137`; причина и трессировка уходят в **stderr** (`:138–146`);
- любой другой сбой загрузки — `raise` без второй загрузки модели и трессировка в stderr
  (`:150–155`) → `/align` отвечает 5xx, а не `ok: true`;
- `star_token_enabled()` (`:160–169`) отдаёт режим наружу; `serve.py` включает поле `star_token`
  в ответ `/align` (`serve.py:199–200`): True/False для baseline, `null` для дообученного
  чекпоинта (в этой ветке звёздный токен не используется).

### 1.3 BR-494 (P-69-06): `align.py` — кириллица без романизации превращалась в заглушки

**Было** (`alignment-ml/align.py:125–132, 147–153`): при `ImportError` (`uroman` не установлен)
романизация не выполнялась, кириллица подавалась в MMS_FA как есть, `_sanitize_for_vocab` вычищала
все символы вне латинского словаря и подставляла заглушку `"a"` на каждое слово. Итог — транскрипт
вида `"a a a …"`, правдоподобные, но бессмысленные интервалы, `/align` отвечает `ok: true`, маркеры
сохраняются в UI.

**Стало** (`alignment-ml/align.py`):

- `AlignmentSetupError` (`:42`) — отдельный тип «окружение не позволяет выравнивать»;
- `_romanize` при `ImportError` бросает `AlignmentSetupError` с текстом «uroman не установлен —
  романизация обязательна для MMS_FA…» (`:189–193`), кириллица больше не подаётся в модель;
- `_sanitize_for_vocab` считает долю слов-заглушек и отклоняет запрос при превышении
  `MAX_PLACEHOLDER_RATIO = 0.10` (`:53`, `:227–232`), каждое заменённое слово печатается в stderr;
- `serve.py` переводит `AlignmentSetupError` в **503** с причиной (`serve.py:193–197`), а не в
  `500`/`ok: true`.

### 1.4 BR-495 (P-69-07): `train.py` — «плохой» чанк подменялся случайным другим

**Было** (`alignment-ml/train.py:271–280` по снимку аудита): `except Exception` при чтении/подготовке
чанка рекурсивно брал `random.randrange(len(self.items))` — другой случайный пример. Эпоха
получала дубликаты, соответствие «индекс ↔ (аудио, текст)» ломалось, статистика «пропущено» в
`build_chunk_items` подмены не учитывала; системные ошибки (диск, библиотека) тоже глотались, а на
6-й попытке многочасовой прогон падал невнятным `raise`.

**Стало** (`alignment-ml/train.py`):

- `AlignmentDataset.PER_ITEM_ERRORS = (OSError, RuntimeError, ValueError)` (`:354`) — только
  ошибки конкретного файла/фрагмента (`soundfile.LibsndfileError` — подкласс `RuntimeError`);
- `__getitem__` (`:365`) при такой ошибке печатает трессировку в stderr (`:388–396`), добавляет
  индекс в `bad_indices` и возвращает `None` (sentinel), без рекурсии и случайной подмены
  (`:397–398`);
- `rebuild_without_bad()` (`:402–415`) пересобирает датасет без «плохих» индексов;
- `exclude_unreadable_items()` (`:417–457`) — префильтр «заранее»: до создания DataLoader
  выбрасывает чанки с отсутствующим/нечитаемым файлом или пустым интервалом, проверяя только
  заголовок (`soundfile.info`), без декодирования PCM; вызывается в `main()` (`:629–630`);
- `DataCollatorCTCWithPadding` (`:468–473`) выбрасывает `None`-элементы, а батч, целиком состоящий
  из нечитаемых чанков, — явная `RuntimeError`, а не обучение на чужой выборке;
- исключения, не связанные с конкретным файлом (`ImportError`, `KeyError`, `TypeError`),
  пробрасываются — тест это фиксирует.

### 1.5 BR-496 (P-69-08): `chunking.py` — padding на принудительном резе и рез посреди слова

**Было** (`alignment-ml/chunking.py:26–28, 154–161`): границы чанка считались по слогам, а
`lead_pad_ms=200`/`tail_pad_ms=800` добавлялись **всегда**. Если граница появилась из
принудительного реза по `max_chunk_ms` (непрерывное пение), padding затягивал в чанк начало
следующего слова и конец предыдущего, а `text_chunk` (собирается по словам) их не описывал; слово,
расщеплённое резом, попадало в текст обоих чанков. Обучение получало систематически
рассогласованные пары «аудио ↔ текст».

**Стало** (`alignment-ml/chunking.py`):

- `_enforce_max_length` (`:67–103`) режет по `max_chunk_ms` только по границам **слов**
  (перебирает текущий кусок до первого слога слова, в которое попал `idx`, `:92`); если слово само
  длиннее лимита — механический рез (такой чанк отсеивает CTC-фильтр);
- padding добавляется только на «тихих» границах: разрыв до соседней группы больше
  `silence_threshold_ms` (`:173–188`), на принудительном резе и склейке коротких кусков — 0.

### 1.6 BR-497 (P-69-09): `start_trainig.sh` / `resume_trainig.sh` — жёсткий `~/Karaoke` и отсутствие проверок

**Было**: `cd ~/Karaoke/alignment-ml` без проверки результата, без `set -e`/`set -u`, без проверки
активации venv, `source .venv/bin/activate` и `python train.py` — при чужом каталоге/пользователе
скрипт продолжал работу в текущем каталоге, активировал чужой venv (или никакой) и запускал
системный `python`; код возврата маскировал первый сбой.

**Стало** (`alignment-ml/start_trainig.sh`, `alignment-ml/resume_trainig.sh`):

- `set -euo pipefail` (`:9`);
- `cd "$(dirname "${BASH_SOURCE[0]}")"` (`:11`);
- проверка `.venv/bin/python` (`:13–17`) и файла манифеста (`:19–24`) с понятными сообщениями и
  кодом 1;
- `ALIGN_MANIFEST` переопределяет путь, остальные аргументы передаются `train.py` как есть;
- `exec ./.venv/bin/python train.py --manifest "$MANIFEST" "$@"` (`:26`); в
  `resume_trainig.sh` — `--resume`.

### 1.7 BR-498 (P-69-10): `requirements.txt` — невоспроизводимое окружение

**Было**: `torch>=2.1`, `torchaudio>=2.1`, `transformers>=4.40` — независимые нижние границы;
pip мог собрать несовместимую пару torch/torchaudio, а `transformers` 4.40–4.45 даёт `TypeError`
на `TrainingArguments(eval_strategy=…, processing_class=…)`; требование `perl` для `uroman` нигде
не описано.

**Стало** (`alignment-ml/requirements.txt`, `alignment-ml/README.md`):

- `torch==2.11.0` и `torchaudio==2.11.0` — **одной парой** (`:5–6`); 2.11.0 — последняя доступная
  версия torchaudio в индексе (`pip index versions torchaudio` → `LATEST: 2.11.0`), поэтому
  `torch` зафиксирован той же минорной версией, что и torchaudio;
- `transformers>=4.46` (`:9`) — `eval_strategy` с 4.41, `processing_class` с 4.46;
- README, раздел «Запуск»: явное требование системного `perl` для `uroman` и проверка `perl -v`;
  там же описаны скрипты-обёртки и код возврата `evaluate.py`.

---

## 2. Регрессионные тесты «до/после»

Тесты лежат в `alignment-ml/tests/` (stdlib `unittest`, новых зависимостей нет):

| Файл | BR | Что проверяет |
|---|---|---|
| `test_evaluate_errors.py` | 492 | раздельные счётчики, тип+трессировка, код 2 при пустом прогоне и при доле ошибок > 5 %, граница ровно 5 % |
| `test_align_modes.py` | 493/494 | `star_token` True/False, причина отката, отсутствие второй загрузки модели при сбое окружения, `503` для `AlignmentSetupError`, отказ без `uroman`, порог заглушек 10 % |
| `test_dataset_exclusion.py` | 495 | sentinel `None` + `bad_indices` + трессировка, отсутствие подмены, `rebuild_without_bad`, префильтр, фильтр `None` в коллаторе, проброс `ImportError` |
| `test_chunking_boundaries.py` | 496 | рез по границам слов, отсутствие padding на принудительном резе, сохранение padding на настоящей паузе, работа с `--lead-pad-ms 0` |
| `test_train_scripts.py` | 497 | запуск из каталога скрипта, `./.venv/bin/python`, коды 1 без venv/манифеста, `--resume`, `set -euo pipefail` |
| `test_requirements_pin.py` | 498 | одна пара torch/torchaudio, `transformers>=4.46`, `perl` в README |

Запуск:

```bash
cd alignment-ml
./.venv/bin/python -m unittest discover -s tests -v
```

### 2.1 После правки

```
Ran 52 tests in 3.297s

OK
```

(18 прежних тестов BR-489/490/491 + 34 новых.)

### 2.2 До правки (те же тесты на коде `HEAD`, изменения спрятаны `git stash push -- alignment-ml`)

```
Ran 52 tests in 0.750s

FAILED (failures=17, errors=19)
```

Падают 32 из 34 новых тестов — на дефектах, а не на отсутствии символов: `evaluate` не выходит с
кодом 2 и не печатает трессировку, `/align` не отдаёт `star_token`, `_romanize` возвращает
кириллицу, `_sanitize_for_vocab` не бросает, датасет подменяет пример, чанки получают padding на
принудительном резе, скрипты уходят в `~/Karaoke`, `torch` не зафиксирован. Два теста проходят на
старом коде намеренно — это защита от пере-исправления, а не «до/после»:

- `test_placeholder_ratio_at_threshold_allowed` — ровно 10 % заглушек не должны отклонять запрос;
- `test_quiet_boundary_keeps_padding` — на настоящей паузе padding обязан остаться.

---

## 3. Проверки (локально, до коммита)

| Проверка | Команда | Результат |
|---|---|---|
| Backend tests (app) | `GRADLE_USER_HOME=... ./gradlew --max-workers=2 ... :karaoke-app:test` | `BUILD SUCCESSFUL` (`:karaoke-app:test FROM-CACHE` — Kotlin не менялся) |
| Backend tests (web) | то же `:karaoke-web:test` | `BUILD SUCCESSFUL` (`FROM-CACHE`) |
| Kotlin lint | `./gradlew ... ktlintCheck` | `BUILD SUCCESSFUL` (19 tasks, кэш) |
| Python tests | `cd alignment-ml && ./.venv/bin/python -m unittest discover -s tests` | `Ran 52 tests … OK` |
| Knowledge structure | `bash tools/check-knowledge-structure.sh` | `OK: knowledge/ structure valid (9/9 checks passed)` |
| Knowledge lint | `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | `All documentation passes …` |
| Doc references | `python3 tools/check-doc-references.py` | см. § 3.1 |
| Recordhash | `python3 tools/check-recordhash-consistency.py` | `OK recordhash-контракт …` |
| MP4 mentions | `bash tools/check-no-mp4-mentions.sh` | `OK: all 139 MP4 mentions are in baseline` |
| Per-feature docs | `bash tools/check-feature-doc.sh docs/features/*.md` | `Все документы валидны` |
| Subagent isolation | `bash tools/check-subagent-isolation.sh` | см. § 3.2 |

### 3.1 `check-doc-references.py`

После создания отчёта и правки относительных путей в документации:

```
Проверено: ссылок 4297, путей 2514, эндпоинтов 331; whitelist: 51 записей.
OK: битых ссылок, несуществующих путей и эндпоинтов нет.
```

### 3.2 `check-subagent-isolation.sh`

Guard отрабатывает по `master..HEAD`; на ветке ожидаемо один коммит (правки пакета), чужих
worktree/checkout не затрагивал. До коммита guard печатал «ветка не содержит новых commits» — это
штатное поведение пустой ветки, а не срабатывание изоляции.

---

## 4. За владельцем

1. **Значения порогов.** `MAX_PLACEHOLDER_RATIO = 0.10` (доля слов-заглушек) и
   `MAX_ALIGN_ERROR_RATIO = 0.05` (доля упавших строк `evaluate.py`) взяты из карточки аудита как
   разумный дефолт. Менять код не нужно — это константы модулей; если на реальном датасете
   (10–14 % штатных расхождений разбивки) порог окажется неудобным, решение — за владельцем.
2. **Пара версий torch/torchaudio.** Зафиксирована `2.11.0` (последняя доступная torchaudio в
   индексе `pip index versions`). Локальный venv админской машины сейчас содержит
   `torch 2.13.0` + `torchaudio 2.11.0` — несовместимую пару; пересборка venv по
   `requirements.txt` — действие владельца (я venv не трогал, тесты гонял интерпретатором
   существующего venv, только читая его).
3. **Реальный прогон обучения.** Тесты проверяют обвязку (чанки, датасет, скрипты), но не
   качество дообучения: GPU/датасета в этой сессии не было, обучение и модели не запускались
   (как и в аудите).
4. **Периметр `serve.py`** (сверка P-69 §6) и **retry-политика клиента** (P-69 §8) — не менялись;
   остаются открытыми вопросами владельца (LAN без аутентификации; `AlignmentServiceClient`
   возвращает `null` без повторов).

---

## 5. Документация (SSoT + FR-009)

- `knowledge/domains/integration/components/alignment-ml.md` — контракт `/align`
  (`star_token`, 503), обязательная романизация и порог заглушек, наблюдаемый откат `with_star`,
  исключение нечитаемых чанков, границы чанков и padding, скрипты и `ALIGN_MANIFEST`, пины
  зависимостей и `perl`, диагностика `evaluate.py`; запись в Changelog.
- `docs/features/alignment-ml.md` (FR-009) — обновлены статус/спека (`#392`), разделы
  «HTTP-сервис», «Пайплайн обучения», «Обучение и оценка», «Тесты», «Инварианты» и
  `## Известные ловушки` (добавлены ловушки романизации, `except Exception` вокруг загрузки
  модели, случайной подмены чанка и padding на принудительном резе).

---

## 6. Хэши

Хэш коммита с правками и отчётом — в комментарии к #392 и `git log -1 --format=%H` ветки
`610-pkg-s3-20` (хэш коммита не вписан в сам отчёт: amend изменил бы его).
`sha256` изменённых файлов:

```
5678183a95271ac274272fe7263990f827cdab15a4b2036fb29d413c1563e8c2  alignment-ml/align.py
b8da96a5a25a76e2d528d04055a090da314c91f081c884cc6e802b25690d969e  alignment-ml/serve.py
8c149b970e0b6d6969a2750f27fd06eab0ef79110909d8a12349474548606e97  alignment-ml/evaluate.py
fb1de9e6b3aad5ed77797414234bd0fb3994c3c12aaa6a07d3506ab632d7fc75  alignment-ml/train.py
ae3d78b24adc3f0fea193e866256d924ba134da8e6db703cb04e6f4b6c9f59e3  alignment-ml/chunking.py
10af5e05dd45f08975f96f34cdaaa938348d4f2638b03575d13654a5eb7d8a86  alignment-ml/start_trainig.sh
44572397e2da028017c844979d46dfd5551db5050737006491654caeb0e037b7  alignment-ml/resume_trainig.sh
26c716b2d538c1c952d52a27c2a6e4cd2aa96d6253c9b33e239e696f13cf9d6f  alignment-ml/requirements.txt
94d7e2bedf5690780c2f713ffdecbf74b06fcab9b23449094a1802e980faa33a  alignment-ml/README.md
d90e54cebb58973247e8dac797a4187d419385babc8ff60314b78a38833cfe66  alignment-ml/tests/test_evaluate_errors.py
8ffeaba0abc7ae045d2fb9407e479246ce343547c8a0f8d676d327b0547c0876  alignment-ml/tests/test_align_modes.py
8a80aab4e092a3036e52a6d39595e51e0858bce85f57e1bb5f5c64bb383be841  alignment-ml/tests/test_dataset_exclusion.py
4223a1fc2cd4d83e072508ee0c6323fea43d2b719686ac696e09bb864f137cfc  alignment-ml/tests/test_chunking_boundaries.py
55785c6d50d93dd3ee2cb2ba13b033dd25c60d6127f46a53d38437db47d24f21  alignment-ml/tests/test_train_scripts.py
f4efbadce0cd43d9830ec9ffd80912a60365858133f67ed5504af2d7fb2f8790  alignment-ml/tests/test_requirements_pin.py
85fcc8d614b535cd4f3d72fceaf54c233a304321d8a799d468c7ccf8c63b9a41  knowledge/domains/integration/components/alignment-ml.md
8e8e827222b78d6006b8b18afcd662a24d5390c4c71165fa910e983636baf8e3  docs/features/alignment-ml.md
```
