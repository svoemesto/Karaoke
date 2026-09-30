// #203: расхождение форматирования между админским SubsEdit и composable.
// Канон задан владельцем 2026-09-29: «поведение админского редактора».
//
// Обе проверки ставят маркер ПОСЛЕ первого слога намеренно: wasBr инициализируется
// true, поэтому первое слово всегда с заглавной независимо от комментария.
// Различие видно только на последующих словах.

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { formatText } from '../useKaraokeEditor.js'

const syl = (label) => ({ markertype: 'syllables', label })
const set = (label) => ({ markertype: 'setting', label })

describe('#203.1 пустой комментарий COMMENT| ', () => {
  test('даёт перенос строки и НЕ рендерит пустой span', () => {
    const html = formatText([syl('раз'), set('COMMENT| '), syl('два')], 0)
    assert.ok(!html.includes('fx-comment'), 'пустой span рендериться не должен: ' + html)
    assert.ok(html.includes('</span><br>'), 'перенос строки ожидается: ' + html)
  })

  test('НЕ поднимает регистр следующего слова', () => {
    const html = formatText([syl('раз'), set('COMMENT| '), syl('два')], 0)
    assert.ok(html.includes('>два<'), 'слово после пустого комментария остаётся строчным: ' + html)
    assert.ok(!html.includes('>Два<'), 'регистр подниматься не должен: ' + html)
  })
})

describe('#203.1b непустой комментарий COMMENT|текст', () => {
  test('рендерит span и поднимает регистр следующего слова', () => {
    const html = formatText([syl('раз'), set('COMMENT| жест'), syl('два')], 0)
    assert.ok(html.includes('fx-comment'), 'span комментария ожидается: ' + html)
    assert.ok(html.includes('>Два<'), 'после непустого комментария регистр поднимается: ' + html)
  })

  test('пустой и непустой комментарии различаются', () => {
    const empty = formatText([syl('раз'), set('COMMENT| '), syl('два')], 0)
    const full = formatText([syl('раз'), set('COMMENT| жест'), syl('два')], 0)
    assert.notEqual(empty, full, 'ветки обязаны различаться')
  })
})

describe('#203.2 перенос строки при смене группы голоса', () => {
  test('смена группы даёт <br>', () => {
    const html = formatText([syl('раз'), set('GROUP|1'), syl('два')], 0)
    assert.ok(html.includes('Раз</span><br>'), 'перед сменой группы нужен перенос: ' + html)
  })

  test('две разные группы — два переноса', () => {
    const html = formatText([syl('раз'), set('GROUP|1'), syl('два'), set('GROUP|2'), syl('три')], 0)
    const brs = (html.match(/<br>/g) || []).length
    assert.equal(brs, 2, 'две смены группы — два переноса: ' + html)
  })

  test('повтор той же группы переноса не добавляет', () => {
    const once = formatText([syl('раз'), set('GROUP|1'), syl('два')], 0)
    const twice = formatText([syl('раз'), set('GROUP|1'), set('GROUP|1'), syl('два')], 0)
    const brsOnce = (once.match(/<br>/g) || []).length
    const brsTwice = (twice.match(/<br>/g) || []).length
    assert.equal(brsOnce, 1)
    assert.equal(brsTwice, 1, 'повтор той же группы не должен давать лишний перенос: ' + twice)
  })

  test('без смены группы переносов нет', () => {
    const html = formatText([syl('раз'), syl('два'), syl('три')], 0)
    assert.equal((html.match(/<br>/g) || []).length, 0, 'переносов быть не должно: ' + html)
  })
})
