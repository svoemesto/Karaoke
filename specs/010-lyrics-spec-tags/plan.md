# Implementation Plan: Спецтеги в тексте песни для авто-разметки маркеров

**Branch**: `010-lyrics-spec-tags` | **Date**: 2026-07-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-lyrics-spec-tags/spec.md`

## Summary

Добавить в текст песни распознавание спецтегов вида `~имя~` / `~имя:значение~`, расположенных на отдельной строке, которые при построении разметки транслируются в конкретный тип маркера (`newline`, `group:N`, `comment:текст` в v1, плюс человекочитаемые алиасы `Куплет`/`Припев`/`Бридж`/`Приговор` для `group:0..3`). Механизм — строго аддитивный обвес над уже существующим кодом: генерализуется текущее правило "пустая строка → маркер NEWLINE" (`WhisperMarkerAligner.buildTargetWords`/`buildMarkersFromSyllableTimes`) на произвольный реестр тег→маркер (+ таблица алиасов, резолвящихся в ту же пару markertype+label, что и их каноническая параметризованная форма), и добавляется зеркальный, тоже строго аддитивный, шаг синхронизации в редакторе разметки (`SubsEdit.vue`), который на пересчёте маркеров добавляет отсутствующие маркеры по тегам без удаления/изменения существующих. При отсутствии тегов в тексте поведение обеих сторон не меняется.

## Technical Context

**Language/Version**: Kotlin (JDK 17, Spring Boot, тот же модуль `karaoke-app`) для backend-парсинга/генерации маркеров; TypeScript/Vue 3 (`webvue3`) для зеркальной логики в редакторе — оба уже используемые в проекте стеки, новых языков/версий не вводится.

**Primary Dependencies**: Нет новых зависимостей. Backend: `kotlin.text.Regex` (stdlib), уже используемый `kotlinx.serialization` для `SourceMarker`. Frontend: чистый JS/Vue-код внутри уже существующего `SubsEdit.vue`, без новых npm-пакетов.

**Storage**: N/A — новых таблиц/колонок не требуется. Маркеры продолжают храниться в существующей колонке `source_markers` (JSON, `Settings.sourceMarkersList`, см. `model/Settings.kt:407-437`). Спецтег — это не персистентная сущность, а результат парсинга уже существующего поля `sourceText`.

**Testing**: В проекте нет CI-гейта на тесты (см. `constitution.md`, секция «Рабочий процесс»: «Тесты — интеграционные, большинство `@Disabled`... не полагаться на них как на проверку»). Тем не менее для чистых функций парсинга тегов (`SpecTags.parseLine`) и генерации маркеров (`buildMarkersFromSyllableTimes`) добавляются точечные unit-тесты (без сети/БД/браузера) как регрессионная страховка для инварианта обратной совместимости — они быстрые и не зависят от внешних сервисов, так что не попадают под предупреждение конституции про ненадёжные интеграционные тесты. Финальная приёмка — вручную пользователем в реальном редакторе (см. `quickstart.md`).

**Target Platform**: Backend — существующий JVM-процесс `karaoke-app` на admin-машине (без изменений в деплое/контейнерах). Frontend — существующая admin SPA `webvue3` в браузере.

**Project Type**: Расширение существующего backend-модуля + существующего admin-фронтенда в монорепозитории (не новый проект/сервис).

**Performance Goals**: Не вводит новых требований к производительности — парсинг тегов — это O(строк текста песни) с простым regex, выполняется в рамках уже существующих вызовов (`buildMarkersFromSyllableTimes` при "Точные маркеры", пересчёт маркеров при сохранении в `SubsEdit.vue`). Заметной деградации по сравнению с текущим поведением быть не должно.

**Constraints**: Жёсткое требование обратной совместимости (см. `spec.md` FR-004, FR-005, FR-010) — при отсутствии тегов в тексте результат обеих операций (авто-разметка, ручной пересчёт) должен быть побайтово идентичен сегодняшнему. Механизм не должен искажать подсчёт слогов, используемый для сопоставления с результатом форс-алаймента (Whisper/`align.py`) — тег обязан быть невидим для этого сопоставления.

**Scale/Scope**: Изменения затрагивают ТРИ независимые реализации одного контракта (см. `contracts/tag-registry.md`): `WhisperMarkerAligner.kt` (+ новый файл-реестр тегов) на backend, `SubsEdit.vue` в `webvue3` (admin-редактор), и `useKaraokeEditor.js`+`EditorWorkView.vue` в `karaoke-public` (краудсорсинг-редактор заданий — найдено постфактум, см. Constitution Check ниже и `research.md` §9: текст задания там — прямая копия официального `sourceText`, который может уже содержать спецтеги). `karaoke-web` (`PublicSongEditorController.kt`) — чистый passthrough sourceText/markers, изменений не требует. БД-схему, `SyncRegistry`, деплой не затрагивает.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Применимость | Статус |
|---|---|---|
| I. Self-contained автопайплайн | Фича не вызывает никакой внешний SaaS/API — работает исключительно с уже существующим текстом песни и уже существующим локальным форс-алаймент сервисом (`AlignmentServiceClient`, вызывается ровно как сегодня). | ✅ PASS |
| II. Сырой JDBC + дифф по хэшам | Нет новых таблиц/колонок и нет нового кода работы с БД — маркеры продолжают идти через существующий `Settings.setSourceMarkers`/`saveToDb()`. | ✅ PASS (не применим, изменений в персистентности нет) |
| III. Двух-БД синхронизация через SyncRegistry | Колонка `source_markers` уже участвует (или не участвует) в sync по своим текущим правилам — эта фича не меняет её формат (список `SourceMarker`, просто больше типов `markertype`/`label` в уже свободных строковых полях), новых sync-целей не добавляет. | ✅ PASS |
| IV. Async-очередь задач | Не добавляет новых `ProcessBuilder`/подпроцессов. | ✅ PASS (не применим) |
| V. Двух-фронтенд: admin/public — разные приложения | Изменения есть в ОБОИХ фронтендах (`webvue3` — admin `SubsEdit.vue`; `karaoke-public` — краудсорсинг `useKaraokeEditor.js`/`EditorWorkView.vue`, обнаружено постфактум, см. Technical Context). Это НЕ смешивание ответственностей: между приложениями нет общего импортируемого кода — каждое несёт СВОЮ независимую копию контракта (как и сегодняшнее слогоделение), файлы физически разные, изменения делаются параллельно и независимо в каждом. | ✅ PASS (с уточнением) |
| VI. Code Standards (KDoc/JSDoc, per-feature doc FR-009) | `docs/features/` — фиксированный список 12 ключевых продуктовых подсистем + 1 cross-cutting (см. `docs/features/README.md`); механизм спецтегов — расширение существующей разметки маркеров внутри `karaoke-app`, не отдельная 13-я ключевая подсистема, поэтому нового файла в `docs/features/` не создаётся. `docs/architecture-notes.md` документирует только инициативу Phase 001/002 (проверено: ни одна из обычных пронумерованных фич 003/004/005/008/009 там не упомянута) — по тому же прецеденту отдельная запись там для этой фичи тоже не добавляется; `specs/010-lyrics-spec-tags/` сам служит для неё durable-документом. Новые публичные символы (`SpecTags`, изменённые сигнатуры в `WhisperMarkerAligner`, новая функция в `SubsEdit.vue`) MUST получить KDoc/JSDoc со ссылкой на `specs/010-lyrics-spec-tags/contracts/tag-registry.md`. | ✅ DONE — KDoc/JSDoc добавлены со ссылкой на `contracts/tag-registry.md`; никакого нового doc-файла не создаётся (см. итоговое решение). |
| VII. Cross-Machine Setup | Не затрагивается — фича не меняет setup/AI-конфиги/CI-инфраструктуру. | ✅ PASS (не применим) |

Нарушений, требующих секции «Complexity Tracking», нет.

## Project Structure

### Documentation (this feature)

```text
specs/010-lyrics-spec-tags/
├── plan.md              # этот файл
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md         # Phase 1
├── contracts/
│   └── tag-registry.md  # Phase 1 — контракт синтаксиса тегов и реестра тег→маркер
└── tasks.md              # Phase 2 (создаётся /speckit.tasks)
```

### Source Code (repository root)

Монорепозиторий, расширение существующих backend/frontend модулей (не новый проект):

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/
│   ├── WhisperMarkerAligner.kt   # buildTargetWords/buildMarkersFromSyllableTimes — врезка обработки тегов
│   ├── SpecTags.kt                # НОВЫЙ: парсер ~тег~/~тег:значение~ + реестр тег→(Markertype, label) + таблица алиасов
│   ├── SourceMarker.kt            # без изменений — формат маркера уже достаточен
│   └── Markertype.kt              # без изменений — новые теги мапятся на существующие типы (NEWLINE/SETTING)
└── src/test/kotlin/.../model/
    └── SpecTagsTest.kt            # НОВЫЙ: unit-тесты парсера + buildMarkersFromSyllableTimes с тегами

webvue3/src/components/Songs/edit/
└── SubsEdit.vue                   # зеркальный парсер тегов + syncMarkersFromSpecTags(), вызов рядом
                                    # с updateMarkersBySyllables()

karaoke-public/src/
├── composables/useKaraokeEditor.js  # ТРЕТЬЯ независимая копия того же парсера/реестра/алиасов +
│                                     # syncMarkersFromSpecTags() (без region-специфики WaveSurfer)
└── views/EditorWorkView.vue         # вызов syncMarkersFromSpecTags() при загрузке задания
                                      # (loadTask) и на ввод текста (onTextInput)

docs/
└── architecture-notes.md          # датированная запись об изменении (не новый per-feature документ — см. Constitution Check VI)
```

**Structure Decision**: Никакой новой структуры проекта не вводится — фича целиком укладывается в существующие файлы `WhisperMarkerAligner.kt` (backend, ядро генерации маркеров из текста) и `SubsEdit.vue` (frontend, ручной редактор маркеров), плюс один новый небольшой файл-реестр на backend (`SpecTags.kt`) и один новый per-feature документ. Отдельного контракта REST API не требуется — существующие эндпоинты (`/edit/forcedAlignMarkers`, `/song/savesourcetextmarkers`, `/song/voicesourcemarkers`) не меняют форму запроса/ответа, меняется только то, какие маркеры туда попадают. Формальный "контракт" здесь — это сам синтаксис тега и таблица реестра (см. `contracts/tag-registry.md`), потому что он реализуется независимо в двух местах (Kotlin и Vue, как и `getSyllables()` сегодня) и должен оставаться идентичным в обеих реализациях.

## Complexity Tracking

*Нет нарушений Constitution Check, требующих обоснования — секция не заполняется.*
