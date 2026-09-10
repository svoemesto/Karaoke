# Architecture Notes — Karaoke project

Дневник последних архитектурных решений и изменений. Источник истины для
высокоуровневого контекста; детали фич — в `specs/NNN-*/spec.md` и
`docs/features/<slug>.md`.

> **Pass 300** (2026-09-03): Исправлен баг #50 (OP) — добавлен watcher на
> `countRows` в admin tables (Authors, Albums, Pictures, SiteUsers) для сброса
> `currentPage` при уменьшении выборки после фильтра. Эталон — `Songs/SongsTable.vue:998-1009`.
> Backend не менялся. См. `specs/300-author-pagination-filter-bug/` и
> `docs/features/pagination-filter-admin-tables.md`.

> **Pass 301** (2026-09-03): Исправлен баг #51 (OP) — `<textarea v-text>`
> → `<textarea :value>` в `webvue3/src/components/Songs/edit/SearchText.vue:36`
> для реактивного обновления после `extractLyricsBySearchResultId`. Добавлен
> `display: block` в `.group-button` для гарантии столбика кнопок
> «Открыть на сайте» / «Получить текст по ссылке». Итерация 2: `.st-body-column-2`
> → `display: flex; flex-direction: column`, `.result-text` → `flex:1; min-height:0`,
> чтобы textarea занимала всё доступное пространство **минус** кнопки
> (раньше textarea уползала под footer). Backend не менялся.
> См. `specs/301-search-text-extract-btn/` и
> `docs/features/search-text-extract-btn.md`.

> **Pass 307** (2026-09-06): Задача #56 (OP) — спец-плашка «Отдельные песни
> разных авторов» теперь **первая** в сетке `/zakroma` (а не последняя).
> Добавлено поле `tbl_authors.sort_order INTEGER NOT NULL DEFAULT 0`
> (миграция `46_author_sort_order.sql`, идемпотентна; пересоздан
> `recordhash`-триггер). SQL ORDER BY изменён на `sort_order ASC, author ASC`.
> Бэкенд: новое поле в `Author`, `AuthorDTO`, `AuthorTilePublicDto`
> (JSON: `sortOrder`). Публичный фронт: слот `#trailing` → `#leading` в
> `AuthorTiles.vue` для спец-плашки. Админка: редактируемая колонка `Sort`
> в `AuthorsTable.vue` через inline `<input type="number">`. Кеш
> `authorsTilesCache` НЕ меняется (TTL ≤60с, Clarification Q5). Sync LOCAL↔SERVER
> работает автоматически через `recordhash`. См. `specs/307-special-authors-zakroma-order/`
> и `docs/features/zakroma-tiles-sort-order.md`.

> **Pass 311** (2026-09-06): исправлен баг в спеке 307 — реализация
> `ORDER BY sort_order ASC, author ASC` нарушала требование спеки
> «ненулевые sort_order **ПЕРЕД** нулевыми» (т.к. `0 < 1`, нулевые
> оказывались раньше ненулевых). Также фильтр `ready_songs_count > 0`
> исключал из публичной выборки авторов с `sort_order != 0` но без
> готовых песен (например, «Саундтреки», sort_order=1, ready=0,
> total=62). Исправлено в `Author.loadAuthorTilesWithCounts`:
> - WHERE: `(ready_songs_count > 0 OR sort_order != 0)` для публичной
>   поверхности (onlyPublished=true); для редакторов — старая логика
>   по `total_songs_count > 0`.
> - ORDER BY: `(sort_order = 0), sort_order ASC, author ASC` —
>   `false < true` в Postgres, поэтому нулевые уезжают в конец.
> - Проверено на фикстуре: Jane Air(-1) → Саундтреки(1) → Lumen(5) →
>   нулевые по алфавиту. Урок: верификация должна включать **граничные
>   значения** (отрицательные, ноль, положительные) во всех комбинациях,
>   а не только «один ненулевой vs все нулевые». См. также
>   `livedocs/features/307-orm-field-save-paths.md`.
> **Pass 318** (2026-09-08): задача OpenProject #66 «Проверка инцидентов
> на проде за последнюю неделю» — one-shot **операционный** анализ логов
> контейнера `karaoke-db`. Скачано 273 959 строк / 92.6 МБ за период
> 2026-09-01…2026-09-08 через `ssh root@188.119.64.111` + `docker logs
> --since=168h --timestamps` (плюс `--tail=100000` для покрытия «хвоста»;
> `json-file` log driver ограничен ~30 МБ ring-buffer). Категоризация
> выявила доминирующую verbose-категорию **`log_temp_files`** (18.56 %) —
> артефакт `log_temp_files=0`. Реальных ERROR'ов — **9 за неделю**
> (0.0033 % от объёма). **Вердикт: ОТКЛЮЧАТЬ**. По прямому согласию
> владельца применено `ALTER SYSTEM`: `log_temp_files=-1`,
> `log_min_duration_statement=1500`, `log_checkpoints=off` +
> `SELECT pg_reload_conf()`. Smoke-test (намеренный `SELECT * FROM
> definitely_does_not_exist_table_12345`) подтвердил, что ERROR-сообщения
> по-прежнему пишутся в лог. Контрольный замер через 24 ч (SC-003) — отложен.
>
> **Технические уроки**:
> - `mawk 1.3.4` (Ubuntu 24.04) **не поддерживает 3-арг `match(s, regex, array)`**
>   и не выдерживает сложные regex — пришлось переписать горячий путь на
>   Python (`_parse_logs.py`). Урок: даже «простые» POSIX-утилиты в современных
>   дистрибутивах могут быть `mawk`, а не `gawk` — нужно проверять.
> - `set -o pipefail` **ломает `sort | head -N`** на больших файлах: `sort`
>   продолжает писать после закрытия pipe → SIGPIPE (exit 141) → pipefail
>   трактует как ошибку → `set -e` выходит. Решение: либо без pipefail,
>   либо `trap "" PIPE`, либо `|| true`. Урок: для скриптов с `sort | head`
>   НЕ использовать `set -o pipefail`.
> - Категоризация `__unclassified__` составила 79.28 % — это **не** баг
>   категоризации, а особенность docker logs: многострочные SQL-STATEMENT
>   разбиваются на десятки строк, только первая имеет `[NNN] LOG:` префикс,
>   остальные идут без префикса. В таких случаях лучше **не** ужесточать
>   regex'ы (ложные срабатывания), а явно объяснять в отчёте.
>
> **Артефакты**: `specs/318-prod-db-log-audit/{spec,plan,research,data-model,
> quickstart,tasks}.md`, `report.md` (9 разделов, md5 стабилен между повторами —
> SC-002 PASS), `data/{logs.jsonl,incidents.jsonl,metrics.json,categories.json}`
> (в git; `raw.log` санитизирован и gitignored). Скрипты в `scripts/`
> воспроизводимы end-to-end за ≤30 секунд на 270k строк (см. quickstart.md).

> **Pass 344** (2026-09-09): Задача OpenProject **#69** «Кеширование
> информации из хранилища». Главный hot path —
> `HealthReport.getHealthReportList(song)` — для каждой песни дёргает
> `StorageApiClient.fileExists` через nginx path-proxy (50-100ms).
> На странице Songs (18k+ песен) — 72k+ HTTP round-trip, страница грузится
> минуты.
>
> Решение: in-memory TTL-cache через готовый паттерн `PollingCache<V>`
> (см. `knowledge/domains/caching/components/web-caches.md`). Копия
> `PollingCache.kt` из `karaoke-web/.../services/` в `karaoke-app` (KDoc
> ссылка на оригинал). Новый бин `StorageMetadataCache` (@Component)
> с двумя `PollingCache` (local + remote) + `LongAdder` счётчики +
> SLF4J-категория `infra.cache.storage` (новая, зарегистрирована в
> `knowledge/domains/monitoring/components/log-categories.md`). TTL=300s
> настраивается через `application.yml`. Endpoint
> `GET /api/health/cacheStats` для метрик.
>
> **Прецедент**: 2026-09-09 (Pass 340) спека #339 провалилась потому,
> что агент изобрёл форму кеша (БД-таблица `storage_file_cache`) вместо
> готового `PollingCache`. Данная спека явно использует Knowledge-first
> подход (см. spec.md § Knowledge References, Constitution Principle IX).
>
> **Артефакты**: `specs/344-storage-metadata-cache/{spec,plan,research,data-model,
> quickstart,tasks,checklists/requirements}.md`,
> `contracts/cache-stats-api.md`. Реализация:
> `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PollingCache.kt`
> (copy), `services/StorageMetadataCache.kt` (новый), `controllers/CacheStatsController.kt`
> (новый), модификация `HealthReport.kt` (autowire + wrap в actions*).
> Per-feature документ: `docs/features/storage-metadata-cache.md` (FR-009).

> **Pass 345** (2026-09-09, ветка `348-storage-cache-eternal`): owner попросил
> «вечный кеш» вместо TTL=300s. Существующая реализация #344 (in-memory
> PollingCache) **superseded** спецификацией #348. Persistent storage через
> Postgres (`tbl_storage_metadata_cache`, миграция
> `deploy/karaoke-db/48_storage_metadata_cache.sql`). TTL = ∞. Write-through
> hooks в `KaraokeStorageService.uploadFile/deleteFile` и
> `StorageApiClient.uploadFile/deleteFile`. Manual refresh через
> `DELETE /api/health/cache/refresh`.
>
> **Артефакты**: `specs/348-storage-cache-eternal/spec.md`,
> `deploy/karaoke-db/48_storage_metadata_cache.sql`,
> `controllers/CacheAdminController.kt` (новый),
> `services/StorageMetadataCache.kt` (полностью переписан, V2 — Postgres DAO),
> `services/PollingCache.kt` + `PollingCacheTest.kt` (УДАЛЕНЫ — V1 superseded).

> **Pass 349** (2026-09-09, ветка `349-tracker-must-link`): owner
> обнаружил, что OpenProject #69 был обработан через `/speckit-full 69`
> БЕЗ выполнения workflow `claim → add-comment → mark-review`. #69
> был открыт без `tracker.sh claim-issue`, отчёт опубликован задним
> числом. Это governance failure. Amendment ввёл:
> * `tools/check-spec-issue-link.py` — NEW. Валидирует наличие
>   `## OpenProject Tracking` секции в modern-спеках. CI gate в
>   `.github/workflows/lint.yml`.
> * `.specify/templates/spec-template.md` — добавлена MANDATORY секция.
> * `AGENTS.md` 2.1.0 → 2.2.0. Section «Issue-tracker OpenProject» дополнен.
> * `specs/344` и `specs/348` обновлены под новый формат.
> * Report `specs/344-storage-metadata-cache/report.md` (10 133 chars)
>   опубликован в OpenProject #69. Issue сейчас: assignee=ai-agent,
>   status=In review.
>
> **Артефакты**: PR #449.

> **Pass 350** (2026-09-09, ветка `350-spec-hooks-auto-tracker`):
> автоматизация OpenProject Tracker workflow (Pass 349 amendment).
> * `tools/tracker-bootstrap.sh` — NEW (~100 lines). Хук before_specify:
>   detect OpenProject ID в `$ARGUMENTS` (regex `#NN` / `№NN` /
>   `задача NN` / `task NN` / `OP #NN` / `OpenProject NN`); вызывает
>   `tracker.sh claim-issue` (idempotent). No-op если не найден.
> * `tools/tracker-implement-done.sh` — NEW (~140 lines). Хук
>   after_implement: detect Issue ID; использует `report.md` или
>   auto-generates stub; вызывает `tracker.sh add-comment + mark-review`.
> * `tools/specify-bootstrap.sh` — интегрирован auto-claim hook.
> * `.specify/extensions.yml` — оба хука зарегистрированы как optional.
> * `AGENTS.md` 2.2.0 → 2.3.0 (semver MINOR). Подсекция «Auto-hooks (Pass 350)».
> * `tools/README.md` — добавлено описание новых scripts.
>
> Хуки OPTIONAL — manual workflow остаётся fallback.

> **Pass 351 (planned, 2026-09-09)**: follow-up на task OpenProject #65
> «Ошибка при проверке наличия файла в удаленном хранилище». Root-cause
> (network timeout блокирует caller thread ~60s) НЕ починен — смягчено только
> через кеш Pass 344/345. Создана задача OpenProject #71 «Graceful
> degradation для remote StorageApiClient» с плановым scope:
> configurable `fileExistsTimeoutSeconds` + circuit breaker +
> `infra.cache.storage.network.failure` метрика. После реализации
> pass-351 — закрыть #65. См. `specs/349-tracker-must-link/report-65.md`.

> **Pass 351 — статус #65 / #71 (2026-09-09)**:
> * Task #65 auto-claim (Pass 350 hook), затем add-comment с
>   `specs/349-tracker-must-link/report-65.md` (5330 chars, comment id=344).
> * Status #65 = In progress (per OpenProject workflow), assignee=ai-agent.
> * Создана task #71 «Graceful degradation для remote StorageApiClient
>   (#65 follow-up)». assignee пока не назначен.

> **Pass 357 (2026-09-10)**: спека 357 — Folder Import Overwrite (Audit #73).
> Расширение спеки 299 (`saveToDbLocked`). Аудит всех 49 мест `Song.saveToDb()`
> в `karaoke-app/src/main/kotlin/`: 30 мест категории B1 (долгие процессы с IO/ML/
> ffmpeg между load и save) переведены на `saveToDbLocked()`. Добавлен WARN-лог
> `song.locked_save_diff_overlap` в `saveToDbLocked()` для операционной
> диагностики race overlap (FR-160..FR-180). Обновлена документация
> `knowledge/domains/catalog/components/song-entity.md` (раздел «Методы» —
> добавлено сравнение `saveToDb()` vs `saveToDbLocked()`).
> OpenProject #73: claim → in progress (Pass 350 hook).

> **Pass 358** (2026-09-10): Задача #74 (OP) — настраиваемое количество
> строк на странице в admin-таблицах webvue3. Добавлено 14 параметров
> `ui.<table>.rows_per_page` (тип INT, дефолты 25/30/50/500) в
> `listKaraokeProperties` (`karaoke-app/.../KaraokeProperties.kt`). Серверная
> валидация `1..1000` в эндпоинте `/api/properties/setproperty` (FR-006).
> Новый Vuex-модуль `webvue3/src/store/modules/tableSettings.js` —
> кеш per-table значений с геттерами `getRowsPerPage(tableKey)`,
> `isSavingRowsPerPage(tableKey)` и action `setRowsPerPage({tableKey, value})`.
> UI-поле `<b-form-input type="number">` в верхнем блоке пагинации
> 14 таблиц (Songs, Authors, Albums, Pictures, SiteUsers, Subscriptions,
> ShareLinks, Dictionaries, Properties, SitePlaylists, ListeningHistory,
> Processes, News, Stats). Решение владельца: хранить в `KaraokeProperties`
> (глобально per-table, НЕ per-user — см. Clarifications § spec.md/358).
> Без оптимистичного обновления (UI применяет значение только после успешного
> ответа backend — см. Clarifications Q3). Никаких новых REST-эндпоинтов:
> используется существующее `/api/propertiesdigests` (POST, для чтения
> разом) и `/api/properties/setproperty` (POST, для записи).
> См. `specs/358-rows-per-page/`, `docs/features/rows-per-page.md`.
> ADR-нарушение: фича использует `KaraokeProperties` для UI-настроек
> (не только render-config), что переопределяет первоначальную интерпретацию
> ADR `local-0001`. Решение владельца зафиксировано в spec.md § Clarifications.

> **Pass 361** (2026-09-10): Bugfix #76 (OP) — редактор на публичном сайте видел
> только альбомы с готовыми песнями, как гость. Root cause: `ZakromaAlbumsView.vue`
> — единственный клиент эндпоинта `/api/public/authors/{authorId}/albums`, который
> делал наивный `fetch` без `Authorization: Bearer <token>`-заголовка.
> `SiteUserResolver.resolve` (`SiteUserResolver.kt:25`) берёт токен **только** из
> `Authorization`-заголовка (контракт спеки 017), поэтому бэкенд всегда видел
> анонимного пользователя → `onlyPublishedFor(request) == true` → выборка гостя.
> Бэкенд уже корректен с Pass 360 (спек 356, `Album.loadAlbumTilesWithCounts` +
> `PublicApiController.onlyPublishedFor`); fix — только 5 строк кода во фронте
> (+ JSDoc) по образцу `useZakromaStreamProgress.js:144-146` и `services/api.js:15-18`.
> Валидация (автор #17 АнимациЯ): гость → 22 альбома, 0 с `readySongCount=0`;
> редактор → 29 альбомов, 7 с `readySongCount=0`. Без изменений в БД, миграциях,
> триггерах, DTO, других контроллерах. См. `specs/360-editor-sees-all-albums/`,
> `docs/features/zakroma-albums-by-author.md` (секция «Bugfix #76 — Pass 361»).
> Pass 350 hooks `tracker-implement-done.sh` отработали: `#76 → In review`.

> **Pass 361** (2026-09-10): Исправлен баг OpenProject #77 — на крупных
> авторах (≈2500+ песен, напр. «Машина Времени») URL
> `GET /api/public/account/playlists/membership?ids=22982,...` превышал
> 8 КБ (HTTP request-line лимит nginx `large_client_header_buffers`) →
> браузер получал `HTTP 414 Request-URI Too Large` → membership-карта
> не доходила до фронта → иконки избранного/плейлистов оставались в
> нейтральном состоянии. Решение: добавлен новый `POST
> /api/public/account/playlists/membership` с JSON-телом
> `{"ids": number[]}` (без лимита, ограничен только
> `nginx client_max_body_size`, default = 1m, на dev-машинах ≥ 20M).
> Бэкенд: новый DTO `MembershipRequest(val ids: List<Long>)`,
> общая логика вынесена в private `buildMembershipResponse(user, songIds)`,
> `@PostMapping("/playlists/membership") membershipPost(...)` делегирует
> в helper. Старый `@GetMapping` сохранён для backward-compat (DEPRECATED).
> SQL без изменений (`SitePlaylistItem.songIdsInPlaylists(...)` —
> единственный `WHERE song_id IN (...)` запрос, O(1)). Фронт:
> новая `authPostJson(path, jsonBody, token)` в `services/authApi.js`
> (JSON-body, существующий `authPost` form-encoded не тронут — 10+
> мест использования); `fetchMembership(ids)` в `services/playlistApi.js`
> переписан с `authGet` на `authPostJson`. CSRF не требуется — JWT
> в `Authorization: Bearer` header (см. `SiteAuthInterceptor.kt:25`).
> Никаких новых секретов, никаких миграций БД. Линтеры PASS
> (ktlintCheck, ESLint clean, Prettier clean, KDoc 96.1%, JSDoc 94.0%).
> См. `specs/361-playlists-membership-uri-length/`,
> `docs/features/playlist-membership.md`,
> `knowledge/adr/0009-get-vs-post-large-payload.md`.
