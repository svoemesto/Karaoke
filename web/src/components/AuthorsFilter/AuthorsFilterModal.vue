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
                <div v-text="'Следить:'"></div>
              </div>
              <div class="afm-row-input">
                <input class="afm-input-field" v-model="authorsFilterWatched">
              </div>
              <button :disabled="!authorsFilterWatched" class="afm-button-clear-field" @click.left="authorsFilterWatched=''" v-text="'X'"></button>
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
  data() {
    return {
      authorsFilterId: this.$store.getters.getAuthorsFilterId,
      authorsFilterAuthor: this.$store.getters.getAuthorsFilterAuthor,
      authorsFilterYmId: this.$store.getters.getAuthorsFilterYmId,
      authorsFilterLastAlbumYm: this.$store.getters.getAuthorsFilterLastAlbumYm,
      authorsFilterLastAlbumProcessed: this.$store.getters.getAuthorsFilterLastAlbumProcessed,
      authorsFilterWatched: this.$store.getters.getAuthorsFilterWatched
    }
  },
  methods: {
    ok() {
      this.$store.dispatch('setAuthorsFilterId', { authorsFilterId: this.authorsFilterId });
      this.$store.dispatch('setAuthorsFilterAuthor', { authorsFilterAuthor: this.authorsFilterAuthor });
      this.$store.dispatch('setAuthorsFilterYmId', { authorsFilterYmId: this.authorsFilterYmId });
      this.$store.dispatch('setAuthorsFilterLastAlbumYm', { authorsFilterLastAlbumYm: this.authorsFilterLastAlbumYm });
      this.$store.dispatch('setAuthorsFilterLastAlbumProcessed', { authorsFilterLastAlbumProcessed: this.authorsFilterLastAlbumProcessed });
      this.$store.dispatch('setAuthorsFilterWatched', { authorsFilterWatched: this.authorsFilterWatched });

      let params = {};
      if (this.authorsFilterId) params.filter_id = this.authorsFilterId;
      if (this.authorsFilterAuthor) params.filter_author = this.authorsFilterAuthor;
      if (this.authorsFilterYmId) params.filter_ym_id = this.authorsFilterYmId;
      if (this.authorsFilterLastAlbumYm) params.filter_last_album_ym = this.authorsFilterLastAlbumYm;
      if (this.authorsFilterLastAlbumProcessed) params.filter_last_album_processed = this.authorsFilterLastAlbumProcessed;
      if (this.authorsFilterWatched) params.filter_watched = this.authorsFilterWatched;
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