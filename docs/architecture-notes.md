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