<template>
  <transition name="modal-fade">
    <div class="aab-modal-backdrop">
      <div class="aab-area">
        <div class="aab-area-modal-header">Альбомы автора: {{ author.author }}</div>
        <div class="aab-area-modal-body">
          <p class="aab-hint">
            Перетащите строку за {{ '⠿' }}, чтобы изменить порядок отображения альбомов автора
            (сквозной, общий для всех лет, а не отдельно по годам).
          </p>
          <div v-if="loading" class="aab-empty">Загрузка…</div>
          <div v-else-if="!albums.length" class="aab-empty">У автора пока нет альбомов.</div>
          <table v-else class="aab-table">
            <thead>
              <tr>
                <th class="aab-th-handle" />
                <th class="aab-th-pos">#</th>
                <th>Год</th>
                <th>Название</th>
                <th>Тип</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(album, idx) in albums"
                :key="album.id"
                class="aab-row"
                :class="{ 'aab-row-dragging': dragIndex === idx }"
                draggable="true"
                @dragstart="onDragStart(idx)"
                @dragover.prevent
                @dragenter.prevent="onDragEnter(idx)"
                @drop.prevent
                @dragend="onDragEnd"
              >
                <td class="aab-handle" title="Перетащить">⠿</td>
                <td class="aab-pos">{{ idx + 1 }}</td>
                <td>{{ album.year }}</td>
                <td>{{ album.name }}</td>
                <td>{{ albumTypeLabel(album.albumType) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="aab-area-modal-footer">
          <button type="button" class="aab-btn-save" :disabled="saving || loading" @click="save">
            Сохранить
          </button>
          <button type="button" class="aab-btn-close" @click="close">Отмена</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
// specs/011-album-song-rename: значения соответствуют AlbumType.dbValue
// (karaoke-app/model/AlbumType.kt) — держать в синхроне при добавлении новых типов.
const ALBUM_TYPE_LABELS = {
  studio: 'Студийный',
  live: 'Концертный',
  compilation: 'Сборник',
  bootleg: 'Бутлег',
  single: 'Сингл',
  archive: 'Архивный',
  tribute: 'Трибьют/Кавер',
}

/**
 * Модальное окно со списком альбомов автора, порядок которых можно менять драг-дропом.
 *
 * Порядок — сквозной по автору (не по годам): при сохранении на сервер уходит id альбомов в
 * порядке строк таблицы, каждому присваивается sortOrder = его позиция (см. Album.reorderAlbums).
 *
 * @see docs/features/dual-db-sync.md
 */
export default {
  name: 'AuthorAlbumsModal',
  props: {
    authorId: {
      type: [Number, String],
      required: true,
    },
  },
  data() {
    return {
      albums: [],
      loading: true,
      saving: false,
      dragIndex: null,
    }
  },
  computed: {
    author() {
      return (this.$store.getters.getAuthorsDigest || []).find((a) => a.id === this.authorId) || {}
    },
  },
  created() {
    this.$store
      .dispatch('loadAlbumsByAuthorIdPromise', this.authorId)
      .then((albums) => {
        this.albums = (albums || [])
          .slice()
          .sort((a, b) => a.sortOrder - b.sortOrder || a.year - b.year)
        this.loading = false
      })
      .catch((error) => {
        console.error('Ошибка при загрузке альбомов автора:', error)
        this.loading = false
      })
  },
  methods: {
    albumTypeLabel(value) {
      return ALBUM_TYPE_LABELS[value] || value
    },
    onDragStart(idx) {
      this.dragIndex = idx
    },
    onDragEnter(idx) {
      if (this.dragIndex === null || this.dragIndex === idx) return
      const moved = this.albums.splice(this.dragIndex, 1)[0]
      this.albums.splice(idx, 0, moved)
      this.dragIndex = idx
    },
    onDragEnd() {
      this.dragIndex = null
    },
    save() {
      this.saving = true
      const ids = this.albums.map((a) => a.id)
      this.$store
        .dispatch('reorderAlbumsPromise', ids)
        .then(() => {
          this.$store.dispatch('loadAlbumsDigests', {})
          this.saving = false
          this.close()
        })
        .catch((error) => {
          console.error('Ошибка при сохранении порядка альбомов:', error)
          this.saving = false
        })
    },
    close() {
      this.$emit('close')
    },
  },
}
</script>

<style scoped>
.aab-modal-fade-enter,
.aab-modal-fade-leave-active {
  opacity: 0;
}

.aab-modal-fade-enter-active,
.aab-modal-fade-leave-active {
  transition: opacity 0.5s ease;
}

.aab-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.aab-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
  min-width: 400px;
}

.aab-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.aab-modal-backdrop {
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

.aab-area {
  background: #ffffff;
  box-shadow: 2px 2px 20px 1px;
  overflow-x: auto;
  display: flex;
  flex-direction: column;
  width: auto;
  height: auto;
  position: relative;
  max-width: calc(100vw - 20px);
  max-height: calc(100vh - 20px);
}

.aab-hint {
  font-size: small;
  color: dimgray;
  margin: 0 0 10px 0;
}

.aab-empty {
  font-size: small;
  color: gray;
  margin-bottom: 10px;
}

.aab-table {
  border-collapse: collapse;
  font-size: small;
}

.aab-table th {
  text-align: left;
  padding: 4px 8px;
  border-bottom: 2px solid black;
}

.aab-th-handle,
.aab-th-pos {
  width: 30px;
  text-align: center;
}

.aab-table td {
  padding: 4px 8px;
  border-bottom: 1px solid lightgray;
}

.aab-row {
  cursor: grab;
}

.aab-row-dragging {
  opacity: 0.4;
  background-color: lightyellow;
}

.aab-handle {
  text-align: center;
  color: gray;
  user-select: none;
}

.aab-pos {
  text-align: center;
  color: gray;
}

.aab-btn-save {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 18px;
  cursor: pointer;
  font-weight: bold;
  color: #4aae9b;
  background: transparent;
  width: 150px;
  height: auto;
}
.aab-btn-save:hover {
  background: darkgreen;
}
.aab-btn-save[disabled] {
  opacity: 0.5;
  cursor: default;
}

.aab-btn-close {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 20px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: transparent;
  width: 100px;
  height: auto;
}
</style>
