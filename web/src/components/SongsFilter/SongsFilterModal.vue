<template>
  <transition name="modal-fade">
    <div class="modal-backdrop">
      <div class="area">

        <div class="area-modal-header">
          Фильтр для песен
        </div>

        <div class="area-modal-body">
          <div class="root-wrapper">

            <div class="filter-row">
              <datalist id="dictAuthorsId">
                <option v-for="val in dictAuthors" :key="val" :value="val"/>
              </datalist>
              <div class="row-label">
                <div v-text="'Исполнитель:'"></div>
              </div>
              <div class="row-input">
                <input class="input-field" v-model="songsFilterSongAuthor" list="dictAuthorsId">
              </div>
              <button :disabled="!songsFilterSongAuthor" class="button-clear-field" @click.left="songsFilterSongAuthor=''" v-text="'X'"></button>
            </div>

            <div class="filter-row">
              <datalist id="dictDatesId">
                <option v-for="val in dictDates" :key="val" :value="val"/>
              </datalist>
              <div class="row-label">
                <div v-text="'Дата:'"></div>
              </div>
              <div class="row-input">
                <input class="input-field" v-model="songsFilterPublishDate" list="dictDatesId">
              </div>
              <button :disabled="!songsFilterPublishDate" class="button-clear-field" @click.left="songsFilterPublishDate=''" v-text="'X'"></button>
            </div>

            <div class="filter-row">
              <div class="row-label">
                <div v-text="'Время:'"></div>
              </div>
              <div class="row-input">
                <input class="input-field" v-model="songsFilterPublishTime">
              </div>
              <button :disabled="!songsFilterPublishTime" class="button-clear-field" @click.left="songsFilterPublishTime=''" v-text="'X'"></button>
            </div>

            <div class="filter-row">
              <div class="row-label">
                <div v-text="'ID:'"></div>
              </div>
              <div class="row-input">
                <input class="input-field" v-model="songsFilterId">
              </div>
              <button :disabled="!songsFilterId" class="button-clear-field" @click.left="songsFilterId=''" v-text="'X'"></button>
            </div>

            <div class="filter-row">
              <div class="row-label">
                <div v-text="'Композиция:'"></div>
              </div>
              <div class="row-input">
                <input class="input-field" v-model="songsFilterSongName">
              </div>
              <button :disabled="!songsFilterSongName" class="button-clear-field" @click.left="songsFilterSongName=''" v-text="'X'"></button>
            </div>

            <div class="filter-row">
              <div class="row-label">
                <div v-text="'Альбом:'"></div>
              </div>
              <div class="row-input">
                <input class="input-field" v-model="songsFilterSongAlbum">
              </div>
              <button :disabled="!songsFilterSongAlbum" class="button-clear-field" @click.left="songsFilterSongAlbum=''" v-text="'X'"></button>
            </div>


            <div class="filter-row">
              <div class="row-label">
                <div v-text="'ID статуса:'"></div>
              </div>
              <div class="row-input">
                <input class="input-field" v-model="songsFilterIdStatus">
              </div>
              <button :disabled="!songsFilterIdStatus" class="button-clear-field" @click.left="songsFilterIdStatus=''" v-text="'X'"></button>
            </div>

            <div class="filter-row">
              <div class="row-label">
                <div v-text="'Tags:'"></div>
              </div>
              <div class="row-input">
                <input class="input-field" v-model="songsFilterTags">
              </div>
              <button :disabled="!songsFilterTags" class="button-clear-field" @click.left="songsFilterTags=''" v-text="'X'"></button>
            </div>

            <div class="filter-row">
              <div class="row-label">
                <div v-text="'Version:'"></div>
              </div>
              <div class="row-input">
                <input class="input-field" v-model="songsFilterResultVersion">
              </div>
              <button :disabled="!songsFilterResultVersion" class="button-clear-field" @click.left="songsFilterResultVersion=''" v-text="'X'"></button>
            </div>

          </div>
        </div>

        <div class="area-modal-footer">
          <button type="button" class="btn-close" @click="ok">Применить фильтр</button>
          <button type="button" class="btn-close" @click="cancel">Отмена</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>

export default {
  name: "SongsFilterModal",
  data() {
    return {
      songsFilterId: this.$store.getters.getSongsFilterId,
      songsFilterSongName: this.$store.getters.getSongsFilterSongName,
      songsFilterSongAuthor: this.$store.getters.getSongsFilterSongAuthor,
      songsFilterSongAlbum: this.$store.getters.getSongsFilterSongAlbum,
      songsFilterPublishDate: this.$store.getters.getSongsFilterPublishDate,
      songsFilterPublishTime: this.$store.getters.getSongsFilterPublishTime,
      songsFilterIdStatus: this.$store.getters.getSongsFilterIdStatus,
      songsFilterTags: this.$store.getters.getSongsFilterTags,
      songsFilterResultVersion: this.$store.getters.getSongsFilterResultVersion,
      dictAuthors: []
    }
  },
  mounted() {
    this.$store.getters.songAuthorsPromise.then(data => {
      this.dictAuthors = JSON.parse(data).authors;
    });
  },
  computed: {
    dictDates() {
      function addZero(num) {
        if (num >= 0 && num <= 9) {
          return '0' + num;
        } else {
          return num;
        }
      }
      const today = new Date();
      let result = [];
      for (let i = -2; i < 6; i++) {
        const date = new Date(today.getFullYear(), today.getMonth(), today.getDate()+i);
        result.push(`${addZero(date.getDate())}.${addZero(date.getMonth() + 1)}.${date.getFullYear().toString().substring(2)}`);
      }
      return result;
    }
  },
  methods: {
    ok() {
      this.$store.dispatch('setSongsFilterId', { songsFilterId: this.songsFilterId });
      this.$store.dispatch('setSongsFilterSongName', { songsFilterSongName: this.songsFilterSongName });
      this.$store.dispatch('setSongsFilterSongAuthor', { songsFilterSongAuthor: this.songsFilterSongAuthor });
      this.$store.dispatch('setSongsFilterSongAlbum', { songsFilterSongAlbum: this.songsFilterSongAlbum });
      this.$store.dispatch('setSongsFilterPublishDate', { songsFilterPublishDate: this.songsFilterPublishDate });
      this.$store.dispatch('setSongsFilterPublishTime', { songsFilterPublishTime: this.songsFilterPublishTime });
      this.$store.dispatch('setSongsFilterIdStatus', { songsFilterIdStatus: this.songsFilterIdStatus });
      this.$store.dispatch('setSongsFilterTags', { songsFilterTags: this.songsFilterTags });
      this.$store.dispatch('setSongsFilterResultVersion', { songsFilterResultVersion: this.songsFilterResultVersion });

      let params = {};
      if (this.songsFilterId) params.filter_id = this.songsFilterId;
      if (this.songsFilterSongName) params.filter_songName = this.songsFilterSongName;
      if (this.songsFilterSongAuthor) params.filter_author = this.songsFilterSongAuthor;
      if (this.songsFilterSongAlbum) params.filter_album = this.songsFilterSongAlbum;
      if (this.songsFilterPublishDate) params.filter_date = this.songsFilterPublishDate;
      if (this.songsFilterPublishTime) params.filter_time = this.songsFilterPublishTime;
      if (this.songsFilterIdStatus) params.filter_status = this.songsFilterIdStatus;
      if (this.songsFilterTags) params.filter_tags = this.songsFilterTags;
      if (this.songsFilterResultVersion !== '') params.filter_result_version = this.songsFilterResultVersion;
      this.$store.dispatch('loadSongsDigests', params )

      this.$emit('close');
    },
    cancel() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.modal-fade-enter,
.modal-fade-leave-active {
  opacity: 0;
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity .5s ease
}

.area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}

.modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
}

.area {
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

.btn-close {
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

.root-wrapper {
  display: flex;
  flex-direction: column;
}
.button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}
.filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.row-label {
  min-width: 140px;
  max-width: 140px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.row-input {
  display: block;
  padding-bottom: 3px;
  width: 200px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}

.input-field {
  border-radius: 5px;
  width: fit-content;
}

.input-field:hover {
  background-color: lightyellow;
}
.input-field:focus {
  background-color: cyan;
}

</style>