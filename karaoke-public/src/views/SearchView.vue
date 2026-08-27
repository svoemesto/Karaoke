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

      <!-- Результаты — таблица (десктоп) -->
      <div v-else-if="searchResults.length" class="km-table-wrap">
        <table class="km-table">
          <colgroup>
            <col style="width: 190px" />
            <col style="width: 38px" />
            <col style="width: 180px" />
            <col style="width: 26px" />
            <col />
            <col style="width: 220px" />
            <col style="width: 24px" />
            <col style="width: 48px" />
            <col style="width: 26px" />
            <col style="width: 26px" />
          </colgroup>
          <thead>
            <tr>
              <th class="km-th">Исполнитель</th>
              <th class="km-th km-th-center">Год</th>
              <th class="km-th">Альбом</th>
              <th class="km-th km-th-center">№</th>
              <th class="km-th">Композиция</th>
              <th class="km-th" colspan="5">&nbsp;</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="sett in searchResults" :key="sett.id" class="km-tr">
              <td class="km-td">
                {{ sett.author
                }}<span v-if="sett.authorAlias" class="km-alias"> ({{ sett.authorAlias }})</span>
              </td>
              <td class="km-td km-td-center">{{ sett.year }}</td>
              <td class="km-td">{{ sett.album }}</td>
              <td class="km-td km-td-center km-track">{{ sett.track }}</td>
              <td class="km-td km-td-name">
                <RouterLink :to="{ path: '/song', query: { id: sett.id } }" class="km-song-link">{{
                  sett.songName
                }}</RouterLink>
              </td>
              <td class="km-td km-td-date">
                <span v-if="showDate(sett)" class="km-date-text">{{ dateLabel(sett) }}</span>
                <PremiumIcon
                  v-if="showCoin(sett)"
                  :state="sett.contentReady ? 'ready' : 'notready'"
                  :clickable="showCartIcon(sett)"
                  @subscribe="onSubscribeClick(sett)"
                />
              </td>
              <td class="km-td km-td-center">
                <CartIcon v-if="showCartIcon(sett)" :song-id="sett.id" />
              </td>
              <td class="km-td km-td-center">
                <span class="km-player-icon">
                  <PlayerIcon
                    :song-id="sett.id"
                    :content-ready-state="sett.contentReady ? 'ready' : 'notready'"
                    :in-air="sett.freelyAvailableNow"
                    :flag-free="sett.alwaysFree"
                    :premium="isPremium"
                    :has-subscription="subscriptions.subscriptionIds.has(Number(sett.id))"
                  />
                </span>
              </td>
              <td class="km-td km-td-center">
                <FavoriteIcon :song-id="sett.id" />
              </td>
              <td class="km-td km-td-center km-group-end">
                <PlaylistIcon :song-id="sett.id" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Результаты — карточки (мобильный) -->
      <div v-if="!searchIsLoading && searchResults.length" class="km-cards">
        <div v-for="sett in searchResults" :key="sett.id" class="km-card">
          <div class="km-card-meta">
            <span class="km-card-author"
              >{{ sett.author
              }}<span v-if="sett.authorAlias" class="km-alias">
                ({{ sett.authorAlias }})</span
              ></span
            >
            <span class="km-card-year">{{ sett.year }}</span>
            <span class="km-card-album">{{ sett.album }}</span>
          </div>
          <div class="km-card-top">
            <span class="km-card-track">{{ sett.track }}</span>
<RouterLink :to="{ path: '/song', query: { id: sett.id } }" class="km-card-title">{{
              sett.songName
            }}</RouterLink>
            <CartIcon v-if="showCartIcon(sett)" :song-id="sett.id" />
            <PlayerIcon
              :song-id="sett.id"
              :content-ready-state="sett.contentReady ? 'ready' : 'notready'"
              :in-air="sett.freelyAvailableNow"
              :flag-free="sett.alwaysFree"
              :premium="isPremium"
              :has-subscription="subscriptions.subscriptionIds.has(Number(sett.id))"
            />
            <FavoriteIcon :song-id="sett.id" />
            <PlaylistIcon :song-id="sett.id" />
          </div>
          <div v-if="showDate(sett) || showCoin(sett)" class="km-card-date">
            <span v-if="showDate(sett)" class="km-date-text">{{ dateLabel(sett) }}</span>
            <PremiumIcon
              v-if="showCoin(sett)"
              :state="sett.contentReady ? 'ready' : 'notready'"
              :clickable="showCartIcon(sett)"
              @subscribe="onSubscribeClick(sett)"
            />
          </div>
        </div>
      </div>

      <p v-else-if="!searchIsLoading && searched" class="km-empty">Ничего не найдено.</p>
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
    return {
      // Pass 239 (specs/239-zakroma-author-songs-batch-render): readiness больше НЕ догружается
      // per-row (это валило сайт на крупных авторах). PlayerIcon получает все данные через props.
      membership: usePlaylistMembership(),
      subscriptions: useSongSubscriptions(),
      cart,
      user,
    }
  },
  data() {
    return {
      form: { songName: '', author: '', text: '' },
      searched: false,
      // Модалка подписки на конкретную песню — открывается кликом по золотой иконке плеера.
      subscribingSongId: null,
      subscribingSongName: '',
    }
  },
  computed: {
    ...mapGetters('songs', ['authors', 'searchResults', 'searchIsLoading']),
    isPremium() {
      return !!(this.user && this.user.effectivePremium)
    },
  },
  watch: {
    // Pass 239 (specs/239-zakroma-author-songs-batch-render): readiness.load() убран (источник
    // зависания). membership.load() оставлен — для не-избранных плейлистов (bulk-fetch одним
    // запросом, см. usePlaylistMembership.load() — убран chunking).
    searchResults: {
      immediate: true,
      handler(list) {
        const ids = (list || []).map((s) => s.id)
        this.membership.load(ids)
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
      this.onSearch()
    }
  },
  methods: {
    ...mapActions('songs', ['loadAuthors', 'search']),
    // Монетка «премиум-контент» — только не-премиум посетителю и только для контента, недоступного
    // бесплатно прямо сейчас (specs/143-song-free-access-window). Золотая/серебряная — по
    // contentReadyFor(). Не гейтится личной покупкой — коин это категория контента, не персональный
    // статус доступа (см. showCartIcon/showDate ниже, где покупка уже учитывается).
    showCoin(sett) {
      return !this.isPremium && !sett.freelyAvailableNow
    },
    // Pass 239: isSongActiveForUser/isSongContentReady — эквиваленты readiness.stateFor/contentReadyFor
    // из флагов песни + singleton'ов (без per-row readiness).
    isSongActiveForUser(sett) {
      return (
        !!sett.contentReady &&
        (sett.freelyAvailableNow ||
          this.isPremium ||
          (this.subscriptions && this.subscriptions.subscriptionIds.has(Number(sett.id))))
      )
    },
    isSongContentReady(sett) {
      return !!sett.contentReady
    },
    // Иконка «в корзину» — в тех же условиях, что и золотая иконка плеера.
    showCartIcon(sett) {
      return (
        sett.songSubscriptionAvailable &&
        this.isSongContentReady(sett) &&
        !this.isSongActiveForUser(sett)
      )
    },
    // Личная подписка на эту песню уже даёт доступ ('active'), но песня не свободно доступна всем —
    // используется, чтобы скрыть текст "Будет в эфире с…"/"В эфире до…" для уже купленных (FR-009).
    isPurchased(sett) {
      return !sett.freelyAvailableNow && this.isSongActiveForUser(sett)
    },
    // Клик по золотой иконке плеера (PlayerIcon сам решает, когда её показывать) — открываем модалку
    // оформления подписки на конкретную песню.
    onSubscribeClick(sett) {
      this.subscribingSongId = sett.id
      this.subscribingSongName = `${sett.songName} — ${sett.author}`
    },
    // После активации (в т.ч. бесплатной по акции) — закрыть модалку и перезагрузить
    // подписки (Pass 239: иконка плеера использует subscriptionIds из useSongSubscriptions).
    onSongSubscriptionActivated() {
      const boughtId = this.subscribingSongId
      this.subscribingSongId = null
      if (this.subscriptions) this.subscriptions.loadOnce(true)
      if (boughtId && this.cart.isInCart(boughtId)) this.cart.toggle(boughtId)
    },
    // FR-009/FR-010 spec.md (specs/143-song-free-access-window): текст о сроках эфира — только
    // непремиум-пользователю, только для не-всегда-бесплатных и не-купленных песен, и только когда
    // есть что показать (ещё не в эфире, но дата НАЗНАЧЕНА — дата эфира; в эфире и в окне — дата
    // окончания окна). Для "дата эфира вообще не назначена" — намеренно пусто (баг-репорт: без
    // проверки airTimestamp здесь показывалось "Будет в эфире с Дата пока не определена").
    showDate(sett) {
      return (
        !this.isPremium &&
        !sett.alwaysFree &&
        !this.isPurchased(sett) &&
        ((!sett.onAir && sett.airTimestamp != null) || sett.freelyAvailableNow)
      )
    },
    // "Будет в эфире с {datePublish}" (ещё не в эфире) или "В эфире до {freeAccessWindowEndText}"
    // (в эфире и в окне бесплатного доступа).
    dateLabel(sett) {
      return sett.onAir
        ? `В эфире до ${sett.freeAccessWindowEndText}`
        : `Будет в эфире с ${sett.datePublish}`
    },
    onSearch() {
      this.searched = true
      this.search({ songName: this.form.songName, author: this.form.author, text: this.form.text })
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

/* Форма */
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

/* Таблица */
.km-table-wrap {
  overflow-x: auto;
  border-radius: 8px;
  border: 1px solid var(--km-border);
}
.km-table {
  width: 100%;
  border-collapse: collapse;
  background: var(--km-card);
  table-layout: fixed;
}
.km-th {
  background: var(--km-bg2);
  color: var(--km-text2);
  font-size: 0.72rem;
  font-weight: 600;
  padding: 0.3rem 0.4rem;
  border-bottom: 1px solid var(--km-border);
  text-transform: uppercase;
  letter-spacing: 0.03em;
}
.km-th-center {
  text-align: center;
}
.km-td {
  padding: 0.25rem 0.3rem;
  border-bottom: 1px solid var(--km-border);
  font-size: 0.8rem;
  vertical-align: middle;
}
.km-tr:last-child .km-td {
  border-bottom: none;
}
.km-tr:hover .km-td {
  background: var(--km-hover);
}
.km-td-center {
  text-align: center;
}
/* Воздух справа от иконки плеера (после удаления PlatformLink[sponsr]):
   расстояние между PlayerIcon и FavoriteIcon — как между FavoriteIcon и PlaylistIcon */
.km-player-icon {
  display: inline-block;
  margin-right: -20px;
}
.km-td-name {
  text-align: left;
}
.km-alias {
  font-style: italic;
  font-size: 0.82em;
  color: var(--km-text2);
}
.km-td-date {
  text-align: right;
  color: var(--km-text2);
  font-size: 0.78rem;
  white-space: nowrap;
}
.km-date-text {
  margin-right: 5px;
  vertical-align: middle;
}
.km-track {
  color: var(--km-text2);
}
.km-group-end {
  border-right: 2px solid var(--km-border);
}
.km-song-link {
  color: var(--km-accent);
  text-decoration: none;
  font-size: 0.82rem;
}
.km-song-link:hover {
  text-decoration: underline;
}

/* Мобильные карточки */
.km-cards {
  display: none;
}

.km-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 10px;
  padding: 0.75rem;
  margin-bottom: 0.5rem;
}
.km-card-meta {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.3rem;
  flex-wrap: wrap;
}
.km-card-author {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--km-text);
}
.km-card-year {
  font-size: 0.72rem;
  color: var(--km-text2);
}
.km-card-album {
  font-size: 0.72rem;
  color: var(--km-text2);
}
.km-card-top {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.4rem;
}
.km-card-track {
  font-size: 0.75rem;
  color: var(--km-text2);
  min-width: 20px;
  text-align: center;
}
.km-card-title {
  flex: 1;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--km-accent);
  text-decoration: none;
}
.km-card-title:hover {
  text-decoration: underline;
}
.km-card-date {
  font-size: 0.78rem;
  color: var(--km-text2);
  text-align: center;
  padding-top: 0.25rem;
}

@media (max-width: 768px) {
  .km-table-wrap {
    display: none;
  }
  .km-cards {
    display: block;
  }
}
</style>
