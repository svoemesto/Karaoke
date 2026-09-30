// E2E публичного сайта (karaoke-public).
//
// Зачем эта инфраструктура: правки публичного редактора (#200, #201, #202,
// #203) проверялись юнит-тестами по исходникам, но НЕ проверялось, что они
// доехали до работающего сайта. Правки попали в master, а контейнер
// karaoke-public остался на образе шестидневной давности — дыру обнаружили
// только вручную. Юнит-тест доказывает, что функция ведёт себя правильно,
// но не что браузер отдаёт именно эту функцию.
//
// Поэтому здесь есть проверка контракта развёртывания: правила рендера
// публичного редактора ОБЯЗАНЫ присутствовать в отдаваемом бандле.

import { expect, test } from '@playwright/test'

test.describe('публичный сайт', () => {
  test('отдаётся и грузится без ошибок в консоли', async ({ page }) => {
    const errors = []
    page.on('pageerror', (e) => errors.push(e.message))
    page.on('console', (m) => {
      if (m.type() === 'error') errors.push(m.text())
    })

    const response = await page.goto('/')
    expect(response, 'сервер должен ответить').not.toBeNull()
    expect(response.status()).toBeLessThan(500)
    await expect(page.locator('#app').first()).toBeAttached()
    expect(errors, 'ошибок при загрузке быть не должно').toHaveLength(0)
  })

  test('контракт развёртывания: правила рендера редактора в отдаваемом бандле', async ({
    request,
  }) => {
    const html = await (await request.get('/')).text()
    const scripts = [...html.matchAll(/src="([^"]*assets\/[^"]*\.js)"/g)].map((m) => m[1])
    expect(scripts.length, 'в index.html должны быть ссылки на бандлы').toBeGreaterThan(0)

    let bundle = ''
    for (const src of scripts) {
      const body = await (await request.get(src)).text()
      if (body.includes('markertype')) bundle = body // бандль с редактором
    }
    expect(bundle, 'бандль с кодом редактора должен быть найден').not.toBe('')

    // #203: пустой комментарий даёт перенос строки и НЕ рендерит пустой span.
    // Строковый литерал переживает минификацию, поэтому проверка устойчива.
    expect(bundle, 'ветка пустого COMMENT| должна быть в бандле').toContain('COMMENT| ')

    // #202: дедупликация по ВРЕМЕННОМУ диапазону, а не по индексному окну.
    expect(bundle, 'дедуп по времени (m.time >= …) должен быть в бандле').toMatch(
      /\.time>=\w+&&\w+\.time<=/,
    )
    // FIX #018: маркер не встаёт в ноль.
    expect(bundle, 'защита FIX #018 (time < 0.5) должна быть в бандле').toMatch(/<\s*\.5\s*&&/)
  })
})
