<template>
  <transition name="modal-fade">
    <div class="afm-modal-backdrop">
      <div class="afm-area">
        <div class="afm-area-modal-header">Фильтр для альбомов</div>

        <div class="afm-area-modal-body">
          <div class="afm-root-wrapper">
            <div class="afm-filter-row">
              <div class="afm-row-label">
                <div v-text="'ID:'" />
              </div>
              <div class="afm-row-input">
                <input v-model="albumsFilterId" class="afm-input-field" />
              </div>
              <button
                :disabled="!albumsFilterId"
                class="afm-button-clear-field"
                @click.left="albumsFilterId = ''"
                v-text="'X'"
              />
            </div>

            <div class="afm-filter-row">
              <div class="afm-row-label">
                <div v-text="'Автор:'" />
              </div>
              <div class="afm-row-input">
                <input
                  v-model="albumsFilterAuthorName"
                  list="list_authors"
                  class="afm-input-field"
                />
              </div>
              <button
                :disabled="!albumsFilterAuthorName"
                class="afm-button-clear-field"
                @click.left="albumsFilterAuthorName = ''"
                v-text="'X'"
              />
            </div>

            <div class="afm-filter-row">
              <div class="afm-row-label">
                <div v-text="'Год:'" />
              </div>
              <div class="afm-row-input">
                <input v-model="albumsFilterYear" class="afm-input-field" />
              </div>
              <button
                :disabled="!albumsFilterYear"
                class="afm-button-clear-field"
                @click.left="albumsFilterYear = ''"
                v-text="'X'"
              />
            </div>

            <div class="afm-filter-row">
              <div class="afm-row-label">
                <div v-text="'Название:'" />
              </div>
              <div class="afm-row-input">
                <input v-model="albumsFilterName" class="afm-input-field" />
              </div>
              <button
                :disabled="!albumsFilterName"
                class="afm-button-clear-field"
                @click.left="albumsFilterName = ''"
                v-text="'X'"
              />
            </div>

            <div class="afm-filter-row">
              <div class="afm-row-label">
                <div v-text="'Тип:'" />
              </div>
              <div class="afm-row-input">
                <select v-model="albumsFilterAlbumType" class="afm-input-field">
                  <option value="">— любой —</option>
                  <option v-for="opt in albumTypeOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </option>
                </select>
              </div>
              <button
                :disabled="!albumsFilterAlbumType"
                class="afm-button-clear-field"
                @click.left="albumsFilterAlbumType = ''"
                v-text="'X'"
              />
            </div>
          </div>
        </div>

        <div class="afm-area-modal-footer">
          <button type="button" class="afm-btn-close" @click="ok">Применить фильтр</button>
          <button type="button" class="afm-btn-close" @click="cancel">Отмена</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
// specs/011-album-song-rename: значения соответствуют AlbumType.dbValue
// (karaoke-app/model/AlbumType.kt) — держать в синхроне при добавлении новых типов.
const ALBUM_TYPE_LABEL_OPTIONS = [
  { value: 'studio', label: 'Студийный' },
  { value: 'live', label: 'Концертный' },
  { value: 'compilation', label: 'Сборник' },
  { value: 'bootleg', label: 'Бутлег' },
  { value: 'single', label: 'Сингл' },
]

/**
 * Модальное окно для filter.
 *
 * @see AGENTS.md
 */
export default {
  name: 'AlbumsFilterModal',
  data() {
    return {
      albumTypeOptions: ALBUM_TYPE_LABEL_OPTIONS,
    }
  },
  computed: {
    albumsFilterId: {
      get() {
        return this.$store.getters.getAlbumsFilterId
      },
      set(value) {
        this.$store.dispatch('setAlbumsFilterId', { value: value })
      },
    },
    albumsFilterAuthorName: {
      get() {
        return this.$store.getters.getAlbumsFilterAuthorName
      },
      set(value) {
        this.$store.dispatch('setAlbumsFilterAuthorName', { value: value })
      },
    },
    albumsFilterYear: {
      get() {
        return this.$store.getters.getAlbumsFilterYear
      },
      set(value) {
        this.$store.dispatch('setAlbumsFilterYear', { value: value })
      },
    },
    albumsFilterName: {
      get() {
        return this.$store.getters.getAlbumsFilterName
      },
      set(value) {
        this.$store.dispatch('setAlbumsFilterName', { value: value })
      },
    },
    albumsFilterAlbumType: {
      get() {
        return this.$store.getters.getAlbumsFilterAlbumType
      },
      set(value) {
        this.$store.dispatch('setAlbumsFilterAlbumType', { value: value })
      },
    },
  },
  async beforeMount() {
    this.$store.dispatch('setAlbumsFilterId', {
      value: await this.$store.getters.getWebvueProp('albumsFilterId', ''),
    })
    this.$store.dispatch('setAlbumsFilterAuthorName', {
      value: await this.$store.getters.getWebvueProp('albumsFilterAuthorName', ''),
    })
    this.$store.dispatch('setAlbumsFilterYear', {
      value: await this.$store.getters.getWebvueProp('albumsFilterYear', ''),
    })
    this.$store.dispatch('setAlbumsFilterName', {
      value: await this.$store.getters.getWebvueProp('albumsFilterName', ''),
    })
    this.$store.dispatch('setAlbumsFilterAlbumType', {
      value: await this.$store.getters.getWebvueProp('albumsFilterAlbumType', ''),
    })
    if (
      !this.$store.getters.getAuthorsDigest ||
      this.$store.getters.getAuthorsDigest.length === 0
    ) {
      this.$store.dispatch('loadAuthorsDigests', {})
    }
  },
  methods: {
    ok() {
      let params = {}
      if (this.albumsFilterId) params.filterId = this.albumsFilterId
      if (this.albumsFilterAuthorName) params.filterAuthorName = this.albumsFilterAuthorName
      if (this.albumsFilterYear) params.filterYear = this.albumsFilterYear
      if (this.albumsFilterName) params.filterName = this.albumsFilterName
      if (this.albumsFilterAlbumType) params.filterAlbumType = this.albumsFilterAlbumType
      this.$store.dispatch('loadAlbumsDigests', params)

      this.$emit('close')
    },
    cancel() {
      this.$emit('close')
    },
  },
}
</script>

<style scoped>
.afm-modal-fade-enter,
.afm-modal-fade-leave-active {
  opacity: 0;
}

.afm-modal-fade-enter-active,
.afm-modal-fade-leave-active {
  transition: opacity 0.5s ease;
}

.afm-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.afm-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.afm-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}

.afm-modal-backdrop {
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

.afm-area {
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

.afm-btn-close {
  border: 1px solid white;
  border-radius: 10px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: transparent;
  width: 150px;
  height: auto;
  font-size: small;
}

.afm-root-wrapper {
  display: flex;
  flex-direction: column;
}
.afm-button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}
.afm-filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.afm-row-label {
  min-width: 140px;
  max-width: 140px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.afm-row-input {
  display: block;
  padding-bottom: 3px;
  width: 200px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}

.afm-input-field {
  box-sizing: border-box;
  border: 1px solid #767676;
  border-radius: 5px;
  padding: 1px 4px;
  font: inherit;
  background-color: white;
  width: calc(100% - 18px);
}

.afm-input-field:hover {
  background-color: lightyellow;
}
.afm-input-field:focus {
  background-color: cyan;
}

select.afm-input-field {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  cursor: pointer;
}
</style>
