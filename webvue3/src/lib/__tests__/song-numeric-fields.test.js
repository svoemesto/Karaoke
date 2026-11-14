// Регрессионный тест на клиентскую валидацию числовых полей карточки песни
// (BR-092, аудит #336, пакет PKG-S3-31, OpenProject #403; KARAOKE-401).
//
// ДЕФЕКТ, который закрывает модуль. Числовые поля карточки песни — свободный
// текст (`<input v-model="song.year">` и подобные) без всякой валидации. Сервер
// принимает строку как есть, но мягкий парсер в `Song.kt`
// (`fields[SongField.YEAR]?.toLongOrNull() ?: 0L`) превращает опечатку («20x4»,
// «2024—2025», лишний символ) в 0 — и UPDATE записывает 0, ЗАТИРАЯ прежнее
// значение. Клиент при этом получает успех и показывает опечатку как
// сохранённую: админ ошибки не видел, а после F5 в поле оказался 0.
//
// РЕШЕНИЕ (чисто клиентское, бэкенд не менялся). Числовые поля проверяются ДО
// отправки: невалидное значение не включается в diff (сервер его не получает), а
// пользователь видит ошибку с названием поля. Пустое значение допустимо — поле
// очищено, сервер сам приведёт его к 0.
//
// Тест фиксирует контракт модуля `webvue3/src/lib/song-numeric-fields.js`:
// какие поля считаются числовыми, что считается корректным значением, и что
// `findInvalidNumericSongFields` отдаёт в UI (подпись поля и значение).
//
// Запуск: cd webvue3 && node --test src/lib/__tests__/song-numeric-fields.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import {
  SONG_NUMERIC_FIELDS,
  isSongNumericField,
  isValidNumericSongField,
  findInvalidNumericSongFields,
} from '../song-numeric-fields.js'

describe('числовые поля карточки песни: набор полей (BR-092)', () => {
  test('в наборе — все семь числовых полей', () => {
    assert.deepEqual(Object.keys(SONG_NUMERIC_FIELDS).sort(), [
      'audioDeltaMs',
      'audioParentId',
      'audioSimilarityPercent',
      'bpm',
      'rootId',
      'track',
      'year',
    ])
  })

  test('у каждого поля есть непустая подпись для сообщения об ошибке', () => {
    for (const [name, label] of Object.entries(SONG_NUMERIC_FIELDS)) {
      assert.equal(typeof label, 'string', `поле ${name}: подпись должна быть строкой`)
      assert.ok(label.length > 0, `поле ${name}: подпись не должна быть пустой`)
    }
  })
})

describe('числовые поля карточки песни: isSongNumericField (BR-092)', () => {
  test('известное поле — числовое', () => {
    assert.equal(isSongNumericField('year'), true)
    assert.equal(isSongNumericField('track'), true)
    assert.equal(isSongNumericField('bpm'), true)
    assert.equal(isSongNumericField('rootId'), true)
    assert.equal(isSongNumericField('audioParentId'), true)
    assert.equal(isSongNumericField('audioSimilarityPercent'), true)
    assert.equal(isSongNumericField('audioDeltaMs'), true)
  })

  test('неизвестное поле — не числовое', () => {
    assert.equal(isSongNumericField('title'), false)
    assert.equal(isSongNumericField('performer'), false)
    assert.equal(isSongNumericField(''), false)
    assert.equal(isSongNumericField(undefined), false)
  })

  test('подмена прототипа не выдаёт числовое поле', () => {
    assert.equal(isSongNumericField('toString'), false)
    assert.equal(isSongNumericField('constructor'), false)
    assert.equal(isSongNumericField('__proto__'), false)
  })
})

describe('числовые поля карточки песни: isValidNumericSongField (BR-092)', () => {
  test('целое — корректно', () => {
    assert.equal(isValidNumericSongField('year', '2024'), true)
    assert.equal(isValidNumericSongField('track', '7'), true)
    assert.equal(isValidNumericSongField('bpm', '120'), true)
  })

  test('отрицательное целое — корректно (знак «минус» часть целого числа)', () => {
    assert.equal(isValidNumericSongField('audioDeltaMs', '-250'), true)
    assert.equal(isValidNumericSongField('track', '-1'), true)
  })

  test('пустое значение — корректно: поле очищено, сервер приведёт его к 0', () => {
    assert.equal(isValidNumericSongField('year', ''), true)
  })

  test('null и undefined — корректно: значения нет', () => {
    assert.equal(isValidNumericSongField('year', null), true)
    assert.equal(isValidNumericSongField('year', undefined), true)
  })

  test('опечатка «20x4» — неверно', () => {
    assert.equal(isValidNumericSongField('year', '20x4'), false)
  })

  test('диапазон «2024—2025» — неверно', () => {
    assert.equal(isValidNumericSongField('year', '2024—2025'), false)
  })

  test('дробное «12.5» — неверно: на бэкенде поля целочисленные', () => {
    assert.equal(isValidNumericSongField('year', '12.5'), false)
  })

  test('пробелы вокруг числа не делают его неверным', () => {
    assert.equal(isValidNumericSongField('year', ' 2024 '), true)
  })

  test('число (не строка) — корректно', () => {
    assert.equal(isValidNumericSongField('year', 2024), true)
    assert.equal(isValidNumericSongField('track', 0), true)
  })

  test('нечисловое поле не проверяется: любое значение корректно', () => {
    assert.equal(isValidNumericSongField('title', 'что угодно'), true)
    assert.equal(isValidNumericSongField('title', '20x4'), true)
    assert.equal(isValidNumericSongField('performer', null), true)
  })
})

describe('числовые поля карточки песни: findInvalidNumericSongFields (BR-092)', () => {
  test('невалидное поле возвращается с подписью и значением', () => {
    const invalid = findInvalidNumericSongFields([{ name: 'year', new: '20x4' }])

    assert.equal(invalid.length, 1)
    assert.equal(invalid[0].name, 'year')
    assert.equal(invalid[0].label, SONG_NUMERIC_FIELDS.year)
    assert.equal(invalid[0].value, '20x4')
  })

  test('валидные изменения не возвращаются', () => {
    const invalid = findInvalidNumericSongFields([
      { name: 'year', new: '2024' },
      { name: 'track', new: '' },
      { name: 'title', new: 'любой текст' },
    ])

    assert.deepEqual(invalid, [])
  })

  test('невалидные поля собираются в том же порядке, что и в наборе изменений', () => {
    const invalid = findInvalidNumericSongFields([
      { name: 'title', new: 'песня' },
      { name: 'year', new: '20x4' },
      { name: 'bpm', new: '12.5' },
    ])

    assert.equal(invalid.length, 2)
    assert.deepEqual(
      invalid.map((item) => item.name),
      ['year', 'bpm'],
    )
    assert.equal(invalid[1].label, SONG_NUMERIC_FIELDS.bpm)
  })

  test('пустой набор и отсутствие набора дают пустой список', () => {
    assert.deepEqual(findInvalidNumericSongFields([]), [])
    assert.deepEqual(findInvalidNumericSongFields(undefined), [])
    assert.deepEqual(findInvalidNumericSongFields(null), [])
  })
})
