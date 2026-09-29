// Регрессионные тесты на два смежных дефекта жизненного цикла редактора:
// #187 — утечка document-листенеров и падение после закрытия модалки;
// #188 — глобальные хоткеи срабатывают при вводе в дочерние модалки.
//
// Живая песня 11718 «Говорит Деметра» (2170 маркеров). Логин не требуется.
//
// До фикса:
//   #187 — после закрытия редактора любое нажатие клавиши давало
//          TypeError (слушатели остались на document, this.ws уже null).
//   #188 — ввод «ss» в AI-редакторе удалял маркер (хоткей `s` ->
//          deleteMarker), ввод «w» создавал новый (хоткей `w` -> addMarker).
//
// Запуск: E2E_BASE_URL=http://localhost:7906 npm run test:e2e -- editor-lifecycle.spec.js

import { expect, test } from '@playwright/test'

const SONG_ID = 11718

async function openEditor(page) {
  // Список песен тянет POST /api/songs (~81 МБ) — обрываем, он не нужен.
  // Сравнение по pathname, а не glob: `**/api/songs**` матчит и
  // `/api/songsdigests`, из-за чего обрывался нужный запрос.
  await page.route(
    (url) => url.pathname === '/api/songs',
    (r) => r.abort(),
  )
  await page.goto('/songs', { waitUntil: 'domcontentloaded' })

  // Песни приходят только с фильтром; нужен loadSongsDigestsPromise — обычный
  // loadSongsDigests промис не возвращает.
  await page.evaluate(async (id) => {
    const store =
      document.querySelector('#app[data-v-app]').__vue_app__.config.globalProperties.$store
    const raw = await store.dispatch('loadSongsDigestsPromise', { filterId: String(id) })
    store.commit('updateSongsDigests', JSON.parse(raw))
  }, SONG_ID)

  await page.locator('.fld-song-name').first().click()
  await page.locator('button[title="Редактировать субтитры"]').first().click()
  await page.locator('.se-subsedit-area').waitFor({ state: 'attached' })
  await page.locator('#waveform').waitFor({ state: 'attached' })
  await page.waitForTimeout(7000)
}

function getSubsEdit(page, fn) {
  return page.evaluate((body) => {
    const app = document.querySelector('#app[data-v-app]').__vue_app__
    const seen = new Set()
    let inst = null
    const walk = (v) => {
      if (!v || typeof v !== 'object' || seen.has(v)) return
      seen.add(v)
      const n = v.component?.type
      if (v.component && n && (n.name || n.__name) === 'SubsEdit') inst = v.component
      if (Array.isArray(v.children)) v.children.forEach(walk)
      if (v.component?.subTree) walk(v.component.subTree)
    }
    walk(app._container._vnode)
    if (!inst) return { err: 'экземпляр SubsEdit не найден' }
    // eslint-disable-next-line no-new-func
    return new Function('p', `return (${body})(p)`)(inst.proxy)
  }, fn)
}

test.describe('жизненный цикл редактора (#187, #188)', () => {
  test('#187: закрытие модалки снимает слушатели клавиатуры с document', async ({ page }) => {
    test.setTimeout(150_000)

    // Считаем add/remove на document и set/clearInterval прямо в странице,
    // ДО загрузки приложения. Это единственный способ доказать утечку:
    // снаружи она не видна — watcher'ы в Vue 3 останавливаются при размонтировании,
    // поэтому TypeError в этой ситуации НЕ возникает, и ловить падение бессмысленно.
    //
    // ИСТОРИЯ ВАРИАНТА: сначала тест ждал падения в консоли после закрытия. Он
    // проходил и на сломанном коде — то есть не ловил ничего. Проверено вручную:
    // `this.ws` в этом пути дёргает watcher pressedX (2251), а не сам обработчик
    // клавиатуры, и остановленные watcher'ы не срабатывают. Реальный дефект —
    // утечка, и вот её тест и меряет.
    await page.addInitScript(() => {
      const w = window
      w.__io = { keydown: 0, keyup: 0, setInterval: 0, clearInterval: 0 }
      const add = document.addEventListener.bind(document)
      const rem = document.removeEventListener.bind(document)
      document.addEventListener = function (t, ...a) {
        if (t === 'keydown') w.__io.keydown++
        if (t === 'keyup') w.__io.keyup++
        return add(t, ...a)
      }
      document.removeEventListener = function (t, ...a) {
        if (t === 'keydown') w.__io.keydown--
        if (t === 'keyup') w.__io.keyup--
        return rem(t, ...a)
      }
      const si = window.setInterval.bind(window)
      const ci = window.clearInterval.bind(window)
      window.setInterval = function (...a) {
        w.__io.setInterval++
        return si(...a)
      }
      window.clearInterval = function (...a) {
        w.__io.clearInterval++
        return ci(...a)
      }
    })

    await openEditor(page)

    const afterOpen = await page.evaluate(() => ({ ...window.__io }))
    expect(afterOpen.keydown, 'редактор должен вешать слушатель keydown').toBeGreaterThan(0)
    expect(afterOpen.keyup, 'редактор должен вешать слушатель keyup').toBeGreaterThan(0)

    // Закрываем штатной кнопкой «Выход».
    await page.locator('button.se-btn-close').first().click()
    await expect(page.locator('.se-subsedit-area')).toHaveCount(0)
    await page.waitForTimeout(1200)

    const afterClose = await page.evaluate(() => ({ ...window.__io }))
    console.log('IO:', JSON.stringify({ afterOpen, afterClose }))

    // ГЛАВНОЕ УТВЕРЖДЕНИЕ: после закрытия на document не осталось ни одного
    // обработчика клавиатуры этого редактора. До фикса здесь было keydown: 1,
    // keyup: 1 — и они висели до перезагрузки страницы.
    expect(afterClose.keydown, 'слушатель keydown должен быть снят').toBe(0)
    expect(afterClose.keyup, 'слушатель keyup должен быть снят').toBe(0)

    // Про таймеры и wavesurfer.destroy() тест НЕ утверждает: счётчик интервалов
    // считает их по всему приложению и не отличает интервалы SubsEdit от чужих,
    // а экземпляр после размонтирования недоступен. Эта часть закрыта чтением
    // кода (beforeUnmount), а не браузером — говорить об этом честнее, чем
    // вставлять здесь зелёную галочку, которая ничего не проверяет.
  })

  test('#188: ввод в AI-редакторе не вызывает хоткеи редактора', async ({ page }) => {
    test.setTimeout(150_000)

    // ГЛАВНОЕ УТВЕРЖДЕНИЕ ТЕСТА — ОТСУТСТВИЕ ОШИБОК, а не изменение счётчика
    // маркеров. На старом коде число маркеров НЕ менялось (addMarker падал
    // раньше, чем успевал что-то добавить), а настоящий симптом был:
    //   TypeError: Cannot read properties of undefined (reading 'region')
    //   at Proxy.addMarker
    // то есть глобальные хоткеи срабатывали при вводе в дочернюю модалку.
    const errors = []
    page.on('pageerror', (e) => errors.push('pageerror: ' + e.message))
    page.on('console', (m) => {
      if (m.type() === 'error' && !/MIDI/i.test(m.text())) errors.push('console: ' + m.text())
    })

    await openEditor(page)

    const before = await getSubsEdit(page, 'p => p.sourceMarkers.length')
    expect(before.err).toBeUndefined()

    // Открываем AI-редактор — в нём есть textarea, куда печатает пользователь.
    // Кнопка не имеет текста, только иконку с title, поэтому целимся по img[alt].
    await page.locator('img[alt="ai text editor"]').click()
    await page.locator('textarea.ae-textarea').first().waitFor({ state: 'visible' })

    // Именно поле AI-редактора: textarea#editor основного редактора — другое
    // поле, и нажатия в нём гасить не нужно.
    const textarea = page.locator('textarea.ae-textarea').first()
    await expect(textarea, 'в AI-редакторе должно быть поле ввода').toBeVisible()

    // Печатаем то, что раньше ломало разметку:
    // `s` — deleteMarker, `w`/`1` — addMarker, `o` — addSettingMarker.
    await textarea.click()
    await textarea.type('ssw1o', { delay: 40 })
    await page.waitForTimeout(1200)

    const after = await getSubsEdit(page, 'p => p.sourceMarkers.length')
    expect(after.err).toBeUndefined()
    expect(after, 'ввод текста не должен ни удалять, ни создавать маркеры').toBe(before)
    expect(errors, 'ввод в модалке не должен дёргать хоткеи редактора').toEqual([])
  })
})
