<template>
  <transition name="modal-fade">
    <div class="afm-modal-backdrop">
      <div class="afm-area">

        <div class="afm-area-modal-header">
          Фильтр для настроек
        </div>

        <div class="afm-area-modal-body">
          <div class="afm-root-wrapper">

            <div class="afm-filter-row">
              <div class="afm-row-label">
                <div v-text="'ID:'"></div>
              </div>
              <div class="afm-row-input">
                <input class="afm-input-field" v-model="authorsFilterId">
              </div>
              <button :disabled="!authorsFilterId" class="afm-button-clear-field" @click.left="authorsFilterId=''" v-text="'X'"></button>
            </div>

            <div class="afm-filter-row">
              <div class="afm-row-label">
                <div v-text="'Автор:'"></div>
              </div>
              <div class="afm-row-input">
                <input class="afm-input-field" v-model="authorsFilterAuthor">
              </div>
              <button :disabled="!authorsFilterAuthor" class="afm-button-clear-field" @click.left="authorsFilterAuthor=''" v-text="'X'"></button>
            </div>

            <div class="afm-filter-row">
              <div class="afm-row-label">
                <div v-text="'Yandex.ID:'"></div>
              </div>
              <div class="afm-row-input">
                <input class="afm-input-field" v-model="authorsFilterYmId">
              </div>
              <button :disabled="!authorsFilterYmId" class="afm-button-clear-field" @click.left="authorsFilterYmId=''" v-text="'X'"></button>
            </div>

            <div class="afm-filter-row">
              <div class="afm-row-label">
                <div v-text="'Последний альбом (Yandex):'"></div>
              </div>
              <div class="afm-row-input">
                <input class="afm-input-field" v-model="authorsFilterLastAlbumYm">
              </div>
              <button :disabled="!authorsFilterLastAlbumYm" class="afm-button-clear-field" @click.left="authorsFilterLastAlbumYm=''" v-text="'X'"></button>
            </div>

            <div class="afm-filter-row">
              <div class="afm-row-label">
                <div v-text="'Последний альбом (DB):'"></div>
              </div>
              <div class="afm-row-input">
                <input class="afm-input-field" v-model="authorsFilterLastAlbumProcessed">
              </div>
              <button :disabled="!authorsFilterLastAlbumProcessed" class="afm-button-clear-field" @click.left="authorsFilterLastAlbumProcessed=''" v-text="'X'"></button>
            </div>
            
            <div class="afm-filter-row">
              <div class="afm-row-label">
                <div v-text="'Следить?:'"></div>
              </div>
              <div class="afm-row-input">
                <input class="afm-input-field" v-model="authorsFilterWatched">
              </div>
              <button :disabled="!authorsFilterWatched" class="afm-button-clear-field" @click.left="authorsFilterWatched=''" v-text="'X'"></button>
            </div>

            <div class="afm-filter-row">
              <div class="afm-row-label">
                <div v-text="'Пропустить?:'"></div>
              </div>
              <div class="afm-row-input">
                <input class="afm-input-field" v-model="authorsFilterSkip">
              </div>
              <button :disabled="!authorsFilterSkip" class="afm-button-clear-field" @click.left="authorsFilterSkip=''" v-text="'X'"></button>
            </div>

            <div class="afm-filter-row">
              <div class="afm-row-label">
                <div v-text="'Новый альбом?:'"></div>
              </div>
              <div class="afm-row-input">
                <input class="afm-input-field" v-model="authorsFilterHaveNewAlbum">
              </div>
              <button :disabled="!authorsFilterHaveNewAlbum" class="afm-button-clear-field" @click.left="authorsFilterHaveNewAlbum=''" v-text="'X'"></button>
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

export default {
  name: "AuthorsFilterModal",
  async beforeMount() {
    this.$store.dispatch('setAuthorsFilterId', { value: await this.$store.getters.getWebvueProp('authorsFilterId', '') });
    this.$store.dispatch('setAuthorsFilterAuthor', { value: await this.$store.getters.getWebvueProp('authorsFilterAuthor', '') });
    this.$store.dispatch('setAuthorsFilterYmId', { value: await this.$store.getters.getWebvueProp('authorsFilterYmId', '') });
    this.$store.dispatch('setAuthorsFilterLastAlbumYm', { value: await this.$store.getters.getWebvueProp('authorsFilterLastAlbumYm', '') });
    this.$store.dispatch('setAuthorsFilterLastAlbumProcessed', { value: await this.$store.getters.getWebvueProp('authorsFilterLastAlbumProcessed', '') });
    this.$store.dispatch('setAuthorsFilterWatched', { value: await this.$store.getters.getWebvueProp('authorsFilterWatched', '') });
    this.$store.dispatch('setAuthorsFilterSkip', { value: await this.$store.getters.getWebvueProp('authorsFilterSkip', '') });
    this.$store.dispatch('setAuthorsFilterHaveNewAlbum', { value: await this.$store.getters.getWebvueProp('authorsFilterHaveNewAlbum', '') });
  },
  computed: {
    authorsFilterId: {
      get() { return this.$store.getters.getAuthorsFilterId; },
      set(value) { this.$store.dispatch('setAuthorsFilterId', { value: value }); }
    },
    authorsFilterAuthor: {
      get() { return this.$store.getters.getAuthorsFilterAuthor; },
      set(value) { this.$store.dispatch('setAuthorsFilterAuthor', { value: value }); }
    },
    authorsFilterYmId: {
      get() { return this.$store.getters.getAuthorsFilterYmId; },
      set(value) { this.$store.dispatch('setAuthorsFilterYmId', { value: value }); }
    },
    authorsFilterLastAlbumYm: {
      get() { return this.$store.getters.getAuthorsFilterLastAlbumYm; },
      set(value) { this.$store.dispatch('setAuthorsFilterLastAlbumYm', { value: value }); }
    },
    authorsFilterLastAlbumProcessed: {
      get() { return this.$store.getters.getAuthorsFilterLastAlbumProcessed; },
      set(value) { this.$store.dispatch('setAuthorsFilterLastAlbumProcessed', { value: value }); }
    },
    authorsFilterWatched: {
      get() { return this.$store.getters.getAuthorsFilterWatched; },
      set(value) { this.$store.dispatch('setAuthorsFilterWatched', { value: value }); }
    },
    authorsFilterSkip: {
      get() { return this.$store.getters.getAuthorsFilterSkip; },
      set(value) { this.$store.dispatch('setAuthorsFilterSkip', { value: value }); }
    },
    authorsFilterHaveNewAlbum: {
      get() { return this.$store.getters.getAuthorsFilterHaveNewAlbum; },
      set(value) { this.$store.dispatch('setAuthorsFilterHaveNewAlbum', { value: value }); }
    },
  },
  methods: {
    ok() {
      this.$store.dispatch('setAuthorsFilterId', { value: this.authorsFilterId });
      this.$store.dispatch('setAuthorsFilterAuthor', { value: this.authorsFilterAuthor });
      this.$store.dispatch('setAuthorsFilterYmId', { value: this.authorsFilterYmId });
      this.$store.dispatch('setAuthorsFilterLastAlbumYm', { value: this.authorsFilterLastAlbumYm });
      this.$store.dispatch('setAuthorsFilterLastAlbumProcessed', { value: this.authorsFilterLastAlbumProcessed });
      this.$store.dispatch('setAuthorsFilterWatched', { value: this.authorsFilterWatched });
      this.$store.dispatch('setAuthorsFilterSkip', { value: this.authorsFilterSkip });
      this.$store.dispatch('setAuthorsFilterHaveNewAlbum', { value: this.authorsFilterHaveNewAlbum });

      let params = {};
      if (this.authorsFilterId) params.filter_id = this.authorsFilterId;
      if (this.authorsFilterAuthor) params.filter_author = this.authorsFilterAuthor;
      if (this.authorsFilterYmId) params.filter_ym_id = this.authorsFilterYmId;
      if (this.authorsFilterLastAlbumYm) params.filter_last_album_ym = this.authorsFilterLastAlbumYm;
      if (this.authorsFilterLastAlbumProcessed) params.filter_last_album_processed = this.authorsFilterLastAlbumProcessed;
      if (this.authorsFilterWatched) params.filter_watched = this.authorsFilterWatched;
      if (this.authorsFilterSkip) params.filter_skip = this.authorsFilterSkip;
      if (this.authorsFilterHaveNewAlbum) params.filter_HaveNewAlbum = this.authorsFilterHaveNewAlbum;
      this.$store.dispatch('loadAuthorsDigests', params )

      this.$emit('close');
    },
    cancel() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.afm-modal-fade-enter,
.afm-modal-fade-leave-active {
  opacity: 0;
}

.afm-modal-fade-enter-active,
.afm-modal-fade-leave-active {
  transition: opacity .5s ease
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
  background: #FFFFFF;
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
  border-radius: 5px;
  width: fit-content;
}

.afm-input-field:hover {
  background-color: lightyellow;
}
.afm-input-field:focus {
  background-color: cyan;
}

</style>