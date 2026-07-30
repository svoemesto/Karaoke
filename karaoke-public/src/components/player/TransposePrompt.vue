<template>
  <div class="transpose-prompt" @click.stop>
    <div class="transpose-prompt-content">
      <div class="transpose-prompt-title">Транспонирование — PREMIUM</div>
      <div class="transpose-prompt-desc">
        Изменяйте тональность песни на лету, чтобы петь в комфортной для вас тональности.
      </div>
      <div class="transpose-prompt-actions">
        <button class="transpose-prompt-cta" @click="goPremium">
          Оформить подписку →
        </button>
        <button class="transpose-prompt-dismiss" @click="$emit('dismiss')">
          Закрыть
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * Premium upsell prompt для функции транспонирования.
 *
 * @see docs/features/audio-transpose.md
 */
import { useRouter } from 'vue-router'

const router = useRouter()

function goPremium() {
  router.push('/premium')
}
</script>

<style scoped>
.transpose-prompt {
  position: absolute;
  bottom: 100%;
  right: 0;
  margin-bottom: 6px;
  background: #222;
  border: 1px solid #444;
  border-radius: 6px;
  padding: 14px;
  min-width: 240px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.6);
  z-index: 30;
  font-size: 13px;
  color: #eee;
}
.transpose-prompt-title {
  font-weight: 700;
  font-size: 14px;
  margin-bottom: 6px;
  color: #f80;
}
.transpose-prompt-desc {
  color: #ccc;
  margin-bottom: 12px;
  line-height: 1.4;
}
.transpose-prompt-actions {
  display: flex;
  gap: 8px;
}
.transpose-prompt-cta {
  background: #f80;
  border: none;
  color: #fff;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 600;
  font-size: 13px;
}
.transpose-prompt-cta:hover {
  background: #ff9a33;
}
.transpose-prompt-dismiss {
  background: transparent;
  border: 1px solid #666;
  color: #ccc;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.transpose-prompt-dismiss:hover {
  background: #333;
}
</style>
