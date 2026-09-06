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