# Implementation Plan: Флаг «песня доступна» для авто-новостей + очистка ленты и таблицы учёта

**Branch**: `101-song-news-flag` | **Date**: 2026-07-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/101-song-news-flag/spec.md`

## Summary

Сегодня существует ровно один вид авто-новости о песне («в эфире»), фактически
требующий одновременно готовности контента И наступления даты эфира, с
идемпотентностью через отдельную таблицу `tbl_song_news_announced`. Эта фича
вводит **второй, независимый** вид авто-новости — «песня появилась в
коллекции» (готова по контенту, независимо от даты эфира) — и одновременно
упрощает механизм «в эфире»:

1. **«Доступна»** — новый персистентный флаг у песни (хранится как ключ внутри
   уже существующего JSON-поля `player_readiness_flags`, БЕЗ новой колонки и
   БЕЗ правки recordhash-триггера). Флаг включается в `Song.saveToDb()`, когда
   впервые выполняется условие полной готовности контента. Новость создаётся
   **на сервере**, в момент применения синхронизации (`MainController.doChangeRecords`),
   когда сервер видит, что его собственное («до применения») значение флага
   было `false`/отсутствовало, а входящее — `true`.
2. **«В эфире»** — упрощается: плановая проверка на проде (~раз в 5 минут)
   больше не полагается на кумулятивную таблицу учёта, а рассматривает только
   узкое скользящее окно недавних кандидатов (дата/время эфира попадают в
   последние ~10 минут) и проверяет отсутствие уже существующей новости для
   этой песни. Синхронизация и апрув задания редактора **больше не создают**
   эту новость напрямую — только плановая проверка или ручное создание
   администратором.
3. **Очистка**: `tbl_news` очищается (`TRUNCATE`), `tbl_song_news_announced`
   удаляется (`DROP TABLE`). Разовый Kotlin-backfill (не raw SQL) выставляет
   флаг «доступна» в `true` на LOCAL и на SERVER для уже готовых сегодня песен
   — без создания видимых новостей — чтобы очистка не спровоцировала лавину.
   Для «в эфире» отдельный backfill не нужен: скользящее окно самой проверки
   уже физически не захватывает старые события.

## Technical Context

**Language/Version**: Kotlin 2.x / JDK 17 (существующий стек `karaoke-app`/`karaoke-web`, Spring Boot)

**Primary Dependencies**: Spring `@Scheduled` (уже используется —
`SongReleaseAnnouncementScheduler`, `StatsCacheScheduler`); `KaraokeConnection`/
`KaraokeDbTable` (сырой JDBC). Новых внешних зависимостей не требуется.

**Storage**: PostgreSQL, сырой JDBC. Схема `tbl_songs` не меняется (новый флаг
живёт внутри уже существующей текстовой JSON-колонки `player_readiness_flags`
— без новой колонки, без правки md5-формулы recordhash-триггера). Схема
`tbl_news` не меняется (переиспользуется уже существующая `category="premium"`,
задокументированная, но пока никогда не использовавшаяся автоматически).
`tbl_song_news_announced` удаляется целиком.

**Testing**: В CI юнит/интеграционных тестов для этого модуля нет (Constitution,
«Рабочий процесс» → «Тесты»); проверка — вручную на prod-like окружении по
`quickstart.md`.

**Target Platform**: `karaoke-web` (плановая проверка «в эфире», применение
синхронизации и детекция «доступна» — оба живут там, единственный модуль,
непрерывно работающий на PROD) и `karaoke-app` (флаг выставляется в
`Song.saveToDb()` — общий код модели, выполняется и на admin-машине, и
переносится в `karaoke-web` через общий модуль; удаляется мёртвый код в
`SongEditorController.approve()`).

**Project Type**: точечное расширение существующего backend-кода (без
изменений в `karaoke-public`/`webvue3` — категория «premium» уже присутствует
во фронтенде обоих SPA, изменений там не требуется)

**Performance Goals**: плановая проверка «в эфире» не должна деградировать при
росте каталога (18k+ песен на проде, Constitution Principle II) — дешёвая
первая фаза (id + date + time + id_status, без текста/маркеров/base64) плюс
фильтрация по скользящему окну в Kotlin, полная загрузка только для
кандидатов, попавших в окно (обычно 0-5 строк за тик). Детекция «доступна»
добавляет один точечный `SELECT` по PK на каждую обновляемую строку
`tbl_songs` в рамках `/changerecords` — не заметно на типичных объёмах одной
синхронизации (десятки-сотни строк, не весь каталог).

**Constraints**:
- Только сырой JDBC, без JPA/Hibernate (Principle II) — не меняется.
- Любое изменение набора синхронизируемых полей `tbl_songs` обязано сохранять
  корректность recordhash — именно поэтому новый флаг размещается **внутри**
  уже участвующего в формуле поля `player_readiness_flags`, а не в новой
  колонке (Principle III).
- `karaoke-app` не разворачивается на PROD (Principle I) → любая логика,
  которая должна работать непрерывно на PROD (плановая проверка «в эфире»,
  детекция «доступна» при синхронизации), обязана жить в `karaoke-web`.
- Прямые DDL/DML на PROD БД и деплой на сервер — только по прямому согласию
  пользователя, на каждое действие отдельно (см. «Ограничения и доступы
  агента»). Backfill-эндпоинт должен быть запущен пользователем вручную и на
  LOCAL, и на PROD.

**Scale/Scope**: 18k+ песен на проде (Constitution). Изменяемых файлов — семь
плюс одна новая миграция (см. Project Structure). Один новый вид новости, одна
удаляемая таблица, одна упрощаемая проверка.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн | ✅ PASS | Никаких внешних SaaS-зависимостей — только БД-операции внутри уже существующих JVM-процессов. |
| II. Сырой JDBC + diff по хэшам | ✅ PASS | Флаг «доступна» размещён внутри уже хэшируемого поля `player_readiness_flags` — recordhash-формула не меняется, миграция схемы не нужна. Сравнение «до/после» при синхронизации — точечный `SELECT` по PK, не O(n²)-скан. Backfill выполняется через обычный `Song.saveToDb()` (не raw SQL), что гарантирует корректный diff/recordhash без риска разойтись между LOCAL/PROD. |
| III. Двух-БД синхронизация через SyncRegistry | ✅ PASS | Флаг «доступна» — обычное поле `Song`, участвующее в штатной синхронизации (никакого нового исключения). Новость «доступна» (`category="premium"`), как и существующая «в эфире» (`category="air"`), остаётся `source="auto"` и вне `NewsSyncTarget`-diff — переиспользует уже принятое в specs/089 решение, не новый прецедент. |
| IV. Async-очередь с парсингом stdout | N/A | Все операции — быстрые синхронные DB-запросы, не идут через `KaraokeProcess*`/`ProcessBuilder`. |
| V. Два фронтенда — разные приложения | ✅ PASS (без изменений) | `category="premium"` уже присутствует в `CATEGORY_META` обоих SPA (`karaoke-public/src/views/NewsView.vue`, `NewsBell.vue`) и в `webvue3/.../NewsTable.vue` — фронтенд-изменений не требуется. |
| VI. Code Standards (KDoc/lint/per-feature-doc) | ⚠️ К выполнению | Новый/изменённый код должен получить KDoc с `@see docs/features/dual-db-sync.md`; сам документ должен быть обновлён в этом же PR (FR-009 Конституции) — механизм авто-новостей меняется существенно (было: 1 кумулятивная таблица, стало: флаг на песне + скользящее окно). |
| VII. Cross-Machine Setup | N/A | Фича не касается локальных AI-конфигов/line-endings. |
| Ограничения и доступы агента | ⚠️ Требует согласия пользователя | Миграция (`DROP TABLE`/`TRUNCATE`) и backfill-эндпоинт должны быть выполнены пользователем на LOCAL и на PROD по прямому согласию, не автономно агентом. Деплой `karaoke-web`/`karaoke-app` на PROD — по согласию. |

**Вывод**: Gate пройден. Единственная содержательная особенность (не
нарушение) — детекция «до/после» в `doChangeRecords` через точечный `SELECT`
перед `UPDATE`, что является минимальным отходом от «чисто слепого применения
SQL» ради корректности FR-004 (см. Complexity Tracking).

## Project Structure

### Documentation (this feature)

```text
specs/101-song-news-flag/
├── plan.md              # Этот файл
├── research.md          # Phase 0 — решения и их обоснование
├── data-model.md         # Phase 1 — сущности и переходы состояний
├── quickstart.md         # Phase 1 — сценарии ручной проверки
└── contracts/
    └── news-lifecycle.md # Phase 1 — контракты трёх точек (save/sync/scheduler)
```

### Source Code (repository root)

```text
deploy/karaoke-db/
└── 34_cleanup_song_news_announced.sql   # новый: DROP TABLE tbl_song_news_announced,
                                          #   TRUNCATE tbl_news (без изменений схемы tbl_songs)

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/
│   ├── Song.kt                          # + newsAvailableAnnounced (внутри player_readiness_flags),
│   │                                    #   + проверка условия готовности в saveToDb() (update-путь
│   │                                    #   и, отдельно, путь первого создания записи)
│   └── SongNewsAnnounced.kt             # УДАЛЯЕТСЯ целиком (таблица дропается)
├── controllers/
│   └── SongEditorController.kt          # approve() — убрать прямой вызов checkAndAnnounce
│                                         #   (фоновый поток + checkAndAnnounceRunning, specs/098) —
│                                         #   мёртвый код по FR-007 spec.md
└── services/
    └── SongReleaseAnnouncementService.kt # переписывается: checkAndAnnounce → windowed-скан «в
                                          #   эфире» без tbl_song_news_announced; новая функция
                                          #   detectAndAnnounceAvailability(...) для вызова из
                                          #   doChangeRecords; backfillExistingReadySongs → backfill
                                          #   флага «доступна» через обычный saveToDb()

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── controllers/
│   └── MainController.kt                # doChangeRecords — убрать старый вызов checkAndAnnounce
│                                         #   (эфир), добавить вызов detectAndAnnounceAvailability
│                                         #   до/после применения dataUpdate/dataCreate для tbl_songs
└── services/
    └── SongReleaseAnnouncementScheduler.kt # checkOnAir() — новая windowed-логика вместо старой
```

**Structure Decision**: Никакого нового модуля, никакой новой таблицы для
хранения (только удаление старой). Флаг «доступна» переиспользует уже
существующий JSON-blob-паттерн (`player_readiness_flags`) — ключевое решение,
устраняющее необходимость трогать recordhash-формулу и позволяющее backfill'у
идти через штатный `saveToDb()`. Основная бизнес-логика остаётся
сконцентрированной в `SongReleaseAnnouncementService` (как и в specs/089/092)
— вызывающий код (`MainController`, `SongReleaseAnnouncementScheduler`,
`SongEditorController`) только вызывает/перестаёт вызывать её функции.

## Complexity Tracking

> Заполняется только при нарушениях Constitution Check, требующих обоснования.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| `doChangeRecords` перестаёт быть чисто «слепым применением» списка SQL-выражений — для строк `tbl_songs` из `dataUpdate` добавляется точечный `SELECT` текущего значения флага перед `UPDATE` | FR-004 spec.md буквально требует сравнения «было false, стало true» именно в момент применения синхронизации на сервере — без хотя бы одного прочтения «старого» состояния эту транзицию невозможно отличить от «уже было true давно» | Existence-check по аналогии с механизмом «в эфире» (создавать новость, если флаг=true и такой новости ещё нет) был рассмотрен и отклонён: он либо требует видимых bookkeeping-строк в `tbl_news` для backfill'а уже готовых песен (прямое нарушение FR-012 «без создания видимой новости»), либо (если делать эти строки черновиками без `publish_at`) засоряет админский список новостей (`News.loadAll` показывает все строки, включая черновики) — то есть воспроизводит тот самый спам, который эта фича должна устранить. Точечный `SELECT` по первичному ключу, выполняемый только для строк `tbl_songs`, реально присутствующих в текущем батче синхронизации (не по всему каталогу), — минимальная и достаточная цена корректности. |
