# Implementation Plan: Исправление регрессий редакторов текста песни после внедрения спецтегов

**Branch**: `163-fix-song-editor-regressions` | **Date**: 2026-08-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/163-fix-song-editor-regressions/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

После внедрения фичи спецтегов (`~Куплет~`, `~Припев~` и т.п., specs/010-lyrics-spec-tags) две
НЕЗАВИСИМЫЕ копии синхронизации маркеров из спецтегов (`syncMarkersFromSpecTags`) — по одной в
каждом из двух редакторов админки (`webvue3`) — получили один и тот же структурный дефект
неидемпотентности, но с разными наблюдаемыми последствиями из-за разных дальнейших фиксов и разных
реальных временных меток маркеров. Диагностика (Phase 0) подтвердила: причина обеих регрессий — на
100% во frontend-слое `webvue3`, backend (`SongEditorController.kt`/`SpecTags.kt`) не затрагивается.

Технический подход: (1) для облегчённого редактора — сделать `syncMarkersFromSpecTags` в
`useKaraokeEditor.js` идемпотентной (устранить дублирование при совпадении времени нового
тег-маркера с соседним слоговым), портировав туда же уже проверенную защиту `#018` (минимальный
зазор от соседних маркеров), и добавить проверку поля `ok` в ответе `saveNow()`, чтобы ошибка
сохранения больше не маскировалась под успех; (2) для SubsEdit — снизить количество избыточных
полных перерисовок вейвформы за одно нажатие клавиши (сейчас `updateMarkersBySyllables()` +
`redrawMarkers()` выполняются дважды за keystroke из-за каскада `sourceText` → `sourceSyllables`
watcher'ов), чтобы уменьшить как видимую вспышку «слипания в нуле» (артефакт WaveSurfer
`setContent`/`setOptions`, известный класс бага, см. specs/016-019), так и связанное с этим
торможение. Оба фикса без изменения формата данных/API.

## Technical Context

**Language/Version**: JavaScript (Vue 3 Options API, ES2020+, Node 22) для frontend. Backend
(Kotlin 2.x/JDK 17) НЕ затрагивается — Phase 0 диагностика подтвердила, что `editSave`/`editById`
(`SongEditorController.kt`) и `SpecTags.kt` не участвуют ни в одной из двух регрессий (см.
research.md §1).

**Primary Dependencies**: Vue 3 + Vite (`webvue3`), WaveSurfer.js (regions plugin, используется и в
`SubsEdit.vue`, и в `SongKaraokeEditorView.vue`), общая чистая логика разметки
`webvue3/src/composables/useKaraokeEditor.js` (используется только облегчённым редактором;
`SubsEdit.vue` держит независимую копию той же логики спецтегов).

**Storage**: PostgreSQL через сырой JDBC (без изменений схемы — фича не меняет формат хранения
текста/маркеров песни).

**Testing**: Ручная браузерная проверка в `webvue3` (по конституции — CI без автотестов для
frontend, не полагаться на автотесты как единственную проверку). Диагностика Phase 0 воспроизвела
дефект неидемпотентности изолированным Node-скриптом, вызывающим `syncMarkersFromSpecTags`
напрямую (без браузера/БД) — тот же приём стоит использовать как быстрый регрессионный чек перед
ручной проверкой в браузере.

**Target Platform**: Веб (админка `webvue3`, десктоп/browser), без изменений на публичном сайте
`karaoke-public` (Principle V — два независимых фронтенда, регрессия ограничена админкой).

**Project Type**: Web application (существующий монорепозиторий: `karaoke-app` backend +
`webvue3` admin SPA) — правка bug-fix уровня, без новых сервисов/модулей.

**Performance Goals**: Время готовности SubsEdit к редактированию для песни со спецтегами — не
более чем на 10% дольше песни сопоставимого размера без спецтегов (см. FR-006/SC-003 spec.md).

**Constraints**: Аддитивный контракт `syncMarkersFromSpecTags` (никогда не удаляет/не изменяет
существующие маркеры, см. `specs/010-lyrics-spec-tags/contracts/tag-registry.md`) должен остаться
неизменным — фикс не должен ослаблять этот инвариант. Формат API `/api/songeditor/edit/save` и
аналогичного эндпоинта SubsEdit менять нельзя без явного обоснования (см. Assumptions spec.md).

**Scale/Scope**: Три frontend-файла (`SubsEdit.vue`, `webvue3/src/composables/useKaraokeEditor.js`,
`SongKaraokeEditorModal.vue`) и их независимые копии логики спецтегов; backend не затрагивается —
без новых сущностей БД, без миграций.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Применимость | Статус |
|---|---|---|
| I. Self-contained автопайплайн | Не затрагивается — фикс редакторов разметки, не медиапайплайн (ffmpeg/MLT/Demucs/Sheetsage) | ✅ N/A |
| II. Сырой JDBC + дифф по хэшам | Не затрагивается — backend не меняется (см. Technical Context) | ✅ N/A |
| III. Двух-БД синхронизация через SyncRegistry | Не затрагивается — новых sync-полей/таблиц не вводится | ✅ N/A |
| IV. Async-очередь задач | Не затрагивается — сохранение текста/маркеров синхронный HTTP-запрос, не `KaraokeProcess*`-джоба | ✅ N/A |
| V. Два фронтенда — разные приложения | Регрессия и фикс ограничены `webvue3` (админка); `karaoke-public` не трогаем, хотя там есть своя независимая копия той же спецтег-логики (не в скоупе — пользователь описал баг только «в админке», см. Assumptions spec.md) | ✅ PASS |
| VI. Code Standards (KDoc/JSDoc, ktlint/ESLint, per-feature docs) | Применяется: любые правки в `.vue`/`.js`/`.kt` MUST сохранить JSDoc/KDoc-покрытие; per-feature документ `docs/features/editor-tasks.md` (уже упоминает `SongKaraokeEditorView.vue` и `SubsEdit.vue`) MUST быть обновлён в этом же PR (FR-009) | ⚠️ ACTION REQUIRED в реализации |
| VII. Cross-Machine Setup | Не затрагивается — фикс не меняет AI-конфиги/git-инфраструктуру | ✅ N/A |
| VIII. Секреты и git-гигиена | Не затрагивается — фикс не работает с `.env`/секретами | ✅ N/A |

Нарушений, требующих обоснования в Complexity Tracking, не выявлено.

**Post-Phase-1 re-check**: дизайн (research.md, data-model.md, contracts/,
quickstart.md) подтвердил исходную оценку — все правки остаются внутри `webvue3`, backend не
затронут, новых сущностей/эндпоинтов нет. Единственный открытый пункт — VI (per-feature документ)
— переносится в tasks.md как обязательный шаг реализации, не как нарушение.

## Project Structure

### Documentation (this feature)

```text
specs/163-fix-song-editor-regressions/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/
│   └── sync-idempotency-invariant.md  # Phase 1 output — усиление контракта specs/010
├── checklists/
│   └── requirements.md  # From /speckit.specify + /speckit.clarify
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
webvue3/src/
├── components/
│   ├── Songs/edit/
│   │   └── SubsEdit.vue                 # Полноценный редактор — watcher-каскад sourceText/
│   │                                     # sourceSyllables (~2158-2193) дважды за keystroke вызывает
│   │                                     # updateMarkersBySyllables()+redrawMarkers(); своя копия
│   │                                     # spec-tag логики (specTagAnchors ~2059,
│   │                                     # syncMarkersFromSpecTags ~3471, уже со фиксом #018)
│   └── SongEditor/
│       └── SongKaraokeEditorModal.vue    # Модалка — saveNow() (~210-254) не проверяет поле `ok`
│                                          # в ответе backend
└── composables/
    └── useKaraokeEditor.js               # Общая чистая логика спецтегов для облегчённого
                                           # редактора — syncMarkersFromSpecTags (~183-229)
                                           # неидемпотентна (подтверждено воспроизведением),
                                           # НЕ получила фиксы #018/#019, которые были применены
                                           # только в SubsEdit.vue

docs/features/
└── editor-tasks.md                       # Per-feature документ, MUST обновить (FR-009)
```

**Structure Decision**: Изменения ограничены тремя существующими файлами `webvue3` (admin SPA).
Backend (`karaoke-app`) не затрагивается вовсе — подтверждено диагностикой Phase 0. Новых
директорий/модулей/сервисов фича не создаёт — это точечный bug-fix в рамках уже существующей
структуры «два независимых фронтенда» (Principle V).

## Complexity Tracking

Нарушений Constitution Check нет — таблица не заполняется.
