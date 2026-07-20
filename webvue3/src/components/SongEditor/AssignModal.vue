<template>
  <div class="se-overlay" @click.self="$emit('close')">
    <div class="se-modal">
      <h3 class="se-modal-title">Назначить песню на разметку</h3>

      <label class="se-field">
        <span>Пользователь сайта</span>
        <select v-model="assigneeId">
          <option :value="0" disabled>— выберите —</option>
          <option v-for="u in siteUsers" :key="u.id" :value="u.id">
            {{ u.displayName || u.email }} ({{ u.email }})
          </option>
        </select>
      </label>

      <div class="se-field">
        <span>Песни (можно выбрать несколько)</span>

        <div v-if="selectedSongs.length > 0" class="se-selected-block">
          <div class="se-selected-chips">
            <div v-for="s in selectedSongs" :key="s.id" class="se-chip">
              <span>#{{ s.id }} · {{ s.author }} <template v-if="s.album">— {{ s.album }}</template> — {{ s.songName }}</span>
              <button type="button" class="se-chip-remove" title="Убрать из выбора" @click="toggleSelect(s)">✕</button>
            </div>
          </div>
          <button type="button" class="se-btn se-btn-clear-all" @click="selectedSongs = []">Очистить всё ({{ selectedSongs.length }})</button>
        </div>

        <datalist id="se-authors-list">
          <option v-for="a in dictAuthors" :key="a" :value="a" />
        </datalist>
        <div class="se-search-row">
          <input
              v-model="authorQuery"
              type="text"
              list="se-authors-list"
              placeholder="Автор..."
              class="se-search-author"
              @keyup.enter="doSearch"
          />
          <input
              v-model="albumQuery"
              type="text"
              placeholder="Альбом..."
              class="se-search-album"
              @keyup.enter="doSearch"
          />
          <input
              v-model="searchQuery"
              type="text"
              placeholder="Название песни..."
              class="se-search-name"
              @keyup.enter="doSearch"
          />
          <button type="button" class="se-btn" @click="doSearch">Найти</button>
        </div>
        <label class="se-checkbox">
          <input v-model="onlyStatus1" type="checkbox" @change="doSearch" />
          <span>Только кандидаты на разметку (статус «Создание текста»)</span>
        </label>
        <div v-if="searching" class="se-search-message">Загрузка...</div>
        <div v-else-if="!searched" class="se-search-message">Введите критерии и нажмите «Найти»</div>
        <div v-else-if="results.length === 0" class="se-search-message">Ничего не найдено</div>
        <template v-else>
          <button type="button" class="se-btn se-btn-select-all" @click="selectAllResults">
            Выбрать все результаты ({{ results.length }})
          </button>
          <ul class="se-result-list">
            <li
                v-for="s in results" :key="s.id" class="se-result-row"
                :class="{ 'se-result-row-selected': isSelected(s.id) }"
                @click="toggleSelect(s)"
            >
              <input type="checkbox" class="se-result-checkbox" :checked="isSelected(s.id)" @click.stop="toggleSelect(s)" />
              <span class="se-result-text">
                #{{ s.id }} · {{ s.author }} <template v-if="s.album">— {{ s.album }}</template> — {{ s.songName }}
                <template v-if="s.year">({{ s.year }})</template>
              </span>
              <span class="se-result-status">{{ s.status }}</span>
              <button type="button" class="se-btn se-btn-hr-mini" @click.stop="hrSongId = s.id">HR</button>
            </li>
          </ul>
        </template>
      </div>

      <p v-if="message" class="se-msg" :class="{ 'se-msg-err': isError }">{{ message }}</p>

      <div class="se-modal-btns">
        <button class="se-btn" @click="$emit('close')">Отмена</button>
        <button class="se-btn se-btn-primary" :disabled="!canSubmit || busy" @click="doAssign">
          {{ busy ? 'Назначаем…' : (selectedSongs.length > 1 ? `Назначить (${selectedSongs.length})` : 'Назначить') }}
        </button>
      </div>
    </div>

    <health-report-table v-if="hrSongId" :id="String(hrSongId)" @close="hrSongId = null" />
  </div>
</template>

<script>
import HealthReportTable from "../Common/HealthReport/HealthReportTable.vue";

export default {
  name: "AssignModal",
  components: { HealthReportTable },
  emits: ['close', 'assigned'],
  data() {
    return {
      assigneeId: 0, busy: false, message: '', isError: false,
      searchQuery: '', authorQuery: '', albumQuery: '', dictAuthors: [], results: [], searching: false, searched: false, onlyStatus1: true,
      selectedSongs: [], hrSongId: null,
    }
  },
  computed: {
    siteUsers() { return this.$store.getters.getEditorSiteUsers || [] },
    canSubmit() { return this.assigneeId > 0 && this.selectedSongs.length > 0 },
  },
  mounted() {
    this.$store.dispatch('loadEditorSiteUsers');
    this.$store.getters.songAuthorsPromise.then(data => {
      this.dictAuthors = JSON.parse(data).authors || [];
    }).catch(() => {});
  },
  methods: {
    async doSearch() {
      this.searching = true;
      this.searched = true;
      try {
        this.results = await this.$store.dispatch('searchCandidateSongs', {
          query: this.searchQuery.trim(),
          author: this.authorQuery.trim(),
          album: this.albumQuery.trim(),
          onlyStatus1: this.onlyStatus1,
        });
      } catch (e) {
        this.results = [];
      } finally {
        this.searching = false;
      }
    },
    isSelected(id) { return this.selectedSongs.some(s => s.id === id) },
    toggleSelect(s) {
      const idx = this.selectedSongs.findIndex(x => x.id === s.id);
      if (idx >= 0) this.selectedSongs.splice(idx, 1);
      else this.selectedSongs.push(s);
    },
    selectAllResults() {
      const known = new Set(this.selectedSongs.map(s => s.id));
      for (const s of this.results) if (!known.has(s.id)) this.selectedSongs.push(s);
    },
    // Назначает КАЖДУЮ выбранную песню отдельным запросом (бэкенд не умеет батч) — по одному
    // пользователю за раз. Успешно назначенные убираются из выбора; при частичном отказе модалка не
    // закрывается (иначе сообщение об ошибке мелькнёт и пропадёт вместе с размонтированием) — только
    // когда назначены ВСЕ, эмитим 'assigned' (родитель закрывает модалку и обновляет таблицу).
    //
    // Двухпроходный цикл — первый проход без clearMarkers; песни, у которых бэкенд нашёл непустые
    // маркеры (error "markers_exist"), собираются и переспрашиваются ОДНИМ confirm() на весь батч
    // (не по каждой песне отдельно), затем второй проход повторяет их запрос с явным clearMarkers.
    async doAssign() {
      if (!this.canSubmit) return;
      this.busy = true;
      this.message = '';
      const toAssign = [...this.selectedSongs];
      const succeeded = [];
      const failed = [];
      const pendingConfirm = [];

      for (const s of toAssign) {
        try {
          const res = await this.$store.dispatch('assignSong', { songId: s.id, assigneeId: this.assigneeId });
          if (res && res.ok) succeeded.push(s);
          else if (res && res.error === 'markers_exist') pendingConfirm.push(s);
          else failed.push({ song: s, error: res && res.error });
        } catch (e) {
          failed.push({ song: s, error: 'request_failed' });
        }
      }

      if (pendingConfirm.length > 0) {
        const clearMarkers = window.confirm('В песне уже есть маркеры. Удалить их при назначении задания?');
        for (const s of pendingConfirm) {
          try {
            const res = await this.$store.dispatch('assignSong', { songId: s.id, assigneeId: this.assigneeId, clearMarkers });
            if (res && res.ok) succeeded.push(s);
            else failed.push({ song: s, error: res && res.error });
          } catch (e) {
            failed.push({ song: s, error: 'request_failed' });
          }
        }
      }

      const succeededIds = new Set(succeeded.map(s => s.id));
      this.selectedSongs = this.selectedSongs.filter(s => !succeededIds.has(s.id));
      this.busy = false;
      if (failed.length === 0) {
        this.$emit('assigned');
      } else {
        this.isError = succeeded.length === 0;
        this.message = (succeeded.length > 0 ? `Назначено ${succeeded.length} из ${toAssign.length}. ` : '')
            + 'Не удалось: ' + failed.map(f => `#${f.song.id} — ${this.errorText(f.error)}`).join('; ');
      }
    },
    errorText(code) {
      return {
        song_not_found: 'Песня с таким ID не найдена',
        user_not_found: 'Пользователь не найден',
        already_assigned: 'уже назначена этому пользователю',
        request_failed: 'ошибка запроса',
      }[code] || 'не удалось назначить';
    }
  }
}
</script>

<style scoped>
.se-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.45);
  display: flex; align-items: center; justify-content: center; z-index: 1000;
}
.se-modal {
  background: #fff; border-radius: 12px; padding: 1.5rem; width: 920px; max-width: 92vw;
  display: flex; flex-direction: column; gap: 0.8rem; font-family: Avenir, Helvetica, Arial, sans-serif;
}
.se-modal-title { margin: 0 0 0.5rem; font-size: 1.1rem; }
.se-field { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.85rem; }
.se-field select, .se-field input {
  padding: 0.45rem 0.6rem; border: 1px solid #ccc; border-radius: 8px; font-size: 0.9rem;
}
.se-msg { font-size: 0.85rem; color: #2a7a3a; margin: 0; }
.se-msg-err { color: #c0392b; }
.se-modal-btns { display: flex; justify-content: flex-end; gap: 0.6rem; margin-top: 0.5rem; }
.se-btn {
  border: 1px solid #bbb; border-radius: 8px; padding: 0.45rem 1rem; background: antiquewhite;
  cursor: pointer; font-size: 0.9rem;
}
.se-btn:hover { background: lightpink; }
.se-btn-primary { background: #3b82f6; color: #fff; border: none; }
.se-btn-primary:hover { opacity: 0.9; background: #3b82f6; }
.se-btn:disabled { opacity: 0.5; cursor: default; }

.se-search-row { display: flex; gap: 0.4rem; }
.se-search-author { flex: 0 0 28%; }
.se-search-album { flex: 0 0 28%; }
.se-search-name { flex: 1; }
.se-checkbox {
  display: flex; align-items: center; gap: 0.4rem; font-size: 0.8rem; color: #444;
  flex-direction: row !important; cursor: pointer;
}
.se-checkbox input { padding: 0; width: auto; }
.se-search-message { font-size: 0.85rem; color: #777; padding: 0.4rem 0; }
.se-result-list {
  list-style: none; margin: 0; padding: 0; max-height: 180px; overflow-y: auto;
  border: 1px solid #ddd; border-radius: 8px;
}
.se-result-row {
  padding: 0.4rem 0.6rem; font-size: 0.85rem; cursor: pointer; border-bottom: 1px solid #eee;
  display: flex; justify-content: flex-start; align-items: center; gap: 0.5rem; text-align: left;
}
.se-result-row:last-child { border-bottom: none; }
.se-result-row:hover { background: #eef4ff; }
.se-result-status { font-size: 0.7rem; color: #888; white-space: nowrap; margin-left: auto; }
.se-result-checkbox { padding: 0; width: auto; flex: 0 0 auto; }
.se-result-text { flex: 1; }
.se-result-row-selected { background: #f3fbf3; }
.se-result-row-selected:hover { background: #e8f7ea; }
.se-btn-hr-mini { padding: 0.15rem 0.5rem; font-size: 0.72rem; flex: 0 0 auto; }
.se-btn-select-all { align-self: flex-start; font-size: 0.8rem; padding: 0.3rem 0.7rem; margin-bottom: 0.3rem; }
.se-selected-block {
  display: flex; flex-direction: column; gap: 0.35rem; padding: 0.5rem 0.6rem;
  border: 1px solid #cfe3cf; border-radius: 8px; background: #f3fbf3;
}
.se-selected-chips {
  display: flex; flex-wrap: wrap; align-content: flex-start; gap: 0.4rem;
  max-height: 140px; overflow-y: auto;
}
.se-chip {
  display: flex; align-items: center; gap: 0.35rem; background: #fff; border: 1px solid #cfe3cf;
  border-radius: 20px; padding: 0.2rem 0.5rem; font-size: 0.8rem;
}
.se-chip-remove {
  border: none; background: transparent; cursor: pointer; font-size: 0.85rem; color: #888; padding: 0;
  line-height: 1;
}
.se-chip-remove:hover { color: #c0392b; }
.se-btn-clear-all { align-self: flex-start; font-size: 0.78rem; padding: 0.2rem 0.6rem; }
</style>
