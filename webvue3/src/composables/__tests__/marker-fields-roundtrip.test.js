// Регрессионный тест на потерю полей маркера при одобрении волонтёрской правки
// (тикет #200 карты wayfinder #186).
//
// ПУТЬ ПОТЕРИ. Лёгкий редактор (админский) не редактирует
// note/chord/stringLad/lockLad. Раньше `markersFromServer` их вообще не читал,
// а `markersToSave` писал пустые строки. При одобрении задания путь
// `SongEditorController` -> `Song.setSourceMarkers` ПОЛНОСТЬЮ заменяет список
// маркеров голоса в БД — то есть заполненное админом содержимое стиралось.
//
// Тест фиксирует контракт «пронести, не управляя»: значения, которые пришли с
// сервера, должны выйти без изменений.
//
// Запуск: node --test <путь>/marker-fields-roundtrip.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { markersToSave, markersFromServer } from '../useKaraokeEditor.js'

// Маркер в том виде, в каком его отдаёт сервер: с заполненными полями,
// которые заполнял админ в полновесном SubsEdit.
const FROM_SERVER = [
  {
    time: 12.5,
    label: 'сло_во',
    note: 'A|440',
    chord: 'Am',
    stringLad: '3|5',
    lockLad: 'true',
    color: '#D2691E',
    position: 'bottom',
    markertype: 'syllables',
  },
]

describe('поля, которые лёгкий редактор не редактирует (#200)', () => {
  test('markersFromServer ЧИТАЕТ note/chord/stringLad/lockLad', () => {
    const [m] = markersFromServer(FROM_SERVER)
    assert.equal(m.note, 'A|440', 'note должен читаться с сервера')
    assert.equal(m.chord, 'Am', 'chord должен читаться с сервера')
    assert.equal(m.stringLad, '3|5', 'stringLad должен читаться с сервера')
    assert.equal(m.lockLad, 'true', 'lockLad должен читаться с сервера')
  })

  test('полный round-trip сервер -> редактор -> сервер НЕ теряет поля', () => {
    const fromServer = markersFromServer(FROM_SERVER)
    const out = markersToSave(fromServer)
    for (const key of ['note', 'chord', 'stringLad', 'lockLad']) {
      assert.equal(
        out[0][key],
        FROM_SERVER[0][key],
        `${key} потерялся по пути сервер -> редактор -> сервер`,
      )
    }
  })

  test('правка тайминга волонтёром не затирает поля админа', () => {
    // Волонтёр двигает маркер по времени — это всё, что он делает.
    const fromServer = markersFromServer(FROM_SERVER)
    fromServer[0].time = 13.25
    const out = markersToSave(fromServer)
    assert.equal(out[0].time, 13.25, 'правка тайминга должна сохраниться')
    assert.equal(out[0].note, 'A|440', 'но note админа при этом не должен стираться')
    assert.equal(out[0].lockLad, 'true', 'и lockLad тоже')
  })

  test('отсутствующие поля остаются пустыми, а не undefined', () => {
    const [m] = markersToSave([{ time: 1, label: 'x', markertype: 'syllables' }])
    for (const key of ['note', 'chord', 'stringLad', 'lockLad']) {
      assert.equal(m[key], '', `${key} должен быть пустой строкой, а не undefined`)
    }
  })
})
