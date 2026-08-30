# UI Contract: ZakromaView state и AppHeader back-link

**Дата**: 2026-08-30
**Спека**: [spec.md](spec.md)

## AppHeader.back — без изменений

Компонент `AppHeader.vue` (см. `karaoke-public/src/components/AppHeader.vue:95-144`) уже принимает prop `back` в одной из трёх форм:

```ts
// Форма 1 (используется в ZakromaView):
{ to: '/zakroma', label: '← К списку авторов' }

// Форма 2 (path + query):
{ to: '/zakroma', query: {}, label: '← К списку авторов' }

// Форма 3 (named route):
{ name: 'zakroma', label: '← К списку авторов' }
```

`AppHeader.backRouteTo` computed (см. `AppHeader.vue:118-130`) превращает prop в target для `<RouterLink :to="...">`. Vue-router сам обрабатывает клик:
- Если target — это тот же path → no-op (RouterLink не навигирует).
- Если target — другой path → vue-router навигирует.
- **По умолчанию используется `router.push`** (история браузера пополняется). Это ПРОБЛЕМА для US2: каждый клик на «← К списку авторов» плодит дубликат в истории.

### Дополнительный fix в `AppHeader`

Для соответствия FR-004 (явно требует `router.replace`, а не `push`), нужно добавить в `AppHeader.vue` поддержку флага `replace` (или всегда использовать `replace` для back-link — что безопаснее, т.к. back-link семантически означает «возврат», а не «новая страница»).

**Альтернатива**: использовать `<router-link :to="..." replace>` (встроенный механизм Vue Router). Это однострочное изменение в `<RouterLink>`.

**Контракт после фикса**:

```vue
<!-- AppHeader.vue (новое) -->
<RouterLink v-if="back" :to="backRouteTo" replace class="km-back">
  {{ back.label }}
</RouterLink>
```

Флаг `replace` говорит vue-router использовать `router.replace` вместо `router.push` для конкретной ссылки. Это не меняет prop-API `back`, не требует изменений в ZakromaView, и применяется ТОЛЬКО к back-link в шапке (другие RouterLink в `AppHeader` остаются push — например, логотип).

### Почему не менять prop `back`

Можно было бы добавить в prop `back` флаг `{ to, label, replace: true }`, но это:
- усложняет контракт (3 формы × 2 модификатора = 6 вариантов);
- создаёт обязательство для всех вызывающих кодов помнить про `replace: true`;
- `<router-link replace>` — стандартный паттерн Vue Router, который говорит сам за себя.

`<router-link replace>` — правильный уровень абстракции.

## ZakromaView — новый watcher

```js
// karaoke-public/src/views/ZakromaView.vue (новое в блоке watch: {})
watch: {
  '$route.path'(newPath, oldPath) {
    // Срабатывает при смене path между маршрутами zakroma-семейства.
    // vue-router переиспользует экземпляр компонента, поэтому data() не вызывается заново
    // — нужен явный сброс.
    //
    // Логика:
    //  - переход с любого "закромного" path на "/zakroma" (без authorId) → сброс state,
    //    показать сетку тайлов.
    //  - переход с "/zakroma" на "/zakroma/:authorId" → НЕ трогаем (state уже сброшен,
    //    выбор автора произойдёт в обработчике клика по тайлу — там вызовется
    //    loadZakromaStream). watcher не должен мешать нормальному flow.
    //  - переход между "/zakroma/:authorId" и "/zakroma/special-bucket" → не трогаем,
    //    это другой режим отображения (таблица спецзаказных), state нужно сохранять
    //    частично (см. спеку 258).
    if (newPath === '/zakroma' && oldPath !== '/zakroma') {
      // Переход с "/zakroma/N" или "/zakroma/special-bucket" → "/zakroma"
      this.backToAuthors()
    }
  },
}
```

### Почему условие `newPath === '/zakroma' && oldPath !== '/zakroma'`

- Гарантирует, что watcher срабатывает ТОЛЬКО при переходе НА сетку тайлов, а не при любом изменении path.
- Исключает ложные срабатывания на query-only изменения (которых у path-based маршрутов zakroma нет, но защита от регрессии).
- Исключает ложные срабатывания при переходе `/zakroma` → `/zakroma/:authorId` (обратное направление, где state и так пустой — выбор автора произойдёт в `onAuthorSelect`).
- Исключает ложные срабатывания при переходе между `/zakroma/:authorId` (например, навигация на конкретного автора из другого view, не из тайлов — deep-link с другого маршрута). Хотя текущий flow такого не предполагает, защита от регрессии полезна.

### Альтернативный API: явный `backToAuthors()` через route change

Можно НЕ добавлять watcher, а заменить текущие `$router.replace` в `onAuthorSelect` и `onSelectSpecialBucket` на watcher-управляемый сброс. **Отвергнуто**: это больше кода, менее прозрачно, и всё равно не решит проблему с переходом `/zakroma/50` → `/zakroma` через шапку (там нет `$router.replace` в коде `ZakromaView` — RouterLink в AppHeader навигирует сам).

## Vuex store — без изменений

Никаких новых actions/mutations/getters в `karaoke-public/src/store/modules/zakroma.js` не требуется (см. research.md R-2).

## Router — без изменений

`karaoke-public/src/router/index.js` уже корректно определяет маршруты:

```js
{ path: '/zakroma', name: 'zakroma', component: ZakromaView },
{ path: '/zakroma/:authorId(\\d+)', name: 'zakroma-author', component: ZakromaView },
{ path: '/zakroma/special-bucket', name: 'zakroma-special-bucket', component: ZakromaView },
```

Существующий `beforeEach` (см. `router/index.js:145-167`) обрабатывает legacy query-based URL. Не трогаем.

## Трекинг (observability)

Существующий `router.afterEach` (см. `router/index.js:170-174`) пишет в `tbl_events` событие `event_type=ui, link_type=navigate, link_name=<route_name_or_path>`. Это покрывает навигацию `/zakroma/50` → `/zakroma` через шапку автоматически — никаких дополнительных событий трекинга не требуется.

## Итоговый список изменений

| Файл | Изменение |
|------|-----------|
| `karaoke-public/src/views/ZakromaView.vue` | +1 watcher `'$route.path'` (~10 строк) |
| `karaoke-public/src/components/AppHeader.vue` | +1 атрибут `replace` на `<RouterLink>` для back-link (1 строка) |
| `karaoke-public/src/store/modules/zakroma.js` | без изменений |
| `karaoke-public/src/router/index.js` | без изменений |
| `livedocs/features/276-fix-zakroma-authors-link.md` | новый файл (FR-009) |

Всего ~11 строк кода + 1 per-feature документ.
