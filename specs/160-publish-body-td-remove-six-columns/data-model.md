# Data Model: Чистка DTO от processColor* (28 → 1)

**Phase**: 1 (Design & Contracts)
**Branch**: `160-publish-body-td-remove-six-columns`
**Spec**: [`./spec.md`](./spec.md)
**Research**: [`./research.md`](./research.md)
**Date**: 2026-08-06

Этот документ фиксирует итоговую форму сущностей после PR. Все изменения — **аддитивное удаление** полей без изменения имён/типов/порядка оставшихся.

---

## 1. SongDTOdigest (лёгкий DTO для пагинированных списков)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTOdigest.kt`
**Назначение**: ответ `/api/songsdigests` — лёгкий список песен для админки.
**Изменение**: data class fields 61–88 (28 полей) → 1 поле (`processColorPlayerDemo`).

### 1.1. Удаляемые поля (27 шт., точный список)

```
processColorBoosty
processColorSponsr
processColorVk
processColorMeltLyrics
processColorMeltKaraoke
processColorMeltChords
processColorMeltMelody
processColorDzenLyrics
processColorDzenKaraoke
processColorDzenChords
processColorDzenMelody
processColorVkLyrics
processColorVkKaraoke
processColorVkChords
processColorVkMelody
processColorTelegramLyrics
processColorTelegramKaraoke
processColorTelegramChords
processColorTelegramMelody
processColorPlLyrics
processColorPlKaraoke
processColorPlChords
processColorPlMelody
processColorMaxLyrics
processColorMaxKaraoke
processColorMaxChords
processColorMaxMelody
```

### 1.2. Сохраняемое поле (1 шт.)

```kotlin
val processColorPlayerDemo: String,
```

**Тип**: `String` (CSS-цвет, например `"#00FF00"` или `"#A9A9A9"`).
**Потребитель**: бейдж `DE` в `webvue3/src/components/Songs/SongsTable.vue:329`.

### 1.3. Остальные поля

Не затрагиваются этим PR (60 полей: `id`, `idPrevious`/`Next`/`Left`/`Right`, `idStatus`, `status`, `tags`, `color`, `songName`/`Censored`, `author`, `album`, `date`, `time`, `timecode`, `ms`, `dateTimePublish`, `year`, `track`, `countVoices`, `firstSongInAlbum`, `flagBoosty`..`flagMaxMelody` (24 поля), `flagFree`, `resultVersion`, `versionBoosty`..`versionMaxMelody` (24 поля), `rate`, `healthReportText`/`Color`/`List`, `formattedTextSong`/`Tabs`/`Chords`, `description`/`shortDescription`/`warning`, `rootId`, `audioParentId`, `free`, `songType`, `haveSourceText`, `albumId`/`Name`, `idTelegramDemo`).

---

## 2. SongDTO (полный DTO)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTO.kt`
**Назначение**: ответ `/api/songs`, `/api/song/{id}` (для админки).
**Изменение**: data class fields 68–95 (28 полей) → 1 поле; `toDtoDigest()` assignments 353–380 (28 присваиваний) → 1 присваивание.

### 2.1. Удаляемые поля

Идентично `SongDTOdigest` (см. §1.1) — 27 полей.

### 2.2. Сохраняемое поле

```kotlin
val processColorPlayerDemo: String,
```

### 2.3. `toDtoDigest()` (Song.kt-style companion, в SongDTO.kt:304+)

После удаления 27 полей из `SongDTOdigest` соответствующие 27 присваиваний в `SongDTO.toDtoDigest()` (строки 353–380) удаляются. Остаётся:

```kotlin
fun toDtoDigest(): SongDTOdigest =
    SongDTOdigest(
        // ... остальные поля ...
        processColorPlayerDemo = processColorPlayerDemo,
        // ... остальные поля ...
    )
```

---

## 3. Song (доменная модель — НЕ ИЗМЕНЯЕТСЯ в части геттеров/diff)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`
**Изменение**: только `toDTO()` (8232–8259) — удалить 27 присваиваний `processColorX = processColorX`. Геттеры 2454–2538 и diff 6816–6966 **сохраняются**.

### 3.1. Геттеры (2454–2538, сохраняются)

```kotlin
val processColorMeltLyrics: String get() = getColorToProcessTypeName(statusProcessLyrics)        // строка 2454
val processColorMeltKaraoke: String get() = getColorToProcessTypeName(statusProcessKaraoke)      // 2455
val processColorMeltChords: String get() = getColorToProcessTypeName(statusProcessChords)       // 2456
val processColorMeltMelody: String get() = getColorToProcessTypeName(statusProcessMelody)      // 2457
val processColorPlayerDemo: String get() = getColorToProcessTypeName(statusProcessDemo)         // 2458
val processColorVk: String get() = if (idVk.isNotBlank()) "#00FF00" else "#A9A9A9"                // 2471
val processColorBoosty: String get() = /* ... */                                                  // 2472
val processColorSponsr: String get() = if (idSponsr.isNotBlank()) "#00FF00" else "#A9A9A9"      // 2480
val processColorBoostyFiles: String get() = /* dead code, out of scope */                        // 2483
val processColorVkLyrics: String get() = if (idVkLyrics.isNotBlank()) "#00FF00" else "#A9A9A9"  // 2485
// ... + ещё 20 геттеров до строки 2538
```

### 3.2. `toDTO()` (8177+, изменяется)

Удалить 27 строк присваивания `processColorX = processColorX,` для всех полей кроме `processColorPlayerDemo`. Номера строк после удаления: сдвинутся вверх, но **порядок остальных присваиваний сохраняется**.

### 3.3. Diff-логика (6816–6966, сохраняется)

Использует `settA.processColorX vs settB.processColorX` — продолжает работать, поскольку `Song` остаётся неизменным.

---

## 4. Publication (доменная модель — НЕ ИЗМЕНЯЕТСЯ)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt`
**Строки 249+**: использует `publishX!!.processColorMeltLyrics` (и другие) — это геттеры `Song`, которые сохраняются. Без изменений.

---

## 5. PublishTableBodyTd (Vue-компонент — изменяется)

**Файл**: `webvue3/src/components/Publish/components/PublishTableBodyTd.vue`
**Изменение**: 6 цветовых блоков-колонок удаляются, `.publish-name` расширяется до 210 px, 20 computed и 3 метода `dblClick*` удаляются.

### 5.1. Template (после PR)

```vue
<div v-if="publish" class="publish">
  <div
    class="publish-name"
    :style="styleSongName"
    :title="publishTitle"
    @click.left="editSong"
    v-text="publishText"
  />
</div>
<div v-else class="empty" />
```

**Удалено**: 6 блоков `<div class="publish-column">...</div>` (строки 17–48).

### 5.2. Computed (после PR)

```javascript
computed: {
  parentRoute() { return 'Publications' },
  publishTitle() { /* сохранён */ },
  publishText() { /* сохранён */ },
  styleSongName() { /* сохранён */ },
  // processColor* — все 20 удалены
}
```

### 5.3. Methods (после PR)

```javascript
methods: {
  editSong() { /* сохранён */ },
  closeSongEdit() { /* сохранён */ },
  // dblClick* — все 3 удалены
}
```

### 5.4. CSS (после PR)

```css
.publish {
  display: flex;
  flex-direction: row;
  min-width: 210px;  /* было 200px */
  max-width: 210px;  /* было 200px */
  /* ... сохранено ... */
}
.publish-name {
  display: block;
  width: 210px;  /* было 150px */
  /* ... сохранено ... */
}
.publish-name:hover { /* сохранён */ }
.empty {
  font-size: 0;
  width: 210px;  /* было 200px */
  height: 20px;
  background-color: grey;
  /* ... сохранено ... */
}
/* .publish-column, .publish-column-cell-top, .publish-column-cell-bottom — удалены */
```

---

## 6. SongEdit (Vue-компонент — изменяется)

**Файл**: `webvue3/src/components/Songs/edit/SongEdit.vue`
**Изменение**: 4 PLAY-кнопки (строки 2297–2328) теряют `:style="{ backgroundColor: song.processColorMelt* }"`. Фон становится одинаковым (CSS-класс `.group-button`).

### 6.1. PLAY-кнопки (после PR)

```vue
<button class="group-button" title="PLAY KARAOKE" @click="playKaraoke">PLAY KARAOKE</button>
<button class="group-button" title="PLAY LYRICS" @click="playLyrics">PLAY LYRICS</button>
<button class="group-button" title="PLAY CHORDS" @click="playChords">PLAY CHORDS</button>
<button class="group-button" title="PLAY TABS" @click="playTabs">PLAY TABS</button>
```

**Удалено**: 4 строки `:style="{ backgroundColor: song.processColorMelt* }"`.

---

## 7. Связи и инварианты

| Связь | Сохраняется? | Обоснование |
|---|---|---|
| `Song.processColor*` геттеры → `Song.toDTO()` присваивания | **Сохраняется** (только в `toDTO()` удаляется 27 из 28) | `toDTO()` — мост между доменом и DTO; для `processColorPlayerDemo` мост сохраняется |
| `Song.processColor*` геттеры → `Song.diff` LOCAL↔SERVER | **Сохраняется** полностью | Принцип III NON-NEGOTIABLE |
| `Song.processColor*` геттеры → `Publication.publishXcolorMeltY` | **Сохраняется** | `Publication` строит собственные геттеры из `Song.processColor*` |
| `Song.processColor*` геттеры → серверные шаблоны `${song.processColorX}` | **Сохраняется** | Thymeleaf получает raw `Song` через атрибут модели |
| `Song.processColor*` геттеры → JS `data.processColorX` после `/song/{id}` | **Сохраняется** | `MainController.getSong` возвращает raw `Song` |
| `SongDTO.processColorPlayerDemo` → `webvue3 SongsTable.vue:329` | **Сохраняется** | Единственный живой потребитель |
| `SongDTOdigest.processColorPlayerDemo` → `webvue3 SongsTable.vue:329` | **Сохраняется** | Тот же потребитель (digest/полный DTO — оба имеют это поле) |

---

## 8. Validation rules

- **Compile-time**: количество параметров `data class` = количество присваиваний в `toDTO()`/`toDtoDigest()`. Kotlin compiler обеспечивает это. Любое расхождение → ошибка компиляции.
- **Runtime**: `Song` getters могут вернуть любую строку-цвет (`#RRGGBB`); фронт использует её в `style.backgroundColor` — браузер сам валидирует.
- **Linting**: ESLint (Vue/JS) и ktlint (Kotlin) — baseline не должен расти. Проверяется в `tools/check-eslint-baseline.sh` и `./gradlew ktlintCheck`.
- **JSDoc/KDoc coverage**: `export default` для Vue-компонента и `class`/`data class` для Kotlin — 100% покрытие (FR-006).

---

## 9. State transitions

**N/A** — это рефакторинг без изменения жизненного цикла сущностей. `Song` продолжает создаваться через `loadFromDbById`, изменяться через `saveToDb()`, синхронизироваться через diff. Никакие новые состояния/переходы не вводятся.
