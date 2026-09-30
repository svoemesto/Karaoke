// Контракт подсветки слов с неоднозначной «ё» (SubsEdit.vue, слой исходного текста).
//
// Проверяем то, что ломается тихо и не видно на глаз: экранирование пользовательского
// ввода (текст песни уходит в innerHTML), границы слов (кириллица, где \b не работает),
// и взаимозаменяемость «е»/«ё».

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { escapeHtml, buildHighlightRegex, highlightWords } from '../highlightWords.js'

const DICT = ['всё', 'весь', 'её', 'ещё', 'жёлтый', 'счёт']

describe('escapeHtml', () => {
  test('экранирует всё, что может стать разметкой', () => {
    assert.equal(escapeHtml('<b>все</b>'), '&lt;b&gt;все&lt;/b&gt;')
    assert.equal(escapeHtml('a & b'), 'a &amp; b')
    assert.equal(escapeHtml('"всё"'), '&quot;всё&quot;')
    assert.equal(escapeHtml("it's"), 'it&#39;s')
  })

  test('null/undefined не роняют', () => {
    assert.equal(escapeHtml(null), '')
    assert.equal(escapeHtml(undefined), '')
  })
})

describe('buildHighlightRegex', () => {
  test('пустой словарь даёт null', () => {
    assert.equal(buildHighlightRegex([]), null)
    assert.equal(buildHighlightRegex(null), null)
    assert.equal(buildHighlightRegex(['', '   ']), null)
  })

  test('«е» и «ё» взаимозаменяемы', () => {
    // ВАЖНО: не используем re.test() по кругу — с флагом 'g' регулярка
    // запоминает lastIndex между вызовами, и второй test() провалится
    // независимо от логики. В проде используется matchAll, который
    // исходную регулярку не трогает, поэтому там такой проблемы нет.
    const matches = (w, t) => {
      const re = buildHighlightRegex([w])
      return new RegExp(re.source, re.flags.replace('g', '')).test(t)
    }
    assert.ok(matches('всё', 'всё'))
    assert.ok(matches('всё', 'все'))
    assert.ok(matches('всё', 'ВСЁ'))
    assert.ok(matches('всё', 'ВСЕ'))
  })
})

describe('highlightWords', () => {
  test('подсвечивает оба написания одного слова', () => {
    const html = highlightWords('Все ушли домой, всё пропало', DICT)
    assert.equal(
      html,
      '<span class="se-hl-attention">Все</span> ушли домой, <span class="se-hl-attention">всё</span> пропало',
    )
  })

  test('НЕ режет слова посередине (границы слова)', () => {
    // «всем» не должен матчиться на «всё»/«всё». Проверяем, что внутри
    // более длинного слова подсветки нет.
    const html = highlightWords('всем привет', DICT)
    assert.ok(
      !html.includes('<span class="se-hl-attention"'),
      'внутри слова подсветки быть не должно: ' + html,
    )
  })

  test('учитывает кириллицу в границах, а не только латиницу', () => {
    const html = highlightWords('известность', DICT)
    assert.ok(!html.includes('<mark'), 'кириллический суффикс должен отсекать: ' + html)
  })

  test('латиница внутри кириллицы тоже считается частью слова', () => {
    // В данных проекта встречаются гомоглифы (словарь «Слова с Ё»).
    const html = highlightWords('всёx', DICT)
    assert.ok(
      !html.includes('<span class="se-hl-attention"'),
      'латинская буква справа должна отсекать: ' + html,
    )
  })

  test('регистр не важен', () => {
    assert.ok(highlightWords('ВСЁ', DICT).includes('<span class="se-hl-attention"'))
    assert.ok(highlightWords('всё', DICT).includes('<span class="se-hl-attention"'))
    assert.ok(highlightWords('Ещё', DICT).includes('<span class="se-hl-attention"'))
  })

  test('БЕЗОПАСНОСТЬ: текст песни экранируется и не может стать разметкой', () => {
    const html = highlightWords('<img src=x onerror=alert(1)> все', DICT)
    assert.ok(!html.includes('<img'), 'тег из текста не должен попасть в HTML: ' + html)
    assert.ok(html.includes('&lt;img'), 'должен быть экранирован: ' + html)
    assert.ok(
      html.includes('<span class="se-hl-attention">все</span>'),
      'подсветка при этом работает',
    )
  })

  test('совпадение, найденное ВНУТРИ тега, тоже экранируется', () => {
    const html = highlightWords('<b>все</b>', DICT)
    assert.ok(!html.includes('<b>'), 'открывающий тег экранирован')
    assert.ok(html.includes('&lt;b&gt;'))
  })

  test('переводы строк и пустые совпадения не ломают разметку', () => {
    const html = highlightWords('все\nвсё\r\nвсем', DICT)
    assert.equal(
      (html.match(/<span class="se-hl-attention">/g) || []).length,
      2,
      'совпадений ровно два',
    )
  })

  test('пустой словарь — просто экранированный текст, без подсветки', () => {
    assert.equal(highlightWords('все всё', []), 'все всё')
    assert.equal(highlightWords('<b>', null), '&lt;b&gt;')
  })

  test('пустой текст даёт пустую строку', () => {
    assert.equal(highlightWords('', DICT), '')
    assert.equal(highlightWords(null, DICT), '')
  })

  test('слово с дефисом из словаря матчится целиком', () => {
    assert.ok(highlightWords('подъём', ['подъём']).includes('<span class="se-hl-attention"'))
    assert.ok(
      highlightWords('подъем', ['подъём']).includes('<span class="se-hl-attention"'),
      '«е» вместо «ё»',
    )
    // «подъехал» — не вариант «подъём» (там х вместо м), подсветки быть не должно
    assert.ok(!highlightWords('подъехал', ['подъём']).includes('<span class="se-hl-attention"'))
    // «подъёмник» — ДРУГОЕ слово, и граница слова его отвергает: именно для
    // этого в WORD_CHAR входит и дефис, и кириллица. Если бы «подъём» матчился
    // как префикс, подсветка съедала бы хвост любого слова на «подъём».
    assert.ok(!highlightWords('подъёмник', ['подъём']).includes('<mark'))
    assert.ok(highlightWords('подъёмник', ['подъёмник']).includes('<span class="se-hl-attention"'))
  })
})
