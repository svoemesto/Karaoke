<template>
  <transition name="modal-fade">
    <div class="modal-backdrop">
      <div class="area">

        <div class="area-modal-header">
          Фильтр для песен
        </div>

        <div class="area-modal-body">
          <b-tabs content-class="mt-3">
            <b-tab title="Поля для поиска" active>
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
                    <div v-text="'Кол-во голосов:'"></div>
                  </div>
                  <div class="row-input">
                    <input class="input-field" v-model="songsFilterCountVoices">
                  </div>
                  <button :disabled="!songsFilterCountVoices" class="button-clear-field" @click.left="songsFilterCountVoices=''" v-text="'X'"></button>
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

                <div class="filter-row">
                  <div class="row-label">
                    <div v-text="'Ver.Boosty:'"></div>
                  </div>
                  <div class="row-input">
                    <input class="input-field" v-model="songsFilterVersionBoosty">
                  </div>
                  <button :disabled="!songsFilterVersionBoosty" class="button-clear-field" @click.left="songsFilterVersionBoosty=''" v-text="'X'"></button>
                </div>

                <div class="filter-row">
                  <div class="row-label">
                    <div v-text="'Ver.BoostyFiles:'"></div>
                  </div>
                  <div class="row-input">
                    <input class="input-field" v-model="songsFilterVersionBoostyFiles">
                  </div>
                  <button :disabled="!songsFilterVersionBoostyFiles" class="button-clear-field" @click.left="songsFilterVersionBoostyFiles=''" v-text="'X'"></button>
                </div>

                <div class="filter-row">
                  <div class="row-label">
                    <div v-text="'Ver.Sponsr:'"></div>
                  </div>
                  <div class="row-input">
                    <input class="input-field" v-model="songsFilterVersionSponsr">
                  </div>
                  <button :disabled="!songsFilterVersionSponsr" class="button-clear-field" @click.left="songsFilterVersionSponsr=''" v-text="'X'"></button>
                </div>

                <div class="filter-row">
                  <div class="row-label">
                    <div v-text="'Ver.Dzen.Kar:'"></div>
                  </div>
                  <div class="row-input">
                    <input class="input-field" v-model="songsFilterVersionDzenKaraoke">
                  </div>
                  <button :disabled="!songsFilterVersionDzenKaraoke" class="button-clear-field" @click.left="songsFilterVersionDzenKaraoke=''" v-text="'X'"></button>
                </div>

                <div class="filter-row">
                  <div class="row-label">
                    <div v-text="'Ver.Vk.Kar:'"></div>
                  </div>
                  <div class="row-input">
                    <input class="input-field" v-model="songsFilterVersionVkKaraoke">
                  </div>
                  <button :disabled="!songsFilterVersionVkKaraoke" class="button-clear-field" @click.left="songsFilterVersionVkKaraoke=''" v-text="'X'"></button>
                </div>

                <div class="filter-row">
                  <div class="row-label">
                    <div v-text="'Ver.Tg.Kar:'"></div>
                  </div>
                  <div class="row-input">
                    <input class="input-field" v-model="songsFilterVersionTelegramKaraoke">
                  </div>
                  <button :disabled="!songsFilterVersionTelegramKaraoke" class="button-clear-field" @click.left="songsFilterVersionTelegramKaraoke=''" v-text="'X'"></button>
                </div>

                <div class="filter-row">
                  <div class="row-label">
                    <div v-text="'Ver.Pl.Kar:'"></div>
                  </div>
                  <div class="row-input">
                    <input class="input-field" v-model="songsFilterVersionPlKaraoke">
                  </div>
                  <button :disabled="!songsFilterVersionPlKaraoke" class="button-clear-field" @click.left="songsFilterVersionPlKaraoke=''" v-text="'X'"></button>
                </div>

                <div class="filter-row">
                  <div class="row-label">
                    <div v-text="'Rate:'"></div>
                  </div>
                  <div class="row-input">
                    <input class="input-field" v-model="songsFilterRate">
                  </div>
                  <button :disabled="!songsFilterRate" class="button-clear-field" @click.left="songsFilterRate=''" v-text="'X'"></button>
                </div>

              </div>
            </b-tab>
            <b-tab title="История">
              <div class="history-table-body">
                <b-table
                    :items="songsHistory"
                    :fields="songHistoryFields"
                    small
                    fixed
                    bordered
                    hover
                    @row-clicked="onRowClicked"
                >
                  <template #cell(historyName)="data">
                    <div
                        class="fld-history-name"
                        v-text="data.value"
                    ></div>
                  </template>
                </b-table>
              </div>
            </b-tab>
          </b-tabs>

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

import Vue from "vue";
import {TabsPlugin} from 'bootstrap-vue'
import { TablePlugin } from 'bootstrap-vue'
Vue.use(TabsPlugin)
Vue.use(TablePlugin)

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
      songsFilterCountVoices: this.$store.getters.getSongsFilterCountVoices,
      songsFilterTags: this.$store.getters.getSongsFilterTags,
      songsFilterResultVersion: this.$store.getters.getSongsFilterResultVersion,
      songsFilterVersionBoosty: this.$store.getters.getSongsFilterVersionBoosty,
      songsFilterVersionBoostyFiles: this.$store.getters.getSongsFilterVersionBoostyFiles,
      songsFilterVersionSponsr: this.$store.getters.getSongsFilterVersionSponsr,
      songsFilterVersionDzenKaraoke: this.$store.getters.getSongsFilterVersionDzenKaraoke,
      songsFilterVersionVkKaraoke: this.$store.getters.getSongsFilterVersionVkKaraoke,
      songsFilterVersionTelegramKaraoke: this.$store.getters.getSongsFilterVersionTelegramKaraoke,
      songsFilterVersionPlKaraoke: this.$store.getters.getSongsFilterVersionPlKaraoke,
      songsFilterRate: this.$store.getters.getSongsFilterRate,
      dictAuthors: []
    }
  },
  mounted() {
    this.$store.getters.songAuthorsPromise.then(data => {
      this.dictAuthors = JSON.parse(data).authors;
      this.$store.dispatch('loadSongsHistory' );
    });
  },
  computed: {
    songsHistory() {
      return this.$store.getters.getSongsHistory;
    },
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
    },
    songHistoryFields() {
      return [
        {
          key: 'historyName',
          label: 'Название'
        },
        // {
        //   key: 'historyArgs',
        //   label: 'Аргументы'
        // }
      ]
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
      this.$store.dispatch('setSongsFilterCountVoices', { songsFilterCountVoices: this.songsFilterCountVoices });
      this.$store.dispatch('setSongsFilterTags', { songsFilterTags: this.songsFilterTags });
      this.$store.dispatch('setSongsFilterResultVersion', { songsFilterResultVersion: this.songsFilterResultVersion });
      this.$store.dispatch('setSongsFilterVersionBoosty', { songsFilterVersionBoosty: this.songsFilterVersionBoosty });
      this.$store.dispatch('setSongsFilterVersionBoostyFiles', { songsFilterVersionBoostyFiles: this.songsFilterVersionBoostyFiles });
      this.$store.dispatch('setSongsFilterVersionSponsr', { songsFilterVersionSponsr: this.songsFilterVersionSponsr });
      this.$store.dispatch('setSongsFilterVersionDzenKaraoke', { songsFilterVersionDzenKaraoke: this.songsFilterVersionDzenKaraoke });
      this.$store.dispatch('setSongsFilterVersionVkKaraoke', { songsFilterVersionVkKaraoke: this.songsFilterVersionVkKaraoke });
      this.$store.dispatch('setSongsFilterVersionTelegramKaraoke', { songsFilterVersionTelegramKaraoke: this.songsFilterVersionTelegramKaraoke });
      this.$store.dispatch('setSongsFilterVersionPlKaraoke', { songsFilterVersionPlKaraoke: this.songsFilterVersionPlKaraoke });
      this.$store.dispatch('setSongsFilterRate', { songsFilterRate: this.songsFilterRate });

      let params = {};
      if (this.songsFilterId) params.filter_id = this.songsFilterId;
      if (this.songsFilterSongName) params.filter_songName = this.songsFilterSongName;
      if (this.songsFilterSongAuthor) params.filter_author = this.songsFilterSongAuthor;
      if (this.songsFilterSongAlbum) params.filter_album = this.songsFilterSongAlbum;
      if (this.songsFilterPublishDate) params.filter_date = this.songsFilterPublishDate;
      if (this.songsFilterPublishTime) params.filter_time = this.songsFilterPublishTime;
      if (this.songsFilterIdStatus) params.filter_status = this.songsFilterIdStatus;
      if (this.songsFilterCountVoices) params.filter_count_voices = this.songsFilterCountVoices;
      if (this.songsFilterTags) params.filter_tags = this.songsFilterTags;
      if (this.songsFilterResultVersion !== '') params.filter_result_version = this.songsFilterResultVersion;
      if (this.songsFilterVersionBoosty !== '') params.filter_version_boosty = this.songsFilterVersionBoosty;
      if (this.songsFilterVersionBoostyFiles !== '') params.filter_version_boosty_files = this.songsFilterVersionBoostyFiles;
      if (this.songsFilterVersionSponsr !== '') params.filter_version_sponsr = this.songsFilterVersionSponsr;
      if (this.songsFilterVersionDzenKaraoke !== '') params.filter_version_dzen_karaoke = this.songsFilterVersionDzenKaraoke;
      if (this.songsFilterVersionVkKaraoke !== '') params.filter_version_vk_karaoke = this.songsFilterVersionVkKaraoke;
      if (this.songsFilterVersionTelegramKaraoke !== '') params.filter_version_telegram_karaoke = this.songsFilterVersionTelegramKaraoke;
      if (this.songsFilterVersionPlKaraoke !== '') params.filter_version_pl_karaoke = this.songsFilterVersionPlKaraoke;
      if (this.songsFilterRate !== '') params.filter_rate = this.songsFilterRate;
      this.$store.dispatch('loadSongsDigests', params );
      this.$store.dispatch('loadSongsHistory' );

      this.$emit('close');
    },
    cancel() {
      this.$emit('close');
    },
    onRowClicked(item) {
      let args = item.historyArgs;
      this.songsFilterId = args['id'] ? args['id'] : ''
      this.songsFilterSongName = args['song_name'] ? args['song_name'] : ''
      this.songsFilterSongAuthor = args['song_author'] ? args['song_author'] : ''
      this.songsFilterSongAlbum = args['song_album'] ? args['song_album'] : ''
      this.songsFilterPublishDate = args['publish_date'] ? args['publish_date'] : ''
      this.songsFilterPublishTime = args['publish_time'] ? args['publish_time'] : ''
      this.songsFilterIdStatus = args['id_status'] ? args['id_status'] : ''
      this.songsFilterCountVoices = args['filter_count_voices'] ? args['filter_count_voices'] : ''
      this.songsFilterTags = args['tags'] ? args['tags'] : ''
      this.songsFilterResultVersion = args['filter_result_version'] ? args['filter_result_version'] : ''
      this.songsFilterVersionBoosty = args['filter_version_boosty'] ? args['filter_version_boosty'] : ''
      this.songsFilterVersionBoostyFiles = args['filter_version_boosty_files'] ? args['filter_version_boosty_files'] : ''
      this.songsFilterVersionSponsr = args['filter_version_sponsr'] ? args['filter_version_sponsr'] : ''
      this.songsFilterVersionDzenKaraoke = args['filter_version_dzen_karaoke'] ? args['filter_version_dzen_karaoke'] : ''
      this.songsFilterVersionVkKaraoke = args['filter_version_vk_karaoke'] ? args['filter_version_vk_karaoke'] : ''
      this.songsFilterVersionTelegramKaraoke = args['filter_version_telegram_karaoke'] ? args['filter_version_telegram_karaoke'] : ''
      this.songsFilterVersionPlKaraoke = args['filter_version_pl_karaoke'] ? args['filter_version_pl_karaoke'] : ''
      this.songsFilterRate = args['filter_rate'] ? args['filter_rate'] : ''

      this.ok();
    }
  }
}
</script>

<style scoped>

.history-table-body {
  width: fit-content;
}

.fld-history-name {
  min-width: 300px;
  max-width: 300px;
  text-align: left;
  font-size: small;
  cursor: default;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
}
.fld-history-name:hover {
  text-decoration: underline;
  cursor: pointer;
}

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