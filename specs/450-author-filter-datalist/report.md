# Report: Список авторов в фильтрах

**Issue**: OpenProject #183 — «Список авторов в фильтрах»
**Spec**: [`specs/450-author-filter-datalist/spec.md`](spec.md)
**Branch**: `450-author-filter-datalist`
**Дата**: 2026-09-24

## Что сделано

Поля «Автор:» в модальных окнах фильтров компонентов **«Авторы»** и
**«Альбомы»** admin SPA `webvue3` теперь показывают нативный список
подсказок (`<datalist>`) с именами авторов — так же, как это уже работало
в фильтре компонента **«Песни»**. Источник подсказок — существующий
Vuex-геттер `songAuthorsPromise` (`POST /api/songs/authors`), серверных
изменений нет.

## Изменённые файлы

| Файл | Изменение |
|---|---|
| `webvue3/src/components/Authors/filter/AuthorsFilterModal.vue` | `dictAuthors` + `<datalist id="authorsDictAuthorsId">` + `list=` + JSDoc |
| `webvue3/src/components/Albums/filter/AlbumsFilterModal.vue` | `dictAuthors` + `<datalist id="albumsDictAuthorsId">`; заменена «мёртвая» привязка `list="list_authors"` |
| `docs/features/author-filter-datalist.md` | НОВЫЙ per-feature документ (FR-009) |
| `knowledge/system/frontend/filter-stores.md` | Отметка о едином источнике datalist авторов + changelog |

## Соответствие требованиям

| FR | Статус |
|---|---|
| FR-001 подсказки в фильтре «Авторы» | ✅ |
| FR-002 подсказки в фильтре «Альбомы» | ✅ |
| FR-003 единый источник (`songAuthorsPromise`) для трёх фильтров | ✅ |
| FR-004 выбор подставляет точное имя, семантика не меняется | ✅ |
| FR-005 пустое поле — прежнее поведение | ✅ |
| FR-006 подсказки опциональны (ошибка → текстовый ввод) | ✅ |
| FR-007 одинаковое поведение во всех трёх фильтрах | ✅ |
| FR-008 загрузка не блокирует открытие/не сбрасывает фильтры | ✅ |
| FR-009 только `webvue3` | ✅ |

## Проверки

- `cd webvue3 && npm run lint` — OK
- `cd webvue3 && npm run build` — OK (✓ built)
- `cd webvue3 && npm run format:check` — OK (prettier --check по изменённым файлам)
- `tools/check-feature-doc.sh docs/features/author-filter-datalist.md` — OK
- `tools/check-knowledge-structure.sh` — 9/9 checks passed
- `python3 tools/lint-knowledge.py` — новых нарушений нет (остались
  предсуществующие: `mko-*.md` в rendering не перечислены в domain.md)

## Не выполнено агентом

- **Ручной E2E** (`quickstart.md`, сценарии 1–4) — требует браузера и
  живого backend с непустым справочником авторов; выполняется владельцем.
- **Tracker workflow** (`add-comment` + `mark-review` #183) — после merge PR
  и ревью владельцем.

## Governance

- Claim `#183` в OpenProject не удался: `HTTP 422 — The chosen user is not
  allowed to be 'Assignee'`. Требуется уточнение проекта/типа issue.
- Ветка создана через `tools/specify-bootstrap.sh` (номер 450).
- Knowledge-first pre-flight выполнен (см. spec.md § Knowledge References).

## Ссылки

- [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md),
  [data-model.md](data-model.md),
  [contracts/ui-author-datalist.md](contracts/ui-author-datalist.md),
  [quickstart.md](quickstart.md), [tasks.md](tasks.md)
