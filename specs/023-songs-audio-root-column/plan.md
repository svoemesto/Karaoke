# Implementation Plan: Колонка audio_parent_id в таблице песен админки

**Branch**: `023-songs-audio-root-column` | **Date**: 2026-07-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/023-songs-audio-root-column/spec.md`

## Summary

Добавить в админскую таблицу «Песни» (`webvue3/src/components/Songs/SongsTable.vue`) колонку «A-root», отображающую `audio_parent_id` из `tbl_settings`. Колонка должна быть третьей по счёту (после `root`). При наведении на ячейки `root` и `A-root` в тултипе показывать информацию о связанной песне: автор, год, альбом, название. Также добавить фильтр по `audio_parent_id` в `SongsFilterModal.vue`.

Backend-часть: расширить `SongDTOdigest` полем `audioParentId`, убедиться, что `Song.loadListFromDb` уже умеет фильтровать по `audio_parent_id` (поле присутствует в схеме и API `/api/songsdigests`), и добавить отдельный endpoint `/api/song/{id}/shortinfo` для получения минимальной информации о песне по id (для тултипа). Альтернативно — вернуть короткую информацию прямо в дайджесте, но это увеличит размер ответа для всех строк.

## Technical Context

**Language/Version**: Kotlin 1.9, JavaScript/ECMAScript 2022, Vue 3

**Primary Dependencies**: Spring Boot (karaoke-app), Vue 3, Vite, Bootstrap-vue-next 0.40.5, Vuex

**Storage**: PostgreSQL (сырой JDBC через `KaraokeConnection`), MinIO для медиа

**Testing**: Интеграционные тесты в `karaoke-app/src/test` (большинство `@Disabled`, требуют сеть/браузер/credentials). Проверка фичи — ручная через `npm run dev` и gradle-сборку.

**Target Platform**: Linux (dev-pc), Docker-контейнеры на проде

**Project Type**: Web application: backend `karaoke-app` + admin frontend `webvue3`

**Performance Goals**: Таблица «Песни» отображает до нескольких тысяч строк; загрузка дайджеста должна оставаться < 3 секунд. Тултип должен появляться не позднее чем через 500 мс при наведении.

**Constraints**: Тултип не должен делать N+1 запросов на каждую ячейку таблицы. Для массовой загрузки информации о песнях-родителях использовать пакетный endpoint или кэшировать результаты.

**Scale/Scope**: 18k+ песен в БД. Изменения затрагивают только admin SPA (`webvue3`) и `karaoke-app` (DTO + endpoint).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Проверяем соответствие [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md):

| Принцип | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн | ✅ OK | Фича не затрагивает обработку медиа/видео, нет внешних SaaS. |
| II. Сырой JDBC + дифф по хэшам | ✅ OK | Изменения в `SongDTOdigest` и `tbl_settings` требуют пересоздания `recordhash`-триггера при изменении колонок. `audio_parent_id` уже существует в схеме, sync триггер на него уже действует. Для фильтра используется `Song.loadListFromDb` с `WHERE audio_parent_id = ?`, никаких O(n²) сравнений. |
| III. Двух-БД синхронизация через SyncRegistry | ✅ OK | `tbl_settings` уже участвует в sync (`SyncRegistry.all`), `audio_parent_id` уже покрыт `recordhash`. Новый endpoint read-only, не влияет на sync. |
| IV. Async-очередь задач с парсингом stdout | ✅ OK | Не применимо — фича только UI + read-only API. |
| V. Двух-фронтенд: админка и публичный сайт — разные приложения | ✅ OK | Изменения только в `webvue3`, публичный сайт `karaoke-public` не затронут. |
| VI. Code Standards (KDoc/JSDoc, per-feature docs) | ⚠️ Требует внимания | Нужно добавить KDoc/JSDoc к новым методам/полям и обновить per-feature документ `docs/features/songs-table.md` (или создать, если отсутствует) согласно FR-009. |
| VII. Cross-Machine Setup | ✅ OK | Изменения в общих файлах проекта, локальные конфиги не коммитятся. |

## Project Structure

### Documentation (this feature)

```text
specs/023-songs-audio-root-column/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
karaoke-app/
├── src/main/kotlin/com/svoemesto/karaokeapp/
│   ├── controllers/ApiController.kt          # endpoint /api/song/{id}/shortinfo
│   └── model/
│       ├── SongDTOdigest.kt                    # + audioParentId
│       ├── SongDTO.kt                          # already has audioParentId
│       └── Song.kt                             # already has audioParentId + filter mapping
└── src/main/resources/db/migration/            # no new migration needed (column exists)

webvue3/
├── src/
│   ├── components/Songs/
│   │   ├── SongsTable.vue                      # + A-root column, tooltip, template #cell(audioParentId)
│   │   └── filter/
│   │       ├── SongsFilterModal.vue             # + A-root filter field
│   │       └── store.js                         # + songsFilterAudioParentId state/getters/mutations/actions
│   └── store/index.js                           # register filter module (if needed)
└── docs/features/songs-table.md                 # per-feature doc (FR-009)
```

**Structure Decision**: Проект — Gradle multi-module (karaoke-app backend, karaoke-web public API, webvue3 admin SPA). Фича чисто административная, поэтому изменения только в `karaoke-app` (DTO + endpoint) и `webvue3` (таблица, фильтр, store).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нарушений нет. Дополнительная сложность (новый endpoint) обоснована необходимостью избежать N+1 запросов при отображении тултипов для каждой строки таблицы.
