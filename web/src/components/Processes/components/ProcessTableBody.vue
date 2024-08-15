<template>
  <div :style="styleRoot" class="table-process-body">
    <div v-for="process in processes" :key="process.id" @click="clickRow(process.id)" :style="styleRow">
      <process-table-body-td name="id" :text="process.id" :id="process.id" position="left"/>
      <process-table-body-td class="process-name" name="name" :text="process.name" :id="process.id" />
      <process-table-body-td name="status" :text="process.status" :id="process.id" />
      <process-table-body-td name="priority" :text="process.priority" :id="process.id" />
      <process-table-body-td name="description" :text="process.description" :id="process.id" />
      <process-table-body-td name="type" :text="process.type" :id="process.id" />
      <process-table-body-td name="startStr" :text="process.startStr" :id="process.id" />
      <process-table-body-td name="endStr" :text="process.endStr" :id="process.id" />
      <process-table-body-td name="percentageStr" :text="process.percentageStr" :id="process.id" />
      <process-table-body-td name="timePassedStr" :text="process.timePassedStr" :id="process.id" />
      <process-table-body-td name="timeLeftStr" :text="process.timeLeftStr" :id="process.id" position="right"/>
    </div>
  </div>


</template>

<script>
import ProcessTableBodyTd from './ProcessTableBodyTd.vue'

export default {
  name: "SongTableBody",
  components: {
    ProcessTableBodyTd
  },
  data () {
    return {
      isSongEditVisible: false,
    };
  },
  props: {
    processes: {
      type: Array,
      required: false,
      default: () => []
    },
    width: {
      type: String,
      required: false,
      default: () => '100%'
    }
  },
  computed: {
    styleRoot() {
      return {
        margin:0,
        marginBottom: 'auto',
        overflowY: 'scroll',
        width: this.width,
        padding: 0,
        height: 'calc(100vh - 150px)'
      }
    },
    styleRow() {
      return {
        display: 'flex',
        padding: 0,
        margin:0
      }
    }
  },
  methods: {
    clickRow(processId) {
      if (!this.$store.getters.isChangedProcess) {
        // console.log('Called clickRow');
        this.$store.commit('setCurrentProcessId', processId)
      }
    }
  }
}
</script>

<style scoped>

.process-name {
  color: black;
  cursor: default;
}
.process-name:hover {
  color: red;
  cursor: pointer;
}

</style>