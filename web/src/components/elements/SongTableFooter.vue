<template>
  <div :style="styleRoot">
    <div class="caption">Песен: <span>{{ countSongs }}</span>, страница <span>{{ currPage + 1 }}</span> из <span>{{ countPages }}</span></div>
    <button class="button-first" @click="goToFirstPage" :disabled="currPage===0 || disabled">&lt;&lt;</button>
    <button class="button-previous" @click="goToPreviousPage" :disabled="currPage===0|| disabled">&lt;</button>
    <button class="button-next" @click="goToNextPage" :disabled="currPage===countPages-1|| disabled">&gt;</button>
    <button class="button-last" @click="goToLastPage" :disabled="currPage===countPages-1|| disabled">&gt;&gt;</button>
    <input class="input-page-size" v-model="ps" @input="changePageSize" :disabled="disabled"/>
  </div>
</template>

<script>
export default {
  name: "SongTableFooter",
  data() {
    return {
      ps: this.$store.getters.getPageSize
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
</style>