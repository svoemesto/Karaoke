<template>
  <div :style="styleRoot">
    <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
    <div class="caption">Процессов: <span>{{ countProcesses }}</span>, страница <span>{{ currPage + 1 }}</span> из <span>{{ countPages }}</span></div>
    <button class="button-first" @click="goToFirstPage" :disabled="!countPages || currPage===0 || disabled">&lt;&lt;</button>
    <button class="button-previous" @click="goToPreviousPage" :disabled="!countPages || currPage===0|| disabled">&lt;</button>
    <button class="button-next" @click="goToNextPage" :disabled="!countPages || currPage===countPages-1|| disabled">&gt;</button>
    <button class="button-last" @click="goToLastPage" :disabled="!countPages || currPage===countPages-1|| disabled">&gt;&gt;</button>
    <input class="input-page-size" v-model="ps" @input="changePageSize" :disabled="!countPages || disabled"/>
    <button class="button-action" @click="reload">Обновить</button>
    <button class="button-action" @click="delDone">Удалить завершенные</button>
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
      ps: this.$store.getters.getProcessPageSize,
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
      return this.$store.getters.isChangedProcess;
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
    countProcesses() {
      return this.$store.getters.getCountProcesses
    },
    countPages() {
      return this.$store.getters.getCountProcessPages
    },
    currPage() {
      return this.$store.getters.getCurrentProcessPageIndex
    },
    pageSize() {
      return this.$store.getters.getProcessPageSize
    }
  },
  methods: {
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },
    goToFirstPage() {
      // console.log('GoTo FIRST page')
      this.$store.commit('setCurrentProcessPageIndex', 0)
    },
    goToPreviousPage() {
      // console.log('GoTo PREVIOUS page')
      this.$store.commit('setCurrentProcessPageIndex', Math.max(0,this.currPage-1))
    },
    goToNextPage() {
      // console.log('GoTo NEXT page')
      this.$store.commit('setCurrentProcessPageIndex', Math.min(this.countPages-1,this.currPage+1))
    },
    goToLastPage() {
      // console.log('GoTo LAST page')
      this.$store.commit('setCurrentProcessPageIndex', this.countPages-1)
    },
    changePageSize() {
      const newPs = !this.ps ? this.pageSize : this.ps;
      this.goToFirstPage();
      this.$store.commit('setProcessPageSize', newPs);
    },
    reload() {
      this.$store.dispatch('loadProcessesAndDictionaries', {})
    },
    delDone() {
      this.$store.dispatch('deleteDoneProcessesPromise').then(() => {
        this.$store.dispatch('loadProcessesAndDictionaries', {})
      })
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

  .button-action {
    border: solid 1px black;
    border-radius: 6px;
    width: auto;
    height: 30px;
    margin-left: 2px;
    background-color: antiquewhite;
    color: black;
  }
  .button-action:hover {
    background-color: lightpink;
  }
  .button-action:focus {
    background-color: darksalmon;
  }
  .button-action[disabled] {
    background-color: lightgray;
  }
</style>