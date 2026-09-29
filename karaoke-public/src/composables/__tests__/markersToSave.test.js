// Контракт сериализации маркеров: имя поля `lockLad` должно совпадать с
// `karaoke-app/.../model/SourceMarker.kt`.
//
// ИСТОРИЯ (тикет #201 карты wayfinder #186). Фронты долгое время отправляли
// ключ `locklad` — с заглавной «л» только в начале, `locklad` вместо `lockLad`.
// Jackson на бэкенде настроен с `ignoreUnknownKeys = true`, поэтому значение
// МОЛЧА выбрасывалось: без ошибки, без лога. `Song.kt` проверяет
// `marker.lockLad.toBoolean()`, чтобы «прибить ноту к конкретной струне и ладу»
// вместо автоназначения — значит фича не работала ни разу за всё время,
// при том что кнопка в UI работала и подсвечивалась.
//
// Ошибка была дополнительно «задокументирована» в комментариях обоих
// контроллеров как «поле admin-формата, которого нет в SourceMarker» —
// поэтому её и не замечали. Тест ниже фиксирует канон, чтобы регрессия
// не вернулась молча.
//
// Запуск: node --test karaoke-public/src/composables/__tests__/markersToSave.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { markersToSave } from '../useKaraokeEditor.js'

const SAMPLE = [{ time: 1.5, label: 'сло_во', markertype: 'syllables', position: 'bottom' }]

describe('markersToSave: имя поля lockLad (тикет #201)', () => {
  test('выдаёт ключ lockLad — как в SourceMarker.kt', () => {
    const [m] = markersToSave(SAMPLE)
    assert.ok('lockLad' in m, 'ключ lockLad должен присутствовать в payload')
  })

  test('НЕ выдаёт опечатку locklad (иначе значение теряется на бэкенде молча)', () => {
    const [m] = markersToSave(SAMPLE)
    assert.equal(
      'locklad' in m,
      false,
      'ключ locklad недопустим: SourceMarker не имеет такого поля, ignoreUnknownKeys его отбросит',
    )
  })

  test('в payload ровно один из двух похожих ключей, без дублей', () => {
    const [m] = markersToSave(SAMPLE)
    const similar = Object.keys(m).filter((k) => k.toLowerCase() === 'locklad')
    assert.deepEqual(similar, ['lockLad'])
  })

  test('остальные поля контракта на месте', () => {
    const [m] = markersToSave(SAMPLE)
    for (const key of [
      'time',
      'label',
      'note',
      'chord',
      'stringLad',
      'color',
      'position',
      'markertype',
    ]) {
      assert.ok(key in m, `ключ ${key} должен присутствовать`)
    }
  })
})
