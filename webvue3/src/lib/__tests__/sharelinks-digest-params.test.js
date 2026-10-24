// Регрессионный тест на серверную пагинацию дайджеста share-ссылок (BR-410, аудит #336,
// пакет PKG-S2-17, OpenProject #380).
//
// ДО ПРАВКИ `ShareLinksTable.reload()` отправлял `loadShareLinksDigest({})`. Бэкенд
// (`ShareLinksAdminController.digest`) отдаёт одну страницу (`page=1`, `pageSize=25`,
// `LIMIT/OFFSET`), а таблица показывала её как полный набор: страницы 2+ были пусты,
// `b-pagination` рисовал их по серверному `totalCount`; сортировка шла только по 25
// загруженным строкам. `onPerPageChange` слал ключ `perPage`, которого бэкенд не знает,
// поэтому размер страницы терялся.
//
// Тест фиксирует контракт: UI-состояние всегда превращается в `page`/`pageSize`/
// `sortBy`/`sortDir`, pageSize не выходит за 1..100 (клампит бэкенд), а неизвестная
// колонка не притворяется «сортировкой по ней».
//
// Запуск: cd webvue3 && node --test src/lib/__tests__/sharelinks-digest-params.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import {
  buildShareLinksDigestParams,
  clampShareLinksPerPage,
  SHARELINKS_MAX_PER_PAGE,
} from '../sharelinks-digest-params.js'

describe('параметры серверной страницы share-ссылок (BR-410)', () => {
  test('первая страница и размер страницы доезжают до бэкенда', () => {
    const params = buildShareLinksDigestParams({ page: 2, perPage: 50 })
    assert.equal(params.page, 2, 'страница обязана передаваться — иначе всегда page=1')
    assert.equal(params.pageSize, 50, 'perPage в UI = pageSize в API')
    assert.ok(!('perPage' in params), 'ключ perPage бэкенд не знает')
  })

  test('pageSize клампится к диапазону бэкенда 1..100', () => {
    assert.equal(buildShareLinksDigestParams({ perPage: 1000 }).pageSize, 100)
    assert.equal(buildShareLinksDigestParams({ perPage: 0 }).pageSize, 1)
    assert.equal(buildShareLinksDigestParams({}).pageSize, 25)
    assert.equal(clampShareLinksPerPage('abc'), 25)
    assert.equal(SHARELINKS_MAX_PER_PAGE, 100)
  })

  test('невалидная страница приводится к 1', () => {
    assert.equal(buildShareLinksDigestParams({ page: 0 }).page, 1)
    assert.equal(buildShareLinksDigestParams({ page: 'x' }).page, 1)
  })

  test('серверная колонка сортировки уходит вместе с направлением', () => {
    const params = buildShareLinksDigestParams({
      sortBy: [{ key: 'songName', order: 'asc' }],
    })
    assert.equal(params.sortBy, 'songName')
    assert.equal(params.sortDir, 'ASC')
  })

  test('клиентская колонка (status) не превращается в чужую сортировку', () => {
    const params = buildShareLinksDigestParams({ sortBy: [{ key: 'status', order: 'asc' }] })
    assert.ok(!('sortBy' in params), 'status сервер не сортирует — ключ не передаём')
    assert.equal(params.sortDir, 'DESC')
  })

  test('без сортировки действует серверный дефолт', () => {
    const params = buildShareLinksDigestParams({ sortBy: [] })
    assert.deepEqual(params, { page: 1, pageSize: 25, sortDir: 'DESC' })
  })
})
