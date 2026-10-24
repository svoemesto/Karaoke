// Регрессионный тест на сериализацию form-urlencoded (BR-379 / BR-426, аудит #336,
// пакет PKG-S2-17, OpenProject #380).
//
// ДО ПРАВКИ `getParamStringToSend` писала КАЖДЫЙ ключ объекта, включая значения
// `undefined`. Для вызывающего это означало «параметр не задан», а сервер получал
// строку «undefined»:
//
//   { target: 'local', page: 1, pageSize: undefined }
//     -> 'target=local&page=1&pageSize=undefined'
//
// Бэкенд объявляет `pageSize: Int = 500` (ListeningHistoryController) и
// `filterAssigneeId: Long?` (SongEditorController); конверсия «undefined» в число
// даёт MethodArgumentTypeMismatchException → 400. Поэтому «Применить фильтр» в истории
// прослушиваний (BR-379) и «Удалить одобренные» (BR-426) не работали.
//
// Тест фиксирует контракт: отсутствующий параметр НЕ передаётся, а заданные значения
// (включая 0, false и пустую строку) передаются как есть.
//
// Запуск: cd webvue3 && node --test src/lib/__tests__/params-serializer.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { getParamStringToSend } from '../utils.js'

describe('getParamStringToSend: параметры, которых нет, не передаются (BR-379/BR-426)', () => {
  test('undefined-значение не попадает в тело запроса', () => {
    // Ровно тот payload, который собирал store.js ListeningHistory до правки.
    const body = getParamStringToSend({ target: 'local', page: 1, pageSize: undefined })
    assert.equal(body, 'target=local&page=1')
    assert.ok(!body.includes('pageSize'), 'ключ pageSize не должен присутствовать вовсе')
    assert.ok(!body.includes('undefined'), 'в теле запроса не должно быть строки undefined')
  })

  test('null-значение не попадает в тело запроса', () => {
    const body = getParamStringToSend({ filterStatus: null, filterAuthor: 'Иванов' })
    assert.equal(body, 'filterAuthor=%D0%98%D0%B2%D0%B0%D0%BD%D0%BE%D0%B2')
  })

  test('payload массового удаления без фильтров не содержит undefined-ключей (BR-426)', () => {
    const body = getParamStringToSend({
      target: 'local',
      filterAssigneeId: undefined,
      filterStatus: undefined,
      filterAuthor: undefined,
    })
    assert.equal(body, 'target=local')
  })

  test('заданные значения передаются без изменений (0, false, пустая строка)', () => {
    const body = getParamStringToSend({ a: 0, b: false, c: '', d: 'текст' })
    assert.equal(body, 'a=0&b=false&c=&d=%D1%82%D0%B5%D0%BA%D1%81%D1%82')
  })

  test('порядок ключей сохраняется', () => {
    assert.equal(getParamStringToSend({ z: 1, a: 2 }), 'z=1&a=2')
  })
})
