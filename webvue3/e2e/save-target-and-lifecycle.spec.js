// #216 и #217 — сохранение SongEdit: цель запроса и жизненный цикл таймера.
//
// #216: saveSong брал цель как params.id = ctx.state.currentSongId, то есть в момент
// ОТПРАВКИ, а params содержал только значения полей. При переходе на другую песню
// currentSongId меняется раньше, чем догружается содержимое новой песни, поэтому
// правка уходила в следующую песню вместо своей.
//
// #217: у компонента не было beforeUnmount, поэтому таймер автосейва срабатывал
// после размонтирования, а executeSave перевзводил его снова.
//
// Оба теста обязаны ПАДАТЬ на старом коде:
//   - #216: старый код отправил бы id=99999, тест ждёт id песни;
//   - #217: без beforeUnmount запрос ушёл бы после ухода со страницы.

import { expect, test } from '@playwright/test'

import { openEditor, withComponent } from './fixtures/song.js'

test.setTimeout(180000)

// Вызывает метод инстанса компонента с указанным именем.
const callComponent = (page, compName, method, arg) =>
  withComponent(
    page,
    compName,
    (inst, payload) => inst[payload.method](payload.arg),
    { method, arg: arg === undefined ? null : arg },
  )

// Находит инстанс SongEdit в дереве компонентов.
const withSongEdit = (page, fn, arg) => withComponent(page, 'SongEdit', fn, arg)

test('#216: правка уходит в свою песню, даже если currentSongId уже другой', async ({ page }) => {
  await openEditor(page)

  const sent = []
  await page.route('**/api/song/update*', async (route) => {
    sent.push(route.request().postData())
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
  })

  const result = await withSongEdit(
    page,
    (inst) => {
      const store = inst.$store
      // Правка внесена в песню, которая сейчас открыта.
      store.commit('setCurrentSongField', { name: 'author', value: 'Тестовый автор #216' })
      const realSongId = store.getters.getCurrentSong.id
      // Имитируем переход на другую песню: цель в сторе уже сменилась,
      // содержимое currentSong — ещё старое (окно гонки).
      store.commit('setCurrentSongIdOnly', 99999)
      return { realSongId, diff: store.getters.getSongDiff.length }
    },
  )

  expect(result.error, result.error || '').toBeUndefined()
  expect(result.diff, 'нужна хотя бы одна несохранённая правка').toBeGreaterThan(0)

  await withSongEdit(page, (inst) => inst.executeSave())
  await page.waitForTimeout(1500)

  expect(sent.length, 'сохранение должно уйти ровно один раз').toBe(1)
  const body = sent[0]
  expect(body, 'в теле запроса должен быть id песни').toContain(`id=${result.realSongId}`)
  expect(body, 'правка не должна уходить в песню, открытую в сторе').not.toContain('id=99999')
})

test('#217: после размонтирования таймер автосейва не срабатывает', async ({ page }) => {
  await openEditor(page)

  const sent = []
  await page.route('**/api/song/update*', async (route) => {
    sent.push(route.request().postData())
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
  })

  // Вносим правку — watcher diff взводит таймер автосейва (autoSaveDelayMs = 1000).
  // Watcher в Vue срабатывает асинхронно, поэтому ждём nextTick.
  const armed = await withSongEdit(
    page,
    async (inst) => {
      inst.$store.commit('setCurrentSongField', { name: 'year', value: 2030 })
      await inst.$nextTick()
      return { timerArmed: Boolean(inst.saveTimer), diff: inst.diff.length }
    },
  )
  expect(armed.error, armed.error || '').toBeUndefined()
  expect(armed.diff, 'правка должна зарегистрироваться в diff').toBeGreaterThan(0)
  expect(armed.timerArmed, 'watcher diff обязан взвести таймер автосейва').toBe(true)

  // Размонтируем компонент БЕЗ перезагрузки страницы. page.goto уничтожал бы весь
  // JS-контекст вместе с таймером, и тест проходил бы и на старом коде — то есть
  // ничего бы не проверял. Закрываем модалку средствами приложения: SongEdit
  // размонтируется, а страница остаётся живой.
  await callComponent(page, 'SongsTable', 'closeSongEdit')
  await page.waitForTimeout(300)
  const stillMounted = await withSongEdit(page, () => true).catch(() => null)
  // withSongEdit возвращает { error } вместо исключения, когда инстанс не найден.
  const isMounted = Boolean(stillMounted && !stillMounted.error)
  expect(isMounted, 'SongEdit должен быть размонтирован после закрытия модалки').toBe(false)

  // Дебаунс 1000 мс плюс запас на саму отправку.
  await page.waitForTimeout(2500)

  expect(sent.length, 'после размонтирования сохранение уходить не должно').toBe(0)
})
