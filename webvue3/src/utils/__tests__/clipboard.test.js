// Контракт копирования в буфер обмена (спека #558, OpenProject #360).
//
// До: админка вызывала `navigator.clipboard.writeText` напрямую. Clipboard API
// требует secure context и может быть отклонён браузером — тогда вызов бросал
// TypeError, кнопка молча не работала, пользователь не получал ни копирования,
// ни сообщения. В легаси-странице песен для этого был запасной путь
// (`copyToClipboardSafely` + `execCommand`); при удалении страницы запасной путь
// в SPA не переехал.
//
// После: единый хелпер `copyToClipboardSafely` — сначала Clipboard API, при
// недоступности или отказе — `execCommand('copy')`; возвращает признак успеха,
// чтобы вызывающий мог показать текст для ручного копирования.
//
// Проверяем все четыре ветки:
//   1. Clipboard API доступен и работает → копирование выполнено, запасной путь
//      не понадобился;
//   2. Clipboard API недоступен (нет navigator.clipboard) → сработал запасной путь;
//   3. Clipboard API есть, но отклонён (отказ по правам) → сработал запасной путь;
//   4. не сработал ни один путь → возвращается false (вызывающий показывает текст).

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { copyToClipboardSafely, copyWithExecCommand } from '../clipboard.js'

/** Минимальный документ для запасного пути. */
function fakeDocument({ execCommandResult = true } = {}) {
  const created = []
  // Журнал созданий — не тот же массив, что и список «живых» узлов: textarea
  // создаётся и сразу удаляется, и по «живым» узлам это не видно.
  const createdLog = []
  return {
    created,
    createdLog,
    body: {
      appendChild(node) {
        created.push(node)
        createdLog.push(node)
      },
      removeChild(node) {
        const index = created.indexOf(node)
        if (index >= 0) created.splice(index, 1)
      },
    },
    createElement(tag) {
      return {
        tag,
        value: '',
        style: {},
        attributes: {},
        setAttribute(name, value) {
          this.attributes[name] = value
        },
        select() {
          this.selected = true
        },
      }
    },
    execCommand(command) {
      this.lastCommand = command
      return execCommandResult
    },
  }
}

describe('copyToClipboardSafely — копирование с запасным путём', () => {
  test('Clipboard API работает — копирование выполнено, запасной путь не тронут', async () => {
    const doc = fakeDocument()
    let written = null
    const nav = {
      clipboard: {
        writeText: async (text) => {
          written = text
        },
      },
    }

    const ok = await copyToClipboardSafely('Иванов', { navigator: nav, document: doc })

    assert.equal(ok, true, 'копирование должно пройти')
    assert.equal(written, 'Иванов', 'в буфер должно уйти переданное значение')
    assert.equal(doc.created.length, 0, 'запасной путь не должен создавать textarea')
  })

  test('Clipboard API недоступен — срабатывает запасной путь', async () => {
    const doc = fakeDocument()
    const nav = {}

    const ok = await copyToClipboardSafely('Песня', { navigator: nav, document: doc })

    assert.equal(ok, true, 'копирование должно пройти через запасной путь')
    assert.equal(doc.createdLog.length, 1, 'запасной путь должен создавать textarea')
    assert.equal(doc.createdLog[0].value, 'Песня', 'в textarea должно быть значение')
    assert.equal(doc.lastCommand, 'copy', 'должен выполняться execCommand copy')
    assert.equal(doc.created.length, 0, 'textarea после копирования должна убираться')
  })

  test('Clipboard API отклонён — срабатывает запасной путь', async () => {
    const doc = fakeDocument()
    const nav = {
      clipboard: {
        writeText: async () => {
          throw new Error('NotAllowedError')
        },
      },
    }

    const ok = await copyToClipboardSafely('Текст', { navigator: nav, document: doc })

    assert.equal(ok, true, 'копирование должно пройти через запасной путь')
    assert.equal(doc.lastCommand, 'copy', 'должен выполняться execCommand copy')
  })

  test('оба пути не сработали — возвращается false (текст показывают вручную)', async () => {
    const doc = fakeDocument({ execCommandResult: false })
    const nav = {}

    const ok = await copyToClipboardSafely('Текст', { navigator: nav, document: doc })

    assert.equal(ok, false, 'вызывающий должен узнать, что копирование не вышло')
  })

  test('без документа запасной путь не падает, а возвращает false', () => {
    const ok = copyWithExecCommand('Текст', undefined)
    assert.equal(ok, false, 'без DOM копирование невозможно — это не ошибка')
  })
})
