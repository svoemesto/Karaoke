# Implementation Plan: Устойчивый импорт файлов из папки без падения по памяти

**Branch**: `082-fix-import-folder-oom` | **Date**: 2026-07-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/082-fix-import-folder-oom/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

«Добавить файлы из папки» падает по нехватке памяти (Java Heap Out Of
Memory) на больших/глубоко вложенных папках. Технический подход
(см. `research.md`): (1) `Utils.getListFiles` сейчас материализует **весь**
рекурсивный обход дерева (`Files.walk(...).toList()`, без фильтрации по
расширению на этом шаге и без закрытия `Stream`) в память одним списком,
причём делает это многократно из-за того, как перегрузка с несколькими
расширениями переиспользует однорасширенческую версию — переписать на
однопроходный, фильтрующий во время обхода и корректно закрываемый (`.use{}`)
проход; (2) тяжёлая пост-обработка каждой новой песни в
`ApiController.doCreateFromFolder`/`MainController.doCreateFromFolder`
(поиск дубликата, поиск аудио-родителя по звучанию, поиск текста песни,
неограниченный фоновый `thread(start = true)` на песню без найденного
текста) сейчас не имеет верхней границы одновременно запущенных фоновых
операций — ограничить конкурентность и не прерывать всю операцию из-за
одной проблемной песни; (3) по User Story 2 верификация показала, что
кортеж заданий, как его определяет `spec.md` (демукс → mp3 музыки/голоса →
загрузка в оба хранилища), уже корректно и единообразно формируется в
`threadId=1` (`THREAD_LANE_HEALTH_REPORT`) — правка кода не требуется.
`KEY_BPM_FROM_FILE` ставится в отдельный лейн (`threadId=2`,
`THREAD_LANE_STEM_JOBS`) **намеренно**: он не входит в кортеж и не должен
занимать его слот (подтверждено пользователем при ревью планирования —
первый проход ошибочно посчитал это багом). Вместо правки `threadId`
добавляется поясняющий комментарий в `Song.createFromPath`, чтобы это
архитектурное решение не спровоцировало повторную ошибочную "починку" в
будущем.

## Technical Context

**Language/Version**: Kotlin 2.x, Spring Boot 3.x, JDK 17 — модуль `karaoke-app` (only; фикс не затрагивает `karaoke-web`/фронтенды)

**Primary Dependencies**: `java.nio.file` (`Files.walk`/`DirectoryStream`) для обхода папки; существующие `KaraokeProcess`/`HealthReport` (очередь и каскад автоисправления); Playwright (`YandexLyricsSearchOutcome` — поиск текста), `SearXNG` (фоновый поиск текста через `getLyricsSearch`), сравнение по звучанию (`findAudioParentByWaveform`) — новых внешних зависимостей не требуется, существующие вызовы сохраняются, но ограничиваются по конкурентности

**Storage**: PostgreSQL через сырой JDBC (`tbl_processes`, `tbl_songs`) — новых колонок/миграций не предполагается; изменения только в логике постановки заданий и обхода файловой системы

**Testing**: В CI автотестов почти нет для `karaoke-app` (`AGENTS.md`: «не полагайся на них»); ИСКЛЮЧЕНИЕ — `Utils.getListFiles` является чистой функцией без сети/БД/браузера (работает с временными файлами на диске), поэтому для неё целесообразен узкий unit-тест по образцу уже существующего `SpecTagsTest` (чистая логика, без сети/credentials — не подпадает под предупреждение AGENTS.md о непригодных для CI интеграционных тестах). Устойчивость к OOM на реальном большом дереве и корректность кортежа заданий в очереди — ручная проверка по `quickstart.md` на dev-pc

**Target Platform**: admin-машина (`karaoke-app` в Docker), Linux; на прод-сервере `karaoke-app` не разворачивается (сервер не затронут этим фиксом)

**Project Type**: точечный бэкенд-фикс в одном модуле (`karaoke-app`); фронтенд (`webvue3`) не меняется — кнопка/диалог «Добавить файлы из папки» и итоговое SSE-уведомление остаются как есть

**Performance Goals**: обход и импорт папки с несколькими тысячами файлов не должен приводить к росту потребления памяти, пропорциональному числу файлов (SC-002); полный кортеж заданий (демукс → mp3 → загрузка локально → загрузка удалённо) формируется для 100% новых песен без ручного вмешательства (SC-003)

**Constraints**: НЕЛЬЗЯ нарушать `redirectErrorStream(true)` (Principle IV) — фикс не меняет запуск subprocess; НЕЛЬЗЯ вводить JPA/Hibernate (Principle II) — только сырой JDBC, уже используемый; НЕЛЬЗЯ вводить новые внешние SaaS-зависимости в горячий путь (Principle I) — существующие Playwright/SearXNG вызовы сохраняются по сути, только ограничиваются по конкурентности, а не заменяются; кортеж заданий ДОЛЖЕН оставаться в одном согласованном лейне (`THREAD_LANE_HEALTH_REPORT`), как и весь остальной каскад `HealthReport`

**Scale/Scope**: единственный экземпляр `karaoke-app` на admin-машине; целевой сценарий — папка (с произвольной вложенностью подпапок) с несколькими тысячами аудиофайлов, включая массовое одновременное добавление многих новых песен за одну операцию

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн (NON-NEGOTIABLE) | PASS | Playwright/SearXNG/waveform-сравнение остаются локальными вызовами внутри `karaoke-app`; фикс лишь ограничивает их конкурентность, новых внешних SaaS не добавляет. |
| II. Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE) | PASS | Все запросы к `tbl_processes`/`tbl_songs` в затронутом коде уже идут через `KaraokeConnection`/сырой JDBC; новых ORM-зависимостей не вводится. |
| III. Двух-БД синхронизация через SyncRegistry | N/A | `tbl_processes` не входит в `SyncRegistry.all` (очередь заданий локальна для инстанса); импорт песен (`tbl_songs`) уже участвует в sync и этой фичей не меняется. |
| IV. Async-очередь задач с парсингом stdout (NON-NEGOTIABLE) | PASS (целевой принцип для US2) | Кортеж заданий (демукс → mp3 → upload local/remote) — часть уже существующей `threadId`-лейновой модели; верификация подтвердила, что кортеж уже единообразно в `THREAD_LANE_HEALTH_REPORT` (правка кода не требуется), `KEY_BPM_FROM_FILE` намеренно в отдельном лейне. Новых лейнов фикс не вводит, `redirectErrorStream` не меняет. |
| V. Двух-фронтенд: админка и публичный сайт | N/A | Фронтенд не меняется — кнопка/диалог `webvue3` и SSE-уведомление об итоге импорта остаются как есть. |
| VI. Code Standards (NON-NEGOTIABLE) | PASS (обязательства на этапе реализации) | Изменённые публичные функции (`getListFiles`, `createFromPath`) — с обновлённым KDoc (`@see docs/features/async-process-queue.md`); ktlint clean; `docs/features/async-process-queue.md` обновляется в том же PR (FR-009). |
| VII. Cross-Machine Setup | N/A | Личных AI-конфигов/cross-machine файлов фикс не касается. |
| Ограничения агента (dev-pc/dev) | OK | Работа ведётся на `dev-pc`/`dev` — разрешены локальные пересборка/перезапуск `karaoke-app` и операции с локальной БД без отдельного согласия (см. Governance-исключение в constitution.md); прод не затрагивается. |

Нарушений, требующих секции «Complexity Tracking», не выявлено.

## Project Structure

### Documentation (this feature)

```text
specs/082-fix-import-folder-oom/
├── plan.md               # This file (/speckit.plan command output)
├── research.md           # Phase 0 output (/speckit.plan command)
├── data-model.md          # Phase 1 output (/speckit.plan command)
├── quickstart.md         # Phase 1 output (/speckit.plan command)
├── contracts/            # Phase 1 output (/speckit.plan command)
└── tasks.md              # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── Utils.kt                          # getListFiles(...): переписать на однопроходный, фильтрующий во
│                                     # время обхода, закрываемый (.use{}) обход вместо
│                                     # Files.walk(...).toList() + многократной пост-фильтрации
├── model/Song.kt                     # createFromPath(...): обработка файл-за-файлом без накопления
│                                     # избыточных списков; threadId менять НЕ требуется — кортеж
│                                     # (DEMUCS2 и далее) уже корректно в THREAD_LANE_HEALTH_REPORT;
│                                     # добавить комментарий, поясняющий, что KEY_BPM_FROM_FILE
│                                     # намеренно в отдельном лейне (не входит в кортеж)
├── controllers/ApiController.kt      # doCreateFromFolder(...): ограничить конкурентность фоновых
│                                     # thread(start = true) на песню (поиск текста через SearXNG);
│                                     # сохранить продолжение при ошибке отдельной песни, собрать
│                                     # итоговую сводку (добавлено/пропущено) для SSE-уведомления
├── controllers/MainController.kt     # doCreateFromFolder(...): тот же путь через createFromPath/
│                                     # getListFiles — получает фикс автоматически, отдельно
│                                     # проверить (quickstart.md), что легаси-эндпоинт не падает
│                                     # так же, как основной
└── HealthReport.kt                   # без структурных изменений — используется как референс
                                     # конвенции threadId (THREAD_LANE_HEALTH_REPORT по умолчанию,
                                     # с наследованием threadId "родителя" при inProgressParent)

docs/features/
└── async-process-queue.md            # обновление по FR-009: явно зафиксировать, что вся цепочка
                                     # заданий одной песни (включая самый первый шаг из импорта)
                                     # обязана оставаться в THREAD_LANE_HEALTH_REPORT
```

**Structure Decision**: Однопроектная структура — весь фикс локализован в
модуле `karaoke-app` (backend). Новых модулей/эндпоинтов/колонок БД не
создаётся; фронтенд (`webvue3`) не меняется. Директория `tests/` не
заводится как отдельная структура — единственный новый тест (для
`getListFiles`) добавляется рядом с существующими в
`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/`, по уже принятому в
проекте плоскому расположению (см. `SpecTagsTest.kt`).

## Post-Design Constitution Check

*Ре-проверка после Phase 1 (`data-model.md`, `contracts/`, `quickstart.md`).*

Дизайн не добавил новых зависимостей, модулей, колонок БД, HTTP-эндпоинтов
или thread-лейнов сверх того, что было оценено в Constitution Check выше —
фикс `getListFiles`/`createFromPath`/`doCreateFromFolder` целиком укладывается
в существующие паттерны (Principle II, IV), схема БД не меняется, фронтенд
не затрагивается (Principle V). Повторных нарушений не выявлено, статус
таблицы Constitution Check не меняется.

## Complexity Tracking

> Не заполняется — нарушений Constitution Check, требующих обоснования, нет.
