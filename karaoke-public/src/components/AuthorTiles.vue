<template>
  <div class="at-grid at-modern">
    <!-- specs/307-special-authors-zakroma-order: слот #leading — доп. тайл(ы) в НАЧАЛЕ той же
         сетки, что и обычные авторы (например виртуальная плашка «Отдельные песни разных
         авторов» в ZakromaView.vue). Рендерится ПЕРЕД обычными тайлами автора — спец-плашка
         занимает первую позицию слева сверху. .at-grid растягивает/выравнивает любой прямой
         потомок одинаково, вне зависимости от того, кто его отрендерил. -->
    <slot name="leading" />
    <button
      v-for="t in tiles"
      :key="t.author"
      type="button"
      :class="['at-tile', { 'at-selected': selected === t.author }]"
      @click="$emit('select', t.author)"
    >
      <div class="at-pic">
        <img
          v-if="t.authorPictureUrl"
          :src="t.authorPictureUrl"
          class="at-img"
          loading="lazy"
          alt=""
          @error="$event.target.style.display = 'none'"
        />
      </div>
      <div class="at-namerow">
        <span class="at-name">{{ t.author }}</span>
        <span class="at-count" :title="`Песен в базе: ${t.songCount}`">{{ t.songCount }}</span>
      </div>
    </button>
    <!-- Слот #trailing оставлен для обратной совместимости с прежним поведением
         (deprecated после спеки 307 — спец-плашка теперь через #leading). -->
    <slot name="trailing" />
  </div>
</template>

<script>
/**
 * Компонент «Author Tiles».
 *
 * @emits select
 * @slot leading - доп. тайл(ы) в начале той же сетки (например спец-плашка «Отдельные
 *   песни разных авторов» в ZakromaView.vue, см. spec 307). Рендерится ПЕРВЫМ в `.at-grid`.
 * @slot trailing - доп. тайл(ы) в конце той же сетки (deprecated после spec 307,
 *   оставлен для обратной совместимости).
 *
 * @see AGENTS.md
 * @see specs/307-special-authors-zakroma-order/spec.md
 */
export default {
  name: 'AuthorTiles',
  props: {
    tiles: { type: Array, default: () => [] },
    selected: { type: String, default: '' },
  },
  emits: ['select'],
}
</script>

<style scoped>
.at-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
  padding: 4px 0 12px;
}

.at-tile {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  padding: 0;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  text-align: center;
  transition:
    transform 0.12s ease,
    box-shadow 0.12s ease,
    border-color 0.12s ease;
}
.at-tile:hover {
  transform: translateY(-2px);
}

/* Область картинки — всегда чёрный фон, превью автора (125×50) вписывается по contain */
.at-pic {
  position: relative;
  height: 64px;
  background: #000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.at-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  display: block;
}
/* Строка с именем автора и счётчиком песен */
.at-namerow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 6px 8px;
}
.at-name {
  flex: 1;
  min-width: 0;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
/* Счётчик песен — компактная пилюля, прижата вправо */
.at-count {
  flex-shrink: 0;
  padding: 1px 7px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.5;
  border-radius: 10px;
  white-space: nowrap;
}

.at-modern .at-tile {
  background: var(--km-card);
  border: 1px solid var(--km-border);
}
.at-modern .at-name {
  color: var(--km-text);
}
.at-modern .at-count {
  color: var(--km-text2);
  background: var(--km-bg2);
}
.at-modern .at-tile:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.35);
}
.at-modern .at-selected {
  border-color: var(--km-accent);
  box-shadow: 0 0 0 2px var(--km-accent);
}
</style>
