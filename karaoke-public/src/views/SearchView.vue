<template>
  <div class="km-page">
    <!-- Хедер единый (spec 250) -->
    <AppHeader :back="{ to: '/', label: '← Главная' }" />

    <div class="km-content">
      <!-- Форма поиска -->
      <div class="km-search-form">
        <div class="km-fields">
          <div class="km-field">
            <label class="km-label">Исполнитель</label>
            <input
              v-model="form.author"
              list="list_authors"
              class="km-input"
              placeholder="Введите имя..."
              @keyup.enter="onSearch"
            />
            <datalist id="list_authors">
              <option v-for="a in authors" :key="a" :value="a" />
            </datalist>
          </div>
          <div class="km-field">
            <label class="km-label">Название</label>
            <input
              v-model="form.songName"
              class="km-input"
              placeholder="Название песни..."
              @keyup.enter="onSearch"
            />
          </div>
          <div class="km-field">
            <label class="km-label">Слова</label>
            <input
              v-model="form.text"
              class="km-input"
              placeholder="Слова из текста..."
              @keyup.enter="onSearch"
            />
          </div>
        </div>
        <button class="km-search-btn" @click="onSearch">Искать</button>
      </div>

      <!-- Загрузка -->
      <div v-if="searchIsLoading" class="km-loading">Загрузка...</div>

      <!--
        Spec 262-search-pagination (FR-013, T011): счётчик «Показано X из Y» над списком
        результатов. aria-live="polite" — screen reader озвучивает обновления. Виден только
        при непустом списке.

        ВАЖНО (багфикс Pass 242): используется отдельный v-if (НЕ v-else-if от loading).
        Раньше счётчик был в цепочке v-else-if с km-song-list — при непустом searchResults
        Vue отрисовывал только первую совпавшую ветку (counter), а km-song-list через
        v-else-if пропускался — список не рендерился, хотя данные в state были.
        Теперь счётчик — независимый брат km-song-list, оба используют v-if от
        !searchIsLoading && searchResults.length.
      -->
      <div
        v-if="!searchIsLoading && searchResults.length && searchTotalCount > 0"
        class="km-counter"
        aria-live="polite"
      >
        Показано {{ searchResults.length }} из {{ searchTotalCount }}
      </div>

      <!--
        Spec 261 (US2 / Clarification Q1 → A): единый row-паттерн, применяется и на десктопе,
        и на мобилке (Clarification Q1 unified). Старые ветки <table> и <div class="km-cards">
        удалены полностью. Структура строки и CSS — копия эталонной PlaylistEditView (FR-004).

        ВАЖНО (багфикс Pass 242): v-if заменён с v-else-if на самостоятельный — см. комментарий
        выше про km-counter. Раньше из-за цепочки v-else-if список не отрисовывался, когда
        счётчик был виден.
      -->
      <div v-if="!searchIsLoading && searchResults.length" class="km-song-list">
        <div v-for="song in searchResults" :key="song.id" class="km-song-row">
          <!--
            Spec 261 (FR-005/FR-006): чёрная плашка с двумя превью. Альбом — квадрат 48×48,
            автор — горизонтальная карточка 120×48 (аспект 5:2). Если URL пуст или картинка
            не загрузилась — плейсхолдер «♪» / «👤» (FR-005, mirror PlaylistEditView.vue:109-128).
            loading="lazy" decoding="async" — D5 в research.md, важно для mobile viewport.
          -->
          <div class="km-song-pictures">
            <img
              v-if="song.albumPictureUrl && !hasImageError(song, 'album')"
              class="km-song-cover"
              :src="song.albumPictureUrl"
              loading="lazy"
              decoding="async"
              alt=""
              @error="markImageError(song, 'album')"
            />
            <div v-else class="km-song-cover km-song-cover-fallback" aria-hidden="true">♪</div>
            <img
              v-if="song.authorPictureUrl && !hasImageError(song, 'author')"
              class="km-song-author"
              :src="song.authorPictureUrl"
              loading="lazy"
              decoding="async"
              alt=""
              @error="markImageError(song, 'author')"
            />
            <div v-else class="km-song-author km-song-author-fallback" aria-hidden="true">👤</div>
          </div>

          <!--
            Spec 261 (FR-007/FR-008/FR-009): название песни — кликабельная ссылка на /song?id=<id>;
            имя автора — кликабельная ссылка на /zakroma/<authorId> (если резолвится через
            authorTiles; иначе — обычный текст, контракт спеки 259 FR-002). Подпись — формат
            «Автор - год, альбом» с условными разделителями (FR-009, mirror
            PlaylistEditView.vue:139-159). Спека 259 (FR-001) — нативные <a href> из <router-link>,
            Ctrl/Cmd-клик открывает в новой вкладке.
          -->
          <div class="km-song-info">
            <router-link :to="{ name: 'song', query: { id: song.id } }" class="km-song-title-link">
              {{ song.songName || 'Песня #' + song.id }}
            </router-link>
            <div class="km-song-sub">
              <template v-if="song.author || song.album">
                <router-link
                  v-if="song.author && authorIdFor(song.author)"
                  :to="{ name: 'zakroma-author', params: { authorId: authorIdFor(song.author) } }"
                  class="km-song-author-link"
                  >{{ song.author
                  }}<span v-if="song.authorAlias" class="km-alias">
                    ({{ song.authorAlias }})</span
                  ></router-link
                >
                <span v-else-if="song.author"
                  >{{ song.author
                  }}<span v-if="song.authorAlias" class="km-alias">
                    ({{ song.authorAlias }})</span
                  ></span
                >
                <span v-if="(song.year && song.year > 0) || song.album"> - </span>
                <span v-if="song.year && song.year > 0">{{ song.year }}</span>
                <span v-if="song.year && song.year > 0 && song.album">, </span>
                <span v-if="song.album">{{ song.album }}</span>
              </template>
            </div>
          </div>

          <!--
            Spec 261 (FR-010..FR-013): action icons group — те же иконки, что в прежней
            реализации (PlayerIcon / CartIcon / FavoriteIcon / PlaylistIcon), плюс inline-badge
            с подписью эфира («В эфире до...» / «Будет в эфире с...») и монеткой `PremiumIcon`.
            Условия показа (showDate/showCoin/showCartIcon) те же, что в прежнем коде — никаких
            изменений бизнес-логики. Поле `song.contentReady` корректно приходит из бэка
            (spec 261 / SongPublicDto.contentReady), иконка плеера теперь зелёная/золотая/серая
            по фактическому статусу, а не всегда-серая (bug-фикс).
          -->
          <div class="km-song-actions">
            <span v-if="showDate(song)" class="km-date-text">{{ dateLabel(song) }}</span>
            <PremiumIcon
              v-if="showCoin(song)"
              :state="song.contentReady ? 'ready' : 'notready'"
              :clickable="showCartIcon(song)"
              @subscribe="onSubscribeClick(song)"
            />
            <CartIcon v-if="showCartIcon(song)" :song-id="song.id" />
            <PlayerIcon
              :song-id="song.id"
              :content-ready-state="song.contentReady ? 'ready' : 'notready'"
              :in-air="song.freelyAvailableNow"
              :flag-free="song.alwaysFree"
              :premium="isPremium"
              :has-subscription="subscriptions.subscriptionIds.has(Number(song.id))"
            />
            <FavoriteIcon :song-id="song.id" />
            <PlaylistIcon :song-id="song.id" />
          </div>
        </div>
      </div>

      <!--
        Spec 262-search-pagination (FR-014, T012): кнопка «Загрузить ещё» — infinite scroll.
        Видна когда есть ещё страницы (`hasMore=true`) и не идёт текущая подгрузка
        (`isLoadingMore=false`). Inline-спиннер во время загрузки. На мобильных —
        полная ширина (CSS @media).
      -->
      <div
        v-if="searchHasMore || searchIsLoadingMore || searchPaginationError"
        class="km-load-more"
      >
        <button
          v-if="!searchPaginationError"
          class="km-load-more-btn"
          :disabled="!searchHasMore || searchIsLoadingMore"
          :aria-label="`Загрузить следующие ${searchPagination.pageSize} результатов`"
          @click="onLoadMore"
        >
          <span v-if="searchIsLoadingMore" class="km-spinner" aria-hidden="true" />
          {{ searchIsLoadingMore ? 'Загрузка…' : 'Загрузить ещё' }}
        </button>
        <!--
          Spec 262-search-pagination (FR-015, T013): inline-сообщение об ошибке + retry.
          role="alert" для screen reader; retry сбрасывает error-флаг и снова вызывает
          loadMoreSearchResults.
        -->
        <div v-if="searchPaginationError" class="km-load-more-error" role="alert">
          Не удалось загрузить ещё.
          <button class="km-load-more-retry" @click="retryLoadMore">Повторить</button>
        </div>
      </div>

      <!--
        Spec 262-search-pagination (Pass 243 fix): условие для «Ничего не найдено»
        теперь включает `searchResults.length === 0`. Без этого надпись показывалась
        одновременно со списком песен (когда searchResults.length > 0 но hasMore=false),
        потому что km-load-more не отрисовывался — а v-else-if от него попадал
        на это условие.
      -->
      <p v-else-if="!searchIsLoading && searched && searchResults.length === 0" class="km-empty">
        Ничего не найдено.
      </p>
    </div>

    <SongSubscriptionModal
      :visible="!!subscribingSongId"
      :song-id="subscribingSongId"
      :song-name="subscribingSongName"
      @close="subscribingSongId = null"
      @activated="onSongSubscriptionActivated"
    />
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex'
import { computed } from 'vue'
import { useStore } from 'vuex'
import PlayerIcon from '../components/PlayerIcon.vue'
import PremiumIcon from '../components/PremiumIcon.vue'
import SongSubscriptionModal from '../components/SongSubscriptionModal.vue'
import FavoriteIcon from '../components/FavoriteIcon.vue'
import PlaylistIcon from '../components/PlaylistIcon.vue'
import CartIcon from '../components/CartIcon.vue'
import AppHeader from '../components/AppHeader.vue'
import { useEngagementTracking } from '../composables/useEngagementTracking'
import { usePlaylistMembership } from '../composables/usePlaylistMembership'
import { useSongSubscriptions } from '../composables/useSongSubscriptions'
import { useCart } from '../composables/useCart'
import { useAuth } from '../composables/useAuth'

/**
 * View-страница «Search» — основной layout и data-fetching.
 *
 * Spec 261 (FR-004): структура строки результата поиска унифицирована со строкой
 * PlaylistEditView (`<div class="km-song-list">` + `<div class="km-song-row">` × N),
 * старые ветки `<table>` (десктоп) и `<div class="km-cards">` (мобил) удалены (Clarification
 * Q1 → A, 2026-08-28 — единый row-паттерн через CSS, без отдельной card-ветки). Шаблон и
 * стили скопированы из PlaylistEditView.vue:95-189 / :801-995 (стили визуально попиксельно
 * совпадают на десктопе и адаптивно под мобилку).
 *
 * Spec 261 (US1 / FR-001/FR-002): иконка плеера `<PlayerIcon>` теперь получает
 * `song.contentReady` напрямую из DTO (`SongPublicDto.contentReady`, добавлено в Phase 2).
 * Раньше поле отсутствовало — `<PlayerIcon :content-ready-state>` всегда видел `false` →
 * серая disabled, несмотря на реальный статус песни (баг «в эфире — недоступно»). Теперь
 * иконка зелёная для «в эфире», золотая (демо) для готовой не в эфире, серая для неготовой —
 * логика совпадает со страницей песни и Закромами (Pass 239, PlayerIcon.vue:80-95).
 *
 * @see AGENTS.md
 */

export default {
  name: 'SearchView',
  components: {
    PlayerIcon,
    PremiumIcon,
    SongSubscriptionModal,
    FavoriteIcon,
    PlaylistIcon,
    CartIcon,
    AppHeader,
  },
  setup() {
    useEngagementTracking('search')
    const { user } = useAuth()
    const cart = useCart()
    cart.load()
    const store = useStore()
    /**
     * Spec 259 (FR-006): плоский список тайлов авторов из Vuex-стора `zakroma`.
     * Используется для резолва `item.author` (имя) → `authorId` (числовой ID) для
     * построения ссылки на «Закрома автора». Никаких дополнительных HTTP-запросов на
     * рендер строки — кэш уже заполняется до первого показа результатов.
     */
    const authorTiles = computed(() => store.state.zakroma?.authorTiles || [])
    /**
     * Резолв имени автора в числовой authorId (строка — для path-параметра роута).
     * Возвращает `null` если автор отсутствует в кэше (Edge Case: удалён из `tbl_authors`).
     * @param {string} name имя автора (`song.author`)
     * @returns {string|null} authorId как строка или null
     */
    function authorIdFor(name) {
      if (!name) return null
      const tile = authorTiles.value.find((t) => t && t.author === name)
      return tile && tile.id != null ? String(tile.id) : null
    }
    return {
      // Pass 239: readiness больше НЕ догружается per-row; передача через props идёт напрямую.
      membership: usePlaylistMembership(),
      subscriptions: useSongSubscriptions(),
      cart,
      user,
      authorTiles,
      authorIdFor,
    }
  },
  data() {
    return {
      form: { songName: '', author: '', text: '' },
      searched: false,
      // Модалка подписки на конкретную песню — открывается кликом по золотой иконке плеера.
      subscribingSongId: null,
      subscribingSongName: '',
      // Spec 261 (FR-005): карта ошибок загрузки превью `<img>` для @error-фолбэка.
      // Ключ `${songId}:${'album'|'author'}`, значение `true` означает «заменить на плейсхолдер».
      // Идемпотентна: повторная ошибка не рекурсирует, флаг остаётся. Сбрасывается при новом
      // списке результатов (см. watch ниже) — старые ошибки для песни, ушедшей из списка,
      // удаляются автоматически (GC по новому ids-set).
      imageErrors: {},
    }
  },
  computed: {
    ...mapGetters('songs', [
      'authors',
      'searchResults',
      'searchIsLoading',
      // Spec 262-search-pagination (T007): getters пагинации
      'searchPagination',
      'searchHasMore',
      'searchIsLoadingMore',
      'searchTotalCount',
      'searchPaginationError',
    ]),
    isPremium() {
      return !!(this.user && this.user.effectivePremium)
    },
    // Spec 262-search-pagination (T014): URL-state — извлекаем page/pageSize из query.
    pageFromUrl() {
      const p = Number(this.$route.query.page)
      return Number.isFinite(p) && p >= 1 ? p : 1
    },
    pageSizeFromUrl() {
      const ps = Number(this.$route.query.pageSize)
      return Number.isFinite(ps) && ps >= 1 ? ps : 35
    },
  },
  watch: {
    // Pass 239: readiness.load() убран (источник зависания). membership.load() оставлен
    // для не-избранных плейлистов bulk-fetch одним запросом (usePlaylistMembership.load()).
    searchResults: {
      immediate: true,
      handler(list) {
        const ids = (list || []).map((s) => s.id)
        // Спека 261 (FR-005): GC старых ошибок превью — оставляем только для id, которые
        // сейчас в результате; новые id без ошибок будут показывать <img> (флаг отсутствует).
        const keepIds = new Set(ids)
        const next = {}
        for (const k of Object.keys(this.imageErrors)) {
          const idStr = k.split(':')[0]
          if (keepIds.has(Number(idStr))) next[k] = true
        }
        this.imageErrors = next
        this.membership.load(ids)
      },
    },
    // Spec 262-search-pagination (FR-012, T014, багфикс Pass 242): синхронизация URL → state.
    // При изменении URL (F5 на ?page=N, back/forward, programmatic navigation) восстанавливаем
    // срез через restoreFromUrl. Это единственная точка URL→state — обратное направление
    // (state → URL) выполняется явно в onLoadMore() и onSearch(), чтобы избежать рекурсии
    // между двумя watch'ами, которая ломала «Ничего не найдено» после многократных кликов.
    '$route.query.page': {
      immediate: false,
      handler() {
        const urlPage = this.pageFromUrl
        if (urlPage !== this.searchPagination.page) {
          this.restoreFromUrl()
        }
      },
    },
  },
  mounted() {
    this.loadAuthors()
    const q = this.$route.query
    if (q.author || q.songName || q.text) {
      this.form.author = q.author || ''
      this.form.songName = q.songName || ''
      this.form.text = q.text || ''
      // Spec 262-search-pagination: если URL содержит page>1, запускаем
      // restoreFromUrl — последовательно подгружаем страницы 1..N.
      this.onSearch()
    }
    // Спека 259 (FR-006): гарантируем наличие кэша `authorTiles` к моменту рендера строк —
    // иначе имя автора в первой отрисовке будет обычным текстом (Edge Case). Дедуп 30 с
    // внутри action не плодит лишних запросов (см. store/modules/zakroma.js).
    if (this.authorTiles.length === 0) {
      this.$store.dispatch('zakroma/loadAuthorTiles', 'main').catch(() => {
        // Тихо: если tiles не загрузились, имя автора просто не станет ссылкой —
        // страница всё равно работает, без падения.
      })
    }
  },
  methods: {
    ...mapActions('songs', ['loadAuthors', 'search', 'loadMoreSearchResults']),
    // —— Spec 261: error-flags для превью (FR-005 @error-фолбэк) ———
    hasImageError(song, kind) {
      return !!this.imageErrors[`${song.id}:${kind}`]
    },
    markImageError(song, kind) {
      // Vue.set для реактивности добавляемого ключа (Vue 2).
      this.$set(this.imageErrors, `${song.id}:${kind}`, true)
    },
    // —— Бизнес-логика показа иконок (как в прежней реализации, FR-011/FR-012/FR-013) ———
    // Монетка «премиум-контент» — только не-премиум посетителю и только для контента,
    // недоступного бесплатно прямо сейчас (specs/143-song-free-access-window).
    showCoin(song) {
      return !this.isPremium && !song.freelyAvailableNow
    },
    // Spec 261 (US3): isSongActiveForUser/isSongContentReady — эквиваленты Pass-239 readiness;
    // isSongContentReady теперь читает `song.contentReady` из DTO (источник — Song.isContentReady
    // через SongPublicDto), а не undefined как раньше.
    isSongActiveForUser(song) {
      return (
        !!song.contentReady &&
        (song.freelyAvailableNow ||
          this.isPremium ||
          (this.subscriptions && this.subscriptions.subscriptionIds.has(Number(song.id))))
      )
    },
    isSongContentReady(song) {
      return !!song.contentReady
    },
    // Иконка «в корзину» — в тех же условиях, что и золотая иконка плеера.
    showCartIcon(song) {
      return (
        song.songSubscriptionAvailable &&
        this.isSongContentReady(song) &&
        !this.isSongActiveForUser(song)
      )
    },
    // Личная подписка на эту песню уже даёт доступ — скрываем текст «В эфире...» для купленных.
    isPurchased(song) {
      return !song.freelyAvailableNow && this.isSongActiveForUser(song)
    },
    // Клик по золотой иконке плеера → модалка оформления подписки на конкретную песню.
    onSubscribeClick(song) {
      this.subscribingSongId = song.id
      this.subscribingSongName = `${song.songName} — ${song.author}`
    },
    // После активации (в т.ч. бесплатной по акции) — закрыть модалку и перезагрузить подписки.
    onSongSubscriptionActivated() {
      const boughtId = this.subscribingSongId
      this.subscribingSongId = null
      if (this.subscriptions) this.subscriptions.loadOnce(true)
      if (boughtId && this.cart.isInCart(boughtId)) this.cart.toggle(boughtId)
    },
    // FR-009/FR-010 (specs/143-song-free-access-window): подпись со сроками эфира — только
    // непремиум-пользователю, только для не-всегда-бесплатных и не-купленных песен, и только
    // когда есть что показать.
    showDate(song) {
      return (
        !this.isPremium &&
        !song.alwaysFree &&
        !this.isPurchased(song) &&
        ((!song.onAir && song.airTimestamp != null) || song.freelyAvailableNow)
      )
    },
    dateLabel(song) {
      return song.onAir
        ? `В эфире до ${song.freeAccessWindowEndText}`
        : `Будет в эфире с ${song.datePublish}`
    },
    onSearch() {
      this.searched = true
      // Spec 262-search-pagination: при смене фильтров — пагинация сбрасывается на 1
      // (FR-011 спеки). Если в URL был ?page=N — очищаем перед запросом.
      const filters = {
        songName: this.form.songName,
        author: this.form.author,
        text: this.form.text,
      }
      const hasFilters = filters.songName || filters.author || filters.text
      const urlPage = this.pageFromUrl
      // Если URL содержит page>1 И есть фильтры — восстанавливаем срез (после перезагрузки F5).
      // Иначе — обычный новый поиск с page=1.
      if (hasFilters && urlPage > 1) {
        this.search({ ...filters, page: 1, pageSize: this.pageSizeFromUrl })
        // После успешной первой порции дозагружаем страницы 2..N последовательно.
        // Делается через watch 'searchPagination.page' — он сработает при изменении.
      } else {
        // Удаляем ?page из URL, чтобы не было разночтений.
        if (this.$route.query.page) {
          const query = { ...this.$route.query }
          delete query.page
          this.$router.replace({ query }).catch(() => {})
        }
        this.search({ ...filters, page: 1, pageSize: this.pageSizeFromUrl })
      }
    },
    // Spec 262-search-pagination (FR-010, T013): подгрузка следующей порции по клику.
    onLoadMore() {
      // Передаём текущие фильтры: сначала из $route.query (после URL-sync),
      // иначе из this.form (когда URL не обновлён фильтрами после первого поиска).
      // Pass 243 fix: без fallback на this.form фильтры терялись после первого поиска,
      // потому что onSearch НЕ обновляет URL фильтрами — и loadMoreSearchResults
      // отправлял запрос БЕЗ text, получая пустой массив от бэкенда.
      const currentFilters = {
        songName: this.$route.query.songName || this.form.songName || '',
        author: this.$route.query.author || this.form.author || '',
        text: this.$route.query.text || this.form.text || '',
        album: this.$route.query.album || this.form.album || '',
      }
      // Возвращаем Promise, чтобы можно было обновить URL после успешной подгрузки.
      const promise = this.loadMoreSearchResults(currentFilters)
      // После успеха — обновляем ?page=N в URL без перезагрузки.
      // Не через watch, чтобы избежать рекурсии URL↔state (Pass 242 bugfix).
      if (promise && typeof promise.then === 'function') {
        promise
          .then(() => {
            const newPage = this.searchPagination.page
            if (newPage > 1 && this.pageFromUrl !== newPage) {
              const query = { ...this.$route.query, page: newPage }
              this.$router.replace({ query }).catch(() => {})
            }
          })
          .catch(() => {
            // Ошибка уже обработана в action (state.searchPagination.error=true),
            // URL не обновляем.
          })
      }
    },
    // Spec 262-search-pagination (FR-015, T013): retry после ошибки.
    retryLoadMore() {
      this.$store.commit('songs/setSearchPaginationError', false)
      this.onLoadMore()
    },
    // Spec 262-search-pagination (T014, Story 1 acceptance 4): восстановление с F5
    // при наличии ?page=N — последовательно подгружаем страницы 2..N.
    async restoreFromUrl() {
      const targetPage = this.pageFromUrl
      if (targetPage <= 1) return
      const currentFilters = {
        songName: this.$route.query.songName || this.form.songName || '',
        author: this.$route.query.author || this.form.author || '',
        text: this.$route.query.text || this.form.text || '',
        album: this.$route.query.album || '',
      }
      // Перезапускаем поиск с page=1 (сброс) и затем подгружаем targetPage-1 порций.
      this.search({
        ...currentFilters,
        page: 1,
        pageSize: this.pageSizeFromUrl,
      }).then(() => {
        let remaining = targetPage - 1
        const loadNext = () => {
          if (remaining <= 0) return
          remaining -= 1
          this.loadMoreSearchResults(currentFilters).then(() => loadNext())
        }
        loadNext()
      })
    },
  },
}
</script>

<style scoped>
.km-page {
  min-height: 100vh;
  background: var(--km-bg);
  color: var(--km-text);
}

/* Контент */
.km-content {
  max-width: 900px;
  margin: 0 auto;
  padding: 1.5rem 1rem;
}
.km-loading {
  padding: 2rem;
  text-align: center;
  color: var(--km-text2);
}
.km-empty {
  text-align: center;
  color: var(--km-text2);
  padding: 2rem;
}

/*
  === Spec 262-search-pagination: счётчик «Показано X из Y» + кнопка «Загрузить ещё» ===
  Размещаются под списком результатов; margin-top отделён от .km-song-list.
  Цвета через существующие CSS-переменные `--km-text2` (б хардкода).
  Кнопка «Загрузить ещё» адаптивна: полная ширина на мобильных вьюпортах (<480px).
*/
.km-counter {
  padding: 0.5rem 0.75rem;
  font-size: 0.85rem;
  color: var(--km-text2);
  text-align: right;
}
.km-load-more {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.5rem;
  padding: 1.5rem 1rem;
  flex-wrap: wrap;
}
.km-load-more-btn {
  background: var(--km-accent);
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 0.6rem 2rem;
  font-size: 0.92rem;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s;
  min-width: 180px;
}
.km-load-more-btn:hover:not(:disabled) {
  opacity: 0.88;
}
.km-load-more-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.km-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: km-spin 0.8s linear infinite;
  vertical-align: middle;
  margin-right: 0.4rem;
}
@keyframes km-spin {
  to {
    transform: rotate(360deg);
  }
}
.km-load-more-error {
  font-size: 0.88rem;
  color: var(--km-text2);
  display: inline-flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  justify-content: center;
}
.km-load-more-retry {
  background: transparent;
  color: var(--km-accent);
  border: 1px solid var(--km-accent);
  border-radius: 6px;
  padding: 0.25rem 0.85rem;
  font-size: 0.85rem;
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s;
}
.km-load-more-retry:hover {
  background: var(--km-accent);
  color: #fff;
}
@media (max-width: 480px) {
  .km-load-more-btn {
    width: 100%;
  }
}

/* Форма (без изменений, как раньше) */
.km-search-form {
  display: flex;
  gap: 1rem;
  align-items: flex-end;
  flex-wrap: wrap;
  margin-bottom: 1.5rem;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 14px;
  padding: 1rem;
}
.km-fields {
  display: flex;
  gap: 0.75rem;
  flex: 1;
  flex-wrap: wrap;
}
.km-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  flex: 1;
  min-width: 160px;
}
.km-label {
  font-size: 0.75rem;
  color: var(--km-text2);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.km-input {
  background: var(--km-input);
  color: var(--km-text);
  border: 1px solid var(--km-border);
  border-radius: 8px;
  padding: 0.4rem 0.75rem;
  font-size: 0.9rem;
  width: 100%;
  transition: border-color 0.15s;
}
.km-input:focus {
  outline: none;
  border-color: var(--km-accent);
}
.km-search-btn {
  background: var(--km-accent);
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 0.5rem 1.5rem;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: opacity 0.15s;
  align-self: flex-end;
}
.km-search-btn:hover {
  opacity: 0.88;
}

/*
  === Spec 261 (FR-004 + Clarification Q1 → A): строки результатов поиска. ===
  Скопировано из PlaylistEditView.vue:801-995 с минимальной адаптацией (нет
  drag-handle / mute-кнопок / remove — специфика поиска, они не нужны). Шаблон
  применяется одинаково на десктопе и мобилке — карточек-фолбэка больше нет
  (был v-if/km-cards ниже media-query 768px — удалено).
*/
.km-song-list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.km-song-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 10px;
  /* Самый плотный список (FR-005: 0 по вертикали — пользователь уже уточнил в PlaylistEditView). */
  padding: 0 0.7rem;
}
.km-song-row:hover {
  border-color: var(--km-accent);
}
/*
  Чёрная плашка с двумя превью (Spec 261 / PlaylistEditView.vue:844-885).
  margin/padding 5px, gap 5px между картинками. Альбом — квадрат 48×48, автор — горизонтальная
  карточка 120×48 (аспект 5:2). Картинки и плейсхолдеры — без своего фона/скругления, всё
  держит чёрный div-обёртка.
*/
.km-song-pictures {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 5px;
  padding: 5px;
  background: #000;
  border-radius: 8px;
  flex-shrink: 0;
}
.km-song-cover,
.km-song-cover-fallback {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
}
.km-song-author,
.km-song-author-fallback {
  width: 120px;
  height: 48px;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
}
.km-song-cover,
.km-song-author {
  object-fit: cover;
}
.km-song-cover-fallback,
.km-song-author-fallback {
  color: #888;
  font-size: 1.3rem;
  background: transparent;
}
/* Блок «название + подпись» (PlaylistEditView.vue:915-918 + 919-921 стили «km-song-title»). */
.km-song-info {
  flex: 1;
  min-width: 0;
}
/*
  Спека 259 (FR-001/FR-002/FR-007) + 261 (FR-007/FR-008): кликабельные название и автор.
  Название — display:block, чтобы overflow-обрезка работала в `<router-link>` (рендерит `<a>`
  с дефолтным display:inline). Автор — inline-block: в одной строке с разделителями « - ».
  Цвет — из существующей CSS-переменной --km-accent (работает в обоих дизайнах). Underline
  только по hover/focus — без агрессивного визуального шума в покое.
*/
.km-song-title-link {
  display: block;
  color: var(--km-accent, #0077ff);
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.92rem;
  font-weight: 600;
}
.km-song-author-link {
  display: inline-block;
  max-width: 100%;
  color: var(--km-accent, #0077ff);
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  font-size: 0.76rem;
}
.km-song-title-link:hover,
.km-song-title-link:focus-visible,
.km-song-author-link:hover,
.km-song-author-link:focus-visible {
  text-decoration: underline;
  outline: none;
}
.km-alias {
  font-style: italic;
  font-size: 0.82em;
  color: var(--km-text2);
}
.km-song-sub {
  font-size: 0.76rem;
  color: var(--km-text2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.km-date-text {
  font-size: 0.76rem;
  color: var(--km-text2);
  white-space: nowrap;
  margin-right: 4px;
}

/*
  Action icons group (FR-010..FR-013): плеер / корзина / премиум-монетка / избранное / плейлист
  + подпись эфира + алиас автора. Сворачивается в один ряд на узких экранах естественным
  flex-wrap (без media-query — Clarification Q1 unified, та же разметка на обоих вьюпортах).
*/
.km-song-actions {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-shrink: 0;
}
</style>
