// BR-361 / BR-362 (аудит #336, пакет PKG-S3-28, OpenProject #400): задания стемов.
//
// ЧТО БЫЛО. Таймер опроса создавался ПОСЛЕ `await load()` — на уже размонтированном
// компоненте (утечка фонового опроса), а удалённая карточка возвращалась в список при
// ближайшем поллинге (сервер чистит её раз в 5 минут).
//
// ЧТО СТАЛО. Таймер — на инстансе и создаётся синхронно; задания с `deleteRequested`
// в список не возвращаются.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { visibleJobs, hasActiveJobs } from '../stemJobs.js'
import { readCode } from './sourceContract.js'

describe('BR-362: удалённое задание не возвращается в список', () => {
  test('задание с deleteRequested отфильтровывается', () => {
    const jobs = [
      { id: 1, status: 'WAITING' },
      { id: 2, status: 'DONE', deleteRequested: true },
    ]
    assert.deepEqual(
      visibleJobs(jobs).map((j) => j.id),
      [1],
    )
  })

  test('исходный массив не мутируется', () => {
    const jobs = [{ id: 1, deleteRequested: true }]
    visibleJobs(jobs)
    assert.equal(jobs.length, 1)
  })

  test('пустой/не-массив — пустой список', () => {
    assert.deepEqual(visibleJobs(null), [])
    assert.deepEqual(visibleJobs(undefined), [])
  })
})

describe('BR-361: активные задания', () => {
  test('WAITING/WORKING считаются активными', () => {
    assert.equal(hasActiveJobs([{ status: 'WAITING' }]), true)
    assert.equal(hasActiveJobs([{ status: 'WORKING' }]), true)
    assert.equal(hasActiveJobs([{ status: 'DONE' }]), false)
    assert.equal(hasActiveJobs([]), false)
  })
})

describe('BR-361/BR-362: контракт исходников (падает на старом коде)', () => {
  test('таймер опроса — на инстансе и создаётся до await', () => {
    const src = readCode('src/views/StemJobsView.vue')
    assert.ok(!/^let pollTimer/m.test(src), 'ручка таймера не должна быть на уровне модуля')
    assert.ok(src.includes('this.pollTimer'), 'таймер должен жить на инстансе')
    assert.ok(src.includes('clearInterval'), 'таймер должен сниматься при размонтировании')

    const mountedIdx = src.indexOf('async mounted()')
    const timerIdx = src.indexOf('this.pollTimer = setInterval')
    const loadIdx = src.indexOf('await this.load()')
    assert.ok(mountedIdx >= 0 && timerIdx > mountedIdx, 'таймер создаётся в mounted()')
    assert.ok(timerIdx < loadIdx, 'таймер создаётся ДО await this.load()')
  })

  test('load() отфильтровывает удалённые задания', () => {
    const src = readCode('src/views/StemJobsView.vue')
    assert.ok(src.includes('visibleJobs'), 'нужна фильтрация deleteRequested')
  })
})
