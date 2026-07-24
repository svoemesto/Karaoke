<template>
  <div class="km-page">
    <!-- Хедер -->
    <header class="km-header">
      <div class="km-header-inner">
        <div class="km-header-left">
          <RouterLink to="/" class="km-back">← Главная</RouterLink>
          <a href="/"><img src="/KARAOKE_LOGO.png" class="km-logo" alt="Karaoke logo" /></a>
        </div>
        <div class="km-header-right">
          <AuthStatusWidget />
          <div class="km-theme-toggle">
            <button
              :class="['km-tb', theme === 'light' ? 'active' : '']"
              title="Светлая"
              @click="setTheme('light')"
            >
              ☀
            </button>
            <button
              :class="['km-tb', theme === 'system' ? 'active' : '']"
              title="Авто"
              @click="setTheme('system')"
            >
              ⬡
            </button>
            <button
              :class="['km-tb', theme === 'dark' ? 'active' : '']"
              title="Тёмная"
              @click="setTheme('dark')"
            >
              🌙
            </button>
          </div>
        </div>
      </div>
    </header>

    <!-- Быстрый фильтр по названию песни (только когда автор выбран) -->
    <div v-if="authorChosen" class="km-filter-bar">
      <div class="km-filter-inner">
        <span class="km-filter-icon">🔎</span>
        <input
          v-model="songFilter"
          type="text"
          class="km-input km-filter-input"
          placeholder="Быстрый фильтр по названию песни..."
          @keydown.esc="songFilter = ''"
        />
        <button
          v-if="songFilter"
          type="button"
          class="km-filter-clear"
          title="Очистить"
          @click="songFilter = ''"
        >
          ×
        </button>
      </div>
    </div>

    <!-- Фильтр автора -->
    <div class="km-content">
      <!-- Обычный режим: сетка тайлов + одна спец-плашка в конце.
           Режим specialBucket: сетка скрыта, рендерится таблица спецзаказных ниже.
           @see specs/008-special-orders/spec.md -->
      <div v-if="!authorChosen">
        <AuthorTiles :tiles="authorTiles" :selected="selectedAuthor" @select="onAuthorSelect">
          <!-- Спец-плашка «Отдельные песни разных авторов» — последний элемент ТОЙ ЖЕ сетки
               тайлов (слот trailing в AuthorTiles.vue), поэтому по размеру/раскладке не
               отличается от обычных тайлов авторов. Клик → табличное отображение. -->
          <template v-if="specialBucket.length > 0" #trailing>
            <button
              type="button"
              class="at-tile km-special-tile"
              :title="`Открыть таблицу «Отдельные песни разных авторов»`"
              :class="{ 'at-selected': isSpecialBucketSelected }"
              @click="onSelectSpecialBucket"
            >
              <div class="at-pic">
                <span class="km-special-tile-icon">📁</span>
              </div>
              <div class="at-namerow">
                <span class="at-name">Отдельные песни разных авторов</span>
                <span class="at-count" :title="`Песен в коллекции: ${totalSpecialSongs}`">{{
                  totalSpecialSongs
                }}</span>
              </div>
            </button>
          </template>
        </AuthorTiles>
      </div>

      <button
        v-if="authorChosen && !isSpecialBucketSelected"
        type="button"
        class="km-back-btn"
        @click="backToAuthors"
      >
        ← К списку авторов
      </button>
      <button
        v-if="isSpecialBucketSelected"
        type="button"
        class="km-back-btn"
        @click="backToAuthors"
      >
        ← К списку авторов
      </button>

      <!-- Обычный автор: таблица песен (как раньше) -->
      <div v-if="authorChosen && !isSpecialBucketSelected && isLoading" class="km-loading">
        Загрузка...
      </div>

      <div v-if="authorChosen && !displayedZakroma.length && songFilter" class="km-loading">
        Ничего не найдено по запросу «{{ songFilter }}»
      </div>

      <!-- Таблица: либо обычный автор, либо виртуальный спец-автор. -->
      <template v-if="authorChosen">
        <div v-for="zak in displayedZakroma" :key="zak.author" class="km-author-block">
          <!-- Заголовок автора -->
          <div class="km-author-header">
            <img
              v-if="zak.authorPictureUrl"
              :src="zak.authorPictureUrl"
              class="km-author-pic"
              alt=""
              @error="$event.target.style.display = 'none'"
            />
            <span class="km-author-name">{{ zak.author }}</span>
            <RouterLink
              :to="{ path: '/author-playlist', query: { author: zak.author } }"
              class="km-author-pl-btn"
              :title="`Плейлист по песням автора «${zak.author}»`"
              >🎧 Плейлист по песням автора «{{ zak.author }}»</RouterLink
            >
          </div>

          <!-- Альбомы -->
          <div v-for="alb in zak.albums" :key="alb.albumName" class="km-album-block">
            <div class="km-album-header">
              <img
                v-if="alb.albumPictureUrl"
                :src="alb.albumPictureUrl"
                class="km-album-pic"
                alt=""
                @error="$event.target.style.display = 'none'"
              />
              <span class="km-album-name">{{ alb.year }} — {{ alb.albumName }}</span>
            </div>

            <!-- Десктоп: таблица -->
            <div class="km-table-wrap">
              <table class="km-table">
                <colgroup>
                  <col style="width: 28px" />
                  <col />
                  <col style="width: 220px" />
                  <col style="width: 24px" />
                  <col style="width: 24px" />
                  <col style="width: 32px" />
                  <col style="width: 26px" />
                  <col style="width: 26px" />
                </colgroup>
                <thead>
                  <tr>
                    <th class="km-th km-th-center">№</th>
                    <th class="km-th">Композиция</th>
                    <th class="km-th" colspan="6">&nbsp;</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="sett in alb.albumSettings" :key="sett.id" class="km-tr">
                    <td class="km-td km-td-center km-track">{{ sett.track }}</td>
                    <td class="km-td km-td-name">
                      <RouterLink
                        :to="{ path: '/song', query: { id: sett.id } }"
                        class="km-song-link"
                        >{{ sett.songName }}</RouterLink
                      >
                    </td>
                    <td class="km-td km-td-date">
                      <span v-if="showDate(sett)" class="km-date-text">{{ sett.datePublish }}</span>
                      <PremiumIcon
                        v-if="showCoin(sett)"
                        :state="readiness.contentReadyFor(sett.id)"
                        :clickable="showCartIcon(sett)"
                        @subscribe="onSubscribeClick(sett, zak.author)"
                      />
                    </td>
                    <td class="km-td km-td-center">
                      <CartIcon v-if="showCartIcon(sett)" :song-id="sett.id" />
                    </td>
                    <td class="km-td km-td-center">
                      <PlayerIcon
                        :song-id="sett.id"
                        :watch-state="readiness.stateFor(sett.id)"
                        :content-ready-state="readiness.contentReadyFor(sett.id)"
                      />
                    </td>
                    <td class="km-td km-td-center">
                      <PlatformLink
                        link-name="sponsr"
                        :link-value="sett.linkSponsrPlay"
                        :song-id="sett.id"
                        song-version="all"
                      />
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

            <!-- Мобильные карточки -->
            <div class="km-cards">
              <div v-for="sett in alb.albumSettings" :key="sett.id" class="km-card">
                <div class="km-card-top">
                  <span class="km-card-track">{{ sett.track }}</span>
                  <RouterLink
                    :to="{ path: '/song', query: { id: sett.id } }"
                    class="km-card-title"
                    >{{ sett.songName }}</RouterLink
                  >
                  <CartIcon v-if="showCartIcon(sett)" :song-id="sett.id" />
                  <PlayerIcon
                    :song-id="sett.id"
                    :watch-state="readiness.stateFor(sett.id)"
                    :content-ready-state="readiness.contentReadyFor(sett.id)"
                  />
                  <PlatformLink
                    link-name="sponsr"
                    :link-value="sett.linkSponsrPlay"
                    :song-id="sett.id"
                    song-version="all"
                  />
                  <FavoriteIcon :song-id="sett.id" />
                  <PlaylistIcon :song-id="sett.id" />
                </div>
                <div v-if="showDate(sett) || showCoin(sett)" class="km-card-date">
                  <span v-if="showDate(sett)" class="km-date-text">{{ sett.datePublish }}</span>
                  <PremiumIcon
                    v-if="showCoin(sett)"
                    :state="readiness.contentReadyFor(sett.id)"
                    :clickable="showCartIcon(sett)"
                    @subscribe="onSubscribeClick(sett, zak.author)"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
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
import PlatformLink from '../components/PlatformLink.vue'
import PlayerIcon from '../components/PlayerIcon.vue'
import PremiumIcon from '../components/PremiumIcon.vue'
import SongSubscriptionModal from '../components/SongSubscriptionModal.vue'
import FavoriteIcon from '../components/FavoriteIcon.vue'
import PlaylistIcon from '../components/PlaylistIcon.vue'
import CartIcon from '../components/CartIcon.vue'
import AuthStatusWidget from '../components/AuthStatusWidget.vue'
import AuthorTiles from '../components/AuthorTiles.vue'
import { useDesign } from '../composables/useDesign'
import { useEngagementTracking } from '../composables/useEngagementTracking'
import { usePlayerReadiness } from '../composables/usePlayerReadiness'
import { usePlaylistMembership } from '../composables/usePlaylistMembership'
import { useCart } from '../composables/useCart'
import { useAuth } from '../composables/useAuth'

// Нормализация строки для быстрого фильтра по названию: регистронезависимо, без краевых
// пробелов, Ё приравнивается к Е (чтобы «ёлка»/«елка» находили друг друга).
function normalize(s) {
  return (s || '').toLowerCase().replace(/ё/g, 'е').trim()
}

/**
 * View-страница «Zakroma» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */

export default {
  name: 'ZakromaView',
  components: {
    PlatformLink,
    PlayerIcon,
    PremiumIcon,
    SongSubscriptionModal,
    FavoriteIcon,
    PlaylistIcon,
    CartIcon,
    AuthStatusWidget,
    AuthorTiles,
  },
  setup() {
    useEngagementTracking('zakroma')
    const { theme, applyTheme } = useDesign()
    const { user } = useAuth()
    const cart = useCart()
    cart.load()
    function setTheme(val) {
      theme.value = val
      applyTheme(val)
    }
    return {
      theme,
      setTheme,
      readiness: usePlayerReadiness(),
      membership: usePlaylistMembership(),
      cart,
      user,
    }
  },
  data() {
    return {
      selectedAuthor: this.$route.query.author || '',
      // Плитки-пикер видны, пока автор не выбран. После выбора (в т.ч. «Все авторы») скрываются.
      authorChosen: !!this.$route.query.author,
      // Режим «Отдельные песни разных авторов» (открывается кликом по спец-плашке в сетке).
      specialBucketShown: this.$route.query.specialBucket === 'true',
      // Модалка подписки на конкретную песню — открывается кликом по золотой иконке плеера.
      subscribingSongId: null,
      subscribingSongName: '',
      // Быстрый клиентский фильтр по названию песни (без запроса к бэку).
      songFilter: '',
    }
  },
  computed: {
    ...mapGetters('zakroma', ['authorTiles', 'zakroma', 'specialBucket', 'isLoading']),
    isPremium() {
      return !!(this.user && this.user.effectivePremium)
    },
    /** True, когда открыт режим «Отдельные песни разных авторов».
     * Используется для скрытия обычных тайлов и рендера плоской таблицы спецзаказных. */
    isSpecialBucketSelected() {
      return this.specialBucketShown
    },
    /** Общее число песен во всех спецзаказных авторах — для пилюли-счётчика на тайле. */
    totalSpecialSongs() {
      return (this.specialBucket || []).reduce(
        (sum, zak) =>
          sum + (zak.albums || []).reduce((s2, alb) => s2 + (alb.albumSettings || []).length, 0),
        0,
      )
    },
    // Тот же zakroma, но с albumSettings/альбомами/авторами, отфильтрованными по songFilter.
    // Watch/загрузка readiness-membership намеренно завязаны на исходный zakroma (см. watch ниже),
    // а не на этот computed — иначе каждое нажатие клавиши будет дёргать сетевые запросы готовности.
    filteredZakroma() {
      const q = normalize(this.songFilter)
      if (!q) return this.zakroma
      return (this.zakroma || [])
        .map((zak) => ({
          ...zak,
          albums: (zak.albums || [])
            .map((alb) => ({
              ...alb,
              albumSettings: (alb.albumSettings || []).filter((s) =>
                normalize(s.songName).includes(q),
              ),
            }))
            .filter((alb) => alb.albumSettings.length > 0),
        }))
        .filter((zak) => zak.albums.length > 0)
    },
    /** Zakroma для отображения: либо обычная (запрос автора), либо реальные спецзаказные
     * авторы (каждый — свой блок Автор→Альбом→Песни, как обычный автор в Закромах).
     * Используется в template вместо filteredZakroma. */
    displayedZakroma() {
      if (this.specialBucketShown) {
        return this.specialBucket || []
      }
      return this.filteredZakroma
    },
    /** Сейчас идёт загрузка? Учитываем оба режима (обычный + спец). */
    isLoadingAny() {
      if (this.specialBucketShown) {
        // Спец-режим загружает через loadSpecialBucket (без isLoading в сторе)
        return false
      }
      return this.isLoading
    },
  },
  watch: {
    // Готовность плеера подгружаем асинхронно, как только пришли данные закромов (и при их смене).
    zakroma: {
      immediate: true,
      handler(list) {
        const ids = (list || []).flatMap((z) =>
          z.albums.flatMap((a) => a.albumSettings.map((s) => s.id)),
        )
        this.readiness.load(ids)
        this.membership.load(ids)
      },
    },
    // То же самое для спецзаказных авторов — без этого watcher-а иконки готовности
    // (PlayerIcon/PremiumIcon/CartIcon) в режиме specialBucket вечно висели в состоянии
    // "loading", т.к. readiness/membership для этих id никогда не запрашивались.
    specialBucket: {
      immediate: true,
      handler(list) {
        const ids = (list || []).flatMap((z) =>
          z.albums.flatMap((a) => a.albumSettings.map((s) => s.id)),
        )
        this.readiness.load(ids)
        this.membership.load(ids)
      },
    },
  },
  mounted() {
    // Основной каталог: scope='main' — авторы БЕЗ is_special_order=true.
    this.loadAuthorTiles('main')
    // Спец-каталог (виртуальный «автор» в конце) — нужен для тайла и плоской таблицы.
    this.loadSpecialBucket()
    // Таблицу грузим только если автор уже выбран (например, зашли по ссылке ?author=...).
    if (this.authorChosen) this.loadZakroma(this.selectedAuthor)
  },
  methods: {
    ...mapActions('zakroma', ['loadAuthorTiles', 'loadZakroma', 'loadSpecialBucket']),
    // Монетка «премиум-контент» — только не-премиум посетителю и только для контента, доступного
    // лишь премиуму (эксклюзив или ещё не в эфире). Золотая/серебряная — по contentReadyFor().
    showCoin(sett) {
      return !this.isPremium && (sett.exclusive || !sett.onAir)
    },
    // Иконка «в корзину» — в тех же условиях, что и золотая иконка плеера (контент готов, зрителю
    // сейчас недоступен, но подписка на песню разрешена автором).
    showCartIcon(sett) {
      return (
        sett.songSubscriptionAvailable &&
        this.readiness.contentReadyFor(sett.id) === 'ready' &&
        this.readiness.stateFor(sett.id) !== 'active'
      )
    },
    // Клик по золотой иконке плеера (PlayerIcon сам решает, когда её показывать) — открываем модалку
    // оформления подписки на конкретную песню.
    onSubscribeClick(sett, author) {
      this.subscribingSongId = sett.id
      this.subscribingSongName = `${sett.songName} — ${author}`
    },
    // После активации (в т.ч. бесплатной по акции) — закрыть модалку и перепроверить доступность,
    // чтобы иконка сразу стала зелёной.
    onSongSubscriptionActivated() {
      const boughtId = this.subscribingSongId
      this.subscribingSongId = null
      const ids = (this.zakroma || []).flatMap((z) =>
        z.albums.flatMap((a) => a.albumSettings.map((s) => s.id)),
      )
      this.readiness.load(ids)
      // Купили напрямую песню, которая уже лежала в корзине — убираем её оттуда, чтобы не предлагать
      // оплатить то, что уже куплено.
      if (boughtId && this.cart.isInCart(boughtId)) this.cart.toggle(boughtId)
    },
    // Реальную дату публикации (или «Дата пока не определена») показываем всем для ещё не вышедших
    // НЕ-эксклюзивных песен; у не-премиума она соседствует с монеткой. Тексты «Эксклюзивно на SPONSR»
    // не выводим никому — их заменяет монетка (не-премиуму) / пустая ячейка (премиуму). В эфире — пусто.
    showDate(sett) {
      return !sett.onAir && !sett.exclusive
    },
    onAuthorSelect(author) {
      this.selectedAuthor = author
      this.authorChosen = true
      this.songFilter = ''
      this.$router.replace({ path: '/zakroma', query: author ? { author } : {} })
      this.loadZakroma(author)
    },
    /** Открыть табличное отображение «Отдельные песни разных авторов» как обычного автора. */
    onSelectSpecialBucket() {
      this.specialBucketShown = true
      this.authorChosen = true
      this.songFilter = ''
      this.$router.replace({ path: '/zakroma', query: { specialBucket: 'true' } })
    },
    backToAuthors() {
      this.selectedAuthor = ''
      this.authorChosen = false
      this.specialBucketShown = false
      this.songFilter = ''
      this.$router.replace({ path: '/zakroma', query: {} })
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

/* Хедер */
.km-header {
  background: var(--km-header);
  border-bottom: 1px solid var(--km-border);
  padding: 0.5rem 1rem;
  position: sticky;
  top: 0;
  z-index: 100;
}
.km-header-inner {
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.km-header-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.km-back {
  color: var(--km-accent);
  text-decoration: none;
  font-size: 0.85rem;
  white-space: nowrap;
}
.km-back:hover {
  text-decoration: underline;
}
.km-logo {
  height: 36px;
  width: auto;
}
.km-header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.km-theme-toggle {
  display: flex;
  border: 1px solid var(--km-border);
  border-radius: 20px;
  overflow: hidden;
}
.km-tb {
  background: transparent;
  color: var(--km-text2);
  border: none;
  padding: 0.2rem 0.55rem;
  font-size: 0.95rem;
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s;
}
.km-tb:hover {
  background: var(--km-hover);
  color: var(--km-text);
}
.km-tb.active {
  background: var(--km-accent);
  color: #fff;
}

/* Быстрый фильтр по названию песни — sticky-панель сразу под хедером */
.km-filter-bar {
  position: sticky;
  top: 53px; /* высота .km-header: padding 0.5rem*2 + логотип 36px + border 1px */
  z-index: 90;
  background: var(--km-header);
  border-bottom: 1px solid var(--km-border);
  padding: 0.5rem 1rem;
}
.km-filter-inner {
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.km-filter-icon {
  color: var(--km-text2);
  font-size: 0.9rem;
}
.km-filter-input {
  flex: 1;
}
.km-filter-clear {
  background: transparent;
  border: none;
  color: var(--km-text2);
  font-size: 1.2rem;
  line-height: 1;
  cursor: pointer;
  padding: 0.2rem 0.4rem;
}
.km-filter-clear:hover {
  color: var(--km-text);
}

/* Поле ввода (общий стиль km-input, как в SearchView/LoginView/AccountView) */
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

/* Контент */
.km-content {
  max-width: 900px;
  margin: 0 auto;
  padding: 1rem;
}
.km-loading {
  padding: 2rem;
  text-align: center;
  color: var(--km-text2);
}

/* Кнопка возврата к списку авторов */
.km-back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 1.25rem;
  padding: 0.4rem 0.9rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--km-accent);
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 8px;
  cursor: pointer;
  transition:
    background 0.15s,
    box-shadow 0.15s;
}
.km-back-btn:hover {
  background: var(--km-hover);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

/* Блок автора */
.km-author-block {
  margin-bottom: 2rem;
}
.km-author-header {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.6rem 0;
  margin-bottom: 0.75rem;
}
.km-author-pic {
  height: 44px;
  width: auto;
  border-radius: 6px;
  background: black;
}
.km-author-name {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--km-text);
}
/* Спец-тайл «Отдельные песни разных авторов» — встраивается последним элементом в сетку
   .at-grid (слот trailing в AuthorTiles.vue), поэтому по размеру строго совпадает с обычными
   тайлами авторов (grid сам растягивает/выравнивает любой прямой потомок). Классы
   .at-pic/.at-namerow/.at-name/.at-count позаимствованы у AuthorTiles.vue для единообразия,
   но их правила scoped к AuthorTiles.vue и не действуют на слот-контент — поэтому дублируем
   их здесь под префиксом .km-special-tile (см. AuthorTiles.vue на случай синхронизации стиля).
   @see specs/008-special-orders/spec.md */
.km-special-tile {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  padding: 0;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  text-align: center;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  transition:
    transform 0.12s ease,
    box-shadow 0.12s ease,
    border-color 0.12s ease;
}
.km-special-tile:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.35);
}
.km-special-tile.at-selected {
  border-color: var(--km-accent);
  box-shadow: 0 0 0 2px var(--km-accent);
}
.km-special-tile .at-pic {
  position: relative;
  height: 64px;
  background: #000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.km-special-tile-icon {
  font-size: 1.7rem;
  line-height: 1;
  color: #fff;
  opacity: 0.85;
  user-select: none;
}
.km-special-tile .at-namerow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 6px 8px;
}
.km-special-tile .at-name {
  flex: 1;
  min-width: 0;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--km-text);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.km-special-tile .at-count {
  flex-shrink: 0;
  padding: 1px 7px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.5;
  border-radius: 10px;
  white-space: nowrap;
  color: var(--km-text2);
  background: var(--km-bg2);
}
.km-author-pl-btn {
  margin-left: auto;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--km-accent);
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 16px;
  padding: 0.3rem 0.8rem;
  text-decoration: none;
  white-space: nowrap;
}
.km-author-pl-btn:hover {
  background: var(--km-hover);
  border-color: var(--km-accent);
}

/* Блок альбома */
.km-album-block {
  margin-bottom: 1.5rem;
}
.km-album-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  padding: 0.3rem 0;
}
.km-album-pic {
  height: 36px;
  width: 36px;
  object-fit: cover;
  border-radius: 4px;
  background: black;
}
.km-album-name {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--km-text2);
}

/* Таблица (десктоп) */
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
.km-td-name {
  text-align: left;
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
  text-align: center;
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

/* Адаптивность */
@media (max-width: 768px) {
  .km-table-wrap {
    display: none;
  }
  .km-cards {
    display: block;
  }
}
</style>
