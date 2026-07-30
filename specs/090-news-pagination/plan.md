# Implementation Plan: Пагинация ленты новостей

**Branch**: `090-news-pagination` | **Date**: 2026-07-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/090-news-pagination/spec.md`

## Summary

Страница «Новости проекта» (публичная лента karaoke-public) и раздел «Новости» в
админке (webvue3) сейчас загружают **весь** список `tbl_news` одним запросом
(`News.loadPublished`/`News.loadAll` без LIMIT/OFFSET). После активации
specs/089-auto-news-song-release в `tbl_news` образовалось 19000+ строк (каждая
уже готовая песня в эфире породила отдельную авто-новость вместо ожидавшегося
снапшота на активации — известный факт, отдельная задача по ограничению
частоты создания не входит в объём этой фичи). Технический подход: добавить
серверную постраничную выборку (LIMIT/OFFSET + total count) в оба места чтения
списка и постраничный UI поверх неё — «Показать ещё» на публичной ленте
(карточный UI, дозагрузка без потери уже показанных карточек) и
`<b-pagination>` в админской таблице (табличный UI, посадка на конкретную
страницу). Логика создания новостей (`SongReleaseAnnouncementService`,
`News.createAutoAnnouncement`) не меняется.

## Technical Context

**Language/Version**: Kotlin 2.x (JDK 17, Spring Boot 3.x) + Vue 3 (Vite, JS)

**Primary Dependencies**: Spring Boot Web (существующие контроллеры), raw JDBC
(`KaraokeConnection`), Vuex (webvue3), bootstrap-vue-next `<b-pagination>`
(webvue3, уже используется в проекте для других таблиц), обычные fetch/XHR
обёртки (`newsApi.js`/`promisedXMLHttpRequest`) без новых зависимостей.

**Storage**: PostgreSQL, таблица `tbl_news` (существующая, без структурных
изменений — новых колонок/индексов не требуется: сортировка идёт по уже
используемым `publish_at`/`id`).

**Testing**: Ручная проверка на локальном стенде (docker) с реалистичным
объёмом данных (19000+ строк в `tbl_news`, как на проде) — см. quickstart.md.
Автотестов в CI для этого стека нет (см. constitution.md, «Рабочий процесс»).

**Target Platform**: Backend — Linux Docker-контейнеры (`karaoke-web` на
проде, `karaoke-app`/`karaoke-web` локально); Frontend — браузер (публичный
сайт и админка).

**Project Type**: Web-приложение (backend Kotlin/Spring + 2 отдельных Vue3 SPA).

**Performance Goals**: Открытие страницы новостей (публичной и админской)
отдаёт первую порцию быстрее 2 секунд при 19000+ строках в таблице (SC-001,
SC-002 spec.md).

**Constraints**: Не менять логику авто-создания новостей (specs/089). Не
менять состав полей `NewsDto`. Существующие вызовы `/api/news/create|update|delete`
и `/api/public/news/since` — без изменений контракта.

**Scale/Scope**: ~19000+ строк в `tbl_news` на проде сегодня, дальше растёт по
1 новости на каждую песню, впервые ставшую публично доступной.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Проверка | Статус |
|---|---|---|
| I. Self-contained автопайплайн | Фича не трогает медиа-пайплайн (ffmpeg/melt/Demucs) | ✅ N/A |
| II. Сырой JDBC + дифф по хэшам | Пагинация — обычный LIMIT/OFFSET SELECT для UI-листинга, не sync-diff между LOCAL/SERVER; `Song`/`News` diff-сравнения (`associateBy { it.id }`) не затрагиваются | ✅ Соответствует |
| III. Двух-БД синхронизация через SyncRegistry | Структура `tbl_news` не меняется, `NewsSyncTarget`/`listHashes()`/флаги sync не трогаются | ✅ Соответствует |
| IV. Async-очередь задач | Пагинация — синхронный HTTP-запрос/ответ, не длительная операция, `KaraokeProcess*` не участвует | ✅ N/A |
| V. Двух-фронтенд разделение | Публичная (karaoke-public) и админская (webvue3) реализации пагинации делаются раздельно, без общего кода между SPA | ✅ Соответствует |
| VI. Code Standards | Новые/изменённые публичные функции получат KDoc/JSDoc; per-feature документ — см. ниже | ✅ Соответствует (см. Complexity Tracking о non-need per-feature doc) |
| VII. Cross-Machine Setup | Изменения не затрагивают `.gitattributes`/`.git-blame-ignore-revs`/локальные AI-конфиги | ✅ N/A |

Нарушений, требующих обоснования в Complexity Tracking, нет — секция ниже
пуста по существу (Constitution Check полностью проходит без исключений).

## Project Structure

### Documentation (this feature)

```text
specs/090-news-pagination/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/News.kt                          # loadPublished/loadAll → +limit/offset +count
└── controllers/NewsController.kt          # /api/news/list → +page/perPage параметры

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/
└── PublicNewsController.kt                # /api/public/news → +page/size параметры

karaoke-public/src/
├── services/newsApi.js                    # fetchNews(page, size)
└── views/NewsView.vue                     # "Показать ещё" UI + состояние страницы

webvue3/src/components/News/
├── store.js                               # totalCount/currentPage/perPage state
└── NewsTable.vue                          # <b-pagination> + серверная подгрузка страницы
```

**Structure Decision**: Изменения укладываются в существующую структуру двух
бэкенд-модулей (`karaoke-app` — админский `/api/news/list`, `karaoke-web` —
публичный `/api/public/news`) и двух независимых Vue3 SPA
(`karaoke-public`, `webvue3`) — как и оговорено Principle V. Новых
модулей/директорий не создаётся.

## Complexity Tracking

*Пусто — Constitution Check выше не выявил нарушений, требующих обоснования.*

Примечание (не нарушение, а явное решение по объёму работ): `tbl_news`/раздел
«Новости» не входит в 12 ключевых подсистем `docs/features/README.md`, и эта
фича не меняет sync-логику (`docs/features/dual-db-sync.md` уже обновлён в
specs/089 под текущую схему `tbl_news`). Обновление per-feature документа
поэтому не требуется по FR-009 — фича правит только способ постраничного
чтения существующих данных.
