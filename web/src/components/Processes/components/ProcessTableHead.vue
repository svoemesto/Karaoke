<template>
  <div :style="style">
    <table class="table table-sm table-hover">
      <tr style="padding: 0; margin: 0;">
        <div style="display: flex; padding: 0; margin: 0;">
          <process-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="id" name="id" position="left"/>
          <process-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="name" name="name" />
          <process-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="status" name="status" :dict="dictProcessStatuses"/>
          <process-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="priority" name="priority" />
          <process-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="description" name="description" />
          <process-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="type" name="type" :dict="dictProcessTypes"/>
          <process-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="startStr" name="startStr" />
          <process-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="endStr" name="endStr" />
          <process-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="percentageStr" name="percentageStr" />
          <process-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="timePassedStr" name="timePassedStr" />
          <process-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="timeLeftStr" name="timeLeftStr" position="right"/>
        </div>
      </tr>
    </table>
  </div>


</template>

<script>
import ProcessTableHeadTd from './ProcessTableHeadTd.vue'

export default {
  name: "SongTableHead",
  components: {
    ProcessTableHeadTd
  },
  props: {
    width: {
      type: String,
      required: false,
      default: () => '100%'
    }
  },
  data() {
    return {
      id: '',
      name: '',
      status: '',
      priority: '',
      description: '',
      type: '',
      startStr: '',
      endStr: '',
      percentageStr: '',
      timePassedStr: '',
      timeLeftStr: ''
    }
  },
  computed: {
    dictProcessStatuses() {
      return this.$store.getters.getProcessStatuses;
    },
    dictProcessTypes() {
      return this.$store.getters.getProcessTypes;
    },
    style() {
      return {
        height: '43px',
        margin: 0,
        marginBottom: 'auto',
        padding: 0,
        width: this.width + 'px'
      }
    }
  },
  methods: {
    applyFilter() {
      let params = {};
      if (this.id) params.filter_id = this.id;
      if (this.name) params.filter_name = this.name;
      if (this.status) params.filter_status = this.status;
      if (this.priority) params.filter_priority = this.priority;
      if (this.description) params.filter_description = this.description;
      if (this.type) params.filter_type = this.type;
      if (this.date) params.filter_date = this.date;
      if (this.time) params.filter_time = this.time;
      if (this.tags) params.filter_tags = this.tags;
      this.$store.dispatch('loadProcessesAndDictionaries', params)
    },
    changeField(name, fld) {
      this[name] = fld;
    }
  }
}
</script>

<style scoped>
  table {
    font-size: small;
    width: 100%;
    height: 100%;
  }
</style>