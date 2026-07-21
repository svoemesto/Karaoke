<template>
  <div class="lr-card">
    <div class="lr-icon">🔒</div>
    <h2 class="lr-title">Только для зарегистрированных</h2>
    <p class="lr-text">{{ text }}</p>
    <div class="lr-actions">
      <RouterLink
        :to="{ path: '/login', query: { redirect: $route.fullPath } }"
        class="lr-btn lr-btn-primary"
        >Войти</RouterLink
      >
      <RouterLink
        :to="{ path: '/register', query: { redirect: $route.fullPath } }"
        class="lr-btn lr-btn-secondary"
        >Регистрация</RouterLink
      >
    </div>
  </div>
</template>

<script>
/**
 * Компонент «Login Required».
 *
 * @see AGENTS.md
 */
export default {
  name: 'LoginRequired',
  props: {
    text: {
      type: String,
      default:
        'Избранное и плейлисты доступны только зарегистрированным пользователям. Войдите или зарегистрируйтесь — это бесплатно.',
    },
  },
}
</script>

<style scoped>
.lr-card {
  max-width: 440px;
  margin: 3rem auto;
  background: var(--km-card, #fff);
  color: var(--km-text, #1a1a1a);
  border: 1px solid var(--km-border, #ddd);
  border-radius: 16px;
  padding: 2rem 1.5rem;
  text-align: center;
}
.lr-icon {
  font-size: 2.5rem;
  margin-bottom: 0.5rem;
}
.lr-title {
  font-size: 1.3rem;
  font-weight: 700;
  margin: 0 0 0.6rem;
}
.lr-text {
  color: var(--km-text2, #666);
  font-size: 0.92rem;
  line-height: 1.5;
  margin: 0 0 1.5rem;
}
.lr-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
}
.lr-btn {
  padding: 0.55rem 1.4rem;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 600;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  border: 1px solid transparent;
}
.lr-btn-primary {
  background: var(--km-accent, #0077ff);
  color: #fff;
}
.lr-btn-primary:hover {
  filter: brightness(1.08);
}
.lr-btn-secondary {
  background: transparent;
  color: var(--km-accent, #0077ff);
  border-color: var(--km-accent, #0077ff);
}
.lr-btn-secondary:hover {
  background: var(--km-hover, rgba(0, 0, 0, 0.05));
}
</style>
