---
status: Active
slug: 293-skip-author-toggle
related:
  - ../features/286-author-song-counts-cache.md
  - ../features/182-editor-self-assign-tasks.md
---

# 293-skip-author-toggle — Галочка «Работа со SKIP-авторами и песнями»

> **SDD-сводка**: 1-2 страницы. Drill-down — `specs/293-skip-author-toggle/`.

## Что

Новая булева галочка в настройках пользователя: «Может работать со
SKIP-авторами и песнями». Позволяет залогиненному пользователю
(админу/редактору) видеть и управлять контентом, скрытым от публики
механизмами SKIP (`tbl_authors.skip = TRUE` или тег `SKIP` в
`tbl_songs.tags`).

## Где

- **Выставляется**: `webvue3` → список пользователей → карточка
  редактора → раздел «Права и роли» → новая галочка.
- **Действует**: `karaoke-public` → «Закрома» (`/закroma`,
  `/api/public/закroma`), история прослушиваний, страницы песен
  (`SongView`), `/api/public/authors-tiles`.
- **НЕ отображается** в `karaoke-public/AccountView` (по выбору
  пользователя в `/speckit.specify` 2026-09-02).

## Почему

Раньше редакторы не могли увидеть SKIP-контент через UI — приходилось
лезть в БД напрямую для снятия SKIP-тега или исправления метаданных.
Это замедляло реакцию на реквесты правообладателя и создавало риск
ошибок.

## Compliance

`SKIP` = «удалено по требованию правообладателя». **Share-link для
SKIP-песен запрещён** независимо от `can_work_with_skipped`:

- UI: кнопки `Поделиться` / `Share-link` скрыты для SKIP-песен.
- Бэкенд: `SongShareLinkService.createLink` бросает `SongSkipped` →
  HTTP `409 Conflict` с `errorCode: "share.songSkipped"`.

OG/SEO-страницы (для ботов) НЕ меняются — SKIP-песни по-прежнему
скрыты от индексации (compliance).

## Что важно знать

- **Авто-выдачи админам НЕТ** (clarify Q3, 2026-09-02). Админ выставляет
  галочку себе явно, как и любому другому пользователю. Унифицированная
  логика без OR с `is_admin`.
- **Визуальный бейдж «SKIP»**: показывается только пользователям с
  галочкой, чтобы они понимали, что это скрытый контент (страховка от
  случайной публикации).
- **Миграция V45** добавлена в `deploy/karaoke-db/45_site_user_can_work_with_skipped.sql`,
  idempotent.
- **recordhash-триггер** пересоздан (Constitution §III), sync LOCAL↔SERVER
  работает.
- **Race condition**: изменение флага отражается на следующем HTTP-запросе
  (без logout/login) — `SiteUserResolver` намеренно не кэширует.

## Файлы

### Backend

- `deploy/karaoke-db/45_site_user_can_work_with_skipped.sql` — миграция.
- `karaoke-app/.../model/SiteUser.kt` — поле `canWorkWithSkipped`.
- `karaoke-app/.../model/SiteUserDto.kt` — DTO-поле.
- `karaoke-app/.../model/Zakroma.kt` — параметр `canSeeSkipped`,
  поля `authorSkip`/`contentRemoved`.
- `karaoke-web/.../controllers/MainController.kt` — `/закroma`,
  `/filter` с флагом.
- `karaoke-web/.../controllers/PublicApiController.kt` — `/api/public/закroma`,
  `/api/public/authors` с флагом.
- `karaoke-web/.../dto/ZakromaPublicDto.kt` — поля DTO.
- `karaoke-web/.../util/ShareErrorCode.kt` — `SONG_SKIPPED`.
- `karaoke-web/.../services/SongShareLinkService.kt` — `SongSkipped` exception,
  `songIsSkipped` проверка.
- `karaoke-web/.../controllers/PublicShareController.kt` — 409 catch.
- `karaoke-app/.../controllers/ListeningHistoryController.kt` — SKIP-фильтр
  снят для admin endpoint.

### Frontend

- `webvue3/src/components/SiteUsers/edit/SiteUserEdit.vue` — галочка.
- `webvue3/src/components/SiteUsers/store.js` — payload.
- `webvue3/src/components/SiteUsers/SiteUsersTable.vue` — колонка.
- `karaoke-public/src/views/ZakromaView.vue` — бейджи автора/песен.
- `karaoke-public/src/views/SongView.vue` — бейдж + скрытие share.

### Документация

- `archive/docs/features/editor-skipped-content-access.md` — per-feature.
- `specs/293-skip-author-toggle/spec.md` — фиче-спека.
- `specs/293-skip-author-toggle/plan.md` — implementation plan.
- `specs/293-skip-author-toggle/research.md` — технические решения.
- `specs/293-skip-author-toggle/data-model.md` — модель данных.
- `specs/293-skip-author-toggle/contracts/` — API-контракты.
- `specs/293-skip-author-toggle/quickstart.md` — validation guide.

## Связанные bounded contexts

- **identity** (`livedocs/domain/identity.md`) — `SiteUser` aggregate,
  добавлено новое поле.
- **catalog** (`livedocs/domain/catalog.md`) — SKIP-механика для авторов
  и песен, не изменилась; изменился только runtime-фильтр.

## Связанные фичи (для drill-down)

- `286-author-song-counts-cache` — счётчики SKIP-авторов в БД
  (контрастный пример: данные в БД есть, UI фильтрует).
- `182-editor-self-assign-tasks` — паттерн `canSelfAssignTasks`
  (аналогия для нового флага).
- `live-docs/architecture-notes.md` — общий контекст SKIP-механики.