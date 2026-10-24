// #189: save() был голым dispatch без await/catch — два быстрых нажатия давали
// два ПАРАЛЛЕЛЬНЫХ POST /api/song/savesourcetextmarkers, и более ранний ответ мог
// перетереть более поздний (потеря правок).
//
// Отдельная находка: doDiffBeatsInc/Dec вызывали мутирующий эндпоинт
// /api/song/diffbeatsinc ЧЕТЫРЕ раза подряд. На бэкенде это
// `song.fields[DIFFBEATS] = diffBeats + 1; song.saveToDb()`, то есть одно
// нажатие давало +4 к diffBeats и четыре записи в БД. Кнопки, вызывавшие эти
// методы, закомментированы в шаблоне, поэтому дефект ЛАТЕНТНЫЙ — но вызывается
// через сам компонент, и при раскомментировании кнопок сразу стал бы боевым.

import { expect, test } from '@playwright/test'

import { openEditor, withSubsEdit } from './fixtures/song.js'

test.setTimeout(180000)

const callComponent = (page, method, arg) =>
  withSubsEdit(
    page,
    (p, payload) => p[payload.method](payload.arg),
    { method, arg: arg === undefined ? null : arg },
  )

test('#189: мутирующий эндпоинт вызывается ОДИН раз, а не четыре', async ({ page }) => {
  const hits = []
  // Отвечаем -1 («песня не найдена»): тогда код НЕ дёргает doChordsDel/doChordsAdd,
  // и проверка остаётся чистой — считаем только число вызовов эндпоинта.
  await page.route('**/api/song/diffbeatsinc', (r) => {
    hits.push(Date.now())
    return r.fulfill({ status: 200, contentType: 'application/json', body: '-1' })
  })
  await openEditor(page)
  await callComponent(page, 'doDiffBeatsInc')
  await page.waitForTimeout(1200)
  expect(hits.length, 'эндпоинт мутирует БД, вызывать его нужно один раз').toBe(1)
})

test('#189: два быстрых save() не дают пересекающихся POST', async ({ page }) => {
  let inFlight = 0
  let maxInFlight = 0
  let total = 0
  await page.route('**/api/song/savesourcetextmarkers', async (r) => {
    inFlight++
    total++
    maxInFlight = Math.max(maxInFlight, inFlight)
    // Задержка, чтобы перекрытие было гарантированно заметным.
    await new Promise((res) => setTimeout(res, 400))
    inFlight--
    await r.fulfill({ status: 200, contentType: 'application/json', body: 'ok' })
  })
  await openEditor(page)

  // ОБА сохранения — внутри ОДНОГО page.evaluate и БЕЗ промежуточного await.
  //
  // Почему так (аудит #336, BR-618): save() объявлен async, и page.evaluate
  // возвращает промис вызова метода. Прежняя версия теста ждала первый save()
  // отдельным evaluate — то есть первый POST /api/song/savesourcetextmarkers
  // успевал полностью завершиться (включая задержку 400 мс в заглушке) ДО
  // того, как вызывался второй save(). Сохранения физически не могли
  // пересечься, и утверждение maxInFlight === 1 было истинно при ЛЮБОЙ
  // реализации — в том числе если очередь saving/saveQueued убрать из
  // компонента. Регрессионная защита #189 была обнулена.
  //
  // Теперь оба вызова идут подряд, без await между ними: второй save()
  // стартует, пока первый ещё в полёте. На корректном коде второй встаёт в
  // очередь (saveQueued) и уходит после первого; на коде без очереди — два
  // POST'а идут одновременно, и утверждение падает.
  const res = await withSubsEdit(page, async (p) => {
    const first = p.save() // уходит в полёт, НЕ ждём
    const second = p.save() // стартует, пока первый в полёте
    await Promise.allSettled([first, second])
    return { saving: p.saving, queued: p.saveQueued }
  })

  console.log(
    'SAVE: всего=' + total + ' максимум_одновременных=' + maxInFlight + ' ' + JSON.stringify(res),
  )
  expect(total, 'второе сохранение должно выполниться, а не потеряться').toBe(2)
  expect(
    maxInFlight,
    'сохранения не должны пересекаться: очередь saving/saveQueued обязана '
      + 'выстраивать их в цепочку',
  ).toBe(1)
  expect(res.saving, 'после завершения очередь должна быть пуста').toBe(false)
  expect(res.queued, 'повторное сохранение должно быть выполнено, а не остаться в очереди').toBe(false)
})
