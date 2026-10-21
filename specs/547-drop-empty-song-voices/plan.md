# Implementation Plan: Пустые голоса песни удаляются при сохранении

**Branch**: `547-drop-empty-song-voices` | **Date**: 2026-10-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/547-drop-empty-song-voices/spec.md`

## Summary

При любом сохранении песни удалять голоса с индексом > 0, у которых пуст текст **и** нет
маркеров; оставшиеся сдвигать без дыр, первый голос не трогать. Технический подход
(обоснован в [research.md](research.md)):

1. Чистая функция нормализации `normalizeVoices` (`VoiceNormalization.kt`) + чистые
   парсеры JSON-полей — тестируется без БД, не имеет сайд-эффектов (R2).
2. Хук в начале `Song.saveToDb()` — одна точка покрывает все пути сохранения:
   сохранение голоса, карточку песни, апрув черновика, фоновые задания (R1).
   Гард: если песня загружена без текста/маркеров (`withoutMarkersAndText`), хук не
   работает (R3).
3. Один комбинированный сеттер `setSourceTextAndMarkers` вместо трёх последовательных
   сохранений — иначе удалённый пустой голос «возвращается» при записи текста (R4).
4. Ответ `POST /api/song/savesourcetextmarkers` — `{ok, voice, countVoices}`, чтобы
   редактор после сдвига нумерации писал в тот же голос (R5, FR-010).
5. При фактическом удалении пересчитываются `result_text` и `formatted_text_*` (R6).
6. Схема БД не меняется; данные остаются JSON-текстом в `tbl_songs` (R9).

## Technical Context

**Language/Version**: Kotlin (JVM 17), Spring Boot 2.x/3.x, Gradle multi-module
(`karaoke-app` — движок и админ-API, `karaoke-web` — прод-слой); frontend — Vue 3 +
Vite (`webvue3`, админка).

**Primary Dependencies**: существующие — `kotlinx.serialization` (Json, `SourceMarker`),
SLF4J (логирование, `local-0005`), JUnit 5 (тесты). **Новых зависимостей нет.**

**Storage**: PostgreSQL через сырой JDBC (`KaraokeConnection`, конституция Principle II).
Голоса — JSON-текст в `tbl_songs.source_text` и `tbl_songs.source_markers`;
схема не меняется.

**Testing**: JUnit 5 юнит-тесты чистой функции
(`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/VoiceNormalizationTest.kt`) +
ручная проверка на локальной БД и в админке ([quickstart.md](quickstart.md)).
Интеграционные тесты проекта в основном `@Disabled` и для проверки не используются.

**Target Platform**: admin-машина `nsa-i9` (Linux, Docker), `karaoke-app` разворачивается
только локально; браузер — админка `webvue3`.

**Project Type**: web-service (Kotlin backend) + SPA админка (Vue 3).

**Performance Goals**: сохранение песни не медленнее текущего более чем на 10 % (SC-007);
нормализация — O(число голосов + маркеров), без дополнительных обращений к БД.

**Constraints**:
- Без миграций БД (FR-013) и без JPA/Hibernate (Principle II).
- Ошибки сохранения не глушатся (ADR `local-0002`); нечитаемые данные → нормализация
  пропускается, факт в лог (FR-012).
- Песни, загруженные с `withoutMarkersAndText = true`, не должны получить запись
  `source_text`/`source_markers` (R3) — это защита от потери текста.
- ktlint + ESLint + формат — без новых нарушений (Principle VI).
- Перезапуск `karaoke-app` — только с согласия владельца и после остановки очереди
  (AGENTS.md, Pass 380/381).

**Scale/Scope**: локальная БД — 26 864 песни, из них 298 с 2+ голосами и 6 с пустым
не-первым голосом (строго пустой — 1). Затрагивается: 1 модель + 1 новый файл
нормализации, 3 контроллера, 1 компонент SPA + 1 store action, 1 юнит-тест,
2 файла knowledge, 1 per-feature документ.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Проверка | Итог |
|---|---|---|
| **I. Self-contained автопайплайн** | новый внешний SaaS/сервис не добавляется | PASS |
| **II. Сырой JDBC + дифф по хэшам** | правки через существующий `saveToDb`/`getDiff`, схема и `recordhash` не меняются; JPA не вводится | PASS |
| **III. Two-DB sync через SyncRegistry** | `Song` уже syncable; новых сущностей и флагов нет; нормализация применяется до diff → уезжает обычным push | PASS |
| **IV. Async-очередь задач** | новых процессов и `ProcessBuilder` нет; существующий хук аудио-потомков не подавляется (R7) | PASS |
| **V. Двух-фронтенд** | правки только в `webvue3` (админка); `karaoke-public` не затрагивается | PASS |
| **VI. Code Standards** | KDoc на новую публичную функцию + `@see docs/features/drop-empty-song-voices.md`; per-feature документ в том же PR; ktlint/ESLint | PASS (KDoc — задачи T003–T009, документ — T027, knowledge — T025–T026) |
| **VII. Cross-Machine Setup** | локальные AI-конфиги не затрагиваются | PASS |
| **VIII. Секреты и git-гигиена** | секретов не добавляется; запросы к БД — через `docker exec`, пароли не печатаются | PASS |
| **IX. Knowledge-first** | pre-flight выполнен до спеки (6 ссылок, маяк `.specify/.preflight-drop-empty-song-voices.ok`); SSoT-обновление включено в план (R8) | PASS |
| **IX.3 Subagent workspace isolation** | субагенты не требуются; работа идёт в одной ветке `547-…` | PASS |

Нарушений нет → Complexity Tracking пуст.

## Project Structure

### Documentation (this feature)

```text
specs/547-drop-empty-song-voices/
├── plan.md                       # этот файл
├── spec.md                       # спека (вход)
├── research.md                   # Phase 0 (R1–R10)
├── data-model.md                 # Phase 1: модель голоса и нормализации
├── quickstart.md                 # Phase 1: проверка end-to-end
├── contracts/
│   ├── voice-normalization.md    # чистая функция: семантика + 12 тест-кейсов
│   ├── save-voice-endpoint.md    # POST /api/song/savesourcetextmarkers
│   └── voice-list-endpoint.md    # /api/song/voices + countVoices в DTO
├── checklists/requirements.md    # чек-лист качества спеки
└── tasks.md                      # Phase 2 (/speckit.tasks)
```

### Source Code (repository root)

```text
karaoke-app/
├── src/main/kotlin/com/svoemesto/karaokeapp/
│   ├── model/
│   │   ├── VoiceNormalization.kt      # NEW: чистая нормализация + парсеры голосов
│   │   ├── Song.kt                    # хук в saveToDb(), setSourceTextAndMarkers(),
│   │   │                              #   транзиентный результат нормализации для API
│   │   └── SongField.kt               # (не меняется) ключи SOURCE_TEXT/SOURCE_MARKERS
│   └── controllers/
│       ├── ApiController.kt           # /api/song/savesourcetextmarkers: один сеттер,
│       │                              #   ответ {ok, voice, countVoices}, bad_markers
│       ├── MainController.kt          # легаси-эндпоинты: комбинированный сеттер,
│       │                              #   контракт (вью text) не меняется
│       └── SongEditorController.kt    # апрув черновика: применить весь набор голосов
│                                      #   одним сохранением (setAllVoices) + truncate
├── src/test/kotlin/com/svoemesto/karaokeapp/model/
│   └── VoiceNormalizationTest.kt      # NEW: 12+ юнит-кейсов (контракт)
└── (стиль/лог: ktlint, SLF4J — без новых зависимостей)

webvue3/
└── src/components/Songs/
    ├── store.js                       # saveSourceTextAndMarkers: разбор нового ответа
    └── edit/SubsEdit.vue              # после save: currentVoice/dataVoices по ответу

knowledge/domains/catalog/components/
├── song-entity.md                     # SSoT: правило нормализации голосов при сохранении
└── dictionaries.md                    # кросс-ссылка (гейт .ssot-map.yml для Song.kt)

docs/features/
└── drop-empty-song-voices.md          # NEW: per-feature документ (Principle VI / FR-009)
```

**Structure Decision**: web-service + SPA-админка (Option 2 шаблона). Backend —
`karaoke-app` (модель + контроллеры), frontend — `webvue3` (только админка, конституция
Principle V). `karaoke-web` не меняется: эндпоинт сохранения голоса живёт в админ-API.

## Phase 0 — Research (готово)

[research.md](research.md): R1 точка применения, R2 чистый парсер, R3 гард
`withoutMarkersAndText`, R4 комбинированный сеттер, R5 контракт ответа, R6 производные
поля, R7 аудио-потомки, R8 SSOT/доки, R9 схема БД, R10 тестирование.
Открытых NEEDS CLARIFICATION нет.

## Phase 1 — Design & Contracts (готово)

- [data-model.md](data-model.md) — сущность «голос», предикат пустоты, результат
  нормализации, инварианты I1–I6, пример 15696 до/после, границы и защита.
- [contracts/voice-normalization.md](contracts/voice-normalization.md) — семантика чистой
  функции, парсеры, 12 обязательных тест-кейсов.
- [contracts/save-voice-endpoint.md](contracts/save-voice-endpoint.md) — запрос/ответ,
  `voice = -1`, ошибки `bad_markers`/`bad_voice`/`song_not_found`, побочные эффекты.
- [contracts/voice-list-endpoint.md](contracts/voice-list-endpoint.md) — чтение голосов и
  `countVoices`; форма не меняется, значение — реальное число голосов.
- [quickstart.md](quickstart.md) — проверка end-to-end: юнит-тесты, SQL до/после,
  API-сценарии, UI, регрессия аудио-потомков, идемпотентность, SSOT-гейт.

### Порядок реализации

Единственный источник порядка задач и зависимостей — [tasks.md](tasks.md) (нумерация
`T001…T031`). Здесь фиксируется только форма конвейера:

1. **Foundational (T003–T009)**: чистая нормализация + юнит-тесты → методы `Song`
   (`setSourceTextAndMarkers`, `setAllVoices`) → хук в `saveToDb()`.
2. **US1 (T010–T013)**: эндпоинт с новым ответом → store → `SubsEdit` → проверка на 15696.
3. **US2 (T014–T018)**: легаси-эндпоинты, `/song/savesourcemarkers|savesourcetext`,
   апрув черновика (`SongEditorController`) → проверки.
4. **US3 (T019–T021)** и **US4 (T022–T024)**: рендер-индексация и согласованность редактора.
5. **Polish (T025–T031)**: knowledge, per-feature документ, линтеры/сборка, полный quickstart,
   регрессии 544/546/299/357, неизменность заданий/черновиков.

### Риски и их снятие

| Риск | Снятие |
|---|---|
| Нормализация сработала на песне, загруженной без текста → стёрт текст | гард R3 по `fields[...] == null` + тест-кейс «облегчённая песня не переписывается» |
| Рекурсия `saveToDb` через `sourceMarkersList` | нормализация использует чистые парсеры (R2), геттер не вызывается |
| Пустой голос «воскресает» вторым сохранением текста | комбинированный сеттер (R4) |
| После сдвига нумерации правка уходит не в тот голос | ответ `{voice, countVoices}` + правка `SubsEdit` (R5) |
| Цикл синхронизации аудио-потомков | анализ R7 + шаг регрессии в quickstart |
| CI падает на `.ssot-map.yml` | обновление `dictionaries.md` в том же PR (R8) |
| Рост латентности сохранения | нормализация O(n) без БД; пересчёт производных только при удалении (R6); замер SC-007 |

## Constitution Check (после Phase 1)

Повторная проверка по итогам дизайна: новых нарушений нет.

- Дизайн не добавляет зависимостей, схемных изменений и новых процессов (I, II, IV).
- Контракт ответа меняется только у админского эндпоинта; прод-слой `karaoke-web` и
  публичный фронтенд не затрагиваются (V).
- KDoc/`@see` (задачи T003–T009), per-feature документ (T027) и SSoT-обновление
  (`song-entity.md` — T025, `dictionaries.md` — T026) включены в задачи (VI, IX).
- Внешних секретов, конфигов и кросс-машинных настроек не появляется (VII, VIII).

## Complexity Tracking

> Нарушений Constitution Check нет — таблица пуста.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
