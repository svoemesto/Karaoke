# Implementation Plan: Заполнение аудиоданных при выборе похожей версии песни

**Branch**: `129-copy-family-audio` (контекст спецификации; текущая checkout-ветка `master`) | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/129-copy-family-audio/spec.md`

## Summary

При ручном выборе строки в окне «Похожие версии песни» нужно сохранить в текущей песне ID выбранной версии, процент успешной акустической сверки и signed-сдвиг в миллисекундах. Процент будет добавлен в существующий запрос выбора, а уже передаваемый `deltaMs` будет использован одновременно для сдвига маркеров и поля `audioDeltaMs`. Backend установит три аудиополя вместе с уже копируемыми текстом, маркерами, `rootId` и статусом в одном `Song.saveToDb()`, вернёт нормализованные значения в ответе, а открытый редактор синхронизирует объект и snapshot без повторного autosave. Автоматический `autoAssignOriginalByWaveform`, публичный frontend, схема БД и SyncRegistry не изменяются.

## Technical Context

**Language/Version**: Kotlin 2.2.20 на JDK 17 для `karaoke-app`; JavaScript ES modules, Vue 3.5.21 и Vuex 4.1 для `webvue3`.

**Primary Dependencies**: Существующие Spring Boot/Kotlin-контроллеры, `KaraokeDbTable`/raw JDBC, Vue/Vuex/Vite и `promisedXMLHttpRequest`; новые зависимости не нужны.

**Storage**: PostgreSQL, существующие non-null-поля `tbl_songs.audio_parent_id`, `audio_similarity_percent`, `audio_delta_ms`; field-level diff, recordhash, SSE и текущий sync-flow уже поддерживают эти поля. Миграция не требуется.

**Testing**: Ручной E2E/API/SQL quickstart; `./gradlew :karaoke-app:compileKotlin`; `./gradlew ktlintCheck`; `cd webvue3 && npm run lint:check && npm run format:check && npm run build`. Frontend unit-test runner в проекте отсутствует, существующие backend-тесты требуют окружения.

**Target Platform**: Браузер админской SPA `webvue3` и локальный admin backend `karaoke-app`; публичный сайт и production-only `karaoke-web` не входят в runtime scope.

**Project Type**: Full-stack web feature в существующем admin SPA + REST/JDBC backend.

**Performance Goals**: Не запускать дополнительную waveform-сверку при выборе: использовать уже сохранённый в модалке результат. Один запрос выбора должен выполнять одно backend-сохранение набора данных; поля должны отображаться сразу после успешного ответа.

**Constraints**: Сохранить signed `deltaMs`; валидные `0%/0 мс` отличать от отсутствия результата по presence nullable-параметров; без новых таблиц, миграций, внешних сервисов и npm/Gradle-зависимостей; не менять автоматические и публичные сценарии; не принимать произвольный `audioParentId` от клиента; защищать self-selection и несогласованные параметры.

**Scale/Scope**: Одна пара «текущая песня — выбранный кандидат» за операцию в редакторе; затрагиваются два Vue-компонента, один Vuex action, один backend DTO/endpoint/helper и один per-feature документ. Массовая обработка каталога и публичное API вне scope.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase 0 Gate

| Principle | Проверка | Статус |
|---|---|---|
| I. Self-contained автопайплайн | Новых внешних SaaS/API в media hot path нет; используется существующий результат локальной сверки. | PASS |
| II. Raw JDBC + diff/hash | Новых сущностей/запросов сравнения БД нет; три поля сохраняются существующим `Song.saveToDb()` и `getDiff()`. | PASS |
| III. Dual-DB SyncRegistry | Схема и sync-набор не меняются; уже синхронизируемые поля используются без новых колонок. | PASS |
| IV. Async queue | Новых subprocess/длительных задач нет; `WaveformCompare` повторно не запускается при выборе. | PASS |
| V. Admin/public separation | Изменения ограничены `webvue3` и локальным `karaoke-app`; `karaoke-public` не затрагивается. | PASS |
| VI. Code standards | План включает KDoc/JSDoc `@see`, ktlint/ESLint/Prettier checks и обновление `docs/features/songs-table.md`. | PASS |
| VII. Cross-machine setup | Не изменяются персональные AI-конфиги, ветки, deployment-конфиги или локальные credentials. | PASS |
| VIII. Secrets/git hygiene | Новые файлы не содержат секретов; deploy/env-файлы и сервер не затрагиваются. | PASS |

**Gate result**: PASS. Необходимость нарушения принципов не обнаружена; `Complexity Tracking` не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/129-copy-family-audio/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── select-family-song.md
└── tasks.md                 # создаётся отдельной командой /speckit.tasks
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── Utils.kt                 # ручное применение family selection и единый save
└── controllers/ApiController.kt  # request/response и валидация endpoint

webvue3/src/components/Songs/
├── store.js                 # selectFamilySongPromise и snapshot mutation
└── edit/
    ├── FamilySongsModal.vue # payload выбора с процентом и signed delta
    └── SongEdit.vue         # применение ответа, busy/error UX

docs/features/
└── songs-table.md           # описание ручного выбора в Songs/SongEdit
```

**Structure Decision**: Сохраняется существующая split-архитектура admin frontend (`webvue3`) и core backend (`karaoke-app`). Данные остаются в существующей сущности `Song`; отдельный сервис, репозиторий или новый модуль не вводятся.

## Implementation Design

### Phase 0: Research decisions

Исследование зафиксировано в [research.md](./research.md):

1. `FamilySongsModal.compareResults[song.id]` — источник результата конкретного кандидата.
2. Новый request-параметр — `audioSimilarityPercent`; `deltaMs` переиспользуется для `audioDeltaMs`.
3. Nullable-пара применяется для различения успешных нулевых значений и отсутствия сверки.
4. Ручной caller передаёт opt-in `audioParentId = another.id`; автоматический caller helper остаётся без аудиоприсваиваний.
5. Все изменения выполняются до одного `saveToDb()`, затем подтверждаются перечитыванием.
6. Ответ содержит сохранённые аудиополя; frontend обновляет current object и snapshot.

### Phase 1: Backend contract and persistence

1. Расширить `SelectFamilySongResultDto` полями `audioParentId`, `audioSimilarityPercent`, `audioDeltaMs`, сохранив текущие `rootId` и `idStatus`.
2. Расширить `selectFamilySong` nullable-параметром `audioSimilarityPercent`; оставить `deltaMs` существующим параметром.
3. До загрузки/мутации проверить self-selection, наличие кандидата, диапазон процента и согласованность присутствия `audioSimilarityPercent`/`deltaMs`. При отсутствии обеих метрик использовать `0/0`.
4. Расширить `applyFamilySongSelection` opt-in параметрами ручного аудиовыбора. Устанавливать выбранный `another.id`, процент и дельту перед существующим единственным `song.saveToDb()`; не менять поведение `autoAssignOriginalByWaveform` при вызове с дефолтами.
5. Не применять automatic audio-parent flattening и не менять `audioCompareHistory`.
6. После сохранения проверить загрузкой из БД, что три аудиополя применились. При несоответствии вернуть ошибку вместо ложного успешного DTO.
7. Добавить KDoc/`@see docs/features/songs-table.md` для изменяемого DTO/endpoint, если текущий публичный символ не имеет соответствующей ссылки.

### Phase 1: Frontend flow and state

1. В `FamilySongsModal.select()` передавать `audioSimilarityPercent` только для `status === 'done'`; передавать `null` для неисполненной/ошибочной сверки, включая корректные нули.
2. Расширить `selectFamilySongPromise` пробросом нового nullable-параметра, не отправляя его при отсутствии результата.
3. В `SongEdit.selectFamilySong()` передавать новый payload, ждать ответа, применять `rootId`, `idStatus` и три возвращённые аудиополя к открытой песне.
4. Закрывать модалку только после успешного ответа; добавить in-flight guard, обработку HTTP/JSON-ошибки и toast с сохранением возможности повторить выбор.
5. Добавить узкую Vuex mutation для синхронизации применённых полей в `currentSong` и `snapshotSong`, чтобы backend-сохранённые значения не ушли повторно через debounce autosave.
6. Не расширять `SongDTOdigest`: таблице нужен существующий `audioParentId`, а процент/дельта требуются только открытому редактору.

### Phase 1: Documentation and verification artifacts

1. Добавить [data-model.md](./data-model.md) с сущностями, sentinel-значениями, переходами и persistence-инвариантами.
2. Добавить [contracts/select-family-song.md](./contracts/select-family-song.md) с HTTP и UI event contract, валидацией и ошибками.
3. Добавить [quickstart.md](./quickstart.md) с локальными ручными сценариями, API/SQL-проверкой и статическими командами.
4. Обновить `docs/features/songs-table.md`: ручной выбор похожей версии, три аудиополя, правило `0/0`, единый save и ограничения автоматического flow.
5. Проверить formatting/lint/compile и пройти все сценарии quickstart.

## Complexity Tracking

Нарушений Конституции нет. Новые модули, зависимости, таблицы, миграции и отдельные persistence-слои не вводятся; дополнительная backend-проверка после существующего `saveToDb()` локализована в endpoint ручного выбора.

## Post-Phase 1 Constitution Check

| Principle | Результат после дизайна |
|---|---|
| I | PASS — нет внешних media-интеграций. |
| II | PASS — raw JDBC/diff сохраняются, единый UPDATE. |
| III | PASS — существующие sync-поля, без schema/registry изменений. |
| IV | PASS — нет новых subprocess или async queue jobs. |
| V | PASS — только admin flow. |
| VI | PASS — docs/KDoc/JSDoc и проверки включены в scope. |
| VII | PASS — нет персональных конфигов/серверных правок. |
| VIII | PASS — секреты не затрагиваются. |

**Final gate result**: PASS.
