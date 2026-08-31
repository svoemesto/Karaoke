# Implementation Plan: 279 — Восстановить поиск родителя при добавлении файлов из папки

**Branch**: `279-fix-parent-search-folder-add` | **Date**: 2026-08-31 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/279-fix-parent-search-folder-add/spec.md`

**Note**: Этот план создан по результатам `/speckit.plan`. Технический подход зафиксирован в [research.md](research.md) (обновлён после анализа кода от 2026-08-31).

## Summary

Восстановить автоматический поиск «родителя» при добавлении файлов из папки через `POST /api/utils/createfromfolder`. **Корневая причина подтверждена анализом кода** (без логов): регресс спеки 278 (`acfb936d`) — `applyDuplicateOriginal` после `songToSave.saveToDb()` не синхронизирует `newSong` в памяти, и последующий вызов `song.saveToDb()` внутри `findAudioParentByWaveform` перезаписывает только что записанный `root_id` обратно в 0.

**Технический подход** (из [research.md](research.md), H1 CONFIRMED):
1. **PRIMARY**: после `songToSave.saveToDb()` в `applyDuplicateOriginal` синхронизировать `newSong.rootId` и другие поля с записанным состоянием. Аналогично для `applyAudioParentMarkers` (consistency).
2. **OPTIONAL**: заменить `LOWER(song_author) = LOWER(?)` на `song_author ILIKE ?` в `findDuplicateOriginal` (для кириллицы в локали C/POSIX) — отдельная задача, не связана с текущим багом.

## Technical Context

**Language/Version**: Kotlin 1.x, JDK 17 (как у остального `karaoke-app`).

**Primary Dependencies**: Spring Boot 2.x/3.x, PostgreSQL JDBC (сырой JDBC через `KaraokeConnection`), Gradle multi-module.

**Storage**: PostgreSQL (через сырой JDBC). Никаких миграций схемы БД не требуется (фиксы локализованы в SQL внутри `Utils.findDuplicateOriginal` и в логике `Utils.applyDuplicateOriginal`).

**Testing**: в CI нет. Существующие тесты в `karaoke-app/src/test` помечены `@Disabled`. Проверка — ручная на стороне пользователя через [quickstart.md](quickstart.md).

**Target Platform**: Linux server (admin-машина), Docker + docker-compose. `karaoke-app` запускается в Docker-контейнере на admin-машине.

**Project Type**: Backend (Spring Boot / Kotlin) + Vue 3 admin SPA (UI-контракт не меняется, см. FR-011 спеки).

**Performance Goals**: не меняются. Поиск родителя — один SQL-запрос с индексом по `root_id` (если есть) или seq scan; для одной песни < 100 мс.

**Constraints**:
- Никаких миграций схемы БД (поля `root_id`, `source_text`, и т.д. уже существуют).
- Никаких изменений UI/HTTP-контракта (FR-011 спеки).
- Существующая защита от race condition (спека 278) должна сохраняться (FR-007 спеки).
- Constitution § II — сырой JDBC, никакого JPA/Hibernate/Exposed.
- Constitution § VI — KDoc-комментарии у публичных API, линтеры (`./gradlew :karaoke-web:ktlintCheck`).

**Scale/Scope**: 1 файл `Utils.kt` + возможно 1 файл `ApiController.kt` (если нужно изменить `doCreateFromFolder`). 2-5 строк изменений (после диагностики).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---------|--------|-------------|
| I. Self-contained автопайплайн | ✓ PASS | Фикс локализован в `karaoke-app`, без новых внешних зависимостей. |
| II. Сырой JDBC + дифф по хэшам | ✓ PASS | Используется существующий `KaraokeConnection` через `Song.loadFromDbById`, `Song.saveToDb`. Никакого JPA/Hibernate. |
| III. Двух-БД синхронизация | ✓ PASS | `tbl_songs` уже в `SyncRegistry`; фикс не меняет структуру. `recordhash`-триггер не затрагивается (нет изменений схемы). |
| IV. Async-очередь задач | ✓ PASS | `applyDuplicateOriginal` синхронный, не через очередь. `findYandexSongLyrics` остаётся синхронным (как в спеке 278). |
| V. Двух-фронтенд | ✓ PASS | UI-контракт не меняется. `webvue3` (админка) и `karaoke-public` (публичный) не затрагиваются. |
| VI. Code Standards | ✓ PASS | KDoc-комментарии сохраняются/добавляются. `./gradlew :karaoke-web:ktlintCheck` запускается на PR. ESLint baseline не меняется (UI не трогается). |
| VII. Cross-Machine Setup | ✓ PASS | Никаких изменений в `.gitattributes`, `.git-blame-ignore-revs`. |
| VIII. Секреты и git-гигиена | ✓ PASS | Никаких секрет-файлов, никаких изменений в `deploy/.env`. |

**GATE result**: 8/8 PASS. Никаких нарушений, фикс проходит Constitution Check.

## Project Structure

### Documentation (this feature)

```text
specs/279-fix-parent-search-folder-add/
├── plan.md              # Этот файл (/speckit.plan output)
├── research.md          # Phase 0 output — гипотезы H1 (PRIMARY), H4 (SECONDARY) + план диагностики
├── data-model.md        # Phase 1 output — модель данных (поля Song)
├── quickstart.md        # Phase 1 output — ручная проверка сценариев SC-001..SC-007
├── contracts/           # Phase 1 output
│   ├── find-duplicate-original.md
│   ├── apply-duplicate-original.md
│   └── http-createfromfolder.md
├── checklists/          # Уже создано в /speckit.specify
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit.tasks — НЕ создаётся /speckit.plan)
```

### Source Code (repository root)

**Затрагиваемые файлы** (после диагностики):

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt`:
  - `applyDuplicateOriginal:4528` — добавить try/catch с подробным логированием (FR-007b спеки). При исключении — локальное логирование + НЕ пробрасывать.
  - `findDuplicateOriginal:4402` — заменить `LOWER(song_author) = LOWER(?)` на `song_author ILIKE ?` (FR-006 спеки, гипотеза H4).

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`:
  - `doCreateFromFolder:5390` — возможно, потребуется изменить порядок вызовов для FR-007a (при сбое `applyDuplicateOriginal` запускать `findYandexSongLyrics`). Решение зависит от результата диагностики.

**Структурное решение**: минимально-инвазивный фикс в 1-2 файлах `karaoke-app`. UI/HTTP/БД-схема/Sync/queue не затрагиваются.

## Implementation Plan

### Шаг 1 — Фикс H1 (PRIMARY): синхронизация `newSong` после `applyDuplicateOriginal`

В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4528` (`applyDuplicateOriginal`):

```kotlin
fun applyDuplicateOriginal(
    newSong: Song,
    original: Song,
) {
    // specs/278-fix-key-loss-on-lyrics-search: ...
    val songToSave =
        Song.loadFromDbById(
            id = newSong.id,
            database = newSong.database,
            storageService = newSong.storageService,
            storageApiClient = newSong.storageApiClient,
        ) ?: newSong
    songToSave.rootId = original.id
    songToSave.sourceText = original.sourceText
    songToSave.resultText = original.resultText
    songToSave.sourceMarkers = original.sourceMarkers
    songToSave.formattedTextSong = original.formattedTextSong
    songToSave.formattedTextTabs = original.formattedTextTabs
    songToSave.formattedTextChords = original.formattedTextChords
    songToSave.fields[SongField.ID_STATUS] = "1"
    songToSave.saveToDb()

    // specs/279-fix-parent-search-folder-add: синхронизировать newSong в памяти с только что
    // записанным состоянием. Без этого следующий шаг doCreateFromFolder (findAudioParentByWaveform
    // → song.saveToDb() в строках 4879/4898/4919/4933) увидит расхождение: this.rootId=0 (в памяти)
    // != savedSong.rootId=original.id (из БД) → diff включит root_id=0 → UPDATE перезатрёт только что
    // записанный root_id обратно в 0. Регресс после спеки 278 — раньше присваивание шло напрямую
    // в newSong (newSong.rootId = original.id), теперь через songToSave.
    newSong.rootId = original.id
    newSong.sourceText = original.sourceText
    newSong.resultText = original.resultText
    newSong.sourceMarkers = original.sourceMarkers
    newSong.formattedTextSong = original.formattedTextSong
    newSong.formattedTextTabs = original.formattedTextTabs
    newSong.formattedTextChords = original.formattedTextChords
    newSong.fields[SongField.ID_STATUS] = "1"
}
```

Аналогично для `applyAudioParentMarkers` (`Utils.kt:4568`) — для consistency (H2 из research.md). Там после `songToSave.saveToDb()` добавить:

```kotlin
song.audioParentId = songToSave.audioParentId
song.audioSimilarityPercent = songToSave.audioSimilarityPercent
song.audioDeltaMs = songToSave.audioDeltaMs
song.sourceText = songToSave.sourceText
song.resultText = songToSave.resultText
song.sourceMarkers = songToSave.sourceMarkers
song.formattedTextSong = songToSave.formattedTextSong
song.formattedTextTabs = songToSave.formattedTextTabs
song.formattedTextChords = songToSave.formattedTextChords
song.fields[SongField.ID_STATUS] = songToSave.fields[SongField.ID_STATUS] ?: "5"
```

### Шаг 2 — Ручная проверка по SC-001..SC-007

См. [quickstart.md](quickstart.md). Все 7 SC должны пройти. Особенно SC-001 (импорт с суффиксами — `root_id` записывается И НЕ перезатирается).

### Шаг 3 — Линтеры

```bash
./gradlew :karaoke-web:ktlintCheck
# Должно пройти без НОВЫХ нарушений (baseline в config/ktlint/baseline-*.xml).
```

### Шаг 4 — Сборка

```bash
./gradlew :karaoke-app:bootJar --parallel
```

### Шаг 5 — Документация

- Обновить [livedocs/features/238-import-folder-author-album-cover.md](../../livedocs/features/238-import-folder-author-album-cover.md) — запись в `## История`: «2026-08-31: bugfix — после спеки 278 `applyDuplicateOriginal`/`applyAudioParentMarkers` пишут в БД через `songToSave`, но `newSong`/`song` в памяти оставался «грязным» (rootId=0 / audio_*=дефолт). Последующий `song.saveToDb()` внутри `findAudioParentByWaveform` (или другой код-путь) видел расхождение и перезаписывал только что записанные поля. Добавлена явная синхронизация `newSong`/`song` с записанным состоянием сразу после `songToSave.saveToDb()`.»

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет нарушений) | — | — |

Все 8 принципов Constitution соблюдены. Никаких нарушений для justifying.

## Open Questions

Нет открытых вопросов. Корневая причина подтверждена анализом кода.

## Risks

- **R1**: Изменения в `applyDuplicateOriginal` и `applyAudioParentMarkers` — точечные (несколько строк), влияют только на эти две функции. Другие 44+ вызывающих мест `Song.saveToDb()` не затронуты.
- **R2**: Синхронизация `newSong` в памяти после `songToSave.saveToDb()` — это «best effort» защита от будущих регрессий. Альтернатива: вообще убрать reload-from-db-before-save в `applyDuplicateOriginal`/`applyAudioParentMarkers` (вернуть ДО-278 код), но тогда теряется защита от race condition (спека 278). Текущий фикс сохраняет обе защиты.
- **R3**: Фикс не покрывает случай, если между `applyDuplicateOriginal` и `findAudioParentByWaveform` параллельный процесс (например, `KEY_BPM_FROM_FILE`) обновит `song_tone`/`song_bpm` через свой `Song.saveToDb()`. Тогда `findAudioParentByWaveform` увидит stale `newSong.tone` (который мы НЕ синхронизировали — синхронизировали только поля, относящиеся к root/audio-parent). Но это нормально: `KEY_BPM_FROM_FILE` пишет через свой `Song` объект, и его `saveToDb` использует getDiff с актуальным savedSong. Если после `KEY_BPM_FROM_FILE` `findAudioParentByWaveform` вызывает `song.saveToDb()`, то diff увидит только реальные изменения (например, `song.tone`). Если ничего не изменилось — diff пуст. Никакой перезаписи root_id/audio_parent_id.

## Done When

- [ ] Шаг 1 применён (синхронизация в `applyDuplicateOriginal` и `applyAudioParentMarkers`).
- [ ] `./gradlew :karaoke-web:ktlintCheck` проходит без новых нарушений.
- [ ] `./gradlew :karaoke-app:bootJar` собирается успешно.
- [ ] SC-001..SC-007 (см. [quickstart.md](quickstart.md)) пройдены на стороне пользователя.
- [ ] LiveDoc `238-import-folder-author-album-cover.md` обновлён в `## История`.
- [ ] PR создан и прошёл CI 7/7.
