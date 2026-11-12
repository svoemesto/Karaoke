# Implementation Plan: Удаление песни с каскадной уборкой

**Branch**: `739-plan` (от `739-song-delete-cascade`, коммит `41c1f333` со спекой) | **Date**: 2026-11-12 | **Spec**: [`specs/739-song-delete-cascade/spec.md`](spec.md)

**Input**: Feature specification from `specs/739-song-delete-cascade/spec.md` (утверждена владельцем 2026-11-12)

**Issue**: KARAOKE-429 (Plane) | **Карта решений**: `.scratch/karaoke/wayfinder/song-deletion/map.md`

---

## Summary

Администратор удаляет песню — и вместе с ней уходят её файлы с диска, её объекты
из обоих хранилищ (локальный MinIO и удалённый MinIO), а если песня была
последней в альбоме — запись альбома, его обложка и (только если в папке нет
файлов живых песен) папка альбома. Удаление разбито на три фазы: **LOCAL-БД
(синхронно, одна транзакция) → SERVER-БД (синхронно, отдельная транзакция) →
фоновая уборка артефактов** (задание очереди `KaraokeProcess`, порядок FR-031:
диск → хранилища → альбом → папка альбома). Каждое действие пишется в журнал
уборки в БД (`tbl_song_cleanup_runs` + `tbl_song_cleanup_entries`), прогон имеет
терминальный статус (`DONE` / `PARTIAL` / `FAILED`), остаток виден в админке и
добирается вручную.

Техническая опора — не новая подсистема, а переиспользование существующих
контуров проекта: реестр владения артефактами (`SongArtifactRenamer`),
реестр типов файлов и их локаций (`KaraokeFileType`), формулы имён объектов
(`StorageCacheReset`), готовый механизм уборки с отпечатком и журналом
(`ArtifactCleanupService` / `CleanupJournal`), очередь заданий с лейнами и
восстановлением после рестарта (`KaraokeProcess`), транзакционный двух-БД
контур (`KaraokeConnection`) и API хранилищ (`KaraokeStorageService`,
`StorageApiClient`).

**Главный риск фичи** — правило владения артефактом: от него зависит, чужое ли мы
удаляем. Оно принципиально разное для двух операций (удаление песни — строгое
совпадение; разовая уборка — нормализованное совпадение, которое, наоборот,
**исключает** объект из кандидатов). План разводит эти правила в два разных
матчера.

**Два расхождения карты решений с фактами кода** (карта — источник решений,
спека — контракт; обе не меняются, расхождения фиксируются здесь):

1. FK на `tbl_songs` не два, а **три**: кроме `tbl_song_authors` (CASCADE) и
   `tbl_news.song_id` (NO ACTION) есть ещё `tbl_song_news_announced.song_id`
   (NO ACTION, `deploy/karaoke-db/33_song_news_announced.sql:23`) — он блокирует
   удаление так же, как новости.
2. `tbl_songs_sync` — таблица на **SERVER** (пишется `Utils.setSongToSyncRemoteTable`
   через `Connection.remote()`), и **читающего её кода в проекте нет**: это
   write-only очередь. Удаление из неё — гигиена, а не условие корректности.

---

## Technical Context

**Language/Version**: Kotlin 1.x / JDK 17 (backend, Gradle multi-module), JavaScript + Vue 3 (admin SPA `webvue3`)

**Primary Dependencies**: Spring Boot (web), MinIO SDK (хранилища), PostgreSQL JDBC (сырой JDBC, без ORM), Vite + Vuex (admin)

**Storage**: PostgreSQL (две базы: LOCAL на admin-машине, SERVER на проде), MinIO (два бакета-инстанса: локальный `karaoke-storage:9000` и удалённый на проде), локальная файловая система `/sm-karaoke/{work,done1,done2}`

**Testing**: JUnit 5 (`./gradlew :karaoke-app:test`), `node --test` (`webvue3` unit), PostgreSQL-интеграция, ручная приёмка по [`quickstart.md`](quickstart.md)

**Target Platform**: Linux server (admin-машина `nsa-i9`, контейнер `karaoke-app`); публичная часть `karaoke-web`/`karaoke-public` — не меняется

**Project Type**: web-service (admin API + SPA) с фоновой очередью заданий

**Performance Goals**: предпросмотр удаления — единицы секунд (1 обход папки + 2 листинга бакета по префиксу); уборка — в фоне, не блокирует HTTP-запрос; сканирование не должно упираться в N+1 (счётчики — пакетными запросами)

**Constraints**: MinIO-операции не выполняются в HTTP-треде пользовательского запроса (инвариант storage № 4); `redirectErrorStream(true)` для всех `ProcessBuilder`; транзакции JDBC короткие, HTTP-вызовы внутри транзакции запрещены; никаких JPA/Hibernate-импортов; никаких новых магических кодов вне словарей

**Scale/Scope**: каталог 18k+ песен; удаление — единичная операция, но разовая уборка в объёме фичи затрагивает ~310 осиротевших ключей, 745 устаревших копий, 1 927 осиротевших файлов, 1 424 черновика, 2 101 историческую строку (суммарно ≥10 ГБ по SC-007)

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Требование | Как соблюдается | Вердикт |
|---|---|---|---|
| **I. Self-contained автопайплайн** | Без внешних SaaS | Уборка — локальный FS + два MinIO-инстанса проекта; новых внешних сервисов нет | PASS |
| **II. Сырой JDBC + дифф по хэшам** | Только `KaraokeConnection`, без ORM | Все операции БД — через `KaraokeConnection.getConnection()` и `PreparedStatement`; JPA-импортов нет | PASS |
| **III. Двух-БД синхронизация через SyncRegistry** | Синхронизируемые сущности — в `SyncRegistry.all` + 8 флагов | `Song` уже в `SyncRegistry`. Новые таблицы журнала **не** синхронизируются (журнал — локальный хроник); это осознанное исключение, обосновано ниже | PASS с обоснованием |
| **IV. Async-очередь задач** | Длительные операции — через `KaraokeProcess`; `redirectErrorStream(true)` | Уборка — новый тип `SONG_DELETE_CLEANUP` в lane `-1`; subprocess-вызовов нет, но механизм очереди (claim, цепочка, восстановление) используется штатно | PASS |
| **V. Двух-фронтенд** | `webvue3` и `karaoke-public` — разные приложения | Изменения только в `karaoke-app` и `webvue3`; публичная часть не трогается (FR-044) | PASS |
| **VI. Code Standards** | KDoc + `@see` на per-feature документ; ktlint/ESLint; FR-009 — обновить `knowledge/product/features/<slug>.md` | Новый per-feature документ `knowledge/product/features/song-delete-cascade.md` создаётся в том же PR; KDoc на всех публичных API; линтеры в pre-commit и CI | PASS |
| **VII. Cross-Machine Setup** | Локальные конфиги агентов вне репозитория | Новых агентских конфигов нет | PASS |
| **VIII. Секреты и git-гигиена** | Секреты только через env; дефолт невалидный | Флаг `songDeleteEnabled` — булево свойство `KaraokeProperties` с дефолтом `false`; секретов нет | PASS |
| **IX. Knowledge-first** | 5 шагов pre-flight до поиска по коду | Выполнено; результат — раздел «Knowledge References» ниже | PASS |
| **IX.3. Subagent isolation** | Отдельный `git worktree` на параллельные PR-ветки | Работа ведётся в `/home/nsa/Karaoke/.worktrees/739-plan`, ветка `739-plan` | PASS |

**Осознанные отступления (не нарушения).**

1. **Таблицы журнала уборки не участвуют в двух-БД синхронизации.** Принцип III
   требует регистрации в `SyncRegistry.all` для сущностей с LOCAL↔SERVER sync.
   Журнал уборки — локальная хроника администратора: на SERVER он не нужен, а
   его синхронизация означала бы перенос путей к файлам admin-машины на прод.
   Исключение зафиксировано здесь; в `knowledge/domains/processing/components/two-db-sync.md`
   (список «не в sync») запись добавляется в том же PR.
2. **Удаление песни идёт в обход `SongSyncTarget`.** Строка удаляется в обеих БД
   явно (решение владельца № 11, FR-001), а не через diff-механику sync. Sync
   остаётся страховкой: при включённом `sync_songs_push_delete_allowed` он
   приведёт SERVER-БД в согласие при ближайшем прогоне (см. открытый вопрос В-08).

**Post-design re-check (после Phase 1):** PASS — новые артефакты не нарушают ни
один принцип; таблицы журнала вынесены в локальный контур сознательно (п. 1),
что не меняет ни Constitution, ни `SyncRegistry`.

---

## Knowledge References

*MUST-секция (AGENTS.md § Tier-1 MUST #0, Constitution § IX). Фиксирует факт
pre-flight: что искали → что нашли.*

### Протокол pre-flight

| Шаг | Что сделано | Результат |
|---|---|---|
| 1 | `knowledge/README.md` — полностью; `knowledge/domains/README.md` — полностью | Прочитаны; определены релевантные домены: `catalog`, `storage`, `health`, `processing`, `editorial`, `rendering`, `persistence` |
| 2 | Три grep-запроса по `knowledge/` (темы: удаление песни/каскад; MinIO/storage/объекты по префиксу; история/события/черновики) | См. «Grep-запросы» ниже |
| 3 | `domain.md` + `components/*.md` релевантных доменов | См. «Прочитанные документы» |
| 4 | Все `local-*.md` из `knowledge/adr/` (17 файлов: local-0001…local-0017) | См. «Прочитанные документы» |
| 5 | `knowledge/system/agent-mechanics.md` | Прочитан полностью; правила 1–12 учтены (правка инструментом, порядок «правка → проверка → коммит», отчёт по критериям) |

### Grep-запросы (что искали → что нашли)

**Запрос 1 — удаление песни, каскад, удаление альбома.**
`удален(ие|ия) песни|deleteSong|removeSong|song.*delete|каскад` →
`knowledge/domains/catalog/components/{album-entity,dictionaries,song-entity}.md`,
`knowledge/domains/health/*`, `knowledge/domains/karaoke-web/components/public-controllers-{5,6}.md`,
`knowledge/domains/monetization/domain.md`, `knowledge/domains/monitoring/components/*`,
`knowledge/domains/publishing/components/publishing-services.md`,
`knowledge/domains/storage/components/storage-api-client.md`,
`knowledge/product/features/{complete-song-rename,escape-confirm-user-data,process-bulk-actions}.md`,
`knowledge/system/agent-mechanics-cases.md`,
`knowledge/system/frontend/{confirm-escape,composable-playlist-membership,subs-edit-admin-editor,webvue3-views-detailed}.md`,
`knowledge/system/infra/{audit-336-registry,deploy-overview,sql-migrations}.md`.
**Найдено:** контракт текущего удаления песни (BR-106: «строка сначала, потом
файлы», fail-closed при отказе БД) — `catalog/components/song-entity.md`; правило
владения артефактом — `product/features/complete-song-rename.md`; реестр классов
уборки и вердикты `PROTECTED`/`KEEP` — `catalog/components/dictionaries.md`;
процедура SQL-миграций — `system/infra/sql-migrations.md`.

**Запрос 2 — MinIO, storage, объекты по префиксу.**
`minio|storageFileNamesForSong|префикс|storage/[a-z]*` →
`knowledge/domains/storage/**`, `knowledge/domains/catalog/components/{audio-descendant-sync,dictionaries,entities-catalog,pictures,remaining-models,song-entity,song-lifecycle}.md`,
`knowledge/domains/health/**`, `knowledge/domains/processing/components/{async-process-queue,karaoke-properties,key-bpm-from-file}.md`,
`knowledge/domains/karaoke-web/**`, `knowledge/adr/local-0003-shared-minio-image-cache.md`.
Точечно: `storageFileNamesForSong` → `storage/domain.md:407`;
`PUBLICATION_PICTURE_NAME_SUFFIXES` → `complete-song-rename.md:37,52`.
**Найдено:** два бэкенда хранения и инварианты storage № 1–6 (в т.ч. «MinIO-операции
не в HTTP-треде пользовательского запроса»); состав ключей песни
`$автор/$год - $альбом/$файл`; состав картинок альбома/автора и их пути; правило
«вечный кеш метаданных» (следствие для FR-037); **`karaoke-cache` не существует**
(ADR `local-0003` и `storage/domain.md` описывают несуществующий бакет — расхождение
известно и в объём правки SSoT входит).

**Запрос 3 — история, события, черновики, `song_id`.**
`черновик|draft|listening_history|search_results|events|song_id` →
`knowledge/domains/catalog/components/{entities-catalog,remaining-models,song-entity}.md`,
`knowledge/domains/editorial/components/assignment-lifecycle.md`,
`knowledge/domains/processing/components/{async-process-queue,schedulers,two-db-sync}.md`,
`knowledge/domains/persistence/domain.md`,
`knowledge/domains/stats/components/{dictionaries,event-funnel}.md`,
`knowledge/domains/sse/domain.md`, `knowledge/domains/storage/components/storage-api-client.md`.
**Найдено:** перечень всех таблиц каталога и их sync-направления
(`entities-catalog.md` — таблица «Сущность → таблица → sync-target → CRUD →
Vuex»); модель `WebEvent` (`tbl_events`, `song_id` nullable) и конвейер
`SamplingFilter → DedupCache → EventsBuffer → EventsRetentionScheduler`;
`ListeningHistory` (upsert `play_count + 1`), `SitePlaylist`/`SitePlaylistItem`,
`SongAssignment`/`SongAssignmentDraft`; `SearchAsync`/`SearchResult` — только LOCAL,
в sync не участвуют.

### Прочитанные документы (по доменам)

**catalog** — `domain.md`; `components/`: `song-entity.md`, `song-lifecycle.md`,
`album-entity.md`, `dictionaries.md`, `entities-catalog.md`, `remaining-models.md`,
`pictures.md`, `audio-descendant-sync.md`.
**storage** — `domain.md`; `components/storage-api-client.md`.
**health** — `domain.md`.
**processing** — `components/async-process-queue.md`, `components/two-db-sync.md`.
**editorial** — `components/assignment-lifecycle.md`.
**rendering** — `domain.md` (замечание: домен утверждает, что видео лежит в MinIO
по ключу `done_files/<songId>/<version>.mp4`; по замеру бакетов этого нет — FR-016
из `song-artifact-cleanup.md` верен, расхождение SSoT фиксируется в PR).
**product/features** — `song-artifact-cleanup.md`, `complete-song-rename.md`.
**system** — `system/agent-mechanics.md`, `system/infra/sql-migrations.md` (фрагмент).
**ADR (все `local-*.md`)** — `local-0001` (дефолты `KaraokeProperties`),
`local-0002` (не глотать исключения БД), `local-0003` (кеш изображений в MinIO),
`local-0004` (lazy/eager-load в `webvue3`), `local-0005` (структурированное
логирование `karaoke-app`), `local-0006` (логирование `karaoke-web`),
`local-0007` (`albumId` в NDJSON-стриме), `local-0008` (скрытые типы альбомов),
`local-0009` (жизненный цикл thread-local соединения), `local-0010` (Harbor),
`local-0011` (Forgejo и CI), `local-0012` (Plane-трекер), `local-0013` (бэкапы
infra), `local-0014` (ограничения heap `karaoke-app`), `local-0015` (allow-list
прод-хоста), `local-0016` (перехват команд агента), `local-0017` (выборочный CI).

### Что нашлось и как это повлияло на план

| Находка | Источник | Влияние на план |
|---|---|---|
| `Song.deleteFromDb` возвращает `Unit` и глотает исключения БД | `song-entity.md` (BR-106) + код `Song.kt:6898` | Эндпоинт обязан возвращать честный результат — FR-002, SC-003 (R-015) |
| Правило владения: имя-префикс + «продолжение артефакта» | `complete-song-rename.md` | Основа матчера удаления (R-003) |
| Реестр классов уборки с вердиктами `PROTECTED`/`KEEP` | `dictionaries.md` + `song-artifact-cleanup.md` | При удалении песни вердикты **не применяются** (FR-012) — отдельный матчер, а не `ArtifactCleanupClass` |
| Инвариант storage № 4: MinIO не в HTTP-треде | `storage/domain.md` | Уборка — только фоном (FR-029) |
| Отпечаток набора + журнал + состояния прогона уже есть | `song-artifact-cleanup.md` | Журнал уборки песни — в БД (FR-034), TSV-журнал остаётся за разовой уборкой (KARAOKE-423) |
| `KaraokeProcess`: лейны, single-flight, восстановление после рестарта | `async-process-queue.md` | Фоновая уборка — штатное задание очереди (FR-030) |
| `tbl_events` — миллионы строк, `song_id` nullable | `entities-catalog.md` | Чистим только `song_id <> 0` (FR-007) |
| `tbl_songs_sync` живёт на SERVER | `two-db-sync.md` + `Utils.setSongToSyncRemoteTable` | Удаляем строку оттуда (решение владельца № 11), потребителя нет (R-014) |
| `recordhash` обязателен для sync-таблиц | `two-db-sync.md` (Principle III) | Новые таблицы журнала в sync не входят — обосновано в Constitution Check |
| Туннель admin→прод заблокирован с 2026-11-09 (KARAOKE-407) | `local-0015`, карта решений | SERVER-удаление может не состояться — FR-045 требует честного ответа (В-01, В-04) |
| `redirectErrorStream(true)` обязателен | ADR `local-0006` | Соблюдается (в фоновой задаче subprocess-ов нет) |
| MDC/структурированные логи `event:key=value` | ADR `local-0005` | Формат кодов событий фичи (R-011) |
| Не глотать исключения БД; ошибки — в лог со стеком | ADR `local-0002` | Честный успех/провал в эндпоинте (R-015) |
| Закрытие thread-local соединения в одноразовых потоках | ADR `local-0009` | Фоновая задача — шаг очереди (поток воркера), не `thread {}` |

### Где искать не нашлось

- **Документа про «удаление песни с каскадной уборкой» в `knowledge/` нет** —
  фича новая; источники решений — карта `song-deletion/map.md` и спека.
- **Документа про `tbl_songs_sync` как для прод-публикации нет** — таблица
  упомянута только в комментариях миграций 43/52/53; потребителя в коде не
  найдено (R-014).
- **Отдельного компонента про «владение артефактом» в `knowledge/domains/health`
  нет** — правило живёт в per-feature документе `complete-song-rename.md` и в коде
  `SongArtifactRenamer`; в плане оно поднимается до отдельного компонента
  (`knowledge/domains/catalog/components/song-artifact-ownership.md`).

---

## Project Structure

### Documentation (this feature)

```text
specs/739-song-delete-cascade/
├── plan.md              # Этот файл (/speckit.plan)
├── spec.md              # Утверждённая спека (не изменяется)
├── research.md          # Phase 0: 15 решений + 8 открытых вопросов
├── data-model.md        # Phase 1: сущности, миграции, состояния прогона
├── quickstart.md        # Phase 1: сценарии приёмки по SC и edge cases
├── contracts/
│   └── song-delete-api.md   # Phase 1: HTTP-контракт фичи и фоновой задачи
└── tasks.md             # Phase 2 (/speckit.tasks) — НЕ создаётся на этом шаге
```

Плюс, в том же PR (Constitution § VI, FR-009):

```text
knowledge/product/features/song-delete-cascade.md   # per-feature документ (FR-009)
knowledge/domains/catalog/components/song-artifact-ownership.md  # правило владения
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/
│   ├── Song.kt                       # deleteFromDb → возвращает результат (R-015)
│   ├── SongArtifactRenamer.kt        # isArtifactContinuation переиспользуется как есть
│   └── SongNameHistory.kt            # НОВ: чтение/запись истории имён
├── services/
│   └── songdeletion/                 # НОВ: контур фичи
│       ├── SongDeletionService.kt    # Фаза A (LOCAL) и фаза B (SERVER)
│       ├── SongDeletionPlanner.kt    # план каскада по таблицам + правило владения
│       ├── SongArtifactOwnership.kt  # НОВ: строгий матчер владения (R-003)
│       ├── SongCleanupJournal.kt     # чтение/запись tbl_song_cleanup_runs/entries
│       ├── SongCleanupExecutor.kt    # Фаза C: порядок FR-031, повторы с backoff
│       └── SongDeletionPreview.kt    # предпросмотр (точный, SC-004)
├── controllers/
│   └── ApiController.kt              # /api/song/deletepreview, /api/song/delete,
│                                     # /api/song/cleanup/runs/**
├── KaraokeProcessTypes.kt            # НОВ: SONG_DELETE_CLEANUP
├── Utils.kt                          # runFunctionWithArgs: + songDeleteCleanup
├── KaraokeProperties.kt              # НОВ: songDeleteEnabled
└── model/KaraokeDbTable.kt           # без изменений

webvue3/src/
├── components/Songs/
│   ├── store.js                      # разбор нового тела ответа delete
│   └── edit/SongEdit.vue             # окно подтверждения с числами (FR-023…FR-027)

deploy/karaoke-db/
├── 58_news_song_id_on_delete_set_null.sql   # НОВ: разблокировка FK (FR-009)
├── 59_song_name_history.sql                 # НОВ: история имён (FR-013)
└── 60_song_cleanup_journal.sql              # НОВ: журнал уборки (FR-034, FR-035)

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/
├── services/songdeletion/            # НОВ: unit + integration тесты
└── audit/SongDeleteRegressionTest.kt # НОВ: регрессии (FK, идемпотентность)
```

**Structure Decision**: изменения ложатся в существующие модули — новый пакет
`services/songdeletion/` в `karaoke-app` (как уже сделано для
`services/cleanup/`), три SQL-миграции в `deploy/karaoke-db/`, правки в
существующих `ApiController`, `Song.kt`, `Utils.kt`, `KaraokeProperties.kt` и
`webvue3`. Отдельный модуль Gradle, отдельный фронтенд и отдельная подсистема
очереди **не создаются**: очередь `KaraokeProcess` — штатный механизм (Assumptions
спеки: «отдельная подсистема не создаётся»), а публичная часть не меняется
(FR-044).

---

## Архитектура решения

### 1. Каскад в БД (FR-001, FR-005…FR-009, SC-002)

**Инвентарь FK** — см. R-001 в [`research.md`](research.md). Ключевое: удаление
блокируют **два** FK с правилом `NO ACTION` — `tbl_news.song_id` и
`tbl_song_news_announced.song_id`.

**Миграция `58_news_song_id_on_delete_set_null.sql`**:

```sql
ALTER TABLE public.tbl_news DROP CONSTRAINT IF EXISTS tbl_news_song_id_fkey;
ALTER TABLE public.tbl_news
    ADD CONSTRAINT tbl_news_song_id_fkey
    FOREIGN KEY (song_id) REFERENCES public.tbl_songs(id) ON DELETE SET NULL;

ALTER TABLE public.tbl_song_news_announced
    DROP CONSTRAINT IF EXISTS tbl_song_news_announced_song_id_fkey;
ALTER TABLE public.tbl_song_news_announced
    ADD CONSTRAINT tbl_song_news_announced_song_id_fkey
    FOREIGN KEY (song_id) REFERENCES public.tbl_songs(id) ON DELETE CASCADE;
```

`tbl_news.song_id` → `SET NULL` (FR-009: новость остаётся, привязка обнуляется).
`tbl_song_news_announced.song_id` → `CASCADE`: колонка входит в
`PRIMARY KEY (song_id)`, `SET NULL` неприменим; таблица — метка «решение об
анонсе принято», данных песни не хранит (R-002, открытый вопрос В-02).

**Каскад без FK** (удаляются явными запросами в одной транзакции фазы A):
`tbl_song_assignments` → `tbl_song_assignment_drafts` (FR-005);
`tbl_site_playlist_items` (FR-005 — «позиции в плейлистах»);
`tbl_song_share_links` (FR-005 — «ссылки на неё»);
`tbl_listening_history` (FR-006); `tbl_search_results`, `tbl_search_async` (FR-006);
`tbl_events` — **только `song_id <> 0`** (FR-007); `tbl_songs.audio_parent_id`
потомков → `NULL` (FR-008).

**Что НЕ удаляется:** `audit_log`, `songs_history.txt`, старые записи кеша
хранилища (FR-038); опубликованные копии на внешних площадках (Out of scope).
`tbl_songs_sync` удаляется в фазе B — на SERVER (решение владельца № 11); на LOCAL
таблица присутствует в схеме, но строки в неё не пишутся (см. R-014).

### 1.1 Предпросмотр и подтверждение (FR-023…FR-027)

Перед удалением администратор видит, что именно будет удалено:

| Показатель | Источник | FR |
|---|---|---|
| количество файлов и объём в мегабайтах | обход папки альбома, правило владения | FR-023 |
| число объектов **в каждом** хранилище (локальное и удалённое — отдельно) | листинг бакета с префиксом `$автор/$год - $альбом/` в каждом хранилище | FR-024 |
| удаляется ли вместе с песней альбом (с его обложкой и папкой) — и **почему**, если папка остаётся | проверка «остались ли живые песни в альбоме» + «есть ли в папке файлы живых песен» | FR-025 |
| число родственных песен | `tbl_songs.audio_parent_id = id` | FR-026 |

Подтверждение не требует дополнительного ввода и не ограничено по объёму или
числу песен (FR-027). Предпросмотр и уборка используют **один** код правила
владения — иначе расхождение между показанными и удалёнными файлами не может
быть нулевым (SC-004). Точность обеспечивается реальным листингом бакета по
префиксу, а не оценкой по БД (R-006, открытый вопрос В-05).

### 2. Хранилища (FR-013, FR-014, SC-001)

**Что лежит у песни в MinIO** (бакет `karaoke`):

| Объект | Ключ | Вердикт |
|---|---|---|
| mp3-стемы (5) | `$автор/$год - $альбом/$файл.{accompaniment,vocals,bass,drums,other}.mp3` | удаляем |
| публикационные картинки (9 суффиксов) | `$автор/$год - $альбом/$файл [karaoke].png` и др. | удаляем |
| обложка альбома + превью | `$автор/$год - $альбом/$автор - $год - $альбом{.album,.preview.album}.png` | **не удаляем** при удалении песни (FR-014) |
| картинка автора + превью | `$автор/$автор{.author,.preview.author}.png` | **не удаляем** никогда (FR-014) |

Видео, исходники, flac-стемы, субтитры, `.song`/`.settings` и проекты рендера в
MinIO **не загружаются** (FR-016 из `song-artifact-cleanup.md`; подтверждено
замером обоих бакетов) — в плане уборки их нет.

**Правило «свой объект»**: ключ начинается с `$автор/$год - $альбом/$файл`, а
остаток — продолжение артефакта (пусто / `.ext` / `-суффикс` / ` [метка]`).
Обложки альбома и автора начинаются с другого префикса и отсекаются
автоматически — FR-014 обеспечен **структурно**, а не «списком исключений»
(R-005).

**Варианты старого имени (FR-013).** В БД хранится только текущее `file_name`.
Чтобы FR-013 был исполним на будущее, вводится `tbl_song_name_history`
(миграция `59_song_name_history.sql`), заполняемая при каждом переименовании
песни (`Song.renameAllArtifacts` → вставка прежнего имени **до** переименования).
При удалении песни удаляются объекты по текущему имени **и** по всем именам из
истории (R-004, открытый вопрос В-03).

**Отсутствие в одном хранилище — не ошибка** (edge case спеки): удаление
выполняется по каждому хранилищу отдельно; «нет объекта» — нормальный исход, а
не сбой.

**Кеш метаданных (FR-037).** После удаления объектов инвалидируются строки
`tbl_storage_metadata_cache` по ключам `(source, bucket, fileName)` — тем же
механизмом, что `POST /api/song/resetStorageCache`
(`StorageMetadataCache.refreshKeys`), для обоих источников.

**Соотнесение с накопленным объёмом** (R-013): 310 истинных сирот (2,58 ГБ) и 745
устаревших копий (4,38 ГБ) — это объём **разовой уборки** (FR-041), а не следствие
удаления одной песни. Исключение — устаревшие копии **самой удаляемой** песни:
они удаляются вместе с ней (FR-013) через `tbl_song_name_history`. Near-match
объекты (3 520 файлов) не трогаются никогда (FR-042, SC-008).

### 3. Диск: файлы песни, альбом, папка (FR-010, FR-015…FR-022, SC-005)

**Папка альбома** `Song.rootFolder` = `/sm-karaoke/{work,done1,done2}/<автор>/<год> - <альбом>/`
— общая для всех песен альбома. Поэтому «удалить файлы песни» = удаление **по
префиксу имени**, а не удаление папки.

**Файлы песни (FR-010)** — обход папки и её подпапок:
корень, `hdemucs_mmi/`, `done_files/`, `done_chords/`, `sheetsage/`, все
каталоги `symlink_*` (перечень — `SongArtifactRenamer.ARTIFACT_SUB_FOLDERS` и
`SYMLINK_FOLDER_PREFIX`). Удаляются: исходник, ключевые данные (`[key].json`),
стемы, видео, тексты, файлы проектов рендера, материалы Sheetsage. Вердикты
реестра уборки (`PROTECTED`/`KEEP`) **не применяются** — FR-012: при удалении
песни удаляются в том числе исходники и файлы проектов рендера.

**Битые симлинки (FR-015)** — после удаления целевых файлов оставшиеся
симлинки с несуществующей целью удаляются; живые симлинки других песен — нет.

**Неопознанные файлы (FR-016)** — остаются на месте и пишутся в журнал с
`result = KEPT`, `reason = not_recognized`.

**Near-match (FR-011)** — файл, чьё имя совпадает с именем **живой** песни после
нормализации (регистр, `ё`/`е`, кавычки, `1`/`01`), не удаляется **никогда**.

**Каскад альбома (FR-018…FR-022)**:

1. После удаления песни определяется, остались ли в альбоме живые песни
   (`tbl_songs.album_id = id`).
2. Если живых песен нет → удаляются: запись `tbl_albums`, запись `tbl_pictures`
   картинки альбома, обложка и превью альбома в **обоих** хранилищах (FR-019).
3. Папка альбома удаляется, **только если в ней нет файлов живых песен**
   (FR-020, FR-021). Проверка — фактический обход папки после удаления
   артефактов песни: если остались файлы, папка не удаляется, причина
   `album_has_live_songs` пишется в журнал. Это и есть поведение альбома-«близнеца»
   (FR-022, edge case спеки): запись-сирота удаляется, папка остаётся.
4. Пустые подпапки внутри папки альбома удаляются только вместе с альбомом
   (FR-017); подпапка с файлами других песен не удаляется.

**Порядок** (FR-031): диск → хранилища → альбом и его обложка → папка альбома.
Папка альбома — последним шагом, потому что её содержимое известно только после
удаления файлов песни.

### 4. Корректность: честный успех, идемпотентность, транзакционность (FR-002, FR-003, SC-003)

**Корень проблемы.** `Song.deleteFromDb(withFiles, sync)` (`Song.kt:6898`)
возвращает `Unit`: при отсутствии соединения, при исключении и при
`deletedRows <= 0` он пишет WARN и делает `return`. Вызывающий
`ApiController.doDeleteSong` (`ApiController.kt:4357`) ничего не проверяет и
отвечает `200` с пустым телом. **Это и есть «ложный успех»** (SC-003): карточка
песни закрывается, песня остаётся в базе.

**Исправление (R-015).** `deleteFromDb` начинает возвращать результат
(`deleted` / `not_found` / `db_error` с причиной). Эндпоинт возвращает
структурированный ответ — контракт в
[`contracts/song-delete-api.md`](contracts/song-delete-api.md).

**Идемпотентность (edge case спеки).** Повторный вызов на уже удалённую песню
возвращает `200 status = already_deleted` — **успех**, а не ошибка (FR-002
различает «успех», «отказ БД» и «отсутствие такой песни»). Отсутствие песни
не считается ошибкой только тогда, когда удаление уже состоялось; при первом
обращении к несуществующей песне — `404 song_not_found`.

**Защита от удаления при активной обработке (FR-004).** Перед фазами A и B
проверяется `KaraokeProcess.hasActiveProcess(songId, database)` — существующий
метод (`KaraokeProcess.kt:1537`), который уже используется для блокировки
переименования песни. Если над песней идёт задание в статусе `WAITING`/`WORKING`,
удаление отклоняется с `409 process_running` и понятным сообщением (edge case
спеки: «Над песней идёт фоновая обработка → удаление запрещено с понятным
сообщением»). Проверка повторяется в начале фоновой уборки — между подтверждением
и началом уборки могло стартовать новое задание.

**Транзакционность.** Фаза A — одна транзакция на LOCAL: удаление строки песни и
всех связанных записей; любой сбой → `rollback`, эндпоинт отвечает `500 db_error`.
Фаза B — отдельная транзакция на SERVER; её сбой **не отменяет** фазу A, но
результат честно отражает неудачу (`status = partially_deleted`,
`deletedServer = false`) — FR-045.

**Порядок LOCAL → SERVER.** Если удалить только SERVER, а LOCAL оставить, то
ближайший прогон sync (LOCAL→SERVER) **вернёт** песню на SERVER по расхождению
`recordhash`. Если удалить только LOCAL — песня останется жить на сайте, но
sync-движок с `sync_songs_push_delete_allowed` её удалит. Удаление LOCAL первым
делает направление односторонним и предсказуемым.

**Транзакционность фоновой фазы невозможна** (диск, MinIO, две БД) — вместо неё
правило **«строка удаляется только после того, как артефакты признаны удалёнными
или оставленными с причиной»**: журнал пишется на каждом шаге, прогон получает
терминальный статус, остаток не теряется (FR-033, SC-009).

**Поведение при частичном отказе:**

| Что отказало | Что происходит | Статус |
|---|---|---|
| LOCAL-БД | откат фазы A; файлы и объекты **не трогаются** (fail-closed, как BR-106) | `500 db_error` |
| SERVER-БД | фаза A выполнена; SERVER-удаление уходит в очередь с повторами | `200 partially_deleted` |
| Хранилище LOCAL | повторы 5×, затем остаток в журнале | прогон `PARTIAL` |
| Хранилище REMOTE | то же | прогон `PARTIAL` |
| Диск (файл занят) | запись в журнале `FAILED`, `reason = file_busy`; остальные файлы удаляются | прогон `PARTIAL` |
| Папка альбома не пуста | папка остаётся, причина в журнале | прогон `DONE` (остаток — `KEPT`, не `FAILED`) |

### 5. Фоновая уборка: процесс, повторы, добор (FR-029, FR-030, FR-032, FR-033)

Новый тип `KaraokeProcessTypes.SONG_DELETE_CLEANUP`, lane
`THREAD_LANE_LIGHT_BACKGROUND` (`threadId = -1`) — операция сетевая и файловая,
не тяжёлый рендер. Шаг исполняется существующим механизмом
`runFunctionWithArgs`: `["runFunctionWithArgs", "songDeleteCleanup", "songId=…", "runId=…"]`.

**Переживание рестарта (FR-030)** — существующий механизм
`KaraokeProcess.setWorkingToWaiting` (WORKING → WAITING при старте). Шаги
идемпотентны: уже удалённый файл/объект при повторе даёт `SKIPPED`, а не
`FAILED`.

**Повторы (FR-032)** — до 5 попыток с нарастающими паузами 2s → 60s (решение
владельца KARAOKE-414). Механизм очереди даёт retry только для `DEMUCS*`, поэтому
backoff реализован **внутри** шага; каждая попытка пишется отдельной записью
журнала (`attempt` 1…5).

**Добор (FR-033)** — после исчерпания попыток остаток в журнале, прогон получает
статус `PARTIAL`/`FAILED`, в админке видны остаток и кнопка «добрать»
(`POST /api/song/cleanup/runs/{runId}/resume`). Повторный запуск продолжает **тот
же** прогон (тот же `run_id`) — журнал содержит обе попытки, итоговый статус
становится `DONE`, когда остаток пуст (US3-сценарий 4).

### 6. Наблюдаемость (FR-034, FR-035, FR-036, SC-009)

**Журнал в БД (FR-034)** — две новые таблицы (миграция
`60_song_cleanup_journal.sql`): `tbl_song_cleanup_runs` (прогон: состояние
`PLANNED → APPROVED → RUNNING → DONE | PARTIAL | FAILED`, инициатор, счётчики,
время) и `tbl_song_cleanup_entries` (действие: путь/ключ, размер, результат,
причина, номер попытки, дата, инициатор, `run_id`). Полный состав — в
[`data-model.md`](data-model.md).

**Коды событий** — категория `infra.song.delete`, формат `event:key=value`
(ADR `local-0005`): `song.delete.requested`, `song.delete.blocked.process`,
`song.delete.blocked.ownership`, `song.delete.db.local.ok`,
`song.delete.db.local.failed`, `song.delete.db.server.ok`,
`song.delete.db.server.failed`, `song.cleanup.run.started`,
`song.cleanup.step.done`, `song.cleanup.step.failed`, `song.cleanup.run.finished`.

**Отчёт по каждому удалению (US1-сценарий 4)** — прогон даёт сводку «что
удалено, что осталось и почему осталось»: счётчики по файлам и объектам,
освобождённый объём, список `KEPT` с причинами.

**Статус в интерфейсе (FR-036)** — `GET /api/song/cleanup/runs` + SSE-уведомление
при переходе прогона в терминальное состояние (существующий механизм `SNS.send`).

### 7. Безопасность и доступ (FR-028, FR-046)

- **Кто может.** Все эндпоинты — в admin-контуре `karaoke-app` (Principle V;
  ADR `0004` — `karaoke-app` развёрнут только на admin-машине). Доступен любой
  администратор с текущими правами; отдельная роль не вводится (FR-028).
- **Подтверждение (FR-027).** Окно подтверждения — существующий `CustomConfirm.vue`
  в текстовом режиме, без дополнительного ввода, без квот и лимитов; значения
  подставляются с экранированием (`knowledge/system/frontend/confirm-escape.md`).
- **Защита от удаления «не нашей» песни (R-009).** Перед удалением проверяется:
  строка существует в LOCAL; `root_folder` начинается с одного из
  `PROJECT_ROOT_FOLDERS` (`/sm-karaoke/work|done1|done2`); `file_name` непустой и
  не содержит разделителей пути. Иначе — `409 not_owned`, ничего не удаляется.
- **Нет каскада в чужие данные (FR-046).** Три рубежа: (1) белый список таблиц
  каскада (перечень в R-001, никаких «похожих» таблиц); (2) правило владения
  артефактом (R-003) — чужая песня не удаляется; (3) проверка `root_folder`.
  Обложки альбома и автора защищены структурно (FR-014).
- **Публичная часть не меняется (FR-044, SC-010).** Ни один эндпоинт
  `karaoke-web`/`karaoke-public` не трогается; публичная страница удалённой
  песни продолжает отвечать как раньше.

### 8. Тест-стратегия

Полная матрица — в [`quickstart.md`](quickstart.md) (13 edge cases × 10 SC).

**Unit (чистые функции, без БД и сети):**

| Тест | Что проверяет | FR |
|---|---|---|
| `SongArtifactOwnershipTest` | строгое правило владения: регистр, `ё`/`е`, кавычки, `1`/`01`, соседняя песня с префиксом, продолжения `.ext` / `-суффикс` / ` [метка]` | FR-011 |
| `ArtifactNormalizationTest` | нормализация для разовой уборки: near-match **не** попадает в кандидаты | FR-042 |
| `SongDeletionPlannerTest` | план каскада: какие таблицы чистятся, какие записи остаются (`events` — только `song_id <> 0`) | FR-005…FR-007 |
| `CleanupRunStateMachineTest` | переходы состояний прогона; `DONE` невозможен при `*_failed > 0` | FR-034, SC-003 |
| `SongDeletionPreviewTest` | предпросмотр == факт (расхождение 0) на синтетическом наборе | FR-023, SC-004 |
| `StorageKeyOwnershipTest` | свои и чужие ключи в MinIO (обложка альбома/автора не попадают в удаляемые) | FR-013, FR-014 |
| `AlbumFolderGuardTest` | папка альбома не удаляется при наличии файлов живых песен | FR-020, FR-021, FR-022 |

**Integration (живая БД + тестовое хранилище):**

| Тест | Что проверяет | FR |
|---|---|---|
| `SongDeleteEndToEndTest` | удаление песни с полным набором артефактов: строка gone, файлы gone, объекты gone, альбом gone | SC-001, SC-002 |
| `SongDeleteIdempotencyTest` | повторное удаление → `already_deleted`, не ошибка | FR-002 |
| `SongDeleteDbFailureTest` | отказ БД → `500 db_error`, **не** `200`; карточка не закрывается | FR-002, FR-003, SC-003 |
| `SongDeleteServerUnavailableTest` | SERVER недоступен → `partially_deleted`, SERVER-удаление в очереди | FR-045 |
| `SongDeleteStorageRetryTest` | хранилище недоступно → 5 попыток с backoff → `PARTIAL` + остаток в журнале → добор → `DONE` | FR-032, FR-033, SC-006 |
| `SongDeleteAlbumTwinTest` | альбом-«близнец»: запись удалена, папка осталась | FR-022 |
| `SongDeleteNewsFkTest` | регрессия: песня с новостью удаляется (после миграции 58) | FR-009 |
| `SongDeleteDescendantTest` | потомки живы, `audio_parent_id` обнулён | FR-008 |
| `SongDeleteRestartResumeTest` | перезапуск в середине уборки → продолжение | FR-030, SC-009 |

**Регресс-тесты, которые не должны сломаться:** `RenameAllArtifactsTest`
(правило владения не изменилось), `StorageCacheResetTest` (формулы имён),
`ArtifactCleanupGuardTest` / `CleanupJournalTest` (разовая уборка не затронута),
`PkgS322ProcessTest` (`hasActiveProcess` не блокируется soft-deleted строкой).

**Команды проверки** (AGENTS.md § «Обязательная проверка после изменения»):

```bash
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")/.gradle}"
./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
./gradlew :karaoke-app:test --parallel
./gradlew :karaoke-web:ktlintCheck
cd webvue3 && npm run lint && npm run test:unit && npm run format:check && cd ..
```

### 9. Порядок раскатки, feature flag, миграционная безопасность, откат

**Раскатка (R-010):**

1. **Миграции** `58`, `59`, `60` — вручную на LOCAL, затем на PROD
   (runbook `knowledge/system/infra/sql-migrations.md`, решение 4; проверка
   `confdeltype` — в [`data-model.md`](data-model.md)).
2. **Код** — с **выключенным** флагом `songDeleteEnabled` (по умолчанию `false`).
3. **Включение флага** на admin-машине, приёмка по [`quickstart.md`](quickstart.md).
4. Включение на проде — по отдельному решению владельца.

**Миграционная безопасность:**

| Ситуация | Поведение |
|---|---|
| Старый код + новая схема | работает: `SET NULL`/`CASCADE` только ослабляют ограничения; новая таблица истории имён старому коду не мешает |
| Новый код + старая схема | `DELETE` падает по FK → эндпоинт возвращает `500 db_error`; **ложного успеха нет** (именно поэтому порядок «миграция → код» обязателен) |
| Откат флага | `songDeleteEnabled=false` → `503 song_delete_disabled`; откат кода и миграций не нужен |
| Откат миграции | не требуется: `SET NULL`/`CASCADE` — ослабление, безопасное для старого кода |

**Feature flag.** Свойство `songDeleteEnabled` в `KaraokeProperties`
(файл `/sm-karaoke/system/Karaoke.properties`, путь `PATH_TO_KARAOKE_PROPERTIES_FILE`),
регистрируется в `listKaraokeProperties` как
`KaraokeProperty(key = "songDeleteEnabled", defaultValue = false, description = …)`
и читается через `KaraokeProperties.getBoolean("songDeleteEnabled")`.
Дефолт `false` — по конвенции существующих флагов автоуборки (`cleanupEnabled`,
`cleanupDryRun`, specs/544). Выключен → все эндпоинты фичи отвечают
`503 song_delete_disabled`. Флаг даёт обратимость без отката кода —
необходимо, потому что операции необратимы. Переключение — либо правкой файла
свойств + перезапуск, либо через существующий endpoint
`/api/properties/setproperty` (свойство зарегистрировано — значит, доступно и
из админки).

### 10. Что НЕ входит в объём

По «Out of scope» карты решений `song-deletion/map.md` и Assumptions спеки:

| Не входит | Источник |
|---|---|
| Папка автора на диске (`<root>/<автор>/`) — не удаляется, даже если пуста | Out of scope карты |
| Мягкое удаление, корзина, возврат удалённой песни | Out of scope карты; Assumptions спеки |
| Блокировка удаления опубликованных песен — можно удалять | Out of scope карты |
| Файлы на внешних площадках (VK, Telegram, Boosty) | Out of scope карты; US1-сценарий 5 |
| Картинка автора — общая, удаляется не при удалении песни | Out of scope карты; FR-014 |
| Починка переноса ключей в MinIO при переименовании (`renameAllArtifacts`) — KARAOKE-428 | Out of scope карты; Assumptions спеки |
| Починка allow-list туннеля admin-машина→прод — KARAOKE-407 | Out of scope карты; Assumptions спеки |
| Изменение публичного поведения сайта | FR-044, SC-010 |
| `songs_history.txt`, `audit_log`, старые записи кеша хранилища | FR-038 |
| Разработка публичного API удаления для внешних потребителей | FR-028 (только админка) |
| Применение самой разовой уборки (745 копий, 310 сирот, 1 927 файлов) | отдельный проход по протоколу FR-039…FR-043; **в объёме фичи — протокол и механизм, не сам проход** |

**Уточнение объёма.** FR-041…FR-043 требуют, чтобы разовые уборки были
**возможны**: перечень кандидатов с размерами и отпечатком набора (FR-039),
исполнение **только после утверждения владельцем** с отказом при изменившемся
составе набора (FR-040), запись каждого удаления в журнал (FR-043). Сами проходы
по накопленному объёму выполняются после приёмки основной фичи — тем же кодом
владения и тем же журналом (R-013).

---

## Открытые вопросы владельцу

| # | Вопрос | Предлагаемое решение | Что блокирует |
|---|---|---|---|
| В-01 | При недоступной SERVER-БД: удалять локально (честно «частично удалено») или отказывать целиком? | Удалять локально; SERVER-удаление в очереди с добором; ответ `partially_deleted` | Реализацию фазы B (не текст спеки) |
| В-02 | `tbl_song_news_announced` → `ON DELETE CASCADE` (удаляется строка-метка) — подтвердить | CASCADE; таблица не хранит данных песни | Текст миграции 58 |
| В-03 | Вводить ли `tbl_song_name_history` как условие FR-013 | Вводить; запись при `renameAllArtifacts` | Исполнимость FR-013 на будущее |
| В-04 | SERVER-БД недоступна с 2026-11-09 (KARAOKE-407) — приемлемо ли, что FR-001/FR-045 проверяются только на LOCAL | Проверять локально | Полноту приёмки, не реализацию |
| В-05 | Предпросмотр: согласен ли владелец на 2 сетевых листинга бакета ради точности 0 (SC-004) | Да; оценка по БД нарушает SC-004 | Точность предпросмотра |
| В-06 | Журнал уборки — новые таблицы в БД (FR-034) | Новые таблицы `tbl_song_cleanup_runs` / `tbl_song_cleanup_entries` | Схему миграции 60 |
| В-07 | Удалять ли строку из `tbl_songs_sync` на SERVER, где у таблицы нет потребителя | Удалять (гигиена, исполнение решения № 11) | Поведение фазы B |
| В-08 | Включать ли `sync_songs_push_delete_allowed` как страховку удаления на SERVER | Не включать без отдельного решения — это общая политика sync | Эксплуатацию, не код |

Все восемь вопросов имеют предлагаемое решение; ни один не требует изменения
утверждённой спеки. Вопросы В-01…В-03 и В-06 меняют только код и миграции.

---

## Phase 0: Outline & Research

Результат — [`research.md`](research.md): 15 решений (R-001…R-015) с
обоснованием и отклонёнными альтернативами; 8 открытых вопросов с
предлагаемыми решениями. Все «NEEDS CLARIFICATION» из Technical Context
разрешены: стек, хранилища, тестирование, целевая платформа, производительность
и ограничения определены из репозитория, а не предположены.

## Phase 1: Design & Contracts

| Артефакт | Содержание |
|---|---|
| [`data-model.md`](data-model.md) | Сущности (Песня, Альбом, Артефакт, Прогон уборки, Запись журнала, История имён, Кандидат разовой уборки), связи, переходы состояний, три миграции, два правила владения, ограничения целостности |
| [`contracts/song-delete-api.md`](contracts/song-delete-api.md) | 5 HTTP-эндпоинтов, контракт фоновой задачи, контракт флага, совместимость |
| [`quickstart.md`](quickstart.md) | Препятствия, команды, 13 сценариев US1…US4, матрица 13 edge cases × 10 SC, проверка миграционной безопасности |

**Post-design Constitution re-check**: PASS — см. раздел «Constitution Check»
(два осознанных отступления обоснованы; ни один принцип не нарушен).

---

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Новый пакет `services/songdeletion/` (5–6 классов) рядом с существующим `services/cleanup/` | Фича объединяет три фазы (LOCAL-БД, SERVER-БД, фоновая уборка) и предпросмотр; размещение в `model/Song.kt` (~10 000 строк) сделало бы файл нечитаемым, а в `ApiController.kt` — смешало бы HTTP-слой с логикой | Один класс `SongDeletionService` в `model/Song.kt` — нарушение «сначала найди, потом читай» (agent-mechanics, правило 3) и прецедент `HealthReport.kt` (2 240 строк в одном файле) |
| Три SQL-миграции вместо одной | Разные риски отката: FK-ослабление (58) необратимо в обратную сторону без вреда, таблицы (59, 60) — независимые сущности с разными жизненными циклами | Одна миграция — при откате нельзя вернуть только схему журнала, не трогая FK |
| Таблицы журнала в БД, а не TSV-файл (как в specs/544) | FR-034 прямо требует «в базе данных»; журнал должен переживать рестарт приложения (SC-009) и позволять выборку «что осталось» по прогону | TSV-журнал `cleanup-logs/` — не переживает переустановку контейнера, не даёт выборок; решение владельца KARAOKE-423 оставляет TSV за разовой уборкой |
| Отдельный матчер владения рядом с `SongArtifactRenamer` | Нужно **строгое** правило для удаления песни и **нормализованное** — для разовой уборки; это противоположные правила, и одно не может обслуживать оба случая (R-003) | Переиспользовать `ArtifactCleanupClass` — вердикты `PROTECTED`/`KEEP` прямо запрещены при удалении песни (FR-012) |
| Таблица истории имён песни | FR-013 требует удалять объекты по ранее использовавшимся именам; иначе история имён невосстановима (R-004) | Сканировать весь префикс альбома — небезопасно: удалит артефакты соседних песен |
| Отдельный флаг фичи | Операции необратимы; без флага откат = откат кода и пересборка образа | Включение сразу — необратимые операции без приёмки на LOCAL |
