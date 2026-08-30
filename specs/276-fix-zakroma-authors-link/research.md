# Research: 276-fix-zakroma-authors-link

**Дата**: 2026-08-30
**Спека**: [spec.md](spec.md)

## Контекст

Баг: vue-router переиспользует экземпляр компонента `ZakromaView` при навигации между маршрутами `/zakroma`, `/zakroma/:authorId`, `/zakroma/special-bucket` (все три используют один и тот же `component: ZakromaView` в `karaoke-public/src/router/index.js:40-44`). Из-за этого `data()` НЕ вызывается заново при переходе `/zakroma/50` → `/zakroma`, и поле `authorChosen = true` остаётся прежним — клик на «← К списку авторов» лишь меняет URL, но не сбрасывает состояние view.

Спека 258 (введшая path-based routing) ошибочно полагалась на то, что vue-router пересоздаёт компонент при смене path — это верно только при `:key="$route.path"` на `<router-view>` или при смене `name` компонента. Ни того, ни другого сейчас нет (см. `ZakromaView.vue:544-551`).

## R-1: Выбор механизма сброса состояния

**Decision**: добавить `watch: { '$route.path': ... }` в `ZakromaView.vue`, который при смене path (и только если новый path не содержит `:authorId` и не равен `/zakroma/special-bucket`) вызывает существующий метод `backToAuthors()` для сброса состояния.

**Rationale**:
1. `backToAuthors()` уже содержит весь нужный сброс (`selectedAuthorId = ''`, `selectedAuthor = ''`, `authorChosen = false`, `specialBucketShown = false`, `songFilter = ''`) + `$router.replace({ path: '/zakroma', query: {} })`. Переиспользование исключает дублирование логики и риск рассинхрона (если позже в сброс добавятся новые поля — оба места придётся менять).
2. `watch` на `$route.path` — стандартный и хорошо читаемый паттерн Vue 3 Options API, не требует дополнительных зависимостей.
3. `watch` срабатывает только при реальной смене значения path, рекурсии нет (vue-router вызывает watcher один раз per navigation, после того как `$route` обновился).
4. Точечное изменение: только `ZakromaView.vue`. `App.vue`, `AppHeader.vue`, `router/index.js` — без изменений.

**Alternatives considered**:

- **Alt-1: `:key="$route.path"` на `<router-view>` в `App.vue`** — самый простой. Плюсы: минимальный код (одна строка), полностью делегирует сброс vue-router'у. Минусы: пересоздаёт ВСЕ view при ЛЮБОЙ смене path (например, `/zakroma/50` → `/song?id=123` тоже пересоздаст `SongView`, что может сломать его локальный стейт — плеер, скролл-позиция). Слишком грубо для нашего случая, когда пересоздание нужно только для `ZakromaView`. **Отвергнут**.

- **Alt-2: `beforeRouteUpdate` navigation guard в `ZakromaView`** — вызывается vue-router ДО перехода, может сбросить state и даже предотвратить навигацию. Плюсы: явный контроль над lifecycle. Минусы: смешивает navigation guard с reset логикой; хуже читается; требует дополнительного `next()` вызова. **Отвергнут как менее идиоматичный** для Vue 3 Options API.

- **Alt-3: `watch: { $route: ... }` (на весь объект)** — следит за ЛЮБЫМ изменением `$route` (path, query, params). Плюсы: проще. Минусы: будет срабатывать на query-изменения, которых у маршрутов `/zakroma` нет (всё path-based после спеки 258). Избыточен. **Отвергнут в пользу `$route.path`**.

## R-2: Отмена активного стрима при возврате на сетку

**Decision**: переиспользовать существующий force-refresh-механизм через `loadZakromaStream({ author: this.selectedAuthor, expectedCount: undefined })`, который:
1. В store-action `loadZakromaStream` (см. `karaoke-public/src/store/modules/zakroma.js:193-264`) проверяется `lastLoadedTimestampByAuthor[author]`.
2. Если `lastTs = 0` или разница > 30 сек — force fetch, иначе dedup.
3. Перед новым fetch создаётся новый `composable.start(author, expectedCount)` — старый composable из прошлого вызова остаётся в локальной переменной store-action, теряется GC'ом и НЕ отменяется явно через AbortController.

Проблема: существующий `cancelZakromaStream()` (`ZakromaView.vue:744-767`) использует тот же force-refresh как «отмену» (см. комментарий: «обходной путь: force-refresh стрима с тем же автором → store создаст новый composable, сразу вызовет controller.abort() через dedup-bypass»). Это работает, но не идеально — старый composable остаётся живым до прихода первого batch нового стрима.

**Решение для 276**: НЕ вводить новый API отмены. Просто вызвать `backToAuthors()` ИЛИ явно `this.loadZakromaStream({ author: this.selectedAuthor, expectedCount: undefined }).catch(() => {})` ПЕРЕД `backToAuthors()`. Это совпадает с поведением существующего `cancelZakromaStream()` (см. `ZakromaView.vue:760-766`). Если прошлый fetch ещё не вернулся — он будет проигнорирован в `setZakroma` после `commit('setZakroma', [])` (state.zakroma очищается синхронно, см. `zakroma.js:208`).

**Rationale**:
- Не плодим новых store-actions без необходимости.
- Существующий код уже использует этот pattern (см. `cancelZakromaStream`).
- Если позже выяснится, что нужна явная отмена (Pass 6+ рефакторинг), это можно вынести в отдельную спеку.

**Alternatives considered**:

- **Alt-1: expose `cancelStream()` в store как отдельный action** — более чистый API, но требует держать ссылку на composable в store-state (или передавать её через `setup()`). Это уже запланировано в комментарии `ZakromaView.vue:758-759` как «Phase 6+ refactor». Не блокирует 276. **Отложено**.

## R-3: Защита от зацикливания watcher'а

**Decision**: watcher на `$route.path` в Vue по умолчанию срабатывает только при реальном изменении значения (deep: false для строки), поэтому:
- Клик на «← К списку авторов» → `router.replace('/zakroma')` → watcher срабатывает один раз → `backToAuthors()` → `router.replace('/zakroma', query: {})` → query-изменение, но path тот же → watcher НЕ срабатывает.
- Если посетитель жмёт «← К списку авторов» дважды подряд — первый раз сбрасывает state, второй раз смотрит на `zakromaHeaderBack` (computed) → возвращает `null` (т.к. `authorChosen = false`) → ссылки в шапке нет → повторных срабатываний нет.

**Rationale**: vue watch + reactive computed `zakromaHeaderBack` дают корректное поведение «из коробки» без явной защиты от рекурсии.

**Verification**: ручной e2e (см. `quickstart.md`, сценарий Q3 «двойной клик на ссылку не ломает состояние»).

## R-4: Per-feature документ (Constitution VI FR-009)

**Decision**: создать `livedocs/features/276-fix-zakroma-authors-link.md` со следующей структурой (по шаблону `livedocs/features/250-unify-site-header.md`):
- Заголовок с кратким описанием бага и фикса.
- Секция «Симптом» — что видел пользователь.
- Секция «Причина» — почему vue-router не пересоздаёт компонент.
- Секция «Решение» — какой механизм выбран (R-1) и почему (R-2, R-3).
- Секция «Связанные документы» — ссылки на spec.md, plan.md, спек 258, спек 250 (для AppHeader).
- Секция «Тест» — выдержка из `quickstart.md`.

**Rationale**: FR-009 обязывает обновлять per-feature документ при изменении кода ключевой подсистемы. `ZakromaView` — ключевая подсистема публичного сайта. Без документации через 2-3 релиза причина бага будет забыта.

## R-5: Совместимость с существующим redirect'ом в router

**Decision**: ничего не менять в `router/index.js`. Существующий `beforeEach` (см. `router/index.js:145-167`) уже корректно обрабатывает legacy `/zakroma?author=X` → `/zakroma/:authorId` и `/zakroma?specialBucket=true` → `/zakroma/special-bucket`. Эти redirect'ы остаются для backward compatibility с сохранёнными ссылками и закладками.

**Rationale**: A-3 spec.md запрещает возвращаться к query-based. Существующие redirect'ы — это компромисс: deep-link со старым URL по-прежнему работает, но активный routing — path-based. Не ломаем это.

## Резюме решений

| # | Решение | Файл |
|---|---------|------|
| R-1 | watcher на `$route.path` → `backToAuthors()` | `karaoke-public/src/views/ZakromaView.vue` |
| R-2 | без нового store-action; переиспользовать существующий force-refresh через `loadZakromaStream({ author, expectedCount: undefined })` | `karaoke-public/src/views/ZakromaView.vue` |
| R-3 | vue watch по умолчанию не рекурсивен, дополнительной защиты нет | — |
| R-4 | создать `livedocs/features/276-fix-zakroma-authors-link.md` | `livedocs/features/` |
| R-5 | `router/index.js` не трогаем | — |
