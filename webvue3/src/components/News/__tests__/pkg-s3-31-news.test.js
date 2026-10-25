// Регрессионные тесты пакета PKG-S3-31 (аудит #336, OpenProject #403) — новости.
//
// Записи пакета в этом файле:
//   BR-389 — поле «Строк на странице» для новостей не работало.
//   BR-392 — ошибка загрузки новостей проглатывалась.
//   BR-393 — пагинация новостей: выигрывал последний ПРИШЕДШИЙ ответ.
//
// ДО ПРАВКИ (BR-389): `perPage` — computed без setter'а, а `this.perPage = ...` был
// записью в readonly-computed (значение не менялось). `loadNews(ctx)` игнорировал
// payload, и сервер всегда получал `pageSize=35`. Плюс `this.currentPage = 1` писало
// в несуществующее поле (в компоненте есть только computed `currentPageModel`).
//
// ДО ПРАВКИ (BR-392): `catch` только логировал — админ видел «новостей нет» вместо отказа.
//
// ДО ПРАВКИ (BR-393): любой ответ безусловно коммитил `setNewsList` — при быстрых
// кликах по страницам список страницы 3 показывался под номером страницы 2.
//
// Запуск: cd webvue3 && node --test src/components/News/__tests__/pkg-s3-31-news.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const NEWS_STORE = stripComments(readFileSync(new URL('../store.js', import.meta.url), 'utf-8'))
const NEWS_TABLE = stripComments(
  readFileSync(new URL('../NewsTable.vue', import.meta.url), 'utf-8'),
)

/**
 * Достаёт тело мутации/экшена/метода по имени.
 *
 * @param {string} source — исходник.
 * @param {string} name — имя блока.
 * @returns {string|null} тело блока либо null.
 */
const memberBody = (source, name) => {
  const definition = new RegExp('\\n\\s{2,6}(?:async\\s+)?' + name + '\\s*\\(').exec(source)
  if (!definition) return null
  let open = source.indexOf('(', definition.index + definition[0].length - 1)
  let depth = 0
  for (let i = open; i < source.length; i++) {
    if (source[i] === '(') depth++
    else if (source[i] === ')') {
      depth--
      if (depth === 0) {
        open = i + 1
        break
      }
    }
  }
  const brace = source.indexOf('{', open)
  if (brace < 0) return null
  depth = 0
  for (let i = brace; i < source.length; i++) {
    if (source[i] === '{') depth++
    else if (source[i] === '}') {
      depth--
      if (depth === 0) return source.slice(brace + 1, i)
    }
  }
  return null
}

describe('PKG-S3-31 / Новости', () => {
  describe('BR-389 — «Строк на странице» работает', () => {
    test('perPage компонента берётся из общей настройки таблиц', () => {
      const perPage = /perPage\(\)\s*\{[\s\S]*?\n    \},/.exec(NEWS_TABLE)
      assert.ok(perPage, 'BR-389: не найден computed perPage')
      assert.match(
        perPage[0],
        /getRowsPerPage\('news'\)/,
        'BR-389: размер страницы обязан браться из tableSettings',
      )
    })

    test('в компоненте нет записи в readonly-computed perPage', () => {
      assert.doesNotMatch(
        NEWS_TABLE,
        /this\.perPage = /,
        'BR-389: запись в readonly-computed не меняла значение',
      )
    })

    test('страница сбрасывается через currentPageModel, а не через несуществующее поле', () => {
      assert.match(
        NEWS_TABLE,
        /this\.currentPageModel = 1/,
        'BR-389: сброс страницы должен идти через computed-сеттер',
      )
      assert.doesNotMatch(
        NEWS_TABLE,
        /this\.currentPage = 1/,
        'BR-389: поля currentPage в компоненте нет',
      )
    })

    test('размер страницы синхронизируется со стором и уходит в запрос', () => {
      assert.match(NEWS_STORE, /setNewsPerPage\(ctx, perPage\)/)
      const loadNews = memberBody(NEWS_STORE, 'loadNews')
      assert.ok(loadNews, 'BR-389: не найден action loadNews')
      assert.match(loadNews, /payload && payload\.perPage/, 'BR-389: perPage должен учитываться')
      assert.match(loadNews, /payload && payload\.page/, 'BR-389: page должен учитываться')
      assert.match(loadNews, /pageSize: requestedPerPage/, 'BR-389: pageSize уходит на сервер')
    })
  })

  describe('BR-392 — ошибка загрузки новостей видима', () => {
    test('в сторе есть состояние ошибки, которое заполняется в catch', () => {
      assert.match(NEWS_STORE, /newsListError: ''/)
      assert.match(NEWS_STORE, /getNewsListError\(state\)/)
      const loadNews = memberBody(NEWS_STORE, 'loadNews')
      assert.ok(loadNews, 'BR-392: не найден action loadNews')
      assert.match(loadNews, /setNewsListError/, 'BR-392: ошибка обязана попадать в state')
    })
  })

  describe('BR-393 — пагинация: побеждает последний запрос', () => {
    test('у загрузки есть поколение запроса, ответ применяет только владелец', () => {
      assert.match(NEWS_STORE, /newsListGeneration/)
      const loadNews = memberBody(NEWS_STORE, 'loadNews')
      assert.ok(loadNews, 'BR-393: не найден action loadNews')
      assert.match(
        loadNews,
        /newsListGeneration\.isCurrent\(ticket\)/,
        'BR-393: устаревший ответ не должен применяться',
      )
      assert.match(loadNews, /finally/, 'BR-393: флаг загрузки снимается при любом исходе')
    })
  })
})
