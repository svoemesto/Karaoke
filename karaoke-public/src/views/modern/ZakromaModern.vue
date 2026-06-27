<template>
  <div class="km-page">
    <!-- Хедер -->
    <header class="km-header">
      <div class="km-header-inner">
        <div class="km-header-left">
          <RouterLink to="/" class="km-back">← Главная</RouterLink>
          <a href="/"><img src="/KARAOKE_LOGO.png" class="km-logo" alt="Karaoke logo" /></a>
        </div>
        <div class="km-theme-toggle">
          <button :class="['km-tb', theme === 'light' ? 'active' : '']" @click="setTheme('light')" title="Светлая">☀</button>
          <button :class="['km-tb', theme === 'system' ? 'active' : '']" @click="setTheme('system')" title="Авто">⬡</button>
          <button :class="['km-tb', theme === 'dark' ? 'active' : '']" @click="setTheme('dark')" title="Тёмная">🌙</button>
        </div>
      </div>
    </header>

    <!-- Фильтр автора -->
    <div class="km-content">
      <select v-model="selectedAuthor" class="km-select" @change="onAuthorChange">
        <option value="">(Выберите автора)</option>
        <option v-for="a in authors" :key="a" :value="a">{{ a }}</option>
      </select>

      <div v-if="isLoading" class="km-loading">Загрузка...</div>

      <div v-for="zak in zakroma" :key="zak.author" class="km-author-block">
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
                <col style="width: 32px" />
                <col style="width: 22px" /><col style="width: 22px" /><col style="width: 22px" /><col style="width: 26px" />
                <col style="width: 22px" /><col style="width: 22px" /><col style="width: 22px" /><col style="width: 26px" />
                <col style="width: 22px" /><col style="width: 22px" /><col style="width: 22px" /><col style="width: 26px" />
                <col style="width: 22px" /><col style="width: 22px" /><col style="width: 22px" /><col style="width: 22px" />
              </colgroup>
              <thead>
                <tr>
                  <th class="km-th km-th-center">№</th>
                  <th class="km-th km-group-end" colspan="2">Композиция</th>
                  <th class="km-th km-th-center km-group-end" colspan="4">Karaoke</th>
                  <th class="km-th km-th-center km-group-end" colspan="4">Lyrics</th>
                  <th class="km-th km-th-center km-group-end" colspan="4">TABS</th>
                  <th class="km-th km-th-center" colspan="4">Chords</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="sett in alb.albumSettings" :key="sett.id" class="km-tr">
                  <td class="km-td km-td-center km-track">{{ sett.track }}</td>
                  <td class="km-td km-td-name">
                    <RouterLink :to="{ path: '/song', query: { id: sett.id } }" class="km-song-link">{{ sett.songName }}</RouterLink>
                  </td>
                  <td class="km-td km-td-center km-group-end">
                    <PlatformLink link-name="sponsr" :link-value="sett.linkSponsrPlay" :song-id="sett.id" song-version="all" />
                  </td>
                  <template v-if="sett.onAir">
                    <td class="km-td km-td-icon"><PlatformLink link-name="dzen" :link-value="sett.linkDzenKaraoke" :song-id="sett.id" song-version="karaoke" /></td>
                    <td class="km-td km-td-icon"><PlatformLink link-name="max"  :link-value="sett.linkMaxKaraoke"  :song-id="sett.id" song-version="karaoke" /></td>
                    <td class="km-td km-td-icon"><PlatformLink link-name="vk"   :link-value="sett.linkVkKaraoke"   :song-id="sett.id" song-version="karaoke" /></td>
                    <td class="km-td km-td-icon km-group-end"><PlatformLink link-name="tg" :link-value="sett.linkTgKaraoke" :song-id="sett.id" song-version="karaoke" /></td>
                    <td class="km-td km-td-icon"><PlatformLink link-name="dzen" :link-value="sett.linkDzenLyrics"  :song-id="sett.id" song-version="lyrics" /></td>
                    <td class="km-td km-td-icon"><PlatformLink link-name="max"  :link-value="sett.linkMaxLyrics"   :song-id="sett.id" song-version="lyrics" /></td>
                    <td class="km-td km-td-icon"><PlatformLink link-name="vk"   :link-value="sett.linkVkLyrics"    :song-id="sett.id" song-version="lyrics" /></td>
                    <td class="km-td km-td-icon km-group-end"><PlatformLink link-name="tg" :link-value="sett.linkTgLyrics"  :song-id="sett.id" song-version="lyrics" /></td>
                    <td class="km-td km-td-icon"><PlatformLink link-name="dzen" :link-value="sett.linkDzenTabs"    :song-id="sett.id" song-version="tabs" /></td>
                    <td class="km-td km-td-icon"><PlatformLink link-name="max"  :link-value="sett.linkMaxTabs"     :song-id="sett.id" song-version="tabs" /></td>
                    <td class="km-td km-td-icon"><PlatformLink link-name="vk"   :link-value="sett.linkVkTabs"      :song-id="sett.id" song-version="tabs" /></td>
                    <td class="km-td km-td-icon km-group-end"><PlatformLink link-name="tg" :link-value="sett.linkTgTabs"    :song-id="sett.id" song-version="tabs" /></td>
                    <td class="km-td km-td-icon"><PlatformLink link-name="dzen" :link-value="sett.linkDzenChords"  :song-id="sett.id" song-version="chords" /></td>
                    <td class="km-td km-td-icon"><PlatformLink link-name="max"  :link-value="sett.linkMaxChords"   :song-id="sett.id" song-version="chords" /></td>
                    <td class="km-td km-td-icon"><PlatformLink link-name="vk"   :link-value="sett.linkVkChords"    :song-id="sett.id" song-version="chords" /></td>
                    <td class="km-td km-td-icon"><PlatformLink link-name="tg"   :link-value="sett.linkTgChords"    :song-id="sett.id" song-version="chords" /></td>
                  </template>
                  <td v-else class="km-td km-td-date" colspan="16">{{ sett.datePublish }}</td>
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
                <PlatformLink link-name="sponsr" :link-value="sett.linkSponsrPlay" :song-id="sett.id" song-version="all" />
              </div>
              <template v-if="sett.onAir">
                <div class="km-card-platforms">
                  <div class="km-card-version">
                    <span class="km-ver-label">Karaoke</span>
                    <div class="km-ver-icons">
                      <PlatformLink link-name="dzen" :link-value="sett.linkDzenKaraoke" :song-id="sett.id" song-version="karaoke" />
                      <PlatformLink link-name="max"  :link-value="sett.linkMaxKaraoke"  :song-id="sett.id" song-version="karaoke" />
                      <PlatformLink link-name="vk"   :link-value="sett.linkVkKaraoke"   :song-id="sett.id" song-version="karaoke" />
                      <PlatformLink link-name="tg"   :link-value="sett.linkTgKaraoke"   :song-id="sett.id" song-version="karaoke" />
                    </div>
                  </div>
                  <div class="km-card-version">
                    <span class="km-ver-label">Lyrics</span>
                    <div class="km-ver-icons">
                      <PlatformLink link-name="dzen" :link-value="sett.linkDzenLyrics"  :song-id="sett.id" song-version="lyrics" />
                      <PlatformLink link-name="max"  :link-value="sett.linkMaxLyrics"   :song-id="sett.id" song-version="lyrics" />
                      <PlatformLink link-name="vk"   :link-value="sett.linkVkLyrics"    :song-id="sett.id" song-version="lyrics" />
                      <PlatformLink link-name="tg"   :link-value="sett.linkTgLyrics"    :song-id="sett.id" song-version="lyrics" />
                    </div>
                  </div>
                  <div class="km-card-version">
                    <span class="km-ver-label">TABS</span>
                    <div class="km-ver-icons">
                      <PlatformLink link-name="dzen" :link-value="sett.linkDzenTabs"    :song-id="sett.id" song-version="tabs" />
                      <PlatformLink link-name="max"  :link-value="sett.linkMaxTabs"     :song-id="sett.id" song-version="tabs" />
                      <PlatformLink link-name="vk"   :link-value="sett.linkVkTabs"      :song-id="sett.id" song-version="tabs" />
                      <PlatformLink link-name="tg"   :link-value="sett.linkTgTabs"      :song-id="sett.id" song-version="tabs" />
                    </div>
                  </div>
                  <div class="km-card-version">
                    <span class="km-ver-label">Chords</span>
                    <div class="km-ver-icons">
                      <PlatformLink link-name="dzen" :link-value="sett.linkDzenChords"  :song-id="sett.id" song-version="chords" />
                      <PlatformLink link-name="max"  :link-value="sett.linkMaxChords"   :song-id="sett.id" song-version="chords" />
                      <PlatformLink link-name="vk"   :link-value="sett.linkVkChords"    :song-id="sett.id" song-version="chords" />
                      <PlatformLink link-name="tg"   :link-value="sett.linkTgChords"    :song-id="sett.id" song-version="chords" />
                    </div>
                  </div>
                </div>
              </template>
              <div v-else class="km-card-date">{{ sett.datePublish }}</div>
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
import { useDesign } from '../../composables/useDesign'

export default {
  name: 'ZakromaModern',
  components: { PlatformLink },
  setup() {
    const { theme, applyTheme } = useDesign()
    function setTheme(val) { theme.value = val; applyTheme(val) }
    return { theme, setTheme }
  },
  data() {
    return { selectedAuthor: this.$route.query.author || '' }
  },
  computed: {
    ...mapGetters('zakroma', ['authors', 'zakroma', 'isLoading']),
  },
  mounted() {
    this.loadAuthors()
    this.loadZakroma(this.selectedAuthor)
  },
  methods: {
    ...mapActions('zakroma', ['loadAuthors', 'loadZakroma']),
    onAuthorChange() {
      const author = this.selectedAuthor
      this.$router.replace({ path: '/zakroma', query: author ? { author } : {} })
      this.loadZakroma(author)
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

/* Select */
.km-select {
  width: 100%;
  display: block;
  margin-bottom: 1.5rem;
  background: var(--km-input);
  color: var(--km-text);
  border: 1px solid var(--km-border);
  border-radius: 8px;
  padding: 0.4rem 0.75rem;
  font-size: 0.9rem;
  appearance: auto;
}

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
.km-td-icon { text-align: center; padding: 0; overflow: hidden; }
.km-td-name { text-align: left; }
.km-td-date { text-align: center; color: var(--km-text2); font-size: 0.78rem; }
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
.km-card-platforms {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.35rem 0.5rem;
}
.km-card-version {
  display: flex;
  align-items: center;
  gap: 0.3rem;
}
.km-ver-label {
  font-size: 0.68rem;
  color: var(--km-text2);
  min-width: 38px;
  font-weight: 600;
}
.km-ver-icons {
  display: flex;
  gap: 1px;
}

/* Адаптивность */
@media (max-width: 768px) {
  .km-table-wrap { display: none; }
  .km-cards { display: block; }
}
</style>
