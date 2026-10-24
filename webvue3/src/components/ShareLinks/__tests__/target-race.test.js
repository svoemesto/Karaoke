// BR-409 (аудит #336, пакет PKG-RACE-TARGET, OpenProject #367).
//
// Гонка: дайджест share-ссылок target-aware, но ответ применялся без сверки с target, а
// «Отозвать» подставлял ТЕКУЩИЙ target вместо target строки. При быстром переключении
// local → remote → local ответ старой БД мог прийти последним и остаться в таблице; отзыв брал
// id строки из чужой БД, а target — из селекта, и отзывал запись в другой базе.
//
// Тест воспроизводит перемежающийся порядок через шов FakeXhr.
//
// Запуск: cd webvue3 && npm run test:unit

import { beforeEach, describe, test } from 'node:test'
import assert from 'node:assert/strict'

import shareLinks from '../store.js'
import { FakeXhr } from '../../../test-utils/fakeXhr.js'

/** Минимальный Vuex-контекст: только state/commit/dispatch, без зависимости от vuex. */
function createCtx(module) {
  const state = structuredClone(module.state)
  const foreign = []
  const ctx = { state, getters: {} }
  ctx.commit = (type, payload) => {
    const mutation = module.mutations[type]
    assert.ok(mutation, `неизвестная мутация ${type}`)
    mutation(state, payload)
  }
  ctx.dispatch = (type, payload) => {
    const action = module.actions[type]
    if (action) return action(ctx, payload)
    foreign.push({ type, payload })
    return Promise.resolve({ success: true })
  }
  return { ctx, state, foreign }
}

const byTarget = (target) => (request) => request.params.target === target

const DIGEST_LOCAL = {
  shareLinksDigest: [{ id: 1, ownerEmail: 'local@example.test' }],
  totalCount: 1,
}
const DIGEST_REMOTE = {
  shareLinksDigest: [{ id: 2, ownerEmail: 'remote@example.test' }],
  totalCount: 1,
}

describe('BR-409: гонка цели (target) в дайджесте share-ссылок', () => {
  beforeEach(() => {
    FakeXhr.install()
  })

  test('ответ прежней БД, пришедший последним, не перезаписывает дайджест текущей', async () => {
    const { ctx, state } = createCtx(shareLinks)

    const localLoad = ctx.dispatch('loadShareLinksDigest', {})
    ctx.dispatch('setShareLinksTarget', 'remote')
    const remoteLoad = ctx.dispatch('loadShareLinksDigest', {})

    const localRequest = FakeXhr.last(byTarget('local'))
    const remoteRequest = FakeXhr.last(byTarget('remote'))

    remoteRequest.respond(200, DIGEST_REMOTE)
    await remoteLoad
    localRequest.respond(200, DIGEST_LOCAL)
    await localLoad

    assert.deepEqual(
      state.shareLinksDigest.map((row) => row.ownerEmail),
      ['remote@example.test'],
      'в таблице должны остаться строки выбранной БД',
    )
    assert.equal(state.shareLinksDigestTarget, 'remote')
    assert.equal(state.shareLinksDigestTotalCount, 1)
  })

  test('смена target очищает дайджест до прихода ответа новой БД', async () => {
    const { ctx, state } = createCtx(shareLinks)

    const firstLoad = ctx.dispatch('loadShareLinksDigest', {})
    FakeXhr.last(byTarget('local')).respond(200, DIGEST_LOCAL)
    await firstLoad
    assert.equal(state.shareLinksDigest.length, 1)

    ctx.dispatch('setShareLinksTarget', 'remote')

    assert.deepEqual(
      state.shareLinksDigest,
      [],
      'строки прежней БД не должны изображать данные новой',
    )
    assert.equal(state.shareLinksDigestTotalCount, 0)
    assert.equal(state.shareLinksDigestTarget, 'local', 'происхождение дайджеста не переписывается')
  })

  test('отзыв адресуется БД строки, даже если селект уже переключён', async () => {
    const { ctx, state, foreign } = createCtx(shareLinks)

    const load = ctx.dispatch('loadShareLinksDigest', {})
    FakeXhr.last(byTarget('local')).respond(200, DIGEST_LOCAL)
    await load
    assert.equal(state.shareLinksDigestTarget, 'local')

    // Админ переключил селект, но подтверждение отзыва открыто на строке прежней БД:
    // компонент фиксирует target строки и передаёт его явно.
    ctx.dispatch('setShareLinksTarget', 'remote')
    await ctx.dispatch('revokeShareLink', { shareLinkId: 1, reason: 'admin', target: 'local' })

    const revoke = foreign.find((call) => call.type === 'revokeSiteUserShareLink')
    assert.ok(revoke, 'отзыв должен уйти в существующий action shareLinkStore')
    assert.deepEqual(revoke.payload, { shareLinkId: 1, reason: 'admin', target: 'local' })
    assert.equal(state.shareLinksTarget, 'remote', 'селект при этом остаётся на выбранной БД')
  })

  test('без явного target отзыв использует БД показанного дайджеста', async () => {
    const { ctx, foreign } = createCtx(shareLinks)

    const load = ctx.dispatch('loadShareLinksDigest', {})
    FakeXhr.last(byTarget('local')).respond(200, DIGEST_LOCAL)
    await load

    ctx.dispatch('setShareLinksTarget', 'remote')
    await ctx.dispatch('revokeShareLink', { shareLinkId: 7 })

    const revoke = foreign.find((call) => call.type === 'revokeSiteUserShareLink')
    assert.equal(revoke.payload.target, 'local')
    assert.equal(revoke.payload.reason, 'admin')
  })

  test('пока дайджест не загружен, отзыв отклоняется, а не уходит в случайную БД', async () => {
    const { ctx, foreign } = createCtx(shareLinks)

    await assert.rejects(
      () => ctx.dispatch('revokeShareLink', { shareLinkId: 1 }),
      /Неизвестна БД строки/,
    )
    assert.equal(foreign.length, 0, 'запрос отзыва не должен уходить без target')
  })
})
