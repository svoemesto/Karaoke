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

    <div class="km-content">
      <!-- Форма поиска -->
      <div class="km-search-form">
        <div class="km-fields">
          <div class="km-field">
            <label class="km-label">Исполнитель</label>
            <input
              list="list_authors"
              v-model="form.author"
              class="km-input"
              placeholder="Введите имя..."
            />
            <datalist id="list_authors">
              <option v-for="a in authors" :key="a" :value="a" />
            </datalist>
          </div>
          <div class="km-field">
            <label class="km-label">Название</label>
            <input v-model="form.songName" class="km-input" placeholder="Название песни..." />
          </div>
          <div class="km-field">
            <label class="km-label">Слова</label>
            <input v-model="form.text" class="km-input" placeholder="Слова из текста..." @keyup.enter="onSearch" />
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
            <col style="width: 110px" />
            <col style="width: 38px" />
            <col style="width: 120px" />
            <col style="width: 26px" />
            <col />
            <col style="width: 220px" />
            <col style="width: 24px" />
            <col style="width: 32px" />
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
              <td class="km-td">{{ sett.author }}</td>
              <td class="km-td km-td-center">{{ sett.year }}</td>
              <td class="km-td">{{ sett.album }}</td>
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
            <span class="km-card-author">{{ sett.author }}</span>
            <span class="km-card-year">{{ sett.year }}</span>
            <span class="km-card-album">{{ sett.album }}</span>
          </div>
          <div class="km-card-top">
            <span class="km-card-track">{{ sett.track }}</span>
            <RouterLink :to="{ path: '/song', query: { id: sett.id } }" class="km-card-title">{{ sett.songName }}</RouterLink>
            <PlayerIcon :song-id="sett.id" :state="readiness.stateFor(sett.id)" />
            <PlatformLink link-name="sponsr" :link-value="sett.linkSponsrPlay" :song-id="sett.id" song-version="all" />
            <FavoriteIcon :song-id="sett.id" />
            <PlaylistIcon :song-id="sett.id" />
          </div>
          <div v-if="showDate(sett) || showCoin(sett)" class="km-card-date">
            <span v-if="showDate(sett)" class="km-date-text">{{ sett.datePublish }}</span>
            <PremiumIcon v-if="showCoin(sett)" :state="readiness.contentReadyFor(sett.id)" />
          </div>
        </div>
      </div>

      <p v-else-if="!searchIsLoading && searched" class="km-empty">Ничего не найдено.</p>
    </div>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex'
import PlatformLink from '../../components/PlatformLink.vue'
import PlayerIcon from '../../components/PlayerIcon.vue'
import PremiumIcon from '../../components/PremiumIcon.vue'
import FavoriteIcon from '../../components/FavoriteIcon.vue'
import PlaylistIcon from '../../components/PlaylistIcon.vue'
import AuthStatusWidget from '../../components/AuthStatusWidget.vue'
import { useDesign } from '../../composables/useDesign'
import { usePlayerReadiness } from '../../composables/usePlayerReadiness'
import { usePlaylistMembership } from '../../composables/usePlaylistMembership'
import { useAuth } from '../../composables/useAuth'

export default {
  name: 'SearchModern',
  components: { PlatformLink, PlayerIcon, PremiumIcon, FavoriteIcon, PlaylistIcon, AuthStatusWidget },
  setup() {
    const { theme, applyTheme } = useDesign()
    const { user } = useAuth()
    function setTheme(val) { theme.value = val; applyTheme(val) }
    return { theme, setTheme, readiness: usePlayerReadiness(), membership: usePlaylistMembership(), user }
  },
  data() {
    return {
      form: { songName: '', author: '', text: '' },
      searched: false
    }
  },
  computed: {
    ...mapGetters('songs', ['authors', 'searchResults', 'searchIsLoading']),
    isPremium() {
      return !!(this.user && this.user.effectivePremium)
    },
  },
  watch: {
    // Готовность плеера подгружаем асинхронно, как только пришли результаты поиска (и при их смене).
    searchResults: {
      immediate: true,
      handler(list) {
        const ids = (list || []).map(s => s.id)
        this.readiness.load(ids)
        this.membership.load(ids)
      }
    }
  },
  mounted() {
    this.loadAuthors()
    const q = this.$route.query
    if (q.author || q.songName || q.text) {
      this.form.author   = q.author   || ''
      this.form.songName = q.songName || ''
      this.form.text     = q.text     || ''
      this.onSearch()
    }
  },
  methods: {
    ...mapActions('songs', ['loadAuthors', 'search']),
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
    onSearch() {
      this.searched = true
      this.search({ songName: this.form.songName, author: this.form.author, text: this.form.text })
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
  max-width: 1000px;
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
.km-logo { height: 36px; width: auto; }
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
  max-width: 1000px;
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
.km-search-btn:hover { opacity: 0.88; }

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
.km-track { color: var(--km-text2); }
.km-group-end { border-right: 2px solid var(--km-border); }
.km-song-link { color: var(--km-accent); text-decoration: none; font-size: 0.82rem; }
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
.km-card-meta {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.3rem;
  flex-wrap: wrap;
}
.km-card-author { font-size: 0.78rem; font-weight: 700; color: var(--km-text); }
.km-card-year   { font-size: 0.72rem; color: var(--km-text2); }
.km-card-album  { font-size: 0.72rem; color: var(--km-text2); }
.km-card-top {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.4rem;
}
.km-card-track { font-size: 0.75rem; color: var(--km-text2); min-width: 20px; text-align: center; }
.km-card-title {
  flex: 1;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--km-accent);
  text-decoration: none;
}
.km-card-title:hover { text-decoration: underline; }
.km-card-date { font-size: 0.78rem; color: var(--km-text2); text-align: center; padding-top: 0.25rem; }

@media (max-width: 768px) {
  .km-table-wrap { display: none; }
  .km-cards { display: block; }
}
</style>
