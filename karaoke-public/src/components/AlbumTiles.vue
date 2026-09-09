<template>
  <div class="alt-grid alt-modern">
    <!-- specs/356-zakroma-albums-by-author: слот #leading — псевдо-плашка «Все песни автора
         с группировкой по альбомам». Рендерится ПЕРВЫМ в сетке. -->
    <slot name="leading" />
    <button
      v-for="a in albums"
      :key="a.id"
      type="button"
      class="alt-tile"
      :data-album-id="a.id"
      @click="$emit('select', a.id)"
    >
      <!-- Тип альбома (studio/single/live/compilation/bootleg/archive/tribute) — бейдж НАД обложкой. -->
      <div
        v-if="albumTypeLabel(a.albumType)"
        class="alt-type-badge"
        :class="`alt-type-${a.albumType}`"
      >
        {{ albumTypeLabel(a.albumType) }}
      </div>
      <div class="alt-pic">
        <img
          v-if="a.pictureUrl"
          :src="a.pictureUrl"
          class="alt-img"
          loading="lazy"
          alt=""
          @error="$event.target.style.display = 'none'"
        />
        <span v-else class="alt-placeholder">📀</span>
      </div>
      <div class="alt-namerow">
        <span class="alt-name">
          <span v-if="a.year > 0" class="alt-year">{{ a.year }}</span>
          {{ a.name }}
        </span>
        <span class="alt-count" :title="captionTitle(a)">{{ caption(a) }}</span>
      </div>
    </button>
  </div>
</template>

<script>
/**
 * Компонент «Album Tiles» — сетка плашек альбомов для `/zakroma/{authorId}/albums`
 * и секции «Альбомы автора» на `/zakroma/{authorId}`.
 *
 * Визуальный стиль — по аналогии с `AuthorTiles.vue` (спека 307). Плашка содержит
 * обложку альбома (200×200), год выпуска, название, количество песен.
 *
 * @emits select - emitted with album id (Long) при клике на плашку
 * @slot leading - псевдо-плашка «Все песни автора с группировкой по альбомам»
 *   (рендерится ПЕРВЫМ в `.alt-grid`).
 *
 * @see specs/356-zakroma-albums-by-author/spec.md FR-001, FR-002
 * @see docs/features/zakroma-albums-by-author.md
 * @see specs/307-special-authors-zakroma-order/spec.md (паттерн `<slot name="leading" />`)
 */
export default {
  name: 'AlbumTiles',
  props: {
    /**
     * Массив альбомов (ответ API `/api/public/authors/{authorId}/albums`).
     * Каждый элемент: `{ id, name, year, pictureUrl, totalSongCount, readySongCount, albumType }`.
     */
    albums: { type: Array, default: () => [] },
    /**
     * Подпись счётчика: `ready` — «N готовых» (для гостя), `total` — «N песен» (для редактора).
     */
    countMode: {
      type: String,
      default: 'ready',
      validator: (v) => ['ready', 'total'].includes(v),
    },
  },
  emits: ['select'],
  methods: {
    /**
     * Возвращает число для подписи плашки.
     */
    count(a) {
      return this.countMode === 'total' ? a.totalSongCount : a.readySongCount
    },
    /**
     * Возвращает подпись счётчика (например, «5 готовых» или «7 песен»).
     */
    caption(a) {
      const n = this.count(a)
      const lastDigit = n % 10
      const lastTwo = n % 100
      // Простая склонялка для русского языка
      let word
      if (this.countMode === 'total') {
        if (lastTwo >= 11 && lastTwo <= 14) word = 'песен'
        else if (lastDigit === 1) word = 'песня'
        else if (lastDigit >= 2 && lastDigit <= 4) word = 'песни'
        else word = 'песен'
      } else {
        // ready
        if (lastTwo >= 11 && lastTwo <= 14) word = 'готовых'
        else if (lastDigit === 1) word = 'готовая'
        else if (lastDigit >= 2 && lastDigit <= 4) word = 'готовые'
        else word = 'готовых'
      }
      return `${n} ${word}`
    },
    /**
     * Tooltip счётчика — полная формулировка для accessibility.
     */
    captionTitle(a) {
      return this.countMode === 'total'
        ? `Всего песен в альбоме: ${a.totalSongCount}`
        : `Готовых песен (id_status >= 6): ${a.readySongCount}`
    },
    /**
     * Pass 360: маппинг AlbumType.dbValue → русская подпись для бейджа типа
     * (студийный, сингл, концертный, сборник, бутлег, архив, трибьют).
     * Соответствует AlbumType.description в karaoke-app (single source of truth).
     * Возвращает '' если тип неизвестен — бейдж тогда не рендерится.
     */
    albumTypeLabel(t) {
      if (!t) return ''
      const map = {
        studio: 'Студийный',
        single: 'Сингл',
        live: 'Концертный',
        compilation: 'Сборник',
        bootleg: 'Бутлег',
        archive: 'Архив',
        tribute: 'Трибьют',
      }
      return map[t] || ''
    },
  },
}
</script>

<style scoped>
.alt-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
  padding: 4px 0 12px;
}

.alt-tile {
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
.alt-tile:hover {
  transform: translateY(-2px);
}

.alt-pic {
  position: relative;
  aspect-ratio: 1 / 1;
  background: #000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.alt-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.alt-placeholder {
  font-size: 48px;
  color: #555;
}

.alt-namerow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 6px 8px;
}
.alt-name {
  flex: 1;
  min-width: 0;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
  /* Pass 358 fix: убираем -webkit-line-clamp, чтобы длинные названия альбомов
     не обрезались. Текст переносится по словам. */
  word-break: break-word;
  white-space: normal;
}
.alt-year {
  font-weight: 700;
  color: var(--km-accent, #4a90e2);
  margin-right: 4px;
}
.alt-count {
  flex-shrink: 0;
  padding: 1px 7px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.5;
  border-radius: 10px;
  white-space: nowrap;
}

/* Pass 360: бейдж типа альбома (студийный, сингл и т.п.) НАД обложкой. */
.alt-type-badge {
  display: block;
  text-align: center;
  padding: 3px 6px;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  color: var(--km-text2, #aaa);
  background: var(--km-bg2, #2a2a2a);
  border-bottom: 1px solid var(--km-border, #333);
  border-radius: 6px 6px 0 0;
}
/* Цветовые акценты для разных типов. */
.alt-type-studio {
  color: #4a90e2;
}
.alt-type-single {
  color: #9b59b6;
}
.alt-type-live {
  color: #e67e22;
}
.alt-type-compilation {
  color: #16a085;
}
.alt-type-bootleg {
  color: #c0392b;
}
.alt-type-archive {
  color: #7f8c8d;
}
.alt-type-tribute {
  color: #f1c40f;
}

.alt-modern .alt-tile {
  background: var(--km-card, #1a1a1a);
  border: 1px solid var(--km-border, #333);
}
.alt-modern .alt-name {
  color: var(--km-text, #eee);
}
.alt-modern .alt-count {
  color: var(--km-text2, #aaa);
  background: var(--km-bg2, #2a2a2a);
}
.alt-modern .alt-tile:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.35);
}
</style>
