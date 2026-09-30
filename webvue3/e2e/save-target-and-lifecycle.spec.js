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

test.setTimeout(180000)

// Вызывает метод инстанса компонента с указанным именем.
const callComponent = (page, compName, method, arg) =>
  page.evaluate(
    ([name, m, a]) => {
      const app = document.querySelector('#app[data-v-app]').__vue_app__
      const seen = new Set()
      let inst = null
      const walk = (v) => {
        if (!v || typeof v !== 'object' || seen.has(v)) return
        seen.add(v)
        const t = v.component?.type
        if (v.component && t && (t.name || t.__name) === name) inst = v.component
        if (Array.isArray(v.children)) v.children.forEach(walk)
        if (v.component?.subTree) walk(v.component.subTree)
      }
      walk(app._container._vnode)
      if (!inst) throw new Error(`${name} не найден в дереве компонентов`)
      return inst.proxy[m](a)
    },
    [compName, method, arg ?? null],
  )

const openEditor = async (page) => {
  // Список песен не нужен и тяжёлый — гасим его, как в measure-input-latency.spec.js.
  await page.route(
    (u) => u.pathname === '/api/songs',
    (r) => r.abort(),
  )
  await page.goto('/songs', { waitUntil: 'domcontentloaded' })
  await page.evaluate(async () => {
    const s = document.querySelector('#app[data-v-app]').__vue_app__.config.globalProperties.$store
    s.commit(
      'updateSongsDigests',
      JSON.parse(await s.dispatch('loadSongsDigestsPromise', { filterId: '11718' })),
    )
  })
  await page.locator('.fld-song-name').first().click()
  // Кнопка открывает SongEditModal, внутри которого живёт SongEdit (и вложенный SubsEdit).
  await page.locator('button[title="Редактировать субтитры"]').first().click()
  await page.waitForFunction(
    () => {
      const s = document.querySelector('#app[data-v-app]').__vue_app__.config.globalProperties.$store
      const song = s.getters.getCurrentSong
      return Boolean(song && song.id)
    },
    null,
    { timeout: 30000 },
  )
  await page.waitForTimeout(2500)
}

// Находит инстанс SongEdit в дереве компонентов.
const withSongEdit = (page, fn, arg) =>
  page.evaluate(
    ([body, a]) => {
      const app = document.querySelector('#app[data-v-app]').__vue_app__
      const seen = new Set()
      let inst = null
      const walk = (v) => {
        if (!v || typeof v !== 'object' || seen.has(v)) return
        seen.add(v)
        const t = v.component?.type
        if (v.component && t && (t.name || t.__name) === 'SongEdit') inst = v.component
        if (Array.isArray(v.children)) v.children.forEach(walk)
        if (v.component?.subTree) walk(v.component.subTree)
      }
      walk(app._container._vnode)
      if (!inst) return { error: 'SongEdit не найден в дереве компонентов' }
      // inst уже прокси компонента — тело функции получает его напрямую.
      // eslint-disable-next-line no-new-func
      return new Function('inst', 'arg', `return (${body})(inst, arg)`)(inst.proxy, a)
    },
    [fn.toString(), arg ?? null],
  )

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
