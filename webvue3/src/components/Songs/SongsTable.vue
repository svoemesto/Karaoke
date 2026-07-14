<template>
  <div class="songs-bv-table">
    <SongEditModal v-if="isSongEditVisible" 
      @close="closeSongEdit" 
      :parent-route="parentRoute" 
      :songs-digests="songsDigests"/>
    <SongsFilter v-if="isSongsFilterVisible" 
      @close="closeSongsFilter"/>
    <SmartCopyModal v-if="isSmartCopyVisible" 
      @close="closeSmartCopy" 
      :ids="songsIds"/>
    <custom-confirm v-if="isCustomConfirmVisible" 
      :params="customConfirmParams" 
      @close="closeCustomConfirm" />
    <health-report-table v-if="isHealthReportTableVisible"
      :id="currentSongId"
      @close="closeHealthReportTable"/>
    <ReviewModal v-if="isAssignReviewVisible" @close="isAssignReviewVisible = false" @reviewed="onAssignmentReviewed" />
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
          v-model:sort-by="sortBy"
          small
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
              class="fld-song-id"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(rootId)="data">
          <div
              class="fld-root-id"
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
              v-text="data.value ? data.value : '-'"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(time)="data">
          <div
              class="fld-time"
              v-text="data.value ? data.value : '-'"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(tags)="data">
          <div
              class="fld-tags"
              v-text="data.value ? data.value : '-'"
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
        <template #cell(timecode)="data">
          <div
              class="fld-timecode"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(healthReportText)="data">
          <div
              class="fld-health-report-text"
              v-text="data.value"
              :style="{ backgroundColor: data.item.healthReportColor, color: currentSongId === data.item.id ? 'blue' : 'black' }"
              @click.left="showHealthReportTable(data.item.id)"
          ></div>
        </template>
        <template #cell(resultVersion)="data">
          <div
              class="fld-result-version"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(player)="data">
          <div
              class="fld-player"
              :style="{ backgroundColor: data.item.color }"
          >
            <a
                v-if="data.item.idStatus >= 3"
                href="#"
                class="player-icon-link"
                title="Открыть онлайн-плеер"
                @click.left.prevent="openPlayer(data.item.id)"
            >
              <svg width="18" height="18" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg" fill="none">
                <circle cx="10" cy="10" r="10" fill="#22A447"/>
                <path d="M8 6.5v7l6-3.5-6-3.5Z" fill="#fff"/>
              </svg>
            </a>
            <span v-else class="player-icon-disabled" title="Плеер недоступен (статус < 3)">
              <svg width="18" height="18" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg" fill="none">
                <circle cx="10" cy="10" r="10" fill="#E6E6E6"/>
                <path d="M8 6.5v7l6-3.5-6-3.5Z" fill="#919191"/>
              </svg>
            </span>
          </div>
        </template>
        <template #cell(playerDemo)="data">
          <div
              class="fld-player"
              :style="{ backgroundColor: data.item.color }"
          >
            <a
                v-if="data.item.idStatus >= 3"
                href="#"
                class="player-icon-link"
                title="Открыть DEMO-плеер"
                @click.left.prevent="openPlayerDemo(data.item.id)"
            >
              <svg width="18" height="18" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg" fill="none">
                <circle cx="10" cy="10" r="10" fill="#C5A53C"/>
                <path d="M8 6.5v7l6-3.5-6-3.5Z" fill="#fff"/>
              </svg>
            </a>
            <span v-else class="player-icon-disabled" title="DEMO-плеер недоступен (статус < 3)">
              <svg width="18" height="18" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg" fill="none">
                <circle cx="10" cy="10" r="10" fill="#E6E6E6"/>
                <path d="M8 6.5v7l6-3.5-6-3.5Z" fill="#919191"/>
              </svg>
            </span>
          </div>
        </template>
        <template #cell(assign)="data">
          <div class="fld-assign" :style="{ backgroundColor: data.item.color }" @click.stop>
            <template v-if="data.item.idStatus < 3">
              <button
                  v-if="assignmentStatusFor(data.item.id)"
                  class="assign-badge"
                  :class="`assign-badge-${assignmentStatusFor(data.item.id).status}`"
                  :title="assignmentStatusFor(data.item.id).assigneeName"
                  @click="openAssignmentReview(data.item.id)"
              >{{ assignStatusLabel(assignmentStatusFor(data.item.id).status) }}</button>
              <select v-else class="assign-select" @change="onAssignSelect(data.item.id, $event)">
                <option value="" selected disabled>Назначить…</option>
                <option v-for="u in editorSiteUsers" :key="u.id" :value="u.id">{{ u.displayName || u.email }}</option>
              </select>
            </template>
          </div>
        </template>
        <template #cell(flagSponsr)="data">
          <div
              class="fld-flag-sponsr"
              v-text="data.value ? data.value : '-'"
              :style="{ backgroundColor: data.item.processColorSponsr, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagVk)="data">
          <div
              class="fld-flag-vk"
              v-text="data.value ? data.value : '-'"
              :style="{ backgroundColor: data.item.processColorVk, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagDzenLyrics)="data">
          <div
              class="fld-flag-dzen-lyrics"
              v-text="data.value ? data.value : '-'"
              @dblclick.left="playLyrics(data.item.id)"
              :style="{ backgroundColor: data.item.processColorMeltLyrics, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagDzenKaraoke)="data">
          <div
              class="fld-flag-dzen-karaoke"
              v-text="data.value ? data.value : '-'"
              @dblclick.left="playKaraoke(data.item.id)"
              :style="{ backgroundColor: data.item.processColorMeltKaraoke, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagDzenChords)="data">
          <div
              class="fld-flag-dzen-chords"
              v-text="data.value ? data.value : '-'"
              @dblclick.left="playChords(data.item.id)"
              :style="{ backgroundColor: data.item.processColorMeltChords, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagDzenMelody)="data">
          <div
              class="fld-flag-dzen-melody"
              v-text="data.value ? data.value : '-'"
              @dblclick.left="playTabs(data.item.id)"
              :style="{ backgroundColor: data.item.processColorMeltMelody, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagVkLyrics)="data">
          <div
              class="fld-flag-vk-lyrics"
              v-text="data.value ? data.value : '-'"
              :style="{ backgroundColor: data.item.processColorVkLyrics, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagVkKaraoke)="data">
          <div
              class="fld-flag-vk-karaoke"
              v-text="data.value ? data.value : '-'"
              :style="{ backgroundColor: data.item.processColorVkKaraoke, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagVkChords)="data">
          <div
              class="fld-flag-vk-chords"
              v-text="data.value ? data.value : '-'"
              :style="{ backgroundColor: data.item.processColorVkChords, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagVkMelody)="data">
          <div
              class="fld-flag-vk-melody"
              v-text="data.value ? data.value : '-'"
              :style="{ backgroundColor: data.item.processColorVkMelody, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagTelegramLyrics)="data">
          <div
              class="fld-flag-tg-lyrics"
              v-text="data.value ? data.value : '-'"
              :style="{ backgroundColor: data.item.processColorTelegramLyrics, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagTelegramKaraoke)="data">
          <div
              class="fld-flag-tg-karaoke"
              v-text="data.value ? data.value : '-'"
              :style="{ backgroundColor: data.item.processColorTelegramKaraoke, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagTelegramChords)="data">
          <div
              class="fld-flag-tg-chords"
              v-text="data.value ? data.value : '-'"
              :style="{ backgroundColor: data.item.processColorTelegramChords, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagTelegramMelody)="data">
          <div
              class="fld-flag-tg-melody"
              v-text="data.value ? data.value : '-'"
              :style="{ backgroundColor: data.item.processColorTelegramMelody, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagExclusive)="data">
          <div
              class="fld-flag-exclusive"
              v-text="data.value ? data.value : '-'"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        <template #cell(flagFree)="data">
          <div
              class="fld-flag-free"
              v-text="data.value ? data.value : '-'"
              :style="{ backgroundColor: data.item.color, color: currentSongId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
       <template #cell(flagMaxLyrics)="data">
         <div
             class="fld-flag-max-lyrics"
             v-text="data.value"
             :style="{ backgroundColor: data.item.processColorMaxLyrics, color: currentSongId === data.item.id ? 'blue' : 'black' }"
         ></div>
       </template>
       <template #cell(flagMaxKaraoke)="data">
         <div
             class="fld-flag-max-karaoke"
             v-text="data.value"
             :style="{ backgroundColor: data.item.processColorMaxKaraoke, color: currentSongId === data.item.id ? 'blue' : 'black' }"
         ></div>
       </template>
       <template #cell(flagMaxChords)="data">
         <div
             class="fld-flag-max-chords"
             v-text="data.value"
             :style="{ backgroundColor: data.item.processColorMaxChords, color: currentSongId === data.item.id ? 'blue' : 'black' }"
         ></div>
       </template>
       <template #cell(flagMaxMelody)="data">
         <div
             class="fld-flag-max-melody"
             v-text="data.value"
             :style="{ backgroundColor: data.item.processColorMaxMelody, color: currentSongId === data.item.id ? 'blue' : 'black' }"
         ></div>
       </template>       
<!--        <template #cell(flagPlLyrics)="data">-->
<!--          <div-->
<!--              class="fld-flag-pl-lyrics"-->
<!--              v-text="data.value"-->
<!--              :style="{ backgroundColor: data.item.processColorPlLyrics, color: currentSongId === data.item.id ? 'blue' : 'black' }"-->
<!--          ></div>-->
<!--        </template>-->
<!--        <template #cell(flagPlKaraoke)="data">-->
<!--          <div-->
<!--              class="fld-flag-pl-karaoke"-->
<!--              v-text="data.value"-->
<!--              :style="{ backgroundColor: data.item.processColorPlKaraoke, color: currentSongId === data.item.id ? 'blue' : 'black' }"-->
<!--          ></div>-->
<!--        </template>-->
<!--        <template #cell(flagPlChords)="data">-->
<!--          <div-->
<!--              class="fld-flag-pl-chords"-->
<!--              v-text="data.value"-->
<!--              :style="{ backgroundColor: data.item.processColorPlChords, color: currentSongId === data.item.id ? 'blue' : 'black' }"-->
<!--          ></div>-->
<!--        </template>-->
<!--        <template #cell(flagPlMelody)="data">-->
<!--          <div-->
<!--              class="fld-flag-pl-melody"-->
<!--              v-text="data.value"-->
<!--              :style="{ backgroundColor: data.item.processColorPlMelody, color: currentSongId === data.item.id ? 'blue' : 'black' }"-->
<!--          ></div>-->
<!--        </template>-->
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
                :style="{ backgroundColor: '#fff0', height: '18px', minHeight: '18px', padding: '0' }"
            ></b-form-rating>
          </div>
        </template>
      </b-table>
    </div>
    <div class="songs-bv-table-footer">
      <button class="btn-round-long-double" @click="isSmartCopyVisible=true" :disabled="countRows===0" title="Smart Copy">{{smartCopyButtonCaption}}</button>
      <button class="btn-round-double" @click="isSongsFilterVisible=true" title="Фильтр">
        <img alt="filter" class="icon-40" src="../../assets/svg/icon_filter.svg">
      </button>
      <button class="btn-round-double" @click="searchTextForAll" :disabled="countRows===0" title="Найти тексты для всех песен"><img alt="search texts for all" class="icon-40" src="../../assets/svg/icon_search_text.svg"></button>
      <button class="btn-round-double" @click="createKaraokeForAll" :disabled="countRows===0" title="Создать караоке для всех песен"><img alt="create karaoke for all" class="icon-40" src="../../assets/svg/icon_song.svg"></button>
      <button class="btn-round-double" @click="createDemucs2ForAll" :disabled="countRows===0" title="Создать DEMUCS2 для всех песен"><img alt="create demucs2 for all" class="icon-40" src="../../assets/svg/icon_demucs2.svg"></button>
      <button class="btn-round-double" @click="createDemucs5ForAll" :disabled="countRows===0" title="Создать DEMUCS5 для всех песен"><img alt="create demucs5 for all" class="icon-40" src="../../assets/svg/icon_demucs5.svg"></button>
      <!-- <button class="btn-round-double" @click="createMP3KaraokeForAll" :disabled="countRows===0" title="Создать MP3 KARAOKE для всех песен"><img alt="create mp3 karaoke for all" class="icon-40" src="../../assets/svg/icon_mp3karaoke.svg"></button> -->
      <!-- <button class="btn-round-double" @click="createMP3LyricsForAll" :disabled="countRows===0" title="Создать MP3 LYRICS для всех песен"><img alt="create mp3 lyrics for all" class="icon-40" src="../../assets/svg/icon_mp3lyrics.svg"></button> -->
      <button class="btn-round-double" @click="createSymlinksForAll" :disabled="countRows===0" title="Создать SYMLINKS для всех песен"><img alt="create symlink for all" class="icon-40" src="../../assets/svg/icon_symlink.svg"></button>
      <button class="btn-round-double" @click="createSheetsageForAll" :disabled="countRows===0" title="Создать SHEETSAGE для всех песен"><img alt="create sheetsage for all" class="icon-40" src="../../assets/svg/icon_chords.svg"></button>
      <button class="btn-round-double" @click="updateStoreForAll" :disabled="countRows===0" title="Обновить хранилище для всех песен"><img alt="update store for all" class="icon-40" src="../../assets/svg/icon_update_store.svg"></button>
      <button class="btn-round-double" @click="addSyncForAll" :disabled="countRows===0 || !allowAddSync" title="Добавить записи в SYNC-таблицу"><img alt="add records to SYNC table" class="icon-40" src="../../assets/svg/icon_sync.svg"></button>
      <button class="btn-round-double" @click="repairAll" :disabled="countRows===0" title="Repair All"><img alt="Repair all" class="icon-40" src="../../assets/svg/icon_repair.svg"></button>
    </div>

  </div>
</template>

<script>

import { BPagination, BSpinner, BTable, BFormRating } from 'bootstrap-vue-next'
import SongEditModal from "../../components/Songs/edit/SongEditModal.vue";
import SongsFilter from "../../components/Songs/filter/SongsFilterModal.vue";
import SmartCopyModal from "../../components/Common/SmartCopy/SmartCopyModal.vue";
import CustomConfirm from "../../components/Common/CustomConfirm.vue";
import HealthReportTable from "../Common/HealthReport/HealthReportTable.vue";
import ReviewModal from "../SongEditor/ReviewModal.vue";

const ASSIGN_STATUS_LABELS = {
  assigned: 'Назначено', in_progress: 'В работе', submitted: 'На проверке',
  approved: 'Одобрено', rejected: 'Отклонено',
};

export default {
  name: "SongsTable",
  components: {
    HealthReportTable,
    SongEditModal,
    SongsFilter,
    SmartCopyModal,
    CustomConfirm,
    ReviewModal,
    BPagination,
    BSpinner,
    BTable,
    BFormRating
  },
  data() {
    return {
      perPage: 50,
      currentPage: 1,
      sortBy: [],
      isSongEditVisible: false,
      isSongsFilterVisible: false,
      isSmartCopyVisible: false,
      isCustomConfirmVisible: false,
      isHealthReportTableVisible: false,
      isAssignReviewVisible: false,
      customConfirmParams: undefined,
      isBusy: false,
      allowAddSync: false,
      hrQueue: [],
      hrRunning: 0,
      HR_MAX_CONCURRENT: 3
    }
  },
  watch: {
    songsDigestIsLoading: {
      handler () {
        this.isBusy = this.songsDigestIsLoading;
      }
    },
    countRows: {
      handler () {
        this.currentPage = 1;
        this.updateHealthReportForCurrentPage();
        this.reloadAssignmentStatus();
      }
    },
    currentSongId: {
      handler () {
        const songPageNumber = this.songIdAndPageId.get(this.currentSongId);
        if (songPageNumber !== undefined && this.currentPage !== songPageNumber) this.currentPage = songPageNumber;
      }
    },
    currentPage: {
      handler () {
        this.hrQueue = [];
        this.updateHealthReportForCurrentPage();
        this.reloadAssignmentStatus();
      }
    }
  },
  async mounted() {
    // this.$store.dispatch('loadSongsDigests', { filterAuthor: 'Павел Кашин'} )
    this.allowAddSync = await this.propAllowAddSync();
    // Источник (local/server) для кнопки «Назначить» — KaraokeProperty editorAssignmentDefaultTarget.
    await this.$store.dispatch('loadEditorDefaultTarget');
    this.$store.dispatch('loadEditorSiteUsers', this.$store.getters.getEditorDefaultTarget);
    this.reloadAssignmentStatus();
  },
  computed: {
    parentRoute() {
      return 'Songs';
    },
    smartCopyButtonCaption() {
      let caption = '';
      if (this.countRows > 0) {
        caption = this.countRows + ' [' + this.totalDuration + ']';
      }
      return caption;
    },
    songIdAndPageId() {
      const result = new Map();
      for (let i = 0; i < this.songsIds.length; i++) {
        const songId = this.songsIds[i];
        const pageNumber = Math.floor(i / this.perPage) + 1;
        result.set(songId, pageNumber);
      }
      return result;
    },
    currentSongId() {
      return this.$store.getters.getCurrentSongId;
    },
    songsIds() {
      return this.$store.getters.getSongsDigestIds;
    },
    // Только id видимой страницы (та же формула, что updateHealthReportForCurrentPage) — батч-статус
    // назначений грузим не на весь каталог, а по странице, как и HR-запросы.
    currentPageSongIds() {
      return this.songsIds.filter(id => this.songIdAndPageId.get(id) === this.currentPage);
    },
    editorSiteUsers() {
      return this.$store.getters.getEditorSiteUsers || [];
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
      return this.songsDigests ? this.songsDigests.length : 0;
    },
    totalDuration() {
      return this.$store.getters.getTotalDuration;
    },
    songDigestFields() {
      return [
        {
          key: 'id',
          sortable: true,
          label: 'ID',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'rootId',
          sortable: true,
          label: 'root',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'center',
            fontSize: 'smaller'
          }
        },
        {
          key: 'songName',
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
          label: 'V',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'timecode',
          sortable: true,
          label: 't/c',
          style: {
            minWidth: '60px',
            maxWidth: '60px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'healthReportText',
          sortable: true,
          label: 'HR',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'player',
          label: '▶',
          style: {
            minWidth: '22px',
            maxWidth: '22px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'playerDemo',
          label: '▶',
          style: {
            minWidth: '22px',
            maxWidth: '22px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'assign',
          label: 'Редактор',
          style: {
            minWidth: '90px',
            maxWidth: '90px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagSponsr',
          sortable: true,
          label: 'SP',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagVk',
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
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
          sortable: true,
          label: 'TM',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagMaxLyrics',
          sortable: true,
          label: 'ML',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagMaxKaraoke',
          sortable: true,
          label: 'MK',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagMaxChords',
          sortable: true,
          label: 'MC',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagMaxMelody',
          sortable: true,
          label: 'MM',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagExclusive',
          sortable: true,
          label: 'EX',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'flagFree',
          sortable: true,
          label: 'FR',
          style: {
            minWidth: '20px',
            maxWidth: '20px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        // {
        //   key: 'flagPlLyrics',
        //   label: 'PL',
        //   style: {
        //     minWidth: '20px',
        //     maxWidth: '20px',
        //     textAlign: 'center',
        //     fontSize: 'small'
        //   }
        // },
        // {
        //   key: 'flagPlKaraoke',
        //   label: 'PK',
        //   style: {
        //     minWidth: '20px',
        //     maxWidth: '20px',
        //     textAlign: 'center',
        //     fontSize: 'small'
        //   }
        // },
        // {
        //   key: 'flagPlChords',
        //   label: 'PC',
        //   style: {
        //     minWidth: '20px',
        //     maxWidth: '20px',
        //     textAlign: 'center',
        //     fontSize: 'small'
        //   }
        // },
        // {
        //   key: 'flagPlMelody',
        //   label: 'PM',
        //   style: {
        //     minWidth: '20px',
        //     maxWidth: '20px',
        //     textAlign: 'center',
        //     fontSize: 'small'
        //   }
        // },
        {
          key: 'rate',
          sortable: true,
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
    openPlayer(id) {
      window.open('/player/' + id, '_blank')
    },
    async openPlayerDemo(id) {
      try {
        const resp = await fetch('/api/song/' + id + '/demobounds')
        if (!resp.ok) return
        const bounds = await resp.json()
        if (bounds.start == null || bounds.end == null) return
        const params = new URLSearchParams({
          version: 'DEMO',
          demoStart: String(bounds.start),
          demoEnd: String(bounds.end),
        })
        window.open('/player/' + id + '?' + params.toString(), '_blank')
      } catch (e) {
        console.error('openPlayerDemo failed:', e)
      }
    },
    // --- Кнопка «Назначить»/«Назначено» (онлайн-редактор) -----------------------------------
    reloadAssignmentStatus() {
      const target = this.$store.getters.getEditorDefaultTarget;
      return this.$store.dispatch('loadAssignmentStatusBySongIds', { songIds: this.currentPageSongIds, target });
    },
    assignmentStatusFor(songId) {
      return this.$store.getters.getAssignmentStatusBySongId[songId];
    },
    assignStatusLabel(s) { return ASSIGN_STATUS_LABELS[s] || 'Назначено' },
    async onAssignSelect(songId, event) {
      const assigneeId = event.target.value;
      event.target.value = '';
      if (!assigneeId) return;
      const target = this.$store.getters.getEditorDefaultTarget;
      let res = await this.$store.dispatch('assignSong', { songId, assigneeId, target });
      if (res && res.error === 'markers_exist') {
        const clearMarkers = window.confirm('В песне уже есть маркеры. Удалить их при назначении задания?');
        res = await this.$store.dispatch('assignSong', { songId, assigneeId, target, clearMarkers });
      }
      if (res && res.ok) {
        this.reloadAssignmentStatus();
      } else {
        alert('Не удалось назначить: ' + ((res && res.error) || 'неизвестная ошибка'));
      }
    },
    async openAssignmentReview(songId) {
      const info = this.assignmentStatusFor(songId);
      if (!info) return;
      await this.$store.dispatch('loadAssignmentById', { id: info.assignmentId, target: this.$store.getters.getEditorDefaultTarget });
      this.isAssignReviewVisible = true;
    },
    onAssignmentReviewed() {
      this.isAssignReviewVisible = false;
      this.reloadAssignmentStatus();
    },
    updateHealthReportForCurrentPage() {
      for (const settingsId of this.songsIds) {
        const songPageNumber = this.songIdAndPageId.get(settingsId);
        if (songPageNumber === this.currentPage) {
          const filteredSongs = this.songsDigests.filter(song => song.id === settingsId);
          if (filteredSongs && filteredSongs.length > 0) {
            const song = filteredSongs[0];
            if (song.healthReportText === '-') {
              this._enqueueHrRequest(settingsId);
            }
          }
        }
      }
    },
    _enqueueHrRequest(settingsId) {
      this.hrQueue.push(settingsId);
      this._processHrQueue();
    },
    _processHrQueue() {
      while (this.hrRunning < this.HR_MAX_CONCURRENT && this.hrQueue.length > 0) {
        const id = this.hrQueue.shift();
        this.hrRunning++;
        this.$store.dispatch('setCurrentSongHealthReports', id)
            .finally(() => { this.hrRunning--; this._processHrQueue(); });
      }
    },
    repairAllForCurrentPage() {
      for (const settingsId of this.songsIds) {
        const songPageNumber = this.songIdAndPageId.get(settingsId);
        if (songPageNumber === this.currentPage) {
          const filteredSongs = this.songsDigests.filter(song => song.id === settingsId);
          if (filteredSongs && filteredSongs.length > 0) {
            const song = filteredSongs[0];
            if (song.healthReportText !== '-' && song.healthReportText !== '0') {
              const healthReportListCanRepair = song.healthReportList.filter(healthReport => healthReport.canResolve);
              if (healthReportListCanRepair.length > 0) {
                this.$store.dispatch('repairAllPromise', song.id);
              }
            }
          }
        }
      }
    },
    async propAllowAddSync() {
      const propValue = await this.$store.getters.getPropValue('allowAddSync');
      return propValue === 'true'
    },
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
    addSyncForAll() {
      this.customConfirmParams = {
        header: 'Подтвердите добавление записей в SYNC-таблицу',
        body: `Выбрано песен: <strong>${this.countRows}.</strong><br>Добавить эти песни в SYNC-таблицу?`,
        timeout: 10,
        callback: this.doAddSyncForAll
      }
      this.isCustomConfirmVisible = true;
    },
    doAddSyncForAll() {
      this.$store.dispatch('addSyncForAll')
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
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
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
      this.$store.dispatch('setLastThreadId', {value: result.threadId});
      this.$store.dispatch('createKaraokeForAllPromise', {
        priorLyrics: result.priorLyrics,
        priorKaraoke: result.priorKaraoke,
        priorChords: result.priorChords,
        priorMelody: result.priorMelody,
        threadId: result.threadId
      }).then(data => {
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
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateDemucs2ForAll(result) {
      this.$store.dispatch('setLastPriorDemucs', {value: result.prior});
      this.$store.dispatch('setLastThreadId', {value: result.threadId});
      this.$store.dispatch('createDemucs2ForAllPromise', { prior: result.prior, threadId: result.threadId }).then(data => {
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
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateDemucs5ForAll(result) {
      this.$store.dispatch('setLastPriorDemucs', {value: result.prior});
      this.$store.dispatch('setLastThreadId', {value: result.threadId});
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
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
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
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateMP3KaraokeForAll(result) {
      this.$store.dispatch('setLastPriorDemucs', {value: result.prior});
      this.$store.dispatch('setLastThreadId', {value: result.threadId});
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
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateMP3LyricsForAll(result) {
      this.$store.dispatch('setLastPriorDemucs', {value: result.prior});
      this.$store.dispatch('setLastThreadId', {value: result.threadId});
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
    repairAll() {
      this.customConfirmParams = {
        header: 'Подтвердите Repair All',
        body: `Выполнить Repair All для всех песен на странице?`,
        callback: this.doRepairAll
      }
      this.isCustomConfirmVisible = true;
    },
    doRepairAll() {
      this.repairAllForCurrentPage();
      this.isCustomConfirmVisible = false;
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
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateSymlinksForAll(result) {
      this.$store.dispatch('setLastPriorSymlinks', {value: result.prior});
      this.$store.dispatch('setLastThreadId', {value: result.threadId});
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
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
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
      this.$store.dispatch('setLastThreadId', {value: result.threadId});
      let songsIds = this.$store.getters.getSongsDigestIds.join(';')
      this.$store.dispatch('collectStorePromise', {
        songsIds: songsIds,
        priorLyrics: result.priorLyrics,
        priorKaraoke: result.priorKaraoke,
        threadId: result.threadId
      }).then(data => {
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
    async showHealthReportTable(id) {
      // console.log('showHealthReportTable called', id);
      await this.$store.dispatch('setCurrentSongId', id);
      // console.log('showHealthReportTable this.currentSongId', id, this.currentSongId);
      this.isHealthReportTableVisible = true;
    },
    closeHealthReportTable() {
      this.$store.dispatch('setCurrentSongHealthReports', this.currentSongId);
      this.isHealthReportTableVisible = false;
    },
    async editSong(id) {
      this.hrQueue = [];
      await this.$store.dispatch('setCurrentSongId', id);
      this.isSongEditVisible = true;
      this.updateHealthReportForCurrentPage();
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
      this.updateHealthReportForCurrentPage();
    },
    closeSmartCopy() {
      this.isSmartCopyVisible = false;
    },
    onRowClicked(item, index) {
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

/* Иконка сортировки (bootstrap-vue-next SortIcon) выведена из потока, чтобы клик по заголовку
   (сортирует так же, как клик по иконке) не менял ширину столбца — она появляется только
   при наведении на заголовок. */
.songs-bv-table-body th {
  position: relative;
}
.songs-bv-table-body th svg.bi {
  position: absolute;
  right: 2px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0 !important;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.songs-bv-table-body th:hover svg.bi {
  opacity: 0.6 !important;
}

.fld-song-id {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-root-id {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: smaller;
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
.fld-timecode {
  min-width: 60px;
  max-width: 60px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-health-report-text {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
}
.fld-health-report-text:hover {
  text-decoration: underline;
  cursor: pointer;
}
.fld-player {
  min-width: 22px;
  max-width: 22px;
  text-align: center;
  line-height: 0;
  white-space: nowrap;
  overflow: hidden;
}
.fld-player .player-icon-link {
  display: inline-block;
  line-height: 0;
  cursor: pointer;
}
.fld-player .player-icon-disabled {
  display: inline-block;
  line-height: 0;
  cursor: default;
}
.fld-assign {
  min-width: 90px;
  max-width: 90px;
  text-align: center;
  font-size: small;
}
.fld-assign .assign-select,
.fld-assign .assign-badge {
  display: inline-block;
  box-sizing: border-box;
  max-width: 100%;
  font-size: small;
  font-family: inherit;
  line-height: normal;
  margin: 0;
  padding: 0 4px;
  border: none;
  border-radius: 3px;
  cursor: pointer;
}
.fld-assign .assign-select {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  background-color: #eef0f2;
  color: #555;
}
.fld-assign .assign-select:hover {
  background-color: #e2e5e8;
}
.fld-assign .assign-badge {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
}
.fld-assign .assign-badge-assigned { background: #e2e6ea; color: #5a6570; }
.fld-assign .assign-badge-in_progress { background: #dbeafe; color: #1e5fbf; }
.fld-assign .assign-badge-submitted { background: #fef3c7; color: #92700a; }
.fld-assign .assign-badge-approved { background: #d1f5d8; color: #24803a; }
.fld-assign .assign-badge-rejected { background: #ffe0cc; color: #b8500f; }
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
.fld-flag-max-lyrics {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-max-karaoke {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-max-chords {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-max-melody {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-exclusive {
  min-width: 20px;
  max-width: 20px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-flag-free {
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
  height: 18px;
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
.btn-round-long-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 150px;
  height: 50px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.btn-round-long-double:hover {
  background-color: lightpink;
}
.btn-round-long-double:focus {
  background-color: darksalmon;
}
.btn-round-long-double[disabled] {
  background-color: lightgray;
}
.icon-40 {
  width: 40px;
  height: 40px;
}
.star-spacing {
  margin: 0 !important;
}
.b-form-rating {
  margin: 0 !important;
}
</style>
