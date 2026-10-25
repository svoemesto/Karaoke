// BR-092 (аудит #336, пакет PKG-S3-31, OpenProject #403).
//
// ДЕФЕКТ. Числовые поля карточки песни — свободный текст: `<input v-model="song.year">`
// и подобные без всякой валидации. Сервер принимает строку как есть (`SongUpdateMapper`:
// `"year" to SongField.YEAR`), но мягкий парсер в `Song.kt`
// (`fields[SongField.YEAR]?.toLongOrNull() ?: 0L`) превращает опечатку («20x4»,
// «2024—2025», лишний символ) в 0 — и UPDATE записывает 0, ЗАТИРАЯ прежнее значение.
// Клиент при этом получает успех, кладёт в `snapshotSong` исходную строку и показывает её
// как сохранённую: админ ошибки не видел, а после F5 в поле оказался 0.
//
// РЕШЕНИЕ (клиентская валидация — бэкенд в этом пакете не меняется). Числовые поля
// проверяются ДО отправки: невалидное значение не включается в diff (сервер его не
// получает), а пользователь видит ошибку с названием поля. Пустое значение допустимо —
// поле очищено, и сервер сам приведёт его к 0.
//
// Почему целые, а не «любое число»: все перечисленные поля на бэкенде — целочисленные
// (`year`, `track`, `bpm`, `rootId`, `audioParentId` — Long; `audioSimilarityPercent` — Int;
// `audioDeltaMs` — Long). Дробное значение там тоже превратилось бы в 0.

/**
 * Числовые поля карточки песни: имя поля → подпись для сообщения об ошибке.
 *
 * @type {Object<string, string>}
 */
export const SONG_NUMERIC_FIELDS = {
  year: 'Год',
  track: '№ трека',
  bpm: 'Темп',
  rootId: 'Root ID',
  audioParentId: 'Аудио-родитель (id)',
  audioSimilarityPercent: 'Аудио-схожесть, %',
  audioDeltaMs: 'Аудио-сдвиг, мс',
}

/**
 * Является ли поле числовым полем карточки песни.
 *
 * @param {string} name — имя поля.
 * @returns {boolean}
 */
export function isSongNumericField(name) {
  return Object.prototype.hasOwnProperty.call(SONG_NUMERIC_FIELDS, name)
}

/**
 * Проверяет значение числового поля карточки песни.
 *
 * @param {string} name — имя поля.
 * @param {*} value — значение поля (строка из input или число).
 * @returns {boolean} true — значение корректно (или поле не числовое).
 */
export function isValidNumericSongField(name, value) {
  if (!isSongNumericField(name)) return true
  if (value === undefined || value === null || value === '') return true
  const text = String(value).trim()
  return /^-?\d+$/.test(text)
}

/**
 * Отбирает невалидные числовые поля из набора изменений.
 *
 * @param {Array<{name: string, new: *}>} items — элементы diff.
 * @returns {Array<{name: string, label: string, value: *}>} список невалидных полей.
 */
export function findInvalidNumericSongFields(items) {
  const invalid = []
  for (const item of items || []) {
    if (!isValidNumericSongField(item.name, item.new)) {
      invalid.push({
        name: item.name,
        label: SONG_NUMERIC_FIELDS[item.name],
        value: item.new,
      })
    }
  }
  return invalid
}
