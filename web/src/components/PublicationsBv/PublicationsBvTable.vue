<template>
  <div class="publications-bv-table">
    <SongEditModal v-if="isSongEditVisible" @close="closeSongEdit"/>
    <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
    <div class="publications-bv-table-header">
      <b-pagination
          v-model="currentPage"
          :total-rows="countRows"
          :per-page="perPage"
          :limit="20"
          size="sm"
          pills
      ></b-pagination>
    </div>

    <div class="publications-bv-table-body">
      <b-table
          :items="publicationsDigest"
          :busy="isBusy"
          :per-page="perPage"
          :current-page="currentPage"
          small
          fixed
          bordered
          hover
      >
        <template #table-busy>
          <div class="text-center text-danger my-2">
            <b-spinner class="align-middle"></b-spinner>
            <strong>Loading...</strong>
          </div>
        </template>
<!--        <template #table-colgroup="scope">-->
<!--          <col-->
<!--              v-for="field in scope.fields"-->
<!--              :key="field.key"-->
<!--              :style="field.style"-->
<!--          >-->
<!--        </template>-->
<!--        <template #cell(csrId)="data">-->
<!--          <div-->
<!--              class="fld-id"-->
<!--              v-text="data.value"-->
<!--          ></div>-->
<!--        </template>-->
<!--        <template #cell(csrName)="data">-->
<!--          <div-->
<!--              class="fld-name"-->
<!--              v-text="data.value"-->
<!--          ></div>-->
<!--        </template>-->
        <template #cell()="data">
          <div
              class="fld-name"
              v-text="data.value"
          ></div>
        </template>
      </b-table>
    </div>

    <div class="publications-bv-table-footer">
      <button class="button-date-group" @click="clickButtonDateGroup('all')">С начала</button>
      <button class="button-date-group" @click="clickButtonDateGroup('fromtoday')">С сегодня</button>
      <button class="button-date-group" @click="clickButtonDateGroup('fromnotpublish')">С незавершенной</button>
      <button class="button-date-group" @click="clickButtonDateGroup('fromnotcheck')">С непроверенной</button>
      <button class="button-date-group" @click="clickButtonDateGroup('fromnotdone')">С неготовой</button>
      <button class="button-date-group" @click="clickButtonDateGroup('unpublish')">UNPUBLISH</button>
    </div>

  </div>
</template>

<script>

import Vue from "vue";
import { TablePlugin } from 'bootstrap-vue'
import { PaginationPlugin } from 'bootstrap-vue'
import { SpinnerPlugin } from 'bootstrap-vue'

import SongEditModal from "@/components/Songs/edit/SongEditModal.vue";
import CustomConfirm from "../Common/CustomConfirm.vue";
Vue.use(TablePlugin)
Vue.use(PaginationPlugin)
Vue.use(SpinnerPlugin)

export default {
  name: "PublicationsBvTable",
  components: {
    SongEditModal,
    CustomConfirm
  },
  data() {
    return {
      perPage: 50,
      currentPage: 1,
      isSongEditVisible: false,
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      isBusy: false
    }
  },
  watch: {
    publicationsDigestIsLoading: {
      handler () {
        this.isBusy = this.publicationsDigestIsLoading;
      }
    }
  },
  computed: {
    publicationsDigestIsLoading() {
      return this.$store.getters.getPublicationsDigestIsLoading;
    },
    publicationsDigest() {
      return this.$store.getters.getPublicationsDigest;
    },
    countRows() {
      return this.publicationsDigest.length;
    },
    publicationDigestFields() {
      return [
        {
          key: 'csrId',
          label: 'ID',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'csrName',
          label: 'Name',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
      ]
    }
  },
  methods: {
    clickButtonDateGroup(param) {
      this.$store.dispatch('loadPublicationsDigests', { filterCond: param})
    },
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },
    closeSongEdit() {
      this.isSongEditVisible = false;
    },
  }
}
</script>

<style scoped>

.publications-bv-table {
  padding: 0;
  margin: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}

.publications-bv-table-header {
  width: fit-content;
}

.publications-bv-table-body {
  width: fit-content;
}

.publications-bv-table-footer {
  margin-top: auto;
  display: flex;
  flex-direction: row;
  align-items: center;
}

.button-date-group {
  border: solid 1px black;
  border-radius: 6px;
  width: 150px;
  height: 30px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.button-date-group:hover {
  background-color: lightpink;
}
.button-date-group:focus {
  background-color: darksalmon;
}
.button-date-group[disabled] {
  background-color: lightgray;
}

.fld-id {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-name {
  min-width: 250px;
  max-width: 250px;
  text-align: left;
  font-size: small;
  cursor: default;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
}

</style>