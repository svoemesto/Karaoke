# Specification Quality Checklist: Fix Key/Tone Loss During Lyrics Search in Add-Files-From-Folder

**Purpose**: Валидация полноты и качества спецификации перед переходом к планированию.
**Created**: 2026-08-30
**Feature**: [spec.md](spec.md)

## Content Quality

- [x] Нет implementation details (языков, фреймворков, API) — спека описывает ЧТО и ПОЧЕМУ, ссылаясь на конкретные файлы/строки только как «proof of problem location», не предписывая API контракты.
- [x] Сфокусирована на пользовательской ценности — устранение ручного повторного определения тональности, корректное состояние песни после импорта.
- [x] Написана для нетехнических стейкхолдеров — user story описывают поведение с точки зрения администратора, не разработчика.
- [x] Все обязательные секции заполнены (User Scenarios, Requirements, Success Criteria, Assumptions).

## Requirement Completeness

- [x] Нет маркеров `[NEEDS CLARIFICATION]` — все спорные моменты решены обоснованными предположениями.
- [x] Requirements testable and unambiguous — каждый FR проверяем через конкретный сценарий в БД.
- [x] Success criteria measurable — SC-001 (100% песен с заполненной тональностью), SC-002 (отсутствие новых процессов), SC-003 (3 точки saveToDb покрыты), SC-005 (конкретный сценарий с 10 файлами).
- [x] Success criteria technology-agnostic — описывают состояние БД и поведение, а не API/фреймворки.
- [x] Все acceptance scenarios определены — для каждого User Story есть 2-3 сценария Given/When/Then.
- [x] Edge cases identified — 6 edge cases покрывают исключения Playwright, фоновый поиск, гонки с Demucs, etc.
- [x] Scope clearly bounded — только `doCreateFromFolder` + `setSourceMarkers`, никаких других мест `saveToDb()`.
- [x] Dependencies and assumptions identified — FR-004 явно фиксирует, что `saveToDb()` НЕ меняется, FR-003 указывает на существующий паттерн loadFromDbById.

## Feature Readiness

- [x] Все FR имеют чёткие acceptance criteria через связанные User Story.
- [x] User scenarios покрывают основные пути (P1 — KEY_BPM_FROM_FILE, P2 — DEMUCS2).
- [x] Feature удовлетворяет measurable outcomes из Success Criteria.
- [x] Нет implementation details в спецификации — конкретные файлы/строки только как доказательство расположения бага, не как предписание реализации.

## Notes

- Спецификация готова к `/speckit.plan`. Корневая причина бага подтверждена через CodeGraph: race condition между `KaraokeProcess.createProcess(KEY_BPM_FROM_FILE)` в `Song.createFromPath` (Song.kt:8152) и синхронным `findYandexSongLyrics` в `doCreateFromFolder` (ApiController.kt:5449) — `saveToDb()` после длительной операции использует устаревший in-memory объект `newSong` без перезагрузки из БД.
- Подход к исправлению (reload из БД перед saveToDb) — минимально-инвазивный, не трогает 46 других точек вызова `saveToDb()`.
- Risk: `applyDuplicateOriginal` (ApiController.kt:5415) — нужно проверить при реализации, делает ли он saveToDb; если да — применить тот же паттерн. Спека FR-002 это явно требует.
