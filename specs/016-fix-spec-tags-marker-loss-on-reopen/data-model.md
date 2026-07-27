# Data Model: Спецтеги — сохранение маркеров после «Точные маркеры → Apply → Save → reopen»

**Input**: spec.md, research.md
**Phase**: 1 (design)

## 1. Scope

Эта спецификация — **баг-фикс существующей фичи** (spec 010, спецтеги). Новые сущности не вводятся. Этот документ фиксирует **внутреннее состояние компонента `SubsEdit.vue`**, вовлечённое в баг, и его жизненный цикл на reopen-потоке.

## 2. Внутреннее состояние `SubsEdit.vue` (релевантные поля)

| Поле | Тип | Источник | Назначение | Жизненный цикл на reopen |
|---|---|---|---|---|
| `sourceText` | `String` | `getSourceText(voice)` (Vuex getter) | Текст лирики текущего голоса | Устанавливается в `mounted()`; watcher пересчитывает `sourceSyllables` и зовёт `syncMarkersFromSpecTags` |
| `loadedMarkers` | `SourceMarker[]` | `getSourceMarkers(voice)` (Vuex getter) | **Снимок** маркеров из БД на момент открытия | Загружается в `mounted()`; **используется как «эталон» для решения, что вообще есть у голоса** |
| `sourceMarkers` | `SourceMarker[]` (с `region`-обёрткой от WaveSurfer) | Заполняется из `loadedMarkers`, модифицируется пользователем, передаётся в `save()` | **Живой** список маркеров, с которым работает UI (рендеринг region'ов, добавление/удаление, Save) | Должен быть заполнен из `loadedMarkers` **ДО** любого watcher'а на `sourceText` |
| `sourceSyllables` | `String[]` | Вычисляется через `getSyllables(this.getProcessedSourceText)` | Список слогов, по которому `updateMarkersBySyllables` сверяет label/color syllables-маркеров | Пересчитывается в watcher'е `sourceText` |
| `autoMarkersDebug.markers` | `SourceMarker[]` | Возвращается из backend (`/api/songeditor/edit/forcedAlignMarkers` или `/autoMarkers`) | Результат forced-alignment / Whisper, показывается в модалке, **применяется** в `applyAutoMarkersToEditor` через полную замену `sourceMarkers` | Устанавливается по кнопке, очищается при закрытии модалки |
| `isEditMode` | `Boolean` | — | Включает/выключает редактирование | В `mounted()` = `true` |
| `currentVoice` | `Int` | — | Активный голос | `0` по умолчанию; watcher перезагружает маркеры при смене |
| `wsRegions` | WaveSurfer Regions plugin | `initWavesurfer()` | API регионов | Создаётся в `initWavesurfer()`; используется `syncMarkersFromSpecTags` для создания region'ов новых маркеров |

### 2.1 Связь `loadedMarkers` ↔ `sourceMarkers`

- На **первом открытии** `loadedMarkers` — снимок из БД. `sourceMarkers` инициализируется как `[]` (data default), затем **должен** быть заполнен из `loadedMarkers` до того, как любой watcher (на `sourceText` и т.п.) попытается с ним работать.
- На **закрытии/переоткрытии** редактора — `sourceMarkers` теряется (компонент пересоздаётся), `loadedMarkers` перечитывается заново.
- В **процессе редактирования** `loadedMarkers` остаётся «эталоном», но **не обновляется** при правках. Только `sourceMarkers` мутирует. Save шлёт `sourceMarkers` (см. `getMarkersToSave()`, `SubsEdit.vue:2863-2877`).
- На **смене голоса** (`currentVoice` watcher) оба перезагружаются.

## 3. `SourceMarker` (импортируется из спеки 010)

`SourceMarker` — структура маркера, как в backend `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SourceMarker.kt`:

```kotlin
data class SourceMarker(
    var time: Double,
    var label: String = "",
    var note: String = "",
    var chord: String = "",
    var stringLad: String = "",
    var lockLad: String = "",
    var tag: String = "",
    var color: String,
    var position: String,
    var markertype: String,
)
```

Контракт по `markertype` ↔ label/семантике — см. `specs/010-lyrics-spec-tags/contracts/tag-registry.md` и `Markertype` enum в `karaoke-app/.../model/`.

В UI-стороне `SourceMarker` дополнительно обёрнут полем `region` (WaveSurfer region API, не персистится в БД).

## 4. Поток данных (до фикса — содержит баг)

```text
mounted():
  initWavesurfer()  → ws.load() (async, аудио начнёт декодироваться)
  sourceText = await getSourceText()           → queue watcher
  loadedMarkers = await getSourceMarkers()     → поле установлено
  ws.on('decode', handler)                     → handler зарегистрирован
  // ... остальной setup

[Vue next tick]
  sourceText watcher:
    sourceSyllables = getSyllables(...)
    updateMarkersBySyllables()                  → sourceMarkers=[] → noop
    syncMarkersFromSpecTags()                   → !! BUG: sourceMarkers=[]
                                                  → anchors: N штук
                                                  → для каждого: insertPos=0
                                                  → splice(0,0,...) N раз
                                                  → sourceMarkers = [spec-tag-1, spec-tag-2, ...]
    ...                                         → UI рендерит ТОЛЬКО spec tag-маркеры

[позже, когда аудио декодировано]
  ws.on('decode') handler:
    if (loadedMarkers.length > 0 && sourceMarkers.length === 0) {  → ЛОЖЬ
      // не выполняется — реальные маркеры из БД НЕ загружаются
    }
```

## 5. Поток данных (после фикса)

```text
mounted():
  initWavesurfer()  → ws.load() (async)
  loadedMarkers = await getSourceMarkers()     → поле установлено
  // *** НОВОЕ: заполнить sourceMarkers СИНХРОННО, до любого watcher'а на sourceText ***
  for m in loadedMarkers:
    if (m is COMMENT| " " empty syllables) skip
    else sourceMarkers.push(m with region)
  createBeatMarkers()
  sourceText = await getSourceText()           → queue watcher
  // ... остальной setup
  ws.on('decode', handler):
    // *** УБРАНО: цикл загрузки маркеров (уже выполнен выше) ***
    // Опционально: очистить wsRegions.clearRegions() на re-decode

[Vue next tick]
  sourceText watcher:
    sourceSyllables = getSyllables(...)
    updateMarkersBySyllables()                  → синхронизирует label/color syllables-маркеров
                                                  с новым sourceSyllables (без обнуления лишних — фикс P2)
    syncMarkersFromSpecTags()                   → sourceMarkers=заполнен
                                                  → anchors: N штук
                                                  → для каждого: insertPos = позиция после i-го syllables
                                                  → windowStart = позиция после (i-1)-го syllables
                                                  → если в окне уже есть маркер с тем же (markertype, label) — skip (FR-006)
                                                  → иначе — splice(insertPos, 0, newMarker)
                                                  → sourceMarkers = [реальные маркеры из БД + новые spec tag маркеры]
    ...

[позже, когда аудио декодировано]
  ws.on('decode') handler:
    // Ничего не делает для маркеров (они уже загружены)
```

## 6. Валидация инвариантов (после фикса)

- **INV-1**: На ПЕРВОМ открытии `sourceMarkers.length > 0` **ДО** срабатывания watcher'а на `sourceText` (т.е. к моменту вызова `syncMarkersFromSpecTags()` в нём).
- **INV-2**: `loadedMarkers.length` (из БД) совпадает с `sourceMarkers.length` после `createBeatMarkers()` с точностью до «проигнорированных» маркеров (см. фильтр в ШАГ 4.1/1 — `COMMENT| ` и пустые syllables).
- **INV-3**: `syncMarkersFromSpecTags()` НЕ вставляет маркер, если в окне между двумя соседними syllables-маркерами уже есть маркер с тем же `(markertype, label)` (FR-006 спеки 010).
- **INV-4**: `updateMarkersBySyllables()` НЕ обнуляет `label` syllables-маркера при `index >= sourceSyllables.length` (фикс P2 — лишние syllables-маркеры остаются нетронутыми).
- **INV-5**: `applyAutoMarkersToEditor` остаётся «жёсткой заменой» маркеров (FR-008 спеки 015, by design).

## 7. Что НЕ меняется в data model

- Структура `SourceMarker` (бэкенд) — не трогаем.
- Схема БД (`tbl_settings.source_markers`) — не трогаем.
- API-эндпойнты (`/song/savesourcetextmarkers`, `/song/voicesourcemarkers`, `/songeditor/edit/forcedAlignMarkers`, `/songeditor/edit/autoMarkers`) — не трогаем.
- Vuex-стор `webvue3/src/components/Songs/store.js` (`getSourceMarkers`, `saveSourceTextAndMarkers`) — не трогаем.

Меняется **только** последовательность присваиваний и порядок вызовов внутри `SubsEdit.vue` (а также опционально: `updateMarkersBySyllables` не обнуляет label, `syncMarkersFromSpecTags` имеет гард на пустой `sourceMarkers`).
