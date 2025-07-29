<template>
  <div class="songs-bv-table">
    <SongEditModal v-if="isSongEditVisible" @close="closeSongEdit"/>
    <SongsFilter v-if="isSongsFilterVisible" @close="closeSongsFilter"/>
    <SmartCopyModal v-if="isSmartCopyVisible" @close="closeSmartCopy" :ids="songsIds"/>
    <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
    <div class="songs-bv-table-header">
      <b-pagination
          v-model="currentPage"
          :total-rows="countRows"
          :per-page="perPage"
          :limit="20"
          size="sm"
          pills
      ></b-pagination>
    </div>
    <div class="songs-bv-table-body">
      <b-table
          :items="songsDigests"
          :busy="isBusy"
          :fields="songDigestFields"
          :per-page="perPage"
          :current-page="currentPage"
          small
          fixed
          bordered
          hover
          @row-clicked="onRowClicked"
      >
        <template #table-busy>
          <div class="text-center text-danger my-2">
            <b-spinner class="align-middle"></b-spinner>
            <strong>Loading...</strong>
          </div>
        </template>
        <template #table-colgroup="scope">
          <col
              v-for="field in scope.fields"
              :key="field.key"
              :style="field.style"
          >
        </template>
        <template #cell(id)="data">
          <div
              class="fld-id"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(songName)="data">
          <div
              class="fld-song-name"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
              @click.left="editSong(data.item.id)"
          ></div>
        </template>
        <template #cell(author)="data">
          <div
              class="fld-author"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(year)="data">
          <div
              class="fld-year"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(album)="data">
          <div
              class="fld-album"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(track)="data">
          <div
              class="fld-track"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(date)="data">
          <div
              class="fld-date"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(time)="data">
          <div
              class="fld-time"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(tags)="data">
          <div
              class="fld-tags"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(status)="data">
          <div
              class="fld-song-status"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(countVoices)="data">
          <div
              class="fld-count-voices"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(resultVersion)="data">
          <div
              class="fld-result-version"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagSponsr)="data">
          <div
              class="fld-flag-sponsr"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorSponsr, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagBoosty)="data">
          <div
              class="fld-flag-boosty"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorBoosty, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagVk)="data">
          <div
              class="fld-flag-vk"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorVk, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagDzenLyrics)="data">
          <div
              class="fld-flag-dzen-lyrics"
              v-text="data.value"
              @dblclick.left="playLyrics(data.item.id)"
              :style="{ backgroundColor: data.item.processColorMeltLyrics, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagDzenKaraoke)="data">
          <div
              class="fld-flag-dzen-karaoke"
              v-text="data.value"
              @dblclick.left="playKaraoke(data.item.id)"
              :style="{ backgroundColor: data.item.processColorMeltKaraoke, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagDzenChords)="data">
          <div
              class="fld-flag-dzen-chords"
              v-text="data.value"
              @dblclick.left="playChords(data.item.id)"
              :style="{ backgroundColor: data.item.processColorMeltChords, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagDzenMelody)="data">
          <div
              class="fld-flag-dzen-melody"
              v-text="data.value"
              @dblclick.left="playTabs(data.item.id)"
              :style="{ backgroundColor: data.item.processColorMeltMelody, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagVkLyrics)="data">
          <div
              class="fld-flag-vk-lyrics"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorVkLyrics, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagVkKaraoke)="data">
          <div
              class="fld-flag-vk-karaoke"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorVkKaraoke, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagVkChords)="data">
          <div
              class="fld-flag-vk-chords"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorVkChords, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagVkMelody)="data">
          <div
              class="fld-flag-vk-melody"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorVkMelody, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagTelegramLyrics)="data">
          <div
              class="fld-flag-tg-lyrics"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorTelegramLyrics, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagTelegramKaraoke)="data">
          <div
              class="fld-flag-tg-karaoke"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorTelegramKaraoke, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagTelegramChords)="data">
          <div
              class="fld-flag-tg-chords"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorTelegramChords, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagTelegramMelody)="data">
          <div
              class="fld-flag-tg-melody"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorTelegramMelody, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagPlLyrics)="data">
          <div
              class="fld-flag-pl-lyrics"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorPlLyrics, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagPlKaraoke)="data">
          <div
              class="fld-flag-pl-karaoke"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorPlKaraoke, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagPlChords)="data">
          <div
              class="fld-flag-pl-chords"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorPlChords, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagPlMelody)="data">
          <div
              class="fld-flag-pl-melody"
              v-text="data.value"
              :style="{ backgroundColor: data.item.processColorPlMelody, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(rate)="data">
          <div
              class="fld-rate"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          >
            <b-form-rating
                id="rate-inline"
                size="sm"
                no-border
                inline
                disabled
                v-model="data.value"
                :style="{ height: '18px', backgroundColor: '#fff0' }"
            ></b-form-rating>
          </div>
        </template>
      </b-table>
    </div>
    <div class="songs-bv-table-footer">
      <button class="btn-round-double" @click="isSmartCopyVisible=true" :disabled="countRows===0" title="Smart Copy">{{countRows}}</button>
      <button class="btn-round-double" @click="isSongsFilterVisible=true" title="Фильтр">
        <img alt="filter" class="icon-40" src="../../assets/svg/icon_filter.svg">
      </button>
      <button class="btn-round-double" @click="searchTextForAll" :disabled="countRows===0" title="Найти тексты для всех песен"><img alt="search texts for all" class="icon-40" src="../../assets/svg/icon_search_text.svg"></button>
      <button class="btn-round-double" @click="createKaraokeForAll" :disabled="countRows===0" title="Создать караоке для всех песен"><img alt="create karaoke for all" class="icon-40" src="../../assets/svg/icon_song.svg"></button>
      <button class="btn-round-double" @click="createDemucs2ForAll" :disabled="countRows===0" title="Создать DEMUCS2 для всех песен"><img alt="create demucs2 for all" class="icon-40" src="../../assets/svg/icon_demucs2.svg"></button>
      <button class="btn-round-double" @click="createDemucs5ForAll" :disabled="countRows===0" title="Создать DEMUCS5 для всех песен"><img alt="create demucs5 for all" class="icon-40" src="../../assets/svg/icon_demucs5.svg"></button>
      <button class="btn-round-double" @click="createMP3KaraokeForAll" :disabled="countRows===0" title="Создать MP3 KARAOKE для всех песен"><img alt="create mp3 karaoke for all" class="icon-40" src="../../assets/svg/icon_mp3karaoke.svg"></button>
      <button class="btn-round-double" @click="createMP3LyricsForAll" :disabled="countRows===0" title="Создать MP3 LYRICS для всех песен"><img alt="create mp3 lyrics for all" class="icon-40" src="../../assets/svg/icon_mp3lyrics.svg"></button>
      <button class="btn-round-double" @click="createSymlinksForAll" :disabled="countRows===0" title="Создать SYMLINKS для всех песен"><img alt="create symlink for all" class="icon-40" src="../../assets/svg/icon_symlink.svg"></button>
      <button class="btn-round-double" @click="createSheetsageForAll" :disabled="countRows===0" title="Создать SHEETSAGE для всех песен"><img alt="create sheetsage for all" class="icon-40" src="../../assets/svg/icon_chords.svg"></button>
      <button class="btn-round-double" @click="updateStoreForAll" :disabled="countRows===0" title="Обновить хранилище для всех песен"><img alt="update store for all" class="icon-40" src="../../assets/svg/icon_update_store.svg"></button>
    </div>

  </div>
</template>

<script>

import Vue from "vue";
import { TablePlugin } from 'bootstrap-vue'
import { PaginationPlugin } from 'bootstrap-vue'
import { SpinnerPlugin } from 'bootstrap-vue'
import { FormRatingPlugin } from 'bootstrap-vue'

import SongEditModal from "@/components/Songs/edit/SongEditModal.vue";
import SongsFilter from "@/components/SongsFilter/SongsFilterModal.vue";
import SmartCopyModal from "@/components/SmartCopy/SmartCopyModal.vue";
import CustomConfirm from "@/components/Common/CustomConfirm.vue";
Vue.use(TablePlugin)
Vue.use(PaginationPlugin)
Vue.use(SpinnerPlugin)
Vue.use(FormRatingPlugin)

export default {
  name: "SongsBvTable",
  components: {
    SongEditModal,
    SongsFilter,
    SmartCopyModal,
    CustomConfirm
  },
  data() {
    return {
      perPage: 50,
      currentPage: 1,
      isSongEditVisible: false,
      isSongsFilterVisible: false,
      isSmartCopyVisible: false,
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      isBusy: false,
      currentSongId: 0,
      previousSongId: 0,
      nextSongId: 0
    }
  },
  watch: {
    songsDigestIsLoading: {
      handler () {
        this.isBusy = this.songsDigestIsLoading;
      }
    }
  },
  mounted() {
    // this.$store.dispatch('loadSongsDigests', { filter_author: 'Павел Кашин'} )
  },
  computed: {
    songsIds() {
      return this.$store.getters.getSongsDigestIds;
    },
    songsDigestIsLoading() {
      return this.$store.getters.getSongsDigestIsLoading;
    },
    songsDigests() {
      return this.$store.getters.getSongsDigest;
    },
    songsHistory() {
      return this.$store.getters.getSongsHistory;
    },
    countRows() {
      return this.songsDigests.length;
    },
    songDigestFields() {
      return [
        {
          key: 'id',
          label: 'ID',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'songName',
          label: 'Композиция',
          style: {
            minWidth: '250px',
            maxWidth: '250px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'author',
          label: 'Исполнитель',
          style: {
            minWidth: '150px',
            maxWidth: '150px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'year',
          label: 'Год',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'album',
          label: 'Альбом',
          style: {
            minWidth: '175px',
            maxWidth: '175px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'track',
          label: '№',
          style: {
            minWidth: '35px',
            maxWidth: '35px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'date',
          label: 'Дата',
          style: {
            minWidth: '60px',
            maxWidth: '60px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'time',
          label: 'Время',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'tags',
          label: 'Tags',
          style: {
            minWidth: '35px',
            maxWidth: '35px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'status',
          label: 'Status',
          style: {
            minWidth: '150px',
            maxWidth: '150px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'countVoices',
          label: 'c',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'resultVersion',
          label: 'V',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagSponsr',
          label: 'SP',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagBoosty',
          label: 'BOO',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagVk',
          label: 'VG',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagDzenLyrics',
          label: 'ZL',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagDzenKaraoke',
          label: 'ZK',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagDzenChords',
          label: 'ZC',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagDzenMelody',
          label: 'ZM',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagVkLyrics',
          label: 'VL',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagVkKaraoke',
          label: 'VK',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagVkChords',
          label: 'VC',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagVkMelody',
          label: 'VM',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagTelegramLyrics',
          label: 'TL',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagTelegramKaraoke',
          label: 'TK',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagTelegramChords',
          label: 'TC',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagTelegramMelody',
          label: 'TM',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagPlLyrics',
          label: 'PL',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagPlKaraoke',
          label: 'PK',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagPlChords',
          label: 'PC',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagPlMelody',
          label: 'PM',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'rate',
          label: 'Rate',
          style: {
            minWidth: '100px',
            maxWidth: '100px',
            textAlign: 'center',
            fontSize: 'small'
          }
        }
      ]
    }
  },
  methods: {
    searchTextForAll() {
      this.customConfirmParams = {
        header: 'Подтвердите поиск текста',
        body: `Выбрано песен: <strong>${this.countRows}.</strong><br>Найти в Интернете тексты для всех песен, для которых ещё нет текстов?`,
        timeout: 10,
        callback: this.doSearchTextForAll
      }
      this.isCustomConfirmVisible = true;
    },
    doSearchTextForAll() {
      this.$store.dispatch('searchTextForAll')
    },
    createKaraokeForAll() {
      this.customConfirmParams = {
        header: 'Подтвердите создание караоке',
        body: `Выбрано песен: <strong>${this.countRows}.</strong><br>Создать караоке для всех песен?`,
        callback: this.doCreateKaraokeForAll,
        fields: [
          {
            fldName: 'priorLyrics',
            fldLabel: 'Приоритет Lyrics:',
            fldValue: this.$store.getters.getLastPriorLyrics,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          },
          {
            fldName: 'priorKaraoke',
            fldLabel: 'Приоритет Karaoke:',
            fldValue: this.$store.getters.getLastPriorKaraoke,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          },
          {
            fldName: 'priorChords',
            fldLabel: 'Приоритет Chords:',
            fldValue: this.$store.getters.getLastPriorChords,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          },
          {
            fldName: 'priorMelody',
            fldLabel: 'Приоритет Melody:',
            fldValue: this.$store.getters.getLastPriorMelody,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateKaraokeForAll(result) {
      this.$store.dispatch('setLastPriorLyrics', {value: result.priorLyrics});
      this.$store.dispatch('setLastPriorKaraoke', {value: result.priorKaraoke});
      this.$store.dispatch('setLastPriorChords', {value: result.priorChords});
      this.$store.dispatch('setLastPriorMelody', {value: result.priorMelody});
      this.$store.dispatch('createKaraokeForAllPromise', {priorLyrics: result.priorLyrics, priorKaraoke: result.priorKaraoke, priorChords: result.priorChords, priorMelody: result.priorMelody}).then(data => {
        let response = JSON.parse(data);
        this.customConfirmParams = {
          isAlert: true,
          alertType: response ? 'info' : 'error',
          header: 'Создание караоке',
          body: `Создание караоке для всех песен прошло успешно.`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    createDemucs2ForAll() {
      this.customConfirmParams = {
        header: 'Подтвердите создание DEMUCS2',
        body: `Выбрано песен: <strong>${this.countRows}.</strong><br>Создать DEMUCS2 для всех песен?`,
        callback: this.doCreateDemucs2ForAll,
        fields: [
          {
            fldName: 'prior',
            fldLabel: 'Приоритет:',
            fldValue: this.$store.getters.getLastPriorDemucs,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateDemucs2ForAll(result) {
      this.$store.dispatch('setLastPriorDemucs', {value: result.prior});
      this.$store.dispatch('createDemucs2ForAllPromise', { prior: result.prior }).then(data => {
        let response = JSON.parse(data);
        this.customConfirmParams = {
          isAlert: true,
          alertType: response ? 'info' : 'error',
          header: 'Создание DEMUCS2',
          body: `Создание DEMUCS2 для всех песен прошло успешно.`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    createDemucs5ForAll() {
      this.customConfirmParams = {
        header: 'Подтвердите создание DEMUCS5',
        body: `Выбрано песен: <strong>${this.countRows}.</strong><br>Создать DEMUCS5 для всех песен?`,
        callback: this.doCreateDemucs5ForAll,
        fields: [
          {
            fldName: 'prior',
            fldLabel: 'Приоритет:',
            fldValue: this.$store.getters.getLastPriorDemucs,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateDemucs5ForAll(result) {
      this.$store.dispatch('setLastPriorDemucs', {value: result.prior});
      this.$store.dispatch('createDemucs5ForAllPromise', { prior: result.prior }).then(data => {
        let response = JSON.parse(data);
        this.customConfirmParams = {
          isAlert: true,
          alertType: response ? 'info' : 'error',
          header: 'Создание DEMUCS5',
          body: `Создание DEMUCS5 для всех песен прошло успешно.`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    createSheetsageForAll() {
      this.customConfirmParams = {
        header: 'Подтвердите создание SHEETSAGE',
        body: `Выбрано песен: <strong>${this.countRows}.</strong><br>Создать SHEETSAGE для всех песен?`,
        callback: this.doCreateSheetsageForAll,
        fields: [
          {
            fldName: 'prior',
            fldLabel: 'Приоритет:',
            fldValue: this.$store.getters.getLastPriorDemucs,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateSheetsageForAll(result) {
      this.$store.dispatch('setLastPriorDemucs', {value: result.prior});
      this.$store.dispatch('createSheetsageForAllPromise', { prior: result.prior }).then(data => {
        let response = JSON.parse(data);
        this.customConfirmParams = {
          isAlert: true,
          alertType: response ? 'info' : 'error',
          header: 'Создание SHEETSAGE',
          body: `Создание SHEETSAGE для всех песен прошло успешно.`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    createMP3KaraokeForAll() {
      this.customConfirmParams = {
        header: 'Подтвердите создание MP3 KARAOKE',
        body: `Выбрано песен: <strong>${this.countRows}.</strong><br>Создать MP3 KARAOKE для всех песен?`,
        callback: this.doCreateMP3KaraokeForAll,
        fields: [
          {
            fldName: 'prior',
            fldLabel: 'Приоритет:',
            fldValue: this.$store.getters.getLastPriorDemucs,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateMP3KaraokeForAll(result) {
      this.$store.dispatch('setLastPriorDemucs', {value: result.prior});
      this.$store.dispatch('createMP3KaraokeForAllPromise', { prior: result.prior }).then(data => {
        let response = JSON.parse(data);
        this.customConfirmParams = {
          isAlert: true,
          alertType: response ? 'info' : 'error',
          header: 'Создание MP3 KARAOKE',
          body: `Создание MP3 KARAOKE для всех песен прошло успешно.`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    createMP3LyricsForAll() {
      this.customConfirmParams = {
        header: 'Подтвердите создание MP3 LYRICS',
        body: `Выбрано песен: <strong>${this.countRows}.</strong><br>Создать MP3 LYRICS для всех песен?`,
        callback: this.doCreateMP3LyricsForAll,
        fields: [
          {
            fldName: 'prior',
            fldLabel: 'Приоритет:',
            fldValue: this.$store.getters.getLastPriorDemucs,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateMP3LyricsForAll(result) {
      this.$store.dispatch('setLastPriorDemucs', {value: result.prior});
      this.$store.dispatch('createMP3LyricsForAllPromise', { prior: result.prior }).then(data => {
        let response = JSON.parse(data);
        this.customConfirmParams = {
          isAlert: true,
          alertType: response ? 'info' : 'error',
          header: 'Создание MP3 LYRICS',
          body: `Создание MP3 LYRICS для всех песен прошло успешно.`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    createSymlinksForAll() {
      this.customConfirmParams = {
        header: 'Подтвердите создание SYMLINKs',
        body: `Выбрано песен: <strong>${this.countRows}.</strong><br>Создать SYMLINKs для всех песен?`,
        callback: this.doCreateSymlinksForAll,
        fields: [
          {
            fldName: 'prior',
            fldLabel: 'Приоритет:',
            fldValue: this.$store.getters.getLastPriorSymlinks,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateSymlinksForAll(result) {
      this.$store.dispatch('setLastPriorSymlinks', {value: result.prior});
      this.$store.dispatch('createSymlinksForAllPromise', { prior: result.prior } ).then(data => {
        let response = JSON.parse(data);
        this.customConfirmParams = {
          isAlert: true,
          alertType: response ? 'info' : 'error',
          header: 'Создание SYMLINKs',
          body: `Создание SYMLINKs для всех песен прошло успешно.`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },

    updateStoreForAll() {
      this.customConfirmParams = {
        header: 'Подтвердите обновление хранилища',
        body: `Выбрано песен: <strong>${this.countRows}.</strong><br>Скопировать недостающие файлы в хранилище<br>и создать задачи на кодирование недостающих 720p?`,
        callback: this.doUpdateStoreForAll,
        fields: [
          {
            fldName: 'priorLyrics',
            fldLabel: 'Приоритет Lyrics:',
            fldValue: this.$store.getters.getLastPriorCodeLyrics,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          },
          {
            fldName: 'priorKaraoke',
            fldLabel: 'Приоритет Karaoke:',
            fldValue: this.$store.getters.getLastPriorCodeKaraoke,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doUpdateStoreForAll(result) {
      this.$store.dispatch('setLastPriorCodeLyrics', {value: result.priorLyrics});
      this.$store.dispatch('setLastPriorCodeKaraoke', {value: result.priorKaraoke});
      let songsIds = this.$store.getters.getSongsDigestIds.join(';')
      this.$store.dispatch('collectStorePromise', {songsIds: songsIds, priorLyrics: result.priorLyrics, priorKaraoke: result.priorKaraoke}).then(data => {
        let result = JSON.parse(data);
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Обновление хранилища',
          body: `Готово.<hr>
                Скопировано файлов: <strong>${result[0]}</strong><br>
                Создано задач на кодирование 720p: <strong>${result[1]}</strong>`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },

    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },

    editSong(id) {
      this.$store.commit('setCurrentSongId', id);
      this.isSongEditVisible = true;
    },
    playLyrics(id) {
      this.$store.getters.playLyrics(id);
    },
    playKaraoke(id) {
      this.$store.getters.playKaraoke(id);
    },
    playChords(id) {
      this.$store.getters.playChords(id);
    },
    playTabs(id) {
      this.$store.getters.playTabs(id);
    },
    closeSongEdit() {
      this.isSongEditVisible = false;
    },
    closeSongsFilter() {
      this.isSongsFilterVisible = false;
    },
    closeSmartCopy() {
      this.isSmartCopyVisible = false;
    },
    onRowClicked(item, index) {
      this.currentSongId = item.id;
      this.previousSongId = item.idPrevious;
      this.nextSongId = item.idNext;
      console.log(`Row '${index}' clicked: `, item.songName);
    },
    getCellStyle(data) {
      return {
        backgroundColor: data.item.color
      }
    }
  }
}
</script>

<style>

.songs-bv-table {
  padding: 0;
  margin: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}

.songs-bv-table-header {
  width: fit-content;
}

.songs-bv-table-body {
  width: fit-content;
}

.songs-bv-table-footer {
  margin-top: auto;
  display: flex;
  flex-direction: row;
  align-items: center;
}

.fld-id {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-song-name {
  min-width: 250px;
  max-width: 250px;
  text-align: left;
  font-size: small;
  cursor: default;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
}
.fld-song-name:hover {
  text-decoration: underline;
  cursor: pointer;
}
.fld-author {
  min-width: 150px;
  max-width: 150px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-year {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-album {
  min-width: 175px;
  max-width: 175px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-track {
  min-width: 35px;
  max-width: 35px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-date {
  min-width: 60px;
  max-width: 60px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-time {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-tags {
  min-width: 35px;
  max-width: 35px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-song-status {
  min-width: 150px;
  max-width: 150px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-count-voices {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-result-version {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-boosty {
  min-width: 100%;
  max-width: 100%;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-sponsr {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-vk {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-dzen-lyrics {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-dzen-karaoke {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-dzen-chords {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-dzen-melody {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-vk-lyrics {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-vk-karaoke {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-vk-chords {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-vk-melody {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-tg-lyrics {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-tg-karaoke {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-tg-chords {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-tg-melody {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-pl-lyrics {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-pl-karaoke {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-pl-chords {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-pl-melody {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-rate {
  min-width: 100px;
  max-width: 100px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.btn-round-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 50px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.btn-round-double:hover {
  background-color: lightpink;
}
.btn-round-double:focus {
  background-color: darksalmon;
}
.btn-round-double[disabled] {
  background-color: lightgray;
}
.icon-40 {
  width: 40px;
  height: 40px;
}

</style>