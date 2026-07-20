<template>
  <transition name="modal-fade">
    <div class="fsm-modal-backdrop">
      <div class="fsm-area">
        <div class="fsm-header">
          <div class="fsm-header-title">Песни из той же группы (id / root_id)</div>
          <div class="fsm-search">
            <input
                v-model="searchQuery"
                type="text"
                class="fsm-search-input"
                placeholder="Поиск по названию песни..."
                @keyup.enter="doSearch"
            />
            <button type="button" class="fsm-search-button" @click="doSearch">Найти</button>
          </div>
        </div>
        <div class="fsm-body">
          <div v-if="loading" class="fsm-message">Загрузка...</div>
          <div v-else-if="songs.length === 0" class="fsm-message">{{ searched ? 'Ничего не найдено по запросу' : 'Похожих песен не найдено' }}</div>
          <table v-else class="fsm-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Автор</th>
                <th>Год</th>
                <th>Альбом</th>
                <th>Название</th>
                <th>Разница, сек</th>
                <th class="fsm-compare-th">
                  Сверка
                  <button type="button" class="fsm-compare-all" :disabled="comparingAll" @click="compareAll">
                    {{ comparingAll ? '…' : 'Сверить все' }}
                  </button>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
v-for="s in songs" :key="s.id"
                  class="fsm-row"
                  :class="{'fsm-row-original': s.original, 'fsm-row-disabled': s.current, 'fsm-row-low-status': s.idStatus < 3}"
                  :title="s.current ? 'Текущая песня' : 'Скопировать текст из этой песни'"
                  @click="select(s)">
                <td>{{ s.id }}</td>
                <td>{{ s.author }}</td>
                <td>{{ s.year || '' }}</td>
                <td class="fsm-col-left">{{ s.album }}</td>
                <td class="fsm-col-left">{{ s.songName }} <span v-if="s.original" class="fsm-original-badge" title="Оригинал">ОРИГИНАЛ</span></td>
                <td class="fsm-diff" :class="{'fsm-diff-zero': s.diffSeconds === 0}">{{ formatDiff(s.diffSeconds) }}</td>
                <td class="fsm-compare" @click.stop>
                  <template v-if="s.current">—</template>
                  <template v-else>
                    <span v-if="cmp(s).status === 'loading'" class="fsm-cmp-loading">…</span>
                    <button
v-else type="button" class="fsm-cmp-button" :class="cmpClass(s)"
                            :title="cmpTitle(s)" @click.stop="compareOne(s)">
                      <template v-if="cmp(s).status === 'done'">{{ cmp(s).similarityPercent }}% (Δ {{ fmtDelta(cmp(s).deltaMs) }})</template>
                      <template v-else-if="cmp(s).status === 'error'">ошибка</template>
                      <template v-else>Сверить</template>
                    </button>
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="fsm-footer">
          <button type="button" class="fsm-button-close" @click="close">Выход</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
export default {
  name: "FamilySongsModal",
  props: {
    id: {
      type: [String, Number],
      required: true
    }
  },
  data() {
    return {
      songs: [],
      loading: true,
      searchQuery: '',
      searched: false,
      compareResults: {},   // id -> {status:'idle'|'loading'|'done'|'error', similarityPercent, deltaMs, stemUsed, error}
      comparingAll: false
    };
  },
  async mounted() {
    try {
      const data = await this.$store.dispatch('getFamilySongsPromise');
      this.songs = JSON.parse(data);
    } finally {
      this.loading = false;
    }
  },
  methods: {
    formatDiff(diffSeconds) {
      return (diffSeconds > 0 ? '+' : '') + diffSeconds;
    },
    cmp(s) {
      return this.compareResults[s.id] || { status: 'idle' };
    },
    fmtDelta(ms) {
      return (ms > 0 ? '+' : '') + ms + ' мс';
    },
    cmpClass(s) {
      const r = this.cmp(s);
      if (r.status !== 'done') return '';
      return r.similarityPercent >= 90 ? 'fsm-cmp-high' : (r.similarityPercent >= 60 ? 'fsm-cmp-mid' : 'fsm-cmp-low');
    },
    cmpTitle(s) {
      const r = this.cmp(s);
      if (r.status === 'done') return 'Стем: ' + (r.stemUsed === 'vocals' ? 'вокал' : 'микс') + '. Нажмите, чтобы пересверить';
      if (r.status === 'error') return r.error || 'Ошибка сверки';
      return 'Сравнить вейвформы вокала с текущей песней';
    },
    async compareOne(s) {
      if (s.current) return;
      this.compareResults[s.id] = { status: 'loading' };
      try {
        const data = await this.$store.dispatch('compareWaveformPromise', { idAnother: s.id });
        const r = JSON.parse(data);
        if (r && r.ok) {
          this.compareResults[s.id] = {
            status: 'done',
            similarityPercent: r.similarityPercent,
            deltaMs: r.deltaMs,
            stemUsed: r.stemUsed
          };
        } else {
          this.compareResults[s.id] = { status: 'error', error: (r && r.error) || 'Ошибка сверки' };
        }
      } catch (e) {
        this.compareResults[s.id] = { status: 'error', error: 'Ошибка запроса' };
      }
    },
    async compareAll() {
      if (this.comparingAll) return;
      this.comparingAll = true;
      try {
        const targets = this.songs.filter(s => !s.current);
        const CONC = 3;
        let idx = 0;
        const worker = async () => {
          while (idx < targets.length) {
            const s = targets[idx++];
            await this.compareOne(s);
          }
        };
        await Promise.all(Array.from({ length: Math.min(CONC, targets.length) }, worker));
      } finally {
        this.comparingAll = false;
      }
    },
    select(song) {
      if (song.current) return;
      const r = this.compareResults[song.id];
      const deltaMs = (r && r.status === 'done') ? r.deltaMs : null;
      this.$emit('select', { id: song.id, deltaMs: deltaMs });
    },
    close() {
      this.$emit('close');
    },
    async doSearch() {
      const query = this.searchQuery.trim();
      if (!query) return;
      this.loading = true;
      this.searched = true;
      try {
        const data = await this.$store.dispatch('searchOriginalSongsPromise', {search: query});
        this.songs = JSON.parse(data);
      } finally {
        this.loading = false;
      }
    }
  }
}
</script>

<style scoped>

.fsm-modal-fade-enter,
.fsm-modal-fade-leave-active {
  opacity: 0;
}

.fsm-modal-fade-enter-active,
.fsm-modal-fade-leave-active {
  transition: opacity .5s ease
}

.fsm-modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1055;
}

.fsm-area {
  background: #FFFFFF;
  box-shadow: 2px 2px 20px 1px;
  overflow-x: auto;
  display: flex;
  flex-direction: column;
  width: auto;
  height: auto;
  min-width: 500px;
  position: relative;
  max-width: 1280px;
  max-height: 720px;
  font-family: Avenir, Helvetica, Arial, sans-serif;
  font-weight: 300;
}

.fsm-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.fsm-header-title {
  white-space: nowrap;
}

.fsm-search {
  display: flex;
  gap: 6px;
  align-items: center;
}

.fsm-search-input {
  font-size: 14px;
  padding: 4px 8px;
  border: 1px solid #999;
  border-radius: 4px;
  min-width: 220px;
}

.fsm-search-button {
  border: 1px solid white;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  font-weight: bold;
  color: #4AAE9B;
  background: transparent;
  padding: 4px 14px;
  white-space: nowrap;
}

.fsm-search-button:hover {
  background: darkgreen;
}

.fsm-body {
  background-color: white;
  padding: 10px;
  color: black;
  overflow-y: auto;
}

.fsm-message {
  padding: 20px;
  text-align: center;
  color: #555;
}

.fsm-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.fsm-table th {
  text-align: center;
  padding: 4px 8px;
  border-bottom: 2px solid darkslategray;
  border-right: 1px solid #bbb;
}

.fsm-table th:last-child {
  border-right: none;
}

.fsm-table td {
  text-align: center;
  padding: 4px 8px;
  border-bottom: 1px solid #ddd;
  border-right: 1px solid #ddd;
}

.fsm-table td.fsm-col-left {
  text-align: left;
}

.fsm-table td:last-child {
  border-right: none;
}

.fsm-row {
  cursor: pointer;
}

.fsm-row:hover {
  background-color: #e8f4f2;
}

.fsm-row-low-status {
  background-color: #f0f0f0;
}

.fsm-row-low-status:hover {
  background-color: #e4e4e4;
}

.fsm-row-disabled {
  cursor: default;
  color: #aaa;
  background-color: #f4f4f4;
}

.fsm-row-disabled:hover {
  background-color: #f4f4f4;
}

.fsm-row-original {
  background-color: #fff6df;
  font-weight: bold;
}

.fsm-row-original:hover {
  background-color: #fdecb8;
}

.fsm-original-badge {
  display: inline-block;
  font-size: 10px;
  font-weight: bold;
  letter-spacing: 0.5px;
  color: #8a6100;
  background-color: #ffd76a;
  border-radius: 6px;
  padding: 1px 5px;
  margin-left: 6px;
  vertical-align: middle;
}

.fsm-diff {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.fsm-diff-zero {
  color: #4AAE9B;
  font-weight: bold;
}

.fsm-compare-th {
  white-space: nowrap;
}

.fsm-compare-all {
  margin-left: 8px;
  border: 1px solid #4AAE9B;
  border-radius: 6px;
  font-size: 11px;
  font-weight: bold;
  cursor: pointer;
  color: #4AAE9B;
  background: white;
  padding: 2px 8px;
  white-space: nowrap;
}

.fsm-compare-all:hover:not(:disabled) {
  background: #4AAE9B;
  color: white;
}

.fsm-compare-all:disabled {
  cursor: default;
  opacity: 0.5;
}

.fsm-compare {
  text-align: center;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.fsm-cmp-loading {
  color: #888;
}

.fsm-cmp-button {
  border: 1px solid #bbb;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  background: white;
  color: #333;
  padding: 2px 8px;
  white-space: nowrap;
}

.fsm-cmp-button:hover {
  background: #eef;
}

.fsm-cmp-high {
  border-color: #2e9c4a;
  color: #1c6e31;
  font-weight: bold;
  background: #eafbee;
}

.fsm-cmp-mid {
  border-color: #c99a1e;
  color: #8a6100;
  background: #fff8e6;
}

.fsm-cmp-low {
  border-color: #c05252;
  color: #8a2f2f;
  background: #fdecec;
}

.fsm-footer {
  background-color: darkslategray;
  padding: 10px;
  display: flex;
  justify-content: flex-end;
}

.fsm-button-close {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 18px;
  cursor: pointer;
  font-weight: bold;
  color: #4AAE9B;
  background: transparent;
  width: 150px;
  height: auto;
}

.fsm-button-close:hover {
  background: darkgreen;
}

</style>
