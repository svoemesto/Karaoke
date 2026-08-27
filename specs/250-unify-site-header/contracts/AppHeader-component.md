# Component Contract: `<AppHeader>`

**Branch**: `250-unify-site-header` | **Date**: 2026-08-27
**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md) | **Data Model**: [data-model.md](data-model.md)

## Summary

`<AppHeader>` — Vue 3 single-file component, рендерит шапку публичного сайта `karaoke-public`. Заменяет дублированный `<header class="km-header">` блок в 16 view-файлах.

Расположение: `karaoke-public/src/components/AppHeader.vue`.

## Props (типизированные, runtime-validated в dev)

```ts
type BackLink = {
  to: string                       // router path, например '/zakroma'
  label: string                    // видимый текст, например '← Закрома'
  query?: Record<string, string>   // опциональный query (AuthorPlaylistView)
}

defineProps({
  // Left slot
  back: { type: Object as PropType<BackLink | null>, default: null },
  // Right slot
  profileLink: { type: Object as PropType<BackLink | null>, default: null },
  // Toggles (right slot)
  showAuthWidget: { type: Boolean, default: true },
  showThemeToggle: { type: Boolean, default: true },
  // Layout
  sticky: { type: Boolean, default: true },
  // Logo (rarely overridden)
  logoSrc: { type: String, default: '/KARAOKE_LOGO.png' },
  logoAlt: { type: String, default: 'Своё Место' },
  maxWidth: { type: String, default: '900px' },
})
```

## Slots

| Slot | Default content | Перебивает prop |
|------|-----------------|------------------|
| `left` | `<RouterLink>` из `back.to`/`back.label` (если `back` не null) | `back` prop игнорируется |
| `center` | пусто | — |
| `right` | `profileLink` → `AuthStatusWidget` → theme toggle (если включены) | `profileLink`, `showAuthWidget`, `showThemeToggle` игнорируются |

**Приоритет**: slot > prop. Если задан `slot="left"` и одновременно `back` prop — используется slot, prop игнорируется (с dev-warning).

## Events

Нет событий наружу — `<AppHeader>` stateless. Дочерние компоненты (`AuthStatusWidget`, theme toggle) могут emit'ить свои события, но это их API.

## DOM contract

Гарантированная структура (для CSS-стилей и тестов):

```html
<header class="km-header" :class="{ 'km-header-sticky': sticky }">
  <div class="km-header-inner" :style="{ maxWidth }">
    <div class="km-header-left">
      <!-- slot=left OR back prop -->
    </div>
    <div class="km-header-center">
      <!-- slot=center -->
    </div>
    <div class="km-header-right">
      <!-- profileLink + AuthStatusWidget + theme toggle OR slot=right -->
      <RouterLink to="/" class="km-logo-link">
        <img :src="logoSrc" :alt="logoAlt" class="km-logo" />
      </RouterLink>
    </div>
  </div>
</header>
```

**Гарантии**:
- `<header>` ВСЕГДА имеет class `km-header`.
- `<img class="km-logo">` ВСЕГДА в `.km-header-right` (даже если визуально меняется порядок внутри).
- `<RouterLink to="/">` ВСЕГДА оборачивает `<img>` (логотип всегда кликабелен → home).
- `<div class="km-header-left">` рендерится ВСЕГДА (даже если пуст — для flex-layout consistency).
- `<div class="km-header-center">` рендерится ВСЕГДА (для возможности slot=center).

## CSS contract

Классы, экспортируемые `<AppHeader>` (могут быть использованы родителями через `:deep()`):

- `.km-header` — контейнер, sticky по умолчанию.
- `.km-header-inner` — flex-контейнер.
- `.km-header-left`, `.km-header-center`, `.km-header-right` — три слота.
- `.km-back` — стиль back-ссылки (используется внутри default slot left).
- `.km-logo-link` — `<a>`/`<RouterLink>` вокруг лого.
- `.km-logo` — `<img>` лого.
- `.km-logo-large` — модификатор для главной (max-width: 200px вместо 100px).
- `.km-theme-toggle` — контейнер 3 кнопок light/system/dark.
- `.km-tb` — отдельная кнопка темы.

**CSS-переменные** (используются внутри, `--km-*` определены в общем `style.css`):
- `--km-header` (фон)
- `--km-border` (нижняя граница)
- `--km-accent` (цвет ссылок back/profileLink)

## Usage Examples

### TypeScript-style usage (Vue 3 `<script setup>`)

```vue
<template>
  <AppHeader :back="{ to: '/', label: '← Главная' }" />
  <main>...</main>
</template>

<script setup>
import AppHeader from '@/components/AppHeader.vue'
</script>
```

### Options API usage (для существующих view-файлов)

```vue
<template>
  <AppHeader :back="back" :profile-link="profileLink" />
</template>

<script>
import AppHeader from '@/components/AppHeader.vue'

export default {
  components: { AppHeader },
  data() {
    return {
      back: { to: '/zakroma', label: '← Закрома' },
      profileLink: null,
    }
  },
}
</script>
```

### Slot-based (EditorWorkView)

```vue
<template>
  <AppHeader :sticky="false">
    <template #left>
      <RouterLink to="/account/editor" class="km-back">← Мои задания</RouterLink>
    </template>
    <template #center>
      <span class="km-song-title">{{ task.songName }}</span>
      <span class="km-song-author">{{ task.author }}</span>
    </template>
    <template #right>
      <span v-if="task" class="km-status-badge" :class="`km-status-${status}`">
        {{ statusLabel }}
      </span>
    </template>
  </AppHeader>
</template>
```

### AuthorPlaylistView с query

```vue
<template>
  <AppHeader
    :back="{ to: '/zakroma', label: '← Закрома', query: { author: $route.params.slug } }"
  />
</template>
```

## Tests / Verification

**Unit-тесты не пишутся** (Constitution § «Рабочий процесс»: тесты в CI нет, существующие `@Disabled`). Smoke-test — ручной (см. [quickstart.md](quickstart.md)).

**Где проверить компонент**:
1. Открыть `karaoke-public/src/components/AppHeader.vue` — props/slots соответствуют этому контракту.
2. В DevTools открыть любую страницу — DOM соответствует DOM contract выше.
3. `grep -rn "km-header\|km-back\|km-logo" karaoke-public/src/views/ --include="*.vue"` — пусто (только в `<AppHeader>.vue`).

## Migration Path (для существующих view-файлов)

**Шаблон миграции** для каждого view:

1. **Было** (пример `SearchView.vue`):
   ```vue
   <template>
     <header class="km-header">
       <div class="km-header-inner">
         <div class="km-header-left">
           <RouterLink to="/" class="km-back">← Главная</RouterLink>
           <a href="/"><img src="/KARAOKE_LOGO.png" class="km-logo" alt="Karaoke logo" /></a>
         </div>
         <div class="km-header-right">
           <AuthStatusWidget />
           <div class="km-theme-toggle">...</div>
         </div>
       </div>
     </header>
   </template>
   <style scoped>
   .km-header { ... }
   .km-header-inner { ... }
   .km-header-left { ... }
   .km-back { ... }
   </style>
   ```

2. **Стало**:
   ```vue
   <template>
     <AppHeader :back="{ to: '/', label: '← Главная' }" />
   </template>
   <script>
   import AppHeader from '@/components/AppHeader.vue'
   export default { components: { AppHeader } }
   </script>
   <style scoped>
   /* удалено: .km-header, .km-header-inner, .km-header-left, .km-back */
   </style>
   ```

## Out-of-Scope (напоминание)

- Изменения в `<AuthStatusWidget>` — не требуются (используется as-is через slot/prop).
- Изменения в `useAuth`, `useDesign` composables — не требуются.
- Изменения в Vue Router — не требуются.

## Next Step

→ [quickstart.md](quickstart.md) — manual smoke-test guide.