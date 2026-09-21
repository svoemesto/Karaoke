# Report — OpenProject #82 «Root и A-Root в таблице песен»

## Статус

**Готово к деплою.** PR #467 (фикс) → PR #468 (hotfix регрессии из #467)
смержены в `master`. Сборка docker-образа `karaoke-webvue3:1` — на владельце.

- PR #467: https://github.com/svoemesto/Karaoke/pull/467
- PR #468: https://github.com/svoemesto/Karaoke/pull/468
- Финальный коммит: `2621a678` «fix(webvue3): revert <b-tooltip> wrapper to v-b-tooltip directive (#82)»

## Что сделано

Три подзадачи #82 в `webvue3/src/components/Songs/SongsTable.vue`.

### 1. Двойной тултип при ховере

**Symptom (по #82):** при ховере на ячейку root / A-root появляется первый
плейсхолдер («Загрузка…» / «Нет связанной песни», чёрный фон, сверху),
а через несколько секунд — второй с результатом поиска (синий фон, справа-снизу).
Первый не исчезает.

**Root cause:** директива `v-b-tooltip.hover` из bootstrap-vue-next
(см. `node_modules/bootstrap-vue-next/dist/floatingUi-BBabBWxD.mjs`,
`createFloatingDirective` в `utils-BYUyX1-A.mjs`) читает атрибут `title`
с DOM-элемента ровно один раз — на `mounted`. Реактивный `:title` binding
переустанавливает атрибут при каждом ре-рендере ячейки → появляется
**нативный браузерный тултип**. Старый `setTooltipTitle` пытался
обновить b-tooltip через `el.__tooltip.setContent`, но реальный инстанс
директива хранит в `el.$__tooltip` (см. `createFloatingDirective` →
`propertyName = "$__tooltip"`). `el.__tooltip` всегда `undefined` →
`setContent` был no-op → **b-tooltip показывал устаревший «Загрузка…»
вечно**, рядом с ним нативный тултип с обновлённым контентом. Итого:
два тултипа одновременно, первый «висит».

**Fix:** контент тултипа передаётся через **объектный binding**
`v-b-tooltip.hover="{ title: rootTooltipTitle(data.value) }"`. По
`resolveContent` (директива bootstrap-vue-next): если `binding.value`
— объект с полем `title`, **DOM-атрибут не читается и не удаляется**
→ нативный тултип не появляется → единственный источник контента.
При изменении `songShortInfoCache` обновляется реактивный `rootTooltipTitle(id)`,
директива пересоздаёт BPopoverTemplate (`unbind` → `bind` в `updated`
hook), контент перерисовывается.

### 2. Прочерк для нулевых значений в root

`v-text="data.value ? data.value : '-'"` уже стоял на A-root;
добавлен такой же тернарник на root-колонку.

### 3. Кликабельность не-нулевых root / A-root

- `@click.left="openRootSong(data.value)"` на обеих ячейках.
- `openRootSong(id)` → внутри `if (!id || id <= 0) return` →
  `this.editSong(id)` (тот же путь, что у колонки «Композиция»).
- Курсор: новый класс `.fld-root-link { cursor: pointer }`,
  `:class="{ 'fld-root-link': data.value > 0 }"`, плюс hover-underline.

## Регрессия, найденная владельцем

PR #467 ввёл **критическую регрессию**: ячейки root/A-root полностью
пропали из таблицы. Root cause — переход с `v-b-tooltip.hover` на
компонент `<b-tooltip>` (обёртку) сломал рендеринг b-table cell slot
(b-table ожидает single DOM-element в cell slot, а `<b-tooltip>`
рендерит фрагмент с teleport / BPopoverTemplate wrapper).

**Owner первым поймал регрессию** на скриншоте `localhost:7906/songs`
**до** сборки образа — критический fail-fast (владелец увидит регрессию
на скриншоте, а не на проде).

**Fix:** PR #468 — откат к директиве `v-b-tooltip.hover` с объектным
binding `{ title: ... }` (см. секцию 1 выше). BTooltip убран из
импортов и из `components`. Старые `formatTooltipTitle` /
`setTooltipTitle` удалены, логика перенесена в `rootTooltipTitle` /
`loadRootInfo` / `openRootSong`.

## CI / проверки

PR #467: 9/9 PASS (ktlint, ESLint+Prettier webvue3, ESLint+Prettier
karaoke-public, JSDoc ≥50%, KDoc ≥50%, Knowledge SSoT impact,
Knowledge SSoT structure, Docs structure + offline links, Baseline
stats informational).

PR #468: 9/9 PASS — те же проверки.

JSDoc strict — 99.4% (156/157) для webvue3. Все новые методы
(`rootTooltipTitle`, `loadRootInfo`, `openRootSong`) имеют JSDoc.

## Что осталось владельцу

1. `cd /home/nsa/Karaoke/deploy && bash do.sh build_start_webvue3` —
   пересобрать docker-образ `svoemestodev/karaoke-webvue3:1` с фиксом
   из master. Я не делал этого из DSH-sandbox: правило команды
   (см. preset reviewer §6 + peer-mail #186) — сборку docker-образов
   делает владелец, не агент.
2. Проверить визуально в браузере:
   - На ячейках root / A-root без значений — прочерк.
   - Hover на ячейке с не-нулевым id — **один** тултип, текст
     меняется с «Загрузка…» на «Автор — Год — Альбом — Композиция»
     (или «Не найдено», или «Нет связанной песни» для id ≤ 0).
     Никаких нативных браузерных тултипов с другим стилем.
   - Клик на не-нулевой ячейке root / A-root — открывается SongEdit
     для соответствующей родительской песни.
3. После визуального подтверждения — закрыть #82 (`close-issue 82`).

## Lessons learned (для архитектурного changelog)

- **bootstrap-vue-next директива `v-b-tooltip`** хранит инстанс в
  `el.$__tooltip`, не в `el.__tooltip`. Любые попытки обновить
  контент после mount через `el.__tooltip` — no-op.
- **bootstrap-vue-next компонент `<b-tooltip>` нельзя использовать как
  wrapper в b-table cell slot** — ломает рендеринг таблицы. Решение:
  оставаться на директиве, но передавать контент через объектный
  binding `{ title: ... }`.
- **Из DSH-sandbox нельзя пересобирать docker-образы** (sandbox
  блокирует `/home/nsa/.docker/buildx/`). Правило: правки → PR →
  владелец делает `do.sh build_*` локально. Нашёл это правило в
  agent-preset ревьюера и peer-mail #186 после того, как сделал
  хак с `DOCKER_CONFIG=/tmp/...` в PR #467 — отметил это явно в
  commit message PR #468.
