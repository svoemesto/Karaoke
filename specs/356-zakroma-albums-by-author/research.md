# Research: Закрома — Альбомы авторов

> **Прецедент**: спека 286 (Кэш счётчиков песен автора в `tbl_authors`) — копируется паттерн 1:1 для `tbl_albums`. Различия минимальны (FK `author_id` вместо строкового `song_author`).

## R1: SQL-триггер для денормализации счётчиков

**Decision**: Создать `trg_tbl_songs_update_album_counts` (AFTER INSERT/UPDATE/DELETE на `tbl_songs`) — атомарно обновляет `total_song_count`/`ready_song_count` в `tbl_albums`. Полный паттерн — спека 286.

**Rationale**:
- Источник истины — БД-триггер (а не код приложения). Это гарантирует консистентность счётчиков при любых операциях: ручные UPDATE через psql, импорт через `importFolderMP3` (спека 282), bulk-операции (`process_bulk_actions`, спека 319).
- Атомарность: `total_song_count` и `ready_song_count` обновляются в одной транзакции с INSERT/UPDATE/DELETE — race conditions невозможны (см. спек 343 race-65-fix).
- Backfill одним UPDATE с подзапросом COUNT(*) (идемпотентный).
- `recordhash` обновляется через существующий `update_tbl_albums_recordhash()` — добавить новые колонки в md5.

**Alternatives considered**:
- **Код приложения (`Album.save()` обновляет счётчики)**: rejected — race conditions при concurrent updates (см. спек 343 race-65-fix), не покрывает ручные SQL-операции.
- **Materialized view**: rejected — спека 297 уже добавила для `tbl_songs`, для `tbl_albums` это overkill (10k записей vs 18k песен).
- **Polling раз в N минут**: rejected — UX-лаг, числа в UI не совпадают с реальностью.

**Edge cases** (см. spec.md § Edge Cases):
- Песня с `album_id = NULL` → триггер no-op (аналог `song_author` без соответствия в спеке 286).
- UPDATE `album_id` (перенос песни между альбомами) → декремент у OLD.album_id + инкремент у NEW.album_id с учётом id_status.
- Skip-альбом (`tbl_albums.skip = true`) → счётчики обновляются (UI скрывает отдельно).

## R2: URL-схема страницы песен альбома

**Decision**: Query-параметр `/zakroma/{author_id}?album={album_id}` (см. A-004, Clarification не понадобилась — оба варианта в issue #70, выбран query).

**Rationale**:
- Backward compatible с существующей страницей `/zakroma/{author_id}` (просто добавление фильтра).
- Проще клиентская фильтрация: `route.query.album` сразу доступен, не нужен matcher regex.
- Sharing URL сохраняет фильтр (для SEO/social).
- Альтернативный формат `/zakroma/{authorId}/{albumId}` отклонён.

**Alternatives considered**:
- **Path-based `/zakroma/{authorId}/{albumId}`**: rejected — требует нового роута, дублирование страницы песен с разной логикой фильтрации.
- **Hash `#album=42`**: rejected — нестандартно для публичного сайта, плохая SEO.

## R3: Сортировка альбомов в сетке

**Decision**: `ORDER BY year ASC NULLS LAST, name ASC` (Clarification Q1, A-009).

**Rationale**:
- Хронологический порядок (от старых к новым) — библиотечный подход, типичный для каталогов классической музыки, сборников и тематических альбомов.
- Тай-брейкер по названию — детерминированный порядок внутри одного года.
- `NULLS LAST` — альбомы без года уходят в конец (PostgreSQL синтаксис).
- Поле `tbl_albums.sort_order` уже существует (используется в `Album.compareTo` и в админке), но в публичной сетке НЕ учитывается — пользователь явно выбрал year+name.

**Alternatives considered**:
- **Год DESC** (рекомендация по умолчанию — новые первыми): rejected пользователем (Clarification Q1).
- **`sort_order` ASC, year ASC, name ASC** (паттерн спек 307 для авторов): rejected — пользователь не упомянул `sort_order`, выбрал year+name.
- **Алфавитный по названию**: rejected — менее информативно для музыкального каталога.

## R4: Визуальный стиль псевдо-плашки

**Decision**: Псевдо-плашка в стиле обычных тайлов (Clarification Q2, A-010) — `<slot name="leading" />` в `AuthorTiles.vue`.

**Rationale**:
- Использует уже реализованный паттерн из спеки 307 для спец-плашки «Отдельные песни разных авторов» на `/zakroma`. Никакого нового UI-компонента.
- Подпись «Все песни автора с группировкой по альбомам» + иконка 📁 (или 🗂).
- Ссылка: `/zakroma/{author_id}` (без query-параметра `album`).

**Alternatives considered**:
- **Кнопка-ссылка над сеткой**: rejected — нарушает визуальную консистентность с `/zakroma` (где спец-плашка именно псевдо-плашка).
- **Псевдо-плашка другого цвета**: rejected — пользователь не запросил визуального выделения.

## R5: UI-настройки (слайдер размера + переключатель режима)

**Decision**: 
- Хранилище — `localStorage` (НЕ БД) — A-003.
- Слайдер: диапазон 200..400px, шаг 50, дефолт 200. Применяется в обоих разделах (`/zakroma`, `/zakroma/{author_id}/albums`).
- Переключатель: «Плашки» (дефолт) / «Таблица». Значения `"tiles"` / `"table"` в `localStorage["zakroma_view_mode"]`.

**Rationale**:
- Чисто клиентские настройки — нет смысла в БД-персистентности (гость не авторизован, нет профиля).
- `localStorage` уже используется в проекте для подобных настроек (`km-zakroma-album-mode`, `km-zakroma-hidden-album-types` — см. `ZakromaView.vue`).
- Composables (`useZakromaSettings.js`) — Vue 3 Composition API, реактивный state через `ref()`.

**Alternatives considered**:
- **БД-персистентность через `tbl_settings`**: rejected — overkill, добавляет API-запросы.
- **Cookie**: rejected — UX-плохо (синхронизация между окнами требует reload).
- **Только на `/zakroma`** (без альбомов): rejected — пользователь явно сказал «И для закромов, и для альбомов».

## R6: Frontend компоненты

**Decision**: Переиспользовать `AuthorTiles.vue` для альбомов (через `<slot name="leading" />` и slot-driven данные). Создать `ZakromaSettings.vue` + `useZakromaSettings.js` composable.

**Rationale**:
- `AuthorTiles.vue` — уже реализованный компонент (спека 307), со слотом `<slot name="leading" />` для спец-плашки. Переиспользование минимизирует код.
- Отдельный view `ZakromaAlbumsView.vue` vs доработка `ZakromaView.vue` — выбран ОТДЕЛЬНЫЙ view (новый роут `/zakroma/:authorId(\d+)/albums`), т.к. текущий `ZakromaView.vue` уже большой и логика существенно отличается (нет группировки песен по альбомам, только плашки альбомов).
- Композаблы — паттерн публичного сайта (Vue 3 Composition API + localStorage).

**Alternatives considered**:
- **Расширить `ZakromaView.vue` новыми состояниями**: rejected — God component, логика слишком сложная.
- **`AlbumTiles.vue` как отдельный компонент**: rejected — 90% кода дублирует `AuthorTiles.vue`. Лучше параметризовать через props (например, `tile-size`).

## R7: Кеш на сервере

**Decision**: `albumsTilesCache` в `PublicApiController.kt` (TTL ≤60с, аналог `authorsTilesCache` в спека 248 + 286). Invalidation через `consumeDirty()` при sync.

**Rationale**:
- Горячий путь `/zakroma/{author_id}/albums` — кеш снижает нагрузку на БД.
- TTL ≤60с — баланс между свежестью и нагрузкой.
- `consumeDirty()` — уже реализованный паттерн инвалидации для авторов; копируется для альбомов.

**Alternatives considered**:
- **Без кеша**: rejected — нагрузка на БД возрастает (хотя с денормализованными счётчиками уже меньше).
- **TTL 5 минут** (как у `authorsTilesCache`): rejected — слишком долго, изменения видны с задержкой.

## R8: Per-feature документ (FR-009)

**Decision**: Создать `docs/features/zakroma-albums-by-author.md` — обязательно для merge (Constitution VI FR-009).

**Rationale**:
- Per-feature документ фиксирует контракт фичи для будущих разработчиков (Pass 380+).
- Шаблон — `docs/features/zakroma-tiles-sort-order.md` (прецедент спеки 307).

**Alternatives considered**:
- **Обновить существующий `docs/features/zakroma-tiles-sort-order.md`**: rejected — это другая фича (sort_order, не albums).

## R9: ADR для фиксации решения по сортировке альбомов

**Decision**: Создать `knowledge/adr/local-0007-album-tile-sort-order.md` — фиксирует выбор `year ASC + name ASC` (а не sort_order).

**Rationale**:
- Решение о сортировке — это local ADR (паттерн спеки 307).
- Защищает от переизобретения: если кто-то решит добавить `sort_order` в SQL-сортировку альбомов в будущем, увидит что решение было осознанным.

**Alternatives considered**:
- **Без ADR**: rejected — решение легко забыть / переизобрести.

## R10: OpenAPI / REST контракт

**Decision**: Endpoint `GET /api/public/authors/{authorId}/albums?scope=main` (см. FR-014).

**Параметры**:
- `authorId: Long` — путь.
- `scope: String = "main"` — query (для будущих scope'ов, как у `/authors-tiles`).

**Ответ**: `200 OK`, `List<AlbumTilePublicDto>` (см. data-model.md).

**Errors**:
- `404 Not Found` — автор не найден.
- `400 Bad Request` — некорректный `authorId`.

**Альтернатива**: GraphQL — не используется в проекте (только REST).

## R11: Sync / два-БД

**Decision**: Sync флаги `sync_albums_push_update_allowed = true`, `sync_albums_pull_update_allowed = true` (без изменений). `recordhash` триггер обновляется миграцией.

**Rationale**:
- `Album` уже зарегистрирован в `SyncRegistry.all` (key="albums") — никаких изменений в `KaraokeProperties.kt`.
- `recordhash` обновляется миграцией (новая функция `update_tbl_albums_recordhash` включает колонки).
- Backfill recordhash для существующих строк — отдельным UPDATE в миграции.

## Технические риски

| Риск | Митигация |
|---|---|
| **Триггер блокирует bulk INSERT** (race condition при большом импорте) | Триггер AFTER — не блокирует, только обновляет. На импорте 1000 песен — overhead минимален (~10k UPDATE на tbl_albums, идемпотентны). |
| **NULL year уходит в конец** — пользователь ожидает конкретный порядок | `NULLS LAST` — стандартная семантика, документирована в spec.md § Edge Cases. |
| **localStorage очищается пользователем** — настройки сбрасываются | Допустимое поведение (нет смысла в БД-персистентности). |
| **`/zakroma/{author_id}/albums` дублирует логику с `/zakroma`** | Компонент `AuthorTiles.vue` параметризуется через props/slots — нет дублирования. |

## Открытые вопросы (на стадии implement уточнятся)

- **Хлебная крошка на странице песен с `?album=`** — A-005 говорит «отложить, если сложно». На implement: проверить, есть ли уже логика динамической хлебной крошки в `ZakromaView.vue`. Если да — добавить «К альбомам автора» в качестве одной из веток.
- **Поведение при `total_song_count = 0` для редактора** — в спеке написано «все альбомы», но если у альбома 0 песен, его показывать? Edge case — решается в implement (скорее всего, скрываем).
- **Старая строка `song_album`** — нужно ли чистить legacy `tbl_songs.song_album`? Нет, это вне scope спеки. Потенциально отдельная спека на миграцию.