<template>
  <button
    type="button"
    class="share-trigger"
    :disabled="!isPremium"
    :title="
      isPremium ? 'Создать временную ссылку для друзей' : 'Доступно только для премиум-аккаунтов'
    "
    @click="modalVisible = true"
  >
    <span class="share-icon">🔗</span>
    Временный доступ
  </button>

  <ShareLinkModal
    v-if="modalVisible"
    :visible="modalVisible"
    :song-id="songId"
    @close="modalVisible = false"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import { useAuth } from '../composables/useAuth'
import ShareLinkModal from './ShareLinkModal.vue'

defineProps({
  songId: { type: [Number, String], required: true },
})

// isPremiumUser живёт в usePlayerAccess (результат /api/public/player/{id}/access), но
// ради одной кнопки мы его не дёргаем. Просто проверяем наличие токена — залогинен ли
// пользователь. Если серверно окажется не-премиум — бэкенд отдаст 403 share.notOwner,
// и UI обработает ошибку через общий error flow в ShareLinkModal.
const { token } = useAuth()
const isPremium = computed(() => !!token.value)

const modalVisible = ref(false)
</script>

<!-- Pill-стиль кнопки — точная копия .km-meta-actions :deep(.share-trigger) из SongView.vue.
     Стили пришлось продублировать прямо здесь, потому что Vue scoped-CSS применяет
     :deep(...) только к дочерним компонентам с data-v-... атрибутом, а <button> внутри
     ShareLinkButton — это «внук» SongView, data-v-... атрибут ему не достаётся, и
     :deep(.share-trigger) не подхватывает его. Дубль pill-стиля здесь. -->
<style scoped>
.share-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0.4rem 0.85rem;
  background: var(--km-bg);
  border: 1px solid var(--km-border);
  border-radius: 999px;
  color: var(--km-text);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  transition:
    border-color 0.15s,
    background 0.15s;
}
.share-trigger:hover:not(:disabled) {
  border-color: var(--km-accent);
}
.share-trigger:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.share-icon {
  font-size: 1em;
}
</style>
