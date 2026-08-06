# Implementation Plan: Кнопка «Типограф» в онлайн-редакторе

**Branch**: `155-editor-typograph-button` | **Date**: 2026-08-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/155-editor-typograph-button/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Добавить кнопку «Типограф» рядом с кнопкой «Очистить маркеры» в двух онлайн-редакторах
караоке-разметки — админском (`webvue3`) и публичном (`karaoke-public`, задания внешних
редакторов). Кнопка переиспользует уже существующий backend-эндпоинт
`POST /api/replacesymbolsinsong` (тот же, что использует кнопка «Типограф» в классическом
редакторе `SubsEdit.vue`) для замены текста текущего голоса по типографским правилам, а затем
пересинхронизирует маркеры с новым текстом тем же способом, каким это уже делает существующий
обработчик `onTextInput()` в обоих онлайн-редакторах. Backend не меняется — только два новых
метода/кнопки во фронтенде.

## Technical Context

**Language/Version**: JavaScript (Vue 3 Options API), без изменений в Kotlin/JDK 17 backend —
фича полностью укладывается в существующий публичный REST-контракт.

**Primary Dependencies**: Vue 3; общий helper `promisedXMLHttpRequest` (уже есть в
`webvue3/src/lib/utils.js` и `karaoke-public/src/lib/utils.js`); общие чистые функции
`splitSyllables`/`relabelSyllables`/`syncMarkersFromSpecTags` из
`composables/useKaraokeEditor.js` (свои копии в каждом фронтенде, уже используются в
`onTextInput()`). Новых зависимостей не требуется.

**Storage**: N/A — фича не меняет схему БД и не добавляет персистентных сущностей; результат
замены — это просто новый текст голоса, который сохраняется существующим механизмом
автосохранения/сохранения задания (тем же путём, что и любое ручное редактирование текста).

**Testing**: В проекте нет CI-тестов для фронтенда (`AGENTS.md`/`constitution.md`: «в CI нет»).
Проверка — вручную по `quickstart.md`, в обоих онлайн-редакторах (админка и прод).

**Target Platform**: Браузер, два независимых Vue 3 SPA (`webvue3` — админка,
`karaoke-public` — публичный сайт).

**Project Type**: Web-приложение, изменение затрагивает только фронтенд (2 файла), backend не
трогаем.

**Performance Goals**: Не применимо — одиночный короткий POST-запрос по клику, идентичный уже
существующему в `SubsEdit.vue`; отдельных требований к производительности нет.

**Constraints**:
- Backend-эндпоинт `POST /api/replacesymbolsinsong` переиспользуется **как есть**, без изменений
  (уже `permitAll` в `SecurityConfig.kt`, доступен обоим фронтендам без дополнительной
  авторизации).
- Ответ эндпоинта — «сырая» строка (`String`, НЕ JSON) — вызывающий код не должен пропускать её
  через `JSON.parse` (в `karaoke-public` есть готовый `apiPost()` в `services/api.js`, но он
  всегда делает `JSON.parse(response)` — для этого эндпоинта он **не подходит**, нужен прямой
  вызов `promisedXMLHttpRequest`, как это уже делает `webvue3/.../store.js#getReplacedSymbolsInText`).
- Поведение замены текста должно быть побайтово идентично тому, что делает `doReplaceText()` в
  `SubsEdit.vue` (тот же эндпоинт, тот же параметр `txt`).
- Пересинхронизация маркеров после замены должна использовать существующий путь `onTextInput()`
  (или эквивалентную последовательность `splitSyllables` → `relabelSyllables` →
  `syncMarkersFromSpecTags`), а не логику `doReplaceBrokenMarkers()` из `SubsEdit.vue` — она
  привязана к другой (region-based) модели маркеров, не применимой к spec-tag-модели онлайн-
  редакторов.
- Оба онлайн-редактора исторически дублируют компоненты/composables (см. `KaraokePlayer.js`,
  `useKaraokeEditor.js`) — Principle V Конституции прямо запрещает смешивать admin/public,
  поэтому кнопка реализуется отдельно в каждом компоненте, без общего пакета.
- `editor-tasks` — одна из 20 ключевых подсистем (`docs/features/README.md`, #23); FR-009
  Конституции требует обновить `docs/features/editor-tasks.md` в том же PR.

**Scale/Scope**: 2 Vue-компонента (`webvue3/src/components/SongEditor/SongKaraokeEditorView.vue`,
`karaoke-public/src/views/EditorWorkView.vue`) + 1 документ (`docs/features/editor-tasks.md`).
Backend, БД, миграции — не затрагиваются.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Применимость | Статус |
|---|---|---|
| I. Self-contained автопайплайн | N/A — фича не касается ffmpeg/melt/Demucs/Sheetsage и не добавляет внешних API в горячий путь обработки медиа | ✅ PASS |
| II. Сырой JDBC + дифф по хэшам | N/A — нет изменений в БД/JDBC-коде | ✅ PASS |
| III. Двух-БД синхронизация через SyncRegistry | N/A — новых сущностей/таблиц нет | ✅ PASS |
| IV. Async-очередь задач | N/A — нет длительных операций/`KaraokeProcess*` | ✅ PASS |
| V. Двух-фронтенд: админка и публичный сайт — разные приложения | Применимо: кнопка реализуется отдельно в `webvue3` и `karaoke-public`, без общего пакета, повторяя уже устоявшийся в проекте паттерн дублирования (`useKaraokeEditor.js`, `KaraokePlayer.js`) | ✅ PASS (соответствует паттерну, не нарушение) |
| VI. Code Standards (KDoc/JSDoc, ktlint/ESLint, FR-009 per-feature docs) | Применимо: новые методы получают JSDoc (FR-006); `docs/features/editor-tasks.md` обновляется в этом PR (FR-009); линтеры прогоняются перед коммитом | ✅ PASS (запланировано в tasks) |
| VII. Cross-Machine Setup | N/A — фича не касается онбординга/AI-конфигов | ✅ PASS |
| VIII. Секреты и git-гигиена | N/A — секретов/`.env`-файлов фича не касается | ✅ PASS |

Нарушений нет, секция «Complexity Tracking» не заполняется.

## Project Structure

### Documentation (this feature)

```text
specs/155-editor-typograph-button/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── replacesymbolsinsong.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
karaoke-app/
└── src/main/kotlin/com/svoemesto/karaokeapp/controllers/
    └── ApiController.kt          # POST /api/replacesymbolsinsong — уже существует, БЕЗ изменений

webvue3/                                              # админка (Vue 3 SPA)
├── src/components/SongEditor/
│   └── SongKaraokeEditorView.vue                     # + кнопка «Типограф» + doTypograph()
├── src/composables/
│   └── useKaraokeEditor.js                           # переиспользуется как есть (без изменений)
└── src/lib/utils.js                                  # promisedXMLHttpRequest — переиспользуется

karaoke-public/                                       # прод/публичный SPA
├── src/views/
│   └── EditorWorkView.vue                            # + кнопка «Типограф» + doTypograph()
├── src/composables/
│   └── useKaraokeEditor.js                           # переиспользуется как есть (без изменений)
└── src/lib/utils.js                                  # promisedXMLHttpRequest — переиспользуется

docs/features/
└── editor-tasks.md                                   # обновить по FR-009 (описать новую кнопку)
```

**Structure Decision**: Изменения — точечные правки в двух уже существующих presentational-
компонентах онлайн-редактора (по одному на каждый фронтенд), без новых модулей/файлов/пакетов.
Backend не меняется — используется существующий публичный (permitAll) эндпоинт
`POST /api/replacesymbolsinsong`. Общий код (`useKaraokeEditor.js`, `lib/utils.js`) уже
задублирован между двумя SPA согласно устоявшемуся в проекте паттерну (Principle V) и не
меняется в рамках этой фичи.

## Complexity Tracking

> Не заполняется — нарушений Constitution Check нет.
