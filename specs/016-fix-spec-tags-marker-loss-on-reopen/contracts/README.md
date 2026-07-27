# Contracts: Спецтеги — сохранение маркеров после reopen

**Input**: spec.md, research.md, data-model.md
**Phase**: 1 (design)

## 1. Скоуп контрактов

Эта спецификация — **баг-фикс существующей фичи** (spec 010, спецтеги). Внешних API-контрактов не вводится: ни бэкенд-эндпойнты, ни структуры БД, ни Vuex-стор, ни реестр тегов, ни алиасы — ничего не меняется.

Поэтому папка `contracts/` содержит **только ссылку** на единственный существующий контракт, релевантный этой спецификации, и фиксирует **обязательство НЕ менять** его в рамках данного фикса.

## 2. Существующие контракты (наследуются, не меняются)

### 2.1 Контракт грамматики и реестра спецтегов

Единственный источник правды по синтаксису и реестру спецтегов:

> **`specs/010-lyrics-spec-tags/contracts/tag-registry.md`**

Этот контракт реализуется независимо в **пяти местах** (см. сам tag-registry.md, секция «Контракт»), включая:
- `karaoke-app/.../model/SpecTags.kt` + `WhisperMarkerAligner.kt` (backend)
- `webvue3/.../SubsEdit.vue` (admin, полновесный) — **то место, которое правит этот фикс**
- `webvue3/.../useKaraokeEditor.js` (admin, лёгкий)
- `karaoke-public/.../useKaraokeEditor.js` (краудсорсинг)
- `alignment-ml/syllables.py` (форс-алаймент сервис)

**Обязательство этой спецификации**: грамматика, реестр v1 (`newline` / `group:N` / `comment:текст`), алиасы v1 (`Куплет`/`Припев`/`Бридж`/`Приговор`), инварианты FR-005/FR-006/FR-007 — **без изменений**. Эта спецификация — баг-фикс локальной логики синхронизации маркеров в `SubsEdit.vue`, а НЕ изменение контракта.

### 2.2 Контракт структуры маркера

Единственный источник правды по структуре `SourceMarker`:

> **`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SourceMarker.kt`**

`SourceMarker` — `data class` с полями `time`, `label`, `note`, `chord`, `stringLad`, `lockLad`, `tag`, `color`, `position`, `markertype` (все `var`, сериализуется через `kotlinx.serialization`).

В UI-стороне `SourceMarker` оборачивается дополнительным полем `region` (WaveSurfer region API, не персистится в БД). На границе UI ↔ backend (через Vuex-стор) это поле отбрасывается (см. `getMarkersToSave()`, `SubsEdit.vue:2863-2877`).

**Обязательство этой спецификации**: структура `SourceMarker` и формат сериализации — **без изменений**.

### 2.3 Контракт `Markertype`

См. enum `Markertype` в `karaoke-app/.../model/`. Контракт по `markertype` ↔ семантике/цвету/позиции — без изменений.

## 3. Новые контракты, вводимые этой спецификацией

**Нет.** Эта спецификация не вводит новых API, форматов или реестров. Она правит **внутренний порядок вызовов** внутри `SubsEdit.vue` — это не контракт, а деталь реализации.

## 4. Что ДОЛЖНО остаться неизменным в `SubsEdit.vue` (для сохранения совместимости с контрактом 2.1)

После фикса следующие **наблюдаемые снаружи** свойства `SubsEdit.vue` НЕ должны измениться:

1. **Парсинг и резолвинг спецтегов** — `parseSpecTagLine`, `resolveSpecTag`, `SPEC_TAG_REGEX`, `SPEC_TAG_REGISTRY`, `SPEC_TAG_ALIASES` — байт-в-байт как в спецификации 010. **Любая правка = изменение контракта 2.1, запрещено в этой спеке.**

2. **Кнопки быстрой вставки** — `onInsertSpecTag`, `insertSpecTagAtCursor` — без изменений (FR-011 спеки 010).

3. **Точка вызова `syncMarkersFromSpecTags()` из watcher'а `sourceText`** — остаётся (для US3 спеки 010: «ручное редактирование → автодобавление»). Фикс — в ПОРЯДКЕ присваиваний в `mounted()`/`currentVoice` watcher'е, а НЕ в удалении вызова.

4. **Семантика «Точные маркеры + Apply = полная замена»** — `applyAutoMarkersToEditor` остаётся `sourceMarkers = []` + push новых. Текст подтверждения в `doForcedAlignMarkers` остаётся прежним (FR-008 спеки 015).

5. **Поведение для песен БЕЗ спецтегов** — должно быть **идентично** текущему (FR-007 спеки 015: регрессия недопустима). Тег-парсер возвращает пустые anchors, `syncMarkersFromSpecTags` пропускает всё, остальной код отрабатывает как раньше.

## 5. Что ДОЛЖНО измениться в `SubsEdit.vue` (в скоупе этой спеки)

1. **Порядок загрузки в `mounted()`**: `loadedMarkers` → заполнить `sourceMarkers` → `createBeatMarkers()` → `sourceText`. (Сейчас `sourceText` ставится ПЕРЕД `loadedMarkers` и ДО регистрации `ws.on('decode')`; цикл загрузки маркеров находится в `ws.on('decode')`, который срабатывает ПОЗЖЕ watcher'а `sourceText`.)

2. **Цикл загрузки в `ws.on('decode')`**: убрать (или свести к «re-decode, только если в sourceMarkers пусто» — для отказоустойчивости при смене трека/перезагрузке).

3. **Порядок в `currentVoice` watcher'е**: тот же приём — `loadedMarkers` + заполнение `sourceMarkers` ДО `this.sourceText = ...`.

4. **Опционально (P2)**: `syncMarkersFromSpecTags()` — гард `if (this.sourceMarkers.length === 0) return` в начале.

5. **Опционально (P2)**: `updateMarkersBySyllables()` — не обнулять `label` syllables-маркера при `index >= sourceSyllables.length`, а оставлять как есть.

## 6. Проверка совместимости с контрактом 2.1 после фикса

- Сравнить байт-в-байт поведение `parseSpecTagLine`/`resolveSpecTag` до/после фикса на фикстурах из `SpecTagsTest.kt` (Kotlin) и аналогичных frontend-фикстурах (если есть).
- Прогнать `quickstart.md` спеки 010 (Сценарии A, B, C, C2, D, E) — все должны давать идентичный результат.
- Существующие unit-тесты `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/SpecTagsTest.kt` — должны проходить без изменений.
- Существующие unit-тесты `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/WhisperMarkerAlignerSpecTagsTest.kt` (если есть) — должны проходить без изменений.

## 7. Резюме

Эта спецификация **наследует** контракт `specs/010-lyrics-spec-tags/contracts/tag-registry.md` и **не вводит новых контрактов**. Все изменения локализованы в порядке вызовов внутри `SubsEdit.vue` — это bugfix-уровень правки, не API-уровень.
