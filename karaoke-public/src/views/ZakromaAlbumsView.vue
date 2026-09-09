<template>
  <div class="km-page zakroma-albums-page">
    <!-- Хедер единый (spec 250). Back-link → /zakroma (список авторов). -->
    <AppHeader :back="{ to: '/zakroma', label: '← К списку авторов' }" />

    <!-- specs/356-zakroma-albums-by-author (US4+US5): панель настроек (слайдер размера + плашки/таблица). -->
    <ZakromaSettings />

    <div class="km-content">
      <!-- Заголовок страницы -->
      <h1 class="km-page-title">
        Альбомы автора
        <span v-if="authorName" class="km-page-title-author">{{ authorName }}</span>
      </h1>

      <!-- Загрузка -->
      <div v-if="loading" class="km-loading">Загружаем альбомы…</div>

      <!-- Ошибка -->
      <div v-else-if="error" class="km-error" role="alert">
        {{ error }}
        <button type="button" class="km-btn" @click="loadAlbums">Повторить</button>
      </div>

      <!-- Пустое состояние -->
      <div v-else-if="albums.length === 0" class="km-empty">У этого автора пока нет альбомов.</div>

      <!-- Сетка плашек альбомов + псевдо-плашка «Все песни» через #leading -->
      <AlbumTiles v-else :albums="albums" :count-mode="countMode" @select="onAlbumSelect">
        <!-- Псевдо-плашка «Все песни автора с группировкой по альбомам» — ПЕРВЫЙ элемент сетки
             (слот #leading в AlbumTiles.vue). Ведёт на /zakroma/{authorId} без фильтра.
             @see specs/356-zakroma-albums-by-author/spec.md FR-002, Clarification Q2 -->
        <template #leading>
          <button type="button" class="alt-tile km-pseudo-tile" @click="onPseudoTileClick">
            <div class="alt-pic">
              <span class="km-pseudo-tile-icon">📂</span>
            </div>
            <div class="alt-namerow">
              <span class="alt-name">Все песни автора с группировкой по альбомам</span>
            </div>
          </button>
        </template>
      </AlbumTiles>
    </div>
  </div>
</template>

<script>
import AppHeader from '../components/AppHeader.vue'
import AlbumTiles from '../components/AlbumTiles.vue'
import ZakromaSettings from '../components/ZakromaSettings.vue'

/**
 * Страница «Альбомы автора» (`/zakroma/:authorId/albums`, спека 356).
 *
 * Показывает сетку плашек альбомов конкретного автора. Первая — псевдо-плашка
 * «Все песни автора с группировкой по альбомам» (ведёт на `/zakroma/{authorId}` без фильтра).
 *
 * Клик по плашке альбома → `/zakroma/{authorId}?album={albumId}` (страница песен с фильтром).
 *
 * @see specs/356-zakroma-albums-by-author/spec.md FR-002
 * @see docs/features/zakroma-albums-by-author.md
 */
export default {
  name: 'ZakromaAlbumsView',
  components: { AppHeader, AlbumTiles, ZakromaSettings },
  data() {
    return {
      albums: [],
      authorName: '',
      loading: true,
      error: null,
    }
  },
  computed: {
    /**
     * ID автора из URL. Vue-router regex `\d+` гарантирует, что это число.
     */
    authorId() {
      return parseInt(this.$route.params.authorId, 10)
    },
    /**
     * Режим подписи счётчика: для гостя — «N готовых», для редактора — «N песен».
     * Определяется по наличию auth-токена в localStorage (быстрая синхронная проверка).
     * Редактор видит больше альбомов (бэкенд отдаёт их по флагу `onlyPublished=false`).
     */
    countMode() {
      // Простая эвристика: если в localStorage есть токен — пользователь залогинен
      // (не обязательно редактор, но сервер разберётся через SessionUtil).
      // Это используется ТОЛЬКО для подписи; реальный фильтр — на бэкенде (FR-010/011).
      return localStorage.getItem('km_auth_token') ? 'total' : 'ready'
    },
  },
  watch: {
    /**
     * Перезагрузка при смене authorId в URL (например, навигация между авторами).
     */
    '$route.params.authorId': {
      handler() {
        this.loadAlbums()
      },
    },
  },
  mounted() {
    this.loadAlbums()
  },
  methods: {
    /**
     * Загружает список альбомов из публичного API.
     */
    async loadAlbums() {
      this.loading = true
      this.error = null
      try {
        const response = await fetch(`/api/public/authors/${this.authorId}/albums?scope=main`, {
          credentials: 'include',
        })
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}: ${response.statusText}`)
        }
        const data = await response.json()
        this.albums = Array.isArray(data) ? data : []
      } catch (e) {
        console.error('[ZakromaAlbumsView] loadAlbums failed:', e)
        this.error = `Не удалось загрузить альбомы: ${e.message}`
        this.albums = []
      } finally {
        this.loading = false
      }
    },
    /**
     * Клик по плашке альбома → переход на страницу песен с фильтром по альбому.
     * Используем `albumId` (`tbl_albums.id`) — у нужных песен в `tbl_songs.album_id`
     * проставлен соответствующий FK, `ZakromaAlbum.albumId` заполняется на бэке
     * (Pass 357, `Zakroma.buildFromSongs`).
     * @param {number} albumId
     */
    onAlbumSelect(albumId) {
      this.$router.push({
        name: 'zakroma-author',
        params: { authorId: this.authorId },
        query: { albumId },
      })
    },
    /**
     * Клик по псевдо-плашке «Все песни автора» → переход на страницу песен автора
     * без фильтра (на ту же страницу `/zakroma/{authorId}`, но без `?album=`).
     */
    onPseudoTileClick() {
      this.$router.push({
        name: 'zakroma-author',
        params: { authorId: this.authorId },
      })
    },
  },
}
</script>

<style scoped>
.zakroma-albums-page {
  padding: 0 16px;
  /* Pass 358 fix: центрирование содержимого на широких экранах,
     как на остальных страницах (HomeView.km-main и др.). */
  max-width: 1400px;
  margin: 0 auto;
}
.km-page-title {
  font-size: 18px;
  font-weight: 600;
  margin: 16px 0 12px;
}
.km-page-title-author {
  font-weight: 400;
  color: var(--km-text2, #aaa);
  margin-left: 8px;
}
.km-loading,
.km-empty {
  padding: 24px;
  text-align: center;
  color: var(--km-text2, #aaa);
  /* Используем flex для надёжного центрирования на обеих темах (classic + modern),
     где у родительского .km-content может быть разная ширина контейнера. */
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60px;
}
.km-error {
  padding: 16px;
  background: rgba(255, 80, 80, 0.1);
  border: 1px solid rgba(255, 80, 80, 0.3);
  border-radius: 8px;
  color: #ff6464;
}
.km-btn {
  margin-left: 12px;
  padding: 4px 12px;
  background: var(--km-accent, #4a90e2);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
/* Псевдо-плашка — тот же размер и стиль, что обычные плашки (слот #leading). */
.km-pseudo-tile {
  /* Стили наследуются от .alt-tile в scoped компонента AlbumTiles. */
}
.km-pseudo-tile-icon {
  font-size: 48px;
  color: var(--km-accent, #4a90e2);
}
</style>
