// BR-298 / BR-325 (аудит #336, пакет PKG-S3-27, OpenProject #399): статусные карты.
//
// BR-298 — ЧТО БЫЛО. `ChatUnreadBadge` наблюдал только за `isLoggedIn`, а премиум-статус
// читается из `user.effectivePremium` (обновляется фоновым fetchMe раз в 5 минут). Пользователь,
// ставший премиумом в открытой вкладке, не получал бейдж до полной перезагрузки; при истечении
// подписки polling продолжался каждые 20 секунд впустую.
//
// BR-325 — ЧТО БЫЛО. `AuthorPlaylistView.statusOf(songId)` искал песню линейным `find` по всему
// массиву, а вызывался из шаблона на каждую строку 4 раза + в hasPlayable/playableIds — O(n²)
// на каждый рендер (n ≈ 2500 песен крупного автора).
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import {
  CHAT_POLL_DECISION,
  SONG_STATUS,
  buildSongStatusMap,
  chatPollDecision,
  lockIconFor,
  lockTitleFor,
  playableSongIds,
  songStatusOf,
} from '../statusMap.js'
import { readCode } from './sourceContract.js'

describe('BR-298: polling чата зависит от премиума', () => {
  test('премиум — polling включить', () => {
    assert.equal(chatPollDecision(true), CHAT_POLL_DECISION.START)
  })

  test('не премиум — polling выключить (иначе запросы впустую)', () => {
    assert.equal(chatPollDecision(false), CHAT_POLL_DECISION.STOP)
  })
})

describe('BR-325: карта статусов вместо линейного поиска', () => {
  const songs = [
    { songId: 1, contentReady: true, freelyAvailableNow: true },
    { songId: 2, contentReady: true },
    { songId: 3, contentReady: false },
  ]

  test('не премиум, без подписок: свободная — available, платная — premium, неготовая — notready', () => {
    const map = buildSongStatusMap(songs, false, null)
    assert.equal(songStatusOf(map, 1), SONG_STATUS.AVAILABLE)
    assert.equal(songStatusOf(map, 2), SONG_STATUS.PREMIUM)
    assert.equal(songStatusOf(map, 3), SONG_STATUS.NOT_READY)
  })

  test('премиум — всё готовое доступно', () => {
    const map = buildSongStatusMap(songs, true, null)
    assert.equal(songStatusOf(map, 2), SONG_STATUS.AVAILABLE)
    assert.equal(songStatusOf(map, 3), SONG_STATUS.NOT_READY)
  })

  test('подписка на песню даёт available', () => {
    const map = buildSongStatusMap(songs, false, new Set([2]))
    assert.equal(songStatusOf(map, 2), SONG_STATUS.AVAILABLE)
  })

  test('песни нет в карте — loading', () => {
    const map = buildSongStatusMap(songs, false, null)
    assert.equal(songStatusOf(map, 999), SONG_STATUS.LOADING)
  })

  test('карта строится ОДИН раз: 2500 песен — 2500 записей, а не 2500 find() на строку', () => {
    const many = Array.from({ length: 2500 }, (_, i) => ({
      songId: i + 1,
      contentReady: true,
      freelyAvailableNow: i % 2 === 0,
    }))
    const map = buildSongStatusMap(many, false, null)
    assert.equal(map.size, 2500)
    // Поиск по карте — O(1) на песню.
    assert.equal(songStatusOf(map, 2500), SONG_STATUS.PREMIUM)
  })

  test('playableSongIds собирает доступные из той же карты', () => {
    const map = buildSongStatusMap(songs, false, null)
    assert.deepEqual(playableSongIds(songs, map), [1])
  })

  test('иконки и подписи соответствуют статусу', () => {
    assert.equal(lockIconFor(SONG_STATUS.AVAILABLE), '♪')
    assert.equal(lockIconFor(SONG_STATUS.PREMIUM), '🪙')
    assert.equal(lockIconFor(SONG_STATUS.NOT_READY), '🔒')
    assert.equal(lockIconFor(SONG_STATUS.LOADING), '…')
    assert.equal(lockTitleFor(SONG_STATUS.PREMIUM), 'Доступно премиум-пользователям')
    assert.equal(lockTitleFor(SONG_STATUS.NOT_READY), 'Песня ещё готовится')
  })
})

describe('BR-298 / BR-325: контракт исходников (падает на старом коде)', () => {
  test('ChatUnreadBadge наблюдает за isPremium, а не только за isLoggedIn', () => {
    const src = readCode('src/components/ChatUnreadBadge.vue')
    assert.ok(src.includes('chatPollDecision'), 'нужна проверка решения по премиуму')
    assert.ok(
      /isPremium: \{[\s\S]{0,300}handler\(premium\)/.test(src),
      'watch должен быть на isPremium (BR-298)',
    )
    assert.ok(
      !/isLoggedIn: \{[\s\S]{0,300}handler\(loggedIn\)/.test(src),
      'старый watch только на isLoggedIn — источник бага',
    )
  })

  test('AuthorPlaylistView использует карту статусов, а не find()', () => {
    const src = readCode('src/views/AuthorPlaylistView.vue')
    assert.ok(src.includes('buildSongStatusMap'), 'нужна карта статусов')
    assert.ok(src.includes('songStatusOf'), 'чтение статуса — O(1) из карты')
    assert.ok(
      !/songs\.value\.find\(\(x\) => x\.songId === songId\)/.test(src),
      'линейный find по массиву — старый источник O(n²)',
    )
    assert.ok(src.includes('playableSongIds'), 'playableIds должен собираться из карты')
  })
})
