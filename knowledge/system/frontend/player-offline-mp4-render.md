# Плеер: детерминированный офлайн-рендер MP4 (webvue3)

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/player/KaraokePlayer.js` — канвас-плеер, который
> используется и на странице `/player/:id`, и как headless-рендерер кадров для
> `PlayerMp4RenderService` (`webvue3/src/views/PlayerView.vue`).

## Назначение

Плеер умеет два режима: обычное воспроизведение по `requestAnimationFrame` и
офлайн-рендер, когда внешний оркестратор (Kotlin/Playwright) вызывает
`renderFrameAt(dt)` по одному кадру. В офлайн-режиме (`_offline = true`,
`_forcedTime = dt`) кадр обязан зависеть **только** от `dt`: повторный рендер
той же песни при тех же параметрах должен давать тот же файл. Это инвариант,
записанный в `PlayerMp4RenderService`.

## Как работает

- `renderFrameAt(dt)` выставляет `_forcedTime` и вызывает `_renderFrame()`;
  `_getDisplayTime()` возвращает `_forcedTime` вместо часов.
- `_renderFrame()` строит кадр: фон (панорама тоже от `_forcedTime`), сплэш,
  караоке-строки, бейджи, DEMO-экран.
- `_renderSplash()` рисует три блока: название, автор, подпись версии
  (`KaraokePlayer.RENDER_VERSION_SPLASH`).
- `_renderDemoEndScreen(ctx, W, H, alpha, timeSec)` рисует финальный экран DEMO
  и анимирует стрелки «▼»; фаза берётся из `timeSec` (времени кадра).

## Инварианты

- В офлайн-кадре нет источников времени, кроме `dt`: `Date.now()`,
  `performance.now()`, реальные таймеры запрещены.
- Набор подписей версии покрывает все значения бэкендового enum `RenderVersion`
  (`LYRICS`, `KARAOKE`, `CHORDS`, `TABS`, `DEMO`).
- В режиме `inlineData` (превью редактора) `songId` берётся из `inlineData.id`;
  без него URL файла не собирается, а пользователь видит ошибку.
- Ошибка сохранения файла не проглатывается в `console`: она показывается в
  пункте меню «Сохранить файл».

## Известные ловушки

- **Не возвращайте `Date.now()` в анимации DEMO-экрана.** Раньше стрелки
  «подпрыгивали» по стенным часам: между соседними кадрами проходили десятки и
  сотни миллисекунд, файл был невоспроизводим (BR-483).
- **`_renderDemoEndScreen` вызывается только из офлайн-ветки** — при правке
  сигнатуры обновите вызов в `_renderFrame`, иначе `timeSec` останется `0` и
  анимация замрёт на одной фазе.
- **`_getPlayerFileUrl()` может вернуть `null`.** Не подставляйте его в `fetch`
  без проверки: именно так возникал запрос `/api/song/undefined/playerfile` и
  молчаливый отказ (BR-484).
- **`RENDER_VERSION_SPLASH` — единый источник подписей.** Локальная копия карты
  в `_renderSplash` уже приводила к пропуску блока версии для CHORDS/TABS
  (BR-485); при добавлении версии на бэкенде обновите статическую карту.
- Офлайн-рендер не использует UI-хром плеера (меню, ползунки) — не добавляйте
  в кадр элементы, зависящие от DOM/анимаций браузера.

## Ссылки

- [views (webvue3)](webvue3-views.md) — каталог view-страниц, включая `PlayerView.vue`.
- [Vuex-паттерны](vuex-patterns.md) — почему офлайн-режим не трогает store.
- [Per-feature: целостность состояния админ-UI](../infra/audit-336-registry.md)
- [PlayerMp4RenderService](../../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PlayerMp4RenderService.kt) — оркестратор кадров.
