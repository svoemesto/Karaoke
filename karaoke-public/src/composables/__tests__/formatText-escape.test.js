// BR-523 (аудит #336, пакет PKG-S3-02, OpenProject #387): регрессионный тест на
// экранирование пользовательской метки перед попаданием в HTML-буфер (v-html).
//
// КОНТЕКСТ. Дефект BR-312 (S2, OpenProject #377, волна D): метка комментария
// («COMMENT|<img src=x onerror=alert(1)>») интерполируется в HTML без
// экранирования, а результат уходит в v-html админского ReviewModal и
// публичного редактора. Само исправление — в другой волне; здесь заводится
// ТЕСТ, который ловит регрессию.
//
// ПОЧЕМУ ТЕСТ ЖДЁТ (skip), А НЕ ПАДАЕТ. Дефект BR-312 ещё открыт: падающий
// тест в CI роняет сборку master задолго до прихода волны D. Тест включается
// одной правкой (SKIP_UNTIL_BR312 → false) ВМЕСТЕ с фиксом BR-312.
//
// СОСТОЯНИЕ ПОД КОНТРОЛЕМ. tools/check-marker-escape-guard.sh проверяет:
//   (а) тест «взведён» — падает на уязвимом снимке кода и проходит на
//       исправленном (фикстуры в tools/fixtures/marker-escape/);
//   (б) skip-состояние теста соответствует состоянию кода: код исправлен →
//       тест включён; код ещё уязвим → тест ждёт. Расхождение → guard падает.
//
// ПРОВЕРЯЕМОЕ СВОЙСТВО. «Опасного» в выводе — не подстрока «onerror» сама по
// себе (после экранирования она остаётся инертным текстом, и комментарий
// должен быть виден пользователю), а ЖИВАЯ РАЗМЕТКА: пользовательский «<»
// не должен превращаться в тег. Поэтому проверяем: (1) нет сырого «<img»,
// (2) нет тега с атрибутом onerror, (3) присутствует экранированная сущность.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { formatText } from '../useKaraokeEditor.js'

// Переключатель активности теста. false — тест включён.
// Фикс BR-312 (OpenProject #377) сопровождается правкой этой строки.
const SKIP_UNTIL_BR312 = false // BR-312 исправлен: метка экранируется перед интерполяцией в HTML

const set = (label) => ({ markertype: 'setting', label })
const syl = (label) => ({ markertype: 'syllables', label })

const PAYLOAD = 'COMMENT|<img src=x onerror=alert(1)>'

describe(
  'BR-523: метка комментария не превращается в разметку (XSS-регрессия)',
  { skip: SKIP_UNTIL_BR312 },
  () => {
    test('в HTML-буфере нет сырого <img', () => {
      const html = formatText([set(PAYLOAD)], 0)
      assert.ok(!html.includes('<img'), 'в выводе не должно быть сырого <img: ' + html)
    })

    test('ни один тег в выводе не несёт onerror (payload не стал живой разметкой)', () => {
      const html = formatText([set(PAYLOAD)], 0)
      assert.ok(!/<[^>]*onerror/.test(html), 'в выводе не должно быть тега с onerror: ' + html)
    })

    test('вместо сырого тега — экранированная сущность', () => {
      const html = formatText([set(PAYLOAD)], 0)
      assert.ok(html.includes('&lt;img'), 'ожидается экранированная сущность &lt;img: ' + html)
    })

    test('регресс-контроль: обычный текст не экранируется', () => {
      const html = formatText([syl('раз'), set('COMMENT| жест'), syl('два')], 0)
      assert.ok(html.includes('>Два<'), 'обычный текст должен остаться как есть: ' + html)
      assert.ok(!html.includes('&lt;'), 'обычный текст не должен содержать сущности: ' + html)
      assert.ok(!html.includes('&amp;'), 'обычный текст не должен содержать сущности: ' + html)
    })
  },
)
