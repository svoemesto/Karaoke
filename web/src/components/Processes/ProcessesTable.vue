<template>
  <div :style="styleRoot">
    <process-table-head :width="width"/>
    <process-table-body :width="width" :processes="processes"/>
    <process-table-footer :width="width"/>
  </div>
</template>

<script>

// import { mapActions } from "vuex";

import ProcessTableBody from './components/ProcessTableBody.vue'
import ProcessTableHead from './components/ProcessTableHead.vue'
import ProcessTableFooter from './components/ProcessTableFooter.vue'

export default {
  name: "SongsTable",
  components: {
    ProcessTableBody,
    ProcessTableHead,
    ProcessTableFooter
  },
  computed: {
    processes() {
      return this.$store.getters.getProcesses
    },
    width() {
      return this.$store.getters.getProcessFieldsParamsWidth
    },
    styleRoot() {
      return {
        padding: 0,
        margin: 0,
        width: this.width+25+'px',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: 'lightblue'
      }
    }
  },
  methods: {
  }
}
</script>

<style scoped>

</style>