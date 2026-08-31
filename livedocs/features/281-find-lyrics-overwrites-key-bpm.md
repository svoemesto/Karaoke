---
status: Active
slug: 281-find-lyrics-overwrites-key-bpm
related:
  - ../features/278-fix-key-loss-on-lyrics-search.md
  - ../features/279-fix-parent-search-folder-add.md
  - ../features/129-copy-family-audio.md
  - ../features/020-fix-search-lyrics-autofill.md
  - ../domain/processing.md
  - ../../specs/281-find-lyrics-overwrites-key-bpm/spec.md
---

# 281 — Race condition: stale `Song` перезатирает параллельно записанные поля (LiveDoc)

> Drill-down — [specs/281-find-lyrics-overwrites-key-bpm/spec.md](../../specs/281-find-lyrics-overwrites-key-bpm/spec.md).

## Что делает

Закрывает 6 точек гонки между долгими операциями (`searchsongtextall`, `applyFamilySongSelection`,
`findAudioParentByWaveform`, `autoAssignOriginalByWaveform`, `findParentAndAudioParentForAll`,
`Song.setSourceMarkers`/`setSourceText`) и фоновыми процессами `KEY_BPM_FROM_FILE` / `DEMUCS2` /
`Sheetsage`. Паттерн — **reload-from-db-before-save** (Pass 278, `specs/278`), применённый
локально в `doCreateFromFolder` для импорта из папки; здесь расширен до всех остальных путей.

## Почему это важно

`Song.saveToDb()` сравнивает `this` (in-memory) с `savedSong` (БД) и пишет UPDATE только по
различающимся полям. Если объект в памяти «устарел» — между `loadFromDbById` и `saveToDb` прошло
несколько секунд, в течение которых фоновый процесс успел обновить `key`/`bpm`/URL'ы стемов, —
то `this.key == ""` против `savedSong.key == "Am"` попадает в diff и `song_tone` сбрасывается в
пусто. Админу приходится повторно запускать `KEY_BPM_FROM_FILE`. Поведение Pass 278 (импорт из
папки) уже было починено, но аналогичный баг оставался в других путях — пользователь нашёл его
на «Найти тексты для всех песен».

## Что починено

| # | Место | Поведение | Паттерн фикса |
|---|-------|-----------|---------------|
| FR-001 | `UtilsAI.applyFoundLyricsIfMissing` (4 движка) | перед `saveToDb` | reload |
| FR-010 | `Utils.findParentAndAudioParentForAll` (else-ветка) | перед `saveToDb` | reload |
| FR-011 | `Utils.applyFamilySongSelection` (модалка + autoAssign) | `saveToDb` + caller-sync | reload+sync |
| FR-012 | `Utils.autoAssignOriginalByWaveform` (финал) | перед `saveToDb` | reload |
| FR-013 | `Utils.findAudioParentByWaveform` (4× saveToDb) | перед КАЖДЫМ `saveToDb` | reload×4 |
| FR-014 | `Song.setSourceMarkers` / `setSourceText` (цикл апрува) | перед `saveToDb` | reload+sync |

**Совместимость с Pass 278/279**: `applyDuplicateOriginal` и `applyAudioParentMarkers` остаются
без изменений (у них уже был reload+sync). `doCreateFromFolder` (Pass 278) — без изменений.
`Song.saveToDb()` — без изменений (Pass 278 FR-004).

## User Stories (краткий список)

- **US1** (P1): «Найти тексты для всех песен» не перезатирает key/bpm.
- **US2** (P1): любой из 4 движков (YANDEX_SYNC/ASYNC, SEARXNG, FOURGET) защищён.
- **US3** (P2): нет регрессий в Pass 278 (импорт из папки).

## Functional Requirements (указатель)

- **FR-001..FR-014** — см. спекy.
- **FR-020..FR-022** — уже реализовано (Pass 278/279), не трогаем.
- **FR-030** — `Song.saveToDb()` не модифицируется.
- **FR-031** — KDoc обновлён во всех затронутых функциях со ссылками на спеку.

## Acceptance Criteria (manual + automated)

- [ ] Все 6 точек содержат `Song.loadFromDbById(...)` перед `saveToDb` (code review).
- [ ] `gradle :karaoke-app:compileKotlin` PASS.
- [ ] `gradle :karaoke-web:ktlintCheck` PASS (без новых нарушений).
- [ ] `gradle :karaoke-app:bootJar` PASS.
- [ ] Регрессий в Pass 278 acceptance нет.
- [ ] LiveDoc-структура + cross-links PASS.

## Связанные LiveDocs

- Feature: [278-fix-key-loss-on-lyrics-search.md](../features/278-fix-key-loss-on-lyrics-search.md) —
  первый фикс race condition (только импорт из папки).
- Feature: [279-fix-parent-search-folder-add.md](../features/279-fix-parent-search-folder-add.md) —
  sync `newSong` в памяти с записанным состоянием (паттерн, переиспользованный в FR-011).
- Feature: [129-copy-family-audio.md](../features/129-copy-family-audio.md) — `applyFamilySongSelection`
  helper (FR-011).
- Feature: [020-fix-search-lyrics-autofill.md](../features/020-fix-search-lyrics-autofill.md) —
  `applyFoundLyricsIfMissing` (FR-001).
- Domain: [processing.md](../domain/processing.md) — фоновые процессы, которые пишут key/bpm/URL'ы.

## Код

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt:128` — `applyFoundLyricsIfMissing` (FR-001).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:162` — `findParentAndAudioParentForAll` (FR-010).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4647` — `applyFamilySongSelection` (FR-011).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4850` — `autoAssignOriginalByWaveform` final (FR-012).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4893` — `findAudioParentByWaveform` (FR-013).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:3626/3662` — `setSourceMarkers`/`setSourceText` (FR-014).

## История

- Создан: 2026-08-31 (Pass 281 — расширение race-condition фикса Pass 278 на все пути).
