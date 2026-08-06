# Contracts: Удаление 18 столбцов-флагов из таблицы «Песни»

**Created**: 2026-08-06
**Feature**: [spec.md](./spec.md)

## Контракты

**Нет контрактов API, изменяемых этой фичей.**

Эта фича — чисто UI-удаление 18 столбцов из отображения в таблице админки `webvue3`. Бэкенд (`/api/songs/list`), DTO, формат JSON, эндпоинты — **всё остаётся неизменным**.

## Существующие контракты (НЕ изменяются)

### `/api/songs/list` (бэкенд → Vuex `state.songs`)

Продолжает возвращать тот же JSON со всеми 18 флагами в `flagSponsr`, `flagVk`, `flagDzen*`, `flagVk*`, `flagTelegram*`, `flagMax*`. UI просто перестаёт их отрисовывать в таблице «Песни».

### Vuex-геттеры `playLyrics`, `playKaraoke`, `playChords`, `playTabs`, `playDemo`

Продолжают существовать. Используются в:
- `webvue3/src/components/Songs/edit/SongEdit.vue` — кнопки PLAY LYRICS / PLAY KARAOKE / PLAY CHORDS / PLAY TABS / PLAY DEMO.
- `webvue3/src/components/Publish/components/PublishTableBodyTd.vue` — таблица публикаций.

В `SongsTable.vue` обёртки-методы (this.playLyrics(id) и т.д.) удаляются, но обращение к `$store.getters.playLyrics` из других компонентов остаётся рабочим.

### Computed `processColorSponsr`, `processColorVk`, и т.п.

Продолжают вычисляться на бэкенде (Kotlin/Java) и приходить в JSON. В `SongsTable.vue` перестают использоваться, но в `Publish/components/PublishTableBodyTd.vue` остаются (используются в той же таблице публикаций).

## Формат Vuex-state `fieldSongParams[]`

Изменяется — массив укорачивается с 22 элементов до 12. Это **внутренний контракт** между `store.js` и потенциальными потребителями (сейчас нет прямых потребителей в коде, см. [research.md Decision 4](../research.md)). Геттер `getFieldSongParams` остаётся как API Vuex, просто возвращает меньший массив.

**До**:
```js
fieldSongParams: [
  { name: 'id', params: {...} },
  { name: 'songName', params: {...} },
  // ... 20 других полей ...
  { name: 'flagSponsr', params: {...} },     // УДАЛЯЕТСЯ
  { name: 'flagVk', params: {...} },          // УДАЛЯЕТСЯ
  { name: 'flagDzenLyrics', params: {...} },  // УДАЛЯЕТСЯ
  // ... 7 других удаляемых полей ...
]
```

**После**:
```js
fieldSongParams: [
  { name: 'id', params: {...} },
  { name: 'songName', params: {...} },
  // ... 11 оставшихся полей ...
]
```
