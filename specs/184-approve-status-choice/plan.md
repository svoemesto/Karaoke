# Implementation Plan: Выбор статуса песни при апруве задания редактора (5 или 6)

**Branch**: `184-approve-status-choice` | **Date**: 2026-08-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/184-approve-status-choice/spec.md`

## Summary

При одобрении задания онлайн-редактора (`POST /api/songeditor/approve`) админ
получает выбор финального статуса песни: **5** («Маркеры проверены» — принять работу
редактора, но НЕ запускать рендер DEMO и sync related-таблиц) или **6**
(«Готово» — текущее поведение). Сегодня статус жёстко зашит: `song.fields[SongField.ID_STATUS] = "6"`
(`SongEditorController.kt:380-383`), и чтобы отложить релиз, админ вынужден
после апрува вручную понижать статус в `SongEdit` — с риском, что автотриггеры
(рендер DEMO, sync, новости) успеют сработать в промежутке.

Технический подход — четыре аддитивные правки, без миграций БД и без новых
зависимостей:

1. **Backend `/approve`**: новый `@RequestParam(required = false) idStatus: Int?`
   (валидные значения 5/6, `null` → 6); хардкод заменяется на выбранное значение
   с сохранением защиты `if (song.idStatus < target)`; `triggerRenderMp4DemoIfNeeded`
   и `thread { sync related }` гейтятся по **фактическому** `song.idStatus >= 6`
   (см. [research.md](./research.md) D-2), push самой песни — всегда (D-3).
2. **Backend `/byId`**: аддитивное поле `idStatus` (статус ПЕСНИ) в ответ — без него
   UI-гейт US2 нереализуем, песня в методе уже загружена, лишних запросов нет (D-5).
3. **Vuex `approveAssignment`**: dual-format payload (`Number` | `{id, idStatus}`)
   по образцу уже существующего `loadAssignmentById` (D-6).
4. **`ReviewModal.vue`**: radio-group «Финальный статус песни» (default 6) +
   баннер-подсказка; radio ВСЕГДА виден при известном `idStatus` (Pass 51-3.1); нативные radio в
   существующей `se-*` дизайн-системе модалки, без `bootstrap-vue-next` (D-7).

Новости («в коллекции» / «в эфире») дополнительной защиты не требуют — обе ветки
уже гейтятся по `idStatus == 6` / `>= 6` в трёх независимых местах кода (D-4).

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17) — backend; Vue 3 + Vite (Node 22 LTS) — admin SPA.

**Primary Dependencies**:
- Backend: Spring Boot (`@PostMapping`/`@RequestParam`), сырой JDBC через
  `KaraokeConnection` (реально — только существующий `Song.saveToDb()`).
- Frontend: Vue 3 + Vuex (`webvue3`), нативные `<input type="radio">` + локальные
  `se-*` CSS-классы модалки. **Новых зависимостей нет.**

**Storage**: PostgreSQL, таблица `tbl_songs` (колонка `id_status`) — **изменений схемы
нет**, миграций нет, `recordhash`-триггеры не пересоздаются (D-8).

**Testing**: ручные сценарии в [quickstart.md](./quickstart.md) + прямые `curl` к
`/approve` и `/byId`. Автотестов в CI нет (AGENTS.md «Тесты»); проверка линтеров —
`./gradlew ktlintCheck`, `cd webvue3 && npm run lint:check`, `bash tools/check-kdoc-coverage.sh`,
`bash tools/check-jsdoc-coverage.sh webvue3`.

**Target Platform**: Linux, `karaoke-app` (admin-машина, JRE 17 в Docker) + `webvue3`
(SPA, Docker nginx). На PROD `karaoke-app` не разворачивается — эндпоинты
`/api/songeditor/*` живут только на admin-машине (constitution «Технологический стек»).

**Project Type**: web-service + admin SPA (см. `Project Structure` ниже).

**Performance Goals**:
- `POST /api/songeditor/approve` — латентность не растёт: добавляется только
  сравнение Int и одно ветвление; при `idStatus = 5` время ответа **уменьшается**
  (не создаётся процесс рендера, не стартует sync-поток).
- `POST /api/songeditor/byId` — +0 SQL-запросов (песня уже загружена, `SongEditorController.kt:276`).
- UI: выбор статуса + одобрение ≤ 2 клика (SC-001).

**Constraints**:
- Обратная совместимость обязательна: запрос без `idStatus` MUST вести себя
  идентично сегодняшнему (SC-003) — обеспечивается `required = false` + дефолт 6.
- Апрув НЕ понижает статус (`if (song.idStatus < target)`) и НЕ падает с ошибкой
  при попытке понижения (spec Edge Cases, FR-012).
- Идемпотентность повторного апрува (specs/094) не ломается — короткое замыкание
  `already_approved` стоит ДО логики статуса (`SongEditorController.kt:333-335`).
- Push одобренной разметки на SERVER выполняется при любом статусе (D-3).

**Scale/Scope**: ~18k песен в `tbl_songs`; апрув — единичная операция админа
(единицы в час). 2 файла backend + 2 файла frontend, ~60 строк изменений.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Verdict | Notes |
|---|---|---|
| I. Self-contained автопайплайн | ✅ Pass | Никаких внешних API. Наоборот — при статусе 5 локальный пайплайн (рендер DEMO) осознанно НЕ запускается. |
| II. Сырой JDBC + дифф по хэшам | ✅ Pass | Новых SQL-запросов нет. Статус пишется существующим `Song.saveToDb()` (reflection-diff). O(n²)-риска нет — сравнений коллекций не добавляем. |
| III. Двух-БД синхронизация | ✅ Pass | Схема БД не меняется → `recordhash`-триггеры не трогаем. `tbl_songs.id_status` уже в sync. Push песни сохраняется (D-3); sync related-таблиц гейтится по факту финального статуса (D-2) — это поведенческий гейт, не изменение `SyncRegistry`. |
| IV. Async-очередь + `redirectErrorStream` | ✅ Pass | `ProcessBuilder` не добавляется. `KaraokeProcess.createProcess(RENDER_MP4_DEMO)` только **условно не вызывается** — существующая изоляция сбоев в `triggerRenderMp4DemoIfNeeded` сохраняется. |
| V. Двух-фронтенд | ✅ Pass | Правки только в `webvue3` (admin). `karaoke-public` не затронут — публичные редакторы не апрувят задания. Bootstrap-классы в `se-*`-модалку не тащим (D-7). |
| VI. Code Standards | ✅ Pass | KDoc на изменённых публичных методах контроллера обновляется (описание нового параметра + гейтов); JSDoc-блок `ReviewModal` дополняется. **FR-009**: в том же PR обновляются `docs/features/approve-pipeline.md` (гейт рендера/sync) и `docs/features/editor-tasks.md` (UI апрува). |
| VII. Cross-Machine Setup | ✅ Pass | Локальные AI-конфиги не трогаем. Новых файлов вне `specs/` не создаётся. |
| VIII. Секреты и git-гигиена | ✅ Pass | Новых секретов/env нет. Хардкод IP/паролей не вводится. |

**Gates**: ✅ Все пройдены — `Complexity Tracking` пуст (нарушений нет).

**Re-check после Phase 1 (design)**: ✅ Пройдены повторно. Design не ввёл ни новых
таблиц, ни новых внешних вызовов, ни DTO с boolean-полями (ловушка Jackson `is`
неприменима — `idStatus` числовой). Единственное расширение контракта — аддитивное
поле в JSON-ответе `/byId` и необязательный query-параметр в `/approve`; оба
обратно совместимы (см. [contracts/](./contracts/)).

## Project Structure

### Documentation (this feature)

```text
specs/184-approve-status-choice/
├── plan.md                      # Этот файл (/speckit.plan output)
├── research.md                  # Phase 0 output — D-1..D-8
├── data-model.md                # Phase 1 output — сущности, переходы статуса
├── contracts/
│   ├── README.md                # Phase 1 output — индекс + инварианты
│   ├── approve-endpoint.md      # Phase 1 output — дельта к specs/094 контракту
│   └── byid-endpoint.md         # Phase 1 output — аддитивное поле idStatus
├── quickstart.md                # Phase 1 output — ручная валидация (10 сценариев)
├── checklists/
│   └── requirements.md          # /speckit.specify output (16/16 pass)
└── tasks.md                     # Phase 2 output (/speckit.tasks — НЕ создано)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── controllers/
│   └── SongEditorController.kt        # ИЗМЕНЯЕТСЯ
│       ├── approve()          :315-487  # +param idStatus, замена хардкода "6",
│       │                                # гейты render/sync по факту (FR-001..FR-004, FR-012)
│       └── byId()             :266-297  # +поле "idStatus" в map ответа (FR-011)
└── model/
    ├── Song.kt                        # НЕ ИЗМЕНЯЕТСЯ (markNewsAvailableIfReady:5126
    │                                  # уже гейтит по idStatus == 6 — см. research D-4)
    └── SongField.kt                   # НЕ ИЗМЕНЯЕТСЯ (ID_STATUS уже есть)

webvue3/src/components/SongEditor/
├── store.js                           # ИЗМЕНЯЕТСЯ
│   └── approveAssignment()    :200-206  # dual-format payload (FR-006, FR-008)
└── ReviewModal.vue                    # ИЗМЕНЯЕТСЯ
    ├── template               :12-24    # информационный бейдж статуса песни в .se-meta (Pass 51-3.1: ВСЕГДА виден, не блокирует radio)
    ├── template               :92-106   # radio-group перед кнопками (FR-007); баннер УДАЛЁН Pass 51-3.2 (FR-010 [REMOVED])
    ├── data()                 :134-144  # +selectedIdStatus: 6
    ├── computed               :145-198  # +songIdStatus, +canChooseIdStatus, +idStatusLabel
    ├── watch                  :199-211  # +a() → сброс selectedIdStatus в 6 (ловушка D-7)
    └── doApprove()            :281-308  # dispatch объектом + сообщение со статусом (FR-008, FR-009)

docs/features/
├── approve-pipeline.md                # ОБНОВЛЯЕТСЯ (constitution VI / FR-009):
│                                      # условный запуск рендера DEMO и sync related
└── editor-tasks.md                    # ОБНОВЛЯЕТСЯ: выбор статуса в UI апрува

karaoke-public/                        # НЕ ЗАТРОНУТ
deploy/karaoke-db/                     # НЕ ЗАТРОНУТ (миграций нет — research D-8)
```

**Structure Decision**: изменения строго локализованы в существующих файлах —
2 метода одного контроллера (`karaoke-app`, admin-only) + 1 Vuex-action и
1 SFC-компонент (`webvue3`, admin SPA). Новых модулей, классов, DTO, таблиц и
миграций не создаётся; это соответствует constitution V (разделение
admin/public: публичный `karaoke-public` не участвует в апруве заданий) и
принципу «расширять существующие паттерны, а не вводить greenfield».

`ReviewModal.vue` — общий компонент для трёх точек входа (`SongEditorTable`,
`SongsTable`, `SongEdit`), поэтому контрол появляется во всех трёх местах без
правок в вызывающих компонентах (см. spec, Key Entities).

## Complexity Tracking

> Нарушений Constitution Check нет — таблица не заполняется.
