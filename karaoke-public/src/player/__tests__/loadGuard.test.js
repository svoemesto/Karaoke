// BR-302 / BR-303 (аудит #336, пакет PKG-S2-14, OpenProject #377): регрессионные
// тесты на инварианты жизненного цикла загрузки плеера.
//
// BR-302 — ЧТО БЫЛО. Проверки отмены после `await` читали ОБЩЕЕ поле
// `this._activeAbortController`, а не контроллер своей загрузки. Если за время
// init() стартовал playSong() следующего трека, он делал _abortActive() и создавал
// свой контроллер: старая init() видела ЧУЖОЙ (не отменённый) сигнал и продолжала
// работу, перезаписывая state нового трека; либо падала на `null.signal`.
//
// BR-303 — ЧТО БЫЛО. Терминальный `release` share-сессии отправлялся на любой
// `visibilitychange → hidden` (переключение вкладки, сворачивание окна, блокировка
// экрана телефона). Бэкенд обнулял active_session_token_hash, heartbeat отвечал 410,
// гость видел «Время сеанса истекло…», хотя ссылка ещё валидна.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { isStaleLoad, resolveShareReleaseReason } from '../loadGuard.js'

function controller(aborted = false) {
  return { signal: { aborted } }
}

describe('BR-302: устаревшая загрузка не пишет в state', () => {
  test('свой и тот же контроллер — загрузка актуальна', () => {
    const mine = controller()
    assert.equal(isStaleLoad(mine, mine), false)
  })

  test('поле указывает на контроллер СЛЕДУЮЩЕГО трека — загрузка устарела', () => {
    const mine = controller()
    const next = controller()
    assert.equal(isStaleLoad(next, mine), true)
  })

  test('свой контроллер отменён — загрузка устарела', () => {
    const mine = controller(true)
    assert.equal(isStaleLoad(mine, mine), true)
  })

  test('поле пусто (abort обнулил его) — fail-closed, устарела', () => {
    assert.equal(isStaleLoad(null, controller()), true)
  })

  test('нет контроллера своей загрузки — fail-closed, устарела', () => {
    assert.equal(isStaleLoad(controller(), null), true)
  })
})

describe('BR-303: терминальный release только при реальном уходе со страницы', () => {
  test('pagehide — страница уходит, release допустим', () => {
    assert.equal(resolveShareReleaseReason('pagehide'), 'closed')
  })

  test('beforeunload — страница уходит, release допустим', () => {
    assert.equal(resolveShareReleaseReason('beforeunload'), 'closed')
  })

  test('visibilitychange (переключение вкладки/сворачивание) — release НЕЛЬЗЯ', () => {
    assert.equal(
      resolveShareReleaseReason('visibilitychange'),
      null,
      'release на visibilitychange обнуляет серверную сессию у ещё валидной ссылки',
    )
  })

  test('неизвестное событие — fail-closed, release НЕЛЬЗЯ', () => {
    assert.equal(resolveShareReleaseReason('blur'), null)
  })
})
