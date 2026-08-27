# Data Model: 254 — header-back-link «К списку авторов»

**Branch**: `254-fix-zakroma-header-back-link` | **Date**: 2026-08-27

## Резюме

UI-template-фикс в одном файле `Zakroma-public/src/views/ZakromaView.vue`.
Никаких новых сущностей / полей / моделей данных не вводится.

## Что делается

| Изменение | Было | Стало |
|-----------|------|-------|
| `AppHeader` `:back` prop | статический `{ to: '/', label: '← Главная' }` (всегда) | динамический через `computed zakromaHeaderBack`: `null` или `{ to: '/zakroma', label: '← К списку авторов' }` |
| In-page `<button class="km-back-btn">` × 2 | видимы при `authorChosen / isSpecialBucketSelected` | удалены |
| `.km-back-btn` стили в scoped CSS | 4 правила (`@media (max-width: 768px)` и т.д.) | удалены (не используются) |

## Что НЕ меняется

| Слой | Файл / артефакт | Почему не трогаем |
|------|------------------|--------------------|
| API AppHeader | `karaoke-public/src/components/AppHeader.vue` | Уже поддерживает `back: Object, default: null` + `v-if="back"` в рендере (`AppHeader.vue:96` + `:6`). Никаких новых props не нужно. |
| Router config | `karaoke-public/src/router/*.js` | Маршруты `/zakroma` и `/zakroma?author=X` уже существуют. |
| Backend | `karaoke-web/`, `karaoke-app/` | Не задействованы. |
| DTO | `ZakromaPublicDto.kt` и др. | Без изменений. |
| Vuex store | `karaoke-public/src/store/modules/zakroma.js` | Не задействован. |
| Метод `backToAuthors()` | в `ZakromaView.vue:methods` | Остаётся, может использоваться в будущем для программной навигации. Клик на header-back-link вызывает vue-router → view сама сбрасывает state через watcher / mounted, метод не требуется для самого перехода. |
| Другие view | `AuthorPlaylistView.vue`, `SongView.vue`, `SearchView.vue`, `AccountView.vue` | Каждый имеет свой `:back` prop; наша правка только в `ZakromaView.vue`. |

## Валидация

Визуальная + DOM-проверка:
```js
// Без автора
document.querySelectorAll('.km-header-left .km-back').length  // 0

// С автором
document.querySelectorAll('.km-header-left .km-back').length  // 1
document.querySelector('.km-back').textContent.trim()         // '← К списку авторов'
document.querySelector('.km-back').getAttribute('href')       // '/zakroma'

// В теле страницы (под AppHeader)
document.querySelectorAll('.km-back-btn').length              // 0
```

## Стейт-машина

Не применимо — фикс чисто декларативный (Vue computed). Нет `v-if`-переключений стейт-машины, нет watchers, нет actions.
