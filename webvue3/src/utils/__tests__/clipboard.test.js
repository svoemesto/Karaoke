// Копирование в буфер обмена (админский SPA webvue3).
//
// Контракт тот же, что в публичном SPA (BR-296, karaoke-public/src/utils/clipboard.js):
// копирование возвращает `true` ТОЛЬКО если текст действительно попал в буфер —
// либо `navigator.clipboard.writeText()` разрешился, либо `document.execCommand('copy')`
// вернул `true`. Любой иной исход — `false`, и UI показывает «Скопируйте вручную».
//
// ДО. Админка вызывала `navigator.clipboard.writeText` напрямую: API доступен не
// везде (нужен secure context, возможен отказ по правам) — прямой вызов бросал
// TypeError, и кнопка «Скопировать» молча не работала. Запасной путь был только
// в удалённой легаси-странице песен.
//
// ПОСЛЕ. Все прямые вызовы в компонентах заменены на общий хелпер
// `copyTextToClipboard`; UI получает признак успеха и сообщает об отказе.
//
// Запуск: cd webvue3 && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

import { copyTextToClipboard } from '../clipboard.js'

// src/utils/__tests__/ → корень пакета webvue3
const HERE = path.dirname(fileURLToPath(import.meta.url))
const APP_ROOT = path.resolve(HERE, '..', '..', '..')

/** Прочитать исходник пакета. */
function readSource(relativePath) {
  return readFileSync(path.join(APP_ROOT, relativePath), 'utf8')
}

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

describe('копирование: результат проверяется', () => {
  test('navigator.clipboard успешно записал — true', async () => {
    const doc = fakeDocument()
    const ok = await copyTextToClipboard('Иванов', {
      navigator: { clipboard: { writeText: async () => {} } },
      document: doc,
    })
    assert.equal(ok, true)
  })

  test('clipboard API отсутствует, execCommand вернул true — true', async () => {
    const doc = fakeDocument({ execResult: true })
    const ok = await copyTextToClipboard('Иванов', {
      navigator: {},
      document: doc,
    })
    assert.equal(ok, true)
  })

  test('clipboard API отклонил, execCommand вернул FALSE — false (буфер пуст)', async () => {
    const doc = fakeDocument({ execResult: false })
    const ok = await copyTextToClipboard('Иванов', {
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
    const ok = await copyTextToClipboard('Иванов', {
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
    const ok = await copyTextToClipboard('Иванов', {
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

describe('контракт исходников: прямых вызовов Clipboard API быть не должно', () => {
  const components = [
    'src/components/Songs/edit/SongEdit.vue',
    'src/components/Pictures/edit/PictureEdit.vue',
    'src/components/Songs/edit/SearchText.vue',
    'src/components/Songs/edit/WhisperDebugModal.vue',
  ]

  test('в компонентах нет прямых вызовов navigator.clipboard.writeText', () => {
    for (const component of components) {
      const src = readSource(component)
      assert.ok(
        !src.includes('navigator.clipboard.writeText'),
        `${component}: прямой вызов Clipboard API без обработки — копирование молча не сработает`,
      )
    }
  })

  test('все компоненты копируют через общий хелпер', () => {
    for (const component of components) {
      const src = readSource(component)
      assert.ok(
        src.includes('copyTextToClipboard'),
        `${component}: копирование должно идти через utils/clipboard.js (результат проверяется)`,
      )
    }
  })

  test('SongEdit и PictureEdit сообщают пользователю о неудаче копирования', () => {
    for (const component of [
      'src/components/Songs/edit/SongEdit.vue',
      'src/components/Pictures/edit/PictureEdit.vue',
    ]) {
      const src = readSource(component)
      assert.ok(
        src.includes('!copied'),
        `${component}: результат копирования должен передаваться в тост (признак !copied)`,
      )
    }
  })
})
