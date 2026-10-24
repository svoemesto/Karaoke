// #202: syncMarkersFromSpecTags в публичном composable разошёлся с админским.
// Спека 163 сознательно не распространила фикс на публичную копию, и дрейф
// привёл к тому, что массив маркеров раздувался на каждое нажатие клавиши.
// У редактора с автосохранением это уезжало в черновик без участия пользователя.
//
// Синтаксис спецтегов: ~newline~, ~group:0~, ~comment:текст~ (SPEC_TAG_REGISTRY).

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { syncMarkersFromSpecTags } from '../useKaraokeEditor.js'

const syl = (label, time) => ({ markertype: 'syllables', label, time })

const TEXT = 'раз два\n~newline~\nтри'

describe('#202 FIX #018: спецтег не встаёт в ноль', () => {
  test('при соседях около нуля время всё равно не меньше 0.5', () => {
    // prevEndTime=0.1, nextStartTime=0.2 -> gap=0.1. Без защиты формула
    // prevEndTime + gap/2 дала бы 0.15, и полоса слипалась бы с нулём.
    const out = syncMarkersFromSpecTags([syl('раз', 0), syl('два', 0.1), syl('три', 0.2)], TEXT)
    const added = out.filter((m) => m.markertype === 'newline')
    assert.equal(added.length, 1, 'маркер должен добавиться один раз')
    assert.ok(added[0].time >= 0.5, 'время должно быть >= 0.5, получено ' + added[0].time)
  })
})

describe('#202 идемпотентность', () => {
  test('повторный вызов на неизменном тексте не добавляет маркеры', () => {
    const first = syncMarkersFromSpecTags([syl('раз', 1), syl('два', 2), syl('три', 3)], TEXT)
    const second = syncMarkersFromSpecTags(first, TEXT)
    assert.equal(second.length, first.length, 'число маркеров не должно расти при повторе')
  })

  test('десять вызовов подряд не раздувают массив', () => {
    let markers = [syl('раз', 1), syl('два', 2), syl('три', 3)]
    const before = markers.length
    for (let i = 0; i < 10; i++) markers = syncMarkersFromSpecTags(markers, TEXT)
    assert.ok(markers.length <= before + 1, 'раздувание: ' + markers.length)
  })

  test('после сортировки повторный вызов тоже не добавляет', () => {
    // Индексная дедупликация ломалась именно на этом: sortMarkers() при равенстве
    // времени вытеснял вставленный тег-маркер за пределы индексного окна.
    const markers = [syl('раз', 1), syl('два', 1), syl('три', 2)]
    const first = syncMarkersFromSpecTags(markers, TEXT)
    const n1 = first.length
    const sorted = first
      .slice()
      .sort((a, b) =>
        a.time === b.time ? a.markertype.localeCompare(b.markertype) : a.time - b.time,
      )
    const second = syncMarkersFromSpecTags(sorted, TEXT)
    assert.equal(second.length, n1, 'сортировка не должна ломать идемпотентность')
  })
})

describe('#202 несколько разных спецтегов', () => {
  test('оба добавляются', () => {
    const text = 'раз два\n~newline~\nтри\nчетыре\n~group:1~\nпять'
    const out = syncMarkersFromSpecTags(
      [syl('раз', 1), syl('два', 2), syl('три', 3), syl('четыре', 4), syl('пять', 5)],
      text,
    )
    const types = out.map((m) => m.markertype)
    assert.equal(types.filter((t) => t === 'newline').length, 1, 'newline должен добавиться')
    assert.equal(types.filter((t) => t === 'setting').length, 1, 'group должен добавиться')
  })
})

// BR-524 (аудит #336, OpenProject #387): поведение #202 — дедуп по ВРЕМЕННОМУ
// диапазону, а не по индексному окну — раньше проверялось в e2e ПО ТЕКСТУ
// минифицированного бандла (регекс /\.time>=\w+&&\w+\.time<=/). Такая проверка
// зависела от версии минификатора и падала без дефекта. Теперь поведение
// проверяется по исходникам: дубликатом считается маркер с тем же типом и
// меткой, чьё ВРЕМЯ попадает в окно [prevEndTime, windowEnd].
describe('#202 дедуп по временному диапазону', () => {
  const newlineMarker = (time) => ({ markertype: 'newline', label: '', time })

  test('маркер с той же меткой ВНУТРИ окна — дубликат (не добавляем)', () => {
    const out = syncMarkersFromSpecTags(
      [syl('раз', 1), syl('два', 2), syl('три', 3), newlineMarker(2.5)],
      TEXT,
    )
    const newlines = out.filter((m) => m.markertype === 'newline')
    assert.equal(newlines.length, 1, 'дубликат внутри окна добавляться не должен')
  })

  test('маркер с той же меткой ВНЕ окна — не дубликат (добавляем)', () => {
    const out = syncMarkersFromSpecTags(
      [syl('раз', 1), syl('два', 2), syl('три', 3), newlineMarker(0.5)],
      TEXT,
    )
    const newlines = out.filter((m) => m.markertype === 'newline')
    assert.equal(
      newlines.length,
      2,
      'метка вне временного окна дубликатом не считается — маркер добавляется',
    )
  })
})
