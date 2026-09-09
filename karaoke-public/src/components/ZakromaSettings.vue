<template>
  <div class="km-zakroma-settings">
    <div class="km-zakroma-settings-row">
      <label class="km-zakroma-settings-label">
        Размер плашек: <strong>{{ tileSize }}px</strong>
      </label>
      <input
        type="range"
        :min="MIN_TILE_SIZE"
        :max="MAX_TILE_SIZE"
        :step="TILE_SIZE_STEP"
        :value="tileSize"
        class="km-zakroma-slider"
        @input="onSliderInput"
      />
    </div>
    <div class="km-zakroma-settings-row">
      <span class="km-zakroma-settings-label">Режим:</span>
      <div class="km-zakroma-toggle">
        <button
          type="button"
          :class="['km-tb', { active: viewMode === VIEW_MODE_TILES }]"
          title="Плашки"
          @click="setViewMode(VIEW_MODE_TILES)"
        >
          ▦ Плашки
        </button>
        <button
          type="button"
          :class="['km-tb', { active: viewMode === VIEW_MODE_TABLE }]"
          title="Таблица"
          @click="setViewMode(VIEW_MODE_TABLE)"
        >
          ☰ Таблица
        </button>
      </div>
    </div>
  </div>
</template>

<script>
/**
 * Панель настроек Закромов (спека 356, US4+US5).
 * Размер плашек + переключатель режима. Сохраняет в `useZakromaSettings.js`
 * (singleton refs, общие для /zakroma и /zakroma/{authorId}/albums).
 *
 * @see specs/356-zakroma-albums-by-author/spec.md FR-012, FR-013
 */
import {
  useZakromaSettings,
  MIN_TILE_SIZE,
  MAX_TILE_SIZE,
  TILE_SIZE_STEP,
  VIEW_MODE_TILES,
  VIEW_MODE_TABLE,
} from '../composables/useZakromaSettings.js'

export default {
  name: 'ZakromaSettings',
  data() {
    return {
      MIN_TILE_SIZE,
      MAX_TILE_SIZE,
      TILE_SIZE_STEP,
      VIEW_MODE_TILES,
      VIEW_MODE_TABLE,
    }
  },
  computed: {
    tileSize() {
      return this.zakromaSettings.tileSize.value
    },
    viewMode() {
      return this.zakromaSettings.viewMode.value
    },
  },
  created() {
    this.zakromaSettings = useZakromaSettings()
  },
  methods: {
    onSliderInput(e) {
      const v = parseInt(e.target.value, 10)
      if (Number.isFinite(v)) this.setTileSize(v)
    },
    setTileSize(v) {
      this.zakromaSettings.setTileSize(v)
    },
    setViewMode(v) {
      this.zakromaSettings.setViewMode(v)
    },
  },
}
</script>

<style scoped>
.km-zakroma-settings {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 8px 12px;
  background: var(--km-card, #1a1a1a);
  border: 1px solid var(--km-border, #333);
  border-radius: 6px;
  margin: 8px 16px;
}
.km-zakroma-settings-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.km-zakroma-settings-label {
  font-size: 12px;
  color: var(--km-text2, #aaa);
}
.km-zakroma-slider {
  width: 160px;
  accent-color: var(--km-accent, #4a90e2);
}
.km-zakroma-toggle {
  display: inline-flex;
  border: 1px solid var(--km-border, #333);
  border-radius: 6px;
  overflow: hidden;
}
.km-tb {
  padding: 4px 10px;
  background: transparent;
  border: none;
  color: var(--km-text2, #aaa);
  font-size: 12px;
  cursor: pointer;
}
.km-tb.active {
  background: var(--km-accent, #4a90e2);
  color: white;
}
</style>
