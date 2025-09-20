<template>
  <div class="processes-bv-table">
<!--    <ProcessEditModal v-if="isProcessEditVisible" @close="closeProcessEdit"/>-->
    <ProcessesFilter v-if="isProcessesFilterVisible" @close="closeProcessesFilter"/>
    <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
    <div class="processes-bv-table-header">
      <b-pagination
          v-model="currentPage"
          :total-rows="countRows"
          :per-page="perPage"
          :limit="20"
          size="sm"
          pills
      ></b-pagination>
    </div>
    <div class="processes-bv-table-body">
      <b-table
          :items="processesDigests"
          :busy="isBusy"
          :fields="processDigestFields"
          :per-page="perPage"
          :current-page="currentPage"
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
              class="fld-id"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentProcessId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(name)="data">
          <div
              class="fld-process-name"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentProcessId === data.item.id ? 'blue' : 'black' }"
              @click.left="editProcess(data.item.id)"
          ></div>
        </template>

        <template #cell(status)="data">
          <div
              class="fld-status"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentProcessId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(priority)="data">
          <div
              class="fld-priority"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentProcessId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(description)="data">
          <div
              class="fld-description"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentProcessId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(type)="data">
          <div
              class="fld-type"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentProcessId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(startStr)="data">
          <div
              class="fld-start"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentProcessId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(endStr)="data">
          <div
              class="fld-end"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentProcessId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(percentageStr)="data">
          <div
              class="fld-percentage"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentProcessId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(timePassedStr)="data">
          <div
              class="fld-passed"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentProcessId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(timeLeftStr)="data">
          <div
              class="fld-left"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentProcessId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

      </b-table>
    </div>
    <div class="processes-bv-table-footer">
      <button class="btn-round-double" @click="isProcessesFilterVisible=true" title="Фильтр">
        <img alt="filter" class="icon-40" src="../../assets/svg/icon_filter.svg">
      </button>
    </div>


  </div>
</template>

<script>

import Vue from "vue";
import { TablePlugin } from 'bootstrap-vue'
import { PaginationPlugin } from 'bootstrap-vue'
import { SpinnerPlugin } from 'bootstrap-vue'

// import ProcessEditModal from "@/components/Processes/edit/ProcessEditModal.vue";
import ProcessesFilter from "@/components/ProcessesFilter/ProcessesFilterModal.vue";
import CustomConfirm from "../Common/CustomConfirm.vue";
Vue.use(TablePlugin)
Vue.use(PaginationPlugin)
Vue.use(SpinnerPlugin)

export default {
  name: "ProcessesBvTable",
  components: {
    // ProcessEditModal,
    ProcessesFilter,
    CustomConfirm
  },
  data() {
    return {
      perPage: 50,
      currentPage: 1,
      isProcessEditVisible: false,
      isProcessesFilterVisible: false,
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      isBusy: false,
      currentProcessId: 0
    }
  },
  watch: {
    processesDigestIsLoading: {
      handler () {
        this.isBusy = this.processesDigestIsLoading;
      }
    }
  },
  mounted() {
    // this.$store.dispatch('loadProcessesDigests', { filter_author: 'Павел Кашин'} )
  },
  computed: {
    processesDigestIsLoading() {
      return this.$store.getters.getProcessesDigestIsLoading;
    },
    processesDigests() {
      return this.$store.getters.getProcessesDigest;
    },
    countRows() {
      return this.processesDigests.length;
    },
    processDigestFields() {
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
          key: 'name',
          label: 'Имя',
          style: {
            minWidth: '400px',
            maxWidth: '400px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'status',
          label: 'Статус',
          style: {
            minWidth: '90px',
            maxWidth: '90px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'priority',
          label: 'Prior',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'description',
          label: 'Описание',
          style: {
            minWidth: '200px',
            maxWidth: '200px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'type',
          label: 'Тип',
          style: {
            minWidth: '120px',
            maxWidth: '120px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'startStr',
          label: 'Начало',
          style: {
            minWidth: '120px',
            maxWidth: '120px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'endStr',
          label: 'Конец',
          style: {
            minWidth: '120px',
            maxWidth: '120px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'percentageStr',
          label: '%',
          style: {
            minWidth: '70px',
            maxWidth: '70px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'timePassedStr',
          label: 'Pass',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'timeLeftStr',
          label: 'Left',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
      ]
    }
  },
  methods: {

    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },

    editProcess(id) {
      this.$store.commit('setCurrentProcessId', id);
      this.isProcessEditVisible = true;
    },
    closeProcessEdit() {
      this.isProcessEditVisible = false;
    },
    closeProcessesFilter() {
      this.isProcessesFilterVisible = false;
    },
    onRowClicked(item, index) {
      this.currentProcessId = item.id;
      console.log(`Row '${index}' clicked: `, item.processName);
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

.processes-bv-table {
  padding: 0;
  margin: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}

.processes-bv-table-header {
  width: fit-content;
}

.processes-bv-table-body {
  width: fit-content;
}

.processes-bv-table-footer {
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
.fld-process-name {
  min-width: 400px;
  max-width: 400px;
  text-align: left;
  font-size: small;
  cursor: default;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
}
.fld-process-name:hover {
  text-decoration: underline;
  cursor: pointer;
}
.fld-status {
  min-width: 90px;
  max-width: 90px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-priority {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-description {
  min-width: 200px;
  max-width: 200px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-type {
  min-width: 120px;
  max-width: 120px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-start {
  min-width: 120px;
  max-width: 120px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-end {
  min-width: 120px;
  max-width: 120px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-percentage {
  min-width: 70px;
  max-width: 70px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-passed {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-left {
  min-width: 50px;
  max-width: 50px;
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