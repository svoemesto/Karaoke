// BR-523 (аудит #336, пакет PKG-S3-02, OpenProject #387): регрессионный тест на
// экранирование пользовательской метки в АДМИНСКОЙ копии formatText.
//
// Контекст и правило включения — в парном файле
// karaoke-public/src/composables/__tests__/formatText-escape.test.js:
// дефект BR-312 (S2, OpenProject #377, волна D) открыт, тест ждёт фикса и
// включается вместе с ним (SKIP_UNTIL_BR312 → false).
//
// Вторая копия formatText живёт в webvue3 (ReviewModal.vue рендерит её через
// v-html), поэтому регресс-тест нужен и здесь: починка только публичной копии
// оставила бы админку уязвимой.
//
// Запуск: cd webvue3 && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { formatText } from '../useReviewModalFormat.js'

// Переключатель активности теста. false — тест включён.
// Фикс BR-312 (OpenProject #377) сопровождается правкой этой строки.
const SKIP_UNTIL_BR312 = 'BR-312 не исправлен (OpenProject #377, волна D)'

const set = (label) => ({ markertype: 'setting', label })
const syl = (label) => ({ markertype: 'syllables', label })

const PAYLOAD = 'COMMENT|<img src=x onerror=alert(1)>'

describe(
  'BR-523: метка комментария не превращается в разметку (XSS-регрессия, админская копия)',
  { skip: SKIP_UNTIL_BR312 },
  () => {
    test('в HTML-буфере нет сырого <img', () => {
      const html = formatText([set(PAYLOAD)], -1)
      assert.ok(!html.includes('<img'), 'в выводе не должно быть сырного <img: ' + html)
    })

    test('ни один тег в выводе не несёт onerror (payload не стал живой разметкой)', () => {
      const html = formatText([set(PAYLOAD)], -1)
      assert.ok(!/<[^>]*onerror/.test(html), 'в выводе не должно быть тега с onerror: ' + html)
    })

    test('вместо сырого тега — экранированная сущность', () => {
      const html = formatText([set(PAYLOAD)], -1)
      assert.ok(html.includes('&lt;img'), 'ожидается экранированная сущность &lt;img: ' + html)
    })

    test('регресс-контроль: обычный текст не экранируется', () => {
      const html = formatText([syl('раз'), set('COMMENT| жест'), syl('два')], -1)
      assert.ok(html.includes('>Два<'), 'обычный текст должен остаться как есть: ' + html)
      assert.ok(!html.includes('&lt;'), 'обычный текст не должен содержать сущности: ' + html)
      assert.ok(!html.includes('&amp;'), 'обычный текст не должен содержать сущности: ' + html)
    })
  },
)
