# Отчёт: Устранение утечки соединений с БД (BR-627)

**Спека**: [spec.md](spec.md) | **Issue**: OpenProject #410 (аудит #336, BR-627)
**Ветка**: `648-fix-connection-leak-impl` (worktree `.worktrees/br627`)
**Дата**: 2026-10-26 | **Спека не менялась** (FR-001…FR-012, SC-001…SC-005 — как есть)

## 1. Кратко

Утечка физических JDBC-соединений закрыта в трёх слоях:

1. **Код** — все 4 оставшихся фоновых потока, ходящих в БД, переведены на
   канонический `startOneShotThread` (закрытие thread-local соединения потока в
   `finally` при любом исходе). Канонический механизм доработан **одним
   аддитивным параметром** `alsoClose` — для потоков, которые ходят в две БД
   (LOCAL + SERVER): у каждого экземпляра `KaraokeConnection` свой
   `ThreadLocal`-кеш, поэтому закрытия только переданного экземпляра было бы
   недостаточно.
2. **Проверка** — `OneShotThreadWiringTest` перестала работать по фиксированному
   перечню из 2 файлов: теперь она обходит **все** файлы прикладного кода,
   обращающиеся к БД (**161 файл** из ~420 `.kt` в `karaoke-app` + `karaoke-web`),
   и требует в них канонического способа запуска. Проверка снабжена негативной
   фикстурой «до/после» (FR-004, FR-006).
3. **Документация (SSoT)** — контракт соединения дополнен (динамический охват
   проверки, параметр `alsoClose`, ссылка на регламент тестов); эксплуатационная
   справка получила способ проверки числа занятых соединений; регламент запуска
   тестов получил требование к запасу по лимиту соединений.

Переход на ограниченный пул соединений (HikariCP) **не делался** — FR-010.

## 2. Что сделано по каждому FR

| FR | Требование | Как выполнено | Где |
|---|---|---|---|
| **FR-001** | Все фоновые потоки, которым нужен БД, запускаются каноническим способом, закрывающим соединение при любом исходе | 4 места переведены на `startOneShotThread`; канонический механизм дополнен параметром `alsoClose` (закрытие соединений всех БД, к которым пошёл поток) | `karaoke-app/.../ExportAlignmentDataset.kt`, `.../StorageCacheWarm.kt`, `.../CacheEtagSizeBackfill.kt`, `.../controllers/SongEditorController.kt`, `.../KaraokeProcessWorker.kt`, `.../services/OneShotThread.kt` |
| **FR-002** | В прикладном коде не остаётся сырых фоновых потоков (`kotlin.concurrent.thread`) в файлах, обращающихся к БД | В `karaoke-app/src/main` и `karaoke-web/src/main` **ноль** вызовов `kotlin.concurrent.thread`; импорты `kotlin.concurrent.thread` удалены | см. § 3 «до/после» |
| **FR-003** | Проверка охватывает все файлы прикладного кода, обращающиеся к БД, а не перечень из двух | Детектор динамически находит DB-файлы по маркерам обращения к БД; охват — **161 файл** | `karaoke-app/src/test/.../services/RawThreadDbGuard.kt`, `OneShotThreadWiringTest.kt` |
| **FR-004** | Проверка отличает нарушение от допустимого случая | Сырой поток в файле **без** обращений к БД — не нарушение (тест); сырой поток в файле **с** обращением к БД — нарушение (тест) | `OneShotThreadWiringTest.kt` (2 теста) |
| **FR-005** | Поведенческая проверка подтверждает закрытие при успехе и при исключении | `OneShotThreadTest` (существовал) + 2 новых теста на `alsoClose` (успех/исключение) — 5 тестов, все зелёные | `karaoke-app/src/test/.../services/OneShotThreadTest.kt` |
| **FR-006** | Регрессионная проверка подтверждает, что до правки те же сценарии давали незакрытое соединение («до/после») | Негативная фикстура: копия **реального** DB-файла с внедрённым `thread {` обязана быть найдена; **отдельно** проверено на исходном коде — тест падает с указанием файла и строки | `OneShotThreadWiringTest.kt` + ручной прогон § 3 |
| **FR-007** | Документация содержит правило закрытия по типам потока, признанное ограничение, компенсирующую меру и ссылку на решение | Всё это — в одном месте: `knowledge/domains/persistence/domain.md` (§ «Соединение на поток»); ограничение и мера — там же и в ADR | `knowledge/domains/persistence/domain.md` |
| **FR-008** | Эксплуатационная справка описывает признак исчерпания и способ проверки состояния | Добавлен `SELECT count(*) FROM pg_stat_activity;` + сравнение с `max_connections`; явно сказано, что «перезапустить БД» не решает проблему | `knowledge/system/infra/deploy-overview.md` |
| **FR-009** | Регламент запуска тестов называет, что тестовые потоки тоже оставляют соединения, и требуемый запас по лимиту | Новый подраздел «Прогон backend-тестов и лимит соединений БД»: тестовые потоки оставляют соединения; запас `max_connections=300` (`DB_MAX_CONNECTIONS`); команда проверки | `knowledge/system/infra/ci-tools.md` |
| **FR-010** | НЕ переводить работу с БД на ограниченный пул соединений | Пул не вводился; HikariCP не добавлялся; контракт ThreadLocal сохранён | — |
| **FR-011** | НЕ менять публичные контракты API и не требовать миграции данных | HTTP-эндпоинты, ответы и схема БД не тронуты; миграций нет; изменён только **внутренний** helper `startOneShotThread` (аддитивный параметр) | `karaoke-app/.../services/OneShotThread.kt` |
| **FR-012** | Каждое изменение прикладного кода сопровождается обновлением SSoT в том же PR | Обновлены 4 документа `knowledge/` + создан per-feature документ | см. § 5 |

### Изменённые файлы прикладного кода (6)

| Файл | Что изменилось |
|---|---|
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/OneShotThread.kt` | аддитивный параметр `alsoClose: List<KaraokeConnection>`; закрытие дополнительных соединений в том же `finally`; KDoc |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/ExportAlignmentDataset.kt` | `thread {` → `startOneShotThread(WORKING_DATABASE, "export-alignment-dataset")`; импорт `kotlin.concurrent.thread` удалён |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StorageCacheWarm.kt` | `thread {` → `startOneShotThread(database, "cache-warm")`; импорт удалён |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/CacheEtagSizeBackfill.kt` | `thread {` → `startOneShotThread(Connection.local(), "cache-etag-size-backfill")`; импорт удалён |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt` | `thread {` → `startOneShotThread(WORKING_DATABASE, "approve-sync-related", alsoClose = listOf(Connection.remote()))`; импорт удалён |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` | поток очереди: добавлен `alsoClose = listOf(Connection.remote())` (ветка sync-batch ходит в SERVER) |

## 3. «До/после» — с фактами

### 3.1. Сырые потоки в прикладном коде

**До** (на коммите `19d1f173`, база ветки):

```console
$ grep -rnP '(?<![\w.])thread\s*\{|(?<![\w.])thread\s*\((?!\s*\))' --include='*.kt' \
      karaoke-app/src/main karaoke-web/src/main
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/ExportAlignmentDataset.kt:58:    thread {
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt:556:                    thread {
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StorageCacheWarm.kt:59:    thread {
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/CacheEtagSizeBackfill.kt:75:    thread {
```

**После**:

```console
$ grep -rnP '(?<![\w.])thread\s*\{|(?<![\w.])thread\s*\((?!\s*\))' --include='*.kt' \
      karaoke-app/src/main karaoke-web/src/main karaoke-db/src/main
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/AutoOneClickSyncScheduler.kt:78:     * `@Volatile` — visibility между scheduler-thread и request-thread (Tomcat).
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt:202:     * - фоновые задачи API-контроллеров (`thread { … }`).
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt:398:            // Не закрываем соединение — оно per-thread (см. KaraokeConnection.getConnection).
```

Остались **только комментарии** — кода с сырыми потоками нет. Детектор снимает
комментарии перед разбором, поэтому они нарушением не считаются.

### 3.2. Проверка падает на состоянии «до» (FR-006)

Файл `ExportAlignmentDataset.kt` временно возвращён в состояние «до правки»
(`git checkout -- <файл>`), тест запущен без изменений:

```console
$ env -u DISPLAY ./gradlew :karaoke-app:test \
      --tests "com.svoemesto.karaokeapp.services.OneShotThreadWiringTest" ...
OneShotThreadWiringTest > во всех файлах прикладного кода с обращением к БД нет сырых фоновых потоков() FAILED
    org.opentest4j.AssertionFailedError at OneShotThreadWiringTest.kt:109

AssertionFailedError: в файлах прикладного кода, обращающихся к БД, найдены сырые фоновые потоки
(утечка thread-local соединения потока):
  .../ExportAlignmentDataset.kt:58 [KOTLIN_THREAD] thread {
```

Файл восстановлен → тест снова зелёный.

### 3.3. Проверка зелёная на состоянии «после»

```console
$ env -u DISPLAY ./gradlew :karaoke-app:test \
      --tests "com.svoemesto.karaokeapp.services.OneShotThreadWiringTest" \
      --tests "com.svoemesto.karaokeapp.services.OneShotThreadTest" ...
BUILD SUCCESSFUL
[OneShotThreadWiringTest] охват: файлов прикладного кода, обращающихся к БД — 161; сырых фоновых потоков — 0
```

Состав тестов (11):

| Тест | Что доказывает |
|---|---|
| `OneShotThreadWiringTest > в аудируемых файлах нет сырых одноразовых потоков` | адресный слой (4 файла BR-038/BR-060) |
| `OneShotThreadWiringTest > поток очереди запускается через startOneShotThread` | поток очереди — канонический способ |
| `OneShotThreadWiringTest > во всех файлах прикладного кода с обращением к БД нет сырых фоновых потоков` | FR-002 + FR-003 + SC-003 (161 файл) |
| `OneShotThreadWiringTest > негативная фикстура — внедрённый сырой поток в файле с обращением к БД обнаруживается` | FR-006: «до/после» на копии реального файла |
| `OneShotThreadWiringTest > сырой поток в файле без обращений к БД нарушением не считается` | FR-004 (различение) |
| `OneShotThreadWiringTest > сырой поток в файле с обращением к БД нарушением является` | FR-004 (обратная сторона) |
| `OneShotThreadTest > одноразовый поток закрывает своё thread-local соединение` | FR-005 (успех) |
| `OneShotThreadTest > соединение одноразового потока закрывается даже если тело упало` | FR-005 (исключение) |
| `OneShotThreadTest > поток одноразовой задачи — daemon и запускается` | инфраструктурный инвариант |
| `OneShotThreadTest > закрываются соединения обеих БД, к которым пошёл поток` | FR-001 для потока, ходящего в две БД |
| `OneShotThreadTest > закрываются соединения обеих БД даже если тело упало` | то же при исключении |

## 4. Проверки, которые прогнаны

### 4.1. Контрактный прогон CI (R-47, `knowledge/guidelines/architecture-conventions.md`)

Список `run:`-команд **извлечён парсингом** `.github/workflows/lint.yml`
(36 команд), а не по памяти:

```console
$ python3 - <<'PY' > /tmp/ci_runs.txt   # извлечение списка
... (парсинг .github/workflows/lint.yml)
PY
$ while read -r cmd; do cmd="${cmd//\$\{\{ matrix.spa \}\}/webvue3}"; \
      eval "$cmd" >/tmp/g.out 2>&1 || echo "НЕ-НУЛЕВОЙ: $cmd"; done < /tmp/ci_runs.txt
```

Итоговый результат (после коммитов): **36 из 36 — OK**, ненулевых нет.

Первый прогон (до создания `report.md` и добавления новых файлов в индекс) дал
одну ненулевую команду — `python3 tools/check-doc-references.py`: она жаловалась
на несуществующий на тот момент `report.md` и на неотслеживаемый git'ом
`RawThreadDbGuard.kt` (инструмент ищет файлы через `git ls-files` — ровно то,
что видит CI). После создания отчёта и коммита — обе находки исчезли.

### 4.2. Пять гейтов знаний

```console
$ bash tools/check-knowledge-structure.sh
OK: knowledge/ structure valid (9/9 checks passed)

$ bash tools/check-knowledge-cross-links.sh
OK: All 740 cross-links valid

$ python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt
All documentation passes architectural style and structural checks.

$ python3 tools/check-doc-references.py
Проверено: ссылок 4531, путей 3290, эндпоинтов 366; whitelist: 57 записей.
OK: битых ссылок, несуществующих путей и эндпоинтов нет.

$ python3 tools/check-ssot-impact.py
[SSoT] Checking impact surface against base 'origin/master'
[1/3] Found 21 changed files (base=origin/master)
[2/3] Filtered to 6 significant files (excluded 15)
[3/3] Checked 0 rules; 0 violation(s)
OK: all matched SSoT-impact rules satisfied.
```

### 4.3. Прочие гейты

| Проверка | Результат |
|---|---|
| `bash tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md` | OK (все документы валидны, включая новый `docs/features/fix-connection-leak.md`) |
| `./gradlew ktlintCheck` | BUILD SUCCESSFUL (после правки двух замечаний `chain-wrapping`/`indent` в новом тестовом файле) |
| `bash tools/check-kdoc-coverage.sh --strict` | OK — 96.9% (591/610) |
| `bash tools/check-jsdoc-coverage.sh webvue3` / `karaoke-public` | OK — 98.1% (204/208) |
| `cd webvue3 && npm run lint:check && npx prettier --check …` | OK |
| `cd karaoke-public && npm run lint:check && npx prettier --check …` | OK |
| `bash tools/check-no-jpa-imports.sh --quiet` | OK |
| `bash tools/check-no-mp4-mentions.sh --quiet` | OK |
| `bash tools/check-value-placeholders.sh` | OK |
| `bash tools/check-spec-issue-link.py` | OK |
| `bash tools/check-direct-commits.sh` (+ `--self-test`) | OK |
| `git ls-files \| grep -iE '\.env$\|do\.env$\|\.key$\|\.pem$\|\.p12$\|\.pfx$'` | пусто (Constitution VIII.3) |
| `pre-commit run --all-files` | 33 хука — все Passed (см. примечание ниже) |
| `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin` | BUILD SUCCESSFUL |
| `./gradlew :karaoke-web:bootJar :karaoke-app:bootJar` | BUILD SUCCESSFUL |
| `./gradlew :karaoke-app:test :karaoke-web:test` (с `env -u DISPLAY`) | BUILD SUCCESSFUL — 879 + 212 тестов, 0 падений |

**Примечание про `pre-commit`:** хук `eslint (karaoke-public public SPA)` в первом
прогоне упал с кодом 2 — не из-за кода, а из-за отсутствия
`karaoke-public/node_modules` в worktree (ESLint без конфига не стартует).
После подключения `node_modules` из общего кэша хук проходит. Фронтенд в этой
задаче **не менялся** (0 файлов в `webvue3/` и `karaoke-public/`), поэтому шаги
сборки Vite и сборки Docker-образов не выполнялись — они относятся к изменениям
фронтенда и Dockerfile.

### 4.4. Полный прогон backend-тестов (SC-002, SC-004)

```console
$ env -u DISPLAY ./gradlew :karaoke-app:test :karaoke-web:test --max-workers=2 \
      -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true
BUILD SUCCESSFUL
karaoke-app: tests=879 failures=0 errors=0
karaoke-web: tests=212 failures=0 errors=0
```

Падений из-за исчерпания соединений БД (`sorry, too many clients already`) нет;
тесты, требующие живого окружения, помечены `@Disabled` и в прогон не входят.

## 5. SSoT-синхронизация (FR-012)

| Документ | Что изменилось |
|---|---|
| `knowledge/domains/persistence/domain.md` | контракт соединения: параметр `alsoClose` и правило «поток, ходящий в две БД»; описание новой проверки (динамический охват, негативная фикстура); ссылка на регламент прогона тестов; уточнение статуса «третьего типа» и остатка по `withDb`; ловушка № 4 дополнена прецедентом BR-627 |
| `knowledge/system/infra/deploy-overview.md` | способ проверки состояния БД (`SELECT count(*) FROM pg_stat_activity;`) и явное «перезапустить БД» ≠ решение (FR-008) |
| `knowledge/system/infra/ci-tools.md` | подраздел «Прогон backend-тестов и лимит соединений БД»: тестовые потоки оставляют соединения; требуемый запас `max_connections=300`; регламент прогона (FR-009) |
| `knowledge/adr/local-0009-thread-local-connection-lifecycle.md` | датированная поправка «2026-10-26 — BR-627»: что закрыто, что не изменилось (контракт типов потока, ограничение, компенсирующая мера). ADR **не переписан**, дополнен поправкой (append-only) |
| `docs/features/fix-connection-leak.md` | **новый** per-feature документ (Constitution VI FR-009): 6 обязательных секций, инварианты, ловушки, ссылки |

Карта `.ssot-map.yml` под новые правки правил не потребовала: ни один из
изменённых файлов кода под существующие правила карты не подпадает; правки
SSoT сделаны по FR-012 спеки и Constitution IX.

## 6. Решения, принятые самостоятельно (и почему)

| # | Решение | Обоснование |
|---|---|---|
| D-1 | **Объём = вариант A (точечный фикс), пул соединений не вводится** | FR-010 спеки + ADR local-0009 (п. 5 «Альтернатива 3»). Вопрос Q1 в «Clarifications» спеки остался без записанного ответа владельца — реализация следует FR-010, который прямо запрещает пул в этой задаче; правку спеки не делал |
| D-2 | **Аддитивный параметр `alsoClose` в `startOneShotThread`** | Поток sync-related в `SongEditorController` и поток очереди в `KaraokeProcessWorker` ходят в ДВЕ БД; у каждого `KaraokeConnection` свой `ThreadLocal`, поэтому закрытия только переданного экземпляра было бы недостаточно (та же утечка). Альтернатива «ручной `finally` внутри тела» отклонена: закрытие перестало бы быть гарантией канонического механизма |
| D-3 | **Проверка остаётся JUnit-тестом модуля, а не shell-guard'ом в `tools/`** | Лид указал `OneShotThreadWiringTest` как точку расширения; отдельный guard потребовал бы полной регистрации по R-47.3 (скрипт + CI-джоб + pre-commit + `--self-test`) и создал бы второй механизм контроля того же правила (R-48) |
| D-4 | **«Сырой фоновый поток» = `kotlin.concurrent.thread` (все формы) + `java.lang.Thread { … }` кроме `addShutdownHook`** | FR-002 называет `kotlin.concurrent.thread`; Kotlin регистрозависим, поэтому `thread` и `Thread` — разные идентификаторы. `Thread {` в файлах с БД встречается ровно в двух местах — оба JVM shutdown hook'а (не фоновые рабочие потоки). Исключение сделано **по конструкции**, а не списком файлов |
| D-5 | **Закрытие соединения к SERVER в потоке очереди (`KaraokeProcessWorker`) — тоже поправлено** | Тот же класс утечки, найден при разборе; правка в одну строку через `alsoClose`. Формально FR-001 этого не требовал (поток уже шёл каноническим способом), но не закрывать его означало бы оставить половину фикса |
| D-6 | **`println` в затронутых файлах не переводились на SLF4J** | Предсуществующий долг вне контракта спеки (FR-011: поведение не меняем). Новый код `println` не добавлялся |
| D-7 | **Комментарии и строковые литералы снимаются перед разбором** | В прикладном коде есть KDoc-упоминания `thread { … }` (`KaraokeConnection.kt:202`, `AutoOneClickSyncScheduler.kt:78`) — без снятия комментариев проверка давала бы ложные срабатывания. Код внутри строковых шаблонов `${…}` при этом сохраняется |
| D-8 | **Спека не менялась** | Спека — контракт. Все невыполнимое/неоднозначное зафиксировано здесь, в отчёте |

## 7. Что осталось на владельца

1. **SC-001 / SC-004 — измерение на живом окружении.** «Число занятых соединений
   вернулось к исходному после 20 фоновых операций» и «очередь остаётся
   отзывчивой во время полного прогона проверок» — измеримы только на живой БД и
   живом приложении. Автоматическая часть (детект + поведенческий тест) сделана;
   процедура измерения — в `quickstart.md` (сценарий 5). Требует запуска 20 фоновых
   операций в админке.
2. **Ответ владельца на Q1 (объём)** в «Clarifications» спеки не записан. Реализация
   соответствует FR-010 (вариант A). Если владелец выберет вариант B (пул
   соединений в этой же задаче) — это отдельная спека с замерами.
3. **Тип «завершаемый пулом»** — признанное ограничение остаётся: правила закрытия
   для него нет. В прикладном коде таких потоков нет (`Dispatchers.IO/Default` не
   используются), компенсирующая мера — `max_connections=300`. Снятие ограничения —
   переход на ограниченный пул соединений (отдельная задача).
4. **Семейство `withDb` в 16 контроллерах** (включая `SongEditorController`) —
   закрывает соединение в потоке Tomcat; это другой класс дефекта (BR-006/027/116),
   уже зафиксированный в «Известные TODO» домена persistence. В этой задаче не
   трогалось.
5. **Граница статического анализа.** `Thread({ … }, "name")` (SAM-лямбда в скобках)
   и `Thread(runnable, "name")` в файле с обращением к БД детектором не ловятся:
   статически не отличить одноразовый рабочий поток от фабрики потока пула. В
   прикладном коде таких мест нет; если появятся — правило надо будет уточнить.

## 8. Процесс (обязательные шаги проекта)

| Шаг | Артефакт | Итог |
|---|---|---|
| `/speckit-plan` | [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [quickstart.md](quickstart.md), [contracts/connection-lifecycle.md](contracts/connection-lifecycle.md) | Constitution Check — PASS (нарушений нет) |
| `/speckit-tasks` | [tasks.md](tasks.md) | 32 задачи в 6 фазах; по каждому FR — покрытие |
| `/speckit-analyze` | разбор согласованности (ниже) | 0 CRITICAL; 2 замечания учтены в реализации |
| `/speckit-implement` | код + тесты + SSoT | см. § 2–5 |

### Результат `/speckit-analyze`

| ID | Категория | Severity | Место | Суть | Что сделано |
|---|---|---|---|---|---|
| A1 | Underspecification | MEDIUM | spec.md FR-003 | не определено, что значит «файл, обращающийся к БД» | определено в research.md D-3 (маркеры обращения к БД) и в KDoc детектора |
| A2 | Underspecification | MEDIUM | spec.md § Clarifications, Q1 | ответ владельца не записан | реализация следует FR-010 (вариант A); решение зафиксировано в § 6 D-1 и § 7 п. 2 |
| A3 | Coverage | MEDIUM | spec.md SC-001, SC-004 | измеримы только на живом окружении | автоматическая часть сделана; процедура измерения — quickstart § 5; остаётся владельцу (§ 7 п. 1) |
| A4 | Duplication | LOW | spec.md § Edge Cases ↔ FR-001/FR-005 | edge case про двойное закрытие дублирует FR-005 | проверяется тестом `alsoClose` (закрытие не зависит от исхода) |
| A5 | Inconsistency | LOW | spec.md «Status: Draft» | спека осталась в статусе Draft, хотя контракт утверждён | спека не менялась (контракт); отмечено здесь |

Покрытие требований: **FR-001…FR-012 — 12 из 12** имеют задачи и реализацию;
**SC-001…SC-005 — 5 из 5** имеют измеримый способ проверки, из них SC-001 и
SC-004 требуют живого окружения. Нарушений Конституции не выявлено.

## 9. Коммиты

Ветка `648-fix-connection-leak-impl` (от `19d1f173`), без push:

| Коммит | Содержание |
|---|---|
| `adf3a058` | `fix(app): закрыть утечку соединений в фоновых потоках (BR-627, OpenProject #410)` — 6 файлов прикладного кода |
| `90f7186d` | `test(app): проверка сырых потоков — охват всех файлов с доступом к БД (BR-627)` — 3 тестовых файла |
| `d370f035` | `docs(knowledge): контракт соединения, эксплуатационная справка и регламент прогонов (BR-627)` — 4 документа `knowledge/`, per-feature документ, артефакты спеки и отчёт |

Итого в ветке: 21 файл, +2244 / −45 строк. Рабочее дерево чистое.

Шаги workflow трекера OpenProject #410: выполнено `claim-issue` (статус
`In progress`). Шаги `add-comment` / `mark-review` **не выполнялись** — по
workflow спеки они идут **после merge**, а merge не было (push/PR запрещены
задачей). Статус трекера оставлен `In progress`; следующий шаг — комментарий с
этим отчётом и `mark-review` после merge.

## 10. Ограничения соблюдены

- push / PR / merge / деплой / рестарт контейнеров — **не выполнялись**;
- `specs/336-code-audit/registry.csv` — **не менялся**;
- продовая БД — **не трогалась**;
- спека `specs/645-fix-connection-leak/spec.md` — **не менялась**;
- работа велась только в worktree `.worktrees/br627`, ветка `648-fix-connection-leak-impl`;
- `GRADLE_USER_HOME` и `DOCKER_CONFIG` — от корня репозитория
  (`/home/nsa/Karaoke/.gradle`, `/home/nsa/Karaoke/.docker`), без хардкода пути машины;
- backend-тесты запускались с `env -u DISPLAY`;
- после правок кода — прогон с `--rerun-tasks`.
