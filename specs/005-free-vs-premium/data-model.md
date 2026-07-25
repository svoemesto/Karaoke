# Data Model: Таблица «FREE vs PREMIUM» на /premium (QW-1)

**Branch**: `005-free-vs-premium` | **Phase**: 1 | **Date**: 2026-07-25

Фича не вводит новых сущностей БД (`spec.md` → Key Entities, FR-011: список
фичей в исходниках). Ниже — статическая структура данных таблицы и
переиспользуемые механизмы.

---

## Статическая (не БД) сущность: COMPARISON_ROWS

Hardcoded массив в `PremiumView.vue` (аналогично `REASONS` в
`004-reasons-to-register`, `TOP_AUTHORS` в `003-about-page`):

| Поле | Тип | Пример |
|------|-----|--------|
| `feature` | String | `Избранное` |
| `free` | String \| Boolean | `"до 100"` или `true`/`false` |
| `premium` | String \| Boolean | `"до 500"` или `true`/`false` |
| `highlight` | Boolean (опц.) | выделить строку (для будущего использования, не обязателен в MVP) |

9 записей — финальный список после верификации (см. `research.md` Decision 1,
`spec.md` FR-003):

1. Онлайн-плеер для песен «в эфире» — `true` / `true`
2. Поиск и каталог — `true` / `true`
3. Демо-фрагменты эксклюзивных — `true` / `true`
4. Полный доступ к плееру (все песни) — `false` / `true`
5. Избранное — `"до 100"` / `"до 500"`
6. Свои плейлисты — `"1 (избранное)"` / `"до 50"`
7. Непрерывное воспроизведение, повтор, случайный порядок — `false` / `true`
8. Создание минусовок (Demucs) — `false` / `true`
9. Чат с автором проекта — `false` / `true`

---

## Переиспользуемый механизм 1: определение FREE/PREMIUM

Источник: `composables/useAuth.js` → `{ user }`, поле `user.effectivePremium`
(Boolean). Подтверждено использованием в 8 файлах `karaoke-public`
(`SearchView.vue`, `ZakromaView.vue`, `PlaylistsView.vue`, `ChatView.vue`,
`AccountView.vue`, `StemJobsView.vue`, `ChatUnreadBadge.vue`,
`PlaylistIcon.vue`) — тот же паттерн `!!(this.user && this.user.effectivePremium)`.

**Использование в фиче**: `computed: { isPremium() { return
!!(this.user && this.user.effectivePremium) } }` — переключает между полной
таблицей (не-премиум) и упрощённым видом «Вы премиум» (US2).

---

## Переиспользуемый механизм 2: трекинг клика по CTA

Источник: `services/tracking.js` → `trackUi(subtype, detail)` →
`POST /api/public/events`. Тот же паттерн, что и в `004-reasons-to-register`.

**Использование в фиче**: `trackUi('navigate', 'free_vs_premium_cta')` при
клике на CTA «Оформить премиум-подписку» (FR-008, US3).

---

## Реальные backend-константы, на которые опирается контент таблицы (не API-вызов)

Источник: `karaoke-web/.../controllers/PublicPlaylistController.kt`,
`companion object`:

| Константа | Значение | Строка таблицы |
|-----------|----------|-----------------|
| `FREE_FAVORITES_LIMIT` | 100 | «Избранное», колонка FREE |
| `PREMIUM_ITEMS_LIMIT` | 500 | «Избранное»/«Свои плейлисты», колонка PREMIUM (лимит песен на плейлист) |
| `PREMIUM_PLAYLIST_LIMIT` | 50 | «Свои плейлисты», колонка PREMIUM (лимит числа плейлистов) |

Эти числа **не запрашиваются** через API (см. `research.md` Decision 2) —
записаны как текст в hardcoded-массиве. Если константы в бэкенде изменятся,
таблицу нужно обновить вручную (единственный источник рассинхронизации,
осознанно принятый спекой FR-011).

---

## Что НЕ является сущностью этой фичи

- Новых таблиц/колонок/миграций — нет.
- Новых REST-эндпоинтов — нет.
- `tbl_price_tariffs`/`tbl_subscriptions` — упомянуты в `spec.md` Key
  Entities, но используются существующим блоком выбора тарифа в
  `PremiumView.vue` (`useSiteSubscription()`), который эта фича не трогает —
  таблица добавляется **над** ним, не взаимодействует с его данными.
