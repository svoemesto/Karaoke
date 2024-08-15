<template>
  <div :style="styleRoot">
    <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
    <div class="caption">Песен: <span>{{ countSongs }}</span>, страница <span>{{ currPage + 1 }}</span> из <span>{{ countPages }}</span></div>
    <button class="button-first" @click="goToFirstPage" :disabled="!countPages || currPage===0 || disabled">&lt;&lt;</button>
    <button class="button-previous" @click="goToPreviousPage" :disabled="!countPages || currPage===0|| disabled">&lt;</button>
    <button class="button-next" @click="goToNextPage" :disabled="!countPages || currPage===countPages-1 || disabled">&gt;</button>
    <button class="button-last" @click="goToLastPage" :disabled="!countPages || currPage===countPages-1|| disabled">&gt;&gt;</button>
    <input class="input-page-size" v-model="ps" @input="changePageSize" :disabled="!countPages || disabled"/>
    <button class="btn-round-double" @click="searchTextForAll" :disabled="!countPages || disabled" title="Найти тексты для всех песен"><img alt="search texts for all" class="icon-40" src="../../../assets/svg/icon_search_text.svg"></button>
    <button class="btn-round-double" @click="createKaraokeForAll" :disabled="!countPages || disabled" title="Создать караоке для всех песен"><img alt="create karaoke for all" class="icon-40" src="../../../assets/svg/icon_song.svg"></button>
    <button class="btn-round-double" @click="createDemucs2ForAll" :disabled="!countPages || disabled" title="Создать DEMUCS2 для всех песен"><img alt="create demucs2 for all" class="icon-40" src="../../../assets/svg/icon_demucs2.svg"></button>
    <button class="btn-round-double" @click="createSymlinksForAll" :disabled="!countPages || disabled" title="Создать SYMLINKS для всех песен"><img alt="create symlink for all" class="icon-40" src="../../../assets/svg/icon_symlink.svg"></button>
    <button class="btn-round-double" @click="setDateTimeAuthor" :disabled="!countPages || disabled" title="установить дату/время публикации для песен автора, начиная с текущей"><img alt="calendar" class="icon-40" src="../../../assets/svg/icon_calendar_later.svg"></button>
  </div>
</template>

<script>
import CustomConfirm from "../../Common/CustomConfirm.vue";
export default {
  name: "SongTableFooter",
  components: {
    CustomConfirm
  },
  data() {
    return {
      ps: this.$store.getters.getSongPageSize,
      isCustomConfirmVisible: false,
      customConfirmParams: undefined
    }
  },
  props: {
    width: {
      type: String,
      required: false,
      default: () => '100%'
    }
  },
  computed: {
    disabled() {
      return this.$store.getters.isChangedSong;
    },
    styleRoot() {
      return {
        marginTop: 'auto',
        marginLeft: '10px',
        width: this.width+'px',
        display: 'flex',
        alignItems: 'center'
      }
    },
    countSongs() {
      return this.$store.getters.getCountSongs
    },
    countPages() {
      return this.$store.getters.getCountSongPages
    },
    currPage() {
      return this.$store.getters.getCurrentSongPageIndex
    },
    pageSize() {
      return this.$store.getters.getSongPageSize
    }
  },
  methods: {
    searchTextForAll() {
      this.customConfirmParams = {
        header: 'Подтвердите поиск текста',
        body: `Выбрано песен: <strong>${this.countSongs}.</strong><br>Найти в Интернете тексты для всех песен, для которых ещё нет текстов?`,
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
        body: `Выбрано песен: <strong>${this.countSongs}.</strong><br>Создать караоке для всех песен?`,
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
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateKaraokeForAll(result) {
      this.$store.dispatch('setLastPriorLyrics', {value: result.priorLyrics});
      this.$store.dispatch('setLastPriorKaraoke', {value: result.priorKaraoke});
      this.$store.dispatch('createKaraokeForAllPromise', {priorLyrics: result.priorLyrics, priorKaraoke: result.priorKaraoke}).then(data => {
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
        body: `Выбрано песен: <strong>${this.countSongs}.</strong><br>Создать DEMUCS2 для всех песен?`,
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
    createSymlinksForAll() {
      this.customConfirmParams = {
        header: 'Подтвердите создание SYMLINKs',
        body: `Выбрано песен: <strong>${this.countSongs}.</strong><br>Создать SYMLINKs для всех песен?`,
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
    setDateTimeAuthor() {
      this.customConfirmParams = {
        header: 'Подтвердите изменение дат',
        body: `Вы действительно хотите установить дату/время публикации для песен автора, начиная с текущей?`,
        timeout: 10,
        callback: this.doSetDateTimeAuthor
      }

      this.isCustomConfirmVisible = true;
    },
    doSetDateTimeAuthor() {
      this.$store.dispatch('setDateTimeAuthorPromise')
      this.$store.dispatch('setDateTimeAuthorPromise').then(data => {
        let response = JSON.parse(data);
        this.customConfirmParams = {
          isAlert: true,
          alertType: response ? 'info' : 'error',
          header: 'Изменение дат',
          body: `Изменение дат прошло успешно.`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },

    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },
    goToFirstPage() {
      // console.log('GoTo FIRST page')
      this.$store.commit('setCurrentSongPageIndex', 0)
    },
    goToPreviousPage() {
      // console.log('GoTo PREVIOUS page')
      this.$store.commit('setCurrentSongPageIndex', Math.max(0,this.currPage-1))
    },
    goToNextPage() {
      // console.log('GoTo NEXT page')
      this.$store.commit('setCurrentSongPageIndex', Math.min(this.countPages-1,this.currPage+1))
    },
    goToLastPage() {
      // console.log('GoTo LAST page')
      this.$store.commit('setCurrentSongPageIndex', this.countPages-1)
    },
    changePageSize() {
      const newPs = !this.ps ? this.pageSize : this.ps;
      this.goToFirstPage();
      this.$store.commit('setSongPageSize', newPs);
    }
  }
}
</script>

<style scoped>
  span {
    font-weight: bold;
  }
  button {
    border: solid 1px black;
    border-radius: 5px;
    width: 35px;
    margin: 0 1px;
    font-weight: bold;
  }

  input {
    border: solid 1px black;
    border-radius: 5px;
    width: 50px;
    text-align: center;
  }

  .caption {
    margin-left: 5px
  }

  .button-first {
    margin-left: 5px
  }

  .input-page-size {
    margin-left: 5px
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