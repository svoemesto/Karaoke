<template>
  <div class="transpose-control">
    <button
      class="transpose-btn"
      :title="currentOffset !== 0 ? `Тональность: ${formatOffset(currentOffset)}` : 'Тональность'"
      @click="toggleMenu"
    >
      <span class="transpose-icon">♫</span>
      <span v-if="currentOffset !== 0 && isPremium" class="transpose-badge">{{
        formatOffset(currentOffset)
      }}</span>
    </button>

    <!-- Premium user: transpose menu -->
    <div v-if="menuOpen && isPremium" class="transpose-menu kp-menu">
      <div v-if="baseKey" class="transpose-base-key">Базовая: {{ baseKey }}</div>
      <div class="transpose-options">
        <div
          v-for="opt in options"
          :key="opt.offset"
          class="kp-menu-item"
          :class="{ active: opt.offset === currentOffset }"
          @click="selectOffset(opt.offset)"
        >
          <span>{{ opt.label }}</span>
          <span
            v-if="Math.abs(opt.offset) === 6"
            class="transpose-warning"
            title="Возможны артефакты"
            >⚠️</span
          >
        </div>
      </div>
    </div>

    <!-- Free user: premium prompt -->
    <TransposePrompt v-if="menuOpen && !isPremium" @dismiss="menuOpen = false" />
  </div>
</template>

<script setup>
/**
 * Компонент управления тональностью (transpose) для KaraokePlayer.
 *
 * Для премиум-пользователей: показывает меню выбора тональности ±6 полутонов.
 * Для бесплатных пользователей: показывает upsell prompt.
 *
 * @see docs/features/audio-transpose.md
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { getTransposeOptions } from '../../utils/musicTheory.js'
import TransposePrompt from './TransposePrompt.vue'

const props = defineProps({
  /** KaraokePlayer instance */
  player: { type: Object, required: true },
  /** Базовая тональность песни */
  baseKey: { type: String, default: null },
  /** ID песни для localStorage persistence */
  songId: { type: [String, Number], required: true },
  /** Премиум-статус пользователя */
  isPremium: { type: Boolean, default: false },
})

const menuOpen = ref(false)
const currentOffset = ref(0)

const options = computed(() => getTransposeOptions(props.baseKey || 'C major'))

function formatOffset(offset) {
  return offset > 0 ? `+${offset}` : String(offset)
}

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function selectOffset(offset) {
  currentOffset.value = offset
  if (props.player && props.player.setTransposeOffset) {
    props.player.setTransposeOffset(offset)
  }
  menuOpen.value = false
}

// Restore saved transpose from localStorage (только для премиума)
onMounted(() => {
  if (props.isPremium) {
    const saved = localStorage.getItem(`transpose_${props.songId}`)
    if (saved !== null) {
      const offset = parseInt(saved, 10)
      if (!isNaN(offset) && offset >= -6 && offset <= 6) {
        currentOffset.value = offset
        // Применяем к плееру сразу после restore
        if (props.player && props.player.setTransposeOffset) {
          props.player.setTransposeOffset(offset)
        }
      }
    }
  }

  document.addEventListener('click', handleOutsideClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleOutsideClick)
})

function handleOutsideClick(e) {
  if (!e.target.closest('.transpose-control')) {
    menuOpen.value = false
  }
}
</script>

<style scoped>
.transpose-control {
  position: relative;
}
.transpose-btn {
  background: rgba(17, 17, 17, 0.7);
  border: 1px solid #444;
  border-radius: 6px;
  padding: 4px 8px;
  line-height: 0;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  color: #ccc;
}
.transpose-btn:hover {
  background: rgba(34, 34, 34, 0.9);
}
.transpose-icon {
  font-size: 16px;
}
.transpose-badge {
  font-size: 11px;
  font-weight: 600;
  color: #f80;
}
.transpose-menu {
  position: absolute;
  bottom: 100%;
  right: 0;
  margin-bottom: 6px;
  min-width: 220px;
}
.transpose-base-key {
  padding: 6px 14px;
  color: #aaa;
  font-size: 12px;
  border-bottom: 1px solid #444;
}
.transpose-options .active {
  background: #08f;
  color: #fff;
}
.transpose-warning {
  font-size: 10px;
  margin-left: 4px;
}
</style>
