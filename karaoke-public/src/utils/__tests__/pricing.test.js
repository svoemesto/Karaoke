// BR-326 (аудит #336, пакет PKG-S3-27, OpenProject #399): цены — из API, а не из текста.
//
// ЧТО БЫЛО. На публичной странице «О проекте» цены были зашиты в текст («449 ₽/мес»,
// «49 ₽»), тогда как реальные тарифы живут в БД (`tbl_price_tariffs`) и правятся админом;
// страница `/premium` берёт их из API. После правки тарифа `/about` показывал старую цену —
// на одном сайте два разных числа о деньгах.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { formatRub, pickDefaultTariff, priceTexts } from '../pricing.js'
import { readCode } from './sourceContract.js'

describe('BR-326: тексты цен собираются из тарифов', () => {
  test('тариф по умолчанию — с isDefault', () => {
    const tariffs = [
      { id: 1, priceRub: 100 },
      { id: 2, priceRub: 449, isDefault: true },
    ]
    assert.equal(pickDefaultTariff(tariffs).id, 2)
  })

  test('без isDefault берётся первый', () => {
    assert.equal(pickDefaultTariff([{ id: 7, priceRub: 50 }]).id, 7)
  })

  test('пустой список — null', () => {
    assert.equal(pickDefaultTariff([]), null)
    assert.equal(pickDefaultTariff(null), null)
  })

  test('форматирование суммы', () => {
    assert.equal(formatRub(449), '449')
    assert.equal(formatRub(1200), '1 200')
    assert.equal(formatRub('49'), '49')
    assert.equal(formatRub('abc'), '')
  })

  test('цены подставляются из тарифов', () => {
    const out = priceTexts(
      [{ id: 1, priceRub: 449, isDefault: true }],
      [{ id: 2, priceRub: 49, isDefault: true }],
    )
    assert.equal(out.site, '449 ₽/мес')
    assert.equal(out.song, '49 ₽')
    assert.equal(out.hasPrices, true)
  })

  test('тарифов нет — цены не показываются вовсе (лучше, чем заведомо неверное число)', () => {
    const out = priceTexts([], [])
    assert.equal(out.site, '')
    assert.equal(out.song, '')
    assert.equal(out.hasPrices, false)
  })
})

describe('BR-326: контракт исходников (падает на старом коде)', () => {
  test('AboutView не содержит зашитых цен', () => {
    const src = readCode('src/views/AboutView.vue')
    assert.ok(
      !/449\s*₽/.test(src),
      'цена 449 ₽/мес была зашита в текст страницы (BR-326)',
    )
    assert.ok(
      !/49\s*₽(?!.*priceRub)/.test(src),
      'цена 49 ₽ была зашита в текст страницы (BR-326)',
    )
    assert.ok(src.includes('priceTexts'), 'цены должны собираться из тарифов')
    assert.ok(
      /apiGet\('\/api\/public\/account\/subscription\/tariffs', \{ scope: 'SITE' \}\)/.test(src),
      'тарифы нужно брать из публичного API',
    )
  })
})
