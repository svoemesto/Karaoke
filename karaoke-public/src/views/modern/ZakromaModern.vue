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
            <button :class="['km-tb', theme === 'light' ? 'active' : '']" @click="setTheme('light')" title="Светлая">☀</button>
            <button :class="['km-tb', theme === 'system' ? 'active' : '']" @click="setTheme('system')" title="Авто">⬡</button>
            <button :class="['km-tb', theme === 'dark' ? 'active' : '']" @click="setTheme('dark')" title="Тёмная">🌙</button>
          </div>
        </div>
      </div>
    </header>

    <!-- Фильтр автора -->
    <div class="km-content">
      <AuthorTiles
        v-if="!authorChosen"
        :tiles="authorTiles"
        :selected="selectedAuthor"
        variant="modern"
        @select="onAuthorSelect"
      />

      <button v-if="authorChosen" type="button" class="km-back-btn" @click="backToAuthors">← К списку авторов</button>

      <div v-if="authorChosen && isLoading" class="km-loading">Загрузка...</div>

      <div v-for="zak in (authorChosen ? zakroma : [])" :key="zak.author" class="km-author-block">
        <!-- Заголовок автора -->
        <div class="km-author-header">
          <img
            v-if="zak.authorPictureUrl"
            :src="zak.authorPictureUrl"
            class="km-author-pic"
            @error="$event.target.style.display='none'"
            alt=""
          />
          <span class="km-author-name">{{ zak.author }}</span>
        </div>

        <!-- Альбомы -->
        <div v-for="alb in zak.albums" :key="alb.albumName" class="km-album-block">
          <div class="km-album-header">
            <img
              v-if="alb.albumPictureUrl"
              :src="alb.albumPictureUrl"
              class="km-album-pic"
              @error="$event.target.style.display='none'"
              alt=""
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
                <col style="width: 32px" />
                <col style="width: 26px" />
              </colgroup>
              <thead>
                <tr>
                  <th class="km-th km-th-center">№</th>
                  <th class="km-th">Композиция</th>
                  <th class="km-th" colspan="4">&nbsp;</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="sett in alb.albumSettings" :key="sett.id" class="km-tr">
                  <td class="km-td km-td-center km-track">{{ sett.track }}</td>
                  <td class="km-td km-td-name">
                    <RouterLink :to="{ path: '/song', query: { id: sett.id } }" class="km-song-link">{{ sett.songName }}</RouterLink>
                  </td>
                  <td class="km-td km-td-date">
                    <span v-if="showDate(sett)" class="km-date-text">{{ sett.datePublish }}</span>
                    <PremiumIcon v-if="showCoin(sett)" :state="readiness.contentReadyFor(sett.id)" />
                  </td>
                  <td class="km-td km-td-center">
                    <PlayerIcon :song-id="sett.id" :state="readiness.stateFor(sett.id)" />
                  </td>
                  <td class="km-td km-td-center">
                    <PlatformLink link-name="sponsr" :link-value="sett.linkSponsrPlay" :song-id="sett.id" song-version="all" />
                  </td>
                  <td class="km-td km-td-center km-group-end">
                    <FavoriteIcon :song-id="sett.id" />
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
                <RouterLink :to="{ path: '/song', query: { id: sett.id } }" class="km-card-title">{{ sett.songName }}</RouterLink>
                <PlayerIcon :song-id="sett.id" :state="readiness.stateFor(sett.id)" />
                <PlatformLink link-name="sponsr" :link-value="sett.linkSponsrPlay" :song-id="sett.id" song-version="all" />
                <FavoriteIcon :song-id="sett.id" />
              </div>
              <div v-if="showDate(sett) || showCoin(sett)" class="km-card-date">
                <span v-if="showDate(sett)" class="km-date-text">{{ sett.datePublish }}</span>
                <PremiumIcon v-if="showCoin(sett)" :state="readiness.contentReadyFor(sett.id)" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex'
import PlatformLink from '../../components/PlatformLink.vue'
import PlayerIcon from '../../components/PlayerIcon.vue'
import PremiumIcon from '../../components/PremiumIcon.vue'
import FavoriteIcon from '../../components/FavoriteIcon.vue'
import AuthStatusWidget from '../../components/AuthStatusWidget.vue'
import AuthorTiles from '../../components/AuthorTiles.vue'
import { useDesign } from '../../composables/useDesign'
import { usePlayerReadiness } from '../../composables/usePlayerReadiness'
import { usePlaylistMembership } from '../../composables/usePlaylistMembership'
import { useAuth } from '../../composables/useAuth'

export default {
  name: 'ZakromaModern',
  components: { PlatformLink, PlayerIcon, PremiumIcon, FavoriteIcon, AuthStatusWidget, AuthorTiles },
  setup() {
    const { theme, applyTheme } = useDesign()
    const { user } = useAuth()
    function setTheme(val) { theme.value = val; applyTheme(val) }
    return { theme, setTheme, readiness: usePlayerReadiness(), membership: usePlaylistMembership(), user }
  },
  data() {
    return {
      selectedAuthor: this.$route.query.author || '',
      // Плитки-пикер видны, пока автор не выбран. После выбора (в т.ч. «Все авторы») скрываются.
      authorChosen: !!this.$route.query.author
    }
  },
  computed: {
    ...mapGetters('zakroma', ['authorTiles', 'zakroma', 'isLoading']),
    isPremium() {
      return !!(this.user && this.user.effectivePremium)
    },
  },
  watch: {
    // Готовность плеера подгружаем асинхронно, как только пришли данные закромов (и при их смене).
    zakroma: {
      immediate: true,
      handler(list) {
        const ids = (list || []).flatMap(z => z.albums.flatMap(a => a.albumSettings.map(s => s.id)))
        this.readiness.load(ids)
        this.membership.load(ids)
      }
    }
  },
  mounted() {
    this.loadAuthorTiles()
    // Таблицу грузим только если автор уже выбран (например, зашли по ссылке ?author=...).
    if (this.authorChosen) this.loadZakroma(this.selectedAuthor)
  },
  methods: {
    ...mapActions('zakroma', ['loadAuthorTiles', 'loadZakroma']),
    // Монетка «премиум-контент» — только не-премиум посетителю и только для контента, доступного
    // лишь премиуму (эксклюзив или ещё не в эфире). Золотая/серебряная — по contentReadyFor().
    showCoin(sett) {
      return !this.isPremium && (sett.exclusive || !sett.onAir)
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
      this.$router.replace({ path: '/zakroma', query: author ? { author } : {} })
      this.loadZakroma(author)
    },
    backToAuthors() {
      this.selectedAuthor = ''
      this.authorChosen = false
      this.$router.replace({ path: '/zakroma', query: {} })
    }
  }
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
.km-back:hover { text-decoration: underline; }
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
  transition: background 0.15s, color 0.15s;
}
.km-tb:hover { background: var(--km-hover); color: var(--km-text); }
.km-tb.active { background: var(--km-accent); color: #fff; }

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
  transition: background 0.15s, box-shadow 0.15s;
}
.km-back-btn:hover { background: var(--km-hover); box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3); }

/* Блок автора */
.km-author-block { margin-bottom: 2rem; }
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

/* Блок альбома */
.km-album-block { margin-bottom: 1.5rem; }
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
.km-th-center { text-align: center; }
.km-td {
  padding: 0.25rem 0.3rem;
  border-bottom: 1px solid var(--km-border);
  font-size: 0.8rem;
  vertical-align: middle;
}
.km-tr:last-child .km-td { border-bottom: none; }
.km-tr:hover .km-td { background: var(--km-hover); }
.km-td-center { text-align: center; }
.km-td-name { text-align: left; }
.km-td-date { text-align: right; color: var(--km-text2); font-size: 0.78rem; white-space: nowrap; }
.km-date-text { margin-right: 5px; vertical-align: middle; }
.km-track { text-align: center; color: var(--km-text2); }
.km-group-end { border-right: 2px solid var(--km-border); }
.km-song-link {
  color: var(--km-accent);
  text-decoration: none;
  font-size: 0.82rem;
}
.km-song-link:hover { text-decoration: underline; }

/* Мобильные карточки */
.km-cards { display: none; }

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
.km-card-title:hover { text-decoration: underline; }
.km-card-date {
  font-size: 0.78rem;
  color: var(--km-text2);
  text-align: center;
  padding-top: 0.25rem;
}

/* Адаптивность */
@media (max-width: 768px) {
  .km-table-wrap { display: none; }
  .km-cards { display: block; }
}
</style>
