// BR-322 / BR-323 / BR-318 / BR-334 / BR-341 / BR-343 / BR-346
// (аудит #336, пакет PKG-S3-27, OpenProject #399): «пусто» ≠ «ошибка».
//
// ЧТО БЫЛО. Загрузчики в views/store не различали сбой от пустого результата:
//   - BR-322 `HistoryView.reload()` — ни catch, ни ветки на status !== 200;
//   - BR-323 `stats.loadStats` — catch отсутствует, на главной рисуются нули;
//   - BR-318 `usePlaylistMembership.loadPlaylists()` — единственная из трёх bulk-загрузок без catch;
//   - BR-334 `EditorWorkView.load()` — reject оставлял `loading = true` навсегда;
//   - BR-341 `PlaylistEditView.load()` — 403/5xx трактовались как «Плейлист не найден»;
//   - BR-343 `NewsView` — пустой catch превращал 500/503 в «Пока новостей нет»;
//   - BR-346 `PremiumView` — ошибка расчёта цены вообще не показывалась.
//
// ЧТО СТАЛО. Единая классификация: READY / EMPTY / NOT_FOUND / UNAUTHORIZED / ERROR.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import {
  LOAD_STATE,
  classifyLoad,
  loadErrorCode,
  loadErrorMessage,
} from '../loadState.js'
import { readSource } from './sourceContract.js'

const isEmptyArray = (b) => !b || !Array.isArray(b.items) || b.items.length === 0

describe('BR-322 / BR-323 / BR-334 / BR-341 / BR-343: классификация ответа', () => {
  test('200 с данными — ready', () => {
    const out = classifyLoad(200, { items: [1] }, { isEmpty: isEmptyArray })
    assert.equal(out.state, LOAD_STATE.READY)
    assert.equal(out.message, '')
  })

  test('200 без данных — empty (НЕ ошибка)', () => {
    const out = classifyLoad(200, { items: [] }, { isEmpty: isEmptyArray })
    assert.equal(out.state, LOAD_STATE.EMPTY)
    assert.equal(out.message, '')
  })

  test('404 — not_found', () => {
    const out = classifyLoad(404, null)
    assert.equal(out.state, LOAD_STATE.NOT_FOUND)
    assert.ok(out.message.length > 0)
  })

  test('401/403 — unauthorized («войдите заново»), а НЕ «не найдено»', () => {
    assert.equal(classifyLoad(401, null).state, LOAD_STATE.UNAUTHORIZED)
    assert.equal(classifyLoad(403, null).state, LOAD_STATE.UNAUTHORIZED)
  })

  test('500 — error с текстом, а не «пусто»', () => {
    const out = classifyLoad(500, null)
    assert.equal(out.state, LOAD_STATE.ERROR)
    assert.ok(out.message.length > 0)
  })

  test('не-200 не считается «пусто» ни при каких условиях', () => {
    for (const status of [401, 403, 404, 500, 502, 503]) {
      const out = classifyLoad(status, { items: [] }, { isEmpty: isEmptyArray })
      assert.notEqual(out.state, LOAD_STATE.EMPTY, `статус ${status} не должен давать EMPTY`)
    }
  })

  test('loadErrorMessage даёт текст для каждого состояния-ошибки', () => {
    assert.ok(loadErrorMessage(LOAD_STATE.ERROR).length > 0)
    assert.ok(loadErrorMessage(LOAD_STATE.NOT_FOUND).length > 0)
    assert.ok(loadErrorMessage(LOAD_STATE.UNAUTHORIZED).length > 0)
  })

  test('loadErrorCode достаёт код из тела (error / errorCode)', () => {
    assert.equal(loadErrorCode({ error: 'no_tariff' }), 'no_tariff')
    assert.equal(loadErrorCode({ errorCode: 'share.notOwner' }), 'share.notOwner')
    assert.equal(loadErrorCode(null), '')
    assert.equal(loadErrorCode({ error: 42 }), '')
  })
})

describe('BR-322 / BR-323 / BR-318 / BR-334 / BR-341 / BR-343 / BR-346: контракт исходников', () => {
  test('HistoryView: try/catch + явный error', () => {
    const src = readSource('src/views/HistoryView.vue')
    assert.ok(src.includes('classifyLoad'), 'нужна классификация ответа')
    assert.ok(/catch\s*\(e\)\s*\{[\s\S]{0,200}this\.error\s*=/.test(src), 'нужен catch с error')
    assert.ok(src.includes('error:'), 'нужно состояние error в data()')
  })

  test('stats store: loaded/error и никаких нулей при сбое', () => {
    const src = readSource('src/store/modules/stats.js')
    assert.ok(src.includes('loaded:'), 'нужно состояние loaded')
    assert.ok(src.includes('setError'), 'нужно состояние error')
    assert.ok(
      /catch\s*\(e\)\s*\{[\s\S]{0,200}commit\('setError'/.test(src),
      'ошибка загрузки статистики не должна глотаться',
    )
  })

  test('HomeView показывает «недоступно» вместо нулей', () => {
    const src = readSource('src/views/HomeView.vue')
    assert.ok(src.includes('statsError'), 'нужно отличать ошибку от данных')
    assert.ok(
      /v-if="statsError"/.test(src),
      'блок статистики должен иметь ветку ошибки',
    )
  })

  test('usePlaylistMembership: try/catch вокруг fetchPlaylists', () => {
    const src = readSource('src/composables/usePlaylistMembership.js')
    const fn = src.slice(src.indexOf('async function loadPlaylists'), src.indexOf('// ---- BroadcastChannel'))
    assert.ok(
      /try\s*\{[\s\S]*fetchPlaylists\(\)[\s\S]*\}\s*catch/.test(fn),
      'loadPlaylists должен ловить сетевой reject (BR-318)',
    )
  })

  test('EditorWorkView: try/catch/finally вокруг fetchTask', () => {
    const src = readSource('src/views/EditorWorkView.vue')
    const fn = src.slice(src.indexOf('async _loadTask()'), src.indexOf('async initWaveSurfer()'))
    assert.ok(
      /try\s*\{[\s\S]*fetchTask\([\s\S]*\}\s*catch/.test(fn),
      'fetchTask должен быть в try/catch (BR-334)',
    )
    assert.ok(/finally\s*\{[\s\S]{0,120}this\.loading = false/.test(fn), 'loading сбрасывается в finally')
  })

  test('PlaylistEditView: notFound только на 404', () => {
    const src = readSource('src/views/PlaylistEditView.vue')
    assert.ok(src.includes('classifyLoad'), 'нужна классификация ответа')
    const fn = src.slice(src.indexOf('async function load()'), src.indexOf('// Упорядоченный список'))
    assert.ok(
      /notFoundStatuses: \[404\]/.test(fn),
      'notFound ставится только на 404 (BR-341)',
    )
    assert.ok(
      /unauthorizedStatuses: \[401, 403\]/.test(fn),
      '401/403 — «войдите заново», а не «не найдено»',
    )
  })

  test('NewsView: отдельный флаг ошибки, markAllSeen только при успехе', () => {
    const src = readSource('src/views/NewsView.vue')
    assert.ok(src.includes('loadError'), 'нужен отдельный флаг ошибки')
    const fn = src.slice(src.indexOf('async reload()'), src.indexOf('// Ответ {items, total, hasMore}'))
    assert.ok(
      /applyPage\(data, true\)[\s\S]{0,80}this\.markAllSeen\(\)[\s\S]{0,200}catch/.test(fn),
      'markAllSeen вызывается только после успешной загрузки (BR-343)',
    )
  })

  test('PremiumView: ошибка цены показывается с повтором', () => {
    const src = readSource('src/views/PremiumView.vue')
    assert.ok(src.includes('priceError'), 'нужна отдельная ошибка расчёта цены')
    assert.ok(src.includes('retryPrice'), 'нужен повтор расчёта цены')
    assert.ok(
      /v-else-if="priceError"/.test(src),
      'ветка ошибки цены должна быть в шаблоне (BR-346)',
    )
  })
})
