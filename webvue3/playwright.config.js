// Playwright-конфигурация для e2e-тестов админки (webvue3).
//
// Контекст: усилие «Аудит SubsEdit.vue» (карта OpenProject #186). Инфраструктура
// e2e доводится до рабочего состояния ДО починки дефектов, чтобы каждый S1
// закрывался регрессионным тестом, а не «на глаз».
//
// Прецедент в проекте: Playwright уже используется на бэкенде —
// karaoke-app/build.gradle.kts подключает com.microsoft.playwright:playwright:1.56.0,
// а knowledge/domains/processing/components/playwright-rendering.md фиксирует
// ловушки headless-запуска. Здесь npm-драйвер, версии независимы.
//
// Запуск: npm run test:e2e          (headless, против запущенного контейнера)
//         npm run test:e2e:headed   (то же, но с видимым браузером)
//
// Переменные окружения:
//   E2E_BASE_URL  — адрес приложения (по умолчанию http://localhost:7906);
//   E2E_SONG_ID   — id песни для набора (по умолчанию 11718);
//   E2E_WORKERS   — число воркеров Playwright (по умолчанию 2 — см. ниже).
//
// Адрес приложения переопределяется переменной E2E_BASE_URL — это то, что
// понадобится для CI и для прогона против другого окружения.

import os from 'node:os'

import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL || 'http://localhost:7906'

export default defineConfig({
  testDir: './e2e',
  outputDir: './e2e/.artifacts',
  // Исключаем артефакты прогона из индекса тестов.
  testIgnore: /.*\.spec\.js\.tmp/,

  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  // Ретраев НЕТ — и в CI тоже. Прежде было `retries: process.env.CI ? 1 : 0`:
  // упавший тест в CI перезапускался и «проходил» во второй раз, маскируя
  // нестабильность набора (аудит #336, BR-624). Пока набор не стал стабильным,
  // ретрай — способ спрятать дефект, а не починить его.
  retries: 0,
  // Параллелизм безопасен: ни один тест набора не пишет в БД — все мутирующие
  // вызовы заглушены page.route (savesourcetextmarkers, diffbeatsinc, update*),
  // а ввод в редактор меняет только локальное поле sourceText. Прежний
  // `workers: 1` с комментарием «админка мутирует общее состояние песни» был
  // неверным: общего изменяемого состояния у набора нет, а последовательный
  // прогон растягивал его до 4–6 минут (BR-624 / P-68-11).
  //
  // Число воркеров по умолчанию — НЕ больше 2. Собственное значение Playwright
  // (половина ядер) на машине с 36 ядрами даёт 18 одновременных редакторов, и
  // стенд с 2170 маркерами на каждом начинает отдавать таймауты: часть тестов
  // падает без единого дефекта в коде. Даже 4 воркера перегружают пул
  // соединений БД стенда («too many clients already» в логах karaoke-app), и
  // редактор не дожидается готовности. 2 воркера — вдвое быстрее
  // последовательного прогона и устойчиво. Переопределяется E2E_WORKERS.
  workers: process.env.E2E_WORKERS
    ? Number(process.env.E2E_WORKERS)
    : Math.max(1, Math.min(2, Math.ceil(os.cpus().length / 2))),

  reporter: process.env.CI
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],

  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    actionTimeout: 10_000,
    navigationTimeout: 30_000,
    // Ловушка из knowledge/domains/processing/components/playwright-rendering.md:
    // headless Chromium требует --no-sandbox, иначе permission denied.
    launchOptions: { args: ['--no-sandbox', '--disable-dev-shm-usage'] },
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1600, height: 1000 } },
    },
  ],
})
