<template>
  <div :style="styleRoot">
    <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
    <div class="caption">Песен: <span>{{ countSongs }}</span>, страница <span>{{ currPage + 1 }}</span> из <span>{{ countPages }}</span></div>
    <button class="button-first" @click="goToFirstPage" :disabled="currPage===0 || disabled">&lt;&lt;</button>
    <button class="button-previous" @click="goToPreviousPage" :disabled="currPage===0|| disabled">&lt;</button>
    <button class="button-next" @click="goToNextPage" :disabled="currPage===countPages-1|| disabled">&gt;</button>
    <button class="button-last" @click="goToLastPage" :disabled="currPage===countPages-1|| disabled">&gt;&gt;</button>
    <input class="input-page-size" v-model="ps" @input="changePageSize" :disabled="disabled"/>
    <button class="btn-round-double" @click="searchTextForAll" :disabled="disabled" title="Найти тексты для всех песен"><img alt="search texts for all" class="icon-40" src="../../assets/svg/icon_search_text.svg"></button>
    <button class="btn-round-double" @click="createKaraokeForAll" :disabled="disabled" title="Создать караоке для всех песен"><img alt="create karaoke for all" class="icon-40" src="../../assets/svg/icon_song.svg"></button>
    <button class="btn-round-double" @click="createDemucs2ForAll" :disabled="disabled" title="Создать DEMUCS2 для всех песен"><img alt="create demucs2 for all" class="icon-40" src="../../assets/svg/icon_demucs2.svg"></button>
    <button class="btn-round-double" @click="createSymlinksForAll" :disabled="disabled" title="Создать SYMLINKS для всех песен"><img alt="create symlink for all" class="icon-40" src="../../assets/svg/icon_symlink.svg"></button>
    <button class="btn-round-double" @click="setDateTimeAuthor" :disabled="disabled" title="установить дату/время публикации для песен автора, начиная с текущей"><img alt="calendar" class="icon-40" src="../../assets/svg/icon_calendar_later.svg"></button>
  </div>
</template>

<script>
import CustomConfirm from "../CustomConfirm.vue";
export default {
  name: "SongTableFooter",
  components: {
    CustomConfirm
  },
  data() {
    return {
      ps: this.$store.getters.getPageSize,
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
      return this.$store.getters.isChanged;
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
      return this.$store.getters.getCountPages
    },
    currPage() {
      return this.$store.getters.getCurrentPageIndex
    },
    pageSize() {
      return this.$store.getters.getPageSize
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
        timeout: 10,
        callback: this.doCreateKaraokeForAll
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateKaraokeForAll() {
      this.$store.dispatch('createKaraokeForAll')
    },
    createDemucs2ForAll() {
      this.customConfirmParams = {
        header: 'Подтвердите создание DEMUCS2',
        body: `Выбрано песен: <strong>${this.countSongs}.</strong><br>Создать DEMUCS2 для всех песен?`,
        timeout: 10,
        callback: this.doCreateDemucs2ForAll
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateDemucs2ForAll() {
      this.$store.dispatch('createDemucs2ForAll')
    },
    createSymlinksForAll() {
      this.customConfirmParams = {
        header: 'Подтвердите создание SYMLINKs',
        body: `Выбрано песен: <strong>${this.countSongs}.</strong><br>Создать SYMLINKs для всех песен?`,
        timeout: 10,
        callback: this.doCreateSymlinksForAll
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateSymlinksForAll() {
      this.$store.dispatch('createSymlinksForAll')
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
      this.$store.dispatch('setDateTimeAuthor')
    },

    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },
    goToFirstPage() {
      console.log('GoTo FIRST page')
      this.$store.commit('setCurrentPageIndex', 0)
    },
    goToPreviousPage() {
      console.log('GoTo PREVIOUS page')
      this.$store.commit('setCurrentPageIndex', Math.max(0,this.currPage-1))
    },
    goToNextPage() {
      console.log('GoTo NEXT page')
      this.$store.commit('setCurrentPageIndex', Math.min(this.countPages-1,this.currPage+1))
    },
    goToLastPage() {
      console.log('GoTo LAST page')
      this.$store.commit('setCurrentPageIndex', this.countPages-1)
    },
    changePageSize() {
      const newPs = !this.ps ? this.pageSize : this.ps;
      this.goToFirstPage();
      this.$store.commit('setPageSize', newPs);
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