<template>
  <transition name="modal-fade">
    <div class="sfm-modal-backdrop">
      <div class="sfm-area">

        <div class="sfm-area-modal-header">
          Фильтр для песен
        </div>

        <div class="sfm-area-modal-body">
          <b-tabs content-class="sfm-mt-3">
            <b-tab title="Поля для поиска" active>
              <div class="sfm-root-wrapper">

                <div class="sfm-filter-row">
                  <datalist id="dictAuthorsId">
                    <option v-for="val in dictAuthors" :key="val" :value="val"/>
                  </datalist>
                  <div class="sfm-row-label">
                    <div v-text="'Исполнитель:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterSongAuthor" list="dictAuthorsId">
                  </div>
                  <button :disabled="!songsFilterSongAuthor" class="sfm-button-clear-field" @click.left="songsFilterSongAuthor=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <datalist id="dictDatesId">
                    <option v-for="val in dictDates" :key="val" :value="val"/>
                  </datalist>
                  <div class="sfm-row-label">
                    <div v-text="'Дата:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterPublishDate" list="dictDatesId">
                  </div>
                  <button :disabled="!songsFilterPublishDate" class="sfm-button-clear-field" @click.left="songsFilterPublishDate=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Время:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterPublishTime">
                  </div>
                  <button :disabled="!songsFilterPublishTime" class="sfm-button-clear-field" @click.left="songsFilterPublishTime=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'ID:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterId">
                  </div>
                  <button :disabled="!songsFilterId" class="sfm-button-clear-field" @click.left="songsFilterId=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Композиция:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterSongName">
                  </div>
                  <button :disabled="!songsFilterSongName" class="sfm-button-clear-field" @click.left="songsFilterSongName=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Альбом:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterSongAlbum">
                  </div>
                  <button :disabled="!songsFilterSongAlbum" class="sfm-button-clear-field" @click.left="songsFilterSongAlbum=''" v-text="'X'"></button>
                </div>


                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'ID статуса:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterIdStatus">
                  </div>
                  <button :disabled="!songsFilterIdStatus" class="sfm-button-clear-field" @click.left="songsFilterIdStatus=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Кол-во голосов:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterCountVoices">
                  </div>
                  <button :disabled="!songsFilterCountVoices" class="sfm-button-clear-field" @click.left="songsFilterCountVoices=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Tags:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterTags">
                  </div>
                  <button :disabled="!songsFilterTags" class="sfm-button-clear-field" @click.left="songsFilterTags=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Version:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterResultVersion">
                  </div>
                  <button :disabled="!songsFilterResultVersion" class="sfm-button-clear-field" @click.left="songsFilterResultVersion=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Ver.Boosty:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterVersionBoosty">
                  </div>
                  <button :disabled="!songsFilterVersionBoosty" class="sfm-button-clear-field" @click.left="songsFilterVersionBoosty=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Ver.BoostyFiles:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterVersionBoostyFiles">
                  </div>
                  <button :disabled="!songsFilterVersionBoostyFiles" class="sfm-button-clear-field" @click.left="songsFilterVersionBoostyFiles=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Ver.Sponsr:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterVersionSponsr">
                  </div>
                  <button :disabled="!songsFilterVersionSponsr" class="sfm-button-clear-field" @click.left="songsFilterVersionSponsr=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Ver.Dzen.Kar:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterVersionDzenKaraoke">
                  </div>
                  <button :disabled="!songsFilterVersionDzenKaraoke" class="sfm-button-clear-field" @click.left="songsFilterVersionDzenKaraoke=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Ver.Vk.Kar:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterVersionVkKaraoke">
                  </div>
                  <button :disabled="!songsFilterVersionVkKaraoke" class="sfm-button-clear-field" @click.left="songsFilterVersionVkKaraoke=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Ver.Tg.Kar:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterVersionTelegramKaraoke">
                  </div>
                  <button :disabled="!songsFilterVersionTelegramKaraoke" class="sfm-button-clear-field" @click.left="songsFilterVersionTelegramKaraoke=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Ver.Pl.Kar:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterVersionPlKaraoke">
                  </div>
                  <button :disabled="!songsFilterVersionPlKaraoke" class="sfm-button-clear-field" @click.left="songsFilterVersionPlKaraoke=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Status.Proc.Lyr:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterStatusProcessLyrics">
                  </div>
                  <button :disabled="!songsFilterStatusProcessLyrics" class="sfm-button-clear-field" @click.left="songsFilterStatusProcessLyrics=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Status.Proc.Kar:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterStatusProcessKaraoke">
                  </div>
                  <button :disabled="!songsFilterStatusProcessKaraoke" class="sfm-button-clear-field" @click.left="songsFilterStatusProcessKaraoke=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Rate:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterRate">
                  </div>
                  <button :disabled="!songsFilterRate" class="sfm-button-clear-field" @click.left="songsFilterRate=''" v-text="'X'"></button>
                </div>

                <div class="sfm-filter-row">
                  <div class="sfm-row-label">
                    <div v-text="'Is Sync:'"></div>
                  </div>
                  <div class="sfm-row-input">
                    <input class="sfm-input-field" v-model="songsFilterIsSync">
                  </div>
                  <button :disabled="!songsFilterIsSync" class="sfm-button-clear-field" @click.left="songsFilterIsSync=''" v-text="'X'"></button>
                </div>

              </div>
            </b-tab>
            <b-tab title="История">
              <div class="sfm-history-table-body">
                <b-table
                    :items="songsHistory"
                    :fields="songHistoryFields"
                    small
                    bordered
                    hover
                    @row-clicked="onRowClicked"
                >
                  <template #cell(historyName)="data">
                    <div
                        class="sfm-fld-history-name"
                        v-text="data.value"
                    ></div>
                  </template>
                </b-table>
              </div>
            </b-tab>
          </b-tabs>

        </div>

        <div class="sfm-area-modal-footer">
          <button type="button" class="sfm-btn-close" @click="ok">Применить фильтр</button>
          <button type="button" class="sfm-btn-close" @click="cancel">Отмена</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>

import {BTab, BTable, BTabs} from 'bootstrap-vue-next'

export default {
  name: "SongsFilterModal",
  components: {
    BTab,
    BTabs,
    BTable
  },
  data() {
    return {
      // songsFilterId: this.$store.getters.getSongsFilterId,
      // songsFilterSongName: this.$store.getters.getSongsFilterSongName,
      // songsFilterSongAuthor: this.$store.getters.getSongsFilterSongAuthor,
      // songsFilterSongAlbum: this.$store.getters.getSongsFilterSongAlbum,
      // songsFilterPublishDate: this.$store.getters.getSongsFilterPublishDate,
      // songsFilterPublishTime: this.$store.getters.getSongsFilterPublishTime,
      // songsFilterIdStatus: this.$store.getters.getSongsFilterIdStatus,
      // songsFilterCountVoices: this.$store.getters.getSongsFilterCountVoices,
      // songsFilterTags: this.$store.getters.getSongsFilterTags,
      // songsFilterResultVersion: this.$store.getters.getSongsFilterResultVersion,
      // songsFilterVersionBoosty: this.$store.getters.getSongsFilterVersionBoosty,
      // songsFilterVersionBoostyFiles: this.$store.getters.getSongsFilterVersionBoostyFiles,
      // songsFilterVersionSponsr: this.$store.getters.getSongsFilterVersionSponsr,
      // songsFilterVersionDzenKaraoke: this.$store.getters.getSongsFilterVersionDzenKaraoke,
      // songsFilterVersionVkKaraoke: this.$store.getters.getSongsFilterVersionVkKaraoke,
      // songsFilterVersionTelegramKaraoke: this.$store.getters.getSongsFilterVersionTelegramKaraoke,
      // songsFilterVersionPlKaraoke: this.$store.getters.getSongsFilterVersionPlKaraoke,
      // songsFilterRate: this.$store.getters.getSongsFilterRate,
      // songsFilterIsSync: this.$store.getters.getSongsFilterIsSync,
      // songsFilterStatusProcessLyrics: this.$store.getters.getSongsFilterStatusProcessLyrics,
      // songsFilterStatusProcessKaraoke: this.$store.getters.getSongsFilterStatusProcessKaraoke,
      dictAuthors: []
    }
  },
  async beforeMount() {
    this.$store.dispatch('setSongsFilterId', { value: await this.$store.getters.getWebvueProp('songsFilterId', '') });
    this.$store.dispatch('setSongsFilterSongName', { value: await this.$store.getters.getWebvueProp('songsFilterSongName', '') });
    this.$store.dispatch('setSongsFilterSongAuthor', { value: await this.$store.getters.getWebvueProp('songsFilterSongAuthor', '') });
    this.$store.dispatch('setSongsFilterSongAlbum', { value: await this.$store.getters.getWebvueProp('songsFilterSongAlbum', '') });
    this.$store.dispatch('setSongsFilterPublishDate', { value: await this.$store.getters.getWebvueProp('songsFilterPublishDate', '') });
    this.$store.dispatch('setSongsFilterPublishTime', { value: await this.$store.getters.getWebvueProp('songsFilterPublishTime', '') });
    this.$store.dispatch('setSongsFilterIdStatus', { value: await this.$store.getters.getWebvueProp('songsFilterIdStatus', '') });
    this.$store.dispatch('setSongsFilterCountVoices', { value: await this.$store.getters.getWebvueProp('songsFilterCountVoices', '') });
    this.$store.dispatch('setSongsFilterTags', { value: await this.$store.getters.getWebvueProp('songsFilterTags', '') });
    this.$store.dispatch('setSongsFilterResultVersion', { value: await this.$store.getters.getWebvueProp('songsFilterResultVersion', '') });
    this.$store.dispatch('setSongsFilterVersionBoosty', { value: await this.$store.getters.getWebvueProp('songsFilterVersionBoosty', '') });
    this.$store.dispatch('setSongsFilterVersionBoostyFiles', { value: await this.$store.getters.getWebvueProp('songsFilterVersionBoostyFiles', '') });
    this.$store.dispatch('setSongsFilterVersionSponsr', { value: await this.$store.getters.getWebvueProp('songsFilterVersionSponsr', '') });
    this.$store.dispatch('setSongsFilterVersionDzenKaraoke', { value: await this.$store.getters.getWebvueProp('songsFilterVersionDzenKaraoke', '') });
    this.$store.dispatch('setSongsFilterVersionVkKaraoke', { value: await this.$store.getters.getWebvueProp('songsFilterVersionVkKaraoke', '') });
    this.$store.dispatch('setSongsFilterVersionTelegramKaraoke', { value: await this.$store.getters.getWebvueProp('songsFilterVersionTelegramKaraoke', '') });
    this.$store.dispatch('setSongsFilterVersionPlKaraoke', { value: await this.$store.getters.getWebvueProp('songsFilterVersionPlKaraoke', '') });
    this.$store.dispatch('setSongsFilterRate', { value: await this.$store.getters.getWebvueProp('songsFilterRate', '') });
    this.$store.dispatch('setSongsFilterIsSync', { value: await this.$store.getters.getWebvueProp('songsFilterIsSync', '') });
    this.$store.dispatch('setSongsFilterStatusProcessLyrics', { value: await this.$store.getters.getWebvueProp('songsFilterStatusProcessLyrics', '') });
    this.$store.dispatch('setSongsFilterStatusProcessKaraoke', { value: await this.$store.getters.getWebvueProp('songsFilterStatusProcessKaraoke', '') });
  },
  async mounted() {
    this.$store.getters.songAuthorsPromise.then(data => {
      this.dictAuthors = JSON.parse(data).authors;
      this.$store.dispatch('loadSongsHistory' );
    });
  },
  computed: {
    songsFilterId: {
      get() { return this.$store.getters.getSongsFilterId; },
      set(value) { this.$store.dispatch('setSongsFilterId', { value: value }); }
    },
    songsFilterSongName: {
      get() { return this.$store.getters.getSongsFilterSongName; },
      set(value) { this.$store.dispatch('setSongsFilterSongName', { value: value }); }
    },
    songsFilterSongAuthor: {
      get() { return this.$store.getters.getSongsFilterSongAuthor; },
      set(value) { this.$store.dispatch('setSongsFilterSongAuthor', { value: value }); }
    },
    songsFilterSongAlbum: {
      get() { return this.$store.getters.getSongsFilterSongAlbum; },
      set(value) { this.$store.dispatch('setSongsFilterSongAlbum', { value: value }); }
    },
    songsFilterPublishDate: {
      get() { return this.$store.getters.getSongsFilterPublishDate; },
      set(value) { this.$store.dispatch('setSongsFilterPublishDate', { value: value }); }
    },
    songsFilterPublishTime: {
      get() { return this.$store.getters.getSongsFilterPublishTime; },
      set(value) { this.$store.dispatch('setSongsFilterPublishTime', { value: value }); }
    },
    songsFilterIdStatus: {
      get() { return this.$store.getters.getSongsFilterIdStatus; },
      set(value) { this.$store.dispatch('setSongsFilterIdStatus', { value: value }); }
    },
    songsFilterCountVoices: {
      get() { return this.$store.getters.getSongsFilterCountVoices; },
      set(value) { this.$store.dispatch('setSongsFilterCountVoices', { value: value }); }
    },
    songsFilterTags: {
      get() { return this.$store.getters.getSongsFilterTags; },
      set(value) { this.$store.dispatch('setSongsFilterTags', { value: value }); }
    },
    songsFilterResultVersion: {
      get() { return this.$store.getters.getSongsFilterResultVersion; },
      set(value) { this.$store.dispatch('setSongsFilterResultVersion', { value: value }); }
    },
    songsFilterVersionBoosty: {
      get() { return this.$store.getters.getSongsFilterVersionBoosty; },
      set(value) { this.$store.dispatch('setSongsFilterVersionBoosty', { value: value }); }
    },
    songsFilterVersionBoostyFiles: {
      get() { return this.$store.getters.getSongsFilterVersionBoostyFiles; },
      set(value) { this.$store.dispatch('setSongsFilterVersionBoostyFiles', { value: value }); }
    },
    songsFilterVersionSponsr: {
      get() { return this.$store.getters.getSongsFilterVersionSponsr; },
      set(value) { this.$store.dispatch('setSongsFilterVersionSponsr', { value: value }); }
    },
    songsFilterVersionDzenKaraoke: {
      get() { return this.$store.getters.getSongsFilterVersionDzenKaraoke; },
      set(value) { this.$store.dispatch('setSongsFilterVersionDzenKaraoke', { value: value }); }
    },
    songsFilterVersionVkKaraoke: {
      get() { return this.$store.getters.getSongsFilterVersionVkKaraoke; },
      set(value) { this.$store.dispatch('setSongsFilterVersionVkKaraoke', { value: value }); }
    },
    songsFilterVersionTelegramKaraoke: {
      get() { return this.$store.getters.getSongsFilterVersionTelegramKaraoke; },
      set(value) { this.$store.dispatch('setSongsFilterVersionTelegramKaraoke', { value: value }); }
    },
    songsFilterVersionPlKaraoke: {
      get() { return this.$store.getters.getSongsFilterVersionPlKaraoke; },
      set(value) { this.$store.dispatch('setSongsFilterVersionPlKaraoke', { value: value }); }
    },
    songsFilterRate: {
      get() { return this.$store.getters.getSongsFilterRate; },
      set(value) { this.$store.dispatch('setSongsFilterRate', { value: value }); }
    },
    songsFilterIsSync: {
      get() { return this.$store.getters.getSongsFilterIsSync; },
      set(value) { this.$store.dispatch('setSongsFilterIsSync', { value: value }); }
    },
    songsFilterStatusProcessLyrics: {
      get() { return this.$store.getters.getSongsFilterStatusProcessLyrics; },
      set(value) { this.$store.dispatch('setSongsFilterStatusProcessLyrics', { value: value }); }
    },
    songsFilterStatusProcessKaraoke: {
      get() { return this.$store.getters.getSongsFilterStatusProcessKaraoke; },
      set(value) { this.$store.dispatch('setSongsFilterStatusProcessKaraoke', { value: value }); }
    },
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
      this.$store.dispatch('setSongsFilterId', { value: this.songsFilterId });
      this.$store.dispatch('setSongsFilterSongName', { value: this.songsFilterSongName });
      this.$store.dispatch('setSongsFilterSongAuthor', { value: this.songsFilterSongAuthor });
      this.$store.dispatch('setSongsFilterSongAlbum', { value: this.songsFilterSongAlbum });
      this.$store.dispatch('setSongsFilterPublishDate', { value: this.songsFilterPublishDate });
      this.$store.dispatch('setSongsFilterPublishTime', { value: this.songsFilterPublishTime });
      this.$store.dispatch('setSongsFilterIdStatus', { value: this.songsFilterIdStatus });
      this.$store.dispatch('setSongsFilterCountVoices', { value: this.songsFilterCountVoices });
      this.$store.dispatch('setSongsFilterTags', { value: this.songsFilterTags });
      this.$store.dispatch('setSongsFilterResultVersion', { value: this.songsFilterResultVersion });
      this.$store.dispatch('setSongsFilterVersionBoosty', { value: this.songsFilterVersionBoosty });
      this.$store.dispatch('setSongsFilterVersionBoostyFiles', { value: this.songsFilterVersionBoostyFiles });
      this.$store.dispatch('setSongsFilterVersionSponsr', { value: this.songsFilterVersionSponsr });
      this.$store.dispatch('setSongsFilterVersionDzenKaraoke', { value: this.songsFilterVersionDzenKaraoke });
      this.$store.dispatch('setSongsFilterVersionVkKaraoke', { value: this.songsFilterVersionVkKaraoke });
      this.$store.dispatch('setSongsFilterVersionTelegramKaraoke', { value: this.songsFilterVersionTelegramKaraoke });
      this.$store.dispatch('setSongsFilterVersionPlKaraoke', { value: this.songsFilterVersionPlKaraoke });
      this.$store.dispatch('setSongsFilterRate', { value: this.songsFilterRate });
      this.$store.dispatch('setSongsFilterIsSync', { value: this.songsFilterIsSync });
      this.$store.dispatch('setSongsFilterStatusProcessLyrics', { value: this.songsFilterStatusProcessLyrics });
      this.$store.dispatch('setSongsFilterStatusProcessKaraoke', { value: this.songsFilterStatusProcessKaraoke });

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
      if (this.songsFilterIsSync !== '') params.filter_is_sync = this.songsFilterIsSync;
      if (this.songsFilterStatusProcessLyrics !== '') params.filter_status_process_lyrics = this.songsFilterStatusProcessLyrics;
      if (this.songsFilterStatusProcessKaraoke !== '') params.filter_status_process_karaoke = this.songsFilterStatusProcessKaraoke;
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
      this.songsFilterIsSync = args['filter_is_sync'] ? args['filter_is_sync'] : ''
      this.songsFilterStatusProcessLyrics = args['filter_status_process_lyrics'] ? args['filter_status_process_lyrics'] : ''
      this.songsFilterStatusProcessKaraoke = args['filter_status_process_karaoke'] ? args['filter_status_process_karaoke'] : ''

      this.ok();
    }
  }
}
</script>

<style scoped>

.sfm-history-table-body {
  width: fit-content;
}

.sfm-fld-history-name {
  min-width: 300px;
  max-width: 300px;
  text-align: left;
  font-size: small;
  cursor: default;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
}
.sfm-fld-history-name:hover {
  text-decoration: underline;
  cursor: pointer;
}

.sfm-modal-fade-enter,
.sfm-modal-fade-leave-active {
  opacity: 0;
}

.sfm-modal-fade-enter-active,
.sfm-modal-fade-leave-active {
  transition: opacity .5s ease
}

.sfm-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.sfm-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.sfm-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}

.sfm-modal-backdrop {
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

.sfm-area {
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

.sfm-btn-close {
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

.sfm-root-wrapper {
  display: flex;
  flex-direction: column;
}
.sfm-button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}
.sfm-filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.sfm-row-label {
  min-width: 140px;
  max-width: 140px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.sfm-row-input {
  display: block;
  padding-bottom: 3px;
  width: 200px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}

.sfm-input-field {
  border-radius: 5px;
  width: fit-content;
}

.sfm-input-field:hover {
  background-color: lightyellow;
}
.sfm-input-field:focus {
  background-color: cyan;
}

</style>