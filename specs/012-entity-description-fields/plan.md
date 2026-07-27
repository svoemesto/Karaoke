# Implementation Plan: Доп. поля Author/Album/Song (Описание/Короткое описание/Предупреждение) + новый UI Закромов

**Branch**: `012-entity-description-fields` | **Date**: 2026-07-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-entity-description-fields/spec.md`

## Summary

Три сущности (`Author`, `Album`, `Song`) получают три новых текстовых поля
(`description`, `shortDescription`, `warning`, все пустые по умолчанию). Поля
пробрасываются во все admin- и public-DTO этих сущностей, редактируются в
webvue3. На публичном сайте (`karaoke-public`) поля отображаются на странице
«Закрома» (для автора и альбома) и на странице песни (в информационном блоке).
Одновременно Закрома переходят на `Album` как источник истины для
названия/типа/порядка альбома (вместо эвристики по таблице песен) и получают
переключатель «сквозной / сгруппированный по типу» показ плюс кнопки быстрого
фильтра по типу альбома со счётчиком.

Технический подход: три поля — обычные `VARCHAR`/`TEXT` колонки, без HTML и
без нового rich-text редактора (см. `research.md` §1) — «форматированный
текст» реализуется как читаемый многострочный текст (`white-space: pre-wrap`
на фронте), без риска XSS и без новых npm-зависимостей. Тултип — нативный
атрибут `title` (см. `research.md` §2). Группировка/фильтры по типу альбома —
целиком на фронте поверх уже отдаваемого списка альбомов, backend лишь
добавляет посчитанные счётчики и канонические русские подписи по типу (единый
источник правды вместо задублированной фронтовой мапы).

## Technical Context

**Language/Version**: Kotlin 2.x (JVM 17, Spring Boot 3.x) — backend; JavaScript/Vue 3 (Node 22) — frontend. Без изменений относительно существующего стека.

**Primary Dependencies**: Существующие — Spring Boot, сырой JDBC (`KaraokeConnection`), Vue 3 + Vite, Bootstrap 5 / Bootstrap-vue-next (webvue3), Bootstrap 5 (karaoke-public). Новых зависимостей НЕ добавляется (решение по «форматированному тексту» — см. research.md §1 — намеренно исключает WYSIWYG-библиотеку).

**Storage**: PostgreSQL через сырой JDBC. Новая миграция `deploy/karaoke-db/31_entity_description_fields.sql`, применяется вручную на LOCAL и PROD (Принцип II/III).

**Testing**: В проекте нет CI-тестов для этого слоя (конституция, «Рабочий процесс»: «в CI нет … не полагаться»). Проверка — ручная, через `quickstart.md` (админка + публичный сайт в браузере).

**Target Platform**: `karaoke-app` (admin-машина, ядро/БД-слой), `karaoke-web` (прод, публичный API), `webvue3` (admin SPA, без авторизации), `karaoke-public` (публичный SPA). Docker + docker-compose, без изменений в образах/раннтайме.

**Project Type**: Web application (двух-фронтенд: admin + public, backend Kotlin/Spring, raw JDBC — Принцип V).

**Performance Goals**: Не производительность-критичная фича; переключение режима/фильтров — мгновенно (<1с), см. SC-003/SC-004 spec.md — достигается тем, что фильтрация/группировка целиком клиентская (без похода на сервер).

**Constraints**: Никакого JPA/Hibernate (Принцип II). Любая новая/изменённая колонка синхронизируемой таблицы обязана пересобрать `recordhash`-триггер на LOCAL и PROD (Принцип II/III). Admin (`webvue3`) и public (`karaoke-public`) остаются раздельными приложениями — общий код/компоненты между ними не создаются (Принцип V).

**Scale/Scope**: 3 сущности × 3 поля = 9 новых колонок/атрибутов; ~6-8 затрагиваемых DTO; 2 публичные Vue-страницы (`ZakromaView.vue`, `SongView.vue`); 3 admin-таблицы редактирования (`AuthorsTable.vue`, `AlbumsTable.vue`, `SongEdit.vue`) + расширение `CustomConfirm.vue`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Оценка | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн (NON-NEGOTIABLE) | ✅ N/A | Фича не трогает ffmpeg/MLT/Demucs/Sheetsage/внешние API. |
| II. Сырой JDBC + diff по хэшам (NON-NEGOTIABLE) | ✅ PASS (с действием) | Новые колонки — через `ALTER TABLE`, `KaraokeDbTable`-reflection (Author/Album) или `SongField`-map (Song); JPA не вводится. **Обязательное действие**: пересобрать `update_tbl_authors_recordhash()`, `update_tbl_albums_recordhash()`, `update_tbl_songs_recordhash()`, включив 3 новые колонки в каждую — иначе LOCAL↔SERVER diff не увидит новые поля (см. research.md §3, `29_albums.sql`/`27_author_special_order.sql` как образец). |
| III. Двух-БД синхронизация через SyncRegistry | ✅ PASS (без нового кода) | Все три таблицы уже зарегистрированы в `SyncRegistry.all` (`AuthorsSyncTarget` key=`"authors"`, `AlbumsSyncTarget` key=`"albums"`, Song — key=`"settings"`, `SyncTarget.kt:280-497`) — новых sync-таргетов/флагов `KaraokeProperties` заводить не нужно, только регенерация recordhash (см. выше). |
| IV. Async-очередь / ProcessBuilder | ✅ N/A | Фича не запускает подпроцессы. |
| V. Двух-фронтенд: админка и публичный сайт | ✅ PASS | Admin-правки строго в `webvue3` (`CustomConfirm.vue`, `SongEdit.vue`); public-правки строго в `karaoke-public` (`ZakromaView.vue`, `SongView.vue`). Общих компонентов между ними не создаётся. |
| VI. Code Standards (NON-NEGOTIABLE) | ⚠️ PASS (с действием) | Новые публичные классы/функции (напр. новые свойства `AlbumType`, новый DTO для счётчиков типов) должны получить KDoc/JSDoc с `@see` на этот фича-документ. **per-feature-doc FR-009 конституции** (не путать с локальным FR-009 этой спеки, который про редактирование полей песни в админке — см. `spec.md`): миграция трогает recordhash-механику — вероятно требуется правка `docs/features/dual-db-sync.md` (зафиксировать, что 3 таблицы получили новые колонки); задача будет явно выделена в `/speckit-tasks`. |
| VII. Cross-Machine Setup | ✅ N/A | Фича не трогает AI-конфиги/`.gitattributes`/`.git-blame-ignore-revs`. |

Нарушений, требующих обоснования в Complexity Tracking, нет.

## Project Structure

### Documentation (this feature)

```text
specs/012-entity-description-fields/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── api.md           # Phase 1 output — изменения в API-контрактах
└── tasks.md              # Phase 2 output (/speckit.tasks — ещё не создан)
```

### Source Code (repository root)

```text
# Web application — двух-фронтенд (Принцип V), backend Kotlin, raw JDBC

deploy/karaoke-db/
└── 31_entity_description_fields.sql   # ALTER TABLE x3 + recordhash-триггеры x3

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/
├── Author.kt / AuthorDTO.kt           # + description/shortDescription/warning
├── Album.kt / AlbumDTO.kt             # + description/shortDescription/warning
├── AlbumType.kt                       # + groupLabel/filterLabel (канонические RU-подписи)
├── SongField.kt                       # + DESCRIPTION/SHORT_DESCRIPTION/WARNING
└── Song.kt                            # + свойства-геттеры/сеттеры (по образцу formattedTextSong),
                                        #   INSERT/diff/load/toDTO — 4 места, см. data-model.md

karaoke-app/.../model/SongDTO.kt, SongDTOdigest.kt   # + 3 поля (digest — по необходимости)

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/
├── SongPublicDto.kt                   # + description/shortDescription/warning
└── ZakromaPublicDto.kt                # + поля author-уровня, album-уровня,
                                        #   + новый albumTypeCounts (счётчики/подписи по типу)

webvue3/src/components/
├── Authors/AuthorsTable.vue           # + 3 поля в changeValue()/createAuthor()
├── Albums/AlbumsTable.vue             # + 3 поля в changeValue()/createAlbum()
├── Songs/edit/SongEdit.vue            # + 3 v-model полей рядом с key/bpm
└── Common/CustomConfirm.vue           # + ветка fldIsTextarea (нет сегодня — только input/select/boolean)

karaoke-public/src/views/
├── ZakromaView.vue                    # header: переключатель режима + быстрые фильтры;
│                                       #   author/album блоки: warning/shortDescription/tooltip;
│                                       #   группировка по типу с разделителями
└── SongView.vue                       # .km-meta-card: + warning/shortDescription/tooltip песни
```

**Structure Decision**: Существующая структура репозитория (`karaoke-app` /
`karaoke-web` / `webvue3` / `karaoke-public` / `deploy/karaoke-db`) сохраняется
без изменений — новых модулей/пакетов не создаётся, фича добавляет поля и
атрибуты в уже существующие файлы вдоль уже установленных паттернов (see
data-model.md/research.md). Никакой отдельной "test/" структуры не заводится
— проверка мануальная (quickstart.md), как и для остальных фич проекта.

## Complexity Tracking

*Нет нарушений Constitution Check, требующих обоснования — таблица не заполняется.*
