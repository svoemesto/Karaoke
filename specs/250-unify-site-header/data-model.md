# Data Model: 250 — Унификация шапки сайта

**Branch**: `250-unify-site-header` | **Date**: 2026-08-27
**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md)

## Summary

**Это UI-only рефакторинг — никаких изменений в data-модели нет.** База данных, REST API, Vuex store, entities — не затрагиваются. Существующие реактивные поля (`auth.currentUser`, `auth.isPremium`, `useCart()`, `usePlaylistMembership()`) работают как раньше.

Этот документ фиксирует:
1. **`<AppHeader>` Vue-компонент** как UI-артефакт (новый, заменяет дубли в 16 view-файлах).
2. **Существующие реактивные данные**, которые `<AppHeader>` потребляет (без изменений).

## Entities

#### `<AppHeader>` (новый Vue-component)

Тип Vue 3 SFC (`<script setup>` + `<template>` + `<style scoped>`). Расположение: `karaoke-public/src/components/AppHeader.vue`.

**Props** (типизированные, `$route.params` не используется — компонент не знает о текущем маршруте):

| Prop | Type | Default | Описание |
|------|------|---------|----------|
| `back` | `BackLink \| null` | `null` | Back-ссылка в левом слоте. См. `BackLink` ниже. |
| `profileLink` | `BackLink \| null` | `null` | Ссылка «Профиль →» в правом слоте (между AuthWidget и theme toggle). |
| `showAuthWidget` | `boolean` | `true` | Показывать ли `AuthStatusWidget` справа. |
| `showThemeToggle` | `boolean` | `true` | Показывать ли переключатель темы справа. |
| `sticky` | `boolean` | `true` | `position: sticky; top: 0` для шапки. |
| `logoSrc` | `string` | `'/KARAOKE_LOGO.png'` | Путь к логотипу (hardcoded default; для будущего мультибренда — параметризуемый). |
| `logoAlt` | `string` | `'Своё Место'` | Alt-текст для a11y. |
| `maxWidth` | `string` | `'900px'` | `max-width` контейнера `.km-header-inner`. |

**Slots**:

| Slot | Описание |
|---|---|
| `left` | Перебивает prop `back`. Для кастомных левых слотов (например, цепочка ссылок). |
| `center` | Контент в центре (для `EditorWorkView`: заголовок песни + автор). |
| `right` | Перебивает prop `profileLink` + widgets. Для кастомных правых слотов. |

**Приоритет**: slot перебивает prop (если задан `slot="left"`, prop `back` игнорируется).

#### `BackLink` (тип, не entity)

Просто тип для props:

```ts
type BackLink = {
  to: string                    // router path
  label: string                 // текст ссылки ('← Главная', '← Закрома')
  query?: Record<string, string> // опциональный query (для AuthorPlaylistView)
}
```

Не хранится в store, не реактивен — pure data, передаётся через props.

#### Потребляемые реактивные данные (existing, no changes)

| Источник | Поля | Использование в `<AppHeader>` |
|----------|------|------------------------------|
| `useAuth()` composable | `user`, `isPremium` | Передаются в `AuthStatusWidget` через slot. |
| `useDesign()` composable | `theme`, `applyTheme` | Theme toggle (3 кнопки). |
| `useCart()` composable | — | Не используется в шапке (только в SearchView). |
| Vue Router | `route.name`, `route.path` | Не используется в `<AppHeader>` напрямую (но logoLink = `/` hardcoded). |

## Validation Rules (внутри `<AppHeader>`)

- Если `back` и `slot="left"` оба заданы — warning в dev-mode (slot имеет приоритет).
- Если `logoSrc` пустой — fallback на inline SVG-плейсхолдер (не блокирует UI).
- Если `theme === undefined` (composition не инициализирован) — theme toggle скрывается (через `v-if="theme"`).

## State Transitions

Нет — `<AppHeader>` stateless. Реактивность наследуется от дочерних компонентов (`AuthStatusWidget` реактивен на `auth.isPremium` через `usePremiumLiveSync`).

## Relationships

```
<HomeView>          ─→ <AppHeader :back="null" :profile-link="null">
<SearchView>        ─→ <AppHeader :back="{ to: '/', label: '← Главная' }">
<ZakromaView>       ─→ <AppHeader :back="{ to: '/', label: '← Главная' }">
<SongView>          ─→ <AppHeader :back="{ to: '/zakroma', label: '← Назад' }">
<AboutView>         ─→ <AppHeader :back="{ to: '/', label: '← Главная' }">
<NewsView>          ─→ <AppHeader :back="{ to: '/', label: '← Главная' }">
<PremiumView>       ─→ <AppHeader :back="{ to: '/', label: '← Главная' }">
<LoginView>         ─→ <AppHeader :back="{ to: '/', label: '← Главная' }">
<RegisterView>      ─→ <AppHeader :back="{ to: '/', label: '← Главная' }">
<OfertaView>        ─→ <AppHeader :back="{ to: '/', label: '← Главная' }">
<AccountView>       ─→ <AppHeader :back="{ to: '/', label: '← Главная' }">
<EditorTasksView>   ─→ <AppHeader :back="{ to: '/account', label: '← Личный кабинет' }" :show-auth-widget="false" :show-theme-toggle="false">
<EditorWorkView>    ─→ <AppHeader :sticky="false">  <!-- slot-based, см. slot center/right -->
                            <template #left><RouterLink to="/account/editor">← Мои задания</RouterLink></template>
                            <template #center>...заголовок песни...</template>
                            <template #right>...статус-бейдж...</template>
                          </AppHeader>
<PlaylistEditView>  ─→ <AppHeader :back="{ to: '/account/playlists', label: '← Мои плейлисты' }">
<AuthorPlaylistView>─→ <AppHeader :back="{ to: '/zakroma', label: '← Закрома', query: { author: $route.params.slug } }">
<PlaylistsView>     ─→ <AppHeader :back="{ to: '/', label: '← Главная' }" :profile-link="{ to: '/account', label: 'Профиль →' }">
<ChatView>          ─→ <AppHeader :back="{ to: '/', label: '← Главная' }" :profile-link="{ to: '/account', label: 'Профиль →' }">
<CartView>          ─→ <AppHeader :back="{ to: '/', label: '← Главная' }">
<StemJobsView>      ─→ <AppHeader :back="{ to: '/account', label: '← Личный кабинет' }">
<SubscriptionsView> ─→ <AppHeader :back="{ to: '/account', label: '← Личный кабинет' }">

(НЕ используют <AppHeader> по FR/A-002, A-003):
<PlayerView>        ─→ no header
<ShareView>         ─→ no header
<SubscriptionReturnView> ─→ no header
```

## Out-of-Scope

- Изменения Vuex-стора — не требуются.
- Изменения API endpoints — не требуются.
- Изменения БД — не требуются.
- Новые сущности (entity classes) — не вводятся.

## Next Step

→ [contracts/AppHeader-component.md](contracts/AppHeader-component.md) — детальный API-контракт компонента (props/slots/events), [quickstart.md](quickstart.md) — manual smoke-test guide.