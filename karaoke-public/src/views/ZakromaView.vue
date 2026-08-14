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

    <!-- Переключатель сквозной/групповой + быстрые фильтры по типу альбома
         (specs/012-entity-description-fields FR-023/024/025/026/027). Показывается только для
         одного выбранного реального автора — у "спецзаказных"/множественных наборов эта шапка
         не показывается (нет единого набора счётчиков типов). -->
    <div v-if="authorChosen && zakromaAlbumTypeCounts.length > 0" class="km-album-controls-bar">
      <div class="km-album-controls-inner">
        <div class="km-theme-toggle km-album-mode-toggle">
          <button
            :class="['km-tb', albumDisplayMode === 'continuous' ? 'active' : '']"
            title="Сквозной список"
            @click="setAlbumDisplayMode('continuous')"
          >
            Сквозной
          </button>
          <button
            :class="['km-tb', albumDisplayMode === 'grouped' ? 'active' : '']"
            title="По типам альбомов"
            @click="setAlbumDisplayMode('grouped')"
          >
            По типам альбомов
          </button>
        </div>
        <div class="km-album-type-filters">
          <button
            v-for="summary in zakromaAlbumTypeCounts"
            :key="summary.dbValue"
            type="button"
            :class="[
              'km-album-type-filter-btn',
              hiddenAlbumTypes.has(summary.dbValue) ? 'off' : '',
            ]"
            @click="toggleAlbumType(summary.dbValue)"
          >
            {{ summary.filterLabel }} ({{ summary.count }})
          </button>
        </div>
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

      <!-- 181: real-time progress meter (FR-FE-005, FR-FE-011) -->
      <div
        v-if="authorChosen && !isSpecialBucketSelected && isStreaming"
        class="km-stream-progress"
        role="progressbar"
        :aria-valuemin="0"
        :aria-valuemax="streamProgress.expectedCount || 0"
        :aria-valuenow="streamProgress.receivedCount || 0"
        aria-live="polite"
      >
        <div class="km-stream-text">
          Загружаем {{ streamProgress.receivedCount || 0 }} из
          {{ streamProgress.expectedCount || 0 }} песен автора {{ selectedAuthor }}…
        </div>
        <div class="km-stream-bar">
          <div
            class="km-stream-bar-fill"
            :style="{
              width:
                Math.min(
                  100,
                  Math.round(
                    ((streamProgress.receivedCount || 0) /
                      Math.max(streamProgress.expectedCount || 1, 1)) *
                      100,
                  ),
                ) + '%',
            }"
          />
        </div>
        <button
          type="button"
          class="km-stream-cancel"
          title="Отменить загрузку"
          @click="cancelZakromaStream"
        >
          Отмена
        </button>
      </div>

      <!-- 181: ошибка стрима + retry (FR-FE-001 сценарий 4) -->
      <div
        v-if="authorChosen && !isSpecialBucketSelected && streamError && !isStreaming"
        class="km-stream-error"
        role="alert"
      >
        <span>{{ streamError }}</span>
        <button type="button" class="km-stream-retry" @click="retryLoadZakroma">Повторить</button>
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
            <div class="km-author-name-wrap">
              <div v-if="zak.authorWarning" class="km-warning-text">{{ zak.authorWarning }}</div>
              <span class="km-author-name-line">
                <span class="km-author-name" :title="zak.authorDescription || null">{{
                  zak.author
                }}</span>
                <span v-if="zak.authorShortDescription" class="km-short-description-text">{{
                  zak.authorShortDescription
                }}</span>
              </span>
            </div>
            <RouterLink
              :to="{ path: '/author-playlist', query: { author: zak.author } }"
              class="km-author-pl-btn"
              :title="`Плейлист по песням автора «${zak.author}»`"
              >🎧 Плейлист по песням автора «{{ zak.author }}»</RouterLink
            >
          </div>

          <!-- Альбомы: сквозной список либо группировка по типу (переключатель в шапке,
               specs/012-entity-description-fields FR-023/024) — единый v-for отдаёт вперемешку
               заголовки групп и сами альбомы, чтобы не дублировать разметку блока альбома дважды. -->
          <template v-for="item in albumRenderItems(zak)" :key="item.key">
            <div v-if="item.type === 'header'" class="km-album-group-header">{{ item.label }}</div>
            <div v-else class="km-album-block">
              <div class="km-album-header">
                <img
                  v-if="item.alb.albumPictureUrl"
                  :src="item.alb.albumPictureUrl"
                  class="km-album-pic"
                  alt=""
                  @error="$event.target.style.display = 'none'"
                />
                <div class="km-album-name-wrap">
                  <div v-if="item.alb.warning" class="km-warning-text">{{ item.alb.warning }}</div>
                  <span class="km-album-name-line">
                    <span class="km-album-name" :title="item.alb.description || null"
                      >{{ item.alb.year }} — {{ item.alb.albumName }}</span
                    >
                    <span v-if="item.alb.shortDescription" class="km-short-description-text">{{
                      item.alb.shortDescription
                    }}</span>
                  </span>
                  <!-- FR-017: подпись типа теперь показывается для ВСЕХ типов, включая "studio" —
                     каноническая подпись приходит с бэкенда (AlbumType.description), фронт больше
                     не хранит свою (рассинхронизированную) RU-мапу. -->
                  <span v-if="item.alb.albumTypeLabel" class="km-album-type-badge">{{
                    item.alb.albumTypeLabel
                  }}</span>
                </div>
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
                    <col style="width: 26px" />
                    <col style="width: 26px" />
                  </colgroup>
                  <thead>
                    <tr>
                      <th class="km-th km-th-center">№</th>
                      <th class="km-th">Композиция</th>
                      <th class="km-th" colspan="5">&nbsp;</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="sett in item.alb.albumSettings" :key="sett.id" class="km-tr">
                      <td class="km-td km-td-center km-track">{{ sett.track }}</td>
                      <td class="km-td km-td-name">
                        <RouterLink
                          :to="{ path: '/song', query: { id: sett.id } }"
                          class="km-song-link"
                          >{{ sett.songName }}</RouterLink
                        >
                      </td>
                      <td class="km-td km-td-date">
                        <span v-if="showDate(sett)" class="km-date-text">{{
                          dateLabel(sett)
                        }}</span>
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
                      <td class="km-td km-td-center km-player-cell">
                        <PlayerIcon
                          :song-id="sett.id"
                          :watch-state="readiness.stateFor(sett.id)"
                          :content-ready-state="readiness.contentReadyFor(sett.id)"
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
                <div v-for="sett in item.alb.albumSettings" :key="sett.id" class="km-card">
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
                    <FavoriteIcon :song-id="sett.id" />
                    <PlaylistIcon :song-id="sett.id" />
                  </div>
                  <div v-if="showDate(sett) || showCoin(sett)" class="km-card-date">
                    <span v-if="showDate(sett)" class="km-date-text">{{ dateLabel(sett) }}</span>
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
          </template>
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
// useZakromaStreamProgress подключается в T014 (Phase 4) — полноценный UI прогрессометра.
// На этой фазе (T010) стрим идёт через store action `loadZakromaStream`, который
// сам инстанцирует composable и пишет в state.streamProgress/streamError.

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
      // specs/012-entity-description-fields: режим отображения альбомов ("continuous"/"grouped")
      // и скрытые типы альбомов (быстрый фильтр) — персистентны в localStorage (как тема), не
      // привязаны к конкретному автору (общая настройка посетителя, FR-023/025).
      albumDisplayMode:
        localStorage.getItem('km-zakroma-album-mode') === 'grouped' ? 'grouped' : 'continuous',
      hiddenAlbumTypes: new Set(
        (() => {
          try {
            return JSON.parse(localStorage.getItem('km-zakroma-hidden-album-types') || '[]')
          } catch {
            return []
          }
        })(),
      ),
    }
  },
  computed: {
    ...mapGetters('zakroma', [
      'authorTiles',
      'zakroma',
      'specialBucket',
      'isStreaming',
      'streamProgress',
      'streamError',
    ]),
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
    /** Счётчики альбомов по типу (для переключателя/быстрых фильтров в шапке) — только когда
     * показан ровно один автор (specs/012-entity-description-fields FR-025/026); в спецзаказном
     * режиме (несколько виртуальных авторов сразу) единого набора счётчиков нет. */
    zakromaAlbumTypeCounts() {
      if (this.displayedZakroma.length !== 1) return []
      return this.displayedZakroma[0].albumTypeCounts || []
    },
    /** Сейчас идёт загрузка? Учитываем оба режима (обычный + спец). */
    isLoadingAny() {
      if (this.specialBucketShown) {
        // Спец-режим загружает через loadSpecialBucket (без isStreaming в сторе)
        return false
      }
      return this.isStreaming
    },
  },
  watch: {
    // Готовность плеера подгружаем асинхронно, как только пришли данные закромов (и при их смене).
    // БЕЗ `immediate: true`: на browser back из /song/{id} state.zakroma не
    // меняется (dedup в loadZakromaStream → early return), и если бы
    // watcher's `immediate: true` стрелял — readiness.load(populated_ids)
    // сбросил бы states в 'loading' и загрузил API заново. Если API
    // зависает, иконки остаются в 'loading' навсегда (только что
    // наблюдали в проде: 181/246). Без `immediate: true` watch fires
    // ТОЛЬКО на реальные изменения state.zakroma (setZakroma([]) →
    // setZakroma([...])), readiness загружается ОДИН раз при первом
    // клике, потом состояние preserved через navigation back.
    zakroma: {
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
    // expectedCount = undefined: тайлы ещё не загружены в этот момент,
    // backend fallback'ит на DB-запрос. Передавать 0 нельзя — bug 181/243.
    if (this.authorChosen)
      this.loadZakromaStream({ author: this.selectedAuthor, expectedCount: undefined })
  },
  methods: {
    ...mapActions('zakroma', ['loadAuthorTiles', 'loadZakromaStream', 'loadSpecialBucket']),
    /** Переключатель "сквозной/по группам" (FR-023) — персистентно в localStorage. */
    setAlbumDisplayMode(mode) {
      this.albumDisplayMode = mode
      localStorage.setItem('km-zakroma-album-mode', mode)
    },
    /** Быстрый фильтр по типу альбома (FR-025/027) — вкл/выкл, персистентно в localStorage;
     * действует одинаково в обоих режимах отображения (сквозном и групповом). */
    toggleAlbumType(dbValue) {
      const next = new Set(this.hiddenAlbumTypes)
      if (next.has(dbValue)) {
        next.delete(dbValue)
      } else {
        next.add(dbValue)
      }
      this.hiddenAlbumTypes = next
      localStorage.setItem('km-zakroma-hidden-album-types', JSON.stringify(Array.from(next)))
    },
    /** Альбомы автора без скрытых по быстрому фильтру типов — общая точка для обоих режимов
     * отображения (визуальный порядок внутри — как пришло с бэка, уже отсортировано по sortOrder,
     * см. US4). */
    visibleAlbums(zak) {
      return (zak.albums || []).filter((alb) => !this.hiddenAlbumTypes.has(alb.albumType))
    },
    /** Единый плоский список элементов для рендера альбомного блока автора: в сквозном режиме —
     * только альбомы (уже отфильтрованные быстрым фильтром), в групповом — заголовок группы перед
     * каждой непустой группой (FR-024), в порядке `albumTypeCounts` (studio→single→live→
     * compilation→bootleg, только типы с count>0 у этого автора). */
    albumRenderItems(zak) {
      const visible = this.visibleAlbums(zak)
      if (this.albumDisplayMode !== 'grouped') {
        return visible.map((alb) => ({ type: 'album', key: `a-${alb.albumName}-${alb.year}`, alb }))
      }
      const items = []
      for (const summary of zak.albumTypeCounts || []) {
        const albumsOfType = visible.filter((alb) => alb.albumType === summary.dbValue)
        if (albumsOfType.length === 0) continue
        items.push({ type: 'header', key: `h-${summary.dbValue}`, label: summary.groupLabel })
        for (const alb of albumsOfType) {
          items.push({ type: 'album', key: `a-${alb.albumName}-${alb.year}`, alb })
        }
      }
      return items
    },
    // Монетка «премиум-контент» — только не-премиум посетителю и только для контента, недоступного
    // бесплатно прямо сейчас (specs/143-song-free-access-window: вне эфира ИЛИ окно истекло, и не
    // помечено «всегда бесплатно» неявно учтено внутри freelyAvailableNow). Золотая/серебряная — по
    // contentReadyFor(). Не гейтится личной покупкой — коин это категория контента, не персональный
    // статус доступа (см. showCartIcon/showDate ниже, где покупка уже учитывается).
    showCoin(sett) {
      return !this.isPremium && !sett.freelyAvailableNow
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
    // Личная подписка на эту конкретную песню уже даёт доступ ('active' в readiness), но песня
    // при этом не свободно доступна всем (иначе доступ дало бы окно/alwaysFree, не покупка) —
    // используется, чтобы скрыть текст "Будет в эфире с…"/"В эфире до…" для уже купленных песен
    // (FR-009 spec.md).
    isPurchased(sett) {
      return !sett.freelyAvailableNow && this.readiness.stateFor(sett.id) === 'active'
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
    // FR-009/FR-010 spec.md (specs/143-song-free-access-window): текст о сроках эфира — только
    // непремиум-пользователю, только для не-всегда-бесплатных и не-купленных песен, и только когда
    // есть что показать (ещё не в эфире, но дата НАЗНАЧЕНА — дата эфира; в эфире и в окне — дата
    // окончания окна). Для "в эфире, окно истекло, не куплена" И для "дата эфира вообще не
    // назначена" (готова, но ждёт публикации — не "будет в эфире", а просто пока недоступна) —
    // намеренно пусто (см. data-model.md, роль иконки-монетки; баг-репорт: без проверки
    // airTimestamp здесь показывалось нелепое "Будет в эфире с Дата пока не определена").
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
    onAuthorSelect(author) {
      this.selectedAuthor = author
      this.authorChosen = true
      this.songFilter = ''
      this.$router.replace({ path: '/zakroma', query: author ? { author } : {} })
      // 181: stream loader (FR-FE-003). expectedCount берётся с тайла — ищем
      // в authorTiles (имя автора → songCount). **MUST** be undefined если
      // тайла нет (deep-link `?author=...` до загрузки тайлов, или автор
      // не в основном списке) — backend fallback'ит на DB-запрос
      // `Song.loadAuthorSongCounts(...)`. Иначе фронт пришлёт 0 и
      // `meta` будет «0 из 0» (регрессия 181/243).
      const tile = (this.authorTiles || []).find((t) => t.author === author)
      const expectedCount = tile ? tile.songCount : undefined
      this.loadZakromaStream({ author, expectedCount })
    },
    retryLoadZakroma() {
      // FR-FE-001: повторный запуск после ошибки.
      this.onAuthorSelect(this.selectedAuthor)
    },
    cancelZakromaStream() {
      // FR-FE-006: «Отмена» → abort fetch + возврат к сетке авторов.
      // T018: cancel() ВЫЗВАН ДО backToAuthors() — важно, иначе router
      // перейдёт на /zakroma (без query) до того, как controller.abort()
      // успеет сработать + state.zakroma будет сбрасываться уже после
      // смены маршрута (давая посетителю увидеть «зависший» список).
      //
      // На текущей фазе (v1) мы НЕ держим ссылку на composable в data()
      // (она создаётся внутри store action). Поэтому используем обходной
      // путь: force-refresh стрима с тем же автором → store создаст новый
      // composable, сразу вызовет controller.abort() через dedup-bypass
      // (lastTs=0 → force), catch обрабатывает 'aborted' → state
      // сбрасывается → backToAuthors().
      //
      // Это работает, но требует рефакторинга в Phase 6+: держать composable
      // в data(), expose cancel() через setup() return.
      // expectedCount = undefined: backend fallback'ит на DB-запрос.
      this.loadZakromaStream({ author: this.selectedAuthor, expectedCount: undefined }).catch(
        () => {
          // ignore — abort обработан внутри
        },
      )
      this.backToAuthors()
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

/* Переключатель сквозной/групповой + быстрые фильтры по типу альбома
   (specs/012-entity-description-fields FR-023/024/025/026/027) — переиспользует визуальный
   паттерн .km-theme-toggle/.km-tb (та же "таблетка"), sticky-панель как .km-filter-bar. */
.km-album-controls-bar {
  position: sticky;
  top: 53px;
  z-index: 89;
  background: var(--km-header);
  border-bottom: 1px solid var(--km-border);
  padding: 0.5rem 1rem;
}
.km-album-controls-inner {
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}
.km-album-type-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.km-album-type-filter-btn {
  background: var(--km-accent);
  color: #fff;
  border: 1px solid var(--km-accent);
  border-radius: 14px;
  padding: 0.15rem 0.6rem;
  font-size: 0.8rem;
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s;
}
.km-album-type-filter-btn.off {
  background: transparent;
  color: var(--km-text2);
  border-color: var(--km-border);
}
.km-album-group-header {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--km-text);
  margin: 1rem 0 0.4rem;
  padding-bottom: 0.25rem;
  border-bottom: 1px solid var(--km-border);
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

/* 181: real-time progress meter (FR-FE-005, FR-FE-011). */
.km-stream-progress {
  position: sticky;
  top: 56px;
  z-index: 50;
  background: var(--km-bg2);
  border-bottom: 1px solid var(--km-border);
  padding: 0.6rem 1rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}
.km-stream-text {
  font-size: 0.95rem;
  color: var(--km-text);
  flex: 1 1 auto;
  min-width: 0;
}
.km-stream-bar {
  flex: 2 1 240px;
  height: 4px;
  background: var(--km-bg3, var(--km-bg));
  border-radius: 2px;
  overflow: hidden;
  min-width: 120px;
}
.km-stream-bar-fill {
  height: 100%;
  background: var(--km-accent);
  transition: width 0.2s ease;
}
.km-stream-cancel {
  background: transparent;
  color: var(--km-text2);
  border: 1px solid var(--km-border);
  border-radius: 14px;
  padding: 0.3rem 0.9rem;
  cursor: pointer;
  font-size: 0.9rem;
}
.km-stream-cancel:hover {
  background: var(--km-bg3, var(--km-bg));
  color: var(--km-text);
}

/* 181: error + retry (FR-FE-001 сценарий 4). */
.km-stream-error {
  padding: 1rem;
  margin: 0.5rem 1rem;
  background: var(--km-bg2);
  border: 1px solid var(--km-warn, #c33);
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
  color: var(--km-text);
}
.km-stream-retry {
  background: var(--km-accent);
  color: #fff;
  border: none;
  border-radius: 14px;
  padding: 0.3rem 0.9rem;
  cursor: pointer;
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
/* specs/012-entity-description-fields: предупреждение (красным, над именем), короткое описание
   (серым, через пробел после имени), описание — в title-тултипе на имени/названии. */
.km-author-name-wrap,
.km-album-name-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}
.km-author-name-line,
.km-album-name-line {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
  flex-wrap: wrap;
}
.km-warning-text {
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--km-danger, #dc3545);
  text-transform: uppercase;
}
.km-short-description-text {
  font-size: 0.85rem;
  font-weight: 400;
  color: var(--km-text2);
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
.km-album-type-badge {
  font-size: 0.7rem;
  font-weight: 500;
  color: var(--km-text2);
  text-transform: uppercase;
  letter-spacing: 0.02em;
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
/* Воздух справа от иконки плеера (после удаления PlatformLink[sponsr]):
   расстояние между PlayerIcon и FavoriteIcon — как между FavoriteIcon и PlaylistIcon */
.km-player-cell {
  padding-right: 1rem;
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
