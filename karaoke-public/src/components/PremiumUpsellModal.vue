<template>
  <teleport to="body">
    <div v-if="state.open" class="pu-overlay" @click.self="close">
      <div class="pu-card" role="dialog" aria-modal="true">
        <button class="pu-close" title="Закрыть" @click="close">×</button>
        <div class="pu-badge">🪙 Премиум</div>
        <h3 class="pu-title">{{ state.title }}</h3>
        <p v-if="state.message" class="pu-message">{{ state.message }}</p>
        <ul v-if="state.benefits.length" class="pu-benefits">
          <li v-for="(b, i) in state.benefits" :key="i">{{ b }}</li>
        </ul>
        <div class="pu-actions">
          <RouterLink to="/account" class="pu-btn pu-btn-primary" @click="close"
            >Мой профиль</RouterLink
          >
          <button class="pu-btn pu-btn-secondary" @click="close">Понятно</button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script>
import { usePremiumModal } from '../composables/usePremiumModal'

/**
 * Модальное окно для upsell.
 *
 * @see AGENTS.md
 */

export default {
  name: 'PremiumUpsellModal',
  setup() {
    const { state, close } = usePremiumModal()
    return { state, close }
  },
}
</script>

<style scoped>
.pu-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3000;
  padding: 1rem;
}
.pu-card {
  position: relative;
  width: 100%;
  max-width: 440px;
  background: var(--km-card, #fff);
  color: var(--km-text, #1a1a1a);
  border: 1px solid var(--km-border, #ddd);
  border-radius: 14px;
  padding: 1.5rem 1.5rem 1.25rem;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}
.pu-close {
  position: absolute;
  top: 8px;
  right: 12px;
  background: transparent;
  border: none;
  font-size: 1.5rem;
  line-height: 1;
  color: var(--km-text2, #888);
  cursor: pointer;
}
.pu-badge {
  display: inline-block;
  background: linear-gradient(135deg, #f6c94b, #d99413);
  color: #5a3c00;
  font-weight: 700;
  font-size: 0.8rem;
  padding: 0.25rem 0.7rem;
  border-radius: 20px;
  margin-bottom: 0.75rem;
}
.pu-title {
  font-size: 1.15rem;
  font-weight: 700;
  margin: 0 0 0.5rem;
}
.pu-message {
  font-size: 0.9rem;
  color: var(--km-text2, #555);
  margin: 0 0 0.75rem;
}
.pu-benefits {
  list-style: none;
  padding: 0;
  margin: 0 0 1.25rem;
}
.pu-benefits li {
  position: relative;
  padding-left: 1.5rem;
  margin-bottom: 0.45rem;
  font-size: 0.88rem;
  line-height: 1.35;
}
.pu-benefits li::before {
  content: '✓';
  position: absolute;
  left: 0;
  color: #d99413;
  font-weight: 700;
}
.pu-actions {
  display: flex;
  gap: 0.6rem;
  justify-content: flex-end;
}
.pu-btn {
  padding: 0.5rem 1.1rem;
  border-radius: 8px;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
}
.pu-btn-primary {
  background: var(--km-accent, #0077ff);
  color: #fff;
}
.pu-btn-primary:hover {
  filter: brightness(1.08);
}
.pu-btn-secondary {
  background: transparent;
  color: var(--km-text2, #555);
  border-color: var(--km-border, #ccc);
}
.pu-btn-secondary:hover {
  background: var(--km-hover, rgba(0, 0, 0, 0.05));
}
</style>
