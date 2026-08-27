# Contract: `AuthStatusWidget` integration

> Файл: `karaoke-public/src/components/AuthStatusWidget.vue`
> Изменение: добавить inline-бейдж непрочитанных новостей внутрь ссылки «Новости».

## DOM diff

### Before (текущее состояние)

```html
<RouterLink to="/news" class="km-auth-link km-auth-link-news">Новости</RouterLink>
```

### After

```html
<RouterLink to="/news" class="km-auth-link km-auth-link-news">
  Новости
  <span
    v-if="showBadge"
    class="km-news-badge"
    :aria-label="ariaLabel"
  >{{ badgeText }}</span>
</RouterLink>
```

## Reactive bindings

```js
import { useNewsUnreadCount } from '../composables/useNewsUnreadCount'

export default {
  name: 'AuthStatusWidget',
  setup() {
    const { user, isLoggedIn } = useAuth()
    const { count: cartCount, load: loadCart } = useCart()
    const { badgeText, ariaLabel, showBadge } = useNewsUnreadCount()
    if (isLoggedIn.value) loadCart()
    return { user, isLoggedIn, cartCount, badgeText, ariaLabel, showBadge }
  },
  // ... rest unchanged
}
```

## CSS additions

В `<style scoped>` секцию добавить:

```css
.km-news-badge {
  display: inline-block;
  background: #e05555;
  color: #fff;
  font-size: 0.68rem;
  font-weight: 700;
  border-radius: 10px;
  padding: 0 0.4em;
  margin-left: 0.3em;
  vertical-align: top;
  min-width: 1.2em;
  text-align: center;
  line-height: 1.4;
}
```

> Стиль согласован с существующим `.km-cart-count` (тот же автор, та же палитра) — пользователь видит одинаковый визуальный язык для всех inline-бейджей в шапке.

## Constraints (валидируются после имплементации)

- ✅ `badgeText` имеет ненулевое значение → `showBadge = true` → `<span>` существует в DOM.
- ✅ На узких экранах (≤ 700px) `<RouterLink>` скрыт → `<span>` тоже скрыт (наследование `display: none` от родителя).
- ✅ `aria-label` обновляется реактивно при изменении `count`.
- ✅ Polling работает независимо от того, смонтирован ли `<AuthStatusWidget>` (singleton на module-level).
- ✅ На editor-страницах `<AuthStatusWidget>` не рендерится (spec 250, FR-011/FR-014) → бейдж не виден автоматически.
