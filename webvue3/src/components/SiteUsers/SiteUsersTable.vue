<template>
  <div class="sut-table">
    <SiteUserEditModal v-if="isUserEditVisible" @close="closeUserEdit" />

    <div class="sut-toolbar">
      <label class="sut-toolbar-item">
        БД:
        <select v-model="target" @change="onTargetChange">
          <option value="local">Локальная</option>
          <option value="remote">Сервер</option>
        </select>
      </label>
      <input class="sut-toolbar-item" v-model="filterEmail" placeholder="Фильтр по email" @keyup.enter="reload">
      <select class="sut-toolbar-item" v-model="filterIsBanned" @change="reload">
        <option value="">Все статусы</option>
        <option value="+">Только забаненные</option>
        <option value="-">Только активные</option>
      </select>
      <button class="sut-toolbar-item sut-btn" @click="reload">Обновить</button>
    </div>

    <div class="sut-table-header">
      <b-pagination
          v-model="currentPage"
          :total-rows="countRows"
          :per-page="perPage"
          :limit="20"
          size="sm"
          pills
      ></b-pagination>
    </div>
    <div class="sut-table-body">
      <b-table
          :items="siteUsersDigest"
          :busy="isBusy"
          :fields="siteUserDigestFields"
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
          <col v-for="field in scope.fields" :key="field.key" :style="field.style">
        </template>

        <template #cell(email)="data">
          <div class="fld-email" v-text="data.value" @click.left="editUser(data.item.id, data.item)"></div>
        </template>

        <template #cell(banned)="data">
          <div :style="{ color: data.value ? 'darkred' : 'darkgreen', textAlign: 'center' }">
            {{ data.value ? 'Забанен' : 'Активен' }}
          </div>
        </template>

        <template #cell(premium)="data">
          <div style="text-align: center">{{ data.value ? 'Да' : '' }}</div>
        </template>
      </b-table>
    </div>

    <div class="sut-table-footer">
      <span class="sut-count">Всего: {{ countRows }}</span>
    </div>
  </div>
</template>

<script>
import { BPagination, BSpinner, BTable } from 'bootstrap-vue-next'
import SiteUserEditModal from "./edit/SiteUserEditModal.vue";

export default {
  name: "SiteUsersTable",
  components: { SiteUserEditModal, BPagination, BSpinner, BTable },
  data() {
    return {
      perPage: 19,
      currentPage: 1,
      isUserEditVisible: false,
      isBusy: false,
      filterEmail: '',
      filterIsBanned: '',
    }
  },
  watch: {
    siteUsersDigestIsLoading: {
      handler() { this.isBusy = this.siteUsersDigestIsLoading }
    }
  },
  computed: {
    siteUsersDigestIsLoading() { return this.$store.getters.getSiteUsersDigestIsLoading },
    siteUsersDigest() { return this.$store.getters.getSiteUsersDigest },
    countRows() { return this.siteUsersDigest ? this.siteUsersDigest.length : 0 },
    target: {
      get() { return this.$store.getters.getSiteUsersTarget },
      set(value) { this.$store.dispatch('setSiteUsersTarget', value) }
    },
    siteUserDigestFields() {
      return [
        { key: 'id', label: 'ID', style: { minWidth: '50px', maxWidth: '50px', textAlign: 'center', fontSize: 'small' } },
        { key: 'email', label: 'Email', style: { minWidth: '260px', maxWidth: '260px', textAlign: 'left', fontSize: 'small' } },
        { key: 'displayName', label: 'Имя', style: { minWidth: '200px', maxWidth: '200px', textAlign: 'left', fontSize: 'small' } },
        { key: 'sponsrUid', label: 'Sponsr UID', style: { minWidth: '110px', maxWidth: '110px', textAlign: 'center', fontSize: 'small' } },
        { key: 'premium', label: 'Премиум', style: { minWidth: '90px', maxWidth: '90px', textAlign: 'center', fontSize: 'small' } },
        { key: 'banned', label: 'Статус', style: { minWidth: '100px', maxWidth: '100px', textAlign: 'center', fontSize: 'small' } },
        { key: 'createdAt', label: 'Регистрация', style: { minWidth: '160px', maxWidth: '160px', textAlign: 'center', fontSize: 'small' } },
        { key: 'lastLoginAt', label: 'Последний вход', style: { minWidth: '160px', maxWidth: '160px', textAlign: 'center', fontSize: 'small' } },
      ]
    }
  },
  mounted() {
    this.reload();
  },
  methods: {
    reload() {
      this.$store.dispatch('loadSiteUsersDigest', {
        filterEmail: this.filterEmail,
        filterIsBanned: this.filterIsBanned,
      });
    },
    onTargetChange() {
      this.currentPage = 1;
      this.reload();
    },
    editUser(id, user) {
      this.$store.commit('setSiteUserCurrentId', id);
      this.$store.commit('setSiteUserCurrent', user);
      this.isUserEditVisible = true;
    },
    closeUserEdit() {
      this.isUserEditVisible = false;
      this.reload();
    },
    onRowClicked(item) {
      this.editUser(item.id, item);
    }
  }
}
</script>

<style scoped>
.sut-table {
  padding: 0;
  margin: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}
.sut-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: small;
}
.sut-toolbar-item { font-size: small; }
.sut-btn {
  border: solid 1px black;
  border-radius: 6px;
  padding: 4px 10px;
  background-color: antiquewhite;
  cursor: pointer;
}
.sut-btn:hover { background-color: lightpink; }
.sut-table-header { width: fit-content; }
.sut-table-body { width: fit-content; }
.sut-table-footer { margin-top: 6px; font-size: small; color: gray; }
.fld-email {
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.fld-email:hover { text-decoration: underline; cursor: pointer; }
</style>
