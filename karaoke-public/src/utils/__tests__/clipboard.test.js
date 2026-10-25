// BR-296 (аудит #336, пакет PKG-S3-27, OpenProject #399): копирование в буфер обмена.
//
// ЧТО БЫЛО. `copyToClipboard()` (ShareButton.vue и зеркало в ShareLinkModal.vue) ничего
// не возвращала. `document.execCommand('copy')` возвращает `false` (команда запрещена/
// не поддерживается) или бросает исключение, а `copied = true` выставлялся БЕЗУСЛОВНО.
// UI писал «Ссылка скопирована», хотя в буфере ничего не было.
//
// ЧТО СТАЛО. Копирование возвращает `true` только если текст реально попал в буфер:
// `navigator.clipboard.writeText()` разрешился ИЛИ `document.execCommand('copy')` вернул
// `true`. Иначе — `false`, и UI показывает «Скопируйте вручную».
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { copyTextToClipboard } from '../clipboard.js'
import { readSource, sourceHas, sourceLacks } from './sourceContract.js'

/** Минимальный DOM для fallback-ветки. */
function fakeDocument({ execResult = true, execThrows = false } = {}) {
  const created = []
  const body = {
    appendChild(node) {
      created.push(node)
      node.parentNode = body
    },
    removeChild(node) {
      node.parentNode = null
    },
  }
  return {
    body,
    created,
    execCommand() {
      if (execThrows) throw new Error('NotAllowedError')
      return execResult
    },
    createElement() {
      return { value: '', style: {}, select() {} }
    },
  }
}

describe('BR-296: результат копирования проверяется', () => {
  test('navigator.clipboard успешно записал — true', async () => {
    const doc = fakeDocument()
    const ok = await copyTextToClipboard('https://example.test/s', {
      navigator: { clipboard: { writeText: async () => {} } },
      document: doc,
    })
    assert.equal(ok, true)
  })

  test('clipboard API отсутствует, execCommand вернул true — true', async () => {
    const doc = fakeDocument({ execResult: true })
    const ok = await copyTextToClipboard('https://example.test/s', {
      navigator: {},
      document: doc,
    })
    assert.equal(ok, true)
  })

  test('clipboard API отклонил, execCommand вернул FALSE — false (буфер пуст)', async () => {
    const doc = fakeDocument({ execResult: false })
    const ok = await copyTextToClipboard('https://example.test/s', {
      navigator: {
        clipboard: {
          writeText: async () => {
            throw new Error('denied')
          },
        },
      },
      document: doc,
    })
    assert.equal(
      ok,
      false,
      'execCommand вернул false — текст НЕ скопирован, UI не должен говорить «Скопировано»',
    )
  })

  test('clipboard API отклонил, execCommand БРОСИЛ — false', async () => {
    const doc = fakeDocument({ execThrows: true })
    const ok = await copyTextToClipboard('https://example.test/s', {
      navigator: {
        clipboard: {
          writeText: async () => {
            throw new Error('denied')
          },
        },
      },
      document: doc,
    })
    assert.equal(ok, false)
  })

  test('нет ни clipboard API, ни execCommand — false (без исключения)', async () => {
    const ok = await copyTextToClipboard('https://example.test/s', {
      navigator: {},
      document: { body: {} },
    })
    assert.equal(ok, false)
  })

  test('пустой текст — false, ничего не копируем', async () => {
    const doc = fakeDocument()
    const ok = await copyTextToClipboard('', { navigator: {}, document: doc })
    assert.equal(ok, false)
    assert.equal(doc.created.length, 0, 'textarea не должна создаваться для пустого текста')
  })

  test('textarea убирается из DOM в любом случае', async () => {
    const doc = fakeDocument({ execResult: false })
    await copyTextToClipboard('x', { navigator: {}, document: doc })
    assert.equal(doc.created.length, 1)
    assert.equal(doc.created[0].parentNode, null, 'textarea обязана быть удалена из DOM')
  })
})

describe('BR-296: контракт исходников (падает на старом коде)', () => {
  test('ShareButton.vue использует общий хелпер и НЕ ставит copied безусловно', () => {
    const src = readSource('src/components/ShareButton.vue')
    assert.ok(
      src.includes('copyTextToClipboard'),
      'ShareButton должен копировать через utils/clipboard.js (результат проверяется)',
    )
    const guardAt = src.indexOf('if (!ok)')
    assert.ok(guardAt >= 0, 'должна быть проверка результата копирования (if (!ok))')
    assert.ok(
      /if \(!ok\) \{[\s\S]*?this\.copied = true/.test(src),
      'copied выставляется только ПОСЛЕ проверки результата — иначе это старый баг',
    )
    assert.ok(
      src.includes('copyFailed'),
      'при неудачном копировании нужен явный признак «Скопируйте вручную»',
    )
  })

  test('ShareLinkModal.vue (зеркало) — copied только при успехе', () => {
    const src = readSource('src/components/ShareLinkModal.vue')
    assert.ok(
      src.includes('copyTextToClipboard'),
      'ShareLinkModal должен использовать общий хелпер копирования',
    )
    assert.ok(
      !/await copyToClipboard\(body\.url\)\s*\n\s*copied\.value = true/.test(src),
      'copied.value = true сразу после await без проверки результата — старый баг',
    )
  })

  test('старый ShareButton без проверки результата больше не использует execCommand напрямую', () => {
    assert.ok(
      sourceLacks('src/components/ShareButton.vue', "document.execCommand('copy')"),
      'ShareButton не должен вызывать execCommand напрямую — только через хелпер',
    )
    assert.ok(
      sourceHas('src/components/ShareLinkModal.vue', "document.execCommand('copy')") === false,
      'ShareLinkModal не должен вызывать execCommand напрямую — только через хелпер',
    )
  })
})
